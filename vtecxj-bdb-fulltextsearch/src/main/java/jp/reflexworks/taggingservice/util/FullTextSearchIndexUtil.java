package jp.reflexworks.taggingservice.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jp.reflexworks.taggingservice.env.FullTextSearchEnvUtil;
import jp.reflexworks.taggingservice.model.FullTextSearchCondition;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * 全文検索インデックス ユーティリティ
 */
public class FullTextSearchIndexUtil extends IndexUtil {

	/** 親キーとIndex項目の区切り文字 */
	public static final String ITEM_PREFIX = "#";
	/** Index項目とIndex項目値の区切り文字 */
	public static final String ITEM_END = "/";
	/** Index項目値とselfidの区切り文字 */
	public static final String INDEX_SELF = Constants.START_STRING;

	/** 全文検索インデックス符号 */
	private static final String EQUATION = FullTextSearchCondition.EQUATION;
	/** 全文検索インデックス符号の文字列 */
	private static final int EQUATION_LEN = FullTextSearchCondition.EQUATION.length();

	/**
	 * 全文検索条件を取得
	 * @param paramStr パラメータ文字列
	 * @return 全文検索条件の場合オブジェクト
	 */
	public static FullTextSearchCondition getCondition(String paramStr) {
		if (StringUtils.isBlank(paramStr)) {
			return null;
		}
		int idx = paramStr.indexOf(EQUATION);
		if (idx > 0) {
			String item = paramStr.substring(0, idx);
			String text = paramStr.substring(idx + EQUATION_LEN);
			if (!StringUtils.isBlank(item) && !StringUtils.isBlank(text)) {
				return new FullTextSearchCondition(item, text);
			}
		}
		return null;
	}

	/**
	 * 全文検索インデックスリストを取得.
	 * インデックス登録更新時に使用。
	 * <p>
	 * インデックスキー文字列を作成する。
	 * <ul>
	 *   <li>キー (DISTKEYなし) : \u0001{全文検索インデックス項目短縮値}/{Index項目の値}\u0001{selfid}</li>
	 *   <li>キー (DISTKEYあり) : {DISTKEY項目短縮値}/{DISTKEY項目の値}\u0001{全文検索インデックス項目短縮値}/{Index項目の値}\u0001{selfid}</li>
	 * </ul>
	 * </p>
	 * @param parentItemShortening 親階層#Index項目名の短縮値
	 * @param selfid 自階層
	 * @param conditions 全文検索条件リスト
	 * @param distkeys DISTKEYリスト キー:DISTKEY短縮値、値:DISTKEYの値
	 * @return インデックスキー文字列
	 */
	public static List<String> getFullTextIndexes(String parentItemShortening, String selfid,
			FullTextSearchCondition condition, Map<String, String> distkeys)
	throws IOException {
		List<String> indexes = new ArrayList<String>();
		// 先頭から1文字ずつ減らしたリスト
		List<String> values = getFullTextIndexStrings(condition.text);
		if (values == null || values.isEmpty()) {
			values = new ArrayList<>();
			values.add("");
		}

		// (DISTKEYなし) : \u0001{全文検索インデックス項目短縮値}/{Index項目の値}\u0001{selfid}</li>
		// (DISTKEYあり) : {DISTKEY項目短縮値}/{DISTKEY項目の値}\u0001{全文検索インデックス項目短縮値}/{Index項目の値}\u0001{selfid}</li>

		// 複数DISTKEY対応
		if (distkeys != null && !distkeys.isEmpty()) {
			for (Map.Entry<String, String> mapEntry : distkeys.entrySet()) {
				String distkeyShortening = mapEntry.getKey();
				String distkeyValue = mapEntry.getValue();
				for (String value : values) {
					String index = editFullTextIndex(parentItemShortening, selfid, value,
							distkeyShortening, distkeyValue);
					indexes.add(index);
				}
			}
		} else {
			for (String value : values) {
				String index = editFullTextIndex(parentItemShortening, selfid, value,
						null, null);
				indexes.add(index);
			}
		}
		return indexes;
	}

	/**
	 * 全文検索インデックスリストを取得.
	 * 検索時に使用。
	 * <p>
	 * インデックスキー文字列を作成する。
	 * <ul>
	 *   <li>キー (DISTKEYなし) : \u0001{全文検索インデックス項目短縮値}/{Index項目の値}\u0001{selfid}</li>
	 *   <li>キー (DISTKEYあり) : {DISTKEY項目短縮値}/{DISTKEY項目の値}\u0001{全文検索インデックス項目短縮値}/{Index項目の値}\u0001{selfid}</li>
	 * </ul>
	 * </p>
	 * @param parentItemShortening 親階層#Index項目名の短縮値
	 * @param conditions 全文検索条件リスト
	 * @param distkeyShortening DISTKEY短縮値
	 * @param distkeyValue DISTKEYの値
	 * @return インデックスキー文字列
	 */
	public static String getFullTextIndex(String parentItemShortening,
			FullTextSearchCondition condition, String distkeyShortening, String distkeyValue)
	throws IOException {
		// 検索文字列を正規化
		String value = convertFullTextIndex(condition.text);

		// (DISTKEYなし) : \u0001{全文検索インデックス項目短縮値}/{Index項目の値}\u0001{selfid}</li>
		// (DISTKEYあり) : {DISTKEY項目短縮値}/{DISTKEY項目の値}\u0001{全文検索インデックス項目短縮値}/{Index項目の値}\u0001{selfid}</li>

		return editFullTextIndex(parentItemShortening, null, value,
				distkeyShortening, distkeyValue);
	}

	/**
	 * 全文検索インデックス文字列リストを取得.
	 * 例) 値が「電子レンジ」の場合のインデックス値は以下の通り。
	 *     ・ 電子レンジ
	 *     ・ 子レンジ
	 *     ・ レンジ
	 *     ・ ンジ
	 *     ・ ジ
	 * 文字列は正規化する。(全角英数字・記号を半角にする。半角カナを全角カナにする。英字を小文字にする。)
	 * 長さを規定数にする。
	 * @param text 文字列
	 * @return 全文検索インデックス文字列リスト
	 */
	private static List<String> getFullTextIndexStrings(String text) {
		if (StringUtils.isBlank(text)) {
			return null;
		}
		// 文字列を正規化
		String normalizeText = convertFullTextIndex(text);
		// 1文字ずつずらす
		int len = normalizeText.length();
		List<String> strings = new ArrayList<>(len);
		strings.add(substringFullTextIndex(normalizeText));
		for (int i = 1; i < len; i++) {
			strings.add(substringFullTextIndex(normalizeText.substring(i)));
		}
		return strings;
	}

	/**
	 * 全文検索インデックス値を編集.
	 * (DISTKEYなし) : \u0001{全文検索インデックス項目短縮値}/{Index項目の値}\u0001{selfid}</li>
	 * (DISTKEYあり) : {DISTKEY項目短縮値}/{DISTKEY項目の値}\u0001{全文検索インデックス項目短縮値}/{Index項目の値}\u0001{selfid}</li>
	 * @param parentItemShortening 親階層#Index項目名の短縮値
	 * @param selfid 自階層
	 * @param value 値
	 * @param distkeyShortening DISTKEY短縮値
	 * @param distkeyValue DISTKEYの値
	 * @return
	 */
	private static String editFullTextIndex(String parentItemShortening, String selfid,
			String value, String distkeyShortening, String distkeyValue) {
		StringBuilder sb = new StringBuilder();
		if (!StringUtils.isBlank(distkeyShortening)) {
			sb.append(distkeyShortening);
			sb.append(ITEM_END);
			sb.append(StringUtils.null2blank(distkeyValue));
		}
		sb.append(INDEX_SELF);
		sb.append(parentItemShortening);
		sb.append(ITEM_END);
		sb.append(value);
		if (!StringUtils.isBlank(selfid)) {
			sb.append(INDEX_SELF);
			sb.append(selfid);
		}
		return sb.toString();
	}

	/**
	 * インデックス文字列から、インデックス項目短縮値までを抽出.
	 * 更新判定に使用。
	 *   キー (DISTKEYなし) : \u0001{インデックス項目短縮値}/{Index項目の値}\u0001{selfid}
	 *   キー (DISTKEYあり) : {DISTKEY項目短縮値}/{DISTKEY項目の値}\u0001{インデックス項目短縮値}/{Index項目の値}\u0001{selfid}
	 * @param indexUri インデックス文字列
	 * @return インデックス文字列のうち、インデックス項目短縮値まで
	 */
	public static String getShortingByIndexUri(String indexUri) {
		int startIdx = indexUri.indexOf(INDEX_SELF);
		int endIdx = indexUri.indexOf(ITEM_END, startIdx);
		return indexUri.substring(0, endIdx);
	}

	/**
	 * 文字列を全文検索インデックス用に変換.
     *   英字は小文字にする。
     *   全角英文字は半角英小文字にする。
     *   全角数値は半角数値にする。
     *   全角記号`！”＃＄％＆’（）ー＝＾〜￥｜＠［］｛｝；：＋＊，．／＜＞？＿`は半角にする。
     *   半角カナは全角カナにする。
	 * @param text 文字列
	 * @return 変換した文字列
	 */
	public static String convertFullTextIndex(String text) {
		if (StringUtils.isBlank(text)) {
			return text;
		}
		// normalizeで以下の変換を実行。
		// * 全角英数字・記号を半角英数字・記号に変換。
		// * 半角カナは全角カナに変換
		String tmp = StringUtils.normalize(text);
		// 大文字を小文字に変換
		return tmp.toLowerCase(Locale.ENGLISH);
	}

	/**
	 * 文字列を規定の文字数で切り取る.
	 * @param text 文字列
	 * @return 編集した文字列
	 */
	public static String substringFullTextIndex(String text) {
		if (StringUtils.isBlank(text)) {
			return text;
		}
		int limit = FullTextSearchEnvUtil.getFulltextindexWordcountLimit();
		int len = text.length();
		if (len > limit) {
			return text.substring(0, limit);
		} else {
			return text;
		}
	}

}
