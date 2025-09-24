package jp.reflexworks.taggingservice.env;

import jp.reflexworks.taggingservice.plugin.AccessTokenManager;
import jp.reflexworks.taggingservice.plugin.AclManager;
import jp.reflexworks.taggingservice.plugin.AuthenticationManager;
import jp.reflexworks.taggingservice.plugin.CaptchaManager;
import jp.reflexworks.taggingservice.plugin.EMailManager;
import jp.reflexworks.taggingservice.plugin.LogManager;
import jp.reflexworks.taggingservice.plugin.LoginLogoutManager;
import jp.reflexworks.taggingservice.plugin.MemorySortManager;
import jp.reflexworks.taggingservice.plugin.MessageManager;
import jp.reflexworks.taggingservice.plugin.MonitorManager;
import jp.reflexworks.taggingservice.plugin.NamespaceManager;
import jp.reflexworks.taggingservice.plugin.PropertyManager;
import jp.reflexworks.taggingservice.plugin.RXIDManager;
import jp.reflexworks.taggingservice.plugin.ReflexSecurityManager;
import jp.reflexworks.taggingservice.plugin.RequestResponseManager;
import jp.reflexworks.taggingservice.plugin.ResourceMapperManager;
import jp.reflexworks.taggingservice.plugin.ServiceManager;
import jp.reflexworks.taggingservice.plugin.TaskQueueManager;
import jp.reflexworks.taggingservice.plugin.UserManager;

/**
 * システム設定.
 * <p>
 * サービスごとに設定する項目は、
 * jp.reflexworks.taggingservice.api.SettingConst インターフェースに定義すること。
 * </p>
 */
public interface TaggingEnvConst extends ReflexEnvConst {

	/** XMLシリアライズのstrictモード設定 **/
	public static final String SERIALIZE_STRICT = "_serialize.strict";
	/** 認証にセッションを使用しないオプション */
	public static final String DISABLE_SESSION = "_disable.session";
	/** スーパーユーザのユーザ名 */
	public static final String SUPER_USER = "_super.username";
	/** システム管理サービスとなるサービスのURL (一般ノードの初期データ登録に使用) */
	public static final String SYSTEM_URL = "_system.url";
	/** CreateService処理におけるTaskQueueの繰り返し回数上限 */
	public static final String CREATESERVICE_TASKQUEUE_TIMES = "_createservice.taskqueue.times";
	/** CreateService処理におけるTaskQueueの開始遅延時間(ミリ秒) */
	public static final String CREATESERVICE_TASKQUEUE_COUNTDOWNMILLES =
			"_createservice.taskqueue.countdownmilles";
	/** リクエストデータの最大サイズ */
	public static final String REQUEST_PAYLOAD_SIZE = "_request.payload.size";
	/** ページネーション : 1度のリクエスト(TaskQueue)で検索するページ数の最大値 */
	public static final String PAGINATION_LIMIT = "_pagination.limit";
	/** ページネーション : セッションに格納する検索パターン数 */
	public static final String POINTERSLIST_LIMIT = "_pointerslist.limit";
	/** エラーCookie存続時間(秒) */
	public static final String ERRORCOOKIE_MAXAGE = "_errorcookie.maxage";
	/** HTML格納フォルダ(/d/_html)を省略せず元のままとするかどうか */
	public static final String IS_INTACT_HTMLURL = "_is.intact.htmlurl";
	/** OriginヘッダのチェックでOKとする値 */
	public static final String IGNORE_ORIGIN_ERROR_PREFIX = "_ignore.origin.error.";
	/** アカウントを小文字変換しないフラグ */
	public static final String ENABLE_ACCOUNT_UPPERCASE = "_enable.account.uppercase";
	/** アカウントをメールアドレス限定としないフラグ */
	public static final String ENABLE_ACCOUNT_NOMAILADDRESS = "_enable.account.nomailaddress";
	/** 認証なしをエラーとするフラグ */
	public static final String DISABLE_WITHOUT_AUTH = "_disable.without.auth";
	/** SIDをHttpOnly指定しないフラグ */
	public static final String DISABLE_SESSION_HTTPONLY = "_disable.session.httponly";
	/** ブラウザキャッシュする(no-cacheを指定しない)フラグ */
	public static final String ENABLE_BLOWSER_CACHE = "_enable.blowser.cache";
	/** URLのサーバ名からコンテキストパスまで */
	public static final String REFLEX_SERVERCONTEXTPATH = "_reflex.servercontextpath";
	/** リクエスト許可Host */
	public static final String REQUEST_ALLOWORIGIN_PREFIX = "_request.alloworigin.";
	/** サービス設定確認ロックに失敗したときのリトライ回数 **/
	public static final String SERVICESETTINGS_RETRY_COUNT = "_servicesettings.retry.count";
	/** サービス設定確認ロックリトライ時のスリープ時間(ミリ秒) **/
	public static final String SERVICESETTINGS_RETRY_WAITMILLIS = "_servicesettings.retry.waitmillis";
	/** サービスごとの設定定義クラス接頭辞 (_servicesetting.class.{文字列}={クラス名}) **/
	public static final String SERVICESETTING_CLASS_PREFIX = "_servicesetting.class.";
	/** 初期起動時にサービス設定に失敗したときのリトライ回数 **/
	public static final String SERVICESETTINGS_INIT_RETRY_COUNT = "_servicesettings.init.retry.count";
	/** 初期起動時のサービス設定リトライ時のスリープ時間(ミリ秒) **/
	public static final String SERVICESETTINGS_INIT_RETRY_WAITMILLIS = "_servicesettings.init.retry.waitmillis";
	/** 初期実行するコマンド接頭辞 */
	public static final String INIT_COMMANDPATH_PREFIX = "_init.commandpath.";
	/** フィルタのアクセスログ（処理経過ログ）を出力するかどうか */
	public static final String FILTER_ENABLE_ACCESSLOG = "_filter.enable.accesslog";
	/** 初期処理のアクセスログを出力するかどうか */
	public static final String INIT_ENABLE_ACCESSLOG = "_init.enable.accesslog";
	/** シャットダウン処理待ち回数 */
	public static final String SHUTDOWN_AWAIT_COUNT = "_shutdown.await.count";
	/** シャットダウン処理待ち時のスリープ時間(ミリ秒) */
	public static final String SHUTDOWN_AWAIT_WAITMILLIS = "_shutdown.await.waitmillis";

	// プラグイン
	/** ReflexContext継承クラス名 */
	public static final String PLUGIN_REFLEXCONTEXT = "_plugin.reflexcontext";
	/** ResourceMapper管理プラグインクラス名 */
	public static final String PLUGIN_RESOURCEMAPPERMANAGER = "_plugin.resourcemappermanager";
	/** プロパティ管理プラグインクラス名 */
	public static final String PLUGIN_PROPERTYMANAGER = "_plugin.propertymanager";
	/** データストア管理プラグインクラス名 */
	public static final String PLUGIN_DATASTOREMANAGER = "_plugin.datastoremanager";
	/** キャッシュ管理プラグインクラス名 */
	public static final String PLUGIN_CACHEMANAGER = "_plugin.cachemanager";
	/** コンテンツ管理プラグインクラス名 */
	public static final String PLUGIN_CONTENTMANAGER = "_plugin.contentmanager";
	/** リクエスト・レスポンス管理プラグインクラス名 */
	public static final String PLUGIN_REQUESTRESPONSEMANAGER = "_plugin.requestresponsemanager";
	/** 採番管理プラグインクラス名 */
	public static final String PLUGIN_ALLOCATEIDSMANAGER = "_plugin.allocateidsmanager";
	/** 加算管理プラグインクラス名 */
	public static final String PLUGIN_INCREMENTMANAGER = "_plugin.incrementmanager";
	/** 認証管理プラグインクラス名 */
	public static final String PLUGIN_AUTHENTICATIONMANAGER = "_plugin.authenticationmanager";
	/** ACL管理プラグインクラス名 */
	public static final String PLUGIN_ACLMANAGER = "_plugin.aclmanager";
	/** セッション管理プラグインクラス名 */
	public static final String PLUGIN_SESSIONMANAGER = "_plugin.sessionmanager";
	/** サービス管理プラグインクラス名 */
	public static final String PLUGIN_SERVICEMANAGER = "_plugin.servicemanager";
	/** ユーザ管理プラグインクラス名 */
	public static final String PLUGIN_USERMANAGER = "_plugin.usermanager";
	/** アクセストークン管理プラグインクラス名 */
	public static final String PLUGIN_ACCESSTOKENMANAGER = "_plugin.accesstokenmanager";
	/** RXID管理プラグインクラス名 */
	public static final String PLUGIN_RXIDMANAGER = "_plugin.rxidmanager";
	/** ログイン・ログアウト管理プラグインクラス名 */
	public static final String PLUGIN_LOGINLOGOUTMANAGER = "_plugin.loginlogoutmanager";
	/** ログ管理プラグインクラス名 */
	public static final String PLUGIN_LOGMANAGER = "_plugin.logmanager";
	/** メッセージ管理プラグインクラス名 */
	public static final String PLUGIN_MESSAGEMANAGER = "_plugin.messagemanager";
	/** セキュリティ管理プラグインクラス名 */
	public static final String PLUGIN_SECURITYMANAGER = "_plugin.securitymanager";
	/** キャプチャ管理プラグインクラス名 */
	public static final String PLUGIN_CAPTCHAMANAGER = "_plugin.captchamanager";
	/** 非同期処理管理プラグインクラス名 */
	public static final String PLUGIN_TASKQUEUEMANAGER = "_plugin.taskqueuemanager";
	/** メール管理プラグインクラス名 */
	public static final String PLUGIN_EMAILMANAGER = "_plugin.emailmanager";
	/** BigQuery管理プラグインクラス名 */
	public static final String PLUGIN_BIGQUERYMANAGER = "_plugin.bigquerymanager";
	/** 名前空間管理プラグインクラス名 */
	public static final String PLUGIN_NAMESPACEMANAGER = "_plugin.namespacemanager";
	/** モニター管理プラグインクラス名 */
	public static final String PLUGIN_MONITORMANAGER = "_plugin.monitormanager";
	/** インメモリソート管理プラグインクラス名 */
	public static final String PLUGIN_MEMORYSORTMANAGER = "_plugin.memorysortmanager";
	/** 例外管理プラグインクラス名 */
	public static final String PLUGIN_EXCEPTIONMANAGER = "_plugin.exceptionmanager";
	/** プッシュ通知管理プラグインクラス名 */
	public static final String PLUGIN_PUSHNOTIFICATIONMANAGER = "_plugin.pushnotificationmanager";
	/** WebSocket管理プラグインクラス名 */
	public static final String PLUGIN_WEBSOCKETMANAGER = "_plugin.websocketmanager";
	/** PDF管理プラグインクラス名 */
	public static final String PLUGIN_PDFMANAGER = "_plugin.pdfmanager";
	/** RDB管理プラグインクラス名 */
	public static final String PLUGIN_RDBMANAGER = "_plugin.rdbmanager";
	/** OAuth管理プラグインクラス名 */
	public static final String PLUGIN_OAUTHMANAGER = "_plugin.oauthmanager";
	/** シークレット管理プラグインクラス名 */
	public static final String PLUGIN_SECRETMANAGER = "_plugin.secretmanager";

	// ----- 定数値、デフォルト値 -----

	/** 認証失敗許容数デフォルト値 */
	public static final int AUTH_FAILED_COUNT_DEFAULT = 50;
	/** 認証失敗許容数保持の有効時間(秒) デフォルト値 */
	public static final int AUTH_FAILED_COUNT_EXPIRE_DEFAULT = 86400;	// 1日
	/** CreateService処理のTaskQueue繰り返し回数上限 デフォルト数 */
	public static final int CREATESERVICE_TASKQUEUE_TIMES_DEFAULT = 5;
	/** CreateService処理のTaskQueue開始遅延時間(ミリ秒) デフォルト数 */
	public static final int CREATESERVICE_TASKQUEUE_COUNTDOWNMILLES_DEFAULT = 3000;
	/** リクエストデータの最大サイズデフォルト */
	public static final int REQUEST_PAYLOAD_SIZE_DEFAULT = 104857600;
	/** ページネーションの上限 デフォルト */
	public static final int PAGINATION_LIMIT_DEFAULT = 100;
	/** カーソルリスト数上限 デフォルト */
	public static final int POINTERSLIST_LIMIT_DEFAULT = 10;
	/** エラーステータスCookie存続時間(秒) デフォルト */
	public static final int ERRORCOOKIE_MAXAGE_DEFAULT = 10;
	/** WSSEにおいてキャプチャ不要な失敗回数 デフォルト */
	public static final int WSSE_WITHOUT_CAPTCHA_DEFAULT = 2;
	/** 設定デフォルト : サービス設定ロックリトライ総数 */
	public static final int SERVICESETTINGS_RETRY_COUNT_DEFAULT = 100;
	/** 設定デフォルト : サービス設定ロックリトライ時のスリープ時間(ミリ秒) */
	public static final int SERVICESETTINGS_RETRY_WAITMILLIS_DEFAULT = 30;
	/** 設定デフォルト : 初期起動時のリトライ総数 */
	public static final int SERVICESETTINGS_INIT_RETRY_COUNT_DEFAULT = 25;
	/** 設定デフォルト : 初期起動時のリトライ時のスリープ時間(ミリ秒) */
	public static final int SERVICESETTINGS_INIT_RETRY_WAITMILLIS_DEFAULT = 30000;
	/** 設定デフォルト : 検索でインメモリソートなど処理中の場合のリトライ回数 **/
	public static final int PROCESSING_GET_RETRY_COUNT_DEFAULT = 12;
	/** 設定デフォルト : 検索でインメモリソートなど処理中の場合の待ち時間(ミリ秒) **/
	public static final int PROCESSING_GET_RETRY_WAITMILLIS_DEFAULT = 500;
	/** 設定デフォルト : シャットダウン処理待ち回数 */
	public static final int SHUTDOWN_AWAIT_COUNT_DEFAULT = 1000;
	/** 設定デフォルト : シャットダウン処理待ち時のスリープ時間(ミリ秒) */
	public static final int SHUTDOWN_AWAIT_WAITMILLIS_DEFAULT = 30;

	/** ResourceMapper管理プラグインクラス デフォルト */
	static final Class<? extends ResourceMapperManager> PLUGIN_RESOURCEMAPPERMANAGER_DEFAULT =
			jp.reflexworks.taggingservice.plugin.def.ResourceMapperManagerDefault.class;
	/** プロパティ管理プラグインクラス デフォルト */
	static final Class<? extends PropertyManager> PLUGIN_PROPERTYMANAGER_DEFAULT =
			jp.reflexworks.taggingservice.plugin.def.PropertyManagerDefault.class;
	/** リクエスト・レスポンス管理プラグインクラス デフォルト */
	static final Class<? extends RequestResponseManager> PLUGIN_REQUESTRESPONSEMANAGER_DEFAULT =
			jp.reflexworks.taggingservice.plugin.def.RequestResponseManagerDefault.class;
	/** サービス管理プラグインクラス デフォルト */
	static final Class<? extends ServiceManager> PLUGIN_SERVICEMANAGER_DEFAULT =
			jp.reflexworks.taggingservice.plugin.def.ServiceManagerDefault.class;
	/** ユーザ管理プラグインクラス デフォルト */
	static final Class<? extends UserManager> PLUGIN_USERMANAGER_DEFAULT =
			jp.reflexworks.taggingservice.plugin.def.UserManagerDefault.class;
	/** アクセストークン管理プラグインクラス デフォルト */
	static final Class<? extends AccessTokenManager> PLUGIN_ACCESSTOKENMANAGER_DEFAULT =
			jp.reflexworks.taggingservice.plugin.def.AccessTokenManagerDefault.class;
	/** RXID管理プラグインクラス デフォルト */
	static final Class<? extends RXIDManager> PLUGIN_RXIDMANAGER_DEFAULT =
			jp.reflexworks.taggingservice.plugin.def.RXIDManagerDefault.class;
	/** 認証管理プラグインクラス デフォルト */
	static final Class<? extends AuthenticationManager> PLUGIN_AUTHENTICATIONMANAGER_DEFAULT =
			jp.reflexworks.taggingservice.plugin.def.AuthenticationManagerDefault.class;
	/** ログイン・ログアウト管理プラグインクラス デフォルト */
	static final Class<? extends LoginLogoutManager> PLUGIN_LOGINLOGOUTMANAGER_DEFAULT =
			jp.reflexworks.taggingservice.plugin.def.LoginLogoutManagerDefault.class;
	/** ACL管理プラグインクラス デフォルト */
	static final Class<? extends AclManager> PLUGIN_ACLMANAGER_DEFAULT =
			jp.reflexworks.taggingservice.plugin.def.AclManagerDefault.class;
	/** ログ管理プラグインクラス デフォルト */
	static final Class<? extends LogManager> PLUGIN_LOGMANAGER_DEFAULT =
			jp.reflexworks.taggingservice.plugin.def.LogManagerDefault.class;
	/** メッセージ管理プラグインクラス デフォルト */
	static final Class<? extends MessageManager> PLUGIN_MESSAGEMANAGER_DEFAULT =
			jp.reflexworks.taggingservice.plugin.def.MessageManagerDefault.class;
	/** セキュリティ管理プラグインクラス デフォルト */
	static final Class<? extends ReflexSecurityManager> PLUGIN_SECURITYMANAGER_DEFAULT =
			jp.reflexworks.taggingservice.plugin.def.ReflexSecurityManagerDefault.class;
	/** キャプチャ管理プラグインクラス デフォルト */
	static final Class<? extends CaptchaManager> PLUGIN_CAPTCHAMANAGER_DEFAULT =
			jp.reflexworks.taggingservice.plugin.def.CaptchaManagerDefault.class;
	/** 非同期処理管理プラグインクラス デフォルト */
	static final Class<? extends TaskQueueManager> PLUGIN_TASKQUEUEMANAGER_DEFAULT =
			jp.reflexworks.taggingservice.plugin.def.TaskQueueManagerDefault.class;
	/** メール管理プラグインクラス デフォルト */
	static final Class<? extends EMailManager> PLUGIN_EMAILMANAGER_DEFAULT =
			jp.reflexworks.taggingservice.plugin.def.EMailManagerDefault.class;
	/** 名前空間管理プラグインクラス デフォルト */
	static final Class<? extends NamespaceManager> PLUGIN_NAMESPACEMANAGER_DEFAULT =
			jp.reflexworks.taggingservice.plugin.def.NamespaceManagerDefault.class;
	/** モニター管理プラグインクラス デフォルト */
	static final Class<? extends MonitorManager> PLUGIN_MONITORMANAGER_DEFAULT =
			jp.reflexworks.taggingservice.plugin.def.MonitorManagerDefault.class;
	/** インメモリソート管理プラグインクラス デフォルト */
	static final Class<? extends MemorySortManager> PLUGIN_MEMORYSORTMANAGER_DEFAULT =
			jp.reflexworks.taggingservice.plugin.def.MemorySortManagerDefault.class;

	/** シャットダウン時の待ち処理リスト名 */
	static final String NAME_AWAIT_SHUTDOWN = "_awaitShutdown";

}
