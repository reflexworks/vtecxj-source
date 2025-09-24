package jp.reflexworks.batch;

import java.io.IOException;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.blogic.MessageQueueBlogic;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;

/**
 * メッセージキュー未送信チェック処理.
 */
public class BatchJobMessageQueueCallable extends ReflexCallable<Boolean> {

	/** サービス名 */
	private String serviceName;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ.
	 * @param serviceName サービス名
	 */
	public BatchJobMessageQueueCallable(String serviceName) {
		this.serviceName = serviceName;
	}

	/**
	 * メッセージキュー未送信チェック処理 実行
	 */
	@Override
	public Boolean call()
	throws IOException, TaggingException {
		long startTime = 0;
		if (logger.isTraceEnabled()) {
			logger.debug("[call check message queue] start. serviceName=" + serviceName);
			startTime = new Date().getTime();
		}

		SystemContext systemContext = new SystemContext(serviceName, 
				getRequestInfo(), getConnectionInfo());
		MessageQueueBlogic blogic = new MessageQueueBlogic();
		blogic.checkMessageQueue(systemContext);

		if (logger.isTraceEnabled()) {
			long finishTime = new Date().getTime();
			long time = finishTime - startTime;
			StringBuilder sb = new StringBuilder();
			sb.append("[call check message queue] end - ");
			sb.append(time);
			sb.append("ms.");
			logger.debug(sb.toString());
		}

		return true;
	}

}
