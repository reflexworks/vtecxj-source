package jp.reflexworks.taggingservice.bdbclient;

import jp.sourceforge.reflex.util.StringUtils;

/**
 * カーソルの情報を分割して保持するクラス.
 */
public class InnerCursor {
	
	/** 定義なし */
	private static final int NO_DEFINITION = 0;
	
	/** 親階層 */
	private String parentUri;
	/** OR条件Index */
	private Integer orIdx;
	/** カーソル */
	private String dsCursorStr;
	/** カーソル使用済みかどうか */
	private boolean isUsed;

	/**
	 * コンストラクタ
	 * @param cursorStr カーソル
	 */
	public InnerCursor(String cursorStr) {
		if (cursorStr != null) {
			int idx = cursorStr.indexOf(BDBClientConst.CURSOR_SEPARATOR);
			if (idx > 0) {
				// カーソルは、{parentUri},{OR条件Index},{カーソル}
				parentUri = cursorStr.substring(0, idx);
				int idx1 = idx + 1;
				int idx2 = cursorStr.indexOf(BDBClientConst.CURSOR_SEPARATOR, idx1);
				if (idx2 > 0) {
					String orIdxStr = cursorStr.substring(idx1, idx2);
					orIdx = StringUtils.intValue(orIdxStr, NO_DEFINITION);
					if (orIdx < 0 || orIdx == Integer.MAX_VALUE) {
						orIdx = NO_DEFINITION;
					}
					dsCursorStr = cursorStr.substring(idx2 + 1);
				} else {
					// カーソルは、{parentUri},{カーソル}
					orIdx = NO_DEFINITION;
					dsCursorStr = cursorStr.substring(idx1);
				}
			} else {
				// カーソルはカーソル部分のみ
				dsCursorStr = cursorStr;
			}
		}
	}

	/**
	 * コンストラクタ
	 * @param parentUri 親階層
	 * @param dsCursorStr カーソル
	 */
	public InnerCursor(String parentUri, String dsCursorStr) {
		this.parentUri = parentUri;
		this.dsCursorStr = dsCursorStr;
	}

	/**
	 * 親階層を取得
	 * @return 親階層
	 */
	public String getParentUri() {
		return parentUri;
	}
	
	/**
	 * OR条件インデックスを取得.
	 * @return OR条件インデックス
	 */
	public Integer getOrIdx() {
		return orIdx;
	}
	
	/**
	 * カーソルを取得
	 * @return カーソル
	 */
	public String getCursorStr() {
		return dsCursorStr;
	}
	
	/**
	 * カーソル使用済みフラグを取得.
	 * @return trueの場合、カーソル使用済み。
	 */
	public boolean isUsed() {
		return isUsed;
	}
	
	/**
	 * カーソル使用済みフラグをONにする.
	 */
	public void setUsed() {
		isUsed = true;
	}

	/**
	 * カーソルを取得.
	 * @return {親階層},{OR条件インデックス},{カーソル}
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		if (!StringUtils.isBlank(parentUri)) {
			sb.append(parentUri);
			sb.append(BDBClientConst.CURSOR_SEPARATOR);
		}
		if (orIdx != null) {
			sb.append(orIdx);
			sb.append(BDBClientConst.CURSOR_SEPARATOR);
		}
		sb.append(dsCursorStr);
		return sb.toString();
	}

}
