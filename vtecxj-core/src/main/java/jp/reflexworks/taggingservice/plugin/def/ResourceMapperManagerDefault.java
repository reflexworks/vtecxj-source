package jp.reflexworks.taggingservice.plugin.def;

import jp.reflexworks.servlet.util.ServletContextUtil;
import jp.reflexworks.taggingservice.mapper.ReflexResourceMapperManager;

/**
 * ResourceMapper管理実装クラス.
 */
public class ResourceMapperManagerDefault extends ReflexResourceMapperManager {

	// 実装は ReflexResourceMapperManager の通り
	// abstract method のみ実装。

	/**
	 * デフォルトのIndex、暗号化、項目ACL情報を配列にして返却.
	 * FeedTemplateMapper生成時の引数に使用する。
	 * @param serviceName サービス名
	 * @return Index、暗号化、項目ACL情報
	 */
	protected String[] getDefaultRights(ServletContextUtil contextUtil) {
		return ResourceMapperManagerDefaultConst.DEFAULT_RIGHTS;
	}

	/**
	 * デフォルトのIndex、暗号化、項目ACL情報を配列にして返却.
	 * FeedTemplateMapper生成時の引数に使用する。
	 * @param serviceName サービス名
	 * @return Index、暗号化、項目ACL情報
	 */
	protected String[] getDefaultRights() {
		return ResourceMapperManagerDefaultConst.DEFAULT_RIGHTS;
	}

}
