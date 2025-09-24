package jp.reflexworks.taggingservice.index;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.util.LogUtil;

/**
 * インデックス登録・更新・削除処理.
 */
public class UpdateIndexCallable extends ReflexCallable<Boolean> {

	/** 対象サービス名 */
	private FeedBase indexFeed;
	/** サービスエントリー */
	private boolean isDelete;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param indexFeed インデックス更新情報
	 * @param isDelete インデックス削除の場合true
	 */
	UpdateIndexCallable(FeedBase indexFeed, boolean isDelete) {
		this.indexFeed = indexFeed;
		this.isDelete = isDelete;
	}

	/**
	 * インデックス登録・更新・削除処理.
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		ReflexAuthentication auth = getAuth();
		RequestInfo requestInfo = getRequestInfo();
		ConnectionInfo connectionInfo = getConnectionInfo();
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
					"[update index call] start.");
		}

		try {
			UpdateIndexManager updateIndexManager = new UpdateIndexManager();
			updateIndexManager.putIndex(indexFeed, isDelete, auth, requestInfo, connectionInfo);
			return true;

		} finally {
			if (logger.isTraceEnabled()) {
				logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
						"[update index call] end.");
			}
		}
	}

}
