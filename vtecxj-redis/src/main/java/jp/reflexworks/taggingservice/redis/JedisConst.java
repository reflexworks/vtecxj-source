package jp.reflexworks.taggingservice.redis;

/**
 * Redis 定数クラス.
 */
public final class JedisConst {

	/** プロパティキー : _redis.host.master={Masterのhost}:{port} 形式で指定.  */
	public static final String PROP_REDIS_HOST_MASTER = "_redis.host.master";
	/** プロパティキー : _redis.host.master={Slaveのhost}:{port} 形式で指定.  */
	public static final String PROP_REDIS_HOST_SLAVE = "_redis.host.slave";

	/** プロパティキー : Redisパスワードがある場合に指定. */
	public static final String PROP_REDIS_PASSWORD = "_redis.password";

	/** プロパティキー : Redis最大待ち時間(ミリ秒). */
	// Default MaxWaitMillis is -1.
	public static final String PROP_REDIS_MAX_WAITMILLIS =
			"_redis.max.waitmillis";
	/** プロパティキー : Redis最大コネクション数. */
	// MaxTotal - JedisPool blocks after getting 8 connections
	public static final String PROP_REDIS_MAX_TOTAL = "_redis.max.total";
	/** Redisサーバ障害とみなす、全体でのJedisConnectionException発生回数 */
	public static final String PROP_REDIS_SERVERDOWN_ERRORCOUNT = "_redis.serverdown.errorcount";
	/** エラー時の総リトライ回数 */
	public static final String PROP_REDIS_RETRY_COUNT = "_redis.retry.count";
	/** エラーリトライ時の待ち時間(ミリ秒) */
	public static final String PROP_REDIS_RETRY_WAITMILLIS = "_redis.retry.waitmillis";
	/** コネクション取得エラー時の総リトライ回数 */
	public static final String PROP_REDIS_GETCONNECTION_RETRY_COUNT = "_redis.getconnection.retry.count";
	/** コネクション取得エラーリトライ時の待ち時間(ミリ秒) */
	public static final String PROP_REDIS_GETCONNECTION_RETRY_WAITMILLIS = "_redis.getconnection.retry.waitmillis";
	/** SIDの長さ */
	public static final String PROP_SID_LENGTH = "_sid.length";
	/** SID発行時の重複総リトライ回数 */
	public static final String PROP_SID_RETRY_COUNT = "_sid.retry.count";
	/** 設定 : Redisへのアクセスログを出力するかどうか */
	public static final String PROP_REDIS_ENABLE_ACCESSLOG = "_redis.enable.accesslog";
	/** 設定 : Redisプールの再生成を行わない期間(秒) */
	public static final String PROP_REDIS_WITHOUT_RECREATE_SEC = "_redis.without.recreate.sec";
	/** 設定 : Redisの再起動ロック失敗をクリアする回数 */
	public static final String PROP_REDIS_CLEAR_LOCKFAILED_NUM = "_redis.clear.lockfailed.num";

	/** ポート区切り文字. */
	public static final String PORT_DELIMITER = ":";

	/** プロパティデフォルト値 : Redisタイムアウト(秒). */
	public static final int REDIS_TIMEOUT_DEFAULT = 10;
	/** プロパティデフォルト値 : Redis最大待ち時間(ミリ秒). */
	public static final int REDIS_MAX_WAITMILLIS_DEFAULT = 10000;
	/** プロパティデフォルト値 : Redis最大コネクション数. */
	public static final int REDIS_MAX_TOTAL_DEFAULT = 8;
	/** プロパティデフォルト値 : Redisサーバ障害とみなす、全体でのJedisConnectionException発生回数 */
	public static final int REDIS_SERVERDOWN_ERRORCOUNT_DEFAULT = 2;
	/** プロパティデフォルト値 : エラー時の総リトライ回数 */
	public static final int REDIS_RETRY_COUNT_DEFAULT = 2;
	/** プロパティデフォルト値 : エラーリトライ時の待ち時間(ミリ秒) */
	public static final int REDIS_RETRY_WAITMILLIS_DEFAULT = 200;
	/** プロパティデフォルト値 : コネクション取得エラー時の総リトライ回数 */
	public static final int REDIS_GETCONNECTION_RETRY_COUNT_DEFAULT = 2;
	/** プロパティデフォルト値 : コネクション取得エラーリトライ時の待ち時間(ミリ秒) */
	public static final int REDIS_GETCONNECTION_RETRY_WAITMILLIS_DEFAULT = 200;
	/** プロパティデフォルト値 : SIDの長さ */
	public static final int SID_LENGTH_DEFAULT = 48;
	/** プロパティデフォルト値 : SID発行時の重複総リトライ回数 */
	public static final int SID_RETRY_COUNT_DEFAULT = 50;
	/** プロパティデフォルト値 : Redisプールの再生成を行わない期間(秒) */
	public static final int REDIS_WITHOUT_RECREATE_SEC_DEFAULT = 180;
	/** プロパティデフォルト値 : Redisの再起動ロック失敗をクリアする回数 */
	public static final int REDIS_CLEAR_LOCKFAILED_NUM_DEFAULT = 400;

	// エントリー、allocids、自動採番関連
	/** Redisのキープリフィックス : セッション. */
	public static final String TBL_SESSION = "S";
	/** Redisのキープリフィックス : RXID使用回数. */
	public static final String TBL_RXID = "RC";
	/** Redisのキープリフィックス : Feedキャッシュ. */
	public static final String TBL_CACHE_FEED = "CF";
	/** Redisのキープリフィックス : Entryキャッシュ. */
	public static final String TBL_CACHE_ENTRY = "CE";
	/** Redisのキープリフィックス : 文字列キャッシュ. */
	public static final String TBL_CACHE_TEXT = "CT";
	/** Redisのキープリフィックス : 整数キャッシュ. */
	public static final String TBL_CACHE_LONG = "CL";
	/** Redisのキープリフィックス : セッション第二キーFeed. */
	public static final String TBL_SESSION_FEED = "F";
	/** Redisのキープリフィックス : セッション第二キーEntry. */
	public static final String TBL_SESSION_ENTRY = "E";
	/** Redisのキープリフィックス : セッション第二キー文字列. */
	public static final String TBL_SESSION_TEXT = "T";
	/** Redisのキープリフィックス : セッション第二キー数値. */
	public static final String TBL_SESSION_LONG = "L";
	/** Redisのキープリフィックス : セッション第二キーUID. */
	public static final String TBL_SESSION_UID = "AUTH_UID";

	/** Redisのキープリフィックス : サービス名区切り文字. */
	public static final String TBL_SERVICENAME_DELIMITER = "#";

	// コネクションMap用キー
	/** 更新用コネクション */
	public static final String CONN_NAME_WRITE = "_redis_write";
	/** 参照用コネクション */
	public static final String CONN_NAME_READ = "_redis_read";

	/** 各処理でコネクションエラー発生時、コネクション取得リトライを行った後命令実行を再度実行する。 */
	public static final int RETRY = 1;

	/** JedisPoolのMaster、Slave区分. */
	public enum StateJedisPool { master, slave, none };

	/** Redis情報 : replication */
	public static final String REDIS_INFO_REPLICATION = "replication";
	/** Redis情報 : コメント行 */
	public static final String REDIS_INFO_COMMENT = "#";
	/** Redis情報 : 区切り文字 */
	public static final String REDIS_INFO_DELIMITER = ":";
	/** Redis情報 : role */
	public static final String REDIS_INFO_ROLE = "role";

	/** メモリ上のstaticオブジェクト格納キー : Redis接続インタフェース */
	public static final String STATIC_NAME_REDIS ="_redis";

	/** Feed */
	public static final String FEED = "Feed";
	/** Entry */
	public static final String ENTRY = "Entry";
	/** String */
	public static final String STRING = "String";
	/** Long */
	public static final String LONG = "Long";

	/**
	 * コンストラクタ.
	 */
	private JedisConst() { }

}
