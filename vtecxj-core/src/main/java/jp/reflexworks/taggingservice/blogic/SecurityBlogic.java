package jp.reflexworks.taggingservice.blogic;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.servlet.util.AuthTokenUtil;
import jp.reflexworks.servlet.util.WsseAuth;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.SettingConst;
import jp.reflexworks.taggingservice.env.TaggingEnvConst;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.AuthenticationException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.CaptchaManager;
import jp.reflexworks.taggingservice.plugin.ReflexSecurityManager;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.LogUtil;

/**
 * セキュリティチェック.
 */
public class SecurityBlogic {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * Hostヘッダのチェック (DNSリバインディング対策).
	 * エラーの場合AuthenticationExceptionをスローします。
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @throws AuthenticationException セキュリティチェックエラー
	 */
	public void checkRequestHost(ReflexRequest req, ReflexResponse resp)
	throws IOException, TaggingException {
		ReflexSecurityManager securityManager = TaggingEnvUtil.getSecurityManager();
		securityManager.checkRequestHost(req, resp);
	}

	/**
	 * Hostヘッダのチェック (DNSリバインディング対策).
	 * 内部リクエスト用。
	 * エラーの場合AuthenticationExceptionをスローします。
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @throws AuthenticationException セキュリティチェックエラー
	 */
	public void checkRequestHostByInside(ReflexRequest req, ReflexResponse resp)
	throws IOException, TaggingException {
		ReflexSecurityManager securityManager = TaggingEnvUtil.getSecurityManager();
		securityManager.checkRequestHostByInside(req, resp);
	}

	/**
	 * X-Requested-Withヘッダチェック.
	 * 以下の場合、リクエストヘッダにX-Requested-Withの指定がないとエラー。
	 * <ul>
	 *   <li>POST、PUT、DELETE</li>
	 *   <li>戻り値がJSON</li>
	 * </ul>
	 * @param req リクエスト
	 */
	public void checkRequestedWith(ReflexRequest req)
	throws IOException, TaggingException {
		ReflexSecurityManager securityManager = TaggingEnvUtil.getSecurityManager();
		securityManager.checkRequestedWith(req);
	}

	/**
	 * ブラックリストチェック.
	 * 同一ユーザ+IPアドレスで認証失敗回数が一定回数を超えた場合認証エラーとする。
	 * @param user ユーザ識別値
	 * @param req リクエスト
	 * @param systemContext SystemContext
	 * @return 認証失敗回数
	 */
	public long checkAuthFailureCount(String user, ReflexRequest req,
			SystemContext systemContext)
	throws IOException, TaggingException {
		String serviceName = systemContext.getServiceName();
		long authFailureCount = getAuthFailureCount(user, req, systemContext);
		long maxAuthFailedCount = TaggingEnvUtil.getPropLong(
				serviceName, SettingConst.AUTH_FAILED_COUNT,
				TaggingEnvUtil.getSystemPropLong(SettingConst.AUTH_FAILED_COUNT,
						TaggingEnvConst.AUTH_FAILED_COUNT_DEFAULT));
		if (authFailureCount > maxAuthFailedCount) {
			String ip = req.getLastForwardedAddr();
			StringBuilder sb = new StringBuilder();
			sb.append("IP address is included in the black list. user=");
			sb.append(user);
			sb.append(", ip=");
			sb.append(ip);
			String msg = sb.toString();
			if (logger.isDebugEnabled()) {
				logger.debug(LogUtil.getRequestInfoStr(req.getRequestInfo()) +
						"[checkAuthFailureCount] " + msg);
			}
			AuthenticationException ae = new AuthenticationException("Security error.");
			ae.setSubMessage(msg);
			throw ae;
		}
		return authFailureCount;
	}

	/**
	 * 認証失敗回数を取得
	 * @param user ユーザ識別値
	 * @param req リクエスト
	 * @param systemContext SystemContext
	 * @return ユーザ識別値+IPアドレスでの認証失敗回数
	 */
	public long getAuthFailureCount(String user, ReflexRequest req,
			SystemContext systemContext)
	throws IOException, TaggingException {
		ReflexSecurityManager securityManager = TaggingEnvUtil.getSecurityManager();
		return securityManager.getAuthFailureCount(user, req, systemContext);
	}

	/**
	 * 認証失敗回数を加算
	 * @param user ユーザ識別値
	 * @param req リクエスト
	 * @param systemContext SystemContext
	 * @return ユーザ識別値+IPアドレスでの認証失敗回数
	 */
	public long incrementAuthFailureCount(String user, ReflexRequest req,
			SystemContext systemContext)
	throws IOException, TaggingException {
		ReflexSecurityManager securityManager = TaggingEnvUtil.getSecurityManager();
		return securityManager.incrementAuthFailureCount(user, req, systemContext);
	}

	/**
	 * 認証失敗回数をクリア
	 * @param user ユーザ識別値
	 * @param req リクエスト
	 * @param systemContext SystemContext
	 */
	public void clearAuthFailureCount(String user, ReflexRequest req,
			SystemContext systemContext)
	throws IOException, TaggingException {
		ReflexSecurityManager securityManager = TaggingEnvUtil.getSecurityManager();
		securityManager.clearAuthFailureCount(user, req, systemContext);
	}

	/**
	 * ホワイトリストチェック.
	 * サービス管理者の場合、ホワイトリストの指定があればホワイトリストのアドレスからのみリクエスト可
	 * @param req リクエスト
	 * @param systemContext SystemContext
	 */
	public void checkAdminAddress(ReflexRequest req, SystemContext systemContext)
	throws IOException, TaggingException {
		ReflexSecurityManager securityManager = TaggingEnvUtil.getSecurityManager();
		securityManager.checkWhiteRemoteAddress(req, systemContext);
	}

	/**
	 * RXID使用回数チェック.
	 * GETでPathInfoが指定されたパターンと合致しているものは、指定された回数。その他は1回。
	 * RXIDの使用回数が上記の回数を超えている場合はAuthenticationExceptionをスローします。
	 * @param wsseAuth WSSE認証情報
	 * @param req リクエスト
	 * @param systemContext SystemContext
	 */
	public void checkRXIDCount(WsseAuth wsseAuth, ReflexRequest req,
			SystemContext systemContext)
	throws IOException, TaggingException {
		String rxidStr = AuthTokenUtil.getRXIDString(wsseAuth);
		ReflexSecurityManager securityManager = TaggingEnvUtil.getSecurityManager();
		securityManager.checkRXIDCount(rxidStr, req, systemContext);
	}

	/**
	 * Catpchaチェック.
	 * @param req リクエスト
	 * @param action アクション
	 * @param systemContext SystemContext
	 */
	public void checkCaptcha(ReflexRequest req, String action)
	throws IOException, TaggingException {
		CaptchaManager captchaManager = TaggingEnvUtil.getCaptchaManager();
		captchaManager.verify(req, action);
	}

	/**
	 * キャプチャ不要なWSSE認証回数を取得.
	 * @param serviceName サービス名
	 * @return キャプチャ不要なWSSE認証回数
	 */
	public int getWsseWithoutCaptchaCount(String serviceName) {
		CaptchaManager captchaManager = TaggingEnvUtil.getCaptchaManager();
		return captchaManager.getWsseWithoutCaptchaCount(serviceName);
	}
	
	/**
	 * 登録されたクロスドメインの場合、Origin URLを許可するレスポンスヘッダを付加する.
	 * @param req リクエスト
	 * @param resp レスポンス
	 */
	public void checkCORS(ReflexRequest req, ReflexResponse resp) 
	throws IOException, TaggingException {
		ReflexSecurityManager securityManager = TaggingEnvUtil.getSecurityManager();
		securityManager.checkCORS(req, resp);
	}

}
