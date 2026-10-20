package jp.reflexworks.taggingservice.blogic;

import jp.reflexworks.atom.api.AtomConst;
import jp.reflexworks.taggingservice.api.SettingConst;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;

/**
 * メッセージキュー定数クラス.
 */
public interface MessageQueueConst {

	/** 設定 : メッセージキュー有効期限(分) サービスごとに設定可 */
	public static final String MESSAGEQUEUE_EXPIRE_MIN = SettingConst.MESSAGEQUEUE_EXPIRE_MIN;
	/** 設定 : デバッグログエントリー出力フラグ サービスごとに設定 **/
	public static final String DEBUGLOG_NOTIFICATION = SettingConst.DEBUGLOG + "notification";
	/** 設定 : メッセージキューのアクセスログを出力するかどうか */
	public static final String MESSAGEQUEUE_ENABLE_ACCESSLOG = "_messagequeue.enable.accesslog";
	/** 設定 : メッセージキュー取得後の非同期削除待ち時間(ミリ秒) */
	public static final String MESSAGEQUEUE_DELETE_WAITMILLIS = "_messagequeue.delete.wailtmillis";
	/** 設定 : メッセージキュー取得後の削除が排他エラーの場合のリトライ回数 */
	public static final String MESSAGEQUEUE_DELETE_RETRY_COUNT = "_messagequeue.delete.retry.count";
	/** 設定 : メッセージキュー取得後の削除が排他エラーの場合のリトライ待ち時間(ミリ秒) */
	public static final String MESSAGEQUEUE_DELETE_RETRY_WAITMILLIS = "_messagequeue.delete.retry.waitmillis";

	/** 設定デフォルト : メッセージキュー有効期限(分) */
	public static final int MESSAGEQUEUE_EXPIRE_MIN_DEFAULT = 5;
	/** 設定デフォルト : メッセージキュー取得後の非同期削除待ち時間(ミリ秒) */
	public static final int MESSAGEQUEUE_DELETE_WAITMILLIS_DEFAULT = 250;
	/** 設定デフォルト : メッセージキュー取得後の削除が排他エラーの場合のリトライ回数 */
	public static final int MESSAGEQUEUE_DELETE_RETRY_COUNT_DEFAULT = 10;
	/** 設定デフォルト : メッセージキュー取得後の削除が排他エラーの場合のリトライ待ち時間(ミリ秒) */
	public static final int MESSAGEQUEUE_DELETE_RETRY_WAITMILLIS_DEFAULT = 500;
	
	/** URI : メッセージキュー */
	public static final String URI_MQ = "/_mq";
	/** URI階層 : mqstatus */
	public static final String URI_LAYER_MQSTATUS = "/mqstatus";
	/** メッセージキューURIの、チャネル(URLエンコード)とUIDの分割記号 */
	public static final String DELIMITER_CHANNEL_URI = "@";
	/** チャネルのスラッシュ変換値 */
	public static final String REPLACEMENT_SLASH = "[]";
	/** チャネルのスラッシュ変換値 */
	public static final String REPLACEMENT_SLASH_REGEX = "\\[\\]";
	/** メッセージキューURI親階層の長さ /_mq/ */
	public static final int URI_MQ_LEN1 = TaggingEntryUtil.editSlash(URI_MQ).length();

	/** 送信先を表すREL */
	public static final String REL_TO = "to";
	/** 送信先 : ポーリング */
	public static final String POLLING = "#";
	/** 送信先 : すべて */
	public static final String WILDCARD = AtomConst.GROUP_WILDCARD;
	
	/** Push通知を行わないフラグの値 */
	public static final String TRUE = "true";
	
	/** Push通知オフ設定URI階層 (グループ/{UID}/disable_notification) */
	public static final String URI_DISABLE_NOTIFICATION = "/disable_notification";
	/** Push通知オフ設定 */
	public static final String URN_DISABLE_NOTIFICATION = Constants.URN_PREFIX + "disable.notification:";
	/** Push通知オフ設定 文字列長 */
	public static final int URN_DISABLE_NOTIFICATION_LEN = URN_DISABLE_NOTIFICATION.length();

	/** メッセージキュー親フォルダのACL設定 : グループ */
	public static final String ACL_TYPE_MQ_GROUP = AclConst.ACL_TYPE_CREATE + AclConst.ACL_TYPE_EXTERNAL;
	/** メッセージキュー親フォルダのACL設定 : 本人 */
	public static final String ACL_TYPE_MQ_UID = AclConst.ACL_TYPE_DELETE +
			AclConst.ACL_TYPE_RETRIEVE + AclConst.ACL_TYPE_EXTERNAL;
	
	/** デバッグログエントリー タイトル */
	public static final String DEBUGLOG_TITLE = "MessageQueue";
	/** デバッグログエントリー サブタイトル */
	public static final String DEBUGLOG_SUBTITLE = "INFO";
	
	/** メッセージキューのキーのselfidの文字列長 */
	public static final int MQ_NUM_LEN = 20;
	/**
	 * メッセージキューのキーのselfidに加算する値.
	 * 送信先ごとの加算キー(/_mq/{チャネルの変換値}@{UID})で採番していた時の
	 * メッセージより必ず大きい番号とし、キーの重複・順序の逆転を防ぐ。
	 */
	public static final long MQ_NUM_OFFSET = 1_000_000_000_000_000L;
	/** 検索がフェッチ最大数を超えた場合のフラグ */
	static final String MARK_FETCH_LIMIT = Constants.MARK_FETCH_LIMIT;

}
