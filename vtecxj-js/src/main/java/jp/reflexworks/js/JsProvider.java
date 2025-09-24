package jp.reflexworks.js;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.js.async.JsAsync;
import jp.reflexworks.servlet.HttpStatus;
import jp.reflexworks.servlet.ReflexServletUtil;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.ReflexServletBase;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.plugin.MessageManager;
import jp.reflexworks.taggingservice.util.MessageUtil;

/**
 * Server Side JavaScript サーブレット.
 * <p>
 * ReflexWorks TaggingServiceのサーブレットは、以下のクラスを継承してください。<br>
 * <ul>
 * <li>jp.reflexworks.taggingservice.api.ReflexServletBase</li>
 * </ul>
 *
 * </p>
 */
@SuppressWarnings("serial")
public class JsProvider extends ReflexServletBase {

	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * GET処理.
	 * @param httpReq リクエスト
	 * @param httpResp レスポンス
	 */
	@Override
	public void doGet(HttpServletRequest httpReq, HttpServletResponse httpResp)
			throws IOException {
		doExec(httpReq,httpResp,"GET");
	}

	/**
	 * OPTIONS処理.
	 * @param httpReq リクエスト
	 * @param httpResp レスポンス
	 */
	@Override
	public void doOptions(HttpServletRequest httpReq, HttpServletResponse httpResp)
			throws IOException {
		doExec(httpReq,httpResp,"OPTIONS");
	}

	/**
	 * POST処理.
	 * @param httpReq リクエスト
	 * @param httpResp レスポンス
	 */
	@Override
	public void doPost(HttpServletRequest httpReq, HttpServletResponse httpResp)
			throws IOException {
		doExec(httpReq,httpResp,"POST");
	}

	/**
	 * PUT処理.
	 * @param httpReq リクエスト
	 * @param httpResp レスポンス
	 */
	@Override
	public void doPut(HttpServletRequest httpReq, HttpServletResponse httpResp)
			throws IOException {
		doExec(httpReq,httpResp,"PUT");
	}

	/**
	 * DELETE処理.
	 * @param httpReq リクエスト
	 * @param httpResp レスポンス
	 */
	@Override
	public void doDelete(HttpServletRequest httpReq, HttpServletResponse httpResp)
			throws IOException {
		doExec(httpReq,httpResp,"DELETE");
	}

	/**
	 * 有効なメソッドの実行処理.
	 * @param httpReq リクエスト
	 * @param httpResp レスポンス
	 * @param method メソッド
	 */
	private void doExec(HttpServletRequest httpReq, HttpServletResponse httpResp, String method) throws IOException  {
		if (!(httpReq instanceof ReflexRequest)) {
			logger.warn("[doExec] HttpServletRequest is not ReflexRequest. " + httpReq.getClass().getName());
			return;
		}
		if (!(httpResp instanceof ReflexResponse)) {
			logger.warn("[doExec] HttpServletResponse is not ReflexResponse. " + httpResp.getClass().getName());
			return;
		}
		ReflexRequest req = (ReflexRequest)httpReq;
		ReflexResponse resp = (ReflexResponse)httpResp;
		String func = req.getPathInfo().substring(1);

		// 非同期処理かどうか判定
		RequestParam param = (RequestParam)req.getRequestType();
		if (param.getOption(RequestParam.PARAM_ASYNC) != null) {
			doAsync(req, resp, func, method);
		} else {
			doExecResponse(req, resp, func, method);
		}
	}

	/**
	 * JS即時実行
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param func サーバサイドJS名
	 * @param method Method
	 */
	private void doExecResponse(ReflexRequest req, ReflexResponse resp, String func, String method)
			throws IOException {
		// TODO test
		//long startTime = new java.util.Date().getTime();

		JsContext jscontext = JsExec.doExec(req, resp,func,method);

		//if (logger.isDebugEnabled()) {
		//	logger.debug(getLogStr("[doExecResponse] JsExec.doExec end. ", startTime));
		//}

		if(jscontext.getStatus()==-1) {
			resp.setContentType("text/html;charset=UTF-8");
			if (jscontext.result!=null&&jscontext.result instanceof String) {
				String result = (String) jscontext.result;
				resp.getWriter().print(result.substring(1,result.length()-1));
			}else {
				resp.getWriter().print("");
			}
		}else
		if(jscontext.getStatus()==-2) {
			// do nothing
		}
		else {
			// reqにXmlHttpRequestがなければJSONで返さないように
			if (ReflexServletUtil.hasXRequestedWith(req)||req.getParameter("x")!=null||method.equals("OPTIONS")) {
				doResponse(req, resp, jscontext.respFeed, jscontext.getStatus());
			}else {
				resp.sendError(417,"X-Requested-With header is required.");
			}
		}

		//if (logger.isDebugEnabled()) {
		//	logger.debug(getLogStr("[doExecResponse] doResponse end. ", startTime));
		//}
	}

	/**
	 * 非同期処理.
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param func サーバサイドJS名
	 * @param method Method
	 */
	private void doAsync(ReflexRequest req, ReflexResponse resp, String func, String method)
	throws IOException {
		JsAsync jsAsync = new JsAsync();
		jsAsync.doAsync(req, resp, func, method);

		// レスポンス
		String serviceName = req.getServiceName();
		MessageManager msgManager = TaggingEnvUtil.getMessageManager();

		int status = HttpStatus.SC_ACCEPTED;
		FeedBase msgFeed = MessageUtil.createMessageFeed(
				msgManager.getMsgAccept(serviceName), serviceName);
		doResponse(req, resp, msgFeed, status);
	}

	/**
	 * サーブレット開始
	 */
	@Override
	public void init() throws ServletException {
		if (logger.isTraceEnabled()) {
			logger.debug("[init] start.");
		}
		TaggingEnvUtil.setAwaitShutdownOn(this.getClass().getName());
		JsExec.init();
		if (logger.isTraceEnabled()) {
			logger.debug("[init] end.");
		}
	}
	
	/**
	 * サーブレット終了
	 */
	@Override
	public void destroy() {
		try {
			JsExec.close();
		} finally {
			TaggingEnvUtil.removeAwaitShutdown(this.getClass().getName());
		}
	}

}
