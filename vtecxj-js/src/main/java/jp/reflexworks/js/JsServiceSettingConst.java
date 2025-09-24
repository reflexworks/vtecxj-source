package jp.reflexworks.js;

/**
 * サービスごとの設定項目一覧.
 * ここに定義されている項目は、/_settings/properties に設定できます。
 */
public interface JsServiceSettingConst {

	/** 設定 : サーバサイドJS実行タイムアウト時間(秒) */
	public static final String JAVASCRIPT_EXECTIMEOUT = "_javascript.exectimeout";

	/** 設定 : バッチジョブのサーバサイドJS実行タイムアウト時間(秒) */
	public static final String JAVASCRIPT_BATCHJOBTIMEOUT = "_javascript.batchjobtimeout";

}
