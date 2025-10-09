package jp.reflexworks.taggingservice.bdbclient;

import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.util.Constants;

/**
 * BDBクライアント 定数クラス
 */
public interface BDBClientConst {

	/** BDBリクエストのタイムアウト(ミリ秒) **/
	public static final String BDBREQUEST_TIMEOUT_MILLIS = "_bdbrequest.timeout.millis";
	/** BDBリクエストに失敗したときのリトライ回数 **/
	public static final String BDBREQUEST_RETRY_COUNT = "_bdbrequest.retry.count";
	/** BDBリクエストリトライ時のスリープ時間(ミリ秒) **/
	public static final String BDBREQUEST_RETRY_WAITMILLIS = "_bdbrequest.retry.waitmillis";
	/** 初期起動時にBDBリクエストに失敗したときのリトライ回数 **/
	public static final String BDBREQUEST_INIT_RETRY_COUNT = "_bdbrequest.init.retry.count";
	/** 初期起動時にBDBリクエストリトライ時のスリープ時間(ミリ秒) **/
	public static final String BDBREQUEST_INIT_RETRY_WAITMILLIS = "_bdbrequest.init.retry.waitmillis";
	/** 設定 : BDBサーバへのアクセスログを出力するかどうか */
	public static final String BDBREQUEST_ENABLE_ACCESSLOG = "_bdbrequest.enable.accesslog";
	/** 一括更新に失敗したときのリトライ回数 (データストア検索・更新失敗リトライ回数分リトライ後のリトライ)  **/
	public static final String BULKPUT_RETRY_COUNT = "_bulkput.retry.count";
	/** 一括更新リトライ時のスリープ時間(ミリ秒) **/
	public static final String BULKPUT_RETRY_WAITMILLIS = "_bulkput.retry.waitmillis";
	/** 一括更新非同期処理終了待ちスリープ時間(ミリ秒) **/
	public static final String BULKPUT_SYNC_WAITMILLIS = "_bulkput.sync.waitmillis";
	/** BDBサーバEntry取得数 **/
	public static final String BDBSERVER_GET_LIMIT = "_bdbserver.get.limit";
	/** BDBへのリクエストヘッダ設定値 (_bdbrequest.header.{連番}={キー}:{値}) **/
	public static final String BDBREQUEST_HEADER_PREFIX = "_bdbrequest.header.";
	/** BDBへのリクエストヘッダ設定値 キーと値の区切り文字 **/
	public static final String BDBREQUEST_HEADER_DELIMITER = ":";
	/** データ移行バッチジョブサーバリクエストURL **/
	public static final String URL_MIGRATE = "_url.migrate";
	/** インデックス登録・更新・削除バッチジョブサーバリクエストURL **/
	public static final String URL_UPDATEINDEX = "_url.updateindex";
	/** Cacheによる排他の有効期間(秒) **/
	public static final String BDBCLIENT_EXCLUSION_EXPIRE_SEC = "_bdbclient.exclusion.expire.sec";
	/** メインスレッド初期取得Entryキャッシュを有効にするかどうか **/
	public static final String BDBCLIENT_DISABLE_INITMAINTHREADCACHE = "_bdbclient.disable.initmainthreadcache";
	/** メインスレッド初期取得Entryキャッシュの有効期間(秒) **/
	public static final String BDBCLIENT_INITMAINTHREADCACHE_EXPIRE_SEC = "_bdbclient.initmainthreadcache.expire.sec";
	/** Entryサーバ最大取得数 (リクエストヘッダ制限に対応) **/
	public static final String ENTRYSERVER_GET_LIMIT = "_entryserver.get.limit";
	/** Entryサーバ最大更新数 (リクエストヘッダ制限に対応) **/
	public static final String ENTRYSERVER_PUT_LIMIT = "_entryserver.put.limit";
	/** 設定 : メインスレッドキャッシュのアクセスログ(処理経過ログ)を出力するかどうか */
	public static final String INITMAINTHREADCACHE_ENABLE_ACCESSLOG = "_initmainthreadcache.enable.accesslog";

	/** 設定デフォルト : BDBリクエストのタイムアウト(ミリ秒) */
	static final int BDBREQUEST_TIMEOUT_MILLIS_DEFAULT = 60000;
	/** 設定デフォルト : リトライ総数 */
	static final int BDBREQUEST_RETRY_COUNT_DEFAULT = 3;
	/** 設定デフォルト : リトライ時のスリープ時間(ミリ秒) */
	static final int BDBREQUEST_RETRY_WAITMILLIS_DEFAULT = 80;
	/** 設定デフォルト : 初期起動時のリトライ総数 */
	static final int BDBREQUEST_INIT_RETRY_COUNT_DEFAULT = 25;
	/** 設定デフォルト : 初期起動時のリトライ時のスリープ時間(ミリ秒) */
	static final int BDBREQUEST_INIT_RETRY_WAITMILLIS_DEFAULT = 30000;
	/** 設定デフォルト : 一括更新リトライ総数 */
	static final int BULKPUT_RETRY_COUNT_DEFAULT = 30;
	/** 設定デフォルト : 一括更新リトライ時のスリープ時間(ミリ秒) */
	static final int BULKPUT_RETRY_WAITMILLIS_DEFAULT = 250;
	/** 設定デフォルト : 一括更新非同期処理終了待ち(ミリ秒) */
	static final int BULKPUT_SYNC_WAITMILLIS_DEFAULT = 20;
	/** 設定デフォルト : BDBサーバEntry取得数 */
	static final int BDBSERVER_GET_LIMIT_DEFAULT = 3;
	/** 設定デフォルト : Cacheによる排他の有効期限(秒) */
	static final int BDBCLIENT_EXCLUSION_EXPIRE_SEC_DEFAULT = 3600;	// 1時間
	/** 設定デフォルト : メインスレッド初期処理キャッシュの有効期間(秒) **/
	static final int BDBCLIENT_INITMAINTHREADCACHE_EXPIRE_SEC_DEFAULT = 86400;	// 1日
	/** 設定デフォルト : Entryサーバ最大取得数 (リクエストヘッダ制限に対応) **/
	static final int ENTRYSERVER_GET_LIMIT_DEFAULT = 500;
	/** 設定デフォルト : Entryサーバ最大更新数 (リクエストヘッダ制限に対応) **/
	static final int ENTRYSERVER_PUT_LIMIT_DEFAULT = 500;

	/** コネクション情報格納キー : Entryメモリキャッシュ */
	static final String CONNECTION_INFO_ENTRYMAP ="_ds_entrymap";
	/** コネクション情報格納キー : Feedメモリキャッシュ */
	static final String CONNECTION_INFO_FEEDMAP ="_ds_feedmap";
	/** コネクション情報格納キー : 登録予定Entryメモリキャッシュ */
	static final String CONNECTION_INFO_TMP_ENTRYMAP ="_ds_tmpentrymap";
	/** コネクション情報格納キー : ConsistentHash Map */
	static final String CONNECTION_INFO_CONSISTENTHASHMAP ="_ds_consistenthashmap";

	/** カーソル区切り文字 : {親階層},{カーソル} */
	public static final String CURSOR_SEPARATOR = ",";
	/** Request Header value : XMLHttpRequest */
	public static final String X_REQUESTED_WITH_VALUE = "TaggingService";
	/** BDBサーバの状態格納項目 */
	public static final String PROP_BDBSERVER_STATUS = "subtitle";
	/** BDBサーバの状態 : assignable */
	public static final String BDBSERVER_STATUS_ASSIGNABLE = "assignable";

	/** 検索がフェッチ最大数を超えた場合のフラグ */
	static final String MARK_FETCH_LIMIT = Constants.MARK_FETCH_LIMIT;

	/** URLパラメータ : テーブルリスト取得 : GET /b/?_list={テーブル名}&_service={サービス名} */
	static final String PARAM_LIST = "_list";
	/** URLパラメータ : サービス名 */
	static final String PARAM_SERVICE = RequestParam.PARAM_SERVICE;
	/** URLパラメータ : BDB環境統計情報取得 : GET /b/?_stats */
	static final String PARAM_STATS = "_stats";
	/** URLパラメータ : idkey */
	public static final String PARAM_IDKEY = "_idkey";
	/** URLパラメータ : getentriesbyidkeys */
	public static final String PARAM_GETENTRIES_BY_IDKEY = "_getentriesbyidkeys";

	/** OR条件の検索済みキー格納キー */
	static final String SESSION_GETFEED_OR = "_GETFEED_OR_";

	/** monitorパラメータ : Manifest */
	public static final String MONITOR_MANIFEST = "manifest";
	/** monitorパラメータ : Entry */
	public static final String MONITOR_ENTRY = "entry";
	/** monitorパラメータ : インデックス */
	public static final String MONITOR_INDEX = "index";
	/** monitorパラメータ : 全文検索インデックス */
	public static final String MONITOR_FULLTEXTSEARCH = "fulltextsearch";
	/** monitorパラメータ : 採番・カウンタ */
	public static final String MONITOR_ALLOCIDS = "allocids";

	/** Request Header value : Migrate */
	public static final String X_REQUESTED_WITH_MIGRATE = "Migrate";

}
