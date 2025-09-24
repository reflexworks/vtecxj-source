package jp.reflexworks.js.async;

/**
 * サーバサイドJS非同期処理 定数クラス
 */
public class JsAsyncConst {

	/** 設定 : バッチジョブサーバURL */
	public static final String PROP_URL_JSASYNC = "_url.jsasync";
	/** 設定 : バッチジョブサーバへのサーバサイドJS非同期処理リクエストタイムアウト(ミリ秒) */
	public static final String JSASYNC_REQUEST_TIMEOUT_MILLIS = "_jsasync.request.timeout.millis";

	/** 設定デフォルト値 : バッチジョブサーバへのサーバサイドJS非同期処理リクエストタイムアウト(ミリ秒) */
	public static final int JSASYNC_REQUEST_TIMEOUT_MILLIS_DEFAULT = 600000;

	/** Request Header value : JsAsync */
	public static final String X_REQUESTED_WITH_JSASYNC = "JsAsync";

}
