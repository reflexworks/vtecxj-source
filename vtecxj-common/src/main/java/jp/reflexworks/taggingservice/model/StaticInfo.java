package jp.reflexworks.taggingservice.model;

import java.util.Date;

/**
 * Static情報
 */
public class StaticInfo<T> {
	
	/** 情報 */
	private T info;
	/** アクセス時刻 */
	private Date accesstime;
	
	/**
	 * コンストラクタ.
	 * @param info 情報
	 * @param accesstime アクセス時刻
	 */
	public StaticInfo(T info, Date accesstime) {
		this.info = info;
		this.accesstime = accesstime;
	}

	/**
	 * 情報を取得
	 * @return 情報
	 */
	public T getInfo() {
		return info;
	}

	/**
	 * アクセス時刻を取得.
	 * @return アクセス時刻
	 */
	public Date getAccesstime() {
		return accesstime;
	}

	/**
	 * 文字列表現を取得.
	 * @return このオブジェクトの文字列表現
	 */
	@Override
	public String toString() {
		return String.valueOf(info);
	}

}
