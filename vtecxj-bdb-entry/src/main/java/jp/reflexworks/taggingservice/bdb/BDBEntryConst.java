package jp.reflexworks.taggingservice.bdb;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * BDB 定数クラス.
 */
public class BDBEntryConst {

	/** `DBEntry` : Entry */
	static final String DB_ENTRY = "DBEntry";

	/** テーブル名リスト */
	public static final List<String> DB_NAMES = new CopyOnWriteArrayList<String>();
	static {
		DB_NAMES.add(DB_ENTRY);
	}

}
