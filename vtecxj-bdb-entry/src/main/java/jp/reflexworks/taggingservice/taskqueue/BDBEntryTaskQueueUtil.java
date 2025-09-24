package jp.reflexworks.taggingservice.taskqueue;

import java.io.IOException;
import java.util.concurrent.Future;

import jp.reflexworks.taggingservice.env.BDBEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.ReflexBDBRequestInfo;
import jp.reflexworks.taggingservice.plugin.TaskQueueManager;

/**
 * 非同期処理ユーティリティ.
 */
public class BDBEntryTaskQueueUtil {

	/**
	 * 非同期処理登録.
	 * @param callable 非同期処理
	 * @param countdownMillis 非同期処理開始までの待ち時間
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @return Future
	 */
	public static Future<?> addTask(ReflexBDBCallable<?> callable,
			long countdownMillis, String serviceName, String namespace,
			ReflexBDBRequestInfo requestInfo)
	throws IOException, TaggingException {
		// サーバ稼働中のみキャッシュする。(起動処理中はキャッシュしない)
		if (!BDBEnvUtil.isRunning()) {
			return null;
		}

		BDBEntryTaskQueue<?> taskQueue = new BDBEntryTaskQueue(callable,
				serviceName, namespace, requestInfo);

		TaskQueueManager taskQueueManager = BDBEnvUtil.getTaskQueueManager();
		return taskQueueManager.addTask(taskQueue, countdownMillis);
	}

}
