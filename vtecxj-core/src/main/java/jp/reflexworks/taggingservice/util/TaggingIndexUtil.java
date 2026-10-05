package jp.reflexworks.taggingservice.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.sourceforge.reflex.util.StringUtils;

/**
 * インデックスユーティリティ
 */
public class TaggingIndexUtil {

	/** インデックス再作成で複数のインデックス項目を指定する場合の区切り文字 */
	public static final String ITEM_NAMES_DELIMITER = ",";

	/**
	 * インデックスを使用するかどうかチェックする.
	 * @param parentUri 親キー
	 * @param itemName 項目名。nullの場合親キーに何らかのインデックス指定があるかどうかチェックする。
	 * @param templateIndexMap インデックス情報 キー:項目、値:URLパターン
	 * @param templateFullTextIndexMap 全文検索インデックス情報 キー:項目、値:URLパターン
	 * @return インデックスを使用する場合true
	 */
	public static boolean useIndex(String parentUri, String itemName,
			Map<String, Pattern> templateIndexMap) {
		if (StringUtils.isBlank(parentUri) || templateIndexMap == null) {
			return false;
		}

		String tmpParamUri = TaggingEntryUtil.removeLastSlash(parentUri);
		if (!StringUtils.isBlank(itemName)) {
			// 項目名指定
			Pattern pattern = templateIndexMap.get(itemName);
			if (pattern != null) {
				Matcher matcher = pattern.matcher(tmpParamUri);
				if (matcher.matches()) {
					return true;
				}
			}
			return false;
		} else {
			// 親キーにインデックス指定があるかどうか
			List<String> indexItemNames = getIndexItemNames(parentUri, templateIndexMap);
			return indexItemNames != null && !indexItemNames.isEmpty();
		}
	}

	/**
	 * 親キーが対象のインデックス項目をすべて取得.
	 * @param parentUri 親キー
	 * @param templateIndexMap インデックス情報 キー:項目、値:URLパターン
	 * @return 対象のインデックス項目リスト
	 */
	public static List<String> getIndexItemNames(String parentUri,
			Map<String, Pattern> templateIndexMap) {
		if (StringUtils.isBlank(parentUri)) {
			return null;
		}
		String tmpParamUri = TaggingEntryUtil.removeLastSlash(parentUri);
		List<String> items = new ArrayList<>();
		// インデックス
		if (templateIndexMap != null) {
			for (Map.Entry<String, Pattern> mapEntry : templateIndexMap.entrySet()) {
				String tmpItem = mapEntry.getKey();
				Pattern pattern = mapEntry.getValue();
				Matcher matcher = pattern.matcher(tmpParamUri);
				if (matcher.matches()) {
					items.add(tmpItem);
				}
			}
		}
		return items;
	}

	/**
	 * インデックス再作成で指定されたインデックス項目(カンマ区切り)を分割.
	 * 各項目の前後の空白は除去し、空の項目は無視する。重複は除く。(指定順を保持)
	 * @param itemNamesStr インデックス項目 (カンマ区切りで複数指定可)
	 * @return インデックス項目リスト。指定が無い場合は空のリスト。
	 */
	public static List<String> getSpecifiedItemNames(String itemNamesStr) {
		Set<String> items = new LinkedHashSet<>();
		if (!StringUtils.isBlank(itemNamesStr)) {
			for (String itemName : itemNamesStr.split(ITEM_NAMES_DELIMITER)) {
				String tmpItemName = itemName.trim();
				if (!tmpItemName.isEmpty()) {
					items.add(tmpItemName);
				}
			}
		}
		return new ArrayList<>(items);
	}

}
