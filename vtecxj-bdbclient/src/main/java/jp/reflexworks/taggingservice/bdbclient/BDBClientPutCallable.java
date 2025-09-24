package jp.reflexworks.taggingservice.bdbclient;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.UpdatedInfo;
import jp.reflexworks.taggingservice.plugin.DatastoreManager;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.RetryUtil;

/**
 * 一括更新非同期処理.
 */
public class BDBClientPutCallable extends ReflexCallable<List<UpdatedInfo>> {

	/** Feed */
	private FeedBase feed;
	/** 親階層(自動採番登録時に使用) */
	private String parentUri;
	/** インデックスをすべて更新するオプション */
	private boolean updateAllIndex;
	/** 元のサービス名 */
	private String originalServiceName;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param feed Feed (1スレッドあたりの更新分)
	 * @param parentUri 親URI。自動採番登録時に使用。
	 * @param updateAllIndex インデックスをすべて更新するオプション
	 * @param originalServiceName 元のサービス名
	 */
	public BDBClientPutCallable(FeedBase feed, String parentUri,
			boolean updateAllIndex, String originalServiceName) {
		this.feed = feed;
		this.parentUri = parentUri;
		this.updateAllIndex = updateAllIndex;
		this.originalServiceName = originalServiceName;
	}

	/**
	 * 更新.
	 */
	@Override
	public List<UpdatedInfo> call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) + "[Put call] start.");
		}

		// ReflexContextを取得
		ReflexContext reflexContext = (ReflexContext)getReflexContext();
		ReflexAuthentication auth = reflexContext.getAuth();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();

		DatastoreManager datastoreManager = TaggingEnvUtil.getDatastoreManager();

		List<UpdatedInfo> updatedInfos = null;
		int numRetries = BDBClientUtil.getBulkPutRetryCount();
		int waitMillis = BDBClientUtil.getBulkPutRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				updatedInfos = datastoreManager.put(feed, parentUri, updateAllIndex,
						originalServiceName, auth, requestInfo, connectionInfo);
				break;

			} catch (IOException e) {
				if (r >= numRetries) {
					// 更新失敗 (ログエントリーは呼び出し元で書く。)
					throw e;
				}
				boolean isRetry = RetryUtil.isRetryError(e, Constants.PUT);
				if (isRetry) {
					if (logger.isInfoEnabled()) {
						logger.info(LogUtil.getRequestInfoStr(requestInfo) + "[Put call] retry: " + r);
					}
					RetryUtil.sleep(waitMillis + r * 10);
				} else {
					// リトライ対象でないエラー
					throw e;
				}
			}
		}

		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) + "[Put call] end.");
		}
		return updatedInfos;
	}

}
