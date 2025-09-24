package jp.reflexworks.taggingservice.storage;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import jp.reflexworks.taggingservice.env.TaggingEnvUtil;

/**
 * Google Cloud Storage用staticクラス
 */
public class CloudStorageEnv {

	// 全てのサービスの秘密鍵を保持するわけではなく、一度読んだものをキャッシュするような考え方。

	/**
	 * 秘密鍵ファイル.
	 * キー:名前空間、値:秘密鍵ファイル
	 */
	private ConcurrentMap<String, byte[]> contentSecrets =
			new ConcurrentHashMap<String, byte[]>();

	/**
	 * 秘密鍵ファイル.
	 * キー:名前空間、値:秘密鍵ファイル
	 */
	private ConcurrentMap<String, byte[]> bucketSecrets =
			new ConcurrentHashMap<String, byte[]>();

	/** バケットの接頭辞 */
	private String bucketPrefix;

	/**
	 * キャッシュ書き込みロック.
	 * キー:ファイルパス、値:AtomicBoolean
	 */
	private ConcurrentMap<String, AtomicBoolean> cacheLockMap =
			new ConcurrentHashMap<String, AtomicBoolean>();

	/**
	 * 秘密鍵ファイルを取得 (コンテンツ登録・取得用).
	 * サービスに対応する秘密鍵が存在しない場合、システム管理サービスの秘密鍵を返却する。
	 * @param namespace 名前空間
	 * @return 秘密鍵ファイル (コンテンツ登録・取得用)
	 */
	public byte[] getContentSecret(String namespace) {
		if (namespace == null) {
			return null;
		}
		if (contentSecrets.containsKey(namespace)) {
			return contentSecrets.get(namespace);
		}
		return contentSecrets.get(TaggingEnvUtil.getSystemService());
	}

	/**
	 * 秘密鍵ファイルをセット (コンテンツ登録・取得用)
	 * @param serviceName サービス名
	 * @param secret 秘密鍵ファイル (コンテンツ登録・取得用)
	 */
	void setContentSecret(String serviceName, byte[] secret) {
		if (serviceName == null) {
			return;
		}
		if (secret != null) {
			this.contentSecrets.put(serviceName, secret);
		} else {
			this.contentSecrets.remove(serviceName);
		}
	}

	/**
	 * 秘密鍵ファイルを削除 (コンテンツ登録・取得用).
	 * @param serviceName サービス名
	 */
	void removeContentSecret(String serviceName) {
		if (serviceName == null) {
			return;
		}
		if (contentSecrets.containsKey(serviceName)) {
			contentSecrets.remove(serviceName);
		}
	}

	/**
	 * 秘密鍵ファイルを取得 (バケット登録用).
	 * サービスに対応する秘密鍵が存在しない場合、システム管理サービスの秘密鍵を返却する。
	 * @param serviceName サービス名
	 * @return 秘密鍵ファイル (バケット登録用)
	 */
	public byte[] getBucketSecret(String serviceName) {
		if (serviceName == null) {
			return null;
		}
		if (bucketSecrets.containsKey(serviceName)) {
			return bucketSecrets.get(serviceName);
		}
		return bucketSecrets.get(TaggingEnvUtil.getSystemService());
	}

	/**
	 * 秘密鍵ファイルをセット (バケット登録用)
	 * @param serviceName サービス名
	 * @param secret 秘密鍵ファイル (バケット登録用)
	 */
	void setBucketSecret(String serviceName, byte[] secret) {
		if (serviceName == null) {
			return;
		}
		if (secret != null) {
			this.bucketSecrets.put(serviceName, secret);
		} else {
			this.bucketSecrets.remove(serviceName);
		}
	}

	/**
	 * 秘密鍵ファイルを削除 (バケット登録用).
	 * @param serviceName サービス名
	 */
	public void removeBucketSecret(String serviceName) {
		if (serviceName == null) {
			return;
		}
		if (bucketSecrets.containsKey(serviceName)) {
			bucketSecrets.remove(serviceName);
		}
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
