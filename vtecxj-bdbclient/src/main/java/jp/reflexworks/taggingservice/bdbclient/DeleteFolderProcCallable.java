package jp.reflexworks.taggingservice.bdbclient;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

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

	/**
	 * 並列実行数を制限するセマフォ (JVM全体).
	 * 本処理の中でさらに非同期処理(Entry取得など)の終了を待つため、
	 * 本処理だけで非同期処理プールを使い切るとデッドロックする。並列実行数を制限して防ぐ。
	 */
	private static volatile Semaphore parallelSemaphore;

	/** Entry */
	private EntryBase entry;
	/** URI */
	private String uri;
	/** 削除対象ID URIリスト */
	private Map<String, String> deleteFolderIdUris;
	/** 実行元サービス名 */
	private String originalServiceName;
	/** インデックス更新をまとめて行うバッファ (nullの場合は更新ごとに行う) */
	private DeleteFolderIndexBuffer indexBuffer;
	/** 並列実行数の許可を取得している場合true */
	private volatile boolean hasPermit;

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
		this(entry, uri, deleteFolderIdUris, originalServiceName, null);
	}

	/**
	 * コンストラクタ
	 * @param entry Entry
	 * @param uri URI
	 * @param deleteFolderIdUris 削除対象ID URIリスト
	 * @param originalServiceName 実行元サービス名
	 * @param indexBuffer インデックス更新をまとめて行うバッファ (nullの場合は更新ごとに行う)
	 */
	public DeleteFolderProcCallable(EntryBase entry, String uri,
			Map<String, String> deleteFolderIdUris, String originalServiceName,
			DeleteFolderIndexBuffer indexBuffer) {
		this.entry = entry;
		this.uri = uri;
		this.deleteFolderIdUris = deleteFolderIdUris;
		this.originalServiceName = originalServiceName;
		this.indexBuffer = indexBuffer;
	}

	/**
	 * 非同期処理登録.
	 * 並列実行数が上限に達している場合、空きが出るまで待つ。
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @return Future
	 */
	public Future<UpdatedInfo> addTask(SystemAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		Semaphore semaphore = getParallelSemaphore();
		try {
			semaphore.acquire();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException(e);
		}
		hasPermit = true;
		try {
			return (Future<UpdatedInfo>)TaskQueueUtil.addTask(this, 0, auth, requestInfo, connectionInfo);
		} catch (IOException | TaggingException | RuntimeException e) {
			// 非同期処理を登録できなかった場合は許可を返却する
			releasePermit();
			throw e;
		}
	}

	/**
	 * フォルダ削除の並列処理.
	 */
	@Override
	public UpdatedInfo call() throws IOException, TaggingException {
		try {
			return callProc();
		} finally {
			releasePermit();
		}
	}

	/**
	 * フォルダ削除の並列処理 (本体).
	 * @return 更新情報
	 */
	private UpdatedInfo callProc() throws IOException, TaggingException {
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
						deleteFolderIdUris, retrieveManager, originalServiceName, indexBuffer, auth,
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

	/**
	 * 並列実行数の許可を返却.
	 */
	private synchronized void releasePermit() {
		if (hasPermit) {
			hasPermit = false;
			getParallelSemaphore().release();
		}
	}

	/**
	 * 並列実行数を制限するセマフォを取得.
	 * 設定を読み込んだ後に生成するため、初回呼び出し時に生成する。
	 * @return セマフォ
	 */
	private static Semaphore getParallelSemaphore() {
		Semaphore semaphore = parallelSemaphore;
		if (semaphore == null) {
			synchronized (DeleteFolderProcCallable.class) {
				semaphore = parallelSemaphore;
				if (semaphore == null) {
					semaphore = new Semaphore(BDBClientUtil.getDeleteFolderParallelMax(), true);
					parallelSemaphore = semaphore;
				}
			}
		}
		return semaphore;
	}

}
