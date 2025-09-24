package jp.reflexworks.taggingservice.storage;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.StorageClass;
import com.google.cloud.storage.StorageException;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.servlet.HttpStatus;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexStatic;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.LockingException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.ServiceManager;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.RetryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * Google Cloud Storage ユーティリティ.
 */
public class CloudStorageUtil {
	
	/** バケット名エイリアスの親階層文字列長 */
	private static final int URI_BUCKET_SLASH_LEN = CloudStorageConst.URI_BUCKET_SLASH.length();

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(CloudStorageUtil.class);

	/**
	 * Google Cloud Storage用static情報を取得.
	 * @return Google Cloud Storage用static情報
	 */
	public static CloudStorageEnv getStorageEnv() {
		return (CloudStorageEnv)ReflexStatic.getStatic(CloudStorageConst.STATIC_NAME_STORAGE);
	}

	/**
	 * ストレージアップロード失敗時リトライ総数を取得.
	 * @return ストレージアップロード失敗時リトライ総数
	 */
	public static int getStorageUploadRetryCount() {
		return TaggingEnvUtil.getSystemPropInt(CloudStorageConst.STORAGE_UPLOAD_RETRY_COUNT,
				CloudStorageConst.STORAGE_UPLOAD_RETRY_COUNT_DEFAULT);
	}

	/**
	 * ストレージアップロード失敗時リトライ総数を取得.
	 * @return ストレージアップロード失敗時リトライ総数
	 */
	public static int getStorageUploadRetryWaitmillis() {
		return TaggingEnvUtil.getSystemPropInt(CloudStorageConst.STORAGE_UPLOAD_RETRY_WAITMILLIS,
				CloudStorageConst.STORAGE_UPLOAD_RETRY_WAITMILLIS_DEFAULT);
	}

	/**
	 * ストレージダウンロード失敗時リトライ総数を取得.
	 * @return ストレージダウンロード失敗時リトライ総数
	 */
	public static int getStorageDownloadRetryCount() {
		return TaggingEnvUtil.getSystemPropInt(CloudStorageConst.STORAGE_DOWNLOAD_RETRY_COUNT,
				CloudStorageConst.STORAGE_DOWNLOAD_RETRY_COUNT_DEFAULT);
	}

	/**
	 * ストレージダウンロード失敗時リトライ総数を取得.
	 * @return ストレージダウンロード失敗時リトライ総数
	 */
	public static int getStorageDownloadRetryWaitmillis() {
		return TaggingEnvUtil.getSystemPropInt(CloudStorageConst.STORAGE_DOWNLOAD_RETRY_WAITMILLIS,
				CloudStorageConst.STORAGE_DOWNLOAD_RETRY_WAITMILLIS_DEFAULT);
	}

	/**
	 * ローカルキャッシュアクセス失敗時リトライ総数を取得.
	 * @return ローカルキャッシュアクセス失敗時リトライ総数
	 */
	public static int getStorageCacheRetryCount() {
		return TaggingEnvUtil.getSystemPropInt(CloudStorageConst.STORAGE_CACHE_RETRY_COUNT,
				CloudStorageConst.STORAGE_CACHE_RETRY_COUNT_DEFAULT);
	}

	/**
	 * ローカルキャッシュアクセス失敗時リトライ総数を取得.
	 * @return ローカルキャッシュアクセス失敗時リトライ総数
	 */
	public static int getStorageCacheRetryWaitmillis() {
		return TaggingEnvUtil.getSystemPropInt(CloudStorageConst.STORAGE_CACHE_RETRY_WAITMILLIS,
				CloudStorageConst.STORAGE_CACHE_RETRY_WAITMILLIS_DEFAULT);
	}

	/**
	 * ローカルキャッシュロック失敗時リトライ総数を取得.
	 * @return ローカルキャッシュアクセス失敗時リトライ総数
	 */
	public static int getStorageCachelockRetryCount() {
		return TaggingEnvUtil.getSystemPropInt(CloudStorageConst.STORAGE_CACHELOCK_RETRY_COUNT,
				CloudStorageConst.STORAGE_CACHELOCK_RETRY_COUNT_DEFAULT);
	}

	/**
	 * ローカルキャッシュロック失敗時リトライ総数を取得.
	 * @return ローカルキャッシュアクセス失敗時リトライ総数
	 */
	public static int getStorageCachelockRetryWaitmillis() {
		return TaggingEnvUtil.getSystemPropInt(CloudStorageConst.STORAGE_CACHELOCK_RETRY_WAITMILLIS,
				CloudStorageConst.STORAGE_CACHELOCK_RETRY_WAITMILLIS_DEFAULT);
	}

	/**
	 * バケット登録失敗時リトライ総数を取得.
	 * @return ストレージダウンロード失敗時リトライ総数
	 */
	public static int getCreateBucketRetryCount() {
		return TaggingEnvUtil.getSystemPropInt(CloudStorageConst.STORAGE_CREATEBUCKET_RETRY_COUNT,
				CloudStorageConst.STORAGE_CREATEBUCKET_RETRY_COUNT_DEFAULT);
	}

	/**
	 * コンテンツファイルを配置するローカルディレクトリ(キャッシュに使用)を取得.
	 * @return コンテンツファイルを配置するローカルディレクトリ(キャッシュに使用)
	 *         指定なしはnullであり、この場合はローカルキャッシュしない。
	 */
	public static String getStorageCacheDir() {
		return TaggingEnvUtil.getSystemProp(
				CloudStorageConst.STORAGE_CACHE_DIR,
				null);
	}

	/**
	 * コンテンツファイルを配置するローカルディレクトリ(キャッシュに使用)を取得.
	 * @return コンテンツファイルを配置するローカルディレクトリ(キャッシュに使用)
	 */
	public static long getStorageCacheMaxsige() {
		return TaggingEnvUtil.getSystemPropLong(
				CloudStorageConst.STORAGE_CACHE_MAXSIZE,
				CloudStorageConst.STORAGE_CACHE_MAXSIZE_DEFAULT);
	}

	/**
	 * バケットのストレージクラスを取得.
	 * @return バケットのストレージクラス
	 */
	public static StorageClass getBucketStorageClass() {
		String storageClassName = TaggingEnvUtil.getSystemProp(
				CloudStorageConst.STORAGE_BUCKET_STORAGECLASS,
				CloudStorageConst.STORAGE_BUCKET_STORAGECLASS_DEFAULT).toUpperCase(Locale.ENGLISH);
		try {
			return StorageClass.valueOfStrict(storageClassName);
		} catch (IllegalArgumentException e) {
			logger.warn("[getBucketStorageClass] IllegalArgumentException: " + e.getMessage());
		}
		return StorageClass.valueOfStrict(CloudStorageConst.STORAGE_BUCKET_STORAGECLASS_DEFAULT);
	}

	/**
	 * バケットのロケーションを取得.
	 * @return バケットのロケーション
	 */
	public static String getBucketLocation() {
		return TaggingEnvUtil.getSystemProp(
				CloudStorageConst.STORAGE_BUCKET_LOCATION,
				CloudStorageConst.STORAGE_BUCKET_LOCATION_DEFAULT);
	}

	/**
	 * バケット名登録URIを取得
	 * /_service/{サービス名}/content
	 * @param serviceName サービス名
	 * @return バケット名登録URI
	 */
	public static String getContentUri(String serviceName) {
		ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
		StringBuilder sb = new StringBuilder();
		sb.append(serviceManager.getServiceUri(serviceName));
		sb.append(CloudStorageConst.URI_LAYER_CONTENT);
		return sb.toString();
	}

	/**
	 * バケット名登録URIを取得
	 * /_bucket/{バケット名}
	 * @param bucketName バケット名
	 * @return バケット名登録URI
	 */
	public static String getBucketUri(String bucketName) {
		StringBuilder sb = new StringBuilder();
		sb.append(CloudStorageConst.URI_BUCKET);
		sb.append("/");
		sb.append(bucketName);
		return sb.toString();
	}

	/**
	 * StorageException (extends RuntimeException) をCloudStorageExceptionに変換する.
	 * @param e 例外
	 * @return CloudStorageException 変換した例外
	 */
	public static CloudStorageException convertException(StorageException e) {
		return new CloudStorageException(e);
	}

	/**
	 * 例外を変換する.
	 * リトライ対象の場合例外をスローしない。
	 * @param e データストア例外
	 */
	public static void convertError(CloudStorageException e)
	throws IOException {
		if (isInputError(e)) {
			throw new IllegalParameterException(e.getMessage(), e);
		}
		if (isRetryError(e)) {
			return;
		}
		convertIOError(e);
	}

	/**
	 * CloudStorageExceptionをIOExceptionに変換してスローする。
	 * @param e CloudStorageException
	 */
	public static void convertIOError(CloudStorageException e)
	throws IOException {
		throw new IOException(e.getCause());
	}

	/**
	 * StorageExceptionの入力エラー判定.
	 * @param e StorageException
	 * @return 入力エラーの場合true
	 */
	public static boolean isInputError(CloudStorageException e) {
		return false;
	}

	/**
	 * リトライエラーの場合trueを返す.
	 * @param e StorageException
	 * @return リトライエラーの場合true
	 */
	public static boolean isRetryError(CloudStorageException e) {
		if (e == null) {
			return false;
		}
		if (e.getMessage() != null) {
			String msg = e.getMessage().toLowerCase(Locale.ENGLISH);
			// タイムアウトの場合リトライ対象とする。
			if (msg.indexOf("timeout") > -1 || msg.indexOf("timed out") > -1) {
				return true;
			}
		}
		if (e.isRetryable()) {
			return true;
		}
		return false;
	}

	/**
	 * バケットが既に登録されているエラーかどうかを判定.
	 * @param e StorageException
	 * @return バケットが既に登録されているエラーの場合true
	 */
	public static boolean isBucketDuplicated(CloudStorageException e) {
		// コードが409の場合重複
		int code = e.getCode();
		if (code == HttpStatus.SC_CONFLICT) {
			return true;
		}
		String msg = e.getMessage();
		if (msg != null && (msg.startsWith(CloudStorageConst.MSG_BUCKET_DUPLICATED_OWN) ||
				msg.startsWith(CloudStorageConst.MSG_BUCKET_DUPLICATED_OWN2) ||
				msg.startsWith(CloudStorageConst.MSG_BUCKET_DUPLICATED_OTHER))) {
			return true;
		}
		return false;
	}

	/**
	 * スリープ処理.
	 * @param waitMillis スリープ時間(ミリ秒)
	 */
	public static void sleep(long waitMillis) {
		try {
			Thread.sleep(waitMillis);
		} catch (InterruptedException e) {
			logger.warn("[sleep] InterruptedException: " + e.getMessage());
		}
	}

	/**
	 * 実行開始ログ編集
	 * @param command コマンド
	 * @return 実行開始ログ文字列
	 */
	public static String getStartLog(String command) {
		return getStartLog(command, null, null);
	}

	/**
	 * 実行開始ログ編集
	 * @param command コマンド
	 * @param blobId BlobId
	 * @return 実行開始ログ文字列
	 */
	public static String getStartLog(String command, BlobId blobId) {
		String bucket = null;
		String name = null;
		if (blobId != null) {
			bucket = blobId.getBucket();
			name = blobId.getName();
		}
		return getStartLog(command, bucket, name);
	}

	/**
	 * 実行開始ログ編集
	 * @param command コマンド
	 * @param blobInfo Blob情報
	 * @return 実行開始ログ文字列
	 */
	public static String getStartLog(String command, BlobInfo blobInfo) {
		String bucket = null;
		String name = null;
		if (blobInfo != null) {
			bucket = blobInfo.getBucket();
			name = blobInfo.getName();
		}
		return getStartLog(command, bucket, name);
	}

	/**
	 * 実行開始ログ編集
	 * @param command コマンド
	 * @param bucketInfo バケット情報
	 * @return 実行開始ログ文字列
	 */
	public static String getStartLog(String command, BucketInfo bucketInfo) {
		String bucket = null;
		if (bucketInfo != null) {
			bucket = bucketInfo.getName();
		}
		return getStartLog(command, bucket, null);
	}

	/**
	 * 実行開始ログ編集
	 * @param command コマンド
	 * @param bucket バケット
	 * @param name 名前
	 * @return 実行開始ログ文字列
	 */
	public static String getStartLog(String command, String bucket, String name) {
		StringBuilder sb = new StringBuilder();
		sb.append("[Storage] ");
		sb.append(command);
		sb.append(" start");
		if (bucket != null) {
			sb.append(" : bucket=");
			sb.append(bucket);
		}
		if (name != null) {
			sb.append(", name=");
			sb.append(name);
		}
		return sb.toString();
	}

	/**
	 * 実行開始ログ編集
	 * @param command コマンド
	 * @param startTime Redisコマンド実行直前の時間
	 * @return 実行終了ログ文字列
	 */
	public static String getEndLog(String command, long startTime) {
		return getEndLog(command, null, null, startTime);
	}

	/**
	 * 実行開始ログ編集
	 * @param command コマンド
	 * @param blobId BlobId
	 * @param startTime Redisコマンド実行直前の時間
	 * @return 実行終了ログ文字列
	 */
	public static String getEndLog(String command, BlobId blobId, long startTime) {
		String bucket = null;
		String name = null;
		if (blobId != null) {
			bucket = blobId.getBucket();
			name = blobId.getName();
		}
		return getEndLog(command, bucket, name, startTime);
	}

	/**
	 * 実行開始ログ編集
	 * @param command コマンド
	 * @param blobInfo Blob情報
	 * @param startTime Redisコマンド実行直前の時間
	 * @return 実行終了ログ文字列
	 */
	public static String getEndLog(String command, BlobInfo blobInfo, long startTime) {
		String bucket = null;
		String name = null;
		if (blobInfo != null) {
			bucket = blobInfo.getBucket();
			name = blobInfo.getName();
		}
		return getEndLog(command, bucket, name, startTime);
	}

	/**
	 * 実行開始ログ編集
	 * @param command コマンド
	 * @param bucketInfo バケット情報
	 * @param startTime Redisコマンド実行直前の時間
	 * @return 実行終了ログ文字列
	 */
	public static String getEndLog(String command, BucketInfo bucketInfo, long startTime) {
		String bucket = null;
		if (bucketInfo != null) {
			bucket = bucketInfo.getName();
		}
		return getEndLog(command, bucket, null, startTime);
	}

	/**
	 * 実行開始ログ編集
	 * @param command コマンド
	 * @param bucket バケット
	 * @param name 名前
	 * @param startTime Redisコマンド実行直前の時間
	 * @return 実行終了ログ文字列
	 */
	public static String getEndLog(String command, String bucket, String name, long startTime) {
		StringBuilder sb = new StringBuilder();
		sb.append("[Storage] ");
		sb.append(command);
		sb.append(" end");
		if (bucket != null) {
			sb.append(" : bucket=");
			sb.append(bucket);
		}
		if (name != null) {
			sb.append(", name=");
			sb.append(name);
		}

		sb.append(getElapsedTimeLog(startTime));
		return sb.toString();
	}

	/**
	 * 経過時間ログ表記を取得
	 * @param startTime 開始時間
	 * @return 経過時間ログ表記
	 */
	private static String getElapsedTimeLog(long startTime) {
		long finishTime = new Date().getTime();
		long time = finishTime - startTime;
		StringBuilder sb = new StringBuilder();
		sb.append(" - ");
		sb.append(time);
		sb.append("ms");
		return sb.toString();
	}

	/**
	 * URIからローカルキャッシュのファイルパスを取得.
	 * {設定のコンテンツキャッシュディレクトリ}/{名前空間}/contents/{URI変換文字列}
	 * @param uri URI
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return ローカルキャッシュのファイルパス
	 */
	public static String getCacheContentFilepath(String uri, String serviceName,
			String namespace, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		StringBuilder sb = new StringBuilder();
		sb.append(getCacheContentDirpath(serviceName, namespace, requestInfo, connectionInfo));
		sb.append(getCacheFilename(uri));
		return sb.toString();
	}

	/**
	 * URIからローカルキャッシュのディレクトリパスを取得.
	 * {設定のコンテンツキャッシュディレクトリ}/{名前空間}/contents/
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return ローカルキャッシュのディレクトリパス
	 */
	public static String getCacheContentDirpath(String serviceName, String namespace,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		StringBuilder sb = new StringBuilder();
		sb.append(getCacheServiceDirpath(serviceName, namespace, requestInfo, connectionInfo));
		sb.append(CloudStorageConst.CACHE_DIR_CONTENTS);
		sb.append(File.separator);
		return sb.toString();
	}

	/**
	 * URIからローカルキャッシュのヘッダ情報格納ファイルパスを取得.
	 * {設定のコンテンツキャッシュディレクトリ}/{名前空間}/headers/{URI変換文字列}.txt
	 * @param uri URI
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return ローカルキャッシュのヘッダ情報格納ファイルパス
	 */
	public static String getCacheHeaderFilepath(String uri, String serviceName,
			String namespace, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		StringBuilder sb = new StringBuilder();
		sb.append(getCacheHeaderDirpath(serviceName, namespace, requestInfo, connectionInfo));
		sb.append(getCacheFilename(uri));
		sb.append(CloudStorageConst.CACHE_HEADERS_EXTENSION);
		return sb.toString();
	}

	/**
	 * URIからローカルキャッシュのヘッダ情報格納ファイルパスを取得.
	 * {設定のコンテンツキャッシュディレクトリ}/{サービス名}/headers/
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return ローカルキャッシュのヘッダ情報格納ファイルパス
	 */
	public static String getCacheHeaderDirpath(String serviceName, String namespace,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		StringBuilder sb = new StringBuilder();
		sb.append(getCacheServiceDirpath(serviceName, namespace, requestInfo, connectionInfo));
		sb.append(CloudStorageConst.CACHE_DIR_HEADERS);
		sb.append(File.separator);
		return sb.toString();
	}

	/**
	 * ローカルキャッシュのサービス名ディレクトリまでのパスを取得.
	 * {設定のコンテンツキャッシュディレクトリ}/{名前空間}/
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return ローカルキャッシュのサービス名ディレクトリまでのパス
	 */
	public static String getCacheServiceDirpath(String serviceName, String namespace,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		StringBuilder sb = new StringBuilder();
		sb.append(getStorageCacheDir());
		sb.append(File.separator);
		sb.append(namespace);
		sb.append(File.separator);
		return sb.toString();
	}

	/**
	 * URIからローカルキャッシュのファイル名を取得.
	 * @param uri URI
	 * @return ローカルキャッシュのファイル名
	 */
	public static String getCacheFilename(String uri) {
		if (!StringUtils.isBlank(uri)) {
			return uri.replace("/", CloudStorageConst.LAYER_DELIMITER);
		}
		return null;
	}

	/**
	 * コンテンツファイル名からURIを取得
	 * @param filename コンテンツファイル名
	 * @return URI
	 */
	public static String getUriByCacheFilename(String filename) {
		if (!StringUtils.isBlank(filename)) {
			return filename.replace(CloudStorageConst.LAYER_DELIMITER, "/");
		}
		return null;
	}

	/**
	 * 例外がリトライ対象かどうか判定
	 * @param e 例外
	 * @param requestInfo リクエスト情報
	 * @throws IOException リトライ対象でない場合スローする
	 */
	public static void checkRetryError(IOException e, RequestInfo requestInfo) {
		StringBuilder sb = new StringBuilder();
		sb.append(LogUtil.getRequestInfoStr(requestInfo));
		sb.append("[checkRetryError] ");
		sb.append(e.getClass().getSimpleName());
		sb.append(": ");
		sb.append(e.getMessage());
		logger.info(sb.toString());

		// 一旦すべてリトライ対象とする

	}

	/**
	 * キャッシュ書き込みロックの状態を取得.
	 * @param filepath コンテンツのローカルファイルパス
	 * @return キャッシュ書き込みロックの状態。trueの場合ロック中。
	 */
	public static boolean isCacheLock(String filepath) {
		return getStorageEnv().isCacheLock(filepath);
	}

	/**
	 * キャッシュ書き込みロックを取得.
	 * @param filepath コンテンツのローカルファイルパス
	 * @return キャッシュ書き込みロックを取得できた場合true
	 */
	public static boolean getCacheLock(String filepath) {
		return getStorageEnv().getCacheLock(filepath);
	}

	/**
	 * キャッシュ書き込みロックを解放.
	 * @param filepath コンテンツのローカルファイルパス
	 */
	public static void releaseCacheLock(String filepath) {
		getStorageEnv().releaseCacheLock(filepath);
	}

	/**
	 * キャッシュのロックを取得.
	 * ロック失敗の場合は規定回数リトライします。
	 * @param filepath コンテンツのローカルファイルパス
	 * @throws LockingException ロック失敗
	 */
	public static void lockCache(String filepath)
	throws LockingException {
		int numRetries = CloudStorageUtil.getStorageCachelockRetryCount();
		int waitMillis = CloudStorageUtil.getStorageCachelockRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			boolean isLock = CloudStorageUtil.getCacheLock(filepath);
			if (isLock) {
				break;
			}
			if (r == numRetries) {
				throw new LockingException("Contents cache lock failed.");
			}
			RetryUtil.sleep(waitMillis);
		}
	}

	/**
	 * ストレージへのアクセスログを出力するかどうかを取得.
	 * @return ストレージへのアクセスログを出力する場合true
	 */
	public static boolean isEnableAccessLog() {
		return TaggingEnvUtil.getSystemPropBoolean(
				CloudStorageConst.STORAGE_ENABLE_ACCESSLOG, false) &&
				logger.isDebugEnabled();
	}

	/**
	 * ローカルキャッシュの最大容量しきい値(byte)を取得.
	 * @return ローカルキャッシュの最大容量しきい値(byte)
	 */
	public static long getCacheMaxsize() {
		return TaggingEnvUtil.getSystemPropLong(CloudStorageConst.STORAGE_CACHE_MAXSIZE,
				CloudStorageConst.STORAGE_CACHE_MAXSIZE_DEFAULT);
	}

	/**
	 * データストアからバケット名を取得.
	 * バケットが存在しない場合はnullを返す。
	 * @param serviceName サービス名
	 * @param systemContext システム管理サービスのReflexContext
	 * @return バケット名
	 */
	public static String getBucketNameByEntry(String serviceName, SystemContext systemContext)
	throws IOException, TaggingException {
		// データストアからバケット名を取得
		String contentUri = CloudStorageUtil.getContentUri(serviceName);
		EntryBase contentEntry = systemContext.getEntry(contentUri, true);
		if (isEnableAccessLog()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(systemContext.getRequestInfo()));
			sb.append("[getBucketNameByEntry] start. serviceName=");
			sb.append(serviceName);
			sb.append(", contentUri=");
			sb.append(contentUri);
			sb.append(", systemContext.getServiceName()=");
			sb.append(systemContext.getServiceName());
			sb.append(", contentEntry=");
			sb.append(systemContext.getResourceMapper().toJSON(contentEntry));
			logger.debug(sb.toString());
		}
		String bucketName = getBucketNameByEntry(contentEntry);
		if (bucketName != null) {
			return bucketName;
		}
		return null;
	}

	/**
	 * コンテント情報格納Entryからバケット名を取得.
	 * バケット名が設定されていない場合はnullを返却。
	 * @param contentEntry コンテント情報格納Entry
	 * @return バケット名
	 */
	public static String getBucketNameByEntry(EntryBase contentEntry) {
		// (2024.5.17) バケット名の設定箇所をエイリアス /_bucket/{バケット名} に修正。
		if (contentEntry != null) {
			List<String> aliases = contentEntry.getAlternate();
			if (aliases != null) {
				for (String alias : aliases) {
					if (alias.startsWith(CloudStorageConst.URI_BUCKET_SLASH)) {
						return alias.substring(URI_BUCKET_SLASH_LEN);
					}
				}
			}
		}
		return null;
	}

	/**
	 * バケット名の接頭辞を取得
	 * @return バケット名の接頭辞
	 */
	public static String getBucketPrefix() {
		return getStorageEnv().getBucketPrefix();
	}

	/**
	 * Storage接続インタフェースを取得.
	 * @param secret 秘密鍵
	 * @return Storage接続インタフェース
	 */
	public static CloudStorage getStorage(byte[] secret) throws IOException {
		// リトライ回数
		int numRetries = CloudStorageUtil.getStorageDownloadRetryCount();
		int waitMillis = CloudStorageUtil.getStorageDownloadRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				return CloudStorage.getStorage(secret);

			} catch (CloudStorageException e) {
				// リトライ判定、入力エラー判定
				CloudStorageUtil.convertError(e);
				if (r >= numRetries) {
					// リトライ対象だがリトライ回数を超えた場合
					CloudStorageUtil.convertIOError(e);
				}
				CloudStorageUtil.sleep(waitMillis + r * 10);
			}
		}

		throw new IllegalStateException("Unreachable code. (getStorage)");
	}

	/**
	 * サービス名から秘密鍵を取得 (コンテンツ登録・取得用).
	 * @param namespace 名前空間
	 * @return 秘密鍵 (コンテンツ登録・取得用)
	 */
	public static byte[] getContentSecret(String namespace) {
		CloudStorageEnv storageEnv = getStorageEnv();
		return storageEnv.getContentSecret(namespace);
	}

	/**
	 * サービス名から秘密鍵を取得 (バケット登録用).
	 * @param serviceName サービス名
	 * @return 秘密鍵 (バケット登録用)
	 */
	public static byte[] getBucketSecret(String serviceName) {
		CloudStorageEnv storageEnv = getStorageEnv();
		return storageEnv.getBucketSecret(serviceName);
	}

}
