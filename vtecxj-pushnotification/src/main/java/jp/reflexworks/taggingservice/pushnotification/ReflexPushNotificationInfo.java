package jp.reflexworks.taggingservice.pushnotification;

import java.util.List;

import jp.reflexworks.taggingservice.pushnotification.ReflexPushNotificationConst.PushType;

/**
 * Push通知情報クラス.
 */
public class ReflexPushNotificationInfo {
	
	/** UID */
	String uid;
	/** Push通知方法 */
	PushType type;
	/** Push通知Token */
	List<String> pushTokens;
	/** バッジ数 */
	Long badge;
	
	/**
	 * Push通知情報 コンストラクタ
	 * @param uid UID
	 * @param type Push通知方法
	 * @param pushToken Push通知Token
	 * @param badge バッジ数
	 */
	ReflexPushNotificationInfo(String uid, PushType type, List<String> pushTokens, Long badge) {
		this.uid = uid;
		this.type = type;
		this.pushTokens = pushTokens;
		this.badge = badge;
	}

}
