package jp.reflexworks.taggingservice.api;

import java.util.Arrays;
import java.util.List;

/**
 * サービスごとに指定する設定
 */
public interface SettingConst {

	/** サービス設定 : エントリー最大数デフォルト設定 **/
	public static final String ENTRY_NUMBER_DEFAULT = "_entry.number.default";
	/** サービス設定 : 検索条件除外設定 **/
	public static final String IGNORE_CONDITION_PREFIX = "_ignore.condition.";
	/** サービス設定 : エラー画面表示URLパターン */
	public static final String ERRORPAGE_PREFIX = "_errorpage.";
	/** RXIDのカウント指定URLパターン */
	public static final String RXID_COUNTER_PREFIX = "_rxid.counter.";
	/** RXID有効時間(分)設定 **/
	public static final String RXID_MINUTE = "_rxid.minute";
	/** セッション有効時間(分)設定 **/
	public static final String SESSION_MINUTE = "_session.minute";
	/** サービス設定 : EMail情報 */
	public static final String MAIL_PREFIX = "_mail.";
	/** IPアドレスホワイトリスト設定(サービス管理者) **/
	public static final String WHITE_REMOTEADDR_PREFIX = "_white.remoteaddress.";
	/** BigQueryのプロジェクトID */
	public static final String BIGQUERY_PROJECTID = "_bigquery.projectid";
	/** BigQueryのデータセット名 */
	public static final String BIGQUERY_DATASET = "_bigquery.dataset";
	/** BigQueryのロケーション */
	public static final String BIGQUERY_LOCATION = "_bigquery.location";
	/** JSON出力においてfeed.entryを省略するかどうか */
	public static final String JSON_STARTARRAYBRACKET = "_json.startarraybracket";
	/** バッチジョブ **/
	public static final String BATCHJOB_PREFIX = "_batchjob.";
	/** 検索でインメモリソートなど処理中の場合のリトライ回数 **/
	public static final String PROCESSING_GET_RETRY_COUNT = "_processing.get.retry.count";
	/** 検索でインメモリソートなど処理中の場合の待ち時間(ミリ秒) **/
	public static final String PROCESSING_GET_RETRY_WAITMILLIS = "_processing.get.retry.waitmillis";
	/** ログアラートのレベルパターン設定 **/
	public static final String LOGALERT_LEVEL_PATTERN = "_logalert.level.pattern";
	/** ログアラートメール送信のメールアドレス設定 **/
	public static final String LOGALERT_MAIL_ADDRESS = "_logalert.mail.address";
	/** 認証コードの桁数 **/
	public static final String VERIFY_CODE_LENGTH = "_verify.code.length";
	/** 認証コードに英字も含むか **/
	public static final String ENABLE_ALPHABET_VERIFY = "_enable.alphabet.verify";
	/** 認証コード検証失敗可能回数 **/
	public static final String VERIFY_FAILED_COUNT = "_verify.failed.count";
	/** 認証失敗許容数設定 **/
	public static final String AUTH_FAILED_COUNT = "_auth.failed.count";
	/** 認証失敗許容数保持の有効時間(秒) */
	public static final String AUTH_FAILED_COUNT_EXPIRE = "_auth.failed.count.expire";
	/** URLフェッチのタイムアウト時間(ミリ秒) */
	public static final String URLFETCH_TIMEOUTMILLIS = "_urlfetch.timeoutmillis";
	/** アプリリダイレクトの対象URL設定 接頭辞 */
	public static final String REDIRECT_APP_PREFIX = "_redirect_app.";
	/** 旧バージョン */
	public static final String LEGACY = "_legacy";
	/** デバッグログエントリー出力 接頭辞 **/
	public static final String DEBUGLOG = "_debuglog.";
	/** メッセージキュー有効期限(分) **/
	public static final String MESSAGEQUEUE_EXPIRE_MIN = "_messagequeue.expire.min";
	/** サービス設定 : CORS(クロスドメイン) Origin **/
	public static final String CORS_ORIGIN = "_cors.origin";
	/** サービス設定 : reCAPTCHA 秘密鍵 (v3) (旧) **/
	public static final String RECAPTCHA_SECRETKEY = "_recaptcha.secretkey";
	/** サービス設定 : reCAPTCHA 秘密鍵 (v2) (旧) **/
	public static final String RECAPTCHA_SECRETKEY_V2 = "_recaptcha.secretkey.v2";
	/** WSSEにおいてキャプチャ不要な失敗回数 */
	public static final String WSSE_WITHOUT_CAPTCHA = "_wsse.without.captcha";
	/** RDB設定 */
	public static final String RDB = "_rdb.";

	/**
	 * サービスの情報が存在する場合、システムの情報を無視する設定一覧.
	 * <p>
	 * 条件を複数取得する、前方一致指定の項目に有効。
	 * 追加があればString配列に項目を追加すること。
	 * </p>
	 */
	public static final List<String> IGNORE_SYSTEM_IF_EXIST_SERVICE_INFO =
			Arrays.asList(new String[]{MAIL_PREFIX, BIGQUERY_PROJECTID, BIGQUERY_DATASET,
					BIGQUERY_LOCATION});

	/** ユーザ初期エントリー設定 : ユーザ番号に置き換える記号 */
	public static final String SETTING_USERINIT_UID = "#";

	/** Patternオブジェクトに変換対象設定一覧 */
	public static final List<String> SETTING_PATTERNS =
			Arrays.asList(new String[]{LOGALERT_LEVEL_PATTERN});

}
