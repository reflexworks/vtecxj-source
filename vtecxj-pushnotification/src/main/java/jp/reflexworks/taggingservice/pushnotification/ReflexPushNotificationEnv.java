package jp.reflexworks.taggingservice.pushnotification;

import com.google.auth.oauth2.GoogleCredentials;
/**
 * Push Notifidation用staticクラス
 */
public class ReflexPushNotificationEnv {

	/** 
	 * Googleデフォルト認証情報
	 *  (環境変数 GOOGLE_APPLICATION_CREDENTIALS 等から自動取得)
	 */
    private GoogleCredentials googleCredentials;

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
