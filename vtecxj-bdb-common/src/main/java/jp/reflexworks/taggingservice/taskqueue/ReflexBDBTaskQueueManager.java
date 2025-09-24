package jp.reflexworks.taggingservice.taskqueue;

/**
 * 非同期処理 環境情報.
 */
public class ReflexBDBTaskQueueManager extends ReflexTaskQueueAbstractManager {

	/**
	 * 非同期処理結果オブジェクトを保持しておくかどうか.
	 * @return 非同期処理結果オブジェクトを保持しておく場合true
	 */
	@Override
	public boolean isStoredFuture() {
		// 非同期処理結果は保持しない。
		return false;
	}

}
