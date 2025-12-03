package jp.reflexworks.taggingservice.pushnotification;

import java.util.Arrays;
import java.util.List;

import jp.reflexworks.taggingservice.util.Constants;

/**
 * Push通知 定数クラス
 */
public interface ReflexPushNotificationConst {

	/** 設定 : Push通知のアクセスログを出力するかどうか */
	public static final String PUSHNOTIFICATION_ENABLE_ACCESSLOG = "_pushnotification.enable.accesslog";

	/** Firebaseサービスアカウント秘密鍵JSON格納キー */
	public static final String URI_SECRET_JSON = Constants.URI_SETTINGS + "/firebase.json";

	/** URI : Push通知登録トークン設定(階層) */
	public static final String URI_PUSH_NOTIFICATION = "/push_notification";

	/** Pusht通知リクエスト先区分: FCM, EXPO */
	public enum PushType { FCM, EXPO };

	/** URN : FCM登録トークン */
	public static final String URN_PREFIX_FCM = Constants.URN_PREFIX + "fcm:";
	/** URN : FCM登録トークンの長さ */
	public static final int URN_PREFIX_FCM_LEN = URN_PREFIX_FCM.length();

	/** URN : Expo登録トークン */
	public static final String URN_PREFIX_EXPO = Constants.URN_PREFIX + "expo:";
	/** URN : Expo登録トークンの長さ */
	public static final int URN_PREFIX_EXPO_LEN = URN_PREFIX_EXPO.length();

	/** Expo push通知のdataに設定するキー : message */
	public static final String MESSAGE = "message";
	/** Expo push通知のdataに設定するキー : imageurl */
	public static final String IMAGEURL = "imageurl";

	/** デバッグログエントリー タイトル Notification */
	public static final String DEBUGLOG_TITLE_NOTIFICATION = "PushNotification";
	/** デバッグログエントリー タイトル FCM */
	public static final String DEBUGLOG_TITLE_FCM = "PushFCM";
	/** デバッグログエントリー タイトル Expo */
	public static final String DEBUGLOG_TITLE_EXPO = "PushExpo";
	/** デバッグログエントリー サブタイトル */
	public static final String DEBUGLOG_SUBTITLE = "INFO";
	/** デバッグログエントリー サブタイトル */
	public static final String DEBUGLOG_SUBTITLE_WARN = "WARN";
	
	/** メモリ上のstaticオブジェクト格納キー : Push Notification環境情報 */
	public static final String STATIC_NAME_PUSHNOTIFICATION_ENV = "_pushnotification_env";

	/** FCM Workload Identity 権限借用のscope */
	public static final List<String> SCOPES = Arrays.asList("https://www.googleapis.com/auth/firebase.messaging");

}
