package jp.reflexworks.taggingservice.service;

/**
 * サービス管理のための定数クラス.
 */
public interface ServiceConst {

	/** 設定 : stagingサービスの最大アクセス数 */
	public static final String PROP_MAX_ACCESS_COUNT = "_max.access.count";
	/** 設定 : 1日のバッチジョブ最大実行時間(分)  */
	public static final String PROP_MAX_BATCHJOB_EXECUTIONTIME_MIN = "_max.batchjob.executiontime.min";
	/** 
	 * 設定 : BaaSかどうか
	 * このフラグがtrueであれば以下を行う。
	 *   * アクセスカウンタを持つ
	 *   * サービス登録時、公開区分をstagingにする
	 */
	public static final String PROP_ENABLE_BAAS = "_enable.baas";

	/** stagingサービスの最大アクセス数 */
	public static final long MAX_ACCESS_COUNT_DEFAULT = 50000;
	/** 1日のバッチジョブ最大実行時間(分) デフォルト値 */
	public static final int MAX_BATCHJOB_EXECUTIONTIME_MIN_DEFAULT = 180;

	/** 
	 * システム管理サービスのユーザ登録時に登録するフォルダ.
	 *   /@/_service/{サービス名} エントリーのエイリアスに
	 *   /{UID}/service/{サービス名} を付ける際に使用。
	 */
	public static final String URI_LAYER_SERVICE = "/service";
	
	/** URI : アクセスカウンタキー部分 */
	public static final String URI_LAYER_ACCESS_COUNT = "/access_count";
	/** URI : アクセスカウンタ当日キー部分 */
	public static final String URI_LAYER_TODAY = "/today";
	/** URI : ストレージ合計容量キー */
	public static final String URI_LAYER_STORAGE_TOTALSIZE = "/storage_totalsize";
	/** URI : バッチジョブ実行時間キー部分 */
	public static final String URI_LAYER_BATCHJOB_EXECTIME = "/batchjob_exectime";
	
	/** メッセージ : stagingサービスで最大アクセス数を超えた場合 */
	public static final String MSG_OVER_ACCESS_COUNT = "The number of accesses has been exceeded.";
	/** メッセージ : stagingサービスでバッチジョブ最大実行時間を超えた場合 */
	public static final String MSG_OVER_BATCHJOB_EXECTIME = "The batch job execution time is exceeded.";
	
}
