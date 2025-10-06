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
	//public static final String PROP_CREATESERVICE_POSTFOLDER_PREFIX = "_createservice.postfolder.";
	/** 設定 : サービス初期フォルダのACL "_createservice.postfolderacl.{フォルダの登録順}.{連番}={ACL}" */
	//public static final String PROP_CREATESERVICE_POSTFOLDERACL_PREFIX = "_createservice.postfolderacl.";
	/** サービス設定処理のアクセスログ（処理経過ログ）を出力するかどうか */
	public static final String SERVICESETTING_ENABLE_ACCESSLOG = "_servicesetting.enable.accesslog";
	/** 設定デフォルト値 : アクセスカウンタの有効時間(時) */
	public static final int ACCESSCOUNT_EXPIRE_HOUR_DEFAULT = 72;

	/** サービスを表す記号 */
	public static final String MARK_SERVICE = "@";
	
	/**
	 * サービス初期フォルダ
	 * {"登録順", "キー”} の配列で指定
	 */
	public static final String[][] CREATESERVICE_POSTFOLDER = {
		{"1", "/_group"},
		{"2", "/_group/$admin"},
		{"3", "/_group/$content"},
		{"4", "/_group/$useradmin"},
		{"5", "/_settings"},
		{"6", "/_settings/template"},
		{"7", "/_log"},
		{"8", "/_html"},
		{"9", "/_login_history"},
		{"10", "/_user"},
		{"11", "/_mq"},
		{"12", "/_bdbq"},
		{"51", "/_oauth"},
		{"52", "/_oauth/facebook"},
		{"53", "/_oauth/github"},
		{"54", "/_oauth/google"},
		{"55", "/_oauth/twitter"},
		{"56", "/_oauth/yahoo"},
		{"57", "/_oauth/line"}
	};
	
	/**
	 * サービス初期フォルダのACL
	 * {"フォルダ登録順", "ACL登録順", "ACL”} の配列で指定
	 */
	public static final String[][] CREATESERVICE_POSTFOLDER_ACL = {
		// /_group
		{"1", "1", "/_group/$admin,CRUD"},
		{"1", "2", "/_group/$useradmin,CRUD/"},
		// /_group/$admin
		{"2", "1", "/_group/$admin,CRUD"},
		// /_group/$content
		{"3", "1", "/_group/$admin,CRUD"},
		{"3", "2", "/_group/$useradmin,CRUD/"},
		// /_group/$useradmin
		{"4", "1", "/_group/$admin,CRUD"},
		{"4", "2", "/_group/$useradmin,CRUD/"},
		// /_html
		{"8", "1", "/_group/$admin,CRUD"},
		{"8", "2", "/_group/$content,CRUD/"},
		{"8", "3", "+,R/"},
		// /_user
		{"10", "1", "/_group/$admin,CRUD"},
		{"10", "2", "/_group/$useradmin,CRUD/"},
		// /_mq
		{"11", "1", "/_group/$admin,CRUD"},
		{"11", "2", "+,CE/"}
	};

}
