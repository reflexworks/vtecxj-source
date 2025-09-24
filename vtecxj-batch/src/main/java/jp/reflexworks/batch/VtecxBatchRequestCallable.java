package jp.reflexworks.batch;

import java.io.IOException;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.bdbclient.BDBResponseInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBResponseType;
import jp.reflexworks.taggingservice.requester.BDBRequester;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.taskqueue.TaskQueueUtil;
import jp.reflexworks.taggingservice.util.LogUtil;

/**
 * リクエスト非同期処理.
 */
public class VtecxBatchRequestCallable extends ReflexCallable<BDBResponseInfo<FeedBase>> {

	/** URL */
	private String urlStr;
	/** Method */
	private String method;
	/** 名前空間 */
	private String namespace;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param urlStr URL
	 * @param method Method
	 * @param namespace 名前空間
	 */
	public VtecxBatchRequestCallable(String urlStr, String method, String namespace) {
		this.urlStr = urlStr;
		this.method = method;
		this.namespace = namespace;
	}

	/**
	 * 非同期処理登録.
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報 (スレッド間共有オブジェクトを抽出するため設定)
	 * @return Future
	 */
	public Future<BDBResponseInfo<FeedBase>> addTask(ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// TaggingExceptionをログ出力する
		return (Future<BDBResponseInfo<FeedBase>>)TaskQueueUtil.addTask(this, true, 0, auth,
				requestInfo, connectionInfo);
	}

	/**
	 * 非同期でリクエスト.
	 * @return レスポンス情報
	 */
	@Override
	public BDBResponseInfo<FeedBase> call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		if (logger.isTraceEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[request] start.");
		}

		ReflexContext reflexContext = (ReflexContext)getReflexContext();
		BDBRequester<FeedBase> requester = new BDBRequester<FeedBase>(BDBResponseType.FEED);
		return requester.requestByUrl(urlStr, method, null, namespace, null, null, null, null,
				reflexContext.getResourceMapper(), getServiceName(),
				reflexContext.getRequestInfo(), reflexContext.getConnectionInfo());
	}

}
