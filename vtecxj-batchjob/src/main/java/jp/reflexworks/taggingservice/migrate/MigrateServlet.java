package jp.reflexworks.taggingservice.migrate;

import java.io.IOException;

import jakarta.servlet.Servlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.servlet.HttpStatus;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.ReflexServletBase;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.context.ReflexContextUtil;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.MethodNotAllowedException;
import jp.reflexworks.taggingservice.plugin.MessageManager;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.MessageUtil;

/**
 * データ移行サーブレット.
 */
public class MigrateServlet extends ReflexServletBase {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

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

		// なし
		throw new MethodNotAllowedException("Method not allowed. " + httpReq.getMethod());

	}

	/**
	 * PUTメソッド処理
	 * @param httpReq リクエスト
	 * @param httpResp レスポンス
	 */
	@Override
	public void doPut(HttpServletRequest httpReq, HttpServletResponse httpResp)
	throws IOException {
		if (logger.isTraceEnabled()) {
			logger.debug("[doPut] start");
		}

		ReflexRequest req = (ReflexRequest)httpReq;
		ReflexResponse resp = (ReflexResponse)httpResp;
		RequestParam param = (RequestParam)req.getRequestType();
		String serviceName = req.getServiceName();
		RequestInfo requestInfo = req.getRequestInfo();
		ConnectionInfo connectionInfo = req.getConnectionInfo();
		if (param == null) {
			return;
		}
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) + "doPut start");
		}
		ReflexContext reflexContext = ReflexContextUtil.getReflexContext(req);
		ReflexAuthentication auth = reflexContext.getAuth();
		auth.setExternal(false);
		MessageManager msgManager = TaggingEnvUtil.getMessageManager();

		try {
			Object retObj = null;
			int status = HttpStatus.SC_OK;

			if (param.getOption(RequestParam.PARAM_SERVICETOPRODUCTION) != null) {
				// サービス公開
				if (logger.isDebugEnabled()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "_servicetoproduction");
				}
				String targetServiceName = param.getOption(RequestParam.PARAM_SERVICETOPRODUCTION);
				MigrateMinimumManager migrateMinManager = new MigrateMinimumManager();
				migrateMinManager.changeServiceStatus(targetServiceName,
						Constants.SERVICE_STATUS_PRODUCTION, auth, requestInfo, connectionInfo);
				retObj = createMessageFeed(msgManager.getMsgAccept(serviceName), serviceName);
				// ステータスはAccepted.
				status = HttpStatus.SC_ACCEPTED;

			} else if (param.getOption(RequestParam.PARAM_SERVICETOSTAGING) != null) {
				// サービスステータスを開発に更新
				if (logger.isDebugEnabled()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "_servicetostaging");
				}
				String targetServiceName = param.getOption(RequestParam.PARAM_SERVICETOSTAGING);
				MigrateMinimumManager migrateMinManager = new MigrateMinimumManager();
				migrateMinManager.changeServiceStatus(targetServiceName,
						Constants.SERVICE_STATUS_STAGING, auth, requestInfo, connectionInfo);
				retObj = createMessageFeed(msgManager.getMsgAccept(serviceName), serviceName);
				// ステータスはAccepted.
				status = HttpStatus.SC_ACCEPTED;

			} else if (param.getOption(RequestParam.PARAM_ADDSERVER) != null) {
				// サーバ追加
				if (logger.isDebugEnabled()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "_addserver");
				}
				String targetServiceName = param.getOption(RequestParam.PARAM_ADDSERVER);
				FeedBase feed = req.getFeed();
				MaintenanceManager maintenanceManager = new MaintenanceManager();
				maintenanceManager.addServer(targetServiceName, feed, auth, requestInfo, connectionInfo);

			} else if (param.getOption(RequestParam.PARAM_REMOVESERVER) != null) {
				// サーバ削除
				if (logger.isDebugEnabled()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "_removeserver");
				}
				String targetServiceName = param.getOption(RequestParam.PARAM_REMOVESERVER);
				FeedBase feed = req.getFeed();
				MaintenanceManager maintenanceManager = new MaintenanceManager();
				maintenanceManager.removeServer(targetServiceName, feed, auth, requestInfo, connectionInfo);

			} else {
				// その他は無効
				throw new MethodNotAllowedException("Invalid parameter.");
			}

			if (retObj != null) {
				doResponse(req, resp, retObj, status);
			}

		} catch (Throwable e) {
			throw new IOException(e);
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
	 * Called by the servlet container to indicate to a servlet that the
	 * servlet is being taken out of service.  See {@link Servlet#destroy}.
	 */
	@Override
	public void destroy() {
		// Do nothing.
	}

	/**
	 * 戻り値のメッセージFeedを作成.
	 * @param msg メッセージ
	 * @return メッセージFeed
	 */
	private FeedBase createMessageFeed(String msg, String serviceName) {
		return MessageUtil.createMessageFeed(msg, serviceName);
	}

}
