package jp.reflexworks.taggingservice.migrate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBServerType;
import jp.reflexworks.taggingservice.requester.BDBClientServerUtil;
import jp.reflexworks.taggingservice.requester.BDBRequesterUtil;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.NumberingUtil;

/**
 * サービスのBDB接続先設定管理クラス.
 */
public class BDBClientServiceManager {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * サービス作成.
	 * データストアのサービス初期設定.
	 * BDBの接続先エントリーを作成する。
	 * @param newServiceName 新規サービス名
	 * @param serviceStatus サービスステータス (staging/production)
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void createservice(String newServiceName, String serviceStatus,
			ReflexAuthentication auth, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String systemService = auth.getServiceName();

		// システム管理サービスのSystemContextを作成
		SystemContext systemContext = new SystemContext(systemService, requestInfo, connectionInfo);
		//String serviceStatus = Constants.SERVICE_STATUS_STAGING;

		List<EntryBase> postEntries = new ArrayList<>();

		// 親階層Entryを生成
		// /_bdb/service/{サービス名}
		// /_bdb/service/{サービス名}/mnfserver
		// /_bdb/service/{サービス名}/entryserver
		// /_bdb/service/{サービス名}/idxserver
		// /_bdb/service/{サービス名}/ftserver
		// /_bdb/service/{サービス名}/alserver
		postEntries.addAll(createAssignableServerParents(newServiceName));

		// Manifestサーバ割り当て : /_bdb/service/{サービス名}/mnfserver/{Manifestサーバ名}
		List<EntryBase> mnfServerEntries = createAssignServerEntry(newServiceName, serviceStatus,
				Constants.URI_MNFSERVER, 1, BDBServerType.MANIFEST, systemContext);
		postEntries.addAll(mnfServerEntries);

		// Entryサーバ割り当て : /_bdb/service/{サービス名}/entryserver/{Entryサーバ名}
		List<EntryBase> entryServerEntries = createAssignServerEntry(newServiceName, serviceStatus,
				Constants.URI_ENTRYSERVER, 1, BDBServerType.ENTRY, systemContext);
		postEntries.addAll(entryServerEntries);

		// インデックスサーバ割り当て : /_bdb/service/{サービス名}/idxserver/{インデックスサーバ名}
		List<EntryBase> idxServerEntries = createAssignServerEntry(newServiceName, serviceStatus,
				Constants.URI_IDXSERVER, 1, BDBServerType.INDEX, systemContext);
		postEntries.addAll(idxServerEntries);

		// 全文検索インデックスサーバ割り当て : /_bdb/service/{サービス名}/ftserver/{全文検索インデックスサーバ名}
		List<EntryBase> ftServerEntries = createAssignServerEntry(newServiceName, serviceStatus,
				Constants.URI_FTSERVER, 1, BDBServerType.FULLTEXT, systemContext);
		postEntries.addAll(ftServerEntries);

		// 採番・カウンタサーバ割り当て : /_bdb/service/{サービス名}/alserver/{採番・カウンタサーバ名}
		List<EntryBase> alServerEntries = createAssignServerEntry(newServiceName, serviceStatus,
				Constants.URI_ALSERVER, 1, BDBServerType.ALLOCIDS, systemContext);
		postEntries.addAll(alServerEntries);

		FeedBase postFeed = TaggingEntryUtil.createFeed(systemService);
		postFeed.entry = postEntries;
		systemContext.put(postFeed);

		// 割り当てサーバのConsistentHashを生成(後続処理で使用)
		// ConsistentHashの更新
		// サーバURLを取得する。
		String mnfNewServerUrl = BDBRequesterUtil.getMnfServerUrl(newServiceName,
				requestInfo, connectionInfo);
		List<String> mnfNewServerUrls = new ArrayList<>();
		mnfNewServerUrls.add(mnfNewServerUrl);
		BDBRequesterUtil.changeServerList(BDBServerType.MANIFEST, mnfNewServerUrls,
				newServiceName, connectionInfo);
		List<String> entryNewServerUrls = BDBRequesterUtil.getEntryServerUrls(newServiceName,
				requestInfo, connectionInfo);
		BDBRequesterUtil.changeServerList(BDBServerType.ENTRY, entryNewServerUrls,
				newServiceName, connectionInfo);
		List<String> idxNewServerUrls = BDBRequesterUtil.getIdxServerUrls(newServiceName,
				requestInfo, connectionInfo);
		BDBRequesterUtil.changeServerList(BDBServerType.INDEX, idxNewServerUrls,
				newServiceName, connectionInfo);
		List<String> ftNewServerUrls = BDBRequesterUtil.getFtServerUrls(newServiceName,
				requestInfo, connectionInfo);
		BDBRequesterUtil.changeServerList(BDBServerType.FULLTEXT, ftNewServerUrls,
				newServiceName, connectionInfo);
		List<String> alNewServerUrls = BDBRequesterUtil.getAlServerUrls(newServiceName,
				requestInfo, connectionInfo);
		BDBRequesterUtil.changeServerList(BDBServerType.ALLOCIDS, alNewServerUrls,
				newServiceName, connectionInfo);

	}

	/**
	 * サーバ名をリストにして返却.
	 * FeedのEntryの各selfidを抽出し、リストに格納する。
	 * @param feed サーバリスト
	 * @return サーバ名を抽出したリスト
	 */
	public List<String> getSelfidList(FeedBase feed) {
		if (feed == null || feed.entry == null || feed.entry.isEmpty()) {
			return null;
		}
		List<String> list = new ArrayList<>();
		for (EntryBase entry : feed.entry) {
			// selfidがサーバ名
			String uri = entry.getMyUri();
			list.add(TaggingEntryUtil.getSelfidUri(uri));
		}
		return list;
	}

	/**
	 * BDBサーバを割り当てて、設定Entryを生成する.
	 * @param serviceName サービス名
	 * @param serviceStatus サービスステータス
	 * @param serverTypeUri サーバタイプURI
	 * @param num 割り当て数
	 * @param serverType サーバタイプ
	 * @param systemContext SystemEntry
	 * @return 割り当てサーバ設定Entry
	 */
	public List<EntryBase> createAssignServerEntry(String serviceName, String serviceStatus,
			String serverTypeUri, int num, BDBServerType serverType, SystemContext systemContext)
	throws IOException, TaggingException {
		// サーバ割り当て
		// /_bdb/{staging|production}/***serverをFeed検索
		List<String> serverNames = getAssignableServers(serviceName, serviceStatus, serverTypeUri,
				systemContext);
		if (serverNames == null || serverNames.isEmpty()) {
			String parentUri = getAssignableServerUri(serviceStatus, serverTypeUri);
			throw new IllegalStateException("Assignable server does not exist. " + parentUri);
		}

		// 抽出サーバ数
		int serverCnt = serverNames.size();
		if (num > serverCnt) {
			num = serverCnt;
		}
		// 割り当てサーバの抽出
		// この割り当てにはConsistentHashを使用しない。(ConsistentHashの生成には数十msかかるため。)
		List<String> assignedServers = new ArrayList<>();
		if (num == serverCnt) {
			assignedServers.addAll(serverNames);
		} else {
			for (int i = 0; i < num; i++) {
				String serverName = NumberingUtil.chooseOne(serverNames);
				assignedServers.add(serverName);
				serverNames.remove(serverName);
			}
		}

		List<EntryBase> serverEntries = new ArrayList<>();
		for (String serverName : assignedServers) {
			EntryBase serverEntry = createServiceServerEntry(serviceName, serverTypeUri, serverName);
			serverEntries.add(serverEntry);
		}
		return serverEntries;
	}

	/**
	 * 割り当て可能なサーバ名リストを取得.
	 * @param serviceName サービス名
	 * @param serviceStatus サービスステータス
	 * @param serverTypeUri サーバタイプURI (/***server)
	 * @param systemContext SystemContext
	 * @return 割り当て可能なサーバ名リスト
	 */
	public List<String> getAssignableServers(String serviceName, String serviceStatus,
			String serverTypeUri, SystemContext systemContext)
	throws IOException, TaggingException {
		// 1. サービスステータスがproductionの場合、/_bdb/reservation/{サービス名}/***serverをFeed検索
		String parentUri = null;
		FeedBase serversFeed = null;
		if (Constants.SERVICE_STATUS_PRODUCTION.equals(serviceStatus)) {
			parentUri = getReservationServerUri(serviceName, serverTypeUri);
			serversFeed = systemContext.getFeed(parentUri);
		}

		// 2. 1でリストを取得できなかった場合、/_bdb/{staging|production}/***serverをFeed検索
		if (!TaggingEntryUtil.isExistData(serversFeed)) {
			parentUri = getAssignableServerUri(serviceStatus, serverTypeUri);
			serversFeed = systemContext.getFeed(parentUri);
		}
		return getSelfidList(serversFeed);
	}

	/**
	 * サービスへの割り当て候補BDBサーバ名リスト取得のためのURIを取得
	 *   /_bdb/{staging|production}/***server
	 * @param serviceStatus サービスステータス
	 * @param serverTypeUri サーバタイプURI (/***server)
	 * @return URI
	 */
	public String getAssignableServerUri(String serviceStatus, String serverTypeUri) {
		StringBuilder sb = new StringBuilder();
		sb.append(Constants.URI_BDB);
		sb.append("/");
		sb.append(serviceStatus);
		sb.append(serverTypeUri);
		return sb.toString();
	}

	/**
	 * サービスへの予約済み割り当て候補BDBサーバ名リスト取得のためのURIを取得
	 *   /_bdb/reservation/{サービス名}/***server
	 * @param serviceStatus サービスステータス
	 * @param serverTypeUri サーバタイプURI (/***server)
	 * @return URI
	 */
	public String getReservationServerUri(String serviceName, String serverTypeUri) {
		StringBuilder sb = new StringBuilder();
		sb.append(Constants.URI_BDB_RESERVATION);
		sb.append("/");
		sb.append(serviceName);
		sb.append(serverTypeUri);
		return sb.toString();
	}

	/**
	 * サービスのサーバエントリーを生成
	 * @param serviceName サービス名
	 * @param serverTypeUri サーバタイプ階層
	 * @param serverName サーバ名
	 * @return サービスのサーバエントリー
	 */
	public EntryBase createServiceServerEntry(String serviceName, String serverTypeUri,
			String serverName) {
		EntryBase serverEntry = TaggingEntryUtil.createEntry(TaggingEnvUtil.getSystemService());
		String uri = BDBClientServerUtil.getServiceServerUri(serviceName, serverTypeUri, serverName);
		serverEntry.setMyUri(uri);
		return serverEntry;
	}

	/**
	 * createservice時の親フォルダEntry生成
	 * @param newServiceName 新サービス名
	 * @return 親フォルダEntryリスト
	 */
	private List<EntryBase> createAssignableServerParents(String newServiceName) {
		// /_bdb/service/{サービス名}
		// /_bdb/service/{サービス名}/mnfserver
		// /_bdb/service/{サービス名}/idxserver
		// /_bdb/service/{サービス名}/entryserver
		// /_bdb/service/{サービス名}/alserver
		// /_bdb/service/{サービス名}/ftserver

		List<EntryBase> entries = new ArrayList<>();

		String systemService = TaggingEnvUtil.getSystemService();

		String bdbServiceUri = BDBClientServerUtil.getBDBServiceUri(newServiceName);
		EntryBase entry = TaggingEntryUtil.createEntry(systemService);
		entry.setMyUri(bdbServiceUri);
		entries.add(entry);

		entry = TaggingEntryUtil.createEntry(systemService);
		entry.setMyUri(bdbServiceUri + Constants.URI_MNFSERVER);
		entries.add(entry);

		entry = TaggingEntryUtil.createEntry(systemService);
		entry.setMyUri(bdbServiceUri + Constants.URI_ENTRYSERVER);
		entries.add(entry);

		entry = TaggingEntryUtil.createEntry(systemService);
		entry.setMyUri(bdbServiceUri + Constants.URI_IDXSERVER);
		entries.add(entry);

		entry = TaggingEntryUtil.createEntry(systemService);
		entry.setMyUri(bdbServiceUri + Constants.URI_FTSERVER);
		entries.add(entry);

		entry = TaggingEntryUtil.createEntry(systemService);
		entry.setMyUri(bdbServiceUri + Constants.URI_ALSERVER);
		entries.add(entry);

		return entries;
	}

	/**
	 * サービスステータス変更に伴うBDBサーバ変更.
	 *  ・名前空間の変更
	 *  ・BDBサーバの割り当て直し
	 *  ・一部データの移行
	 * @param serviceName 対象サービス名
	 * @param serviceStatus 新サービスステータス
	 * @param reflexContext システム管理サービスのReflexContext (ログイン情報含む)
	 */
	public void changeServiceStatus(String serviceName, String serviceStatus,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		// データ移行リクエストを行う。
		// 正常レスポンスを受信して終了。
		String requestUri = editRequestUri(serviceName, serviceStatus);
		String method = Constants.PUT;
		BDBClientMigrateUtil.requestMigrate(serviceName, requestUri, method, (byte[])null,
				reflexContext);
	}

	/**
	 * サービスステータス更新とそれに伴うデータ移行リクエストURIを編集.
	 *   ?_servicetoproduction={サービス名}
	 *   ?_servicetostaging={サービス名}
	 * @param serviceName サービス名
	 * @param serviceStatus サービスステータス
	 * @return リクエストURI
	 */
	private String editRequestUri(String serviceName, String serviceStatus) {
		StringBuilder sb = new StringBuilder();
		sb.append("?");
		if (Constants.SERVICE_STATUS_PRODUCTION.equals(serviceStatus)) {
			sb.append(RequestParam.PARAM_SERVICETOPRODUCTION);
		} else {	// STAGING
			sb.append(RequestParam.PARAM_SERVICETOSTAGING);
		}
		sb.append("=");
		sb.append(serviceName);
		return sb.toString();
	}

	/**
	 * サービス作成失敗時のリセット処理.
	 * データストアの設定削除
	 * @param newServiceName 新規サービス名
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void resetCreateservice(String newServiceName,
			ReflexAuthentication auth, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String systemService = auth.getServiceName();

		// システム管理サービスのSystemContextを作成
		SystemContext systemContext = new SystemContext(systemService, requestInfo, connectionInfo);

		// /_bdb/service/{サービス名} 配下を削除する。
		String bdbServiceUri = BDBClientServerUtil.getBDBServiceUri(newServiceName);
		systemContext.deleteFolder(bdbServiceUri, false, true);
	}

}
