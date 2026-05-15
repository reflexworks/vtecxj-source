package jp.reflexworks.taggingservice.cloudrunjob;

/**
 * Cloud Run Job 定数クラス
 */
public interface CloudRunJobConst {

	/** Cloud Run Job 引数 : VTECX_URL */
	public static final String VTECX_URL = "VTECX_URL";
	/** Cloud Run Job 引数 : VTECX_APIKEY */
	public static final String VTECX_APIKEY = "VTECX_APIKEY";
	/** Cloud Run Job 引数 : ACCESS_TOKEN */
	public static final String ACCESS_TOKEN = "ACCESS_TOKEN";
	/** Cloud Run Job 引数 : SCRIPT_NAME */
	public static final String SCRIPT_NAME = "SCRIPT_NAME";
	
	/** 設定 : Cloud Run Job 名 */
	public static final String CLOUDRUNJOB_NAME = "_cloudrunjob.name";
	/** 設定 : Cloud Run Job デベロッパー サービスアカウント秘密鍵JSON */
	public static final String CLOUDRUNJOB_FILE_SECRET = "_cloudrunjob.file.secret";
	/** 設定 : JobsClient生成失敗時リトライ総数 */
	public static final String CLOUDRUNJOB_CREATEJOBSCLIENT_RETRY_COUNT = "_cloudrunjob.createjobsclient.retry.count";
	/** 設定 : JobsClient生成失敗時リトライ時のスリープ時間(ミリ秒) */
	public static final String CLOUDRUNJOB_CREATEJOBSCLIENT_RETRY_WAITMILLIS = "_cloudrunjob.createjobsclient.retry.waitmillis";
	/** 設定 : Google Cloud プロジェクトID */
	public static final String GCP_PROJECT_ID = "_gcp.projectid";
	/** 設定 : Google Cloud Run Job の実行リージョン */
	public static final String GCP_REGION = "_gcp.region";

	/** 設定デフォルト値 : JobsClient生成失敗時リトライ総数 */
	public static final int CLOUDRUNJOB_CREATEJOBSCLIENT_RETRY_COUNT_DEFAULT = 3;
	/** 設定デフォルト値 : JobsClient生成失敗時リトライ時のスリープ時間(ミリ秒) */
	public static final int CLOUDRUNJOB_CREATEJOBSCLIENT_RETRY_WAITMILLIS_DEFAULT = 300;

	/** static object : Cloud Run Job シングルトンオブジェクト */
	public static final String STATIC_NAME_CLOUDRUNJOB = "_cloudrunjob";
	
}
