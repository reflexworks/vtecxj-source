package jp.reflexworks.taggingservice.blogic;

/**
 * セキュリティチェックで使用する定数
 */
public interface SecurityConst {

	/** reCAPTCHAのトークン (v3) */
	public static final String REQUEST_PARAM_RECAPTCHA_TOKEN = "g-recaptcha-token";
	/** reCAPTCHAのレスポンス (v2) */
	public static final String REQUEST_PARAM_RECAPTCHA_RESPONSE = "g-recaptcha-response";

	/** 設定 : reCAPTCHA認証リトライ回数 */
	public static final String PROP_RECAPTCHA_RETRY_COUNT = "_recaptcha.retry.count";
	/** 設定 : reCAPTCHA認証リトライ時のスリープ時間(ミリ秒) */
	public static final String PROP_RECAPTCHA_RETRY_WAITMILLIS = "_recaptcha.retry.waitmillis";
	/** 設定 : reCAPTCHA認証リクエストタイムアウト時間(ミリ秒) */
	public static final String PROP_RECAPTCHA_REQUEST_TIMEOUT_MILLIS = "_recaptcha.request.timeout.millis";
	/** 設定 : reCAPTCHAのスコアしきい値 (v3) (この値以下だとエラーになる) */
	public static final String PROP_RECAPTCHA_SCORE_THRESHOLD = "_recaptcha.score.threshold";
	/** 設定デフォルト値 : reCAPTCHA認証リトライ回数 */
	public static final int RECAPTCHA_RETRY_COUNT_DEFAULT = 2;
	/** 設定デフォルト値 : reCAPTCHA認証リトライ時のスリープ時間(ミリ秒) */
	public static final int RECAPTCHA_RETRY_WAITMILLIS_DEFAULT = 200;
	/** 設定デフォルト値 : reCAPTCHA認証リクエストタイムアウト時間(ミリ秒) */
	public static final int RECAPTCHA_REQUEST_TIMEOUT_MILLIS_DEFAULT = 30000;
	/** 設定デフォルト値 : reCAPTCHAのスコアしきい値 (v3) */
	public static final double RECAPTCHA_SCORE_THRESHOLD_DEFAULT = 0;

	/** キャプチャチェックアクション: ログイン */
	public static final String CAPTCHA_ACTION_LOGIN = "login";
	/** キャプチャチェックアクション: パスワードリセットメール送信 */
	public static final String CAPTCHA_ACTION_PASSRESET = "passreset";
	/** キャプチャチェックアクション: ユーザ登録 */
	public static final String CAPTCHA_ACTION_ADDUSER = "adduser";

}
