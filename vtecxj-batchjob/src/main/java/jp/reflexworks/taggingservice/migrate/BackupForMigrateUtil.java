package jp.reflexworks.taggingservice.migrate;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.bdbclient.BDBResponseInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBResponseType;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBServerType;
import jp.reflexworks.taggingservice.requester.BDBClientServerManager;
import jp.reflexworks.taggingservice.requester.BDBClientServerUtil;
import jp.reflexworks.taggingservice.requester.BDBRequester;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.DateUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * データ移行の前のBDBバックアップ
 */
public class BackupForMigrateUtil {

	/** ロガー. */
	private static final Logger logger = LoggerFactory.getLogger(BackupForMigrateUtil.class);

	/**
	 * 現在日時文字列を取得.
	 * @return 現在日時文字列
	 */
	public static String getDatetimeStr() {
		return DateUtil.getDateTimeFormat(new Date(), BackupForMigrateConst.DATE_FORMAT);
	}

	/**
	 * BDBバックアップ.
	 * BDBサーバにバックアップリクエストを送信する。
	 * 各サーバへのリクエストは並列に行う。
	 * @param datetimeStr 現在日時文字列
	 * @param namespace 名前空間
	 * @param serviceName サービス名
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public static void backupBDB(String datetimeStr,
			String namespace, String serviceName, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		Map<String, Future<Boolean>> futures = new HashMap<>();
		futures.putAll(backupBDBEachServerType(BDBServerType.MANIFEST, datetimeStr, namespace, serviceName, auth, requestInfo, connectionInfo));
		futures.putAll(backupBDBEachServerType(BDBServerType.ENTRY, datetimeStr, namespace, serviceName, auth, requestInfo, connectionInfo));
		futures.putAll(backupBDBEachServerType(BDBServerType.INDEX, datetimeStr, namespace, serviceName, auth, requestInfo, connectionInfo));
		futures.putAll(backupBDBEachServerType(BDBServerType.FULLTEXT, datetimeStr, namespace, serviceName, auth, requestInfo, connectionInfo));
		futures.putAll(backupBDBEachServerType(BDBServerType.ALLOCIDS, datetimeStr, namespace, serviceName, auth, requestInfo, connectionInfo));

		// 非同期処理の終了を待つ
		for (Map.Entry<String, Future<Boolean>> mapEntry : futures.entrySet()) {
			String bdbServerName = mapEntry.getKey();
			Future<Boolean> future = mapEntry.getValue();
			try {
				future.get();

			} catch (ExecutionException e) {
				Throwable cause = e.getCause();
				if (logger.isDebugEnabled()) {
					StringBuilder sb = new StringBuilder();
					sb.append(LogUtil.getRequestInfoStr(requestInfo));
					sb.append("[backupBDB] bdbServerName=");
					sb.append(bdbServerName);
					sb.append(" ExecutionException: ");
					sb.append(e.getMessage());
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
					sb.append("[backupBDB] bdbServerName=");
					sb.append(bdbServerName);
					sb.append(" InterruptedException: ");
					sb.append(e.getMessage());
					logger.debug(sb.toString());
				}
				throw new IOException(e);
			}
		}
	}

	/**
	 * BDBバックアップ.
	 * BDBサーバにバックアップコマンドリモート実行する。
	 * @param serverType BDBサーバタイプ
	 * @param datetimeStr 現在日時文字列
	 * @param namespace 名前空間
	 * @param serviceName サービス名
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return Futureリスト
	 */
	private static Map<String, Future<Boolean>> backupBDBEachServerType(BDBServerType serverType,
			String datetimeStr, String namespace, String serviceName,
			ReflexAuthentication auth, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		Map<String, Future<Boolean>> futures = new HashMap<>();
		BDBClientServerManager bdbclientServerManager = new BDBClientServerManager();
		List<String> bdbServerNames = bdbclientServerManager.getBDBServerNames(serviceName,
				serverType, requestInfo, connectionInfo);

		if (bdbServerNames != null) {
			for (String bdbServerName : bdbServerNames) {
				// URLを取得
				String bdbServerUrl = bdbclientServerManager.getBDBServerUrl(
						bdbServerName, serverType, requestInfo, connectionInfo);
				// 並列処理
				BackupForMigrateCallable callable = new BackupForMigrateCallable(
						bdbServerUrl, serverType, bdbServerName, datetimeStr, namespace,
						serviceName);
				Future<Boolean> future = MigrateUtil.addTask(callable, auth,
						requestInfo, connectionInfo);
				futures.put(bdbServerName, future);
			}
		}
		return futures;
	}

	/**
	 * BDBバックアップ.
	 * BDBサーバへバックアップリクエストを行う。
	 * @param serverUrl BDBサーバURL
	 * @param serverType BDBサーバタイプ
	 * @param bdbServerName BDBサーバ名
	 * @param datetimeStr 現在日時文字列
	 * @param namespace 名前空間
	 * @param serviceName サービス名
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	static void backupBDBProc(String serverUrl, BDBServerType serverType,
			String bdbServerName, String datetimeStr,
			String namespace, String serviceName, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String method = BackupForMigrateConst.METHOD;
		// BDBサーバへのURLの組み立て
		StringBuilder bdbSb = new StringBuilder();
		bdbSb.append(serverUrl);
		bdbSb.append("?");
		bdbSb.append(RequestParam.PARAM_BACKUP);
		String backupServerUrl = bdbSb.toString();

		// Cloud StorageへのURLの組み立て
		String serverTypeStr = BDBClientServerUtil.getServerTypeStr(serverType);
		String storageUrl = editStorageUrl(datetimeStr, namespace,
				serverTypeStr, bdbServerName);
		FeedBase reqFeed = TaggingEntryUtil.createAtomFeed();
		reqFeed.title = storageUrl;

		FeedTemplateMapper mapper = TaggingEnvUtil.getAtomResourceMapper();

		BDBRequester<FeedBase> bdbRequester = new BDBRequester<>(BDBResponseType.FEED);
		BDBResponseInfo<FeedBase> bdbResponseInfo = bdbRequester.requestByUrl(
				backupServerUrl, method, reqFeed, namespace, null, null, null, null, mapper,
				serviceName, requestInfo, connectionInfo);

		// 結果をログ出力
		if (logger.isDebugEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[backupBDBProc] URL=");
			sb.append(serverUrl);
			sb.append(" status=");
			sb.append(bdbResponseInfo.status);
			if (bdbResponseInfo.data != null && !StringUtils.isBlank(bdbResponseInfo.data.title)) {
				sb.append(", message=");
				sb.append(bdbResponseInfo.data.title);
			}
			logger.debug(sb.toString());
		}
	}

	/**
	 * Cloud Storage バックアップ保存先URL.
	 * gs://{bucketName}/{バックアップディレクトリ名}/{stage}/{yyyyMMddHHmmss}/{namespace}/{mnf|entry|idx|ft|al}/{サーバ名}
	 * @param datetimeStr 現在日時文字列
	 * @param namespace 名前空間
	 * @param serverTypeStr サーバタイプ文字列 (mnf|entry|idx|ft|al)
	 * @param serverName サーバ名
	 * @return Cloud Storage バックアップ保存先URL
	 */
	private static String editStorageUrl(String datetimeStr, String namespace,
			String serverTypeStr, String serverName) {
		String bucketName = TaggingEnvUtil.getSystemProp(
				BackupForMigrateConst.PROP_BDBBACKUP_BUCKET, null);
		if (StringUtils.isBlank(bucketName)) {
			throw new IllegalStateException("No system settings. " + BackupForMigrateConst.PROP_BDBBACKUP_BUCKET);
		}
		String backupDir = TaggingEnvUtil.getSystemProp(
				BackupForMigrateConst.PROP_BDBBACKUP_DIRECTORY,
				BackupForMigrateConst.BDBBACKUP_DIRECTORY_DEFAULT);
		String stage = TaggingEnvUtil.getStage();

		StringBuilder sb = new StringBuilder();
		sb.append("gs://");
		sb.append(bucketName);
		sb.append("/");
		sb.append(backupDir);
		sb.append("/");
		sb.append(stage);
		sb.append("/");
		sb.append(datetimeStr);
		sb.append("/");
		sb.append(namespace);
		sb.append("/");
		sb.append(serverTypeStr);
		sb.append("/");
		sb.append(serverName);
		return sb.toString();
	}

}
