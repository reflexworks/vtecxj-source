package jp.reflexworks.taggingservice.service;

/**
 * リクエスト・メインスレッド初期処理　定数クラス.
 */
public interface BDBClientInitMainThreadConst {

	/** ロックフラグ */
	public static final Integer LOCK = 1;

	/** リクエスト初期処理で取得・設定するキャッシュのキー接頭辞 : Feed検索結果 */
	public static final String CACHEFEED_KEY_INITMAINTHREAD_PREFIX = "/_#initmainthread";
	/** リクエスト初期処理で取得・設定するキャッシュのキー : システム管理サービスのEntryリスト */
	public static final String CACHEFEED_KEY_INITMAINTHREAD_ENTRYLIST_SYSTEM = CACHEFEED_KEY_INITMAINTHREAD_PREFIX + "/_#entrylist_system";
	/** リクエスト初期処理で取得・設定するキャッシュのキー接頭辞 : システム管理サービスのサービス固有Entryリスト */
	public static final String CACHEFEED_KEY_INITMAINTHREAD_ENTRYLIST_SYSTEM_PREFIX = CACHEFEED_KEY_INITMAINTHREAD_PREFIX + "/_#entrylist_system@";
	/** リクエスト初期処理で取得・設定するキャッシュのキー : 自サービスのEntryリスト */
	public static final String CACHEFEED_KEY_INITMAINTHREAD_ENTRYLIST_SERVICE = CACHEFEED_KEY_INITMAINTHREAD_PREFIX + "/_#entrylist_service";
	/** ユーザ情報キャッシュのキー接頭辞 : Entryリスト */
	public static final String CACHEFEED_KEY_INITMAINTHREAD_USERINFO_PREFIX = CACHEFEED_KEY_INITMAINTHREAD_PREFIX + "/_#userinfo@";

	/** リクエスト・メインスレッド初期処理状態のスレッド内Mapキー : システム管理サービスのEntryリスト取得 */
	public static final String INITMAINTHREAD_STATUS_ENTRIES_SYSTEM = "_initmainthread_entries_#system";
	/** リクエスト・メインスレッド初期処理状態のスレッド内Mapキー接頭辞 : サービスのEntryリスト取得 */
	public static final String INITMAINTHREAD_STATUS_ENTRIES_SYSTEM_PREFIX = "_initmainthread_entries_system@";
	/** リクエスト・メインスレッド初期処理状態のスレッド内Mapキー : 自サービスのEntryリスト取得 */
	public static final String INITMAINTHREAD_STATUS_ENTRIES_SERVICE = "_initmainthread_entries_#service";
	/** リクエスト・メインスレッド初期処理状態のスレッド内Mapキー : システム管理サービスのFeed取得 */
	public static final String INITMAINTHREAD_STATUS_FEED_SYSTEM = "_initmainthread_feed_#system";
	/** リクエスト・メインスレッド初期処理状態のスレッド内Mapキー接頭辞 : サービスのFeed取得 */
	public static final String INITMAINTHREAD_STATUS_FEED_SYSTEM_PREFIX = "_initmainthread_feed_system@";
	/** リクエスト・メインスレッド初期処理状態のスレッド内Mapキー : 自サービスのFeed取得 */
	public static final String INITMAINTHREAD_STATUS_FEED_SERVICE = "_initmainthread_feed_#service";
	/** メモリキャッシュキー接頭辞 : キャッシュからユーザ情報取得ステータス */
	public static final String USERINFOCACHE_STATUS_ENTRIES_KEY_PREFIX = "_userinfo_entries@";
	/** メモリキャッシュキー接頭辞 : キャッシュからユーザ情報取得ステータス */
	public static final String USERINFOCACHE_STATUS_FEED_KEY_PREFIX = "_userinfo_feed@";

	/** リクエスト・メインスレッド初期処理状態の値 : 取得済み */
	public static final String INITMAINTHREAD_STATUS_VALUE = "retrieved";

	/** 正規表現のワイルドカード */
	public static final String REGEX_PATTERN_GROUP = "([^/]+)";
	/** サービス名のダミー文字 */
	public static final String DUMMY_SERVICENAME = "#";

	/** メモリ上のstaticオブジェクト格納キー : リクエスト・メインスレッド初期処理Entryリスト取得のサービス名抽出パターン */
	public static final String STATIC_NAME_INITMAINTHREAD_PATTERNS_ENTRIES = "_initmainthread_patterns_entries";
	/** メモリ上のstaticオブジェクト格納キー : リクエスト・メインスレッド初期処理Feed取得のサービス名抽出パターン */
	public static final String STATIC_NAME_INITMAINTHREAD_PATTERNS_FEED = "_initmainthread_patterns_feed";
	/** メモリ上のstaticオブジェクト格納キー : ユーザ情報Entryリスト取得のサービス名抽出パターン */
	public static final String STATIC_NAME_USERINFOCACHE_PATTERNS_ENTRIES = "_userinfocache_patterns_entries";
	/** メモリ上のstaticオブジェクト格納キー : ユーザ情報Feed取得のサービス名抽出パターン */
	public static final String STATIC_NAME_USERINFOCACHE_PATTERNS_FEED = "_userinfocache_patterns_feed";


	/** 設定データタイプ : システム管理サービス、システム管理サービスのサービス固有、自サービス */
	public enum SettingDataType {SYSTEM, SYSTEM_SERVICE, SERVICE, USER};

}
