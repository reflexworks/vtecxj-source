package jp.reflexworks.taggingservice.plugin.def;

import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.taskqueue.ReflexTaskQueueAbstractManager;

/**
 * 非同期処理管理クラス.
 */
public class TaskQueueManagerDefault extends ReflexTaskQueueAbstractManager {

	/**
	 * 非同期処理結果オブジェクトを保持しておくかどうか.
	 * バッチ処理の場合のみ保持する。
	 * @return 非同期処理結果オブジェクトを保持しておく場合true
	 */
	@Override
	public boolean isStoredFuture() {
		// バッチ起動の場合のみタスクリスト管理を行う。(終了時に実行中スレッドがチェックされないため)
		return !TaggingEnvUtil.isServlet();
	}

}
