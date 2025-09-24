package jp.reflexworks.taggingservice.blogic;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.util.LogUtil;

/**
 * メッセージキュー受信後の削除処理.
 */
public class MessageQueueDeleteCallable extends ReflexCallable<Boolean> {

	/** 削除データ */
	private FeedBase messageFeed;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param serviceName 対象サービス名
	 * @param settingService 設定サービス
	 */
	public MessageQueueDeleteCallable(FeedBase messageFeed) {
		this.messageFeed = messageFeed;
	}

	/**
	 * メッセージキュー削除.
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		if (logger.isDebugEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[delete message queue] start.");
		}

		ReflexContext reflexContext = (ReflexContext)getReflexContext();
		reflexContext.delete(messageFeed);

		if (logger.isDebugEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[delete message queue] end.");
		}

		return true;
	}

}
