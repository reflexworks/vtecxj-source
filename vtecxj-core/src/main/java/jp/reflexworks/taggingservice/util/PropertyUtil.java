package jp.reflexworks.taggingservice.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.sourceforge.reflex.util.StringUtils;

/**
 * 設定情報パースユーティリティ
 */
public class PropertyUtil {

	/** ロガー. */
	private static final Logger logger = LoggerFactory.getLogger(PropertyUtil.class);

	/**
	 * 設定情報文字列を各設定情報にパースし、Mapにして返却します.
	 * @param contextStr 設定情報文字列
	 * @return 各設定情報を格納したMap
	 */
	public static Map<String, String> parsePropertiesMap(String contextStr) {
		Map<String, String> map = new HashMap<>();
		setPropertiesMap(contextStr, map);
		return map;
	}
	
	/**
	 * 設定情報文字列を各設定情報にパースし、Mapに格納します.
	 * @param contextStr 設定情報文字列
	 * @param map Map
	 */
	public static void setPropertiesMap(String contextStr, Map<String, String> map) {
		if (map == null) {
			return;
		}
		// mapのキーバックアップ
		Set<String> prevKeys = new HashSet<String>(map.keySet());
		Set<String> newKeys = new HashSet<String>();

		if (StringUtils.isBlank(contextStr)) {
			return;
		}
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new StringReader(contextStr));

			String line;
			while ((line = reader.readLine()) != null) {
				line = StringUtils.trim(line);
				if (line.length() > 0 && !line.startsWith("#")) {
					int idx = line.indexOf("=");
					if (idx > 0) {
						String key = line.substring(0, idx).trim();
						String value = line.substring(idx + 1).trim();
						map.put(key, value);
						newKeys.add(key);
					} else {
						logger.warn("Illegal setting : " + line);
					}
				}
			}

		} catch (IOException e) {
			logger.warn(e.getMessage(), e);

		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					if (logger.isInfoEnabled()) {
						logger.info("Reader close error.", e);
					}
				}
			}
		}

		if (prevKeys.size() > 0) {
			// 設定されなかった項目を削除する。
			for (String prevKey : prevKeys) {
				if (!newKeys.contains(prevKey)) {
					map.remove(prevKey);
				}
			}
		}
	}

}
