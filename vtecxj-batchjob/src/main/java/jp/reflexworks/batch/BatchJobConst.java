package jp.reflexworks.batch;

import jp.reflexworks.taggingservice.api.SettingConst;
import jp.reflexworks.taggingservice.util.Constants;

/**
 * バッチジョブ定数クラス.
 */
public interface BatchJobConst {

	/** プロパティファイル名 */
	public static final String PROPERTY_FILE_NAME = "vtecxbatchjob.properties";

	/** ジョブステータス : ジョブ登録済み・実行待ち */
	public static final String JOB_STATUS_WAITING = "waiting";
	/** ジョブステータス : 実行中 */
	public static final String JOB_STATUS_RUNNING = "running";
	/** ジョブステータス : 成功 */
	public static final String JOB_STATUS_SUCCEEDED = "succeeded";
	/** ジョブステータス : 失敗 */
	public static final String JOB_STATUS_FAILED = "failed";
	/** ジョブステータス : 未実行 */
	public static final String JOB_STATUS_NOT_EXECUTED = "not_executed";

	/** 設定 : ジョブ設定接頭辞 */
	public static final String PROP_BATCHJOB_PREFIX = SettingConst.BATCHJOB_PREFIX;
	/** ジョブ設定接頭辞の文字列長 */
	public static final int PROP_BATCHJOB_PREFIX_LEN = PROP_BATCHJOB_PREFIX.length();
	/** 設定 : バッチジョブの実行間隔(分) */
	public static final String PROP_BATCHJOB_EXEC_INTERVAL_MINUTE = "_batchjobexec.interval.minute";
	/** 設定 : 起動時のバッチジョブの実行間隔(秒) */
	public static final String PROP_BATCHJOB_EXEC_INIT_INTERVAL_SEC = "_batchjobexec.init.interval.sec";
	/**
	 * 設定 : バッチジョブの実行間隔追加時間(秒)
	 * 現在から(バッチジョブの実行間隔(分)+バッチジョブの実行間隔追加時間(秒))までに実行予定のジョブをスケジュールする。
	 */
	public static final String PROP_BATCHJOB_EXEC_ADDITIONAL_SEC = "_batchjobexec.additional.sec";
	/** 設定 : シャットダウン時の強制終了待ち時間(秒) */
	public static final String PROP_TASKQUEUE_AWAITTERMINATION_SEC = "_taskqueue.awaittermination.sec";
	/** 設定 : バッチジョブサーバURL */
	public static final String PROP_URL_BATCHJOB = "_url.batchjob";
	/** 設定 : バッチジョブサーバへのリクエストタイムアウト時間(ミリ秒) */
	public static final String PROP_BATCHJOB_EXEC_REQUEST_TIMEOUT_MILLIS = "_batchjobexec.request.timeout.millis";

	/** バッチジョブの実行間隔(分) デフォルト値 */
	public static final int BATCHJOB_EXEC_INTERVAL_MINUTE_DEFAULT = 1;
	/** 起動時のバッチジョブの実行間隔(秒) デフォルト値 */
	public static final int BATCHJOB_EXEC_INIT_INTERVAL_SEC_DEFAULT = 0;
	/** バッチジョブの実行間隔追加時間(秒) デフォルト値 */
	public static final int BATCHJOB_EXEC_ADDITIONAL_SEC_DEFAULT = 90;
	/** シャットダウン時の強制終了待ち時間(秒) デフォルト値 */
	public static final int TASKQUEUE_AWAITTERMINATION_SEC_DEFAULT = 60;
	/** バッチジョブサーバへのリクエストタイムアウト時間(ミリ秒) デフォルト値 */
	public static final int BATCHJOB_EXEC_REQUEST_TIMEOUT_MILLIS_DEFAULT = 30000;

	/** URI : ジョブ管理キー親階層 */
	public static final String URI_BATCHJOB = "/_batchjob";

	/** ログタイトル */
	public static final String LOG_TITLE = "BatchJob";
	/** JS実行メソッド */
	public static final String METHOD = "POST";

	/** ジョブ管理キーのジョブ実行時刻フォーマット */
	public static final String FORMAT_BATCHJOB = "yyyyMMddHHmm";

	/** バッチジョブ時間設定 : アスタリスク */
	public static final String CRON_ASTERISK = "*";
	/** バッチジョブ時間設定 : 間隔指定 */
	public static final String CRON_EVERY = "/";
	/** バッチジョブ時間設定の単位 */
	public enum CronTimeUnit {MINUTE, HOUR, DATE, MONTH, DAY};

	/** バッチジョブ処理Future static格納キー */
	public static final String STATIC_BATCHJOB_FUTURE_OF_JOB = "_bathjob_future_of_job";

	/** POD名のデフォルト値 */
	public static final String PODNAME_DEFAULT = "default_pod";
	/** 環境変数名 : POD名 */
	public static final String ENV_PODNAME = "HOSTNAME";
	/** 環境変数 : POD名 */
	public static final String PODNAME = BatchJobUtil.getPodName();

	/** リクエスト情報 : IP */
	public static final String REQUESTINFO_IP = "local";
	/** リクエスト情報 : method */
	public static final String REQUESTINFO_METHOD = BatchJobBlogic.class.getSimpleName();
	/** リクエスト情報 : URL */
	public static final String REQUESTINFO_URL = BatchJobBlogic.class.getName();

	/** バッチジョブサーバリクエストメソッド */
	public static final String METHOD_BATCHJOB = Constants.POST;
	
	/** サービス一覧検索URI */
	public static final String URI_SERVICE = Constants.URI_SERVICE;
	/** サービス一覧検索URI+"/"の文字列長 */
	public static final int URI_SERVICE_SLASH_LEN = URI_SERVICE.length() + 1;

}
