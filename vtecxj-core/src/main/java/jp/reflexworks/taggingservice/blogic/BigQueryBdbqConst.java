package jp.reflexworks.taggingservice.blogic;

/**
 * BDB+BigQuery定数クラス.
 */
public interface BigQueryBdbqConst {
	
	/** URI : BigQuery登録リトライ対象格納フォルダ */
	public static final String URI_BDBQ = "/_bdbq";
	
	/** 登録/削除区分 : 登録 */
	public static final String TYPE_INSERT = "i";
	/** 登録/削除区分 : 削除 */
	public static final String TYPE_DELETE = "d";

}
