package jp.reflexworks.taggingservice.storage;

import jp.reflexworks.taggingservice.env.TaggingEnvConst;

/**
 * Google Cloud Storege 定数クラス.
 */
public class CloudStorageConst {

	/** メモリ上のstaticオブジェクト格納キー */
	public static final String STATIC_NAME_STORAGE = "_storage";

	/** 設定 : 環境ステージ */
	public static final String ENV_STAGE = TaggingEnvConst.ENV_STAGE;
	/** ストレージアップロードに失敗したときのリトライ回数 **/
	public static final String STORAGE_UPLOAD_RETRY_COUNT = "_storage.upload.retry.count";
	/** ストレージアップロードリトライ時のスリープ時間(ミリ秒) **/
	public static final String STORAGE_UPLOAD_RETRY_WAITMILLIS = "_storage.upload.retry.waitmillis";
	/** ストレージダウンロードに失敗したときのリトライ回数 **/
	public static final String STORAGE_DOWNLOAD_RETRY_COUNT = "_storage.download.retry.count";
	/** ストレージダウンロードリトライ時のスリープ時間(ミリ秒) **/
	public static final String STORAGE_DOWNLOAD_RETRY_WAITMILLIS = "_storage.dowload.retry.waitmillis";
	/** バケット生成時のリトライ回数 */
	public static final String STORAGE_CREATEBUCKET_RETRY_COUNT = "_storage.createbucket.retry.count";
	/**
	 * バケットのストレージクラス.
	 * 以下のいずれかを指定します。
	 * <ul>
	 *   <li>multi_regional</li>
	 *   <li>regional</li>
	 *   <li>nearline</li>
	 *   <li>coldline</li>
	 * </ul>
	 */
	public static final String STORAGE_BUCKET_STORAGECLASS = "_storage.bucket.storageclass";
	/**
	 * バケットのロケーション
	 * ストレージクラスに合わせて、以下のいずれかを指定します。
	 * <ul>
	 *   <li>multi_regional : asia, eu, us</li>
	 *   <li>regional : asia-east1, asia-northeast1, europe-west1, us-central1, us-east1, us-west1</li>
	 *   <li>nearline : マルチリージョンまたはリージョンのロケーション</li>
	 *   <li>coldline : マルチリージョンまたはリージョンのロケーション</li>
	 * </ul>
	 */
	public static final String STORAGE_BUCKET_LOCATION = "_storage.bucket.location";
	/** ストレージアクセス秘密鍵ファイル名(コンテンツ登録・取得用及びバケット作成用) */
	public static final String STORAGE_FILE_SECRET = "_storage.file.secret";
	/** ストレージアクセス秘密鍵ファイル名(コンテンツ登録・取得用) */
	public static final String STORAGE_FILE_SECRET_CONTENT = "_storage.file.secret.content";
	/** ストレージアクセス秘密鍵ファイル名(バケット作成用) */
	public static final String STORAGE_FILE_SECRET_BUCKET = "_storage.file.secret.bucket";
	/** 他のスレッドによるバケット登録待ちのリトライ回数 **/
	public static final String STORAGE_WAITCREATEBUCKET_RETRY_COUNT = "_storage.waitcreatebucket.retry.count";
	/** 他のスレッドによるバケット登録待ちリトライ時のスリープ時間(ミリ秒) **/
	public static final String STORAGE_WAITCREATEBUCKET_RETRY_WAITMILLIS = "_storage.waitcreatebucket.retry.waitmillis";
	/** コンテンツファイルを配置するローカルディレクトリ(キャッシュに使用) */
	public static final String STORAGE_CACHE_DIR = "_storage.cache.dir";
	/** コンテンツのローカルキャッシュ最大容量(byte) */
	public static final String STORAGE_CACHE_MAXSIZE = "_storage.cache.maxsize";
	/** ローカルキャッシュアクセスに失敗したときのリトライ回数 **/
	public static final String STORAGE_CACHE_RETRY_COUNT = "_storage.cache.retry.count";
	/** ローカルキャッシュアクセスリトライ時のスリープ時間(ミリ秒) **/
	public static final String STORAGE_CACHE_RETRY_WAITMILLIS = "_storage.cache.retry.waitmillis";
	/** ローカルキャッシュロックに失敗したときのリトライ回数 **/
	public static final String STORAGE_CACHELOCK_RETRY_COUNT = "_storage.cachelock.retry.count";
	/** ローカルキャッシュロックリトライ時のスリープ時間(ミリ秒) **/
	public static final String STORAGE_CACHELOCK_RETRY_WAITMILLIS = "_storage.cachelock.retry.waitmillis";
	/** ストレージへのアクセスログを出力するかどうか */
	public static final String STORAGE_ENABLE_ACCESSLOG = "_storage.enable.accesslog";
	/** ストレージの最大合計容量 (stagingサービス用) */
	public static final String STORAGE_MAX_TOTALSIZE = "_storage.max.totalsize";
	/** バケット作成ロックの有効期限(秒) */
	public static final String STORAGE_LOCK_EXPIRE_SEC = "_storage.lock.expire.sec";
	/** バケットに設定するCORSの有効期間(秒) */
	public static final String STORAGE_BUCKET_CORS_MAXAGE_SEC = "_storage.bucket.cors.maxage.sec";

	/** 設定デフォルト : 環境ステージ */
	public static final String ENV_STAGE_DEFAULT = TaggingEnvConst.ENV_STAGE_DEFAULT;
	/** ストレージアップロード失敗時のリトライ回数デフォルト値 */
	public static final int STORAGE_UPLOAD_RETRY_COUNT_DEFAULT = 2;
	/** ストレージアップロード失敗でリトライ時のスリープ時間デフォルト値 */
	public static final int STORAGE_UPLOAD_RETRY_WAITMILLIS_DEFAULT = 200;
	/** ストレージダウンロード失敗時のリトライ回数デフォルト値 */
	public static final int STORAGE_DOWNLOAD_RETRY_COUNT_DEFAULT = 2;
	/** ストレージダウンロード失敗でリトライ時のスリープ時間デフォルト値 */
	public static final int STORAGE_DOWNLOAD_RETRY_WAITMILLIS_DEFAULT = 100;
	/** バケット生成時のリトライ回数デフォルト値 */
	public static final int STORAGE_CREATEBUCKET_RETRY_COUNT_DEFAULT = 30;
	/** バケットのストレージクラスデフォルト値 */
	public static final String STORAGE_BUCKET_STORAGECLASS_DEFAULT = "REGIONAL";
	/** バケットのロケーションデフォルト値 */
	public static final String STORAGE_BUCKET_LOCATION_DEFAULT = "asia-northeast1";
	/** 他のスレッドによるバケット登録待ちのリトライ回数デフォルト値 **/
	public static final int STORAGE_WAITCREATEBUCKET_RETRY_COUNT_DEFAULT = 10;
	/** 他のスレッドによるバケット登録待ちリトライ時のスリープ時間デフォルト値 **/
	public static final int STORAGE_WAITCREATEBUCKET_RETRY_WAITMILLIS_DEFAULT = 1000;
	/** ローカルキャッシュの最大容量デフォルト値 **/
	public static final long STORAGE_CACHE_MAXSIZE_DEFAULT = 32212254720L;
	/** ローカルキャッシュアクセス失敗時のリトライ回数デフォルト値 */
	public static final int STORAGE_CACHE_RETRY_COUNT_DEFAULT = 2;
	/** ローカルキャッシュアクセス失敗でリトライ時のスリープ時間デフォルト値 */
	public static final int STORAGE_CACHE_RETRY_WAITMILLIS_DEFAULT = 250;
	/** ローカルキャッシュロック失敗時のリトライ回数デフォルト値 */
	public static final int STORAGE_CACHELOCK_RETRY_COUNT_DEFAULT = 200;
	/** ローカルキャッシュロック失敗でリトライ時のスリープ時間デフォルト値 */
	public static final int STORAGE_CACHELOCK_RETRY_WAITMILLIS_DEFAULT = 100;
	/** ストレージの最大合計容量デフォルト値 */
	public static final long STORAGE_MAX_TOTALSIZE_DEFAULT = 104857600;
	/** バケット作成ロックの有効期限(秒)デフォルト値 */
	public static final int STORAGE_LOCK_EXPIRE_SEC_DEFAULT = 300;
	/** 署名付きURLの有効期限(分) デフォルト値 */
	public static final int STORAGE_SIGNEDURL_EXPIRE_MIN_DEFAULT = 15;
	/** バケットに設定するCORSの有効期間(秒) デフォルト値 */
	public static final int STORAGE_BUCKET_CORS_MAXAGE_SEC_DEFAULT = 3600;

	/** バケット登録エラーメッセージ : 自身で登録済み */
	public static final String MSG_BUCKET_DUPLICATED_OWN = "You already own this bucket. Please select another name.";
	/** バケット登録エラーメッセージ : 自身で登録済み2 */
	public static final String MSG_BUCKET_DUPLICATED_OWN2 = "Your previous request to create the named bucket succeeded and you already own it.";
	/** バケット登録エラーメッセージ : 他で登録済み */
	public static final String MSG_BUCKET_DUPLICATED_OTHER = "Sorry, that name is not available. Please try a different one.";

	/** URI content階層 */
	public static final String URI_LAYER_CONTENT = "/content";
	/** URI bucket */
	public static final String URI_BUCKET = "/_bucket";
	/** URI bucket slash */
	public static final String URI_BUCKET_SLASH = URI_BUCKET + "/";

	/** コネクション情報格納キー (コンテンツ登録・取得用) */
	public static final String CONNECTION_INFO_CONTENT ="_storage_content";
	/** コネクション情報格納キー (バケット作成用) */
	public static final String CONNECTION_INFO_BUCKET ="_storage_bucket";

	/** バイト読み込みバッファサイズ */
	public static final int BUFFER_SIZE = 2048;

	/** 接続確認のためのファイル名 */
	public static final String TEST_FILE = "_test";

	/** コンテンツがストレージに登録されている情報 */
	public static final String CONTENT_SRC_STORAGE = "_storage";

	/** ロック用URI 接頭辞 */
	public static final String URI_LOCK_PREFIX = "/#_lock/content/";
	/** ロック中文字列 */
	public static final String LOCK = "lock";

	/** ローカルキャッシュファイル名のURIの階層区切り文字 */
	public static final String LAYER_DELIMITER = "#";
	/** ローカルキャッシュ コンテンツディレクトリ */
	public static final String CACHE_DIR_CONTENTS = "contents";
	/** ローカルキャッシュ ヘッダディレクトリ */
	public static final String CACHE_DIR_HEADERS = "headers";
	/** ローカルキャッシュ ヘッダ情報のファイル拡張子 */
	public static final String CACHE_HEADERS_EXTENSION = ".txt";
	/** ローカルキャッシュ ヘッダ情報のキーと値の区切り文字 */
	public static final String CACHE_HEADERS_DELIMITER = ":";
	/** ローカルキャッシュ ヘッダ情報のリビジョンと更新日時の区切り文字 */
	public static final String CACHE_REVISION_UPDATED_DELIMITER = ",";

}
