package jp.reflexworks.taggingservice.env;

/**
 * Tagging BDB 環境定数クラス
 */
public interface FullTextSearchEnvConst extends ReflexEnvConst {

	/** 設定 : 全文検索インデックスの最大文字数 */
	public static final String FULLTEXTINDEX_WORDCOUNT_LIMIT = "_fulltextindex.wordcount.limit";

	/** 設定デフォルト : 全文検索インデックスの最大文字数 */
	public static final int FULLTEXTINDEX_WORDCOUNT_LIMIT_DEFAULT = 50;

	/** 設定デフォルト : Redisリトライ総数 */
	static final int REDIS_RETRY_COUNT_DEFAULT = 2;
	/** 設定デフォルト : Redisリトライ時のスリープ時間(ミリ秒) */
	static final int REDIS_RETRY_WAITMILLIS_DEFAULT = 100;

}
