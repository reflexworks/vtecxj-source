package jp.reflexworks.batch;

import jp.reflexworks.taggingservice.api.SettingConst;
import jp.reflexworks.taggingservice.util.Constants;

/**
 * バッチジョブ定数クラス.
 */
public interface BatchJobConst {

	/** ジョブステータス : ジョブ登録済み・実行待ち */
	public static final String JOB_STATUS_WAITING = "waiting";
	/** ジョブステータス : 実行中 */
	public static final String JOB_STATUS_RUNNING = "running";
	/** ジョブステータス : 成功 */
	public static final String JOB_STATUS_SUCCEEDED = "succeeded";
	/** ジョブステータス : 失敗 */
	public static final String JOB_STATUS_FAILED = "failed";
	/** ジョブステータス : 実行停止中 */
	public static final String JOB_STATUS_CANCELING = "canceling";
	/** ジョブステータス : 実行停止 */
	public static final String JOB_STATUS_CANCELED = "canceled";

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
	/** 設定 : バッチジョブ実行サーバ(runner)のジョブ実行リクエストURL */
	public static final String PROP_BATCHJOB_RUNNER_URL = "_batchjob.runner.url";
	/** 設定 : バッチジョブ実行サーバからの終了通知リクエストURL (runnerへwebhook_urlとして渡す) */
	public static final String PROP_BATCHJOB_RESPONSE_URL = "_batchjob.response.url";
	/** 設定 : バッチジョブ実行サーバへのリクエストタイムアウト時間(ミリ秒) */
	public static final String PROP_BATCHJOB_RUNNER_REQUEST_TIMEOUT_MILLIS = "_batchjob.runner.request.timeout.millis";
	/** 設定 : バッチジョブ実行サーバへのリクエスト失敗時リトライ総数 */
	public static final String PROP_BATCHJOB_RUNNER_RETRY_COUNT = "_batchjob.runner.retry.count";
	/** 設定 : バッチジョブ実行サーバへのリクエスト失敗時リトライ時のスリープ時間(ミリ秒) */
	public static final String PROP_BATCHJOB_RUNNER_RETRY_WAITMILLIS = "_batchjob.runner.retry.waitmillis";
	/** 設定 : バッチジョブサーバのホスト名 */
	public static final String PROP_BATCHJOB_SERVERNAME = "_batchjob.servername";
	/** 設定 : アクセスログ出力フラグ */
	public static final String PROP_BATCHJOB_ENABLE_ACCESSLOG = "_batchjob.enable.accesslog";

	/** バッチジョブの実行間隔(分) デフォルト値 */
	public static final int BATCHJOB_EXEC_INTERVAL_MINUTE_DEFAULT = 5;
	/** 起動時のバッチジョブの実行間隔(秒) デフォルト値 */
	public static final int BATCHJOB_EXEC_INIT_INTERVAL_SEC_DEFAULT = 0;
	/** バッチジョブの実行間隔追加時間(秒) デフォルト値 */
	public static final int BATCHJOB_EXEC_ADDITIONAL_SEC_DEFAULT = 150;
	/** シャットダウン時の強制終了待ち時間(秒) デフォルト値 */
	public static final int TASKQUEUE_AWAITTERMINATION_SEC_DEFAULT = 60;
	/** バッチジョブサーバへのリクエストタイムアウト時間(ミリ秒) デフォルト値 */
	public static final int BATCHJOB_EXEC_REQUEST_TIMEOUT_MILLIS_DEFAULT = 30000;
	/** バッチジョブ実行サーバへのリクエストタイムアウト時間(ミリ秒) デフォルト値 */
	public static final int BATCHJOB_RUNNER_REQUEST_TIMEOUT_MILLIS_DEFAULT = 30000;
	/** バッチジョブ実行サーバへのリクエスト失敗時リトライ総数 デフォルト値 */
	public static final int BATCHJOB_RUNNER_RETRY_COUNT_DEFAULT = 2;
	/** バッチジョブ実行サーバへのリクエスト失敗時リトライ時のスリープ時間(ミリ秒) デフォルト値 */
	public static final int BATCHJOB_RUNNER_RETRY_WAITMILLIS_DEFAULT = 500;

	/** URI : ジョブ管理キー親階層 */
	public static final String URI_BATCHJOB = Constants.URI_BATCHJOB;
	/** URI : ジョブ管理エイリアス親階層 */
	public static final String URI_BATCHJOB_ALIAS = Constants.URI_BATCHJOB_ALIAS;
	/** URI : バッチジョブ実行サーバで実行されるバッチジョブの親階層 */
	public static final String URI_BATCHJOB_SCRIPT = Constants.URI_HTML + "/batchjob";

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

	/** バッチジョブ実行サーバへのリクエストメソッド */
	public static final String METHOD_BATCHJOB_RUNNER = Constants.POST;
	/** バッチジョブ実行結果受信を表すリクエストパラメータ名 */
	public static final String PARAM_BATCHJOBRESULT = "_batchjobresult";

	/** 実行サーバ連携JSONフィールド : APサーバURL */
	public static final String JSON_URL = "url";
	/** 実行サーバ連携JSONフィールド : サービス名 */
	public static final String JSON_SERVICE_NAME = "service_name";
	/** 実行サーバ連携JSONフィールド : APIキー */
	public static final String JSON_APIKEY = "apikey";
	/** 実行サーバ連携JSONフィールド : アクセストークン */
	public static final String JSON_ACCESSTOKEN = "accesstoken";
	/** 実行サーバ連携JSONフィールド : ジョブ名(サーバサイドJS名) */
	public static final String JSON_SCRIPT_NAME = "script_name";
	/** 実行サーバ連携JSONフィールド : ジョブ実行ID */
	public static final String JSON_JOB_ID = "job_id";
	/** 実行サーバ連携JSONフィールド : 終了通知リクエストURL */
	public static final String JSON_WEBHOOK_URL = "webhook_url";
	/** 実行サーバ連携JSONフィールド : 実行結果(成功/失敗) */
	public static final String JSON_OK = "ok";
	/** 実行サーバ連携JSONフィールド : メッセージ */
	public static final String JSON_MESSAGE = "message";
	/** 実行サーバ連携JSONフィールド : 経過時間(秒) */
	public static final String JSON_ELAPSED_TIME = "elapsed_time";
	
	/** サービス一覧検索URI */
	public static final String URI_SERVICE = Constants.URI_SERVICE;
	/** サービス一覧検索URI+"/"の文字列長 */
	public static final int URI_SERVICE_SLASH_LEN = URI_SERVICE.length() + 1;

}
