package jp.reflexworks.taggingservice.util;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.servlet.ReflexServletUtil;
import jp.reflexworks.servlet.util.WsseAuth;
import jp.reflexworks.servlet.util.WsseUtil;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestType;
import jp.reflexworks.taggingservice.api.ResponseInfo;
import jp.reflexworks.taggingservice.blogic.LogBlogic;
import jp.reflexworks.taggingservice.blogic.LoginLogoutBlogic;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.AuthLockedException;
import jp.reflexworks.taggingservice.exception.AuthenticationException;
import jp.reflexworks.taggingservice.exception.ConnectionException;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.reflexworks.taggingservice.exception.NoEntryException;
import jp.reflexworks.taggingservice.exception.NotInServiceException;
import jp.reflexworks.taggingservice.exception.PermissionException;
import jp.reflexworks.taggingservice.exception.ShutdownException;
import jp.reflexworks.taggingservice.exception.SignatureInvalidException;
import jp.reflexworks.taggingservice.plugin.RequestResponseManager;
import jp.reflexworks.taggingservice.util.Constants.LogLevel;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * 例外判定ユーティリティ.
 */
public class ExceptionUtil implements ReflexServletConst {

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(ExceptionUtil.class);

	/**
	 * WSSEまたはRXIDの認証エラー時、サブメッセージに設定する内容を編集.
	 * @param req リクエスト。nullの場合WSSEまたはRXID情報をWSSE情報から生成。
	 * @param wsseAuth WSSE情報
	 * @return サブメッセージへの設定内容
	 */
	public static String getAuthErrorSubMessageValue(ReflexRequest req, WsseAuth wsseAuth) {
		StringBuilder sb = new StringBuilder();
		WsseUtil wsseUtil = new WsseUtil();
		if (wsseAuth.isRxid) {
			String rxid = null;
			String from = null;
			if (wsseAuth.isQueryString) {
				if (req != null) {
					rxid = wsseUtil.getRXIDValueByParam(req);
				} else {
					rxid = WsseUtil.getRXIDString(wsseAuth);
				}
				from = "urlparam";
			} else if (wsseAuth.isCookie) {
				if (req != null) {
					rxid = wsseUtil.getRXIDValueByCookie(req);
				} else {
					rxid = WsseUtil.getRXIDString(wsseAuth);
				}
				from = "cookie";
			} else {
				if (req != null) {
					rxid = wsseUtil.getRXIDValueByHeader(req);
				} else {
					rxid = WsseUtil.getRXIDString(wsseAuth);
				}
				from = "header";
			}
			sb.append(rxid);
			sb.append(" (");
			sb.append(from);
			sb.append(")");
		} else {
			if (req != null) {
				sb.append(wsseUtil.getWSSEValue(req));
			} else {
				sb.append(wsseAuth.getWsseString());
			}
		}
		return sb.toString();
	}

	/**
	 * エラーレベルを取得.
	 * @return エラーレベル
	 */
	public static String getLevelError() {
		return ReflexExceptionUtil.ERROR;
	}

	/**
	 * エラー処理.
	 * ReflexExceptionUtil.doException の処理 + エラーページ対応
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param e 例外
	 */
	public static void doException(ReflexRequest req, ReflexResponse resp, Throwable te)
	throws IOException {
		RequestInfo requestInfo = req.getRequestInfo();
		ConnectionInfo connectionInfo = req.getConnectionInfo();
		String serviceName = req.getServiceName();
		String method = req.getMethod();
		Throwable e = peelWrap(te);

		LogBlogic logBlogic = new LogBlogic();
		ResponseInfo<FeedBase> responseInfo =
				ReflexExceptionUtil.getStatus(e, method, false, serviceName, requestInfo);
		int responseCode = responseInfo.status;
		String message = responseInfo.data.title;
		FeedBase respFeed = responseInfo.data;
		if (ExceptionUtil.getLevelError().equals(responseInfo.data.rights)) {	// 致命的エラーの場合
			StringBuilder sb = new StringBuilder();
			sb.append(e.getClass().getName());
			sb.append(": ");
			sb.append(e.getMessage());
			logBlogic.writeLogger(responseCode, sb.toString(), LogLevel.ERROR, e,
					serviceName, requestInfo, connectionInfo);

		} else if (responseCode == SC_UPGRADE_REQUIRED) {
			// サービス設定エラー
			// スタックトレースは出力しない。ログエントリーのみ。
			logBlogic.writeLogger(responseCode, message, LogLevel.INFO, e,
					serviceName, requestInfo, connectionInfo);

		} else if (responseCode == SC_FORBIDDEN ||
				responseCode == SC_UNAUTHORIZED) {
			logBlogic.writeLogger(responseCode, message, LogLevel.INFO, e,
					serviceName, requestInfo, connectionInfo);

		} else if (responseCode != SC_NO_CONTENT &&
				responseCode != SC_FORBIDDEN &&
				responseCode != SC_PRECONDITION_FAILED &&
				!(responseCode == SC_FAILED_DEPENDENCY &&
						(NotInServiceException.MESSAGE_PREFIX.equals(message) ||
								NotInServiceException.MESSAGE_NULL.equals(message)))) {
			logBlogic.writeLogger(responseCode, message, LogLevel.INFO, e,
					serviceName, requestInfo, connectionInfo);

		} else {
			// スタックトレースをログ出力
			if (logger.isInfoEnabled()) {
				// NoEntryException以外を出力
				if (!(e instanceof NoEntryException) &&
						!(e instanceof PermissionException) &&
						!(e instanceof AuthenticationException) &&
						!(e instanceof InvalidServiceSettingException) &&
						!(e instanceof SignatureInvalidException)) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + e.getClass().getSimpleName(), e);
				}
			}
		}

		if (e instanceof AuthenticationException &&
				!(e instanceof AuthLockedException)) {
			// WWW-Authenticate: None
			resp.addHeader(HEADER_WWW_AUTHENTICATE, HEADER_VALUE_NONE);
		}

		// ログイン認証エラーの場合は、ログイン履歴を出力する。
		if (e instanceof AuthenticationException) {
			// ログイン失敗履歴出力
			LoginLogoutBlogic loginLogoutBlogic = new LoginLogoutBlogic();
			loginLogoutBlogic.writeAuthError(req, (AuthenticationException)e);
		}

		String errorpageSelfid = null;
		Map<Pattern, String> patterns = null;
		try {
			patterns = ErrorPageUtil.getErrorPagePatterns(serviceName);
		} catch (InvalidServiceSettingException ee) {
			logger.warn(LogUtil.getRequestInfoStr(requestInfo) + "[doException] getErrorPagePatterns error." + ee);
		}
		if (patterns != null) {
			try {
				String uri = null;
				RequestType param = req.getRequestType();
				if (param != null) {
					uri = param.getUri();
				}
				for (Map.Entry<Pattern, String> mapEntry : patterns.entrySet()) {
					Pattern pattern = mapEntry.getKey();
					Matcher matcher = pattern.matcher(uri);
					if (matcher.matches()) {
						errorpageSelfid = mapEntry.getValue();
						if (!StringUtils.isBlank(errorpageSelfid)) {
							break;
						}
					}
				}
			} catch (Throwable t) {
				logger.error(LogUtil.getRequestInfoStr(requestInfo) + t.getClass().getName(), t);
			}
		}

		boolean isRedirect = false;
		if (!StringUtils.isBlank(errorpageSelfid)) {
			// エラーページ表示パターンの場合、エラーページへのリダイレクト。
			isRedirect = ErrorPageUtil.doErrorPageRedirect(req, resp,
					errorpageSelfid, responseCode, message);
		}

		Object respObj = respFeed;
		if (!isRedirect) {
			// データで返却の場合
			if (responseCode == SC_EXPECTATION_FAILED) {
				// X-Requested-Withヘッダエラーはレスポンスデータを返さない。
				respObj = null;
			}

			if (!resp.isWritten()) {
				RequestResponseManager reqRespManager = TaggingEnvUtil.getRequestResponseManager();
				int format = req.getResponseFormat();
				boolean isGZip = reqRespManager.isGZip();
				boolean isStrict = false;
				boolean isNoCache = reqRespManager.isNoCache(req);
				boolean isSameOrigin = reqRespManager.isSameOrigin(req);
				ReflexServletUtil.doResponse(req, resp, respObj, format,
						TaggingEnvUtil.getResourceMapper(serviceName),
						connectionInfo.getDeflateUtil(), responseCode, isGZip,
						isStrict, isNoCache, isSameOrigin);
			}
		}
	}

	/**
	 * 例外スローのためのラップ例外を取り除く.
	 * @param te 例外
	 * @return ラップ例外を取り除いた例外
	 */
	public static Throwable peelWrap(Throwable te) {
		Throwable e = te;
		if (e instanceof IOException) {
			if (!(e instanceof ConnectionException) && e.getCause() != null) {
				e = e.getCause();
			}
		}
		if (e instanceof ExecutionException) {
			if (e.getCause() != null) {
				e = e.getCause();
				if ("java.lang.RuntimeException".equals(e.getClass().getName()) &&
						e.getCause() != null) {
					e = e.getCause();
				}
			}
		}
		if (e instanceof NullPointerException) {
			// サーバシャットダウン時かどうかの判定
			if (!TaggingEnvUtil.isRunning()) {
				e = new ShutdownException("Tagging service is not running.", e);
			}
		}
		return e;
	}

}
