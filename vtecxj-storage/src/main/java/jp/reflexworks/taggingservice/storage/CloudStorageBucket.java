package jp.reflexworks.taggingservice.storage;

import java.util.List;

import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Cors;

/**
 * Bucketのラップクラス
 */
public class CloudStorageBucket {
	
	/** Bucket */
	private Bucket bucket;
	
	/**
	 * コンストラクタ
	 * @param bucket Bucket
	 */
	CloudStorageBucket(Bucket bucket) {
		this.bucket = bucket;
	}
	
	/**
	 * BucketのCORSを取得.
	 * @return BucketのCORS
	 * @throws CloudStorageException StorageExceptionのラップ
	 */
	public List<Cors> getCors() {
		if (bucket == null) {
			return null;
		}
		return bucket.getCors();
	}
	
	/**
	 * BucketのBuilderを生成.
	 * (注)このメソッドで作成されたBuilderはCloud Storage純正のクラスなので、
	 *     生成後のBuilderのメソッドの実行時にはStorageExceptionをcatchすること。
	 * @return BucketのBuilder
	 */
	public Bucket.Builder toBuilder() {
		return bucket.toBuilder();
	}


}
