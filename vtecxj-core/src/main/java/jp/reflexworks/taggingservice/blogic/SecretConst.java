package jp.reflexworks.taggingservice.blogic;

/**
 * SecretManager キャッシュの定数クラス
 */
public interface SecretConst {
	
	/** 設定 : シークレット取得処理のアクセスログを出力するかどうか */
	public static final String SECRET_ENABLE_ACCESSLOG = "_secret.enable.accesslog";
	
	/** Redisキー : Secret Cache */
	static final String URI_SECRET_CACHE_PREFEX = "/_secret/cache/";
	/** Redisキー : reloadsecret実行日時 */
	static final String URI_SECRET_RELOADSECRET = "/_secret/reloadsecret";
	/** Redisキー : Secret Cache のバージョン指定区切り文字 */
	static final String URI_SECRET_CACHE_DELIMITER = "$";

	/** コネクション名 */
	public static final String CONN_NAME_SECRET = "_secret";

}
