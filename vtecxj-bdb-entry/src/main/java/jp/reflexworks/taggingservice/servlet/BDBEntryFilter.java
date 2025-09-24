package jp.reflexworks.taggingservice.servlet;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

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
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.ResponseInfo;
import jp.reflexworks.taggingservice.env.BDBEnvUtil;
import jp.reflexworks.taggingservice.exception.ConnectionException;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.NoEntryException;
import jp.reflexworks.taggingservice.exception.NotInServiceException;
import jp.reflexworks.taggingservice.util.Constants.LogLevel;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.ReflexBDBLogUtil;
import jp.reflexworks.taggingservice.util.ReflexCheckUtil;
import jp.reflexworks.taggingservice.util.ReflexExceptionUtil;

/**
 * BDBインデックスサーブレットフィルタ
 */
public class BDBEntryFilter implements Filter, ReflexServletConst {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 初期処理.
	 * @param filterConfig FilterConfig
	 */
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

		// リクエスト・レスポンスをラップ
		BDBEntryRequest req = new BDBEntryRequest(httpReq);
		BDBEntryResponse resp = new BDBEntryResponse(req, httpResp);

		RequestInfo requestInfo = req.getRequestInfo();

		try {
			if (!BDBEnvUtil.isRunning()) {
				throw new IllegalStateException("Tagging BDB service (entry) is not running.");
			}

			// アクセスログ
			ReflexBDBLogUtil.writeAccessStart(req);

			// 名前空間指定チェック
			checkRequest(req);

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

			// アクセスログ
			ReflexBDBLogUtil.writeAccessEnd(req, resp);
		}
	}

	/**
	 * リクエストの基本設定チェック.
	 *  * 名前空間が指定されていなければエラー
	 *  * サービス名(ログ用)が指定されていなければエラー
	 * @param req リクエスト
	 */
	private void checkRequest(BDBEntryRequest req) {
		ReflexCheckUtil.checkNotNull(req.getNamespace(), "namespace");
		ReflexCheckUtil.checkNotNull(req.getServiceName(), "service name");
	}

	/**
	 * エラー処理.
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param te 例外
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

		ResponseInfo<FeedBase> responseInfo =
				ReflexExceptionUtil.getStatus(e, method, false, serviceName, requestInfo);
		int responseCode = responseInfo.status;
		String message = responseInfo.data.title;
		FeedBase respFeed = responseInfo.data;
		if (ReflexExceptionUtil.ERROR.equals(responseInfo.data.rights)) {	// 致命的エラーの場合
			StringBuilder sb = new StringBuilder();
			sb.append(e.getClass().getName());
			sb.append(": ");
			sb.append(e.getMessage());
			ReflexBDBLogUtil.writeLogger(responseCode, sb.toString(), LogLevel.ERROR, e,
					requestInfo);

		} else if (responseCode == SC_UPGRADE_REQUIRED) {
			// サービス設定エラー
			ReflexBDBLogUtil.writeLogger(responseCode, message, LogLevel.WARN, e,
					requestInfo);

		} else if (responseCode == SC_FORBIDDEN ||
				responseCode == SC_UNAUTHORIZED) {
			ReflexBDBLogUtil.writeLogger(responseCode, message, LogLevel.INFO, e,
					requestInfo);

		} else if (responseCode != SC_NO_CONTENT &&
				responseCode != SC_FORBIDDEN &&
				responseCode != SC_PRECONDITION_FAILED &&
				!(responseCode == SC_FAILED_DEPENDENCY &&
						(NotInServiceException.MESSAGE_PREFIX.equals(message) ||
								NotInServiceException.MESSAGE_NULL.equals(message))) &&
				!(e instanceof IllegalParameterException)) {
			ReflexBDBLogUtil.writeLogger(responseCode, message, LogLevel.INFO, e,
					requestInfo);
		}

		// スタックトレースをログ出力
		if (logger.isInfoEnabled()) {
			// NoEntryException以外を出力
			if (!(e instanceof NoEntryException) && !(e instanceof IllegalParameterException)) {
				logger.info(LogUtil.getRequestInfoStr(requestInfo) + e.getClass().getSimpleName(), e);
			}
		}

		if (!resp.isWritten()) {
			int format = req.getResponseFormat();
			boolean isGZip = BDBEnvUtil.isGZip();
			boolean isStrict = false;
			boolean isNoCache = BDBEnvUtil.isNoCache(req);
			boolean isSameOrigin = BDBEnvUtil.isSameOrigin(req);
			ReflexServletUtil.doResponse(req, resp, respFeed, format,
					BDBEnvUtil.getAtomResourceMapper(),
					connectionInfo.getDeflateUtil(), responseCode, isGZip,
					isStrict, isNoCache, isSameOrigin);
		}
	}

}
