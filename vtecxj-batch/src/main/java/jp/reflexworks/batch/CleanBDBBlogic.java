package jp.reflexworks.batch;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ReflexBlogic;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.bdbclient.BDBClientConst;
import jp.reflexworks.taggingservice.bdbclient.BDBResponseInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.RetryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * BDBクリーンリクエスト処理.
 *  ・各BDBサーバにクリーンリクエストを行う。
 *  ・その際、対象のサーバの有効な名前空間をリクエストヘッダに設定する。
 */
public class CleanBDBBlogic implements ReflexBlogic<ReflexContext, Boolean> {

	/** URLパラメータ : クリーン処理 (名前空間削除、BDBクリーナー実行) */
	public static final String PARAM_CLEAN = "_clean";
	/** クリーン処理メソッド */
	public static final String DELETE = Constants.DELETE;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 実行処理.
	 * @param reflexContext ReflexContext
	 * @param args 引数 (ReflexApplication実行時の引数の4番目以降)
	 *             [0]サーバ一覧ファイル名(サーバ名:URL)(フルパス)
	 *             [1]有効なサービスのサーバごとの名前空間一覧格納ディレクトリ(フルパス)
	 *                配下のファイルの内容は(サービス名:名前空間)のリスト
	 */
	public Boolean exec(ReflexContext reflexContext, String[] args) {
		// 引数チェック
		if (args == null || args.length < 2) {
			throw new IllegalArgumentException("引数を指定してください。[0]サーバ一覧ファイル名、[1]有効なサービスのサーバごとの名前空間一覧格納ディレクトリ");
		}
		String serversFilename = args[0];
		if (StringUtils.isBlank(serversFilename)) {
			throw new IllegalArgumentException("引数[0]のサーバ一覧ファイル名を指定してください。");
		}
		String validNamespacesDirname = args[1];
		if (StringUtils.isBlank(validNamespacesDirname)) {
			throw new IllegalArgumentException("引数[1]の有効なサービスのサーバごとの名前空間一覧格納ディレクトリを指定してください。");
		}

		// サービス名がシステム管理サービスでなければエラー
		String systemService = reflexContext.getServiceName();
		if (!systemService.equals(TaggingEnvUtil.getSystemService())) {
			throw new IllegalStateException("Please specify system service for the service name.");
		}

		// SystemContext作成
		SystemContext systemContext = new SystemContext(systemService,
				reflexContext.getRequestInfo(), reflexContext.getConnectionInfo());

		try {
			// 引数[1]有効なサービスのサーバごとの名前空間一覧格納ディレクトリ配下のファイル一覧を取得する。
			File validNamespacesDir = new File(validNamespacesDirname);
			if (!validNamespacesDir.exists() || !validNamespacesDir.isDirectory()) {
				throw new IllegalArgumentException("引数[1]の有効なサービスのサーバごとの名前空間一覧格納ディレクトリが存在しないか、ディレクトリではありません。" + validNamespacesDirname);
			}

			File[] validNamespacesFiles = validNamespacesDir.listFiles();
			if (validNamespacesFiles == null || validNamespacesFiles.length == 0) {
				logger.warn("[exec] Valid namespaces file does not exist. " + validNamespacesDirname);
				return false;
			}

			// サーバ一覧を取得 (サーバ名:URL)
			Map<String, String> servers = VtecxBatchUtil.getKeyValueList(
					serversFilename, VtecxBatchConst.DELIMITER_SERVICE_NAMESPACE);
			if (servers == null || servers.isEmpty()) {
				throw new IllegalStateException("指定されたサーバ一覧にサーバが設定されていません。");
			}

			// ファイル名から引数[0]のファイル内容より、サーバURLを取得する。
			// 各サーバへのリクエストは非同期に行う。
			List<Future<BDBResponseInfo<FeedBase>>> futures = new ArrayList<>();
			for (File file : validNamespacesFiles) {
				// ファイル名の先頭が"."の場合読み飛ばす。
				String serverName = file.getName();
				if (serverName.startsWith(".")) {
					continue;
				}
				String namespaces = editNamespaces(file);
				if (!StringUtils.isBlank(namespaces)) {
					String bdbUrl = servers.get(serverName);
					if (!StringUtils.isBlank(bdbUrl)) {
						String url = getUrlStr(bdbUrl);
						if (logger.isTraceEnabled()) {
							StringBuilder sb = new StringBuilder();
							sb.append("[exec] serverName=");
							sb.append(serverName);
							sb.append(" namespaces=");
							sb.append(namespaces);
							sb.append(" url=");
							sb.append(url);
							logger.debug(sb.toString());
						}

						Future<BDBResponseInfo<FeedBase>> future = 
								VtecxBatchUtil.request(systemContext, DELETE, url, namespaces);
						futures.add(future);
					} else {
						logger.warn("[exec] bdb url is null. serverName=" + serverName + ", serversFilename=" + serversFilename);
					}
				} else {
					logger.warn("[exec] Namespaces could not be obtained. file=" + file.getPath());
				}
			}

			// 処理の終了を待つ (結果は見ない)
			int waitMillis = TaggingEnvUtil.getSystemPropInt(
					BDBClientConst.BULKPUT_SYNC_WAITMILLIS,
					BDBClientConst.BULKPUT_SYNC_WAITMILLIS_DEFAULT);
			for (Future<BDBResponseInfo<FeedBase>> future : futures) {
				while (!future.isDone()) {
					RetryUtil.sleep(waitMillis);
				}
			}

		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (TaggingException e) {
			throw new RuntimeException(e);
		}
		return true;
	}

	/**
	 * URL編集
	 * @param url URL
	 * @return 編集したURL
	 */
	private String getUrlStr(String url) {
		StringBuilder sb = new StringBuilder();
		sb.append(url);
		sb.append("?");
		sb.append(PARAM_CLEAN);
		return sb.toString();
	}

	/**
	 * ファイルの内容「サービス名:名前空間」のリストから、名前空間のカンマ区切り文字を取得する.
	 * @param file ファイル
	 * @return 名前空間のカンマ区切り文字
	 */
	private String editNamespaces(File file)
	throws IOException {
		try {
			Map<String, String> validNamespaces = VtecxBatchUtil.getKeyValueList(
					file, VtecxBatchConst.DELIMITER_SERVICE_NAMESPACE);

			StringBuilder sb = new StringBuilder();
			boolean isFirst = true;
			for (Map.Entry<String, String> mapEntry : validNamespaces.entrySet()) {
				if (isFirst) {
					isFirst = false;
				} else {
					sb.append(VtecxBatchConst.DELIMITER_NAMESPACES);
				}
				String namespace = mapEntry.getValue();
				sb.append(namespace);
			}
			return sb.toString();

		} catch (IllegalStateException e) {
			// "The key-value data does not exist."エラー(ファイルの中身が異なる場合)はwarnログ出力
			String msg = e.getMessage();
			if (msg != null && msg.startsWith("The key-value data does not exist.")) {
				logger.warn(msg);
				return null;
			} else {
				throw e;
			}
		}
	}

}
