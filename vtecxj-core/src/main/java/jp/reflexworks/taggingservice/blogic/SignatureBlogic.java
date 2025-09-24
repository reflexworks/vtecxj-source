package jp.reflexworks.taggingservice.blogic;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.api.EntryUtil;
import jp.reflexworks.atom.entry.Contributor;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.entry.Link;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.NoExistingEntryException;
import jp.reflexworks.taggingservice.exception.OptimisticLockingException;
import jp.reflexworks.taggingservice.exception.PermissionException;
import jp.reflexworks.taggingservice.exception.SignatureInvalidException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.UserManager;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.AuthenticationUtil;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * 署名処理.
 */
public class SignatureBlogic {

	/** ハッシュ関数 */
	public static final String HASH_ALGORITHM = Constants.HASH_ALGORITHM;
	/** 削除フラグ */
	public static final String DELETED = "-";
	/** 署名検証のACLアクション */
	public static final String ACTION_CHECK = Constants.ACL_TYPE_RETRIEVE;

	private static final int URN_PREFIX_USERSECRET_LEN =
			Constants.URN_PREFIX_USERSECRET.length();

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 署名設定
	 * @param uri 署名対象URI
	 * @param revision リビジョン
	 * @param reflexContext ReflexContext
	 */
	public EntryBase put(String uri, Integer revision, ReflexContext reflexContext)
	throws IOException, TaggingException {

		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();

		// URI入力チェック
		CheckUtil.checkUri(uri);

		// 署名ACLチェック
		checkSignatureAcl(uri, auth, requestInfo, connectionInfo);

		EntryBase entry = reflexContext.getEntry(uri);
		if (entry == null) {
			throw new NoExistingEntryException();
		}

		// リビジョンチェック
		checkRevision(entry, revision);

		// 署名
		sign(uri, entry, reflexContext);
		
		return reflexContext.put(entry);
	}

	/**
	 * 署名設定
	 * @param feed 署名対象
	 * @param reflexContext ReflexContext
	 */
	public FeedBase put(FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		
		// 入力チェック、URI重複チェック
		CheckUtil.checkFeed(feed, false);

		List<EntryBase> updEntries = new ArrayList<>();
		for (EntryBase entry : feed.entry) {
			String uri = entry.getMyUri();
			// URI入力チェック
			CheckUtil.checkUri(uri);
			// 署名ACLチェック
			checkSignatureAcl(uri, auth, requestInfo, connectionInfo);
			EntryBase dsEntry = reflexContext.getEntry(uri);

			// リビジョンチェック
			Integer revision = TaggingEntryUtil.getRevisionByIdStrict(entry.id);
			checkRevision(dsEntry, revision);

			if (dsEntry != null) {
				// Do nothing.
			} else {
				// 登録
				dsEntry = reflexContext.post(entry);
			}
			updEntries.add(dsEntry);
		}
		
		// 署名
		for (EntryBase updEntry : updEntries) {
			sign(updEntry.getMyUri(), updEntry, reflexContext);
		}
		FeedBase updFeed = TaggingEntryUtil.createFeed(serviceName);
		updFeed.entry = updEntries;
		return reflexContext.put(updFeed);
	}

	/**
	 * 署名削除
	 * @param uri 署名削除対象URI
	 * @param revision リビジョン
	 * @param reflexContext ReflexContext
	 */
	public void delete(String uri, Integer revision, ReflexContext reflexContext)
	throws IOException, TaggingException {
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();

		// URI入力チェック
		CheckUtil.checkUri(uri);

		// 署名ACLチェック
		checkSignatureAcl(uri, auth, requestInfo, connectionInfo);

		EntryBase entry = reflexContext.getEntry(uri);
		if (entry == null) {
			throw new NoExistingEntryException();
		}

		// リビジョンチェック
		checkRevision(entry, revision);

		// 署名
		unsign(uri, entry);

		// ACLチェック済みのため、更新時のACLチェックを行わない。
		SystemContext systemContext = new SystemContext(auth,
				requestInfo, connectionInfo);
		systemContext.put(entry);
	}

	/**
	 * 署名検証
	 * @param uri 署名検証URI
	 * @param reflexContext ReflexContext
	 * @return 署名が正しい場合true、間違っている場合false
	 */
	public boolean check(String uri, ReflexContext reflexContext)
	throws IOException, TaggingException {
		EntryBase entry = reflexContext.getEntry(uri, true);
		if (entry == null) {
			throw new NoExistingEntryException();
		}

		// エントリーそのものに参照権限があるかどうかチェック
		AclBlogic aclBlogic = new AclBlogic();
		ReflexAuthentication auth = reflexContext.getAuth();
		String serviceName = reflexContext.getServiceName();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();

		boolean hasAcl = false;

		// id uri
		String idUri = TaggingEntryUtil.getUriById(entry.id);
		try {
			aclBlogic.checkAcl(idUri, ACTION_CHECK, auth, requestInfo, connectionInfo);
			hasAcl = true;
		} catch (PermissionException e) {
			// alias
			List<String> aliases = entry.getAlternate();
			if (aliases != null) {
				for (String alias : aliases) {
					try {
						aclBlogic.checkAcl(alias, ACTION_CHECK, auth, requestInfo, connectionInfo);
						hasAcl = true;
						break;
					} catch (PermissionException ee) {
						// Do nothing.
					}
				}
			}
		}
		if (!hasAcl) {
			throw new PermissionException();
		}

		// 署名検証
		return isValidSignature(entry, uri, serviceName, requestInfo, connectionInfo);
	}

	/**
	 * 指定されたURIの署名について検証します.
	 * @param entry 対象エントリー
	 * @param uri 検証対象URI
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 署名がない場合、署名が無効の場合false
	 */
	public boolean isValidSignature(EntryBase entry, String uri,
			String serviceName, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		SystemContext systemContext = new SystemContext(serviceName, requestInfo, connectionInfo);
		return isValidSignature(entry, uri, systemContext);
	}

	/**
	 * 指定されたURIの署名について検証します.
	 * @param entry 対象エントリー
	 * @param uri 検証対象URI
	 * @param systemContext SystemContext
	 * @return 署名がない場合、署名が無効の場合false
	 */
	public boolean isValidSignature(EntryBase entry, String uri,
			SystemContext systemContext)
	throws IOException, TaggingException {
		// 署名取得
		String signature = getSignature(entry, uri);
		String revision = getRevisionFromSignature(signature);
		if (StringUtils.isBlank(signature) || StringUtils.isBlank(revision)) {
			return false;
		}
		// 署名からUIDを取得
		String uid = getUidFromSignature(signature);
		if (StringUtils.isBlank(uid)) {
			return false;
		}

		// 認証情報Entry取得
		UserManager userManager = TaggingEnvUtil.getUserManager();
		EntryBase userAuthEntry = userManager.getUserAuthEntryByUid(uid, systemContext);
		if (userAuthEntry == null) {
			return false;
		}

		// 署名検証
		String usersecret = getUsersecret(userAuthEntry.contributor);
		String published = entry.published;
		String tmpSignature = createSignature(usersecret, uri, revision, published, uid);
		if (!signature.equals(tmpSignature)) {
			return false;
		} else {
			return true;
		}
	}

	/**
	 * 署名設定
	 * @param uri 署名対象URI
	 * @param entry 署名対象Entry
	 * @param reflexContext ReflexContext
	 */
	public void sign(String uri, EntryBase entry, ReflexContext reflexContext)
	throws IOException, TaggingException {

		// sha256({usersecret} + {URI} + {revision} + {エントリーのpublished})

		ReflexAuthentication auth = reflexContext.getAuth();
		String uid = auth.getUid();

		// ユーザの認証エントリー(/_user/{uid}/auth)を取得
		UserManager userManager = TaggingEnvUtil.getUserManager();
		EntryBase userAuthEntry = userManager.getUserAuthEntryByUid(uid,
				reflexContext);

		// usersecretを取得
		String usersecret = getUsersecret(userAuthEntry);
		if (StringUtils.isBlank(usersecret)) {
			throw new SignatureInvalidException("Usersecret is missing.");
		}

		// <link rel="{self | alternate}" href="{URI}" title="{revision},{署名}" />
		String revision = String.valueOf(TaggingEntryUtil.getRevisionById(entry.id));
		String published = entry.published;

		String signature = createSignature(usersecret, uri, revision, published, uid);
		if (signature == null) {
			if (logger.isTraceEnabled()) {
				logger.debug(LogUtil.getRequestInfoStr(reflexContext.getRequestInfo()) +
						"[sign] signature is null.");
			}
			return;
		}

		// 署名をlinkのtitle属性に設定
		setSignature(entry, uri, signature);
	}

	/**
	 * 署名を削除
	 * @param uri URI
	 * @param entry Entry
	 */
	private void unsign(String uri, EntryBase entry) {
		// 署名をlinkのtitle属性に設定
		setSignature(entry, uri, DELETED);	// "-"を指定
	}

	/**
	 * 署名を設定
	 * @param entry Entry
	 * @param uri URI
	 * @param signature 署名
	 */
	private void setSignature(EntryBase entry, String uri, String signature) {
		// 署名をlinkのtitle属性に設定
		String idUri = TaggingEntryUtil.getUriById(entry.id);
		if (uri.equals(idUri)) {
			for (Link link : entry.link) {
				if (Link.REL_SELF.equals(link._$rel)) {
					link._$title = signature;
					break;
				}
			}
		} else {
			for (Link link : entry.link) {
				if (Link.REL_ALTERNATE.equals(link._$rel) && uri.equals(link._$href)) {
					link._$title = signature;
					break;
				}
			}
		}
	}

	/**
	 * エントリーからusersecretを抽出.
	 * <contributor><uri>urn:virtual-tech.net:usersecret:{ランダム文字列}</uri></contributor>
	 * @param entry Entry
	 * @return usersecret
	 */
	private String getUsersecret(EntryBase entry) {
		if (entry != null) {
			return getUsersecret(entry.contributor);
		}
		return null;
	}

	/**
	 * エントリーのContributorからusersecretを抽出.
	 * <contributor><uri>urn:vte.cx:usersecret:{ランダム文字列}</uri></contributor>
	 * @param contributors Contributorリスト
	 * @return usersecret
	 */
	private String getUsersecret(List<Contributor> contributors) {
		if (contributors != null) {
			for (Contributor contributor : contributors) {
				if (contributor.uri != null && contributor.uri.startsWith(
						Constants.URN_PREFIX_USERSECRET)) {
					return contributor.uri.substring(URN_PREFIX_USERSECRET_LEN);
				}
			}
		}
		return null;
	}

	/**
	 * 署名生成
	 * sha256({usersecret} + {URI} + {revision} + {エントリーのpublished})
	 * @param usersecret usersecret
	 * @param uri URI
	 * @param revision revision
	 * @param published Entry作成日時
	 * @param uid UID
	 * @return {revision},{UID},{署名}
	 */
	private String createSignature(String usersecret, String uri, String revision,
			String published, String uid) {
		if (StringUtils.isBlank(usersecret) || StringUtils.isBlank(uri) ||
				StringUtils.isBlank(revision) || StringUtils.isBlank(published)) {
			return null;
		}

		String signature = null;
		try {
			byte[] usersecretB = usersecret.getBytes(Constants.ENCODING);
			byte[] uriB = uri.getBytes(Constants.ENCODING);
			byte[] revisionB = revision.getBytes(Constants.ENCODING);
			byte[] publishedB = published.getBytes(Constants.ENCODING);
			byte[] v = new byte[usersecretB.length + uriB.length + revisionB.length +
			                    publishedB.length];
			int start = 0;
			int len = usersecretB.length;
			System.arraycopy(usersecretB, 0, v, start, len);
			start += len;
			len = uriB.length;
			System.arraycopy(uriB, 0, v, start, len);
			start += len;
			len = revisionB.length;
			System.arraycopy(revisionB, 0, v, start, len);
			start += len;
			len = publishedB.length;
			System.arraycopy(publishedB, 0, v, start, len);

			MessageDigest md = MessageDigest.getInstance(HASH_ALGORITHM);
			md.update(v);
			byte[] digest = md.digest();

			signature = new String(Base64.encodeBase64(digest), Constants.ENCODING);

		} catch (NoSuchAlgorithmException e) {
			logger.warn(e.getMessage(), e);
		} catch (UnsupportedEncodingException e) {
			logger.warn(e.getMessage(), e);
		}

		if (!StringUtils.isBlank(signature)) {
			StringBuilder sb = new StringBuilder();
			sb.append(revision);
			sb.append(",");
			sb.append(uid);
			sb.append(",");
			sb.append(signature);
			return sb.toString();
		}
		return null;
	}

	/**
	 * 署名 ({revision},{uid},{署名}) からリビジョンを取得します.
	 * @param signature 署名
	 * @return リビジョン
	 */
	private String getRevisionFromSignature(String signature) {
		if (!StringUtils.isBlank(signature)) {
			int idx = signature.indexOf(",");
			if (idx > -1) {
				return signature.substring(0, idx);
			}
		}
		return null;
	}

	/**
	 * 署名 ({revision},{uid},{署名}) からUIDを取得します.
	 * @param signature 署名
	 * @return UID
	 */
	private String getUidFromSignature(String signature) {
		if (!StringUtils.isBlank(signature)) {
			int idx = signature.indexOf(",");
			int idx1 = idx + 1;
			if (idx > -1 && signature.length() > idx1) {
				int idx2 = signature.indexOf(",", idx1);
				if (idx2 > -1) {
					return signature.substring(idx1, idx2);
				}
			}
		}
		return null;
	}

	/**
	 * エントリーから指定されたURIの署名({revision},{uid},{署名})を取得します.
	 */
	private String getSignature(EntryBase entry, String uri) {
		return EntryUtil.getSignature(entry, uri);
	}

	/**
	 * 署名認可チェック
	 * @param uri URI
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	private void checkSignatureAcl(String uri, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		if (!hasSignatureAcl(uri, auth, requestInfo, connectionInfo)) {
			throw new PermissionException();
		}
	}

	/**
	 * リビジョンチェック
	 * _signatureパラメータの値にリビジョンが設定されている.
	 * @param entry Entry
	 * @param revision リビジョン
	 */
	private void checkRevision(EntryBase entry, Integer revision)
	throws OptimisticLockingException {
		if (revision == null) {
			return;
		}
		int entryRev = TaggingEntryUtil.getRevisionById(entry.id);
		if (entryRev != revision) {
			throw new OptimisticLockingException();
		}
	}

	/**
	 * 指定されたキーの署名ACLを持っているかどうかチェック.
	 *   ・指定のキーが/_user/{uid}で始まっている場合、そのuidのユーザのみ署名可能。
	 *   ・そうでない場合、ログインユーザにキーに対する更新権限(U)があれば署名可能。なければエラー。
	 * @param uri URI
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 指定されたキーの署名ACLを持っている場合true
	 */
	public boolean hasSignatureAcl(String uri, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// 認証情報がない場合、スーパーユーザの場合は認可エラー
		if (auth == null || auth.getUid() == null ||
				AuthenticationUtil.isSuperuser(auth)) {
			return false;
		}

		// ユーザトップエントリーのUIDと認証UIDのチェック
		UserManager userManager = TaggingEnvUtil.getUserManager();
		String uriUid = userManager.getUidByUri(uri);
		String myUid = auth.getUid();
		if (myUid.equals(uriUid)) {
			// 指定のキーが/_user/{uid}で始まっている場合で、そのuidのユーザ。
			return true;
		}

		// そうでない場合、ログインユーザにキーに対する更新権限(U)があれば署名可能。
		AclBlogic aclBlogic = new AclBlogic();
		try {
			aclBlogic.checkAcl(uri, Constants.ACL_TYPE_UPDATE, auth, requestInfo, connectionInfo);
			return true;
		} catch (PermissionException e) {
			return false;
		}
	}

}
