package jp.reflexworks.taggingservice.bdbclient;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.UpdatedInfo;
import jp.reflexworks.taggingservice.sys.SystemAuthentication;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.taskqueue.TaskQueueUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.RetryUtil;

/**
 * フォルダ削除の並列処理.
 */
public class DeleteFolderProcCallable extends ReflexCallable<UpdatedInfo> {

	/** Entry */
	private EntryBase entry;
	/** URI */
	private String uri;
	/** 削除対象ID URIリスト */
	private Map<String, String> deleteFolderIdUris;
	/** 実行元サービス名 */
	private String originalServiceName;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param entry Entry
	 * @param uri URI
	 * @param deleteFolderIdUris 削除対象ID URIリスト
	 * @param originalServiceName 実行元サービス名
	 */
	public DeleteFolderProcCallable(EntryBase entry, String uri,
			Map<String, String> deleteFolderIdUris, String originalServiceName) {
		this.entry = entry;
		this.uri = uri;
		this.deleteFolderIdUris = deleteFolderIdUris;
		this.originalServiceName = originalServiceName;
	}

	/**
	 * 非同期処理登録.
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @return Future
	 */
	public Future<UpdatedInfo> addTask(SystemAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		return (Future<UpdatedInfo>)TaskQueueUtil.addTask(this, 0, auth, requestInfo, connectionInfo);
	}

	/**
	 * フォルダ削除の並列処理.
	 */
	@Override
	public UpdatedInfo call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
					"[deleteFolderProc call] start. uri = " + uri);
		}

		BDBClientUpdateManager updateManager = new BDBClientUpdateManager();
		BDBClientRetrieveManager retrieveManager = new BDBClientRetrieveManager();
		SystemAuthentication auth = (SystemAuthentication)getAuth();

		int numRetries = BDBClientUtil.getBulkPutRetryCount();
		int waitMillis = BDBClientUtil.getBulkPutRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				boolean isParallel = false;	// 指定ディレクトリの2階層以下のため、並列削除しない。
				return updateManager.deleteFolderProc(entry, uri, false, isParallel,  
						deleteFolderIdUris, retrieveManager, originalServiceName, auth,
						getRequestInfo(), getConnectionInfo());

			} catch (IOException e) {
				if (r >= numRetries) {
					// 更新失敗 (ログエントリーは呼び出し元で書く。)
					throw e;
				}
				boolean isRetry = RetryUtil.isRetryError(e, Constants.DELETE);
				if (isRetry) {
					if (logger.isInfoEnabled()) {
						logger.info(LogUtil.getRequestInfoStr(requestInfo) +
								"[deleteFolderProc call] retry: " + r);
					}
					RetryUtil.sleep(waitMillis + r * 10);
				} else {
					// リトライ対象でないエラー
					throw e;
				}
			}
		}
		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}
}
