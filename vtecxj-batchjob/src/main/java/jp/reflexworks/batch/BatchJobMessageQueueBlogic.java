package jp.reflexworks.batch;

import java.io.IOException;
import java.util.List;

import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.taskqueue.TaskQueueUtil;

/**
 * バッチジョブサーバで定期的に実行する、メッセージキューの未送信チェックビジネスロジック.
 */
public class BatchJobMessageQueueBlogic {
	
	/**
	 * メッセージキュー未送信チェック処理.
	 * 対象のサービスを抽出し、メッセージキュー未送信チェックを呼び出す。
	 * @param reflexContext ReflexContext
	 */
	public void callCheckMessageQueue(ReflexContext reflexContext) 
	throws IOException, TaggingException {
		List<String> services = BatchJobUtil.getValidServices(reflexContext);
		if (services != null) {
			for (String service : services) {
				BatchJobMessageQueueCallable callable = 
						new BatchJobMessageQueueCallable(service);
				TaskQueueUtil.addTask(
						callable, 0, reflexContext.getAuth(), 
						reflexContext.getRequestInfo(), reflexContext.getConnectionInfo());
			}
		}
	}

}
