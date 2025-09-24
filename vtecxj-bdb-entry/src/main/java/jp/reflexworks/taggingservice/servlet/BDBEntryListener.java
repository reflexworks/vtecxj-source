package jp.reflexworks.taggingservice.servlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jp.reflexworks.servlet.util.ServletContextUtil;
import jp.reflexworks.taggingservice.api.ReflexStatic;
import jp.reflexworks.taggingservice.env.BDBEntryEnv;
import jp.reflexworks.taggingservice.exception.StaticDuplicatedException;

/**
 * サーバ起動、シャットダウン時のリスナー.
 */
public class BDBEntryListener implements ServletContextListener {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * サーバ起動時に呼び出されるメソッド.
	 * web.xmlのlistenerに本クラスを定義しておく必要がある。
	 * @param sce ServletContextEvent
	 */
	public void contextInitialized(ServletContextEvent sce) {
		if (logger.isInfoEnabled()) {
			logger.info("[contextInitialized] start.");
		}
		// 環境情報設定のための必須処理
		// 1. TaggingEnvを生成
		ServletContextUtil contextUtil = new ServletContextUtil();
		contextUtil.contextInitialized(sce);
		BDBEntryEnv env = new BDBEntryEnv(contextUtil);
		try {
			// 2. TaggingEnvをstatic変数に保存
			ReflexStatic.setEnv(env);
			// 3. TaggingEnvの初期処理
			env.init();

			if (logger.isInfoEnabled()) {
				logger.info("[contextInitialized] end.");
			}

		} catch (StaticDuplicatedException e) {
			// エラーを出力し処理継続
			if (logger.isInfoEnabled()) {
				logger.info("[contextInitialized] Setting environment is duplicated: " +
						e.getMessage());
			}
		}
	}

	/**
	 * サーバシャットダウン時に呼び出されるメソッド.
	 * web.xmlのlistenerに本クラスを定義しておく必要がある。
	 * @param sce ServletContextEvent
	 */
	public void contextDestroyed(ServletContextEvent sce) {
		/* (2022.10.4)この処理が実行されないので、HttpServlet.destoryに移動。
		if (logger.isInfoEnabled()) {
			logger.info("[contextDestroyed] start.");
		}
		ReflexStatic.close();
		if (logger.isInfoEnabled()) {
			logger.info("[contextDestroyed] end.");
		}
		*/
	}

}
