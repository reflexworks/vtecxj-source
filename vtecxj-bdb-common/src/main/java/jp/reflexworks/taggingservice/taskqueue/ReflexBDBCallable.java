package jp.reflexworks.taggingservice.taskqueue;

import java.io.IOException;

import jp.reflexworks.taggingservice.api.BaseReflexContext;
import jp.reflexworks.taggingservice.exception.TaggingException;

/**
 * TaggingService非同期処理インターフェース.
 * 呼び出しユーザでのReflexContextはフレームワーク側でセット済み。
 */
public abstract class ReflexBDBCallable<T> {

	/** ReflexContext */
	private BaseReflexContext reflexContext;

	/**
	 * ReflexContextを設定.
	 * @param reflexContext ReflexContext
	 */
	void setReflexContext(BaseReflexContext reflexContext) {
		this.reflexContext = reflexContext;
	}

	/**
	 * ReflexContextを取得.
	 * @return ReflexContext
	 */
	public BaseReflexContext getReflexContext() {
		return reflexContext;
	}

	/**
	 * 非同期処理.
	 */
	public abstract T call() throws IOException, TaggingException;

}
