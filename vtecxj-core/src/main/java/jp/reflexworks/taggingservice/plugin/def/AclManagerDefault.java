package jp.reflexworks.taggingservice.plugin.def;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.Contributor;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.blogic.AclBlogic;
import jp.reflexworks.taggingservice.blogic.AclConst;
import jp.reflexworks.taggingservice.blogic.DatastoreBlogic;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.PermissionException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.AclManager;
import jp.reflexworks.taggingservice.plugin.UserManager;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.sys.SystemUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.reflexworks.taggingservice.util.UserUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * ACL管理クラス.
 */
public class AclManagerDefault implements AclManager, AclConst {

	/**
	 * グループのエスケープ文字
	 */
	private static final Set<String> GROUP_ESCAPE_CHAR = new HashSet<String>();
	static {
		GROUP_ESCAPE_CHAR.add(".");
		GROUP_ESCAPE_CHAR.add("$");
		GROUP_ESCAPE_CHAR.add("/");
	}

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 初期処理.
	 */
	@Override
	public void init() {
		// Do nothing.
	}

	/**
	 * シャットダウン処理.
	 */
	@Override
	public void close() {
		// Do nothing.
	}

	/**
	 * 認可チェック
	 * @param uri キー (フィード検索や自動採番登録の場合、selfidはダミー(#)をセットする。)
	 * @param action C, R, U or D
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @throws PermissionException 認可エラー
	 */
	public void checkAcl(String uri, String action,
			ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		SystemContext systemContext = new SystemContext(auth,
				requestInfo, connectionInfo);

		// システムユーザのチェック
		if (auth != null && SystemUtil.isSystem(auth)) {
			return;
		}
		// 削除処理でサービス管理者の場合OK
		boolean isServiceAdmin = isAuthedGroup(auth, Constants.URI_GROUP_ADMIN);
		if (ACL_TYPE_DELETE.equals(action) && isServiceAdmin) {
			return;
		}

		AclBlogic aclBlogic = new AclBlogic();
		String parentPathUri = aclBlogic.cutDummySelfidUri(uri);
		boolean isMyLayer = uri.equals(parentPathUri);
		DatastoreBlogic datastoreBlogic = new DatastoreBlogic();
		FeedBase feed = datastoreBlogic.getParentPathEntries(parentPathUri, true,
				systemContext.getAuth(), requestInfo, connectionInfo);

		List<EntryBase> parentPathEntries = null;
		if (feed != null) {
			parentPathEntries = feed.entry;
		}

		// サービス管理者がルートURIを指定した場合
		if (TaggingEntryUtil.URI_ROOT.equals(uri) && isServiceAdmin) {
			if (ACL_TYPE_CREATE.equals(action)) {
				// 登録処理の場合OK
				return;
			} else if (ACL_TYPE_RETRIEVE.equals(action) &&
					(parentPathEntries == null || parentPathEntries.isEmpty())) {
				// 検索処理の場合、エントリーが存在しなければOK
				return;
			}
		}

		// ACLチェック
		if (!hasAuthority(uri, parentPathEntries, action, isMyLayer, auth)) {
			String uid = null;
			if (auth != null) {
				uid = auth.getUid();
			}

			if (logger.isTraceEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("checkAcl.!hasAuthority [PermissionException] uid = ");
				sb.append(uid);
				String account = null;
				if (auth != null) {
					account = auth.getAccount();
				}
				sb.append(", account = ");
				sb.append(account);
				sb.append(", action = ");
				sb.append(action);
				sb.append(", uri = ");
				sb.append(uri);
				sb.append(", isExternal = ");
				boolean isExternal = false;
				if (auth != null) {
					isExternal = auth.isExternal();
				}
				sb.append(isExternal);
				logger.trace(sb.toString());
				if (parentPathEntries != null && parentPathEntries.size() > 0) {
					for (EntryBase parentPathEntry : parentPathEntries) {
						if (parentPathEntry != null) {
							StringBuilder sb2 = new StringBuilder();
							sb2.append(LogUtil.getRequestInfoStr(requestInfo));
							sb2.append("checkAcl.!hasAuthority [PermissionException] parentUri : ");
							sb2.append(parentPathEntry.getMyUri());
							sb2.append(", contributor : ");
							sb2.append(parentPathEntry.contributor);
							logger.trace(sb2.toString());
						} else {
							logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
									"checkAcl.!hasAuthority [PermissionException] parentUri : null");
						}
					}
				} else {
					logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
							"checkAcl.!hasAuthority [PermissionException] parentPathEntries is null.");
				}
				PermissionException e = new PermissionException(uid, uri);
				logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
						"checkAcl.!hasAuthority [PermissionException]", e);
			}

			throw new PermissionException(uid, uri);
		}
	}

	/**
	 * 指定されたグループに参加しているかチェック.
	 * @param auth 認証情報
	 * @param groupUri グループ
	 * @throws PermissionException グループに参加していない場合
	 */
	public void checkAuthedGroup(ReflexAuthentication auth, String groupUri)
	throws PermissionException {
		if (!isAuthedGroup(auth, groupUri)) {
			PermissionException pe = new PermissionException();
			pe.setSubMessage("It has not joined the group : " + groupUri);
			throw new PermissionException();
		}
	}

	/**
	 * 指定されたグループに参加しているかどうか.
	 * @param auth 認証情報
	 * @param groupUri グループ
	 * @return 指定されたグループに参加している場合true
	 */
	public boolean isInTheGroup(ReflexAuthentication auth, String groupUri)
	throws IOException, TaggingException {
		return isAuthedGroup(auth, groupUri);
	}

	/**
	 * 自分自身の権限チェック
	 * <p>
	 * フィード検索を対象に、上位階層のACL権限があることを前提にチェックする。<br>
	 * <ul>
	 * <li>ACLの設定がない場合はtrueを返却する。</li>
	 * <li>ACLの設定があり、認証情報のACL設定がある場合はtrueを返却する。</li>
	 * <li>ACLの設定があり、認証情報のACL設定がない場合はfalseを返却する。</li>
	 * </ul>
	 * </p>
	 * @param entry Entry
	 * @param auth 認証情報
	 * @return 権限あり:true, 権限なし:false
	 */
	public boolean hasAuthoritySelf(EntryBase entry, ReflexAuthentication auth)
	throws IOException {
		// システムユーザのチェック
		if (auth != null && SystemUtil.isSystem(auth)) {
			return true;
		}
		// ACL設定がない場合はtrue
		if (entry == null || entry.contributor == null ||
				entry.contributor.size() == 0) {
			return true;
		}
		// ACL指定判定
		boolean hasAcl = false;
		for (Contributor contributor : entry.getContributor()) {
			if (contributor != null) {
				String urn = contributor.getUri();
				if (urn != null && urn.startsWith(Constants.URN_PREFIX_ACL)) {
					boolean ret = hasAuthority(contributor, ACL_TYPE_RETRIEVE, auth,
							true, entry);
					if (ret) {
						return ret;
					}
					hasAcl = true;
				}
			}
		}
		// ACL指定があり、認証情報が認可されなければfalseを返却
		if (hasAcl) {
			return false;
		}
		return true;
	}

	/**
	 * contributorリストの中に、指定されたACL対象と権限(CRUD等のいずれか)の設定があるかどうかチェック.
	 * @param contributors contributorリスト
	 * @param aclUser ACL対象
	 * @param action C, R, U or D
	 * @return 指定されたACL対象と権限がある場合true
	 */
	public boolean hasAclUserAndType(List<Contributor> contributors,
			String aclUser, String action) {
		if (contributors == null || contributors.size() == 0 ||
				StringUtils.isBlank(aclUser) || StringUtils.isBlank(action)) {
			return false;
		}

		for (Contributor contributor : contributors) {
			String tmpUrn = contributor.uri;
			if (tmpUrn == null || !tmpUrn.startsWith(Constants.URN_PREFIX_ACL)) {
				continue;
			}
			String[] tmpUrnParts = tmpUrn.split("\\,");
			String tmpAclUser = getAclUserByUrn(tmpUrnParts);
			String tmpAclType = tmpUrnParts[IDX_ACLTYPE];
			if (aclUser.equals(tmpAclUser) && tmpAclType != null &&
					tmpAclType.indexOf(action) > -1) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 権限チェック
	 * @param uri チェックする階層
	 * @param parentPathEntries 上位階層のEntryリスト
	 * @param action C, R, U or D
	 * @param pIsMyLayer 自Entryの場合true
	 * @param auth 認証情報
	 * @return 権限あり:true, 権限なし:false
	 * @throws IOException
	 */
	private boolean hasAuthority(String uri, List<EntryBase> parentPathEntries,
			String action, boolean pIsMyLayer, ReflexAuthentication auth)
	throws IOException {
		if (parentPathEntries != null && parentPathEntries.size() > 0) {
			boolean isMyLayer = pIsMyLayer;
			for (EntryBase entry : parentPathEntries) {
				// contributorをチェック
				boolean hasContributor = false;
				if (entry != null && entry.getContributor() != null &&
						entry.getContributor().size() > 0) {
					for (Contributor contributor : entry.getContributor()) {
						if (contributor == null) {
							continue;
						}
						// acl指定判定
						String urn = contributor.getUri();
						if (urn != null && urn.startsWith(Constants.URN_PREFIX_ACL)) {
							hasContributor = true;
							boolean ret = hasAuthority(contributor, action, auth,
									isMyLayer, entry);
							if (ret) {
								return ret;
							}
						}
					}
				}
				if (hasContributor) {
					return false;
				}
				if (isMyLayer) {
					isMyLayer = false;
				}
			}
		}

		return false;
	}

	/**
	 * 権限チェック
	 * @param contributor 権限情報
	 * @param action C, R, U or D
	 * @param auth 認証情報
	 * @param isMyLayer contributorが自エントリーのものである場合true。親階層のものである場合false。
	 * @param entry contributorのエントリー
	 * @param systemContext ReflexContext
	 * @return 権限あり:true, 権限なし:false
	 */
	private boolean hasAuthority(Contributor contributor, String action,
			ReflexAuthentication auth, boolean isMyLayer, EntryBase entry)
	throws IOException {
		if (contributor == null) {
			return false;
		}
		if (!ACL_TYPE_CREATE.equals(action) &&
				!ACL_TYPE_RETRIEVE.equals(action) &&
				!ACL_TYPE_UPDATE.equals(action) &&
				!ACL_TYPE_DELETE.equals(action)) {
			return false;
		}
		String tmpUrn = contributor.getUri();
		String[] urn = tmpUrn.split("\\,");
		int urnLen = urn.length;

		if (urnLen == IDX_ACLTYPE + 1) {
			// ログイン認証
			boolean isHit = hasAclUserAndType(auth, tmpUrn, action, isMyLayer, entry);
			if (isHit) {
				return true;
			}
		}

		return false;
	}

	/**
	 * 認可チェック
	 * 指定されたactionのACL Typeが設定されているかどうかチェックする
	 * @param auth 認証情報
	 * @param tmpUrn ContributorのURN
	 * @param action C, R, U or D
	 * @param isMyLayer contributorが自エントリーのものである場合true。親階層のものである場合false。
	 * @param entry tmpUrnのエントリー
	 * @return 認可対象の場合true
	 */
	private boolean hasAclUserAndType(ReflexAuthentication auth, String tmpUrn,
			String action, boolean isMyLayer, EntryBase entry)
	throws IOException {
		// システムユーザでUID設定なしの場合は認可OK
		if (auth != null && SystemUtil.isSystem(auth) && auth.getUid() == null) {
			return true;
		}

		String[] urn = tmpUrn.split("\\,");
		String aclUser = getAclUserByUrn(urn);
		String aclType = urn[IDX_ACLTYPE];

		if (isMyLayer) {
			// 自エントリーのACL設定かつ、Lオプションが指定されている場合は認可しない。
			if (aclType.indexOf(ACL_TYPE_LOW) > -1 &&
					aclType.indexOf(ACL_TYPE_OWN) == -1) {
				return false;
			}
		} else {
			// 上位エントリーのACL設定かつ、Oオプションが指定されている場合は認可しない。
			if (aclType.indexOf(ACL_TYPE_OWN) > -1 &&
					aclType.indexOf(ACL_TYPE_LOW) == -1) {
				return false;
			}
		}

		boolean isExternal = false;
		if (auth != null) {
			isExternal = auth.isExternal();
		}
		if (!StringUtils.isBlank(aclUser)) {
			if (isGroup(aclUser)) {
				// グループ指定
				if (isMatchAclGroup(auth, aclUser)) {
					return hasAclType(urn, action, isExternal);
				}

			} else {
				// ユーザ指定
				if (isMatchAclUser(auth, aclUser, entry)) {
					return hasAclType(urn, action, isExternal);
				}
			}
		}
		return false;
	}

	/**
	 * ACL対象がグループでない場合の、ACLチェック
	 * @param auth 認証情報
	 * @param aclUser ACL対象
	 * @param entry contributorのEntry
	 * @return 認可対象の場合true
	 */
	private boolean isMatchAclUser(ReflexAuthentication auth, String aclUser,
			EntryBase entry) {
		if (aclUser == null || aclUser.length() == 0) {
			return false;
		}
		if (aclUser.equals(ACL_USER_ANY)) {
			return true;	// anonymous
		}

		if (auth == null) {
			return false;
		}

		if (aclUser.equals(ACL_USER_LOGGEDIN) &&
				!StringUtils.isBlank(auth.getAccount()) &&
				!StringUtils.isBlank(auth.getUid())) {
			return true;	// login user
		}

		String uid = auth.getUid();
		String account = auth.getAccount();
		boolean match = isMatchAclUid(uid, aclUser, entry);
		if (!match) {
			match = isMatchAclAccount(account, aclUser);
		}
		return match;
	}

	/**
	 * ACL対象がUIDの場合のACLチェック
	 * @param uid UID
	 * @param aclUser ACL対象
	 * @param entry ACL指定Entry
	 * @return 認可対象の場合true
	 */
	private boolean isMatchAclUid(String uid, String aclUser, EntryBase entry) {
		if (StringUtils.isBlank(uid)) {
			return false;
		}
		if (aclUser.equals(ACL_USER_SELFALIAS)) {
			// "-"指定(selfまたはエイリアスのユーザトップエントリーのuidがログイン情報のuidと等しい)の場合
			List<String> tmpUids = getSelfAliasUids(entry);
			if (tmpUids == null) {
				return false;
			}
			for (String tmpUid : tmpUids) {
				if (uid.equals(tmpUid)) {
					return true;
				}
			}
			return false;
		}

		if (uid.equals(aclUser)) {	// 完全一致
			return true;
		}
		return false;
	}

	/**
	 * ACL対象がアカウントの場合のACLチェック
	 * @param account アカウント
	 * @param aclUser ACL対象
	 * @return 認可対象の場合true
	 */
	private boolean isMatchAclAccount(String account, String aclUser) {
		if (StringUtils.isBlank(account)) {
			return false;
		}

		// ワイルドカード指定を可能とする
		if (aclUser.startsWith(GROUP_WILDCARD)) {
			String tmpAclUser = aclUser.substring(1);
			if (tmpAclUser.endsWith(GROUP_WILDCARD)) {
				// 前・後方がワイルドカード
				tmpAclUser = editAccount(tmpAclUser.substring(0, tmpAclUser.length() - 1));
				return (account.indexOf(tmpAclUser) > -1);
			} else {
				// 前方がワイルドカード
				tmpAclUser = editAccount(tmpAclUser);
				return account.endsWith(tmpAclUser);
			}
		} else {
			if (aclUser.endsWith(GROUP_WILDCARD)) {
				// 後方がワイルドカード
				String tmpAclUser = editAccount(aclUser.substring(0, aclUser.length() - 1));
				return (account.indexOf(tmpAclUser) > -1);
			} else {
				// 完全一致
				String tmpAclUser = editAccount(aclUser);
				return account.equals(tmpAclUser);
			}
		}
	}

	/**
	 * アカウント指定について、メールアドレス指定も可能とする。
	 * @param account アカウント
	 * @return 整形したアカウント
	 */
	private String editAccount(String account) {
		return UserUtil.editAccount(account);
	}

	/**
	 * ACL対象がグループの場合のACLチェック
	 * @param auth 認証情報
	 * @param aclUser グループ
	 * @return 認可対象の場合true
	 */
	private boolean isMatchAclGroup(ReflexAuthentication auth, String aclUser)
	throws IOException {
		if (auth == null || !isGroup(aclUser)) {
			return false;
		}
		// グループチェック
		if (isAuthedGroup(auth, aclUser)) {
			return true;
		}
		return false;	// グループ認証失敗
	}

	/**
	 * 指定されたグループに参加しているかどうかチェック.
	 * <p>
	 * ワイルドカード対応<br>
	 * "*"をワイルドカードとする。
	 * </p>
	 * @param auth 認証情報
	 * @param groupUri グループ
	 * @return 指定されたグループに参加している場合true
	 */
	private boolean isAuthedGroup(ReflexAuthentication auth, String groupUri) {
		if (auth == null) {
			return false;
		}
		// システム権限はtrue
		if (SystemUtil.isSystem(auth)) {
			return true;
		}
		if (StringUtils.isBlank(groupUri) ||
				auth.getGroups() == null || auth.getGroups().isEmpty()) {
			return false;
		}
		List<String> groups = auth.getGroups();
		if (groupUri.indexOf(GROUP_WILDCARD) == -1) {
			// 完全一致判定
			if (groups.contains(groupUri)) {
				return true;
			}
		} else {
			// ワイルドカード使用
			String patternStr = convertGroupPattern(groupUri);
			Pattern pattern = Pattern.compile(patternStr);
			for (String groupStr : groups) {
				Matcher matcher = pattern.matcher(groupStr);
				if (matcher.matches()) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * 指定されたactionのACL Typeが設定されているかどうかチェック
	 * (NotificationUtilで使用されていたメソッド)
	 * @param urn Contributorに指定されたuri
	 * @param action C, R, U or D
	 * @param isMyLayer 自エントリーの場合true
	 * @param isExternal Externalのみの場合true
	 */
	private boolean hasAclType(String urn, String action, boolean isMyLayer,
			boolean isExternal) {
		if (urn == null || action == null || !urn.startsWith(Constants.URN_PREFIX_ACL)) {
			return false;
		}
		String[] urnParts = urn.split("\\,");
		String aclType = urnParts[IDX_ACLTYPE];

		if (isMyLayer) {
			// 自エントリーのACL設定かつ、Lオプションのみが指定されている場合は認可しない。
			if (aclType.indexOf(ACL_TYPE_LOW) > -1 &&
					aclType.indexOf(ACL_TYPE_OWN) == -1) {
				return false;
			}
		} else {
			// 上位エントリーのACL設定かつ、Oオプションのみが指定されている場合は認可しない。
			if (aclType.indexOf(ACL_TYPE_OWN) > -1 &&
					aclType.indexOf(ACL_TYPE_LOW) == -1) {
				return false;
			}
		}

		return hasAclType(urnParts, action, isExternal);
	}

	/**
	 * 指定されたactionのACL Typeが設定されているかどうかチェック
	 */
	private boolean hasAclType(String[] urn, String action, boolean isExternal) {
		boolean ret = false;
		boolean checkE = false;
		if (urn != null && urn.length > IDX_ACLTYPE) {
			int len = urn[IDX_ACLTYPE].length();
			for (int i = 0; i < len; i++) {
				String t = urn[IDX_ACLTYPE].substring(i, i + 1);
				if (action.equals(t)) {
					ret = true;
				} else if (ACL_TYPE_EXTERNAL.equals(t)) {
					checkE = true;
				}
			}
		}

		if (checkE && !isExternal) {
			ret = false;
		}

		return ret;
	}

	/**
	 * グループを正規表現に編集
	 * @param str グループ文字列
	 * @return 正規表現にした文字列
	 */
	private String convertGroupPattern(String str) {
		if (StringUtils.isBlank(str)) {
			return str;
		}
		StringBuilder sb = new StringBuilder();
		int len = str.length();
		for (int i = 0; i < len; i++) {
			String chr = str.substring(i, i + 1);
			if (GROUP_WILDCARD.equals(chr)) {
				sb.append(".*");
			} else if (GROUP_ESCAPE_CHAR.contains(chr)) {
				sb.append("\\");
				sb.append(chr);
			} else {
				sb.append(chr);
			}
		}
		return sb.toString();
	}

	/**
	 * ACL対象がグループかどうか判定
	 * @param aclUser ACL対象
	 * @return ACL対象がグループの場合true
	 */
	private boolean isGroup(String aclUser) {
		if (aclUser != null && aclUser.startsWith("/")) {
			return true;
		}
		return false;
	}

	/**
	 * Contributorのuriのうち、カンマ以降(,CRUDAE)を除いたurnからACLユーザ名を取得します.
	 * @param urnParts Contributorのuriのうち、カンマ以降(,CRUDAE)を除いたurn
	 * @return ACLユーザ名
	 */
	private String getAclUserByUrn(String[] urnParts) {
		if (urnParts == null || urnParts.length < AclConst.IDX_ID + 1) {
			return null;
		}
		String urn = urnParts[AclConst.IDX_ID];
		if (urn != null && urn.startsWith(Constants.URN_PREFIX_ACL)) {
			return urn.substring(Constants.URN_PREFIX_ACL.length());
		}
		return null;
	}

	/**
	 * エントリーに含まれるlink selfとエイリアスからUIDを取得.
	 * @param entry エントリー
	 * @return UIDリスト
	 */
	private List<String> getSelfAliasUids(EntryBase entry) {
		if (entry == null) {
			return null;
		}

		UserManager userManager = TaggingEnvUtil.getUserManager();
		List<String> users = new ArrayList<String>();
		// ID
		String idUri = TaggingEntryUtil.getUriById(entry.id);
		String uid = userManager.getUidByUri(idUri);
		if (!StringUtils.isBlank(uid)) {
			users.add(uid);
		}
		// エイリアス
		List<String> aliases = entry.getAlternate();
		if (aliases != null) {
			for (String alias : aliases) {
				uid = userManager.getUidByUri(alias);
				if (!StringUtils.isBlank(uid)) {
					users.add(uid);
				}
			}
		}
		return users;
	}

}
