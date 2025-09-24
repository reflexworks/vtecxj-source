package jp.reflexworks.taggingservice.bigquery;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.taggingservice.api.SettingConst;
import jp.reflexworks.taggingservice.env.TaggingEnvConst;
import jp.reflexworks.taggingservice.util.Constants;

/**
 * BigQuery定数インターフェース.
 */
public interface BigQueryConst {

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
	/** 設定(サービスごと) : プロジェクトID */
	public static final String BIGQUERY_PROJECTID = SettingConst.BIGQUERY_PROJECTID;
	/** 設定(サービスごと) : データセット名 */
	public static final String BIGQUERY_DATASET = SettingConst.BIGQUERY_DATASET;
	/** 設定(サービスごと) : ロケーション*/
	public static final String BIGQUERY_LOCATION = SettingConst.BIGQUERY_LOCATION;

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
			Constants.URI_SETTINGS_PROPERTIES + " and " + URI_SECRET_JSON + ".";

	/** BigQuery例外メッセージ : Already Exists */
	public static final String BIGQUERYEXCEPTION_ALREADY_EXISTS = "Already Exists";

}
