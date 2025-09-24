package jp.reflexworks.taggingservice.plugin;

import java.io.IOException;
import java.util.concurrent.Future;

import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.taskqueue.ReflexTaskQueue;

/**
 * 非同期処理管理インターフェース.
 */
public interface TaskQueueManager extends ReflexPlugin {

	/**
	 * 非同期処理を追加.
	 * @param taskQueue Reflex非同期処理
	 * @param countdownMillis 非同期処理開始までの待ち時間
	 * @return Future
	 */
	public Future<?> addTask(ReflexTaskQueue<?> taskQueue, long countdownMillis)
	throws IOException, TaggingException;

	/**
	 * 実行中および実行待ちの非同期処理があるかどうか
	 * @return 実行中および実行待ちの非同期処理がある場合true
	 */
	public boolean hasTask();

}
