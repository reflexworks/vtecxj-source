package jp.reflexworks.taggingservice.requester;

/**
 * リクエスト先BDBサーバを取得するための定数クラス
 */
public class BDBClientServerConst {

	/** 設定 : システム管理サービスが使用するManifestサーバのURL 接頭辞 */
	public static final String BDBREQUEST_URL_SYSTEM_MANIFEST_PREFIX = "_bdbrequest.url.system.manifest.";
	/** 設定 : システム管理サービスが使用するEntryサーバのURL 接頭辞 */
	public static final String BDBREQUEST_URL_SYSTEM_ENTRY_PREFIX = "_bdbrequest.url.system.entry.";
	/** 設定 : システム管理サービスが使用するインデックスサーバのURL 接頭辞 */
	public static final String BDBREQUEST_URL_SYSTEM_INDEX_PREFIX = "_bdbrequest.url.system.index.";
	/** 設定 : システム管理サービスが使用するインデックスサーバのURL 接頭辞 */
	public static final String BDBREQUEST_URL_SYSTEM_FULLTEXT_PREFIX = "_bdbrequest.url.system.fulltext.";
	/** 設定 : システム管理サービスが使用するインデックスサーバのURL 接頭辞 */
	public static final String BDBREQUEST_URL_SYSTEM_ALLOCIDS_PREFIX = "_bdbrequest.url.system.allocids.";
	/** 設定 : ConsistentHashのレプリカ数 */
	public static final String CONSISTENTHASH_REPLICA_NUM = "_consistenthash.replica.num";
	/** 設定 : Entryサーバの割り当て数(サービスステータスがproduction時) */
	public static final String BDBSERVER_NUM_ENTRY = "_bdbserver.num.entry";
	/** 設定 : インデックスサーバの割り当て数(サービスステータスがproduction時) */
	public static final String BDBSERVER_NUM_INDEX = "_bdbserver.num.index";
	/** 設定 : 全文検索インデックスサーバの割り当て数(サービスステータスがproduction時) */
	public static final String BDBSERVER_NUM_FULLTEXT = "_bdbserver.num.fulltext";
	/** 設定 : 採番・カウンタサーバの割り当て数(サービスステータスがproduction時) */
	public static final String BDBSERVER_NUM_ALLOCIDS = "_bdbserver.num.allocids";
	/** 設定 : PodのIPアドレス取得からの有効期限(秒) */
	public static final String PROP_GETPODIP_EXPIRE_SEC = "_bdbserver.getpodip.expire.sec";
	/** 設定 : PodのIPアドレス取得シェル コマンドの配置パス */
	public static final String PROP_CMD_PATH_GETPODIP = "_cmd.path.getpodip";
	/** 設定 : サービスアカウントJSONファイル名 */
	public static final String PROP_KUBECTL_FILE_SECRET = "_kubectl.file.secret";
	/** 設定 : サービスアカウント名 */
	public static final String PROP_KUBECTL_SERVICEACCOUNT = "_kubectl.serviceaccount";
	/** 設定 : プロジェクトID */
	public static final String PROP_GCP_PROJECTID = "_gcp.projectid";
	/** 設定 : PodのIPアドレス取得結果出力ファイルパス */
	//public static final String PROP_GETPODIP_OUT_FILEPATH = "_bdbserver.getpodip.out.filepath";
	/** 設定 : ManifestサーバのDeployment名 */
	//public static final String PROP_DEPLOYMENT_NAME_MNFSERVER = "_deployment.name.mnfserver";
	/** 設定 : EntryサーバのDeployment名 */
	//public static final String PROP_DEPLOYMENT_NAME_ENTRYSERVER = "_deployment.name.entryserver";
	/** 設定 : IndexサーバのDeployment名 */
	//public static final String PROP_DEPLOYMENT_NAME_IDXSERVER = "_deployment.name.idxserver";
	/** 設定 : 全文検索インデックスサーバのDeployment名 */
	//public static final String PROP_DEPLOYMENT_NAME_FTSERVER = "_deployment.name.ftserver";
	/** 設定 : 採番・カウンタサーバのDeployment名 */
	//public static final String PROP_DEPLOYMENT_NAME_ALSERVER = "_deployment.name.alserver";
	/** 設定 : BDBサーバのサーブレットパス */
	//public static final String PROP_BDBSERVER_SERVLETPATH = "_bdbserver.servletpath";

	/** 設定デフォルト値 : Entryサーバの割り当て数(サービスステータスがproduction時) */
	public static final int BDBSERVER_NUM_ENTRY_DEFAULT = 1;
	/** 設定デフォルト値 : インデックスサーバの割り当て数(サービスステータスがproduction時) */
	public static final int BDBSERVER_NUM_INDEX_DEFAULT = 1;
	/** 設定デフォルト値 : 全文検索インデックスサーバの割り当て数(サービスステータスがproduction時) */
	public static final int BDBSERVER_NUM_FULLTEXT_DEFAULT = 1;
	/** 設定デフォルト値 : 採番・カウンタサーバの割り当て数(サービスステータスがproduction時) */
	public static final int BDBSERVER_NUM_ALLOCIDS_DEFAULT = 1;
	/** 設定デフォルト : PodのIPアドレス取得からの有効期限(秒)  */
	public static final int GETPODIP_EXPIRE_SEC_DEFAULT = 300;
	/** 設定デフォルト : PodのIPアドレス取得シェル コマンドの配置パス */
	//public static final String CMD_PATH_GETPODIP_DEFAULT = "/var/vtecx/sh/kubectl_get_podip.sh";
	/** 設定デフォルト : PodのIPアドレス取得シェル コマンド実行結果出力ファイルパス */
	//public static final String GETPODIP_OUT_FILEPATH_DEFAULT = "/var/vtecx/log/podips_bdbserver.txt";
	/** 設定デフォルト : BDBサーバのサーブレットパス */
	//public static final String BDBSERVER_SERVLETPATH_DEFAULT = "/b";

	/** 設定の文字数 : システム管理サービスが使用するManifestサーバのURL 接頭辞 */
	public static final int BDBREQUEST_URL_SYSTEM_MANIFEST_PREFIX_LEN = BDBREQUEST_URL_SYSTEM_MANIFEST_PREFIX.length();
	/** 設定の文字数 : システム管理サービスが使用するEntryサーバのURL 接頭辞 */
	public static final int BDBREQUEST_URL_SYSTEM_ENTRY_PREFIX_LEN = BDBREQUEST_URL_SYSTEM_ENTRY_PREFIX.length();
	/** 設定の文字数 : システム管理サービスが使用するインデックスサーバのURL 接頭辞 */
	public static final int BDBREQUEST_URL_SYSTEM_INDEX_PREFIX_LEN = BDBREQUEST_URL_SYSTEM_INDEX_PREFIX.length();
	/** 設定の文字数 : システム管理サービスが使用するインデックスサーバのURL 接頭辞 */
	public static final int BDBREQUEST_URL_SYSTEM_FULLTEXT_PREFIX_LEN = BDBREQUEST_URL_SYSTEM_FULLTEXT_PREFIX.length();
	/** 設定の文字数 : システム管理サービスが使用するインデックスサーバのURL 接頭辞 */
	public static final int BDBREQUEST_URL_SYSTEM_ALLOCIDS_PREFIX_LEN = BDBREQUEST_URL_SYSTEM_ALLOCIDS_PREFIX.length();

	/**
	 * サーバタイプ
	 * MANIFEST : Manifestサーバ
	 * ENTRY : Entryサーバ
	 * INDEX : インデックスサーバ
	 * FULLTEXT : 全文検索インデックスサーバ
	 * ALLOCIDS : 採番・カウンタサーバ
	 */
	public enum BDBServerType {MANIFEST, ENTRY, INDEX, FULLTEXT, ALLOCIDS};

	/**
	 * インデックスタイプ
	 * INDEX : インデックス
	 * FULLTEXT : 全文検索インデックス
	 * MANIFEST : Manifest
	 */
	public enum BDBIndexType {INDEX, FULLTEXT, MANIFEST};

	/**
	 * BDBサーバからのレスポンス戻り値の型指定
	 * FEED : Feedオブジェクトにデシリアライズ
	 * ENTRY : Entryオブジェクトにデシリアライズ
	 * ENTRYLIST : バイト配列を区切ってEntryオブジェクトにデシリアライズしリストに詰める
	 * INPUTSTREAM : InputStreamのまま受け取り
	 */
	public enum BDBResponseType {FEED, ENTRY, ENTRYLIST, INPUTSTREAM};

	/** Consistent Hashのレプリカ数デフォルト */
	public static final int CONSISTENTHASH_REPLICA_NUM_DEFAULT = 300;

	/** 旧サーバのバックアップフォルダURI */
	public static final String URI_PREVIOUS_BACKUP = "/previous_backup";
	/** 旧サーバのバックアップの、名前空間階層URI */
	public static final String URI_LAYER_NAMESPACE = "/namespace";

	/** データ移行時に検索するテーブル名 : Manifest */
	public static final String DB_MANIFEST = "DBManifest";
	/** データ移行時に検索するテーブル名 : Entry */
	public static final String DB_ENTRY = "DBEntry";
	/** データ移行時に検索するテーブル名 : 採番 */
	public static final String DB_ALLOCIDS = "DBAllocids";
	/** データ移行時に検索するテーブル名 : カウンタ */
	public static final String DB_INCREMENT = "DBIncrement";
	/** データ移行時に検索するテーブル名 : キーに紐づくインデックス一覧 */
	public static final String DB_INDEX_ANCESTOR = "DBInnerIndexAncestor";
	/** データ移行時に検索するテーブル名 : キーに紐づく全文検索インデックス一覧 */
	public static final String DB_FULLTEXT_ANCESTOR = "DBFullTextIndexAncestor";

	/** BDBサーバタイプ指定値 : Manifestサーバ */
	public static final String SERVERTYPE_MNF = "mnf";
	/** BDBサーバタイプ指定値 : Entryサーバ */
	public static final String SERVERTYPE_ENTRY = "entry";
	/** BDBサーバタイプ指定値 : インデックスサーバ */
	public static final String SERVERTYPE_IDX = "idx";
	/** BDBサーバタイプ指定値 : 全文検索インデックスサーバ */
	public static final String SERVERTYPE_FT = "ft";
	/** BDBサーバタイプ指定値 : 採番・カウンタサーバ */
	public static final String SERVERTYPE_AL = "al";

	/** ランダム値生成文字数 */
	public static final int RANDOM_LEN = 12;

	/** Redisキャッシュのキー : PodのIP (続きにサーバ名) */
	public static final String CACHESTRING_KEY_PODIP_PREFIX = "/_bdbserver/podip/";

}
