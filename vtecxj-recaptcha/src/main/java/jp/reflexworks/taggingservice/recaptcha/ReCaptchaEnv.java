package jp.reflexworks.taggingservice.recaptcha;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import jp.reflexworks.taggingservice.env.TaggingEnvUtil;

/**
 * reCAPTCHA Enterprise用staticクラス
 */
public class ReCaptchaEnv {

	/**
	 * 秘密鍵ファイル.
	 * キー:名前空間、値:秘密鍵ファイル
	 */
	private ConcurrentMap<String, byte[]> secrets =
			new ConcurrentHashMap<String, byte[]>();

	/**
	 * キャッシュ書き込みロック.
	 * キー:ファイルパス、値:AtomicBoolean
	 */
	private ConcurrentMap<String, AtomicBoolean> cacheLockMap =
			new ConcurrentHashMap<String, AtomicBoolean>();

	/**
	 * 秘密鍵ファイルを取得.
	 * サービスに対応する秘密鍵が存在しない場合、システム管理サービスの秘密鍵を返却する。
	 * @param namespace 名前空間
	 * @return 秘密鍵ファイル
	 */
	public byte[] getSecret(String namespace) {
		if (namespace == null) {
			return null;
		}
		if (secrets.containsKey(namespace)) {
			return secrets.get(namespace);
		}
		return secrets.get(TaggingEnvUtil.getSystemService());
	}

	/**
	 * 秘密鍵ファイルをセット.
	 * @param serviceName サービス名
	 * @param secret 秘密鍵ファイル
	 */
	void setSecret(String serviceName, byte[] secret) {
		if (serviceName == null) {
			return;
		}
		if (secret != null) {
			this.secrets.put(serviceName, secret);
		} else {
			this.secrets.remove(serviceName);
		}
	}

	/**
	 * 秘密鍵ファイルを削除 .
	 * @param serviceName サービス名
	 */
	void removeSecret(String serviceName) {
		if (serviceName == null) {
			return;
		}
		if (secrets.containsKey(serviceName)) {
			secrets.remove(serviceName);
		}
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
