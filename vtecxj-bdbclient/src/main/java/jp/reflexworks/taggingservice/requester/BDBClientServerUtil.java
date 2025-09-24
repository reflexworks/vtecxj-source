package jp.reflexworks.taggingservice.requester;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.bdbclient.BDBClientManager;
import jp.reflexworks.taggingservice.bdbclient.BDBClientUtil;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.RequestParamInfo;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBServerType;
import jp.reflexworks.taggingservice.sys.SystemAuthentication;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * リクエスト先BDBサーバを取得するユーティリティ
 */
public class BDBClientServerUtil {

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(BDBClientServerUtil.class);

	/**
	 * BDBサーバ名からURLを取得.
	 * BDBを検索する。
	 * @param bdbServerName BDBサーバ名
	 * @param serverType BDBサーバタイプ
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return BDBサーバのURL
	 */
	public static String getBDBServerUrlFromBDB(String bdbServerName, BDBServerType serverType,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// BDBサーバURLはシステム共通
		String systemService = TaggingEnvUtil.getSystemService();
		SystemAuthentication systemAuth = new SystemAuthentication(null, null, systemService);
		if (!StringUtils.isBlank(TaggingEnvUtil.getSystemProp(
				BDBClientServerConst.PROP_DEPLOYMENT_NAME_MNFSERVER, null))) {
			SystemContext systemContext = new SystemContext(systemAuth, requestInfo, connectionInfo);
			return getBDBServerUrlFromCmd(bdbServerName, serverType, systemContext);
		}
		
		String bdbServerUri = getServerUri(bdbServerName, serverType);
		RequestParam param = new RequestParamInfo(bdbServerUri, systemService);
		BDBClientManager bdbclientManager = new BDBClientManager();
		EntryBase bdbServerEntry = bdbclientManager.getEntry(param.getUri(), true,
				systemService, systemAuth, requestInfo, connectionInfo);
		if (bdbServerEntry == null) {
			StringBuilder sb = new StringBuilder();
			sb.append("The server entry does not exist. ServerName=");
			sb.append(bdbServerName);
			sb.append(", serverType=");
			sb.append(serverType);
			sb.append(", ServerUri=");
			sb.append(bdbServerUri);
			throw new IllegalStateException(sb.toString());
		}
		String bdbServerUrl = bdbServerEntry.title;
		if (StringUtils.isBlank(bdbServerUrl)) {
			StringBuilder sb = new StringBuilder();
			sb.append("The server URL is not specified. ServerName=");
			sb.append(bdbServerName);
			sb.append(", serverType=");
			sb.append(serverType);
			sb.append(", ServerUri=");
			sb.append(bdbServerUri);
			throw new IllegalStateException(sb.toString());
		}
		return bdbServerUrl;
	}

	/**
	 * サービス名からBDBサーバ名リストを取得.
	 * BDBを検索する。
	 * @param serviceName サービス名
	 * @param serverType BDBサーバタイプ
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return BDBサーバ名リスト
	 */
	public static List<String> getBDBServerNamesFromBDB(String serviceName,
			BDBServerType serverType,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		FeedBase serverNamesFeed = getBDBServerNamesFeed(serviceName, serverType,
				requestInfo, connectionInfo);
		if (!TaggingEntryUtil.isExistData(serverNamesFeed)) {
			StringBuilder sb = new StringBuilder();
			sb.append("The server name entries does not exist. ServiceName=");
			sb.append(serviceName);
			sb.append(", serverType=");
			sb.append(serverType);
			throw new IllegalStateException(sb.toString());
		}
		List<String> serverNames = new ArrayList<>();
		for (EntryBase serverNameEntry : serverNamesFeed.entry) {
			String serverName = getServerName(serverNameEntry);
			serverNames.add(serverName);
		}
		return serverNames;
	}

	/**
	 * サーバ名Entryからサーバ名を抽出.
	 * @param serverNameEntry サーバ名Entry
	 * @return サーバ名
	 */
	public static String getServerName(EntryBase serverNameEntry) {
		return TaggingEntryUtil.getSelfidUri(serverNameEntry.getMyUri());
	}

	/**
	 * サーバ名Entryリストからサーバ名を抽出し、リストに詰めて返却する.
	 * @param entries サーバ名Entryリスト
	 * @return サーバ名リスト
	 */
	public static List<String> getServerNames(List<EntryBase> entries) {
		if (entries == null || entries.isEmpty()) {
			return null;
		}
		List<String> serverNames = new ArrayList<>();
		for (EntryBase entry : entries) {
			String serverName = getServerName(entry);
			serverNames.add(serverName);
		}
		return serverNames;
	}

	/**
	 * サービス名からBDBサーバ名リストFeedを取得.
	 * BDBを検索する。
	 * @param serviceName サービス名
	 * @param serverType BDBサーバタイプ
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return BDBサーバ名リスト
	 */
	public static FeedBase getBDBServerNamesFeed(String serviceName,
			BDBServerType serverType,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String systemService = TaggingEnvUtil.getSystemService();
		String assignedServerUri = getAssignedServerUri(serviceName, serverType);
		RequestParam param = new RequestParamInfo(assignedServerUri, systemService);
		SystemAuthentication systemAuth = new SystemAuthentication(null, null, systemService);
		int limit = TaggingEnvUtil.getEntryNumberDefault(serviceName);
		BDBClientManager bdbclientManager = new BDBClientManager();
		List<EntryBase> entries = new ArrayList<>();
		String cursorStr = null;
		do {
			FeedBase feed = bdbclientManager.getFeed(param.getUri(), param.getConditionsList(),
					param.isUrlForwardMatch(), limit, cursorStr, true, serviceName, systemAuth,
					requestInfo, connectionInfo);
			cursorStr = TaggingEntryUtil.getCursorFromFeed(feed);
			if (TaggingEntryUtil.isExistData(feed)) {
				entries.addAll(feed.entry);
			}

		} while (!StringUtils.isBlank(cursorStr));

		if (entries.isEmpty()) {
			return null;
		}
		FeedBase retFeed = TaggingEntryUtil.createFeed(serviceName);
		retFeed.entry = entries;
		return retFeed;
	}

	/**
	 * BDBサーバ情報エントリーURIを取得.
	 *  /_bdb/{mnfserver|entryserver|idxserver|ftserver|alserver}/{BDBサーバ名}
	 * @param bdbServerName BDBサーバ名
	 * @param serverType ServerType
	 * @return BDBサーバ情報エントリーURI
	 */
	public static String getServerUri(String bdbServerName, BDBServerType serverType) {
		StringBuilder sb = new StringBuilder();
		if (BDBServerType.MANIFEST.equals(serverType)) {
			sb.append(Constants.URI_BDB_MNFSERVER);
		} else if (BDBServerType.ENTRY.equals(serverType)) {
			sb.append(Constants.URI_BDB_ENTRYSERVER);
		} else if (BDBServerType.INDEX.equals(serverType)) {
			sb.append(Constants.URI_BDB_IDXSERVER);
		} else if (BDBServerType.FULLTEXT.equals(serverType)) {
			sb.append(Constants.URI_BDB_FTSERVER);
		} else {	// allocids
			sb.append(Constants.URI_BDB_ALSERVER);
		}
		sb.append("/");
		sb.append(bdbServerName);
		return sb.toString();
	}

	/**
	 * 割り当て済みBDBサーバ名リスト取得URIを取得.
	 *  /_bdb/service/{サービス名}/{mnfserver|entryserver|idxserver|ftserver|alserver}/{BDBサーバ名}
	 *  selfidの{BDBサーバ名}はFeed検索で取得する情報なのでこのメソッドでは付加しない。
	 * @param serviceName サービス名
	 * @param serverType ServerType
	 * @return 割り当て済みBDBサーバ情報エントリーURI
	 */
	public static String getAssignedServerUri(String serviceName, BDBServerType serverType) {
		StringBuilder sb = new StringBuilder();
		sb.append(Constants.URI_BDB_SERVICE);
		sb.append("/");
		sb.append(serviceName);
		if (BDBServerType.MANIFEST.equals(serverType)) {
			sb.append(Constants.URI_MNFSERVER);
		} else if (BDBServerType.ENTRY.equals(serverType)) {
			sb.append(Constants.URI_ENTRYSERVER);
		} else if (BDBServerType.INDEX.equals(serverType)) {
			sb.append(Constants.URI_IDXSERVER);
		} else if (BDBServerType.FULLTEXT.equals(serverType)) {
			sb.append(Constants.URI_FTSERVER);
		} else {	// allocids
			sb.append(Constants.URI_ALSERVER);
		}
		return sb.toString();
	}

	/**
	 * サーバタイプURIからサーバタイプを取得.
	 * @param serverTypeUri サーバタイプURI
	 * @return サーバタイプ
	 */
	public static BDBServerType getServerTypeByUri(String serverTypeUri) {
		if (Constants.URI_ENTRYSERVER.equals(serverTypeUri)) {
			return BDBServerType.ENTRY;
		} else if (Constants.URI_IDXSERVER.equals(serverTypeUri)) {
			return BDBServerType.INDEX;
		} else if (Constants.URI_FTSERVER.equals(serverTypeUri)) {
			return BDBServerType.FULLTEXT;
		} else if (Constants.URI_ALSERVER.equals(serverTypeUri)) {
			return BDBServerType.ALLOCIDS;
		} else if (Constants.URI_MNFSERVER.equals(serverTypeUri)) {
			return BDBServerType.MANIFEST;
		} else {
			return null;
		}
	}

	/**
	 * サーバタイプ文字列(mnf, entry, idx, ft, al)からサーバタイプを取得.
	 * @param serverTypeStr サーバタイプ文字列
	 * @return サーバタイプ
	 */
	public static BDBServerType getServerTypeByStr(String serverTypeStr) {
		if (BDBClientServerConst.SERVERTYPE_ENTRY.equals(serverTypeStr)) {
			return BDBServerType.ENTRY;
		} else if (BDBClientServerConst.SERVERTYPE_IDX.equals(serverTypeStr)) {
			return BDBServerType.INDEX;
		} else if (BDBClientServerConst.SERVERTYPE_FT.equals(serverTypeStr)) {
			return BDBServerType.FULLTEXT;
		} else if (BDBClientServerConst.SERVERTYPE_AL.equals(serverTypeStr)) {
			return BDBServerType.ALLOCIDS;
		} else if (BDBClientServerConst.SERVERTYPE_MNF.equals(serverTypeStr)) {
			return BDBServerType.MANIFEST;
		} else {
			return null;
		}
	}

	/**
	 * サーバタイプからサーバタイプURI階層を取得.
	 * @param serverType サーバタイプ
	 * @return サーバタイプURI階層
	 */
	public static String getServerTypeUri(BDBServerType serverType) {
		if (BDBServerType.ENTRY.equals(serverType)) {
			return Constants.URI_ENTRYSERVER;
		} else if (BDBServerType.INDEX.equals(serverType)) {
			return Constants.URI_IDXSERVER;
		} else if (BDBServerType.FULLTEXT.equals(serverType)) {
			return Constants.URI_FTSERVER;
		} else if (BDBServerType.ALLOCIDS.equals(serverType)) {
			return Constants.URI_ALSERVER;
		} else if (BDBServerType.MANIFEST.equals(serverType)) {
			return Constants.URI_MNFSERVER;
		} else {
			return null;
		}
	}

	/**
	 * サーバタイプからサーバタイプ文字列(mnf|entry|idx|ft|al)を取得.
	 * @param serverType サーバタイプ
	 * @return サーバタイプ文字列(mnf|entry|idx|ft|al)
	 */
	public static String getServerTypeStr(BDBServerType serverType) {
		if (BDBServerType.ENTRY.equals(serverType)) {
			return BDBClientServerConst.SERVERTYPE_ENTRY;
		} else if (BDBServerType.INDEX.equals(serverType)) {
			return BDBClientServerConst.SERVERTYPE_IDX;
		} else if (BDBServerType.FULLTEXT.equals(serverType)) {
			return BDBClientServerConst.SERVERTYPE_FT;
		} else if (BDBServerType.ALLOCIDS.equals(serverType)) {
			return BDBClientServerConst.SERVERTYPE_AL;
		} else if (BDBServerType.MANIFEST.equals(serverType)) {
			return BDBClientServerConst.SERVERTYPE_MNF;
		} else {
			return null;
		}
	}

	/**
	 * サービスのサーバ割り当てフォルダURIを取得
	 *   /_bdb/service/{サービス名}
	 * @param serviceName サービス名
	 * @return URI
	 */
	public static String getBDBServiceUri(String serviceName) {
		StringBuilder sb = new StringBuilder();
		sb.append(Constants.URI_BDB_SERVICE);
		sb.append("/");
		sb.append(serviceName);
		return sb.toString();
	}

	/**
	 * サーバ割り当ての親階層URIを取得
	 *   /_bdb/service/{サービス名}/***server
	 * @param serviceName サービス名
	 * @param serverTypeUri サーバタイプ階層
	 * @return URI
	 */
	public static String getServiceServerUri(String serviceName, String serverTypeUri) {
		// /_bdb/service/{サービス名}/***server
		StringBuilder sb = new StringBuilder();
		sb.append(getBDBServiceUri(serviceName));
		sb.append(serverTypeUri);
		return sb.toString();
	}

	/**
	 * サーバ割り当てURIを取得
	 *   /_bdb/service/{サービス名}/***server/{***サーバ名}
	 * @param serviceName サービス名
	 * @param serverTypeUri サーバタイプ階層
	 * @param serverName サーバ名
	 * @return URI
	 */
	public static String getServiceServerUri(String serviceName, String serverTypeUri,
			String serverName) {
		// /_bdb/service/{サービス名}/***server/{***サーバ名}
		StringBuilder sb = new StringBuilder();
		sb.append(getServiceServerUri(serviceName, serverTypeUri));
		sb.append("/");
		sb.append(serverName);
		return sb.toString();
	}

	/**
	 * システム管理サービスのManifestサーバURLを取得.
	 * @return URL
	 */
	public static String getMnfServerUrlOfSystem() {
		Set<String> serverUrls = TaggingEnvUtil.getSystemPropSet(
				BDBClientServerConst.BDBREQUEST_URL_SYSTEM_MANIFEST_PREFIX);
		if (serverUrls == null) {
			return null;
		}
		return serverUrls.iterator().next();
	}

	/**
	 * システム管理サービスのManifestサーバ名・URLリストを取得.
	 * @return サーバ名とURLのMap
	 */
	public static Map<String, String> getMnfServersOfSystem() {
		// キー:プロパティファイルのキー、値:URLリスト
		Map<String, String> tmpMap = TaggingEnvUtil.getSystemPropMap(
				BDBClientServerConst.BDBREQUEST_URL_SYSTEM_MANIFEST_PREFIX);
		// キー:プロパティファイルのキーの接頭辞を除いた部分(サーバ名)、値:URLリスト
		Map<String, String> retMap = null;
		if (tmpMap != null) {
			retMap = new LinkedHashMap<>();
			for (Map.Entry<String, String> mapEntry : tmpMap.entrySet()) {
				String key = mapEntry.getKey();
				String serverName = key.substring(
						BDBClientServerConst.BDBREQUEST_URL_SYSTEM_MANIFEST_PREFIX_LEN);
				retMap.put(serverName, mapEntry.getValue());
			}
		}
		return retMap;
	}

	/**
	 * システム管理サービスのEntryサーバURLリストを取得.
	 * @return URLリスト
	 */
	public static List<String> getEntryServerUrlsOfSystem() {
		Set<String> serverUrls = TaggingEnvUtil.getSystemPropSet(
				BDBClientServerConst.BDBREQUEST_URL_SYSTEM_ENTRY_PREFIX);
		return new ArrayList<String>(serverUrls);
	}

	/**
	 * システム管理サービスのEntryサーバ名・URLリストを取得.
	 * @return サーバ名とURLのMap
	 */
	public static Map<String, String> getEntryServersOfSystem() {
		// キー:プロパティファイルのキー、値:URLリスト
		Map<String, String> tmpMap = TaggingEnvUtil.getSystemPropMap(
				BDBClientServerConst.BDBREQUEST_URL_SYSTEM_ENTRY_PREFIX);
		// キー:プロパティファイルのキーの接頭辞を除いた部分(サーバ名)、値:URLリスト
		Map<String, String> retMap = null;
		if (tmpMap != null) {
			retMap = new LinkedHashMap<>();
			for (Map.Entry<String, String> mapEntry : tmpMap.entrySet()) {
				String key = mapEntry.getKey();
				String serverName = key.substring(
						BDBClientServerConst.BDBREQUEST_URL_SYSTEM_ENTRY_PREFIX_LEN);
				retMap.put(serverName, mapEntry.getValue());
			}
		}
		return retMap;
	}

	/**
	 * システム管理サービスのインデックスサーバURLリストを取得.
	 * @return URLリスト
	 */
	public static List<String> getIdxServerUrlsOfSystem() {
		Set<String> serverUrls = TaggingEnvUtil.getSystemPropSet(
				BDBClientServerConst.BDBREQUEST_URL_SYSTEM_INDEX_PREFIX);
		return new ArrayList<String>(serverUrls);
	}

	/**
	 * システム管理サービスのインデックスサーバ名・URLリストを取得.
	 * @return サーバ名とURLのMap
	 */
	public static Map<String, String> getIdxServersOfSystem() {
		// キー:プロパティファイルのキー、値:URLリスト
		Map<String, String> tmpMap = TaggingEnvUtil.getSystemPropMap(
				BDBClientServerConst.BDBREQUEST_URL_SYSTEM_INDEX_PREFIX);
		// キー:プロパティファイルのキーの接頭辞を除いた部分(サーバ名)、値:URLリスト
		Map<String, String> retMap = null;
		if (tmpMap != null) {
			retMap = new LinkedHashMap<>();
			for (Map.Entry<String, String> mapEntry : tmpMap.entrySet()) {
				String key = mapEntry.getKey();
				String serverName = key.substring(
						BDBClientServerConst.BDBREQUEST_URL_SYSTEM_INDEX_PREFIX_LEN);
				retMap.put(serverName, mapEntry.getValue());
			}
		}
		return retMap;
	}

	/**
	 * システム管理サービスの全文検索インデックスサーバURLリストを取得.
	 * @return URLリスト
	 */
	public static List<String> getFtServerUrlsOfSystem() {
		Set<String> serverUrls = TaggingEnvUtil.getSystemPropSet(
				BDBClientServerConst.BDBREQUEST_URL_SYSTEM_FULLTEXT_PREFIX);
		return new ArrayList<String>(serverUrls);
	}

	/**
	 * システム管理サービスの全文検索インデックスサーバ名・URLリストを取得.
	 * @return サーバ名とURLのMap
	 */
	public static Map<String, String> getFtServersOfSystem() {
		// キー:プロパティファイルのキー、値:URLリスト
		Map<String, String> tmpMap = TaggingEnvUtil.getSystemPropMap(
				BDBClientServerConst.BDBREQUEST_URL_SYSTEM_FULLTEXT_PREFIX);
		// キー:プロパティファイルのキーの接頭辞を除いた部分(サーバ名)、値:URLリスト
		Map<String, String> retMap = null;
		if (tmpMap != null) {
			retMap = new LinkedHashMap<>();
			for (Map.Entry<String, String> mapEntry : tmpMap.entrySet()) {
				String key = mapEntry.getKey();
				String serverName = key.substring(
						BDBClientServerConst.BDBREQUEST_URL_SYSTEM_FULLTEXT_PREFIX_LEN);
				retMap.put(serverName, mapEntry.getValue());
			}
		}
		return retMap;
	}

	/**
	 * システム管理サービスの採番・カウンタサーバURLリストを取得.
	 * @return URLリスト
	 */
	public static List<String> getAlServerUrlsOfSystem() {
		Set<String> serverUrls = TaggingEnvUtil.getSystemPropSet(
				BDBClientServerConst.BDBREQUEST_URL_SYSTEM_ALLOCIDS_PREFIX);
		return new ArrayList<String>(serverUrls);
	}

	/**
	 * システム管理サービスの採番・カウンタサーバ名・URLリストを取得.
	 * @return サーバ名とURLのMap
	 */
	public static Map<String, String> getAlServersOfSystem() {
		// キー:プロパティファイルのキー、値:URLリスト
		Map<String, String> tmpMap = TaggingEnvUtil.getSystemPropMap(
				BDBClientServerConst.BDBREQUEST_URL_SYSTEM_ALLOCIDS_PREFIX);
		// キー:プロパティファイルのキーの接頭辞を除いた部分(サーバ名)、値:URLリスト
		Map<String, String> retMap = null;
		if (tmpMap != null) {
			retMap = new LinkedHashMap<>();
			for (Map.Entry<String, String> mapEntry : tmpMap.entrySet()) {
				String key = mapEntry.getKey();
				String serverName = key.substring(
						BDBClientServerConst.BDBREQUEST_URL_SYSTEM_ALLOCIDS_PREFIX_LEN);
				retMap.put(serverName, mapEntry.getValue());
			}
		}
		return retMap;
	}
	
	/**
	 * サーバ名から各BDBサーバPodのIPアドレスURLを取得.
	 * @param bdbServerName サーバ名
	 * @param serverType BDBサーバタイプ
	 * @param systemContext SystemContext
	 * @return BDBサーバPodのIPアドレスURL
	 */
	private static String getBDBServerUrlFromCmd(String bdbServerName, BDBServerType serverType,
			SystemContext systemContext)
	throws IOException, TaggingException {
		String podId = getPodIp(bdbServerName, serverType, systemContext);
		String servletPath = TaggingEnvUtil.getSystemProp(
				BDBClientServerConst.PROP_BDBSERVER_SERVLETPATH, 
				BDBClientServerConst.BDBSERVER_SERVLETPATH_DEFAULT);
		// URL組み立て
		StringBuilder sb = new StringBuilder();
		sb.append("http://");
		sb.append(podId);
		sb.append(servletPath);
		return sb.toString();
	}

	/**
	 * サーバ名から各BDBサーバPodのIPアドレスを取得.
	 *   ・RedisキャッシュにIPアドレスが存在する場合、その内容を返却する。
	 *   ・ない場合はkubectlコマンド実行し、APサーバPodを抽出してIPアドレスを取得する。
	 *   ※サーバ名の番号と、デプロイメント名の番号が"sv{no}"形式で一致している前提。
	 * @param bdbServerName サーバ名
	 * @param serverType BDBサーバタイプ
	 * @param systemContext SystemContext
	 * @return 指定されたサーバ名のPodのIPアドレス
	 */
	private static String getPodIp(String bdbServerName, BDBServerType serverType,
			SystemContext systemContext)
	throws IOException, TaggingException {
		RequestInfo requestInfo = systemContext.getRequestInfo();

		long startTime = 0;
		if (BDBClientUtil.isEnableAccessLog()) {
			startTime = new Date().getTime();
		}
		// まずはRedisキャッシュにPodIpリストがあれば取得
		String cacheUri = getCacheStringPodipUri(bdbServerName);
		String podIp = systemContext.getCacheString(cacheUri);
		if (BDBClientUtil.isEnableAccessLog()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[getPodIps.accesslog] getCacheString");
			sb.append(LogUtil.getElapsedTimeLog(startTime));
			logger.debug(sb.toString());
		}

		if (!StringUtils.isBlank(podIp)) {
			return podIp;
		}
		
		// サーバ名からデプロイメント名を編集
		String deploymentName = getDeploymentName(bdbServerName, serverType);

		// RedisキャッシュにPodIpが無い場合、kubectlコマンド実行で取得
		if (BDBClientUtil.isEnableAccessLog()) {
			startTime = new Date().getTime();
		}
		List<String> podIps = BDBClientServerCommandUtil.getPodIps(deploymentName, requestInfo);
		if (BDBClientUtil.isEnableAccessLog()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[getPodIps.accesslog] ReflexWebSocketCommandUtil.getPodIps");
			sb.append(LogUtil.getElapsedTimeLog(startTime));
			logger.debug(sb.toString());
		}
		if (podIps == null || podIps.isEmpty()) {
			throw new IllegalStateException("The connection information of the AP server cannot be acquired.");
		}
		podIp = podIps.get(0);	// BDBサーバは1件のみ
		// 取得した値をRedisキャッシュに登録する
		if (BDBClientUtil.isEnableAccessLog()) {
			startTime = new Date().getTime();
		}
		systemContext.setCacheString(cacheUri, podIp, getPodIpsExpireSec());
		if (BDBClientUtil.isEnableAccessLog()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[getPodIps.accesslog] setCacheString");
			sb.append(LogUtil.getElapsedTimeLog(startTime));
			logger.debug(sb.toString());
		}
		// PodのIPアドレスリストを返却
		return podIp;
	}

	/**
	 * PodのIP取得からのデフォルト(秒)を取得
	 * @return PodのIP取得からのデフォルト(秒)
	 */
	private static int getPodIpsExpireSec() {
		return TaggingEnvUtil.getSystemPropInt(BDBClientServerConst.PROP_GETPODIP_EXPIRE_SEC,
				BDBClientServerConst.GETPODIP_EXPIRE_SEC_DEFAULT);
	}
	
	/**
	 * RedisキャッシュへのIPアドレス格納キーを取得.
	 *   /_bdbserver/podip/{サーバ名}
	 * @param serverName サーバ名
	 * @return RedisキャッシュへのIPアドレス格納キー
	 */
	private static String getCacheStringPodipUri(String serverName) {
		return BDBClientServerConst.CACHESTRING_KEY_PODIP_PREFIX + serverName;
	}
	
	/**
	 * サーバ名からデプロイメント名を取得.
	 * @param bdbServerName サーバ名
	 * @param serverType BDBサーバタイプ
	 * @return デプロイメント名
	 */
	private static String getDeploymentName(String bdbServerName, BDBServerType serverType) {
		String serverNo = null;
		int idx = bdbServerName.indexOf("sv");
		if (idx > 1) {
			serverNo = bdbServerName.substring(idx);
		} else {
			serverNo = "";
		}
		StringBuilder sb = new StringBuilder();
		String propKey = null;
		if (serverType.equals(BDBServerType.MANIFEST)) {
			propKey = BDBClientServerConst.PROP_DEPLOYMENT_NAME_MNFSERVER;
		} else if (serverType.equals(BDBServerType.ENTRY)) {
			propKey = BDBClientServerConst.PROP_DEPLOYMENT_NAME_ENTRYSERVER;
		} else if (serverType.equals(BDBServerType.INDEX)) {
			propKey = BDBClientServerConst.PROP_DEPLOYMENT_NAME_IDXSERVER;
		} else if (serverType.equals(BDBServerType.FULLTEXT)) {
			propKey = BDBClientServerConst.PROP_DEPLOYMENT_NAME_FTSERVER;
		} else if (serverType.equals(BDBServerType.ALLOCIDS)) {
			propKey = BDBClientServerConst.PROP_DEPLOYMENT_NAME_ALSERVER;
		}
		sb.append(TaggingEnvUtil.getSystemProp(propKey, ""));
		sb.append("-");
		sb.append(serverNo);
		return sb.toString();
	}

}
