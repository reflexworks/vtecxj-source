package jp.reflexworks.taggingservice.secret;

/**
 * シークレット管理 定数クラス
 */
public interface ReflexSecretConst {

	/** Google Cloud プロジェクトID */
	static final String PROP_GCP_PROJECTID = "_gcp.projectid";
	/** Secret ManagerのサービスアカウントJSONファイル名 */
	static final String PROP_SECRET_FILE_SECRET = "_secret.file.secret";
	/** 暗号化キーのシークレット名 */
	static final String PROP_SECRETKEY_NAME = "_secret.secretkey.name";
	/** 暗号化キーのシークレットバージョン(オプション) */
	static final String PROP_SECRETKEY_VERSION = "_secret.secretkey.version";

	/** プロパティキー : Secret Managerアクセスエラー時の総リトライ回数 */
	static final String PROP_SECRET_RETRY_COUNT = "_secret.retry.count";
	/** プロパティキー : Secret Managerアクセスエラーリトライ時の待ち時間(ミリ秒) */
	static final String PROP_SECRET_RETRY_WAITMILLIS = "_secret.retry.waitmillis";

	/** プロパティデフォルト値 : Secret Managerアクセスエラー時の総リトライ回数 */
	static final int SECRET_RETRY_COUNT_DEFAULT = 2;
	/** プロパティデフォルト値 : Secret Managerアクセスエラーリトライ時の待ち時間(ミリ秒) */
	static final int SECRET_RETRY_WAITMILLIS_DEFAULT = 200;

	/** 最新バージョン取得時の指定値 */
	static final String VERSION_LATEST = "latest";

}
