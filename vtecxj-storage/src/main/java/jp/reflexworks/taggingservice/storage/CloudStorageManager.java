package jp.reflexworks.taggingservice.storage;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.BucketInfo.SoftDeletePolicy;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageClass;

import jp.reflexworks.atom.entry.Content;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContentInfo;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexEnv;
import jp.reflexworks.taggingservice.api.ReflexStatic;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.blogic.ServiceBlogic;
import jp.reflexworks.taggingservice.conn.ConnectionInfoImpl;
import jp.reflexworks.taggingservice.conn.ReflexConnection;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.PaymentRequiredException;
import jp.reflexworks.taggingservice.exception.StaticDuplicatedException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.UpdatedInfo;
import jp.reflexworks.taggingservice.plugin.CacheManager;
import jp.reflexworks.taggingservice.plugin.CallingAfterCommit;
import jp.reflexworks.taggingservice.plugin.ContentManager;
import jp.reflexworks.taggingservice.plugin.ExecuteAtCreateService;
import jp.reflexworks.taggingservice.plugin.SettingService;
import jp.reflexworks.taggingservice.service.TaggingServiceUtil;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.sys.SystemUtil;
import jp.reflexworks.taggingservice.taskqueue.TaskQueueUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.DeflateUtil;
import jp.sourceforge.reflex.util.FileUtil;
import jp.sourceforge.reflex.util.NumberingUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * Google Cloud Storageへのコンテンツアクセスクラス.
 */
public class CloudStorageManager 
implements ContentManager, SettingService, CallingAfterCommit, ExecuteAtCreateService {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 初期起動時の処理.
	 */
	public void init() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();

		// Google Cloud Storage用static情報をメモリに格納
		CloudStorageEnv storageEnv = new CloudStorageEnv();
		try {
			ReflexStatic.setStatic(CloudStorageConst.STATIC_NAME_STORAGE, storageEnv);
		} catch (StaticDuplicatedException e) {
			storageEnv = (CloudStorageEnv)ReflexStatic.getStatic(CloudStorageConst.STATIC_NAME_STORAGE);
		}

		// バケット名の接頭辞を設定
		String stage = TaggingEnvUtil.getSystemProp(CloudStorageConst.ENV_STAGE,
				CloudStorageConst.ENV_STAGE_DEFAULT);
		String prefix = stage + "-";
		if (isEnableAccessLog()) {
			StringBuilder sb = new StringBuilder();
			sb.append("[init] ");
			sb.append(CloudStorageConst.ENV_STAGE);
			sb.append("=");
			sb.append(stage);
			logger.debug(sb.toString());
		}
		storageEnv.setBucketPrefix(prefix);

		// 秘密鍵の設定
		DeflateUtil deflateUtil = null;
		ConnectionInfo connectionInfo = null;
		try {
			String serviceName = env.getSystemService();
			RequestInfo requestInfo = SystemUtil.getRequestInfo(serviceName,
					"init", "init");
			deflateUtil = new DeflateUtil();
			connectionInfo = new ConnectionInfoImpl(deflateUtil, requestInfo);

			// バケット登録用
			String secretFilename = env.getSystemProp(
					CloudStorageConst.STORAGE_FILE_SECRET_BUCKET);
			if (StringUtils.isBlank(secretFilename)) {
				secretFilename = env.getSystemProp(
						CloudStorageConst.STORAGE_FILE_SECRET);
			}
			String jsonPath = FileUtil.getResourceFilename(secretFilename);
			if (!StringUtils.isBlank(jsonPath)) {
				byte[] secret = getSecretFileData(jsonPath);
				if (secret != null) {
					if (!checkBucketConnection(secret, serviceName, requestInfo, connectionInfo)) {
						String message = "secret json is invalid. " + secretFilename;
						logger.warn("[init] Cloud storage Connection failed. " + message);
						throw new IllegalStateException(message);
					}
					// 接続OKの場合、鍵を格納する。
					storageEnv.setBucketSecret(serviceName, secret);
				}
			} else {
				// 秘密鍵なしで接続
				if (!checkBucketConnection(null, serviceName, requestInfo, connectionInfo)) {
					String message = " (no secret json)";
					logger.warn("[init] Cloud storage Connection failed." + message);
					throw new IllegalStateException(message);
				}
			}

			// コンテンツ登録・取得用
			secretFilename = env.getSystemProp(
					CloudStorageConst.STORAGE_FILE_SECRET_CONTENT);
			if (StringUtils.isBlank(secretFilename)) {
				secretFilename = env.getSystemProp(
						CloudStorageConst.STORAGE_FILE_SECRET);
			}
			jsonPath = FileUtil.getResourceFilename(secretFilename);
			if (!StringUtils.isBlank(jsonPath)) {
				byte[] secret = getSecretFileData(jsonPath);
				if (secret != null) {
					if (!checkContentConnection(secret, serviceName, requestInfo, connectionInfo)) {
						String message = "secret json is invalid. " + secretFilename;
						logger.warn("[init] Cloud storage Connection failed. " + message);
						throw new IllegalStateException(message);
					}
					// 接続OKの場合、鍵を格納する。
					storageEnv.setContentSecret(serviceName, secret);
				}
			} else {
				// 秘密鍵なしで接続
				if (!checkContentConnection(null, serviceName, requestInfo, connectionInfo)) {
					String message = " (no secret json)";
					logger.warn("[init] Cloud storage Connection failed." + message);
					throw new IllegalStateException(message);
				}
			}

		} catch (FileNotFoundException e) {
			logger.warn("[init] json file is not found.", e);
			throw new IllegalStateException(e);
		} catch (IOException e) {
			logger.warn("[init] IOException", e);
			throw new IllegalStateException(e);
		} catch (TaggingException e) {
			logger.warn("[init] TaggingserviceException", e);
			throw new IllegalStateException(e);
		} finally {
			if (connectionInfo != null) {
				connectionInfo.close();
			}
		}
	}

	/**
	 * シャットダウン時の処理.
	 */
	public void close() {
		// Do nothing.
	}

	/**
	 * コンテンツをアップロードする.
	 * @param uri URI
	 * @param data アップロードデータ
	 * @param headers アップロードコンテンツのヘッダ情報
	 * @param isBySize 画像ファイルのサイズ展開を行う場合true
	 * @param reflexContext データアクセスコンテキスト
	 */
	public EntryBase upload(String uri, byte[] data, Map<String, String> headers,
			boolean isBySize, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		ReflexAuthentication auth = reflexContext.getAuth();
		if (isEnableAccessLog()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
					"[upload] start. uri = " + uri);
		}

		// ストレージ容量チェック
		checkStorageTotalsize(serviceName, requestInfo, connectionInfo);

		// ACLはU(更新)があれば処理可のため(前処理でチェック済み)、検索等はSystemContextで行う。
		SystemContext systemContext = new SystemContext(auth, requestInfo, connectionInfo);

		// アップロード
		EntryBase entry = null;
		if (isBySize) {
			CloudStorageBySize cloudStorageBySize = new CloudStorageBySize();
			// 拡張子チェック
			cloudStorageBySize.checkExtension(uri);
			// 元ファイルのアップロード
			entry = uploadProc(uri, data, headers, systemContext);
			// サイズ指定登録
			cloudStorageBySize.uploadBySize(uri, data, headers, this, systemContext);
		} else {
			// アップロード
			entry = uploadProc(uri, data, headers, systemContext);
		}

		return entry;
	}

	/**
	 * コンテンツをアップロードする.
	 * @param uri URI
	 * @param data アップロードデータ
	 * @param headers アップロードコンテンツのヘッダ情報
	 * @param systemContext データアクセスコンテキスト
	 */
	EntryBase uploadProc(String uri, byte[] data, Map<String, String> headers,
			SystemContext systemContext)
	throws IOException, TaggingException {
		String serviceName = systemContext.getServiceName();
		RequestInfo requestInfo = systemContext.getRequestInfo();
		ConnectionInfo connectionInfo = systemContext.getConnectionInfo();
		ReflexAuthentication auth = systemContext.getAuth();
		String namespace = systemContext.getNamespace();
		if (isEnableAccessLog()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
					"[uploadProc] start. uri = " + uri);
		}

		// Entry取得
		EntryBase entry = systemContext.getEntry(uri);
		if (entry == null) {
			// Entry登録
			entry = createStorageEntry(uri, serviceName);
			entry = systemContext.post(entry);
		} else {
			// Entry更新(id=Etagを更新する)
			editStorageEntry(entry);
			if (isEnableAccessLog()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[uploadProc] uri = ");
				sb.append(uri);
				sb.append(" entry.id = ");
				sb.append(entry.id);
				logger.debug(sb.toString());
			}
			entry = systemContext.put(entry);
		}
		int revision = TaggingEntryUtil.getRevisionById(entry.id);
		String updated = TaggingEntryUtil.getUpdated(entry);

		// コンテンツアップロード
		if (useStorageCache()) {
			// ローカルキャッシュに登録する。
			LocalCacheManager localCacheManager = new LocalCacheManager();
			localCacheManager.writeToCache(uri, revision, updated, data, headers,
					auth, namespace, requestInfo, connectionInfo);
			// コンテンツのクラウドストレージ登録TaskQueueを呼び出す。
			CloudStorageUploaderCallable uploaderCallable =
					new CloudStorageUploaderCallable(uri);
			TaskQueueUtil.addTask(uploaderCallable, 0, auth, requestInfo, connectionInfo);

		} else {
			// ストレージにアップロードする。
			uploadToStorage(uri, data, headers, namespace, serviceName, 
					requestInfo, connectionInfo);
		}

		return entry;
	}

	/**
	 * コンテンツをアップロードする.
	 * @param uri URI
	 * @param in アップロードコンテンツのストリーム
	 * @param headers アップロードコンテンツのヘッダ情報
	 * @param namespace 名前空間
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	void uploadToStorage(String uri, byte[] data, Map<String, String> headers,
			String namespace, String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		if (logger.isDebugEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[uploadToStorage] start. uri = ");
			sb.append(uri);
			sb.append(" size = ");
			sb.append(data.length);
			logger.debug(sb.toString());
		}
		// バケット名取得
		String bucket = getBucketName(true, namespace, serviceName, requestInfo,
				connectionInfo);
		if (bucket == null) {
			logger.warn(LogUtil.getRequestInfoStr(requestInfo) +
					"[uploadToStorage] bucket is null.");
			throw new IOException("The bucket could not created.");
		}
		// ファイル名
		String fileName = editFileName(uri);

		// BlobInfo.Builder
		BlobInfo.Builder builder = BlobInfo.newBuilder(bucket, fileName);
		String contentType = getValue(headers, ReflexServletConst.HEADER_CONTENT_TYPE);
		if (!StringUtils.isBlank(contentType)) {
			builder.setContentType(contentType);
		}
		String contentDisposition = getValue(headers, ReflexServletConst.HEADER_CONTENT_DISPOSITION);
		if (!StringUtils.isBlank(contentDisposition) && contentDisposition.indexOf(ReflexServletConst.HEADER_FORM_DATA) == -1) {
			builder.setContentDisposition(contentDisposition);
		}
		String contentEncoding = getValue(headers, ReflexServletConst.HEADER_CONTENT_ENCODING);
		if (!StringUtils.isBlank(contentEncoding)) {
			builder.setContentEncoding(contentEncoding);
		}
		String contentLanguage = getValue(headers, ReflexServletConst.HEADER_CONTENT_LANGUAGE);
		if (!StringUtils.isBlank(contentLanguage)) {
			builder.setContentLanguage(contentLanguage);
		}

		CloudStorageConnection storage = null;
		// リトライ回数
		int numRetries = CloudStorageUtil.getStorageUploadRetryCount();
		int waitMillis = CloudStorageUtil.getStorageUploadRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				// Storage接続インタフェース取得
				storage = getStorageContent(namespace, connectionInfo);
				// Cloud Storageへアップロード
				storage.create(builder.build(), data);
				break;
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
	}

	/**
	 * コンテンツをダウンロードする.
	 * @param uri URI
	 * @param reflexContext データアクセスコンテキスト
	 * @return コンテンツ情報
	 */
	public ReflexContentInfo download(EntryBase entry, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		String namespace = reflexContext.getNamespace();
		String uri = entry.getMyUri();
		if (isEnableAccessLog()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
					"[download] start. uri = " + uri);
		}

		ReflexContentInfo contentInfo = null;
		boolean useCache = useStorageCache();
		if (useCache) {
			// キャッシュからコンテンツ取得
			LocalCacheManager localCacheManager = new LocalCacheManager();
			contentInfo = localCacheManager.readFromCache(entry, serviceName, namespace,
					requestInfo, connectionInfo);
			if (contentInfo != null) {
				// コンテンツの更新時刻更新非同期処理を呼び出す。
				LocalCacheLastmodifiedCallable lastModifiedCallable =
						new LocalCacheLastmodifiedCallable(uri);
				TaskQueueUtil.addTask(lastModifiedCallable, true, 0, auth, requestInfo,
						connectionInfo);
			}
		}
		if (contentInfo == null) {
			// コンテンツダウンロード
			contentInfo = downloadFromStorage(entry, namespace, auth, requestInfo,
					connectionInfo);
			if (contentInfo != null && useCache) {
				// コンテンツをローカルキャッシュに登録する非同期処理を呼び出す。
				LocalCacheWriterCallable writerCallable = new LocalCacheWriterCallable(
						entry, contentInfo);
				// TaskQueueエラー時にログエントリー書き込みをしない。
				TaskQueueUtil.addTask(writerCallable, true, 0, auth, requestInfo,
						connectionInfo);
			}
		}
		return contentInfo;
	}

	/**
	 * コンテンツをダウンロードする.
	 * @param entry Entry
	 * @param namespace 名前空間
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return コンテンツ情報
	 */
	private ReflexContentInfo downloadFromStorage(EntryBase entry,
			String namespace, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		String uri = entry.getMyUri();
		if (isEnableAccessLog()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
					"[downloadFromStorage] start. uri = " + entry.getMyUri());
		}

		// バケット名取得
		String bucket = getBucketName(false, namespace, serviceName, 
				requestInfo, connectionInfo);
		// バケット名が取得できない場合は、このサービスのバケットが存在しない。
		if (bucket == null) {
			if (isEnableAccessLogInfo()) {
				logger.info("[download] bucket is null.");
			}
			return null;
		}
		// ファイル名
		String fileName = editFileName(uri);

		BlobId blobid = BlobId.of(bucket, fileName);

		// 権限エラーの場合、例外が発生する。
		// ファイルが存在しない場合は正常に処理され、nullが返る。
		// bucketが存在しない場合も正常に処理され、nullが返る。
		CloudStorageConnection storage = null;
		// リトライ回数
		int numRetries = CloudStorageUtil.getStorageDownloadRetryCount();
		int waitMillis = CloudStorageUtil.getStorageDownloadRetryWaitmillis();
		CloudStorageInfo cloudStorageInfo = null;
		for (int r = 0; r <= numRetries; r++) {
			try {
				// Storage接続インタフェース取得
				storage = getStorageContent(namespace, connectionInfo);
				// Cloud Storageからダウンロード
				CloudStorageBlob blob = storage.get(blobid);
				if (blob != null) {
					if (isEnableAccessLog()) {
						logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
								"[download] blob was successfully obtained. uri = " + uri);
					}
					cloudStorageInfo = new CloudStorageInfo(uri, blob, entry);
				} else {
					if (isEnableAccessLog()) {
						logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
								"[download] blob is null. uri = " + uri);
					}
				}
				break;

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
		return cloudStorageInfo;
	}

	/**
	 * コンテンツを削除する.
	 * @param uri URI
	 * @param reflexContext データアクセスコンテキスト
	 * @return コンテンツ情報
	 */
	public EntryBase delete(String uri, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		String namespace = reflexContext.getNamespace();
		if (isEnableAccessLog()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
					"[delete] start. uri = " + uri);
		}
		// ACLはD(削除)があれば処理可のため(前処理でチェック済み)、検索等はSystemContextで行う。
		SystemContext systemContext = new SystemContext(reflexContext.getAuth(), 
				requestInfo, connectionInfo);
		// データストアからEntryを取得
		EntryBase entry = systemContext.getEntry(uri);

		// コンテンツ削除
		deleteFromStorage(uri, namespace, serviceName, requestInfo, connectionInfo);
		// ローカルキャッシュからもコンテンツ削除
		if (useStorageCache()) {
			LocalCacheManager localCacheManager = new LocalCacheManager();
			localCacheManager.deleteFromCache(uri, serviceName, namespace,
					requestInfo, connectionInfo);
		}

		// コンテンツEntryの更新
		if (editStorageDeleteEntry(entry)) {
			entry = systemContext.put(entry);
		}
		return entry;
	}

	/**
	 * コンテンツを削除する.
	 * @param uri URI
	 * @param namespace 名前空間
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return コンテンツを削除できた場合true
	 */
	private boolean deleteFromStorage(String uri, String namespace, String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// バケット名取得
		String bucket = getBucketName(false, namespace, serviceName, requestInfo,
				connectionInfo);
		// バケット名が取得できない場合は、このサービスのバケットが存在しない。
		if (bucket == null) {
			if (isEnableAccessLogInfo()) {
				logger.info(LogUtil.getRequestInfoStr(requestInfo) +
						"[delete] bucket is null.");
			}
			return false;
		}
		// ファイル名
		String fileName = editFileName(uri);

		BlobId blobid = BlobId.of(bucket, fileName);

		// 権限エラーの場合、例外が発生する。
		// ファイルが存在しない場合は正常に処理され、nullが返る。
		// bucketが存在しない場合も正常に処理され、nullが返る。
		CloudStorageConnection storage = null;
		// リトライ回数
		int numRetries = CloudStorageUtil.getStorageDownloadRetryCount();
		int waitMillis = CloudStorageUtil.getStorageDownloadRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				// Storage接続インタフェース取得
				storage = getStorageContent(namespace, connectionInfo);
				// Cloud Storageから削除
				return storage.delete(blobid);

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
		//
		logger.warn(LogUtil.getRequestInfoStr(requestInfo) + "[download] Unreachable code.");
		return false;
	}

	/**
	 * ファイルから秘密鍵データを取得.
	 * @return 秘密鍵データ
	 */
	private byte[] getSecretFileData(String filePath) {
		byte[] data = null;
		InputStream isJson = null;
		try {
			isJson = FileUtil.getInputStreamFromFile(filePath);
			if (isJson == null) {
				throw new FileNotFoundException("SecretFile is not found. " + filePath);
			}
			ByteArrayOutputStream bout = new ByteArrayOutputStream();
			byte[] buffer = new byte[CloudStorageConst.BUFFER_SIZE];
			int len = 0;
			while ((len = isJson.read(buffer)) > 0) {
				bout.write(buffer, 0, len);
			}
			data = bout.toByteArray();

		} catch (IOException e) {
			logger.warn("[getSecretFileData] IO error.", e);
		} finally {
			if (isJson != null) {
				try {
					isJson.close();
				} catch (IOException e) {
					logger.warn("[getSecretFileData] close error.", e);
				}
			}
		}
		return data;
	}

	/**
	 * Google Cloud Storageへの接続確認
	 *   権限エラーの場合、例外が発生する。
	 *     ・秘密鍵不正
	 *        com.google.cloud.storage.StorageException: Error signing service account access token request with private key.
	 *     ・ACL無し
	 *        com.google.cloud.storage.StorageException: Caller does not have storage.objects.get access to object {bucket}/{file name}.
	 *
	 *   ファイルが存在しない場合は正常に処理され、nullが返る。
	 *   bucketが存在しない場合
	 *     ・ダウンロード
	 *        正常に処理され、nullが返る。
	 *     ・アップロード
	 *        com.google.cloud.storage.StorageException: Not Found
	 * @param secret 秘密鍵
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	private boolean checkContentConnection(byte[] secret, String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// まずバケット名をデータストアから取得
		String systemService = TaggingEnvUtil.getSystemService();
		SystemContext systemContext = new SystemContext(systemService, requestInfo,
				connectionInfo);
		String bucketName = getBucketNameByEntry(serviceName, systemContext);
		if (StringUtils.isBlank(bucketName)) {
			throw new IOException("Bucket is nothing. serviceName = " + serviceName);
		}
		// Storageコネクションを生成
		CloudStorageConnection storage = getStorage(secret);
		// 接続確認
		return checkConnection(storage, bucketName);
	}

	/**
	 * 接続確認.
	 * @param storage Storageコネクションインターフェース
	 * @param bucketName バケット名
	 * @return 接続OKの場合true
	 */
	private boolean checkConnection(CloudStorageConnection storage,
			String bucketName)
	throws IOException, TaggingException {
		// 接続確認
		int numRetries = CloudStorageUtil.getStorageDownloadRetryCount();
		int waitMillis = CloudStorageUtil.getStorageDownloadRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				// バケット操作はOwnerのみ。
				// コンテンツのダウンロードでテストする。
				BlobId blobid = BlobId.of(bucketName, CloudStorageConst.TEST_FILE);
				// データが存在すればBlobオブジェクト、存在しなければnullが返る。
				// 異常なくgetできれば成功とする。
				storage.get(blobid);
				return true;

			} catch (CloudStorageException e) {
				if (logger.isDebugEnabled()) {
					logger.debug("[checkConnection] StorageException", e);
				}
				// リトライ判定、入力エラー判定
				if (!CloudStorageUtil.isRetryError(e)) {
					// 接続認証エラーとみなす
					return false;
				}
				if (r >= numRetries) {
					// リトライ対象だがリトライ回数を超えた場合
					CloudStorageUtil.convertIOError(e);
				}
				CloudStorageUtil.sleep(waitMillis + r * 10);
			}
		}

		logger.warn("[checkConnection] Unreachable code.");
		return false;
	}

	/**
	 * バケット用コネクションの接続確認.
	 * データストアからバケット名を取得する。
	 * アップロードの場合、バケット名の登録が無ければバケットを生成する。
	 * @param secret 秘密鍵
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return バケット名
	 */
	private boolean checkBucketConnection(byte[] secret, String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// Storageコネクションを生成
		CloudStorageConnection storage = getStorage(secret);

		// データストアからバケット名を取得
		String systemService = TaggingEnvUtil.getSystemService();
		SystemContext systemContext = new SystemContext(systemService, requestInfo,
				connectionInfo);
		String bucketName = getBucketNameByEntry(serviceName, systemContext);
		if (isEnableAccessLog()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[checkBucketConnection] bucketName=");
			sb.append(bucketName);
			logger.debug(sb.toString());
		}
		if (!StringUtils.isBlank(bucketName)) {
			// 接続確認
			return checkConnection(storage, bucketName);

		} else {
			// データストアにバケット名が登録されていない場合、このサービス用のバケットを登録する。
			bucketName = createBucket(storage, serviceName, systemContext,
					requestInfo, connectionInfo);
			return !StringUtils.isBlank(bucketName);
		}
	}

	/**
	 * コンテンツ登録・取得用Storage接続インタフェースを取得.
	 * @param namespace 名前空間
	 * @param connectionInfo コネクション情報
	 * @return コンテンツ登録・取得用Storage接続インタフェース
	 */
	private CloudStorageConnection getStorageContent(String namespace,
			ConnectionInfo connectionInfo)
	throws IOException {
		return getStorage(CloudStorageConst.CONNECTION_INFO_CONTENT, namespace,
				connectionInfo);
	}

	/**
	 * バケット登録用Storage接続インタフェースを取得.
	 * @param namespace 名前空間
	 * @param connectionInfo コネクション情報
	 * @return バケット登録用Storage接続インタフェース
	 */
	CloudStorageConnection getStorageBucket(String namespace,
			ConnectionInfo connectionInfo)
	throws IOException {
		return getStorage(CloudStorageConst.CONNECTION_INFO_BUCKET, namespace,
				connectionInfo);
	}

	/**
	 * Storage接続インタフェースを取得.
	 * @param connName コネクション名
	 * @param namespace 名前空間
	 * @param connectionInfo コネクション情報
	 * @return Storage接続インタフェース
	 */
	private CloudStorageConnection getStorage(String connName, String namespace,
			ConnectionInfo connectionInfo)
	throws IOException {
		CloudStorageConnection storage = null;
		boolean exists = false;
		ReflexConnection<?> reflexConn = connectionInfo.get(connName);
		if (reflexConn != null) {
			// コネクション情報からコネクションを取得
			storage = (CloudStorageConnection)reflexConn;
			exists = true;
		}
		if (storage == null) {
			// Google Cloud Storageコネクションを取得
			byte[] secret = null;
			if (CloudStorageConst.CONNECTION_INFO_CONTENT.equals(connName)) {
				secret = getContentSecret(namespace);
			} else {
				secret = getBucketSecret(namespace);
			}
			storage = getStorage(secret);
			if (storage != null) {
				// 取得したCloud Storageコネクションをコネクション情報に格納
				if (exists) {
					connectionInfo.close(connName);
				}
				connectionInfo.put(connName, storage);
			}
		}
		return storage;
	}

	/**
	 * Storage接続インタフェースを取得.
	 * @param secret 秘密鍵
	 * @return Storage接続インタフェース
	 */
	private CloudStorageConnection getStorage(byte[] secret) throws IOException {
		CloudStorage storage = CloudStorageUtil.getStorage(secret);
		return new CloudStorageConnection(storage);
	}

	/**
	 * バケット名を取得.
	 * データストアからバケット名を取得する。
	 * アップロードの場合、バケット名の登録が無ければバケットを生成する。
	 * @param isCreate バケットが存在しなければ作成する場合true (アップロードの場合true)
	 * @param namespace 名前空間
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return バケット名
	 */
	String getBucketName(boolean isCreate, String namespace, String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		if (isEnableAccessLog()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[getBucketName] start. namespace=");
			sb.append(namespace);
			sb.append(", serviceName=");
			sb.append(serviceName);
			sb.append(", isCreate=");
			sb.append(isCreate);
			logger.debug(sb.toString());
		}
		// データストアからバケット名を取得
		String systemService = TaggingEnvUtil.getSystemService();
		SystemContext systemContext = new SystemContext(systemService, requestInfo,
				connectionInfo);
		String bucketName = getBucketNameByEntry(serviceName, systemContext);
		if (!StringUtils.isBlank(bucketName)) {
			return bucketName;
		}

		if (isCreate) {
			// データストアにバケット名が登録されていない場合、このサービス用のバケットを登録する。
			CloudStorageConnection storage = getStorageBucket(namespace,
					connectionInfo);
			return createBucket(storage, serviceName, systemContext,
					requestInfo, connectionInfo);
		} else {
			// ダウンロードの場合、バケット名の登録がなければデータの登録もなし。
			return null;
		}
	}

	/**
	 * データストアからバケット名を取得.
	 * バケットが存在しない場合はnullを返す。
	 * @param serviceName サービス名
	 * @param systemContext システム管理サービスのReflexContext
	 * @return バケット名
	 */
	private String getBucketNameByEntry(String serviceName, SystemContext systemContext)
	throws IOException, TaggingException {
		return CloudStorageUtil.getBucketNameByEntry(serviceName, systemContext);
	}

	/**
	 * コンテント情報格納Entryからバケット名を取得.
	 * バケット名が設定されていない場合はnullを返却。
	 * @param contentEntry コンテント情報格納Entry
	 * @return バケット名
	 */
	private String getBucketNameByEntry(EntryBase contentEntry) {
		return CloudStorageUtil.getBucketNameByEntry(contentEntry);
	}

	/**
	 * バケットを新規登録.
	 * バケットを新規登録し、バケット名をデータストアに保存します。
	 * @param storage ストレージインターフェース
	 * @param serviceName サービス名
	 * @param systemContext システム管理ユーザ権限ReflexContext
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return バケット名
	 */
	private String createBucket(CloudStorageConnection storage, String serviceName,
			SystemContext systemContext,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {

		// ランダム値発行
		// StorageClass
		// Location

		String bucketName = null;

		// サービスで排他が必要
		// 排他はRedisを使用して行う。
		String contentUri = CloudStorageUtil.getContentUri(serviceName);
		String lockUri = getLockUri(serviceName);
		boolean lock = false;
		CacheManager cacheManager = TaggingEnvUtil.getCacheManager();
		try {
			lock = cacheManager.setStringIfAbsent(lockUri, CloudStorageConst.LOCK, 
					systemContext);
			if (lock) {
				if (logger.isDebugEnabled()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
							"[createBucket] start");
				}
				int expireSec = TaggingEnvUtil.getSystemPropInt(
						CloudStorageConst.STORAGE_LOCK_EXPIRE_SEC,
						CloudStorageConst.STORAGE_LOCK_EXPIRE_SEC_DEFAULT);
				cacheManager.setExpireString(lockUri, expireSec, systemContext);
				// もう一度Entryにバケット名が登録されていないか確認する。
				// Entry検索後からロックまでの間に別スレッドでバケット登録された場合に対応。
				EntryBase contentEntry = systemContext.getEntry(contentUri, false);
				bucketName = getBucketNameByEntry(contentEntry);
				if (bucketName != null) {
					if (logger.isDebugEnabled()) {
						logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
								"[createBucket] The bucket was created by another thread.");
					}
					return bucketName;
				}

				// バケット名について、とりあえずサービス名で登録してみる。
				bucketName = getDefaultBucketName(serviceName);

				StorageClass bucketStorageClass = CloudStorageUtil.getBucketStorageClass();
				String bucketLocation = CloudStorageUtil.getBucketLocation();
				// Cloud Storage バケットの削除（復元可能）を無効にする設定
				SoftDeletePolicy.Builder softDeletePolicyBuilder = SoftDeletePolicy.newBuilder();
				softDeletePolicyBuilder.setRetentionDuration(Duration.ZERO);
				SoftDeletePolicy softDeletePolicy = softDeletePolicyBuilder.build();

				int numRetries = CloudStorageUtil.getCreateBucketRetryCount();
				int numRetriesTimeout = CloudStorageUtil.getStorageUploadRetryCount();
				int waitMillisTimeout = CloudStorageUtil.getStorageUploadRetryWaitmillis();
				for (int r = 0; r <= numRetries; r++) {
					try {
						//if (isEnableAccessLog()) {
						if (logger.isDebugEnabled()) {
							logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
									"[createBucket] Trying to create bucket : " + bucketName);
						}
						// バケット生成
						storage.create(BucketInfo.newBuilder(bucketName)
								// See here for possible values: http://g.co/cloud/storage/docs/storage-classes
								.setStorageClass(bucketStorageClass)
								// Possible values: http://g.co/cloud/storage/docs/bucket-locations#location-mr
								.setLocation(bucketLocation)
								// Cloud Storage バケットの削除（復元可能）を無効にする設定をセット
								.setSoftDeletePolicy(softDeletePolicy)

								.build());

						// バケット生成成功
						// バケット名をデータストアに登録する
						if (contentEntry != null) {
							editContentEntry(bucketName, contentEntry);
						} else {
							contentEntry = createContentEntry(bucketName, serviceName);
						}
						systemContext.put(contentEntry);

						if (logger.isInfoEnabled()) {
							logger.info(LogUtil.getRequestInfoStr(requestInfo) +
									"[createBucket] bucketName = " + bucketName);
						}

						break;

					} catch (CloudStorageException e) {
						// リトライ判定、入力エラー判定
						if (CloudStorageUtil.isBucketDuplicated(e)) {
							if (r >= numRetries) {
								if (logger.isInfoEnabled()) {
									logger.info(LogUtil.getRequestInfoStr(requestInfo) +
											"[createBucket] bucket is duplicated. bucketName = " + bucketName);
								}
								// リトライ対象だがリトライ回数を超えた場合
								CloudStorageUtil.convertIOError(e);
							} else {
								if (logger.isInfoEnabled()) {
									logger.info(LogUtil.getRequestInfoStr(requestInfo) +
											"[createBucket] bucket is duplicated. bucketName = " + bucketName + ". retry...");
								}
							}
							// 名前を変更してリトライ
							bucketName = createBucketName(serviceName);

						} else {
							// 名前被りのリトライとタイムアウトリトライはリトライ回数を別にする。
							CloudStorageUtil.convertError(e);
							if (r >= numRetriesTimeout - 1) {
								// リトライ対象だがリトライ回数を超えた場合
								CloudStorageUtil.convertIOError(e);
							}
							CloudStorageUtil.sleep(waitMillisTimeout + r * 10);
						}
					}
				}

			} else {
				// 他スレッドでバケット登録中
				// 一定時間待って、再度バケット名をEntryから取得する。
				if (logger.isDebugEnabled()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
							"[createBucket] Another thread is creating the bucket...");
				}
				int numRetries = TaggingEnvUtil.getSystemPropInt(
						CloudStorageConst.STORAGE_WAITCREATEBUCKET_RETRY_COUNT,
						CloudStorageConst.STORAGE_WAITCREATEBUCKET_RETRY_COUNT_DEFAULT);
				int waitMillis = TaggingEnvUtil.getSystemPropInt(
						CloudStorageConst.STORAGE_WAITCREATEBUCKET_RETRY_WAITMILLIS,
						CloudStorageConst.STORAGE_WAITCREATEBUCKET_RETRY_WAITMILLIS_DEFAULT);
				for (int l = 0; l < numRetries; l++) {
					// まず一定時間スリープ
					CloudStorageUtil.sleep(waitMillis + l * 10);
					// コンテント情報格納Entryを読む
					EntryBase contentEntry = systemContext.getEntry(contentUri, false);
					bucketName = getBucketNameByEntry(contentEntry);
					if (bucketName != null) {
						logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
								"[createBucket] The bucket was created by another thread. loop = " + l);
						break;
					}
				}
			}

		} finally {
			if (lock) {
				// ロック解除
				cacheManager.deleteString(lockUri, systemContext);
			}
		}
		return bucketName;
	}
	
	/**
	 * バケット作成ロックURIを取得
	 *   /_lock/content/{サービス名}
	 * @param serviceName
	 * @return
	 */
	private String getLockUri(String serviceName) {
		StringBuilder sb = new StringBuilder();
		sb.append(CloudStorageConst.URI_LOCK_PREFIX);
		sb.append(serviceName);
		return sb.toString();
	}

	/**
	 * サービス名から秘密鍵を取得 (コンテンツ登録・取得用).
	 * @param namespace 名前空間
	 * @return 秘密鍵 (コンテンツ登録・取得用)
	 */
	private byte[] getContentSecret(String namespace) {
		return CloudStorageUtil.getContentSecret(namespace);
	}

	/**
	 * サービス名から秘密鍵を取得 (バケット登録用).
	 * @param serviceName サービス名
	 * @return 秘密鍵 (バケット登録用)
	 */
	private byte[] getBucketSecret(String serviceName) {
		return CloudStorageUtil.getBucketSecret(serviceName);
	}

	/**
	 * Google Cloud Storage用static情報を取得.
	 * @return Google Cloud Storage用static情報
	 */
	private CloudStorageEnv getStorageEnv() {
		return CloudStorageUtil.getStorageEnv();
	}

	/**
	 * URIの先頭の"/"を除去
	 * @param uri URI
	 * @return Google Cloud Storage用ファイル名
	 */
	private String editFileName(String uri) {
		return uri.substring(1);
	}

	/**
	 * Mapから値を取得.
	 * nameそのままで取得し、なければnameを小文字にして取得。
	 * Mapがnullの場合はnullを返却。
	 * @param map Map
	 * @param name 名前
	 * @return 値
	 */
	private String getValue(Map<String, String> map, String name) {
		if (map == null || name == null) {
			return null;
		}
		String val = map.get(name);
		if (StringUtils.isBlank(val)) {
			val = map.get(name.toLowerCase(Locale.ENGLISH));
		}
		return val;
	}

	/**
	 * コンテント情報Entryを生成
	 * @param bucketName バケット名
	 * @param serviceName サービス名
	 * @return コンテント情報Entry
	 */
	private EntryBase createContentEntry(String bucketName, String serviceName) {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// (2024.5.17) バケット名の設定箇所をエイリアス /_bucket/{バケット名} に修正。
		String uri = CloudStorageUtil.getContentUri(serviceName);
		entry.setMyUri(uri);
		String bucketAlias = CloudStorageUtil.getBucketUri(bucketName);
		entry.addAlternate(bucketAlias);
		return entry;
	}

	/**
	 * コンテント情報Entryを編集
	 * @param bucketName バケット名
	 * @param contentEntry コンテント情報Entry
	 */
	private void editContentEntry(String bucketName, EntryBase contentEntry) {
		// (2024.5.17) バケット名の設定箇所をエイリアス /_bucket/{バケット名} に修正。
		//contentEntry.title = bucketName;
		// 既存のバケットエイリアスが登録されていれば削除する。
		List<String> currentAliases = contentEntry.getAlternate();
		if (currentAliases != null) {
			for (String currentAlias : currentAliases) {
				if (currentAlias.startsWith(CloudStorageConst.URI_BUCKET_SLASH)) {
					contentEntry.removeAlternate(currentAlias);
				}
			}
		}
		// バケットエイリアスを登録
		String bucketAlias = CloudStorageUtil.getBucketUri(bucketName);
		contentEntry.addAlternate(bucketAlias);
	}

	/**
	 * デフォルトのバケット名を取得
	 * @param serviceName サービス名
	 * @return バケット名
	 */
	private String getDefaultBucketName(String serviceName) {
		StringBuilder sb = new StringBuilder();
		String prefix = getBucketPrefix();
		sb.append(prefix);
		sb.append(serviceName);
		return sb.toString();
	}

	/**
	 * サービス名 + ランダム値でバケット名を生成
	 * @param serviceName サービス名
	 * @return バケット名
	 */
	private String createBucketName(String serviceName) {
		StringBuilder sb = new StringBuilder();
		String prefix = getBucketPrefix();
		sb.append(prefix);
		sb.append(serviceName);
		sb.append("-");
		sb.append(StringUtils.zeroPadding(NumberingUtil.random(0, 999999), 6));
		return sb.toString();
	}

	/**
	 * バケット名の接頭辞を取得
	 * @return バケット名の接頭辞
	 */
	private String getBucketPrefix() {
		return CloudStorageUtil.getBucketPrefix();
	}

	/**
	 * コンテンツEntryを生成
	 * @param uri URI
	 * @param serviceName サービス名
	 * @return コンテンツEntry
	 */
	private EntryBase createStorageEntry(String uri, String serviceName) {
		EntryBase entry = TaggingEntryUtil.createEntry(serviceName);
		entry.setMyUri(uri);
		editStorageEntry(entry);
		return entry;
	}

	/**
	 * Entryにストレージ登録ありの情報を設定する
	 * @param entry Entry
	 */
	private void editStorageEntry(EntryBase entry) {
		entry.id = null;	// キャッシュとの不整合があることを考慮し、idはクリアする。
		if (entry.content != null) {
			if (isContentSrcStorage(entry)) {
				return;
			}
		} else {
			entry.content = new Content();
		}
		entry.content._$src = CloudStorageConst.CONTENT_SRC_STORAGE;
	}

	/**
	 * Entryからストレージ登録ありの情報を消去する
	 * @param entry Entry
	 * @return 更新の必要がある場合true
	 */
	private boolean editStorageDeleteEntry(EntryBase entry) {
		if (isContentSrcStorage(entry)) {
			entry.content._$src = "";
			return true;
		}
		return false;
	}

	/***
	 * ローカルキャッシュを使用するかどうかの判定.
	 * @return ローカルキャッシュを使用する場合true
	 */
	private boolean useStorageCache() {
		return !StringUtils.isBlank(CloudStorageUtil.getStorageCacheDir());
	}

	/**
	 * ストレージ容量チェック
	 * @param serviceName  サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	private void checkStorageTotalsize(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// サービスステータス取得
		ServiceBlogic serviceBlogic = new ServiceBlogic();
		String serviceStatus = serviceBlogic.getServiceStatus(serviceName, requestInfo, connectionInfo);
		if (Constants.SERVICE_STATUS_PRODUCTION.equals(serviceStatus)) {
			// productionサービスはチェックしない。
			return;
		}

		// 容量取得
		String uri = TaggingServiceUtil.getStorageTotalsizeUri(serviceName);
		SystemContext systemContext = new SystemContext(serviceName, requestInfo, connectionInfo);
		Long size = systemContext.getCacheLong(uri);
		long maxSize = TaggingEnvUtil.getSystemPropLong(CloudStorageConst.STORAGE_MAX_TOTALSIZE,
				CloudStorageConst.STORAGE_MAX_TOTALSIZE_DEFAULT);
		if (size != null && size > maxSize) {
			throw new PaymentRequiredException("The storage total size exceeded.");
		}
	}

	/**
	 * サービス初期設定時の処理.
	 * 実行ノードで指定されたサービスが初めて実行された際に呼び出されます。
	 * この処理は他のスレッドを排他して呼ばれます。
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	@Override
	public void settingService(String serviceName, RequestInfo requestInfo, ConnectionInfo connectionInfo)
			throws IOException, TaggingException {
		// Do nothing.
	}

	/**
	 * サービス情報クローズ.
	 * static領域にある指定されたサービスの情報を削除する。
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	@Override
	public void closeService(String serviceName, RequestInfo requestInfo, ConnectionInfo connectionInfo)
			throws IOException, TaggingException {
		CloudStorageEnv storageEnv = getStorageEnv();
		storageEnv.removeContentSecret(serviceName);
		storageEnv.removeBucketSecret(serviceName);
	}

	/**
	 * サービス初期設定に必要なURIリスト.
	 * サービス初期設定に必要なシステム管理サービスのURIリストを返却する。
	 * @param serviceName サービス名
	 * @return URIリスト
	 */
	@Override
	public List<String> getSettingEntryUrisBySystem(String serviceName) {
		List<String> uris = new ArrayList<>();
		uris.add(CloudStorageUtil.getContentUri(serviceName));
		return uris;
	}

	/**
	 * サービス初期設定に必要なFeed検索用URIリスト.
	 * サービス初期設定に必要なシステム管理サービスのFeed検索用URIリストを返却する。
	 * @param serviceName サービス名
	 * @return URIリスト
	 */
	@Override
	public List<String> getSettingFeedUrisBySystem(String serviceName) {
		return null;
	}

	/**
	 * サービス初期設定に必要なURIリスト.
	 * サービス初期設定に必要なシステム管理サービスのURIリストを返却する。
	 * @param serviceName サービス名
	 * @return URIリスト
	 */
	@Override
	public List<String> getSettingEntryUrisBySystem() {
		return null;
	}

	/**
	 * サービス初期設定に必要なFeed検索用URIリスト.
	 * サービス初期設定に必要なシステム管理サービスのFeed検索用URIリストを返却する。
	 * @param serviceName サービス名
	 * @return URIリスト
	 */
	@Override
	public List<String> getSettingFeedUrisBySystem() {
		return null;
	}

	/**
	 * サービス初期設定に共通して必要なURIリスト.
	 * サービス初期設定に共通して必要な自サービスのURIリストを返却する。
	 * @return URIリスト
	 */
	@Override
	public List<String> getSettingEntryUris() {
		return null;
	}

	/**
	 * サービス初期設定に共通して必要なURIリスト.
	 * サービス初期設定に共通して必要な自サービスのFeed検索用URIリストを返却する。
	 * @return URIリスト
	 */
	@Override
	public List<String> getSettingFeedUris() {
		return null;
	}
	
	/**
	 * ストレージへのアクセスログを出力するかどうかを取得.
	 * @return ストレージへのアクセスログを出力する場合true
	 */
	private boolean isEnableAccessLog() {
		return CloudStorageUtil.isEnableAccessLog() && logger.isDebugEnabled();
	}
	
	/**
	 * ストレージへのアクセスログを出力するかどうかを取得.
	 * @return ストレージへのアクセスログを出力する場合true
	 */
	private boolean isEnableAccessLogInfo() {
		return CloudStorageUtil.isEnableAccessLog() && logger.isInfoEnabled();
	}
	
	/**
	 * エントリー削除後のコンテンツ削除
	 * @param prevEntry 削除されたエントリー
	 * @param systemContext SystemContext
	 */
	@Override
	public void afterDeleteEntry(EntryBase prevEntry, SystemContext systemContext)
	throws IOException, TaggingException {
		// 削除されたエントリーのコンテンツがストレージに登録されていない場合は処理を抜ける。
		if (!isContentSrcStorage(prevEntry)) {
			return;
		}
		// エントリーを再度検索
		String uri = TaggingEntryUtil.getUriById(prevEntry.id);
		EntryBase entry = systemContext.getEntry(uri, false);
		// 再検索されたエントリーが以下の場合は、コンテンツを削除する。
		// ・エントリーが存在しない (削除されたままの状態)
		// ・エントリーが存在するが「content.___src="_storage"」項目が無い
		if (entry == null || !isContentSrcStorage(entry)) {
			// コンテンツ削除
			String serviceName = systemContext.getServiceName();
			RequestInfo requestInfo = systemContext.getRequestInfo();
			ConnectionInfo connectionInfo = systemContext.getConnectionInfo();
			String namespace = systemContext.getNamespace();
			deleteFromStorage(uri, namespace, serviceName, requestInfo, connectionInfo);
			// ローカルキャッシュからもコンテンツ削除
			if (useStorageCache()) {
				LocalCacheManager localCacheManager = new LocalCacheManager();
				localCacheManager.deleteFromCache(uri, serviceName, namespace,
						requestInfo, connectionInfo);
			}
		}
	}
	
	/**
	 * コンテンツがストレージに格納されているかどうか取得.
	 *  content.___src="_storage" の場合trueを返す。
	 * @param entry エントリー
	 * @return コンテンツがストレージに格納されている場合true
	 */
	private boolean isContentSrcStorage(EntryBase entry) {
		if (entry != null && entry.content != null &&
				CloudStorageConst.CONTENT_SRC_STORAGE.equals(entry.content._$src)) {
			return true;
		}
		return false;
	}

	/**
	 * 署名付きURLを取得.
	 * @param method コンテンツ取得の場合GET、コンテンツ登録の場合PUT、自動採番登録の場合POST
	 * @param uri キー
	 * @param headers リクエストヘッダ
	 * @param reflexContext ReflexContext
	 * @return 署名付きURL
	 */
	public FeedBase getSignedUrl(String method, String uri, Map<String, String> headers, 
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		String objectName = uri.substring(1);	// 先頭のスラッシュを除く
		if (logger.isDebugEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[getSignedUrl] start. method = ");
			sb.append(method);
			sb.append(" objectName = ");
			sb.append(objectName);
			logger.debug(sb.toString());
		}
		// バケット名取得
		String bucket = getBucketName(true, namespace, serviceName, requestInfo,
				connectionInfo);
		if (bucket == null) {
			logger.warn(LogUtil.getRequestInfoStr(requestInfo) +
					"[getSignedUrl] bucket is null.");
			throw new IOException("The bucket could not created.");
		}

		// Define Resource
		BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucket, objectName)).build();
		HttpMethod httpMethod = getHttpMethod(method);
		// リクエストヘッダはPUTの場合のContent-Typeだけ指定する
		Map<String, String> tmpHeaders = new HashMap<>();
		if (Constants.PUT.equals(method)) {
			// application/octet-stream固定
			tmpHeaders.put(ReflexServletConst.HEADER_CONTENT_TYPE, 
					ReflexServletConst.CONTENT_TYPE_APPLICATION_OCTET_STREAM);
			// Content-Dispositionにファイル名指定があれば設定
			String contentDisposition = getValue(headers, ReflexServletConst.HEADER_CONTENT_DISPOSITION);
			if (!StringUtils.isBlank(contentDisposition) &&
					contentDisposition.startsWith(ReflexServletConst.HEADER_VALUE_ATTACHMENT_FILENAME_PREFIX)) {
				tmpHeaders.put(ReflexServletConst.HEADER_CONTENT_DISPOSITION, contentDisposition);
			}
		}

		String urlStr = null;
		// リトライ回数
		int numRetries = CloudStorageUtil.getStorageUploadRetryCount();
		int waitMillis = CloudStorageUtil.getStorageUploadRetryWaitmillis();
		int expireMin = TaggingEnvUtil.getPropInt(serviceName, 
				CloudStorageSettingConst.STORAGE_SIGNEDURL_EXPIRE_MIN, 
				CloudStorageConst.STORAGE_SIGNEDURL_EXPIRE_MIN_DEFAULT);
		for (int r = 0; r <= numRetries; r++) {
			try {
				// Storage接続インタフェース取得
				CloudStorageConnection storage = getStorageContent(namespace, connectionInfo);
				// 署名付きURLの取得
				URL url = storage.signUrl(
								blobInfo,
								expireMin,
								TimeUnit.MINUTES,
								Storage.SignUrlOption.httpMethod(httpMethod),
								Storage.SignUrlOption.withExtHeaders(tmpHeaders),
								Storage.SignUrlOption.withV4Signature());
				
				urlStr = url.toString();
				break;

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
		
		FeedBase feed = TaggingEntryUtil.createFeed(serviceName);
		feed.title = urlStr;
		feed.subtitle = uri;
		return feed;
	}
	
	private HttpMethod getHttpMethod(String method) {
		if (Constants.PUT.equals(method)) {
			return HttpMethod.PUT;
		} else if (Constants.DELETE.equals(method)) {
			return HttpMethod.DELETE;
		} else {
			return HttpMethod.GET;
		}
	}

	/**
	 * エントリー更新後に呼び出される処理
	 * @param updatedInfos 更新情報
	 * @param reflexContext ReflexContext
	 */
	@Override
	public void doAfterCommit(List<UpdatedInfo> updatedInfos, ReflexContext reflexContext) 
	throws IOException, TaggingException {
		CloudStorageCorsManager corsManager = new CloudStorageCorsManager();
		corsManager.doAfterCommit(updatedInfos, reflexContext, this);
	}
	
	/**
	 * サービス登録時の処理
	 * @param newServiceName サービス名
	 * @param auth 実行ユーザ認証情報
	 * @param reflexContext システム管理サービスのSystemContext
	 */
	@Override
	public void doCreateService(String newServiceName, ReflexAuthentication auth, 
			SystemContext systemContext)
	throws IOException, TaggingException {
		// システム管理サービスにバケット情報が残っている場合、初期化する。
		if (logger.isDebugEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(systemContext.getRequestInfo()) +
					"[doCreateService] start. newServiceName=" + newServiceName);
		}
		
		// データストアからバケット名を取得
		String systemService = systemContext.getServiceName();
		String contentUri = CloudStorageUtil.getContentUri(newServiceName);
		EntryBase contentEntry = systemContext.getEntry(contentUri, true);
		String bucketName = getBucketNameByEntry(contentEntry);
		if (bucketName != null) {
			// バケットエイリアスを削除
			if (logger.isDebugEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(systemContext.getRequestInfo()));
				sb.append("[doCreateService] remove bucketAlias. newServiceName=");
				sb.append(newServiceName);
				sb.append(" bucketName=");
				sb.append(bucketName);
				logger.debug(sb.toString());
			}
			String bucketAlias = CloudStorageUtil.getBucketUri(bucketName);
			EntryBase updEntry = TaggingEntryUtil.createEntry(systemService);
			updEntry.setMyUri(contentUri);
			updEntry.addAlternate(bucketAlias);
			FeedBase updFeed = TaggingEntryUtil.createFeed(systemService, updEntry);
			systemContext.removeAlias(updFeed);
		}
	}

}
