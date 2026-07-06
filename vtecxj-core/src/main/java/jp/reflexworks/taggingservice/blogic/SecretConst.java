package jp.reflexworks.taggingservice.blogic;

/**
 * SecretManager キャッシュの定数クラス
 */
public interface SecretConst {
	
	/** 設定 : シークレット取得処理のアクセスログを出力するかどうか */
	public static final String SECRET_ENABLE_ACCESSLOG = "_secret.enable.accesslog";
	
	/** Redisキー : Secret latest */
	static final String URI_SECRET_LATEST_PREFEX = "/_secret/latest/";
	/** Redisキー : Secret latest のキーリスト取得 */
	static final String URI_SECRET_LATEST_KEYLIST = URI_SECRET_LATEST_PREFEX + "*";
	/** Redisキー : staticオブジェクトのMapキーのバージョン指定区切り文字 */
	static final String SECRET_STATIC_KEY_DELIMITER = "$";

	/** コネクション名 */
	public static final String CONN_NAME_SECRET = "_secret";

	/** メモリ上のstaticオブジェクト格納キー : シークレットのバージョンと値 */
	static final String STATIC_NAME_SECRET_STATIC = "_secret_static";

	/** シークレットに値が登録されていない場合の値 */
	static final String SECRET_NOVALUE = "*null*";
	/** シークレットに値が登録されていない場合のバージョンID */
	static final String SECRET_VERSIONID_NOVALUE = "-1";

	/** URLパラメータ : モニター対象キー */
	static final String PARAM_MONITOR_KEY = "key";
	/** URLパラメータ : モニター対象バージョン */
	static final String PARAM_MONITOR_VERSION = "version";

}
