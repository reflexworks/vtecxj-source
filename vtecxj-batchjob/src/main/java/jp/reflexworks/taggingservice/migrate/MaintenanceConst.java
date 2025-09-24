package jp.reflexworks.taggingservice.migrate;

import jp.reflexworks.taggingservice.util.Constants;

/**
 * サーバ追加・削除 定数クラス
 */
public interface MaintenanceConst {

	/** サービスステータス : サーバ追加・削除処理の対象 */
	public static final String SERVICE_STATUS_TARGET = Constants.SERVICE_STATUS_PRODUCTION;
	/** サービスステータス : メンテナンス中(1) メンテナンス開始 */
	public static final String SERVICE_STATUS_MAINTENANCE = Constants.SERVICE_STATUS_MAINTENANCE;
	/** サービスステータス : サーバ追加・削除処理の対象 */
	public static final String SERVICE_STATUS_MAINTENANCE_FAILURE= Constants.SERVICE_STATUS_MAINTENANCE_FAILURE;
	/** サーバ追加・削除処理の進捗区分 : メンテナンス中(1) メンテナンス開始 */
	public static final String PROGRESS_MAINTENANCE_1 = "maintenance_1";
	/** サーバ追加・削除処理の進捗区分 : メンテナンス中(2) サーバ情報更新済み、データ移行中 */
	public static final String PROGRESS_MAINTENANCE_2 = "maintenance_2";

}
