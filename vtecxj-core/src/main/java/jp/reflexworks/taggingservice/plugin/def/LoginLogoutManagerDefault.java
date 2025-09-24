package jp.reflexworks.taggingservice.plugin.def;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.servlet.HttpStatus;
import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.servlet.util.AuthTokenUtil;
import jp.reflexworks.servlet.util.UrlUtil;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestType;
import jp.reflexworks.taggingservice.blogic.ServiceBlogic;
import jp.reflexworks.taggingservice.blogic.UserBlogic;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.AuthenticationException;
import jp.reflexworks.taggingservice.exception.NotInServiceException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.AuthenticationManager;
import jp.reflexworks.taggingservice.plugin.LoginLogoutManager;
import jp.reflexworks.taggingservice.plugin.MessageManager;
import jp.reflexworks.taggingservice.plugin.ReflexSecurityManager;
import jp.reflexworks.taggingservice.plugin.ServiceManager;
import jp.reflexworks.taggingservice.plugin.UserManager;
import jp.reflexworks.taggingservice.servlet.TaggingRequest;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.taskqueue.TaskQueueUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.MessageUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.reflexworks.taggingservice.util.TaggingLogUtil;
import jp.reflexworks.taggingservice.util.UserUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * ログイン・ログアウト管理クラス.
 */
public class LoginLogoutManagerDefault implements LoginLogoutManager {

	/** ログイン履歴項目 : ip */
	public static final String LOGIN_IP = "ip";
	/** ログイン履歴項目 : uid */
	public static final String LOGIN_UID = "uid";
	/** ログイン履歴項目 : account */
	public static final String LOGIN_ACCOUNT = "account";
	/** ログイン履歴項目 : useragent */
	public static final String LOGIN_USERAGENT = "useragent";
	/** ログイン履歴項目 : cause */
	public static final String LOGIN_CAUSE = "cause";

	/** 他サービスへのログインURL生成処理で除去するパラメータ */
	private static final Set<String> IGNORE_PARAMS = new HashSet<String>();
	static {
		IGNORE_PARAMS.add(RequestType.PARAM_LOGIN);
	}

	/** UserAgent 小文字 */
	private static final String HEADER_USER_AGENT_LOWERCASE =
			ReflexServletConst.HEADER_USER_AGENT.toLowerCase(Locale.ENGLISH);

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 初期処理.
	 * サーバ起動時に一度だけ呼び出されます。
	 */
	@Override
	public void init() {
		// Do nothing.
	}

	/**
	 * シャットダウン処理.
	 */
	public void close() {
		// Do nothing.
	}

	/**
	 * ログイン処理.
	 * @param req リクエスト
	 * @param resp レスポンス
	 */
	@Override
	public FeedBase login(ReflexRequest req, ReflexResponse resp) {
		MessageManager msgManager = TaggingEnvUtil.getMessageManager();
		String serviceName = req.getServiceName();
		return MessageUtil.createMessageFeed(msgManager.getMsgLogin(serviceName),
				serviceName);
	}

	/**
	 * ログアウト処理.
	 * @param req リクエスト
	 * @param resp レスポンス
	 */
	@Override
	public FeedBase logout(ReflexRequest req, ReflexResponse resp) {
		MessageManager msgManager = TaggingEnvUtil.getMessageManager();
		String serviceName = req.getServiceName();
		return MessageUtil.createMessageFeed(msgManager.getMsgLogout(serviceName),
				serviceName);
	}

	/**
	 * 他サービスにログイン.
	 * 他サービスへのリダイレクトレスポンスを返す。
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param targetServiceName ログイン先サービス名
	 * @return メッセージ
	 */
	public FeedBase loginService(ReflexRequest req, ReflexResponse resp,
			String targetServiceName)
	throws IOException, TaggingException {
		RequestInfo requestInfo = req.getRequestInfo();
		ConnectionInfo connectionInfo = req.getConnectionInfo();

		// サービスが稼働していなければエラー
		ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
		boolean isEnabledService = serviceManager.isEnabled(req, targetServiceName,
				requestInfo, connectionInfo);
		if (!isEnabledService) {
			throw new NotInServiceException(targetServiceName);
		}
		// このノードでサービス情報を保持していない場合、設定処理を行う。
		serviceManager.settingServiceIfAbsent(targetServiceName, requestInfo,
				connectionInfo);

		// 指定されたサービスのRXIDを発行
		String account = req.getAuth().getAccount();
		SystemContext systemContext = new SystemContext(targetServiceName,
				requestInfo, connectionInfo);
		UserManager userManager = TaggingEnvUtil.getUserManager();
		String rxid = userManager.createRXIDByAccount(account, systemContext);
		if (StringUtils.isBlank(rxid)) {
			String subMsg = "The account does not register for the specified service.";
			AuthenticationException ae = new AuthenticationException();
			ae.setSubMessage(subMsg);
			throw ae;
		}

		// リダイレクト
		String loginServiceUrl = createLoginServiceUrl(req, rxid, targetServiceName);
		resp.sendRedirect(loginServiceUrl);
		resp.setStatus(HttpStatus.SC_MOVED_TEMPORARILY);

		// メッセージは特になし
		return null;
	}

	/**
	 * 他サービスへのログインURLを生成
	 * @param req リクエスト
	 * @param rxid RXID
	 * @param serviceName ログイン先サービス
	 * @return URL
	 */
	private String createLoginServiceUrl(ReflexRequest req, String rxid,
			String serviceName)
	throws IOException, TaggingException {
		// http://{サービス名}.vte.cx/d/xxx?_login&_RXID={RXID}
		// サーブレットパスとPathInfo、loginを除くQueryStringはそのままとする。
		RequestInfo requestInfo = req.getRequestInfo();
		ConnectionInfo connectionInfo = req.getConnectionInfo();
		ServiceBlogic serviceBlogic = new ServiceBlogic();
		String redirectContextpath = serviceBlogic.getRedirectUrlContextPath(
				serviceName, requestInfo, connectionInfo);

		StringBuilder sb = new StringBuilder();
		sb.append(redirectContextpath);
		sb.append(req.getServletPath());

		Map<String, String> addingParams = new HashMap<String, String>();
		addingParams.put(RequestType.PARAM_RXID, rxid);
		String pathInfoQuery = UrlUtil.editPathInfoQuery(req, IGNORE_PARAMS, addingParams, false);
		sb.append(pathInfoQuery);

		return sb.toString();
	}

	/**
	 * ログイン履歴出力.
	 * @param req リクエスト
	 */
	public void writeLoginHistory(ReflexRequest req) {
		// WSSE、RXID認証の場合が対象
		ReflexAuthentication auth = req.getAuth();
		if (auth == null) {
			return;
		}
		String authType = auth.getAuthType();
		if (authType == null) {
			return;
		}
		if (!Constants.AUTH_TYPE_WSSE.equals(authType) &&
				!Constants.AUTH_TYPE_RXID.equals(authType) &&
				!authType.startsWith(Constants.AUTH_TYPE_OAUTH)) {
			return;
		}

		String uid = auth.getUid();
		String account = auth.getAccount();
		String title = getAuthType(auth);
		RequestInfo requestInfo = req.getRequestInfo();
		String loginHistoryInfoStr = editLoginHistoryInfo(req, uid, account, null);

		// ログイン履歴出力は非同期処理
		WriteLoginHistoryCallable callable = new WriteLoginHistoryCallable(title, uid,
				account, loginHistoryInfoStr);
		try {
			TaskQueueUtil.addTask(callable, 0, auth, requestInfo, req.getConnectionInfo());
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[writeLoginHistory] Error occured. ");
			logger.warn(sb.toString(), e);
		}
	}

	/**
	 * ログイン失敗履歴出力.
	 * @param reflexReq リクエスト
	 * @param ae AuthenticationException
	 */
	public void writeAuthError(ReflexRequest reflexReq, AuthenticationException ae) {
		TaggingRequest req = (TaggingRequest)reflexReq;
		String serviceName = req.getServiceName();
		String title = ae.getClass().getSimpleName();
		String cause = ae.getSubMessage();
		String uid = null;
		String account = null;

		ReflexAuthentication auth = req.getAuth();
		if (auth == null) {
			// 認証なしの場合、サービス名だけセットした認証情報を作成する。
			AuthenticationManager authManager = TaggingEnvUtil.getAuthenticationManager();
			auth = authManager.createAuth(account, uid, null, null, serviceName);
		}
		RequestInfo requestInfo = req.getRequestInfo();
		ConnectionInfo connectionInfo = req.getConnectionInfo();
		SystemContext systemContext = new SystemContext(serviceName, requestInfo, connectionInfo);

		if (auth != null) {
			uid = auth.getUid();
			account = auth.getAccount();
		} else if (req.getWsseAuth() != null) {
			String[] usernameAndService = AuthTokenUtil.getUsernameAndService(
					req.getWsseAuth());
			if (usernameAndService != null && usernameAndService.length > 1) {
				account = UserUtil.editAccount(usernameAndService[0]);
				UserBlogic userBlogic = new UserBlogic();
				try {
					uid = userBlogic.getUidByAccount(account, systemContext);
				} catch (IOException | TaggingException e) {
					// ログイン履歴の登録なのでログだけ出力し、後続の処理を行う
					StringBuilder sb = new StringBuilder();
					sb.append(LogUtil.getRequestInfoStr(requestInfo));
					sb.append("[writeAuthError] Error occured. (getUidByAccount) ");
					logger.warn(sb.toString(), e);
				}
			}
		}

		String loginHistoryInfoStr = editLoginHistoryInfo(req, uid, account, cause);

		// ログイン履歴出力は非同期処理
		WriteLoginHistoryCallable callable = new WriteLoginHistoryCallable(title, uid,
				account, loginHistoryInfoStr);
		try {
			TaskQueueUtil.addTask(callable, 0, auth, requestInfo, connectionInfo);
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[writeAuthError] Error occured. (call writeLoginHistory) ");
			logger.warn(sb.toString(), e);
		}
	}

	/**
	 * ログイン履歴出力.
	 * @param req リクエスト
	 * @param title login:WSSE、login:RXID、login:OAuth-{provider}、AuthenticationException のいずれか
	 * @param uid UID
	 * @param account アカウント
	 * @param loginHistoryInfoStr ログイン情報文字列
	 * @param systemContext SystemContext
	 */
	void writeLoginHistoryProc(String title, String uid, String account,
			String loginHistoryInfoStr, SystemContext systemContext) {
		String serviceName = systemContext.getServiceName();
		// 番号取得
		try {
			String addidsUri = Constants.URI_LOGIN_HISTORY;
			String numStr = TaggingLogUtil.getLogNum(systemContext, addidsUri);

			EntryBase entry = TaggingEntryUtil.createEntry(serviceName);
			entry.setMyUri(getLoginLogUri(numStr));
			if (!StringUtils.isBlank(uid)) {
				entry.addAlternate(getLoginLogAlias(numStr, uid));
			}
			// ログイン履歴情報
			entry.summary = loginHistoryInfoStr;
			// 認証方法
			entry.title = title;

			// ログイン履歴エントリー登録
			systemContext.post(entry);

		} catch (IOException | TaggingException e) {
			// ログイン履歴の登録なのでログだけ出力し、後続の処理を行う
			logger.warn("[writeLoginHistoryProc] Error occurred." , e);
		}
	}

	/**
	 * ログイン履歴EntryのURIを取得
	 * /_login_history/{Long.MAX_VALUE - addidsの値}
	 * @param numStr
	 * @return ログイン履歴EntryのURI
	 */
	private String getLoginLogUri(String numStr) {
		StringBuilder sb = new StringBuilder();
		sb.append(Constants.URI_LOGIN_HISTORY);
		sb.append("/");
		sb.append(numStr);
		return sb.toString();
	}

	/**
	 * ログイン履歴Entryのエイリアスを取得
	 * /_user/{UID}/login_history/{Long.MAX_VALUE - addidsの値}
	 * @param numStr
	 * @return ログイン履歴Entryのエイリアス
	 */
	private String getLoginLogAlias(String numStr, String uid) {
		StringBuilder sb = new StringBuilder();
		sb.append(getLoginHistoryUserFolderUri(uid));
		sb.append("/");
		sb.append(numStr);
		return sb.toString();
	}

	/**
	 * ユーザごとのログイン履歴フォルダエントリーのキーを取得.
	 *  /_user/{UID}/login_history
	 * @param uid UID
	 * @return ユーザごとのログイン履歴フォルダエントリーのキー
	 */
	public String getLoginHistoryUserFolderUri(String uid) {
		UserBlogic userBlogic = new UserBlogic();
		String userTopUri = userBlogic.getUserTopUriByUid(uid);

		StringBuilder sb = new StringBuilder();
		sb.append(userTopUri);
		sb.append(Constants.URI_LAYER_LOGIN_HISTORY);
		return sb.toString();
	}

	/**
	 * ログイン履歴内容を編集.
	 * { "ip":"xxx", "uid":"xxx", "account":"xxx", "useragent":"xxx", "cause":"xxx" }
	 * @param req リクエスト
	 * @param uid UID
	 * @param account アカウント
	 * @param cause AuthenticationExceptionの場合のエラー原因
	 * @return ログイン履歴内容
	 */
	private String editLoginHistoryInfo(ReflexRequest req, String uid, String account,
			String cause) {
		// IPアドレス
		ReflexSecurityManager securityManager = TaggingEnvUtil.getSecurityManager();
		String ip = securityManager.getIPAddr(req);
		// User-Agent
		String userAgent = getUserAgent(req);

		StringBuilder sb = new StringBuilder();
		sb.append("{ \"");
		sb.append(LOGIN_IP);
		sb.append("\":");
		sb.append(jsonQuote(ip));
		if (!StringUtils.isBlank(uid)) {
			sb.append(", \"");
			sb.append(LOGIN_UID);
			sb.append("\":");
			sb.append(jsonQuote(uid));
		}
		if (!StringUtils.isBlank(account)) {
			sb.append(", \"");
			sb.append(LOGIN_ACCOUNT);
			sb.append("\":");
			sb.append(jsonQuote(account));
		}
		sb.append(", \"");
		sb.append(LOGIN_USERAGENT);
		sb.append("\":");
		sb.append(jsonQuote(userAgent));
		if (!StringUtils.isBlank(cause)) {
			sb.append(", \"");
			sb.append(LOGIN_CAUSE);
			sb.append("\":");
			sb.append(jsonQuote(cause));
		}
		sb.append(" }");
		return sb.toString();
	}

	/**
	 * JSONの値をエスケープし、クォートで囲む.
	 * @param str JSONの値
	 * @return 編集した値
	 */
	private String jsonQuote(String str) {
		if (str == null) {
			return "\"null\"";
		}
		return JSONObject.quote(str);
	}

	/**
	 * リクエストヘッダからUser-Agentを取得
	 * @param req リクエスト
	 * @return User-Agentの値
	 */
	private String getUserAgent(ReflexRequest req) {
		String val = req.getHeader(ReflexServletConst.HEADER_USER_AGENT);
		if (val == null) {
			val = req.getHeader(HEADER_USER_AGENT_LOWERCASE);
		}
		return val;
	}

	/**
	 * 認証情報を取得
	 * "login:WSSE"または"login:RXID"
	 * @param auth
	 * @return 認証情報文字列
	 */
	private String getAuthType(ReflexAuthentication auth) {
		StringBuilder sb = new StringBuilder();
		sb.append("login:");
		sb.append(auth.getAuthType());
		return sb.toString();
	}

}
