package jp.reflexworks.taggingservice.env;

/**
 * TaggingService共通システム設定.
 */
public interface ReflexEnvConst {

	/** システム設定接頭辞 */
	public static final String SYSTEM_PROP_PREFIX = "_";

	/** 環境ステージ */
	public static final String ENV_STAGE = "_env.stage";
	/** タイムゾーン **/
	public static final String TIMEZONE = "_timezone";
	/** ロケール **/
	public static final String LOCALE = "_locale";
	/** ロケールの区切り文字 {language}_{country} */
	public static final String LOCALE_DELIMITER = "_";

	/** エントリー最大数設定 **/
	public static final String ENTRY_NUMBER_LIMIT = "_entry.number.limit";
	/** フィードリスト最大数設定 **/
	public static final String FEED_NUMBER_LIMIT = "_feed.number.limit";
	/** エイリアス最大数設定 **/
	public static final String ALIAS_NUMBER_LIMIT = "_alias.number.limit";
	/** フェッチ件数制限 */
	public static final String FETCH_LIMIT = "_fetch.limit";
	/** 更新エントリー最大数設定 **/
	public static final String UPDATE_ENTRY_NUMBER_LIMIT = "_update.entry.number.limit";
	/** 採番件数制限 */
	public static final String ALLOCIDS_LIMIT = "_allocids.limit";
	/** Index上限数 */
	public static final String INDEX_LIMIT = "_index.limit";
	/** システム管理サービスとなるサービス */
	public static final String SYSTEM_SERVICE = "_system.service";
	/** salt */
	public static final String ENTRY_SALT = "_entry.salt";
	/** システム管理サービスの名前空間 **/
	public static final String NAMESPACE_SYSTEM = "_namespace.system";
	/** キーの階層数上限 **/
	public static final String MAXNUM_KEY_HIERARCHIES = "_maxnum.key.hierarchies";
	/** サーバタイプ **/
	public static final String REFLEX_SERVERTYPE = "_reflex.servertype";
	/** 非同期処理プール数 (バッチジョブ用) */
	public static final String TASKQUEUE_POOLSIZE_BATCHJOB = "_taskqueue.poolsize.batchjob";
	/** 非同期処理プール数 (内部処理用) */
	public static final String TASKQUEUE_POOLSIZE_SYSTEM = "_taskqueue.poolsize.system";
	/** BDBサーバへのリクエストログを出力するかどうか */
	public static final String BDB_ENABLE_REQUESTLOG = "_bdb.enable.requestlog";
	/** システム管理サービスの情報static保存期間(秒) */
	public static final String STATICINFO_TIMELIMIT_SEC = "_staticinfo.timelimit.sec";
	/** static情報ロック取得に失敗したときのリトライ回数 **/
	public static final String STATICINFO_RETRY_COUNT = "_staticinfo.retry.count";
	/** static情報ロック取得リトライ時のスリープ時間(ミリ秒) **/
	public static final String STATICINFO_RETRY_WAITMILLIS = "_staticinfo.retry.waitmillis";

	// ----- 定数値、デフォルト値 -----

	// ATOM標準
	public static final String ATOM_STANDARD = "@atom";

	/** 設定デフォルト : 環境ステージ */
	public static final String ENV_STAGE_DEFAULT = "main";
	/** ページ最大値 デフォルト */
	public static final int ENTRY_NUMBER_DEFAULT_DEFAULT = 100;
	/** セッション有効時間（分）デフォルト */
	public static final int SESSION_MINUTE_DEFAULT = 30;
	/** RXID有効時間（分）デフォルト */
	public static final int RXID_MINUTE_DEFAULT = 120;
	/** 1ページあたりの件数制限デフォルト値 */
	public static final int ENTRY_NUMBER_LIMIT_DEFAULT = 5000;
	/** Feedリストの件数制限デフォルト値 */
	public static final int FEED_NUMBER_LIMIT_DEFAULT = 1000;
	/** エイリアス件数制限デフォルト値 */
	public static final int ALIAS_NUMBER_LIMIT_DEFAULT = 100;
	/** フェッチ件数制限デフォルト値 */
	public static final int FETCH_LIMIT_DEFAULT = 50000;
	/** 更新Entry件数上限デフォルト値 */
	public static final int UPDATE_ENTRY_NUMBER_LIMIT_DEFAULT = 1000;
	/** 採番数上限デフォルト値 */
	public static final int ALLOCIDS_LIMIT_DEFAULT = 500;
	/** Index上限数のデフォルト値 */
	public static final int INDEX_LIMIT_DEFAULT = 200;
	/** システム管理サービス デフォルト */
	public static final String SYSTEM_SERVICE_DEFAULT = "admin";
	/** スーパーユーザ デフォルト */
	public static final String SUPER_USER_DEFAULT = "reflexworks";
	/** 非同期処理プール数 (バッチジョブ用) デフォルト */
	public static final int TASKQUEUE_POOLSIZE_BATCHJOB_DEFAULT = 500;
	/** 非同期処理プール数 (内部処理用) デフォルト */
	public static final int TASKQUEUE_POOLSIZE_SYSTEM_DEFAULT = 1000;
	/** キーの階層数上限 デフォルト */
	public static final int MAXNUM_KEY_HIERARCHIES_DEFAULT = 10;
	/** JSON出力においてfeed.entryを省略するかどうか デフォルト */
	public static final boolean JSON_STARTARRAYBRACKET_DEFAULT = true;
	/** 設定デフォルト : システム管理サービスの情報static保存期間(秒) */
	public static final int STATICINFO_TIMELIMIT_SEC_DEFAULT = 60;
	/** 設定デフォルト : static情報lock取得リトライ総数 */
	public static final int STATICINFO_RETRY_COUNT_DEFAULT = 100;
	/** 設定デフォルト : static情報lock取得リトライ時のスリープ時間(ミリ秒) */
	public static final int STATICINFO_RETRY_WAITMILLIS_DEFAULT = 80;

}
