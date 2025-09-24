package jp.reflexworks.taggingservice.index;

import java.io.IOException;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBIndexType;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.taskqueue.TaskQueueUtil;
import jp.reflexworks.taggingservice.util.LogUtil;

/**
 * インデックスの更新非同期処理.
 */
public class IndexPutCallable extends ReflexCallable<Boolean> {

	/** サーバURL */
	private String serverUrl;
	/** インデックス情報 */
	private FeedBase feed;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param serverUrl サーバURL
	 * @param feed インデックス情報
	 */
	public IndexPutCallable(String serverUrl, BDBIndexType indexType, FeedBase feed) {
		this.serverUrl = serverUrl;
		this.feed = feed;
	}

	/**
	 * 非同期処理登録.
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報 (ReadEntryMapを利用)
	 * @return Future
	 */
	public Future<Boolean> addTask(ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		return (Future<Boolean>)TaskQueueUtil.addTask(this, 0, auth, requestInfo, connectionInfo);
	}

	/**
	 * インデックスの更新非同期処理.
	 * @return true/false
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		ReflexContext reflexContext = (ReflexContext)getReflexContext();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
					"[IndexPut call] start.");
		}

		IndexCommonManager idxManager = new IndexCommonManager();
		idxManager.requestPut(serverUrl, feed, getServiceName(),
				requestInfo, getConnectionInfo());
		return true;
	}

}
