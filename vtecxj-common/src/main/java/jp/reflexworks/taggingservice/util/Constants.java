package jp.reflexworks.taggingservice.util;

import java.nio.charset.Charset;
import java.util.regex.Pattern;

import jp.reflexworks.atom.api.AtomConst;
import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.taggingservice.api.SettingConst;

/**
 * 定数定義クラス
 */
public interface Constants extends AtomConst {

	/** エンコード */
	public static final String ENCODING = ReflexServletConst.ENCODING;
	/** 文字セット */
	public static final Charset CHARSET = Charset.forName(ENCODING);
	/** 改行コード */
	public static final String NEWLINE = System.lineSeparator();
	/** 改行コード : CRLF */
	public static final String CRLF = "\r\n";
	/** 文字コードの終端 */
	public static final String END_STRING = "\uffff";
	/** 文字コードの先端 */
	public static final String START_STRING = "\u0001";

	/** Manifestのuriに登録する親階層の終端 */
	public static final String END_PARENT_URI_STRING = "\ufffe";

	/** 操作 */
	public enum Action {GET, POST, PUT, DELETE};

	/** Method : GET */
	public static final String GET = ReflexServletConst.GET;
	/** Method : POST */
	public static final String POST = ReflexServletConst.POST;
	/** Method : PUT */
	public static final String PUT = ReflexServletConst.PUT;
	/** Method : DELETE */
	public static final String DELETE = ReflexServletConst.DELETE;

	/** 更新種別 */
	public enum OperationType {INSERT, UPDATE, DELETE};

	/** Generator */
	public static final String REFLEX_TAGGING_SERVICE = "Reflex Tagging Service";

	/** サービスステータス : 新規作成中 */
	public static final String SERVICE_STATUS_CREATING = "creating";
	/** サービスステータス : 非公開(開発中) */
	public static final String SERVICE_STATUS_STAGING = "staging";
	/** サービスステータス : 公開中 */
	public static final String SERVICE_STATUS_PRODUCTION = "production";
	/** サービスステータス : 強制停止 */
	public static final String SERVICE_STATUS_BLOCKED = "blocked";
	/** サービスステータス : 公開移行中 */
	public static final String SERVICE_STATUS_TOPRODUCTION = "toproduction";
	/** サービスステータス : 非公開移行中 */
	public static final String SERVICE_STATUS_TOSTAGING = "tostaging";
	/** サービスステータス : 削除中 */
	public static final String SERVICE_STATUS_DELETING = "deleting";
	/** サービスステータス : 削除済み */
	public static final String SERVICE_STATUS_DELETED = "deleted";
	/** サービスステータス : 登録失敗 */
	public static final String SERVICE_STATUS_FAILURE = "failure";
	/** サービスステータス : サーバ追加等のメンテナンス */
	public static final String SERVICE_STATUS_MAINTENANCE = "maintenance";
	/** サービスステータス : サーバ追加等のメンテナンス失敗 */
	public static final String SERVICE_STATUS_MAINTENANCE_FAILURE = "maintenance_failure";

	// システムエントリー
	/** ルートエントリー */
	public static final String URI_ROOT = "/";
	/** サービスのトップエントリーのキーの接頭辞 */
	public static final String URI_SERVICE_PREFIX = SVC_PREFIX;
	/**
	 * システム管理サービスの共通データ階層.
	 * 必ずしも公開するのではなく、各サービスに関わりのある内容を持つエントリーの親階層。
	 */
	public static final String URI_SYSTEM_MANAGER = "/@";
	/** 管理エントリーのキーの接頭辞 */
	public static final String URI_SYSTEM_PREFIX = "/_";
	/** URI : log (value) */
	public static final String URI_LOG_VAL = "_log";
	/** URI : log */
	public static final String URI_LOG = "/" + URI_LOG_VAL;
	/** ユーザ初期エントリー設定 : ユーザ番号に置き換える記号 */
	public static final String ADDUSERINFO_UID = SettingConst.SETTING_USERINIT_UID;

	/** URI : security */
	public static final String URI_SECURITY = "/_security";
	/** URI : accesskey */
	public static final String URI_LAYER_ACCESSKEY = "/accesskey";

	/** URI : _service (システム管理サービス用。サービス一覧取得のためのキー) */
	public static final String URI_SERVICE_LAYER = "/_service";
	/** URI : /_service (システム管理サービス用。サービス一覧取得のためのキー) */
	public static final String URI_SERVICE = URI_SERVICE_LAYER;
	/** URI : /@/ (システム管理サービスの、一般サービス参照可能フォルダ) */
	public static final String URI_SYSTEM_REFERENCE = URI_SYSTEM_MANAGER + "/";
	/** URI : namespace */
	public static final String URI_NAMESPACE = "/_namespace";
	/** URI : login_history */
	public static final String URI_LOGIN_HISTORY = "/_login_history";
	/** URI layer : login_history */
	public static final String URI_LAYER_LOGIN_HISTORY = "/login_history";
	/** URI : bdb */
	public static final String URI_BDB = "/_bdb";
	/** URI : bdb server */
	public static final String URI_BDB_SERVER = URI_BDB + "/server";
	/** URI : bdb service */
	public static final String URI_BDB_SERVICE = URI_BDB + "/service";
	/** URI : 全文検索インデックス server (階層) */
	public static final String URI_FTSERVER = "/ftserver";
	/** URI : インデックス server (階層) */
	public static final String URI_IDXSERVER = "/idxserver";
	/** URI : Manifest server (階層) */
	public static final String URI_MNFSERVER = "/mnfserver";
	/** URI : Entry server (階層) */
	public static final String URI_ENTRYSERVER = "/entryserver";
	/** URI : 採番・カウンタ server (階層) */
	public static final String URI_ALSERVER = "/alserver";
	/** URI : 全文検索インデックス server */
	public static final String URI_BDB_FTSERVER = URI_BDB + URI_FTSERVER;
	/** URI : インデックス server */
	public static final String URI_BDB_IDXSERVER = URI_BDB + URI_IDXSERVER;
	/** URI : Manifest server */
	public static final String URI_BDB_MNFSERVER = URI_BDB + URI_MNFSERVER;
	/** URI : Entry server */
	public static final String URI_BDB_ENTRYSERVER = URI_BDB + URI_ENTRYSERVER;
	/** URI : 採番・カウンタ server */
	public static final String URI_BDB_ALSERVER = URI_BDB + URI_ALSERVER;
	/** URI : 全文検索条件 */
	public static final String URI_FTCONDITION = "/ftcondition";
	/** URI : インデックス検索条件 */
	public static final String URI_IDXCONDITION = "/idxcondition";
	/** URI : staging の割り当てserver */
	public static final String URI_BDB_STAGING = URI_BDB + "/" + SERVICE_STATUS_STAGING;
	/** URI : production の割り当てserver */
	public static final String URI_BDB_PRODUCTION = URI_BDB + "/" + SERVICE_STATUS_PRODUCTION;
	/** URI : 予約済み割り当てserver */
	public static final String URI_BDB_RESERVATION = URI_BDB + "/reservation";

	/** Cookie : upload */
	public static final String COOKIE_UPLOAD = "upload";
	/** Cookie : OK */
	public static final String COOKIE_OK = "OK";
	/** Cookie : NG */
	public static final String COOKIE_NG = "NG";
	/** Cookie : origin */
	public static final String COOKIE_ORIGIN = "origin";

	/** 拡張子 : png */
	public static final String SUFFIX_PNG = "png";
	/** 拡張子 : jpeg */
	public static final String SUFFIX_JPEG = "jpeg";
	/** 拡張子 : jpg */
	public static final String SUFFIX_JPG = "jpg";
	/** 拡張子 : gif */
	public static final String SUFFIX_GIF = "gif";
	/** 拡張子 : pdf */
	public static final String SUFFIX_PDF = "pdf";

	/** Title : Debug */
	public static final String DEBUG = "DEBUG";
	/** Title : Info */
	public static final String INFO = "INFO";
	/** Title : Warning */
	public static final String WARN = "WARN";
	/** Title : Error */
	public static final String ERROR = "ERROR";

	/** ログレベル */
	public enum LogLevel {DEBUG, INFO, WARN, ERROR};

	/** ハッシュ関数 */
	public static final String HASH_ALGORITHM = "SHA-256";

	/** ユーザ登録なしの場合のダミーUID */
	public static final String NULL_UID = "-1";

	/** サービス管理キー接頭辞 */
	public static final String ADMIN_KEY_PREFIX = "_";

	/** URN : accesskey */
	public static final String URN_PREFIX_ACCESSKEY = URN_PREFIX + "accesskey:";
	/** URN : APIKey */
	public static final String URN_PREFIX_APIKEY = URN_PREFIX + "apikey:";
	/** URN : サービスキー */
	public static final String URN_PREFIX_SERVICEKEY = URN_PREFIX + "servicekey:";

	/** 登録時のエイリアス自動採番記号 */
	public static final String URI_NUMBERING = "/#";

	/** インクリメント範囲指定 : 開始・終了の区切り文字 */
	public static final String INCREMENT_RANGE_SEPARATOR = "-";
	/** インクリメント範囲指定 : ローテーションしない */
	public static final String INCREMENT_RANGE_NOROTATE = "!";

	/** 認証方法 : WSSE */
	public static final String AUTH_TYPE_WSSE = "WSSE";
	/** 認証方法 : RXID */
	public static final String AUTH_TYPE_RXID = "RXID";
	/** 認証方法 : アクセストークン */
	public static final String AUTH_TYPE_ACCESSTOKEN = "AccessToken";
	/** 認証方法 : リンクトークン */
	public static final String AUTH_TYPE_LINKTOKEN = "LinkToken";
	/** 認証方法 : セッション */
	public static final String AUTH_TYPE_SESSION = "Session";
	/** 認証方法 : システム */
	public static final String AUTH_TYPE_SYSTEM = "System";
	/** 認証方法 : OAuth */
	public static final String AUTH_TYPE_OAUTH = "OAuth";

	/** 検索がフェッチ最大数を超えた場合のフラグ */
	public static final String MARK_FETCH_LIMIT = "*";

	/** レスポンスヘッダ : カーソル */
	public static final String HEADER_NEXTPAGE = "x-vtecx-nextpage";

	/** リクエストヘッダ : サービス名指定 */
	public static final String HEADER_SERVICENAME = "X-SERVICENAME";
	/** リクエストヘッダ : 名前空間指定 */
	public static final String HEADER_NAMESPACE = "X-NAMESPACE";
	/** DISTKEY項目リクエストヘッダ */
	public static final String HEADER_DISTKEY_ITEM = "X-DISTKEY-ITEM";
	/** DISTKEYの値リクエストヘッダ */
	public static final String HEADER_DISTKEY_VALUE = "X-DISTKEY-VALUE";
	/** リクエストヘッダ : SID */
	public static final String HEADER_SID = "X-SID";
	/** リクエストヘッダ : IPアドレス */
	public static final String HEADER_IP_ADDR = "X-IP-ADDR";
	/** リクエストヘッダ : ID */
	public static final String HEADER_ID = "X-ID";
	/** リクエストヘッダ : ENTRYバイト長 */
	public static final String HEADER_ENTRY_LENGTH = "X-ENTRY-LENGTH";

	/** リクエストヘッダの値区切り文字 */
	public static final String HEADER_VALUE_SEPARATOR = ";";

	/** X-Requested-With : バッチ */
	public static final String BATCH = "batch";

	/** 加算枠の正規表現 */
	public static final String PATTERN_RANGE_STR =
			"^(\\-?[0-9]+)(\\-(\\-?[0-9]+)(\\!?))*$";
	/** 加算枠のPattern */
	public static final Pattern PATTERN_RANGE = Pattern.compile(PATTERN_RANGE_STR);

	/** サーバの状態格納項目 */
	public static final String FIELD_SERVER_STATUS = "subtitle";
	/** サーバの割り当て状態 : assignable */
	public static final String SERVER_STATUS_ASSIGNABLE = "assignable";

	/** サーバタイプ : AP */
	public static final String SERVERTYPE_AP = "ap";
	/** サーバタイプ : BDB */
	public static final String SERVERTYPE_BDB = "bdb";
	/** サーバタイプ : インデックス */
	public static final String SERVERTYPE_INDEX = "index";
	/** サーバタイプ : 全文検索インデックス */
	public static final String SERVERTYPE_FULLTEXTSEARCH = "fulltextsearch";
	/** サーバタイプ : メモリソート */
	public static final String SERVERTYPE_MEMORYSORT = "memorysort";
	/** サーバタイプ : バッチジョブ */
	public static final String SERVERTYPE_BATCHJOB = "batchjob";
	/** サーバタイプ : バッチ */
	public static final String SERVERTYPE_BATCH = "batch";
	
	/** true文字列 */
	public static final String TRUE = "true";
	/** false文字列 */
	public static final String FALSE = "false";

}
