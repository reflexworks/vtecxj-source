package jp.reflexworks.taggingservice.auth;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.Contributor;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.blogic.DatastoreBlogic;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.AuthenticationException;
import jp.reflexworks.taggingservice.exception.EntryDuplicatedException;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.def.UserManagerDefault;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.MessageUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.NumberingUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * ユーザ管理クラス.
 */
public class TaggingUserManager extends UserManagerDefault {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * ユーザ初期設定に共通して必要なURIリスト(Entry検索).
	 * @param uid UID
	 * @return URIリスト
	 */
	@Override
	public List<String> getUserSettingEntryUris(String uid) {
		List<String> uris = new ArrayList<>();
		// 上位クラスより
		List<String> list = super.getUserSettingEntryUris(uid);
		if (list != null) {
			uris.addAll(list);
		}
		// ２段階認証の公開鍵
		uris.add(getUserTotpUriByUid(uid));
		// 信頼できる端末に指定する値
		uris.add(getUserTrustedDeviceUriByUid(uid));

		return uris;
	}

	/**
	 * ユーザ初期設定に共通して必要なURIリスト(Feed検索).
	 * @param uid UID
	 * @return Feed検索用URIリスト
	 */
	@Override
	public List<String> getUserSettingFeedUris(String uid) {
		return super.getUserSettingFeedUris(uid);
	}

	/**
	 * UIDから２段階認証の公開鍵を格納するエントリーを取得します.
	 * @param uid UID
	 * @param useCache キャッシュを使用する場合true
	 * @param reflexContext ReflexContext
	 * @return ２段階認証の公開鍵を格納するエントリー
	 */
	public EntryBase getUserTotpEntryByUid(String uid, boolean useCache, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String uri = getUserTotpUriByUid(uid);
		return reflexContext.getEntry(uri, useCache);
	}

	/**
	 * UIDから信頼できる端末に設定する値を格納するエントリーを取得します.
	 * @param uid UID
	 * @param useCache キャッシュを使用する場合true
	 * @param reflexContext ReflexContext
	 * @return 信頼できる端末に設定する値を格納するエントリー
	 */
	public EntryBase getUserTrustedDeviceEntryByUid(String uid, boolean useCache,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		String uri = getUserTrustedDeviceUriByUid(uid);
		return reflexContext.getEntry(uri, useCache);
	}

	/**
	 * ２段階認証の公開鍵を格納するEntryのキーを取得します.
	 * <p>
	 * ２段階認証の公開鍵を格納するEntryのキーは「/_user/{uid}/totp」です。
	 * </p>
	 * @param uid ユーザ番号
	 * @return ２段階認証の公開鍵を格納するEntryを格納するキー
	 */
	public String getUserTotpUriByUid(String uid) {
		StringBuilder sb = new StringBuilder();
		sb.append(getUserTopUriByUid(uid));
		sb.append(AuthenticatorConst.URI_TOTP);
		return sb.toString();
	}

	/**
	 * ２段階認証の公開鍵を仮登録するEntryのキーを取得します.
	 * <p>
	 * ２段階認証の公開鍵を仮登録するEntryのキーは「/_user/{uid}/totp_temp」です。
	 * </p>
	 * @param uid ユーザ番号
	 * @return ２段階認証の公開鍵を仮登録するEntryを格納するキー
	 */
	public String getUserTotpTempUriByUid(String uid) {
		StringBuilder sb = new StringBuilder();
		sb.append(getUserTopUriByUid(uid));
		sb.append(AuthenticatorConst.URI_TOTP_TEMP);
		return sb.toString();
	}

	/**
	 * 信頼できる端末に設定する値を格納するEntryのキーを取得します.
	 * <p>
	 * 信頼できる端末に設定する値を格納するEntryのキーは「/_user/{uid}/trusted_device」です。
	 * </p>
	 * @param uid ユーザ番号
	 * @return 信頼できる端末に設定する値を格納するEntryを格納するキー
	 */
	public String getUserTrustedDeviceUriByUid(String uid) {
		StringBuilder sb = new StringBuilder();
		sb.append(getUserTopUriByUid(uid));
		sb.append(AuthenticatorConst.URI_TRUSTED_DEVICE);
		return sb.toString();
	}

	/**
	 * UIDから２段階認証の公開鍵を取得します.
	 * @param uid UID
	 * @param useCache キャッシュを使用する場合true
	 * @param reflexContext ReflexContext
	 * @return TOTPの公開鍵
	 */
	public String getUserTotpSecretByUid(String uid, boolean useCache, ReflexContext reflexContext)
	throws IOException, TaggingException {
		EntryBase totpEntry = getUserTotpEntryByUid(uid, useCache, reflexContext);
		return getSecretByEntry(totpEntry);
	}

	/**
	 * UIDから信頼できる端末に設定する値を取得します.
	 * @param uid UID
	 * @param useCache キャッシュを使用する場合true
	 * @param reflexContext ReflexContext
	 * @return 信頼できる端末に設定する値
	 */
	public String getUserTDIDSecretByUid(String uid, boolean useCache, ReflexContext reflexContext)
	throws IOException, TaggingException {
		EntryBase trustedDeviceEntry = getUserTrustedDeviceEntryByUid(uid, useCache, reflexContext);
		return getSecretByEntry(trustedDeviceEntry);
	}

	/**
	 * Entryのcontributor.uriからsecretの値を取得.
	 * @param entry Entry
	 * @return secret
	 */
	public String getSecretByEntry(EntryBase entry)
	throws IOException, TaggingException {
		if (entry != null && entry.contributor != null) {
			for (Contributor contributor : entry.contributor) {
				if (contributor.uri != null && contributor.uri.startsWith(
						AuthenticatorConst.URN_PREFIX_SECRET)) {
					return contributor.uri.substring(AuthenticatorConst.URN_PREFIX_SECRET_LEN);
				}
			}
		}
		return null;
	}

	/**
	 * ２段階認証(TOTP)登録.
	 * @param req リクエスト
	 * @param reflexContext ReflexContext
	 * @return ２段階認証のための情報
	 */
	@Override
	public FeedBase createTotp(ReflexRequest req, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// すでに/_user/{UID}/totpエントリーが存在する場合はエラー。
		TaggingAuthentication auth = (TaggingAuthentication)req.getAuth();
		String uid = auth.getUid();
		EntryBase totpSecretEntry = getUserTotpEntryByUid(uid, true, reflexContext);
		if (totpSecretEntry != null) {
			AuthenticationException ae = new AuthenticationException();
			ae.setSubMessage("Two-factor authentication already registered. uid=" + uid);
			throw ae;
		}
		// リクエストデータにFeedが設定されていれば本登録、そうでなければ仮登録とみなす。
		FeedBase reqFeed = null;
		try {
			reqFeed = req.getFeed();
		} catch (ClassNotFoundException | DataFormatException e) {
			throw new IOException(e);
		}
		if (reqFeed == null) {
			// 仮登録
			return createTotpTemp(req, reflexContext);
		} else {
			// 本登録
			return createTotpMain(req, reqFeed, reflexContext);
		}
	}

	/**
	 * ２段階認証の仮登録.
	 * @param req リクエスト
	 * @param reflexContext ReflexContext
	 * @return QRコードURLを設定したFeed
	 */
	private FeedBase createTotpTemp(ReflexRequest req, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = req.getServiceName();
		TaggingAuthentication auth = (TaggingAuthentication)req.getAuth();
		String uid = auth.getUid();
		// 公開鍵を発行する。(GoogleAuthを使用)
		String totpSecret = TOTPUtil.createTotpSecret();
		// 公開鍵を仮登録する。(エントリーが存在する場合は上書きする。)
		// キー : /_user/{UID}/totp_temp
		String uri = getUserTotpTempUriByUid(uid);
		EntryBase totpTempEntry = createUserSecretEntry(uri, totpSecret, serviceName);
		reflexContext.put(totpTempEntry);

		// QRコードURLを返却する
		String url = createQRcodeUrl(req, totpSecret, auth.getAccount(), serviceName);
		FeedBase retFeed = MessageUtil.createMessageFeed(url, serviceName);
		return retFeed;
	}

	/**
	 * ２段階認証の本登録.
	 * @param req リクエスト
	 * @param reqFeed リクエストデータ
	 * @param reflexContext ReflexContext
	 * @return
	 */
	private FeedBase createTotpMain(ReflexRequest req, FeedBase reqFeed, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = req.getServiceName();
		TaggingAuthentication auth = (TaggingAuthentication)req.getAuth();
		String uid = auth.getUid();
		String onetimePasswordStr = reqFeed.title;
		int onetimePassword = -1;
		// ワンタイムパスワードが指定されていなければエラー
		if (StringUtils.isBlank(onetimePasswordStr)) {
			AuthenticationException ae = new AuthenticationException();
			ae.setSubMessage("one-time password is required. uid=" + uid);
			throw ae;
		}
		if (!StringUtils.isInteger(onetimePasswordStr)) {
			AuthenticationException ae = new AuthenticationException();
			ae.setSubMessage("one-time password is not integer. uid=" + uid);
			throw ae;
		}
		onetimePassword = Integer.parseInt(onetimePasswordStr);
		if (onetimePassword <= 0) {
			AuthenticationException ae = new AuthenticationException();
			ae.setSubMessage("one-time password is not positive number. uid=" + uid);
			throw ae;
		}

		// /_user/{UID}/totp_tempエントリーを検索する。存在しなければエラー。公開鍵が登録されていなければエラー。
		EntryBase totpTempEntry = reflexContext.getEntry(getUserTotpTempUriByUid(uid));
		if (totpTempEntry == null) {
			AuthenticationException ae = new AuthenticationException();
			ae.setSubMessage("TOTP temporary entry does not exist. uid=" + uid);
			throw ae;
		}
		String totpSecret = getSecretByEntry(totpTempEntry);
		if (StringUtils.isBlank(totpSecret)) {
			AuthenticationException ae = new AuthenticationException();
			ae.setSubMessage("TOTP temporary secret does not exist. uid=" + uid);
			throw ae;
		}

		// 公開鍵を使用しワンタイムパスワードを検証する。(GoogleAuthを使用)検証NGの場合エラー。
		boolean isCorrect = TOTPUtil.verifyOnetimePassword(totpSecret, onetimePassword);
		if (!isCorrect) {
			AuthenticationException ae = new AuthenticationException();
			ae.setSubMessage("one-time password is not correct. uid=" + uid);
			throw ae;
		}

		// 検証OKの場合、/_user/{UID}/totp_tempエントリーの内容を/_user/{UID}/totpとして登録する。
		// 同トランザクションで/_user/{UID}/totp_tempを削除する。
		FeedBase feed = TaggingEntryUtil.createFeed(serviceName);
		String uri = getUserTotpUriByUid(uid);
		EntryBase totpEntry = createUserSecretEntry(uri, totpSecret, serviceName);
		feed.addEntry(totpEntry);
		DatastoreBlogic datastoreBlogic = new DatastoreBlogic();
		datastoreBlogic.editEntryToDelete(totpTempEntry);
		feed.addEntry(totpTempEntry);
		reflexContext.put(feed);

		FeedBase retFeed = MessageUtil.createMessageFeed(AuthenticatorConst.MSG_CREATE_TOTP,
				serviceName);
		return retFeed;
	}

	/**
	 * QRコードサイズを取得.
	 * パラメータに設定があり、値が正の数であれば返却する。
	 * そうでなければデフォルト値を返す。
	 * @param pChs パラメータに設定されたサイズ
	 * @return QRコードサイズ
	 */
	private int getChs(String pChs) {
		int chs = 0;
		if (StringUtils.isInteger(pChs)) {
			int tmpChs = Integer.parseInt(pChs);
			if (tmpChs > 0) {
				chs = tmpChs;
			}
		}
		if (chs <= 0) {
			chs = TaggingEnvUtil.getSystemPropInt(AuthenticatorConst.PROP_TOTP_QRCODE_CHS,
					AuthenticatorConst.TOTP_QRCODE_CHS_DEFAULT);
		}
		return chs;
	}

	/**
	 * TOTP公開鍵、または信頼できる端末に設定する値を保持するエントリーを生成.
	 * @param uri URI ("/_user/{UID}/totp", "/_user/{UID}/totp_temp" or "/_user/{UID}/trusted_device")
	 * @param secret secretに設定する値
	 * @param serviceName サービス名
	 * @return エントリー
	 */
	private EntryBase createUserSecretEntry(String uri, String secret, String serviceName) {
		EntryBase entry = TaggingEntryUtil.createEntry(serviceName);
		entry.setMyUri(uri);
		Contributor contributor = new Contributor();
		contributor.uri = AuthenticatorConst.URN_PREFIX_SECRET + secret;
		entry.addContributor(contributor);
		return entry;
	}

	/**
	 * QRコードURLを生成.
	 * @param req リクエスト
	 * @param totpSecret 公開鍵
	 * @param account アカウント
	 * @param serviceName サービス名
	 * @return QRコードURL
	 */
	private String createQRcodeUrl(ReflexRequest req, String totpSecret, String account,
			String serviceName)
	throws IOException {
		// QRコードURLを返却する
		int chs = getChs(req.getRequestType().getOption(AuthenticatorConst.PARAM_CHS));
		return TOTPUtil.getTotpQRcodeUrl(totpSecret, account, chs, serviceName);
	}

	/**
	 * ２段階認証(TOTP)削除.
	 * @param account ２段階認証削除アカウント
	 * @param reflexContext ReflexContext
	 * @return ２段階認証削除情報
	 */
	@Override
	public FeedBase deleteTotp(String account, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		TaggingAuthentication auth = (TaggingAuthentication)reflexContext.getAuth();
		String uid = null;
		ReflexContext tmpReflexContext = null;
		if (account.equals(auth.getAccount())) {
			// 一般ユーザの場合、自分のUID。
			uid = auth.getUid();
			tmpReflexContext = reflexContext;
		} else {
			// サービス管理者の場合、アカウントからUIDを取得。
			SystemContext systemContext = new SystemContext(auth, reflexContext.getRequestInfo(),
					reflexContext.getConnectionInfo());
			uid = getUidByAccount(account, systemContext);
			if (StringUtils.isBlank(uid)) {
				throw new IllegalParameterException("The user does not exist. " + account);
			}
			tmpReflexContext = systemContext;
		}

		// /_user/{UID}/totpを削除する。
		String totpUri = getUserTotpUriByUid(uid);
		tmpReflexContext.delete(totpUri);

		FeedBase retFeed = MessageUtil.createMessageFeed(AuthenticatorConst.MSG_DELETE_TOTP,
				serviceName);
		return retFeed;
	}

	/**
	 * ２段階認証(TOTP)参照.
	 * @param auth 認証情報
	 * @param reflexContext ReflexContext
	 * @return ２段階認証情報
	 */
	@Override
	public FeedBase getTotp(ReflexRequest req, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		TaggingAuthentication auth = (TaggingAuthentication)req.getAuth();
		String uid = auth.getUid();
		String totpSecret = getUserTotpSecretByUid(uid, true, reflexContext);
		if (StringUtils.isBlank(totpSecret)) {
			AuthenticationException ae = new AuthenticationException();
			ae.setSubMessage("Two-factor authentication does not registed. uid=" + uid);
			throw ae;
		}

		// QRコードURLを返却する
		String url = createQRcodeUrl(req, totpSecret, auth.getAccount(), serviceName);
		FeedBase retFeed = MessageUtil.createMessageFeed(url, serviceName);
		return retFeed;
	}

	/**
	 * 信頼できる端末に指定する値(TDID)を生成.
	 * /_user/{UID}/trusted_device エントリーを登録する。
	 * 登録済みの場合、現在のエントリーを取得し、TDIDを返却する。
	 * @param uid UID
	 * @param reflexContext ReflexContext
	 * @return 信頼できる端末に指定する値(TDID)
	 */
	String createUserTDIDSecretByUid(String uid, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String uri = getUserTrustedDeviceUriByUid(uid);
		String tdid = TOTPUtil.createTotpSecret();
		EntryBase trustedDeviceEntry = createUserSecretEntry(uri, tdid, serviceName);
		try {
			reflexContext.post(trustedDeviceEntry);
		} catch (EntryDuplicatedException e) {
			if (logger.isDebugEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(reflexContext.getRequestInfo()));
				sb.append("[createUserTDIDSecretByUid] EntryDuplicatedException: ");
				sb.append(e.getMessage());
				logger.debug(sb.toString());
			}
			trustedDeviceEntry = reflexContext.getEntry(uri);
			String tmpTdid = getSecretByEntry(trustedDeviceEntry);
			if (StringUtils.isBlank(tmpTdid)) {
				if (logger.isDebugEnabled()) {
					StringBuilder sb = new StringBuilder();
					sb.append(LogUtil.getRequestInfoStr(reflexContext.getRequestInfo()));
					sb.append("[createUserTDIDSecretByUid] tmpTdid is null. uid=");
					sb.append(uid);
					logger.debug(sb.toString());
				}
				throw e;
			} else {
				tdid = tmpTdid;
			}
		}
		return tdid;
	}

	/**
	 * 信頼できる端末にセットする値(TDID)の更新.
	 * @param auth 認証情報
	 * @param reflexContext ReflexContext
	 * @return 信頼できる端末にセットする値(TDID)の更新情報
	 */
	@Override
	public FeedBase changeTdid(ReflexAuthentication auth, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String uid = auth.getUid();
		String uri = getUserTrustedDeviceUriByUid(uid);
		String tdid = createTdidSecret();
		EntryBase trustedDeviceEntry = createUserSecretEntry(uri, tdid, serviceName);
		reflexContext.put(trustedDeviceEntry);

		FeedBase retFeed = MessageUtil.createMessageFeed(AuthenticatorConst.MSG_CHANGE_TDID,
				serviceName);
		return retFeed;
	}

	/**
	 * 信頼できる端末に設定する値(TDID)を生成
	 * @return TDID
	 */
	private String createTdidSecret() {
		int secretLen = TaggingEnvUtil.getSystemPropInt(AuthenticatorConst.PROP_TDID_SECRET_LENGTH,
				AuthenticatorConst.TDID_SECRET_LENGTH_DEFAULT);
		return NumberingUtil.randomString(secretLen);
	}

}
