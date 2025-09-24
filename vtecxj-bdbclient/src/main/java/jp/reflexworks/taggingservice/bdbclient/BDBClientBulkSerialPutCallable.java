package jp.reflexworks.taggingservice.bdbclient;

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
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.UpdatedInfo;
import jp.reflexworks.taggingservice.plugin.DatastoreManager;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.RetryUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;

/**
 * 一括更新非同期処理.
 */
public class BDBClientBulkSerialPutCallable extends ReflexCallable<List<UpdatedInfo>> {

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
	 * @param feed Feed
	 * @param parentUri 親URI。自動採番登録時に使用。
	 * @param updateAllIndex インデックスをすべて更新するオプション
	 * @param originalServiceName 元のサービス名
	 */
	public BDBClientBulkSerialPutCallable(FeedBase feed, String parentUri,
			boolean updateAllIndex, String originalServiceName) {
		this.feed = feed;
		this.parentUri = parentUri;
		this.updateAllIndex = updateAllIndex;
		this.originalServiceName = originalServiceName;
	}

	/**
	 * 一括直列更新処理.
	 */
	@Override
	public List<UpdatedInfo> call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
					"[bulkSerialPut call] start.");
		}

		ReflexContext reflexContext = (ReflexContext)getReflexContext();
		List<UpdatedInfo> updatedInfos = putProc(reflexContext.getAuth(), reflexContext.getRequestInfo(),
				reflexContext.getConnectionInfo());

		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
					"[bulkSerialPut call] end.");
		}
		return updatedInfos;
	}

	/**
	 * 一括直列更新処理.
	 * @param auth 認証情報(更新対象サービス)
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public List<UpdatedInfo> putProc(ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();

		// 更新Entry最大数ごとに更新処理を行う。
		//int limit = TaggingEnvUtil.getUpdateEntryNumberLimit();
		int limit = 1;	// トランザクションは1件ずつ
		FeedBase tmpFeed = TaggingEntryUtil.createFeed(serviceName);
		tmpFeed.entry = new ArrayList<EntryBase>();
		for (EntryBase entry : feed.entry) {
			tmpFeed.entry.add(entry);
			if (tmpFeed.entry.size() >= limit) {
				put(tmpFeed, auth, requestInfo, connectionInfo);
				// クリア
				tmpFeed = TaggingEntryUtil.createFeed(serviceName);
				tmpFeed.entry = new ArrayList<EntryBase>();
			}
		}
		List<UpdatedInfo> updatedInfos = null;
		if (!tmpFeed.entry.isEmpty()) {
			updatedInfos = put(tmpFeed, auth, requestInfo, connectionInfo);
		}
		return updatedInfos;
	}

	/**
	 * 更新処理
	 * @param tmpFeed Feed
	 * @param auth 認証情報(更新対象サービス)
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	private List<UpdatedInfo> put(FeedBase tmpFeed, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		DatastoreManager datastoreManager = TaggingEnvUtil.getDatastoreManager();
		List<UpdatedInfo> updatedInfos = null;
		int numRetries = BDBClientUtil.getBulkPutRetryCount();
		int waitMillis = BDBClientUtil.getBulkPutRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				updatedInfos = datastoreManager.put(tmpFeed, parentUri, updateAllIndex,
						originalServiceName, auth, requestInfo, connectionInfo);
				break;

			} catch (IOException e) {
				if (r >= numRetries) {
					// 更新失敗 (ログエントリーは呼び出し元で書く。)
					throw e;
				}
				boolean isRetry = RetryUtil.isRetryError(e, Constants.PUT);
				if (isRetry) {
					if (logger.isDebugEnabled()) {
						logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
								"[bulkSerialPut call] retry: " + r);
					}
					RetryUtil.sleep(waitMillis + r * 10);
				} else {
					// リトライ対象でないエラー
					throw e;
				}
			}
		}
		return updatedInfos;
	}

}
