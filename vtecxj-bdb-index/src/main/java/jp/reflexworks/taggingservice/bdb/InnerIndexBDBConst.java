package jp.reflexworks.taggingservice.bdb;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * BDB 定数クラス.
 */
public class InnerIndexBDBConst {

	/** `DBInnerIndex` : インデックス */
	static final String DB_INNER_INDEX = "DBInnerIndex";
	/** `DBInnerIndexAncestor` : インデックス更新用Ancestor */
	static final String DB_INNER_INDEX_ANCESTOR = "DBInnerIndexAncestor";
	/** `DBInnerIndexItem` : インデックス項目名の短縮値(採番)の格納テーブル */
	static final String DB_INNER_INDEX_ITEM = "DBInnerIndexItem";
	/** `DBDistkeyItem` : DISTKEYの項目名の短縮値(採番)の格納テーブル */
	static final String DB_DISTKEY_ITEM = "DBDistkeyItem";
	/** `DBAllocids` : 採番テーブル */
	static final String DB_ALLOCIDS = "DBAllocids";
	/** `DBInnerIndexVersion` : インデックスに反映済みのEntryの版(updated + revision) */
	static final String DB_INNER_INDEX_VERSION = "DBInnerIndexVersion";

	/** テーブル名リスト */
	public static final List<String> DB_NAMES = new CopyOnWriteArrayList<String>();
	static {
		DB_NAMES.add(DB_INNER_INDEX);
		DB_NAMES.add(DB_INNER_INDEX_ANCESTOR);
		DB_NAMES.add(DB_INNER_INDEX_ITEM);
		DB_NAMES.add(DB_DISTKEY_ITEM);
		DB_NAMES.add(DB_ALLOCIDS);
		DB_NAMES.add(DB_INNER_INDEX_VERSION);
	}

	/** 短縮値のキー */
	public static final String KEY_SHORTENING = "item";

	/** 同一IDのインデックス更新を直列化するロックの分割数 */
	static final int UPDATE_LOCK_STRIPES = 4096;
	/** 1トランザクションでまとめて更新するIDの最大数 */
	static final int UPDATE_BATCH_SIZE = 50;

}
