package jp.reflexworks.taggingservice.model;

import jp.reflexworks.taggingservice.api.RequestType;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * BDB検索情報.
 */
public class BDBCondition {

	/** 開始キー */
	public String startKeyStr;
	/** 終了キー */
	public String endKeyStr;
	/** カーソル */
	public String cursorStr;
	/** 開始キーを含まないかどうか */
	public boolean excludeStartKey;
	/** 終了キーを含まないかどうか */
	public boolean excludeEndKey;

	/**
	 * コンストラクタ.
	 * @param startKeyStr 開始キー
	 * @param endKeyStr 終了キー
	 * @param cursorStr カーソル文字列
	 */
	public BDBCondition(String startKeyStr, String endKeyStr, String cursorStr) {
		this.startKeyStr = startKeyStr;
		this.endKeyStr = endKeyStr;
		this.cursorStr = cursorStr;
	}

	/**
	 * コンストラクタ.
	 * @param startKeyStr 開始キー
	 * @param endKeyStr 終了キー
	 * @param cursorStr カーソル文字列
	 * @param excludeStartKey 開始キーを含まないかどうか
	 * @param excludeEndKey 終了キーを含まないかどうか
	 */
	public BDBCondition(String startKeyStr, String endKeyStr, String cursorStr,
			boolean excludeStartKey, boolean excludeEndKey) {
		this.startKeyStr = startKeyStr;
		this.endKeyStr = endKeyStr;
		this.cursorStr = cursorStr;
		this.excludeStartKey = excludeStartKey;
		this.excludeEndKey = excludeEndKey;
	}

	/**
	 * 開始キーを取得
	 * @return 開始キー
	 */
	public String getStartKeyStr() {
		return startKeyStr;
	}

	/**
	 * 終了キーを取得.
	 * @return 終了キー
	 */
	public String getEndKeyStr() {
		return endKeyStr;
	}

	/**
	 * カーソルを取得
	 * @return カーソル
	 */
	public String getCursorStr() {
		return cursorStr;
	}

	/**
	 * このオブジェクトの文字列表現.
	 * @return このオブジェクトの文字列表現
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(startKeyStr);
		if (!StringUtils.isBlank(cursorStr)) {
			sb.append("&");
			sb.append(RequestType.PARAM_NEXT);
			sb.append("=");
			sb.append(cursorStr);
		}
		return sb.toString();
	}

	/**
	 * 開始条件を含まないかどうか
	 * @return 開始条件を含まない場合true
	 */
	public boolean isExcludedStartKey() {
		return excludeStartKey;
	}

	/**
	 * 終了条件を含まないかどうか
	 * @return 終了条件を含まない場合true
	 */
	public boolean isExcludedEndKey() {
		return excludeEndKey;
	}

}
