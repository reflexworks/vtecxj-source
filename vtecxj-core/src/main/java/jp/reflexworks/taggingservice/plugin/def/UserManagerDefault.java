package jp.reflexworks.taggingservice.plugin.def;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.api.AtomConst;
import jp.reflexworks.atom.entry.Contributor;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.entry.Link;
import jp.reflexworks.servlet.util.AuthTokenUtil;
import jp.reflexworks.servlet.util.WsseAuth;
import jp.reflexworks.servlet.util.WsseUtil;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.api.SettingConst;
import jp.reflexworks.taggingservice.blogic.AclBlogic;
import jp.reflexworks.taggingservice.blogic.AclConst;
import jp.reflexworks.taggingservice.blogic.LoginLogoutBlogic;
import jp.reflexworks.taggingservice.blogic.SecurityBlogic;
import jp.reflexworks.taggingservice.blogic.SecurityConst;
import jp.reflexworks.taggingservice.blogic.ServiceBlogic;
import jp.reflexworks.taggingservice.blogic.SignatureBlogic;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.AuthTimeoutException;
import jp.reflexworks.taggingservice.exception.AuthenticationException;
import jp.reflexworks.taggingservice.exception.EntryDuplicatedException;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.reflexworks.taggingservice.exception.NoExistingEntryException;
import jp.reflexworks.taggingservice.exception.PermissionException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.AccessTokenManager;
import jp.reflexworks.taggingservice.plugin.CacheManager;
import jp.reflexworks.taggingservice.plugin.EMailManager;
import jp.reflexworks.taggingservice.plugin.OAuthManager;
import jp.reflexworks.taggingservice.plugin.ServiceManager;
import jp.reflexworks.taggingservice.plugin.UserManager;
import jp.reflexworks.taggingservice.plugin.def.UserManagerDefaultConst.AdduserType;
import jp.reflexworks.taggingservice.plugin.def.UserManagerDefaultConst.UserAuthType;
import jp.reflexworks.taggingservice.service.TaggingServiceUtil;
import jp.reflexworks.taggingservice.sys.SystemAuthentication;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.taskqueue.TaskQueueUtil;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.ExceptionUtil;
import jp.reflexworks.taggingservice.util.GroupConst;
import jp.reflexworks.taggingservice.util.GroupUtil;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.reflexworks.taggingservice.util.UserUtil;
import jp.sourceforge.reflex.util.NumberingUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * ユーザ管理クラス.
 */
public class UserManagerDefault implements UserManager {

	/** ユーザフォルダ+スラッシュ */
	private static final String URI_USER_SLASH = TaggingEntryUtil.editSlash(Constants.URI_USER);
	/** ユーザフォルダ+スラッシュの文字列長 */
	private static final int URI_USER_SLASH_LEN = URI_USER_SLASH.length();

	/** ユーザ名・パスワード設定接頭辞の長さ */
	private static final int URN_PREFIX_AUTH_LEN = Constants.URN_PREFIX_AUTH.length();
	/** ユーザ名・パスワード設定接頭辞の長さ */
	private static final int URN_PREFIX_PASSRESET_TOKEN_LEN = UserManagerDefaultConst.URN_PREFIX_PASSRESET_TOKEN.length();
	/** ユーザ名・パスワード設定接頭辞の長さ */
	private static final int URN_PREFIX_OLDPHASH_LEN = UserManagerDefaultConst.URN_PREFIX_OLDPHASH.length();
	
	/** グループ管理グループの接頭辞の長さ */
	private static final int URI_GROUP_GROUPADMIN_PREFIX_LEN = GroupConst.URI_GROUP_GROUPADMIN_PREFIX.length();
	
	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 初期処理.
	 */
	public void init() {
		// ユーザ本登録時に登録するEntryを格納するMapをstatic変数に格納 -> しない
		// とりあえずユーザ本登録時にEntryを読むようにする。

	}

	/**
	 * シャットダウン処理.
	 */
	public void close() {
		// Do nothing.
	}

	/**
	 * ログインユーザのユーザ情報Entryを取得
	 * @param reflexContext ReflexContext
	 * @return ログインユーザのユーザ情報Entry
	 */
	public FeedBase whoami(ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		// 認証情報のチェック
		ReflexAuthentication auth = reflexContext.getAuth();
		if (auth == null || StringUtils.isBlank(auth.getUid())) {
			PermissionException pe = new PermissionException();
			pe.setSubMessage("Authentication is required.");
			throw pe;
		}
		String uri = getUserTopUriByUid(auth.getUid());
		EntryBase userTopEntry = reflexContext.getEntry(uri, true);
		if (userTopEntry != null) {
			return TaggingEntryUtil.createFeed(serviceName, userTopEntry);
		}
		return null;
	}

	/**
	 * アカウントからUIDを取得.
	 * @param account アカウント
	 * @param systemContext SystemContext
	 * @return UID
	 */
	public String getUidByAccount(String account, SystemContext systemContext)
	throws IOException, TaggingException {
		if (StringUtils.isBlank(account)) {
			if (logger.isDebugEnabled()) {
				logger.debug("[getUidByAccount] account is null. account = " + account);
			}
			return null;
		}
		EntryBase entry = getUserTopEntryByAccount(account, systemContext);
		if (entry != null) {
			return getUidByUri(entry.getMyUri());
		}
		if (logger.isDebugEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(systemContext.getRequestInfo()) +
					"[getUidByAccount] UserTopEntry is null. account = " + account);
		}
		return null;
	}

	/**
	 * UIDからアカウントを取得.
	 * @param uid UID
	 * @param reflexContext ReflexContext
	 * @return アカウント
	 */
	public String getAccountByUid(String uid, SystemContext systemContext)
	throws IOException, TaggingException {
		if (StringUtils.isBlank(uid)) {
			return null;
		}
		EntryBase entry = getUserTopEntryByUid(uid, systemContext);
		if (entry != null) {
			// アカウントはtitleに設定
			return entry.title;
		}
		return null;
	}

	/**
	 * アカウントからユーザトップエントリーを取得します.
	 * <p>
	 * ユーザトップエントリーのURIはUIDのため、ユーザ名を検索条件に
	 * ルートエントリー配下をフィード検索します.<br/>
	 * Feedキャッシュを使用します。
	 * </p>
	 * @param account アカウント
	 * @param reflexContext ReflexContext
	 * @return ユーザトップエントリー
	 */
	public EntryBase getUserTopEntryByAccount(String account,
			SystemContext systemContext)
	throws IOException, TaggingException {
		return getUserTopEntryByAccount(account, true, systemContext);
	}

	/**
	 * アカウントからユーザトップエントリーを取得します.
	 * <p>
	 * ユーザトップエントリーのURIはUIDのため、ユーザ名を検索条件に
	 * ルートエントリー配下をフィード検索します.
	 * </p>
	 * @param account アカウント
	 * @param useCache キャッシュを使用する場合true
	 * @param reflexContext ReflexContext
	 * @return ユーザトップエントリー
	 */
	public EntryBase getUserTopEntryByAccount(String account,
			boolean useCache, SystemContext systemContext)
	throws IOException, TaggingException {
		String uriAndQuery = editUserTopQueryString(account);

		FeedBase feed = systemContext.getFeed(uriAndQuery, useCache);
		if (feed != null && feed.entry != null && feed.entry.size() > 0) {
			return TaggingEntryUtil.getFirstEntry(feed);
		}
		return null;
	}

	/**
	 * UIDからユーザトップエントリーを取得します.
	 * キャッシュを使用します。
	 * @param uid UID
	 * @param reflexContext ReflexContext
	 * @return ユーザトップエントリー
	 */
	public EntryBase getUserTopEntryByUid(String uid, ReflexContext reflexContext)
	throws IOException, TaggingException {
		return getUserTopEntryByUid(uid, true, reflexContext);
	}

	/**
	 * UIDからユーザトップエントリーを取得します.
	 * @param uid UID
	 * @param useCache キャッシュを使用する場合true
	 * @param reflexContext ReflexContext
	 * @return ユーザトップエントリー
	 */
	public EntryBase getUserTopEntryByUid(String uid, boolean useCache,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		String uri = getUserTopUriByUid(uid);
		return reflexContext.getEntry(uri, useCache);
	}

	/**
	 * UIDからユーザ認証エントリーを取得します.
	 * キャッシュを使用します。
	 * @param uid UID
	 * @param reflexContext ReflexContext
	 * @return ユーザ認証エントリー
	 */
	public EntryBase getUserAuthEntryByUid(String uid, ReflexContext reflexContext)
	throws IOException, TaggingException {
		return getUserAuthEntryByUid(uid, true, reflexContext);
	}

	/**
	 * UIDからユーザ認証エントリーを取得します.
	 * @param uid UID
	 * @param useCache キャッシュを使用する場合true
	 * @param reflexContext ReflexContext
	 * @return ユーザ認証エントリー
	 */
	public EntryBase getUserAuthEntryByUid(String uid, boolean useCache,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		String uri = getUserAuthUriByUid(uid);
		return reflexContext.getEntry(uri, useCache);
	}

	/**
	 * ログインユーザのRXIDを生成
	 * @param reflexContext ReflexContext
	 * @return RXID
	 */
	public String createRXID(ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 認証情報のチェック
		ReflexAuthentication auth = reflexContext.getAuth();
		if (auth == null || StringUtils.isBlank(auth.getAccount())) {
			return null;
		}
		// SystemContextでアクセス
		SystemContext systemContext = new SystemContext(auth,
				reflexContext.getRequestInfo(), reflexContext.getConnectionInfo());
		return createRXIDByAccount(auth.getAccount(), systemContext);
	}

	/**
	 * 指定されたアカウントのRXIDを生成
	 * @param account アカウント
	 * @param reflexContext ReflexContext
	 * @return RXID
	 */
	public String createRXIDByAccount(String account, SystemContext systemContext)
	throws IOException, TaggingException {
		String serviceName = systemContext.getServiceName();
		// パスワード取得
		String password = getPasswordByAccount(account, systemContext);
		// APIKey取得
		ServiceBlogic serviceBlogic = new ServiceBlogic();
		String apiKey = serviceBlogic.getAPIKey(serviceName, systemContext.getRequestInfo(),
				systemContext.getConnectionInfo());

		// RXID生成
		if (!StringUtils.isBlank(account) && !StringUtils.isBlank(password) &&
				!StringUtils.isBlank(apiKey)) {
			return AuthTokenUtil.createRXIDString(account, password, serviceName, apiKey);
		}
		return null;
	}

	/**
	 * WSSE認証.
	 * @param wsseAuth WSSE・RXID認証情報
	 * @param req リクエスト (ログ用。null可。)
	 * @param reflexContext ReflexContext
	 * @throws AuthenticationException WSSE認証エラー
	 */
	public void checkWsse(WsseAuth wsseAuth, ReflexRequest req, SystemContext systemContext)
	throws IOException, TaggingException {
		String serviceName = systemContext.getServiceName();
		RequestInfo requestInfo = systemContext.getRequestInfo();
		ConnectionInfo connectionInfo = systemContext.getConnectionInfo();

		if (wsseAuth == null) {
			if (logger.isDebugEnabled()) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
						"[checkWsse] wsseAuth is null.");
			}
			return;
		}

		// createdのチェック
		if (!checkCreated(wsseAuth.created, systemContext)) {
			AuthTimeoutException ae = new AuthTimeoutException();
			StringBuilder msgBld = new StringBuilder();
			if (wsseAuth.isRxid) {
				msgBld.append("RXID-created is out of range. RXID=");
			} else {
				msgBld.append("WSSE-created is out of range. WSSE=");
			}
			msgBld.append(ExceptionUtil.getAuthErrorSubMessageValue(req, wsseAuth));
			ae.setSubMessage(msgBld.toString());
			throw ae;
		}

		// パスワードを取得
		String[] usernameAndService = AuthTokenUtil.getUsernameAndService(wsseAuth);
		if (usernameAndService == null) {
			if (logger.isDebugEnabled()) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
						"[checkWsse] usernameAndService is null.");
			}
			return;
		}

		String username = usernameAndService[0];
		String account = UserUtil.editAccount(username);
		String password = getPasswordByAccount(account, systemContext);
		if (password == null) {
			if (logger.isDebugEnabled()) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
						"[authenticate]password is null.");
			}
			AuthenticationException ae = new AuthenticationException();
			StringBuilder msgBld = new StringBuilder();
			if (wsseAuth.isRxid) {
				msgBld.append("RXID-user's password does not exist. RXID=");
			} else {
				msgBld.append("WSSE-user's password does not exist. WSSE=");
			}
			msgBld.append(ExceptionUtil.getAuthErrorSubMessageValue(req, wsseAuth));
			msgBld.append(" account=");
			msgBld.append(account);
			ae.setSubMessage(msgBld.toString());
			throw ae;
		}

		// APIKeyを取得
		ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
		String apiKey = serviceManager.getAPIKey(serviceName, requestInfo,
				connectionInfo);

		// WSSE認証
		WsseUtil wsseUtil = new WsseUtil();
		if (!wsseUtil.checkAuth(wsseAuth, password,
				apiKey)) {
			StringBuilder msgBld = new StringBuilder();
			if (wsseAuth.isRxid) {
				msgBld.append("RXID auth error. RXID=");
			} else {
				msgBld.append("WSSE auth error. WSSE=");
			}
			msgBld.append(ExceptionUtil.getAuthErrorSubMessageValue(req, wsseAuth));
			msgBld.append(" account=");
			msgBld.append(account);
			String msg = msgBld.toString();
			if (logger.isDebugEnabled()) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
						"[authenticate]" + msg);
			}
			AuthenticationException ae = new AuthenticationException();
			ae.setSubMessage(msg);
			throw ae;
		}
	}

	/**
	 * RXIDのcreated範囲チェック.
	 * @param created created
	 * @param reflexContext ReflexContext
	 * @return createdの範囲が正常な場合true
	 */
	private boolean checkCreated(String created, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		int beforeMinute = TaggingEnvUtil.getRxidMinute(serviceName);
		if (beforeMinute <= 0) {
			beforeMinute = UserManagerDefaultConst.CREATED_BEFORE_MINUTE;
		}
		WsseUtil wsseUtil = new WsseUtil();
		return wsseUtil.checkCreated(created, beforeMinute, UserManagerDefaultConst.CREATED_AFTER_MINUTE);
	}

	/**
	 * パスワードを取得
	 * @param account アカウント
	 * @param reflexContext ReflexContext
	 * @return パスワード
	 */
	public String getPasswordByAccount(String account, SystemContext systemContext)
	throws IOException, TaggingException {
		String uid = getUidByAccount(account, systemContext);
		return getPasswordByUid(uid, systemContext);
	}

	/**
	 * パスワードを取得
	 * @param uid UID
	 * @param systemContext SystemContext
	 * @return パスワード
	 */
	public String getPasswordByUid(String uid, SystemContext systemContext)
	throws IOException, TaggingException {
		if (!StringUtils.isBlank(uid)) {
			String uri = getUserAuthUriByUid(uid);
			EntryBase userAuthEntry = systemContext.getEntry(uri, true);
			return getPassword(userAuthEntry);
		}
		return null;
	}

	/**
	 * アカウントを取得.
	 * UIDを元にユーザトップエントリーを検索し、アカウントを取得する。
	 * @param uid UID
	 * @param reflexContext ReflexContext
	 * @return アカウント
	 */
	public String getAccountByUid(String uid, ReflexContext reflexContext)
	throws IOException, TaggingException {
		if (!StringUtils.isBlank(uid)) {
			String uri = getUserTopUriByUid(uid);
			EntryBase userTopEntry = reflexContext.getEntry(uri, true);
			if (userTopEntry != null) {
				return userTopEntry.title;
			}
		}
		return null;
	}

	/**
	 * ニックネームを取得.
	 * @param uid UID
	 * @param reflexContext ReflexContext
	 * @return ニックネーム
	 */
	public String getNicknameByUid(String uid, ReflexContext reflexContext)
	throws IOException, TaggingException {
		if (!StringUtils.isBlank(uid)) {
			String uri = getUserTopUriByUid(uid);
			EntryBase userTopEntry = reflexContext.getEntry(uri, true);
			return getNickname(userTopEntry);
		}
		return null;
	}

	/**
	 * メールアドレスを取得.
	 *  contributor の email タグに設定されている値を返却する。
	 * @param uid UID
	 * @param reflexContext ReflexContext
	 * @return メールアドレス
	 */
	public String getEmailByUid(String uid, ReflexContext reflexContext)
	throws IOException, TaggingException {
		if (!StringUtils.isBlank(uid)) {
			String uri = getUserTopUriByUid(uid);
			EntryBase userTopEntry = reflexContext.getEntry(uri, true);
			return getEmail(userTopEntry);
		}
		return null;
	}

	/**
	 * 仮パスワードを生成
	 * @param password パスワード
	 * @return 仮パスワード
	 */
	private String getTmpPassword(String password) {
		String tmpPassword = createPassword();
		if (!StringUtils.isBlank(password)) {
			tmpPassword = tmpPassword + password;
		}
		return tmpPassword;
	}

	/**
	 * パスワードとしてランダムな文字列を生成します
	 * @return 生成された文字列
	 */
	private String createPassword() {
		return UserUtil.createRandomString(UserManagerDefaultConst.PASSWORD_LEN);
	}

	/**
	 * ユーザ登録.
	 * @param feed ユーザ登録情報
	 * @param reflexContext ReflexContext
	 * @return ユーザのトップエントリー
	 */
	public EntryBase adduser(FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// この処理はリクエストからの呼び出しでないとエラーとする。
		// reCAPTCHAが設定されていない場合エラーとする(CSRF対応)。
		SecurityBlogic securityBlogic = new SecurityBlogic();
		securityBlogic.checkCaptcha(reflexContext.getRequest(), SecurityConst.CAPTCHA_ACTION_ADDUSER);

		// feedチェック
		checkUserAuthFeed(feed);

		String serviceName = reflexContext.getServiceName();
		SystemContext systemContext = new SystemContext(serviceName,
				reflexContext.getRequestInfo(), reflexContext.getConnectionInfo());

		// adduserはEntry1件のみ
		EntryBase entry = feed.entry.get(0);
		return adduserProc(entry, AdduserType.USER, null, systemContext);
	}

	/**
	 * 管理者によるユーザ登録.
	 * @param feed ユーザ登録情報
	 * @param reflexContext ReflexContext
	 * @return ユーザのトップエントリーリスト
	 */
	public FeedBase adduserByAdmin(FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// feedチェック
		checkUserAuthFeed(feed);

		String serviceName = reflexContext.getServiceName();
		SystemContext systemContext = new SystemContext(reflexContext.getAuth(),
				reflexContext.getRequestInfo(), reflexContext.getConnectionInfo());

		// adduser共通処理を実行
		List<EntryBase> retEntries = new ArrayList<EntryBase>();
		for (EntryBase entry : feed.entry) {
			EntryBase retEntry = adduserProc(entry, AdduserType.ADMIN, null, systemContext);
			retEntries.add(retEntry);
		}

		// ユーザトップエントリーリストを返却
		FeedBase retFeed = TaggingEntryUtil.createFeed(serviceName);
		retFeed.entry = retEntries;
		return retFeed;
	}

	/**
	 * グループ管理者によるユーザ登録.
	 * @param feed ユーザ登録情報
	 * @param groupName グループ名
	 * @param reflexContext ReflexContext
	 * @return ユーザのトップエントリーリスト
	 */
	public FeedBase adduserByGroupadmin(FeedBase feed, String groupName, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// グループ名チェック
		CheckUtil.checkNotNull(groupName, "group name");
		// feedチェック
		checkUserAuthFeed(feed);

		String serviceName = reflexContext.getServiceName();
		SystemContext systemContext = new SystemContext(reflexContext.getAuth(),
				reflexContext.getRequestInfo(), reflexContext.getConnectionInfo());

		// adduser共通処理を実行
		List<EntryBase> retEntries = new ArrayList<EntryBase>();
		for (EntryBase entry : feed.entry) {
			EntryBase retEntry = adduserProc(entry, AdduserType.GROUPADMIN, groupName, systemContext);
			retEntries.add(retEntry);
		}

		// ユーザトップエントリーリストを返却
		FeedBase retFeed = TaggingEntryUtil.createFeed(serviceName);
		retFeed.entry = retEntries;
		return retFeed;
	}

	/**
	 * 外部連携によるユーザ登録.
	 * ユーザ1件を登録する。
	 * @param account アカウント
	 * @param nickname ニックネーム
	 * @param feed ユーザ登録情報(任意)。キーの#はUIDに置き換える。
	 * @param reflexContext ReflexContext
	 * @return ユーザのトップエントリー
	 */
	public EntryBase adduserByLink(String account, String nickname, 
			FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// アカウント指定チェック
		CheckUtil.checkNotNull(account, "account");
		SystemContext systemContext = new SystemContext(reflexContext.getAuth(),
				reflexContext.getRequestInfo(), reflexContext.getConnectionInfo());
		return adduserInterimProc(account, null, null, nickname, null, AdduserType.LINK,
				feed, null, systemContext);
	}

	/**
	 * ユーザ登録処理.
	 * @param entry 登録ユーザ
	 * @param adduserType サービス管理者による登録の場合true
	 * @param groupName グループ管理者による登録の場合、グループ名
	 * @param systemContext SystemContext
	 * @return ユーザトップエントリーリスト
	 */
	private EntryBase adduserProc(EntryBase entry, AdduserType adduserType,
			String groupName, SystemContext systemContext)
	throws IOException, TaggingException {
		// メール設定チェック
		EntryBase mailEntry = null;
		if (!StringUtils.isBlank(entry.title) &&
				(!StringUtils.isBlank(entry.summary) || !StringUtils.isBlank(entry.getContentText()))) {
			mailEntry = entry;
		} else {
			if (adduserType.equals(AdduserType.ADMIN)) {
				mailEntry = systemContext.getEntry(
						Constants.URI_SETTINGS_ADDUSER_BYADMIN, true);
			} else if (adduserType.equals(AdduserType.USER)) {
				mailEntry = systemContext.getEntry(
						Constants.URI_SETTINGS_ADDUSER, true);
			} else if (adduserType.equals(AdduserType.GROUPADMIN)) {
				mailEntry = systemContext.getEntry(
						GroupConst.URI_SETTINGS_ADDUSER_BYGROUPADMIN, true);
			}
		}
		boolean isByAdmin = !adduserType.equals(AdduserType.USER);
		if (!isByAdmin && (mailEntry == null || StringUtils.isBlank(mailEntry.title) ||
				(StringUtils.isBlank(mailEntry.summary) && StringUtils.isBlank(entry.getContentText())))) {
			// adduserでメールの設定が無い場合はエラー
			throw new InvalidServiceSettingException("There is no mail setting. (adduser)");
		}

		// 指定されたメールアドレスを小文字に変換し、さらにアカウント利用可能文字以外を削除した値をユーザアカウントとする。
		String[] userInfo = checkUserAuthInfo(entry, UserAuthType.ADDUSER, isByAdmin, isByAdmin);
		String email = userInfo[0];
		String password = userInfo[1];
		String account = userInfo[2];
		String nickname = userInfo[3];

		CheckUtil.checkNotNull(account, "username");
		if (!isByAdmin) {
			// adduserの場合、email未入力はエラー
			CheckUtil.checkNotNull(email, "email");
		}

		// ユーザ仮登録処理
		return adduserInterim(account, password, email, nickname, mailEntry, adduserType,
				groupName, systemContext);
	}

	/**
	 * ユーザ仮登録処理.
	 * 管理者によるユーザ登録の場合、このメソッドで本登録まで行う。
	 * @param account アカウント
	 * @param password パスワード
	 * @param email メールアドレス
	 * @param nickname ニックネーム
	 * @param mailEntry 送信メール
	 * @param adduserType 管理ユーザによる登録かどうか。管理ユーザ登録の場合本登録まで行う。
	 * @param groupName グループ管理者によるユーザ登録の場合、ユーザ名
	 * @param systemContext SystemContext
	 * @return ユーザトップエントリー
	 */
	public EntryBase adduserInterim(String account, String password, String email,
			String nickname, EntryBase mailEntry, AdduserType adduserType,
			String groupName, SystemContext systemContext)
	throws IOException, TaggingException {
		return adduserInterimProc(account, password, email, nickname, mailEntry, adduserType,
				null, groupName, systemContext);
	}

	/**
	 * ユーザ仮登録処理.
	 * 管理者によるユーザ登録の場合、このメソッドで本登録まで行う。
	 * @param account アカウント
	 * @param password パスワード
	 * @param email メールアドレス
	 * @param nickname ニックネーム
	 * @param mailEntry 送信メール
	 * @param adduserType 管理ユーザによる登録かどうか。管理ユーザ登録の場合本登録まで行う。
	 * @param additionalFeed 追加のユーザ登録情報(任意)。キーの#はUIDに置き換える。
	 * @param groupName グループ管理者による登録の場合、グループ名。
	 * @param systemContext SystemContext
	 * @return ユーザトップエントリー
	 */
	private EntryBase adduserInterimProc(String account, String password, String email,
			String nickname, EntryBase mailEntry, AdduserType adduserType,
			FeedBase additionalFeed, String groupName, SystemContext systemContext)
	throws IOException, TaggingException {
		String serviceName = systemContext.getServiceName();
		boolean isByAdmin = !adduserType.equals(AdduserType.USER);
		boolean isLink = adduserType.equals(AdduserType.LINK);
		// 仮登録時はパスワードに余分な文字列を付ける。(本登録時に除去する。)
		if (!isByAdmin && !isLink) {
			password = getTmpPassword(password);
		}

		// ユーザアカウントからユーザトップエントリーを検索する。(自サービス内)
		//   GET /_user?title={ユーザアカウント}
		EntryBase userTopEntry = getUserTopEntryByAccount(account, false, systemContext);
		EntryBase userAuthEntry = null;
		String uid = null;
		SystemContext mySystemContext = null;
		RequestInfo requestInfo = systemContext.getRequestInfo();
		ConnectionInfo connectionInfo = systemContext.getConnectionInfo();
		boolean isGroupOnly = false;	// ユーザ登録済みで、グループ登録のみ行う場合true

		if (userTopEntry != null) {
			// ユーザトップエントリーが存在する場合、ユーザステータスをチェックする。
			//   ユーザステータスが"Interim"(仮登録)、"Cancelled"(退会)であれば処理を継続する。
			//   ユーザステータスが上記以外であれば登録済みエラー(ステータス409)を返却する。
			String userTopUri = userTopEntry.getMyUri();
			String userstatus = getUserstatus(userTopEntry);
			// UID取得
			uid = getUidByUri(userTopUri);
			if (!Constants.USERSTATUS_INTERIM.equals(userstatus) &&
					!Constants.USERSTATUS_CANCELLED.equals(userstatus)) {
				if ((Constants.USERSTATUS_ACTIVATED.equals(userstatus) ||
						Constants.USERSTATUS_REVOKED.equals(userstatus)) &&
						!StringUtils.isBlank(groupName)) {
					// 指定されたグループに参加しているかどうかチェック
					String groupParentUri = GroupUtil.getGroupUri(groupName);
					String groupIdUri = getGroupIdUriByUid(groupParentUri, uid);
					EntryBase myGroupEntry = systemContext.getEntry(groupIdUri);
					if (myGroupEntry == null) {
						// 指定されたグループに未参加のため、後続の処理でグループ参加する。
						isGroupOnly = true;
					}
				}
				if (!isGroupOnly) {
					StringBuilder sb = new StringBuilder();
					sb.append(EntryDuplicatedException.MESSAGE);
					sb.append(" account = ");
					sb.append(account);
					throw new EntryDuplicatedException(sb.toString());
				}
			}
			// UIDが有効となるSystemContextを生成
			mySystemContext = createMySystemContext(uid, account, serviceName,
					requestInfo, connectionInfo);

			List<EntryBase> updEntries = new ArrayList<EntryBase>();
			// ユーザステータスが"Cancelled"か"Revoked"(グループ指定の場合のみ)の場合、"Interim"に更新する。
			if (Constants.USERSTATUS_CANCELLED.equals(userstatus) ||
					Constants.USERSTATUS_REVOKED.equals(userstatus)) {
				setUserstatus(userTopEntry, Constants.USERSTATUS_INTERIM);
				updEntries.add(userTopEntry);
			}

			String userAuthUri = null;
			if (!isLink) {
				// パスワード上書き
				userAuthEntry = getUserAuthEntryByUid(uid, mySystemContext);
				setAuthToEntry(userAuthEntry, password);
				updEntries.add(userAuthEntry);
				userAuthUri = userAuthEntry.getMyUri();
			} else {
				// 追加のFeedを設定
				FeedBase tmpFeed = convertUid(additionalFeed, uid, systemContext);
				if (TaggingEntryUtil.isExistData(tmpFeed)) {
					updEntries.addAll(tmpFeed.entry);
				}
			}
	
			if (updEntries.size() > 0) {
				FeedBase updFeed = TaggingEntryUtil.createFeed(serviceName);
				updFeed.entry = updEntries;
				FeedBase retFeed = mySystemContext.put(updFeed);
				// 戻り値からEntryの取得し直し(id更新)
				for (EntryBase retEntry : retFeed.entry) {
					String myUri = retEntry.getMyUri();
					if (myUri.equals(userAuthUri)) {
						userAuthEntry = retEntry;
					} else if (userTopUri.equals(myUri)) {
						userTopEntry = retEntry;
					}
				}
			}

		} else {
			// ユーザトップエントリーが存在しない場合、ユーザエントリーを登録する。
			//   UIDを自動生成する。(addids)
			uid = createUid(systemContext);
			// UIDが有効となるSystemContextを生成
			mySystemContext = createMySystemContext(uid, account, serviceName,
					requestInfo, connectionInfo);

			//     ユーザトップエントリーのtitleにユーザアカウントを設定する。(認証時の検索に使用)
			//     ユーザトップエントリーについて、ユーザステータス<summary>に"Interim"を設定する。
			//   パスワードを自動生成する。
			//     12桁のパスワードを自動生成する。
			//     パスワード設定がある場合、自動生成パスワード(12桁)+指定されたパスワードとする。
			//   アクセスキーを発行する。
			//   初期エントリーを登録する。
			//     /_user/{UID}
			//     /_user/{UID}/accesskey (アクセスキー)
			//     /_user/{UID}/auth (パスワード)
			//     /_user/{UID}/group
			//     /_user/{UID}/login_history -> 本登録時
			//     /_user/{UID}/service -> 本登録時

			// ユーザエントリーが存在しない場合、ユーザエントリーを登録する。
			// ユーザステータス<summary>に"Interim"を設定する。
			List<EntryBase> insEntries = new ArrayList<EntryBase>();

			// /_user/{UID}
			userTopEntry = createInterimUserTopEntry(uid, email, account,
					nickname, serviceName);
			insEntries.add(userTopEntry);

			// /_user/{UID}/auth
			userAuthEntry = createAuthEntry(password, uid, serviceName);
			insEntries.add(userAuthEntry);
			if (isByAdmin) {
				setUserAuthAclActivate(userAuthEntry);
				setUserAuthAclAdminUpdate(userAuthEntry, groupName);
			}

			// /_user/{UID}/group
			String groupUri = getGroupUriByUid(uid);
			EntryBase groupEntry = createEntry(groupUri, serviceName);
			insEntries.add(groupEntry);

			// 追加のFeedを設定
			if (isLink) {
				FeedBase tmpFeed = convertUid(additionalFeed, uid, systemContext);
				if (TaggingEntryUtil.isExistData(tmpFeed)) {
					insEntries.addAll(tmpFeed.entry);
				}
			}
			
			FeedBase insFeed = TaggingEntryUtil.createFeed(serviceName);
			insFeed.setEntry(insEntries);

			// ユーザトップエントリーとユーザ認証エントリーのみ登録処理
			FeedBase retFeed = mySystemContext.post(insFeed);
			// 戻り値からEntryの取得し直し(id更新)
			userTopEntry = retFeed.entry.get(0);
			if (!isLink) {
				userAuthEntry = retFeed.entry.get(1);
			}
		}

		if (!isGroupOnly) {
			// アクセスキー登録
			AccessTokenManager accessTokenManager = TaggingEnvUtil.getAccessTokenManager();
			String accessKey = accessTokenManager.createAccessKeyStr();
			accessTokenManager.putAccessKey(uid, accessKey, mySystemContext);
		}

		if (isByAdmin) {
			// 本登録
			standardUserInit(uid, userTopEntry, userAuthEntry, groupName, isGroupOnly, mySystemContext);
			// ユーザトップエントリーを取得し直し(メモリキャッシュから)
			userTopEntry = mySystemContext.getEntry(userTopEntry.getMyUri(), true);
		}

		// キャッシュの更新
		//   GET /_user?title={ユーザアカウント}
		getUserTopEntryByAccount(account, false, mySystemContext);

		// 指定されたメールアドレス(小文字変換したものでなく入力値のまま)にメールを送信する。
		if (mailEntry != null && !StringUtils.isBlank(mailEntry.title) &&
				(!StringUtils.isBlank(mailEntry.summary) || !StringUtils.isBlank(mailEntry.getContentText())) &&
				isMailAddress(email)) {
			if (logger.isTraceEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[adduserProc] sendMail [title]: ");
				sb.append(mailEntry.title);
				sb.append(" [summary]: ");
				sb.append(mailEntry.summary);
				sb.append(" [content]: ");
				sb.append(mailEntry.getContentText());
				logger.debug(sb.toString());
			}
			mySystemContext.sendMail(mailEntry, email, null);
		}

		return userTopEntry;
	}

	/**
	 * 自分のUID、アカウントが有効となるSystemContextを取得.
	 * @param uid UID
	 * @param account アカウント
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return SystemContext
	 */
	private SystemContext createMySystemContext(String uid, String account,
			String serviceName, RequestInfo requestInfo, ConnectionInfo connectionInfo) {
		SystemAuthentication auth = new SystemAuthentication(account, uid, serviceName);
		return new SystemContext(auth, requestInfo, connectionInfo);
	}

	/**
	 * ユーザ登録処理のFeedチェック.
	 * @param feed ユーザ情報
	 */
	private void checkUserAuthFeed(FeedBase feed) {
		if (feed == null || feed.entry == null || feed.entry.isEmpty()) {
			throw new IllegalParameterException("User information is required.");
		}
		// entryチェック
		for (EntryBase entry : feed.entry) {
			if (entry == null) {
				throw new IllegalParameterException("User information is required.");
			}
		}
	}

	/**
	 * 管理者によるパスワード更新処理のFeedチェック.
	 * @param feed パスワード更新情報
	 */
	private void checkChangepassByAdminFeed(FeedBase feed) {
		checkUserAuthFeed(feed);
		for (EntryBase entry : feed.entry) {
			String uri = entry.getMyUri();
			CheckUtil.checkUri(uri);
			String[] uriParts = TaggingEntryUtil.getUriParts(uri);
			if (uriParts.length != 4) {
				throw new IllegalParameterException("The key is not for changepass. " + uri);
			}
			if (!Constants.URI_USER_VAL.equals(uriParts[1]) ||
					!Constants.URI_LAYER_AUTH_VAL.equals(uriParts[3])) {
				throw new IllegalParameterException("The key is not for changepass. " + uri);
			}
		}
	}

	/**
	 * ユーザ登録処理のパラメータチェック.
	 * @param entry ユーザ情報
	 * @param userAuthType 処理区分(adduser、passreset、changepass)
	 * @param isByAdmin サービス管理者によるユーザ登録の場合true
	 * @param isNotRequiredEmail メールアドレス形式必須でない場合true
	 * @return [0]メールアドレス、[1]パスワード、[2]アカウント(メールアドレスを小文字に編集し、一部文字を除去)、[3]ニックネーム
	 */
	private String[] checkUserAuthInfo(EntryBase entry, UserAuthType userAuthType,
			boolean isByAdmin, boolean isNotRequiredEmail) {
		return checkUserAuthInfo(entry, userAuthType, isByAdmin, isNotRequiredEmail, true);
	}

	/**
	 * ユーザ登録処理のパラメータチェック.
	 * @param entry ユーザ情報
	 * @param userAuthType 処理区分(adduser、passreset、changepass、changeaccount)
	 * @param isByAdmin サービス管理者によるユーザ登録の場合true
	 * @param isNotRequiredEmail メールアドレス形式必須でない場合true
	 * @param createPass サービス管理者によるユーザ登録でパスワード設定がない場合に自動生成するかどうか
	 * @return [0]メールアドレス、[1]パスワード、[2]アカウント(メールアドレスを小文字に編集し、一部文字を除去)、[3]ニックネーム
	 */
	private String[] checkUserAuthInfo(EntryBase entry, UserAuthType userAuthType,
			boolean isByAdmin, boolean isNotRequiredEmail, boolean createPass) {
		if (entry == null || entry.contributor == null || entry.contributor.isEmpty()) {
			throw new IllegalParameterException("User information is required.");
		}
		Contributor contributor = entry.contributor.get(0);
		if (StringUtils.isBlank(contributor.uri)) {
			throw new IllegalParameterException("User information is required.");
		}
		if (!contributor.uri.startsWith(Constants.URN_PREFIX_AUTH)) {
			throw new IllegalParameterException("User information format is invalid. " + contributor.uri);
		}
		String email = null;
		String password = null;
		String account = null;
		String nickname = contributor.name;
		String tmp = contributor.uri.substring(URN_PREFIX_AUTH_LEN);
		int tmpLen = tmp.length();
		if (tmpLen == 0) {
			throw new IllegalParameterException("User information is required. " + contributor.uri);
		}
		boolean createdPass = false;
		int idx = tmp.indexOf(UserManagerDefaultConst.URN_AUTH_PASSWORD_START);
		if (UserAuthType.ADDUSER == userAuthType) {
			if (idx <= 0 || idx >= tmpLen - 1) {
				if (!isByAdmin) {
					throw new IllegalParameterException("Password is required. " + contributor.uri);
				} else {
					if (idx == -1) {
						// 管理者によるユーザ登録でパスワード指定が無い場合、ランダム値を発行してパスワードとする。
						idx = tmpLen;
						if (createPass) {
							password = createPassword();
							createdPass = true;
						}
					}
				}
			} else {
				password = tmp.substring(idx + 1);
			}
			email = tmp.substring(0, idx);
		} else if (UserAuthType.PASSRESET == userAuthType) {
			if (idx > -1) {
				throw new IllegalParameterException("User information format is invalid. " + contributor.uri);
			}
			email = tmp;
		} else if (UserAuthType.CHANGEPASS == userAuthType) {
			if (idx != 0) {
				throw new IllegalParameterException("User information format is invalid. " + contributor.uri);
			}
			password = tmp.substring(1);
		} else {	// changeaccount
			if (idx > -1) {
				email = tmp.substring(0, idx);
				password = tmp.substring(idx + 1);
			} else {
				email = tmp;
			}
		}
		
		if (!StringUtils.isBlank(email)) {
			// 2019.11.11 adduserByAdminはメールアドレス形式必須としない。
			boolean isNotEmail = false;
			try {
				CheckUtil.checkMailAddress(email);
			} catch (IllegalParameterException e) {
				if (isNotRequiredEmail) {
					isNotEmail = true;
				} else {
					throw e;
				}
			}
			account = UserUtil.editAccount(email);
			if (account == null || account.length() == 0) {
				throw new IllegalParameterException("Please specify characters that can be used in the email. email = " + email);
			}
			if (isNotEmail) {
				if (createdPass) {
					// ユーザ登録でアカウントがメールアドレスでなく、パスワード指定が無い場合はエラー
					throw new IllegalParameterException("Password is required. " + contributor.uri);
				}
				email = null;
			}
		}
		return new String[]{email, password, account, nickname};
	}

	/**
	 * Entryからユーザステータスを取得する.
	 * @param entry Entry
	 * @return ユーザステータス
	 */
	public String getUserstatus(EntryBase entry) {
		if (entry != null) {
			return entry.summary;
		}
		return null;
	}

	/**
	 * ユーザトップエントリーからユーザステータスに関する情報のみ抽出する.
	 * <p>
	 *   <entry>
	 *     <link href="/_user/{UID}" rel="self"/>
	 *     <title>{アカウント}</title>
	 *     <subtitle>{ニックネーム}</subtitle>
	 *     <summary>{ユーザステータス}</summary>
	 *     <contributor>
	 *       <email>{メールアドレス}</email>
	 *     </contributor>
	 *   </entry>
	 * </p>
	 * @param entry ユーザトップエントリー
	 * @param serviceName サービス名
	 * @return ユーザステータスエントリー
	 */
	private EntryBase getUserstatusEntry(EntryBase entry, String serviceName) {
		if (entry == null || StringUtils.isBlank(serviceName)) {
			return null;
		}
		EntryBase statusEntry = TaggingEntryUtil.createEntry(serviceName);
		// UID
		statusEntry.setMyUri(entry.getMyUri());
		// アカウント
		statusEntry.title = entry.title;
		// ニックネーム
		statusEntry.subtitle = entry.subtitle;
		// ユーザステータス
		statusEntry.summary = entry.summary;
		// メールアドレス
		if (entry.contributor != null) {
			for (Contributor contributor : entry.contributor) {
				if (!StringUtils.isBlank(contributor.email)) {
					Contributor statusContributor = new Contributor();
					statusContributor.email = contributor.email;
					statusEntry.addContributor(statusContributor);
				}
			}
		}
		return statusEntry;
	}

	/**
	 * UIDを生成
	 * @param reflexContext ReflexContext
	 * @return UID
	 */
	private String createUid(ReflexContext reflexContext)
	throws IOException, TaggingException {
		// UIDはシステム管理サービスで一意発行
		SystemContext systemContext = new SystemContext(TaggingEnvUtil.getSystemService(),
				reflexContext.getRequestInfo(), reflexContext.getConnectionInfo());
		FeedBase numFeed = systemContext.addids(UserManagerDefaultConst.URI_ADDIDS_UID, 1);
		return numFeed.title;
	}

	/**
	 * ユーザステータスに対応した処理を行う.
	 *  ・Interimの場合、本登録処理を行う。
	 *  ・Activateの場合処理を抜ける。
	 * その他の場合、ユーザステータスエラー
	 * @param auth 認証情報
	 * @param reflexContext ReflexContext
	 */
	public void processByUserstatus(ReflexAuthentication auth, ReflexContext reflexContext)
	throws IOException, TaggingException {
		if (auth == null || StringUtils.isBlank(auth.getUid())) {
			return;	// 認証無しの場合処理を抜ける。
		}
		SystemContext systemContext = new SystemContext(auth,
				reflexContext.getRequestInfo(), reflexContext.getConnectionInfo());
		EntryBase userTopEntry = getUserTopEntryByUid(auth.getUid(), systemContext);
		if (userTopEntry == null) {
			return;	// 処理を抜ける。
		}
		String userstatus = getUserstatus(userTopEntry);
		if (Constants.USERSTATUS_ACTIVATED.equals(userstatus)) {
			return;	// 処理を抜ける。
		} else if (Constants.USERSTATUS_INTERIM.equals(userstatus)) {
			// 本登録処理
			adduserFromInterim(userTopEntry, systemContext);
		} else {
			// ステータスエラー
			AuthenticationException ae = new AuthenticationException();
			ae.setSubMessage("User status is invalid. " + userstatus);
			throw ae;
		}
	}

	/**
	 * 本登録処理.
	 * @param userTopEntry ユーザトップエントリー
	 * @param systemContext ReflexContext
	 */
	private void adduserFromInterim(EntryBase userTopEntry, SystemContext reflexContext)
	throws IOException, TaggingException {
		// アプリの初期処理が成功したら、ユーザエントリーを本登録とする。
		setUserstatus(userTopEntry, Constants.USERSTATUS_ACTIVATED);

		// ユーザ認証エントリーを取得
		String uid = getUidByUri(userTopEntry.getMyUri());
		EntryBase userAuthEntry = getUserAuthEntryByUid(uid, reflexContext);

		// パスワードの自動生成部分を除去する。
		String password = getPassword(userAuthEntry);
		if (password != null && password.length() > UserManagerDefaultConst.PASSWORD_LEN) {
			password = password.substring(UserManagerDefaultConst.PASSWORD_LEN);
			for (Contributor contributor : userAuthEntry.contributor) {
				if (contributor.uri != null &&
						contributor.uri.startsWith(UserManagerDefaultConst.URN_PREFIX_AUTH_COMMA)) {
					String newUrn = createAuthUrn(password);
					contributor.uri = newUrn;
					break;
				}
			}
		}

		// ユーザのCRUD権限を設定する。
		setUserAuthAclActivate(userAuthEntry);

		standardUserInit(uid, userTopEntry, userAuthEntry, null, false, reflexContext);
	}

	/**
	 * ユーザ本登録時に、ユーザのCRUD権限を設定する
	 * @param userAuthEntry ユーザ認証情報
	 */
	private void setUserAuthAclActivate(EntryBase userAuthEntry) {
		String uid = getUidByUri(userAuthEntry.getMyUri());
		// ユーザのCRUD権限を設定する。
		userAuthEntry.contributor.add(TaggingEntryUtil.getAclContributor(uid,
				Constants.ACL_TYPE_CRUD));
	}

	/**
	 * パスワードエントリーにサービス管理グループの更新権限を設定する.
	 * adduserByAdmin、adduserByGroupadminのみ
	 * @param userAuthEntry ユーザ認証情報
	 * @param groupName グループ管理者によるユーザ登録の場合、グループ名
	 * @return 変更があった場合true
	 */
	private boolean setUserAuthAclAdminUpdate(EntryBase userAuthEntry, String groupName) {
		boolean isChange = false;
		// ユーザ管理者のU権限を設定する。
		Contributor contributorAclUseradmin = TaggingEntryUtil.getAclContributor(
				Constants.URI_GROUP_USERADMIN,
				Constants.ACL_TYPE_UPDATE);
		if (!hasContributorUri(contributorAclUseradmin.uri, userAuthEntry.contributor)) {
			userAuthEntry.contributor.add(contributorAclUseradmin);
			isChange = true;
		}
		if (!StringUtils.isBlank(groupName)) {
			Contributor contributorAclGroupadmin = TaggingEntryUtil.getAclContributor(
					GroupUtil.getGroupadminGroup(groupName),
					Constants.ACL_TYPE_UPDATE);
			if (!hasContributorUri(contributorAclGroupadmin.uri, userAuthEntry.contributor)) {
				userAuthEntry.contributor.add(contributorAclGroupadmin);
				isChange = true;
			}
		}
		return isChange;
	}
	
	/**
	 * ユーザグループエントリーに、グループ管理グループのCRUD権限を設定する.
	 * @param userGroupEntry ユーザグループエントリー
	 * @param groupName グループ名
	 * @return 変更があった場合true
	 */
	private boolean setUserGroupAclGroupadmin(EntryBase userGroupEntry, String groupName) {
		boolean isChange = false;
		// グループ管理者グループのCRUD権限を設定する。
		if (!StringUtils.isBlank(groupName)) {
			Contributor contributorAclGroupadmin = TaggingEntryUtil.getAclContributor(
					GroupUtil.getGroupadminGroup(groupName),
					Constants.ACL_TYPE_CRUD);
			if (!hasContributorUri(contributorAclGroupadmin.uri, userGroupEntry.contributor)) {
				userGroupEntry.contributor.add(contributorAclGroupadmin);
				isChange = true;
			}
		}
		return isChange;
	}
	
	/**
	 * contributorリストに、指定されたurnが存在するかチェック
	 * @param urn contributor.uriの値
	 * @param contributors contributorリスト
	 * @return contributorリストに、指定されたurnが存在する場合true
	 */
	private boolean hasContributorUri(String urn, List<Contributor> contributors) {
		if (StringUtils.isBlank(urn)) {
			return false;
		}
		if (contributors != null) {
			for (Contributor contributor : contributors) {
				if (urn.equals(contributor.uri)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * 一般ユーザ初期データ登録
	 * @param uid UID
	 * @param userTopEntry ユーザトップエントリー
	 * @param userAuthEntry ユーザ認証エントリー
	 * @param groupName グループ管理者による登録の場合、グループ名
	 * @param isGroupOnly グループ管理者による登録で、ユーザ自体は登録済みの場合
	 * @param reflexContext ReflexContext
	 */
	private void standardUserInit(String uid, EntryBase userTopEntry,
			EntryBase userAuthEntry, String groupName, boolean isGroupOnly, 
			SystemContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		List<EntryBase> newEntries = new ArrayList<EntryBase>();
		String groupUri = getGroupUriByUid(uid);
		FeedBase initFeed = getUserInitFeed(reflexContext);

		// ユーザトップエントリーを最初に設定
		setUserTopInfoInit(userTopEntry, uid, groupName, isGroupOnly, serviceName);
		newEntries.add(userTopEntry);
		if (!isGroupOnly) {
			// ユーザ認証エントリーを更新する。
			if (userAuthEntry != null) {
				newEntries.add(userAuthEntry);
			}
			// ユーザのグループエントリー
			try {
				EntryBase groupEntry = reflexContext.getEntry(groupUri, true);
				groupEntry = editGroupEntry(groupEntry, groupUri, uid, groupName, serviceName);
				newEntries.add(groupEntry);
			} catch (PermissionException e) {
				logger.warn(LogUtil.getRequestInfoStr(requestInfo) +
						"[statderdUserInit] PermissionException: " +
						e.getMessage());
			}
	
			if (initFeed == null || initFeed.entry == null ||
					initFeed.entry.size() == 0) {
	
				// ユーザ初期登録フィードが設定されていない場合
	
			} else {
				// ユーザ初期登録フィードによる初期エントリー登録処理
				//boolean hasGroupEntry = false;
				for (EntryBase initEntry : initFeed.entry) {
					// link、contributorのUID編集
					convertUid(initEntry, uid);
					newEntries.add(initEntry);
				}
			}
	
			// 追加エントリー
			List<EntryBase> addingEntries = addEntriesByStanderdUserInit(uid, serviceName,
					reflexContext);
			if (addingEntries != null && !addingEntries.isEmpty()) {
				for (EntryBase addingEntry : addingEntries) {
					String addingUri = addingEntry.getMyUri();
					if (!StringUtils.isBlank(addingUri)) {
						boolean exists = false;
						for (EntryBase newEntry : newEntries) {
							if (addingUri.equals(newEntry.getMyUri())) {
								exists = true;
							}
						}
						if (!exists) {
							newEntries.add(addingEntry);
						}
					}
				}
			}
		} else {
			boolean isChangeUserAuth = setUserAuthAclAdminUpdate(userAuthEntry, groupName);
			if (isChangeUserAuth) {
				newEntries.add(userAuthEntry);
			}
			String userGroupUri = getGroupUriByUid(uid);
			EntryBase userGroupEntry = reflexContext.getEntry(userGroupUri);
			boolean isChangeUserGroup = setUserGroupAclGroupadmin(userGroupEntry, groupName);
			if (isChangeUserGroup) {
				newEntries.add(userGroupEntry);
			}
		}
		
		// グループ管理者による登録の場合
		if (!StringUtils.isBlank(groupName)) {
			// グループ参加登録
			// /_group/{グループ名}/{UID}エントリーを登録する。
			//   /_user/{UID}/group/{グループ名} エイリアス
			String groupParentUri = GroupUtil.getGroupUri(groupName);
			EntryBase groupUidEntry = createGroupEntry(groupParentUri, groupName, uid, serviceName);
			newEntries.add(groupUidEntry);
		}

		if (newEntries != null && newEntries.size() > 0) {
			// ユーザ初期エントリー登録
			FeedBase newFeed = TaggingEntryUtil.createFeed(serviceName);
			newFeed.entry = newEntries;
			reflexContext.bulkSerialPut(newFeed, false);
		}
	}

	/**
	 * ユーザトップエントリー初期編集.
	 * <p>
	 * ログインサービスのユーザ情報を自サービスのユーザトップエントリーに項目移送する.<br>
	 * UserSecretを設定する.
	 * </p>
	 * @param userTopEntry 自サービスのユーザトップエントリー
	 * @param uid UID
	 * @param groupName グループ管理者によるユーザ登録の場合、グループ名
	 * @param serviceName サービス名
	 */
	private void setUserTopInfoInit(EntryBase userTopEntry,
			String uid, String groupName, boolean isGroupOnly, String serviceName) {
		// ユーザステータス(summary) -> Activated
		userTopEntry.summary = Constants.USERSTATUS_ACTIVATED;

		List<Contributor> contributors = userTopEntry.contributor;
		if (!isGroupOnly) {
			// ACLを設定する。
			// {サービス管理グループ},CRUD、{uid},R、{uid},CUD/、+,R(ログインユーザの参照権限) -> +,Rは廃止
			if (contributors == null) {
				contributors = new ArrayList<Contributor>();
				userTopEntry.contributor = contributors;
			}
			contributors.add(TaggingEntryUtil.getAclContributor(Constants.URI_GROUP_ADMIN,
					Constants.ACL_TYPE_CRUD));
			contributors.add(TaggingEntryUtil.getAclContributor(Constants.URI_GROUP_USERADMIN,
					Constants.ACL_TYPE_CRUD));
			contributors.add(TaggingEntryUtil.getAclContributor(uid, Constants.ACL_TYPE_RETRIEVE));
			contributors.add(TaggingEntryUtil.getAclContributor(uid,
					Constants.ACL_TYPE_CREATE + Constants.ACL_TYPE_UPDATE + 
					Constants.ACL_TYPE_DELETE + Constants.ACL_TYPE_LOW));
		} 
		
		// グループ管理者による登録の場合
		if (!StringUtils.isBlank(groupName)) {
			// グループ管理ユーザ (activateuser/revokeuser の実行に必要)
			String groupadminGroup = GroupUtil.getGroupadminGroup(groupName);
			String aclType = Constants.ACL_TYPE_CRUD;
			String groupadminGroupUrn = TaggingEntryUtil.getAclUrn(groupadminGroup, aclType);
			boolean exist = false;;
			for (Contributor contributor : contributors) {
				if (groupadminGroupUrn.equals(contributor.uri)) {
					exist = true;
				}
			}
			if (!exist) {
				contributors.add(TaggingEntryUtil.getAclContributor(
						groupadminGroup, aclType));
			}
		}
	}

	/**
	 * 初期エントリーのUID編集
	 * @param entry Entry
	 * @param uid UID
	 */
	private void convertUid(EntryBase entry, String uid) {
		// linkのユーザ番号編集
		List<Link> links = entry.link;
		if (links != null) {
			for (Link link : links) {
				if ((Link.REL_SELF.equals(link._$rel) ||
						Link.REL_ALTERNATE.equals(link._$rel)) &&
						link._$href != null) {
					link._$href = link._$href.replaceAll(Constants.ADDUSERINFO_UID, uid);
				}
			}
		}

		// contributorのユーザ番号編集
		List<Contributor> contributors = entry.contributor;
		if (contributors != null) {
			for (Contributor contributor : contributors) {
				if (contributor.uri != null) {
					contributor.uri = contributor.uri.replaceAll(Constants.ADDUSERINFO_UID, uid);
				}
			}
		}
	}

	/**
	 * 認証情報エントリーに設定するWSSE情報の文字列を取得します.
	 * <p>
	 * /_user/{UID}/auth エントリーのuriに設定する、以下の形式のWSSE情報文字列を作成します。
	 * <ul>
	 * <li>urn:vte.cx:auth:,{password}</li>
	 * </ul>
	 * </p>
	 * @param password パスワード
	 * @return 認証情報エントリーに設定するWSSE情報の文字列
	 */
	private String createAuthUrn(String password) {
		StringBuilder sb = new StringBuilder();
		sb.append(UserManagerDefaultConst.URN_PREFIX_AUTH_COMMA);
		sb.append(StringUtils.null2blank(password));
		return sb.toString();
	}

	/**
	 * ユーザ本登録時に登録するEntryリストの設定を取得.
	 * @param reflexContext ReflexContext
	 */
	public FeedBase getUserInitFeed(ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		RequestInfo requestInfo = reflexContext.getRequestInfo();

		EntryBase adduserinfoEntry = reflexContext.getEntry(
				Constants.URI_SETTINGS_ADDUSERINFO, true);
		if (adduserinfoEntry != null && !StringUtils.isBlank(adduserinfoEntry.summary)) {
			List<EntryBase> entries = new ArrayList<EntryBase>();
			String[] lines = adduserinfoEntry.summary.split(Constants.NEWLINE);
			for (String line : lines) {
				String uri = StringUtils.trim(line);
				if (uri.indexOf(Constants.ADDUSERINFO_UID) > 0) {
					try {
						// #に仮の値を設定してURIフォーマットチェックを行う。
						String tmpUri = uri.replace(Constants.ADDUSERINFO_UID, "999");
						CheckUtil.checkUri(tmpUri);

						EntryBase entry = TaggingEntryUtil.createEntry(serviceName);
						entry.setMyUri(uri);
						entries.add(entry);

					} catch (IllegalParameterException e) {
						StringBuilder sb = new StringBuilder();
						sb.append(LogUtil.getRequestInfoStr(requestInfo));
						sb.append(Constants.URI_SETTINGS_ADDUSERINFO);
						sb.append(" error. ");
						sb.append(e.getMessage());
						logger.warn(sb.toString());
					}
				}
			}
			if (!entries.isEmpty()) {
				FeedBase feed = TaggingEntryUtil.createFeed(serviceName);
				feed.entry = entries;
				return feed;
			}
		}
		return null;
	}

	/**
	 * パスワードリセットのためのメール送信.
	 * @param feed パスワードリセット情報
	 * @param reflexContext ReflexContext
	 * @return ユーザのトップエントリー
	 */
	public EntryBase passreset(FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException {
		AclBlogic aclBlogic = new AclBlogic();
		// ユーザ管理グループ参加ユーザでログインかどうかチェック
		if (!aclBlogic.isInTheGroup(reflexContext.getAuth(), Constants.URI_GROUP_USERADMIN)) {
			// ユーザ管理グループ参加ユーザでない場合、
			// reCAPTCHAが設定されていない場合エラーとする(CSRF対応)。
			SecurityBlogic securityBlogic = new SecurityBlogic();
			securityBlogic.checkCaptcha(reflexContext.getRequest(), SecurityConst.CAPTCHA_ACTION_PASSRESET);
		}

		// feedチェック
		checkUserAuthFeed(feed);
		// passresetはEntry1件のみ
		EntryBase entry = feed.entry.get(0);
		// パスワードリセットのためのメール送信処理
		return passresetProc(entry, false, reflexContext);
	}

	/**
	 * パスワードリセットのためのメール送信.
	 * @param entry パスワードリセット情報
	 * @param reflexContext ReflexContext
	 * @return ユーザのトップエントリー
	 */
	private EntryBase passresetProc(EntryBase entry, boolean isByAdmin,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		SystemContext systemContext = new SystemContext(serviceName,
				requestInfo, connectionInfo);

		// メール設定チェック
		EntryBase mailEntry = null;
		if (!StringUtils.isBlank(entry.title) &&
				(!StringUtils.isBlank(entry.summary) || !StringUtils.isBlank(entry.getContentText()))) {
			mailEntry = entry;
		} else {
			mailEntry = systemContext.getEntry(
						Constants.URI_SETTINGS_PASSRESET, true);
		}
		if (mailEntry == null || StringUtils.isBlank(mailEntry.title) ||
				(StringUtils.isBlank(mailEntry.summary) && StringUtils.isBlank(entry.getContentText()))) {
			// passresetでメールの設定が無い場合はエラー
			throw new InvalidServiceSettingException("There is no mail setting. (passreset)");
		}

		// 指定されたメールアドレスを小文字に変換し、さらにアカウント利用可能文字以外を削除した値をユーザアカウントとする。
		String[] userInfo = checkUserAuthInfo(entry, UserAuthType.PASSRESET, false, false);
		String email = userInfo[0];
		String account = userInfo[2];

		// ユーザアカウントからユーザトップエントリーを検索する。(自サービス内)
		//   GET /_user?title={ユーザアカウント}
		EntryBase userTopEntry = getUserTopEntryByAccount(account, false, systemContext);

		if (userTopEntry == null) {
			// ユーザが未登録の場合エラー
			PermissionException pe = new PermissionException();
			pe.setSubMessage("The account does not exist. " + account);
			throw pe;
		} else {
			String userstatus = getUserstatus(userTopEntry);
			if (Constants.USERSTATUS_INTERIM.equals(userstatus) ||
					Constants.USERSTATUS_CANCELLED.equals(userstatus)) {
				// ユーザステータスに"Interim""Cancelled"が設定されていれば未登録エラーを返す。
				PermissionException pe = new PermissionException();
				StringBuilder sb = new StringBuilder();
				sb.append("The account status is ");
				sb.append(userstatus);
				sb.append(". ");
				sb.append(account);
				pe.setSubMessage(sb.toString());
				throw pe;

			} else if (!Constants.USERSTATUS_ACTIVATED.equals(userstatus)) {
				// ユーザステータスに"Revoked"が設定されていれば認証エラー(401)を返す。
				AuthenticationException ae = new AuthenticationException();
				StringBuilder sb = new StringBuilder();
				sb.append("The account status is not activated: ");
				sb.append(account);
				sb.append(". user status = ");
				sb.append(userstatus);
				ae.setSubMessage(sb.toString());
				throw ae;
			}
		}

		// 指定されたメールアドレス(小文字変換したものでなく入力値のまま)にメールを送信する。
		// UID取得
		String uid = getUidByUri(userTopEntry.getMyUri());
		// UIDが有効となるSystemContextを生成
		SystemContext mySystemContext = createMySystemContext(uid, account, serviceName,
				requestInfo, connectionInfo);
		
		// パスワード変更一時トークンの取得、メール本文への設定
		EntryBase tmpMailEntry = TaggingEntryUtil.copyEntry(mailEntry, 
				TaggingEnvUtil.getResourceMapper(serviceName));
		String rxid = createRXIDByAccount(account, systemContext);
		String passresetTokenUri = getCachePassresetTokenUri(uid);
		String passresetToken = getPassresetTokenFromCache(passresetTokenUri, mySystemContext);
		int rxidSec = TaggingEnvUtil.getRxidMinute(serviceName) * 60;
		if (StringUtils.isBlank(passresetToken)) {
			// パスワード変更一時トークンの発行
			passresetToken = NumberingUtil.randomString(UserManagerDefaultConst.PASSRESET_TOKEN_LEN);
			setPassresetTokenToCache(passresetTokenUri, passresetToken, rxidSec, mySystemContext);
		} else {
			// パスワード変更一時トークンの有効期限延長
			setExpirePassresetTokenToCache(passresetTokenUri, rxidSec, mySystemContext);
		}
		tmpMailEntry.summary = replacePassresetToken(mailEntry.summary, passresetToken, rxid);
		if (mailEntry.content != null && !StringUtils.isBlank(mailEntry.content._$$text)) {
			tmpMailEntry.content._$$text = replacePassresetToken(mailEntry.content._$$text, 
					passresetToken, rxid);
		}

		// メール送信
		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[passresetProc] sendMail [title]: ");
			sb.append(tmpMailEntry.title);
			sb.append(" [summary]: ");
			sb.append(tmpMailEntry.summary);
			sb.append(" [content]: ");
			sb.append(tmpMailEntry.getContentText());
			logger.debug(sb.toString());
		}
		mySystemContext.sendMail(tmpMailEntry, email, null);

		return userTopEntry;
	}

	/**
	 * パスワード更新.
	 * @param feed パスワード更新情報
	 * @param reflexContext ReflexContext
	 */
	public EntryBase changepass(FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// feedチェック
		checkUserAuthFeed(feed);
		// changepassはEntry1件のみ
		EntryBase entry = feed.entry.get(0);
		// パスワード更新処理
		return changepassProc(entry, false, reflexContext);
	}

	/**
	 * 管理者によるパスワード更新.
	 * @param feed パスワード更新情報
	 * @param reflexContext ReflexContext
	 */
	public FeedBase changepassByAdmin(FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException {
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		// feedチェック
		checkChangepassByAdminFeed(feed);

		AclBlogic aclBlogic = new AclBlogic();
		// データ存在チェック
		SystemContext systemContext = new SystemContext(auth, requestInfo, connectionInfo);
		for (EntryBase entry : feed.entry) {
			String uri = entry.getMyUri();
			EntryBase authEntry = systemContext.getEntry(uri, true);
			if (authEntry == null) {
				throw new NoExistingEntryException(NoExistingEntryException.MSG_PREFIX + uri);
			}
			// ACLチェック
			aclBlogic.checkAcl(uri, AclConst.ACL_TYPE_UPDATE, auth, requestInfo, connectionInfo);
		}

		// パスワード更新
		// グループ管理者はcontributorを更新できないため、SystemContextで更新する。
		List<EntryBase> retEntries = new ArrayList<>();
		for (EntryBase entry : feed.entry) {
			retEntries.add(changepassProc(entry, true, systemContext));
		}

		FeedBase retFeed = TaggingEntryUtil.createFeed(reflexContext.getServiceName());
		retFeed.entry = retEntries;
		return retFeed;
	}

	/**
	 * パスワード更新.
	 * @param entry パスワード更新情報
	 * @param isByAdmin 管理者によるパスワード更新の場合true
	 * @param reflexContext ReflexContext
	 * @return ユーザのトップエントリー
	 */
	private EntryBase changepassProc(EntryBase entry, boolean isByAdmin,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		SystemContext systemContext = new SystemContext(serviceName,
				requestInfo, connectionInfo);

		// 指定されたメールアドレスを小文字に変換し、さらにアカウント利用可能文字以外を削除した値をユーザアカウントとする。
		String[] userInfo = checkUserAuthInfo(entry, UserAuthType.CHANGEPASS, false, false);
		String account = null;
		if (!isByAdmin) {
			account = reflexContext.getAccount();
		}
		String uid = null;
		if (!isByAdmin) {
			uid = reflexContext.getUid();
		} else {
			// 管理者によるパスワード変更の場合、link selfに指定されたUIDを抽出
			uid = getUidByUri(entry.getMyUri());
			CheckUtil.checkNotNull(uid, "UID");
		}
		String password = userInfo[1];

		// ユーザアカウントからユーザトップエントリーを検索する。(自サービス内)
		//   GET /_user?title={ユーザアカウント}
		EntryBase userTopEntry = getUserTopEntryByUid(uid, systemContext);

		if (userTopEntry == null) {
			// ユーザが未登録の場合エラー
			if (!isByAdmin) {
				PermissionException pe = new PermissionException();
				pe.setSubMessage("The account does not exist. " + account);
				throw pe;
			} else {
				StringBuilder sb = new StringBuilder();
				sb.append(NoExistingEntryException.MSG);
				sb.append(" UID=");
				sb.append(uid);
				throw new NoExistingEntryException(sb.toString());
			}
		} else {
			String userstatus = getUserstatus(userTopEntry);
			if (Constants.USERSTATUS_INTERIM.equals(userstatus)) {
				// ユーザステータスに"Interim"が設定されていれば未登録エラーを返す。
				PermissionException pe = new PermissionException();
				pe.setSubMessage("The account status is interim. " + account);
				throw pe;

			} else if (!Constants.USERSTATUS_ACTIVATED.equals(userstatus)) {
				// ユーザステータスに"Revoked"が設定されていれば認証エラー(401)を返す。
				AuthenticationException ae = new AuthenticationException();
				StringBuilder sb = new StringBuilder();
				sb.append("The account status is not activated: ");
				sb.append(account);
				sb.append(". user status = ");
				sb.append(userstatus);
				ae.setSubMessage(sb.toString());
				throw ae;
			}
		}
		
		// 旧パスワードまたは一時トークンの指定チェック
		if (!isByAdmin && !isChangephashLegacy(serviceName)) {
			checkInputAuth(uid, entry, systemContext);
		}

		// パスワード更新
		// UID取得
		// UIDが有効となるSystemContextを生成
		SystemContext mySystemContext = createMySystemContext(uid, account, serviceName,
				requestInfo, connectionInfo);

		EntryBase userAuthEntry = getUserAuthEntryByUid(uid, mySystemContext);
		setAuthToEntry(userAuthEntry, password);
		ReflexContext reflexContextForPut = null;
		if (!isByAdmin) {
			reflexContextForPut = mySystemContext;
		} else {
			// 管理者によるパスワード変更の場合、ログインユーザ権限で更新する。(権限がない場合はエラーとなる。)
			reflexContextForPut = reflexContext;
		}
		reflexContextForPut.put(userAuthEntry);
		
		// パスワード変更一時トークンを削除する
		deletePassresetTokenFromCacheByUid(uid, systemContext);

		return userTopEntry;
	}

	/**
	 * アカウント更新のためのメール送信.
	 * @param feed パスワード更新情報
	 * @param reflexContext ReflexContext
	 */
	public void changeaccount(FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// feedチェック
		checkUserAuthFeed(feed);
		// changeaccountはEntry1件のみ
		EntryBase entry = feed.entry.get(0);
		// アカウント更新のためのメール送信処理
		changeaccountProc(entry, false, reflexContext);
	}

	/**
	 * アカウント更新のためのメール送信処理.
	 * @param entry アカウント更新情報
	 * @param isByAdmin 管理者によるアカウント更新の場合true
	 * @param reflexContext ReflexContext
	 * @return ユーザのトップエントリー
	 */
	private void changeaccountProc(EntryBase entry, boolean isByAdmin,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		SystemContext systemContext = new SystemContext(reflexContext.getAuth(),
				requestInfo, connectionInfo);

		String uid = null;
		if (!isByAdmin) {
			uid = reflexContext.getUid();
		} else {
			// 管理者によるパスワード変更の場合、link selfに指定されたUIDを抽出
			uid = getUidByUri(entry.getMyUri());
			CheckUtil.checkNotNull(uid, "UID");
		}

		// メール設定チェック
		EntryBase mailEntry = null;
		if (!StringUtils.isBlank(entry.title) &&
				(!StringUtils.isBlank(entry.summary) || !StringUtils.isBlank(entry.getContentText()))) {
			mailEntry = entry;
		} else {
			mailEntry = systemContext.getEntry(
						Constants.URI_SETTINGS_CHANGEACCOUNT, true);
		}
		if (mailEntry == null || StringUtils.isBlank(mailEntry.title) ||
				(StringUtils.isBlank(mailEntry.summary) && StringUtils.isBlank(entry.getContentText()))) {
			// changeaccountでメールの設定が無い場合はエラー
			throw new InvalidServiceSettingException("There is no mail setting. (changeaccount)");
		}

		// 指定されたメールアドレスを小文字に変換し、さらにアカウント利用可能文字以外を削除した値をユーザアカウントとする。
		// [0]メールアドレス、[1]パスワード、[2]アカウント(メールアドレスを小文字に編集し、一部文字を除去)、[3]ニックネーム
		String[] userInfo = checkUserAuthInfo(entry, UserAuthType.CHANGEACCOUNT, false, false, false);
		String newEmail = userInfo[0];
		String newNickname = userInfo[3];
		String newAccount = userInfo[2];
		String newPassword = userInfo[1];	// パスワードの指定がない場合のみ設定可(ソーシャルアカウントにメールアドレスログイン情報を追加する場合)

		// ユーザアカウントからユーザトップエントリーを検索する。(自サービス内)
		//   GET /_user?title={ユーザアカウント}
		EntryBase newUserTopEntry = getUserTopEntryByAccount(newAccount, false, systemContext);

		if (newUserTopEntry != null) {
			// アカウントが登録済みの場合エラー
			// ただし、仮登録中であればアカウント変更を受け付ける。
			String userstatus = getUserstatus(newUserTopEntry);
			if (!Constants.USERSTATUS_INTERIM.equals(userstatus)) {
				String msg = "The account is already registered. " + newAccount;
				PermissionException pe = new PermissionException(msg);
				throw pe;
			}
		}

		// 認証コードを発行
		int verifyCodeLen = TaggingEnvUtil.getPropInt(serviceName,
				SettingConst.VERIFY_CODE_LENGTH, UserManagerDefaultConst.VERIFY_CODE_LENGTH_DEFAULT);
		boolean enableAlphabetVerify = TaggingEnvUtil.getPropBoolean(serviceName,
				SettingConst.ENABLE_ALPHABET_VERIFY, false);
		String verifyCode = null;
		if (enableAlphabetVerify) {
			verifyCode = NumberingUtil.randomString(verifyCodeLen).toUpperCase(Locale.ENGLISH);
		} else {
			verifyCode = NumberingUtil.randomNumber(verifyCodeLen);
		}

		// 指定されたメールアドレスをキャッシュ(Redis)に登録。
		putEmailToCache(newEmail, newNickname, newPassword, verifyCode, systemContext);

		// 指定されたメールアドレス(小文字変換したものでなく入力値のまま)にメールを送信する。
		// 本文に認証コードを埋め込む。
		mailEntry.summary = replaceMessage(mailEntry.summary, verifyCode, systemContext);
		if (mailEntry.content != null && !StringUtils.isBlank(mailEntry.content._$$text)) {
			mailEntry.content._$$text = replaceMessage(mailEntry.content._$$text, verifyCode,
					systemContext);
		}

		// メール送信
		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[changeaccountProc] sendMail [title]: ");
			sb.append(mailEntry.title);
			sb.append(" [summary]: ");
			sb.append(mailEntry.summary);
			sb.append(" [content]: ");
			sb.append(mailEntry.getContentText());
			logger.debug(sb.toString());
		}
		systemContext.sendMail(mailEntry, newEmail, null);
	}

	/**
	 * 指定されたメールアドレスをキャッシュ(Redis)に登録。
	 * @param newEmail 新メールアドレス
	 * @param newNickname 新ニックネーム
	 * @param newPassword 新パスワード(パスワードの指定がない場合のみ設定可(ソーシャルアカウントにメールアドレスログイン情報を追加する場合))
	 * @param verifyCode 認証コード
	 * @param reflexContext ReflexContext
	 */
	private void putEmailToCache(String newEmail, String newNickname, String newPassword,
			String verifyCode, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		// キー:/_changeaccount/{UID}/email、値:メールアドレス
		// キー:/_changeaccount/{UID}/nickname、値:ニックネーム
		// キー:/_changeaccount/{UID}/phash、値:パスワード
		// キー:/_changeaccount/{UID}/verify、値:認証コード
		// キー:/_changeaccount/{UID}/error_count、値:0 (認証コード不一致回数)
		// 有効期限はRXIDと同じ。
		String uid = reflexContext.getUid();
		String changeaccountTopUri = getChangeaccountUriByUid(uid);
		String changeaccountEmailUri = changeaccountTopUri + UserManagerDefaultConst.URI_EMAIL;
		String changeaccountNicknameUri = changeaccountTopUri + UserManagerDefaultConst.URI_NICKNAME;
		String changeaccountPhashUri = changeaccountTopUri + UserManagerDefaultConst.URI_PHASH;
		String changeaccountVerifyUri = changeaccountTopUri + UserManagerDefaultConst.URI_VERIFY;
		String changeaccountErrorCountUri = changeaccountTopUri + UserManagerDefaultConst.URI_ERROR_COUNT;
		int expireSec = TaggingEnvUtil.getRxidMinute(serviceName) * 60;
		reflexContext.setCacheString(changeaccountEmailUri, newEmail, expireSec);
		if (!StringUtils.isBlank(newNickname)) {
			reflexContext.setCacheString(changeaccountNicknameUri, newNickname, expireSec);
		}
		if (!StringUtils.isBlank(newPassword)) {
			reflexContext.setCacheString(changeaccountPhashUri, newPassword, expireSec);
		}
		reflexContext.setCacheString(changeaccountVerifyUri, verifyCode, expireSec);
		reflexContext.setCacheLong(changeaccountErrorCountUri, 0, expireSec);
	}

	/**
	 * メール本文の変数置換.
	 * RXID、LINKは標準処理では送信先メールアドレスをアカウントとみなすため、アカウント更新処理では使用できない。
	 * こちらでログインアカウントの認証情報を埋め込む。
	 * @param message メール本文
	 * @param verifyCode 認証コード
	 * @param systemContext SystemContext
	 * @return ログイン情報を埋め込み編集したメール本文
	 */
	private String replaceMessage(String message, String verifyCode,
			SystemContext systemContext)
	throws IOException, TaggingException {
		// URLを取得
		String serviceName = systemContext.getServiceName();
		RequestInfo requestInfo = systemContext.getRequestInfo();
		ConnectionInfo connectionInfo = systemContext.getConnectionInfo();
		String uid = systemContext.getUid();
		String account = systemContext.getAccount();
		String url = getUrl(serviceName, requestInfo, connectionInfo);

		String retMessage = message;

		EMailManager emailManager = TaggingEnvUtil.getEMailManager();
		retMessage = emailManager.replaceRXID(retMessage, uid, account, url, systemContext);
		retMessage = emailManager.replaceLink(retMessage, uid, account, url, systemContext);
		return replaceVerify(retMessage, verifyCode);
	}

	/**
	 * URLを取得.
	 * コンテキストパスまで
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return URL
	 */
	private String getUrl(String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		ServiceBlogic serviceBlogic = new ServiceBlogic();
		return serviceBlogic.getRedirectUrlContextPath(serviceName,
				requestInfo, connectionInfo);
	}

	/**
	 * メッセージの指定部分を認証コードに変換.
	 * ${VERYFY}の部分を認証コードに変換する。
	 * @param message メッセージ
	 * @param verifyCode 認証コード
	 * @return 変換したメッセージ
	 */
	private String replaceVerify(String message, String verifyCode) {
		return replaceAll(message, UserManagerDefaultConst.REPLACE_REGEX_VERIFY, verifyCode);
	}

	/**
	 * メッセージの指定部分を認証コードに変換.
	 * ${PASSRESET_TOKEN}の部分をパスワード変更一時トークン+RXIDに変換する。
	 * @param message メッセージ
	 * @param passresetToken パスワード変更一時トークン
	 * @param rxid RXID
	 * @return 変換したメッセージ
	 */
	private String replacePassresetToken(String message, String passresetToken, String rxid) {
		StringBuilder sb = new StringBuilder();
		sb.append(RequestParam.PARAM_PASSRESET_TOKEN);
		sb.append("=");
		sb.append(passresetToken);
		sb.append("&");
		sb.append(RequestParam.PARAM_RXID);
		sb.append("=");
		sb.append(rxid);
		return replaceAll(message, UserManagerDefaultConst.REPLACE_REGEX_PASSRESET_TOKEN, sb.toString());
	}

	/**
	 * メッセージの指定部分を指定値に変換.
	 * @param message メッセージ
	 * @param regex 変換対象文字列
	 * @param replacement 変換後の値
	 * @return 変換したメッセージ
	 */
	private String replaceAll(String message, String regex, String replacement) {
		return StringUtils.replaceAll(message, regex, StringUtils.null2blank(replacement));
	}

	/**
	 * アカウント変更に使用するURI(/_changeaccount/{uid})を取得.
	 * @param uid UID
	 * @return アカウント変更に使用するURI(/_changeaccount/{uid})
	 */
	public String getChangeaccountUriByUid(String uid) {
		if (!StringUtils.isBlank(uid)) {
			StringBuilder sb = new StringBuilder();
			sb.append(UserManagerDefaultConst.URI_CHANGEACCOUNT);
			sb.append("/");
			sb.append(uid);
			return sb.toString();
		}
		return null;
	}

	/**
	 * アカウント更新.
	 * @param verifyCode 認証コード
	 * @param reflexContext ReflexContext
	 * @return ユーザのトップエントリー
	 */
	public EntryBase verifyChangeaccount(String verifyCode, ReflexContext reflexContext)
	throws IOException, TaggingException {
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		ReflexAuthentication auth = reflexContext.getAuth();
		String serviceName = auth.getServiceName();
		SystemContext systemContext = new SystemContext(auth, requestInfo, connectionInfo);
		String uid = auth.getUid();

		// キャッシュのエラーカウントが一定回数を超えている場合はエラー。
		// キー:/_changeaccount/{UID}/error_count
		String changeaccountTopUri = getChangeaccountUriByUid(uid);
		String changeaccountEmailUri = changeaccountTopUri + UserManagerDefaultConst.URI_EMAIL;
		String changeaccountNicknameUri = changeaccountTopUri + UserManagerDefaultConst.URI_NICKNAME;
		String changeaccountPhashUri = changeaccountTopUri + UserManagerDefaultConst.URI_PHASH;
		String changeaccountVerifyUri = changeaccountTopUri + UserManagerDefaultConst.URI_VERIFY;
		String changeaccountErrorCountUri = changeaccountTopUri + UserManagerDefaultConst.URI_ERROR_COUNT;

		Long verifyErrorCnt = systemContext.getCacheLong(changeaccountErrorCountUri);
		if (verifyErrorCnt != null && verifyErrorCnt > UserManagerDefaultConst.VERIFY_FAILED_COUNT_DEFAULT) {
			AuthenticationException ae = new AuthenticationException("Verify failed count exceeded.");
			ae.setSubMessage("Verify failed count exceeded: " + verifyErrorCnt);
			throw ae;
		}

		// キャッシュから認証コードを取得する。キー:/_changeaccount/{UID}/verify
		String newEmail = systemContext.getCacheString(changeaccountEmailUri);
		String newNickname = systemContext.getCacheString(changeaccountNicknameUri);
		String newPassword = systemContext.getCacheString(changeaccountPhashUri);
		String verifyCodeOfCache = systemContext.getCacheString(changeaccountVerifyUri);
		if (StringUtils.isBlank(newEmail) || StringUtils.isBlank(verifyCodeOfCache)) {
			// 有効期限切れ、または登録なし
			AuthenticationException ae = new AuthenticationException("The account change has timed out.");
			ae.setSubMessage("The account change has timed out : " + newEmail);
			throw ae;
		}

		// 認証コード確認
		if (!verifyCode.equals(verifyCodeOfCache)) {
			// 認証コード不一致
			// エラーの場合、キャッシュにエラー回数を加算する。キー:/_user/{UID}/changeaccount_error
			systemContext.incrementCache(changeaccountErrorCountUri, 1);
			AuthenticationException ae = new AuthenticationException("The verification codes do not match.");
			StringBuilder sb = new StringBuilder();
			sb.append("The verification codes do not match. account=");
			sb.append(auth.getAccount());
			sb.append(", newEmail=");
			sb.append(newEmail);
			sb.append(", failed count=");
			long failedCount = 0;
			if (verifyErrorCnt != null) {
				failedCount = verifyErrorCnt;
			}
			sb.append(failedCount);
			ae.setSubMessage(sb.toString());
			throw ae;
		}

		String newAccount = UserUtil.editAccount(newEmail);
		EntryBase interimUserTopEntry = null;
		// 指定されたアカウントで登録がないか再確認
		EntryBase tmpUserTopEntry = getUserTopEntryByAccount(newAccount, systemContext);
		if (tmpUserTopEntry != null) {
			String tmpUserStatus = getUserstatus(tmpUserTopEntry);
			if (Constants.USERSTATUS_INTERIM.equals(tmpUserStatus)) {
				// 仮登録ユーザを削除
				// 一旦ステータスをNothingにする。
				tmpUserTopEntry.title = UserManagerDefaultConst.USER_NOTHING_PREFIX + 
						tmpUserTopEntry.title;
				setUserstatus(tmpUserTopEntry, Constants.USERSTATUS_NOTHING);
				systemContext.put(tmpUserTopEntry);
				interimUserTopEntry = tmpUserTopEntry;
			} else {
				// このアカウントでは登録済みのためエラー
				String msg = "The account is already registered. " + newAccount;
				PermissionException pe = new PermissionException(msg);
				throw pe;
			}
		}
		
		try {
			// ユーザ情報を更新する。
			// アカウント
			String userTopUri = getUserTopUriByUid(uid);
			EntryBase userTopEntry = reflexContext.getEntry(userTopUri, false);
			String oldAccount = userTopEntry.title;
			userTopEntry.title = newAccount;
			if (!StringUtils.isBlank(newNickname)) {
				userTopEntry.subtitle = newNickname;
			}
			// メールアドレス
			boolean setNewEmail = false;
			List<Contributor> contributors = userTopEntry.contributor;
			for (Contributor contributor : contributors) {
				if (!StringUtils.isBlank(contributor.email)) {
					String tmpAccount = UserUtil.editAccount(contributor.email);
					if (oldAccount.equals(tmpAccount)) {
						contributor.email = newEmail;
						setNewEmail = true;
					}
				}
			}
			if (!setNewEmail) {
				Contributor contributor = new Contributor();
				contributor.email = newEmail;
				contributors.add(contributor);
			}
			FeedBase feed = TaggingEntryUtil.createFeed(serviceName);
			feed.addEntry(userTopEntry);
			// パスワード設定 : パスワードの指定がない場合のみ設定可(ソーシャルアカウントにメールアドレスログイン情報を追加する場合)
			if (!StringUtils.isBlank(newPassword)) {
				EntryBase userAuthEntry = getUserAuthEntryByUid(uid, systemContext);
				if (userAuthEntry == null) {
					// userAuthEntry生成
					userAuthEntry = createAuthEntry(newPassword, uid, serviceName);
					feed.addEntry(userAuthEntry);
				} else {
					String currentPassword = getPassword(userAuthEntry);
					if (StringUtils.isBlank(currentPassword)) {
						setAuthToEntry(userAuthEntry, newPassword);
						feed.addEntry(userAuthEntry);
					}
				}
			}
			// 更新
			systemContext.put(feed);
	
			// キャッシュの削除
			systemContext.deleteCacheString(changeaccountVerifyUri);
			if (!StringUtils.isBlank(newPassword)) {
				systemContext.deleteCacheString(changeaccountPhashUri);
			}
			if (!StringUtils.isBlank(newNickname)) {
				systemContext.deleteCacheString(changeaccountNicknameUri);
			}
			systemContext.deleteCacheString(changeaccountEmailUri);
			systemContext.deleteCacheLong(changeaccountErrorCountUri);

			return userTopEntry;

		} finally {
			// 新アカウントが仮登録状態だった場合、旧UIDのデータを削除する。
			if (interimUserTopEntry != null) {
				systemContext.deleteFolder(interimUserTopEntry.getMyUri(), true, true);
			}
		}
	}

	/**
	 * ユーザトップエントリー取得のためのURIを編集
	 * @param limit 一覧最大件数
	 * @param cursorStr カーソル
	 * @return ユーザトップエントリー取得のためのURI
	 */
	private String editUserTopFeedUri(Integer limit, String cursorStr) {
		StringBuilder sb = new StringBuilder();
		sb.append(getUserUri());
		boolean isFirst = true;
		if (limit != null) {
			sb.append("?");
			isFirst = false;
			sb.append(RequestParam.PARAM_LIMIT);
			sb.append("=");
			sb.append(limit);
		}
		if (!StringUtils.isBlank(cursorStr)) {
			if (isFirst) {
				sb.append("?");
				isFirst = false;
			} else {
				sb.append("&");
			}
			sb.append(RequestParam.PARAM_NEXT);
			sb.append("=");
			//sb.append(UrlUtil.urlEncode(cursorStr));
			sb.append(cursorStr);
		}
		return sb.toString();
	}

	/**
	 * ユーザ名によるユーザEntry検索のURIを取得
	 * @return ユーザ名によるユーザEntry検索のURI
	 */
	private String getUserUri() {
		return Constants.URI_USER;
	}

	/**
	 * ユーザのトップエントリーURI(/_user/{uid})を取得.
	 * @param uid UID
	 * @return ユーザのトップエントリーURI(/_user/{uid})
	 */
	public String getUserTopUriByUid(String uid) {
		if (!StringUtils.isBlank(uid)) {
			StringBuilder sb = new StringBuilder();
			sb.append(getUserUri());
			sb.append("/");
			sb.append(uid);
			return sb.toString();
		}
		return null;
	}

	/**
	 * 認証情報を格納するキーを取得します.
	 * <p>
	 * 認証情報のキーは「/_user/{uid}/auth」です。
	 * </p>
	 * @param uid ユーザ番号
	 * @return 認証情報を格納するキー
	 */
	public String getUserAuthUriByUid(String uid) {
		StringBuilder sb = new StringBuilder();
		sb.append(getUserTopUriByUid(uid));
		sb.append(Constants.URI_LAYER_AUTH);
		return sb.toString();
	}

	/**
	 * グループ情報を格納する親キーを取得します.
	 * <p>
	 * 認証情報は、設定情報「group.uri」で設定したパターンから作成したキーのエントリーに格納されます。
	 * 設定がない場合はnullを返却します。
	 * グループの親キーは「/_user/{uid}/group」です。
	 * </p>
	 * @param uid UID
	 * @return グループ情報を格納する親キー
	 */
	public String getGroupUriByUid(String uid) {
		StringBuilder sb = new StringBuilder();
		sb.append(getUserTopUriByUid(uid));
		sb.append(Constants.URI_LAYER_GROUP);
		return sb.toString();
	}

	/**
	 * グループ情報を取得します.
	 * @param uid UID
	 * @param reflexContext ReflexContext
	 * @return グループリスト
	 */
	public List<String> getGroupsByUid(String uid, ReflexContext reflexContext)
	throws IOException, TaggingException {
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		String groupParentUri = getGroupUriByUid(uid);
		SystemContext systemContext = null;
		if (reflexContext instanceof SystemContext) {
			systemContext = (SystemContext)reflexContext;
		} else {
			systemContext = new SystemContext(reflexContext.getServiceName(),
					requestInfo, reflexContext.getConnectionInfo());
		}
		List<String> groups = new ArrayList<String>();
		int len = TaggingEnvUtil.getEntryNumberLimit();
		String cursorStr = null;
		do {
			String param = editUri(groupParentUri, len, cursorStr);
			FeedBase feed = systemContext.getFeed(param, true);
			cursorStr = TaggingEntryUtil.getCursorFromFeed(feed);
			if (feed != null && feed.entry != null) {
				for (EntryBase groupEntry : feed.entry) {
					// グループEntryは「/{グループURI}/{参加者UID}」。
					// グループURIとして、親URIを取得する。
					String childIdUri = TaggingEntryUtil.getUriById(groupEntry.id);
					String groupUri = TaggingEntryUtil.removeLastSlash(
							TaggingEntryUtil.getParentUri(childIdUri));
					if (GroupUtil.isValidGroup(groupEntry, systemContext)) {
						groups.add(groupUri);
					}
				}
			}
		} while (!StringUtils.isBlank(cursorStr));

		if (groups.isEmpty()) {
			return null;
		}
		return groups;
	}

	/**
	 * 指定したURI配下のキーのエントリーで自分が署名していないものを取得.
	 * ただしすでにグループ参加状態のものは除く。
	 * @param uri 親キー
	 * @param reflexContext ReflexContext
	 * @return 親キー配下のエントリーで署名していないEntryリスト
	 */
	public FeedBase getNoGroupMember(String uri, ReflexContext reflexContext) 
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		// キーチェック
		CheckUtil.checkUri(uri);
		
		String uid = getUidByUri(uri);

		SystemContext systemContext = null;
		if (reflexContext instanceof SystemContext) {
			systemContext = (SystemContext)reflexContext;
		} else {
			systemContext = new SystemContext(reflexContext.getAuth(),
					reflexContext.getRequestInfo(), reflexContext.getConnectionInfo());
		}
		
		List<EntryBase> noSignatureEntries = new ArrayList<>();
		// 検索
		int len = TaggingEnvUtil.getEntryNumberLimit();
		String cursorStr = null;
		do {
			String param = editUri(uri, len, cursorStr);
			FeedBase feed = reflexContext.getFeed(param, true);
			cursorStr = TaggingEntryUtil.getCursorFromFeed(feed);
			if (feed != null && feed.entry != null) {
				for (EntryBase entry : feed.entry) {
					// 管理グループの場合チェック対象としない
					String idUri = TaggingEntryUtil.getUriById(entry.id);
					String groupUri = TaggingEntryUtil.removeLastSlash(
							TaggingEntryUtil.getParentUri(idUri));
					if (!GroupUtil.isAdministrativeGroup(idUri) &&
							(StringUtils.isBlank(uid) || 
									!GroupUtil.isMyGroup(uid, groupUri, systemContext))) {
						// 署名チェック
						String groupMemberUri = entry.getMyUri();
						SignatureBlogic signatureBlogic = new SignatureBlogic();
						if (!signatureBlogic.isValidSignature(entry, groupMemberUri, systemContext)) {
							noSignatureEntries.add(entry);
						}
					}
				}
			}
		} while (!StringUtils.isBlank(cursorStr));

		if (noSignatureEntries.isEmpty()) {
			return null;
		}
		FeedBase resultFeed = TaggingEntryUtil.createFeed(serviceName);
		resultFeed.entry = noSignatureEntries;
		return resultFeed;
	}
	
	/**
	 * グループに参加登録する.
	 * グループ参加エントリーの登録処理。署名はなし。
	 * @param group グループ名
	 * @param selfid 自身のグループエイリアスのselfid (/_user/{UID}/group/{selfid})
	 * @param feed UIDリスト(ユーザ管理者・グループ管理者のみ指定可)
	 * @param reflexContext ReflexContext
	 * @return グループエントリー
	 */
	public FeedBase addGroup(String group, String selfid, FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException {
		ReflexAuthentication auth = reflexContext.getAuth();
		String serviceName = auth.getServiceName();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		// グループ管理者の場合の調査用
		SystemContext systemContext = new SystemContext(auth, requestInfo, connectionInfo);
				
		List<String> uids = new ArrayList<>();
		if (TaggingEntryUtil.isExistData(feed)) {
			// Feed指定の場合、ユーザ管理者のみ指定可。
			AclBlogic aclBlogic = new AclBlogic();
			// この処理の前でユーザ管理者かグループ管理者のチェックは行っているため、
			// $useradminでなければ$groupadminである。
			List<String> groupadminGroupnames = new ArrayList<>();
			try {
				// $useradminかどうか
				aclBlogic.checkAuthedGroup(auth, Constants.URI_GROUP_USERADMIN);
			} catch (PermissionException e) {
				// $groupadminの場合、対象のグループ名を抽出
				List<String> myGroups = auth.getGroups();
				for (String myGroup : myGroups) {
					if (myGroup.startsWith(GroupConst.URI_GROUP_GROUPADMIN_PREFIX)) {
						String groupadminGroupname = myGroup.substring(URI_GROUP_GROUPADMIN_PREFIX_LEN);
						if (logger.isDebugEnabled()) {
							logger.debug(LogUtil.getRequestInfoStr(reflexContext.getRequestInfo()) +
									"[addGroup] groupadminGroupname=" + groupadminGroupname);
						}
						groupadminGroupnames.add(groupadminGroupname);
					}
				}
				if (groupadminGroupnames.isEmpty()) {
					PermissionException pe = new PermissionException();
					pe.setSubMessage("You must be a useradmin or groupadmin to add a group.");
					throw pe;
				}
			}
			for (EntryBase entry : feed.entry) {
				// UIDを抽出
				String uid = getUidByUri(entry.getMyUri());
				uids.add(uid);
				if (!groupadminGroupnames.isEmpty()) {
					// グループ管理者の場合、グループ参加者に対してのみ処理が可能
					boolean inGroup = false;
					for (String groupadminGroupname : groupadminGroupnames) {
						String tmpUri = getGroupUserUri(uid, groupadminGroupname);
						EntryBase tmpGroupEntry = systemContext.getEntry(tmpUri);
						if (tmpGroupEntry != null) {
							inGroup = true;
							break;
						}
					}
					if (!inGroup) {
						PermissionException pe = new PermissionException();
						pe.setSubMessage("The specified user is not a member of any group you manage.");
						throw pe;
					}
				}
			}

		} else {
			// Feed指定でない場合、ログインユーザが対象
			String uid = auth.getUid();
			uids.add(uid);
		}
		CheckUtil.checkNotNull(uids, "uid");
		
		List<EntryBase> groupEntries = new ArrayList<>();
		for (String uid : uids) {
			EntryBase groupEntry = createGroupEntry(group, selfid, uid, serviceName);
			groupEntries.add(groupEntry);
		}
		FeedBase groupFeed = TaggingEntryUtil.createFeed(serviceName);
		groupFeed.entry = groupEntries;
		return reflexContext.post(groupFeed);
	}
	
	/**
	 * グループエントリーを生成
	 * @param group グループキー
	 * @param selfid 自身のグループエイリアスのselfid (/_user/{UID}/group/{selfid})
	 * @param uid UID
	 * @param serviceName サービス名
	 * @return グループエントリー
	 */
	private EntryBase createGroupEntry(String group, String selfid, String uid, String serviceName) {
		// {グループ名}/{UID}エントリーを登録
		String groupIdUri = getGroupIdUriByUid(group, uid);
		// 自身のグループエイリアスを追加。(/_user/{UID}/group/{selfid})
		String groupUserAlias = getGroupUserUri(uid, selfid);

		EntryBase groupEntry = createEntry(groupIdUri, serviceName);
		groupEntry.addAlternate(groupUserAlias);

		return groupEntry;
	}

	/**
	 * グループに参加する.
	 * グループ作成者がユーザへの参加承認をしている状態の時、ユーザがグループに参加する。
	 * 自身のグループエイリアス作成(登録がない場合→再加入を想定)と、自身のグループエイリアスへの署名を行う。
	 * @param group グループ名
	 * @param selfid 自身のグループエイリアスのselfid (/_user/{UID}/group/{selfid})
	 * @param reflexContext ReflexContext
	 * @return グループエントリー
	 */
	public EntryBase joinGroup(String group, String selfid, ReflexContext reflexContext)
	throws IOException, TaggingException {
		ReflexAuthentication auth = reflexContext.getAuth();
		String uid = auth.getUid();
		
		SystemContext systemContext = new SystemContext(auth, reflexContext.getRequestInfo(),
				reflexContext.getConnectionInfo());

		// {グループ名}/{UID}エントリーを検索。存在しない場合エラー。
		String groupIdUri = getGroupIdUriByUid(group, uid);
		EntryBase groupEntry = systemContext.getEntry(groupIdUri);
		if (groupEntry == null) {
			PermissionException pe = new PermissionException();
			pe.setSubMessage("No group entry exists. " + groupIdUri);
			throw pe;
		}
		// {グループ名}/{UID}エントリーの署名を検証。署名が正しくない場合エラー。
		SignatureBlogic signatureBlogic = new SignatureBlogic();
		if (!signatureBlogic.isValidSignature(groupEntry, groupIdUri, systemContext)) {
			PermissionException pe = new PermissionException();
			pe.setSubMessage("Invalid signature. " + groupIdUri);
			throw pe;
		}
		// 自身のグループエイリアスがなければ追加。(/_user/{UID}/group/{selfid})
		String groupUserAlias = getGroupUserUri(uid, selfid);
		List<String> aliases = groupEntry.getAlternate();
		boolean addAlias = true;
		if (aliases != null) {
			for (String alias : aliases) {
				if (groupUserAlias.equals(alias)) {
					addAlias = false;
					break;
				}
			}
		}
		if (addAlias) {
			groupEntry.addAlternate(groupUserAlias);
		}
		// 自身のグループエイリアスに署名付与。
		signatureBlogic.sign(groupUserAlias, groupEntry, reflexContext);
		// 更新
		return systemContext.put(groupEntry);
	}
	
	/**
	 * グループから退会する.
	 * グループエントリーの、自身のグループエイリアスを削除する。
	 * @param group グループ名
	 * @param reflexContext ReflexContext
	 * @return 退会したグループエントリー
	 */
	public EntryBase leaveGroup(String group, ReflexContext reflexContext)
	throws IOException, TaggingException {
		ReflexAuthentication auth = reflexContext.getAuth();
		String uid = auth.getUid();
		
		SystemContext systemContext = new SystemContext(auth, reflexContext.getRequestInfo(),
				reflexContext.getConnectionInfo());

		// {グループ名}/{UID}エントリーを検索。存在しない場合エラー。
		String groupIdUri = getGroupIdUriByUid(group, uid);
		EntryBase groupEntry = systemContext.getEntry(groupIdUri);
		if (groupEntry == null) {
			PermissionException pe = new PermissionException();
			pe.setSubMessage("No group entry exists. " + groupIdUri);
			throw pe;
		}
		// 自身のグループエイリアスを削除(/_user/{UID}/group/{selfid})。署名も同時に削除される
		String userGroupParentUri = TaggingEntryUtil.editSlash(getGroupUriByUid(uid));
		List<String> aliases = groupEntry.getAlternate();
		String myGroupUri = null;
		if (aliases != null) {
			for (String alias : aliases) {
				String parentAlias = TaggingEntryUtil.getParentUri(alias);
				if (userGroupParentUri.equals(parentAlias)) {
					myGroupUri = alias;
					break;
				}
			}
		}
		if (StringUtils.isBlank(myGroupUri)) {
			PermissionException pe = new PermissionException();
			pe.setSubMessage("No my group alias exists. " + userGroupParentUri + "_");
			throw pe;
		}
		return reflexContext.delete(myGroupUri);
	}
	
	/**
	 * グループ参加エントリー削除.
	 * @param group グループ名
	 * @param feed UIDリスト(ユーザ管理者・グループ管理者のみ指定可)
	 * @param reflexContext ReflexContext
	 * @return 退会したグループエントリー
	 */
	public FeedBase removeGroup(String group, FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException {
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		// グループ管理者の場合の調査用
		SystemContext systemContext = new SystemContext(auth, requestInfo, connectionInfo);
		
		List<String> uids = new ArrayList<>();
		if (TaggingEntryUtil.isExistData(feed)) {
			// Feed指定の場合、ユーザ管理者のみ指定可。
			AclBlogic aclBlogic = new AclBlogic();
			// この処理の前でユーザ管理者かグループ管理者のチェックは行っているため、
			// $useradminでなければ$groupadminである。
			List<String> groupadminGroupnames = new ArrayList<>();
			try {
				// $useradminかどうか
				aclBlogic.checkAuthedGroup(auth, Constants.URI_GROUP_USERADMIN);
			} catch (PermissionException e) {
				// $groupadminの場合、対象のグループ名を抽出
				List<String> myGroups = auth.getGroups();
				for (String myGroup : myGroups) {
					if (myGroup.startsWith(GroupConst.URI_GROUP_GROUPADMIN_PREFIX)) {
						String groupadminGroupname = myGroup.substring(URI_GROUP_GROUPADMIN_PREFIX_LEN);
						if (logger.isDebugEnabled()) {
							logger.debug(LogUtil.getRequestInfoStr(reflexContext.getRequestInfo()) +
									"[addGroup] groupadminGroupname=" + groupadminGroupname);
						}
						groupadminGroupnames.add(groupadminGroupname);
					}
				}
				if (groupadminGroupnames.isEmpty()) {
					PermissionException pe = new PermissionException();
					pe.setSubMessage("You must be a useradmin or groupadmin to add a group.");
					throw pe;
				}
			}
			for (EntryBase entry : feed.entry) {
				// UIDを抽出
				String uid = getUidByUri(entry.getMyUri());
				uids.add(uid);
				if (!groupadminGroupnames.isEmpty()) {
					// グループ管理者の場合、グループ参加者に対してのみ処理が可能
					boolean inGroup = false;
					for (String groupadminGroupname : groupadminGroupnames) {
						String tmpUri = getGroupUserUri(uid, groupadminGroupname);
						EntryBase tmpGroupEntry = systemContext.getEntry(tmpUri);
						if (tmpGroupEntry != null) {
							inGroup = true;
							break;
						}
					}
					if (!inGroup) {
						PermissionException pe = new PermissionException();
						pe.setSubMessage("The specified user is not a member of any group you manage.");
						throw pe;
					}
				}
			}
		}
		CheckUtil.checkNotNull(uids, "uid");
		
		List<String> groupUris = new ArrayList<>();
		for (String uid : uids) {
			String groupIdUri = getGroupIdUriByUid(group, uid);
			groupUris.add(groupIdUri);
		}
		return reflexContext.delete(groupUris);
	}

	/**
	 * グループのID URIを取得.
	 *   {グループ名}/{UID}
	 * @param group グループ名
	 * @param uid UID
	 * @return グループのID URI
	 */
	private String getGroupIdUriByUid(String group, String uid) {
		StringBuilder sb = new StringBuilder();
		sb.append(group);
		sb.append("/");
		sb.append(uid);
		return sb.toString();
	}
	
	/**
	 * グループのユーザエイリアスURIを取得.
	 *   /_user/{UID}/group/{selfid}
	 * @param uid UID
	 * @param selfid selfid
	 * @return グループのユーザエイリアスURI
	 */
	private String getGroupUserUri(String uid, String selfid) {
		StringBuilder sb = new StringBuilder();
		sb.append(getGroupUriByUid(uid));
		sb.append("/");
		sb.append(selfid);
		return sb.toString();
	}

	/**
	 * アクセスキーを格納するキーを取得します.
	 * @param uid UID
	 * @return アクセスキーを格納するキー
	 */
	public String getAccessKeyUriByUid(String uid) {
		StringBuilder sb = new StringBuilder();
		sb.append(getUserTopUriByUid(uid));
		sb.append(Constants.URI_LAYER_ACCESSKEY);
		return sb.toString();
	}

	/**
	 * ユーザ登録リクエストのエントリーから、メールアドレスを取得します。
	 * <p>
	 * ユーザエントリーのuriに設定する、以下の形式の文字列からメールアドレスを取り出します。
	 * パスワード設定が無い場合もエラーとしません。
	 * <ul>
	 * <li>urn:vte.cx:auth:{email},{password}</li>
	 * </ul>
	 * </p>
	 * @param entry ユーザ登録リクエストのエントリー
	 * @return メールアドレス
	 */
	public String getEmailByAdduser(EntryBase entry) {
		// [0]メールアドレス、[1]パスワード、[2]アカウント(メールアドレスを小文字に編集し、一部文字を除去)、[3]ニックネーム
		// メールアドレス形式でないとエラー
		String[] userAuthInfo = checkUserAuthInfo(entry, UserAuthType.ADDUSER,
				true, false, false);
		return userAuthInfo[0];
	}

	/**
	 * 認証情報エントリーから、パスワードを取得します。
	 * <p>
	 * ユーザエントリーのuriに設定する、以下の形式のWSSE情報文字列からパスワードを取り出します。
	 * <ul>
	 * <li>urn:vte.cx:auth:,{password}</li>
	 * </ul>
	 * (2015.2.19) usernameは/_authエントリーに設定せず、ユーザトップエントリーのものを使用するよう変更。
	 * </p>
	 * @param entry 認証情報エントリー
	 * @return パスワード
	 */
	public String getPassword(EntryBase entry) {
		if (entry != null && entry.contributor != null) {
			for (Contributor cont : entry.contributor) {
				if (!StringUtils.isBlank(cont.uri) &&
						cont.uri.startsWith(AtomConst.URN_PREFIX_AUTH)) {
					int idx = cont.uri.indexOf(UserManagerDefaultConst.URN_AUTH_PASSWORD_START);
					if (idx > 0) {
						return cont.uri.substring(idx + 1);
					}
				}
			}
		}
		return null;
	}

	/**
	 * ユーザトップエントリーから、ニックネームを取得します。
	 * @param entry ユーザトップエントリー
	 * @return ニックネーム
	 */
	public String getNickname(EntryBase entry) {
		if (entry != null) {
			return entry.subtitle;
		}
		return null;
	}

	/**
	 * ユーザトップエントリーから、メールアドレスを取得します。
	 * @param entry ユーザトップエントリー
	 * @return メールアドレス
	 */
	public String getEmail(EntryBase entry) {
		if (entry != null) {
			if (entry.contributor != null) {
				for (Contributor contrbutor : entry.contributor) {
					if (!StringUtils.isBlank(contrbutor.email)) {
						return contrbutor.email;
					}
				}
			}
			// 設定が無い場合はアカウント
			return entry.title;
		}
		return null;
	}

	/**
	 * ユーザトップエントリーの検索条件をキーに付加します.
	 * @param uri キー
	 * @param account アカウント
	 * @return ユーザトップエントリーの検索条件を付加したuri
	 */
	public String editUserTopQueryString(String account) {
		StringBuilder sb = new StringBuilder();
		sb.append(getUserUri());
		sb.append("?");
		sb.append(UserManagerDefaultConst.FIELD_ACCOUNT);
		sb.append("=");
		sb.append(account);
		return sb.toString();
	}

	/**
	 * URIからUIDを取得します.
	 * @param uri URI
	 * @return UID
	 */
	public String getUidByUri(String uri) {
		if (uri != null) {
			// /_user/{第2階層} を取得
			// 第1階層の先頭がアンダースコア(_)の場合は以下メソッドでnullが返ってくる。
			String userTopUri = getUserTopUriByUri(uri);
			if (!StringUtils.isBlank(userTopUri)) {
				return userTopUri.substring(URI_USER_SLASH_LEN);
			}
		}
		return null;
	}

	/**
	 * URIからユーザのトップエントリーURI(/_user/{uid})を取得.
	 * 第二階層より下の階層を削る。
	 * @param uri URI
	 * @return ユーザのトップエントリーURI(/_user/{uid})
	 */
	public String getUserTopUriByUri(String uri) {
		String userTopUri = null;
		if (!StringUtils.isBlank(uri) && uri.startsWith(URI_USER_SLASH) &&
				uri.length() > URI_USER_SLASH_LEN) {
			int idx2 = uri.indexOf("/", URI_USER_SLASH_LEN + 1);
			if (idx2 > -1) {
				userTopUri = uri.substring(0, idx2);
			} else {
				userTopUri = uri;
			}
		}

		return userTopUri;
	}

	/**
	 * WSSE認証内容をエントリーに設定します.
	 * <ol>
	 * <li>エントリーのキーを、ユーザ認証エントリ(/{uid}/_auth)にします。</li>
	 * <li>contributorにWSSE認証内容を設定します。</li>
	 * </ol>
	 * @param entry 編集対象エントリー
	 * @param password パスワード
	 */
	private void setAuthToEntry(EntryBase entry, String password) {
		if (entry != null) {
			Contributor newContributor = new Contributor();
			newContributor.uri = UserUtil.createAuthUrn(password);
			List<Contributor> newContributors = new ArrayList<Contributor>();
			newContributors.add(newContributor);

			List<Contributor> contributors = entry.getContributor();
			if (contributors != null) {
				for (Contributor tmpContributor : contributors) {
					// 現在登録されているwsse情報は除く
					if (tmpContributor.uri != null &&
							tmpContributor.uri.startsWith(Constants.URN_PREFIX_AUTH)) {
						continue;
					}
					newContributors.add(tmpContributor);
				}
			}

			// 最後に設定
			entry.setContributor(newContributors);
		}
	}

	/**
	 * ユーザステータスを設定.
	 * <p>
	 * ユーザステータスは、ユーザトップエントリーのsummaryに設定されています。
	 * </p>
	 * @param entry エントリー
	 * @param userstatus ユーザステータス
	 */
	protected void setUserstatus(EntryBase entry, String userstatus) {
		if (entry != null) {
			entry.summary = userstatus;
		}
	}

	/**
	 * contributorのリストにUserSecretを設定して返却します.
	 * リストがnullの場合は生成して返却します.
	 * リストに既にUserSecretが設定されている場合は、新しく生成したUserSecretに置き換えます.
	 * @param contributors Contributorリスト
	 * @return UserSecret設定Contributorを追加したContributorリスト
	 */
	private List<Contributor> createUserSecret(List<Contributor> contributors) {
		List<Contributor> retConts = new ArrayList<Contributor>();
		if (contributors != null) {
			for (Contributor cont : contributors) {
				if (cont.uri == null ||
						!cont.uri.startsWith(Constants.URN_PREFIX_USERSECRET)) {
					retConts.add(cont);
				}
			}
		}
		retConts.add(createUserSecret());
		return retConts;
	}

	/**
	 * UserSecretを生成し、Contributorに設定して返却します.
	 * @return UserSecretをセットしたContributor
	 */
	private Contributor createUserSecret() {
		// usersecretを設定する。
		Contributor contUserSecret = new Contributor();
		String secret = UserUtil.createRandomString(UserManagerDefaultConst.USERSECRET_LEN);
		contUserSecret.uri = Constants.URN_PREFIX_USERSECRET + secret;
		return contUserSecret;
	}

	/**
	 * ユーザトップエントリーを生成
	 * @param uid UID
	 * @param email メールアドレス
	 * @param account アカウント
	 * @param nickname ニックネーム
	 * @param serviceName サービス名
	 * @return Entry
	 */
	private EntryBase createInterimUserTopEntry(String uid, String email,
			String account, String nickname, String serviceName) {
		EntryBase userTopEntry = TaggingEntryUtil.createEntry(serviceName);
		String userTopUri = getUserTopUriByUid(uid);
		userTopEntry.setMyUri(userTopUri);
		userTopEntry.title = account;
		userTopEntry.subtitle = nickname;
		if (!StringUtils.isBlank(email)) {
			Contributor contributor = new Contributor();
			contributor.email = email;
			userTopEntry.addContributor(contributor);
		}
		setUserstatus(userTopEntry, Constants.USERSTATUS_INTERIM);
		return userTopEntry;
	}

	/**
	 * WSSE認証内容を設定したエントリーを作成します。
	 * @param password パスワード
	 * @param uid ユーザ番号
	 * @param serviceName サービス名
	 * @return WSSE認証内容を設定したエントリー
	 */
	private EntryBase createAuthEntry(String password, String uid, String serviceName) {
		EntryBase userEntry = TaggingEntryUtil.createEntry(serviceName);
		String userUri = getUserAuthUriByUid(uid);
		userEntry.setMyUri(userUri);
		// パスワード設定
		if (password != null) {
			setAuthToEntry(userEntry, password);
		}
		// usersecretの生成
		userEntry.contributor = createUserSecret(userEntry.contributor);
		return userEntry;
	}

	/**
	 * エントリーを生成.
	 * @param uri URI
	 * @param serviceName サービス名
	 * @return エントリー
	 */
	private EntryBase createEntry(String uri, String serviceName) {
		if (StringUtils.isBlank(uri)) {
			return null;
		}
		EntryBase entry = TaggingEntryUtil.createEntry(serviceName);
		entry.setMyUri(uri);
		return entry;
	}

	/**
	 * グループエントリーの編集
	 * @param entry グループエントリー
	 * @param groupUri グループエントリーURI
	 * @param uid UID
	 * @param groupName グループ管理者による登録の場合、グループ名
	 * @param serviceName サービス名
	 * @return グループエントリー
	 */
	private EntryBase editGroupEntry(EntryBase entry, String groupUri,
			String uid, String groupName, String serviceName) {
		if (entry == null) {
			entry = createEntry(groupUri, serviceName);
		}
		entry.contributor = createGroupContributors(uid, groupName);
		return entry;
	}

	/**
	 * グループエントリーのContributorを作成
	 * @param uid UID
	 * @param groupName グループ管理者による登録の場合、グループ名
	 * @return グループエントリーのContributorリスト
	 */
	private List<Contributor> createGroupContributors(String uid, String groupName) {
		List<Contributor> contributors = new ArrayList<Contributor>();
		// サービス管理グループACL
		String tmpUrn = TaggingEntryUtil.getAclUrn(Constants.URI_GROUP_ADMIN,
				Constants.ACL_TYPE_CRUD);
		Contributor contributor = new Contributor();
		contributor.uri = tmpUrn;
		contributors.add(contributor);
		// ユーザ管理グループACL
		tmpUrn = TaggingEntryUtil.getAclUrn(Constants.URI_GROUP_USERADMIN,
				Constants.ACL_TYPE_CRUD);
		contributor = new Contributor();
		contributor.uri = tmpUrn;
		contributors.add(contributor);
		// 自ユーザACL
		tmpUrn = TaggingEntryUtil.getAclUrn(uid, Constants.ACL_TYPE_CRUD);
		contributor = new Contributor();
		contributor.uri = tmpUrn;
		contributors.add(contributor);
		// ログインユーザACL
		tmpUrn = TaggingEntryUtil.getAclUrn(Constants.ACL_USER_LOGGEDIN,
				Constants.ACL_TYPE_CREATE);
		contributor = new Contributor();
		contributor.uri = tmpUrn;
		contributors.add(contributor);
		// グループ管理者による登録の場合、グループ管理グループのACL
		if (!StringUtils.isBlank(groupName)) {
			String groupadminGroupUri = GroupUtil.getGroupadminGroup(groupName);
			tmpUrn = TaggingEntryUtil.getAclUrn(groupadminGroupUri,
					Constants.ACL_TYPE_CRUD);
			contributor = new Contributor();
			contributor.uri = tmpUrn;
			contributors.add(contributor);
		}
		return contributors;
	}

	/**
	 * アクセストークンを取得.
	 * @param reflexContext ReflexContext
	 * @return アクセストークン
	 */
	public String getAccessToken(ReflexContext reflexContext)
	throws IOException, TaggingException {
		ReflexAuthentication auth = reflexContext.getAuth();
		if (auth != null && auth.getUid() != null) {
			AccessTokenManager accessTokenManager =
					TaggingEnvUtil.getAccessTokenManager();
			return accessTokenManager.getAccessTokenByUid(auth.getUid(),
					reflexContext);
		}
		return null;
	}

	/**
	 * リンクトークンを取得.
	 * @param uri URI
	 * @param reflexContext ReflexContext
	 * @return リンクトークン
	 */
	public String getLinkToken(String uri, ReflexContext reflexContext)
	throws IOException, TaggingException {
		ReflexAuthentication auth = reflexContext.getAuth();
		if (auth != null && auth.getUid() != null) {
			AccessTokenManager accessTokenManager =
					TaggingEnvUtil.getAccessTokenManager();
			return accessTokenManager.getLinkToken(auth.getUid(), uri,
					reflexContext);
		}
		return null;
	}

	/**
	 * アクセスキー更新.
	 * @param reflexContext ReflexContext
	 */
	public void changeAccessKey(ReflexContext reflexContext)
	throws IOException, TaggingException {
		ReflexAuthentication auth = reflexContext.getAuth();
		String uid = auth.getUid();
		AccessTokenManager accessTokenManager = TaggingEnvUtil.getAccessTokenManager();
		// アクセスキー生成
		String accessKey = accessTokenManager.createAccessKeyStr();
		// アクセスキー更新
		accessTokenManager.putAccessKey(uid, accessKey, reflexContext);
	}

	/**
	 * ユーザ本登録時の追加エントリーを返却
	 * @param uid UID
	 * @param serviceName サービス名
	 * @param reflexContext ReflexContext
	 * @return ユーザ本登録時の追加エントリー
	 */
	protected List<EntryBase> addEntriesByStanderdUserInit(String uid,
			String serviceName, ReflexContext reflexContext) {
		List<EntryBase> entries = new ArrayList<EntryBase>();
		// システム管理サービスの場合、/_user/{UID}/service エントリーを作成する。
		if (TaggingEnvUtil.getSystemService().equals(serviceName)) {
			entries.add(createUserServiceEntry(uid, serviceName));
		}
		// /_user/{UID}/login_history エントリーを追加。
		EntryBase entry = TaggingEntryUtil.createEntry(serviceName);
		LoginLogoutBlogic loginLogoutBlogic = new LoginLogoutBlogic();
		entry.setMyUri(loginLogoutBlogic.getLoginHistoryUserFolderUri(uid));
		entries.add(entry);

		return entries;
	}

	/**
	 * /_user/{UID}/service エントリーを作成
	 * @param uid UID
	 * @param serviceName サービス名
	 * @return /{UID}/service エントリー
	 */
	private EntryBase createUserServiceEntry(String uid, String serviceName) {
		EntryBase entry = TaggingEntryUtil.createEntry(serviceName);
		String userServiceUri = TaggingServiceUtil.getUserServiceUri(uid);
		entry.setMyUri(userServiceUri);
		return entry;
	}

	/**
	 * URIが /_user/{UID} かどうかを判定
	 * @param uri URI
	 * @return UIDトップエントリーの場合true
	 */
	private boolean isUserTopUri(String uri) {
		return !StringUtils.isBlank(uri) &&
				uri.startsWith(URI_USER_SLASH) &&
				(uri.length() > URI_USER_SLASH_LEN) &&
				((uri.substring(URI_USER_SLASH_LEN)).indexOf("/") == -1);
	}

	/**
	 * アカウントからユーザステータスを取得.
	 * ユーザ管理者権限がある場合のみ実行可。
	 * <p>
	 * <ul>
	 * <li>登録なし : Nothing</li>
	 * <li>仮登録 : Interim</li>
	 * <li>本登録 : Activated</li>
	 * <li>無効 : Revoked</li>
	 * </ul>
	 * </p>
	 * @param email ユーザ名。nullの場合はステータス一覧を取得する。
	 * @param reflexContext ReflexContext
	 * @return ユーザステータス(summaryに設定されている)
	 */
	public EntryBase getUserstatusByAccount(String account, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		// 入力チェック
		CheckUtil.checkNotNull(account, "Account");
		// ユーザトップエントリーを取得
		// /_user のFeed検索はグループ管理者には権限がないため、一旦システム権限で検索する
		SystemContext systemContext = new SystemContext(serviceName,
				requestInfo, connectionInfo);
		EntryBase userTopEntry = getUserTopEntryByAccount(account, true,
				systemContext);
		if (userTopEntry != null) {
			// 参照権限チェック
			AclBlogic aclBlogic = new AclBlogic();
			aclBlogic.checkAcl(userTopEntry.getMyUri(), Constants.ACL_TYPE_RETRIEVE, 
					auth, requestInfo, connectionInfo);
			// ステータスを取得
			return getUserstatusEntry(userTopEntry, serviceName);
		} else {
			// エントリーが存在しない場合
			// ユーザ管理者かどうかチェック
			AclBlogic aclBlogic = new AclBlogic();
			// ユーザ管理グループだとデータなしで返す
			// グループ管理者や一般ユーザの場合、認可エラーををスローする。
			aclBlogic.checkAuthedGroup(reflexContext.getAuth(), Constants.URI_GROUP_USERADMIN);
			return null;
		}
	}

	/**
	 * UIDからユーザステータスを取得.
	 * ユーザ管理者権限がある場合のみ実行可。
	 * <p>
	 * <ul>
	 * <li>登録なし : Nothing</li>
	 * <li>仮登録 : Interim</li>
	 * <li>本登録 : Activated</li>
	 * <li>無効 : Revoked</li>
	 * </ul>
	 * </p>
	 * @param email ユーザ名。nullの場合はステータス一覧を取得する。
	 * @param systemContext SystemContext
	 * @return ユーザステータス(summaryに設定されている)
	 */
	public EntryBase getUserstatusByUid(String uid, SystemContext systemContext)
	throws IOException, TaggingException {
		String serviceName = systemContext.getServiceName();
		// 入力チェック
		CheckUtil.checkNotNull(uid, "UID");
		// ユーザトップエントリーを取得
		EntryBase userTopEntry = getUserTopEntryByUid(uid, true,
				systemContext);
		// ステータスを取得
		return getUserstatusEntry(userTopEntry, serviceName);
	}

	/**
	 * ユーザステータス一覧を取得.
	 * ユーザ管理者権限がある場合のみ実行可。
	 * <p>
	 * <ul>
	 * <li>登録なし : Nothing</li>
	 * <li>仮登録 : Interim</li>
	 * <li>本登録 : Activated</li>
	 * <li>無効 : Revoked</li>
	 * </ul>
	 * </p>
	 * @param limit 一覧件数。nullの場合はデフォルト値。
	 * @param cursorStr カーソル
	 * @param reflexContext ReflexContext
	 * @return ユーザステータス一覧
	 */
	public FeedBase getUserstatusList(Integer limit, String cursorStr,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		SystemContext systemContext = new SystemContext(serviceName,
				requestInfo, connectionInfo);

		int flimit = 0;
		if (limit != null) {
			flimit = limit;
		} else {
			flimit = TaggingEnvUtil.getEntryNumberDefault(serviceName);
		}

		int tmpLimit = flimit;
		String tmpCurosrStr = cursorStr;

		List<EntryBase> entries = new ArrayList<EntryBase>();
		do {
			String requestUri = editUserTopFeedUri(tmpLimit, tmpCurosrStr);
			FeedBase tmpFeed = systemContext.getFeed(requestUri);
			if (TaggingEntryUtil.isExistData(tmpFeed)) {
				for (EntryBase entry : tmpFeed.entry) {
					String myUri = entry.getMyUri();
					if (isUserTopUri(myUri)) {
						// サービス階層直下が数値の場合、ユーザトップエントリーとみなす。
						EntryBase statusEntry = getUserstatusEntry(entry, serviceName);
						entries.add(statusEntry);
					}
				}
			}

			tmpCurosrStr = TaggingEntryUtil.getCursorFromFeed(tmpFeed);
			tmpLimit = flimit - entries.size();

		} while (!StringUtils.isBlank(tmpCurosrStr) && tmpLimit > 0);

		FeedBase feed = null;
		if (entries.size() > 0 || !StringUtils.isBlank(tmpCurosrStr)) {
			feed = TaggingEntryUtil.createFeed(serviceName);
			feed.entry = entries;
			TaggingEntryUtil.setCursorToFeed(cursorStr, feed);
		}
		return feed;
	}

	/**
	 * ユーザ権限剥奪.
	 * @param feed アカウント(entryのtitle)、またはUID(entryのlink selfのhref)
	 * @param isDeleteGroups 同時にグループを削除する場合true
	 * @param reflexContext ReflexContext
	 * @return 処理対象ユーザ。
	 */
	public FeedBase revokeUser(FeedBase feed, boolean isDeleteGroups, ReflexContext reflexContext)
	throws IOException, TaggingException {
		return changeUserstatus(feed, Constants.USERSTATUS_REVOKED, isDeleteGroups, reflexContext);
	}

	/**
	 * ユーザを有効にする.
	 * @param feed アカウント(entryのtitle)、またはUID(entryのlink selfのhref)
	 * @param reflexContext ReflexContext
	 * @return 処理対象ユーザ。
	 */
	public FeedBase activateUser(FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException {
		return changeUserstatus(feed, Constants.USERSTATUS_ACTIVATED, false, reflexContext);
	}

	/**
	 * ユーザ退会.
	 * @param feed アカウント(entryのtitle)、またはUID(entryのlink selfのhref)
	 * @param isDeleteGroups 同時にグループを削除する場合true
	 * @param reflexContext ReflexContext
	 * @return 処理対象ユーザ。
	 */
	public FeedBase cancelUser(FeedBase feed, boolean isDeleteGroups, ReflexContext reflexContext)
	throws IOException, TaggingException {
		return changeUserstatus(feed, Constants.USERSTATUS_CANCELLED, isDeleteGroups, reflexContext);
	}

	/**
	 * ユーザステータス更新
	 * @param feed ユーザ情報
	 * @param userstatus ユーザステータス
	 * @param isDeleteGroups 同時にグループを削除する場合true
	 * @param reflexContext ReflexContext
	 * @return ユーザステータスEntry
	 */
	private FeedBase changeUserstatus(FeedBase feed, String userstatus, 
			boolean isDeleteGroups, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		// 入力チェック
		SystemContext systemContext = new SystemContext(serviceName,
				requestInfo, connectionInfo);
		checkChangeUserstatus(feed, systemContext);
		// ユーザステータス更新
		List<EntryBase> entries = new ArrayList<EntryBase>();
		for (EntryBase entry : feed.entry) {
			EntryBase statusEntry = changeUserstatus(entry, userstatus, isDeleteGroups, 
					reflexContext);
			if (statusEntry != null) {
				entries.add(statusEntry);
			}
		}
		if (entries.isEmpty()) {
			return null;
		}
		FeedBase retFeed = TaggingEntryUtil.createFeed(serviceName);
		retFeed.entry = entries;
		return retFeed;
	}

	/**
	 * ユーザステータス更新
	 * @param feed アカウント(entryのtitle)、またはUID(entryのlink selfのhref)
	 *             事前の処理でURIに /_user/{UID} がセットされている。
	 * @param userstatus ユーザステータス
	 * @param isDeleteGroups RevokedまたはCancelledに変更時、同時にグループを削除する場合true
	 * @param systemContext SystemContext
	 * @return ユーザステータスEntry
	 */
	private EntryBase changeUserstatus(EntryBase entry, String userstatus,
			boolean isDeleteGroups, ReflexContext reflexContext)
	throws IOException, TaggingException {
		ReflexAuthentication auth = reflexContext.getAuth();
		String serviceName = reflexContext.getServiceName();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		String myUri = entry.getMyUri();
		String uid = getUidByUri(myUri);
		if (logger.isTraceEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
					"[changeUserstatus] uid = " + uid);
		}
		EntryBase userTopEntry = reflexContext.getEntry(myUri, true);
		if (userTopEntry == null) {
			throw new IllegalParameterException("The user does not exist. " + myUri);
		}
		
		List<EntryBase> updEntries = new ArrayList<>();
		// グループを同時に削除する場合
		if (isDeleteGroups) {
			List<EntryBase> delGroups = getDeleteGroups(myUri, reflexContext);
			if (delGroups != null && !delGroups.isEmpty()) {
				updEntries.addAll(delGroups);
			}
		}
		// ステータス更新
		EntryBase tmpUserTopEntry = TaggingEntryUtil.createEntry(serviceName);
		tmpUserTopEntry.setMyUri(userTopEntry.getMyUri());
		tmpUserTopEntry.id = userTopEntry.id;	// 削除された場合、ユーザトップエントリーだけ登録されてしまうのを防ぐためidを指定。
		setUserstatus(tmpUserTopEntry, userstatus);
		updEntries.add(tmpUserTopEntry);
		FeedBase updFeed = TaggingEntryUtil.createFeed(serviceName);
		updFeed.entry = updEntries;
		FeedBase retFeed = reflexContext.put(updFeed);

		if (isDeleteGroups) {
			// グループ管理グループの場合、ユーザエントリー等に設定されているグループ管理者のACLを削除する。
			// グループ管理者にはcontributor参照権限がないため、SystemContextで操作する。
			// ACL権限はステータス更新ができているのでOKとする。
			SystemContext systemContext = new SystemContext(auth, requestInfo, connectionInfo);
			String urnPrefix = Constants.URN_PREFIX_ACL + GroupConst.URI_GROUP_GROUPADMIN_PREFIX;
			List<EntryBase> updUserEntries = getRemoveUserGroupAclEntries(
					uid, urnPrefix, systemContext);
			if (updUserEntries != null) {
				FeedBase updUserFeed = TaggingEntryUtil.createFeed(serviceName);
				updUserFeed.entry = updUserEntries;
				systemContext.put(updUserFeed);
			}
		}
		EntryBase updEntry = retFeed.entry.get(retFeed.entry.size() - 1);
		return getUserstatusEntry(updEntry, serviceName);
	}

	/**
	 * ユーザステータス更新の引数チェック.
	 * feedのentryにアカウント(entryのtitle)、またはUID(entryのlink selfのhref)が指定されているかチェックする。
	 * ユーザ削除の引数もこのメソッドでチェックする。
	 * @param feed ユーザステータス更新の入力feed
	 * @param systemContext SystemContext
	 */
	private void checkChangeUserstatus(FeedBase feed, SystemContext systemContext)
	throws IOException, TaggingException {
		CheckUtil.checkNotNull(feed, "Account or UID");
		CheckUtil.checkNotNull(feed.entry, "Account or UID");
		for (EntryBase entry : feed.entry) {
			// アカウント指定の場合、以下のメソッド内でキーをセットされる。
			checkChangeUserstatus(entry, systemContext);
		}
		// ユーザ重複チェック
		CheckUtil.checkDuplicateUrl(feed);
	}

	/**
	 * ユーザステータス更新の引数チェック.
	 * feedのentryにアカウント(entryのtitle)、またはUID(entryのlink selfのhref)が指定されているかチェックする。
	 * ユーザ削除の引数もこのメソッドでチェックする。
	 * アカウント指定の場合、entryにUIDをセットする。
	 * @param feed ユーザステータス更新の入力feed
	 * @param systemContext SystemContext
	 */
	private void checkChangeUserstatus(EntryBase entry, SystemContext systemContext)
	throws IOException, TaggingException {
		CheckUtil.checkNotNull(entry, "Account or UID");
		EntryBase userTopEntry = null;
		String myUri = entry.getMyUri();
		if (!StringUtils.isBlank(myUri)) {
			// UID指定
			if (!isUserTopUri(myUri)) {
				throw new IllegalParameterException("The user key format is invalid. " + myUri);
			}
			userTopEntry = systemContext.getEntry(myUri, true);
			if (userTopEntry == null) {
				throw new IllegalParameterException("The user does not exist. " + myUri);
			}
		} else {
			// アカウント指定
			String account = entry.title;
			if (StringUtils.isBlank(account)) {
				throw new IllegalParameterException("Please specify account.");
			}
			userTopEntry = getUserTopEntryByAccount(account, systemContext);
			if (userTopEntry != null) {
				// URI (/_user/{UID}) をセットする。
				entry.setMyUri(userTopEntry.getMyUri());
			}
			if (userTopEntry == null) {
				throw new IllegalParameterException("The user does not exist. " + account);
			}
		}
	}
	
	/**
	 * 削除対象のグループを取得
	 * @param userTopUri ユーザトップURI
	 * @param reflexContext ReflexContext
	 * @return 削除対象のグループリスト
	 */
	private List<EntryBase> getDeleteGroups(String userTopUri, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String userGroupUri = userTopUri + Constants.URI_LAYER_GROUP;
		List<EntryBase> deleteGroupEntries = new ArrayList<>();
		// 検索
		int len = TaggingEnvUtil.getEntryNumberLimit();
		String cursorStr = null;
		do {
			String param = editUri(userGroupUri, len, cursorStr);
			FeedBase feed = reflexContext.getFeed(param, true);
			cursorStr = TaggingEntryUtil.getCursorFromFeed(feed);
			if (feed != null && feed.entry != null) {
				// エイリアスでなくキーを指定する
				for (EntryBase entry : feed.entry) {
					EntryBase deleteGroupEntry = TaggingEntryUtil.createEntry(serviceName);
					deleteGroupEntry.setMyUri(TaggingEntryUtil.getUriById(entry.id));
					deleteGroupEntry.id = entry.id + "?" + RequestParam.PARAM_DELETE;
					deleteGroupEntries.add(deleteGroupEntry);
				}
			}
		} while (!StringUtils.isBlank(cursorStr));
	
		if (deleteGroupEntries.isEmpty()) {
			return null;
		}
		return deleteGroupEntries;
	}

	/**
	 * ユーザを削除する.
	 * @param feed アカウント(entryのtitle)、またはUID(entryのlink selfのhref)
	 * @param async 削除を非同期に行う場合true
	 * @param reflexContext ReflexContext
	 * @return 処理対象ユーザ。
	 */
	public FeedBase deleteUser(FeedBase feed, boolean async, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		SystemContext systemContext = new SystemContext(serviceName,
				requestInfo, connectionInfo);
		// 入力チェック
		checkChangeUserstatus(feed, systemContext);
		// ユーザ削除
		List<EntryBase> entries = new ArrayList<EntryBase>();
		for (EntryBase entry : feed.entry) {
			EntryBase userTopEntry = deleteUser(entry, async, reflexContext);
			if (userTopEntry != null) {
				EntryBase statusEntry = getUserstatusEntry(entry, serviceName);
				entries.add(statusEntry);
			}
		}
		if (entries.isEmpty()) {
			return null;
		}
		FeedBase retFeed = TaggingEntryUtil.createFeed(serviceName);
		retFeed.entry = entries;
		return retFeed;
	}

	/**
	 * ユーザを削除する.
	 * @param entry アカウント(title)、またはUID(link selfのhref)
	 * @param async 非同期処理の場合true
	 * @param reflexContext ReflexContext
	 * @return 処理対象ユーザ
	 */
	private EntryBase deleteUser(EntryBase entry, boolean async, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String myUri = entry.getMyUri();
		EntryBase userTopEntry = reflexContext.getEntry(myUri);
		if (userTopEntry == null) {
			throw new IllegalParameterException("The user does not exist. " + myUri);
		}
		// ソーシャルログインの登録があれば削除する。
		String uid = getUidByUri(myUri);
		OAuthManager oauthManager = TaggingEnvUtil.getOAuthManager();
		if (oauthManager != null) {
			oauthManager.deleteUser(uid, reflexContext);
		}
		// ユーザ削除
		boolean isParallel = false;
		reflexContext.deleteFolder(myUri, async, isParallel);
		return getUserstatusEntry(userTopEntry, serviceName);
	}

	/**
	 * ユーザ初期設定に共通して必要なURIリスト(Entry検索).
	 * @param uid UID
	 * @return URIリスト
	 */
	@Override
	public List<String> getUserSettingEntryUris(String uid) {
		List<String> uris = new ArrayList<>();
		// パスワード
		uris.add(getUserAuthUriByUid(uid));
		// アクセスキー
		uris.add(getAccessKeyUriByUid(uid));
		// グループ
		uris.add(getGroupUriByUid(uid));

		return uris;
	}

	/**
	 * ユーザ初期設定に共通して必要なURIリスト(Feed検索).
	 * @param uid UID
	 * @return Feed検索用URIリスト
	 */
	@Override
	public List<String> getUserSettingFeedUris(String uid) {
		List<String> uris = new ArrayList<>();
		// グループ
		uris.add(getGroupUriByUid(uid));

		return uris;
	}

	/**
	 * ２段階認証(TOTP)登録.
	 * @param req リクエスト
	 * @param reflexContext ReflexContext
	 * @return ２段階認証のための情報
	 */
	public FeedBase createTotp(ReflexRequest req, ReflexContext reflexContext)
	throws IOException, TaggingException {
		logger.warn("[getTotp] do not correspond.");
		return null;	// 対応しない
	}

	/**
	 * ２段階認証(TOTP)削除.
	 * @param account ２段階認証削除アカウント
	 * @param reflexContext ReflexContext
	 * @return ２段階認証削除情報
	 */
	public FeedBase deleteTotp(String account, ReflexContext reflexContext)
	throws IOException, TaggingException {
		logger.warn("[getTotp] do not correspond.");
		return null;	// 対応しない
	}

	/**
	 * ２段階認証(TOTP)参照.
	 * @param auth 認証情報
	 * @param reflexContext ReflexContext
	 * @return ２段階認証情報
	 */
	public FeedBase getTotp(ReflexRequest req, ReflexContext reflexContext)
	throws IOException, TaggingException {
		logger.warn("[getTotp] do not correspond.");
		return null;	// 対応しない
	}

	/**
	 * 信頼できる端末にセットする値(TDID)の更新.
	 * @param auth 認証情報
	 * @param reflexContext ReflexContext
	 * @return 信頼できる端末にセットする値(TDID)の更新情報
	 */
	public FeedBase changeTdid(ReflexAuthentication auth, ReflexContext reflexContext)
	throws IOException, TaggingException {
		logger.warn("[changeTdid] do not correspond.");
		return null;	// 対応しない
	}
	
	/**
	 * パスワード変更で旧パスワード・一時トークンチェックを行わない旧バージョンかどうか
	 * @param serviceName サービス名
	 * @return 旧バージョンの場合true
	 */
	private boolean isChangephashLegacy(String serviceName) {
		String val = TaggingEnvUtil.getProp(serviceName, SettingConst.LEGACY, null);
		return UserManagerDefaultConst.CHANGEPHASH_LEGACY.equals(val);
	}
	
	/**
	 * Redisキャッシュに格納するパスワード変更一時トークンのキーを取得
	 * @param uid UID 
	 * @return Redisキャッシュに格納するパスワード変更一時トークンのキー
	 */
	private String getCachePassresetTokenUri(String uid) {
		// /_#passreset_token/{UID}
		StringBuilder sb = new StringBuilder();
		sb.append(UserManagerDefaultConst.URI_CACHESTRING_PASSRESET_TOKEN_PREFIX);
		sb.append(uid);
		return sb.toString();
	}

	/**
	 * エントリーのContributorからパスワード変更一時トークンを抽出.
	 * <contributor><uri>urn:vte.cx:passreset_token:{パスワード変更一時トークン}</uri></contributor>
	 * @param contributors Contributorリスト
	 * @return パスワード変更一時トークン
	 */
	private String getPassresetToken(List<Contributor> contributors) {
		if (contributors != null) {
			for (Contributor contributor : contributors) {
				if (contributor.uri != null && contributor.uri.startsWith(
						UserManagerDefaultConst.URN_PREFIX_PASSRESET_TOKEN)) {
					return contributor.uri.substring(URN_PREFIX_PASSRESET_TOKEN_LEN);
				}
			}
		}
		return null;
	}

	/**
	 * エントリーのContributorから現在のパスワードを抽出.
	 * <contributor><uri>urn:vte.cx:oldphash:{現在のパスワード}</uri></contributor>
	 * @param contributors Contributorリスト
	 * @return 現在のパスワード
	 */
	private String getOldPhash(List<Contributor> contributors) {
		if (contributors != null) {
			for (Contributor contributor : contributors) {
				if (contributor.uri != null && contributor.uri.startsWith(
						UserManagerDefaultConst.URN_PREFIX_OLDPHASH)) {
					return contributor.uri.substring(URN_PREFIX_OLDPHASH_LEN);
				}
			}
		}
		return null;
	}
	
	/**
	 * パスワード変更一時トークン、現在のパスワードの入力チェック.
	 * @param uid UID
	 * @param entry 入力値
	 * @param systemContext SystemContext
	 * @throws AuthenticationException パスワード変更一時トークンか現在のパスワードの値が不正な場合、またはいずれも設定がない場合。
	 */
	private void checkInputAuth(String uid, EntryBase entry, SystemContext systemContext) 
	throws IOException, TaggingException {
		String passresetToken = getPassresetToken(entry.contributor);
		String oldPhash = getOldPhash(entry.contributor);
		boolean isChecked = false;
		if (!StringUtils.isBlank(passresetToken)) {
			// パスワード変更一時トークンの値チェック
			String currentPassresetToken = getPassresetTokenFromCacheByUid(uid, systemContext);
			if (StringUtils.isBlank(currentPassresetToken) || !currentPassresetToken.equals(passresetToken)) {
				AuthenticationException ae = new AuthenticationException();
				ae.setSubMessage("passreset_token is invalid.");
				throw ae;
			}
			isChecked = true;
		}
		if (!StringUtils.isBlank(oldPhash)) {
			// 現在のパスワードの値チェック
			EntryBase userAuthEntry = getUserAuthEntryByUid(uid, systemContext);
			String currentPhash = getPassword(userAuthEntry);
			if (StringUtils.isBlank(currentPhash) || !currentPhash.equals(oldPhash)) {
				AuthenticationException ae = new AuthenticationException();
				ae.setSubMessage("oldphash is invalid.");
				throw ae;
			}
			isChecked = true;
		}
		
		// 現在のパスワード、パスワード変更一時トークンのいずれも設定がない場合エラー
		if (!isChecked) {
			AuthenticationException ae = new AuthenticationException();
			ae.setSubMessage("Neither the current password nor the temporary token has been set by changephash.");
			throw ae;
		}
	}
	
	/**
	 * Redisキャッシュに登録されているパスワード変更一時トークンを取得.
	 * @param uid UID
	 * @param systemContext SystemContext
	 * @return パスワード変更一時トークン
	 */
	private String getPassresetTokenFromCacheByUid(String uid, SystemContext systemContext) 
	throws IOException, TaggingException {
		String passresetTokenUri = getCachePassresetTokenUri(uid);
		return getPassresetTokenFromCache(passresetTokenUri, systemContext);
	}

	/**
	 * Redisキャッシュに登録されているパスワード変更一時トークンを取得.
	 * @param passresetTokenUri パスワード変更一時トークンのキー
	 * @param systemContext SystemContext
	 * @return パスワード変更一時トークン
	 */
	private String getPassresetTokenFromCache(String passresetTokenUri, SystemContext systemContext) 
	throws IOException, TaggingException {
		CacheManager cacheManager = TaggingEnvUtil.getCacheManager();
		return cacheManager.getString(passresetTokenUri, systemContext);
	}
	
	/**
	 * パスワード変更一時トークンをRedisキャッシュに格納.
	 * @param passresetTokenUri パスワード変更一時トークンのキー
	 * @param passresetToken パスワード変更一時トークン
	 * @param expireSec 有効期限(秒)
	 * @param systemContext SystemContext
	 */
	private void setPassresetTokenToCache(String passresetTokenUri, String passresetToken,
			int expireSec, SystemContext systemContext) 
	throws IOException, TaggingException {
		CacheManager cacheManager = TaggingEnvUtil.getCacheManager();
		cacheManager.setString(passresetTokenUri, passresetToken, expireSec, systemContext);
	}
	
	/**
	 * パスワード変更一時トークンの有効期限を更新.
	 * @param passresetTokenUri パスワード変更一時トークンのキー
	 * @param expireSec 有効期限(秒)
	 * @param systemContext SystemContext
	 */
	private void setExpirePassresetTokenToCache(String passresetTokenUri, int expireSec, 
			SystemContext systemContext) 
	throws IOException, TaggingException {
		CacheManager cacheManager = TaggingEnvUtil.getCacheManager();
		cacheManager.setExpireString(passresetTokenUri, expireSec, systemContext);
	}
	
	/**
	 * パスワード変更一時トークンをRedisキャッシュから削除.
	 * UIDからパスワード変更一時トークンのキーを生成し削除する。
	 * @param uid UID
	 * @param systemContext SystemContext
	 */
	private void deletePassresetTokenFromCacheByUid(String uid, SystemContext systemContext) 
	throws IOException, TaggingException {
		String passresetTokenUri = getCachePassresetTokenUri(uid);
		deletePassresetTokenFromCache(passresetTokenUri, systemContext);
	}

	/**
	 * パスワード変更一時トークンをRedisキャッシュから削除.
	 * @param passresetTokenUri パスワード変更一時トークンのキー
	 * @param systemContext SystemContext
	 */
	private void deletePassresetTokenFromCache(String passresetTokenUri, SystemContext systemContext) 
	throws IOException, TaggingException {
		CacheManager cacheManager = TaggingEnvUtil.getCacheManager();
		cacheManager.deleteString(passresetTokenUri, systemContext);
	}
	
	/**
	 * メールアドレス形式かどうか.
	 * @param email メールアドレス
	 * @return メールアドレス形式の場合true
	 */
	private boolean isMailAddress(String email) {
		if (StringUtils.isBlank(email)) {
			return false;	// nullの場合falseを返す
		}
		try {
			CheckUtil.checkMailAddress(email);
			return true;
			
		} catch (IllegalParameterException e) {
			return false;
		}
	}
	
	/**
	 * FeedのEntryのURIについて、#をUIDに変換する.
	 * @param feed Feed
	 * @param uid UID
	 * @param reflexContext ReflexContext
	 * @return 編集したFeed
	 */
	private FeedBase convertUid(FeedBase feed, String uid, ReflexContext reflexContext) {
		if (!TaggingEntryUtil.isExistData(feed)) {
			return feed;
		}
		FeedBase retFeed = TaggingEntryUtil.copyFeed(feed, reflexContext.getResourceMapper());
		for (EntryBase entry : retFeed.entry) {
			if (entry.link != null) {
				for (Link link : entry.link) {
					if ((Link.REL_SELF.equals(link._$rel) || 
							Link.REL_ALTERNATE.equals(link._$rel)) &&
							!StringUtils.isBlank(link._$href)) {
						link._$href = link._$href.replaceAll(
								UserManagerDefaultConst.REGEX_CONVERT_UID, uid);
					}
				}
			}
		}
		return retFeed;
	}

	/**
	 * グループ管理者登録.
	 * @param feed グループ管理者情報(複数指定可)
	 * @param reflexContext ReflexContext
	 * @return 
	 */
	public FeedBase createGroupadmin(FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		
		// キー: グループ名、値: グループ管理者のUID配列
		Map<String, String[]> groupadminInfoMap = new HashMap<>();
		// キー: UID、値: ユーザトップエントリー
		Map<String, EntryBase> userTopEntryMap = new HashMap<>();
		// 入力チェック
		CheckUtil.checkFeed(feed, false);
		for (EntryBase entry : feed.entry) {
			if (entry.link != null) {
				String groupName = null;
				List<String> uids = new ArrayList<>();
				for (Link link : entry.link) {
					if (Link.REL_SELF.equals(link._$rel)) {
						// {"___rel": "self", "___href": "/_group/{グループ名}"}
						String tmpGroupUri = link._$href;
						CheckUtil.checkNotNull(tmpGroupUri, "group");
						CheckUtil.checkUri(tmpGroupUri, "group");
						if (!tmpGroupUri.startsWith(GroupConst.URI_GROUP_SLASH)) {
							throw new IllegalParameterException("Invalid group. " + tmpGroupUri);
						}
						groupName = tmpGroupUri.substring(GroupConst.URI_GROUP_SLASH_LEN);
						// 先頭が$の場合エラー
						if (groupName.startsWith(Constants.URI_SERVICE_GROUP_MARK)) {
							throw new IllegalParameterException("The group name contains invalid characters. " + tmpGroupUri);
						}

					} else if (Link.REL_VIA.equals(link._$rel)) {
						// {"___rel": "via", "___title": "{グループ管理者のUID}}"}
						if (!StringUtils.isBlank(link._$title)) {
							// 指定されたグループ管理者の存在チェック
							String tmpUid = link._$title;
							EntryBase tmpUserTopEntry = getUserTopEntryByUid(tmpUid, reflexContext);
							if (tmpUserTopEntry == null) {
								throw new IllegalParameterException("The user does not exist. uid = " + tmpUid);
							}
							// 指定されたグループ管理者のユーザステータスチェック
							String tmpUserstatus = getUserstatus(tmpUserTopEntry);
							if (!Constants.USERSTATUS_ACTIVATED.equals(tmpUserstatus)) {
								throw new IllegalParameterException("Invalid user. uid = " + tmpUid);
							}
							uids.add(tmpUid);
							userTopEntryMap.put(tmpUid, tmpUserTopEntry);
						}
					}
				}
				if (!StringUtils.isBlank(groupName) && !uids.isEmpty()) {
					groupadminInfoMap.put(groupName, uids.toArray(new String[0]));
				}
			}
		}
		if (groupadminInfoMap.isEmpty()) {
			throw new IllegalParameterException("group admin information is required.");
		}

		List<EntryBase> groupadminEntries = new ArrayList<>();
		for (Map.Entry<String, String[]> mapEntry : groupadminInfoMap.entrySet()) {
			// キー: グループ名、値: グループ管理者のUID配列
			String groupName = mapEntry.getKey();
			groupadminEntries.addAll(editGroupadminGroupEntries(groupName, serviceName));
			
			String[] uids = mapEntry.getValue();
			for (String uid : uids) {
				EntryBase userTopEntry = userTopEntryMap.get(uid);
				groupadminEntries.addAll(editGroupadminEntries(groupName, uid, userTopEntry, serviceName));
			}
		}
		
		FeedBase groupadminFeed = TaggingEntryUtil.createFeed(serviceName);
		groupadminFeed.entry = groupadminEntries;
		
		FeedBase tmpRetFeed = reflexContext.put(groupadminFeed);
		
		// 戻り値は、/_group/{グループ名} のエントリーのみ
		FeedBase retFeed = null;
		if (TaggingEntryUtil.isExistData(tmpRetFeed)) {
			retFeed = TaggingEntryUtil.createFeed(serviceName);
			List<EntryBase> retEntries = new ArrayList<>();
			retFeed.entry = retEntries;
			for (EntryBase tmpRetEntry : tmpRetFeed.entry) {
				String tmpUri = tmpRetEntry.getMyUri();
				String tmpParent = TaggingEntryUtil.removeLastSlash(
						TaggingEntryUtil.getParentUri(tmpUri));
				if (Constants.URI_GROUP.equals(tmpParent)) {
					String tmpSelfid = TaggingEntryUtil.getSelfidUri(tmpUri);
					if (!tmpSelfid.startsWith(Constants.URI_SERVICE_GROUP_MARK)) {
						retEntries.add(tmpRetEntry);
					}
				}
			}
		}
		return retFeed;
	}
	
	/**
	 * グループ管理グループのエントリーを編集.
	 * @param groupName グループ名
	 * @return グループ管理グループ登録のエントリーリスト
	 */
	private List<EntryBase> editGroupadminGroupEntries(String groupName, String serviceName) {
		// グループ管理用のグループ登録
		//   /_group/$groupadmin_{グループ名}
		//      ACLには`$admin`、`$useradmin`、`$groupadmin_{グループ名}`のCRUDを設定。
		//   /_group/{グループ名}
		//      ACLには`$admin`、`$useradmin`、`$groupadmin_{グループ名}`のCRUDを設定。

		String groupadminGroupUri = GroupUtil.getGroupadminGroup(groupName);
		String groupUri = GroupUtil.getGroupUri(groupName);

		List<EntryBase> entries = new ArrayList<>();
		// /_group/$groupadmin_{グループ名}
		EntryBase entry = TaggingEntryUtil.createEntry(serviceName);
		entry.setMyUri(groupadminGroupUri);
		entries.add(entry);
		List<Contributor> contributors = new ArrayList<>();
		contributors.add(TaggingEntryUtil.getAclContributor(Constants.URI_GROUP_ADMIN,
				Constants.ACL_TYPE_CRUD));
		contributors.add(TaggingEntryUtil.getAclContributor(Constants.URI_GROUP_USERADMIN,
				Constants.ACL_TYPE_CRUD));
		contributors.add(TaggingEntryUtil.getAclContributor(GroupUtil.getGroupadminGroup(groupName),
				Constants.ACL_TYPE_CRUD));
		entry.contributor = contributors;
		// /_group/{グループ名}
		entry = TaggingEntryUtil.createEntry(serviceName);
		entry.setMyUri(groupUri);
		contributors = new ArrayList<>();
		contributors.add(TaggingEntryUtil.getAclContributor(Constants.URI_GROUP_ADMIN,
				Constants.ACL_TYPE_CRUD));
		contributors.add(TaggingEntryUtil.getAclContributor(Constants.URI_GROUP_USERADMIN,
				Constants.ACL_TYPE_CRUD));
		contributors.add(TaggingEntryUtil.getAclContributor(GroupUtil.getGroupadminGroup(groupName),
				Constants.ACL_TYPE_CRUD));
		entry.contributor = contributors;
		entries.add(entry);

		return entries;
	}
	
	/**
	 * グループ管理者登録の登録・更新エントリーを編集.
	 * @param groupName グループ名
	 * @param uid UID
	 * @param userTopEntry ユーザトップエントリー
	 * @param serviceName サービス名
	 * @return グループ管理者登録の登録・更新エントリーリスト
	 */
	private List<EntryBase> editGroupadminEntries(String groupName, String uid, EntryBase userTopEntry,
			String serviceName) {
		// グループ管理者登録
		//   /_group/$groupadmin_{グループ名}/{グループ管理者のUID}
		//      エイリアス: /_user/{グループ管理者のUID}/group/$groupadmin_{グループ名}
		// 管理対象グループ参加
		//   /_group/{グループ名}/{グループ管理者のUID}
		//      エイリアス: /_user/{グループ管理者のUID}/group/{グループ名}

		String groupadminGroupUri = GroupUtil.getGroupadminGroup(groupName);
		String groupadminSelfid = GroupUtil.getGroupadminGroupName(groupName);
		String groupUri = GroupUtil.getGroupUri(groupName);

		List<EntryBase> entries = new ArrayList<>();
		// /_group/$groupadmin_{グループ名}/{グループ管理者のUID}
		//   エイリアス: /_user/{グループ管理者のUID}/group/$groupadmin_{グループ名}
		EntryBase entry = createGroupEntry(groupadminGroupUri, groupadminSelfid, uid, serviceName);
		entries.add(entry);
		// /_group/{グループ名}/{グループ管理者のUID}
		//   エイリアス: /_user/{グループ管理者のUID}/group/{グループ名}
		entry = createGroupEntry(groupUri, groupName, uid, serviceName);
		entries.add(entry);
		
		return entries;
	}

	/**
	 * グループ管理用グループの削除.
	 * @param feed グループ管理用グループ情報(複数指定可)
	 * @param async 削除を非同期に行う場合true
	 * @param reflexContext ReflexContext
	 * @return 
	 */
	public void deleteGroupadmin(FeedBase feed, boolean async, ReflexContext reflexContext)
	throws IOException, TaggingException {
		CheckUtil.checkNotNull(feed, "group");
		List<String> groupNames = new ArrayList<>();
		for (EntryBase entry : feed.entry) {
			CheckUtil.checkNotNull(entry, "group");
			String uri = entry.getMyUri();
			CheckUtil.checkUri(uri, "group key");
			if (!uri.startsWith(GroupConst.URI_GROUP_SLASH)) {
				throw new IllegalParameterException("Invalid group key: " + uri);
			}
			String groupName = uri.substring(GroupConst.URI_GROUP_SLASH_LEN);
			CheckUtil.checkSlash(groupName, "group nanme");
			groupNames.add(groupName);
		}

		if (async) {
			// 非同期
			DeleteGroupadminGroupCallable callable = new DeleteGroupadminGroupCallable(groupNames);
			TaskQueueUtil.addTask(callable, 0, reflexContext.getAuth(), 
					reflexContext.getRequestInfo(), reflexContext.getConnectionInfo());
		} else {
			// 同期処理
			deleteGroupadminProc(groupNames, reflexContext);
		}
	}

	/**
	 * グループ管理用グループの削除.
	 * @param groupNames グループ管理用グループ名リスト
	 * @param reflexContext ReflexContext
	 * @return グループ管理用グループフォルダリスト
	 */
	protected void deleteGroupadminProc(List<String> groupNames, ReflexContext reflexContext)
	throws IOException, TaggingException {
		for (String groupName : groupNames) {
			// /_user/{UID}, /_user/{UID}/auth、/_user/{UID}/group エントリーのグループ管理ACLを削除
			deleteGroupadminAcl(groupName, reflexContext);
			
			// /_group/{グループ名}のフォルダ削除
			reflexContext.deleteFolder(GroupUtil.getGroupUri(groupName), false, false);
			
			// /_group/$groupadmin_{グループ名}のフォルダ削除
			reflexContext.deleteFolder(GroupUtil.getGroupadminGroup(groupName), false, false);
		}
	}
	
	/**
	 * 管理グループ削除時、/_user/{UID}, /_user/{UID}/auth、/_user/{UID}/group エントリーのグループ管理ACLを削除
	 * @param groupName グループ名
	 * @param reflexContext ReflexContext
	 */
	private void deleteGroupadminAcl(String groupName, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String groupUri = GroupUtil.getGroupUri(groupName);
		int idx = groupUri.length() + 1;
		int len = TaggingEnvUtil.getUpdateEntryNumberLimit() / 2;
		String urnPrefix = Constants.URN_PREFIX_ACL + GroupUtil.getGroupadminGroup(groupName) + ",";
		String cursorStr = null;
		do {
			String param = editUri(groupUri, len, cursorStr);
			FeedBase groupFeed = reflexContext.getFeed(param);
			if (TaggingEntryUtil.isExistData(groupFeed)) {
				List<EntryBase> updEntries = new ArrayList<>();
				for (EntryBase groupEntry : groupFeed.entry) {
					String uid = groupEntry.getMyUri().substring(idx);
					List<EntryBase> updUserEntries = getRemoveUserGroupAclEntries(
							uid, urnPrefix, reflexContext);
					if (updUserEntries != null) {
						updEntries.addAll(updUserEntries);
					}
				}
				if (!updEntries.isEmpty()) {
					FeedBase updFeed = TaggingEntryUtil.createFeed(serviceName);
					updFeed.entry = updEntries;
					reflexContext.put(updFeed);
				}
			}
		
		} while (!StringUtils.isBlank(cursorStr));
	}
	
	
	/**
	 * ユーザエントリー関連のグループACLを除去
	 *   /_user/{UID}、/_user/{UID}/auth、/_user/{UID}/group エントリーが対象
	 * @param uid UID
	 * @param removeUrnPrefix 除去対象グループ名接頭辞
	 * @param reflexContext ReflexContext
	 * @return 編集したエントリーリスト
	 */
	private List<EntryBase> getRemoveUserGroupAclEntries(String uid, 
			String removeUrnPrefix, ReflexContext reflexContext) 
	throws IOException, TaggingException {
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		List<EntryBase> retEntries = new ArrayList<>();
		try {
			EntryBase userTopEntry = getUserTopEntryByUid(uid, reflexContext);
			// contributorから $groupadmin_{groupName} 権限を削除する
			if (userTopEntry != null && userTopEntry.contributor != null) {
				List<Contributor> remainContributors = new ArrayList<>();
				for (Contributor contributor : userTopEntry.contributor) {
					if (contributor.uri == null || !contributor.uri.startsWith(removeUrnPrefix)) {
						remainContributors.add(contributor);
					}
				}
				userTopEntry.contributor = remainContributors;
				retEntries.add(userTopEntry);
			}
			
		} catch (PermissionException pe) {
			// ACLなしなので無視
			if (logger.isDebugEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[deleteGroupadminAcl] PermissionException by getUserTopEntry. uid=");
				sb.append(uid);
				logger.debug(sb.toString());
			}
		}
		try {
			EntryBase userAuthEntry = getUserAuthEntryByUid(uid, reflexContext);
			// contributorから $groupadmin_{groupName} 権限を削除する
			if (userAuthEntry != null && userAuthEntry.contributor != null) {
				List<Contributor> remainContributors = new ArrayList<>();
				for (Contributor contributor : userAuthEntry.contributor) {
					if (contributor.uri == null || !contributor.uri.startsWith(removeUrnPrefix)) {
						remainContributors.add(contributor);
					}
				}
				userAuthEntry.contributor = remainContributors;
				retEntries.add(userAuthEntry);
			}
			
		} catch (PermissionException pe) {
			// ACLなしなので無視
			if (logger.isDebugEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[deleteGroupadminAcl] PermissionException by getUserAuthEntry. uid=");
				sb.append(uid);
				logger.debug(sb.toString());
			}
		}
		try {
			String userGroupUri = getGroupUriByUid(uid);
			EntryBase userGroupEntry = reflexContext.getEntry(userGroupUri);
			// contributorから $groupadmin_{groupName} 権限を削除する
			if (userGroupEntry != null && userGroupEntry.contributor != null) {
				List<Contributor> remainContributors = new ArrayList<>();
				for (Contributor contributor : userGroupEntry.contributor) {
					if (contributor.uri == null || !contributor.uri.startsWith(removeUrnPrefix)) {
						remainContributors.add(contributor);
					}
				}
				userGroupEntry.contributor = remainContributors;
				retEntries.add(userGroupEntry);
			}
			
		} catch (PermissionException pe) {
			// ACLなしなので無視
			if (logger.isDebugEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[deleteGroupadminAcl] PermissionException by getUserAuthEntry. uid=");
				sb.append(uid);
				logger.debug(sb.toString());
			}
		}
		
		if (retEntries.isEmpty()) {
			return null;
		}
		return retEntries;
	}
	
	/**
	 * 検索条件編集
	 * @param groupUri グループキー
	 * @param len 検索件数
	 * @param cursorStr カーソル
	 * @return 検索条件
	 */
	private String editUri(String groupUri, int len, String cursorStr) {
		StringBuilder sb = new StringBuilder();
		sb.append(groupUri);
		sb.append("?");
		sb.append(RequestParam.PARAM_LIMIT);
		sb.append("=");
		sb.append(len);
		if (!StringUtils.isBlank(cursorStr)) {
			sb.append("&");
			sb.append(RequestParam.PARAM_NEXT);
			sb.append("=");
			sb.append(cursorStr);
		}
		return sb.toString();
	}

}
