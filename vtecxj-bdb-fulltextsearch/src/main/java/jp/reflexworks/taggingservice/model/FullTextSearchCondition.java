package jp.reflexworks.taggingservice.model;

import jp.reflexworks.atom.api.Condition;

/**
 * 全文検索条件
 */
public class FullTextSearchCondition {
	
	/** 全文検索条件の符号 : -ft- */
	public static final String EQUATION = Condition.DELIMITER + 
			Condition.FULL_TEXT_SEARCH  + Condition.DELIMITER;
	
	/** 項目 */
	public String item;
	/** 文字列 */
	public String text;
	
	/**
	 * コンストラクタ.
	 * @param item 項目
	 * @param text 文字列
	 */
	public FullTextSearchCondition(String item, String text) {
		this.item = item;
		this.text = text;
	}

	/**
	 * 文字列表現.
	 * @return このオブジェクトの文字列表現
	 */
	@Override
	public String toString() {
		return "FullTextSearchCondition [item=" + item + ", text=" + text + "]";
	}

}
