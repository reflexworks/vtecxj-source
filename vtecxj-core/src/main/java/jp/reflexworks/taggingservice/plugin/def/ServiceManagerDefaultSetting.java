package jp.reflexworks.taggingservice.plugin.def;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.Contributor;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexStatic;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvConst;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.StaticDuplicatedException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.DatastoreManager;
import jp.reflexworks.taggingservice.plugin.NamespaceManager;
import jp.reflexworks.taggingservice.plugin.ResourceMapperManager;
import jp.reflexworks.taggingservice.plugin.ServiceManager;
import jp.reflexworks.taggingservice.plugin.SettingService;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.taskqueue.TaskQueueUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.RetryUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * リクエスト初期処理.
 */
public class ServiceManagerDefaultSetting {

	/** ロックフラグ */
	private static final Integer LOCK = 1;

	/** メモリ上のstaticオブジェクト格納キー : service lockマップ */
	private static final String STATIC_NAME_SERVICE_LOCKMAP ="_service_lockmap";
	/** メモリ上のstaticオブジェクト格納キー : service APIKeyマップ */
	private static final String STATIC_NAME_SERVICE_APIKEYMAP ="_service_apikeymap";
	/** メモリ上のstaticオブジェクト格納キー : service サービスキーマップ */
	private static final String STATIC_NAME_SERVICE_SERVICEKEYMAP ="_service_servicekeymap";
	/** メモリ上のstaticオブジェクト格納キー : service APIKeyのrevision + updatedマップ */
	private static final String STATIC_NAME_SERVICE_APIKEYREVISIONMAP ="_service_apikeyrevisionmap";
	/** メモリ上のstaticオブジェクト格納キー : URLのサーバ名からコンテキストパスまで */
	private static final String STATIC_NAME_SERVICE_SERVERCONTEXTPATH ="_service_servercontextpath";
	/** メモリ上のstaticオブジェクト格納キー : URLのサーバ名からコンテキストパスまでのPattern */
	private static final String STATIC_NAME_SERVICE_SERVERCONTEXTPATH_PATTERN ="_service_servercontextpath_pattern";

	/** APIKey格納接頭辞の長さ */
	private static final int URN_PREFIX_APIKEY_LEN =
			Constants.URN_PREFIX_APIKEY.length();
	/** サービスキー格納接頭辞の長さ */
	private static final int URN_PREFIX_SERVICEKEY_LEN =
			Constants.URN_PREFIX_SERVICEKEY.length();

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 初期処理.
	 * サーバ起動時に一度だけ呼び出されます。
	 */
	public void init() {
		// URLのサーバ名・コンテキストパス設定を取得し、static領域に格納
		setServerContextpath();

		// staticオブジェクトを生成し格納
		// lockMap
		ConcurrentMap<String, Integer> lockMap = new ConcurrentHashMap<String, Integer>();
		try {
			ReflexStatic.setStatic(STATIC_NAME_SERVICE_LOCKMAP, lockMap);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_SERVICE_LOCKMAP, e);
		}
		// apikeyMap
		ConcurrentMap<String, String> apiKeyMap = new ConcurrentHashMap<String, String>();
		try {
			ReflexStatic.setStatic(STATIC_NAME_SERVICE_APIKEYMAP, apiKeyMap);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_SERVICE_APIKEYMAP, e);
		}
		// servicekeyMap
		ConcurrentMap<String, String> serviceKeyMap = new ConcurrentHashMap<String, String>();
		try {
			ReflexStatic.setStatic(STATIC_NAME_SERVICE_SERVICEKEYMAP, serviceKeyMap);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_SERVICE_SERVICEKEYMAP, e);
		}
		// apikeyRevisionMap
		ConcurrentMap<String, Integer> apiKeyRevisionMap = new ConcurrentHashMap<String, Integer>();
		try {
			ReflexStatic.setStatic(STATIC_NAME_SERVICE_APIKEYREVISIONMAP, apiKeyRevisionMap);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_SERVICE_APIKEYREVISIONMAP, e);
		}
	}

	/**
	 * プロパティに設定された「サーバ名からコンテキストパスまで」の値をstatic領域に格納.
	 */
	private void setServerContextpath() {
		// URLのサーバ名・コンテキストパス設定を取得。
		String reflexServerContextpath = TaggingEnvUtil.getSystemProp(
				TaggingEnvConst.REFLEX_SERVERCONTEXTPATH, null);
		if (StringUtils.isBlank(reflexServerContextpath)) {
			throw new IllegalStateException(
					"Setting is required. " + TaggingEnvConst.REFLEX_SERVERCONTEXTPATH);
		}
		try {
			ReflexStatic.setStatic(STATIC_NAME_SERVICE_SERVERCONTEXTPATH, reflexServerContextpath);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_SERVICE_SERVERCONTEXTPATH, e);
		}

		// URLサービス取得パターンを生成
		if (reflexServerContextpath.indexOf(ServiceManagerDefaultConst.MARK_SERVICE) > -1) {
			String tmpReflexServerContextpath = reflexServerContextpath.replaceAll("\\:", "\\\\:");
			tmpReflexServerContextpath = tmpReflexServerContextpath.replaceAll("\\/", "\\\\/");
			tmpReflexServerContextpath = tmpReflexServerContextpath.replaceAll("\\.", "\\\\.");
			tmpReflexServerContextpath = tmpReflexServerContextpath.replaceAll("\\@", "(.+)");
			Pattern reflexServerContextpathPattern = Pattern.compile(tmpReflexServerContextpath);
			try {
				ReflexStatic.setStatic(STATIC_NAME_SERVICE_SERVERCONTEXTPATH_PATTERN, reflexServerContextpathPattern);
			} catch (StaticDuplicatedException e) {
				logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_SERVICE_SERVERCONTEXTPATH_PATTERN, e);
			}
		}
	}

	/**
	 * static領域からサーバ名・コンテキストパスを取得.
	 * @return サーバ名・コンテキストパス
	 */
	String getServerContextpath() {
		return (String)ReflexStatic.getStatic(
				STATIC_NAME_SERVICE_SERVERCONTEXTPATH);
	}

	/**
	 * static領域からサーバ名・コンテキストパスのサービス名抽出パターンを取得.
	 * @return サーバ名・コンテキストパスのサービス名抽出パターン
	 */
	Pattern getServerContextpathPattern() {
		return (Pattern)ReflexStatic.getStatic(
				STATIC_NAME_SERVICE_SERVERCONTEXTPATH_PATTERN);
	}

	/**
	 * サービス初期設定処理.
	 * リクエスト・メインスレッド開始時に実行する。
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void settingServiceIfAbsent(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		if (isEnableAccessLog()) {
			logger.debug("[settingServiceIfAbsent] 1");
		}
		// リクエスト・メインスレッド初期データ取得
		DatastoreManager datastoreManager = TaggingEnvUtil.getDatastoreManager();
		datastoreManager.initMainThread(serviceName, requestInfo, connectionInfo);

		if (isEnableAccessLog()) {
			logger.debug("[settingServiceIfAbsent] 2");
		}
		List<SettingService> settingServiceList = TaggingEnvUtil.getSettingServiceList();

		boolean isLock = true;
		try {
			isLock = isLock(serviceName);
			if (isEnableAccessLog()) {
				logger.debug("[settingServiceIfAbsent] 3 isLock=" + isLock);
			}
			if (!isLock) {
				// ロックされていないため、サービス情報を設定する。
				SystemContext systemContext = new SystemContext(serviceName, requestInfo,
						connectionInfo);

				if (isEnableAccessLog()) {
					logger.debug("[settingServiceIfAbsent] settingService start. " + datastoreManager.getClass().getSimpleName());
				}

				// 1. データストア設定
				datastoreManager.settingService(serviceName, requestInfo, connectionInfo);

				// 2. 名前空間設定 (テンプレートより前に行う)
				NamespaceManager namespaceManager = TaggingEnvUtil.getNamespaceManager();
				if (isEnableAccessLog()) {
					logger.debug("[settingServiceIfAbsent] settingService start. " + namespaceManager.getClass().getSimpleName());
				}
				namespaceManager.settingService(serviceName, requestInfo, connectionInfo);

				if (isEnableAccessLog()) {
					logger.debug("[settingServiceIfAbsent] settingServiceAfterNamespace start. " + datastoreManager.getClass().getSimpleName());
				}

				// 3. 名前空間設定後のデータストア設定 (サービスごとの設定)
				datastoreManager.settingServiceAfterNamespace(serviceName,
						requestInfo, connectionInfo);

				// 4. テンプレート設定 (後続の処理でテンプレートが必要なので一番先に行う)
				ResourceMapperManager resourceMapperManager =
						TaggingEnvUtil.getResourceMapperManager();

				if (isEnableAccessLog()) {
					logger.debug("[settingServiceIfAbsent] settingService start. " + resourceMapperManager.getClass().getSimpleName());
				}
				boolean isSetting = resourceMapperManager.settingService(systemContext);
				if (isEnableAccessLog()) {
					logger.debug("[settingServiceIfAbsent] settingService end. " + 
							resourceMapperManager.getClass().getSimpleName() + 
							" isSetting=" + isSetting);
				}
				if (isSetting) {
					// readEntryMapのクリア
					datastoreManager.clearCache(serviceName, requestInfo, connectionInfo);
				}

				ReflexAuthentication systemAuth = systemContext.getAuth();
				Map<String, Future<Boolean>> futuresMap = new HashMap<>();

				// 5〜. 各管理クラスのサービス情報設定
				for (SettingService settingService : settingServiceList) {
					// すでに初期設定済みのクラスは除く
					if (settingService instanceof DatastoreManager ||
							settingService instanceof NamespaceManager ||
							settingService instanceof ResourceMapperManager) {
						continue;
					}
					ServiceManagerDefaultSettingServiceCallable settingServiceCallable =
							new ServiceManagerDefaultSettingServiceCallable(
									serviceName, settingService);
					Future<Boolean> future = (Future<Boolean>)TaskQueueUtil.addTask(
							settingServiceCallable, 0, systemAuth, requestInfo, connectionInfo);
					futuresMap.put(settingService.getClass().getSimpleName(), future);
				}

				// 各処理の終了確認
				for (Map.Entry<String, Future<Boolean>> mapEntry : futuresMap.entrySet()) {
					String settingServiceName = mapEntry.getKey();
					Future<Boolean> future = mapEntry.getValue();
					try {
						if (isEnableAccessLog()) {
							logger.debug("[settingServiceIfAbsent] settingService start. (futures.get) " + settingServiceName);
						}
						future.get();

					} catch (ExecutionException e) {
						Throwable cause = e.getCause();
						if (logger.isDebugEnabled()) {
							StringBuilder sb = new StringBuilder();
							sb.append(LogUtil.getRequestInfoStr(requestInfo));
							sb.append("[settingService] ");
							sb.append(settingServiceName);
							sb.append(" ExecutionException: ");
							sb.append(cause.getMessage());
							logger.debug(sb.toString());
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
							StringBuilder sb = new StringBuilder();
							sb.append(LogUtil.getRequestInfoStr(requestInfo));
							sb.append("[settingService] ");
							sb.append(settingServiceName);
							sb.append(" InterruptedException: ");
							sb.append(e.getMessage());
							logger.debug(sb.toString());
						}
						throw new IOException(e);
					}
				}
				if (isEnableAccessLog()) {
					logger.debug("[settingServiceIfAbsent] settingService end. (futures.get) ");
				}

			} else {
				if (isEnableAccessLog()) {
					logger.debug("[settingServiceIfAbsent] waitLock start.");
				}
				
				// 他スレッドでサービス情報を更新中
				boolean wait = waitLock(serviceName);
				
				if (isEnableAccessLog()) {
					logger.debug("[settingServiceIfAbsent] waitLock end. wait=" + wait);
				}

				if (!wait) {
					if (logger.isInfoEnabled()) {
						logger.info(LogUtil.getRequestInfoStr(requestInfo) +
								"[settingService] Setting service is locked. (continue.) service name = " + serviceName);
					}
				}
			}
		} finally {
			if (!isLock) {
				releaseLock(serviceName);
			}
		}
	}

	/**
	 * static領域からlockMapを取得.
	 * @return lockMap
	 */
	private ConcurrentMap<String, Integer> getLockMap() {
		return (ConcurrentMap<String, Integer>)ReflexStatic.getStatic(STATIC_NAME_SERVICE_LOCKMAP);
	}

	/**
	 * 指定されたサービスの設定更新中かどうかチェックする。
	 * @param serviceName サービス名
	 * @return 更新中である場合true
	 */
	public boolean isLock(String serviceName)
	throws IOException {
		ConcurrentMap<String, Integer> lockMap = getLockMap();
		Integer val = lockMap.putIfAbsent(serviceName, LOCK);
		return val != null;
	}

	/**
	 * 指定されたサービスのロックを解除する。
	 * @param serviceName サービス名
	 */
	private void releaseLock(String serviceName)
	throws IOException {
		ConcurrentMap<String, Integer> lockMap = getLockMap();
		lockMap.remove(serviceName);
	}

	/**
	 * 他スレッドによるロック中の場合、ロックの終了を待つ。
	 * @param serviceName サービス名
	 * @return ロック解除状態になった場合true、待てなかった場合false。
	 */
	private boolean waitLock(String serviceName) {
		ConcurrentMap<String, Integer> lockMap = getLockMap();
		int numRetries = getServicesettingsRetryCount();
		int waitMillis = getServicesettingsRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			RetryUtil.sleep(waitMillis);
			if (lockMap.get(serviceName) == null) {
				return true;
			}
		}
		return false;
	}

	/**
	 * サービス設定リトライ総数を取得.
	 * @return サービス設定リトライ総数
	 */
	private int getServicesettingsRetryCount() {
		return TaggingEnvUtil.getSystemPropInt(
				TaggingEnvConst.SERVICESETTINGS_RETRY_COUNT,
				TaggingEnvConst.SERVICESETTINGS_RETRY_COUNT_DEFAULT);
	}

	/**
	 * サービス設定リトライ時のスリープ時間(ミリ秒)を取得.
	 * @return サービス設定リトライ時のスリープ時間(ミリ秒)
	 */
	private int getServicesettingsRetryWaitmillis() {
		return TaggingEnvUtil.getSystemPropInt(
				TaggingEnvConst.SERVICESETTINGS_RETRY_WAITMILLIS,
				TaggingEnvConst.SERVICESETTINGS_RETRY_WAITMILLIS_DEFAULT);
	}

	/**
	 * static領域からapikeyMapを取得.
	 * @return apikeyMap
	 */
	ConcurrentMap<String, String> getAPIKeyMap() {
		return (ConcurrentMap<String, String>)ReflexStatic.getStatic(
				STATIC_NAME_SERVICE_APIKEYMAP);
	}

	/**
	 * static領域からserviceKeyMapを取得.
	 * @return serviceKeyMap
	 */
	ConcurrentMap<String, String> getServiceKeyMap() {
		return (ConcurrentMap<String, String>)ReflexStatic.getStatic(
				STATIC_NAME_SERVICE_SERVICEKEYMAP);
	}

	/**
	 * static領域からapikeyの revision + updated Mapを取得.
	 * @return apikeyの revision + updated Map
	 */
	private ConcurrentMap<String, String> getAPIKeyRevisionAndUpdatedMap() {
		return (ConcurrentMap<String, String>)ReflexStatic.getStatic(
				STATIC_NAME_SERVICE_APIKEYREVISIONMAP);
	}

	/**
	 * APIKey設定確認処理.
	 * リクエスト初期処理でスレッド呼び出しされる。
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	void checkSettingAPIKey(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// APIKey設定
		String currentRevUpd = getAPIKeyRevisionAndUpdatedFromDs(serviceName,
				requestInfo, connectionInfo);
		ConcurrentMap<String, String> apiKeyRevisionAndUpdatedMap =
				getAPIKeyRevisionAndUpdatedMap();
		String settingRevUpd = apiKeyRevisionAndUpdatedMap.get(serviceName);
		if (currentRevUpd == null || !currentRevUpd.equals(settingRevUpd)) {
			// メモリに保持しているAPIKeyと、登録されたAPIKeyのリビジョンが異なるため更新する。
			settingAPIKey(serviceName, requestInfo, connectionInfo);
		}
	}

	/**
	 * APIKeyの設定.
	 * @param serviceName サービス名.
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	void settingAPIKey(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// APIKey、サービスキー設定
		EntryBase apikeyEntry = getAPIKeyEntryFromDs(serviceName,
				requestInfo, connectionInfo);
		String apiKey = getAPIKeyByEntry(apikeyEntry);
		String serviceKey = getServiceKeyByEntry(apikeyEntry);
		ConcurrentMap<String, String> apiKeyMap = getAPIKeyMap();
		ConcurrentMap<String, String> serviceKeyMap = getServiceKeyMap();
		ConcurrentMap<String, String> apiKeyRevisionAndUpdatedMap =
				getAPIKeyRevisionAndUpdatedMap();
		if (apiKey != null) {
			apiKeyMap.put(serviceName, apiKey);
			if (serviceKey != null) {
				serviceKeyMap.put(serviceName, serviceKey);
			} else {
				if (serviceKeyMap.containsKey(serviceName)) {
					serviceKeyMap.remove(serviceName);
				}
			}
			String revUpd = TaggingEntryUtil.getUpdatedAndRevision(apikeyEntry);
			apiKeyRevisionAndUpdatedMap.put(serviceName, revUpd);
		} else {
			if (apiKeyMap.containsKey(serviceName)) {
				apiKeyMap.remove(serviceName);
			}
			if (serviceKeyMap.containsKey(serviceName)) {
				serviceKeyMap.remove(serviceName);
			}
			if (apiKeyRevisionAndUpdatedMap.containsKey(serviceName)) {
				apiKeyRevisionAndUpdatedMap.remove(serviceName);
			}
		}
	}

	/**
	 * データストアからAPIKeyを取得.
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	private EntryBase getAPIKeyEntryFromDs(String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		SystemContext systemContext = new SystemContext(serviceName,
				requestInfo, connectionInfo);
		String uri = getAPIKeyUri();
		try {
			return systemContext.getEntry(uri, true);

		} catch (TaggingException e) {
			logger.warn(LogUtil.getRequestInfoStr(requestInfo) +
					"[getAPIKeyFromDs] " + e.getMessage(), e);
		}
		return null;
	}

	/**
	 * データストアのAPIKey Entryのリビジョン+updatedを取得.
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return updated + revision文字列
	 */
	private String getAPIKeyRevisionAndUpdatedFromDs(String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		SystemContext systemContext = new SystemContext(serviceName,
				requestInfo, connectionInfo);
		String uri = getAPIKeyUri();
		try {
			EntryBase entry = systemContext.getEntry(uri, true);
			if (entry != null) {
				return TaggingEntryUtil.getUpdatedAndRevision(entry);
			}

		} catch (TaggingException e) {
			logger.warn(LogUtil.getRequestInfoStr(requestInfo) +
					"[getAPIKeyRevisionAndUpdatedFromDs] " + e.getMessage(), e);
		}

		// 通らない
		logger.warn(LogUtil.getRequestInfoStr(requestInfo) +
				"[getAPIKeyRevisionAndUpdatedFromDs] entry is null.");
		return null;
	}

	/**
	 * APIKeyを保持するEntryのURIを取得.
	 *  / (ルートエントリー) を返却します。
	 * @return APIKeyを保持するEntryのURI
	 */
	private String getAPIKeyUri() {
		return ServiceManagerDefault.getAPIKeyUri();
	}

	/**
	 * EntryからAPIKeyを取得.
	 * APIKeyは、エントリーのconributorのuriの以下の値。
	 * <p> urn:vte.cx:apikey:{APIKey} </p>
	 * @param entry サービスエントリー
	 * @return APIKey
	 */
	private String getAPIKeyByEntry(EntryBase entry) {
		if (entry != null && entry.contributor != null) {
			for (Contributor contributor : entry.contributor) {
				if (contributor.uri != null &&
						contributor.uri.startsWith(Constants.URN_PREFIX_APIKEY)) {
					return contributor.uri.substring(URN_PREFIX_APIKEY_LEN);
				}
			}
		}
		return null;
	}

	/**
	 * Entryからサービスキーを取得.
	 * サービスキーは、エントリーのconributorのuriの以下の値。
	 * <p> urn:vte.cx:servicekey:{サービスキー} </p>
	 * @param entry サービスエントリー
	 * @return サービスキー
	 */
	private String getServiceKeyByEntry(EntryBase entry) {
		if (entry != null && entry.contributor != null) {
			for (Contributor contributor : entry.contributor) {
				if (contributor.uri != null &&
						contributor.uri.startsWith(Constants.URN_PREFIX_SERVICEKEY)) {
					return contributor.uri.substring(URN_PREFIX_SERVICEKEY_LEN);
				}
			}
		}
		return null;
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
		// テンプレート設定のクローズ
		ResourceMapperManager resourceMapperManager =
				TaggingEnvUtil.getResourceMapperManager();
		resourceMapperManager.closeService(serviceName, requestInfo, connectionInfo);
		List<SettingService> settingServiceList = TaggingEnvUtil.getSettingServiceList();
		for (SettingService settingService : settingServiceList) {
			// すでに初期設定済みのクラスは除く
			if (settingService instanceof ServiceManager) {
				continue;
			}
			settingService.closeService(serviceName,
					requestInfo, connectionInfo);
		}
	}

	/**
	 * サービス設定のアクセスログ（処理経過ログ）を出力するかどうか.
	 * テスト用
	 * @return サービス設定のアクセスログ（処理経過ログ）を出力する場合true
	 */
	private boolean isEnableAccessLog() {
		return TaggingEnvUtil.getSystemPropBoolean(
				ServiceManagerDefaultConst.SERVICESETTING_ENABLE_ACCESSLOG, false);
	}

}
