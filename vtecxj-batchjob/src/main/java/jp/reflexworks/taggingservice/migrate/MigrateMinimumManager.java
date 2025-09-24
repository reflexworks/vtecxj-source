package jp.reflexworks.taggingservice.migrate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.entry.Link;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.bdbclient.BDBClientAllocateIdsManager;
import jp.reflexworks.taggingservice.bdbclient.BDBClientConst;
import jp.reflexworks.taggingservice.bdbclient.BDBClientIncrementManager;
import jp.reflexworks.taggingservice.bdbclient.BDBClientUtil;
import jp.reflexworks.taggingservice.bdbclient.BDBResponseInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.index.FullTextIndexPutCallable;
import jp.reflexworks.taggingservice.index.InnerIndexPutCallable;
import jp.reflexworks.taggingservice.model.UpdatedInfo;
import jp.reflexworks.taggingservice.plugin.NamespaceManager;
import jp.reflexworks.taggingservice.plugin.ServiceManager;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBResponseType;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBServerType;
import jp.reflexworks.taggingservice.requester.BDBClientServerManager;
import jp.reflexworks.taggingservice.requester.BDBClientServerUtil;
import jp.reflexworks.taggingservice.requester.BDBRequester;
import jp.reflexworks.taggingservice.requester.BDBRequesterUtil;
import jp.reflexworks.taggingservice.service.TaggingServiceUtil;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.Constants.OperationType;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.RetryUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.ConsistentHash;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * サービスステータス更新時の移行処理管理クラス.
 * システムエントリー(/と/_から始まるEntry)のみ、全サーバ移行。
 */
public class MigrateMinimumManager {

	/** エラー時のログエントリーtitle */
	private static final String LOG_TITLE = "MigrateMinimum";

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * サービスステータス変更に伴うBDBサーバ変更.
	 *  ・名前空間の変更
	 *  ・BDBサーバの割り当て直し
	 *  ・一部データの移行
	 * @param serviceName 対象サービス名
	 * @param serviceStatus 新サービスステータス
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void changeServiceStatus(String serviceName, String serviceStatus,
			ReflexAuthentication auth, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		if (logger.isDebugEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "changeServiceStatus start. newServiceStatus=" + serviceStatus);
		}

		// システム管理サービスのSystemContextを作成
		String systemService = auth.getServiceName();
		SystemContext systemContext = new SystemContext(systemService, requestInfo, connectionInfo);

		// 指定サービスが存在しなければエラー
		String uri = TaggingServiceUtil.getServiceUri(serviceName);
		EntryBase entry = systemContext.getEntry(uri, false);
		String currentServiceStatus = TaggingServiceUtil.getServiceStatus(entry);
		if (StringUtils.isBlank(currentServiceStatus)) {
			throw new IllegalParameterException("The service does not exist. " + serviceName);
		}

		String expectedServiceStatus = null;
		if (Constants.SERVICE_STATUS_PRODUCTION.equals(serviceStatus)) {
			expectedServiceStatus = Constants.SERVICE_STATUS_STAGING;
		} else {	// STAGING
			expectedServiceStatus = Constants.SERVICE_STATUS_PRODUCTION;
		}
		// 指定サービスが期待するステータスでなければエラー
		if (!expectedServiceStatus.equals(currentServiceStatus)) {
			StringBuilder sb = new StringBuilder();
			sb.append("The service status is not '");
			sb.append(expectedServiceStatus);
			sb.append("'. serviceStatus = ");
			sb.append(currentServiceStatus);
			sb.append(", target serviceName = ");
			sb.append(serviceName);
			throw new IllegalParameterException(sb.toString());
		}
		
		// バッチジョブサーバのシャットダウン対応のため、ステータス更新は非同期処理内で行う。

		// 以降の処理を非同期に行う。
		MigrateMinimumCallable callable = new MigrateMinimumCallable(serviceName,
				serviceStatus, entry);
		callable.addTask(auth, requestInfo, connectionInfo);

		if (logger.isDebugEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "changeServiceStatus accepted. newServiceStatus=" + serviceStatus);
		}
	}

	/**
	 * エントリーに指定されたサービスステータスを設定する.
	 * @param entry エントリー
	 * @param status サービスステータス
	 */
	private void setServiceStatus(EntryBase entry, String status) {
		entry.subtitle = status;
	}


	/**
	 * サービスステータス変更に伴うBDBサーバ変更.
	 *  ・名前空間の変更
	 *  ・BDBサーバの割り当て直し
	 *  ・一部データの移行
	 * @param serviceName 対象サービス名
	 * @param serviceStatus 新サービスステータス
	 * @param serviceEntry サービスエントリー
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void assignAndMigrateMinimum(String serviceName, String serviceStatus,
			EntryBase serviceEntry, ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String systemService = TaggingEnvUtil.getSystemService();
		SystemContext systemContext = new SystemContext(systemService, requestInfo, connectionInfo);
		// 失敗時のため、旧設定は変数に保持
		String currentNamespace = null;
		List<EntryBase> mnfCurrentServerEntries = null;
		List<EntryBase> entryCurrentServerEntries = null;
		List<EntryBase> idxCurrentServerEntries = null;
		List<EntryBase> ftCurrentServerEntries = null;
		List<EntryBase> alCurrentServerEntries = null;
		try {
			// ステータス更新
			// 更新中のステータスにする。
			String tmpStatus = null;
			if (Constants.SERVICE_STATUS_PRODUCTION.equals(serviceStatus)) {
				tmpStatus = Constants.SERVICE_STATUS_TOPRODUCTION;
			} else {	// staging
				tmpStatus = Constants.SERVICE_STATUS_TOSTAGING;
			}
			setServiceStatus(serviceEntry, tmpStatus);	// 楽観的排他チェックを行うので、IDは残す。
			systemContext.put(serviceEntry);

			// サービスの設定
			settingService(serviceName, requestInfo, connectionInfo);

			NamespaceManager namespaceManager = TaggingEnvUtil.getNamespaceManager();
			// 現在の名前空間を取得
			currentNamespace = namespaceManager.getNamespace(serviceName, requestInfo,
					connectionInfo);

			List<EntryBase> putEntries = new ArrayList<>();

			// 名前空間の更新
			EntryBase namespaceEntry = namespaceManager.createChangeNamespaceEntry(serviceName,
					requestInfo, connectionInfo);
			putEntries.add(namespaceEntry);
			String newNamespace = namespaceEntry.title;

			BDBClientServiceManager bdbClientServiceManager = new BDBClientServiceManager();
			// Manifestサーバ割り当て : /_bdb/service/{サービス名}/mnfserver/{Manifestサーバ名}
			int num = getServerNum(serviceStatus, BDBServerType.MANIFEST);
			String serviceTypeUri = Constants.URI_MNFSERVER;
			List<EntryBase> mnfNewServerEntries = bdbClientServiceManager.createAssignServerEntry(
					serviceName, serviceStatus, serviceTypeUri, num, BDBServerType.MANIFEST,
					systemContext);
			mnfCurrentServerEntries = getCurrentServers(serviceName, serviceTypeUri,
					systemContext);
			List<EntryBase> updServerEntries = mergeAssignServerEntries(mnfNewServerEntries,
					mnfCurrentServerEntries);
			putEntries.addAll(updServerEntries);

			// Entryサーバ割り当て : /_bdb/service/{サービス名}/entryserver/{Entryサーバ名}
			num = getServerNum(serviceStatus, BDBServerType.ENTRY);
			serviceTypeUri = Constants.URI_ENTRYSERVER;
			List<EntryBase> entryNewServerEntries = bdbClientServiceManager.createAssignServerEntry(
					serviceName, serviceStatus, serviceTypeUri, num, BDBServerType.ENTRY,
					systemContext);
			entryCurrentServerEntries = getCurrentServers(serviceName, serviceTypeUri,
					systemContext);
			updServerEntries = mergeAssignServerEntries(entryNewServerEntries, entryCurrentServerEntries);
			putEntries.addAll(updServerEntries);

			// インデックスサーバ割り当て : /_bdb/service/{サービス名}/idxserver/{インデックスサーバ名}
			num = getServerNum(serviceStatus, BDBServerType.INDEX);
			serviceTypeUri = Constants.URI_IDXSERVER;
			List<EntryBase> idxNewServerEntries = bdbClientServiceManager.createAssignServerEntry(
					serviceName, serviceStatus, serviceTypeUri, num, BDBServerType.INDEX,
					systemContext);
			idxCurrentServerEntries = getCurrentServers(serviceName, serviceTypeUri,
					systemContext);
			updServerEntries = mergeAssignServerEntries(idxNewServerEntries, idxCurrentServerEntries);
			putEntries.addAll(updServerEntries);

			// 全文検索インデックスサーバ割り当て : /_bdb/service/{サービス名}/ftserver/{全文検索インデックスサーバ名}
			num = getServerNum(serviceStatus, BDBServerType.FULLTEXT);
			serviceTypeUri = Constants.URI_FTSERVER;
			List<EntryBase> ftNewServerEntries = bdbClientServiceManager.createAssignServerEntry(
					serviceName, serviceStatus, serviceTypeUri, num, BDBServerType.FULLTEXT,
					systemContext);
			ftCurrentServerEntries = getCurrentServers(serviceName, serviceTypeUri,
					systemContext);
			updServerEntries = mergeAssignServerEntries(ftNewServerEntries, ftCurrentServerEntries);
			putEntries.addAll(updServerEntries);

			// 採番・カウンタサーバ割り当て : /_bdb/service/{サービス名}/alserver/{採番・カウンタサーバ名}
			num = getServerNum(serviceStatus, BDBServerType.ALLOCIDS);
			serviceTypeUri = Constants.URI_ALSERVER;
			List<EntryBase> alNewServerEntries = bdbClientServiceManager.createAssignServerEntry(
					serviceName, serviceStatus, serviceTypeUri, num, BDBServerType.ALLOCIDS,
					systemContext);
			alCurrentServerEntries = getCurrentServers(serviceName, serviceTypeUri,
					systemContext);
			updServerEntries = mergeAssignServerEntries(alNewServerEntries, alCurrentServerEntries);
			putEntries.addAll(updServerEntries);

			// 旧サーバのバックアップ情報も更新する
			List<EntryBase> backupEntries = createPrevBackupEntries(currentNamespace,
					mnfCurrentServerEntries, entryCurrentServerEntries, idxCurrentServerEntries,
					ftCurrentServerEntries, alCurrentServerEntries, serviceName,
					connectionInfo);
			putEntries.addAll(backupEntries);

			// 更新前に、BDBバックアップを行う
			String datetimeStr = BackupForMigrateUtil.getDatetimeStr();
			BackupForMigrateUtil.backupBDB(datetimeStr, currentNamespace, serviceName,
					auth, requestInfo, connectionInfo);

			// サーバ割り当て情報の更新
			FeedBase putFeed = TaggingEntryUtil.createFeed(systemService);
			putFeed.entry = putEntries;
			systemContext.put(putFeed);

			// staticに保持するサーバ情報の更新
			BDBClientServerManager clientServerManager = new BDBClientServerManager();
			List<String> mnfNewServerNames = BDBClientServerUtil.getServerNames(mnfNewServerEntries);
			clientServerManager.changeStaticServerNames(serviceName, BDBServerType.MANIFEST,
					mnfNewServerNames);
			List<String> entryNewServerNames = BDBClientServerUtil.getServerNames(entryNewServerEntries);
			clientServerManager.changeStaticServerNames(serviceName, BDBServerType.ENTRY,
					entryNewServerNames);
			List<String> idxNewServerNames = BDBClientServerUtil.getServerNames(idxNewServerEntries);
			clientServerManager.changeStaticServerNames(serviceName, BDBServerType.INDEX,
					idxNewServerNames);
			List<String> ftNewServerNames = BDBClientServerUtil.getServerNames(ftNewServerEntries);
			clientServerManager.changeStaticServerNames(serviceName, BDBServerType.FULLTEXT,
					ftNewServerNames);
			List<String> alNewServerNames = BDBClientServerUtil.getServerNames(alNewServerEntries);
			clientServerManager.changeStaticServerNames(serviceName, BDBServerType.ALLOCIDS,
					alNewServerNames);

			// staticに保持する名前空間の更新
			namespaceManager.setStaticNamespace(serviceName, newNamespace);

			// ConsistentHashの更新
			// サーバURLを取得する。
			String mnfNewServerUrl = BDBRequesterUtil.getMnfServerUrl(serviceName,
					requestInfo, connectionInfo);
			List<String> mnfNewServerUrls = new ArrayList<>();
			mnfNewServerUrls.add(mnfNewServerUrl);
			BDBRequesterUtil.changeServerList(BDBServerType.MANIFEST, mnfNewServerUrls,
					serviceName, connectionInfo);
			List<String> entryNewServerUrls = BDBRequesterUtil.getEntryServerUrls(serviceName,
					requestInfo, connectionInfo);
			BDBRequesterUtil.changeServerList(BDBServerType.ENTRY, entryNewServerUrls,
					serviceName, connectionInfo);
			List<String> idxNewServerUrls = BDBRequesterUtil.getIdxServerUrls(serviceName,
					requestInfo, connectionInfo);
			BDBRequesterUtil.changeServerList(BDBServerType.INDEX, idxNewServerUrls,
					serviceName, connectionInfo);
			List<String> ftNewServerUrls = BDBRequesterUtil.getFtServerUrls(serviceName,
					requestInfo, connectionInfo);
			BDBRequesterUtil.changeServerList(BDBServerType.FULLTEXT, ftNewServerUrls,
					serviceName, connectionInfo);
			List<String> alNewServerUrls = BDBRequesterUtil.getAlServerUrls(serviceName,
					requestInfo, connectionInfo);
			BDBRequesterUtil.changeServerList(BDBServerType.ALLOCIDS, alNewServerUrls,
					serviceName, connectionInfo);

			// `/`と`/_`から始まるEntryを旧サーバから新サーバに移行。
			// 名前空間を変更しているため、同じBDBサーバが割り当てられたとしてもBDB登録先が異なる。
			migrateByChangeServiceStatus(serviceName, newNamespace, currentNamespace,
					mnfCurrentServerEntries, entryCurrentServerEntries, alCurrentServerEntries,
					auth, requestInfo, connectionInfo);

			// サービスステータスを更新する
			String uri = TaggingServiceUtil.getServiceUri(serviceName);
			EntryBase serviceStatusEntry = systemContext.getEntry(uri, false);
			setServiceStatus(serviceStatusEntry, serviceStatus);
			serviceStatusEntry.id = null;
			systemContext.put(serviceStatusEntry);

			// 旧サーバ情報を削除する。
			// /_bdb/service/{サービス名}/previous_backupをフォルダ削除する。
			String prevBackupUri = MigrateUtil.getPreviousBackupUri(serviceName);
			boolean async = false;
			boolean isParallel = false;
			systemContext.deleteFolder(prevBackupUri, async, isParallel);

		} catch (IOException | TaggingException | RuntimeException | Error e) {
			// エラーの場合
			// * ログエントリー出力
			// * 名前空間を元に戻す
			// * 各割り当てBDBサーバを元に戻す
			// * サービスステータスを元に戻す
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[assignAndMigrateMinimum] Error occured.");
			logger.error(sb.toString(), e);

			failedAssignAndMigrateMinimum(serviceName, serviceStatus, currentNamespace,
					mnfCurrentServerEntries, entryCurrentServerEntries, idxCurrentServerEntries,
					ftCurrentServerEntries, alCurrentServerEntries, systemContext);

			throw e;
		}
	}

	/**
	 * サーバ割り当て数を取得
	 * @param serviceStatus サービスステータス
	 * @param serverType サーバタイプ
	 * @return サーバ割り当て数
	 */
	private int getServerNum(String serviceStatus, BDBServerType serverType) {
		// productionでない場合1を返す
		if (!Constants.SERVICE_STATUS_PRODUCTION.equals(serviceStatus)) {
			return 1;
		}
		// productionの場合
		if (BDBServerType.MANIFEST.equals(serverType)) {
			return 1;
		} else {
			String propKey = null;
			int defVal = 0;
			if (BDBServerType.ENTRY.equals(serverType)) {
				propKey = BDBClientServerConst.BDBSERVER_NUM_ENTRY;
				defVal = BDBClientServerConst.BDBSERVER_NUM_ENTRY_DEFAULT;
			} else if (BDBServerType.INDEX.equals(serverType)) {
				propKey = BDBClientServerConst.BDBSERVER_NUM_INDEX;
				defVal = BDBClientServerConst.BDBSERVER_NUM_INDEX_DEFAULT;
			} else if (BDBServerType.FULLTEXT.equals(serverType)) {
				propKey = BDBClientServerConst.BDBSERVER_NUM_FULLTEXT;
				defVal = BDBClientServerConst.BDBSERVER_NUM_FULLTEXT_DEFAULT;
			} else {	// allocids
				propKey = BDBClientServerConst.BDBSERVER_NUM_ALLOCIDS;
				defVal = BDBClientServerConst.BDBSERVER_NUM_ALLOCIDS_DEFAULT;
			}
			if (defVal < 1) {
				defVal = 1;
			}
			return TaggingEnvUtil.getSystemPropInt(propKey, defVal);
		}
	}

	/**
	 * 現在の割り当てサーバを取得
	 * @param serviceName 対象サービス名
	 * @param serverTypeUri サーバタイプURI
	 * @param systemContext SystemContext
	 * @return 現在の割り当てサーバリスト
	 */
	private List<EntryBase> getCurrentServers(String serviceName, String serverTypeUri,
			SystemContext systemContext)
	throws IOException, TaggingException {
		String serviceServerUri = BDBClientServerUtil.getServiceServerUri(serviceName,
				serverTypeUri);
		FeedBase feed = systemContext.getFeed(serviceServerUri);
		if (feed == null) {
			return null;
		}
		return feed.entry;
	}

	/**
	 * 新Entryリスト、旧Entryリストを比較し、更新対象のみ抽出して返却する.
	 * @param newServerEntries 新Entryリスト
	 * @param currentServerEntries 旧Entryリスト
	 * @return 更新対象Entryリスト
	 */
	private List<EntryBase> mergeAssignServerEntries(List<EntryBase> newServerEntries,
			List<EntryBase> currentServerEntries) {
		List<EntryBase> updateEntries = new ArrayList<>();
		// 新規サーバが既存サーバに含まれるかチェック
		if (newServerEntries != null) {
			for (EntryBase newServerEntry : newServerEntries) {
				String newServerUri = newServerEntry.getMyUri();
				boolean registered = false;
				if (currentServerEntries != null) {
					for (EntryBase currentServerEntry : currentServerEntries) {
						if (newServerUri.equals(currentServerEntry.getMyUri())) {
							registered = true;
							break;
						}
					}
				}
				if (!registered) {
					updateEntries.add(newServerEntry);
				}
			}
		}
		// 現在のサーバが新規サーバに含まれるかチェック
		if (currentServerEntries != null) {
			for (EntryBase currentServerEntry : currentServerEntries) {
				String currentServerUri = currentServerEntry.getMyUri();
				boolean isNew = false;
				if (newServerEntries != null) {
					for (EntryBase newServerEntry : newServerEntries) {
						if (currentServerUri.equals(newServerEntry.getMyUri())) {
							isNew = true;
							break;
						}
					}
				}
				if (!isNew) {
					StringBuilder sb = new StringBuilder();
					sb.append(currentServerEntry.id);
					sb.append("?");
					sb.append(RequestParam.PARAM_DELETE);
					currentServerEntry.id = sb.toString();
					updateEntries.add(currentServerEntry);
				}
			}
		}
		return updateEntries;
	}

	/**
	 * 旧サーバ情報のバックアップEntryリストを生成
	 * @param currentNamespace 旧名前空間
	 * @param mnfServers 旧Manifestサーバ情報
	 * @param entryServers 旧Entryサーバ情報
	 * @param idxServers 旧インデックスサーバ情報
	 * @param ftServers 旧全文検索インデックスサーバ情報
	 * @param alServers 旧採番・カウンタサーバ情報
	 * @param serviceName 対象サービス名
	 * @param connectionInfo コネクション情報 (ResourceMapper取得に使用)
	 * @return 旧サーバ情報のバックアップEntryリスト
	 */
	private List<EntryBase> createPrevBackupEntries(String currentNamespace,
			List<EntryBase> mnfServers, List<EntryBase> entryServers,
			List<EntryBase> idxServers, List<EntryBase> ftServers,
			List<EntryBase> alServers, String serviceName,
			ConnectionInfo connectionInfo) {
		String systemService = TaggingEnvUtil.getSystemService();
		List<EntryBase> prevEntries = new ArrayList<>();

		// バックアップフォルダ
		String prevBackupUri = MigrateUtil.getPreviousBackupUri(serviceName);
		EntryBase prevBackupEntry = TaggingEntryUtil.createEntry(systemService);
		prevBackupEntry.setMyUri(prevBackupUri);
		prevEntries.add(prevBackupEntry);

		// namespace
		// /_bdb/service/{サービス名}/previous_backup/namespace
		String prevNamespaceUri = prevBackupUri + BDBClientServerConst.URI_LAYER_NAMESPACE;
		EntryBase namespaceEntry = TaggingEntryUtil.createEntry(systemService);
		namespaceEntry.setMyUri(prevNamespaceUri);
		namespaceEntry.title = currentNamespace;
		prevEntries.add(namespaceEntry);

		// Manifestサーバ
		// /_bdb/service/{サービス名}/previous_backup/mnfserver/{サーバ名}
		String prevMnfserverUri = prevBackupUri + Constants.URI_MNFSERVER;
		EntryBase prevMnfserverEntry = TaggingEntryUtil.createEntry(systemService);
		prevMnfserverEntry.setMyUri(prevMnfserverUri);
		prevEntries.add(prevMnfserverEntry);
		for (EntryBase serverEntry : mnfServers) {
			prevEntries.add(createBackupEntry(serverEntry, prevMnfserverUri, connectionInfo));
		}

		// Entryサーバ
		// /_bdb/service/{サービス名}/previous_backup/entryserver/{サーバ名}
		String prevEntryserverUri = prevBackupUri + Constants.URI_ENTRYSERVER;
		EntryBase prevEntryserverEntry = TaggingEntryUtil.createEntry(systemService);
		prevEntryserverEntry.setMyUri(prevEntryserverUri);
		prevEntries.add(prevEntryserverEntry);
		for (EntryBase serverEntry : entryServers) {
			prevEntries.add(createBackupEntry(serverEntry, prevEntryserverUri, connectionInfo));
		}

		// インデックスサーバ
		// /_bdb/service/{サービス名}/previous_backup/idxserver/{サーバ名}
		String prevIdxserverUri = prevBackupUri + Constants.URI_IDXSERVER;
		EntryBase prevIdxserverEntry = TaggingEntryUtil.createEntry(systemService);
		prevIdxserverEntry.setMyUri(prevIdxserverUri);
		prevEntries.add(prevIdxserverEntry);
		for (EntryBase serverEntry : idxServers) {
			prevEntries.add(createBackupEntry(serverEntry, prevIdxserverUri, connectionInfo));
		}

		// 全文検索インデックスサーバ
		// /_bdb/service/{サービス名}/previous_backup/ftserver/{サーバ名}
		String prevFtserverUri = prevBackupUri + Constants.URI_FTSERVER;
		EntryBase prevFtserverEntry = TaggingEntryUtil.createEntry(systemService);
		prevFtserverEntry.setMyUri(prevFtserverUri);
		prevEntries.add(prevFtserverEntry);
		for (EntryBase serverEntry : ftServers) {
			prevEntries.add(createBackupEntry(serverEntry, prevFtserverUri, connectionInfo));
		}

		// 採番・カウンタサーバ
		// /_bdb/service/{サービス名}/previous_backup/alserver/{サーバ名}
		String prevAlserverUri = prevBackupUri + Constants.URI_ALSERVER;
		EntryBase prevAlserverEntry = TaggingEntryUtil.createEntry(systemService);
		prevAlserverEntry.setMyUri(prevAlserverUri);
		prevEntries.add(prevAlserverEntry);
		for (EntryBase serverEntry : alServers) {
			prevEntries.add(createBackupEntry(serverEntry, prevAlserverUri, connectionInfo));
		}

		return prevEntries;
	}

	/**
	 * BDBサーバ情報のバックアップを作成する.
	 *   キー : /_bdb/service/{サービス名}/previous_backup/{mnf|entry|idx|ft|al}server/{サーバ名}
	 * @param entry バックアップ対象Entry
	 * @param parentUri /_bdb/service/{サービス名}/previous_backup/{mnf|entry|idx|ft|al}server
	 * @param connectionInfo コネクション情報 (ResourceMapper取得に使用)
	 * @return BDBサーバ情報のバックアップEntry
	 */
	private EntryBase createBackupEntry(EntryBase entry, String parentUri,
			ConnectionInfo connectionInfo) {
		FeedTemplateMapper mapper = TaggingEnvUtil.getResourceMapper(
				TaggingEnvUtil.getSystemService());
		EntryBase backupEntry = TaggingEntryUtil.copyEntry(entry, mapper);
		StringBuilder sb = new StringBuilder();
		sb.append(parentUri);
		sb.append("/");
		sb.append(TaggingEntryUtil.getSelfidUri(entry.getMyUri()));
		backupEntry.setMyUri(sb.toString());
		backupEntry.id = null;
		return backupEntry;
	}

	/**
	 * サービスステータス更新によるデータ移行.
	 * 本メソッドは非同期処理の終了を待って終了とする。
	 * @param serviceName 対象サービス名
	 * @param newNamespace 新名前空間
	 * @param oldNamespace 旧名前空間
	 * @param mnfCurrentServerEntries 旧Manifestサーバリスト
	 * @param entryOldServerEntries 旧Entryサーバリスト
	 * @param alOldServerEntries 旧採番・カウンタサーバリスト
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	private void migrateByChangeServiceStatus(String serviceName,
			String newNamespace, String oldNamespace,
			List<EntryBase> mnfOldServerEntries,
			List<EntryBase> entryOldServerEntries,
			List<EntryBase> alOldServerEntries,
			ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// 対象サービスの認証情報を生成
		ReflexAuthentication targetServiceAuth = MigrateUtil.createServiceAdminAuth(serviceName);
		// 旧EntryサーバURLリストを取得
		List<String> entryOldServerUrls = getEntryServerUrls(entryOldServerEntries,
				requestInfo, connectionInfo);
		ConsistentHash<String> entryOldConsistentHash =
				BDBRequesterUtil.createConsistentHash(entryOldServerUrls);

		List<Future<Boolean>> futures = new ArrayList<>();
		// 旧Manifestサーバごとに繰り返す
		for (EntryBase mnfOldServerEntry : mnfOldServerEntries) {
			MigrateMinForEachMnfServerCallable callable =
					new MigrateMinForEachMnfServerCallable(serviceName,
							oldNamespace, mnfOldServerEntry, entryOldConsistentHash);
			Future<Boolean> future = MigrateUtil.addTask(callable, targetServiceAuth,
					requestInfo, connectionInfo);
			futures.add(future);
		}

		// 旧採番・カウンタサーバごとに繰り返す
		for (EntryBase alOldServerEntry : alOldServerEntries) {
			// スレッド化
			MigrateMinForEachAlServerCallable callable =
					new MigrateMinForEachAlServerCallable(serviceName,
							oldNamespace, alOldServerEntry);
			Future<Boolean> future = MigrateUtil.addTask(callable, targetServiceAuth,
					requestInfo, connectionInfo);
			futures.add(future);
		}

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
	 * サービスステータス更新によるデータ移行のうち、Manifestサーバごとの処理.
	 * @param serviceName 対象サービス名
	 * @param oldNamespace 旧名前空間
	 * @param mnfOldServerEntry 旧Manifestサーバ
	 * @param entryOldServerUrls 旧EntryサーバURLリスト
	 * @param entryOldConsistentHash 旧EntryサーバURL ConsistentHash
	 * @param targetServiceAuth 対象サービスの認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	void migrateForEachMnfServer(String serviceName, String oldNamespace,
			EntryBase mnfOldServerEntry, ConsistentHash<String> entryOldConsistentHash,
			ReflexAuthentication targetServiceAuth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String oldServerMnfName = TaggingEntryUtil.getSelfidUri(mnfOldServerEntry.getMyUri());
		String oldServerMnfUrl = BDBRequesterUtil.getMnfServerUrlByServerName(
				oldServerMnfName, requestInfo, connectionInfo);
		BDBRequester<FeedBase> bdbRequester = new BDBRequester<>(BDBResponseType.FEED);
		List<Future<Boolean>> futures = new ArrayList<>();

		// 旧Manifestサーバをlist検索し、移行対象IDを取得する。
		// GET /b?_list=DBManifest&_keyprefix=:\ufffe/ (ルートエントリー)
		FeedTemplateMapper atomMapper = TaggingEnvUtil.getAtomResourceMapper();
		String tableName = BDBClientServerConst.DB_MANIFEST;
		String getRootUri = getListRootUriMnf(tableName);
		BDBResponseInfo<FeedBase> responseInfoRoot = bdbRequester.requestByMigration(
				oldServerMnfUrl, getRootUri, Constants.GET, oldNamespace, atomMapper,
				serviceName, requestInfo, connectionInfo);
		FeedBase respFeedRoot = responseInfoRoot.data;
		if (TaggingEntryUtil.isExistData(respFeedRoot)) {
			// ルートエントリー存在あり(1件)
			EntryBase listEntry = respFeedRoot.entry.get(0);
			Future<Boolean> future = callMigrateForEachEntry(serviceName, oldNamespace,
					listEntry, entryOldConsistentHash, targetServiceAuth, requestInfo,
					connectionInfo);
			futures.add(future);
		}

		// 一般のシステムエントリー 1階層目
		// GET /b?_list=DBManifest&_keyprefix=/\ufffe_ (システムエントリー)
		String cursorStr = null;
		do {
			String getSystemUri = getListSystemUriMnf(tableName, cursorStr);
			BDBResponseInfo<FeedBase> responseInfoSystem = bdbRequester.requestByMigration(
					oldServerMnfUrl, getSystemUri, Constants.GET, oldNamespace, atomMapper,
					serviceName, requestInfo, connectionInfo);
			FeedBase respFeedSystem = responseInfoSystem.data;
			cursorStr = TaggingEntryUtil.getCursorFromFeed(respFeedSystem);
			if (TaggingEntryUtil.isExistData(respFeedSystem)) {
				// システムエントリー存在あり(複数件)
				for (EntryBase listEntry : respFeedSystem.entry) {
					Future<Boolean> future = callMigrateForEachEntry(serviceName, oldNamespace,
							listEntry, entryOldConsistentHash, targetServiceAuth, requestInfo,
							connectionInfo);
					if (future != null) {
						futures.add(future);
					}
				}
			}

		} while (!StringUtils.isBlank(cursorStr));

		// 一般のシステムエントリー 2階層目以下
		// GET /b?_list=DBManifest&_keyprefix=/_ (システムエントリー)
		cursorStr = null;
		do {
			String getSystemUri = getListSystemUri(tableName, cursorStr);
			BDBResponseInfo<FeedBase> responseInfoSystem = bdbRequester.requestByMigration(
					oldServerMnfUrl, getSystemUri, Constants.GET, oldNamespace, atomMapper,
					serviceName, requestInfo, connectionInfo);
			FeedBase respFeedSystem = responseInfoSystem.data;
			cursorStr = TaggingEntryUtil.getCursorFromFeed(respFeedSystem);
			if (TaggingEntryUtil.isExistData(respFeedSystem)) {
				// システムエントリー存在あり(複数件)
				for (EntryBase listEntry : respFeedSystem.entry) {
					Future<Boolean> future = callMigrateForEachEntry(serviceName, oldNamespace,
							listEntry, entryOldConsistentHash, targetServiceAuth, requestInfo,
							connectionInfo);
					if (future != null) {
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
	 * Entryごとの処理のスレッド生成
	 * @param serviceName 対象サービス名
	 * @param oldNamespace 旧名前空間
	 * @param listEntry Entry情報 (summaryにid)
	 * @param entryOldServerUrls 旧EntryサーバURLリスト
	 * @param entryOldConsistentHash 旧EntryサーバURL ConsistentHash
	 * @param targetServiceAuth 対象サービスの認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return Future
	 */
	private Future<Boolean> callMigrateForEachEntry(String serviceName, String oldNamespace,
			EntryBase listEntry, ConsistentHash<String> entryOldConsistentHash,
			ReflexAuthentication targetServiceAuth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String id = listEntry.summary;
		String idUri = TaggingEntryUtil.getUriById(id);

		String keyUri = MigrateUtil.editManifestKeyUri(listEntry.title);
		if (idUri.equals(keyUri)) {
			String oldServerEntryUrl = BDBRequesterUtil.assign(entryOldConsistentHash, idUri);
			// Entryごとにスレッド化
			MigrateMinForEachEntryCallable callable = new MigrateMinForEachEntryCallable(
					serviceName, id, oldServerEntryUrl, oldNamespace);
			return MigrateUtil.addTask(callable, targetServiceAuth,
					requestInfo, connectionInfo);
		} else {
			// エイリアスの場合処理しない
			return null;
		}
	}

	/**
	 * サービスステータス更新に伴うデータ移行 1エントリーごとの処理
	 * @param serviceName 対象サービス名
	 * @param id ID
	 * @param oldServerEntryUrl 旧EntryサーバURL
	 * @param oldNamespace 旧名前空間
	 * @param targetServiceAuth 対象サービスの認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	void migrateForEachEntry(String serviceName, String id, String oldServerEntryUrl,
			String oldNamespace, ReflexAuthentication targetServiceAuth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// 旧EntryサーバからEntryを取得
		String requestEntryUri = BDBClientUtil.getEntryUri(id);
		FeedTemplateMapper mapper = TaggingEnvUtil.getResourceMapper(serviceName);
		BDBRequester<EntryBase> bdbEntryRequester = new BDBRequester<>(BDBResponseType.ENTRY);
		BDBRequester<FeedBase> bdbPutEntryRequester = new BDBRequester<>(BDBResponseType.FEED);
		BDBResponseInfo<EntryBase> respInfoEntry = bdbEntryRequester.requestByMigration(
				oldServerEntryUrl, requestEntryUri, Constants.GET, oldNamespace, mapper,
				serviceName, requestInfo, connectionInfo);
		if (respInfoEntry == null) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[migrateForEachEntry] Entry does not exist. id=");
			sb.append(id);
			logger.warn(sb.toString());
			return;
		}

		EntryBase entry = respInfoEntry.data;
		String idUri = TaggingEntryUtil.getUriById(id);
		// 新EntryサーバにEntryを登録
		String entryServerUrl = BDBClientUtil.getEntryServerUrl(idUri, serviceName,
				requestInfo, connectionInfo);
		bdbPutEntryRequester.request(entryServerUrl, requestEntryUri, Constants.PUT,
				entry, mapper, serviceName, requestInfo, connectionInfo);

		// インデックス・全文検索インデックスを登録
		UpdatedInfo updatedInfo = new UpdatedInfo(OperationType.INSERT, entry, null);
		List<UpdatedInfo> updatedInfos = new ArrayList<>();
		updatedInfos.add(updatedInfo);

		// インデックス登録更新スレッド実行
		InnerIndexPutCallable innerIndexPutCallable = new InnerIndexPutCallable(
				updatedInfos);
		MigrateUtil.addTask(innerIndexPutCallable, targetServiceAuth, requestInfo, connectionInfo);

		// 全文検索インデックス登録更新スレッド実行
		FullTextIndexPutCallable fullTextIndexPutCallable = new FullTextIndexPutCallable(
				updatedInfos);
		MigrateUtil.addTask(fullTextIndexPutCallable, targetServiceAuth, requestInfo, connectionInfo);

		// インデックス登録の終了待ちについて、スレッドが多すぎるとうまく動かないので待たない

		// Manifestを登録
		BDBRequester<FeedBase> bdbFeedRequester = new BDBRequester<>(BDBResponseType.FEED);
		String mnfPutRequestUri = BDBClientUtil.getPutManifestUri();
		String mnfPutMethod = Constants.PUT;
		// <link rel="self" href={キー} title={ID} />
		List<Link> links = new ArrayList<>();
		links.add(createManifestLink(id, entry.getMyUri()));
		List<String> aliases = entry.getAlternate();
		if (aliases != null) {
			for (String alias : aliases) {
				links.add(createManifestLink(id, alias));
			}
		}
		FeedBase mnfReqFeed = TaggingEntryUtil.createAtomFeed();
		mnfReqFeed.link = links;

		bdbFeedRequester.requestToManifest(mnfPutRequestUri, mnfPutMethod, mnfReqFeed,
				serviceName, requestInfo, connectionInfo);
	}

	/**
	 * サービスステータス更新によるデータ移行のうち、採番・カウンタサーバごとの処理.
	 * @param serviceName 対象サービス名
	 * @param oldNamespace 旧名前空間
	 * @param alOldServerEntry 旧採番・カウンタサーバ
	 * @param targetServiceAuth 対象サービスの認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	void migrateForEachAlServer(String serviceName, String oldNamespace,
			EntryBase alOldServerEntry, ReflexAuthentication targetServiceAuth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String oldServerAlName = TaggingEntryUtil.getSelfidUri(alOldServerEntry.getMyUri());
		String oldServerAlUrl = BDBRequesterUtil.getAlServerUrlByServerName(
				oldServerAlName, requestInfo, connectionInfo);
		BDBRequester<FeedBase> bdbRequester = new BDBRequester<>(BDBResponseType.FEED);
		List<Future<Boolean>> futures = new ArrayList<>();
		FeedTemplateMapper atomMapper = TaggingEnvUtil.getAtomResourceMapper();

		// 旧採番・カウンタサーバをlist検索し、移行対象IDを取得する。
		// 採番 : GET /b?_list=DBAllocids&_keyprefix=/_ (システムエントリー)
		String tableName = BDBClientServerConst.DB_ALLOCIDS;
		String cursorStr = null;
		do {
			String getSystemUri = getListSystemUri(tableName, cursorStr);
			BDBResponseInfo<FeedBase> responseInfoSystem = bdbRequester.requestByMigration(
					oldServerAlUrl, getSystemUri, Constants.GET, oldNamespace, atomMapper,
					serviceName, requestInfo, connectionInfo);
			FeedBase respFeedSystem = responseInfoSystem.data;
			cursorStr = TaggingEntryUtil.getCursorFromFeed(respFeedSystem);
			if (TaggingEntryUtil.isExistData(respFeedSystem)) {
				// システムエントリー存在あり(複数件)
				for (EntryBase entry : respFeedSystem.entry) {
					String uri = entry.title;
					MigrateMinForEachAllocidsCallable callable =
							new MigrateMinForEachAllocidsCallable(serviceName,
									oldNamespace, uri, oldServerAlUrl);
					Future<Boolean> future = MigrateUtil.addTask(callable, targetServiceAuth,
							requestInfo, connectionInfo);
					futures.add(future);
				}
			}

		} while (!StringUtils.isBlank(cursorStr));

		// カウンタ : GET /b?_list=DBIncrement&_keyprefix=/_ (システムエントリー)
		tableName = BDBClientServerConst.DB_INCREMENT;
		do {
			String getSystemUri = getListSystemUri(tableName, cursorStr);
			BDBResponseInfo<FeedBase> responseInfoSystem = bdbRequester.requestByMigration(
					oldServerAlUrl, getSystemUri, Constants.GET, oldNamespace, atomMapper,
					serviceName, requestInfo, connectionInfo);
			FeedBase respFeedSystem = responseInfoSystem.data;
			cursorStr = TaggingEntryUtil.getCursorFromFeed(respFeedSystem);
			if (TaggingEntryUtil.isExistData(respFeedSystem)) {
				for (EntryBase entry : respFeedSystem.entry) {
					String uri = entry.title;
					MigrateMinForEachIncrementCallable callable =
							new MigrateMinForEachIncrementCallable(serviceName, oldNamespace,
									uri, oldServerAlUrl);
					Future<Boolean> future = MigrateUtil.addTask(callable, targetServiceAuth,
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
	 * 採番1件ごとの移行処理.
	 * @param serviceName サービス名
	 * @param oldNamespace 旧名前空間
	 * @param uri URI
	 * @param oldServerAlUrl 旧採番・カウンタサーバURL
	 * @param targetServiceAuth 対象サービスの認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	void migrateForEachAllocids(String serviceName, String oldNamespace, String uri,
			String oldServerAlUrl, ReflexAuthentication targetServiceAuth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// 旧採番・カウンタサーバから1件採番する。GET /b{キー}?_allocids=1
		FeedTemplateMapper atomMapper = TaggingEnvUtil.getAtomResourceMapper();
		BDBRequester<FeedBase> bdbRequester = new BDBRequester<>(BDBResponseType.FEED);
		String requestUri = getAllocidsUri(uri, 1);
		BDBResponseInfo<FeedBase> responseInfoAllocids = bdbRequester.requestByMigration(
				oldServerAlUrl, requestUri, Constants.GET, oldNamespace, atomMapper,
				serviceName, requestInfo, connectionInfo);
		FeedBase respFeed = responseInfoAllocids.data;
		int allocids = 0;
		if (respFeed != null && !StringUtils.isBlank(respFeed.title) &&
				StringUtils.isInteger(respFeed.title)) {
			int val = Integer.parseInt(respFeed.title);
			if (val > 0) {
				allocids = val - 1;	// 今採番した分を引く
			} else {
				allocids = 0;
			}
		}
		if (allocids > 0) {
			// 採番値 - 1 の数だけ、新採番・カウンタサーバにリクエストする。
			// GET /b{キー}?_allocids={採番値 - 1}
			BDBClientAllocateIdsManager allocidsManager = new BDBClientAllocateIdsManager();
			allocidsManager.allocateIds(uri, allocids, targetServiceAuth, requestInfo, connectionInfo);
		}
	}

	/**
	 * カウンタ1件ごとの移行処理.
	 * @param serviceName サービス名
	 * @param oldNamespace 旧名前空間
	 * @param uri URI
	 * @param oldServerAlUrl 旧採番・カウンタサーバURL
	 * @param targetServiceAuth 対象サービスの認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	void migrateForEachIncrement(String serviceName, String oldNamespace, String uri,
			String oldServerAlUrl, ReflexAuthentication targetServiceAuth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		BDBClientIncrementManager incrementManager = new BDBClientIncrementManager();
		FeedTemplateMapper atomMapper = TaggingEnvUtil.getAtomResourceMapper();
		BDBRequester<FeedBase> bdbRequester = new BDBRequester<>(BDBResponseType.FEED);

		// 旧採番・カウンタサーバからカウンタ枠を取得する。GET /b{キー}?_rangeids
		String requestUri = getRangeidsUri(uri);
		BDBResponseInfo<FeedBase> responseInfoRangeids = bdbRequester.requestByMigration(
				oldServerAlUrl, requestUri, Constants.GET, oldNamespace, atomMapper,
				serviceName, requestInfo, connectionInfo);
		FeedBase respFeed = responseInfoRangeids.data;
		if (respFeed != null && !StringUtils.isBlank(respFeed.title)) {
			// 枠の設定がある場合登録する。PUT /b{キー}?_rangeids
			incrementManager.setRange(uri, respFeed.title, targetServiceAuth, requestInfo, connectionInfo);
		}

		// 旧採番・カウンタサーバからカウンタを取得する。GET /b{キー}?_getids
		requestUri = getGetidsUri(uri);
		BDBResponseInfo<FeedBase> responseInfoGetids = bdbRequester.requestByMigration(
				oldServerAlUrl, requestUri, Constants.GET, oldNamespace, atomMapper,
				serviceName, requestInfo, connectionInfo);
		respFeed = responseInfoGetids.data;
		long num = 0;
		if (respFeed != null && StringUtils.isLong(respFeed.title)) {
			num = Long.parseLong(respFeed.title);
		}
		// 新採番・カウンタサーバにカウンタを登録する。PUT /b{キー}?_setids={値}
		incrementManager.setNumber(uri, num, targetServiceAuth, requestInfo, connectionInfo);
	}

	/**
	 * listオプションでルートエントリーを取得するURIを取得 (Manifest用)
	 * @param tableName テーブル名
	 * @return listオプションでルートエントリーを取得するURI
	 */
	private String getListRootUriMnf(String tableName) {
		// GET /b?_list={tableName}&_keyprefix=:\ufffe/ (ルートエントリー)
		String keyprefix = MigrateUtil.getManifestUri("/");
		return BDBClientUtil.getListUri(tableName, keyprefix, null);
	}

	/**
	 * listオプションでシステムエントリー(/_から始まるエントリー1階層目)を取得するURIを取得 (Manifest用)
	 * @param tableName テーブル名
	 * @param cursorStr カーソル
	 * @return listオプションでシステムエントリーを取得するURI
	 */
	private String getListSystemUriMnf(String tableName, String cursorStr) {
		// GET /b?_list={tableName}&_keyprefix=/\ufffe_ (システムエントリー)
		String keyprefix = MigrateUtil.getManifestUri(Constants.URI_SYSTEM_PREFIX);
		return BDBClientUtil.getListUri(tableName, keyprefix, cursorStr);
	}

	/**
	 * listオプションでシステムエントリー(/_から始まるエントリー)を取得するURIを取得
	 * @param tableName テーブル名
	 * @param cursorStr カーソル
	 * @return listオプションでシステムエントリーを取得するURI
	 */
	private String getListSystemUri(String tableName, String cursorStr) {
		// GET /b?_list={tableName}&_keyprefix=/_ (システムエントリー)
		return BDBClientUtil.getListUri(tableName, Constants.URI_SYSTEM_PREFIX, cursorStr);
	}

	/**
	 * 採番リクエストURIを取得
	 * @param uri URI
	 * @param num 採番数
	 * @return 採番リクエストURI
	 */
	private String getAllocidsUri(String uri, int num) {
		// GET /b{キー}?_allocids={num}
		StringBuilder sb = new StringBuilder();
		sb.append(uri);
		sb.append("?");
		sb.append(RequestParam.PARAM_ALLOCIDS);
		sb.append("=");
		sb.append(num);
		return sb.toString();
	}

	/**
	 * 加算値取得リクエストURIを取得
	 * @param uri URI
	 * @return 加算値取得リクエストURI
	 */
	private String getGetidsUri(String uri) {
		// GET /b{キー}?_getids
		StringBuilder sb = new StringBuilder();
		sb.append(uri);
		sb.append("?");
		sb.append(RequestParam.PARAM_GETIDS);
		return sb.toString();
	}

	/**
	 * 加算枠取得リクエストURIを取得
	 * @param uri URI
	 * @return 加算枠取得リクエストURI
	 */
	private String getRangeidsUri(String uri) {
		// GET /b{キー}?_rangeids
		StringBuilder sb = new StringBuilder();
		sb.append(uri);
		sb.append("?");
		sb.append(RequestParam.PARAM_RANGEIDS);
		return sb.toString();
	}

	/**
	 * Manifest登録用Linkを生成
	 * @param id ID
	 * @param uri URI
	 * @return Manifest登録用Linkオブジェクト
	 */
	private Link createManifestLink(String id, String uri) {
		// <link rel="self" href={キー} title={ID} />
		Link link = new Link();
		link._$rel = Link.REL_SELF;
		link._$href = uri;
		link._$title = id;
		return link;
	}

	/**
	 * サービスステータス変更によるデータ移行処理の失敗時、設定を元に戻す処理.
	 * @param serviceName サービス名
	 * @param newServiceStatus 新サービスステータス
	 * @param currentNamespace 旧名前空間
	 * @param mnfCurrentServerEntries 旧Manifestサーバエントリーリスト
	 * @param entryCurrentServerEntries 旧MEntryサーバエントリーリスト
	 * @param idxCurrentServerEntries 旧インデックスサーバエントリーリスト
	 * @param ftCurrentServerEntries 旧全文検索インデックスサーバエントリーリスト
	 * @param alCurrentServerEntries 旧採番・カウンタサーバエントリーリスト
	 * @param systemContext システム管理サービスのSystemContext
	 */
	private void failedAssignAndMigrateMinimum(String serviceName, String newServiceStatus,
			String currentNamespace, List<EntryBase> mnfCurrentServerEntries,
			List<EntryBase> entryCurrentServerEntries, List<EntryBase> idxCurrentServerEntries,
			List<EntryBase> ftCurrentServerEntries, List<EntryBase> alCurrentServerEntries,
			SystemContext systemContext)
	throws IOException, TaggingException {
		String systemService = systemContext.getServiceName();
		// * ログエントリー出力
		String logMsg = "Migration for change status failed. " + serviceName;
		systemContext.log(LOG_TITLE, Constants.ERROR, logMsg);

		// * 名前空間を元に戻す
		List<EntryBase> putEntries = new ArrayList<>();

		// 名前空間の更新
		NamespaceManager namespaceManager = TaggingEnvUtil.getNamespaceManager();
		EntryBase namespaceEntry = namespaceManager.createNamespaceEntry(
				currentNamespace, serviceName);
		putEntries.add(namespaceEntry);

		// * 各割り当てBDBサーバを元に戻す
		// 現在登録しているものを抽出し、差分を登録削除する必要あり。
		String serviceTypeUri = Constants.URI_MNFSERVER;
		List<EntryBase> currentServerEntries = mnfCurrentServerEntries;
		List<EntryBase> nowServerEntries = getCurrentServers(serviceName,
				serviceTypeUri, systemContext);
		List<EntryBase> updServerEntries = mergeAssignServerEntries(currentServerEntries,
				nowServerEntries);
		putEntries.addAll(updServerEntries);

		serviceTypeUri = Constants.URI_ENTRYSERVER;
		currentServerEntries = mnfCurrentServerEntries;
		nowServerEntries = getCurrentServers(serviceName, serviceTypeUri, systemContext);
		updServerEntries = mergeAssignServerEntries(currentServerEntries,
				nowServerEntries);
		putEntries.addAll(updServerEntries);

		serviceTypeUri = Constants.URI_IDXSERVER;
		currentServerEntries = idxCurrentServerEntries;
		nowServerEntries = getCurrentServers(serviceName, serviceTypeUri, systemContext);
		updServerEntries = mergeAssignServerEntries(currentServerEntries,
				nowServerEntries);
		putEntries.addAll(updServerEntries);

		serviceTypeUri = Constants.URI_FTSERVER;
		currentServerEntries = ftCurrentServerEntries;
		nowServerEntries = getCurrentServers(serviceName, serviceTypeUri, systemContext);
		updServerEntries = mergeAssignServerEntries(currentServerEntries,
				nowServerEntries);
		putEntries.addAll(updServerEntries);

		serviceTypeUri = Constants.URI_ALSERVER;
		currentServerEntries = alCurrentServerEntries;
		nowServerEntries = getCurrentServers(serviceName, serviceTypeUri, systemContext);
		updServerEntries = mergeAssignServerEntries(currentServerEntries,
				nowServerEntries);
		putEntries.addAll(updServerEntries);

		// * サービスステータスを元に戻す
		String uri = TaggingServiceUtil.getServiceUri(serviceName);
		EntryBase serviceStatusEntry = systemContext.getEntry(uri, false);
		String serviceStatus = null;
		if (Constants.SERVICE_STATUS_STAGING.equals(newServiceStatus)) {
			serviceStatus = Constants.SERVICE_STATUS_PRODUCTION;
		} else {
			serviceStatus = Constants.SERVICE_STATUS_STAGING;
		}
		setServiceStatus(serviceStatusEntry, serviceStatus);
		serviceStatusEntry.id = null;
		putEntries.add(serviceStatusEntry);

		FeedBase putFeed = TaggingEntryUtil.createFeed(systemService);
		putFeed.entry = putEntries;
		systemContext.put(putFeed);

		// 旧サーバ情報を削除する。
		// /_bdb/service/{サービス名}/previous_backupをフォルダ削除する。
		String prevBackupUri = MigrateUtil.getPreviousBackupUri(serviceName);
		boolean async = false;
		boolean isParallel = false;
		systemContext.deleteFolder(prevBackupUri, async, isParallel);

	}

	/**
	 * サービスの設定処理.
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	private void settingService(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// サービス情報の設定
		ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
		serviceManager.settingServiceIfAbsent(serviceName, requestInfo, connectionInfo);
	}

	/**
	 * Entryサーバのエントリーリストから、EntryサーバURLリストを取得.
	 * @param entryServerEntries Entryサーバのエントリーリスト
	 * @return EntryサーバURLリスト
	 */
	private List<String> getEntryServerUrls(List<EntryBase> entryServerEntries,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		List<String> entryServerUrls = new ArrayList<>();
		for (EntryBase entryServerEntry : entryServerEntries) {
			String serverName = TaggingEntryUtil.getSelfidUri(entryServerEntry.getMyUri());
			String serverUrl = BDBRequesterUtil.getEntryServerUrlByServerName(
					serverName, requestInfo, connectionInfo);
			entryServerUrls.add(serverUrl);
		}
		return entryServerUrls;
	}

}
