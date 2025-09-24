package jp.reflexworks.taggingservice.migrate;

/**
 * サーバ追加・削除　定数クラス
 */
public interface BDBClientMaintenanceConst {

	/** バッチョジョブサーバへのデータ移行リクエストのタイムアウト(ミリ秒) */
	public static final String MIGRATE_REQUEST_TIMEOUT_MILLIS = "_migrate.request.timeout.millis";

	/** バッチョジョブサーバへのデータ移行リクエストのタイムアウト(ミリ秒) デフォルト値 */
	public static final int MIGRATE_REQUEST_TIMEOUT_MILLIS_DEFAULT = 60000;

	/** サーバ追加・削除の複数サーバ名指定時の区切り文字 */
	public static final String DELIMITER_SERVERNAME = ",";

}
