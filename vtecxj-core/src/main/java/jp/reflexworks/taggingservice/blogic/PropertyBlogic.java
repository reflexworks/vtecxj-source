package jp.reflexworks.taggingservice.blogic;

import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.plugin.PropertyManager;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * 設定管理ビジネスロジック.
 */
public class PropertyBlogic {
	
	/**
	 * サービス固有の設定値を返却.
	 *   /_settings/properties に設定された値のみ参照し返却.
	 * @param serviceName サービス名
	 * @param key キー
	 * @return 設定値
	 */
	public String getSettingValue(String serviceName, String key) {
		if (StringUtils.isBlank(key)) {
			return null;
		}
		PropertyManager propertyManager = TaggingEnvUtil.getPropertyManager();
		return propertyManager.getServiceSettingValue(serviceName, key);
	}

}
