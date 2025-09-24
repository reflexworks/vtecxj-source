package jp.reflexworks.taggingservice.index;

import java.io.IOException;
import java.util.concurrent.Future;

import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.taskqueue.TaskQueueUtil;

/**
 * インデックス登録・更新・削除処理　ユーティリティクラス
 */
public class UpdateIndexUtil {

	/**
	 * 非同期処理登録.
	 * @param callable 非同期処理
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報 (スレッド間共有オブジェクトを抽出するため設定)
	 * @return Future
	 */
	public static Future<Boolean> addTask(ReflexCallable<Boolean> callable, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		return (Future<Boolean>)TaskQueueUtil.addTask(callable, 0, auth,
				requestInfo, connectionInfo);
	}

}
