package jp.reflexworks.taggingservice.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.Contributor;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.blogic.AclBlogic;
import jp.reflexworks.taggingservice.blogic.SignatureBlogic;
import jp.reflexworks.taggingservice.blogic.UserBlogic;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.PermissionException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.UserManager;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * グループに関するユーティリティ
 */
public class GroupUtil {

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(GroupUtil.class);

	/**
	 * このグループが有効かどうかチェックする.
	 *   ・ID URIが /_group で始まる形式であれば署名不要
	 *   ・自分で作成したグループの場合署名不要
	 *   ・それ以外であれば、グループ管理者とグループ参加者双方の署名が必要
	 * @param groupEntry グループエントリー
	 * @param systemContext SystemContext
	 * @return このグループが有効な場合true
	 */
	public static boolean isValidGroup(EntryBase groupEntry, SystemContext systemContext)
	throws IOException, TaggingException {
		RequestInfo requestInfo = systemContext.getRequestInfo();
		// グループEntryは「/{グループURI}/{参加者UID}」。
		// グループURIとして、親URIを取得する。
		String childIdUri = TaggingEntryUtil.getUriById(groupEntry.id);
		String groupUri = TaggingEntryUtil.removeLastSlash(
				TaggingEntryUtil.getParentUri(childIdUri));
		String uid = TaggingEntryUtil.getSelfidUri(childIdUri);
		String groupParentUri = getGroupUriByUid(uid);

		boolean ret = false;
		if (!existGroupAlias(groupEntry, uid)) {
			// 自身のグループエイリアスがない場合は無効
			ret = false;
		
		} else if (isAdministrativeGroup(groupUri)) {
			// ID URIが /_group で始まる形式であれば署名不要
			ret = true;

		} else if (isMyGroup(uid, groupUri, systemContext)) {
			// 自分で作成したグループの場合署名不要
			ret = true;

		} else {
			// グループ管理者とグループ参加者双方の署名が必要
			// グループ管理者署名チェック
			String signatureUriGroupAdmin = TaggingEntryUtil.getUriById(groupEntry.id);
			SignatureBlogic signatureBlogic = new SignatureBlogic();
			boolean isSignGroupAdmin = signatureBlogic.isValidSignature(groupEntry,
					signatureUriGroupAdmin, systemContext);
			// 自分自身の署名チェック
			String signatureUriMyself = null;
			boolean isSignMyself = false;
			List<String> aliases = groupEntry.getAlternate();
			if (aliases != null) {
				for (String alias : aliases) {
					String aliasParentUri = TaggingEntryUtil.removeLastSlash(
							TaggingEntryUtil.getParentUri(alias));
					if (groupParentUri.equals(aliasParentUri)) {
						signatureUriMyself = alias;
						if (logger.isTraceEnabled()) {
							logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
									"[isValidGroup] signatureUriMyself=" + signatureUriMyself);
						}
						isSignMyself = signatureBlogic.isValidSignature(groupEntry,
								signatureUriMyself, systemContext);
						break;
					}
				}
			}

			if (isSignGroupAdmin && isSignMyself) {
				// グループ認証成功
				ret = true;
			} else {
				if (!StringUtils.isBlank(signatureUriMyself)) {
					if (logger.isTraceEnabled()) {
						StringBuilder sb = new StringBuilder();
						sb.append(LogUtil.getRequestInfoStr(requestInfo));
						sb.append("[isValidGroup] group signature invalid. key = ");
						if (!isSignGroupAdmin) {
							sb.append(signatureUriGroupAdmin);
						}
						if (!isSignMyself) {
							if (!isSignGroupAdmin) {
								sb.append(", ");
							}
							sb.append(signatureUriMyself);
						}
						logger.debug(sb.toString());
					}
				}
			}
		}

		return ret;
	}

	/**
	 * このグループがグループ管理者側で有効かどうかチェックする.
	 * ユーザの署名は判定しない。ただしユーザのグループエイリアスがない場合は無効とする。
	 * (Push通知はユーザ署名を必要としないため、グループ管理者のチェックのみ行う。)
	 *   ・ID URIが /_group で始まる形式であれば署名不要
	 *   ・自分で作成したグループの場合署名不要
	 *   ・それ以外であれば、グループ管理者の署名が必要
	 * @param groupEntry グループエントリー
	 * @param systemContext SystemContext
	 * @return このグループがグループ管理者側で有効な場合true
	 */
	public static boolean isValidGroupAdmin(EntryBase groupEntry, SystemContext systemContext)
	throws IOException, TaggingException {
		// グループEntryは「/{グループURI}/{参加者UID}」。
		// グループURIとして、親URIを取得する。
		String childIdUri = TaggingEntryUtil.getUriById(groupEntry.id);
		String groupUri = TaggingEntryUtil.removeLastSlash(
				TaggingEntryUtil.getParentUri(childIdUri));
		String uid = TaggingEntryUtil.getSelfidUri(childIdUri);

		boolean ret = false;
		if (!existGroupAlias(groupEntry, uid)) {
			// 自身のグループエイリアスがない場合は無効
			ret = false;
			
		} else if (isAdministrativeGroup(groupUri)) {
			// ID URIが /_group で始まる形式であれば署名不要
			ret = true;

		} else if (isMyGroup(uid, groupUri, systemContext)) {
			// 自分で作成したグループの場合署名不要
			ret = true;

		} else {
			// グループ管理者署名チェック
			String signatureUriGroupAdmin = TaggingEntryUtil.getUriById(groupEntry.id);
			SignatureBlogic signatureBlogic = new SignatureBlogic();
			ret = signatureBlogic.isValidSignature(groupEntry,
					signatureUriGroupAdmin, systemContext);
		}

		return ret;
	}

	/**
	 * 管理用グループかどうかチェック.
	 * グループのID URIが /_group/ で始まる形式であれば管理用グループ
	 * @param idUri グループのID URI
	 * @return ID URIが管理用グループであればtrue
	 */
	public static boolean isAdministrativeGroup(String idUri) {
		if (!StringUtils.isBlank(idUri) &&
				idUri.startsWith(GroupConst.URI_GROUP_SLASH)) {
			return true;
		}
		return false;
	}

	/**
	 * 自分で作成したグループかどうかチェック
	 * @param uid UID
	 * @param groupUri グループ名
	 * @param systemContext SystemContext
	 * @return 自分で作成したグループの場合true
	 */
	public static boolean isMyGroup(String uid, String groupUri, SystemContext systemContext)
	throws IOException, TaggingException {
		// 参加グループのキー先頭/_user/{グループ管理者UID}のUIDと、自分のUIDが同じ場合
		UserBlogic userBlogic = new UserBlogic();
		String groupUid = userBlogic.getUidByUri(groupUri);
		if (uid.equals(groupUid)) {
			return true;
		}
		// グループキー(Entryのidキーの親キー)のACLに、自分のUIDの更新権限(U)がある場合
		List<Contributor> contributors = null;
		String tmpUri = groupUri;
		while (contributors == null && !TaggingEntryUtil.isTop(tmpUri)) {
			EntryBase groupEntry = systemContext.getEntry(tmpUri, true);
			if (groupEntry != null && groupEntry.contributor != null &&
					groupEntry.contributor.size() > 0) {
				contributors = groupEntry.contributor;
			}
			tmpUri = TaggingEntryUtil.removeLastSlash(TaggingEntryUtil.getParentUri(tmpUri));
		}
		// 自分のUIDが指定されているか
		AclBlogic aclBlogic = new AclBlogic();
		return aclBlogic.hasAclUserAndType(contributors, uid, Constants.ACL_TYPE_UPDATE);
	}

	/**
	 * グループエントリーからID URIを取得.
	 * @param groupEntry グループエントリー
	 * @return ID URI
	 */
	public static String getGroupChildIdUri(EntryBase groupEntry) {
		return TaggingEntryUtil.getUriById(groupEntry.id);
	}

	/**
	 * グループエントリーからグループを取得.
	 * 指定されたエントリーのID URIの親階層を返却
	 * @param groupEntry グループエントリー
	 * @return グループ
	 */
	public static String getGroupUri(EntryBase groupEntry) {
		String childIdUri = TaggingEntryUtil.getUriById(groupEntry.id);
		return TaggingEntryUtil.removeLastSlash(TaggingEntryUtil.getParentUri(childIdUri));
	}
	
	/**
	 * グループ情報を格納する親キーを取得.
	 *   /_user/{uid}/group
	 * @param uid UID
	 * @return グループ情報を格納する親キー
	 */
	public static String getGroupUriByUid(String uid) {
		UserManager userManager = TaggingEnvUtil.getUserManager();
		return userManager.getGroupUriByUid(uid);
	}

	/**
	 * グループエントリーからUIDを取得.
	 * 指定されたエントリーのID URIのselfidを返却
	 * @param groupEntry グループエントリー
	 * @return UID
	 */
	public static String getUidByGroup(EntryBase groupEntry) {
		String childIdUri = TaggingEntryUtil.getUriById(groupEntry.id);
		return TaggingEntryUtil.getSelfidUri(childIdUri);
	}

	/**
	 * ACL対象がグループかどうか判定
	 * @param aclUser ACL対象
	 * @return ACL対象がグループの場合true
	 */
	public static boolean isGroup(String aclUser) {
		if (aclUser != null && aclUser.startsWith("/")) {
			return true;
		}
		return false;
	}

	/**
	 * グループメンバーのUIDリストを取得します.
	 * @param groupParentUri グループ
	 * @param systemContext SystemContext
	 * @return グループメンバーのUIDリスト
	 */
	public static List<String> getGroupMemberUids(String groupParentUri, SystemContext systemContext)
	throws IOException, TaggingException {
		List<String> uids = new ArrayList<String>();
		String cursorStr = null;
		do {
			FeedBase feed = systemContext.getFeed(groupParentUri, true);
			cursorStr = TaggingEntryUtil.getCursorFromFeed(feed);
			if (TaggingEntryUtil.isExistData(feed)) {
				for (EntryBase groupEntry : feed.entry) {
					boolean isValid = isValidGroup(groupEntry, systemContext);
					if (isValid) {
						String uid = getUidByGroup(groupEntry);
						uids.add(uid);
					}
				}
			}
		} while (!StringUtils.isBlank(cursorStr));
		
		if (uids.isEmpty()) {
			return null;
		}
		return uids;
	}
	
	/**
	 * グループエントリーに自身のグループエイリアスがあるかどうか
	 * @param groupEntry 
	 * @param uid
	 * @return グループエントリーに自身のグループエイリアスがある場合true
	 */
	private static boolean existGroupAlias(EntryBase groupEntry, String uid) {
		if (groupEntry == null || StringUtils.isBlank(uid)) {
			return false;
		}
		List<String> aliases = groupEntry.getAlternate();
		if (aliases == null) {
			return false;
		}
		
		// /_user/{uid}/group
		String groupParentUri = TaggingEntryUtil.editSlash(getGroupUriByUid(uid));
		for (String alias : aliases) {
			String parentAlias = TaggingEntryUtil.getParentUri(alias);
			if (groupParentUri.equals(parentAlias)) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * グループ管理グループを取得
	 * @param groupName グループ管理グループ名
	 * @return /_group/$groupadmin_{グループ名}
	 */
	public static String getGroupadminGroup(String groupName) {
		return GroupConst.URI_GROUP_GROUPADMIN_PREFIX + StringUtils.null2blank(groupName);
	}
	
	/**
	 * グループ管理グループのグループ名部分を取得.
	 * @param groupName グループ管理グループ名
	 * @return $groupadmin_{グループ名}
	 */
	public static String getGroupadminGroupName(String groupName) {
		return (getGroupadminGroup(groupName)).substring(GroupConst.URI_GROUP_SLASH_LEN);
	}
	
	/**
	 * グループ管理対象URIを取得.
	 *   /_group/{グループ管理グループ名}
	 * @param groupName グループ管理グループ名
	 * @return グループ管理対象URI
	 */
	public static String getGroupUri(String groupName) {
		StringBuilder sb = new StringBuilder();
		sb.append(GroupConst.URI_GROUP_SLASH);
		sb.append(groupName);
		return sb.toString();
	}
	
	/**
	 * グループ参加URIを取得.
	 *   /_group/{グループ管理グループ名}/{UID}
	 * @param groupName グループ管理グループ名
	 * @param uid UID
	 * @return グループ参加URI
	 */
	public static String getGroupUidUri(String groupName, String uid) {
		StringBuilder sb = new StringBuilder();
		sb.append(getGroupUri(groupName));
		sb.append("/");
		sb.append(uid);
		return sb.toString();
	}
	
	/**
	 * ユーザ管理者かどうかチェックする
	 * @param auth 認証情報
	 * @throws PermissionException ユーザ管理者でない場合
	 */
	public static void checkUseradmin(ReflexAuthentication auth) 
	throws IOException, TaggingException {
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAuthedGroup(auth, Constants.URI_GROUP_USERADMIN);
	}

	/**
	 * ユーザ管理者またはグループ管理者かどうかチェックする
	 * グループ管理者かどうかの種類は問わない。(後続処理でACLチェックで判定される想定)
	 * @param auth 認証情報
	 * @throws PermissionException ユーザ管理者でなく、グループ管理者でもない場合
	 */
	public static void checkUseradminOrGroupadmin(ReflexAuthentication auth) 
	throws IOException, TaggingException {
		try {
			// ユーザ管理者かどうか
			checkUseradmin(auth);
		} catch (PermissionException e) {
			// グループ管理者かどうか (グループの種類は問わない)
			AclBlogic aclBlogic = new AclBlogic();
			aclBlogic.checkAuthedGroup(auth, 
					GroupConst.URI_GROUP_GROUPADMIN_PREFIX + Constants.GROUP_WILDCARD);
		}
	}
	
	/**
	 * グループキーチェック
	 * @param group グループキー
	 */
	public static void checkGroupUri(String group) {
		CheckUtil.checkNotNull(group, "group");
		CheckUtil.checkUri(group, "group");
		if (TaggingEntryUtil.isTop(group)) {	// ルートキーはエラー
			throw new IllegalParameterException("Invalid group. " + group);
		}
	}
	
	/**
	 * グループのselfidチェック
	 * グループキーについては、事前にグループキーチェック(checkGroupUri)が実行されていることを想定.
	 * selfidが指定されていなければ、グループキーの一番下の階層の値を返却する。
	 * @param selfid selfid
	 * @param group グループキー
	 * @return selfid。selfidが指定されていなければ、グループキーの一番下の階層の値を返却する。
	 */
	public static String checkGroupSelfid(String selfid, String group) {
		String retSelfid = null;
		if (StringUtils.isBlank(selfid)) {
			retSelfid = TaggingEntryUtil.getSelfidUri(group);
		} else {
			if (selfid.indexOf("/") > -1) {	// selfidにスラッシュ(/)が含まれていればエラー
				throw new IllegalParameterException("Invalid selfid. " + selfid);
			}
			retSelfid = selfid;
		}
		return retSelfid;
	}

}
