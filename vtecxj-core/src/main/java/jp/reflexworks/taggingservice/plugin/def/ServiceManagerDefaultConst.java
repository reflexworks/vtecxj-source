package jp.reflexworks.taggingservice.plugin.def;

/**
 * サービス管理の定数クラス
 */
public interface ServiceManagerDefaultConst {

	/** サービス名に使用できる文字パターン */
	public static final String PATTERN_STR_SERVICENAME = "^[0-9a-zA-Z_-]+$";

	/** サービスの予約語設定 "_service.reserved.{連番}={予約語} */
	public static final String PROP_SERVICE_RESERVED_PREFIX = "_service.reserved.";
	/** 設定 : アクセスカウンタの有効時間(時) */
	public static final String PROP_ACCESSCOUNT_EXPIRE_HOUR = "_accesscount.expire.hour";
	/** 設定 : サービス初期フォルダ "_createservice.postfolder.{フォルダの登録順}={URI}" */
	public static final String PROP_CREATESERVICE_POSTFOLDER_PREFIX = "_createservice.postfolder.";
	/** 設定 : サービス初期フォルダのACL "_createservice.postfolderacl.{フォルダの登録順}.{連番}={ACL}" */
	public static final String PROP_CREATESERVICE_POSTFOLDERACL_PREFIX = "_createservice.postfolderacl.";
	/** サービス設定処理のアクセスログ（処理経過ログ）を出力するかどうか */
	public static final String SERVICESETTING_ENABLE_ACCESSLOG = "_servicesetting.enable.accesslog";
	/** 設定デフォルト値 : アクセスカウンタの有効時間(時) */
	public static final int ACCESSCOUNT_EXPIRE_HOUR_DEFAULT = 72;

	/** サービスを表す記号 */
	public static final String MARK_SERVICE = "@";

}
