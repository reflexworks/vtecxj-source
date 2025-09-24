package jp.reflexworks.taggingservice.subscription;

import java.io.IOException;
import java.util.Enumeration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.servlet.ReflexServlet;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.RequestResponseManager;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;

/**
 * ログアラートのPublishを受け取るSubscriber
 */
public class SubscriptionServlet extends ReflexServlet {

	/** serialVersionUID */
	private static final long serialVersionUID = 2268153244483369801L;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * POSTメソッド処理
	 * @param httpReq リクエスト
	 * @param httpResp レスポンス
	 */
	@Override
	public void doPost(HttpServletRequest httpReq, HttpServletResponse httpResp)
			throws IOException {

		try {
			RequestResponseManager reqRespManager = TaggingEnvUtil.getRequestResponseManager();
			// リクエスト・レスポンスをラップ
			ReflexRequest req = reqRespManager.createReflexRequest(httpReq);

			if (logger.isInfoEnabled()) {
				logger.info("[doPost] Request URL: " + req.getRequestURL().toString());
			}

			// リクエストヘッダをログ出力（デバッグ用）
			if (logger.isTraceEnabled()) {
				writeHeaders(req);
			}

			String pathInfo = TaggingEntryUtil.removeLastSlash(req.getPathInfo());
			if (SubscriptionConst.PATHINFO_MAINTENANCE_NOTICE.equals(pathInfo)) {
				// クラスタのメンテナンス
				MaintenanceNoticeBlogic blogic = new MaintenanceNoticeBlogic();
				blogic.notification(req);

			} else if (SubscriptionConst.PATHINFO_OOM.equals(pathInfo)) {
				// リクエスト判定、再起動処理
				RestartBlogic blogic = new RestartBlogic();
				blogic.subscription(req);
			}

		} catch (IOException | TaggingException | RuntimeException | Error e) {
			// エラーコードは返さない。
			StringBuilder sb = new StringBuilder();
			sb.append("[doPost] Error occured. ");
			sb.append(e.getClass().getName());
			sb.append(": ");
			sb.append(e.getMessage());
			logger.warn(sb.toString(), e);
		}
	}

	/**
	 * リクエストヘッダをログ出力
	 * @param req リクエスト
	 */
	private void writeHeaders(HttpServletRequest req) {
		// デバッグ用
		if (logger.isDebugEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append("[writeHeaders] start.");
			Enumeration<String> enu = req.getHeaderNames();
			while (enu.hasMoreElements()) {
				sb.append(Constants.NEWLINE);
				String name = enu.nextElement();
				String val = req.getHeader(name);
				sb.append(name);
				sb.append(": ");
				sb.append(val);
			}
			sb.append(Constants.NEWLINE);
			sb.append("[writeHeaders] end.");
			logger.debug(sb.toString());
		}
	}

}
