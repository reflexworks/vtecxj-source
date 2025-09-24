package jp.reflexworks.batch;

/**
 * vtecxbatch 定数クラス
 */
public class VtecxBatchConst {

	/** プロパティファイル名 */
	public static final String PROPERTY_FILE_NAME = "vtecxbatch.properties";
	/** システムサービス名 */
	public static final String SYSTEM_SERVICE = "admin";
	/** プロパティ名 : プロジェクトID */
	public static final String PROP_PROJECTID = "_gcp.projectid";
	/** サービス名と名前空間の区切り文字 */
	public static final String DELIMITER_SERVICE_NAMESPACE = ":";
	/** サーバ名とURLの区切り文字 */
	public static final String DELIMITER_SERVER_URL = ":";
	/** 名前空間一覧の区切り文字 */
	public static final String DELIMITER_NAMESPACES = ",";

	// アクセスカウンタ
	/** アクセスカウンタ集計日算出のための、現在時刻からの経過時間(時) */
	public static final String PROP_ACCESSCOUNT_INTERVAL_HOUR = "_accesscount.interval.hour";
	/** アクセスカウンタ集計日算出のための、現在時刻からの経過時間(時) デフォルト値 */
	public static final int ACCESSCOUNT_INTERVAL_HOUR_DEFAULT = 12;

}
