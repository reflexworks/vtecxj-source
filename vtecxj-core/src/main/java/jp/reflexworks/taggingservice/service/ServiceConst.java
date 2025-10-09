package jp.reflexworks.taggingservice.service;

/**
 * サービス管理のための定数クラス.
 */
public interface ServiceConst {

	/** 設定 : stagingサービスの最大アクセス数 */
	public static final String PROP_MAX_ACCESS_COUNT = "_max.access.count";
	/** 
	 * 設定 : BaaSかどうか
	 * このフラグがtrueであれば以下を行う。
	 *   * アクセスカウンタを持つ
	 *   * サービス登録時、公開区分をstagingにする
	 */
	public static final String PROP_ENABLE_BAAS = "_enable.baas";

	/** stagingサービスの最大アクセス数 */
	public static final long MAX_ACCESS_COUNT_DEFAULT = 5000;

	/** 
	 * システム管理サービスのユーザ登録時に登録するフォルダ.
	 *   /@/_service/{サービス名} エントリーのエイリアスに
	 *   /{UID}/service/{サービス名} を付ける際に使用。
	 */
	public static final String URI_LAYER_SERVICE = "/service";
	
	// アクセスカウンタ
	/** URI : アクセスカウンタキー部分 */
	public static final String URI_LAYER_ACCESS_COUNT = "/access_count";
	/** URI : アクセスカウンタ当日キー部分 */
	public static final String URI_LAYER_TODAY = "/today";
	/** URI : ストレージ合計容量キー */
	public static final String URI_LAYER_STORAGE_TOTALSIZE = "/storage_totalsize";
	/** メッセージ : stagingサービスで最大アクセス数を超えた場合 */
	public static final String MSG_OVER_ACCESS_COUNT = "The number of accesses has been exceeded.";
	
}
