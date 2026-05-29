package jp.reflexworks.batch;

import java.util.TimeZone;

/**
 * BatchBDB 定数クラス.
 */
public interface BatchBDBConst {

	/** 設定 : 夜間バッチフラグ有効期限(秒) */
	public static final String BATCH_BDB_EXPIRE_SEC = "_batch.bdb.expire.sec";
	/** 設定 : ログ残存期間(日) */
	public static final String LOG_KEEP_DAY = "_log.keep.day";
	/** 設定 : バッチジョブ履歴残存期間(日) */
	public static final String BATCHJOB_KEEP_DAY = "_batchjob.keep.day";
	/** 設定 : ログ最大取得件数 */
	public static final String LOG_ENTRY_NUMBER_LIMIT = "_log.entry.number.limit";
	/** 設定 : アラートメール送信ディスク使用率 */
	public static final String DISK_USAGE_ALERT = "_disk.usage.alert";

	/** 設定デフォルト : 夜間バッチフラグ有効期限(秒) */
	static final int BATCH_BDB_EXPIRE_SEC_DEFAULT = 10800;
	/** 設定デフォルト : ログ残存期間(日) */
	public static final int LOG_KEEP_DAY_DEFAULT = 1;
	/** 設定デフォルト : バッチジョブ履歴残存期間(日) */
	public static final int BATCHJOB_KEEP_DAY_DEFAULT = 31;
	/** 設定デフォルト : ログ最大取得件数 */
	public static final int LOG_ENTRY_NUMBER_LIMIT_DEFAULT = 1000;
	/** 設定デフォルト : アラートメール送信ディスク使用率 */
	public static final int DISK_USAGE_ALERT_DEFAULT = 70;

	/** データストアのAPIサイズエラーメッセージ(コミット時のみ) */
	public static final String DSMSG_COMMIT_MAXSIZEEXCEEDED = "I/O error";
	/** データストアのAPIサイズエラーメッセージ クライアント返却用 */
	public static final String MSG_COMMIT_MAXSIZEEXCEEDED = "Maximum size exceeded.";

	/** バイト読み込みバッファサイズ */
	public static final int BUFFER_SIZE = 2048;

	/** タイムゾーンID */
	public static final String TIMEZONE_ID = "Asia/Tokyo";
	/** タイムゾーン */
	public static final TimeZone TIMEZONE = TimeZone.getTimeZone(TIMEZONE_ID);
	/** フラグ : start */
	public static final String BATCH_BDB_FLAG_START = "start";
	/** フラグ : end */
	public static final String BATCH_BDB_FLAG_END = "end";

	/** URI : 夜間バッチフラグ */
	public static final String URI_BATCH_BDB = "/_batch_bdb";
	/** URI : ディスク使用量アラートメール */
	public static final String URI_SETTINGS_DISKUSAGE_ALERT = "/_settings/diskusage_alert";

	/** BigQuery ログエントリーテーブル名 */
	public static final String BQ_TABLENAME_LOG = "_log";
	/** BigQuery ログイン履歴テーブル名 */
	public static final String BQ_TABLENAME_LOGIN_HISTORY = "_login_history";
	/** BigQuery バッチジョブ履歴テーブル名 */
	public static final String BQ_TABLENAME_BATCHJOB = "_batchjob";

	/** BigQuery ログエントリーテーブル項目 : key */
	public static final String BQ_LOG_KEY = "key";
	/** BigQuery ログエントリーテーブル項目 : title */
	public static final String BQ_LOG_TITLE = "title";
	/** BigQuery ログエントリーテーブル項目 : subtitle */
	public static final String BQ_LOG_SUBTITLE = "subtitle";
	/** BigQuery ログエントリーテーブル項目 : message */
	public static final String BQ_LOG_MESSAGE = "message";
	/** BigQuery ログエントリーテーブル項目 : updated */
	public static final String BQ_LOG_UPDATED = "updated";
	/** BigQuery ログエントリーテーブル項目 : information */
	public static final String BQ_LOG_INFORMATION = "information";
	/** BigQuery ログイン履歴エントリーテーブル項目 : type */
	public static final String BQ_LOGINHISTORY_TYPE = "type";
	/** BigQuery ログイン履歴エントリーテーブル項目 : ip */
	public static final String BQ_LOGINHISTORY_IP = "ip";
	/** BigQuery ログイン履歴エントリーテーブル項目 : uid */
	public static final String BQ_LOGINHISTORY_UID = "uid";
	/** BigQuery ログイン履歴エントリーテーブル項目 : account */
	public static final String BQ_LOGINHISTORY_ACCOUNT = "account";
	/** BigQuery ログイン履歴エントリーテーブル項目 : useragent */
	public static final String BQ_LOGINHISTORY_USERAGENT = "useragent";
	/** BigQuery ログイン履歴エントリーテーブル項目 : cause */
	public static final String BQ_LOGINHISTORY_CAUSE = "cause";
	/** BigQuery バッチジョブ履歴エントリーテーブル項目 : status */
	public static final String BQ_BATCHJOB_STATUS = "status";
	/** BigQuery バッチジョブ履歴エントリーテーブル項目 : pod */
	public static final String BQ_BATCHJOB_POD = "pod";
	/** BigQuery バッチジョブ履歴エントリーテーブル項目 : script */
	public static final String BQ_BATCHJOB_SCRIPT = "script";
	/** BigQuery バッチジョブ履歴エントリーテーブル項目 : start */
	public static final String BQ_BATCHJOB_START = "start";
	/** BigQuery バッチジョブ履歴エントリーテーブル項目 : cloudrunjob */
	public static final String BQ_BATCHJOB_CLOUDRUNJOB = "cloudrunjob";
	
	/** ディスク使用量アラートメール メッセージ置き換え文字列 : ディスク使用率 */
	public static final String REPLACE_REGEX_DISKUSAGE = "\\$\\{DISKUSAGE\\}";
	/** ディスク使用量アラートメール メッセージ置き換え文字列 : サーバ名 */
	public static final String REPLACE_REGEX_SERVERNAME = "\\$\\{SERVERNAME\\}";

}
