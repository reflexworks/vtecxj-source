package jp.reflexworks.taggingservice.recaptcha;

/**
 * reCAPTCHA Enterprise サービスごとの設定
 * ここに定義されている項目は、/_settings/properties に設定できます。
 * サービスの情報のみ使用し、システムの情報を無視する設定は「IGNORE_SYSTEM_INFO」に「List<String>」形式で定義してください。
 * 
 * ```
 *   	public static final List<String> IGNORE_SYSTEM_INFO =
 *  			Arrays.asList(new String[]{
 * 					xxxx, ... 
 * 			});
 * ```
 */
public interface ReCaptchaSettingConst {
	
	/** サービス設定 : reCAPTCHA Enterprise サイトキー (v3) **/
	public static final String RECAPTCHA_SITEKEY = "_recaptcha.sitekey";
	/** サービス設定 : reCAPTCHA Enterprise サイトキー (v2) **/
	public static final String RECAPTCHA_SITEKEY_V2 = "_recaptcha.sitekey.v2";
	/** サービス設定 : reCAPTCHA Enterprise Google Cloud プロジェクトID **/
	public static final String RECAPTCHA_PROJECTID = "_recaptcha.projectid";
	/** サービス設定 : reCAPTCHA Enterprise サービスアカウント **/
	public static final String RECAPTCHA_SERVICEACCOUNT = "_recaptcha.serviceaccount";

}
