package jp.reflexworks.taggingservice.memorysort;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jp.reflexworks.servlet.HttpStatus;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.ReflexServletBase;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.context.ReflexContextUtil;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.MethodNotAllowedException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.RequestParamInfo;
import jp.reflexworks.taggingservice.provider.ProviderUtil;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * インメモリソート サーブレット.
 */
public class MemorySortServlet extends ReflexServletBase {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 初期処理.
	 */
	@Override
	public void init() throws ServletException {
		super.init();
	}

	/**
	 * GETメソッド処理
	 * @param httpReq リクエスト
	 * @param httpResp レスポンス
	 */
	@Override
	public void doGet(HttpServletRequest httpReq, HttpServletResponse httpResp)
	throws IOException {

		ReflexRequest req = (ReflexRequest)httpReq;
		ReflexResponse resp = (ReflexResponse)httpResp;
		RequestInfo requestInfo = req.getRequestInfo();
		if (logger.isTraceEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "doGet start");
		}
		// ここではExternal判定ができないので、全てExternalとする。
		// Externalにしておかないと、Externalのみアクセス可のエントリー参照でACLエラーとなってしまうため。
		ReflexContext reflexContext = ReflexContextUtil.getReflexContext(req, true);	// External

		try {
			int status = HttpStatus.SC_OK;
			Object retObj = null;

			String targetServiceName = ProviderUtil.getServiceLinkage(req);
			String targetServiceKey = ProviderUtil.getServiceKey(req, targetServiceName);
			checkServiceLinkage(req, targetServiceName);
			// ここでリクエストパラメータ取得(サービス連携対応)
			RequestParam param = null;
			if (StringUtils.isBlank(targetServiceName)) {
				param = (RequestParam)req.getRequestType();
			} else {
				param = new RequestParamInfo(req.getPathInfoQuery(), targetServiceName);
			}

			// インメモリソート処理
			MemorySortBlogic blogic = new MemorySortBlogic();
			retObj = blogic.getFeedAndMemorySort(param, targetServiceName, targetServiceKey,
					reflexContext);

			if (retObj != null) {
				doResponse(req, resp, retObj, status);
			} else if (status != HttpStatus.SC_OK) {
				resp.setStatus(status);
			}

		} catch (Throwable e) {
			throw new IOException(e);
		}
	}

	/**
	 * POSTメソッド処理
	 * @param httpReq リクエスト
	 * @param httpResp レスポンス
	 */
	@Override
	public void doPost(HttpServletRequest httpReq, HttpServletResponse httpResp)
	throws IOException {
		ReflexRequest req = (ReflexRequest)httpReq;

		// 使用しないメソッド
		logMessageInvalidParameter(req);
		throw new MethodNotAllowedException("Invalid parameter.");
	}

	/**
	 * PUTメソッド処理
	 * @param httpReq リクエスト
	 * @param httpResp レスポンス
	 */
	@Override
	public void doPut(HttpServletRequest httpReq, HttpServletResponse httpResp)
	throws IOException {
		ReflexRequest req = (ReflexRequest)httpReq;

		// 使用しないメソッド
		logMessageInvalidParameter(req);
		throw new MethodNotAllowedException("Invalid parameter.");
	}

	/**
	 * DELETEメソッド処理
	 * @param httpReq リクエスト
	 * @param httpResp レスポンス
	 */
	@Override
	public void doDelete(HttpServletRequest httpReq, HttpServletResponse httpResp)
	throws IOException {
		ReflexRequest req = (ReflexRequest)httpReq;

		// 使用しないメソッド
		logMessageInvalidParameter(req);
		throw new MethodNotAllowedException("Invalid parameter.");
	}

	/**
	 * シャットダウン時の処理.
	 * (2022.10.4)ServletContextListener.contextDestroyed が実行されないのでこちらに移動。
	 */
	@Override
	public void destroy() {
		if (logger.isInfoEnabled()) {
			logger.info("[destroy] start.");
		}
		super.destroy();
		TaggingEnvUtil.destroy();
		if (logger.isInfoEnabled()) {
			logger.info("[destroy] end.");
		}
	}

	/**
	 * 不正なリクエストパラメータ時のログメッセージ.
	 * @param req リクエスト
	 * @return ログメッセージ
	 */
	private void logMessageInvalidParameter(ReflexRequest req) {
		StringBuilder sb = new StringBuilder();
		sb.append("MethodNotAllowedException: Invalid parameter. ");
		sb.append(req.getMethod());
		sb.append(" ");
		sb.append(req.getRequestType().toString());
		logger.warn(LogUtil.getRequestInfoStr(req.getRequestInfo()) + sb.toString());
	}

	/**
	 * サービス連携に指定されたサービスのチェック
	 * @param req リクエスト
	 * @param targetServiceName 対象サービス
	 */
	private void checkServiceLinkage(ReflexRequest req, String targetServiceName) 
			throws IOException, TaggingException {
		String serviceName = req.getServiceName();
		// サービス連携の指定がないか、自サービスの場合処理を抜ける。
		if (StringUtils.isBlank(targetServiceName) || serviceName.equals(targetServiceName)) {
			return;
		}
		ProviderUtil.checkServiceLinkage(targetServiceName, req.getRequestInfo(), 
				req.getConnectionInfo());
		// 引数チェックが自サービスのテンプレートで行われているため、
		// サービス連携の場合はIllegalParameterExceptionをスローしないようtry-catchする。
		try {
			req.getRequestType();
		} catch (IllegalParameterException e) {
			// Do nothing.
		}
	}

}
