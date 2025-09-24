package jp.reflexworks.batch;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletContextEvent;
import jp.reflexworks.taggingservice.api.ReflexListener;
import jp.reflexworks.taggingservice.api.ReflexStatic;

/**
 * APサーバ起動、シャットダウン時のリスナー.
 * web.xmlのlistenerに本クラスを定義しておく必要がある。
 */
public class BatchJobListener extends ReflexListener {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * サーバ起動時に呼び出されるメソッド.
	 * web.xmlのlistenerに本クラスを定義しておく必要がある。
	 * @param sce ServletContextEvent
	 */
	public void contextInitialized(ServletContextEvent sce) {
		super.contextInitialized(sce);
		if (logger.isDebugEnabled()) {
			logger.debug("[contextInitialized] start");
		}

		try {
			// バッチジョブFutureを格納するstatic mapを格納
			ConcurrentMap<String, List<BatchJobFuture>> futuresMap = new ConcurrentHashMap<>();
			ReflexStatic.setStatic(BatchJobConst.STATIC_BATCHJOB_FUTURE_OF_JOB, futuresMap);

			if (logger.isDebugEnabled()) {
				logger.debug("[contextInitialized] end");
			}

		} catch (Throwable e) {
			logger.error("[contextInitialized] Error occured.", e);
		}
	}

	/**
	 * サーバシャットダウン時に呼び出されるメソッド.
	 * web.xmlのlistenerに本クラスを定義しておく必要がある。
	 * @param sce ServletContextEvent
	 */
	public void contextDestroyed(ServletContextEvent sce) {
		/* (2022.10.4)この処理が実行されないので、HttpServlet.destoryに移動。
		if (logger.isDebugEnabled()) {
			logger.debug("[contextDestroyed] start");
		}
		// ステータスがwaitingのエントリーを削除
		BatchJobUtil.deleteWaiting();
		// 親クラスのシャットダウン処理呼び出し
		super.contextDestroyed(sce);

		if (logger.isDebugEnabled()) {
			logger.debug("[contextDestroyed] end");
		}
		*/
	}

}
