package jp.reflexworks.taggingservice.taskqueue;

import java.io.IOException;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.TaskQueueManager;
import jp.reflexworks.taggingservice.util.CheckUtil;

/**
 * 非同期処理ユーティリティ.
 */
public class TaskQueueUtil {

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(TaskQueueUtil.class);

	/**
	 * 非同期処理登録.
	 * @param callable 非同期処理
	 * @param countdownMillis 非同期処理開始までの待ち時間
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報 (スレッド間共有オブジェクトを抽出するため設定)
	 * @return Future
	 */
	public static Future<?> addTask(ReflexCallable<?> callable, long countdownMillis,
			ReflexAuthentication auth, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// 例外発生時にログエントリー登録する。
		return addTask(callable, false, countdownMillis,
				auth, requestInfo, connectionInfo);
	}

	/**
	 * 非同期処理登録.
	 * @param callable 非同期処理
	 * @param isDisabledErrorLogEntry 例外発生時にログエントリー出力しないどうか
	 * @param countdownMillis 非同期処理開始までの待ち時間
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報 (スレッド間共有オブジェクトを抽出するため設定)
	 * @return Future
	 */
	public static Future<?> addTask(ReflexCallable<?> callable,
			boolean isDisabledErrorLogEntry, long countdownMillis, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		return addTask(callable, false, isDisabledErrorLogEntry, false, countdownMillis, auth,
				requestInfo, connectionInfo);
	}

	/**
	 * 非同期処理登録.
	 * @param callable 非同期処理
	 * @param printTagingException TaggingException発生時スタックトレースログを出力する場合true
	 * @param isDisabledErrorLogEntry 例外発生時にログエントリー出力しないどうか
	 * @param isMigrate 移行処理の場合true (移行処理の場合、サービスの設定を参照しない)
	 * @param countdownMillis 非同期処理開始までの待ち時間
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報 (スレッド間共有オブジェクトを抽出するため設定)
	 * @return Future
	 */
	public static Future<?> addTask(ReflexCallable<?> callable, boolean printTagingException,
			boolean isDisabledErrorLogEntry, boolean isMigrate,
			long countdownMillis, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		return addTask(callable, printTagingException, isDisabledErrorLogEntry, isMigrate, 
				false, countdownMillis, auth, requestInfo, connectionInfo);
	}

	/**
	 * メインスレッドとして非同期処理登録.
	 * スレッドの終了時に、このスレッドから派生したスレッドの全てのコネクションをクローズする。
	 * @param callable 非同期処理
	 * @param countdownMillis 非同期処理開始までの待ち時間
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報 (スレッド間共有オブジェクトを抽出するため設定)
	 * @return Future
	 */
	public static Future<?> addTaskByMainThread(ReflexCallable<?> callable, 
			long countdownMillis, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		return addTask(callable, false, false, false, true, countdownMillis, 
				auth, requestInfo, connectionInfo);
	}

	/**
	 * 非同期処理登録.
	 * @param callable 非同期処理
	 * @param printTagingException TaggingException発生時スタックトレースログを出力する場合true
	 * @param isDisabledErrorLogEntry 例外発生時にログエントリー出力しないどうか
	 * @param isMigrate 移行処理の場合true (移行処理の場合、サービスの設定を参照しない)
	 * @param isMainThread メインスレッドとして扱う。終了時は全てのコネクションをクローズする。
	 * @param countdownMillis 非同期処理開始までの待ち時間
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報 (スレッド間共有オブジェクトを抽出するため設定)
	 * @return Future
	 */
	private static Future<?> addTask(ReflexCallable<?> callable, boolean printTagingException,
			boolean isDisabledErrorLogEntry, boolean isMigrate, boolean isMainThread, 
			long countdownMillis, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// 引数チェック
		ConnectionInfo tmpConnectionInfo = null;
		try {
			CheckUtil.checkArgNull(auth, "auth");
			CheckUtil.checkArgNull(requestInfo, "requestInfo");
			if (!isMainThread) {
				CheckUtil.checkArgNull(connectionInfo, "connectionInfo");
				tmpConnectionInfo = connectionInfo;
			}
		} catch (IllegalParameterException e) {
			logger.warn("[addTask] IllegalParameterException: " + e.getMessage(), e);
		}

		// Reflex非同期処理オブジェクト作成
		TaggingTaskQueue<?> taskQueue = new TaggingTaskQueue(callable, printTagingException,
				isDisabledErrorLogEntry, isMigrate, auth, requestInfo, tmpConnectionInfo);
		TaskQueueManager taskQueueManager = TaggingEnvUtil.getTaskQueueManager();
		return taskQueueManager.addTask(taskQueue, countdownMillis);
	}

}
