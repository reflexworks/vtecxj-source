package jp.reflexworks.taggingservice.provider;

import java.util.Locale;

import jp.reflexworks.taggingservice.api.RequestParam;

/**
 * 汎用API 定数クラス.
 */
public interface ProviderConst extends RequestParam {

	/** URLパラメータ : Feed形式セッションキャッシュ */
	public static final String PARAM_SESSIONFEED = "_sessionfeed";
	/** URLパラメータ : Entry形式セッションキャッシュ */
	public static final String PARAM_SESSIONENTRY = "_sessionentry";
	/** URLパラメータ : 文字列形式セッションキャッシュ */
	public static final String PARAM_SESSIONSTRING = "_sessionstring";
	/** URLパラメータ : 数値形式セッションキャッシュ */
	public static final String PARAM_SESSIONLONG = "_sessionlong";
	/** URLパラメータ : 数値形式セッションキャッシュのインクリメント */
	public static final String PARAM_SESSIONINCR = "_sessionincr";
	/** URLパラメータ : BigQuery */
	public static final String PARAM_BIGQUERY = "_bq";
	/** URLパラメータ : BigQuery 検索SQL実行 */
	public static final String PARAM_QUERY_BIGQUERY = "_querybq";
	/** URLパラメータ : BigQuery 更新SQL実行 */
	public static final String PARAM_EXEC_BIGQUERY = "_execbq";
	/** URLパラメータ : CSV */
	public static final String PARAM_CSV = "_csv";
	/** URLパラメータ : メール送信 */
	public static final String PARAM_SENDMAIL = "_sendmail";
	/** URLパラメータ : プッシュ通知 */
	public static final String PARAM_PUSHNOTIFICATION = "_pushnotification";
	/** URLパラメータ : メッセージキュー */
	public static final String PARAM_MESSAGEQUEUE = "_mq";
	/** URLパラメータ : メッセージキューステータス */
	public static final String PARAM_MESSAGEQUEUE_STATUS = "_mqstatus";
	/** URLパラメータ : 数値 */
	public static final String PARAM_NUM = "_num";
	/** URLパラメータ : RDB Query SQL実行 */
	public static final String PARAM_QUERY_RDB = "_queryrdb";
	/** URLパラメータ : RDB Exec SQL実行 */
	public static final String PARAM_EXEC_RDB = "_execrdb";
	/** URLパラメータ : BDB+BigQuery */
	public static final String PARAM_BDBQ = "_bdbq";
	/** URLパラメータ : プロパティ値取得 */
	public static final String PARAM_PROPERTY = "_property";

	/** リクエストヘッダ : サービス連携のサービス名 */
	public static final String HEADER_SERVICELINKAGE = "X-SERVICELINKAGE";
	/** リクエストヘッダ : サービス連携のサービス名小文字 */
	public static final String HEADER_SERVICELINKAGE_LOWER = HEADER_SERVICELINKAGE.toLowerCase(Locale.ENGLISH);
	/** リクエストヘッダ : サービス連携のサービスキー */
	public static final String HEADER_SERVICEKEY = "X-SERVICEKEY";
	/** リクエストヘッダ : サービス連携のサービスキー小文字 */
	public static final String HEADER_SERVICEKEY_LOWER = HEADER_SERVICEKEY.toLowerCase(Locale.ENGLISH);
	/** リクエストヘッダ : APIKey */
	public static final String HEADER_APIKEY = "X-APIKEY";
	/** リクエストヘッダ : APIKey小文字 */
	public static final String HEADER_APIKEY_LOWER = HEADER_APIKEY.toLowerCase(Locale.ENGLISH);

	/** メール送信 : to */
	public static final String SENDMAIL_TO = "to";
	/** メール送信 : cc */
	public static final String SENDMAIL_CC = "cc";
	/** メール送信 : bcc */
	public static final String SENDMAIL_BCC = "bcc";
	/** メール送信 : attachment */
	public static final String SENDMAIL_ATTACHMENT = "attachment";

	/** プッシュ通知 : to */
	public static final String PUSHNOTIFICATION_TO = "to";
	
	/** レスポンスメッセージ : BigQuery登録 */
	public static final String MSG_POST_BIGQUERY = "Registered in bigquery.";
	/** レスポンスメッセージ : BigQuery登録(非同期) */
	public static final String MSG_POST_BIGQUERY_ASYNC = "Data registration for BigQuery has been accepted.";
	/** レスポンスメッセージ : BigQuery削除(削除データの登録) */
	public static final String MSG_DELETE_BIGQUERY = "Deleted data has been registered in bigquery.";
	/** レスポンスメッセージ : BigQuery削除(削除データの登録)(非同期) */
	public static final String MSG_DELETE_BIGQUERY_ASYNC = "Data deletion for BigQuery has been accepted.";
	/** レスポンスメッセージ : メール送信 */
	public static final String MSG_SENDMAIL = "Your email has been sent.";
	/** レスポンスメッセージ : Push通知 */
	public static final String MSG_PUSHNOTIFICATION = "The push notification has been sent.";
	/** レスポンスメッセージ : メッセージキュー登録 */
	public static final String MSG_POST_MESSAGEQUEUE = "Registered in the message queue.";
	/** レスポンスメッセージ : メッセージキュー使用設定更新 */
	public static final String MSG_PUT_MESSAGEQUEUE_STATUS = "Message queue status updated.";
	/** レスポンスメッセージ : セッション値削除 */
	public static final String MSG_DELETE_SESSION_VALUE = "Session value deleted.";
	/** レスポンスメッセージ : キャッシュ値削除 */
	public static final String MSG_DELETE_CACHE_VALUE = "Cache value deleted.";
	/** レスポンスメッセージ : RDB更新 */
	public static final String MSG_EXEC_RDB = "SQL has been executed on the database.";
	/** レスポンスメッセージ : 非同期RDB更新 */
	public static final String MSG_EXEC_RDB_ASYNC = "Accepted SQL execution to the database.";
	

}
