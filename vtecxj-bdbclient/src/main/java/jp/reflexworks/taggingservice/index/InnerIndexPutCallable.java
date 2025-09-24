package jp.reflexworks.taggingservice.index;

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
import jp.reflexworks.taggingservice.util.Constants.OperationType;

/**
 * データ更新後の非同期処理.
 */
public class InnerIndexPutCallable extends ReflexCallable<Boolean> {

	/** 更新情報リスト */
	private List<UpdatedInfo> updatedInfos;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param updatedInfos 更新情報リスト
	 */
	public InnerIndexPutCallable(List<UpdatedInfo> updatedInfos) {
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
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[put InnerIndex after commit call] start. ");
			sb.append(toStringUpdatedInfos());
			logger.debug(sb.toString());
		}

		InnerIndexManager idxManager = new InnerIndexManager();
		boolean ret = idxManager.putInnerIndex(updatedInfos, reflexContext);

		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[put InnerIndex after commit call] end. ");
			sb.append(toStringUpdatedInfos());
			logger.debug(sb.toString());
		}

		return ret;
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
