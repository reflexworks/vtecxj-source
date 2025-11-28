package jp.reflexworks.js;

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
public interface JsServiceSettingConst {

	/** 設定 : サーバサイドJS実行タイムアウト時間(秒) */
	public static final String JAVASCRIPT_EXECTIMEOUT = "_javascript.exectimeout";

	/** 設定 : バッチジョブのサーバサイドJS実行タイムアウト時間(秒) */
	public static final String JAVASCRIPT_BATCHJOBTIMEOUT = "_javascript.batchjobtimeout";

}
