package jp.reflexworks.taggingservice.pushnotification;

import java.util.Arrays;
import java.util.List;

import jp.reflexworks.taggingservice.api.SettingConst;

/**
 * サービスごとの設定項目一覧.
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
public interface ReflexPushNotificationSettingConst {

	/** Firebase Cloud Messaging(FCM)のGoogle CloudプロジェクトID */
	public static final String FCM_PROJECT_ID = "_fcm.projectid";
	/** Firebase Cloud Messaging(FCM)のサービスアカウント(Email形式) */
	public static final String FCM_SERVICEACCOUNT = "_fcm.serviceaccount";
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

	/**
	 * サービスの情報のみ使用し、システムの情報を無視する設定一覧.
	 */
	public static final List<String> IGNORE_SYSTEM_INFO =
			Arrays.asList(new String[]{
					FCM_SERVICEACCOUNT,
					FCM_PROJECT_ID,
			});

}
