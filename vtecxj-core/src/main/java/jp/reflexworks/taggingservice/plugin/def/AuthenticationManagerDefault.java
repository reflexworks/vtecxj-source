package jp.reflexworks.taggingservice.plugin.def;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.api.AtomConst;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.servlet.ReflexServletUtil;
import jp.reflexworks.servlet.util.AuthTokenUtil;
import jp.reflexworks.servlet.util.WsseAuth;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.auth.Authentication;
import jp.reflexworks.taggingservice.blogic.AclBlogic;
import jp.reflexworks.taggingservice.blogic.SecurityBlogic;
import jp.reflexworks.taggingservice.blogic.SecurityConst;
import jp.reflexworks.taggingservice.blogic.ServiceBlogic;
import jp.reflexworks.taggingservice.blogic.SessionBlogic;
import jp.reflexworks.taggingservice.env.TaggingEnvConst;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.AuthenticationException;
import jp.reflexworks.taggingservice.exception.NotInServiceException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.AccessTokenManager;
import jp.reflexworks.taggingservice.plugin.AuthenticationManager;
import jp.reflexworks.taggingservice.plugin.DatastoreManager;
import jp.reflexworks.taggingservice.plugin.ServiceManager;
import jp.reflexworks.taggingservice.plugin.UserManager;
import jp.reflexworks.taggingservice.servlet.TaggingRequest;
import jp.reflexworks.taggingservice.sys.SystemAuthentication;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.CookieUtil;
import jp.reflexworks.taggingservice.util.ExceptionUtil;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.UserUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * 認証管理クラス.
 */
public class AuthenticationManagerDefault implements AuthenticationManager {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * サーバ起動時の初期処理.
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
	 * 認証処理.
	 * 認証順序は、アクセストークン認証、リンクトークン認証、WSSE認証、RXID認証、セッション認証の順
	 * @param reflexReq リクエスト
	 */
	@Override
	public ReflexAuthentication autheticate(ReflexRequest reflexReq)
	throws IOException, TaggingException {
		TaggingRequest req = (TaggingRequest)reflexReq;
		String serviceName = req.getServiceName();
		RequestInfo requestInfo = req.getRequestInfo();
		ConnectionInfo connectionInfo = req.getConnectionInfo();
		SystemContext systemContext = new SystemContext(serviceName,
				requestInfo, connectionInfo);

		AccessTokenManager accessTokenManager = TaggingEnvUtil.getAccessTokenManager();
		UserManager userManager = TaggingEnvUtil.getUserManager();
		SessionBlogic sessionBlogic = new SessionBlogic();
		SecurityBlogic securityBlogic = new SecurityBlogic();

		ReflexAuthentication auth = null;
		String uid = null;
		String account = null;
		String authType = null;
		String sessionId = null;
		String authCountUser = null;
		long authFailureCount = 0;
		Integer wsseWithoutCaptchaCount = null;

		try {
			// アクセストークン認証
			String accessToken = req.getAccessToken();
			if (accessToken != null) {
				authType = Constants.AUTH_TYPE_ACCESSTOKEN;
				if (logger.isTraceEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) +
							"[authenticate] authcheck " + authType + " start.");
				}
				uid = accessTokenManager.getUidByAccessToken(accessToken);
				// uidが取得できなかった場合はエラー
				if (StringUtils.isBlank(uid)) {
					String msg = "AccessToken uid is required. AccessToken=" + accessToken;
					if (logger.isInfoEnabled()) {
						logger.info(LogUtil.getRequestInfoStr(requestInfo) +
								"[authenticate] " + msg);
					}
					AuthenticationException ae = new AuthenticationException();
					ae.setSubMessage(msg);
					throw ae;
				}

				// UIDよりユーザ情報取得
				authenticationSetting(uid, userManager, systemContext);

				// IPアドレスのチェック(UIDをキーとする)(ブラックリストチェック)
				authCountUser = uid;
				authFailureCount = securityBlogic.checkAuthFailureCount(
						authCountUser, req, systemContext);

				if (accessTokenManager.checkAccessToken(accessToken, systemContext)) {
					// 認証成功
					// アクセストークンはセッションを生成しない
					account = userManager.getAccountByUid(uid, systemContext);
					auth = new Authentication(account, uid, sessionId,
							authType, serviceName);

				} else {
					// 認証失敗
					String msg = "AccessToken auth error. AccessToken=" + accessToken;
					if (logger.isInfoEnabled()) {
						logger.info(LogUtil.getRequestInfoStr(requestInfo) +
								"[authenticate] " + msg);
					}
					AuthenticationException ae = new AuthenticationException();
					ae.setSubMessage(msg);
					throw ae;
				}
			}

			// リンクトークン認証
			if (auth == null) {
				String linkToken = req.getLinkToken();
				if (!StringUtils.isBlank(linkToken)) {
					authType = Constants.AUTH_TYPE_LINKTOKEN;
					if (logger.isTraceEnabled()) {
						logger.info(LogUtil.getRequestInfoStr(requestInfo) +
								"[authenticate] authcheck " + authType + " start.");
					}
					uid = accessTokenManager.getUidByAccessToken(linkToken);
					// uidが取得できなかった場合はエラー
					if (StringUtils.isBlank(uid)) {
						String msg = "LinkToken uid is required. LinkToken=" + linkToken;
						if (logger.isInfoEnabled()) {
							logger.info(LogUtil.getRequestInfoStr(requestInfo) +
									"[authenticate] " + msg);
						}
						AuthenticationException ae = new AuthenticationException();
						ae.setSubMessage(msg);
						throw ae;
					}

					// UIDよりユーザ情報取得
					authenticationSetting(uid, userManager, systemContext);

					// IPアドレスのチェック(UIDをキーとする)(ブラックリストチェック)
					authCountUser = uid;
					authFailureCount = securityBlogic.checkAuthFailureCount(
							authCountUser, req, systemContext);

					String uri = req.getRequestType().getUri();
					if (accessTokenManager.checkLinkToken(linkToken, uri, systemContext)) {
						// 認証成功
						// リンクトークンはセッションを生成しない
						account = userManager.getAccountByUid(uid, systemContext);
						auth = new Authentication(account, uid, sessionId, linkToken,
								authType, serviceName);
						auth.addLinkTokenUri(uri);
					} else {
						// 認証失敗
						String msg = "LinkToken auth error. LinkToken=" + linkToken;
						if (logger.isInfoEnabled()) {
							logger.info(LogUtil.getRequestInfoStr(requestInfo) +
									"[authenticate] " + msg);
						}
						AuthenticationException ae = new AuthenticationException();
						ae.setSubMessage(msg);
						throw ae;
					}
				}
			}

			// WSSE認証 (Captcha)
			// RXID認証
			if (auth == null) {
				WsseAuth wsseAuth = req.getWsseAuth();
				if (wsseAuth != null) {
					if (wsseAuth.isRxid) {
						authType = Constants.AUTH_TYPE_RXID;
					} else {
						authType = Constants.AUTH_TYPE_WSSE;
					}
					if (logger.isTraceEnabled()) {
						logger.info(LogUtil.getRequestInfoStr(requestInfo) +
								"[authenticate] authcheck " + authType + " start.");
					}
					// WSSEおよびRXIDのフォーマットエラーチェック
					if (StringUtils.isBlank(wsseAuth.username) ||
							StringUtils.isBlank(wsseAuth.passwordDigest) ||
							StringUtils.isBlank(wsseAuth.nonce) ||
							StringUtils.isBlank(wsseAuth.created)) {
						StringBuilder msgBld = new StringBuilder();
						if (wsseAuth.isRxid) {
							msgBld.append("RXID format error. RXID=");
						} else {
							msgBld.append("WSSE format error. WSSE=");
						}
						msgBld.append(getAuthErrorSubMessageValue(req, wsseAuth));
						String msg = msgBld.toString();
						if (logger.isInfoEnabled()) {
							logger.info(LogUtil.getRequestInfoStr(requestInfo) +
									"[authenticate] " + msg);
						}
						AuthenticationException ae = new AuthenticationException();
						ae.setSubMessage(msg);
						throw ae;
					}

					// アカウントの取得
					String[] usernameAndService = AuthTokenUtil.getUsernameAndService(
							wsseAuth);
					account = UserUtil.editAccount(usernameAndService[0]);
					if (StringUtils.isBlank(account)) {
						StringBuilder msgBld = new StringBuilder();
						if (wsseAuth.isRxid) {
							msgBld.append("RXID account is null. RXID=");
						} else {
							msgBld.append("WSSE account is null. WSSE=");
						}
						msgBld.append(getAuthErrorSubMessageValue(req, wsseAuth));
						String msg = msgBld.toString();
						if (logger.isInfoEnabled()) {
							logger.info(LogUtil.getRequestInfoStr(requestInfo) +
									"[authenticate] " + msg);
						}
						AuthenticationException ae = new AuthenticationException();
						ae.setSubMessage(msg);
						throw ae;
					}
					authCountUser = account;

					// サービスチェック
					// RXIDのサービス名と、リクエスト先サービスが合致しなければエラー
					// (ログインサービス除く)
					if (wsseAuth.isRxid) {
						// RXIDからサービス名を抽出
						String rxidServiceName = null;
						if (usernameAndService != null && usernameAndService.length > 1) {
							rxidServiceName = usernameAndService[1];
						}

						if (StringUtils.isBlank(rxidServiceName)) {
							StringBuilder msgBld = new StringBuilder();
							msgBld.append("RXID serviceName is required. RXID=");
							msgBld.append(getAuthErrorSubMessageValue(req, wsseAuth));
							String msg = msgBld.toString();
							if (logger.isInfoEnabled()) {
								logger.info(LogUtil.getRequestInfoStr(requestInfo) +
										"[authenticate] " + msg);
							}
							AuthenticationException ae = new AuthenticationException();
							ae.setSubMessage(msg);
							throw ae;
						}

						if (!serviceName.equals(rxidServiceName)) {
							StringBuilder msgBld = new StringBuilder();
							msgBld.append("RXID serviceName is different. RXID service name=");
							msgBld.append(rxidServiceName);
							msgBld.append(" RXID=");
							msgBld.append(getAuthErrorSubMessageValue(req, wsseAuth));
							String msg = msgBld.toString();
							if (logger.isInfoEnabled()) {
								logger.info(LogUtil.getRequestInfoStr(requestInfo) +
										"[authenticate] " + msg);
							}
							AuthenticationException ae = new AuthenticationException();
							ae.setSubMessage(msg);
							throw ae;
						}

					} else {
						// WSSEの場合、「X-Requested-With」ヘッダが設定されていないとエラー
						if (!ReflexServletUtil.hasXRequestedWith(req)) {
							StringBuilder msgBld = new StringBuilder();
							msgBld.append("X-Requested-With header is required for WSSE. WSSE=");
							msgBld.append(getAuthErrorSubMessageValue(req, wsseAuth));
							String msg = msgBld.toString();
							AuthenticationException ae = new AuthenticationException();
							ae.setSubMessage(msg);
							throw ae;
						}
					}

					// IPアドレスのチェック(アカウントをキーとする)(ブラックリストチェック)
					authFailureCount = securityBlogic.checkAuthFailureCount(
							authCountUser, req, systemContext);

					if (!wsseAuth.isRxid) {
						// WSSE認証で認証に一定回数失敗している場合、キャプチャチェックを行う。
						wsseWithoutCaptchaCount = securityBlogic.getWsseWithoutCaptchaCount(serviceName);
						if (wsseWithoutCaptchaCount > -1 && wsseWithoutCaptchaCount <= authFailureCount) {
							securityBlogic.checkCaptcha(req, SecurityConst.CAPTCHA_ACTION_LOGIN);
						}
					}

					// UID取得
					uid = userManager.getUidByAccount(account, systemContext);
					if (StringUtils.isBlank(uid)) {
						AuthenticationException ae = new AuthenticationException();
						StringBuilder msgBld = new StringBuilder();
						if (wsseAuth.isRxid) {
							msgBld.append("RXID-user does not exist. RXID=");
						} else {
							msgBld.append("WSSE-user does not exist. WSSE=");
						}
						msgBld.append(ExceptionUtil.getAuthErrorSubMessageValue(req, wsseAuth));
						msgBld.append(" account=");
						msgBld.append(account);
						ae.setSubMessage(msgBld.toString());
						throw ae;
					}

					// UIDよりユーザ情報取得
					authenticationSetting(uid, userManager, systemContext);

					// 認証
					userManager.checkWsse(wsseAuth, req, systemContext);

					// RXIDのワンタイムチェック
					securityBlogic.checkRXIDCount(wsseAuth, req, systemContext);

					// 認証成功
					if (usernameAndService != null && usernameAndService.length > 0) {
						auth = sessionBlogic.createSession(account, uid, authType, serviceName,
								requestInfo, connectionInfo);
					}
				}
			}

			// セッション認証
			if (auth == null) {
				sessionId = sessionBlogic.getSessionIdFromRequest(req);
				if (!StringUtils.isBlank(sessionId)) {
					authType = Constants.AUTH_TYPE_SESSION;
					if (logger.isTraceEnabled()) {
						logger.info(LogUtil.getRequestInfoStr(requestInfo) +
								"[authenticate] authcheck " + authType + " start.");
					}
					uid = sessionBlogic.getUidFromSession(systemContext, sessionId);
					if (StringUtils.isBlank(uid)) {
						StringBuilder msgBld = new StringBuilder();
						msgBld.append("Session timeout or incorrect value. SID=");
						msgBld.append(sessionId);
						String msg = msgBld.toString();
						if (logger.isInfoEnabled()) {
							logger.info(LogUtil.getRequestInfoStr(requestInfo) +
									"[authenticate] " + msg);
						}
						AuthenticationException ae = new AuthenticationException();
						ae.setSubMessage(msg);
						throw ae;
					}
					// UIDよりユーザ情報取得
					authenticationSetting(uid, userManager, systemContext);
					// 認証情報を取得
					auth = sessionBlogic.getAuthFromSession(uid, systemContext, sessionId);

					boolean ret = sessionBlogic.resetExpire(auth, requestInfo, connectionInfo);
					if (!ret) {
						StringBuilder msgBld = new StringBuilder();
						msgBld.append("Session timeout. SID=");
						msgBld.append(sessionId);
						String msg = msgBld.toString();
						if (logger.isInfoEnabled()) {
							logger.info(LogUtil.getRequestInfoStr(requestInfo) +
									"[authenticate] " + msg);
						}
						AuthenticationException ae = new AuthenticationException();
						ae.setSubMessage(msg);
						throw ae;
					}

				}
			}

			if (auth != null) {
				// ユーザステータスのチェック。仮登録の場合は本登録処理を実行。
				userManager.processByUserstatus(auth, systemContext);

				// 認証失敗カウントのクリア
				if (authFailureCount > 0) {
					securityBlogic.clearAuthFailureCount(authCountUser, req, systemContext);
				}

				// 認証情報にグループを付加する。
				setGroups(auth, systemContext);

				// サービス管理者の場合、ホワイトリストに指定されたIPアドレスからのみアクセス可。
				AclBlogic aclBlogic = new AclBlogic();
				if (aclBlogic.isInTheGroup(auth, Constants.URI_GROUP_ADMIN)) {
					securityBlogic.checkAdminAddress(req, systemContext);
				}

			} else {
				// 認証なしの場合、サービス名だけセットした認証情報を作成する。
				authType = null;
				auth = new Authentication(account, uid, sessionId, authType,
						serviceName);
			}

			return auth;

		} catch (AuthenticationException e) {
			// 認証失敗回数加算
			if ((Constants.AUTH_TYPE_ACCESSTOKEN.equals(authType) ||
					Constants.AUTH_TYPE_LINKTOKEN.equals(authType) ||
					Constants.AUTH_TYPE_RXID.equals(authType) ||
					Constants.AUTH_TYPE_WSSE.equals(authType)) &&
					!"RXID has been used more than once.".equals(e.getMessage())) {
				authFailureCount = securityBlogic.incrementAuthFailureCount(
						authCountUser, req, systemContext);
				if (Constants.AUTH_TYPE_WSSE.equals(authType)) {
					// WSSE認証の場合、キャプチャ不要な失敗回数であればメッセージを変える。
					if (wsseWithoutCaptchaCount == null) {
						wsseWithoutCaptchaCount =
							securityBlogic.getWsseWithoutCaptchaCount(serviceName);
					}
					if (wsseWithoutCaptchaCount > -1 &&
							authFailureCount >= wsseWithoutCaptchaCount) {
						String msg = "Captcha required at next login.";
						if (logger.isInfoEnabled()) {
							StringBuilder sb = new StringBuilder();
							sb.append(LogUtil.getRequestInfoStr(requestInfo));
							sb.append("[authenticate] ");
							sb.append(msg);
							sb.append(" ");
							sb.append(account);
							logger.info(sb.toString());
						}
						String backSubMessage = e.getSubMessage();
						e = new AuthenticationException(msg);
						e.setSubMessage(backSubMessage);
					}
				}
			}

			// 一行ログ
			String msg = getErrorMessage("authenticate", req, e);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + msg);

			throw e;
		}
	}

	/**
	 * 認証後の処理.
	 * 仮認証に対応。
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param auth 認証情報
	 * @return 処理を継続する場合true
	 */
	public boolean afterAutheticate(ReflexRequest req, ReflexResponse resp, ReflexAuthentication auth)
	throws IOException, TaggingException {
		return true;
	}

	/**
	 * セッションIDをレスポンスに設定
	 * @param req リクエスト
	 * @param resp レスポンス
	 */
	public void setSessionIdToResponse(ReflexRequest req, ReflexResponse resp)
	throws IOException, TaggingException {
		String serviceName = req.getServiceName();
		ReflexAuthentication auth = req.getAuth();
		if (auth != null && !StringUtils.isBlank(auth.getSessionId())) {
			SessionBlogic sessionBlogic = new SessionBlogic();
			int maxAge = sessionBlogic.getSessionExpire(serviceName);
			setCookie(req, resp, ReflexServletConst.COOKIE_SID,
					auth.getSessionId(), maxAge);
		}
	}

	/**
	 * セッションIDをレスポンスから削除
	 * @param req リクエスト
	 * @param resp レスポンス
	 */
	public void deleteSessionIdFromResponse(ReflexRequest req, ReflexResponse resp)
	throws IOException, TaggingException {
		CookieUtil.deleteCookie(resp, ReflexServletConst.COOKIE_SID);
	}

	/**
	 * サービス連携認証.
	 * 対象サービス名とそのサービスキーのチェック、及びログインユーザの登録チェックを行う。
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのServiceKey
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 対象サービスでのログインユーザ認証情報
	 */
	public ReflexAuthentication authenticateByCooperationService(
			String targetServiceName, String targetServiceKey,
			ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// 対象サービス名の入力がない、または現在のサービス名と同じ場合は認証情報をそのまま返す。
		if (auth == null || StringUtils.isBlank(targetServiceName)) {
			return auth;
		}
		String serviceName = auth.getServiceName();
		if (serviceName.equals(targetServiceName)) {
			return auth;
		}

		// ログインチェック
		String account = auth.getAccount();
		if (StringUtils.isBlank(account)) {
			String msg = "Login is required.";
			AuthenticationException ae = new AuthenticationException(msg);
			throw ae;
		}

		// 対象サービスのサービスキーチェック
		// サービスキーを取得
		ServiceBlogic serviceBlogic = new ServiceBlogic();
		String serviceKey = serviceBlogic.getServiceKey(targetServiceName,
				requestInfo, connectionInfo);
		if (StringUtils.isBlank(serviceKey)) {
			// サービス存在なし
			throw new NotInServiceException(targetServiceName);
		} else if (!serviceKey.equals(targetServiceKey)) {
			// 対象サービス認証エラー
			String msg = "The target service's ServiceKey is invalid. service = " + targetServiceName;
			AuthenticationException ae = new AuthenticationException(msg);
			throw ae;
		}

		// 対象サービスの初期設定
		ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
		// このノードでサービス情報を保持していない場合、設定処理を行う。
		serviceManager.settingServiceIfAbsent(targetServiceName, requestInfo,
					connectionInfo);

		// 対象サービスにアカウントが登録されているかどうかチェック
		SystemContext systemContext = new SystemContext(targetServiceName,
				requestInfo, connectionInfo);
		UserManager userManager = TaggingEnvUtil.getUserManager();
		String targetUid = userManager.getUidByAccount(account, systemContext);
		if (StringUtils.isBlank(targetUid)) {
			String msg = "The user is not registered for the specified service. service = " + targetServiceName;
			AuthenticationException ae = new AuthenticationException(msg);
			throw ae;
		}

		// 対象サービスでのグループを取得
		List<String> targetGroups = userManager.getGroupsByUid(targetUid, systemContext);
		ReflexAuthentication targetAuth = new Authentication(account, targetUid,
				auth.getSessionId(), auth.getLinkToken(),
				auth.getAuthType(), targetGroups, targetServiceName);

		// ユーザステータスのチェック。仮登録の場合もエラー。
		EntryBase userstatusEntry = userManager.getUserstatusByAccount(account, systemContext);
		String userstatus = userManager.getUserstatus(userstatusEntry);
		if (!AtomConst.USERSTATUS_ACTIVATED.equals(userstatus)) {
			AuthenticationException ae = new AuthenticationException();
			ae.setSubMessage("User status is invalid. " + userstatus);
			throw ae;
		}

		return targetAuth;
	}

	/**
	 * HttpOnly属性を取得.
	 * @param req リクエスト
	 * @return HttpOnlyの値
	 */
	protected boolean isHttpOnly(ReflexRequest req) {
		// 定義はdisableなので、否定値を返す。
		return !TaggingEnvUtil.getSystemPropBoolean(
				TaggingEnvConst.DISABLE_SESSION_HTTPONLY, false);
	}

	/**
	 * Secure属性を取得.
	 * @param req リクエスト
	 * @return Secureの値
	 */
	protected boolean isSecure(ReflexRequest req)
	throws IOException, TaggingException {
		ServiceBlogic serviceBlogic = new ServiceBlogic();
		return serviceBlogic.isSecure(req);
	}

	/**
	 * Cookieに値をセット
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param key Cookieのキー
	 * @param value Cookieの値
	 * @param maxAge Cookie存続時間(秒)
	 */
	protected void setCookie(ReflexRequest req, ReflexResponse resp, String key, String value,
			int maxAge)
	throws IOException, TaggingException {
		boolean isHttpOnly = isHttpOnly(req);
		boolean isSecure = isSecure(req);
		CookieUtil.setCookie(resp, key, value, maxAge, isHttpOnly, isSecure);
	}

	/**
	 * 認証情報にグループを追加
	 * @param auth 認証情報
	 * @param systemContext SystemContext
	 */
	public void setGroups(ReflexAuthentication auth, SystemContext systemContext)
	throws IOException, TaggingException {
		String uid = auth.getUid();
		if (uid.equals(SystemAuthentication.UID_SYSTEM) ||
				uid.equals(Constants.NULL_UID)) {
			return;
		}
		UserManager userManager = TaggingEnvUtil.getUserManager();
		List<String> groups = userManager.getGroupsByUid(uid, systemContext);
		auth.clearGroup();
		if (groups != null) {
			for (String group : groups) {
				auth.addGroup(group);
			}
		}
	}

	/**
	 * ログ用エラーメッセージ取得.
	 * @param method メソッド名
	 * @param req リクエスト
	 * @param e 例外
	 * @return ログ用エラーメッセージ
	 */
	protected String getErrorMessage(String method, ReflexRequest req,
			AuthenticationException e) {
		StringBuilder sb = new StringBuilder();
		sb.append(getErrorMessageHeader(method, e));
		sb.append(e.getSubMessage());
		return sb.toString();
	}

	/**
	 * ログ用エラーメッセージの先頭部分を取得.
	 * @param method メソッド名
	 * @param e 例外
	 * @return ログ用エラーメッセージの先頭部分
	 */
	protected String getErrorMessageHeader(String method, Throwable e) {
		StringBuilder sb = new StringBuilder();
		sb.append(e.getClass().getSimpleName());
		sb.append(" : ");
		sb.append(e.getMessage());
		sb.append(" [subMessage] ");
		return sb.toString();
	}

	/**
	 * WSSEまたはRXIDの認証エラー時、サブメッセージに設定する内容を編集.
	 * @param req
	 * @param wsseAuth
	 * @return サブメッセージへの設定内容
	 */
	protected String getAuthErrorSubMessageValue(ReflexRequest req, WsseAuth wsseAuth) {
		return ExceptionUtil.getAuthErrorSubMessageValue(req, wsseAuth);
	}

	/**
	 * 認証に使用するユーザ情報のキャッシュ読み込み.
	 * @param uid UID
	 * @param userManager UserManager
	 * @param systemContext SystemContext
	 */
	public void authenticationSetting(String uid, UserManager userManager,
			SystemContext systemContext)
	throws IOException, TaggingException {
		String serviceName = systemContext.getServiceName();
		RequestInfo requestInfo = systemContext.getRequestInfo();
		ConnectionInfo connectionInfo = systemContext.getConnectionInfo();
		DatastoreManager datastoreManager = TaggingEnvUtil.getDatastoreManager();
		datastoreManager.initMainThreadUser(uid, serviceName, requestInfo, connectionInfo);
	}

	/**
	 * 認証情報の複製.
	 * ReflexContextでExternalを設定するために使用する。
	 * @param auth 認証情報
	 * @return 複製した認証オブジェクト
	 */
	@Override
	public ReflexAuthentication copyAuth(ReflexAuthentication auth) {
		return new Authentication(auth);
	}

	/**
	 * 認証情報の生成.
	 * @param account アカウント
	 * @param uid UID
	 * @param sessionId セッションID
	 * @param authType 認証方法
	 * @param serviceName サービス名
	 * @return 生成した認証情報
	 */
	@Override
	public ReflexAuthentication createAuth(String account, String uid, String sessionId,
			String authType, String serviceName) {
		return new Authentication(account, uid, sessionId, authType, serviceName);
	}

	/**
	 * 認証情報の生成.
	 * @param account アカウント
	 * @param uid UID
	 * @param sessionId セッションID
	 * @param linkToken リンクトークン
	 * @param authType 認証方法
	 * @param serviceName サービス名
	 * @return 生成した認証情報
	 */
	@Override
	public ReflexAuthentication createAuth(String account, String uid, String sessionId,
			String linkToken, String authType, String serviceName) {
		return new Authentication(account, uid, sessionId, linkToken, authType, serviceName);
	}

	/**
	 * 認証情報の生成.
	 * @param account アカウント
	 * @param uid UID
	 * @param sessionId セッションID
	 * @param linkToken リンクトークン
	 * @param authType 認証方法
	 * @param groups 参加グループリスト
	 * @param serviceName サービス名
	 * @return 生成した認証情報
	 */
	@Override
	public ReflexAuthentication createAuth(String account, String uid, String sessionId,
			String linkToken, String authType, List<String> groups, String serviceName) {
		return new Authentication(account, uid, sessionId, linkToken, authType, groups, serviceName);
	}

}
