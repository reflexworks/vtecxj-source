package jp.reflexworks.vtecx.init;

import jp.reflexworks.taggingservice.util.Constants;

/**
 * 初期データ登録用定数
 */
public class InitializeConst {

	/** クラスパス以降の相対ディレクトリ */
	public static final String RELATIVE_DIR = "";
	/** プロパティファイル名 */
	public static final String PROPERTY_FILE_NAME = RELATIVE_DIR + "vtecxinit.properties";
	/** システムサービス名 */
	public static final String SERVICE_SYSTEM = "admin";

	/** システム管理サービスの管理ユーザUID */
	public static final String ADMINUSER_UID = "21";

	/** UIDの開始番号 */
	public static final String UID_START_INIT = "21";
	
	// プロパティ設定
	/** システム管理サービスのAPIKeyを格納したSecret名 */
	public static final String SECRET_INIT_SYSTEMSERVICE_APIKEY_NAME = "_secret.init.systemservice.apikey.name";
	/** システム管理サービスの管理ユーザメールアドレスを格納したSecret名 */
	public static final String SECRET_INIT_SYSTEMSERVICE_EMAIL_NAME = "_secret.init.systemservice.email.name";
	/** システム管理サービスの管理ユーザパスワードを格納したSecret名 */
	public static final String SECRET_INIT_SYSTEMSERVICE_PASSWORD_NAME = "_secret.init.systemservice.password.name";
	/**
	 * 各BDBサーバのサーバ名とホスト名(kubernetesのサービス名)を、以下の形式で指定。
	 * _init.bdbserver.{entry|manifest|index|allocids|fulltextsearch}.{サーバ名}={ホスト名}
	 */
	/** Entryサーバのサーバ名・ホスト名設定接頭辞(production振り分け用) */
	public static final String INIT_BDBSERVER_ENTRY_PREFIX = "_init.bdbserver.entry.";
	/** Manifestサーバのサーバ名・ホスト名設定接頭辞(production振り分け用) */
	public static final String INIT_BDBSERVER_MANIFEST_PREFIX = "_init.bdbserver.manifest.";
	/** Indexサーバのサーバ名・ホスト名設定接頭辞(production振り分け用) */
	public static final String INIT_BDBSERVER_INDEX_PREFIX = "_init.bdbserver.index.";
	/** 採番・カウンタサーバのサーバ名・ホスト名設定接頭辞(production振り分け用) */
	public static final String INIT_BDBSERVER_ALLOCIDS_PREFIX = "_init.bdbserver.allocids.";
	/** 全文検索Indexサーバのサーバ名・ホスト名設定接頭辞(production振り分け用) */
	public static final String INIT_BDBSERVER_FULLTEXTSEARCH_PREFIX = "_init.bdbserver.fulltextsearch.";
	/** Entryサーバのサーバ名・ホスト名設定接頭辞(staging振り分け用) */
	public static final String INIT_BDBSERVER_STAGING_ENTRY_PREFIX = "_init.bdbserver.staging.entry.";
	/** Manifestサーバのサーバ名・ホスト名設定接頭辞(staging振り分け用) */
	public static final String INIT_BDBSERVER_STAGING_MANIFEST_PREFIX = "_init.bdbserver.staging.manifest.";
	/** Indexサーバのサーバ名・ホスト名設定接頭辞(staging振り分け用) */
	public static final String INIT_BDBSERVER_STAGING_INDEX_PREFIX = "_init.bdbserver.staging.index.";
	/** 採番・カウンタサーバのサーバ名・ホスト名設定接頭辞(staging振り分け用) */
	public static final String INIT_BDBSERVER_STAGING_ALLOCIDS_PREFIX = "_init.bdbserver.staging.allocids.";
	/** 全文検索Indexサーバのサーバ名・ホスト名設定接頭辞(staging振り分け用) */
	public static final String INIT_BDBSERVER_STAGING_FULLTEXTSEARCH_PREFIX = "_init.bdbserver.staging.fulltextsearch.";

	// BDB関連
	/** URI : bdb */
	public static final String URI_BDB = "/_bdb";
	/** URI : bdb service */
	public static final String URI_BDB_SERVICE = URI_BDB + "/service";
	/** URI : manifest server */
	public static final String URI_MNFSERVER = "/mnfserver";
	/** URI : entry server */
	public static final String URI_ENTRYSERVER = "/entryserver";
	/** URI : index server */
	public static final String URI_IDXSERVER = "/idxserver";
	/** URI : full text index server */
	public static final String URI_FTSERVER = "/ftserver";
	/** URI : allocids server */
	public static final String URI_ALSERVER = "/alserver";
	/** URI : manifest server */
	public static final String URI_BDB_MNFSERVER = URI_BDB + URI_MNFSERVER;
	/** URI : entry server */
	public static final String URI_BDB_ENTRYSERVER = URI_BDB + URI_ENTRYSERVER;
	/** URI : index server */
	public static final String URI_BDB_IDXSERVER = URI_BDB + URI_IDXSERVER;
	/** URI : full text index server */
	public static final String URI_BDB_FTSERVER = URI_BDB + URI_FTSERVER;
	/** URI : allocids server */
	public static final String URI_BDB_ALSERVER = URI_BDB + URI_ALSERVER;
	/** URI : settings properties */
	public static final String URI_SETTINGS_PROPERTIES = "/_settings/properties";
	/** URI : bdb staging */
	public static final String URI_BDB_STAGING = URI_BDB + "/" + Constants.SERVICE_STATUS_STAGING;
	/** URI : bdb production */
	public static final String URI_BDB_PRODUCTION = URI_BDB + "/" + Constants.SERVICE_STATUS_PRODUCTION;
	/** URI : maintenance notice */
	public static final String URI_SETTINGS_MAINTENANCE_NOTICE = "/_settings/maintenance_notice";
	/** URI : diskusage alert */
	public static final String URI_SETTINGS_DISKUSAGE_ALERT = "/_settings/diskusage_alert";
	/** URI : bdb reservation */
	public static final String URI_BDB_RESERVATION = URI_BDB + "/reservation";
	/** URI : bucket */
	public static final String URI_BUCKET = "/_bucket";

	/** Entryサーバのサーバ名・ホスト名設定接頭辞(production振り分け用)の文字列長 */
	public static final int INIT_BDBSERVER_ENTRY_PREFIX_LEN = INIT_BDBSERVER_ENTRY_PREFIX.length();
	/** Manifestサーバのサーバ名・ホスト名設定接頭辞(production振り分け用)の文字列長 */
	public static final int INIT_BDBSERVER_MANIFEST_PREFIX_LEN = INIT_BDBSERVER_MANIFEST_PREFIX.length();
	/** Indexサーバのサーバ名・ホスト名設定接頭辞(production振り分け用)の文字列長 */
	public static final int INIT_BDBSERVER_INDEX_PREFIX_LEN = INIT_BDBSERVER_INDEX_PREFIX.length();
	/** 採番・カウンタサーバのサーバ名・ホスト名設定接頭辞(production振り分け用)の文字列長 */
	public static final int INIT_BDBSERVER_ALLOCIDS_PREFIX_LEN = INIT_BDBSERVER_ALLOCIDS_PREFIX.length();
	/** 全文検索Indexサーバのサーバ名・ホスト名設定接頭辞(production振り分け用)の文字列長 */
	public static final int INIT_BDBSERVER_FULLTEXTSEARCH_PREFIX_LEN = INIT_BDBSERVER_FULLTEXTSEARCH_PREFIX.length();
	/** Entryサーバのサーバ名・ホスト名設定接頭辞(staging振り分け用)の文字列長 */
	public static final int INIT_BDBSERVER_STAGING_ENTRY_PREFIX_LEN = INIT_BDBSERVER_STAGING_ENTRY_PREFIX.length();
	/** Manifestサーバのサーバ名・ホスト名設定接頭辞(staging振り分け用)の文字列長 */
	public static final int INIT_BDBSERVER_STAGING_MANIFEST_PREFIX_LEN = INIT_BDBSERVER_STAGING_MANIFEST_PREFIX.length();
	/** Indexサーバのサーバ名・ホスト名設定接頭辞(staging振り分け用)の文字列長 */
	public static final int INIT_BDBSERVER_STAGING_INDEX_PREFIX_LEN = INIT_BDBSERVER_STAGING_INDEX_PREFIX.length();
	/** 採番・カウンタサーバのサーバ名・ホスト名設定接頭辞(staging振り分け用)の文字列長 */
	public static final int INIT_BDBSERVER_STAGING_ALLOCIDS_PREFIX_LEN = INIT_BDBSERVER_STAGING_ALLOCIDS_PREFIX.length();
	/** 全文検索Indexサーバのサーバ名・ホスト名設定接頭辞(staging振り分け用)の文字列長 */
	public static final int INIT_BDBSERVER_STAGING_FULLTEXTSEARCH_PREFIX_LEN = INIT_BDBSERVER_STAGING_FULLTEXTSEARCH_PREFIX.length();

	/** 各BDBサーバリクエストプロトコル */
	public static final String PROTOCOL = "http://";
	/** 各BDBサーバのサーブレットパス */
	public static final String SERVLET_PATH = "/b";

}
