package jp.reflexworks.taggingservice.bdb;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * BDB 定数クラス.
 */
public class FullTextSearchBDBConst {

	/** `DBFullTextIndex` : 全文検索インデックス */
	static final String DB_FULL_TEXT_INDEX = "DBFullTextIndex";
	/** `DBFullTextIndexAncestor` : 全文検索インデックス更新用Ancestor */
	static final String DB_FULL_TEXT_INDEX_ANCESTOR = "DBFullTextIndexAncestor";
	/** `DBFullTextIndexItem` : 全文検索インデックス項目名の短縮値(採番)の格納テーブル */
	static final String DB_FULL_TEXT_INDEX_ITEM = "DBFullTextIndexItem";
	/** `DBDistkeyItem` : DISTKEYの項目名の短縮値(採番)の格納テーブル */
	static final String DB_DISTKEY_ITEM = "DBDistkeyItem";
	/** `DBAllocids` : 採番テーブル */
	static final String DB_ALLOCIDS = "DBAllocids";

	/** テーブル名リスト */
	public static final List<String> DB_NAMES = new CopyOnWriteArrayList<String>();
	static {
		DB_NAMES.add(DB_FULL_TEXT_INDEX);
		DB_NAMES.add(DB_FULL_TEXT_INDEX_ANCESTOR);
		DB_NAMES.add(DB_FULL_TEXT_INDEX_ITEM);
		DB_NAMES.add(DB_DISTKEY_ITEM);
		DB_NAMES.add(DB_ALLOCIDS);
	}

	/** 短縮値のキー */
	public static final String KEY_SHORTENING = "item";

	/** 全文検索条件の検索済みキー格納キー */
	static final String SESSION_FULLTEXTSEARCH = "_FTGET_";

}
