package jp.reflexworks.taggingservice.auth;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.servlet.HttpStatus;
import jp.reflexworks.servlet.ReflexServletUtil;
import jp.reflexworks.servlet.util.AuthTokenUtil;
import jp.reflexworks.servlet.util.WsseAuth;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.blogic.AclBlogic;
import jp.reflexworks.taggingservice.blogic.SecurityBlogic;
import jp.reflexworks.taggingservice.blogic.SecurityConst;
import jp.reflexworks.taggingservice.blogic.SessionBlogic;
import jp.reflexworks.taggingservice.context.ReflexContextUtil;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.AuthenticationException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.AccessTokenManager;
import jp.reflexworks.taggingservice.plugin.def.AuthenticationManagerDefault;
import jp.reflexworks.taggingservice.servlet.TaggingRequest;
import jp.reflexworks.taggingservice.servlet.TaggingServletUtil;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.CookieUtil;
import jp.reflexworks.taggingservice.util.ExceptionUtil;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.MessageUtil;
import jp.reflexworks.taggingservice.util.UserUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * 認証管理クラス.
 */
public class TaggingAuthenticationManager extends AuthenticationManagerDefault {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * サーバ起動時の初期処理.
	 */
	@Override
	public void init() {
		super.init();
		// Do nothing.
	}

	/**
	 * シャットダウン処理.
	 */
	@Override
	public void close() {
		super.init();
		// Do nothing.
	}

	/**
	 * 認証処理.
	 * 認証順序は、アクセストークン認証、リンクトークン認証、WSSE認証、RXID認証、セッション認証の順.
	 * ２段階認証を行う。
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
		TaggingUserManager userManager = new TaggingUserManager();
		SessionBlogic sessionBlogic = new SessionBlogic();
		SecurityBlogic securityBlogic = new SecurityBlogic();

		TaggingAuthentication auth = null;
		String uid = null;
		String account = null;
		String authType = null;
		String sessionId = null;
		String authCountUser = null;
		long authFailureCount = 0;
		Integer wsseWithoutCaptchaCount = null;

		if (logger.isTraceEnabled()) {
			logger.info(LogUtil.getRequestInfoStr(requestInfo) +
					"[authenticate] start.");
		}

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
					auth = new TaggingAuthentication(account, uid, sessionId,
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
						auth = new TaggingAuthentication(account, uid, sessionId, linkToken,
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
						auth = (TaggingAuthentication)sessionBlogic.createSession(
								account, uid, authType, serviceName, requestInfo, connectionInfo);
					}

					// ２段階認証
					boolean isTwoFactorAuth = isTwoFactorAuth(req, uid, systemContext, userManager);
					if (isTwoFactorAuth) {
						// ２段階認証が必要な場合、セッションに仮認証フラグを立てる。
						setSessionTempAuth(auth, requestInfo, connectionInfo, sessionBlogic);
					} else {
						// 信頼できる端末の場合、Cookieの有効期限を更新する。→ここではできないので、情報をauthに入れて引き継ぐ。
						setTdidToAuth(false, req, auth, requestInfo, connectionInfo, userManager);
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
					ReflexAuthentication tmpAuth = sessionBlogic.getAuthFromSession(uid,
							systemContext, sessionId);
					if (tmpAuth != null) {
						auth = new TaggingAuthentication(tmpAuth);
					}

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

					// 仮認証かどうかチェック
					boolean isTemporary = isSessionTempAuth(auth, requestInfo, connectionInfo,
							sessionBlogic);
					boolean isVerifiedTotp = false;
					if (isTemporary) {
						// ワンタイムパスワードチェック
						String totpPassword = getTOTPOnetimePassword(req);
						if (!StringUtils.isBlank(totpPassword)) {
							isVerifiedTotp = verifyOnetimePassword(totpPassword, auth,
									systemContext, userManager);
							if (isVerifiedTotp) {
								// 本登録にする
								deleteSessionTempAuth(auth, requestInfo, connectionInfo, sessionBlogic);
							}
						}
					}
					if (!auth.isTemporary()) {
						// 信頼できる端末の場合、Cookieの有効期限を更新する。→ここではできないので、情報をauthに入れて引き継ぐ。
						setTdidToAuth(isVerifiedTotp, req, auth, requestInfo, connectionInfo, userManager);
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
				auth = new TaggingAuthentication(account, uid, sessionId, authType,
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
	@Override
	public boolean afterAutheticate(ReflexRequest req, ReflexResponse resp,
			ReflexAuthentication pAuth)
	throws IOException, TaggingException {
		if (pAuth == null) {
			return false;
		}
		String serviceName = req.getServiceName();
		TaggingAuthentication auth = (TaggingAuthentication)pAuth;
		// 仮認証の場合、メッセージを設定し処理終了する。
		if (auth.isTemporary()) {
			// Cookieにセッションをセット
			setSessionIdToResponse(req, resp);
			// 仮認証メッセージをレスポンス
			FeedBase respFeed = MessageUtil.createMessageFeed(AuthenticatorConst.MSG_TEMP_AUTH,
					serviceName);
			TaggingServletUtil.doResponse(req, resp, respFeed,
					HttpStatus.SC_NON_AUTHORITATIVE_INFORMATION);
			return false;
		}
		return true;
	}

	/**
	 * セッションIDをレスポンスに設定
	 * @param req リクエスト
	 * @param resp レスポンス
	 */
	@Override
	public void setSessionIdToResponse(ReflexRequest req, ReflexResponse resp)
	throws IOException, TaggingException {
		super.setSessionIdToResponse(req, resp);
		// TDIDが設定されている場合、Cookieに設定・更新する。
		TaggingAuthentication auth = (TaggingAuthentication)req.getAuth();
		if (auth == null) {
			return;
		}
		String tdid = auth.getTdid();
		if (!StringUtils.isBlank(tdid)) {
			setCookie(req, resp, AuthenticatorConst.TDID, tdid, getMaxAgeTDID());
		}
	}

	/**
	 * ２段階認証対象かどうか
	 * @param req リクエスト
	 * @param uid UID
	 * @param systemContext SystemContext
	 * @param userManager ReflexUserManager
	 * @return ２段階認証の対象であればtrue
	 */
	private boolean isTwoFactorAuth(TaggingRequest req, String uid,
			SystemContext systemContext, TaggingUserManager userManager)
	throws IOException, TaggingException {
		// /_user/{UID}/totp エントリーが存在する場合、２段階認証を行う。
		// ただし、信頼できる端末であれば２段階認証は行わない。
		// 信頼できる端末には、Cookieに「信頼できる端末」を設定する。
		// 信頼できる端末に指定する値(以下TDID)は/_user/{UID}/trusted_deviceに設定する。
		boolean useCache = true;
		String totpSecret = userManager.getUserTotpSecretByUid(uid, useCache, systemContext);
		if (!StringUtils.isBlank(totpSecret)) {
			String tdidSecret = userManager.getUserTDIDSecretByUid(uid, useCache, systemContext);
			String cookieTdid = CookieUtil.getCookieValue(req, AuthenticatorConst.TDID);
			if (!StringUtils.isBlank(cookieTdid) &&
					cookieTdid.equals(tdidSecret)) {
				return false;
			}
			return true;
		} else {
			return false;
		}
	}

	/**
	 * セッションが仮認証状態かどうか判定する.
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @param sessionBlogic SessionBlogic
	 */
	private boolean isSessionTempAuth(TaggingAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo, SessionBlogic sessionBlogic)
	throws IOException, TaggingException {
		ReflexContext reflexContext = ReflexContextUtil.getReflexContext(auth,
				requestInfo, connectionInfo);
		Long val = sessionBlogic.getLong(AuthenticatorConst.SESSION_KEY_TEMPAUTH, reflexContext);
		boolean isTemporary = false;
		if (val != null) {
			isTemporary = true;
		}
		auth.setTemporary(isTemporary);
		return isTemporary;
	}

	/**
	 * セッションを仮認証状態にする.
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @param sessionBlogic SessionBlogic
	 */
	private void setSessionTempAuth(TaggingAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo, SessionBlogic sessionBlogic)
	throws IOException, TaggingException {
		ReflexContext reflexContext = ReflexContextUtil.getReflexContext(auth,
				requestInfo, connectionInfo);
		sessionBlogic.setLongIfAbsent(AuthenticatorConst.SESSION_KEY_TEMPAUTH,
				AuthenticatorConst.SESSION_VALUE_TEMPAUTH, reflexContext);
		auth.setTemporary(true);
	}

	/**
	 * セッションを本認証状態にする.
	 * 仮認証情報を削除する。
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @param sessionBlogic SessionBlogic
	 */
	private void deleteSessionTempAuth(TaggingAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo, SessionBlogic sessionBlogic)
	throws IOException, TaggingException {
		ReflexContext reflexContext = ReflexContextUtil.getReflexContext(auth,
				requestInfo, connectionInfo);
		sessionBlogic.deleteLong(AuthenticatorConst.SESSION_KEY_TEMPAUTH, reflexContext);
		auth.setTemporary(false);
	}

	/**
	 * 信頼される端末の場合、認証情報にTDID(信頼される端末に設定する値)をセットする.
	 * のちにレスポンスにセットする。
	 * WSSE・RXID認証後か、セッション本認証後に呼び出されるメソッド。
	 * @param isVerifiedTotp ２段階認証を行った直後の場合true
	 * @param req リクエスト
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @param userManager TaggingUserManager
	 */
	private void setTdidToAuth(boolean isVerifiedTotp, TaggingRequest req, TaggingAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo, TaggingUserManager userManager)
	throws IOException, TaggingException {
		// 自分の認証情報付きのSystemContextを作成。
		// 生成するEntryのcreatedにuidを指定するため。
		SystemContext systemContext = new SystemContext(auth, requestInfo, connectionInfo);
		String tdid = userManager.getUserTDIDSecretByUid(auth.getUid(), true, systemContext);
		boolean setTdid = false;
		if (!StringUtils.isBlank(tdid)) {
			// CookieにTDIDがセットされている場合、有効期限更新のためレスポンスにTDIDをセットする。
			String cookieTdid = CookieUtil.getCookieValue(req, AuthenticatorConst.TDID);
			if (tdid.equals(cookieTdid)) {
				setTdid = true;
			}
		}
		if (isVerifiedTotp) {
			// ２段階認証後
			// リクエストヘッダに「X-TRUSTED-DEVICE:true」が指定されている場合、レスポンスにTDIDをセットする。
			String xTrustedDevice = getXTrustedDevice(req);
			if (AuthenticatorConst.X_TRUSTED_DEVICE_VALUE.equals(xTrustedDevice)) {
				setTdid = true;
				if (StringUtils.isBlank(tdid)) {
					// 公開鍵の発行
					tdid = userManager.createUserTDIDSecretByUid(auth.getUid(), systemContext);
				}
			}
		}

		if (setTdid) {
			auth.setTdid(tdid);
		}
	}
	
	/**
	 * リクエストヘッダから X-TRUSTED-DEVICE の値を取得。
	 * 小文字(x-trusted-device)にも対応。(Javascriptのfetchリクエストはヘッダ名が小文字になる場合がある。)
	 * @param req リクエスト
	 * @return リクエストヘッダのX-TRUSTED-DEVICEの値
	 */
	private String getXTrustedDevice(TaggingRequest req) {
		String xTrustedDevice = req.getHeader(AuthenticatorConst.HEADER_X_TRUSTED_DEVICE);
		if (StringUtils.isBlank(xTrustedDevice)) {
			xTrustedDevice = req.getHeader(AuthenticatorConst.HEADER_X_TRUSTED_DEVICE_LOWER);
		}
		return xTrustedDevice;

	}

	/**
	 * リクエストからTOTPワンタイムパスワードを取得.
	 * @param req リクエスト
	 * @return TOTPワンタイムパスワード
	 */
	private String getTOTPOnetimePassword(TaggingRequest req) {
		return ReflexServletUtil.getHeaderValue(req,
				AuthenticatorConst.HEADER_AUTHORIZATION,
				AuthenticatorConst.HEADER_AUTHORIZATION_TOTP);
	}

	/**
	 * TOTPワンタイムパスワードを検証.
	 * @param onetimePasswordStr TOTPワンタイムパスワード
	 * @param auth 認証情報
	 * @param systemContext SystemContext
	 * @param userManager TaggingUserManager
	 * @return TOTPワンタイムパスワード検証OKの場合true
	 */
	private boolean verifyOnetimePassword(String onetimePasswordStr, TaggingAuthentication auth,
			SystemContext systemContext, TaggingUserManager userManager)
	throws IOException, TaggingException {
		if (!StringUtils.isInteger(onetimePasswordStr)) {
			return false;
		}
		int onetimePassword = Integer.parseInt(onetimePasswordStr);
		String uid = auth.getUid();
		// TOTP公開鍵を取得
		String totpSecret = userManager.getUserTotpSecretByUid(uid, true, systemContext);
		if (StringUtils.isBlank(totpSecret)) {
			return false;
		}
		return TOTPUtil.verifyOnetimePassword(totpSecret, onetimePassword);
	}

	/**
	 * Cookieに設定するTDIDのMaxAge(秒)を取得.
	 * @return Cookieに設定するTDIDのMaxAge(秒)
	 */
	private int getMaxAgeTDID() {
		return AuthenticatorConst.MAXAGE_TDID;
	}

	/**
	 * 認証情報の複製.
	 * ReflexContextでExternalを設定するために使用する。
	 * @param auth 認証情報
	 * @return 複製した認証オブジェクト
	 */
	@Override
	public ReflexAuthentication copyAuth(ReflexAuthentication auth) {
		return new TaggingAuthentication(auth);
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
		return new TaggingAuthentication(account, uid, sessionId, authType, serviceName);
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
		return new TaggingAuthentication(account, uid, sessionId, linkToken, authType, serviceName);
	}

}
