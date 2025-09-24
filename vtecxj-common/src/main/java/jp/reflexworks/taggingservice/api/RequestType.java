package jp.reflexworks.taggingservice.api;

/**
 * リクエストパラメータに使用する定数.
 */
public interface RequestType {

	/** Servlet path */
	public static final String SERVLET_PATH = "/d";

	/** URLパラメータ : 戻り値の形式をXMLに指定 (デフォルトはJSON) */
	public static final String PARAM_XML = "x";
	/** URLパラメータ : 戻り値の形式をJSONに指定 */
	public static final String PARAM_JSON = "j";
	/** URLパラメータ : 戻り値の形式をMessagePackに指定 */
	public static final String PARAM_MESSAGEPACK = "m";
	/** URLパラメータ : 最大件数を指定 */
	public static final String PARAM_LIMIT = "l";
	/** URLパラメータ : 次ページ取得のカーソルを指定 */
	public static final String PARAM_NEXT = "p";
	/** URLパラメータ : ページ番号を指定 */
	public static final String PARAM_NUMBER = "n";
	/** URLパラメータ : リビジョンを指定 */
	public static final String PARAM_REVISION = "r";
	/** URLパラメータ : 件数取得 */
	public static final String PARAM_COUNT = "c";
	/** URLパラメータ : Entry取得 */
	public static final String PARAM_ENTRY = "e";
	/** URLパラメータ : Feed検索、削除 */
	public static final String PARAM_FEED = "f";
	/** URLパラメータ : ソート順を指定 */
	public static final String PARAM_SORT = "s";
	/** URLパラメータ : contentの中身を取得 */
	public static final String PARAM_CONTENT = "_content";
	/** URLパラメータ : contentの中身無しで返す */
	public static final String PARAM_NOCONTENT = "_nocontent";
	/** URLパラメータ : 配下のエントリー削除処理 (DELETEメソッドで使用) */
	public static final String PARAM_RF = "_rf";
	/** URLパラメータ : ログイン */
	public static final String PARAM_LOGIN = "_login";
	/** URLパラメータ : ログアウト */
	public static final String PARAM_LOGOUT = "_logout";
	/** URLパラメータ : 強制ログイン */
	public static final String PARAM_LOGINF = "_loginf";
	/** URLパラメータ : BlobstoreへのアップロードURLを取得 */
	public static final String PARAM_UPLOADURL = "_uploadurl";
	/** URLパラメータ : 自動採番 */
	public static final String PARAM_ALLOCIDS = "_allocids";
	/** URLパラメータ : 数値の加算処理 */
	public static final String PARAM_ADDIDS = "_addids";
	/** URLパラメータ : 加算処理の現在値取得 */
	public static final String PARAM_GETIDS = "_getids";
	/** URLパラメータ : 加算処理の値設定 */
	public static final String PARAM_SETIDS = "_setids";
	/** URLパラメータ : 加算処理の枠設定 */
	public static final String PARAM_RANGEIDS = "_rangeids";
	/** URLパラメータ : 認証チェックサービス */
	public static final String PARAM_AUTHCHECK = "_authcheck";
	/** URLパラメータ : 認証チェックとユーザ名・ニックネーム・UID取得サービス */
	public static final String PARAM_WHOAMI = "_whoami";
	/** URLパラメータ : Dataスキームで取得 */
	public static final String PARAM_DATASCHEME = "64";
	/** URLパラメータ : RXID */
	public static final String PARAM_RXID = "_RXID";
	/** URLパラメータ : 更新時にrevisionをカウントアップしないオプション */
	public static final String PARAM_SILENT = "_silent";
	/** URLパラメータ : 現在時刻返却サービス */
	public static final String PARAM_GETDATETIME = "_getdatetime";
	/** URLパラメータ : 現在時刻返却サービス */
	public static final String PARAM_NOW = "_now";
	/** URLパラメータ : 処理継続 */
	public static final String PARAM_CONTINUE = "_continue";
	/** URLパラメータ : 複数エントリー検索 */
	public static final String PARAM_GETMULTI = "_getmulti";
	/** URLパラメータ : ユーザ番号取得 */
	public static final String PARAM_UID = "_uid";
	/** URLパラメータ : アカウント取得 */
	public static final String PARAM_ACCOUNT = "_account";
	/** URLパラメータ : 署名 */
	public static final String PARAM_SIGNATURE = "_signature";
	/** URLパラメータ : 指定されたページ数分のカーソル一覧を返却するオプション */
	public static final String PARAM_POINTERS = "_pointers";
	/** URLパラメータ : カーソル一覧取得で最初のページの件数を指定するオプション */
	public static final String PARAM_FIRST = "_first";
	/** URLパラメータ : 指定されたページ数分のカーソル一覧をサーバ内部に取得するオプション */
	public static final String PARAM_PAGINATION = "_pagination";
	/** URLパラメータ : 配下のエントリーを全て抽出するオプション */
	public static final String PARAM_LOWER = "_lower";
	/** URLパラメータ : ノード名を指定するオプション (_lowerオプションと合わせて使用) */
	public static final String PARAM_NODE = "_node";
	/** URLパラメータ : アクセスキー変更オプション */
	public static final String PARAM_ACCESSKEY = "_accesskey";
	/** URLパラメータ : アクセストークン発行オプション */
	public static final String PARAM_ACCESSTOKEN = "_accesstoken";
	/** URLパラメータ : リンクトークン発行オプション */
	public static final String PARAM_LINKTOKEN = "_linktoken";
	/** URLパラメータ : リンクトークン指定オプション */
	public static final String PARAM_TOKEN = "_token";
	/** URLパラメータ : ユーザ登録メール送信オプション */
	public static final String PARAM_ADDUSER = "_adduser";
	/** URLパラメータ : パスワード変更メール送信オプション */
	public static final String PARAM_PASSRESET = "_passreset";
	/** URLパラメータ : パスワード変更オプション */
	public static final String PARAM_CHANGEPASS = "_changephash";
	/** URLパラメータ : アカウント変更メール送信オプション */
	public static final String PARAM_CHANGEACCOUNT = "_changeaccount";
	/** URLパラメータ : アカウント変更オプション */
	public static final String PARAM_CHANGEACCOUNT_VERIFY = "_changeaccount_verify";
	/** URLパラメータ : 管理者によるユーザ登録オプション */
	public static final String PARAM_ADDUSER_BYADMIN = "_adduserByAdmin";
	/** URLパラメータ : 管理者によるパスワード変更オプション */
	public static final String PARAM_CHANGEPASS_BYADMIN = "_changephashByAdmin";
	/** URLパラメータ : サービス作成 */
	public static final String PARAM_CREATESERVICE = "_createservice";
	/** URLパラメータ : サービス削除 */
	public static final String PARAM_DELETESERVICE = "_deleteservice";
	/** URLパラメータ : ログインサービスへのリダイレクト時にサービス名を引き渡すために使用するパラメータ */
	public static final String PARAM_INVOKER = "_invoker";
	/** URLパラメータ : サービス */
	public static final String PARAM_SERVICE = "_service";
	/** URLパラメータ : サービスのステータス取得・更新 */
	public static final String PARAM_SERVICESTATUS = "_servicestatus";
	/** URLパラメータ : サービスステータスを公開にする */
	public static final String PARAM_SERVICETOPRODUCTION = "_servicetoproduction";
	/** URLパラメータ : サービスステータスを開発にする */
	public static final String PARAM_SERVICETOSTAGING = "_servicetostaging";
	/** URLパラメータ : ユーザステータス取得 */
	public static final String PARAM_USERSTATUS = "_userstatus";
	/** URLパラメータ : ユーザを無効にする */
	public static final String PARAM_REVOKEUSER = "_revokeuser";
	/** URLパラメータ : ユーザを有効にする */
	public static final String PARAM_ACTIVATEUSER = "_activateuser";
	/** URLパラメータ : ユーザを削除する */
	public static final String PARAM_DELETEUSER = "_deleteuser";
	/** URLパラメータ : ユーザを退会する */
	public static final String PARAM_CANCELUSER = "_canceluser";
	/** URLパラメータ : RDB */
	public static final String PARAM_RDB = "_rdb";
	/** URLパラメータ : キャッシュ */
	public static final String PARAM_CACHE = "_cache";
	/** URLパラメータ : RXID取得 */
	public static final String PARAM_GETRXID = "_getrxid";
	/** URLパラメータ : Feed形式キャッシュ */
	public static final String PARAM_CACHEFEED = "_cachefeed";
	/** URLパラメータ : Entry形式キャッシュ */
	public static final String PARAM_CACHEENTRY = "_cacheentry";
	/** URLパラメータ : 文字列形式キャッシュ */
	public static final String PARAM_CACHESTRING = "_cachestring";
	/** URLパラメータ : 数値形式キャッシュ */
	public static final String PARAM_CACHELONG = "_cachelong";
	/** URLパラメータ : 数値形式キャッシュのインクリメント */
	public static final String PARAM_CACHEINCR = "_cacheincr";
	/** URLパラメータ : 有効時間 */
	public static final String PARAM_EXPIRE = "_expire";
	/** URLパラメータ : 一括処理 */
	public static final String PARAM_BULK = "_bulk";
	/** URLパラメータ : 一括処理 */
	public static final String PARAM_BULKSERIAL = "_bulkserial";
	/** URLパラメータ : 非同期処理 */
	public static final String PARAM_ASYNC = "_async";
	/** URLパラメータ : エイリアス追加 */
	public static final String PARAM_ADDALIAS = "_addalias";
	/** URLパラメータ : エイリアス削除 */
	public static final String PARAM_REMOVEALIAS = "_removealias";
	/** URLパラメータ : ACL追加 */
	public static final String PARAM_ADDACL = "_addacl";
	/** URLパラメータ : ACL削除 */
	public static final String PARAM_REMOVEACL = "_removeacl";
	/** URLパラメータ : Meta情報を取得しない */
	public static final String PARAM_NOMETA = "_nometa";
	/** URLパラメータ : APIKey */
	public static final String PARAM_APIKEY = "_apikey";
	/** URLパラメータ : サービスキー */
	public static final String PARAM_SERVICEKEY = "_servicekey";
	/** URLパラメータ : ログエントリー登録(バッチ処理などで直接登録) */
	public static final String PARAM_LOG = "_log";
	/** URLパラメータ : フォルダクリア */
	public static final String PARAM_CLEARFOLDER = "_clearfolder";
	/** URLパラメータ : サーバ名 */
	public static final String PARAM_SERVER = "_server";
	/** URLパラメータ : インデックス更新 */
	public static final String PARAM_INDEX = "_index";
	/** URLパラメータ : 指定キー・項目のみの更新 */
	public static final String PARAM_PARTIAL = "_partial";
	/** URLパラメータ : 複数処理 */
	public static final String PARAM_MULTIPLE = "_multiple";
	/** URLパラメータ : キャッシュをクリア */
	public static final String PARAM_CLEARCACHE = "_clearcache";
	/** URLパラメータ : deleteオプション(idに指定) */
	public static final String PARAM_DELETE = "_delete";
	/** URLパラメータ : BDBクリーン : DELETE /b/?_clean */
	public static final String PARAM_CLEAN = "_clean";
	/** URLパラメータ : BDB環境クローズ : DELETE /b/?_close */
	public static final String PARAM_CLOSE = "_close";
	/** URLパラメータ : テーブルリスト取得 : GET /b/?_list={テーブル名} */
	public static final String PARAM_LIST = "_list";
	/** URLパラメータ : BDB環境統計情報取得 : GET /b/?_stats */
	public static final String PARAM_STATS = "_stats";
	/** URLパラメータ : ディスク使用率取得 : GET /b/?_diskusage */
	public static final String PARAM_DISKUSAGE = "_diskusage";
	/** URLパラメータ : テーブルリスト取得時のキー接頭辞 */
	public static final String PARAM_KEYPREFIX = "_keyprefix";
	/** URLパラメータ : distkey */
	public static final String PARAM_DISTKEY = "_distkey";
	/** URLパラメータ : distvalue (distkeyの値) */
	public static final String PARAM_DISTVALUE = "_distvalue";
	/** URLパラメータ : BDBバックアップ */
	public static final String PARAM_BACKUP = "_backup";

	/** URLパラメータ : ワイルドカード */
	public static final String WILDCARD = "*";

	/**
	 * PathInfoを取得.
	 * ただし末尾の"*"は除去されています。
	 * 末尾に"*"が設定されているかどうかは、isUrlFowardMatchメソッドで取得してください。
	 * @return PathInfo
	 */
	public String getUri();

	/**
	 * クエリ文字列を取得.
	 * @return クエリ文字列
	 */
	public String getQueryString();

	/**
	 * 指定されたオプションの値を取得.
	 * QueryStringに指定されたパラメータの値を返却します。
	 * @param name オプション名
	 * @return 値
	 */
	public String getOption(String name);

}
