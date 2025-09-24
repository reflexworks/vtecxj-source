package jp.reflexworks.batch;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.js.JsExec;
import jp.reflexworks.servlet.HttpStatus;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.blogic.BigQueryBlogic;
import jp.reflexworks.taggingservice.blogic.MessageQueueBlogic;
import jp.reflexworks.taggingservice.conn.ConnectionInfoImpl;
import jp.reflexworks.taggingservice.context.ReflexContextUtil;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.MethodNotAllowedException;
import jp.reflexworks.taggingservice.model.RequestInfoImpl;
import jp.reflexworks.taggingservice.plugin.ServiceManager;
import jp.reflexworks.taggingservice.plugin.def.ServiceManagerDefault;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.sourceforge.reflex.util.DeflateUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * バッチジョブ管理サーブレット.
 */
public class BatchJobServlet extends HttpServlet {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());
	
	/**
	 * 初期処理.
	 */
	@Override
	public void init() {
		if (logger.isTraceEnabled()) {
			logger.debug("[init] start.");
		}
		JsExec.init();
	}

	/**
	 * GETメソッド処理
	 * @param httpReq リクエスト
	 * @param httpResp レスポンス
	 */
	@Override
	public void doGet(HttpServletRequest httpReq, HttpServletResponse httpResp)
	throws IOException {

		// なし
		throw new MethodNotAllowedException("Method not allowed. " + httpReq.getMethod());

	}

	/**
	 * POSTメソッド処理
	 * @param httpReq リクエスト
	 * @param httpResp レスポンス
	 */
	@Override
	public void doPost(HttpServletRequest httpReq, HttpServletResponse httpResp)
	throws IOException {
		if (logger.isTraceEnabled()) {
			logger.debug("[doPost] start");
		}

		// バッチジョブ管理処理をTaskQueueに登録してレスポンスする。
		String serviceName = TaggingEnvUtil.getSystemService();
		// 認証情報
		ServiceManager serviceManager = new ServiceManagerDefault();
		ReflexAuthentication auth = serviceManager.createServiceAdminAuth(
				serviceName);
		// リクエスト情報
		RequestInfo requestInfo = new RequestInfoImpl(serviceName,
				BatchJobConst.REQUESTINFO_IP, auth.getUid(), auth.getAccount(),
				BatchJobConst.REQUESTINFO_METHOD, BatchJobConst.REQUESTINFO_URL);

		DeflateUtil deflateUtil = null;
		ConnectionInfo connectionInfo = null;
		try {
			deflateUtil = new DeflateUtil();
			connectionInfo = new ConnectionInfoImpl(deflateUtil, requestInfo);
			ReflexContext reflexContext = ReflexContextUtil.getReflexContext(
					auth, requestInfo, connectionInfo);

			// バッチジョブ実行管理 (非同期処理)
			BatchJobBlogic blogic = new BatchJobBlogic();
			blogic.callManagement(reflexContext);

			// 戻り値
			httpResp.setStatus(HttpStatus.SC_ACCEPTED);
			writeResponseData(httpResp, "BatchJobManagement request is accepted.");

		} catch (Throwable e) {
			logger.error("[doPost] Error occured.", e);
			httpResp.setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
		} finally {
			// コネクション情報のクリア (Deflate圧縮辞書メモリ解放)
			try {
				connectionInfo.close();
			} catch (Exception e) {
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "ConnectionInfo close error.", e);
				}
			}
		}
	}

	/**
	 * PUTメソッド処理.
	 * 各サービスごとの処理を行う
	 * @param httpReq リクエスト
	 * @param httpResp レスポンス
	 */
	@Override
	public void doPut(HttpServletRequest httpReq, HttpServletResponse httpResp)
	throws IOException {
		if (logger.isTraceEnabled()) {
			logger.debug("[doPut] start");
		}

		// サービス名取得
		String serviceName = httpReq.getHeader(Constants.HEADER_SERVICENAME);
		if (StringUtils.isBlank(serviceName)) {
			logger.warn("[doPut] serviceName is null.");
			httpResp.setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
			return;
		}

		// 認証情報
		ServiceManager serviceManager = new ServiceManagerDefault();
		ReflexAuthentication auth = serviceManager.createServiceAdminAuth(
				serviceName);
		// リクエスト情報
		RequestInfo requestInfo = new RequestInfoImpl(serviceName,
				BatchJobConst.REQUESTINFO_IP, auth.getUid(), auth.getAccount(),
				BatchJobConst.REQUESTINFO_METHOD, BatchJobConst.REQUESTINFO_URL);
		
		DeflateUtil deflateUtil = null;
		ConnectionInfo connectionInfo = null;
		try {
			deflateUtil = new DeflateUtil();
			connectionInfo = new ConnectionInfoImpl(deflateUtil, requestInfo);
			SystemContext systemContext = new SystemContext(serviceName, 
					requestInfo, connectionInfo);
			
			// サービス初期処理
			BatchJobBlogic batchJobBlogic = new BatchJobBlogic();
			batchJobBlogic.initService(serviceName, requestInfo, connectionInfo);
			
			if (httpReq.getParameter(RequestParam.PARAM_CHECK_MQ) != null) {
				if (logger.isTraceEnabled()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "_check_mq");
				}
				// メッセージキュー未送信チェック
				MessageQueueBlogic blogic = new MessageQueueBlogic();
				blogic.checkMessageQueue(systemContext);

				// 戻り値
				httpResp.setStatus(HttpStatus.SC_OK);
				writeResponseData(httpResp, "Check message queue completed: " + serviceName);
				
			} else if (httpReq.getParameter(RequestParam.PARAM_CHECK_BDBQ) != null) {
				if (logger.isTraceEnabled()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "_check_bdbq");
				}
				// BDBQリトライチェック
				BigQueryBlogic blogic = new BigQueryBlogic();
				blogic.checkRetryBdbq(systemContext);

				// 戻り値
				httpResp.setStatus(HttpStatus.SC_OK);
				writeResponseData(httpResp, "Check retry bdbq completed: " + serviceName);
				
			} else {
				// その他は無効
				throw new MethodNotAllowedException("Invalid parameter.");
			}

		} catch (Throwable e) {
			logger.error("[doPut] Error occured.", e);
			httpResp.setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
		} finally {
			// コネクション情報のクリア (Deflate圧縮辞書メモリ解放)
			try {
				if (connectionInfo != null) {
					connectionInfo.close();
				}
			} catch (Exception e) {
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) + "ConnectionInfo close error.", e);
				}
			}
		}
	}

	/**
	 * DELETEメソッド処理 (テスト)
	 * @param httpReq リクエスト
	 * @param httpResp レスポンス
	 */
	@Override
	public void doDelete(HttpServletRequest httpReq, HttpServletResponse httpResp)
	throws IOException {

		// なし
		throw new MethodNotAllowedException("Method not allowed. " + httpReq.getMethod());
		
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
		// ステータスがwaitingのエントリーを削除
		BatchJobUtil.deleteWaiting();
		// TaggingServiceのシャットダウン処理
		TaggingEnvUtil.destroy();
		if (logger.isInfoEnabled()) {
			logger.info("[destroy] end.");
		}
	}

	/**
	 * レスポンスデータに文字列出力
	 * @param httpResp レスポンス
	 * @param text 文字列
	 */
	private void writeResponseData(HttpServletResponse httpResp, String text)
	throws IOException {
		try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
				httpResp.getOutputStream(), Constants.ENCODING))) {
			writer.write(text);
			writer.write(Constants.NEWLINE);
		}
	}

}
