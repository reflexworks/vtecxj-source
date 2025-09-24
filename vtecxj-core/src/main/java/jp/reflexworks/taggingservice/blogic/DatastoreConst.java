package jp.reflexworks.taggingservice.blogic;

/**
 * Datastoreビジネスロジックの定数クラス
 */
public interface DatastoreConst {

	/** インデックス使用チェック結果メッセージ : インデックス使用 */
	public static final String MSG_CHECKINDEX_INDEX = "Index search: ";
	/** インデックス使用チェック結果メッセージ : 全文検索インデックス使用 */
	public static final String MSG_CHECKINDEX_FULLTEXTINDEX = "Full text search index: ";
	/** インデックス使用チェック結果メッセージ : インデックス使用なし */
	public static final String MSG_CHECKINDEX_NO_INDEX = "No index is used.";
	/** インデックス使用チェック結果メッセージ : DISTKEY使用 */
	public static final String MSG_CHECKINDEX_DISTKEY = "Distkey: ";

}
