package jp.reflexworks.batch;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ReflexApplication;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBServerType;
import jp.reflexworks.taggingservice.requester.BDBClientServerUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * サーバ一覧を取得し、ファイルに出力する.
 * {サーバ名}:{URL}
 * BDBサーバ、全文検索インデックスサーバ、インデックスサーバの取得に実行。
 */
public class WriteBDBServersApp {

	/**
	 * バッチ用起動メソッド.
	 * @param args (1) プロパティファイル
	 *             (2) サービス名
	 *             (3) ビジネスロジックのクラス名
	 *             (4〜) ビジネスロジックへの引き渡し文字列
	 */

	/** プロパティファイル名 */
	private static final String PROPERTY_FILE_NAME = VtecxBatchConst.PROPERTY_FILE_NAME;
	/** システムサービス名 */
	private static final String SYSTEM_SERVICE = VtecxBatchConst.SYSTEM_SERVICE;

	/** ビジネスロジッククラス名 : Feed検索 */
	private static final String CLASS_NAME_GETFEED = "jp.reflexworks.batch.GetFeedBlogic";

	/** クラス名 */
	private static final String APP_NAME = "[WriteBDBServersApp]";

	/** サーバ名とURLの区切り文字 */
	private static final String DELIMITER = VtecxBatchConst.DELIMITER_SERVER_URL;

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(WriteBDBServersApp.class);

	/**
	 * main
	 * @param args 引数
	 *             [0]出力ファイル名(フルパス)
	 *             [1]サーバタイプ(mnf,entry,idx,ft,al)
	 */
	public static void main(String[] args) {
		try {
			// 引数チェック
			if (args == null || args.length < 2) {
				throw new IllegalArgumentException("引数を指定してください。[0]出力ファイル名(フルパス)、[1]サーバタイプ(mnf,entry,idx,ft,al)");
			}
			String filepath = args[0];
			if (StringUtils.isBlank(filepath)) {
				throw new IllegalArgumentException("引数を指定してください。[0]出力ファイル名(フルパス)");
			}
			String serverTypeStr = args[1];
			if (StringUtils.isBlank(serverTypeStr)) {
				throw new IllegalArgumentException("引数を指定してください。[1]サーバタイプ(mnf,entry,idx,ft,al)");
			}
			BDBServerType serverType = BDBClientServerUtil.getServerTypeByStr(serverTypeStr);	// サーバタイプチェック
			if (serverType == null) {
				throw new IllegalArgumentException("引数[1]サーバタイプには (mnf,entry,idx,ft,al) のいずれかを指定してください。");
			}

			WriteBDBServersApp app = new WriteBDBServersApp();
			app.writeBDBServers(filepath, serverType);

		} catch (Throwable e) {
			logger.error(APP_NAME + " Error occured. " + e.getClass().getName(), e);
		}

		System.exit(0);
	}

	/**
	 * BDBサーバ一覧出力処理
	 * @param filepath 結果出力ファイル名(フルパス)
	 * @param serverType mnf, entry, idx, ft or al
	 */
	public void writeBDBServers(String filepath, BDBServerType serverType)
	throws IOException {
		// キー:BDBサーバ名、値:URL
		Map<String, String> bdbServerMap = getBDBServerMap(serverType);
		if (bdbServerMap == null) {
			// getBDBServerMapメソッドでエラーログ出力済み
			return;
		}

		if (logger.isTraceEnabled()) {
			logger.debug(APP_NAME + "output: " + filepath);
		}

		BufferedWriter writer = null;
		try {
			File namespacesFile = new File(filepath);
			writer = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(namespacesFile), Constants.ENCODING));

			// 結果をファイルに出力する
			for (Map.Entry<String, String> mapEntry : bdbServerMap.entrySet()) {
				String serverName = mapEntry.getKey();
				String url = mapEntry.getValue();
				writer.write(serverName + DELIMITER + url);
				writer.newLine();
			}

		} finally {
			if (writer != null) {
				writer.close();
			}
		}
	}

	/**
	 * BDBサーバ一覧をMap形式で取得.
	 * @param serverType mnf, entry, idx, ft or al
	 * @return BDBサーバ一覧 (キー:BDBサーバ名、値:URL)
	 */
	public Map<String, String> getBDBServerMap(BDBServerType serverType)
	throws IOException {
		ReflexApplication<FeedBase> reflexApp = new ReflexApplication<FeedBase>();

		// まずはシステム管理サービスからサービス一覧を取得する。
		// [0]リクエストURI
		// [1]useCache (true/false、デフォルトはtrue)

		// リクエストURIは全件取得にする。
		String tmpUri = null;
		if (BDBServerType.MANIFEST == serverType) {
			tmpUri = Constants.URI_BDB_MNFSERVER;
		} else if (BDBServerType.ENTRY == serverType) {
			tmpUri = Constants.URI_BDB_ENTRYSERVER;
		} else if (BDBServerType.INDEX == serverType) {
			tmpUri = Constants.URI_BDB_IDXSERVER;
		} else if (BDBServerType.FULLTEXT == serverType) {
			tmpUri = Constants.URI_BDB_FTSERVER;
		} else {	// allocids
			tmpUri = Constants.URI_BDB_ALSERVER;
		}

		List<EntryBase> bdbServerEntries = new ArrayList<>();
		String cursorStr = null;
		do {
			String requestUriBDBServer = VtecxBatchUtil.addCursorStr(tmpUri, cursorStr);
			String[] argsServiceFeed = new String[]{PROPERTY_FILE_NAME, SYSTEM_SERVICE,
					CLASS_NAME_GETFEED, requestUriBDBServer, "true"};
			if (logger.isTraceEnabled()) {
				logger.debug(APP_NAME + "getFeed (bdb server) start");
			}
			FeedBase bdbServerFeed = reflexApp.exec(argsServiceFeed);
			if (logger.isTraceEnabled()) {
				logger.debug(APP_NAME + "getFeed (bdb server) end");
			}

			if (bdbServerFeed == null) {
				cursorStr = null;
			} else {
				cursorStr = TaggingEntryUtil.getCursorFromFeed(bdbServerFeed);
				if (bdbServerFeed.entry != null) {
					bdbServerEntries.addAll(bdbServerFeed.entry);
				}
			}
		} while (!StringUtils.isBlank(cursorStr));

		if (bdbServerEntries.isEmpty()) {
			logger.warn(APP_NAME + "server is nothing. serverType=" + serverType);
			return null;
		}

		// キー:BDBサーバ名、値:URL
		Map<String, String> bdbServerMap = new LinkedHashMap<String, String>();
		for (EntryBase bdbServerEntry : bdbServerEntries) {
			// selfidがBDBサーバ名
			String serverName = TaggingEntryUtil.getSelfidUri(bdbServerEntry.getMyUri());
			// titleにURL
			String url = bdbServerEntry.title;

			bdbServerMap.put(serverName, url);
		}

		if (bdbServerMap.isEmpty()) {
			logger.warn(APP_NAME + "There is no server. serverType=" + serverType);
			return null;
		}
		return bdbServerMap;
	}
}
