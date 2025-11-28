package jp.reflexworks.taggingservice.bigquery;

import java.util.Arrays;
import java.util.List;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.taggingservice.env.TaggingEnvConst;
import jp.reflexworks.taggingservice.util.Constants;

/**
 * BigQuery定数インターフェース.
 */
public interface BigQueryConst {
	
	/** BigQueryのscope */
	public static final List<String> SCOPES = Arrays.asList("https://www.googleapis.com/auth/bigquery");

	/** コネクション情報格納キー */
	public static final String CONNECTION_INFO_BIGQUERY ="_bigquery";

	/** BigQueryサービスアカウント秘密鍵JSON格納キー */
	public static final String URI_SECRET_JSON = Constants.URI_SETTINGS + "/bigquery.json";

	/** 設定 : 環境ステージ */
	public static final String ENV_STAGE = TaggingEnvConst.ENV_STAGE;

	/** 設定 : BigQueryへのアクセスログを出力するかどうか */
	public static final String BIGQUERY_ENABLE_ACCESSLOG = "_bigquery.enable.accesslog";
	/** 設定 : エラー時の総リトライ回数 */
	public static final String BIGQUERY_RETRY_COUNT = "_bigquery.retry.count";
	/** 設定 : エラーリトライ時の待ち時間(ミリ秒) */
	public static final String BIGQUERY_RETRY_WAITMILLIS = "_bigquery.retry.waitmillis";
	/** 設定 : TaskQueueでのエラー時の総リトライ回数 */
	public static final String BIGQUERY_CALLABLE_RETRY_COUNT = "_bigquery.callable.retry.count";
	/** 設定 : TaskQueueでのエラーリトライ時の待ち時間(ミリ秒) */
	public static final String BIGQUERY_CALLABLE_RETRY_WAITMILLIS = "_bigquery.callable.retry.waitmillis";
	/** 設定 : ロケーションのデフォルト値 */
	public static final String BIGQUERY_DEFAULT_LOCATION = "_bigquery.default.location";

	/** プロパティデフォルト値 : エラー時の総リトライ回数 */
	public static final int BIGQUERY_RETRY_COUNT_DEFAULT = 2;
	/** プロパティデフォルト値 : エラーリトライ時の待ち時間(ミリ秒) */
	public static final int BIGQUERY_RETRY_WAITMILLIS_DEFAULT = 200;

	/** プロパティデフォルト値 : TaskQueueでのエラー時の総リトライ回数 */
	public static final int BIGQUERY_CALLABLE_RETRY_COUNT_DEFAULT = 4;
	/** プロパティデフォルト値 : TaskQueueでのエラーリトライ時の待ち時間(ミリ秒) */
	public static final int BIGQUERY_CALLABLE_RETRY_WAITMILLIS_DEFAULT = 200;

	/** BigQueryのフィールド : キー */
	public static final String BQFIELD_KEY = "key";
	/** BigQueryのフィールド : 更新日時 */
	public static final String BQFIELD_UPDATED = "updated";
	/** BigQueryのフィールド : 削除フラグ */
	public static final String BQFIELD_DELETED = "deleted";
	/** BigQueryのフィールド : ID */
	public static final String BQFIELD_ID = "id";

	/** ロケーション デフォルト値 (設定が無い場合に使用) */
	public static final String LOCATION_DEFAULT = "asia-northeast1";

	/** BigQueryにDate型項目を格納する際のフォーマット */
	public static final String BQDATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";

	/** ATOM項目 : content (recordでありrepeatedでない項目) */
	public static final String ATOM_CONTENT = "content";

	/** EntryBaseのフィールド */
	public static final java.lang.reflect.Field[] ATOM_FIELDS = EntryBase.class.getFields();

	/** BigQuery接続設定がされていない場合のエラーメッセージ */
	public static final String MSG_NO_SETTINGS =
			"BigQuery information is required. Please set " +
			Constants.URI_SETTINGS_PROPERTIES + ".";

	/** BigQuery例外メッセージ : Already Exists */
	public static final String BIGQUERYEXCEPTION_ALREADY_EXISTS = "Already Exists";

	/** メモリ上のstaticオブジェクト格納キー : BigQuery環境情報 */
	public static final String STATIC_NAME_BIGQUERY_ENV = "_bigquery_env";
	
	/** BigQueryオブジェクトキャッシュの最大格納数 */
	public static final int CACHE_MAXSIZE = 500;
	/** BigQueryオブジェクトキャッシュの有効期間(分) */
	public static final int CACHE_EXPIRE_MIN = 30;

}
