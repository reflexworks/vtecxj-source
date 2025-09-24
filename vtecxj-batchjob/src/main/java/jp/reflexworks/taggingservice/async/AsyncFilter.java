package jp.reflexworks.taggingservice.async;

import java.io.IOException;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.servlet.ReflexServletUtil;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.ResponseInfo;
import jp.reflexworks.taggingservice.context.ReflexContextUtil;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.NotInServiceException;
import jp.reflexworks.taggingservice.plugin.AuthenticationManager;
import jp.reflexworks.taggingservice.plugin.RequestResponseManager;
import jp.reflexworks.taggingservice.plugin.ServiceManager;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.ExceptionUtil;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.ReflexExceptionUtil;

/**
 * サーバサイドJS・データ移行非同期処理フィルタ
 */
public class AsyncFilter implements Filter {

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
			// このシステムが稼働中かどうかチェック
			if (!TaggingEnvUtil.isRunning()) {
				throw new IllegalStateException("Tagging service is not running.");
			}

			// サービス稼働チェック
			ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
			boolean isEnabledService = serviceManager.isEnabled(req, serviceName,
					requestInfo, connectionInfo);
			if (isEnabledService) {
				// このノードでサービス情報を保持していない場合、設定処理を行う。
				serviceManager.settingServiceIfAbsent(serviceName, requestInfo,
						connectionInfo);
			}

			if (!isEnabledService) {
				// サービス稼働エラー
				throw new NotInServiceException(serviceName);
			}

			// 認証
			ReflexAuthentication auth = authenticationManager.autheticate(req);
			reqRespManager.afterAuthenticate(req, resp, auth);

			// 開始ログ
			ReflexContext reflexContext = ReflexContextUtil.getReflexContext(req);
			AsyncUtil.logStart(req, reflexContext);
			long startTime = new Date().getTime();

			// サーブレットに処理移譲
			chain.doFilter(req, resp);

			// 終了ログ
			AsyncUtil.logEnd(req, startTime, reflexContext);

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
	 * シャットダウン.
	 */
	@Override
	public void destroy() {
		// Do nothing.
	}

	/**
	 * エラー処理.
	 * ログエントリー登録。ロガー出力。
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param e 例外
	 */
	private void doException(ReflexRequest req, ReflexResponse resp, Throwable te)
	throws IOException {
		String serviceName = req.getServiceName();
		RequestInfo requestInfo = req.getRequestInfo();
		ConnectionInfo connectionInfo = req.getConnectionInfo();
		SystemContext systemContext = new SystemContext(serviceName, requestInfo, connectionInfo);
		Throwable e = ExceptionUtil.peelWrap(te);
		// 致命的エラーの場合ロガー出力
		ResponseInfo<FeedBase> responseInfo = ReflexExceptionUtil.getStatus(e, req.getMethod(),
				true, serviceName, requestInfo);
		// ログエントリー出力
		AsyncUtil.logError(e, systemContext);
		// レスポンス
		if (!resp.isWritten()) {
			RequestResponseManager reqRespManager = TaggingEnvUtil.getRequestResponseManager();
			int format = req.getResponseFormat();
			boolean isGZip = reqRespManager.isGZip();
			boolean isStrict = false;
			boolean isNoCache = reqRespManager.isNoCache(req);
			boolean isSameOrigin = reqRespManager.isSameOrigin(req);
			ReflexServletUtil.doResponse(req, resp, responseInfo.data, format,
					TaggingEnvUtil.getResourceMapper(serviceName),
					connectionInfo.getDeflateUtil(), responseInfo.status, isGZip,
					isStrict, isNoCache, isSameOrigin);
		}
	}

}
