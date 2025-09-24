package jp.reflexworks.taggingservice.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.api.Condition;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.entry.Link;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexStatic;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.bdbclient.BDBClientConst;
import jp.reflexworks.taggingservice.bdbclient.BDBClientManager;
import jp.reflexworks.taggingservice.bdbclient.BDBClientUtil;
import jp.reflexworks.taggingservice.conn.ReflexConnectionUtil;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.StaticDuplicatedException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.RequestParamInfo;
import jp.reflexworks.taggingservice.model.UpdatedInfo;
import jp.reflexworks.taggingservice.plugin.CacheManager;
import jp.reflexworks.taggingservice.plugin.DatastoreManager;
import jp.reflexworks.taggingservice.plugin.ServiceManager;
import jp.reflexworks.taggingservice.plugin.SettingService;
import jp.reflexworks.taggingservice.plugin.UserManager;
import jp.reflexworks.taggingservice.service.BDBClientInitMainThreadConst.SettingDataType;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.taskqueue.TaskQueueUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.Constants.OperationType;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * リクエスト・メインスレッド初期処理.
 */
public class BDBClientInitMainThreadManager {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * リクエスト・メインスレッド初期処理
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void initMainThread(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// 処理に必要なEntryを読んでおく
		String systemService = TaggingEnvUtil.getSystemService();
		SystemContext systemContextSystem = new SystemContext(systemService,
				requestInfo, connectionInfo);
		// ReadEntryMapの Share Mapを作成しておく。
		BDBClientUtil.getEntryMap(systemService, connectionInfo);
		BDBClientUtil.getFeedMap(systemService, connectionInfo);
		BDBClientUtil.getEntryMap(serviceName, connectionInfo);
		BDBClientUtil.getFeedMap(serviceName, connectionInfo);
		// システム管理サービス
		initMainThreadBySystem(systemContextSystem);
		// システム管理サービスの自サービスEntry
		initMainThreadBySystem(serviceName, systemContextSystem);
	}

	/**
	 * リクエスト・メインスレッド初期処理のうち、キャッシュからEntryリストを取得.
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void initMainThreadPreparation(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// 処理に必要なEntryを読んでおく
		String systemService = TaggingEnvUtil.getSystemService();
		SystemContext systemContext = new SystemContext(systemService, requestInfo, connectionInfo);
		// システム管理サービス
		initMainThreadBySystemPreparation(systemContext);
		// システム管理サービスの自サービスEntry
		initMainThreadBySystemPreparation(serviceName, systemContext);
	}

	/**
	 * リクエスト・メインスレッド初期処理
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void initMainThreadByService(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// 自サービス
		SystemContext systemContextMyService = new SystemContext(serviceName,
				requestInfo, connectionInfo);
		initMainThreadByService(systemContextMyService);
	}

	/**
	 * リクエスト・メインスレッド初期処理のシステム管理サービス初期処理.
	 * 検索処理を並列で行い、ReadEntryMapに格納する。
	 * @param reflexContext システム管理サービスのReflexContext
	 */
	private void initMainThreadBySystem(ReflexContext reflexContext)
	throws IOException, TaggingException {
		String systemService = reflexContext.getServiceName();
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		List<SettingService> settingServiceList = TaggingEnvUtil.getSettingServiceList();
		SettingDataType settingDataType = SettingDataType.SYSTEM;
		int expireSec = getInitMainthreadCacheExpireSec();

		if (isInitMainthreadEnableAccessLog()) {
			logger.debug("[initMainThreadBySystem(system)] start.");
		}
		// Entry検索
		// すでにキャッシュを読み込んでいる場合は処理を飛ばす。
		if (!isRetrievedCacheEntriesBySystem(systemService, connectionInfo)) {
			if (isInitMainthreadEnableAccessLog()) {
				logger.debug("[initMainThreadBySystem(system)] isRetrievedCacheEntriesBySystem=false");
			}
			Set<String> getEntryUris = getEntryUris(settingDataType, systemService);
			if (isInitMainthreadEnableAccessLog()) {
				logger.debug("[initMainThreadBySystem(system)] getEntryUris.isEmpty=" + getEntryUris.isEmpty());
			}
			// Entry検索。ReadEntryMapに格納。
			if (!getEntryUris.isEmpty()) {
				// Entryキャッシュ検索。
				Set<String> tmpGetEntryUris = getCacheEntriesAndSetReadEntryMap(settingDataType,
						systemService, getEntryUris, reflexContext);
				if (isInitMainthreadEnableAccessLog()) {
					logger.debug("[initMainThreadBySystem(system)] getCacheEntriesAndSetReadEntryMap end. tmpGetEntryUris.isEmpty=" + (tmpGetEntryUris != null && !tmpGetEntryUris.isEmpty()));
				}
				if (tmpGetEntryUris != null && !tmpGetEntryUris.isEmpty()) {
					// データストアへEntry検索。ReadEntryMapとキャッシュに格納。
					getEntriesAndSetCacheFeed(settingDataType, systemService, getEntryUris,
							expireSec, reflexContext);
				}
				if (isInitMainthreadEnableAccessLog()) {
					logger.debug("[initMainThreadBySystem(system)] getEntriesAndSetCacheFeed end.");
				}
			}
		}

		// Feed検索
		// すでにキャッシュを読み込んでいる場合は処理を飛ばす。
		if (!isRetrievedCacheFeedBySystem(systemService, connectionInfo)) {
			if (isInitMainthreadEnableAccessLog()) {
				logger.debug("[initMainThreadBySystem(system)] isRetrievedCacheFeedBySystem=false");
			}
			// キー:SettingServiceのクラス名、値:(キー:URI、値:Future)
			Map<String, Map<String, Future<FeedBase>>> futuresMap = new HashMap<>();
			for (SettingService settingService : settingServiceList) {
				futuresMap.put(settingService.getClass().getSimpleName(), 
						initMainThreadFeed(settingDataType,
							settingService.getSettingFeedUrisBySystem(),
							systemService, expireSec, auth, requestInfo, connectionInfo));
			}
			// 結果確認
			if (!futuresMap.isEmpty()) {
				for (Map.Entry<String, Map<String, Future<FeedBase>>> allMapEntry : futuresMap.entrySet()) {
					String settingServiceName = allMapEntry.getKey();
					Map<String, Future<FeedBase>> futures = allMapEntry.getValue();
					for (Map.Entry<String, Future<FeedBase>> mapEntry : futures.entrySet()) {
						String uri = mapEntry.getKey();
						Future<FeedBase> future = mapEntry.getValue();
						try {
							if (isInitMainthreadEnableAccessLog()) {
								StringBuilder sb = new StringBuilder();
								sb.append("[initMainThreadBySystem(system)] getFeed(future.get) start.");
								sb.append(" settingServiceName=");
								sb.append(settingServiceName);
								sb.append(" uri=");
								sb.append(uri);
								logger.debug(sb.toString());
							}
							future.get();
	
						} catch (ExecutionException e) {
							Throwable cause = e.getCause();
							if (logger.isDebugEnabled()) {
								StringBuilder sb = new StringBuilder();
								sb.append(LogUtil.getRequestInfoStr(requestInfo));
								sb.append("[initMainThreadBySystem] ExecutionException: ");
								sb.append(cause.getMessage());
								sb.append(" settingServiceName=");
								sb.append(settingServiceName);
								sb.append(" uri=");
								sb.append(uri);
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
								sb.append("[initMainThreadBySystem] InterruptedException: ");
								sb.append(e.getMessage());
								sb.append(" settingServiceName=");
								sb.append(settingServiceName);
								sb.append(" uri=");
								sb.append(uri);
								logger.debug(sb.toString());
							}
							throw new IOException(e);
						}
					}
				}
				if (isInitMainthreadEnableAccessLog()) {
					logger.debug("[initMainThreadBySystem(system)] getFeed(future.get) end.");
				}

				// キャッシュ読み込みステータスを設定
				setRetrievedCacheFeedBySystem(systemService, connectionInfo);
			}
		}
		if (isInitMainthreadEnableAccessLog()) {
			logger.debug("[initMainThreadBySystem(system)] end.");
		}
	}

	/**
	 * リクエスト・メインスレッド初期処理のシステム管理サービス初期処理のうち、キャッシュからEntryリスト取得.
	 * キャッシュにEntryリストがある場合、ReadEntryMapに格納する。
	 * @param reflexContext システム管理サービスのReflexContext
	 */
	private void initMainThreadBySystemPreparation(ReflexContext reflexContext)
	throws IOException, TaggingException {
		String systemService = reflexContext.getServiceName();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		if (isInitMainthreadEnableAccessLog()) {
			logger.debug("[initMainThreadBySystemPreparation(system)] start.");
		}
		// Entry検索
		// すでにキャッシュを読み込んでいる場合は処理を飛ばす。
		if (!isRetrievedCacheEntriesBySystem(systemService, connectionInfo)) {
			if (isInitMainthreadEnableAccessLog()) {
				logger.debug("[initMainThreadBySystemPreparation(system)] isRetrievedCacheEntriesBySystem = false");
			}
			SettingDataType settingDataType = SettingDataType.SYSTEM;
			Set<String> getEntryUris = getEntryUris(settingDataType, systemService);
			if (isInitMainthreadEnableAccessLog()) {
				logger.debug("[initMainThreadBySystemPreparation(system)] getEntryUris.isEmpty = " + getEntryUris.isEmpty());
			}
			// Entry検索。ReadEntryMapに格納。
			if (!getEntryUris.isEmpty()) {
				// Entryキャッシュ検索のみ。
				getCacheEntriesAndSetReadEntryMap(settingDataType, systemService,
						getEntryUris, reflexContext);
				if (isInitMainthreadEnableAccessLog()) {
					logger.debug("[initMainThreadBySystemPreparation(system)] getCacheEntriesAndSetReadEntryMap end.");
				}
			}
		}
		if (isInitMainthreadEnableAccessLog()) {
			logger.debug("[initMainThreadBySystemPreparation(system)] end.");
		}
	}

	/**
	 * リクエスト・メインスレッド初期処理の自サービス初期処理.
	 * 検索処理を並列で行い、ReadEntryMapに格納する。
	 * @param serviceName 対象サービス名
	 * @param reflexContext システム管理サービスのReflexContext
	 */
	private void initMainThreadBySystem(String serviceName, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String systemService = reflexContext.getServiceName();
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		List<SettingService> settingServiceList = TaggingEnvUtil.getSettingServiceList();
		SettingDataType settingDataType = SettingDataType.SYSTEM_SERVICE;	// サービスごとのEntry検索（検索先はシステム管理サービス）
		int expireSec = getInitMainthreadCacheExpireSec();

		if (isInitMainthreadEnableAccessLog()) {
			logger.debug("[initMainThreadBySystem(" + serviceName + ")] start.");
		}

		// Entry検索
		// すでにキャッシュを読み込んでいる場合は処理を飛ばす。
		if (!isRetrievedCacheEntriesBySystem(systemService, serviceName, connectionInfo)) {
			if (isInitMainthreadEnableAccessLog()) {
				logger.debug("[initMainThreadBySystem(" + serviceName + ")] isRetrievedCacheEntriesBySystem=false");
			}
			Set<String> getEntryUris = getEntryUris(settingDataType, serviceName);
			if (isInitMainthreadEnableAccessLog()) {
				logger.debug("[initMainThreadBySystem(" + serviceName + ")] getEntryUris.isEmpty=" + getEntryUris.isEmpty());
			}
			// Entry検索。ReadEntryMapに格納。
			if (!getEntryUris.isEmpty()) {
				// Entryキャッシュ検索。
				Set<String> tmpGetEntryUris = getCacheEntriesAndSetReadEntryMap(settingDataType,
						serviceName, getEntryUris, reflexContext);
				if (tmpGetEntryUris != null && !tmpGetEntryUris.isEmpty()) {
					// データストアへEntry検索。ReadEntryMapとキャッシュに格納。
					getEntriesAndSetCacheFeed(settingDataType, serviceName, getEntryUris,
							expireSec, reflexContext);
				}
			}
		}

		// Feed検索
		// すでにキャッシュを読み込んでいる場合は処理を飛ばす。
		if (!isRetrievedCacheFeedBySystem(systemService, serviceName, connectionInfo)) {
			if (isInitMainthreadEnableAccessLog()) {
				logger.debug("[initMainThreadBySystem(" + serviceName + ")] isRetrievedCacheFeedBySystem=false");
			}
			// キー:SettingServiceのクラス名、値:(キー:URI、値:Future)
			Map<String, Map<String, Future<FeedBase>>> futuresMap = new HashMap<>();
			// SettingService
			for (SettingService settingService : settingServiceList) {
				futuresMap.put(settingService.getClass().getSimpleName(), 
					initMainThreadFeed(settingDataType,
						settingService.getSettingFeedUrisBySystem(serviceName),
						serviceName, expireSec, auth, requestInfo, connectionInfo));
			}
			// 結果確認
			if (!futuresMap.isEmpty()) {
				for (Map.Entry<String, Map<String, Future<FeedBase>>> allMapEntry : futuresMap.entrySet()) {
					String settingServiceName = allMapEntry.getKey();
					Map<String, Future<FeedBase>> futures = allMapEntry.getValue();
					for (Map.Entry<String, Future<FeedBase>> mapEntry : futures.entrySet()) {
						String uri = mapEntry.getKey();
						Future<FeedBase> future = mapEntry.getValue();
						try {
							if (isInitMainthreadEnableAccessLog()) {
								StringBuilder sb = new StringBuilder();
								sb.append("[initMainThreadBySystem(" + serviceName + ")] getFeed(future.get) start.");
								sb.append(" settingServiceName=");
								sb.append(settingServiceName);
								sb.append(" uri=");
								sb.append(uri);
								logger.debug(sb.toString());
							}
							future.get();
	
						} catch (ExecutionException e) {
							Throwable cause = e.getCause();
							if (logger.isDebugEnabled()) {
								StringBuilder sb = new StringBuilder();
								sb.append(LogUtil.getRequestInfoStr(requestInfo));
								sb.append("[initMainThreadBySystem(");
								sb.append(serviceName);
								sb.append(")] ExecutionException: ");
								sb.append(cause.getMessage());
								sb.append(" settingServiceName=");
								sb.append(settingServiceName);
								sb.append(" uri=");
								sb.append(uri);
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
								sb.append("[initMainThreadBySystem(");
								sb.append(serviceName);
								sb.append(")] InterruptedException: ");
								sb.append(e.getMessage());
								sb.append(" settingServiceName=");
								sb.append(settingServiceName);
								sb.append(" uri=");
								sb.append(uri);
								logger.debug(sb.toString());
							}
							throw new IOException(e);
						}
					}
				}
				if (isInitMainthreadEnableAccessLog()) {
					logger.debug("[initMainThreadBySystem(" + serviceName + ")] getFeed(future.get) end.");
				}

				// キャッシュ読み込みステータスを設定
				setRetrievedCacheFeedBySystem(systemService, serviceName, connectionInfo);
			}
		}
		if (isInitMainthreadEnableAccessLog()) {
			logger.debug("[initMainThreadBySystem(" + serviceName + ")] end.");
		}
	}

	/**
	 * リクエスト・メインスレッド初期処理の自サービス初期処理.
	 * 検索処理を並列で行い、ReadEntryMapに格納する。
	 * @param serviceName 対象サービス名
	 * @param reflexContext システム管理サービスのReflexContext
	 */
	private void initMainThreadBySystemPreparation(String serviceName, ReflexContext reflexContext)
	throws IOException, TaggingException {
		if (isInitMainthreadEnableAccessLog()) {
			logger.debug("[initMainThreadBySystemPreparation(" + serviceName + ")] start.");
		}
		String systemService = reflexContext.getServiceName();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		SettingDataType settingDataType = SettingDataType.SYSTEM_SERVICE;	// サービスごとのEntry検索（検索先はシステム管理サービス）

		// すでにキャッシュを読み込んでいる場合は処理を飛ばす。
		if (!isRetrievedCacheEntriesBySystem(systemService, serviceName, connectionInfo)) {
			if (isInitMainthreadEnableAccessLog()) {
				logger.debug("[initMainThreadBySystemPreparation(" + serviceName + ")] isRetrievedCacheEntriesBySystem = false");
			}
			Set<String> getEntryUris = getEntryUris(settingDataType, serviceName);
			if (isInitMainthreadEnableAccessLog()) {
				logger.debug("[initMainThreadBySystemPreparation(" + serviceName + ")] getEntryUris.isEmpty = " + getEntryUris.isEmpty());
			}
			// Entry検索。ReadEntryMapに格納。
			if (!getEntryUris.isEmpty()) {
				// Entryキャッシュ検索。
				getCacheEntriesAndSetReadEntryMap(settingDataType, serviceName, getEntryUris,
						reflexContext);
				if (isInitMainthreadEnableAccessLog()) {
					logger.debug("[initMainThreadBySystemPreparation(" + serviceName + ")] getCacheEntriesAndSetReadEntryMap end.");
				}
			}
		}
		if (isInitMainthreadEnableAccessLog()) {
			logger.debug("[initMainThreadBySystemPreparation(" + serviceName + ")] end.");
		}
	}

	/**
	 * リクエスト・メインスレッド初期処理の自サービス初期処理.
	 * 検索処理を並列で行い、ReadEntryMapに格納する。
	 * @param reflexContext 自サービスのReflexContext
	 */
	private void initMainThreadByService(ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		List<SettingService> settingServiceList = TaggingEnvUtil.getSettingServiceList();
		SettingDataType settingDataType = SettingDataType.SERVICE;
		int expireSec = getInitMainthreadCacheExpireSec();

		if (isInitMainthreadEnableAccessLog()) {
			logger.debug("[initMainThreadByService(" + serviceName + ")] start.");
		}

		// Entry検索
		// すでにキャッシュを読み込んでいる場合は処理を飛ばす。
		if (!isRetrievedCacheEntriesByService(serviceName, connectionInfo)) {
			if (isInitMainthreadEnableAccessLog()) {
				logger.debug("[initMainThreadByService(" + serviceName + ")] isRetrievedCacheEntriesByService=false");
			}
			Set<String> getEntryUris = getEntryUris(settingDataType, serviceName);
			if (isInitMainthreadEnableAccessLog()) {
				logger.debug("[initMainThreadByService(" + serviceName + ")] getEntryUris.isEmpty=" + getEntryUris.isEmpty());
			}
			// Entry検索。ReadEntryMapに格納。
			if (!getEntryUris.isEmpty()) {
				// Entryキャッシュ検索。
				Set<String> tmpGetEntryUris = getCacheEntriesAndSetReadEntryMap(settingDataType,
						serviceName, getEntryUris, reflexContext);
				if (tmpGetEntryUris != null && !tmpGetEntryUris.isEmpty()) {
					// データストアへEntry検索。ReadEntryMapとキャッシュに格納。
					getEntriesAndSetCacheFeed(settingDataType, serviceName, getEntryUris,
							expireSec, reflexContext);
				}
			}
		}

		// Feed検索
		// すでにキャッシュを読み込んでいる場合は処理を飛ばす。
		if (!isRetrievedCacheFeedByService(serviceName, connectionInfo)) {
			if (isInitMainthreadEnableAccessLog()) {
				logger.debug("[initMainThreadByService(" + serviceName + ")] isRetrievedCacheFeedByService=false");
			}
			// キー:SettingServiceのクラス名、値:(キー:URI、値:Future)
			Map<String, Map<String, Future<FeedBase>>> futuresMap = new HashMap<>();
			// SettingService
			for (SettingService settingService : settingServiceList) {
				futuresMap.put(settingService.getClass().getSimpleName(), 
					initMainThreadFeed(settingDataType,
						settingService.getSettingFeedUris(),
						serviceName, expireSec, auth, requestInfo, connectionInfo));
			}
			// 結果確認
			if (!futuresMap.isEmpty()) {
				for (Map.Entry<String, Map<String, Future<FeedBase>>> allMapEntry : futuresMap.entrySet()) {
					String settingServiceName = allMapEntry.getKey();
					Map<String, Future<FeedBase>> futures = allMapEntry.getValue();
					for (Map.Entry<String, Future<FeedBase>> mapEntry : futures.entrySet()) {
						String uri = mapEntry.getKey();
						Future<FeedBase> future = mapEntry.getValue();
						if (isInitMainthreadEnableAccessLog()) {
							StringBuilder sb = new StringBuilder();
							sb.append("[initMainThreadByService(" + serviceName + ")] getFeed(future.get) start.");
							sb.append(" settingServiceName=");
							sb.append(settingServiceName);
							sb.append(" uri=");
							sb.append(uri);
							logger.debug(sb.toString());
						}
						try {
							future.get();
	
						} catch (ExecutionException e) {
							Throwable cause = e.getCause();
							if (logger.isDebugEnabled()) {
								StringBuilder sb = new StringBuilder();
								sb.append(LogUtil.getRequestInfoStr(requestInfo));
								sb.append("[initMainThreadByService] ExecutionException: ");
								sb.append(cause.getMessage());
								sb.append(" settingServiceName=");
								sb.append(settingServiceName);
								sb.append(" uri=");
								sb.append(uri);
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
								sb.append("[initMainThreadByService] InterruptedException: ");
								sb.append(e.getMessage());
								sb.append(" settingServiceName=");
								sb.append(settingServiceName);
								sb.append(" uri=");
								sb.append(uri);
								logger.debug(sb.toString());
							}
							throw new IOException(e);
						}
					}
				}
				if (isInitMainthreadEnableAccessLog()) {
					logger.debug("[initMainThreadByService(" + serviceName + ")] getFeed(future.get) end.");
				}

				// キャッシュ読み込みステータスを設定
				setRetrievedCacheFeedByService(serviceName, connectionInfo);
			}
		}

		if (isInitMainthreadEnableAccessLog()) {
			logger.debug("[initMainThreadByService(" + serviceName + ")] end.");
		}
	}

	/**
	 * リクエスト初期処理に必要なFeed検索を並列で実行
	 * @param uris URIリスト
	 * @param originalServiceName 実行元サービス名
	 * @param expireSec 有効期限(秒)
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return キー:URI、値:FutureのMap
	 */
	private Map<String, Future<FeedBase>> initMainThreadFeed(SettingDataType settingDataType,
			List<String> uris, String originalServiceName, int expireSec, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		Map<String, Future<FeedBase>> futures = new LinkedHashMap<>();
		if (uris != null && !uris.isEmpty()) {
			for (String uri : uris) {
				BDBClientInitMainThreadFeedCallable callable =
						new BDBClientInitMainThreadFeedCallable(settingDataType,
								uri, originalServiceName, expireSec);
				Future<FeedBase> future = (Future<FeedBase>)TaskQueueUtil.addTask(
						callable, 0, auth, requestInfo, connectionInfo);
				futures.put(uri, future);
			}
		}
		return futures;
	}

	/**
	 * リクエスト初期処理に必要なFeed検索 URIごとの処理.
	 * @param settingDataType 設定データタイプ
	 * @param uri URI
	 * @param serviceName 元のサービス名
	 * @param expireSec 有効期限(秒)
	 * @param reflexContext システム管理サービスのReflexContext
	 * @return 検索結果
	 */
	FeedBase initMainThreadEachFeed(SettingDataType settingDataType,
			String uri, String serviceName, int expireSec, ReflexContext reflexContext)
	throws IOException, TaggingException {
		FeedBase feed = getCacheFeedAndSetReadFeedMap(uri, reflexContext);
		// キャッシュに存在しない場合はnull、キャッシュにnullが存在する場合は空のFeed
		if (feed == null) {
			// キャッシュに存在しない場合、データストアから取得
			feed = getFeedAndSetCacheFeed(settingDataType, uri, serviceName, expireSec,
					reflexContext);
		}
		return feed;
	}

	/**
	 * データストアからFeed検索し、キャッシュに登録する.
	 * @param settingDataType 設定データタイプ
	 * @param uri URI
	 * @param serviceName 自サービス名
	 * @param expireSec 有効期限(秒)
	 * @param reflexContext 検索対象サービスのReflexContext (システム管理サービスまたは自サービス)
	 * @return Feed
	 */
	private FeedBase getFeedAndSetCacheFeed(SettingDataType settingDataType,
			String uri, String serviceName, int expireSec, ReflexContext reflexContext)
	throws IOException, TaggingException {
		ReflexAuthentication auth = reflexContext.getAuth();	// システム管理サービスの認証情報
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();

		// データストアから取得
		// 全件取得にする。
		List<EntryBase> entries = new ArrayList<>();
		List<Link> links = new ArrayList<>();
		int limit = TaggingEnvUtil.getEntryNumberDefault(serviceName);
		RequestParam param = new RequestParamInfo(uri, serviceName);
		String paramUri = param.getUri();
		List<List<Condition>> conditionList = param.getConditionsList();
		boolean isUrlForwardMatch = param.isUrlForwardMatch();
		String cursorStr = null;
		do {
			BDBClientManager bdbclientManager = new BDBClientManager();
			// Feedが検索できれば、getFeedメソッド内でReadFeedMapに登録される。
			// Entry更新時にこのメソッドを読みキャッシュを更新するため、Feed検索にキャッシュの更新は不可。
			FeedBase feed = bdbclientManager.getFeed(paramUri, conditionList,
					isUrlForwardMatch, limit, cursorStr, false, serviceName,
					auth, requestInfo, connectionInfo);
			cursorStr = TaggingEntryUtil.getCursorFromFeed(feed);
			// ReadFeedMapに登録
			String cacheUri = BDBClientUtil.getFeedUriForCache(paramUri,
					isUrlForwardMatch, conditionList, false);
			String cacheUriWithLimit = BDBClientUtil.addLimitToFeedUriForCache(
					cacheUri, limit, cursorStr);
			String targetServiceName = reflexContext.getServiceName();
			BDBClientUtil.setReadFeedMap(cacheUriWithLimit, feed, targetServiceName,
					requestInfo, connectionInfo);
			if (TaggingEntryUtil.isExistData(feed)) {
				// ReadEntryMapに登録する
				for (EntryBase entry : feed.entry) {
					BDBClientUtil.setReadEntryMap(entry, entry.getMyUri(), targetServiceName,
							requestInfo, connectionInfo);
				}

				// 戻り値
				entries.addAll(feed.entry);
				if (feed.link != null) {
					for (Link link : feed.link) {
						// カーソル以外を返却
						if (!Link.REL_NEXT.equals(link._$rel)) {
							links.add(link);
						}
					}
				}
			}

			// キャッシュに登録する
			FeedBase cacheFeed = null;
			if (feed != null) {
				cacheFeed = feed;
			} else {
				cacheFeed = TaggingEntryUtil.createFeed(targetServiceName);
			}
			setInitMainThreadCacheFeed(cacheUriWithLimit, cacheFeed, expireSec, reflexContext);

		} while (!StringUtils.isBlank(cursorStr));

		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[getFeedAndSetCacheFeed] end. settingDataType=");
			sb.append(settingDataType.name());
			sb.append(" serviceName=");
			sb.append(serviceName);
			sb.append(" uri=");
			sb.append(uri);
			logger.debug(sb.toString());
		}

		FeedBase retFeed = TaggingEntryUtil.createFeed(serviceName);
		retFeed.entry = entries;
		retFeed.link = links;
		return retFeed;
	}

	/**
	 * メインスレッドキャッシュのEntry URIリストを取得.
	 * @param settingDataType 設定データタイプ
	 * @param serviceName サービス名
	 * @return メインスレッドキャッシュのEntry URIリスト
	 */
	private Set<String> getEntryUris(SettingDataType settingDataType, String serviceName) {
		// 対象URIリストを取得
		List<SettingService> settingServiceList = TaggingEnvUtil.getSettingServiceList();
		Set<String> getEntryUris = new HashSet<>();
		if (SettingDataType.SYSTEM.equals(settingDataType)) {
			Set<String> getEntryUrisSystemService = new HashSet<>();
			for (SettingService settingService : settingServiceList) {
				getEntryUris.addAll(getParentPathUris(settingService.getSettingEntryUrisBySystem()));
				getEntryUris.addAll(getParentPathUris(settingService.getSettingFeedUrisBySystem()));
				getEntryUrisSystemService.addAll(getParentPathUris(settingService.getSettingEntryUrisBySystem(BDBClientInitMainThreadConst.DUMMY_SERVICENAME)));
				getEntryUrisSystemService.addAll(getParentPathUris(settingService.getSettingFeedUrisBySystem(BDBClientInitMainThreadConst.DUMMY_SERVICENAME)));
			}
			// システム管理サービスのサービスごとのEntryのうち、サービス名が入らない階層はこちら
			for (String tmpUri : getEntryUrisSystemService) {
				if (tmpUri.indexOf(BDBClientInitMainThreadConst.DUMMY_SERVICENAME) == -1) {
					getEntryUris.add(tmpUri);
				}
			}
		} else if (SettingDataType.SYSTEM_SERVICE.equals(settingDataType)) {
			Set<String> getEntryUrisSystemService = new HashSet<>();
			// SettingService
			for (SettingService settingService : settingServiceList) {
				getEntryUrisSystemService.addAll(getParentPathUris(settingService.getSettingEntryUrisBySystem(BDBClientInitMainThreadConst.DUMMY_SERVICENAME)));
				getEntryUrisSystemService.addAll(getParentPathUris(settingService.getSettingFeedUrisBySystem(BDBClientInitMainThreadConst.DUMMY_SERVICENAME)));
			}
			// システム管理サービスのサービスごとのEntryのうち、サービス名が入る階層のみこちら
			for (String tmpUri : getEntryUrisSystemService) {
				if (tmpUri.indexOf(BDBClientInitMainThreadConst.DUMMY_SERVICENAME) > -1) {
					getEntryUris.add(tmpUri.replace(BDBClientInitMainThreadConst.DUMMY_SERVICENAME, serviceName));
				}
			}
		} else if (SettingDataType.SERVICE.equals(settingDataType)) {
			for (SettingService settingService : settingServiceList) {
				getEntryUris.addAll(getParentPathUris(settingService.getSettingEntryUris()));
				getEntryUris.addAll(getParentPathUris(settingService.getSettingFeedUris()));
			}
		} else {
			logger.warn("[getEntryUris] SettingDataType is invalid. " + settingDataType);
		}
		return getEntryUris;
	}

	/**
	 * メインスレッドキャッシュのFeed URIリストを取得.
	 * @param settingDataType 設定データタイプ
	 * @param serviceName サービス名
	 * @return メインスレッドキャッシュのFeed URIリスト
	 */
	private Set<String> getFeedUris(SettingDataType settingDataType, String serviceName) {
		// 対象URIリストを取得
		List<SettingService> settingServiceList = TaggingEnvUtil.getSettingServiceList();
		Set<String> getFeedUris = new HashSet<>();
		if (SettingDataType.SYSTEM.equals(settingDataType)) {
			for (SettingService settingService : settingServiceList) {
				List<String> tmpList = settingService.getSettingFeedUrisBySystem();
				if (tmpList != null) {
					getFeedUris.addAll(tmpList);
				}
			}
		} else if (SettingDataType.SYSTEM_SERVICE.equals(settingDataType)) {
			for (SettingService settingService : settingServiceList) {
				List<String> tmpList = settingService.getSettingFeedUrisBySystem(serviceName);
				if (tmpList != null) {
					getFeedUris.addAll(tmpList);
				}
			}
		} else if (SettingDataType.SERVICE.equals(settingDataType)) {
			for (SettingService settingService : settingServiceList) {
				List<String> tmpList = settingService.getSettingFeedUris();
				if (tmpList != null) {
					getFeedUris.addAll(tmpList);
				}
			}
		} else {
			logger.warn("[getFeedUris] SettingDataType is invalid. " + settingDataType);
		}
		return getFeedUris;
	}

	/**
	 * URIの自階層+親階層をリストにして返却する.
	 * 重複は取り除く。
	 * @param uris URIリスト
	 * @return URIの自階層+親階層リスト
	 */
	private Set<String> getParentPathUris(List<String> uris) {
		Set<String> getEntryUris = new HashSet<>();
		if (uris != null && !uris.isEmpty()) {
			for (String tmpUri : uris) {
				// 自階層+親階層
				List<String> parentPathUris = TaggingEntryUtil.getParentPathUris(tmpUri);
				getEntryUris.addAll(parentPathUris);
			}
		}
		return getEntryUris;
	}

	/**
	 * キャッシュにリクエスト・メインスレッド初期取得Feedを設定
	 * @param uri URI
	 * @param feed Feed
	 * @param expireSec 有効期限(秒)
	 * @param reflexContext SystemContext
	 */
	private void setInitMainThreadCacheFeed(String uri, FeedBase feed, int expireSec, 
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		String name = getKeyInitMainThreadCacheFeed(uri);
		setInitMainThreadCache(name, feed, expireSec, reflexContext);
	}

	/**
	 * キャッシュにリクエスト・メインスレッド初期取得Feedを設定
	 * @param name キー
	 * @param feed Feed
	 * @param expireSec 有効期限(秒)
	 * @param reflexContext SystemContext
	 */
	private void setInitMainThreadCache(String name, FeedBase feed, int expireSec, 
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		if (!isEnabledInitMainThreadCache()) {
			return;
		}
		// ロックを取得.
		CacheManager cacheManager = TaggingEnvUtil.getCacheManager();
		boolean isLock = false;
		try {
			// ロック中の場合は他スレッドがキャッシュにセットするため、何もしないで終了する。
			isLock = cacheManager.setLongIfAbsent(name, BDBClientInitMainThreadConst.LOCK, 
					reflexContext);
			if (isLock) {
				cacheManager.setFeed(name, feed, expireSec, reflexContext);
			}

		} finally {
			if (isLock) {
				isLock = cacheManager.deleteLong(name, reflexContext);
			}
		}
	}

	/**
	 * キャッシュのキー（リクエスト・メインスレッド初期設定取得Entryリスト）を取得.
	 * @param settingDataType 設定データタイプ.
	 * @param uid UID
	 * @param serviceName 対象サービス名。isSystemService=falseの場合に使用する。
	 * @return キャッシュのキー（リクエスト初期設定取得Entryリスト）
	 */
	private String getKeyInitMainThreadCacheEntries(SettingDataType settingDataType,
			String uid, String serviceName) {
		if (SettingDataType.SYSTEM.equals(settingDataType)) {
			return BDBClientInitMainThreadConst.CACHEFEED_KEY_INITMAINTHREAD_ENTRYLIST_SYSTEM;
		} else if (SettingDataType.SYSTEM_SERVICE.equals(settingDataType)) {
			return BDBClientInitMainThreadConst.CACHEFEED_KEY_INITMAINTHREAD_ENTRYLIST_SYSTEM_PREFIX + serviceName;
		} else if (SettingDataType.SERVICE.equals(settingDataType)) {
			return BDBClientInitMainThreadConst.CACHEFEED_KEY_INITMAINTHREAD_ENTRYLIST_SERVICE;
		} else if (SettingDataType.USER.equals(settingDataType)) {
			return BDBClientInitMainThreadConst.CACHEFEED_KEY_INITMAINTHREAD_USERINFO_PREFIX + uid;
		} else {
			logger.warn("[getKeyInitMainThreadCacheEntries] SettingDataType is invalid. " + settingDataType);
			return null;
		}
	}

	/**
	 * キャッシュのキー（リクエスト初期設定取得Feed検索結果）を取得.
	 * @param uri Feed検索URI
	 * @return キャッシュのキー（リクエスト初期設定取得Entryリスト）
	 */
	private String getKeyInitMainThreadCacheFeed(String uri) {
		return BDBClientInitMainThreadConst.CACHEFEED_KEY_INITMAINTHREAD_PREFIX + uri;
	}

	/**
	 * キャッシュにすでに読み込み済みのリクエスト・メインスレッド初期取得Entryが登録されている場合取得し、
	 * ReadEntryMapに加える。
	 * @param settingDataType 設定データタイプ
	 * @param serviceName サービス名
	 * @param getEntryUris リクエスト・メインスレッド初期取得EntryのURI
 	 * @param reflexContext システム管理サービスのReflexContext
	 * @return 後続処理で読み込みが必要なEntryのURI
	 */
	private Set<String> getCacheEntriesAndSetReadEntryMap(SettingDataType settingDataType,
			String serviceName, Set<String> getEntryUris, ReflexContext reflexContext)
	throws IOException, TaggingException {
		return getCacheEntriesAndSetReadEntryMap(settingDataType, null, serviceName,
				getEntryUris, reflexContext);
	}

	/**
	 * キャッシュにすでに読み込み済みのリクエスト・メインスレッド初期取得Entryが登録されている場合取得し、
	 * ReadEntryMapに加える。
	 * @param settingDataType 設定データタイプ
	 * @param uid UID
	 * @param serviceName サービス名
	 * @param getEntryUris リクエスト・メインスレッド初期取得EntryのURI
 	 * @param reflexContext システム管理サービスのReflexContext
	 * @return 後続処理で読み込みが必要なEntryのURI
	 */
	private Set<String> getCacheEntriesAndSetReadEntryMap(SettingDataType settingDataType,
			String uid, String serviceName, Set<String> getEntryUris,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		if (!isEnabledInitMainThreadCache()) {
			return null;
		}
		String sourceService = reflexContext.getServiceName();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		String name = getKeyInitMainThreadCacheEntries(settingDataType, uid, serviceName);

		// 外から検索できないようキーにURI指定不可文字列を使用しているため、ReflexContextのメソッドから実行しない。
		// CacheManagerのメソッドを直接実行する。
		CacheManager cacheManager = TaggingEnvUtil.getCacheManager();
		FeedBase feed = cacheManager.getFeed(name, reflexContext);
		if (!TaggingEntryUtil.isExistData(feed)) {
			return getEntryUris;
		}

		// 存在しないEntryはnull。feed.linkにキーリストあり。
		Set<String> cachedUris = new HashSet<>();
		int i = 0;
		for (EntryBase entry : feed.entry) {
			String uri = feed.link.get(i)._$href;
			cachedUris.add(uri);
			// ReadEntryMapに登録
			BDBClientUtil.setReadEntryMap(entry, uri, sourceService,
					requestInfo, connectionInfo);
			i++;
		}
		// キャッシュ読み込みステータスを設定
		if (SettingDataType.SYSTEM.equals(settingDataType)) {
			setRetrievedCacheEntriesBySystem(sourceService, connectionInfo);
		} else if (SettingDataType.SYSTEM_SERVICE.equals(settingDataType)) {
			setRetrievedCacheEntriesBySystem(sourceService, serviceName, connectionInfo);
		} else if (SettingDataType.SERVICE.equals(settingDataType)) {
			setRetrievedCacheEntriesByService(sourceService, connectionInfo);
		} else if (SettingDataType.USER.equals(settingDataType)) {
			setRetrievedCacheEntriesByUser(uid, sourceService, connectionInfo);
		} else {
			logger.warn(LogUtil.getRequestInfoStr(requestInfo) +
					"[getCacheEntriesAndSetReadEntryMap] SettingDataType is invalid. " + settingDataType);
		}

		Set<String> retUris = new LinkedHashSet<>();
		for (String getEntryUri : getEntryUris) {
			if (!cachedUris.contains(getEntryUri)) {
				retUris.add(getEntryUri);
			}
		}
		if (retUris.isEmpty()) {
			return null;
		}
		return retUris;
	}

	/**
	 * キャッシュにすでに読み込み済みのリクエスト・メインスレッド初期取得Entryが登録されている場合取得し、
	 * ReadEntryMapに加える。
	 * @param settingDataType 設定データタイプ
	 * @param serviceName 呼び出し元サービス名
	 * @param expireSec 有効期限(秒)
 	 * @param reflexContext 実行先サービスのReflexContext
	 * @return 後続処理で読み込みが必要なEntryのURI
	 */
	private void getEntriesAndSetCacheFeed(SettingDataType settingDataType, String serviceName,
			int expireSec, ReflexContext reflexContext)
	throws IOException, TaggingException {
		Set<String> getEntryUris = getEntryUris(settingDataType, serviceName);
		getEntriesAndSetCacheFeed(settingDataType, serviceName, getEntryUris, expireSec,
				reflexContext);
	}

	/**
	 * キャッシュにすでに読み込み済みのリクエスト・メインスレッド初期取得Entryが登録されている場合取得し、
	 * ReadEntryMapに加える。
	 * @param settingDataType 設定データタイプ
	 * @param serviceName 呼び出し元サービス名
	 * @param getEntryUris リクエスト・メインスレッド初期取得EntryのURI
	 * @param expireSec 有効期限(秒)
 	 * @param reflexContext 実行先サービスのReflexContext
	 * @return 後続処理で読み込みが必要なEntryのURI
	 */
	private void getEntriesAndSetCacheFeed(SettingDataType settingDataType, String serviceName,
			Set<String> getEntryUris, int expireSec, ReflexContext reflexContext)
	throws IOException, TaggingException {
		getEntriesAndSetCacheFeed(settingDataType, null, serviceName, getEntryUris, expireSec,
				reflexContext);
	}

	/**
	 * キャッシュにすでに読み込み済みのリクエスト・メインスレッド初期取得Entryが登録されている場合取得し、
	 * ReadEntryMapに加える。
	 * @param settingDataType 設定データタイプ
	 * @param uid UID
	 * @param serviceName 呼び出し元サービス名
	 * @param getEntryUris リクエスト・メインスレッド初期取得EntryのURI
	 * @param expireSec 有効期限(秒)
 	 * @param reflexContext 実行先サービスのReflexContext
	 * @return 後続処理で読み込みが必要なEntryのURI
	 */
	private void getEntriesAndSetCacheFeed(SettingDataType settingDataType, String uid,
			String serviceName, Set<String> getEntryUris, int expireSec, 
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		String sourceServiceName = reflexContext.getServiceName();
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();

		// データストアへEntry検索。ReadEntryMapに格納。
		DatastoreManager datastoreManager = TaggingEnvUtil.getDatastoreManager();
		FeedBase feed = datastoreManager.getEntries(new ArrayList<String>(getEntryUris),
				true, serviceName, auth, requestInfo, connectionInfo);
		if (TaggingEntryUtil.isExistData(feed)) {
			// キャッシュに登録
			String name = getKeyInitMainThreadCacheEntries(settingDataType, uid, serviceName);
			setInitMainThreadCache(name, feed, expireSec, reflexContext);

			// キャッシュ読み込みステータスを設定
			if (SettingDataType.SYSTEM.equals(settingDataType)) {
				setRetrievedCacheEntriesBySystem(sourceServiceName, connectionInfo);
			} else if (SettingDataType.SYSTEM_SERVICE.equals(settingDataType)) {
				setRetrievedCacheEntriesBySystem(sourceServiceName, serviceName, connectionInfo);
			} else if (SettingDataType.SERVICE.equals(settingDataType)) {
				setRetrievedCacheEntriesByService(sourceServiceName, connectionInfo);
			} else if (SettingDataType.USER.equals(settingDataType)) {
				setRetrievedCacheEntriesByUser(uid, sourceServiceName, connectionInfo);
			} else {
				logger.warn(LogUtil.getRequestInfoStr(requestInfo) +
						"[getEntriesAndSetCacheFeed] SettingDataType is invalid. " + settingDataType);
			}
		}
		
		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[getEntriesAndSetCacheFeed] end. settingDataType=");
			sb.append(settingDataType.name());
			sb.append(" serviceName=");
			sb.append(serviceName);
			sb.append(" uid=");
			sb.append(uid);
			sb.append(" uris=");
			boolean isFirst = true;
			Iterator<String> it = getEntryUris.iterator();
			while (it.hasNext()) {
				if (isFirst) {
					isFirst = false;
				} else {
					sb.append(", ");
				}
				sb.append(it.next());
			}
			logger.debug(sb.toString());
		}
	}

	/**
	 * キャッシュにすでに読み込み済みのリクエスト・メインスレッド初期取得Entryが登録されている場合取得し、
	 * ReadEntryMapに加える。
	 * @param getEntryUris リクエスト・メインスレッド初期取得EntryのURI
 	 * @param reflexContext システム管理サービスのReflexContext
	 * @return 後続処理で読み込みが必要なFeedのURI
	 */
	private FeedBase getCacheFeedAndSetReadFeedMap(String uri, ReflexContext reflexContext)
	throws IOException, TaggingException {
		if (!isEnabledInitMainThreadCache()) {
			return null;
		}
		String serviceName = reflexContext.getServiceName();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();

		// limit対応
		List<EntryBase> entries = new ArrayList<>();
		List<Link> links = new ArrayList<>();
		int limit = TaggingEnvUtil.getEntryNumberDefault(serviceName);
		RequestParam param = new RequestParamInfo(uri, serviceName);
		String paramUri = param.getUri();
		List<List<Condition>> conditionList = param.getConditionsList();
		boolean isUrlForwardMatch = param.isUrlForwardMatch();
		String cursorStr = null;
		boolean isExistCache = false;
		do {
			String cacheUri = BDBClientUtil.getFeedUriForCache(paramUri,
					isUrlForwardMatch, conditionList, false);
			String cacheUriWithLimit = BDBClientUtil.addLimitToFeedUriForCache(
					cacheUri, limit, cursorStr);
			String name = getKeyInitMainThreadCacheFeed(cacheUriWithLimit);
			CacheManager cacheManager = TaggingEnvUtil.getCacheManager();
			FeedBase feed = cacheManager.getFeed(name, reflexContext);
			if (!isExistCache && feed != null) {
				isExistCache = true;
			}
			cursorStr = TaggingEntryUtil.getCursorFromFeed(feed);
			// ReadFeedMapに登録
			BDBClientUtil.setReadFeedMap(cacheUriWithLimit, feed, serviceName,
					requestInfo, connectionInfo);
			if (TaggingEntryUtil.isExistData(feed)) {
				// ReadEntryMapに登録
				for (EntryBase entry : feed.entry) {
					BDBClientUtil.setReadEntryMap(entry, entry.getMyUri(), serviceName,
							requestInfo, connectionInfo);
				}
				// 戻り値
				entries.addAll(feed.entry);
				if (feed.link != null) {
					for (Link link : feed.link) {
						// カーソル以外を返却
						if (!Link.REL_NEXT.equals(link._$rel)) {
							links.add(link);
						}
					}
				}
			}
		} while (!StringUtils.isBlank(cursorStr));

		if (!isExistCache) {
			return null;
		}
		FeedBase retFeed = TaggingEntryUtil.createFeed(serviceName);
		retFeed.entry = entries;
		retFeed.link = links;
		return retFeed;
	}

	/**
	 * リクエスト・メインスレッド初期処理状態マップのキー : システム管理サービスのEntryリスト を取得
	 * @return リクエスト・メインスレッド初期処理状態マップのキー : システム管理サービスのEntryリスト
	 */
	private String getInitThreadStatusMapKeyEntriesBySystem() {
		return BDBClientInitMainThreadConst.INITMAINTHREAD_STATUS_ENTRIES_SYSTEM;
	}

	/**
	 * リクエスト・メインスレッド初期処理状態マップのキー : サービスのEntryリスト を取得
	 * @param serviceName サービス名
	 * @return リクエスト・メインスレッド初期処理状態マップのキー : サービスのEntryリスト
	 */
	private String getInitThreadStatusMapKeyEntriesBySystem(String serviceName) {
		return BDBClientInitMainThreadConst.INITMAINTHREAD_STATUS_ENTRIES_SYSTEM_PREFIX + serviceName;
	}

	/**
	 * リクエスト・メインスレッド初期処理状態マップのキー : 自サービスのEntryリスト を取得
	 * @return リクエスト・メインスレッド初期処理状態マップのキー : 自サービスのEntryリスト
	 */
	private String getInitThreadStatusMapKeyEntriesByService() {
		return BDBClientInitMainThreadConst.INITMAINTHREAD_STATUS_ENTRIES_SERVICE;
	}

	/**
	 * ユーザ情報取得状態マップのキー : Entryリスト を取得
	 * @return ユーザ情報取得状態マップのキー : Entryリスト
	 */
	private String getUserInfoCacheStatusMapKeyEntries(String uid) {
		return BDBClientInitMainThreadConst.USERINFOCACHE_STATUS_ENTRIES_KEY_PREFIX + uid;
	}

	/**
	 * リクエスト・メインスレッド初期処理状態マップのキー : システム管理サービスのFeed を取得
	 * @return リクエスト・メインスレッド初期処理状態マップのキー : システム管理サービスのFeed
	 */
	private String getInitThreadStatusMapKeyFeedBySystem() {
		return BDBClientInitMainThreadConst.INITMAINTHREAD_STATUS_FEED_SYSTEM;
	}

	/**
	 * リクエスト・メインスレッド初期処理状態マップのキー : サービスのFeed を取得
	 * @param serviceName サービス名
	 * @return リクエスト・メインスレッド初期処理状態マップのキー : サービスのFeed
	 */
	private String getInitThreadStatusMapKeyFeedBySystem(String serviceName) {
		return BDBClientInitMainThreadConst.INITMAINTHREAD_STATUS_FEED_SYSTEM_PREFIX + serviceName;
	}

	/**
	 * リクエスト・メインスレッド初期処理状態マップのキー : 自サービスのFeed を取得
	 * @return リクエスト・メインスレッド初期処理状態マップのキー : 自サービスのFeed
	 */
	private String getInitThreadStatusMapKeyFeedByService() {
		return BDBClientInitMainThreadConst.INITMAINTHREAD_STATUS_FEED_SERVICE;
	}

	/**
	 * ユーザ情報取得状態マップのキー : Feed を取得
	 * @return ユーザ情報取得状態マップのキー : Feed
	 */
	private String getUserInfoCacheStatusMapKeyFeed(String uid) {
		return BDBClientInitMainThreadConst.USERINFOCACHE_STATUS_FEED_KEY_PREFIX + uid;
	}

	/**
	 * リクエスト・メインスレッド初期処理状態の値 : 検索済み を取得
	 * @return 検索済みステータス値
	 */
	private String getInitThreadStatusRetrieved() {
		return BDBClientInitMainThreadConst.INITMAINTHREAD_STATUS_VALUE;
	}

	/**
	 * リクエスト・メインスレッド初期処理において、システム管理サービスのEntryリストをキャッシュから取得済みかどうか判定.
	 * @param systemService システム管理サービス
	 * @param connectionInfo コネクション情報
	 * @return システム管理サービスのEntryリストをキャッシュから取得済みの場合true
	 */
	private boolean isRetrievedCacheEntriesBySystem(String systemService,
			ConnectionInfo connectionInfo) {
		String key = getInitThreadStatusMapKeyEntriesBySystem();
		return isRetrievedCache(key, systemService, connectionInfo);
	}

	/**
	 * リクエスト・メインスレッド初期処理において、システム管理サービスのサービス固有Entryリストをキャッシュから取得済みかどうか判定.
	 * @param systemService システム管理サービス
	 * @param serviceName サービス名
	 * @param connectionInfo コネクション情報
	 * @return システム管理サービスのサービス固有Entryリストをキャッシュから取得済みの場合true
	 */
	private boolean isRetrievedCacheEntriesBySystem(String systemService, String serviceName,
			ConnectionInfo connectionInfo) {
		String key = getInitThreadStatusMapKeyEntriesBySystem(serviceName);
		return isRetrievedCache(key, systemService, connectionInfo);
	}

	/**
	 * リクエスト・メインスレッド初期処理において、自サービスのEntryリストをキャッシュから取得済みかどうか判定.
	 * @param serviceName サービス名
	 * @param connectionInfo コネクション情報
	 * @return 自サービスのEntryリストをキャッシュから取得済みの場合true
	 */
	private boolean isRetrievedCacheEntriesByService(String serviceName,
			ConnectionInfo connectionInfo) {
		String key = getInitThreadStatusMapKeyEntriesByService();
		return isRetrievedCache(key, serviceName, connectionInfo);
	}

	/**
	 * ユーザ情報のEntryリストをキャッシュから取得済みかどうか判定.
	 * @param serviceName サービス名
	 * @param connectionInfo コネクション情報
	 * @return ユーザ情報のEntryリストをキャッシュから取得済みの場合true
	 */
	private boolean isRetrievedCacheEntriesByUser(String uid, String serviceName,
			ConnectionInfo connectionInfo) {
		String key = getUserInfoCacheStatusMapKeyEntries(uid);
		return isRetrievedCache(key, serviceName, connectionInfo);
	}

	/**
	 * リクエスト・メインスレッド初期処理において、システム管理サービスのFeedをキャッシュから取得済みかどうか判定.
	 * @param systemService システム管理サービス
	 * @param connectionInfo コネクション情報
	 * @return システム管理サービスのFeedをキャッシュから取得済みの場合true
	 */
	private boolean isRetrievedCacheFeedBySystem(String systemService,
			ConnectionInfo connectionInfo) {
		String key = getInitThreadStatusMapKeyFeedBySystem();
		return isRetrievedCache(key, systemService, connectionInfo);
	}

	/**
	 * リクエスト・メインスレッド初期処理において、システム管理サービスのサービス固有Feedをキャッシュから取得済みかどうか判定.
	 * @param systemService システム管理サービス
	 * @param serviceName サービス名
	 * @param connectionInfo コネクション情報
	 * @return システム管理サービスのサービス固有Feedをキャッシュから取得済みの場合true
	 */
	private boolean isRetrievedCacheFeedBySystem(String systemService, String serviceName,
			ConnectionInfo connectionInfo) {
		String key = getInitThreadStatusMapKeyFeedBySystem(serviceName);
		return isRetrievedCache(key, systemService, connectionInfo);
	}

	/**
	 * リクエスト・メインスレッド初期処理において、自サービスのFeedをキャッシュから取得済みかどうか判定.
	 * @param serviceName サービス名
	 * @param connectionInfo コネクション情報
	 * @return 自サービスのFeedをキャッシュから取得済みの場合true
	 */
	private boolean isRetrievedCacheFeedByService(String serviceName,
			ConnectionInfo connectionInfo) {
		String key = getInitThreadStatusMapKeyFeedByService();
		return isRetrievedCache(key, serviceName, connectionInfo);
	}

	/**
	 * ユーザ情報のFeedをキャッシュから取得済みかどうか判定.
	 * @param serviceName サービス名
	 * @param connectionInfo コネクション情報
	 * @return ユーザ情報のFeedをキャッシュから取得済みの場合true
	 */
	private boolean isRetrievedCacheFeedByUser(String uid, String serviceName,
			ConnectionInfo connectionInfo) {
		String key = getUserInfoCacheStatusMapKeyFeed(uid);
		return isRetrievedCache(key, serviceName, connectionInfo);
	}

	/**
	 * リクエスト・メインスレッド初期処理において、キャッシュから取得済みかどうか判定.
	 * @param key キャッシュキー
	 * @param serviceName サービス名
	 * @param connectionInfo コネクション情報
	 * @return キャッシュから取得済みの場合true
	 */
	private boolean isRetrievedCache(String key, String serviceName,
			ConnectionInfo connectionInfo) {
		Map<String, String> statusMap = ReflexConnectionUtil.getStatusMap(
				serviceName, connectionInfo);
		String val = statusMap.get(key);
		return getInitThreadStatusRetrieved().equals(val);
	}

	/**
	 * リクエスト・メインスレッド初期処理において、システム管理サービスのEntryリストをキャッシュから取得済みとするフラグをセット.
	 * @param systemService システム管理サービス
	 * @param connectionInfo コネクション情報
	 */
	private void setRetrievedCacheEntriesBySystem(String systemService,
			ConnectionInfo connectionInfo) {
		String key = getInitThreadStatusMapKeyEntriesBySystem();
		setRetrievedCache(key, systemService, connectionInfo);
	}

	/**
	 * リクエスト・メインスレッド初期処理において、システム管理サービスのサービス固有Entryリストをキャッシュから取得済みとするフラグをセット.
	 * @param systemService システム管理サービス
	 * @param serviceName サービス名
	 * @param connectionInfo コネクション情報
	 * @return システム管理サービスのサービス固有Entryリストをキャッシュから取得済みの場合true
	 */
	private void setRetrievedCacheEntriesBySystem(String systemService, String serviceName,
			ConnectionInfo connectionInfo) {
		String key = getInitThreadStatusMapKeyEntriesBySystem(serviceName);
		setRetrievedCache(key, systemService, connectionInfo);
	}

	/**
	 * リクエスト・メインスレッド初期処理において、自サービスのEntryリストをキャッシュから取得済みとするフラグをセット.
	 * @param serviceName サービス名
	 * @param connectionInfo コネクション情報
	 */
	private void setRetrievedCacheEntriesByService(String serviceName,
			ConnectionInfo connectionInfo) {
		String key = getInitThreadStatusMapKeyEntriesByService();
		setRetrievedCache(key, serviceName, connectionInfo);
	}

	/**
	 * ユーザ情報Entryリストをキャッシュから取得済みとするフラグをセット.
	 * @param uid UID
	 * @param serviceName サービス名
	 * @param connectionInfo コネクション情報
	 */
	private void setRetrievedCacheEntriesByUser(String uid, String serviceName,
			ConnectionInfo connectionInfo) {
		String key = getUserInfoCacheStatusMapKeyEntries(uid);
		setRetrievedCache(key, serviceName, connectionInfo);
	}

	/**
	 * リクエスト・メインスレッド初期処理において、システム管理サービスのFeedをキャッシュから取得済みとするフラグをセット.
	 * @param systemService システム管理サービス
	 * @param connectionInfo コネクション情報
	 */
	private void setRetrievedCacheFeedBySystem(String systemService, ConnectionInfo connectionInfo) {
		String key = getInitThreadStatusMapKeyFeedBySystem();
		setRetrievedCache(key, systemService, connectionInfo);
	}

	/**
	 * リクエスト・メインスレッド初期処理において、システム管理サービスのサービス固有Feedをキャッシュから取得済みとするフラグをセット.
	 * @param systemService システム管理サービス
	 * @param serviceName サービス名
	 * @param connectionInfo コネクション情報
	 */
	private void setRetrievedCacheFeedBySystem(String systemService, String serviceName,
			ConnectionInfo connectionInfo) {
		String key = getInitThreadStatusMapKeyFeedBySystem(serviceName);
		setRetrievedCache(key, systemService, connectionInfo);
	}

	/**
	 * リクエスト・メインスレッド初期処理において、自サービスのFeedをキャッシュから取得済みとするフラグをセット.
	 * @param serviceName サービス名
	 * @param connectionInfo コネクション情報
	 */
	private void setRetrievedCacheFeedByService(String serviceName, ConnectionInfo connectionInfo) {
		String key = getInitThreadStatusMapKeyFeedByService();
		setRetrievedCache(key, serviceName, connectionInfo);
	}

	/**
	 * ユーザ情報のFeedをキャッシュから取得済みとするフラグをセット.
	 * @param uid UID
	 * @param serviceName サービス名
	 * @param connectionInfo コネクション情報
	 */
	private void setRetrievedCacheFeedByUser(String uid, String serviceName,
			ConnectionInfo connectionInfo) {
		String key = getUserInfoCacheStatusMapKeyFeed(uid);
		setRetrievedCache(key, serviceName, connectionInfo);
	}

	/**
	 * リクエスト・メインスレッド初期処理において、キャッシュから取得済みのフラグをセット.
	 * @param key キャッシュキー
	 * @param serviceName サービス名
	 * @param connectionInfo コネクション情報
	 */
	private void setRetrievedCache(String key, String serviceName,
			ConnectionInfo connectionInfo) {
		Map<String, String> statusMap = ReflexConnectionUtil.getStatusMap(
				serviceName, connectionInfo);
		statusMap.put(key, getInitThreadStatusRetrieved());
	}

	/**
	 * エントリー更新後のキャッシュ更新.
	 * キャッシュ対象のEntryであれば、キャッシュを更新する。
	 * @param updatedInfos 更新情報リスト
	 * @param originalServiceName 実行元サービス名
	 * @param reflexContext ReflexContext
	 */
	void afterCommit(List<UpdatedInfo> updatedInfos, String originalServiceName,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		// キャッシュ対象URIを取得。サービス名はワイルドカードとし、正規表現で一致判定する。
		String systemService = TaggingEnvUtil.getSystemService();
		String serviceName = reflexContext.getServiceName();	// 更新先サービス名
		boolean isSystemService = systemService.equals(serviceName);
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		
		Set<String> settingEntryUrisBySystem = null;
		Set<String> settingEntryUrisBySystemPattern = null;
		Set<String> settingFeedUrisBySystem = null;
		Set<String> settingFeedUrisBySystemPattern = null;
		Set<String> settingEntryUrisByService = null;
		Set<String> settingFeedUrisByService = null;
		if (isSystemService) {
			settingEntryUrisBySystem = getEntryUris(SettingDataType.SYSTEM, systemService);
			settingEntryUrisBySystemPattern = getEntryUris(SettingDataType.SYSTEM_SERVICE,
					BDBClientInitMainThreadConst.REGEX_PATTERN_GROUP);
			settingFeedUrisBySystem = getFeedUris(SettingDataType.SYSTEM, systemService);
			settingFeedUrisBySystemPattern = getFeedUris(SettingDataType.SYSTEM_SERVICE,
					BDBClientInitMainThreadConst.REGEX_PATTERN_GROUP);
		}
		settingEntryUrisByService = getEntryUris(SettingDataType.SERVICE, serviceName);
		settingFeedUrisByService = getFeedUris(SettingDataType.SERVICE, serviceName);

		List<Pattern> patternUserInfoEntryUris = getPatternUserInfoEntryUris();
		List<Pattern> patternUserInfoFeedUris = getPatternUserInfoFeedUris();
		int expireSec = getInitMainthreadCacheExpireSec();

		// 更新したURIと親階層をリストにする。
		Set<String> updatedUris = new HashSet<>();
		Set<String> upatedParentUris = new HashSet<>();
		for (UpdatedInfo updatedInfo : updatedInfos) {
			EntryBase prevEntry = updatedInfo.getPrevEntry();
			if (prevEntry != null) {
				String idUri = TaggingEntryUtil.getUriById(prevEntry.id);
				updatedUris.add(idUri);
				upatedParentUris.add(TaggingEntryUtil.removeLastSlash(
						TaggingEntryUtil.getParentUri(idUri)));
				List<String> aliases = prevEntry.getAlternate();
				if (aliases != null) {
					for (String alias : aliases) {
						updatedUris.add(alias);
						upatedParentUris.add(TaggingEntryUtil.removeLastSlash(
								TaggingEntryUtil.getParentUri(alias)));
					}
				}
			}
			EntryBase updEntry = updatedInfo.getUpdEntry();
			if (updEntry != null) {
				String idUri = TaggingEntryUtil.getUriById(updEntry.id);
				updatedUris.add(idUri);
				upatedParentUris.add(TaggingEntryUtil.removeLastSlash(
						TaggingEntryUtil.getParentUri(idUri)));
				List<String> aliases = updEntry.getAlternate();
				if (aliases != null) {
					for (String alias : aliases) {
						updatedUris.add(alias);
						upatedParentUris.add(TaggingEntryUtil.removeLastSlash(
								TaggingEntryUtil.getParentUri(alias)));
					}
				}
			}
		}

		// システム管理サービス
		if (isSystemService) {
			// システム管理サービスのEntryリスト
			boolean isUpdatedSettingEntry = false;
			if (settingEntryUrisBySystem != null) {
				for (String uri : settingEntryUrisBySystem) {
					if (updatedUris.contains(uri)) {
						isUpdatedSettingEntry = true;
					}
				}
			}
			if (isUpdatedSettingEntry) {
				getEntriesAndSetCacheFeed(SettingDataType.SYSTEM, systemService, expireSec,
						reflexContext);
			}

			// システム管理サービスのサービスごとのEntryリスト
			isUpdatedSettingEntry = false;
			List<Pattern> patternsEntryUri = getPatternSettingEntryUris(
					SettingDataType.SYSTEM_SERVICE,
					settingEntryUrisBySystemPattern);
			Set<String> updatedSettingServices = new HashSet<>();
			for (Pattern pattern : patternsEntryUri) {
				for (String uri : updatedUris) {
					Matcher matcher = pattern.matcher(uri);
					if (matcher.matches()) {
						// サービス名を抽出
						String tmpServiceName = matcher.group(1);
						updatedSettingServices.add(tmpServiceName);
					}
				}
			}
			// 対象のEntryがある場合、サービスを指定して再更新する。
			for (String updatedSettingService : updatedSettingServices) {
				getEntriesAndSetCacheFeed(SettingDataType.SYSTEM_SERVICE,
						updatedSettingService, expireSec, reflexContext);
			}

			// システム管理サービスのFeed
			for (String feedUri : settingFeedUrisBySystem) {
				if (upatedParentUris.contains(feedUri)) {
					getFeedAndSetCacheFeed(SettingDataType.SYSTEM, feedUri,
							originalServiceName, expireSec, reflexContext);
				}
			}

			// システム管理サービスのサービスごとのFeed
			List<Pattern> patternsFeedUri = getPatternSettingFeedUris(
					SettingDataType.SYSTEM_SERVICE,
					settingFeedUrisBySystemPattern);
			for (Pattern pattern : patternsFeedUri) {
				for (String parentUri : upatedParentUris) {
					Matcher matcher = pattern.matcher(parentUri);
					if (matcher.matches()) {
						getFeedAndSetCacheFeed(SettingDataType.SYSTEM_SERVICE, parentUri,
								originalServiceName, expireSec, reflexContext);
					}
				}
			}
		}

		// 全てのサービス
		// Entryリスト
		boolean isUpdatedSettingEntry = false;
		for (String uri : settingEntryUrisByService) {
			if (updatedUris.contains(uri)) {
				isUpdatedSettingEntry = true;
			}
		}
		if (isUpdatedSettingEntry) {
			getEntriesAndSetCacheFeed(SettingDataType.SERVICE, originalServiceName, 
					expireSec, reflexContext);
		}
		// Feed
		boolean isUpdatedSettingFeed = false;
		for (String feedUri : settingFeedUrisByService) {
			if (upatedParentUris.contains(feedUri)) {
				getFeedAndSetCacheFeed(SettingDataType.SERVICE, feedUri,
						originalServiceName, expireSec, reflexContext);
				isUpdatedSettingFeed = true;
			}
		}
		// サービス設定の更新
		if (isUpdatedSettingEntry || isUpdatedSettingFeed) {
			ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
			serviceManager.settingServiceIfAbsent(serviceName, requestInfo, connectionInfo);
		}

		// ユーザ情報
		// Entryリスト
		Set<String> updatedUids = new HashSet<>();
		for (Pattern pattern : patternUserInfoEntryUris) {
			for (String uri : updatedUris) {
				Matcher matcher = pattern.matcher(uri);
				if (matcher.matches()) {
					// UIDを抽出
					String uid = matcher.group(1);
					updatedUids.add(uid);
				}
			}
		}
		// 対象のEntryがある場合、サービスを指定して再更新する。
		for (String updatedUid : updatedUids) {
			Set<String> entryUris = getUserInfoEntryUris(updatedUid);
			getEntriesAndSetCacheFeed(SettingDataType.USER, updatedUid, serviceName,
					entryUris, expireSec, reflexContext);
		}

		// Feed
		for (Pattern pattern : patternUserInfoFeedUris) {
			for (String parentUri : upatedParentUris) {
				Matcher matcher = pattern.matcher(parentUri);
				if (matcher.matches()) {
					getFeedAndSetCacheFeed(SettingDataType.USER, parentUri,
							originalServiceName, expireSec, reflexContext);
				}
			}
		}

	}

	/**
	 * リクエスト・メインスレッド初期処理で取得されるサービスごとのEntryリストのPatternリストを取得.
	 * Static領域から取得。存在しない場合は登録する。
	 * @param settingDataType 設定データタイプ
	 * @param regexUris 正規表現URIリスト
	 * @return Patternリスト
	 */
	private List<Pattern> getPatternSettingEntryUris(SettingDataType settingDataType,
			Set<String> regexUris) {
		String staticKey = null;
		if (SettingDataType.SYSTEM_SERVICE.equals(settingDataType)) {
			staticKey = BDBClientInitMainThreadConst.STATIC_NAME_INITMAINTHREAD_PATTERNS_ENTRIES;
		} else if (SettingDataType.USER.equals(settingDataType)) {
			staticKey = BDBClientInitMainThreadConst.STATIC_NAME_USERINFOCACHE_PATTERNS_ENTRIES;
		} else {
			logger.warn("[getPatternSettingEntryUris] settingDataType is invalid. " + settingDataType);
			return null;
		}
		// Staticに設定がある場合返す
		List<Pattern> patternList = (List<Pattern>)ReflexStatic.getStatic(staticKey);
		if (patternList == null) {
			// Staticに設定がない場合、Patternを作成する
			patternList = new CopyOnWriteArrayList<>();
			for (String regexUri : regexUris) {
				Pattern pattern = Pattern.compile(editStartEnd(regexUri));
				patternList.add(pattern);
			}
			try {
				ReflexStatic.setStatic(staticKey, patternList);
			} catch (StaticDuplicatedException e) {
				// 重複の場合何もしない
			}
		}
		return patternList;
	}

	/**
	 * リクエスト・メインスレッド初期処理で取得されるサービスごとのFeed検索URIリストのPatternリストを取得.
	 * Static領域から取得。存在しない場合は登録する。
	 * @param regexUris 正規表現URIリスト
	 * @return Patternリスト
	 */
	private List<Pattern> getPatternSettingFeedUris(SettingDataType settingDataType,
			Set<String> regexUris) {
		String staticKey = null;
		if (SettingDataType.SYSTEM_SERVICE.equals(settingDataType)) {
			staticKey = BDBClientInitMainThreadConst.STATIC_NAME_INITMAINTHREAD_PATTERNS_FEED;
		} else if (SettingDataType.USER.equals(settingDataType)) {
			staticKey = BDBClientInitMainThreadConst.STATIC_NAME_USERINFOCACHE_PATTERNS_FEED;
		} else {
			logger.warn("[getPatternSettingFeedUris] settingDataType is invalid. " + settingDataType);
			return null;
		}
		// Staticに設定がある場合返す
		List<Pattern> patternList = (List<Pattern>)ReflexStatic.getStatic(staticKey);
		if (patternList == null) {
			patternList = new CopyOnWriteArrayList<>();
			for (String regexUri : regexUris) {
				Pattern pattern = Pattern.compile(editStartEnd(regexUri));
				patternList.add(pattern);
			}
			try {
				ReflexStatic.setStatic(staticKey, patternList);
			} catch (StaticDuplicatedException e) {
				// 重複の場合何もしない
			}
		}
		return patternList;
	}

	/**
	 * 正規表現URIに先頭と終端を付加する.
	 * @param regexUri 正規表現URI
	 * @return 編集した正規表現URI
	 */
	private String editStartEnd(String regexUri) {
		StringBuilder sb = new StringBuilder();
		sb.append("^");
		sb.append(regexUri);
		sb.append("$");
		return sb.toString();
	}

	/**
	 * リクエスト・メインスレッドのユーザ情報初期処理.
	 * @param uid UID
	 * @param servieName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void initMainThreadUser(String uid, String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		SystemContext systemContext = new SystemContext(serviceName,
				requestInfo, connectionInfo);
		ReflexAuthentication auth = systemContext.getAuth();

		if (isEnableAccessLog()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
					"[initMainThreadUser] start. uid=" + uid);
		}

		int expireSec = getInitMainthreadCacheExpireSec();
		SettingDataType settingDataType = SettingDataType.USER;
		if (!isRetrievedCacheEntriesByUser(uid, serviceName, connectionInfo)) {
		// キャッシュから読み込む
			Set<String> getEntryUris = getUserInfoEntryUris(uid);
			// Entry検索。ReadEntryMapに格納。
			if (!getEntryUris.isEmpty()) {
				// Entryキャッシュ検索。
				Set<String> tmpGetEntryUris = getCacheEntriesAndSetReadEntryMap(
						settingDataType, uid, serviceName, getEntryUris, systemContext);
				if (tmpGetEntryUris != null && !tmpGetEntryUris.isEmpty()) {
					// データストアへEntry検索。ReadEntryMapとキャッシュに格納。
					getEntriesAndSetCacheFeed(settingDataType, uid, serviceName, getEntryUris,
							expireSec, systemContext);
				}
			}
		}

		// Feed検索
		// すでにキャッシュを読み込んでいる場合は処理を飛ばす。
		if (!isRetrievedCacheFeedByUser(uid, serviceName, connectionInfo)) {
			Map<String, Future<FeedBase>> futuresMap = new HashMap<>();
			List<String> getFeedUris = getUserInfoFeedUriList(uid);
			futuresMap.putAll(initMainThreadFeed(settingDataType, getFeedUris,
					serviceName, expireSec, auth, requestInfo, connectionInfo));
			// 結果確認
			if (!futuresMap.isEmpty()) {
				for (Map.Entry<String, Future<FeedBase>> mapEntry : futuresMap.entrySet()) {
					String uri = mapEntry.getKey();
					Future<FeedBase> future = mapEntry.getValue();
					try {
						future.get();

					} catch (ExecutionException e) {
						Throwable cause = e.getCause();
						if (logger.isDebugEnabled()) {
							StringBuilder sb = new StringBuilder();
							sb.append(LogUtil.getRequestInfoStr(requestInfo));
							sb.append("[initMainThreadUser] ExecutionException: ");
							sb.append(cause.getMessage());
							sb.append(" uri=");
							sb.append(uri);
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
							sb.append("[initMainThreadUser] InterruptedException: ");
							sb.append(e.getMessage());
							sb.append(" uri=");
							sb.append(uri);
							logger.debug(sb.toString());
						}
						throw new IOException(e);
					}
				}

				// キャッシュ読み込みステータスを設定
				setRetrievedCacheFeedByUser(uid, serviceName, connectionInfo);
			}
		}

		if (isEnableAccessLog()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
					"[initMainThreadUser] end. uid=" + uid);
		}
	}

	/**
	 * ユーザ初期設定取得Entry URIの自階層+親階層リストを取得.
	 * @param uid UID
	 * @return ユーザ初期設定取得Entry URIの自階層+親階層リスト
	 */
	private Set<String> getUserInfoEntryUris(String uid) {
		UserManager userManager = TaggingEnvUtil.getUserManager();
		List<String> userSettingUris = userManager.getUserSettingEntryUris(uid);
		Set<String> tmpUris = getParentPathUris(userSettingUris);
		// UIDがあれば返す
		if (tmpUris == null || tmpUris.isEmpty()) {
			return null;
		}
		Set<String> retUris = new HashSet<>();
		for (String tmpUri : tmpUris) {
			if (tmpUri.indexOf(uid) > -1) {
				retUris.add(tmpUri);
			}
		}
		return retUris;
	}

	/**
	 * ユーザ初期設定に必要なFeed検索URIリストを取得.
	 * @param uid UID
	 * @return ユーザ初期設定に必要なFeed検索URIリスト
	 */
	private Set<String> getUserInfoFeedUris(String uid) {
		List<String> uriList = getUserInfoFeedUriList(uid);
		if (uriList != null && !uriList.isEmpty()) {
			return new HashSet<>(uriList);
		}
		return null;
	}

	/**
	 * ユーザ初期設定に必要なFeed検索URIリストを取得.
	 * @param uid UID
	 * @return ユーザ初期設定に必要なFeed検索URIリスト
	 */
	private List<String> getUserInfoFeedUriList(String uid) {
		UserManager userManager = TaggingEnvUtil.getUserManager();
		return userManager.getUserSettingFeedUris(uid);
	}

	/**
	 * ユーザ情報取得におけるEntry検索のPatternリストを取得.
	 * @return ユーザ情報取得におけるEntry検索のPatternリスト
	 */
	public List<Pattern> getPatternUserInfoEntryUris() {
		Set<String> userInfoEntryUrisPattern = getUserInfoEntryUris(
				BDBClientInitMainThreadConst.REGEX_PATTERN_GROUP);
		return getPatternSettingEntryUris(SettingDataType.USER, userInfoEntryUrisPattern);
	}

	/**
	 * ユーザ情報取得におけるFeed検索のPatternリストを取得.
	 * @return ユーザ情報取得におけるFeed検索のPatternリスト
	 */
	public List<Pattern> getPatternUserInfoFeedUris() {
		Set<String> userInfoFeedUrisPattern = getUserInfoFeedUris(
				BDBClientInitMainThreadConst.REGEX_PATTERN_GROUP);
		return getPatternSettingFeedUris(SettingDataType.USER, userInfoFeedUrisPattern);
	}

	/**
	 * 更新Entryのうち、メインスレッド初期処理に必要なもののみReadEntryMapに追加する.
	 * この処理は、afterCommitスレッド実行前に実行される。
	 * @param updatedInfos 更新情報
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void setUpdatedInfoToReadEntryMap(List<UpdatedInfo> updatedInfos,
			String serviceName, RequestInfo requestInfo, ConnectionInfo connectionInfo) {
		String systemService = TaggingEnvUtil.getSystemService();
		boolean isSystemService = systemService.equals(serviceName);
		Set<String> settingEntryUrisBySystem = null;
		Set<String> settingEntryUrisBySystemPattern = null;
		Set<String> settingFeedUrisBySystem = null;
		Set<String> settingFeedUrisBySystemPattern = null;
		Set<String> settingEntryUrisByService = null;
		Set<String> settingFeedUrisByService = null;
		List<Pattern> patternsEntryUri = null;
		List<Pattern> patternsFeedUri = null;
		if (isSystemService) {
			settingEntryUrisBySystem = getEntryUris(SettingDataType.SYSTEM, systemService);
			settingEntryUrisBySystemPattern = getEntryUris(SettingDataType.SYSTEM_SERVICE,
					BDBClientInitMainThreadConst.REGEX_PATTERN_GROUP);
			settingFeedUrisBySystem = getFeedUris(SettingDataType.SYSTEM, systemService);
			settingFeedUrisBySystemPattern = getFeedUris(SettingDataType.SYSTEM_SERVICE,
					BDBClientInitMainThreadConst.REGEX_PATTERN_GROUP);

			patternsEntryUri = getPatternSettingEntryUris(SettingDataType.SYSTEM_SERVICE,
					settingEntryUrisBySystemPattern);
			patternsFeedUri = getPatternSettingFeedUris(SettingDataType.SYSTEM_SERVICE,
					settingFeedUrisBySystemPattern);
		}
		settingEntryUrisByService = getEntryUris(SettingDataType.SERVICE, serviceName);
		settingFeedUrisByService = getFeedUris(SettingDataType.SERVICE, serviceName);

		List<Pattern> patternUserInfoEntryUris = getPatternUserInfoEntryUris();
		List<Pattern> patternUserInfoFeedUris = getPatternUserInfoFeedUris();

		// 更新したURIと親階層をリストにする。
		for (UpdatedInfo updatedInfo : updatedInfos) {
			Set<String> updatedUris = new HashSet<>();
			Set<String> upatedParentUris = new HashSet<>();
			// ReadEntryMapからは削除済みのため、削除は処理を飛ばす。
			if (OperationType.DELETE.equals(updatedInfo.getFlg()) ||
					updatedInfo.getUpdEntry() == null) {
				continue;
			}
			// 登録・更新
			EntryBase updEntry = updatedInfo.getUpdEntry();
			String idUri = TaggingEntryUtil.getUriById(updEntry.id);
			updatedUris.add(idUri);
			upatedParentUris.add(TaggingEntryUtil.removeLastSlash(TaggingEntryUtil.getParentUri(idUri)));
			List<String> aliases = updEntry.getAlternate();
			if (aliases != null) {
				for (String alias : aliases) {
					updatedUris.add(alias);
					upatedParentUris.add(TaggingEntryUtil.removeLastSlash(TaggingEntryUtil.getParentUri(alias)));
				}
			}

			boolean isUpdatedSettingEntry = false;
			boolean isUpdatedSettingFeed = false;
			// システム管理サービス
			if (isSystemService) {
				if (settingEntryUrisBySystem != null) {
					for (String uri : settingEntryUrisBySystem) {
						if (updatedUris.contains(uri)) {
							isUpdatedSettingEntry = true;
							break;
						}
					}
				}
				if (!isUpdatedSettingEntry) {
					// システム管理サービスのサービスごとのEntryリスト
					for (Pattern pattern : patternsEntryUri) {
						for (String uri : updatedUris) {
							Matcher matcher = pattern.matcher(uri);
							if (matcher.matches()) {
								isUpdatedSettingEntry = true;
								break;
							}
						}
					}
				}

				// システム管理サービスのFeed
				for (String feedUri : settingFeedUrisBySystem) {
					if (upatedParentUris.contains(feedUri)) {
						isUpdatedSettingFeed = true;
						break;
					}
				}

				// システム管理サービスのサービスごとのFeed
				if (!isUpdatedSettingFeed) {
					for (Pattern pattern : patternsFeedUri) {
						for (String parentUri : upatedParentUris) {
							Matcher matcher = pattern.matcher(parentUri);
							if (matcher.matches()) {
								isUpdatedSettingFeed = true;
								break;
							}
						}
					}
				}
			}

			// 全てのサービス
			// Entryリスト
			if (!isUpdatedSettingEntry) {
				for (String uri : settingEntryUrisByService) {
					if (updatedUris.contains(uri)) {
						isUpdatedSettingEntry = true;
						break;
					}
				}
			}
			// Feed
			if (!isUpdatedSettingFeed) {
				for (String feedUri : settingFeedUrisByService) {
					if (upatedParentUris.contains(feedUri)) {
						isUpdatedSettingFeed = true;
						break;
					}
				}
			}

			// ユーザ情報
			// Entryリスト
			if (!isUpdatedSettingEntry) {
				for (Pattern pattern : patternUserInfoEntryUris) {
					for (String uri : updatedUris) {
						Matcher matcher = pattern.matcher(uri);
						if (matcher.matches()) {
							isUpdatedSettingEntry = true;
							break;
						}
					}
				}
			}

			// Feed
			if (!isUpdatedSettingFeed) {
				for (Pattern pattern : patternUserInfoFeedUris) {
					for (String parentUri : upatedParentUris) {
						Matcher matcher = pattern.matcher(parentUri);
						if (matcher.matches()) {
							isUpdatedSettingFeed = true;
							break;
						}
					}
				}
			}

			// /_user?title={account}
			if (!isUpdatedSettingFeed) {
				for (String parentUri : upatedParentUris) {
					if (Constants.URI_USER.equals(parentUri)) {
						isUpdatedSettingFeed = true;
						break;
					}
				}
			}

			if (isUpdatedSettingEntry) {
				for (String uri : updatedUris) {
					BDBClientUtil.setReadEntryMap(updEntry, uri, serviceName,
							requestInfo, connectionInfo);
				}
			}
			if (isUpdatedSettingFeed) {
				// Feed検索は後続処理で検索し設定するため削除
				for (String parentUri : upatedParentUris) {
					BDBClientUtil.removeReadFeedMap(parentUri, serviceName,
							requestInfo, connectionInfo);
				}
			}
		}
	}

	/**
	 * メインスレッド初期処理に必要なデータキャッシュを有効にするかどうか.
	 * @return メインスレッド初期処理に必要なデータキャッシュを有効にする場合true
	 */
	boolean isEnabledInitMainThreadCache() {
		return TaggingEnvUtil.getSystemPropBoolean(
				BDBClientConst.BDBCLIENT_ENABLE_INITMAINTHREADCACHE, false);
	}

	/**
	 * メインスレッドキャッシュの有効期限(秒)を取得
	 * @return メインスレッドキャッシュの有効期限(秒)
	 */
	private int getInitMainthreadCacheExpireSec() {
		return TaggingEnvUtil.getSystemPropInt(
				BDBClientConst.BDBCLIENT_INITMAINTHREADCACHE_EXPIRE_SEC, 
				BDBClientConst.BDBCLIENT_INITMAINTHREADCACHE_EXPIRE_SEC_DEFAULT);
	}

	/**
	 * データストアのアクセスログを出力するかどうか
	 * @return データストアのアクセスログを出力する場合true;
	 */
	private boolean isEnableAccessLog() {
		return BDBClientUtil.isEnableAccessLog() && logger.isDebugEnabled();
	}

	/**
	 * メインスレッド初期化処理のアクセスログ（処理経過ログ）を出力するかどうか.
	 * テスト用
	 * @return メインスレッド初期化処理のアクセスログ（処理経過ログ）を出力する場合true
	 */
	private boolean isInitMainthreadEnableAccessLog() {
		return TaggingEnvUtil.getSystemPropBoolean(
				BDBClientConst.INITMAINTHREADCACHE_ENABLE_ACCESSLOG, false);
	}

}
