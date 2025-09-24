package jp.reflexworks.taggingservice.memorysort;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.servlet.ReflexServletUtil;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
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
import jp.reflexworks.taggingservice.plugin.AuthenticationManager;
import jp.reflexworks.taggingservice.plugin.RequestResponseManager;
import jp.reflexworks.taggingservice.plugin.ServiceManager;
import jp.reflexworks.taggingservice.util.Constants.LogLevel;
import jp.reflexworks.taggingservice.util.ErrorPageUtil;
import jp.reflexworks.taggingservice.util.ExceptionUtil;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.ReflexExceptionUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * インメモリソートサーバ Filter
 */
public class MemorySortFilter implements Filter, ReflexServletConst {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 初期処理.
	 * @param filterConfig FilterConfig
	 */
	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		// Do nothing.
	}

	/**
	 * シャットダウン.
	 */
	@Override
	public void destroy() {
		// Do nothing.
	}

	/**
	 * フィルタ処理.
	 * @param request リクエスト
	 * @param response レスポンス
	 * @param chain チェイン
	 */
	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		HttpServletRequest httpReq = (HttpServletRequest)request;
		HttpServletResponse httpResp = (HttpServletResponse)response;

		RequestResponseManager reqRespManager = TaggingEnvUtil.getRequestResponseManager();
		AuthenticationManager authenticationManager =
				TaggingEnvUtil.getAuthenticationManager();

		// リクエスト・レスポンスをラップ
		ReflexRequest req = reqRespManager.createReflexRequest(httpReq);
		ReflexResponse resp = reqRespManager.createReflexResponse(req, httpResp);

		String serviceName = req.getServiceName();
		RequestInfo requestInfo = req.getRequestInfo();
		ConnectionInfo connectionInfo = req.getConnectionInfo();

		try {
			// サービス稼働チェック、テンプレート内容保持
			ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
			boolean isEnabledService = serviceManager.isEnabled(req, serviceName,
					requestInfo, connectionInfo);
			if (isEnabledService) {
				// このノードでサービス情報を保持していない場合、設定処理を行う。
				serviceManager.settingServiceIfAbsent(serviceName, requestInfo,
						connectionInfo);
			}

			// サービス設定後にRequestParamを取得する。(内部処理でテンプレートを使用するため。)
			req.getRequestType();

			if (!isEnabledService) {
				// サービス稼働エラー
				throw new NotInServiceException(serviceName);
			}

			// 認証
			ReflexAuthentication auth = authenticationManager.autheticate(req);
			reqRespManager.afterAuthenticate(req, resp, auth);

			// サーブレットに処理移譲
			chain.doFilter(req, resp);

		} catch (Throwable e) {
			// エラーの場合
			doException(req, resp, e);

		} finally {
			// リクエストのクリア (Deflate圧縮辞書メモリ解放)
			try {
				req.close();
			} catch (Exception e) {
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "Request close error.", e);
				}
			}
		}
	}

	/**
	 * エラー処理.
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param e 例外
	 */
	private void doException(ReflexRequest req, ReflexResponse resp, Throwable te)
	throws IOException {
		RequestInfo requestInfo = req.getRequestInfo();
		ConnectionInfo connectionInfo = req.getConnectionInfo();
		String serviceName = req.getServiceName();
		String method = req.getMethod();
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
			logBlogic.writeLogger(responseCode, message, LogLevel.WARN, e,
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
						!(e instanceof AuthenticationException)) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) +
							e.getClass().getSimpleName(), e);
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

}
