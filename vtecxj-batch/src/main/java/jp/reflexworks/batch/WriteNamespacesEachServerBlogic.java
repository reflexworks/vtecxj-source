package jp.reflexworks.batch;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ReflexBlogic;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBServerType;
import jp.reflexworks.taggingservice.requester.BDBClientServerUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * 有効な名前空間一覧を取得し、ファイルに出力する.
 * ファイルの内容: {サービス名}:{名前空間}
 */
public class WriteNamespacesEachServerBlogic implements ReflexBlogic<ReflexContext, Boolean> {

	/** サービス一覧検索URI */
	private static final String URI_SERVICE = Constants.URI_SERVICE;
	/** サービス一覧検索URI+"/"の文字列長 */
	private static final int URI_SERVICE_SLASH_LEN = URI_SERVICE.length() + 1;
	/** 名前空間一覧検索URI */
	private static final String URI_NAMESPACE = Constants.URI_NAMESPACE;
	/** 名前空間一覧検索URI+"/"の文字列長 */
	private static final int URI_NAMESPACE_SLASH_LEN = URI_NAMESPACE.length() + 1;
	/** サービスのサーバ名一覧検索URI+"/"の文字列長 */
	private static final int URI_BDB_SERVICE_SLASH_LEN = Constants.URI_BDB_SERVICE.length() + 1;
	/** サービス名と名前空間の区切り文字 */
	private static final String DELIMITER = VtecxBatchConst.DELIMITER_SERVICE_NAMESPACE;
	/** ステータスの区切り文字 */
	private static final String DELIMITER_STATUS = ",";

	/** アプリケーション名 */
	private static final String APP_NAME = "[WriteNamespacesEachServerApp] ";

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 実行処理.
	 * @param reflexContext ReflexContext
	 * @param args 引数 (ReflexApplication実行時の引数の4番目以降)
	 *             [0]出力ディレクトリ(フルパス)
	 *             [1]抽出対象サービスステータス(複数指定の場合カンマ区切り)
	 *             [2]サーバタイプ (`mnf`,`entry`,`idx`,`ft`,`al`のいずれか)
	 */
	public Boolean exec(ReflexContext reflexContext, String[] args) {
		if (args == null) {
			throw new IllegalStateException("引数がnullです。");
		}
		if (args.length < 3) {
			throw new IllegalStateException("引数が不足しています。[0]出力ディレクトリ(フルパス)、[1]抽出対象サービスステータス、[2]サーバタイプ");
		}

		String dirpath = args[0];
		String statuses = args[1];
		String serverTypeStr = args[2];
		if (StringUtils.isBlank(dirpath) || StringUtils.isBlank(statuses) || StringUtils.isBlank(serverTypeStr)) {
			throw new IllegalStateException("引数を指定してください。[0]出力ディレクトリ(フルパス)、[1]抽出対象サービスステータス、[2]サーバタイプ");
		}

		Set<String> validServiceStatuses = new HashSet<String>();
		String[] statusArray = statuses.split(DELIMITER_STATUS);
		for (int i = 0; i < statusArray.length; i++) {
			String status = statusArray[i];
			if (!StringUtils.isBlank(status)) {
				validServiceStatuses.add(status);
			} else {
				throw new IllegalStateException("引数の [1]抽出対象サービスステータス が空白またはnullです。 index = " + i);
			}
		}
		if (validServiceStatuses.isEmpty()) {
			throw new IllegalStateException("引数の [1]抽出対象サービスステータス が設定されていません。");
		}

		BDBServerType serverType = BDBClientServerUtil.getServerTypeByStr(serverTypeStr);
		if (serverType == null) {
			throw new IllegalStateException("引数[2]サーバタイプには、 mnf,entry,idx,ft,al のいずれかを指定してください。");
		}

		try {
			// 有効なサービス一覧を取得
			Set<String> validServices = getValidService(reflexContext, validServiceStatuses);
			// 対象サービスのサーバごとのサービス一覧を取得
			Map<String, Set<String>> servicesEachServerMap = getServicesEachServerMap(
					reflexContext, validServices, serverType);
			// 名前空間出力
			writeNamespace(reflexContext, dirpath, validServices, servicesEachServerMap);

			return true;

		} catch (IOException | TaggingException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 名前空間出力処理
	 * @param reflexContext ReflexContext
	 * @param dirpath 結果出力ディレクトリ(フルパス)
	 * @param validServices 抽出対象サービス一覧
	 * @param servicesEachServerMap サーバごとのサービス一覧Map
	 */
	public void writeNamespace(ReflexContext reflexContext, String dirpath,
			Set<String> validServices, Map<String, Set<String>> servicesEachServerMap)
	throws IOException {
		// キー:サービス名、値:名前空間
		Map<String, String> namespaceMap = getNamespaceMap(reflexContext,
				validServices);
		if (namespaceMap == null) {
			// getNamespaceMapメソッドでエラーログ出力済み
			return;
		}

		// サーバ一覧を取得.
		for (Map.Entry<String, Set<String>> mapEntry : servicesEachServerMap.entrySet()) {
			String serverName = mapEntry.getKey();
			Set<String> services = mapEntry.getValue();
			writeNamespaceProc(dirpath, serverName, services, namespaceMap);
		}
	}

	/**
	 * サーバ名ごとのサービス・名前空間をファイルに出力.
	 * @param dirpath 指定されたディレクトリ
	 * @param serverName サーバ名
	 * @param services サーバ名のサーバが割り当てられたサービス一覧
	 * @param namespaceMap 名前空間一覧
	 */
	private void writeNamespaceProc(String dirpath, String serverName,
			Set<String> services, Map<String, String> namespaceMap)

	throws IOException {
		String filepath = editFilepath(dirpath, serverName);
		if (logger.isTraceEnabled()) {
			logger.debug(APP_NAME + "output: " + filepath);
		}
		File namespacesFile = new File(filepath);
		try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
				new FileOutputStream(namespacesFile), Constants.ENCODING))) {

			// 結果をファイルに出力する
			for (String serviceName : services) {
				String namespace = namespaceMap.get(serviceName);
				writer.write(serviceName + DELIMITER + namespace);
				writer.newLine();
			}
		}
	}

	/**
	 * 名前空間一覧をMap形式で取得.
	 * @param reflexContext ReflexContext
	 * @param validServices 抽出対象サービス一覧
	 * @return 名前空間一覧 (キー:サービス名、値:名前空間)
	 */
	public Map<String, String> getNamespaceMap(ReflexContext reflexContext,
			Set<String> validServices)
	throws IOException {
		GetFeedBlogic getFeedBlogic = new GetFeedBlogic();

		// 名前空間一覧を取得する。
		// リクエストURIは全件取得にする。
		List<EntryBase> namespaceEntries = new ArrayList<>();
		String cursorStr = null;
		do {
			String requestUriNamespace = VtecxBatchUtil.addCursorStr(URI_NAMESPACE, cursorStr);
			String[] argsNamespaceFeed = new String[]{requestUriNamespace, "false"};
			FeedBase namespaceFeed = getFeedBlogic.exec(reflexContext, argsNamespaceFeed);
			if (namespaceFeed == null) {
				cursorStr = null;
			} else {
				cursorStr = TaggingEntryUtil.getCursorFromFeed(namespaceFeed);
				if (namespaceFeed.entry != null) {
					namespaceEntries.addAll(namespaceFeed.entry);
				}
			}
		} while (!StringUtils.isBlank(cursorStr));

		if (namespaceEntries.isEmpty()) {
			logger.warn(APP_NAME + "namespace is nothing.");
			return null;
		}

		// キー:サービス名、値:名前空間
		Map<String, String> namespaceMap = new LinkedHashMap<String, String>();
		for (EntryBase namespaceEntry : namespaceEntries) {
			String namespaceUri = namespaceEntry.getMyUri();
			String serviceName = namespaceUri.substring(URI_NAMESPACE_SLASH_LEN);
			if (validServices.contains(serviceName)) {
				String namespace = namespaceEntry.title;
				if (!StringUtils.isBlank(namespace)) {
					namespaceMap.put(serviceName, namespace);
				} else {
					logger.warn(APP_NAME + "The namespace does not exist. serviceName=" + serviceName);
				}
			}
		}

		if (namespaceMap.isEmpty()) {
			logger.warn(APP_NAME + "There is no valid service. (namespace)");
			return null;
		}
		return namespaceMap;
	}

	/**
	 * 有効なサービス一覧を取得する.
	 * @param reflexContext ReflexContext
	 * @param validServiceStatuses 有効なサービスステータス
	 * @return 有効なサービス一覧
	 */
	private Set<String> getValidService(ReflexContext reflexContext,
			Set<String> validServiceStatuses) {
		GetFeedBlogic getFeedBlogic = new GetFeedBlogic();

		// まずはシステム管理サービスからサービス一覧を取得する。
		// [0]リクエストURI
		// [1]useCache (true/false、デフォルトはtrue)

		// リクエストURIは全件取得にする。
		Set<String> validServices = new HashSet<>();
		boolean existData = false;
		String cursorStr = null;
		do {
			String requestUriService = VtecxBatchUtil.addCursorStr(URI_SERVICE, cursorStr);

			String[] argsServiceFeed = new String[]{requestUriService, "false"};
			FeedBase serviceFeed = getFeedBlogic.exec(reflexContext, argsServiceFeed);
			if (serviceFeed == null) {
				cursorStr = null;
			} else {
				cursorStr = TaggingEntryUtil.getCursorFromFeed(serviceFeed);
				if (serviceFeed.entry != null && !serviceFeed.entry.isEmpty()) {
					existData = true;

					// 有効なサービス名を抽出
					for (EntryBase serviceEntry : serviceFeed.entry) {
						String serviceStatus = serviceEntry.subtitle;
						if (validServiceStatuses.contains(serviceStatus)) {
							String serviceUri = serviceEntry.getMyUri();
							String serviceName = serviceUri.substring(URI_SERVICE_SLASH_LEN);
							validServices.add(serviceName);
						}
					}
				}
			}

		} while (!StringUtils.isBlank(cursorStr));

		if (!existData) {
			logger.warn(APP_NAME + "service is nothing.");
			return null;
		}

		return validServices;
	}

	/**
	 * 対象サービスのサーバをMap形式で取得.
	 * @param reflexContext ReflexContext
	 * @param validServiceStatuses 抽出対象サービス一覧
	 * @return 対象サービスのサーバ一覧 (キー:サービス名、値:サーバ名リスト(BDBサーバ、全文検索サーバ)
	 */
	public Map<String, Set<String>> getServiceServersMap(ReflexContext reflexContext,
			Set<String> validServices)
	throws IOException {
		GetFeedBlogic getFeedBlogic = new GetFeedBlogic();

		// システム管理サービスからサービスのBDB情報一覧を取得する。
		// [0]リクエストURI
		// [1]useCache (true/false、デフォルトはtrue)

		// リクエストURIは全件取得にする。
		List<EntryBase> bdbServiceEntries = new ArrayList<>();
		String cursorStr = null;
		do {
			String requestUriService = VtecxBatchUtil.addCursorStr(URI_SERVICE, cursorStr);

			String[] argsServiceFeed = new String[]{requestUriService, "false"};
			FeedBase bdbServiceFeed = getFeedBlogic.exec(reflexContext, argsServiceFeed);
			if (bdbServiceFeed == null) {
				cursorStr = null;
			} else {
				cursorStr = TaggingEntryUtil.getCursorFromFeed(bdbServiceFeed);
				if (bdbServiceFeed.entry != null) {
					bdbServiceEntries.addAll(bdbServiceFeed.entry);
				}
			}
		} while (!StringUtils.isBlank(cursorStr));

		if (bdbServiceEntries.isEmpty()) {
			logger.warn(APP_NAME + "bdb service is nothing.");
			return null;
		}

		Map<String, Set<String>> serviceServersMap = new LinkedHashMap<>();
		for (EntryBase bdbServiceEntry : bdbServiceEntries) {
			String serviceUri = bdbServiceEntry.getMyUri();
			String serviceName = serviceUri.substring(URI_BDB_SERVICE_SLASH_LEN);
			if (validServices.contains(serviceName)) {
				Set<String> servers = new HashSet<>();
				String bdbServerName = bdbServiceEntry.title;
				if (!StringUtils.isBlank(bdbServerName)) {
					servers.add(bdbServerName);
				}
				String ftServerName = bdbServiceEntry.subtitle;
				if (!StringUtils.isBlank(ftServerName)) {
					servers.add(ftServerName);
				}
				if (!servers.isEmpty()) {
					serviceServersMap.put(serviceName, servers);
				} else {
					logger.warn(APP_NAME + "The server does not exist. serviceName=" + serviceName);
				}
			}
		}

		if (serviceServersMap.isEmpty()) {
			logger.warn(APP_NAME + "There is no valid service. (bdb service)");
			return null;
		}
		return serviceServersMap;
	}

	/**
	 * 対象サービスのサーバをMap形式で取得.
	 * @param reflexContext ReflexContext
	 * @param validServices 抽出対象サービス一覧
	 * @param serverType サーバタイプ
	 * @return 対象サービスのサーバ一覧
	 *         キー:サーバ名(BDB・全文検索・インデックス)、値:サービス名リスト
	 */
	private Map<String, Set<String>> getServicesEachServerMap(ReflexContext reflexContext,
			Set<String> validServices, BDBServerType serverType)
	throws IOException, TaggingException {
		GetFeedBlogic getFeedBlogic = new GetFeedBlogic();

		// システム管理サービスからサービスのBDB情報一覧を取得する。
		// [0]リクエストURI
		// [1]useCache (true/false、デフォルトはtrue)

		// サーバ名取得先
		//    /_bdb/service/{サービス名}/{mnf|entry|idx|ft|al}server 配下をfeed検索

		// 1. デフォルトサーバ取得
		//    /_bdb/service 配下をfeed検索
		// 2. 分散インデックスを取得
		//    /_bdb/service/{サービス名}/ftserver 配下をfeed検索
		//    /_bdb/service/{サービス名}/idxserver 配下をfeed検索

		// リクエストURIは全件取得にする。
		String serverTypeUri = BDBClientServerUtil.getServerTypeUri(serverType);
		Map<String, Set<String>> serverServiceMap = new LinkedHashMap<>();
		String systemService = TaggingEnvUtil.getSystemService();

		for (String serviceName : validServices) {
			if (systemService.equals(serviceName)) {
				// システム管理サービスはサーバ情報をプロパティ設定から取得
				getAndSetServerOfSystemServiceMap(serverServiceMap, serverType, serverTypeUri,
						reflexContext);
			} else {
				// 一般サービス
				String bdbServiceServerUri = BDBClientServerUtil.getServiceServerUri(serviceName,
						serverTypeUri);
				List<EntryBase> serviceServerEntries = new ArrayList<>();
				String cursorStr = null;
				do {
					String requestUriService = VtecxBatchUtil.addCursorStr(bdbServiceServerUri, cursorStr);

					String[] argsServiceServerFeed = new String[]{requestUriService, "false"};
					FeedBase serviceServerFeed = getFeedBlogic.exec(reflexContext, argsServiceServerFeed);
					if (!TaggingEntryUtil.isExistData(serviceServerFeed)) {
						logger.warn(APP_NAME + "bdb service is nothing. serverType=" + serverType.name());
						return null;
					}

					if (serviceServerFeed == null) {
						cursorStr = null;
					} else {
						cursorStr = TaggingEntryUtil.getCursorFromFeed(serviceServerFeed);
						if (serviceServerFeed.entry != null && !serviceServerFeed.entry.isEmpty()) {
							serviceServerEntries.addAll(serviceServerFeed.entry);

							for (EntryBase serviceServerEntry : serviceServerFeed.entry) {
								// selfidがサーバ名
								String uri = serviceServerEntry.getMyUri();
								String bdbServerName = TaggingEntryUtil.getSelfidUri(uri);
								setServerServiceMap(serverServiceMap, serviceName, bdbServerName);
							}

						}
					}
				} while (!StringUtils.isBlank(cursorStr));
			}
		}

		if (serverServiceMap.isEmpty()) {
			logger.warn(APP_NAME + "There is no valid service. (bdb service)");
			return null;
		}
		return serverServiceMap;
	}

	/**
	 * サーバごとのサービス名リストにサービス名を加える.
	 * 指定されたサーバのサービス名リストが未登録の場合は追加する。
	 * @param serverServiceMap サーバごとのサービス名リスト
	 * @param serviceName サービス名
	 * @param serverName サーバ名
	 */
	private void setServerServiceMap(Map<String, Set<String>> serverServiceMap,
			String serviceName, String serverName) {
		if (StringUtils.isBlank(serverName)) {
			return;
		}
		Set<String> services = serverServiceMap.get(serverName);
		if (services == null) {
			services = new HashSet<>();
			serverServiceMap.put(serverName, services);
		}
		services.add(serviceName);
	}

	/**
	 * システム管理サービスのBDBサーバ名を取得し、Mapに設定する.
	 * @param serverServiceMap サーバごとのサービスリストMap
	 * @param serverType サーバタイプ
	 * @param serverTypeUri サーバタイプURI
	 * @param reflexContext ReflexContext
	 */
	private void getAndSetServerOfSystemServiceMap(Map<String, Set<String>> serverServiceMap,
			BDBServerType serverType, String serverTypeUri, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String systemService = reflexContext.getServiceName();
		Map<String, String> serverInfos = null;
		// プロパティからサーバURIを取得
		if (BDBServerType.MANIFEST == serverType) {
			serverInfos = BDBClientServerUtil.getMnfServersOfSystem();
		} else if (BDBServerType.ENTRY == serverType) {
			serverInfos = BDBClientServerUtil.getEntryServersOfSystem();
		} else if (BDBServerType.INDEX == serverType) {
			serverInfos = BDBClientServerUtil.getIdxServersOfSystem();
		} else if (BDBServerType.FULLTEXT == serverType) {
			serverInfos = BDBClientServerUtil.getFtServersOfSystem();
		} else {	// ALLOCIDS
			serverInfos = BDBClientServerUtil.getAlServersOfSystem();
		}

		if (serverInfos != null) {
			for (Map.Entry<String, String> mapEntry : serverInfos.entrySet()) {
				String serverName = mapEntry.getKey();
				Set<String> services = serverServiceMap.get(serverName);
				if (services == null) {
					services = new HashSet<>();
					serverServiceMap.put(serverName, services);
				}
				services.add(systemService);
			}
		}
	}

	/**
	 * 出力ファイルパスを編集.
	 * ディレクトリ + サーバ名
	 * @param dirpath ディレクトリ
	 * @param serverName サーバ名
	 * @return 出力ファイルパス
	 */
	private String editFilepath(String dirpath, String serverName) {
		StringBuilder sb = new StringBuilder();
		sb.append(TaggingEntryUtil.editSlash(dirpath));
		sb.append(serverName);
		return sb.toString();
	}

}
