package jp.reflexworks.taggingservice.env;

import jp.reflexworks.servlet.util.ServletContextUtil;

/**
 * Tagging BDB 環境情報
 */
public class InnerIndexEnv extends ReflexBDBEnvBase {

	/**
	 * コンストラクタ.
	 * @param contextUtil ServletContextUtil
	 */
	public InnerIndexEnv(ServletContextUtil contextUtil) {
		super(contextUtil);
	}

	/**
	 * プラグイン機能.
	 */
	protected void initPlugin() {
		// Do nothing.
	}

}
