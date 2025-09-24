package jp.reflexworks.taggingservice.taskqueue;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ReflexStatic;
import jp.reflexworks.taggingservice.env.ReflexEnvConst;
import jp.reflexworks.taggingservice.env.ReflexEnvUtil;
import jp.reflexworks.taggingservice.exception.StaticDuplicatedException;
import jp.reflexworks.taggingservice.plugin.TaskQueueManager;

/**
 * 非同期処理管理クラス.
 */
public abstract class ReflexTaskQueueAbstractManager implements TaskQueueManager {

	/** 設定 : シャットダウン時の強制終了待ち時間(秒) */
	public static final String PROP_TASKQUEUE_AWAITTERMINATION_SEC = "_taskqueue.awaittermination.sec";
	/** シャットダウン時の強制終了待ち時間(秒) デフォルト値 */
	public static final int TASKQUEUE_AWAITTERMINATION_SEC_DEFAULT = 60;

	/** メモリ上のstaticオブジェクト格納キー : ExecutorService */
	protected static final String STATIC_NAME_TASKQUEUE = "_taskqueue";
	/** メモリ上のstaticオブジェクト格納キー : ScheduledExecutorService */
	protected static final String STATIC_NAME_TASKQUEUE_SCHEDULED = "_taskqueue_scheduled";
	/** メモリ上のstaticオブジェクト格納キー : 非同期実行タスクリスト */
	protected static final String STATIC_NAME_FUTURES = "_futures";

	/** ロガー */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 初期起動時の処理.
	 */
	public void init() {
		int poolSizeBatchjobSystem = ReflexEnvUtil.getSystemPropInt(
				ReflexEnvConst.TASKQUEUE_POOLSIZE_SYSTEM,
				ReflexEnvConst.TASKQUEUE_POOLSIZE_SYSTEM_DEFAULT);
		int poolSizeBatchjob = ReflexEnvUtil.getSystemPropInt(
				ReflexEnvConst.TASKQUEUE_POOLSIZE_BATCHJOB,
				ReflexEnvConst.TASKQUEUE_POOLSIZE_BATCHJOB_DEFAULT);

		// ExecutoreService
		ExecutorService taskQueueExecutorService =
				Executors.newFixedThreadPool(poolSizeBatchjobSystem);
		try {
			ReflexStatic.setStatic(STATIC_NAME_TASKQUEUE, taskQueueExecutorService);
		} catch (StaticDuplicatedException e) {
			taskQueueExecutorService = (ExecutorService)ReflexStatic.getStatic(
					STATIC_NAME_TASKQUEUE);
		}

		// ScheduledExecutoreService
		ScheduledExecutorService taskQueueScheduledExecutorService =
				Executors.newScheduledThreadPool(poolSizeBatchjob);
		try {
			ReflexStatic.setStatic(STATIC_NAME_TASKQUEUE_SCHEDULED, taskQueueScheduledExecutorService);
		} catch (StaticDuplicatedException e) {
			taskQueueExecutorService = (ExecutorService)ReflexStatic.getStatic(
					STATIC_NAME_TASKQUEUE_SCHEDULED);
		}

		// Futures set
		ConcurrentMap<Future<?>, Boolean> futuresSet = new ConcurrentHashMap<>();
		try {
			ReflexStatic.setStatic(STATIC_NAME_FUTURES, futuresSet);
		} catch (StaticDuplicatedException e) {
			futuresSet = (ConcurrentMap<Future<?>, Boolean>)ReflexStatic.getStatic(
					STATIC_NAME_FUTURES);
		}

	}

	/**
	 * シャットダウン時の終了処理
	 */
	public void close() {
		long startTime = new Date().getTime();
		if (logger.isDebugEnabled()) {
			logger.debug("[close] shutdown start.");
		}

		int timeoutSec = ReflexEnvUtil.getSystemPropInt(
				PROP_TASKQUEUE_AWAITTERMINATION_SEC,
				TASKQUEUE_AWAITTERMINATION_SEC_DEFAULT);

		ScheduledExecutorService taskQueueScheduledExecutorService =
				(ScheduledExecutorService)ReflexStatic.getStatic(STATIC_NAME_TASKQUEUE_SCHEDULED);
		if (taskQueueScheduledExecutorService != null) {
			taskQueueScheduledExecutorService.shutdown();
			boolean isTermination = false;
			try {
				isTermination = taskQueueScheduledExecutorService.awaitTermination(timeoutSec, TimeUnit.SECONDS);
				if (!isTermination) {
					logger.warn("[close] (ScheduledExecutorService) awaitTermination failed.");
				}
			} catch (InterruptedException e) {
				logger.warn("[close] (ScheduledExecutorService) InterruptedException: " + e.getMessage(), e);
			} finally {
				if (logger.isDebugEnabled()) {
					long finishTime = new Date().getTime();
					long time = finishTime - startTime;
					StringBuilder sb = new StringBuilder();
					sb.append("[close] (ScheduledExecutorService) shutdown end. isTermination = ");
					sb.append(isTermination);
					sb.append(" - ");
					sb.append(time);
					sb.append("ms");
					logger.debug(sb.toString());
				}
			}
		}

		long nowTime = new Date().getTime();
		long elaspedMillisec = nowTime - startTime;
		int elaspedSec = (int)(elaspedMillisec / 1000);
		int remainingTimeoutSec = timeoutSec - elaspedSec + 1;
		if (logger.isDebugEnabled()) {
			logger.debug("[close] remainingTimeoutSec=" + remainingTimeoutSec);
		}
		ExecutorService taskQueueExecutorService =
				(ExecutorService)ReflexStatic.getStatic(STATIC_NAME_TASKQUEUE);
		if (taskQueueExecutorService != null) {
			taskQueueExecutorService.shutdown();
			boolean isTermination = false;
			try {
				isTermination = taskQueueExecutorService.awaitTermination(remainingTimeoutSec, TimeUnit.SECONDS);
				if (!isTermination) {
					logger.warn("[close] (FixedExecutorService) awaitTermination failed.");
				}
			} catch (InterruptedException e) {
				logger.warn("[close] (FixedExecutorService) InterruptedException: " + e.getMessage(), e);
			} finally {
				if (logger.isDebugEnabled()) {
					long finishTime = new Date().getTime();
					long time = finishTime - startTime;
					StringBuilder sb = new StringBuilder();
					sb.append("[close] (FixedExecutorService) shutdown end. isTermination = ");
					sb.append(isTermination);
					sb.append(" - ");
					sb.append(time);
					sb.append("ms");
					logger.debug(sb.toString());
				}
			}
		}

	}

	/**
	 * 非同期処理を追加.
	 * @param taskQueue Reflex非同期処理
	 * @param countdownMillis 非同期処理開始までの待ち時間
	 * @return Future
	 */
	public Future<?> addTask(ReflexTaskQueue<?> taskQueue, long countdownMillis) {
		// 非同期処理実行
		Future<?> future = null;
		if (countdownMillis > 0) {
			ScheduledExecutorService taskQueueScheduledExecutorService =
					(ScheduledExecutorService)ReflexStatic.getStatic(STATIC_NAME_TASKQUEUE_SCHEDULED);
			future = taskQueueScheduledExecutorService.schedule(taskQueue, countdownMillis,
					TimeUnit.MILLISECONDS);
		} else {
			ExecutorService taskQueueExecutorService =
					(ExecutorService)ReflexStatic.getStatic(STATIC_NAME_TASKQUEUE);
			future = taskQueueExecutorService.submit(taskQueue);
		}

		// バッチ起動の場合のみタスクリスト管理を行う。(終了時に実行中スレッドがチェックされないため)
		if (isStoredFuture()) {
			// タスクリストに追加
			ConcurrentMap<Future<?>, Boolean> futuresSet =
					(ConcurrentMap<Future<?>, Boolean>)ReflexStatic.getStatic(STATIC_NAME_FUTURES);
			futuresSet.put(future, true);

			// タスクリストのうち終了したものをクリアする。
			cleanFutures(futuresSet);
		}

		return future;
	}

	/**
	 * 非同期処理が全て終了しているかどうか
	 * @return 非同期処理が全て終了している場合true
	 */
	@Override
	public boolean hasTask() {
		ConcurrentMap<Future<?>, Boolean> futuresSet =
				(ConcurrentMap<Future<?>, Boolean>)ReflexStatic.getStatic(STATIC_NAME_FUTURES);
		cleanFutures(futuresSet);
		return !futuresSet.isEmpty();
	}

	/**
	 * メモリに保持するタスクリストから、完了したものを取り除く.
	 * @param futuresSet タスクリスト
	 */
	private void cleanFutures(ConcurrentMap<Future<?>, Boolean> futuresSet) {
		List<Future<?>> doneFutures = new ArrayList<Future<?>>();
		for (Future<?> tmpFuture : futuresSet.keySet()) {
			if (tmpFuture.isDone() || tmpFuture.isCancelled()) {
				doneFutures.add(tmpFuture);
			}
		}
		if (!doneFutures.isEmpty()) {
			for (Future<?> doneFuture : doneFutures) {
				futuresSet.remove(doneFuture);
			}
		}
	}

	/**
	 * 非同期処理結果オブジェクトを保持しておくかどうか.
	 * @return 非同期処理結果オブジェクトを保持しておく場合true
	 */
	public abstract boolean isStoredFuture();

}
