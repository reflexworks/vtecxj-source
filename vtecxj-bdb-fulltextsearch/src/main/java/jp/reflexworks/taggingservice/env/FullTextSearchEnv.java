package jp.reflexworks.taggingservice.env;

import jp.reflexworks.servlet.util.ServletContextUtil;
import jp.reflexworks.taggingservice.exception.PluginException;
import jp.reflexworks.taggingservice.plugin.PluginUtil;
import jp.reflexworks.taggingservice.plugin.SessionManager;

/**
 * Tagging BDB 環境情報
 */
public class FullTextSearchEnv extends ReflexBDBEnvBase {

	/** Session manager */
	private Class<? extends SessionManager> sessionManagerClass =
			jp.reflexworks.taggingservice.redis.JedisSessionManager.class;

	/**
	 * コンストラクタ.
	 * @param contextUtil ServletContextUtil
	 */
	public FullTextSearchEnv(ServletContextUtil contextUtil) {
		super(contextUtil);
	}

	/**
	 * プラグイン機能.
	 */
	protected void initPlugin() {
		initPluginProc(sessionManagerClass);
	}

	/**
	 * セッション管理プラグインを取得.
	 * @return Session manager
	 */
	public SessionManager getSessionManager() {
		if (sessionManagerClass == null) {
			return null;
		}
		try {
			return (SessionManager)PluginUtil.newInstance(sessionManagerClass);
		} catch (PluginException e) {
			throw new IllegalStateException(e);
		}
	}

}
