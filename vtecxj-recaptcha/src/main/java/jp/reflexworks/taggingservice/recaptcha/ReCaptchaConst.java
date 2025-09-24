package jp.reflexworks.taggingservice.recaptcha;

import jp.reflexworks.taggingservice.util.Constants;

/**
 * reCAPTCHA Enterprise 定数インターフェース
 */
public interface ReCaptchaConst {

	/** サービスアカウント秘密鍵ファイル名 */
	public static final String RECAPTCHA_FILE_SECRET = "_recaptcha.file.secret";
	/** デフォルト設定 : Google Cloud プロジェクトID **/
	public static final String GCP_PROJECTID = "_gcp.projectid";

	/** メモリ上のstaticオブジェクト格納キー */
	public static final String STATIC_NAME_RECAPTCHA = "_recaptcha";

	/** コネクション情報格納キー */
	public static final String CONNECTION_INFO_RECAPTCHA ="_recaptcha";

	/** reCAPTCHA Enterpriseサービスアカウント秘密鍵JSON格納キー */
	public static final String URI_SECRET_JSON = Constants.URI_SETTINGS + "/recaptcha.json";

	/** reCAPTCHA Enterprise ウェブサイト評価のためのリクエスト設定がされていない場合のエラーメッセージ */
	public static final String MSG_NO_SETTINGS = "ReCaptcha information is required.";

}
