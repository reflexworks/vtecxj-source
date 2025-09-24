package jp.reflexworks.taggingservice.storage;

import java.net.URL;
import java.util.concurrent.TimeUnit;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.SignUrlOption;

import jp.reflexworks.taggingservice.conn.ReflexConnection;

/**
 * Storageコネクション.
 */
public class CloudStorageConnection implements ReflexConnection<CloudStorage> {
	
	/** Google Cloud Storage コネクション */
	private CloudStorage storage;

	/**
	 * コンストラクタ.
	 * @param storage Cloud Storageコネクション
	 */
	public CloudStorageConnection(CloudStorage storage) {
		this.storage = storage;
	}
	
	/**
	 * Cloud Storageコネクションを取得.
	 * @return Cloud Storageコネクション
	 */
	public CloudStorage getConnection() {
		return storage;
	}
	
	/**
	 * クローズ処理.
	 */
	public void close() {
		// Storageオブジェクトにcloseメソッドはない。
		// Do nothing.
	}
	
	/**
	 * バケット生成
	 * @param bucketInfo バケット情報
	 * @return Blob
	 */
	public CloudStorageBucket create(BucketInfo bucketInfo) 
	throws CloudStorageException {
		return storage.create(bucketInfo);
	}
	
	/**
	 * アップロード
	 * @param blobInfo Blob情報
	 * @param data ファイルの内容
	 * @return Blob
	 */
	public CloudStorageBlob create(BlobInfo blobInfo, byte[] data) 
	throws CloudStorageException {
		return storage.create(blobInfo, data);
	}

	/**
	 * ダウンロード
	 * @param blobId Blob ID
	 * @return Blob
	 */
	public CloudStorageBlob get(BlobId blobId) throws CloudStorageException {
		return storage.get(blobId);
	}
	
	/**
	 * 削除
	 * @param blobId Blob ID
	 * @return 削除した場合true
	 */
	public boolean delete(BlobId blobId) throws CloudStorageException {
		return storage.delete(blobId);
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
		return storage.signUrl(blobInfo, duration, unit, options);
	}
	
	/**
	 * Bucketオブジェクトを取得.
	 * @param bucket Bucket名
	 * @param options Bucket取得オプション
	 * @return Bucketオブジェクト
	 */
	public CloudStorageBucket get(String bucket, Storage.BucketGetOption... options) 
	throws CloudStorageException {
		return storage.get(bucket, options);
	}

}
