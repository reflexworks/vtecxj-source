package jp.reflexworks.taggingservice.storage;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.cloud.storage.Storage;

/**
 * Google Cloud Storage用staticクラス
 */
public class CloudStorageEnv {
	
	/** 
	 * Cloud Storage 接続オブジェクト.
	 * スレッドセーフのためシングルトンで持つ。
	 */
	private Storage storage;

	/** バケットの接頭辞 */
	private String bucketPrefix;

	/**
	 * キャッシュ書き込みロック.
	 * キー:ファイルパス、値:AtomicBoolean
	 */
	private ConcurrentMap<String, AtomicBoolean> cacheLockMap =
			new ConcurrentHashMap<String, AtomicBoolean>();

	/**
	 * Cloud Storage 接続オブジェクトを取得.
	 * @return Cloud Storage 接続オブジェクト
	 */
	public Storage getStorage() {
		return storage;
	}

	/**
	 * Cloud Storage 接続オブジェクトを格納.
	 * @param storage loud Storage 接続オブジェクト
	 */
	void setStorage(Storage storage) {
		this.storage = storage;
	}

	/**
	 * バケット名の接頭辞を取得.
	 * @return バケット名の接頭辞
	 */
	public String getBucketPrefix() {
		return bucketPrefix;
	}

	/**
	 * バケット名の接頭辞を設定.
	 * @param bucketPrefix バケット名の接頭辞
	 */
	void setBucketPrefix(String bucketPrefix) {
		this.bucketPrefix = bucketPrefix;
	}

	/**
	 * キャッシュ書き込みロックの状態を取得.
	 * @param filepath コンテンツのローカルファイルパス
	 * @return キャッシュ書き込みロックの状態。trueの場合ロック中。
	 */
	public boolean isCacheLock(String filepath) {
		AtomicBoolean lock = cacheLockMap.get(filepath);
		if (lock != null) {
			return lock.get();
		}
		return false;
	}

	/**
	 * キャッシュ書き込みロックを取得.
	 * @param filepath コンテンツのローカルファイルパス
	 * @return キャッシュ書き込みロックを取得できた場合true
	 */
	public boolean getCacheLock(String filepath) {
		AtomicBoolean lock = cacheLockMap.get(filepath);
		boolean ret = false;
		if (lock != null) {
			ret = lock.compareAndSet(false, true);
		} else {
			lock = new AtomicBoolean(true);
			AtomicBoolean tmp = cacheLockMap.putIfAbsent(filepath, lock);
			ret = tmp == null;
		}
		return ret;
	}

	/**
	 * キャッシュ書き込みロックを解放.
	 * @param filepath コンテンツのローカルファイルパス
	 */
	public void releaseCacheLock(String filepath) {
		cacheLockMap.remove(filepath);
	}

}
