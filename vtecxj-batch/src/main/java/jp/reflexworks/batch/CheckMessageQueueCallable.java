package jp.reflexworks.batch;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.util.LogUtil;

/**
 * メッセージキュー未送信チェック処理のリクエスト処理.
 * サービスごとに行う処理
 *  ・バッチジョブサーバにリクエストする
 */
public class CheckMessageQueueCallable extends ReflexCallable<Boolean> {

	/** サービス名 */
	private String serviceName;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param serviceName サービス名
	 */
	public CheckMessageQueueCallable(String serviceName) {
		this.serviceName = serviceName;
	}

	/**
	 * バッチジョブサーバにリクエストする処理.
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		if (logger.isTraceEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[CheckMessageQueueCallable] start. serviceName=" + serviceName);
		}
		
		CheckMessageQueueBlogic blogic = new CheckMessageQueueBlogic();
		blogic.request(serviceName);

		return true;
	}

}
