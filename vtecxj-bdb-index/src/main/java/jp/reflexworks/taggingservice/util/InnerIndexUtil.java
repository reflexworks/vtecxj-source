package jp.reflexworks.taggingservice.util;

import java.io.IOException;

import jp.reflexworks.atom.api.Condition;
import jp.reflexworks.taggingservice.bdb.BDBConst;
import jp.reflexworks.taggingservice.model.EditedCondition;
import jp.reflexworks.taggingservice.model.InnerIndex;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * Index ユーティリティ
 */
public class InnerIndexUtil extends IndexUtil {

	/** 親キーとIndex項目の区切り文字 */
	public static final String ITEM_PREFIX = "#";
	/** Index項目とIndex項目値の区切り文字 */
	public static final String ITEM_END = "/";
	/** Index項目値とselfidの区切り文字 */
	public static final String INDEX_SELF = BDBConst.INDEX_SELF;

	/**
	 * {parent}#{Index項目}/{Index項目の値}\u0001{selfid}
	 * 項目名はEntryの次の階層から"."でつないだ名前とする。
	 * @param innerIndex インデックス情報
	 * @return インデックスURI
	 */
	public static String createIndexUri(InnerIndex innerIndex) {
		String fldName = innerIndex.getName();
		Object obj = innerIndex.getIndexObj();
		String uri = innerIndex.getIndexUri();
		TaggingEntryUtil.UriPair uriPair = TaggingEntryUtil.getUriPair(uri);
		String parentUri = TaggingEntryUtil.removeLastSlash(uriPair.parent);

		String indexParentUri = createIndexParentUri(fldName, obj, parentUri);

		StringBuilder sb = new StringBuilder();
		sb.append(indexParentUri);
		sb.append(INDEX_SELF);
		if (!"/".equals(uriPair.selfid)) {
			sb.append(uriPair.selfid);
		}
		return sb.toString();
	}

	/**
	 * {parent}#{Index項目}/{Index項目の値}\u0001{selfid}
	 * 項目名はEntryの次の階層から"."でつないだ名前とする。
	 * Index項目の値がnullの場合、「u0001」を付加しない。
	 * @param fldName Index項目名
	 * @param obj Index項目の値
	 * @param parentUri 親階層
	 * @return インデックスURI
	 */
	public static String createIndexParentUri(String fldName, Object obj, String parentUri) {
		StringBuilder sb = new StringBuilder();
		sb.append(parentUri);
		sb.append(InnerIndexConst.ITEM_PREFIX);
		sb.append(fldName);
		sb.append(InnerIndexConst.ITEM_END);
		if (obj != null) {
			sb.append(editIndexValue(obj));
		}
		return sb.toString();
	}

	/**
	 * インデックス検索の場合の検索開始キーを生成
	 * @param editedCondition 検索条件
	 * @return インデックス検索の場合の検索開始キー
	 */
	public static final String createIndexStartUri(EditedCondition editedCondition) {
		Condition condition1 = editedCondition.getIndexCondition();
		if (condition1 == null) {
			return null;
		}
		Condition startCondition = null;
		Condition condition2 = editedCondition.getIndexConditionRange();
		if (condition2 != null) {
			String equation1 = condition1.getEquations();
			if (Condition.LESS_THAN.equals(equation1) ||
					Condition.LESS_THAN_OR_EQUAL.equals(equation1)) {
				startCondition = condition2;
			}
		}
		if (startCondition == null) {
			startCondition = condition1;
		}

		String prop = startCondition.getProp();
		String startEquation = startCondition.getEquations();
		Object startVal = null;
		if (Condition.LESS_THAN.equals(startEquation) ||
				Condition.LESS_THAN_OR_EQUAL.equals(startEquation)) {
			// startVal=null
		} else {
			startVal = convertIndexValueByType(startCondition, editedCondition.getIndexMeta());
		}
		String indexParentUri = createIndexParentUri(prop, startVal,
				editedCondition.getConditionUri());

		StringBuilder sb = new StringBuilder();
		sb.append(indexParentUri);
		if (Condition.EQUAL.equals(startEquation)) {
			sb.append(INDEX_SELF);
		}
		return sb.toString();
	}

	/**
	 * インデックス検索の場合の検索終了キーを生成
	 * @param editedCondition 検索条件
	 * @return インデックス検索の場合の検索終了キー
	 */
	public static final String createIndexEndUri(EditedCondition editedCondition) {
		Condition condition1 = editedCondition.getIndexCondition();
		if (condition1 == null) {
			return null;
		}
		// 開始・終了条件のいずれも指定されている場合
		Condition endCondition = null;
		Condition condition2 = editedCondition.getIndexConditionRange();
		String equation1 = condition1.getEquations();
		if (Condition.LESS_THAN.equals(equation1) ||
				Condition.LESS_THAN_OR_EQUAL.equals(equation1)) {
			endCondition = condition1;
		} else if (condition2 != null) {
			endCondition = condition2;
		} else if (Condition.EQUAL.equals(equation1)) {
			endCondition = condition1;
		} else if (Condition.FORWARD_MATCH.equals(equation1)) {
			endCondition = condition1;	// 
		}
		String prop = condition1.getProp();
		Object endVal = null;
		if (endCondition != null) {
			endVal = convertIndexValueByType(endCondition, editedCondition.getIndexMeta());
		} else {
			// endVal=null
		}
		String keyStr = createIndexParentUri(prop, endVal, editedCondition.getConditionUri());

		StringBuilder sb = new StringBuilder();
		sb.append(keyStr);
		if (Condition.EQUAL.equals(equation1)) {
			sb.append(INDEX_SELF);
		}
		boolean setLessThanEnd = false;
		if (endCondition != null) {
			String endEquation = endCondition.getEquations();
			if (Condition.LESS_THAN.equals(endEquation) ||
					Condition.LESS_THAN_OR_EQUAL.equals(endEquation)) {
				setLessThanEnd = true;
			}
		}
		if (setLessThanEnd) {
			sb.append(BDBConst.LESS_THAN_END);
		} else {
			sb.append(BDBConst.FOWARD_MATCHING_END);
		}
		return sb.toString();
	}

	/**
	 * 結果に条件に指定された値を含まないかどうかを判定.
	 * GREATER_THANの場合true
	 * @param editedCondition 検索条件
	 * @return 結果に条件に指定された値を含まない場合true。(GREATER_THANの場合)
	 */
	public static final boolean isGreaterThan(EditedCondition editedCondition) {
		if (editedCondition == null) {
			return false;
		}
		Condition indexCondition = editedCondition.getIndexCondition();
		if (indexCondition != null) {
			String indexEquation = indexCondition.getEquations();
			if (Condition.GREATER_THAN.equals(indexEquation)) {
				return true;
			}
			Condition indexConditionRange = editedCondition.getIndexConditionRange();
			if (indexConditionRange != null && (
					Condition.LESS_THAN.equals(indexEquation) ||
					Condition.LESS_THAN_OR_EQUAL.equals(indexEquation))) {
				if (Condition.GREATER_THAN.equals(indexConditionRange.getEquations())) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * 検索終了条件の等号・不等号を取得.
	 * @param editedCondition 検索条件
	 * @return 検索終了条件の等号または不等号
	 */
	public static final String getEndEquation(EditedCondition editedCondition) {
		Condition indexCondition = editedCondition.getIndexCondition();
		if (indexCondition != null) {
			String indexEquation = indexCondition.getEquations();
			if (Condition.LESS_THAN.equals(indexEquation) ||
					Condition.LESS_THAN_OR_EQUAL.equals(indexEquation)) {
				return indexEquation;
			} else if (editedCondition.getIndexConditionRange() != null) {
				return editedCondition.getIndexConditionRange().getEquations();
			} else if (Condition.EQUAL.equals(indexEquation)) {
				return indexEquation;
			} else {
				return null;
			}
		} else {
			if (editedCondition.isUriForwardMatch()) {
				return null;
			} else {
				return Condition.EQUAL;
			}
		}
	}

	/**
	 * インデックスを取得.
	 * 検索時に使用。
	 * <p>
	 * インデックスキー文字列を作成する。
	 * <ul>
	 *   <li>キー (DISTKEYなし) : \u0001{インデックス項目短縮値}/{Index項目の値}\u0001{selfid}</li>
	 *   <li>キー (DISTKEYあり) : {DISTKEY項目短縮値}/{DISTKEY項目の値}\u0001{インデックス項目短縮値}/{Index項目の値}\u0001{selfid}</li>
	 * </ul>
	 * </p>
	 * @param parentItemShortening 親階層#Index項目名の短縮値
	 * @param value 検索条件の値
	 * @param distkeyShortening DISTKEY短縮値
	 * @param distkeyValue DISTKEYの値
	 * @param isEqual equal条件の場合true。selfidの\u0001まで付けて返す。
	 * @return インデックスキー文字列
	 */
	public static String getInnerIndexByGet(String parentItemShortening,
			String value, String distkeyShortening, String distkeyValue, boolean isEqual)
	throws IOException {
		return editInnerIndex(parentItemShortening, null, value,
				distkeyShortening, distkeyValue, isEqual);
	}

	/**
	 * インデックスを取得.
	 * 更新時に使用。
	 * <p>
	 * インデックスキー文字列を作成する。
	 * <ul>
	 *   <li>キー (DISTKEYなし) : \u0001{インデックス項目短縮値}/{Index項目の値}\u0001{selfid}</li>
	 *   <li>キー (DISTKEYあり) : {DISTKEY項目短縮値}/{DISTKEY項目の値}\u0001{インデックス項目短縮値}/{Index項目の値}\u0001{selfid}</li>
	 * </ul>
	 * </p>
	 * @param parentItemShortening 親階層#Index項目名の短縮値
	 * @param selfid 自階層
	 * @param value 検索条件の値
	 * @param distkeyShortening DISTKEY短縮値
	 * @param distkeyValue DISTKEYの値
	 * @return インデックスキー文字列
	 */
	public static String getInnerIndex(String parentItemShortening, String selfid,
			String value, String distkeyShortening, String distkeyValue)
	throws IOException {
		return editInnerIndex(parentItemShortening, selfid, value,
				distkeyShortening, distkeyValue, false);
	}

	/**
	 * インデックス値を編集.
	 * (DISTKEYなし) : \u0001{インデックス項目短縮値}/{Index項目の値}\u0001{selfid}</li>
	 * (DISTKEYあり) : {DISTKEY項目短縮値}/{DISTKEY項目の値}\u0001{インデックス項目短縮値}/{Index項目の値}\u0001{selfid}</li>
	 * @param parentItemShortening 親階層#Index項目名の短縮値
	 * @param selfid 自階層
	 * @param value 値
	 * @param distkeyShortening DISTKEY短縮値
	 * @param distkeyValue DISTKEYの値
	 * @param isEqual equal条件の場合true。selfidの\u0001まで付けて返す。
	 * @return インデックス
	 */
	private static String editInnerIndex(String parentItemShortening, String selfid,
			String value, String distkeyShortening, String distkeyValue, boolean isEqual) {
		StringBuilder sb = new StringBuilder();
		if (!StringUtils.isBlank(distkeyShortening)) {
			sb.append(distkeyShortening);
			sb.append(ITEM_END);
			sb.append(StringUtils.null2blank(distkeyValue));
		}
		sb.append(INDEX_SELF);
		sb.append(parentItemShortening);
		sb.append(ITEM_END);
		sb.append(StringUtils.null2blank(value));
		if (!StringUtils.isBlank(selfid)) {
			sb.append(INDEX_SELF);
			sb.append(selfid);
		} else if (isEqual) {
			sb.append(INDEX_SELF);
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

}
