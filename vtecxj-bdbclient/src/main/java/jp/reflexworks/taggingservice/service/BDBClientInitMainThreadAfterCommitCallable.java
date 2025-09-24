package jp.reflexworks.taggingservice.service;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.UpdatedInfo;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.taskqueue.TaskQueueUtil;
import jp.reflexworks.taggingservice.util.LogUtil;

/**
 * リクエスト初期処理 Entry更新処理後のキャッシュ更新.
 */
public class BDBClientInitMainThreadAfterCommitCallable extends ReflexCallable<Boolean> {

	/** 更新情報リスト */
	private List<UpdatedInfo> updatedInfos;
	/** 実行元サービス名 */
	private String originalServiceName;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param updatedInfos 更新情報リスト
	 */
	public BDBClientInitMainThreadAfterCommitCallable(List<UpdatedInfo> updatedInfos,
			String originalServiceName) {
		this.updatedInfos = updatedInfos;
		this.originalServiceName = originalServiceName;
	}

	/**
	 * 非同期処理登録.
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報 (ReadEntryMap共有のため使用)
	 * @return Future
	 */
	public Future<Boolean> addTask(ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		return (Future<Boolean>)TaskQueueUtil.addTask(this, 0, auth, requestInfo, connectionInfo);
	}

	/**
	 * リクエスト初期処理 Entry更新処理後のキャッシュ更新.
	 * @return true
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		if (logger.isTraceEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[init mainThread afterCommit call] start.");
		}

		ReflexContext reflexContext = (ReflexContext)getReflexContext();
		BDBClientInitMainThreadManager manager = new BDBClientInitMainThreadManager();
		manager.afterCommit(updatedInfos, originalServiceName, reflexContext);

		if (logger.isTraceEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[init mainThread afterCommit call] end.");
		}

		return true;
	}

}
