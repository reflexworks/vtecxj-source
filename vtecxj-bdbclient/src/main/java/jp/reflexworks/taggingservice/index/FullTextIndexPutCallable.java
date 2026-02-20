package jp.reflexworks.taggingservice.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.bdbclient.BDBClientManager;
import jp.reflexworks.taggingservice.bdbclient.BDBClientUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.UpdatedInfo;
import jp.reflexworks.taggingservice.requester.BDBRequesterUtil;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.taskqueue.TaskQueueUtil;
import jp.reflexworks.taggingservice.util.Constants.OperationType;
import jp.reflexworks.taggingservice.util.LogUtil;

/**
 * データコミット後の非同期処理.
 */
public class FullTextIndexPutCallable extends ReflexCallable<Boolean> {

	/** 更新情報リスト */
	private List<UpdatedInfo> updatedInfos;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param updatedInfos 更新情報リスト
	 */
	public FullTextIndexPutCallable(List<UpdatedInfo> updatedInfos) {
		this.updatedInfos = updatedInfos;
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
	 * コミット後の非同期処理.
	 * @return true/false
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		ReflexContext reflexContext = (ReflexContext)getReflexContext();
		RequestInfo requestInfo = getRequestInfo();
		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[put FulltextIndex after commit call] start. ");
			sb.append(toStringUpdatedInfos());
			logger.debug(sb.toString());
		}

		FullTextSearchManager ftManager = new FullTextSearchManager();
		List<UpdatedInfo> targetUpdatedInfos = updatedInfos;

		int numRetries = BDBRequesterUtil.getBDBIndexPutRetryCount();
		int waitMillis = BDBRequesterUtil.getBDBIndexPutRetryWaitmillis();
		int retryCount = 0;
		boolean ret = false;
		while (true) {
			try {
				ret = ftManager.putFullTextIndex(targetUpdatedInfos, reflexContext);
				break;
			} catch (IOException | TaggingException e) {
				if (retryCount >= numRetries) {
					throw e;
				}

				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) +
							"[put FulltextIndex after commit call] " +
							BDBClientUtil.getRetryLog(e, retryCount));
				}
				BDBClientUtil.sleep(waitMillis + retryCount * 10);

				List<UpdatedInfo> retryInfos = getRetryUpdatedInfos(reflexContext, requestInfo);
				if (retryInfos == null || retryInfos.isEmpty()) {
					throw e;
				}

				retryCount++;
				if (logger.isWarnEnabled()) {
					logger.warn(LogUtil.getRequestInfoStr(requestInfo) +
							"[put FulltextIndex after commit call] retry start. retryNo=" +
							retryCount + "/" + numRetries +
							", retryUpdatedInfoCount=" + retryInfos.size(), e);
				}
				targetUpdatedInfos = retryInfos;
			}
		}

		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[put FulltextIndex after commit call] end. ");
			sb.append(toStringUpdatedInfos());
			logger.debug(sb.toString());
		}

		return ret;
	}

	/**
	 * リトライ対象の更新情報を取得.
	 * @param reflexContext reflexContext
	 * @param requestInfo requestInfo
	 * @return リトライ対象更新情報
	 * @throws IOException IOException
	 * @throws TaggingException TaggingException
	 */
	private List<UpdatedInfo> getRetryUpdatedInfos(ReflexContext reflexContext,
			RequestInfo requestInfo)
	throws IOException, TaggingException {
		List<UpdatedInfo> retryInfos = new ArrayList<>();
		BDBClientManager manager = new BDBClientManager();
		ReflexAuthentication auth = reflexContext.getAuth();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		String serviceName = reflexContext.getServiceName();

		for (UpdatedInfo updatedInfo : updatedInfos) {
			EntryBase checkEntry = null;
			if (OperationType.DELETE == updatedInfo.getFlg()) {
				checkEntry = updatedInfo.getPrevEntry();
			} else {
				checkEntry = updatedInfo.getUpdEntry();
			}
			if (checkEntry == null || checkEntry.id == null || checkEntry.getMyUri() == null) {
				continue;
			}

			EntryBase currentEntry = manager.getEntry(checkEntry.getMyUri(), false,
					serviceName, auth, requestInfo, connectionInfo);
			if (OperationType.DELETE == updatedInfo.getFlg()) {
				if (currentEntry == null) {
					retryInfos.add(updatedInfo);
				}
			} else {
				if (currentEntry != null && checkEntry.id.equals(currentEntry.id)) {
					retryInfos.add(updatedInfo);
				}
			}
		}
		return retryInfos;
	}

	/**
	 * このオブジェクトの更新情報の文字列表現を取得.
	 * @return このオブジェクトの更新情報の文字列表現
	 */
	private String toStringUpdatedInfos() {
		StringBuilder sb = new StringBuilder();
		boolean isFirst = true;
		for (UpdatedInfo updatedInfo : updatedInfos) {
			if (isFirst) {
				isFirst = false;
			} else {
				sb.append(", ");
			}
			OperationType flg = updatedInfo.getFlg();
			String id = null;
			if (OperationType.DELETE == flg) {
				id = updatedInfo.getPrevEntry().id;
			} else {
				id = updatedInfo.getUpdEntry().id;
			}
			sb.append("flg=");
			sb.append(flg.name());
			sb.append(" id=");
			sb.append(id);
		}
		return sb.toString();
	}

}
