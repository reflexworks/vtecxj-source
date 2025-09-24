package jp.reflexworks.taggingservice.rdb;

/**
 * RDB 定数クラス.
 */
public interface ReflexRDBConst {

	/** 設定(システム) : RDBのJNDI名。"_jndi.jdbc.{サービス名}" */
	public static final String RDB_JNDI_PREFIX = "_jndi.jdbc.";
	/** 設定 : BigQueryへのアクセスログを出力するかどうか */
	public static final String RDB_ENABLE_ACCESSLOG = "_rdb.enable.accesslog";
	/** 設定 : エラー時の総リトライ回数 */
	public static final String RDB_RETRY_COUNT = "_rdb.retry.count";
	/** 設定 : エラーリトライ時の待ち時間(ミリ秒) */
	public static final String RDB_RETRY_WAITMILLIS = "_rdb.retry.waitmillis";
	/** 設定 : DriverManagerでコネクション取得する際に使用 : ホストIPアドレス */
	public static final String RDB_HOST = "_rdb.host";
	/** 設定 : DriverManagerでコネクション取得する際に使用 : ポート番号 */
	public static final String RDB_PORT = "_rdb.port";
	/** 設定 : DriverManagerでコネクション取得する際に使用 : データベース名 */
	public static final String RDB_DATABASE = "_rdb.database";
	/** 設定 : DriverManagerでコネクション取得する際に使用 : ユーザ名 */
	public static final String RDB_USER = "_rdb.user";
	/** 設定 : DriverManagerでコネクション取得する際に使用 : パスワード */
	public static final String RDB_PASS = "_rdb.pass";

	/** プロパティデフォルト値 : エラー時の総リトライ回数 */
	public static final int RDB_RETRY_COUNT_DEFAULT = 2;
	/** プロパティデフォルト値 : エラーリトライ時の待ち時間(ミリ秒) */
	public static final int RDB_RETRY_WAITMILLIS_DEFAULT = 200;

	/** 設定 "_rdb.jndi." の文字数 */
	public static final int RDB_JNDI_PREFIX_LEN = RDB_JNDI_PREFIX.length();

	/** メモリ上のstaticオブジェクト格納キー : RDB接続インタフェース */
	public static final String STATIC_NAME_RDB = "_rdb";

	/** コネクション情報格納キー */
	public static final String CONNECTION_INFO_RDB ="_rdb";

	public static final String JNDI_PREFIX = "java:/comp/env/";
}
