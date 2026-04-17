package jp.reflexworks.taggingservice.blogic;

import java.util.HashMap;
import java.util.Map;

import jp.reflexworks.taggingservice.conn.ReflexConnection;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * シークレットのキャッシュ情報
 * リクエストで持ち回りたいためConnectionオブジェクトとする。
 */
public class SecretCacheConnection implements ReflexConnection<Long> {
	
	/** reloadSecret for Redis */
	private long reloadSecretTime = 0L;
	/** secret cache for Redis */
	private Map<String, String> secretCacheMap = new HashMap<>();

	@Override
	public Long getConnection() {
		// TODO 自動生成されたメソッド・スタブ
		return getReloadSecretTime();
	}

	@Override
	public void close() {
		// TODO 自動生成されたメソッド・スタブ
	}
	
	/**
	 * コンストラクタ
	 * @param reloadSecretTime reloadsecretの実行日時(エポック秒)
	 */
	SecretCacheConnection(long reloadSecretTime) {
		this.reloadSecretTime = reloadSecretTime;
	}

	/**
	 * reloadsecretの実行日時(エポック秒)を取得
	 * @return 
	 */
	public long getReloadSecretTime() {
		return reloadSecretTime;
	}
	
	/**
	 * このスレッド中に取得したシークレットの値を返却
	 * @param name シークレット名
	 * @param versionId バージョン
	 * @return このスレッド中に取得したシークレットの値
	 */
	public String getSecretValue(String secretId, String versionId) {
		String secretCacheUri = getMapKey(secretId, versionId);
		return secretCacheMap.get(secretCacheUri);
	}
	
	/**
	 * このスレッド中に取得したシークレットの値をMapに登録
	 * @param name シークレット名
	 * @param versionId バージョン
	 * @param secretValue シークレットの値
	 */
	public void setSecretValue(String secretId, String versionId, String secretValue) {
		String secretCacheUri = getMapKey(secretId, versionId);
		secretCacheMap.put(secretCacheUri, secretValue);
	}
	
	/**
	 * シークレットの値格納Mapのキーを取得
	 * @param name シークレット名
	 * @param versionId バージョン
	 * @return シークレットの値格納Mapのキー
	 */
	private String getMapKey(String secretId, String versionId) {
		StringBuilder sb = new StringBuilder();
		sb.append(secretId);
		sb.append(SecretConst.URI_SECRET_CACHE_DELIMITER);
		sb.append(StringUtils.null2blank(versionId));
		return sb.toString();
	}

}
