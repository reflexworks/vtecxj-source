package jp.reflexworks.taggingservice.pushnotification;

import jp.reflexworks.taggingservice.api.SettingConst;
import jp.reflexworks.taggingservice.util.Constants;

/**
 * Push通知 定数クラス
 */
public interface ReflexPushNotificationConst {

	/** 設定 : Push通知のアクセスログを出力するかどうか */
	public static final String PUSHNOTIFICATION_ENABLE_ACCESSLOG = "_pushnotification.enable.accesslog";
	/** 設定 : FCM Push通知に失敗したときのリトライ回数 **/
	public static final String FCM_PUSH_RETRY_COUNT = "_fcm.push.retry.count";
	/** 設定 : FCM Push通知リトライ時のスリープ時間(ミリ秒) **/
	public static final String FCM_PUSH_RETRY_WAITMILLIS = "_fcm.push.retry.waitmillis";
	/** 設定 : デバッグログエントリー出力フラグ サービスごとに設定 **/
	public static final String DEBUGLOG_NOTIFICATION = SettingConst.DEBUGLOG + "notification";
	/** 設定デフォルト : FCM Push通知失敗時のリトライ回数デフォルト値 */
	public static final int FCM_PUSH_RETRY_COUNT_DEFAULT = 2;
	/** 設定デフォルト : FCM Push通知失敗でリトライ時のスリープ時間デフォルト値 */
	public static final int FCM_PUSH_RETRY_WAITMILLIS_DEFAULT = 200;

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

}
