package jp.reflexworks.taggingservice.env;

import jp.reflexworks.servlet.util.ServletContextUtil;

/**
 * Tagging BDB 環境情報
 */
public class AllocidsEnv extends ReflexBDBEnvBase {

	/**
	 * コンストラクタ.
	 * @param contextUtil ServletContextUtil
	 */
	public AllocidsEnv(ServletContextUtil contextUtil) {
		super(contextUtil);
	}

	/**
	 * プラグイン機能.
	 */
	protected void initPlugin() {
		// Do nothing.
	}

}
