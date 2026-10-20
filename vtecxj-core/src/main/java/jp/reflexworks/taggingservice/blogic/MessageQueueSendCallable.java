package jp.reflexworks.taggingservice.blogic;

import java.io.IOException;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.util.LogUtil;

/**
 * メッセージキュー送信処理 (グループ宛の非同期登録).
 */
public class MessageQueueSendCallable extends ReflexCallable<Boolean> {

	/** メッセージキュー送信情報 */
	private MessageQueueSendInfo sendInfo;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param sendInfo メッセージキュー送信情報
	 */
	MessageQueueSendCallable(MessageQueueSendInfo sendInfo) {
		this.sendInfo = sendInfo;
	}

	/**
	 * メッセージキュー登録.
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		MessageQueueBlogic blogic = new MessageQueueBlogic();
		long startTime = 0;
		if (blogic.isEnableAccessLog()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
					"[send message queue] start. channel=" + sendInfo.channel +
					", toUids size=" + sendInfo.toUids.size());
			startTime = new Date().getTime();
		}

		// メッセージ登録はリクエストユーザの権限、フォルダ登録はシステム権限で行う。
		ReflexContext reflexContext = (ReflexContext)getReflexContext();
		SystemContext systemContext = new SystemContext(getAuth(), requestInfo,
				getConnectionInfo());
		blogic.sendMessageQueue(sendInfo, systemContext, reflexContext);

		if (blogic.isEnableAccessLog()) {
			long time = new Date().getTime() - startTime;
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
					"[send message queue] end - " + time + "ms. channel=" + sendInfo.channel);
		}
		return true;
	}

}
