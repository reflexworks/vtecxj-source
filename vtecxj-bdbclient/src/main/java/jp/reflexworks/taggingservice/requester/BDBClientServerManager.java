package jp.reflexworks.taggingservice.requester;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.bdbclient.BDBClientUtil;
import jp.reflexworks.taggingservice.api.ReflexStatic;
import jp.reflexworks.taggingservice.blogic.ServiceBlogic;
import jp.reflexworks.taggingservice.env.StaticInfoUtil;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.LockingException;
import jp.reflexworks.taggingservice.exception.StaticDuplicatedException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.StaticInfo;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBServerType;
import jp.reflexworks.taggingservice.sys.SystemAuthentication;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.RetryUtil;
import jp.sourceforge.reflex.util.DateUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * BDBサーバ情報管理実装クラス.
 */
public class BDBClientServerManager {

	/** メモリ上のstaticオブジェクト格納キー : ManifsetサーバのURL情報 lock */
	private static final String STATIC_NAME_MNFSERVERURL_LOCK = "_mnfserverurl_lock";
	/** メモリ上のstaticオブジェクト格納キー : ManifsetサーバのURL情報 */
	private static final String STATIC_NAME_MNFSERVERURL = "_mnfserverurl";
	/** メモリ上のstaticオブジェクト格納キー : EntryサーバのURL情報 lock */
	private static final String STATIC_NAME_ENTRYSERVERURL_LOCK = "_entryserverurl_lock";
	/** メモリ上のstaticオブジェクト格納キー : 全文検索インデックスサーバのURL情報 */
	private static final String STATIC_NAME_ENTRYSERVERURL = "_entryserverurl";
	/** メモリ上のstaticオブジェクト格納キー : インデックスサーバのURL情報 lock */
	private static final String STATIC_NAME_IDXSERVERURL_LOCK = "_idxserverurl_lock";
	/** メモリ上のstaticオブジェクト格納キー : インデックスサーバのURL情報 */
	private static final String STATIC_NAME_IDXSERVERURL = "_idxserverurl";
	/** メモリ上のstaticオブジェクト格納キー : 全文検索インデックスサーバのURL情報 lock */
	private static final String STATIC_NAME_FTSERVERURL_LOCK = "_ftserverurl_lock";
	/** メモリ上のstaticオブジェクト格納キー : 全文検索インデックスサーバのURL情報 */
	private static final String STATIC_NAME_FTSERVERURL = "_ftserverurl";
	/** メモリ上のstaticオブジェクト格納キー : 採番・カウンタサーバのURL情報 lock */
	private static final String STATIC_NAME_ALSERVERURL_LOCK = "_alserverurl_lock";
	/** メモリ上のstaticオブジェクト格納キー : 採番・カウンタサーバのURL情報 */
	private static final String STATIC_NAME_ALSERVERURL = "_alserverurl";
	/** メモリ上のstaticオブジェクト格納キー : サービスごとのManifsetサーバ情報 lock */
	private static final String STATIC_NAME_SERVICEMNF_LOCK = "_servicemnf_lock";
	/** メモリ上のstaticオブジェクト格納キー : サービスごとのManifsetサーバ情報 */
	private static final String STATIC_NAME_SERVICEMNF = "_servicemnf";
	/** メモリ上のstaticオブジェクト格納キー : EntryサーバのURL情報 lock */
	private static final String STATIC_NAME_SERVICEENTRY_LOCK = "_serviceentry_lock";
	/** メモリ上のstaticオブジェクト格納キー : 全文検索インデックスサーバのURL情報 */
	private static final String STATIC_NAME_SERVICEENTRY = "_serviceentry";
	/** メモリ上のstaticオブジェクト格納キー : インデックスサーバのURL情報 lock */
	private static final String STATIC_NAME_SERVICEIDX_LOCK = "_serviceidx_lock";
	/** メモリ上のstaticオブジェクト格納キー : インデックスサーバのURL情報 */
	private static final String STATIC_NAME_SERVICEIDX = "_serviceidx";
	/** メモリ上のstaticオブジェクト格納キー : 全文検索インデックスサーバのURL情報 lock */
	private static final String STATIC_NAME_SERVICEFT_LOCK = "_serviceft_lock";
	/** メモリ上のstaticオブジェクト格納キー : 全文検索インデックスサーバのURL情報 */
	private static final String STATIC_NAME_SERVICEFT = "_serviceft";
	/** メモリ上のstaticオブジェクト格納キー : 採番・カウンタサーバのURL情報 lock */
	private static final String STATIC_NAME_SERVICEAL_LOCK = "_serviceal_lock";
	/** メモリ上のstaticオブジェクト格納キー : 採番・カウンタサーバのURL情報 */
	private static final String STATIC_NAME_SERVICEAL = "_serviceal";

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 初期処理に呼ばれるメソッド.
	 * TaggingEnv.getPropはまだ使用できない。
	 * web.xmlかプロパティファイルに設定された値の取得は、TaggingEnv.getContextValueを使用する。
	 */
	public void init() {
		// ManifestサーバのURL情報 lock
		// キー: サーバ名、値: ロックフラグ
		ConcurrentMap<String, Boolean> mnfserverUrlLockMap = new ConcurrentHashMap<>();
		try {
			ReflexStatic.setStatic(STATIC_NAME_MNFSERVERURL_LOCK, mnfserverUrlLockMap);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_MNFSERVERURL_LOCK, e);
		}
		// ManifestサーバのURL情報
		// キー: サーバ名、値: URL
		ConcurrentMap<String, StaticInfo<String>> mnfserverUrlMap = new ConcurrentHashMap<>();
		try {
			ReflexStatic.setStatic(STATIC_NAME_MNFSERVERURL, mnfserverUrlMap);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_MNFSERVERURL, e);
		}

		// EntryサーバのURL情報 lock
		// キー: サーバ名、値: ロックフラグ
		ConcurrentMap<String, Boolean> ENTRYserverUrlLockMap = new ConcurrentHashMap<>();
		try {
			ReflexStatic.setStatic(STATIC_NAME_ENTRYSERVERURL_LOCK, ENTRYserverUrlLockMap);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_ENTRYSERVERURL_LOCK, e);
		}
		// EntryサーバのURL情報
		// キー: サーバ名、値: URL
		ConcurrentMap<String, StaticInfo<String>> ENTRYserverUrlMap = new ConcurrentHashMap<>();
		try {
			ReflexStatic.setStatic(STATIC_NAME_ENTRYSERVERURL, ENTRYserverUrlMap);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_ENTRYSERVERURL, e);
		}

		// インデックスサーバのURL情報 lock
		// キー: サーバ名、値: ロックフラグ
		ConcurrentMap<String, Boolean> idxserverUrlLockMap = new ConcurrentHashMap<>();
		try {
			ReflexStatic.setStatic(STATIC_NAME_IDXSERVERURL_LOCK, idxserverUrlLockMap);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_IDXSERVERURL_LOCK, e);
		}
		// インデックスサーバのURL情報
		// キー: サーバ名、値: URL
		ConcurrentMap<String, StaticInfo<String>> idxserverUrlMap = new ConcurrentHashMap<>();
		try {
			ReflexStatic.setStatic(STATIC_NAME_IDXSERVERURL, idxserverUrlMap);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_IDXSERVERURL, e);
		}

		// 全文検索インデックスサーバのURL情報 lock
		// キー: サーバ名、値: ロックフラグ
		ConcurrentMap<String, Boolean> ftserverUrlLockMap = new ConcurrentHashMap<>();
		try {
			ReflexStatic.setStatic(STATIC_NAME_FTSERVERURL_LOCK, ftserverUrlLockMap);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_FTSERVERURL_LOCK, e);
		}
		// 全文検索インデックスサーバのURL情報
		// キー: サーバ名、値: URL
		ConcurrentMap<String, StaticInfo<String>> ftserverUrlMap = new ConcurrentHashMap<>();
		try {
			ReflexStatic.setStatic(STATIC_NAME_FTSERVERURL, ftserverUrlMap);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_FTSERVERURL, e);
		}

		// 採番・カウンタサーバのURL情報 lock
		// キー: サーバ名、値: ロックフラグ
		ConcurrentMap<String, Boolean> alserverUrlLockMap = new ConcurrentHashMap<>();
		try {
			ReflexStatic.setStatic(STATIC_NAME_ALSERVERURL_LOCK, alserverUrlLockMap);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_ALSERVERURL_LOCK, e);
		}
		// 採番・カウンタサーバのURL情報
		// キー: サーバ名、値: URL
		ConcurrentMap<String, StaticInfo<String>> alserverUrlMap = new ConcurrentHashMap<>();
		try {
			ReflexStatic.setStatic(STATIC_NAME_ALSERVERURL, alserverUrlMap);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_ALSERVERURL, e);
		}

		// サービスごとのManifestサーバ情報 lock
		// キー: サービス名、値: ロックフラグ
		ConcurrentMap<String, Boolean> serviceMnfLockMap = new ConcurrentHashMap<>();
		try {
			ReflexStatic.setStatic(STATIC_NAME_SERVICEMNF_LOCK, serviceMnfLockMap);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_SERVICEMNF_LOCK, e);
		}
		// サービスごとのManifestサーバ情報
		// キー: サービス名、値: Manifestサーバ名(1件だが、他のサーバと処理を共通とするためListで持つ。)
		ConcurrentMap<String, StaticInfo<List<String>>> serviceMnfMap = new ConcurrentHashMap<>();
		try {
			ReflexStatic.setStatic(STATIC_NAME_SERVICEMNF, serviceMnfMap);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_SERVICEMNF, e);
		}

		// サービスごとのEntryサーバ情報 lock
		// キー: サービス名、値: ロックフラグ
		ConcurrentMap<String, Boolean> serviceEntryLockMap = new ConcurrentHashMap<>();
		try {
			ReflexStatic.setStatic(STATIC_NAME_SERVICEENTRY_LOCK, serviceEntryLockMap);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_SERVICEENTRY_LOCK, e);
		}
		// サービスごとのEntryサーバ情報
		// キー: サービス名、値: Entryサーバ名リスト
		ConcurrentMap<String, StaticInfo<List<String>>> serviceEntryMap = new ConcurrentHashMap<>();
		try {
			ReflexStatic.setStatic(STATIC_NAME_SERVICEENTRY, serviceEntryMap);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_SERVICEENTRY, e);
		}

		// サービスごとのIndexサーバ情報 lock
		// キー: サービス名、値: ロックフラグ
		ConcurrentMap<String, Boolean> serviceIdxLockMap = new ConcurrentHashMap<>();
		try {
			ReflexStatic.setStatic(STATIC_NAME_SERVICEIDX_LOCK, serviceIdxLockMap);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_SERVICEIDX_LOCK, e);
		}
		// サービスごとのIndexサーバ情報
		// キー: サービス名、値: Indexサーバ名リスト
		ConcurrentMap<String, StaticInfo<List<String>>> serviceIdxMap = new ConcurrentHashMap<>();
		try {
			ReflexStatic.setStatic(STATIC_NAME_SERVICEIDX, serviceIdxMap);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_SERVICEIDX, e);
		}

		// サービスごとの全文検索インデックスサーバ情報 lock
		// キー: サービス名、値: ロックフラグ
		ConcurrentMap<String, Boolean> serviceFtLockMap = new ConcurrentHashMap<>();
		try {
			ReflexStatic.setStatic(STATIC_NAME_SERVICEFT_LOCK, serviceFtLockMap);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_SERVICEFT_LOCK, e);
		}
		// サービスごとの全文検索インデックスサーバ情報
		// キー: サービス名、値: 全文検索インデックスサーバ名リスト
		ConcurrentMap<String, StaticInfo<List<String>>> serviceFtMap = new ConcurrentHashMap<>();
		try {
			ReflexStatic.setStatic(STATIC_NAME_SERVICEFT, serviceFtMap);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_SERVICEFT, e);
		}

		// サービスごとの採番・カウンタサーバ情報 lock
		// キー: サービス名、値: ロックフラグ
		ConcurrentMap<String, Boolean> serviceAlLockMap = new ConcurrentHashMap<>();
		try {
			ReflexStatic.setStatic(STATIC_NAME_SERVICEAL_LOCK, serviceAlLockMap);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_SERVICEAL_LOCK, e);
		}
		// サービスごとの採番・カウンタサーバ情報
		// キー: サービス名、値: 採番・カウンタサーバ名リスト
		ConcurrentMap<String, StaticInfo<List<String>>> serviceAlMap = new ConcurrentHashMap<>();
		try {
			ReflexStatic.setStatic(STATIC_NAME_SERVICEAL, serviceAlMap);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_SERVICEAL, e);
		}

	}

	/**
	 * サービスのManifestサーバURLを取得.
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return URL
	 */
	public String getMnfServerUrl(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// システムサービスの場合、プロパティから取得する。
		String systemService = TaggingEnvUtil.getSystemService();
		if (systemService.equals(serviceName)) {
			return BDBClientServerUtil.getMnfServerUrlOfSystem();
		}

		// 一般サービスの場合、サービスのManifestサーバ名リストを取得
		List<String> serverNames = getBDBServerNames(serviceName, BDBServerType.MANIFEST,
				requestInfo, connectionInfo);
		if (serverNames == null || serverNames.isEmpty()) {
			return null;
		}
		// Manifestは1件のみ
		String serverName = serverNames.get(0);
		// サーバ名からURLを取得
		return getBDBServerUrl(serverName, BDBServerType.MANIFEST,
				requestInfo, connectionInfo);
	}

	/**
	 * サービスのEntryサーバURLリストを取得.
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return URLリスト
	 */
	public List<String> getEntryServerUrls(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// システムサービスの場合、プロパティから取得する。
		String systemService = TaggingEnvUtil.getSystemService();
		if (systemService.equals(serviceName)) {
			return BDBClientServerUtil.getEntryServerUrlsOfSystem();
		}

		// 一般サービスの場合、サービスのEntryサーバ名リストを取得
		List<String> serverNames = getBDBServerNames(serviceName, BDBServerType.ENTRY,
				requestInfo, connectionInfo);
		if (serverNames == null || serverNames.isEmpty()) {
			return null;
		}
		List<String> serverUrls = new ArrayList<>();
		for (String serverName : serverNames) {
			String serverUrl = getBDBServerUrl(serverName, BDBServerType.ENTRY,
					requestInfo, connectionInfo);
			serverUrls.add(serverUrl);
		}
		return serverUrls;
	}

	/**
	 * サービスのインデックスサーバURLリストを取得.
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return URLリスト
	 */
	public List<String> getIdxServerUrls(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// システムサービスの場合、プロパティから取得する。
		String systemService = TaggingEnvUtil.getSystemService();
		if (systemService.equals(serviceName)) {
			return BDBClientServerUtil.getIdxServerUrlsOfSystem();
		}

		// 一般サービスの場合、サービスのインデックスサーバ名リストを取得
		List<String> serverNames = getBDBServerNames(serviceName, BDBServerType.INDEX,
				requestInfo, connectionInfo);
		if (serverNames == null || serverNames.isEmpty()) {
			return null;
		}
		List<String> serverUrls = new ArrayList<>();
		for (String serverName : serverNames) {
			String serverUrl = getBDBServerUrl(serverName, BDBServerType.INDEX,
					requestInfo, connectionInfo);
			serverUrls.add(serverUrl);
		}
		return serverUrls;
	}

	/**
	 * サービスの全文検索インデックスサーバURLリストを取得.
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return URLリスト
	 */
	public List<String> getFtServerUrls(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// システムサービスの場合、プロパティから取得する。
		String systemService = TaggingEnvUtil.getSystemService();
		if (systemService.equals(serviceName)) {
			return BDBClientServerUtil.getFtServerUrlsOfSystem();
		}

		// 一般サービスの場合、サービスの全文検索インデックスサーバ名リストを取得
		List<String> serverNames = getBDBServerNames(serviceName, BDBServerType.FULLTEXT,
				requestInfo, connectionInfo);
		if (serverNames == null || serverNames.isEmpty()) {
			return null;
		}
		List<String> serverUrls = new ArrayList<>();
		for (String serverName : serverNames) {
			String serverUrl = getBDBServerUrl(serverName, BDBServerType.FULLTEXT,
					requestInfo, connectionInfo);
			serverUrls.add(serverUrl);
		}
		return serverUrls;
	}

	/**
	 * サービスの採番・カウンタサーバURLリストを取得.
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return URLリスト
	 */
	public List<String> getAlServerUrls(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// システムサービスの場合、プロパティから取得する。
		String systemService = TaggingEnvUtil.getSystemService();
		if (systemService.equals(serviceName)) {
			return BDBClientServerUtil.getAlServerUrlsOfSystem();
		}

		// 一般サービスの場合、サービスの採番・カウンタサーバ名リストを取得
		List<String> serverNames = getBDBServerNames(serviceName, BDBServerType.ALLOCIDS,
				requestInfo, connectionInfo);
		if (serverNames == null || serverNames.isEmpty()) {
			return null;
		}
		List<String> serverUrls = new ArrayList<>();
		for (String serverName : serverNames) {
			String serverUrl = getBDBServerUrl(serverName, BDBServerType.ALLOCIDS,
					requestInfo, connectionInfo);
			serverUrls.add(serverUrl);
		}
		return serverUrls;
	}

	/**
	 * サーバ名からManifestサーバURLを取得.
	 * @param bdbServerName Manifestサーバ名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return URL
	 */
	public String getMnfServerUrlByServerName(String bdbServerName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		return getBDBServerUrl(bdbServerName, BDBServerType.MANIFEST,
				requestInfo, connectionInfo);
	}

	/**
	 * サーバ名からEntryサーバURLを取得.
	 * @param bdbServerName Entryサーバ名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return URL
	 */
	public String getEntryServerUrlByServerName(String bdbServerName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		return getBDBServerUrl(bdbServerName, BDBServerType.ENTRY,
				requestInfo, connectionInfo);
	}

	/**
	 * サーバ名からインデックスサーバURLを取得.
	 * @param bdbServerName インデックスサーバ名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return URL
	 */
	public String getIdxServerUrlByServerName(String bdbServerName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		return getBDBServerUrl(bdbServerName, BDBServerType.INDEX,
				requestInfo, connectionInfo);
	}

	/**
	 * サーバ名から全文検索インデックスサーバURLを取得.
	 * @param bdbServerName 全文検索インデックスサーバ名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return URL
	 */
	public String getFtServerUrlByServerName(String bdbServerName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		return getBDBServerUrl(bdbServerName, BDBServerType.FULLTEXT,
				requestInfo, connectionInfo);
	}

	/**
	 * サーバ名から採番・カウンタサーバURLを取得.
	 * @param bdbServerName 採番・カウンタサーバ名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return URL
	 */
	public String getAlServerUrlByServerName(String bdbServerName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		return getBDBServerUrl(bdbServerName, BDBServerType.ALLOCIDS,
				requestInfo, connectionInfo);
	}

	/**
	 * BDBサーバ名、BDBサーバタイプからURLを取得.
	 * @param serverName BDBサーバ名
	 * @param serverType BDBサーバタイプ
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return URL
	 */
	public String getBDBServerUrl(String serverName, BDBServerType serverType,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		if (StringUtils.isBlank(serverName)) {
			return null;
		}
		// まずstatic情報から取得
		StaticInfo<String> serverUrlInfo = getStaticInfoServerUrl(serverName, serverType);
		boolean getFromBDB = false;
		if (serverUrlInfo == null) {
			getFromBDB = true;
		} else {
			// 取得時間のチェック
			getFromBDB = StaticInfoUtil.isExceeded(serverUrlInfo.getAccesstime());
		}

		if (getFromBDB) {
			// BDBから取得
			return getBDBServerUrlFromBDB(serverName, serverType, requestInfo, connectionInfo);
		} else {
			// static情報を返す
			return serverUrlInfo.getInfo();
		}
	}

	/**
	 * サービス名からサーバ名リストを取得.
	 * @param serviceName サービス名
	 * @param serverType BDBサーバタイプ (Manifest以外)
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return URL
	 */
	public List<String> getBDBServerNames(String serviceName, BDBServerType serverType,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		if (StringUtils.isBlank(serviceName)) {
			return null;
		}
		// まずstatic情報から取得
		StaticInfo<List<String>> serverNameInfo = getStaticInfoServerNames(serviceName,
				serverType);
		boolean getFromBDB = false;
		if (serverNameInfo == null) {
			getFromBDB = true;
		} else {
			// 取得時間のチェック
			// サービスステータスやBDBサーバの追加・削除判定のため、サービスエントリーをチェックする。
			ServiceBlogic serviceBlogic = new ServiceBlogic();
			getFromBDB = serviceBlogic.isNeedToUpdateStaticInfo(serviceName,
					serverNameInfo.getAccesstime(), requestInfo, connectionInfo);
		}

		if (getFromBDB) {
			// BDBから取得
			return getBDBServerNamesFromBDB(serviceName, serverType,
					requestInfo, connectionInfo);
		} else {
			// static情報を返す
			return serverNameInfo.getInfo();
		}
	}

	/**
	 * Static情報からBDBサーバURLを取得.
	 * @param serverName サーバ名
	 * @param serverType サーバタイプ
	 * @return BDBサーバURL
	 */
	private StaticInfo<String> getStaticInfoServerUrl(String serverName,
			BDBServerType serverType) {
		StaticInfo<String> serverUrlInfo = null;
		if (BDBServerType.MANIFEST.equals(serverType)) {
			serverUrlInfo = getStaticMnfServerUrlInfo(serverName);
		} else if (BDBServerType.ENTRY.equals(serverType)) {
			serverUrlInfo = getStaticEntryServerUrlInfo(serverName);
		} else if (BDBServerType.INDEX.equals(serverType)) {
			serverUrlInfo = getStaticIdxServerUrlInfo(serverName);
		} else if (BDBServerType.FULLTEXT.equals(serverType)) {
			serverUrlInfo = getStaticFtServerUrlInfo(serverName);
		} else {	// allocids
			serverUrlInfo = getStaticAlServerUrlInfo(serverName);
		}
		return serverUrlInfo;
	}

	/**
	 * Static情報から対象サービスのBDBサーバ名リストを取得.
	 * @param serviceName サービス名
	 * @param serverType サーバタイプ
	 * @return BDBサーバ名リスト
	 */
	private StaticInfo<List<String>> getStaticInfoServerNames(
			String serviceName, BDBServerType serverType) {
		StaticInfo<List<String>> serverNameInfo = null;
		if (BDBServerType.MANIFEST.equals(serverType)) {
			serverNameInfo = getStaticServiceMnfInfo(serviceName);
		} else if (BDBServerType.ENTRY.equals(serverType)) {
			serverNameInfo = getStaticServiceEntryInfo(serviceName);
		} else if (BDBServerType.INDEX.equals(serverType)) {
			serverNameInfo = getStaticServiceIdxInfo(serviceName);
		} else if (BDBServerType.FULLTEXT.equals(serverType)) {
			serverNameInfo = getStaticServiceFtInfo(serviceName);
		} else {	// allocids
			serverNameInfo = getStaticServiceAlInfo(serviceName);
		}
		return serverNameInfo;
	}

	/**
	 * BDBからサーバ名を元にURLを取得.
	 * @param serverName BDBサーバ名
	 * @param serverType BDBサーバタイプ
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return URL
	 */
	private String getBDBServerUrlFromBDB(String serverName, BDBServerType serverType,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// lock map
		ConcurrentMap<String, Boolean> bdbServerUrlLockMap = null;
		if (BDBServerType.MANIFEST.equals(serverType)) {
			bdbServerUrlLockMap = getStaticMnfServerUrlLockMap();
		} else if (BDBServerType.ENTRY.equals(serverType)) {
			bdbServerUrlLockMap = getStaticEntryServerUrlLockMap();
		} else if (BDBServerType.INDEX.equals(serverType)) {
			bdbServerUrlLockMap = getStaticIdxServerUrlLockMap();
		} else if (BDBServerType.FULLTEXT.equals(serverType)) {
			bdbServerUrlLockMap = getStaticFtServerUrlLockMap();
		} else {	// allocds
			bdbServerUrlLockMap = getStaticAlServerUrlLockMap();
		}

		int numRetries = StaticInfoUtil.getStaticinfoRetryCount();
		int waitMillis = StaticInfoUtil.getStaticinfoRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			// lockフラグを取得
			Boolean lock = false;
			try {
				lock = bdbServerUrlLockMap.putIfAbsent(serverName, true);
				if (lock == null) {
					// BDBからサーバ名リストを取得
					String serverUrl = BDBClientServerUtil.getBDBServerUrlFromBDB(
							serverName, serverType, requestInfo, connectionInfo);

					StaticInfo<String> serverUrlInfo = new StaticInfo<>(serverUrl, new Date());
					if (BDBServerType.MANIFEST.equals(serverType)) {
						setStaticMnfServerUrlInfo(serverName, serverUrlInfo);
					} else if (BDBServerType.ENTRY.equals(serverType)) {
						setStaticEntryServerUrlInfo(serverName, serverUrlInfo);
					} else if (BDBServerType.INDEX.equals(serverType)) {
						setStaticIdxServerUrlInfo(serverName, serverUrlInfo);
					} else if (BDBServerType.FULLTEXT.equals(serverType)) {
						setStaticFtServerUrlInfo(serverName, serverUrlInfo);
					} else {	// allocds
						setStaticAlServerUrlInfo(serverName, serverUrlInfo);
					}
					return serverUrl;
				}
				// lockフラグを取得できなかった場合
				// 猶予期間であれば現在の情報を返却する。
				StaticInfo<String> serverUrlInfo = getStaticInfoServerUrl(serverName, serverType);
				if (serverUrlInfo != null) {
					if (!isExceededDeferment(serverUrlInfo.getAccesstime())) {
						return serverUrlInfo.getInfo();
					}
				}

				// 一定時間待つ
				sleep(waitMillis + r * 1);

				// 再度static情報から取得
				serverUrlInfo = getStaticInfoServerUrl(serverName, serverType);
				boolean getFromBDB = false;
				if (serverUrlInfo == null) {
					getFromBDB = true;
				} else {
					// 取得時間のチェック
					getFromBDB = isExceededDeferment(serverUrlInfo.getAccesstime());
				}
				if (!getFromBDB) {
					return serverUrlInfo.getInfo();
				}

			} finally {
				if (lock == null) {
					// lock解除
					bdbServerUrlLockMap.remove(serverName);
				}
			}
		}
		// ここにたどり着いた場合、lockエラー
		String msg = "The static infomation is locked. " + serverType + " server url";
		LockingException e = new LockingException(msg);
		StringBuilder sb = new StringBuilder();
		sb.append(LogUtil.getRequestInfoStr(requestInfo));
		sb.append("[getBDBServerUrlFromBDB] LockingException: ");
		sb.append(msg);
		logger.warn(sb.toString(), e);
		throw e;
	}

	/**
	 * サービスのサーバ名リストを取得.
	 * @param serviceName サービス名
	 * @param serverType BDBサーバタイプ
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return サービスのサーバ名リスト
	 */
	private List<String> getBDBServerNamesFromBDB(String serviceName, BDBServerType serverType,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// lock map
		ConcurrentMap<String, Boolean> bdbServerNamesLockMap = null;
		if (BDBServerType.MANIFEST.equals(serverType)) {
			bdbServerNamesLockMap = getStaticServiceMnfLockMap();
		} else if (BDBServerType.ENTRY.equals(serverType)) {
			bdbServerNamesLockMap = getStaticServiceEntryLockMap();
		} else if (BDBServerType.INDEX.equals(serverType)) {
			bdbServerNamesLockMap = getStaticServiceIdxLockMap();
		} else if (BDBServerType.FULLTEXT.equals(serverType)) {
			bdbServerNamesLockMap = getStaticServiceFtLockMap();
		} else {	// allocids
			bdbServerNamesLockMap = getStaticServiceAlLockMap();
		}

		int numRetries = StaticInfoUtil.getStaticinfoRetryCount();
		int waitMillis = StaticInfoUtil.getStaticinfoRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			// lockフラグを取得
			Boolean lock = false;
			long startTime = 0;
			try {
				lock = bdbServerNamesLockMap.putIfAbsent(serviceName, true);
				if (lock == null) {
					if (isEnableAccessLog()) {
						StringBuilder sb = new StringBuilder();
						sb.append(LogUtil.getRequestInfoStr(requestInfo));
						sb.append("[getBDBServerNamesFromBDB] serverType=");
						sb.append(serverType.name());
						sb.append(" lock start.");
						logger.debug(sb.toString());
						startTime = new Date().getTime();
					}
					// BDBからサーバURLを取得
					List<String> serverNames = BDBClientServerUtil.getBDBServerNamesFromBDB(
							serviceName, serverType, requestInfo, connectionInfo);
					changeStaticServerNames(serviceName, serverType, serverNames);
					return serverNames;
				}

				// lockフラグを取得できなかった場合
				// 猶予期間であれば現在の情報を返却する。
				StaticInfo<List<String>> serverNameInfo = getStaticInfoServerNames(
						serviceName, serverType);
				if (serverNameInfo != null) {
					if (!isExceededDeferment(serverNameInfo.getAccesstime())) {
						return serverNameInfo.getInfo();
					}
				}

				// 一定時間待つ
				int tmpWaitMillis = waitMillis + r;
				if (isEnableAccessLog()) {
					StringBuilder sb = new StringBuilder();
					sb.append(LogUtil.getRequestInfoStr(requestInfo));
					sb.append("[getBDBServerNamesFromBDB] serverType=");
					sb.append(serverType.name());
					sb.append(" sleep (");
					sb.append(r);
					sb.append(") (");
					sb.append(tmpWaitMillis);
					sb.append(" ms)");
					logger.debug(sb.toString());
				}
				sleep(tmpWaitMillis);

				// 再度static情報から取得
				serverNameInfo = getStaticInfoServerNames(serviceName, serverType);
				boolean getFromBDB = false;
				if (serverNameInfo == null) {
					getFromBDB = true;
					if (isEnableAccessLog()) {
						StringBuilder sb = new StringBuilder();
						sb.append(LogUtil.getRequestInfoStr(requestInfo));
						sb.append("[getBDBServerNamesFromBDB] serverType=");
						sb.append(serverType.name());
						sb.append(" serverNameInfo is null (static).");
						logger.debug(sb.toString());
					}
				} else {
					// 取得時間のチェック
					getFromBDB = isExceededDeferment(serverNameInfo.getAccesstime());
					if (isEnableAccessLog()) {
						StringBuilder sb = new StringBuilder();
						sb.append(LogUtil.getRequestInfoStr(requestInfo));
						sb.append("[getBDBServerNamesFromBDB] serverType=");
						sb.append(serverType.name());
						sb.append(" getFromBDB=");
						sb.append(getFromBDB);
						sb.append(" serverNameInfo.getAccesstime()=");
						sb.append(DateUtil.getDateTimeFormat(serverNameInfo.getAccesstime(), "yyyy-MM-dd HH:mm:ss.SSS"));
						logger.debug(sb.toString());
					}
				}
				if (!getFromBDB) {
					// 他スレッドが更新した情報を返却
					if (logger.isTraceEnabled()) {
						StringBuilder sb = new StringBuilder();
						sb.append(LogUtil.getRequestInfoStr(requestInfo));
						sb.append("[getBDBServerNamesFromBDB] serverType=");
						sb.append(serverType.name());
						sb.append(" get staticInfo.");
						logger.debug(sb.toString());
					}
					return serverNameInfo.getInfo();
				}

			} finally {
				if (lock == null) {
					// lock解除
					bdbServerNamesLockMap.remove(serviceName);
					if (isEnableAccessLog()) {
						StringBuilder sb = new StringBuilder();
						sb.append(LogUtil.getRequestInfoStr(requestInfo));
						sb.append("[getBDBServerNamesFromBDB] serverType=");
						sb.append(serverType.name());
						sb.append(" lock end.");
						sb.append(LogUtil.getElapsedTimeLog(startTime));
						logger.debug(sb.toString());
					}
				}
			}
		}
		// ここにたどり着いた場合、lockエラー
		String msg = "The static infomation is locked. " + serverType + " server names";
		LockingException e = new LockingException(msg);
		StringBuilder sb = new StringBuilder();
		sb.append(LogUtil.getRequestInfoStr(requestInfo));
		sb.append("[getBDBServerNamesFromBDB] LockingException: ");
		sb.append(msg);
		logger.warn(sb.toString(), e);
		throw e;
	}

	/**
	 * static mapより、ManifestサーバURL情報を取得.
	 * @param serverName Manifestサーバ名
	 * @return ManifestサーバURLと取得日時
	 */
	private StaticInfo<String> getStaticMnfServerUrlInfo(String serverName) {
		ConcurrentMap<String, StaticInfo<String>> serverUrlMap =
				(ConcurrentMap<String, StaticInfo<String>>)ReflexStatic.getStatic(
						STATIC_NAME_MNFSERVERURL);
		if (serverUrlMap == null) {
			throw new IllegalStateException("static infomation does not exist. " + STATIC_NAME_MNFSERVERURL);
		}
		return serverUrlMap.get(serverName);
	}

	/**
	 * static mapに、ManifestサーバURLを設定.
	 * @param serverName Manifestサーバ名
	 * @param serverUrlInfo ManifestサーバURLと取得日時
	 */
	private void setStaticMnfServerUrlInfo(String serverName, StaticInfo<String> serverUrlInfo) {
		ConcurrentMap<String, StaticInfo<String>> serverUrlMap =
				(ConcurrentMap<String, StaticInfo<String>>)ReflexStatic.getStatic(
						STATIC_NAME_MNFSERVERURL);
		if (serverUrlMap == null) {
			throw new IllegalStateException("static infomation does not exist. " + STATIC_NAME_MNFSERVERURL);
		}
		serverUrlMap.put(serverName, serverUrlInfo);
	}

	/**
	 * static mapより、ManifestサーバURLのlock mapを取得.
	 * @return ManifestサーバURLのlock map
	 */
	private ConcurrentMap<String, Boolean> getStaticMnfServerUrlLockMap() {
		ConcurrentMap<String, Boolean> serverUrlLockMap =
				(ConcurrentMap<String, Boolean>)ReflexStatic.getStatic(
						STATIC_NAME_MNFSERVERURL_LOCK);
		if (serverUrlLockMap == null) {
			throw new IllegalStateException("static infomation does not exist. " + STATIC_NAME_MNFSERVERURL_LOCK);
		}
		return serverUrlLockMap;
	}

	/**
	 * static mapより、EntryサーバURL情報を取得.
	 * @param serverName Entryサーバ名
	 * @return EntryサーバURLと取得日時
	 */
	private StaticInfo<String> getStaticEntryServerUrlInfo(String serverName) {
		ConcurrentMap<String, StaticInfo<String>> serverUrlMap =
				(ConcurrentMap<String, StaticInfo<String>>)ReflexStatic.getStatic(
						STATIC_NAME_ENTRYSERVERURL);
		if (serverUrlMap == null) {
			throw new IllegalStateException("static infomation does not exist. " + STATIC_NAME_ENTRYSERVERURL);
		}
		return serverUrlMap.get(serverName);
	}

	/**
	 * static mapに、EntryサーバURLを設定.
	 * @param serverName Entryサーバ名
	 * @param serverUrlInfo EntryサーバURLと取得日時
	 */
	private void setStaticEntryServerUrlInfo(String serverName, StaticInfo<String> serverUrlInfo) {
		ConcurrentMap<String, StaticInfo<String>> serverUrlMap =
				(ConcurrentMap<String, StaticInfo<String>>)ReflexStatic.getStatic(
						STATIC_NAME_ENTRYSERVERURL);
		if (serverUrlMap == null) {
			throw new IllegalStateException("static infomation does not exist. " + STATIC_NAME_ENTRYSERVERURL);
		}
		serverUrlMap.put(serverName, serverUrlInfo);
	}

	/**
	 * static mapより、EntryサーバURLのlock mapを取得.
	 * @return EntryサーバURLのlock map
	 */
	private ConcurrentMap<String, Boolean> getStaticEntryServerUrlLockMap() {
		ConcurrentMap<String, Boolean> serverUrlLockMap =
				(ConcurrentMap<String, Boolean>)ReflexStatic.getStatic(
						STATIC_NAME_ENTRYSERVERURL_LOCK);
		if (serverUrlLockMap == null) {
			throw new IllegalStateException("static infomation does not exist. " + STATIC_NAME_ENTRYSERVERURL_LOCK);
		}
		return serverUrlLockMap;
	}

	/**
	 * static mapより、インデックスサーバURL情報を取得.
	 * @param serverName インデックスサーバ名
	 * @return インデックスサーバURLと取得日時
	 */
	private StaticInfo<String> getStaticIdxServerUrlInfo(String serverName) {
		ConcurrentMap<String, StaticInfo<String>> serverUrlMap =
				(ConcurrentMap<String, StaticInfo<String>>)ReflexStatic.getStatic(
						STATIC_NAME_IDXSERVERURL);
		if (serverUrlMap == null) {
			throw new IllegalStateException("static infomation does not exist. " + STATIC_NAME_IDXSERVERURL);
		}
		return serverUrlMap.get(serverName);
	}

	/**
	 * static mapに、インデックスサーバURLを設定.
	 * @param serverName インデックスサーバ名
	 * @param serverUrlInfo インデックスサーバURLと取得日時
	 */
	private void setStaticIdxServerUrlInfo(String serverName, StaticInfo<String> serverUrlInfo) {
		ConcurrentMap<String, StaticInfo<String>> serverUrlMap =
				(ConcurrentMap<String, StaticInfo<String>>)ReflexStatic.getStatic(
						STATIC_NAME_IDXSERVERURL);
		if (serverUrlMap == null) {
			throw new IllegalStateException("static infomation does not exist. " + STATIC_NAME_IDXSERVERURL);
		}
		serverUrlMap.put(serverName, serverUrlInfo);
	}

	/**
	 * static mapより、インデックスサーバURLのlock mapを取得.
	 * @return インデックスサーバURLのlock map
	 */
	private ConcurrentMap<String, Boolean> getStaticIdxServerUrlLockMap() {
		ConcurrentMap<String, Boolean> serverUrlLockMap =
				(ConcurrentMap<String, Boolean>)ReflexStatic.getStatic(
						STATIC_NAME_IDXSERVERURL_LOCK);
		if (serverUrlLockMap == null) {
			throw new IllegalStateException("static infomation does not exist. " + STATIC_NAME_IDXSERVERURL_LOCK);
		}
		return serverUrlLockMap;
	}

	/**
	 * static mapより、全文検索インデックスサーバURL情報を取得.
	 * @param serverName 全文検索インデックスサーバ名
	 * @return 全文検索インデックスサーバURLと取得日時
	 */
	private StaticInfo<String> getStaticFtServerUrlInfo(String serverName) {
		ConcurrentMap<String, StaticInfo<String>> serverUrlMap =
				(ConcurrentMap<String, StaticInfo<String>>)ReflexStatic.getStatic(
						STATIC_NAME_FTSERVERURL);
		if (serverUrlMap == null) {
			throw new IllegalStateException("static infomation does not exist. " + STATIC_NAME_FTSERVERURL);
		}
		return serverUrlMap.get(serverName);
	}

	/**
	 * static mapに、全文検索インデックスサーバURLを設定.
	 * @param serverName 全文検索インデックスサーバ名
	 * @param serverUrlInfo 全文検索インデックスサーバURLと取得日時
	 */
	private void setStaticFtServerUrlInfo(String serverName, StaticInfo<String> serverUrlInfo) {
		ConcurrentMap<String, StaticInfo<String>> serverUrlMap =
				(ConcurrentMap<String, StaticInfo<String>>)ReflexStatic.getStatic(
						STATIC_NAME_FTSERVERURL);
		if (serverUrlMap == null) {
			throw new IllegalStateException("static infomation does not exist. " + STATIC_NAME_FTSERVERURL);
		}
		serverUrlMap.put(serverName, serverUrlInfo);
	}

	/**
	 * static mapより、全文検索インデックスサーバURLのlock mapを取得.
	 * @return 全文検索インデックスサーバURLのlock map
	 */
	private ConcurrentMap<String, Boolean> getStaticFtServerUrlLockMap() {
		ConcurrentMap<String, Boolean> serverUrlLockMap =
				(ConcurrentMap<String, Boolean>)ReflexStatic.getStatic(
						STATIC_NAME_FTSERVERURL_LOCK);
		if (serverUrlLockMap == null) {
			throw new IllegalStateException("static infomation does not exist. " + STATIC_NAME_FTSERVERURL_LOCK);
		}
		return serverUrlLockMap;
	}

	/**
	 * static mapより、採番・カウンタサーバURL情報を取得.
	 * @param serverName 採番・カウンタサーバ名
	 * @return 採番・カウンタサーバURLと取得日時
	 */
	private StaticInfo<String> getStaticAlServerUrlInfo(String serverName) {
		ConcurrentMap<String, StaticInfo<String>> serverUrlMap =
				(ConcurrentMap<String, StaticInfo<String>>)ReflexStatic.getStatic(
						STATIC_NAME_ALSERVERURL);
		if (serverUrlMap == null) {
			throw new IllegalStateException("static infomation does not exist. " + STATIC_NAME_ALSERVERURL);
		}
		return serverUrlMap.get(serverName);
	}

	/**
	 * static mapに、採番・カウンタサーバURLを設定.
	 * @param serverName 採番・カウンタサーバ名
	 * @param serverUrlInfo 採番・カウンタサーバURLと取得日時
	 */
	private void setStaticAlServerUrlInfo(String serverName, StaticInfo<String> serverUrlInfo) {
		ConcurrentMap<String, StaticInfo<String>> serverUrlMap =
				(ConcurrentMap<String, StaticInfo<String>>)ReflexStatic.getStatic(
						STATIC_NAME_ALSERVERURL);
		if (serverUrlMap == null) {
			throw new IllegalStateException("static infomation does not exist. " + STATIC_NAME_ALSERVERURL);
		}
		serverUrlMap.put(serverName, serverUrlInfo);
	}

	/**
	 * static mapより、採番・カウンタサーバURLのlock mapを取得.
	 * @return 採番・カウンタサーバURLのlock map
	 */
	private ConcurrentMap<String, Boolean> getStaticAlServerUrlLockMap() {
		ConcurrentMap<String, Boolean> serverUrlLockMap =
				(ConcurrentMap<String, Boolean>)ReflexStatic.getStatic(
						STATIC_NAME_ALSERVERURL_LOCK);
		if (serverUrlLockMap == null) {
			throw new IllegalStateException("static infomation does not exist. " + STATIC_NAME_ALSERVERURL_LOCK);
		}
		return serverUrlLockMap;
	}

	/**
	 * static mapより、Manifestサーバ名情報を取得.
	 * @param serviceName サービス名
	 * @return サービスのManifestサーバ名と取得日時
	 */
	private StaticInfo<List<String>> getStaticServiceMnfInfo(String serviceName) {
		ConcurrentMap<String, StaticInfo<List<String>>> serverNamesMap =
				(ConcurrentMap<String, StaticInfo<List<String>>>)ReflexStatic.getStatic(
						STATIC_NAME_SERVICEMNF);
		if (serverNamesMap == null) {
			throw new IllegalStateException("static infomation does not exist. " + STATIC_NAME_SERVICEMNF);
		}
		return serverNamesMap.get(serviceName);
	}

	/**
	 * static mapに、サービスのManifestサーバ名を設定.
	 * @param serviceName サービス名
	 * @param serverNamesInfo サービスのManifestサーバ名と取得日時
	 */
	private void setStaticServiceMnfInfo(String serviceName,
			StaticInfo<List<String>> serverNamesInfo) {
		ConcurrentMap<String, StaticInfo<List<String>>> serverNamesMap =
				(ConcurrentMap<String, StaticInfo<List<String>>>)ReflexStatic.getStatic(
						STATIC_NAME_SERVICEMNF);
		if (serverNamesMap == null) {
			throw new IllegalStateException("static infomation does not exist. " + STATIC_NAME_SERVICEMNF);
		}
		serverNamesMap.put(serviceName, serverNamesInfo);
	}

	/**
	 * static mapより、指定されたサービスのManifestサーバ名情報を削除.
	 * @param serviceName サービス名
	 */
	private void removeStaticServiceMnfInfo(String serviceName) {
		ConcurrentMap<String, StaticInfo<List<String>>> serverNamesMap =
				(ConcurrentMap<String, StaticInfo<List<String>>>)ReflexStatic.getStatic(
						STATIC_NAME_SERVICEMNF);
		if (serverNamesMap != null) {
			serverNamesMap.remove(serviceName);
		}
	}

	/**
	 * static mapより、サービスのManifestサーバ名のlock mapを取得.
	 * @return サービスのManifestサーバ名のlock map
	 */
	private ConcurrentMap<String, Boolean> getStaticServiceMnfLockMap() {
		ConcurrentMap<String, Boolean> serverUrlLockMap =
				(ConcurrentMap<String, Boolean>)ReflexStatic.getStatic(
						STATIC_NAME_SERVICEMNF_LOCK);
		if (serverUrlLockMap == null) {
			throw new IllegalStateException("static infomation does not exist. " + STATIC_NAME_SERVICEMNF_LOCK);
		}
		return serverUrlLockMap;
	}

	/**
	 * static mapより、Entryサーバ名リスト情報を取得.
	 * @param serviceName サービス名
	 * @return サービスのEntryサーバ名リストと取得日時
	 */
	private StaticInfo<List<String>> getStaticServiceEntryInfo(String serviceName) {
		ConcurrentMap<String, StaticInfo<List<String>>> serverNamesMap =
				(ConcurrentMap<String, StaticInfo<List<String>>>)ReflexStatic.getStatic(
						STATIC_NAME_SERVICEENTRY);
		if (serverNamesMap == null) {
			throw new IllegalStateException("static infomation does not exist. " + STATIC_NAME_SERVICEENTRY);
		}
		return serverNamesMap.get(serviceName);
	}

	/**
	 * static mapに、サービスのEntryサーバ名リストを設定.
	 * @param serviceName サービス名
	 * @param serverNamesInfo サービスのEntryサーバ名リストと取得日時
	 */
	private void setStaticServiceEntryInfo(String serviceName,
			StaticInfo<List<String>> serverNamesInfo) {
		ConcurrentMap<String, StaticInfo<List<String>>> serverNamesMap =
				(ConcurrentMap<String, StaticInfo<List<String>>>)ReflexStatic.getStatic(
						STATIC_NAME_SERVICEENTRY);
		if (serverNamesMap == null) {
			throw new IllegalStateException("static infomation does not exist. " + STATIC_NAME_SERVICEENTRY);
		}
		serverNamesMap.put(serviceName, serverNamesInfo);
	}

	/**
	 * static mapより、指定されたサービスのEntryサーバ名情報を削除.
	 * @param serviceName サービス名
	 */
	private void removeStaticServiceEntryInfo(String serviceName) {
		ConcurrentMap<String, StaticInfo<List<String>>> serverNamesMap =
				(ConcurrentMap<String, StaticInfo<List<String>>>)ReflexStatic.getStatic(
						STATIC_NAME_SERVICEENTRY);
		if (serverNamesMap != null) {
			serverNamesMap.remove(serviceName);
		}
	}

	/**
	 * static mapより、サービスのEntryサーバ名のlock mapを取得.
	 * @return サービスのEntryサーバ名のlock map
	 */
	private ConcurrentMap<String, Boolean> getStaticServiceEntryLockMap() {
		ConcurrentMap<String, Boolean> serverUrlLockMap =
				(ConcurrentMap<String, Boolean>)ReflexStatic.getStatic(
						STATIC_NAME_SERVICEENTRY_LOCK);
		if (serverUrlLockMap == null) {
			throw new IllegalStateException("static infomation does not exist. " + STATIC_NAME_SERVICEENTRY_LOCK);
		}
		return serverUrlLockMap;
	}

	/**
	 * static mapより、インデックスサーバ名リスト情報を取得.
	 * @param serviceName サービス名
	 * @return サービスのインデックスサーバ名リストと取得日時
	 */
	private StaticInfo<List<String>> getStaticServiceIdxInfo(String serviceName) {
		ConcurrentMap<String, StaticInfo<List<String>>> serverNamesMap =
				(ConcurrentMap<String, StaticInfo<List<String>>>)ReflexStatic.getStatic(
						STATIC_NAME_SERVICEIDX);
		if (serverNamesMap == null) {
			throw new IllegalStateException("static infomation does not exist. " + STATIC_NAME_SERVICEIDX);
		}
		return serverNamesMap.get(serviceName);
	}

	/**
	 * static mapに、サービスのインデックスサーバ名リストを設定.
	 * @param serviceName サービス名
	 * @param serverNamesInfo サービスのインデックスサーバ名リストと取得日時
	 */
	private void setStaticServiceIdxInfo(String serviceName,
			StaticInfo<List<String>> serverNamesInfo) {
		ConcurrentMap<String, StaticInfo<List<String>>> serverNamesMap =
				(ConcurrentMap<String, StaticInfo<List<String>>>)ReflexStatic.getStatic(
						STATIC_NAME_SERVICEIDX);
		if (serverNamesMap == null) {
			throw new IllegalStateException("static infomation does not exist. " + STATIC_NAME_SERVICEIDX);
		}
		serverNamesMap.put(serviceName, serverNamesInfo);
	}

	/**
	 * static mapより、指定されたサービスのインデックスサーバ名情報を削除.
	 * @param serviceName サービス名
	 */
	private void removeStaticServiceIdxInfo(String serviceName) {
		ConcurrentMap<String, StaticInfo<List<String>>> serverNamesMap =
				(ConcurrentMap<String, StaticInfo<List<String>>>)ReflexStatic.getStatic(
						STATIC_NAME_SERVICEIDX);
		if (serverNamesMap != null) {
			serverNamesMap.remove(serviceName);
		}
	}

	/**
	 * static mapより、サービスのインデックスサーバ名のlock mapを取得.
	 * @return サービスのインデックスサーバ名のlock map
	 */
	private ConcurrentMap<String, Boolean> getStaticServiceIdxLockMap() {
		ConcurrentMap<String, Boolean> serverUrlLockMap =
				(ConcurrentMap<String, Boolean>)ReflexStatic.getStatic(
						STATIC_NAME_SERVICEIDX_LOCK);
		if (serverUrlLockMap == null) {
			throw new IllegalStateException("static infomation does not exist. " + STATIC_NAME_SERVICEIDX_LOCK);
		}
		return serverUrlLockMap;
	}

	/**
	 * static mapより、全文検索インデックスサーバ名リスト情報を取得.
	 * @param serviceName サービス名
	 * @return サービスの全文検索インデックスサーバ名リストと取得日時
	 */
	private StaticInfo<List<String>> getStaticServiceFtInfo(String serviceName) {
		ConcurrentMap<String, StaticInfo<List<String>>> serverNamesMap =
				(ConcurrentMap<String, StaticInfo<List<String>>>)ReflexStatic.getStatic(
						STATIC_NAME_SERVICEFT);
		if (serverNamesMap == null) {
			throw new IllegalStateException("static infomation does not exist. " + STATIC_NAME_SERVICEFT);
		}
		return serverNamesMap.get(serviceName);
	}

	/**
	 * static mapに、サービスの全文検索インデックスサーバ名リストを設定.
	 * @param serviceName サービス名
	 * @param serverNamesInfo サービスの全文検索インデックスサーバ名リストと取得日時
	 */
	private void setStaticServiceFtInfo(String serviceName,
			StaticInfo<List<String>> serverNamesInfo) {
		ConcurrentMap<String, StaticInfo<List<String>>> serverNamesMap =
				(ConcurrentMap<String, StaticInfo<List<String>>>)ReflexStatic.getStatic(
						STATIC_NAME_SERVICEFT);
		if (serverNamesMap == null) {
			throw new IllegalStateException("static infomation does not exist. " + STATIC_NAME_SERVICEFT);
		}
		serverNamesMap.put(serviceName, serverNamesInfo);
	}

	/**
	 * static mapより、指定されたサービスのインデックスサーバ名情報を削除.
	 * @param serviceName サービス名
	 */
	private void removeStaticServiceFtInfo(String serviceName) {
		ConcurrentMap<String, StaticInfo<List<String>>> serverNamesMap =
				(ConcurrentMap<String, StaticInfo<List<String>>>)ReflexStatic.getStatic(
						STATIC_NAME_SERVICEFT);
		if (serverNamesMap != null) {
			serverNamesMap.remove(serviceName);
		}
	}

	/**
	 * static mapより、サービスの全文検索インデックスサーバ名のlock mapを取得.
	 * @return サービスの全文検索インデックスサーバ名のlock map
	 */
	private ConcurrentMap<String, Boolean> getStaticServiceFtLockMap() {
		ConcurrentMap<String, Boolean> serverUrlLockMap =
				(ConcurrentMap<String, Boolean>)ReflexStatic.getStatic(
						STATIC_NAME_SERVICEFT_LOCK);
		if (serverUrlLockMap == null) {
			throw new IllegalStateException("static infomation does not exist. " + STATIC_NAME_SERVICEFT_LOCK);
		}
		return serverUrlLockMap;
	}

	/**
	 * static mapより、採番・カウンタサーバ名リスト情報を取得.
	 * @param serviceName サービス名
	 * @return サービスの採番・カウンタサーバ名リストと取得日時
	 */
	private StaticInfo<List<String>> getStaticServiceAlInfo(String serviceName) {
		ConcurrentMap<String, StaticInfo<List<String>>> serverNamesMap =
				(ConcurrentMap<String, StaticInfo<List<String>>>)ReflexStatic.getStatic(
						STATIC_NAME_SERVICEAL);
		if (serverNamesMap == null) {
			throw new IllegalStateException("static infomation does not exist. " + STATIC_NAME_SERVICEAL);
		}
		return serverNamesMap.get(serviceName);
	}

	/**
	 * static mapに、サービスの採番・カウンタサーバ名リストを設定.
	 * @param serviceName サービス名
	 * @param serverNamesInfo サービスの採番・カウンタサーバ名リストと取得日時
	 */
	private void setStaticServiceAlInfo(String serviceName,
			StaticInfo<List<String>> serverNamesInfo) {
		ConcurrentMap<String, StaticInfo<List<String>>> serverNamesMap =
				(ConcurrentMap<String, StaticInfo<List<String>>>)ReflexStatic.getStatic(
						STATIC_NAME_SERVICEAL);
		if (serverNamesMap == null) {
			throw new IllegalStateException("static infomation does not exist. " + STATIC_NAME_SERVICEAL);
		}
		serverNamesMap.put(serviceName, serverNamesInfo);
	}

	/**
	 * static mapより、指定されたサービスの採番・カウンタサーバ名情報を削除.
	 * @param serviceName サービス名
	 */
	private void removeStaticServiceAlInfo(String serviceName) {
		ConcurrentMap<String, StaticInfo<List<String>>> serverNamesMap =
				(ConcurrentMap<String, StaticInfo<List<String>>>)ReflexStatic.getStatic(
						STATIC_NAME_SERVICEAL);
		if (serverNamesMap != null) {
			serverNamesMap.remove(serviceName);
		}
	}

	/**
	 * static mapより、サービスの採番・カウンタサーバ名のlock mapを取得.
	 * @return サービスの採番・カウンタサーバ名のlock map
	 */
	private ConcurrentMap<String, Boolean> getStaticServiceAlLockMap() {
		ConcurrentMap<String, Boolean> serverUrlLockMap =
				(ConcurrentMap<String, Boolean>)ReflexStatic.getStatic(
						STATIC_NAME_SERVICEAL_LOCK);
		if (serverUrlLockMap == null) {
			throw new IllegalStateException("static infomation does not exist. " + STATIC_NAME_SERVICEAL_LOCK);
		}
		return serverUrlLockMap;
	}

	/**
	 * スリープ処理.
	 * @param waitMillis スリープ時間(ミリ秒)
	 */
	private void sleep(long waitMillis) {
		RetryUtil.sleep(waitMillis);
	}

	/**
	 * アクセス時間がstatic情報保持の猶予期間を超過しているかどうか.
	 * 他スレッドによるLock中の場合、猶予期間であれば現在の値を使用する。
	 * @param accessTime アクセス時間
	 * @return アクセス時間がstatic情報保持の猶予期間を超過している場合true
	 */
	private boolean isExceededDeferment(Date accessTime) {
		if (accessTime == null) {
			return true;
		}
		Date now = new Date();
		int timelimitSec = StaticInfoUtil.getStaticinfoTimelimitSec() * 2;

		// accessTime + limit >= now -> false (期限内)
		// accessTime + limit < now -> true (超過)
		Date accessTimePlusLimit = DateUtil.addTime(accessTime, 0, 0, 0, 0, 0, timelimitSec, 0);
		return now.after(accessTimePlusLimit);
	}

	/**
	 * static mapのサーバ名情報を更新
	 * @param serviceName サービス名
	 * @param serverType サーバタイプ
	 * @param serverNames サーバ名リスト
	 */
	public void changeStaticServerNames(String serviceName, BDBServerType serverType,
			List<String> serverNames) {
		StaticInfo<List<String>> serverNamesInfo =
				new StaticInfo<>(serverNames, new Date());
		if (BDBServerType.MANIFEST.equals(serverType)) {
			setStaticServiceMnfInfo(serviceName, serverNamesInfo);
		} else if (BDBServerType.ENTRY.equals(serverType)) {
			setStaticServiceEntryInfo(serviceName, serverNamesInfo);
		} else if (BDBServerType.INDEX.equals(serverType)) {
			setStaticServiceIdxInfo(serviceName, serverNamesInfo);
		} else if (BDBServerType.FULLTEXT.equals(serverType)) {
			setStaticServiceFtInfo(serviceName, serverNamesInfo);
		} else {	// allocds
			setStaticServiceAlInfo(serviceName, serverNamesInfo);
		}
	}

	/**
	 * BDBサーバ情報設定処理.
	 * パフォーマンス向上のため、リクエストの最初に並列でBDBサーバ情報を取得する。
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void settingService(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// システム管理サービスの場合処理を抜ける
		String systemService = TaggingEnvUtil.getSystemService();
		if (systemService.equals(serviceName)) {
			return;
		}

		// BDBサーバURL、サーバ名の取得処理
		// Static情報が有効ならその値を読むだけ。存在しない場合・期限切れの場合はデータ取得してStatic設定を行う。
		SystemAuthentication auth = new SystemAuthentication(null, null, serviceName);
		List<Future<Boolean>> futures = new ArrayList<>();
		// Manifest
		BDBClientSettingServerCallable callable = new BDBClientSettingServerCallable(
				serviceName, BDBServerType.MANIFEST);
		Future<Boolean> future = callable.addTask(auth, requestInfo, connectionInfo);
		futures.add(future);
		// Entry
		callable = new BDBClientSettingServerCallable(serviceName, BDBServerType.ENTRY);
		future = callable.addTask(auth, requestInfo, connectionInfo);
		futures.add(future);
		// Index
		callable = new BDBClientSettingServerCallable(serviceName, BDBServerType.INDEX);
		future = callable.addTask(auth, requestInfo, connectionInfo);
		futures.add(future);
		// FullText Index
		callable = new BDBClientSettingServerCallable(serviceName, BDBServerType.FULLTEXT);
		future = callable.addTask(auth, requestInfo, connectionInfo);
		futures.add(future);
		// Allocids
		callable = new BDBClientSettingServerCallable(serviceName, BDBServerType.ALLOCIDS);
		future = callable.addTask(auth, requestInfo, connectionInfo);
		futures.add(future);

		// 処理の終了を待つ
		for (Future<Boolean> tmpFuture : futures) {
			try {
				tmpFuture.get();

			} catch (ExecutionException e) {
				Throwable cause = e.getCause();
				if (logger.isDebugEnabled()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
							"[settingService] ExecutionException: " +
							cause.getMessage());
				}
				if (cause instanceof IOException) {
					throw (IOException)cause;
				} else if (cause instanceof TaggingException) {
					throw (TaggingException)cause;
				} else {
					throw new IOException(cause);
				}
			} catch (InterruptedException e) {
				if (logger.isDebugEnabled()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
							"[settingService] InterruptedException: " +
							e.getMessage());
				}
				throw new IOException(e);
			}
		}
	}

	/**
	 * サービス情報クローズ.
	 * static領域にある指定されたサービスの情報を削除する。
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void closeService(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		removeStaticServiceMnfInfo(serviceName);
		removeStaticServiceEntryInfo(serviceName);
		removeStaticServiceIdxInfo(serviceName);
		removeStaticServiceFtInfo(serviceName);
		removeStaticServiceAlInfo(serviceName);
	}

	/**
	 * サービス初期設定に必要なURIリスト.
	 * サービス初期設定に必要なシステム管理サービスのURIリストを返却する。
	 * @param serviceName サービス名
	 * @return URIリスト
	 */
	public List<String> getSettingEntryUrisBySystem(String serviceName) {
		return null;
	}

	/**
	 * サービス初期設定に必要なFeed検索用URIリスト.
	 * サービス初期設定に必要なシステム管理サービスのFeed検索用URIリストを返却する。
	 * @param serviceName サービス名
	 * @return URIリスト
	 */
	public List<String> getSettingFeedUrisBySystem(String serviceName) {
		if (TaggingEnvUtil.getSystemService().equals(serviceName)) {
			// システム管理サービスの場合プロパティ設定のためFeed検索対象なし。
			return null;
		}
		List<String> uris = new ArrayList<>();
		// サービス割り当てBDBサーバ名 TODO static情報がないか期限切れの場合のみ返す
		uris.add(BDBClientServerUtil.getAssignedServerUri(serviceName, BDBServerType.MANIFEST));
		uris.add(BDBClientServerUtil.getAssignedServerUri(serviceName, BDBServerType.ENTRY));
		uris.add(BDBClientServerUtil.getAssignedServerUri(serviceName, BDBServerType.INDEX));
		uris.add(BDBClientServerUtil.getAssignedServerUri(serviceName, BDBServerType.FULLTEXT));
		uris.add(BDBClientServerUtil.getAssignedServerUri(serviceName, BDBServerType.ALLOCIDS));
		return uris;
	}

	/**
	 * サービス初期設定に共通して必要なURIリスト.
	 * サービス初期設定に共通して必要なシステム管理サービスのURIリストを返却する。
	 * @return URIリスト
	 */
	public List<String> getSettingEntryUrisBySystem() {
		List<String> uris = new ArrayList<>();
		uris.add(Constants.URI_BDB_SERVICE);
		return uris;
	}

	/**
	 * サービス初期設定に共通して必要なURIリスト.
	 * サービス初期設定に共通して必要なシステム管理サービスのFeed検索用URIリストを返却する。
	 * @return URIリスト
	 */
	public List<String> getSettingFeedUrisBySystem() {
		List<String> uris = new ArrayList<>();
		// BDBサーバURL情報
		uris.add(Constants.URI_BDB_MNFSERVER);
		uris.add(Constants.URI_BDB_ENTRYSERVER);
		uris.add(Constants.URI_BDB_IDXSERVER);
		uris.add(Constants.URI_BDB_FTSERVER);
		uris.add(Constants.URI_BDB_ALSERVER);
		return uris;
	}

	/**
	 * サービス初期設定に共通して必要なURIリスト.
	 * サービス初期設定に共通して必要な自サービスのURIリストを返却する。
	 * @return URIリスト
	 */
	public List<String> getSettingEntryUris() {
		return null;
	}

	/**
	 * サービス初期設定に共通して必要なURIリスト.
	 * サービス初期設定に共通して必要な自サービスのFeed検索用URIリストを返却する。
	 * @return URIリスト
	 */
	public List<String> getSettingFeedUris() {
		return null;
	}

	/**
	 * データストアのアクセスログを出力するかどうか
	 * @return データストアのアクセスログを出力する場合true;
	 */
	private boolean isEnableAccessLog() {
		return BDBClientUtil.isEnableAccessLog() && logger.isDebugEnabled();
	}

}
