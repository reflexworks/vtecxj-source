package jp.reflexworks.taggingservice.recaptcha;

import java.util.Arrays;
import java.util.List;

import jp.reflexworks.taggingservice.util.Constants;

/**
 * reCAPTCHA Enterprise 定数インターフェース
 */
public interface ReCaptchaConst {

	/** scopes */
	public static final List<String> SCOPES = Arrays.asList("https://www.googleapis.com/auth/cloud-platform");
	
	/** サービスアカウント秘密鍵ファイル名 */
	public static final String RECAPTCHA_FILE_SECRET = "_recaptcha.file.secret";
	/** デフォルト設定 : Google Cloud プロジェクトID **/
	public static final String GCP_PROJECTID = "_gcp.projectid";

	/** メモリ上のstaticオブジェクト格納キー : reCAPTCHA */
	public static final String STATIC_NAME_RECAPTCHA_ENV = "_recaptcha_env";

	/** メモリ上のstaticオブジェクト格納キー : Googleのデフォルト認証情報 */
	public static final String STATIC_NAME_GOOGLE_CREDENTIALS = "_google_credentials";

	/** コネクション情報格納キー */
	public static final String CONNECTION_INFO_RECAPTCHA ="_recaptcha";

	/** reCAPTCHA Enterpriseサービスアカウント秘密鍵JSON格納キー */
	public static final String URI_SECRET_JSON = Constants.URI_SETTINGS + "/recaptcha.json";

	/** reCAPTCHA Enterprise ウェブサイト評価のためのリクエスト設定がされていない場合のエラーメッセージ */
	public static final String MSG_NO_SETTINGS = "ReCaptcha information is required.";

	/** トークンの有効期限(秒) */
	public static final int TOKEN_EXPIRE_SEC = 3600;
	
	/** reCAPTCHA Enterprise接続オブジェクトキャッシュの最大格納数 */
	public static final int CACHE_MAXSIZE = 500;
	/** reCAPTCHA Enterprise接続オブジェクトキャッシュの有効期間(分) */
	public static final int CACHE_EXPIRE_MIN = 30;

}
