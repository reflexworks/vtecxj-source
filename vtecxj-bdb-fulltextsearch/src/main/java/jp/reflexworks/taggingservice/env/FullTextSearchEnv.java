package jp.reflexworks.taggingservice.env;

import jp.reflexworks.servlet.util.ServletContextUtil;

/**
 * Tagging BDB 環境情報
 */
public class FullTextSearchEnv extends ReflexBDBEnvBase {

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
		// Do nothing.
	}

}
