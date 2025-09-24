package jp.reflexworks.taggingservice.pushnotification;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import jp.reflexworks.atom.entry.Category;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;

/**
 * プッシュ通知ユーティリティ.
 */
public class ReflexPushNotificationUtil {

	/**
	 * categoryに設定された値をMap形式で取得する.
	 *    キー: _$scheme
	 *    値: _$label
	 * @param entry Push通知メッセージ情報
	 * @return Map
	 */
	public static Map<String, String> getDataMap(EntryBase entry) {
		if (entry == null || entry.category == null) {
			return null;
		}
		Map<String, String> map = new HashMap<>();
		for (Category category : entry.category) {
			if (!StringUtils.isBlank(category._$scheme) && !StringUtils.isBlank(category._$label)) {
				map.put(category._$scheme, category._$label);
			}
		}
		if (!map.isEmpty()) {
			return map;
		}
		return null;
	}

	/**
	 * imageUrlを取得する.
	 * linkの_$title=imageUrlの_$hrefの値。
	 * @param entry Push通知メッセージ情報
	 * @return imageUrl
	 */
	public static String getImageUrl(EntryBase entry) {
		Map<String, String> dataMap = getDataMap(entry);
		if (dataMap != null && dataMap.containsKey(ReflexPushNotificationConst.IMAGEURL)) {
			return dataMap.get(ReflexPushNotificationConst.IMAGEURL);
		}
		return null;
	}

	/**
	 * Push通知デバッグログエントリー出力処理を行うかどうか.
	 * @return Push通知デバッグログエントリー出力処理を行う場合true
	 */
	public static boolean isDebugLog(String serviceName) {
		try {
			return TaggingEnvUtil.getPropBoolean(serviceName,
					ReflexPushNotificationConst.DEBUGLOG_NOTIFICATION, false);
		} catch (InvalidServiceSettingException e) {
			return false;
		}
	}

	/**
	 * Push通知のアクセスログを出力するかどうか.
	 * @return Push通知のアクセスログを出力する場合true
	 */
	public static boolean isEnableAccessLog() {
		return TaggingEnvUtil.getSystemPropBoolean(
				ReflexPushNotificationConst.PUSHNOTIFICATION_ENABLE_ACCESSLOG, false);
	}
	
}
