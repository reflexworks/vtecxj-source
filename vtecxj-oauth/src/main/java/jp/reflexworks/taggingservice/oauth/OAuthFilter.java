package jp.reflexworks.taggingservice.oauth;

import java.io.IOException;

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

import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.blogic.SecurityBlogic;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.NotInServiceException;
import jp.reflexworks.taggingservice.plugin.AuthenticationManager;
import jp.reflexworks.taggingservice.plugin.LogManager;
import jp.reflexworks.taggingservice.plugin.RequestResponseManager;
import jp.reflexworks.taggingservice.plugin.ServiceManager;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.ExceptionUtil;
import jp.reflexworks.taggingservice.util.LogUtil;

/**
 * OAuth Filter
 */
public class OAuthFilter implements Filter, ReflexServletConst {

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
		LogManager logManager = TaggingEnvUtil.getLogManager();

		// リクエスト・レスポンスをラップ
		ReflexRequest req = reqRespManager.createReflexRequest(httpReq);
		ReflexResponse resp = reqRespManager.createReflexResponse(req, httpResp);

		String serviceName = req.getServiceName();
		RequestInfo requestInfo = req.getRequestInfo();
		ConnectionInfo connectionInfo = req.getConnectionInfo();

		try {
			// サービス稼働チェック
			ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
			boolean isEnabledService = serviceManager.isEnabled(req, serviceName,
					requestInfo, connectionInfo);
			if (isEnabledService) {
				// このノードでサービス情報を保持していない場合、設定処理を行う。
				serviceManager.settingServiceIfAbsent(serviceName, requestInfo,
						connectionInfo);
			}

			// アクセスログ
			logManager.writeAccessStart(req);

			if (!isEnabledService) {
				// サービス稼働エラー
				throw new NotInServiceException(serviceName);
			}

			// サービスのリクエストプロトコルチェック
			boolean isProtocolOk = serviceManager.checkServiceStatus(req, resp);
			if (!isProtocolOk) {
				// リダイレクト設定済み
				return;
			}

			// セキュリティチェック
			SecurityBlogic securityBlogic = new SecurityBlogic();
			securityBlogic.checkRequestHost(req, resp);
			String method = req.getMethod();
			if (Constants.POST.equalsIgnoreCase(method) ||
					Constants.PUT.equalsIgnoreCase(method) ||
					Constants.DELETE.equalsIgnoreCase(method)) {
				// X-Requested-With ヘッダチェックはGETの場合行わない。
				securityBlogic.checkRequestedWith(req);
			}

			// アクセスカウンタ
			serviceManager.incrementAccessCounter(serviceName, requestInfo, connectionInfo);

			// サービス名だけセットした認証情報を設定
			AuthenticationManager authManager = TaggingEnvUtil.getAuthenticationManager();
			ReflexAuthentication auth = authManager.createAuth(null, null, null, null,
					serviceName);
			reqRespManager.afterAuthenticate(req, resp, auth);

			// サーブレットに処理移譲
			chain.doFilter(req, resp);

		} catch (Throwable e) {
			// エラーの場合
			ExceptionUtil.doException(req, resp, e);

		} finally {
			// リクエストのクリア (Deflate圧縮辞書メモリ解放)
			try {
				req.close();
			} catch (Exception e) {
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "Request close error.", e);
				}
			}

			// アクセスログ
			logManager.writeAccessEnd(req, resp);
		}
	}

}
