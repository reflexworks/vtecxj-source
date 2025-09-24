package jp.reflexworks.taggingservice.migrate;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.Category;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.servlet.util.UrlUtil;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.bdbclient.BDBClientConst;
import jp.reflexworks.taggingservice.bdbclient.BDBClientUtil;
import jp.reflexworks.taggingservice.bdbclient.BDBResponseInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.PermissionException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.index.FullTextSearchManager;
import jp.reflexworks.taggingservice.index.IndexCommonManager;
import jp.reflexworks.taggingservice.index.InnerIndexManager;
import jp.reflexworks.taggingservice.plugin.NamespaceManager;
import jp.reflexworks.taggingservice.plugin.ServiceManager;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBIndexType;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBResponseType;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBServerType;
import jp.reflexworks.taggingservice.requester.BDBClientServerManager;
import jp.reflexworks.taggingservice.requester.BDBClientServerUtil;
import jp.reflexworks.taggingservice.requester.BDBRequester;
import jp.reflexworks.taggingservice.requester.BDBRequesterUtil;
import jp.reflexworks.taggingservice.service.TaggingServiceUtil;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.NamespaceUtil;
import jp.reflexworks.taggingservice.util.RetryUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.ConsistentHash;
import jp.sourceforge.reflex.util.NumberingUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * サーバ追加・削除管理クラス
 */
public class MaintenanceManager {

	/** 最初にデータ移行を行うサーバタイプ */
	private static final BDBServerType[] MIGRATE_SERVERTYPE_1 = new BDBServerType[]{
			BDBServerType.ENTRY, BDBServerType.ALLOCIDS};
	/** 次にデータ移行を行うサーバタイプ */
	private static final BDBServerType[] MIGRATE_SERVERTYPE_2 = new BDBServerType[]{
			BDBServerType.INDEX, BDBServerType.FULLTEXT};

	/** エラー時のログエントリーtitle */
	private static final String LOG_TITLE = "MaintenanceMigrate";

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * サーバ追加.
	 * @param 対象サービス名
	 * @param feed サーバ追加情報
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void addServer(String serviceName, FeedBase feed, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		if (logger.isDebugEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "addServer start.");
		}

		BDBClientServiceManager bdbClientServiceManager = new BDBClientServiceManager();

		SystemContext systemContext = new SystemContext(TaggingEnvUtil.getSystemService(),
				requestInfo, connectionInfo);

		String targetServiceStatus = MaintenanceConst.SERVICE_STATUS_TARGET;

		// サービスステータスエントリーを取得
		EntryBase serviceEntry = checkAndGetServiceEntry(serviceName, false, systemContext);
		String beforeServiceStatus = getServiceStatus(serviceEntry);
		// サーバ割り当て済みで、データ移行のみリトライかどうかのフラグ
		boolean isRetryMigrate = MaintenanceConst.SERVICE_STATUS_MAINTENANCE_FAILURE.equals(beforeServiceStatus) &&
				MaintenanceConst.PROGRESS_MAINTENANCE_2.equals(getMaintenanceProgress(serviceEntry));
		String maintenanceStatus = null;
		if (!isRetryMigrate) {
			maintenanceStatus = MaintenanceConst.PROGRESS_MAINTENANCE_1;
		} else {
			maintenanceStatus = MaintenanceConst.PROGRESS_MAINTENANCE_2;
		}

		// キー: サーバタイプ、値: 現在の割り当てサーバリスト
		Map<BDBServerType, List<String>> currentServersMap = new HashMap<>();
		// キー: サーバタイプ、値: 新サーバリスト
		Map<BDBServerType, List<String>> newServersMap = new HashMap<>();
		// キー: サーバタイプ、値: バックアップサーバリスト
		Map<BDBServerType, List<String>> backupServersMap = new HashMap<>();
		for (EntryBase entry : feed.entry) {
			BDBServerType serverType = getServerType(entry.title);

			// 現在の割り当てサーバリストを取得
			List<String> currentServerNames = BDBClientServerUtil.getBDBServerNamesFromBDB(
					serviceName, serverType, requestInfo, connectionInfo);
			currentServersMap.put(serverType, currentServerNames);

			if (!isRetryMigrate) {
				// 割り当て可能なサーバ名リストを取得
				String serverTypeUri = BDBClientServerUtil.getServerTypeUri(serverType);
				List<String> assignableServers = bdbClientServiceManager.getAssignableServers(
						serviceName, targetServiceStatus, serverTypeUri, systemContext);
				// 現在の割り当てサーバを差し引いた割り当て可能サーバリスト
				List<String> tmpAssignableServers = new ArrayList<>();
				for (String assignableServer : assignableServers) {
					if (!currentServerNames.contains(assignableServer)) {
						tmpAssignableServers.add(assignableServer);
					}
				}

				// 指定される値は数値またはサーバ名(複数の場合カンマ区切り)
				List<String> newServerNames = new ArrayList<>();
				newServerNames.addAll(currentServerNames);
				newServersMap.put(serverType, newServerNames);

				String inputValue = entry.subtitle;
				if (StringUtils.isInteger(inputValue)) {
					// 追加の場合で割り当て数が指定されている場合、割り当て可能サーバ数をチェックする。
					// 指定された追加サーバより割り当て可能サーバ数が少ない場合はエラー。
					int num = Integer.parseInt(entry.subtitle);
					int assignableSize = tmpAssignableServers.size();
					if (num > assignableSize) {
						StringBuilder sb = new StringBuilder();
						sb.append("The number of assignable servers is exceeded. serverType=");
						sb.append(serverType.name());
						sb.append(", number=");
						sb.append(num);
						throw new IllegalParameterException(sb.toString());
					}

					// サーバ割り当て
					for (int i = 0; i < num; i++) {
						String serverName = NumberingUtil.chooseOne(tmpAssignableServers);
						newServerNames.add(serverName);
						tmpAssignableServers.remove(serverName);
					}

				} else {
					// サーバ名指定
					String[] inputServerNameParts = inputValue.split(
							BDBClientMaintenanceConst.DELIMITER_SERVERNAME);
					for (String inputServerNamePart : inputServerNameParts) {
						if (!tmpAssignableServers.contains(inputServerNamePart)) {
							String msg = null;
							if (currentServerNames.contains(inputServerNamePart)) {
								msg = "The server has already been assigned. ";
							} else {
								msg = "The server cannot be assigned. ";
							}
							throw new IllegalParameterException(msg + inputServerNamePart);
						}
						newServerNames.add(inputServerNamePart);
					}
				}

			} else {
				// データ移行のみリトライの場合、バックアップサーバリストを取得
				List<String> backupServers = getBackupServers(serviceName, serverType,
						systemContext);
				backupServersMap.put(serverType, backupServers);
			}
		}
		
		// バッチジョブサーバのシャットダウン対応のため、ステータス更新は非同期処理内で行う。

		try {
			// データ移行をTaskQueueで行う。
			// ここでは非同期処理の終了は待たない。
			if (!isRetryMigrate) {
				// サーバ割り当て・データ移行
				MaintenanceAddOrRemoveServerCallable callable =
						new MaintenanceAddOrRemoveServerCallable(serviceName, serviceEntry,
								maintenanceStatus, false, newServersMap, currentServersMap);
				MigrateUtil.addTask(callable, auth, requestInfo, connectionInfo);

			} else {
				// サーバ割り当て済みのため、データ移行のみ
				MaintenanceRetryMigrateCallable callable = new MaintenanceRetryMigrateCallable(
						serviceName, serviceEntry, maintenanceStatus, currentServersMap,
						backupServersMap, false);
				MigrateUtil.addTask(callable, auth, requestInfo, connectionInfo);
			}

			if (logger.isDebugEnabled()) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "addServer end.");
			}

		} catch (IOException | TaggingException | RuntimeException | Error e) {
			// サービスステータスを失敗にする
			serviceEntry = updateServiceStatus(serviceEntry,
					MaintenanceConst.SERVICE_STATUS_MAINTENANCE_FAILURE,
					getMaintenanceProgress(serviceEntry),
					systemContext);
			throw e;
		}
	}

	/**
	 * サーバ削除.
	 * @param 対象サービス名
	 * @param feed サーバ追加情報
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void removeServer(String serviceName, FeedBase feed, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		if (logger.isDebugEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "removeServer start.");
		}

		SystemContext systemContext = new SystemContext(TaggingEnvUtil.getSystemService(),
				requestInfo, connectionInfo);

		// サービスステータスエントリーを取得
		EntryBase serviceEntry = checkAndGetServiceEntry(serviceName, true, systemContext);
		String beforeServiceStatus = getServiceStatus(serviceEntry);
		// サーバ割り当て済みで、データ移行のみリトライかどうかのフラグ
		boolean isRetryMigrate = MaintenanceConst.SERVICE_STATUS_MAINTENANCE_FAILURE.equals(beforeServiceStatus) &&
				MaintenanceConst.PROGRESS_MAINTENANCE_2.equals(getMaintenanceProgress(serviceEntry));
		String maintenanceStatus = null;
		if (!isRetryMigrate) {
			maintenanceStatus = MaintenanceConst.PROGRESS_MAINTENANCE_1;
		} else {
			maintenanceStatus = MaintenanceConst.PROGRESS_MAINTENANCE_2;
		}

		// キー: サーバタイプ、値: 現在の割り当てサーバリスト
		Map<BDBServerType, List<String>> currentServersMap = new HashMap<>();
		// キー: サーバタイプ、値: 新サーバリスト
		Map<BDBServerType, List<String>> newServersMap = new HashMap<>();
		// キー: サーバタイプ、値: バックアップサーバリスト
		Map<BDBServerType, List<String>> backupServersMap = new HashMap<>();
		for (EntryBase entry : feed.entry) {
			BDBServerType serverType = getServerType(entry.title);
			// 現在の割り当てサーバリストを取得
			List<String> currentServerNames = currentServersMap.get(serverType);
			if (currentServerNames == null) {
				currentServerNames = BDBClientServerUtil.getBDBServerNamesFromBDB(
						serviceName, serverType, requestInfo, connectionInfo);
				currentServersMap.put(serverType, currentServerNames);

				// 新サーバリストにコピーする
				List<String> newServerNames = new ArrayList<>();
				newServerNames.addAll(currentServerNames);
				newServersMap.put(serverType, newServerNames);
			}

			if (!isRetryMigrate) {
				String[] removeServerNameParts = entry.subtitle.split(
						BDBClientMaintenanceConst.DELIMITER_SERVERNAME);
				List<String> newServerNames = newServersMap.get(serverType);
				for (String removeServerName : removeServerNameParts) {
					// 削除の場合、現在の割り当てサーバリストに指定された削除サーバ名が存在しなければエラー
					if (!currentServerNames.contains(removeServerName)) {
						throw new IllegalParameterException(
								"The server is not assigned. " + removeServerName);
					}

					// 新サーバリストから指定されたサーバを削除
					newServerNames.remove(removeServerName);
				}
				// 新サーバリストが空になる場合エラー
				if (newServerNames.isEmpty()) {
					throw new IllegalParameterException(
							"The server allocation is lost. " + serverType.name());
				}

			} else {
				// データ移行のみリトライの場合、バックアップサーバリストを取得
				List<String> backupServers = getBackupServers(serviceName, serverType,
						systemContext);
				backupServersMap.put(serverType, backupServers);
			}
		}
		
		// バッチジョブサーバのシャットダウン対応のため、ステータス更新は非同期処理内で行う。

		try {
			// データ移行をTaskQueueで行う。
			// ここでは非同期処理の終了は待たない。
			if (!isRetryMigrate) {
				// サーバ割り当て・データ移行
				MaintenanceAddOrRemoveServerCallable callable =
						new MaintenanceAddOrRemoveServerCallable(serviceName, serviceEntry,
								maintenanceStatus, true, newServersMap, currentServersMap);
				MigrateUtil.addTask(callable, auth, requestInfo, connectionInfo);

			} else {
				// サーバ割り当て済みのため、データ移行のみ
				MaintenanceRetryMigrateCallable callable = new MaintenanceRetryMigrateCallable(
						serviceName, serviceEntry, maintenanceStatus, currentServersMap,
						backupServersMap, true);
				MigrateUtil.addTask(callable, auth, requestInfo, connectionInfo);
			}

			if (logger.isDebugEnabled()) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "removeServer end.");
			}

		} catch (IOException | TaggingException | RuntimeException | Error e) {
			// サービスステータスを失敗にする
			serviceEntry = updateServiceStatus(serviceEntry,
					MaintenanceConst.SERVICE_STATUS_MAINTENANCE_FAILURE,
					getMaintenanceProgress(serviceEntry),
					systemContext);
			throw e;
		}
	}

	/**
	 * サーバ追加・削除
	 * @param serviceName 対象サービス名
	 * @param serviceEntry サービスエントリー
	 * @param maintenanceStatus メンテナンスステータス
	 * @param isRemove サーバ削除の場合true
	 * @param newServersMap 新サーバリスト
	 * @param currentServersMap 現在の割り当てサーバリスト
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	void maintenanceAddOrRemoveServer(String serviceName, EntryBase serviceEntry,
			String maintenanceStatus, boolean isRemove, Map<BDBServerType,
			List<String>> newServersMap, Map<BDBServerType, List<String>> currentServersMap,
			ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String systemService = TaggingEnvUtil.getSystemService();
		SystemContext systemContext = new SystemContext(systemService,
				requestInfo, connectionInfo);

		boolean isChangedServiceStatus = false;
		try {
			// サービスステータスをmaintenanceに更新。
			// 更新時にリビジョンによる楽観的排他制御を行う。
			serviceEntry = updateServiceStatus(serviceEntry,
					MaintenanceConst.SERVICE_STATUS_MAINTENANCE,
					maintenanceStatus, systemContext);
			isChangedServiceStatus = true;

			// 更新前に、BDBバックアップを行う
			String datetimeStr = BackupForMigrateUtil.getDatetimeStr();
			// 名前空間の取得
			NamespaceManager namespaceManager = TaggingEnvUtil.getNamespaceManager();
			namespaceManager.settingService(serviceName, requestInfo, connectionInfo);
			String namespace = namespaceManager.getNamespace(serviceName, requestInfo, connectionInfo);
			BackupForMigrateUtil.backupBDB(datetimeStr, namespace, serviceName,
					auth, requestInfo, connectionInfo);

			// サーバ割り当て情報の更新
			serviceEntry = maintenanceAssignServer(serviceName, serviceEntry, newServersMap,
					currentServersMap, isRemove, systemContext);
			// データ移行
			serviceEntry = maintenanceMigrate(serviceName, serviceEntry, newServersMap,
					currentServersMap, isRemove, auth, systemContext);

			// サービスステータスを戻す
			serviceEntry = updateServiceStatus(serviceEntry, MaintenanceConst.SERVICE_STATUS_TARGET,
					"", systemContext);

		} catch (IOException | TaggingException | RuntimeException | Error e) {
			// エラーの場合
			// * ログエントリー出力
			// * サービスステータスを「失敗」に更新する。(データ移行が途中まで行われている場合もあるのでサーバ情報は戻さない。)
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[maintenanceRemoveServer] Error occured.");
			logger.error(sb.toString(), e);

			failedMaintenanceMigrate(serviceName, serviceEntry, isChangedServiceStatus,
					systemContext);

			throw e;
		}
	}

	/**
	 * サーバ追加・削除で、割り当て後の移行処理で失敗した場合の再処理.
	 * @param serviceName 対象サービス名
	 * @param serviceEntry サービスエントリー
	 * @param maintenanceStatus メンテナンスステータス
	 * @param newServersMap 新サーバリスト
	 * @param currentServersMap 現在の割り当てサーバリスト
	 * @param isRemove サーバ削除の場合true
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	void maintenanceRetryMigrate(String serviceName, EntryBase serviceEntry,
			String maintenanceStatus, Map<BDBServerType, List<String>> newServersMap,
			Map<BDBServerType, List<String>> currentServersMap, boolean isRemove,
			ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String systemService = TaggingEnvUtil.getSystemService();
		SystemContext systemContext = new SystemContext(systemService,
				requestInfo, connectionInfo);

		boolean isChangedServiceStatus = false;
		try {
			// サービスステータスをmaintenanceに更新。
			// 更新時にリビジョンによる楽観的排他制御を行う。
			serviceEntry = updateServiceStatus(serviceEntry,
					MaintenanceConst.SERVICE_STATUS_MAINTENANCE,
					maintenanceStatus, systemContext);
			isChangedServiceStatus = true;

			// データ移行
			maintenanceMigrate(serviceName, serviceEntry, newServersMap, currentServersMap,
					isRemove, auth, systemContext);

			// サービスステータスを戻す
			updateServiceStatus(serviceEntry, MaintenanceConst.SERVICE_STATUS_TARGET, "",
					systemContext);

		} catch (IOException | TaggingException | RuntimeException | Error e) {
			// エラーの場合
			// * ログエントリー出力
			// * サービスステータスを「失敗」に更新する。(データ移行が途中まで行われている場合もあるのでサーバ情報は戻さない。)
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[maintenanceRetryMigrate] Error occured.");
			logger.error(sb.toString(), e);

			failedMaintenanceMigrate(serviceName, serviceEntry, isChangedServiceStatus,
					systemContext);

			throw e;
		}
	}

	/**
	 * サーバ割り当て情報の更新.
	 * @param serviceName 対象サービス名
	 * @param serviceEntry サービスエントリー
	 * @param newServersMap 新サーバリスト
	 * @param currentServersMap 現在の割り当てサーバリスト
	 * @param isRemove サーバ削除の場合true
	 * @param systemContext SystemContext
	 * @return サービスエントリー
	 */
	private EntryBase maintenanceAssignServer(String serviceName, EntryBase serviceEntry,
			Map<BDBServerType, List<String>> newServersMap,
			Map<BDBServerType, List<String>> currentServersMap, boolean isRemove,
			SystemContext systemContext)
	throws IOException, TaggingException {
		String systemService = TaggingEnvUtil.getSystemService();
		RequestInfo requestInfo = systemContext.getRequestInfo();
		ConnectionInfo connectionInfo = systemContext.getConnectionInfo();

		Set<BDBServerType> serverTypeSet = newServersMap.keySet();
		BDBClientServiceManager bdbClientServiceManager = new BDBClientServiceManager();

		if (logger.isInfoEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[maintenanceMigrate] start. serviceName=");
			sb.append(serviceName);
			logger.info(sb.toString());
		}

		List<EntryBase> putEntries = new ArrayList<>();

		// 現在のサーバ割り当て情報を元にバックアップエントリーを生成
		List<EntryBase> backupEntries = createPreviousBackupEntries(serviceName,
				serverTypeSet, requestInfo, connectionInfo);
		putEntries.addAll(backupEntries);

		// BDBサーバの差分更新
		for (Map.Entry<BDBServerType, List<String>> mapEntry : currentServersMap.entrySet()) {
			BDBServerType serverType = mapEntry.getKey();
			List<String> currentServers = mapEntry.getValue();
			List<String> newServers = newServersMap.get(serverType);
			// 削除サーバを抽出
			for (String currentServer : currentServers) {
				if (!newServers.contains(currentServer)) {
					// 削除
					String serverTypeUri = BDBClientServerUtil.getServerTypeUri(serverType);
					EntryBase serverEntry = bdbClientServiceManager.createServiceServerEntry(
							serviceName, serverTypeUri, currentServer);
					serverEntry.id = "?" + RequestParam.PARAM_DELETE;
					putEntries.add(serverEntry);
				}
			}

			// 登録サーバを抽出
			for (String newServer : newServers) {
				if (!currentServers.contains(newServer)) {
					// 登録
					String serverTypeUri = BDBClientServerUtil.getServerTypeUri(serverType);
					EntryBase serverEntry = bdbClientServiceManager.createServiceServerEntry(
							serviceName, serverTypeUri, newServer);
					putEntries.add(serverEntry);
				}
			}
		}

		// BDBサーバ割り当て情報を更新
		FeedBase putFeed = TaggingEntryUtil.createFeed(systemService);
		putFeed.entry = putEntries;
		systemContext.put(putFeed);

		// サービスステータスをmaintenance_2に更新。（サーバ情報更新済み、データ移行中）
		// 更新時にリビジョンによる楽観的排他制御を行う。
		serviceEntry = updateServiceStatus(serviceEntry, getServiceStatus(serviceEntry),
				MaintenanceConst.PROGRESS_MAINTENANCE_2, systemContext);

		// サービス情報の設定
		ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
		serviceManager.settingServiceIfAbsent(serviceName, requestInfo, connectionInfo);

		return serviceEntry;
	}


	/**
	 * サーバ割り当て情報の更新とデータ移行.
	 * @param serviceName 対象サービス名
	 * @param serviceEntry サービスエントリー
	 * @param newServersMap 新サーバリスト
	 * @param currentServersMap 現在の割り当てサーバリスト
	 * @param isRemove サーバ削除の場合true
	 * @param auth システム管理サービスの認証情報
	 * @param systemContext システム管理サービスのSystemContext
	 * @return サービスエントリー
	 */
	private EntryBase maintenanceMigrate(String serviceName, EntryBase serviceEntry,
			Map<BDBServerType, List<String>> newServersMap,
			Map<BDBServerType, List<String>> currentServersMap, boolean isRemove,
			ReflexAuthentication auth, SystemContext systemContext)
	throws IOException, TaggingException {
		RequestInfo requestInfo = systemContext.getRequestInfo();
		ConnectionInfo connectionInfo = systemContext.getConnectionInfo();

		// サービス情報の設定
		ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
		serviceManager.settingServiceIfAbsent(serviceName, requestInfo, connectionInfo);

		// 新サーバURLリストMap キー:サーバタイプ、値:新サーバURLリスト
		Map<BDBServerType, List<String>> newServerUrlsMap = new HashMap<>();
		BDBClientServerManager clientServerManager = new BDBClientServerManager();
		for (Map.Entry<BDBServerType, List<String>> mapEntry : newServersMap.entrySet()) {
			// staticに保持するサーバ情報の更新
			BDBServerType serverType = mapEntry.getKey();
			List<String> newServers = mapEntry.getValue();
			clientServerManager.changeStaticServerNames(serviceName, serverType, newServers);

			// ConnectionInfoに保持されている対象サーバのConsistentHashを更新する。
			List<String> newServerUrls = getServerUrlsByServerNames(serverType, newServers,
					requestInfo, connectionInfo);
			newServerUrlsMap.put(serverType, newServerUrls);
			BDBRequesterUtil.changeServerList(serverType, newServerUrls, serviceName, connectionInfo);
		}

		// 対象サービスの認証情報を生成
		ReflexAuthentication targetServiceAuth = MigrateUtil.createServiceAdminAuth(serviceName);

		// サーバの変更に伴うデータ移行
		// 最初はEntry・Allocidsサーバを移行し、終了後Index・Fulltextサーバの移行を行う。
		maintenanceMigrateProc(MIGRATE_SERVERTYPE_1, newServersMap, newServerUrlsMap,
				currentServersMap, isRemove, targetServiceAuth, requestInfo, connectionInfo);
		maintenanceMigrateProc(MIGRATE_SERVERTYPE_2, newServersMap, newServerUrlsMap,
				currentServersMap, isRemove, targetServiceAuth, requestInfo, connectionInfo);

		// 旧サーバ情報バックアップエントリーの削除
		String previousBackupUri = MigrateUtil.getPreviousBackupUri(serviceName);
		boolean async = false;
		boolean isParallel = false;
		systemContext.deleteFolder(previousBackupUri, async, isParallel);

		return serviceEntry;
	}

	/**
	 * 指定されたサーバタイプのデータ移行を並列に行う.
	 * @param targetServerTypes 移行対象サーバタイプ
	 * @param newServersMap 新サーバリスト
	 * @param newServerUrlsMap 新サーバURLリスト
	 * @param currentServersMap 現在の割り当てサーバリスト
	 * @param isRemove サーバ削除の場合true
	 * @param targetServiceAuth 対象サービスの認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	private void maintenanceMigrateProc(BDBServerType[] targetServerTypes,
			Map<BDBServerType, List<String>> newServersMap,
			Map<BDBServerType, List<String>> newServerUrlsMap,
			Map<BDBServerType, List<String>> currentServersMap, boolean isRemove,
			ReflexAuthentication targetServiceAuth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = targetServiceAuth.getServiceName();
		// サーバの変更に伴うデータ移行
		List<Future<Boolean>> futures = new ArrayList<>();
		for (BDBServerType serverType : targetServerTypes) {
			if (!currentServersMap.containsKey(serverType)) {
				continue;
			}
			List<String> currentServers = currentServersMap.get(serverType);
			List<String> newServers = newServersMap.get(serverType);
			// サーバ名をサーバURLに置き換える
			List<String> currentServerUrls = getServerUrlsByServerNames(serverType, currentServers,
					requestInfo, connectionInfo);
			ConsistentHash<String> currentConsistentHash = BDBRequesterUtil.createConsistentHash(currentServerUrls);
			List<String> newServerUrls = newServerUrlsMap.get(serverType);
			// 削除の場合、削除されたサーバのみ処理を行う
			List<String> targetServers = null;
			if (isRemove) {
				targetServers = new ArrayList<>();
				for (String currentServer : currentServers) {
					if (!newServers.contains(currentServer)) {
						targetServers.add(currentServer);
					}
				}
			} else {
				targetServers = currentServers;
			}

			for (String targetServer : targetServers) {
				// サーバごとの処理をスレッドごとに実行
				String targetServerUrl = getServerUrlByServerName(serverType, targetServer,
						requestInfo, connectionInfo);
				MaintenanceForEachServerCallable callable =
						new MaintenanceForEachServerCallable(serviceName, serverType,
								targetServerUrl, newServerUrls, currentConsistentHash);
				Future<Boolean> future = MigrateUtil.addTask(callable, targetServiceAuth,
						requestInfo, connectionInfo);
				futures.add(future);
			}
		}

		// 処理の終了を確認する（結果は見ない）
		int waitMillis = TaggingEnvUtil.getSystemPropInt(
				BDBClientConst.BULKPUT_SYNC_WAITMILLIS,
				BDBClientConst.BULKPUT_SYNC_WAITMILLIS_DEFAULT);
		for (Future<Boolean> future : futures) {
			while (!future.isDone()) {
				RetryUtil.sleep(waitMillis);
			}
		}
	}

	/**
	 * Entryサーバのデータ移行処理
	 * @param serviceName 対象サービス名
	 * @param sourceServerUrl 対象サーバURL
	 * @param newServerUrls 新サーバURLリスト
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	void maintenanceEntryServer(String serviceName, String sourceServerUrl,
			List<String> newServerUrls, ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String namespace = NamespaceUtil.getNamespace(serviceName, requestInfo, connectionInfo);
		BDBRequester<FeedBase> bdbRequester = new BDBRequester<>(BDBResponseType.FEED);
		List<Future<Boolean>> futures = new ArrayList<>();
		FeedTemplateMapper atomMapper = TaggingEnvUtil.getAtomResourceMapper();

		// 全エントリー
		// GET /b?_list=DBEntry
		String tableName = BDBClientServerConst.DB_ENTRY;
		String cursorStr = null;
		do {
			String getListUri = getListUri(tableName, cursorStr);
			BDBResponseInfo<FeedBase> responseInfoList = bdbRequester.requestByMigration(
					sourceServerUrl, getListUri, Constants.GET, namespace, atomMapper,
					serviceName, requestInfo, connectionInfo);
			FeedBase listFeed = responseInfoList.data;
			cursorStr = TaggingEntryUtil.getCursorFromFeed(listFeed);
			if (TaggingEntryUtil.isExistData(listFeed)) {
				for (EntryBase entry : listFeed.entry) {
					String id = entry.title;
					// 新サーバリストで割り当てる
					String idUri = TaggingEntryUtil.getUriById(id);
					String newServerUrl = BDBRequesterUtil.assignServer(BDBServerType.ENTRY,
							newServerUrls, idUri, serviceName, connectionInfo);
					if (!sourceServerUrl.equals(newServerUrl)) {
						// 割り当てサーバが変更された場合、データ移行（新サーバへ登録し、旧サーバから削除）
						MaintenanceEntryForEachEntryCallable callable =
								new MaintenanceEntryForEachEntryCallable(serviceName, id,
										sourceServerUrl);
						Future<Boolean> future = MigrateUtil.addTask(callable, auth,
								requestInfo, connectionInfo);
						futures.add(future);
					}
				}
			}

		} while (!StringUtils.isBlank(cursorStr));

		// 処理の終了を確認して返す（結果は見ない）
		int waitMillis = TaggingEnvUtil.getSystemPropInt(
				BDBClientConst.BULKPUT_SYNC_WAITMILLIS,
				BDBClientConst.BULKPUT_SYNC_WAITMILLIS_DEFAULT);
		for (Future<Boolean> future : futures) {
			while (!future.isDone()) {
				RetryUtil.sleep(waitMillis);
			}
		}
	}

	/**
	 * Entryサーバ追加・削除に伴うデータ移行 1エントリーごとの処理
	 * @param serviceName 対象サービス名
	 * @param id ID
	 * @param oldServerEntryUrl 旧EntryサーバURL
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	void maintenanceEntryForEachEntry(String serviceName, String id,
			String oldServerEntryUrl, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// 旧EntryサーバからEntryを取得
		// デシリアライズせず、InputSteamのまま受け取る。
		FeedTemplateMapper atomMapper = TaggingEnvUtil.getAtomResourceMapper();
		String requestEntryUri = BDBClientUtil.getEntryUri(id);
		BDBRequester<InputStream> bdbRequesterStream = new BDBRequester<>(BDBResponseType.INPUTSTREAM);
		BDBResponseInfo<InputStream> respInfoEntry = bdbRequesterStream.request(
				oldServerEntryUrl, requestEntryUri, Constants.GET, null, atomMapper, serviceName,
				requestInfo, connectionInfo);
		if (respInfoEntry == null) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[maintenanceEntryForEachEntry] Entry does not exist. id=");
			sb.append(id);
			logger.warn(sb.toString());
			return;
		}

		InputStream in = respInfoEntry.data;
		String idUri = TaggingEntryUtil.getUriById(id);
		// 新EntryサーバにEntryを登録
		// InputStreamを直接指定
		String entryServerUrl = BDBClientUtil.getEntryServerUrl(idUri, serviceName,
				requestInfo, connectionInfo);
		bdbRequesterStream.request(entryServerUrl, requestEntryUri, Constants.PUT,
				in, atomMapper, serviceName, requestInfo, connectionInfo);

		// 旧サーバのEntryデータを削除
		bdbRequesterStream.request(oldServerEntryUrl, requestEntryUri, Constants.DELETE, null,
				atomMapper, serviceName,requestInfo, connectionInfo);
	}

	/**
	 * インデックス・全文検索インデックスサーバのデータ移行処理
	 * @param serviceName 対象サービス名
	 * @param indexType インデックスタイプ
	 * @param sourceServerUrl 対象サーバURL
	 * @param newServerUrls 新サーバURLリスト
	 * @param oldConsistentHash 旧サーバURL ConsistentHash
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	void maintenanceIndexServer(String serviceName, BDBIndexType indexType,
			String sourceServerUrl, List<String> newServerUrls,
			ConsistentHash<String> oldConsistentHash, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String namespace = NamespaceUtil.getNamespace(serviceName, requestInfo, connectionInfo);
		BDBRequester<FeedBase> bdbRequester = new BDBRequester<>(BDBResponseType.FEED);
		List<Future<Boolean>> futures = new ArrayList<>();
		FeedTemplateMapper atomMapper = TaggingEnvUtil.getAtomResourceMapper();

		// インデックスのAncestorを全エントリー取得
		// GET /b?_list=DBInnerIndexAncestor または DBFullTextIndexAncestor
		String tableName = null;
		if (BDBIndexType.INDEX.equals(indexType)) {
			tableName = BDBClientServerConst.DB_INDEX_ANCESTOR;
		} else {
			tableName = BDBClientServerConst.DB_FULLTEXT_ANCESTOR;
		}
		String cursorStr = null;
		do {
			String getListUri = getListUri(tableName, cursorStr);
			BDBResponseInfo<FeedBase> responseInfoList = bdbRequester.requestByMigration(
					sourceServerUrl, getListUri, Constants.GET, namespace, atomMapper,
					serviceName, requestInfo, connectionInfo);
			FeedBase listFeed = responseInfoList.data;
			cursorStr = TaggingEntryUtil.getCursorFromFeed(listFeed);
			if (TaggingEntryUtil.isExistData(listFeed)) {
				for (EntryBase entry : listFeed.entry) {
					String idUri = entry.title;
					// データ移行の必要があるかどうかも別スレッドを立ててチェックする。
					MaintenanceIndexForEachEntryCallable callable =
							new MaintenanceIndexForEachEntryCallable(serviceName, idUri,
									indexType, sourceServerUrl, newServerUrls, oldConsistentHash);
					Future<Boolean> future = MigrateUtil.addTask(callable, auth,
							requestInfo, connectionInfo);
					futures.add(future);
				}
			}

		} while (!StringUtils.isBlank(cursorStr));

		// 処理の終了を確認して返す（結果は見ない）
		int waitMillis = TaggingEnvUtil.getSystemPropInt(
				BDBClientConst.BULKPUT_SYNC_WAITMILLIS,
				BDBClientConst.BULKPUT_SYNC_WAITMILLIS_DEFAULT);
		for (Future<Boolean> future : futures) {
			while (!future.isDone()) {
				RetryUtil.sleep(waitMillis);
			}
		}
	}

	/**
	 * インデックスサーバ追加・削除に伴うデータ移行 1エントリーごとの処理
	 * @param serviceName 対象サービス名
	 * @param idUri ID URI
	 * @param indexType インデックスタイプ
	 * @param sourceServerUrl 対象サーバURL
	 * @param newServerUrls 新サーバURLリスト
	 * @param oldConsistentHash 旧サーバConsistentHash
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	void maintenanceIndexForEachEntry(String serviceName, String idUri, BDBIndexType indexType,
			String sourceServerUrl, List<String> newServerUrls,
			ConsistentHash<String> oldConsistentHash,
			ReflexAuthentication auth, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// ManifestサーバからIDを取得
		String mnfUriStr = BDBClientUtil.getGetIdByManifestUri(idUri);
		BDBRequester<FeedBase> bdbRequesterFeed = new BDBRequester<>(BDBResponseType.FEED);
		BDBResponseInfo<FeedBase> respMnf = bdbRequesterFeed.requestToManifest(
				mnfUriStr, Constants.GET, null, serviceName, requestInfo, connectionInfo);
		FeedBase mnfFeed = respMnf.data;
		String id = null;
		if (mnfFeed != null && mnfFeed.link != null && !mnfFeed.link.isEmpty()) {
			id = mnfFeed.link.get(0)._$title;
		}

		// EntryサーバからEntryを取得
		String entryServerUrl = BDBClientUtil.getEntryServerUrl(idUri, serviceName,
				requestInfo, connectionInfo);
		String requestEntryUri = BDBClientUtil.getEntryUri(id);
		FeedTemplateMapper atomMapper = TaggingEnvUtil.getAtomResourceMapper();
		BDBRequester<EntryBase> bdbRequesterEntry = new BDBRequester<>(BDBResponseType.ENTRY);
		BDBResponseInfo<EntryBase> respInfoEntry = bdbRequesterEntry.request(
				entryServerUrl, requestEntryUri, Constants.GET, null, atomMapper,
				serviceName, requestInfo, connectionInfo);
		if (respInfoEntry == null) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[maintenanceIndexForEachEntry] Entry does not exist. id=");
			sb.append(id);
			logger.warn(sb.toString());
			return;
		}

		EntryBase entry = respInfoEntry.data;

		// インデックス生成
		List<EntryBase> indexInfos = null;
		if (BDBIndexType.INDEX.equals(indexType)) {
			InnerIndexManager indexManager = new InnerIndexManager();
			indexInfos = indexManager.createInnerIndexInfos(entry, false,
					serviceName, requestInfo);
		} else {	// FULLTEXT
			FullTextSearchManager ftManager = new FullTextSearchManager();
			indexInfos = ftManager.createFullTextIndexInfos(entry, false,
					serviceName, requestInfo);
		}

		// 現在対象サーバにあるインデックスのうち、サーバが変更されたものを新サーバに登録し、旧サーバから削除する。

		// 旧サーバリストによる振り分け キーはQueryStringを含む。
		IndexCommonManager indexCommonManager = new IndexCommonManager();
		boolean isPartial = true;
		sourceServerUrl = indexCommonManager.addPutParam(sourceServerUrl, isPartial, false);

		BDBServerType serverType = BDBRequesterUtil.getServerType(indexType);
		Map<String, List<EntryBase>> oldIndexInfoMap =
				indexCommonManager.getIndexesForEachServer(indexInfos, isPartial,
						oldConsistentHash, serviceName, requestInfo, connectionInfo);
		// 新サーバリストによる振り分け キーはQueryStringを含む。
		ConsistentHash<String> newConsistentHash = BDBRequesterUtil.getConsistentHash(serverType,
				newServerUrls, serviceName, connectionInfo);
		Map<String, List<EntryBase>> newIndexInfoMap =
				indexCommonManager.getIndexesForEachServer(indexInfos, isPartial,
						newConsistentHash, serviceName, requestInfo, connectionInfo);
		// 対象サーバ分を抽出
		List<EntryBase> currentIndexInfos = oldIndexInfoMap.get(sourceServerUrl);
		List<EntryBase> newIndexInfos = newIndexInfoMap.get(sourceServerUrl);

		// 新・旧サーバで異なるデータがあるかどうかチェック
		List<EntryBase> changeList = new ArrayList<>();
		List<EntryBase> changeListWithDistkey = new ArrayList<>();
		for (EntryBase currentIndexInfo : currentIndexInfos) {
			EntryBase newIndexInfo = getMatchIndexInfo(currentIndexInfo, newIndexInfos);
			if (newIndexInfo == null) {
				// 別サーバに移動対象
				changeList.add(currentIndexInfo);
			} else {
				// 移動対象でないか、DISTKEY指定の場合一部移動対象
				// DISTKEYをチェック
				List<Category> distkeys = currentIndexInfo.getCategory();
				if (distkeys != null && !distkeys.isEmpty()) {
					List<Category> newDistkeys = newIndexInfo.getCategory();
					if (newDistkeys == null || newDistkeys.isEmpty()) {
						changeListWithDistkey.add(currentIndexInfo);
					} else {
						boolean isChange = false;
						for (Category distkey : distkeys) {
							// category schemeにDISTKEY
							boolean isMatch = false;
							for (Category newDistkey : newDistkeys) {
								if (distkey._$scheme.equals(newDistkey._$scheme)) {
									isMatch = true;
									break;
								}
							}
							if (!isMatch) {
								isChange = true;
								break;
							}
						}
						if (isChange) {
							// DISTKEY指定で、インデックスの一部がサーバ移行、一部は元サーバに残る。
							changeListWithDistkey.add(currentIndexInfo);
						}
					}
				}
			}
		}

		// changeList と changeListWithDistkey にデータがあれば移行処理を行う。
		// Map<String, List<EntryBase>> newIndexInfoMap から対象サーバを抽出する。
		Map<String, List<EntryBase>> putIndexInfoMap = new HashMap<>();
		List<EntryBase> deleteIndexInfos = new ArrayList<>();	// 自サーバのみ
		deleteIndexInfos.addAll(changeList);
		// DISTKEYなしのサーバ変更インデックス
		for (EntryBase changeIndexInfo : changeList) {
			for (Map.Entry<String, List<EntryBase>> mapEntry : newIndexInfoMap.entrySet()) {
				String serverUrl = mapEntry.getKey();
				if (sourceServerUrl.equals(serverUrl)) {
					continue;
				}
				List<EntryBase> tmpNewIndexInfos = mapEntry.getValue();
				EntryBase tmpNewIndexInfo = getMatchIndexInfo(changeIndexInfo, tmpNewIndexInfos);
				if (tmpNewIndexInfo != null) {
					// 一致するインデックスがあれば、このサーバが登録先
					List<EntryBase> putIndexInfos = putIndexInfoMap.get(serverUrl);
					if (putIndexInfos == null) {
						putIndexInfos = new ArrayList<>();
						putIndexInfoMap.put(serverUrl, putIndexInfos);
					}
					putIndexInfos.add(changeIndexInfo);	// 配列項目の場合を考慮
				}
			}
		}
		// DISTKEYありのサーバ変更インデックス
		for (EntryBase changeIndexInfo : changeListWithDistkey) {
			for (Map.Entry<String, List<EntryBase>> mapEntry : newIndexInfoMap.entrySet()) {
				String serverUrl = mapEntry.getKey();
				if (sourceServerUrl.equals(serverUrl)) {
					continue;
				}
				List<EntryBase> tmpNewIndexInfos = mapEntry.getValue();
				EntryBase tmpNewIndexInfo = getMatchIndexInfo(changeIndexInfo, tmpNewIndexInfos);
				if (tmpNewIndexInfo != null) {
					// 一致するインデックスがあれば、このサーバが登録先
					List<EntryBase> putIndexInfos = putIndexInfoMap.get(serverUrl);
					if (putIndexInfos == null) {
						putIndexInfos = new ArrayList<>();
						putIndexInfoMap.put(serverUrl, putIndexInfos);
					}
					putIndexInfos.add(tmpNewIndexInfo);
				}
			}
		}

		for (Map.Entry<String, List<EntryBase>> mapEntry : putIndexInfoMap.entrySet()) {
			// 新サーバへインデックス登録
			String serverUrl = mapEntry.getKey();
			List<EntryBase> putIndexInfos = mapEntry.getValue();
			FeedBase feed = TaggingEntryUtil.createAtomFeed();
			feed.entry = putIndexInfos;
			indexCommonManager.requestPut(serverUrl, feed, serviceName, requestInfo, connectionInfo);
		}

		if (!changeListWithDistkey.isEmpty()) {
			// 自サーバのインデックス更新
			if (newIndexInfos != null && !newIndexInfos.isEmpty()) {
				changeListWithDistkey.addAll(newIndexInfos);
			}
			FeedBase feed = TaggingEntryUtil.createAtomFeed();
			feed.entry = changeListWithDistkey;
			indexCommonManager.requestPut(sourceServerUrl, feed, serviceName, requestInfo,
					connectionInfo);

		} else if (!changeList.isEmpty()) {
			// 自サーバのインデックス削除
			String deleteUrl = UrlUtil.addParam(sourceServerUrl, RequestParam.PARAM_DELETE, null);
			FeedBase feed = TaggingEntryUtil.createAtomFeed();
			feed.entry = changeList;
			indexCommonManager.requestPut(deleteUrl, feed, serviceName, requestInfo,
					connectionInfo);
		}
		// 全く変更なしの場合もあり
	}

	/**
	 * 採番・カウンタサーバのデータ移行処理
	 * @param serviceName 対象サービス名
	 * @param sourceServerUrl 対象サーバURL
	 * @param newServerUrls 新サーバURLリスト
	 * @param targetServiceAuth 対象サービスの認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	void maintenanceAlServer(String serviceName, String sourceServerUrl,
			List<String> newServerUrls, ReflexAuthentication targetServiceAuth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String namespace = NamespaceUtil.getNamespace(serviceName, requestInfo, connectionInfo);
		BDBRequester<FeedBase> bdbRequester = new BDBRequester<>(BDBResponseType.FEED);
		List<Future<Boolean>> futures = new ArrayList<>();
		FeedTemplateMapper atomMapper = TaggingEnvUtil.getAtomResourceMapper();
		ConsistentHash<String> newConsistentHash = BDBRequesterUtil.getConsistentHash(
				BDBServerType.ALLOCIDS, newServerUrls, serviceName, connectionInfo);

		// 採番
		// GET /b?_list=DBAllocids
		String tableName = BDBClientServerConst.DB_ALLOCIDS;
		String cursorStr = null;
		do {
			String getListUri = getListUri(tableName, cursorStr);
			BDBResponseInfo<FeedBase> responseInfoList = bdbRequester.requestByMigration(
					sourceServerUrl, getListUri, Constants.GET, namespace, atomMapper,
					serviceName, requestInfo, connectionInfo);
			FeedBase listFeed = responseInfoList.data;
			cursorStr = TaggingEntryUtil.getCursorFromFeed(listFeed);
			if (TaggingEntryUtil.isExistData(listFeed)) {
				for (EntryBase entry : listFeed.entry) {
					String uri = entry.title;
					// 新サーバリストで割り当てる
					String newServerUrl = BDBRequesterUtil.assign(newConsistentHash, uri);
					if (!sourceServerUrl.equals(newServerUrl)) {
						// 割り当てサーバが変更された場合、データ移行（新サーバへ登録し、旧サーバから削除）
						MaintenanceAlForEachAllocidsCallable callable =
								new MaintenanceAlForEachAllocidsCallable(serviceName, uri,
										sourceServerUrl);
						Future<Boolean> future = MigrateUtil.addTask(callable, targetServiceAuth,
								requestInfo, connectionInfo);
						futures.add(future);
					}
				}
			}

		} while (!StringUtils.isBlank(cursorStr));

		// カウンタ
		// GET /b?_list=DBIncrement
		tableName = BDBClientServerConst.DB_INCREMENT;
		cursorStr = null;
		do {
			String getListUri = getListUri(tableName, cursorStr);
			BDBResponseInfo<FeedBase> responseInfoList = bdbRequester.requestByMigration(
					sourceServerUrl, getListUri, Constants.GET, namespace, atomMapper,
					serviceName, requestInfo, connectionInfo);
			FeedBase listFeed = responseInfoList.data;
			cursorStr = TaggingEntryUtil.getCursorFromFeed(listFeed);
			if (TaggingEntryUtil.isExistData(listFeed)) {
				for (EntryBase entry : listFeed.entry) {
					String uri = entry.title;
					// 新サーバリストで割り当てる
					String newServerUrl = BDBRequesterUtil.assign(newConsistentHash, uri);
					if (!sourceServerUrl.equals(newServerUrl)) {
						// 割り当てサーバが変更された場合、データ移行（新サーバへ登録し、旧サーバから削除）
						MaintenanceAlForEachIncrementCallable callable =
								new MaintenanceAlForEachIncrementCallable(serviceName, uri,
										sourceServerUrl);
						Future<Boolean> future = MigrateUtil.addTask(callable, targetServiceAuth,
								requestInfo, connectionInfo);
						futures.add(future);
					}
				}
			}

		} while (!StringUtils.isBlank(cursorStr));

		// 処理の終了を確認して返す（結果は見ない）
		int waitMillis = TaggingEnvUtil.getSystemPropInt(
				BDBClientConst.BULKPUT_SYNC_WAITMILLIS,
				BDBClientConst.BULKPUT_SYNC_WAITMILLIS_DEFAULT);
		for (Future<Boolean> future : futures) {
			while (!future.isDone()) {
				RetryUtil.sleep(waitMillis);
			}
		}
	}

	/**
	 * 採番・カウンタサーバ追加・削除に伴うデータ移行 1採番値ごとの処理
	 * @param serviceName 対象サービス名
	 * @param uri URI
	 * @param oldServerAlUrl 旧採番・カウンタサーバURL
	 * @param targetServiceAuth 対象サービスの認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	void maintenanceAlForEachAllocids(String serviceName, String uri,
			String oldServerAlUrl, ReflexAuthentication targetServiceAuth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String namespace = NamespaceUtil.getNamespace(serviceName, requestInfo, connectionInfo);
		MigrateMinimumManager migrateMinManager = new MigrateMinimumManager();
		migrateMinManager.migrateForEachAllocids(serviceName, namespace, uri,
				oldServerAlUrl, targetServiceAuth, requestInfo, connectionInfo);
	}

	/**
	 * 採番・カウンタサーバ追加・削除に伴うデータ移行 1カウンタごとの処理
	 * @param serviceName 対象サービス名
	 * @param uri URI
	 * @param oldServerAlUrl 旧採番・カウンタサーバURL
	 * @param targetServiceAuth 対象サービスの認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	void maintenanceAlForEachIncrement(String serviceName, String uri,
			String oldServerAlUrl, ReflexAuthentication targetServiceAuth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String namespace = NamespaceUtil.getNamespace(serviceName, requestInfo, connectionInfo);
		MigrateMinimumManager migrateMinManager = new MigrateMinimumManager();
		migrateMinManager.migrateForEachIncrement(serviceName, namespace, uri,
				oldServerAlUrl, targetServiceAuth, requestInfo, connectionInfo);
	}

	/**
	 * サービスステータスをチェックし、サービスエントリーを返却する.
	 * @param serviceName 対象サービス名
	 * @param isRemove 削除の場合true
	 * @param systemContext システム管理サービスのSystemContext
	 * @return サービスエントリー
	 */
	private EntryBase checkAndGetServiceEntry(String serviceName, boolean isRemove,
			SystemContext systemContext)
	throws IOException, TaggingException {
		// サービスステータスエントリーを取得
		ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
		String serviceUri = serviceManager.getServiceUri(serviceName);
		EntryBase serviceEntry = systemContext.getEntry(serviceUri, false);

		String myServiceStatus = getServiceStatus(serviceEntry);
		if (!MaintenanceConst.SERVICE_STATUS_TARGET.equals(myServiceStatus) &&
				!MaintenanceConst.SERVICE_STATUS_MAINTENANCE_FAILURE.equals(myServiceStatus)) {
			String msg = null;
			if (isRemove) {
				msg = "It cannot be remove server in this status. ";
			} else {
				msg = "It cannot be add server in this status. ";
			}
			throw new PermissionException(msg + myServiceStatus);
		}
		return serviceEntry;
	}

	/**
	 * サーバタイプからサーバタイプを取得.
	 * @param serverTypeStr サーバタイプ
	 * @return サーバタイプ
	 */
	private BDBServerType getServerType(String serverTypeStr) {
		if (BDBClientServerConst.SERVERTYPE_ENTRY.equals(serverTypeStr)) {
			return BDBServerType.ENTRY;
		} else if (BDBClientServerConst.SERVERTYPE_IDX.equals(serverTypeStr)) {
			return BDBServerType.INDEX;
		} else if (BDBClientServerConst.SERVERTYPE_FT.equals(serverTypeStr)) {
			return BDBServerType.FULLTEXT;
		} else if (BDBClientServerConst.SERVERTYPE_AL.equals(serverTypeStr)) {
			return BDBServerType.ALLOCIDS;
		} else {
			throw new IllegalParameterException("server type is invalid. " + serverTypeStr);
		}
	}

	/**
	 * 現在の対象サービスのサーバ割り当て情報を元にバックアップエントリーを生成する.
	 * @param serviceName 対象サービス
	 * @param serverTypeSet 対象のサーバタイプセット
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return バックアップエントリーリスト
	 */
	private List<EntryBase> createPreviousBackupEntries(String serviceName,
			Set<BDBServerType> serverTypeSet, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String systemService = TaggingEnvUtil.getSystemService();
		List<EntryBase> putEntries = new ArrayList<>();
		// バックアップフォルダを作成
		EntryBase folderEntry = TaggingEntryUtil.createEntry(systemService);
		folderEntry.setMyUri(MigrateUtil.getPreviousBackupUri(serviceName));
		putEntries.add(folderEntry);
		for (BDBServerType serverType : serverTypeSet) {
			// バックアップのサーバタイプフォルダを作成
			folderEntry = TaggingEntryUtil.createEntry(systemService);
			folderEntry.setMyUri(MigrateUtil.getPreviousBackupServerTypeUri(
					serviceName, serverType));
			putEntries.add(folderEntry);
			// 現在のBDBサーバを取得
			//  /_bdb/service/{サービス名}/{entry|idx|ft|al}serverをFeed検索。
			FeedBase currentServersFeed = BDBClientServerUtil.getBDBServerNamesFeed(
					serviceName, serverType, requestInfo, connectionInfo);
			// 現在のサーバを以下のEntryにバックアップする。
			// /_bdb/service/{サービス名}/previous_backup/{entry|idx|ft|al}server/{サーバ名}
			if (currentServersFeed != null && currentServersFeed.entry != null) {
				for (EntryBase currentServerEntry : currentServersFeed.entry) {
					String serverName = TaggingEntryUtil.getSelfidUri(
							currentServerEntry.getMyUri());
					currentServerEntry.setMyUri(
							MigrateUtil.getPreviousBackupServerUri(
									serviceName, serverType, serverName));
					currentServerEntry.id = null;
					putEntries.add(currentServerEntry);
				}
			}
		}
		return putEntries;
	}

	/**
	 * listオプションで全エントリーを取得するURIを取得
	 * @param tableName テーブル名
	 * @param cursorStr カーソル
	 * @return listオプションで全エントリーを取得するURI
	 */
	private String getListUri(String tableName, String cursorStr) {
		// GET /b?_list={tableName}
		return BDBClientUtil.getListUri(tableName, null, cursorStr);
	}

	/**
	 * サーバ名リストをサーバURLリストに変換する.
	 * @param serverType サーバタイプ
	 * @param servers サーバ名リスト
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return サーバURLリスト
	 */
	private List<String> getServerUrlsByServerNames(BDBServerType serverType,
			List<String> servers, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		List<String> serverUrls = new ArrayList<>();
		for (String serverName : servers) {
			String serverUrl = getServerUrlByServerName(serverType, serverName,
					requestInfo, connectionInfo);
			serverUrls.add(serverUrl);
		}
		return serverUrls;
	}

	/**
	 * サーバ名からサーバURLを取得する.
	 * @param serverType サーバタイプ
	 * @param serverName サーバ名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return サーバURL
	 */
	private String getServerUrlByServerName(BDBServerType serverType, String serverName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serverUrl = null;
		if (BDBServerType.ENTRY.equals(serverType)) {
			serverUrl = BDBRequesterUtil.getEntryServerUrlByServerName(serverName,
					requestInfo, connectionInfo);
		} else if (BDBServerType.INDEX.equals(serverType)) {
			serverUrl = BDBRequesterUtil.getIdxServerUrlByServerName(serverName,
					requestInfo, connectionInfo);
		} else if (BDBServerType.FULLTEXT.equals(serverType)) {
			serverUrl = BDBRequesterUtil.getFtServerUrlByServerName(serverName,
					requestInfo, connectionInfo);
		} else if (BDBServerType.ALLOCIDS.equals(serverType)) {
			serverUrl = BDBRequesterUtil.getAlServerUrlByServerName(serverName,
					requestInfo, connectionInfo);
		} else {
			String msg = "BDBServerType is not valid. " + serverType;
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[getServerUrls] ");
			sb.append(msg);
			logger.warn(sb.toString());
			throw new IllegalStateException(msg);
		}
		return serverUrl;
	}

	/**
	 * 現在のインデックス情報が、対象インデックスリストに含まれるかどうかチェックし、
	 * 含まれる場合インデックス情報を返す.
	 * @param sourceIndexInfo 現在のインデックス情報
	 * @param targetIndexInfos 対象インデックスリスト
	 * @return 現在のインデックス情報が、対象インデックスリストに含まれる場合、インデックス情報。
	 *         含まれない場合null。
	 *         インデックス情報を返す場合も、DISTKEYリスト内容が異なる場合がある。
	 */
	private EntryBase getMatchIndexInfo(EntryBase sourceIndexInfo, List<EntryBase> targetIndexInfos) {
		String uri = sourceIndexInfo.getMyUri();	// link rel="self"のhrefにselfまたはalias
		String itemName = sourceIndexInfo.title;	// titleに項目名
		// DISTKEY指定のみの場合はインデックス情報を返す。
		if (StringUtils.isBlank(itemName)) {
			return sourceIndexInfo;
		}
		// URIと項目名で判定
		if (targetIndexInfos != null) {
			for (EntryBase newIndexInfo : targetIndexInfos) {
				String newIndexUri = newIndexInfo.getMyUri();
				String newIndexItemName = newIndexInfo.title;
				if (uri.equals(newIndexUri) && itemName.equals(newIndexItemName)) {
					return sourceIndexInfo;
				}
			}
		}
		return null;
	}

	/**
	 * サービスステータスの更新.
	 * サービスエントリーにリビジョンが設定されている場合、楽観的排他制御を行う。
	 * @param serviceEntry サービスエントリー
	 * @param serviceStatus サービスステータス
	 * @param progress BDBサーバ追加・削除処理の進捗区分
	 * @param systemContext SystemContext
	 * @return 更新したEntry
	 */
	private EntryBase updateServiceStatus(EntryBase serviceEntry, String serviceStatus,
			String progress, SystemContext systemContext)
	throws IOException, TaggingException {
		serviceEntry.subtitle = serviceStatus;
		setMaintenanceProgress(serviceEntry, progress);
		EntryBase retEntry = systemContext.put(serviceEntry);
		return retEntry;
	}

	/**
	 * サービスステータス変更によるデータ移行処理の失敗時、設定を元に戻す処理.
	 * @param serviceName サービス名
	 * @param serviceEntry サービスエントリー (サービスステータス更新に使用)
	 * @param isChangedServiceStatus サービスステータスをメンテナンス中に更新したかどうか
	 * @param systemContext システム管理サービスのSystemContext
	 */
	private void failedMaintenanceMigrate(String serviceName, EntryBase serviceEntry,
			boolean isChangedServiceStatus, SystemContext systemContext)
	throws IOException, TaggingException {
		RequestInfo requestInfo = systemContext.getRequestInfo();
		try {
			// * ログエントリー出力
			String logMsg = "Migration for adding or removing server failed. " + serviceName;
			systemContext.log(LOG_TITLE, Constants.ERROR, logMsg);
		} catch (RuntimeException | Error e) {
			// ログエントリー出力でエラーの場合、例外をスローしない。
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[failedMaintenanceMigrate] Error occured by writeLogEntry. ");
			logger.warn(sb.toString(), e);
		}

		if (isChangedServiceStatus) {
			try {
				// * サービスステータスを「失敗」に更新する
				serviceEntry.id = null;
				serviceEntry = updateServiceStatus(serviceEntry,
						MaintenanceConst.SERVICE_STATUS_MAINTENANCE_FAILURE,
						getMaintenanceProgress(serviceEntry),
						systemContext);
			} catch (IOException | TaggingException | RuntimeException | Error e) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[failedMaintenanceMigrate] Error occured by updateServiceStatus. ");
				logger.warn(sb.toString(), e);

				try {
					// * ログエントリー出力
					sb.append(e.getClass().getName());
					sb.append(": ");
					sb.append(e.getMessage());
					String logMsg = sb.toString();
					systemContext.log(LOG_TITLE, Constants.ERROR, logMsg);
				} catch (RuntimeException | Error re) {
					// ログエントリー出力でエラーの場合、例外をスローしない。
					StringBuilder rsb = new StringBuilder();
					rsb.append(LogUtil.getRequestInfoStr(requestInfo));
					rsb.append("[failedMaintenanceMigrate] Error occured by writeLogEntry. ");
					logger.warn(rsb.toString(), e);
				}
			}
		}
	}

	/**
	 * サービスステータスを取得.
	 * @param serviceEntry サービスエントリー
	 * @return サービスステータス
	 */
	private String getServiceStatus(EntryBase serviceEntry) {
		return TaggingServiceUtil.getServiceStatus(serviceEntry);
	}

	/**
	 * BDBサーバ追加・削除処理の進捗区分を取得.
	 * @param serviceEntry サービスエントリー
	 * @return BDBサーバ追加・削除処理の進捗区分
	 */
	private String getMaintenanceProgress(EntryBase serviceEntry) {
		return serviceEntry.summary;
	}

	/**
	 * BDBサーバ追加・削除処理の進捗区分を設定.
	 * @param serviceEntry サービスエントリー
	 * @param progress BDBサーバ追加・削除処理の進捗区分
	 */
	private void setMaintenanceProgress(EntryBase serviceEntry, String progress) {
		serviceEntry.summary = progress;
	}

	/**
	 * リトライ処理用　バックアップサーバリストを取得.
	 * @param serviceName サービス名
	 * @param serverType サーバタイプ
	 * @param systemContext システム管理サービスのSystemContext
	 * @return previous_backupに登録されたサーバリスト
	 */
	private List<String> getBackupServers(String serviceName, BDBServerType serverType,
			SystemContext systemContext)
	throws IOException, TaggingException {
		// /_bdb/service/{サービス名}/previous_backup/**server をFeed検索
		String parentUri = MigrateUtil.getPreviousBackupServerTypeUri(serviceName, serverType);
		FeedBase serversFeed = systemContext.getFeed(parentUri);
		BDBClientServiceManager bdbClientServiceManager = new BDBClientServiceManager();
		return bdbClientServiceManager.getSelfidList(serversFeed);
	}

}
