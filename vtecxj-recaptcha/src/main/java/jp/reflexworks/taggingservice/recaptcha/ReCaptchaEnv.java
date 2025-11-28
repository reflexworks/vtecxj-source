package jp.reflexworks.taggingservice.recaptcha;

import com.google.auth.oauth2.GoogleCredentials;

/**
 * reCAPTCHA Enterprise用staticクラス
 */
public class ReCaptchaEnv {

	/** システム管理サービスの秘密鍵ファイル */
	private byte[] defaultSecret;
	
	/** 
	 * Googleデフォルト認証情報
	 *  (環境変数 GOOGLE_APPLICATION_CREDENTIALS 等から自動取得)
	 */
    private GoogleCredentials googleCredentials;

	/**
	 * システム管理サービスの秘密鍵ファイルを取得.
	 * @return システム管理サービスの秘密鍵ファイル
	 */
	public byte[] getDefaultSecret() {
		return defaultSecret;
	}

	/**
	 * システム管理サービスの秘密鍵ファイルをセット.
	 * @param serviceName サービス名
	 * @param defaultSecret システム管理サービスの秘密鍵ファイル
	 */
	void setDefaultSecret(byte[] defaultSecret) {
		this.defaultSecret = defaultSecret;
	}

	/**
	 * Googleデフォルト認証情報を取得
	 * @return Googleデフォルト認証情報を取得
	 */
	public GoogleCredentials getGoogleCredentials() {
		return googleCredentials;
	}

	/**
	 * Googleデフォルト認証情報を設定
	 * @param Googleデフォルト認証情報
	 */
	void setGoogleCredentials(GoogleCredentials googleCredentials) {
		this.googleCredentials = googleCredentials;
	}

}
