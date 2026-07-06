package jp.reflexworks.taggingservice.blogic;

import java.util.HashMap;
import java.util.Map;

import jp.reflexworks.taggingservice.conn.ReflexConnection;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * シークレットのキャッシュ情報
 * リクエストで持ち回りたいためConnectionオブジェクトとする。
 */
public class SecretCacheConnection implements ReflexConnection<Map<String, String>> {
	
	/** secret cache for Redis */
	private Map<String, String> secretCacheMap = new HashMap<>();

	@Override
	public Map<String, String> getConnection() {
		return secretCacheMap;
	}

	@Override
	public void close() {
		// Do nothing.
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
		sb.append(SecretConst.SECRET_STATIC_KEY_DELIMITER);
		sb.append(StringUtils.null2blank(versionId));
		return sb.toString();
	}

}
