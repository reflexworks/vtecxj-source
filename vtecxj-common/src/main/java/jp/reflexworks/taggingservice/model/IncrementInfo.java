package jp.reflexworks.taggingservice.model;

import java.io.Serializable;

/**
 * Incrementテーブル保持情報.
 * 加算処理(addids)で使用。
 */
public class IncrementInfo implements Serializable {

	/** serialVersionUID */
	private static final long serialVersionUID = -3452120930700795898L;
	
	/** 値 */
	private long num;
	/** 枠 */
	private String range;
	
	/**
	 * コンストラクタ.
	 * @param num 値
	 * @param range 枠
	 */
	public IncrementInfo(long num, String range) {
		this.num = num;
		this.range = range;
	}

	/**
	 * 値を取得
	 * @return 値
	 */
	public long getNum() {
		return num;
	}

	/**
	 * 枠を取得.
	 * @return 枠
	 */
	public String getRange() {
		return range;
	}

	/**
	 * 文字列表現を取得
	 * @return このオブジェクトの文字列表現
	 */
	@Override
	public String toString() {
		return "IncrementInfo [num=" + num + ", range=" + range + "]";
	}
	
}
