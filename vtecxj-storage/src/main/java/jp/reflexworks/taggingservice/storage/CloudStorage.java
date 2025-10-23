package jp.reflexworks.taggingservice.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.gax.paging.Page;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.SignUrlOption;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;

/**
 * Storageのラップクラス
 */
public class CloudStorage {

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(CloudStorage.class);

	/** Cloud Storage 接続クラス */
	private Storage storage;
	
	/**
	 * コンストラクタ
	 * @param storage Storage
	 */
	CloudStorage(Storage storage) {
		this.storage = storage;
	}

	/**
	 * Storage接続インタフェースを取得.
	 * @param secret 秘密鍵
	 * @return Storage接続インタフェース
	 */
	public static CloudStorage getStorage(byte[] secret) 
	throws CloudStorageException, IOException {
		StorageOptions storageOptions = null;
		try {
			if (secret == null) {
				if (CloudStorageUtil.isEnableAccessLog()) {
					logger.info("[getStorage] secret is not used.");
				}
				storageOptions = StorageOptions.getDefaultInstance();
			} else {
				if (CloudStorageUtil.isEnableAccessLog()) {
					logger.info("[getStorage] secret is used.");
				}
				ByteArrayInputStream bin = new ByteArrayInputStream(secret);
				GoogleCredentials credentials = GoogleCredentials.fromStream(bin);
				storageOptions = StorageOptions.newBuilder().setCredentials(credentials).build();
			}
			String command = "getStorage";
			long startTime = 0;
			if (CloudStorageUtil.isEnableAccessLog()) {
				logger.info(CloudStorageUtil.getStartLog(command));
				startTime = new Date().getTime();
			}
			Storage storage = storageOptions.getService();
			if (CloudStorageUtil.isEnableAccessLog()) {
				logger.info(CloudStorageUtil.getEndLog(command, startTime));
				startTime = new Date().getTime();
			}
			return new CloudStorage(storage);

		} catch (StorageException e) {
			if (logger.isInfoEnabled()) {
				logger.info("[getStorage] StorageException", e);
			}
			throw CloudStorageUtil.convertException(e);
		}
	}
	
	/**
	 * バケット生成
	 * @param bucketInfo バケット情報
	 * @return Blob
	 */
	public CloudStorageBucket create(BucketInfo bucketInfo) 
	throws CloudStorageException {
		try {
			String command = "create";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.info(CloudStorageUtil.getStartLog(command, bucketInfo));
				startTime = new Date().getTime();
			}
			Bucket ret = storage.create(bucketInfo);
			if (isEnableAccessLog()) {
				logger.info(CloudStorageUtil.getEndLog(command, bucketInfo, startTime));
				startTime = new Date().getTime();
			}
			return new CloudStorageBucket(ret);
		} catch (StorageException e) {
			throw CloudStorageUtil.convertException(e);
		}
	}
	
	/**
	 * アップロード
	 * @param blobInfo Blob情報
	 * @param data ファイルの内容
	 * @return Blob
	 */
	public CloudStorageBlob create(BlobInfo blobInfo, byte[] data) 
	throws CloudStorageException {
		try {
			String command = "create";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.info(CloudStorageUtil.getStartLog(command, blobInfo));
				startTime = new Date().getTime();
			}
			Blob ret = storage.create(blobInfo, data);
			if (isEnableAccessLog()) {
				logger.info(CloudStorageUtil.getEndLog(command, blobInfo, startTime));
				startTime = new Date().getTime();
			}
			return new CloudStorageBlob(ret);
		} catch (StorageException e) {
			throw CloudStorageUtil.convertException(e);
		}
	}

	/**
	 * ダウンロード
	 * @param blobId Blob ID
	 * @return Blob
	 */
	public CloudStorageBlob get(BlobId blobId) throws CloudStorageException {
		try {
			String command = "get";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.info(CloudStorageUtil.getStartLog(command, blobId));
				startTime = new Date().getTime();
			}
			Blob ret = storage.get(blobId);
			if (isEnableAccessLog()) {
				logger.info(CloudStorageUtil.getEndLog(command, blobId, startTime));
				startTime = new Date().getTime();
			}
			return new CloudStorageBlob(ret);
		} catch (StorageException e) {
			throw CloudStorageUtil.convertException(e);
		}
	}
	
	/**
	 * 削除
	 * @param blobId Blob ID
	 * @return 削除した場合true
	 */
	public boolean delete(BlobId blobId) throws CloudStorageException {
		try {
			String command = "delete";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.info(CloudStorageUtil.getStartLog(command, blobId));
				startTime = new Date().getTime();
			}
			boolean ret = storage.delete(blobId);
			if (isEnableAccessLog()) {
				logger.info(CloudStorageUtil.getEndLog(command, blobId, startTime));
				startTime = new Date().getTime();
			}
			return ret;
		} catch (StorageException e) {
			throw CloudStorageUtil.convertException(e);
		}
	}
	
	/**
	 * 署名付きURL取得
	 * @param blobInfo BlobInfo
	 * @param duration 署名の有効期限
	 * @param unit 署名の有効期限時間単位
	 * @param options オプション
	 * @return URL
	 */
	public URL signUrl(BlobInfo blobInfo, long duration, TimeUnit unit, SignUrlOption... options) 
	throws CloudStorageException {
		try {
			String command = "signUrl";
			BlobId blobId = blobInfo.getBlobId();
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.info(CloudStorageUtil.getStartLog(command, blobId));
				startTime = new Date().getTime();
			}
			URL ret = storage.signUrl(blobInfo, duration, unit, options);
			if (isEnableAccessLog()) {
				logger.info(CloudStorageUtil.getEndLog(command, blobId, startTime));
				startTime = new Date().getTime();
			}
			return ret;
		} catch (StorageException e) {
			throw CloudStorageUtil.convertException(e);
		}
	}
	
	/**
	 * Bucketオブジェクトを取得.
	 * @param bucket Bucket名
	 * @param options Bucket取得オプション
	 * @return Bucketオブジェクト
	 */
	public CloudStorageBucket get(String bucket, Storage.BucketGetOption... options) 
	throws CloudStorageException {
		try {
			return new CloudStorageBucket(storage.get(bucket, options));
		} catch (StorageException e) {
			throw CloudStorageUtil.convertException(e);
		}
	}
	
	/**
	 * Bucketリストを取得.
	 * (注)このメソッドが返すBucketはcloudStorage純正のオブジェクトなので、使用先でStorageExceptionのcatchを行うこと。
	 * @param options BucketListOption
	 * @return Bucketリスト
	 */
	public Page<Bucket> list(Storage.BucketListOption... options)
	throws CloudStorageException {
		try {
			return storage.list(options);
		} catch (StorageException e) {
			throw CloudStorageUtil.convertException(e);
		}
	}

	/**
	 * ストレージのアクセスログを出力するかどうか
	 * @return ストレージのアクセスログを出力する場合true;
	 */
	private boolean isEnableAccessLog() {
		return CloudStorageUtil.isEnableAccessLog();
	}

}
