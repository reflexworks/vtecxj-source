package jp.reflexworks.taggingservice.blogic;

/**
 * ページング機能 定数クラス
 */
public class PaginationConst {

	/** セッション格納キー : カーソルリスト接頭辞 */
	public static final String SESSION_KEY_CURSORLIST = "_PAGINATION_LIST";
	/** セッション格納キー : カーソルリストアクセス時刻接頭辞 */
	public static final String SESSION_KEY_ACCESSTIME = "_PAGINATION_ACCESSTIME";
	/** セッション格納キー : ページング処理ロックフラグ (値:インクリメント値) */
	public static final String SESSION_KEY_LOCK = "_PAGINATION_LOCK";
	/** セッション格納キー : インメモリソート処理ロックフラグ (値:検索条件) */
	public static final String SESSION_KEY_MEMORYSORT_LOCK = "_INMEMORYSORT_LOCK";
	/** セッション格納キー : インメモリソート用ページ数 */
	public static final String SESSION_KEY_MEMORYSORT_PAGENUM = "_INMEMORYSORT_PAGENUM";
	/** セッション格納キー : インメモリソート用キーリスト */
	public static final String SESSION_KEY_MEMORYSORT_LIST = "_INMEMORYSORT_LIST";
	/** セッション格納キー : インメモリソート用キーリストのページ数区切り文字 */
	public static final String SESSION_KEY_MEMORYSORT_LIST_DELIMITER = "#";

}
