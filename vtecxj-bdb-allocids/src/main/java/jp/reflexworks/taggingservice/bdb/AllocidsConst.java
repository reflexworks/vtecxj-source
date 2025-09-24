package jp.reflexworks.taggingservice.bdb;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * BDB 定数クラス.
 */
public class AllocidsConst {

	/** `DBAllocids` : 採番 */
	static final String DB_ALLOCIDS = "DBAllocids";
	/** `DBIncrement` : カウンタ */
	static final String DB_INCREMENT = "DBIncrement";

	/** テーブル名リスト */
	public static final List<String> DB_NAMES = new CopyOnWriteArrayList<String>();
	static {
		DB_NAMES.add(DB_ALLOCIDS);
		DB_NAMES.add(DB_INCREMENT);
	}

}
