package jp.reflexworks.taggingservice.subscription;

import jp.reflexworks.taggingservice.util.Constants;

/**
 * サブスクライバー 定数クラス.
 */
public interface SubscriptionConst {

	/** 設定 : 再起動中フラグのキャッシュタイムアウト(秒) */
	public static final String PROP_REBOOT_CACHE_EXPIRE_SEC = "_logalert.reboot.cache.expire.sec";
	/** 設定 : サブスクリプショントークン検証リクエストタイムアウト(ミリ秒) */
	public static final String PROP_LOGALERT_REQUEST_TIMEOUT_MILLIS = "_logalert.request.timeout.millis";
	/** 再起動中フラグのキャッシュタイムアウト(秒)デフォルト値 */
	public static final int REBOOT_CACHE_EXPIRE_SEC_DEFAULT = 1200;
	/** 設定 : サブスクリプショントークン検証リクエストタイムアウト(ミリ秒)デフォルト値 */
	public static final int LOGALERT_REQUEST_TIMEOUT_MILLIS_DEFAULT = 30000;

	/** PathInfo : OutOfMemory */
	public static final String PATHINFO_OOM = "/oom";
	/** PathInfo : Maintenance notice */
	public static final String PATHINFO_MAINTENANCE_NOTICE = "/maintenance_notice";

	/** JSON key : message */
	public static final String MESSAGE = "message";
	/** JSON key : data */
	public static final String DATA = "data";
	/** JSON key : email */
	public static final String EMAIL = "email";
	/** JSON key : email_verified */
	public static final String EMAIL_VERIFIED = "email_verified";
	/** JSON key : aud */
	public static final String AUD = "aud";

	/** ログシンクからのメッセージ項目 : textPayload */
	public static final String LOGSINK_TEXTPAYLOAD = "textPayload";
	/** ログシンクからのメッセージ項目 : resource */
	public static final String LOGSINK_RESOURCE = "resource";
	/** ログシンクからのメッセージ項目 : labels */
	public static final String LOGSINK_LABELS = "labels";
	/** ログシンクからのメッセージ項目 : pod_name */
	public static final String LOGSINK_POD_NAME = "pod_name";

	/** OutOfMemoryError発生ログの前方一致条件 */
	public static final String MESSAGE_OOM_PREFIX = "java.lang.OutOfMemoryError: ";

	/** Bearer Tokenの検証リクエスト先 */
	public static final String URL_TOKEN = "https://oauth2.googleapis.com/tokeninfo?id_token=";
	/** Bearer Tokenの検証リクエストメソッド */
	public static final String METHOD_TOKEN = Constants.GET;

	/** Bearer Tokenのキャッシュキー接頭辞 (/_logalert/token//{トークン}) */
	public static final String BEARERTOKEN_CACHEKEY_PREFIX = "/_logalert/token/";
	/** 設定 : Bearer Tokenのキャッシュタイムアウト(秒) */
	public static final String PROP_BEARERTOKEN_CACHE_EXPIRE_SEC = "_logalert.token.cache.expire.sec";
	/** Bearer Tokenのキャッシュタイムアウト(秒)デフォルト値 */
	public static final int BEARERTOKEN_CACHE_EXPIRE_SEC_DEFAULT = 3600;

	/** 再起動中フラグのキャッシュキー接頭辞 (/_logalert/name/{PodまたはDeployment名}) */
	public static final String REBOOT_CACHEKEY_PREFIX = "/_logalert/reboot/";
	/** 再起動中フラグ */
	public static final String REBOOT_VALUE = "rebooting";

	/** bearer区切り文字(正規表現) */
	public static final String BEARER_DELIMITER_REGEX = "\\.";

	/** 設定 : pushエンドポイントURLのサーブレットパス */
	public static final String PROP_SERVLETPATH = "_logalert.servletpath";
	/** pushエンドポイントURLのサーブレットパス デフォルト値 */
	public static final String SERVLETPATH_DEFAULT = "/l/";
	/** 設定 : サブスクリプション認証サービスアカウント */
	public static final String PROP_SERVICEACCOUNT = "_logalert.serviceaccount";
	/** 設定 : kubenetes管理サービスアカウント (サブスクリプション認証サービスアカウント指定がない場合に使用) */
	public static final String PROP_SERVICEACCOUNT_KUBECTL = "_kubectl.serviceaccount";

	/** URI : メンテナンス通知メール送信内容 */
	public static final String URI_SETTINGS_MAINTENANCE_NOTICE = "/_settings/maintenance_notice";

	/** メッセージ置き換え文字列 : NOTICE */
	public static final String REPLACE_REGEX_NOTICE = "\\$\\{NOTICE\\}";

}
