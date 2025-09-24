package jp.reflexworks.taggingservice.recaptcha;

/**
 * reCAPTCHA Enterprise サービスごとの設定
 */
public interface ReCaptchaSettingConst {
	
	/** サービス設定 : reCAPTCHA Enterprise サイトキー (v3) **/
	public static final String RECAPTCHA_SITEKEY = "_recaptcha.sitekey";
	/** サービス設定 : reCAPTCHA Enterprise サイトキー (v2) **/
	public static final String RECAPTCHA_SITEKEY_V2 = "_recaptcha.sitekey.v2";
	/** サービス設定 : reCAPTCHA Enterprise Google Cloud プロジェクトID **/
	public static final String RECAPTCHA_PROJECTID = "_recaptcha.projectid";

}
