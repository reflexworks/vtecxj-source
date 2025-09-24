package jp.reflexworks.taggingservice.bdbclient;

import java.io.IOException;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.taskqueue.TaskQueueUtil;
import jp.reflexworks.taggingservice.util.LogUtil;

/**
 * Entry取得処理.
 */
public class BDBClientRequestGetEntryCallable extends ReflexCallable<EntryBase> {

	/** URI */
	private String uri;
	/** キャッシュを使用する場合true */
	private boolean useCache;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param uri URI
	 * @param useCache キャッシュを使用する場合true
	 */
	public BDBClientRequestGetEntryCallable(String uri, boolean useCache) {
		this.uri = uri;
		this.useCache = useCache;
	}

	/**
	 * 非同期処理登録.
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報 (ReadEntryMapを利用)
	 * @return Future
	 */
	public Future<EntryBase> addTask(ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		return (Future<EntryBase>)TaskQueueUtil.addTask(this, 0, auth, requestInfo, connectionInfo);
	}

	/**
	 * EntryサーバへEntry取得処理.
	 * @return Entry
	 */
	@Override
	public EntryBase call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		ConnectionInfo connectionInfo = getConnectionInfo();
		ReflexAuthentication auth = getAuth();
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
					"[Request GetEntry call] start.");
		}

		try {
			BDBClientRetrieveManager retrieveManager = new BDBClientRetrieveManager();
			return retrieveManager.requestGetEntry(uri, useCache,
					auth, requestInfo, connectionInfo);

		} finally {
			if (logger.isTraceEnabled()) {
				logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
						"[Request GetEntry call] end.");
			}
		}
	}

}
