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
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * 有効な名前空間一覧を取得し、ファイルに出力する.
 * {サービス名}:{名前空間}
 */
public class WriteNamespacesBlogic implements ReflexBlogic<ReflexContext, Boolean> {

	/** サービス一覧検索URI */
	private static final String URI_SERVICE = Constants.URI_SERVICE;
	/** サービス一覧検索URI+"/"の文字列長 */
	private static final int URI_SERVICE_SLASH_LEN = URI_SERVICE.length() + 1;
	/** 名前空間一覧検索URI */
	private static final String URI_NAMESPACE = Constants.URI_NAMESPACE;
	/** 名前空間一覧検索URI */
	private static final String URI_NAMESPACE_SLASH = URI_NAMESPACE + "/";
	/** サービス名と名前空間の区切り文字 */
	private static final String DELIMITER = VtecxBatchConst.DELIMITER_SERVICE_NAMESPACE;
	/** ステータスの区切り文字 */
	private static final String DELIMITER_STATUS = ",";

	/** アプリケーション名 */
	private static final String APP_NAME = "[WriteNamespacesApp] ";

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 実行処理.
	 * @param reflexContext ReflexContext
	 * @param args 引数 (ReflexApplication実行時の引数の4番目以降)
	 *             [0]出力ファイル名(フルパス)
	 *             [1]抽出対象サービスステータス(複数指定の場合カンマ区切り)
	 */
	public Boolean exec(ReflexContext reflexContext, String[] args) {
		if (args == null) {
			throw new IllegalStateException("引数がnullです。");
		}
		if (args.length < 2) {
			throw new IllegalStateException("引数が不足しています。[0]出力ファイル名(フルパス)、[1]抽出対象サービスステータス");
		}

		String filepath = args[0];
		String statuses = args[1];
		if (StringUtils.isBlank(filepath) || StringUtils.isBlank(statuses)) {
			throw new IllegalStateException("引数を指定してください。[0]出力ファイル名(フルパス)、[1]抽出対象サービスステータス");
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

		try {
			writeNamespace(reflexContext, filepath, validServiceStatuses);
			return true;

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 名前空間出力処理
	 * @param reflexContext ReflexContext
	 * @param filepath 結果出力ファイル名(フルパス)
	 * @param validServiceStatuses 抽出対象サービスステータス
	 */
	public void writeNamespace(ReflexContext reflexContext,
			String filepath, Set<String> validServiceStatuses)
	throws IOException {
		// キー:サービス名、値:名前空間
		Map<String, String> namespaceMap = getNamespaceMap(reflexContext,
				validServiceStatuses);
		if (namespaceMap == null) {
			// getNamespaceMapメソッドでエラーログ出力済み
			return;
		}

		if (logger.isDebugEnabled()) {
			logger.debug(APP_NAME + "output: " + filepath);
		}

		BufferedWriter writer = null;
		try {
			File namespacesFile = new File(filepath);
			writer = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(namespacesFile), Constants.ENCODING));

			// 結果をファイルに出力する
			for (Map.Entry<String, String> mapEntry : namespaceMap.entrySet()) {
				String serviceName = mapEntry.getKey();
				String namespace = mapEntry.getValue();
				writer.write(serviceName + DELIMITER + namespace);
				writer.newLine();
			}

		} finally {
			if (writer != null) {
				writer.close();
			}
		}
	}

	/**
	 * 名前空間一覧をMap形式で取得.
	 * @param reflexContext ReflexContext
	 * @param validServiceStatuses 抽出対象サービスステータス
	 * @return 名前空間一覧 (キー:サービス名、値:名前空間)
	 */
	public Map<String, String> getNamespaceMap(ReflexContext reflexContext,
			Set<String> validServiceStatuses)
	throws IOException {
		GetFeedBlogic getFeedBlogic = new GetFeedBlogic();

		// まずはシステム管理サービスからサービス一覧を取得する。
		// [0]リクエストURI
		// [1]useCache (true/false、デフォルトはtrue)

		// リクエストURIは全件取得にする。
		List<EntryBase> serviceEntries = new ArrayList<>();
		String cursorStr = null;
		do {
			String requestUriService = VtecxBatchUtil.addCursorStr(URI_SERVICE, cursorStr);

			String[] argsServiceFeed = new String[]{requestUriService, "false"};
			FeedBase serviceFeed = getFeedBlogic.exec(reflexContext, argsServiceFeed);
			if (serviceFeed == null) {
				cursorStr = null;
			} else {
				cursorStr = TaggingEntryUtil.getCursorFromFeed(serviceFeed);
				if (serviceFeed.entry != null) {
					serviceEntries.addAll(serviceFeed.entry);
				}
			}
		} while (!StringUtils.isBlank(cursorStr));

		if (serviceEntries.isEmpty()) {
			logger.warn(APP_NAME + "service is nothing.");
			return null;
		}

		// 次に名前空間一覧を取得する。
		// リクエストURIは全件取得にする。
		List<EntryBase> namespaceEntries = new ArrayList<>();
		cursorStr = null;
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
		for (EntryBase serviceEntry : serviceEntries) {
			String serviceStatus = serviceEntry.subtitle;
			if (validServiceStatuses.contains(serviceStatus)) {
				String serviceUri = serviceEntry.getMyUri();
				String serviceName = serviceUri.substring(URI_SERVICE_SLASH_LEN);
				String namespaceUri = URI_NAMESPACE_SLASH + serviceName;
				for (EntryBase namespaceEntry : namespaceEntries) {
					if (namespaceUri.equals(namespaceEntry.getMyUri())) {
						String namespace = namespaceEntry.title;
						if (StringUtils.isBlank(namespace)) {
							logger.warn(APP_NAME + "The namespace does not exist. serviceName=" + serviceName);
							namespace = serviceName;
						} else {
							if (logger.isDebugEnabled()) {
								logger.debug(APP_NAME + "serviceName=" + serviceName + ", namespace=" + namespace);
							}
						}
						namespaceMap.put(serviceName, namespace);
						break;
					}
				}
			}
		}

		if (namespaceMap.isEmpty()) {
			logger.warn(APP_NAME + "There is no valid service.");
			return null;
		}
		return namespaceMap;
	}

}
