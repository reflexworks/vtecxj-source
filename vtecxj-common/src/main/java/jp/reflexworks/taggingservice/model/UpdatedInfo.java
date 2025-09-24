package jp.reflexworks.taggingservice.model;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.taggingservice.util.Constants.OperationType;

/**
 * データストア更新情報.
 */
public class UpdatedInfo {

	/** 更新区分 */
	private OperationType flg;
	/** Insert、Updateの場合は更新後のEntry、Deleteの場合はnull */
	private EntryBase updEntry;
	/** Update、Deleteの場合、更新前のEntry、Insertの場合はnull */
	private EntryBase prevEntry;

	/**
	 * コンストラクタ
	 * @param flg 更新区分
	 * @param entry 更新後Entry
	 * @param prevEntry 更新前Entry
	 */
	public UpdatedInfo(OperationType flg, EntryBase entry, EntryBase prevEntry) {
		this.flg = flg;
		this.updEntry = entry;
		this.prevEntry = prevEntry;
	}

	/**
	 * 更新区分を取得.
	 * @return 更新区分
	 */
	public OperationType getFlg() {
		return flg;
	}

	/**
	 * 更新後Entryを取得
	 * @return 更新後Entry
	 */
	public EntryBase getUpdEntry() {
		return updEntry;
	}

	/**
	 * 更新前Entryを取得
	 * @return 更新前Entry
	 */
	public EntryBase getPrevEntry() {
		return prevEntry;
	}

	/**
	 * 文字列取得.
	 * @return このオブジェクトの文字列表現
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("UpdatedInfo [flg=");
		sb.append(flg);
		sb.append(", entry=");
		sb.append(updEntry.getMyUri());
		sb.append("]");
		return sb.toString();
	}

}
