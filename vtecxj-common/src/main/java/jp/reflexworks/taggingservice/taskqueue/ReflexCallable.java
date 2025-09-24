package jp.reflexworks.taggingservice.taskqueue;

import java.io.IOException;

import jp.reflexworks.taggingservice.api.BaseReflexContext;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;

/**
 * TaggingService非同期処理インターフェース.
 * 呼び出しユーザでのReflexContextはフレームワーク側でセット済み。
 */
public abstract class ReflexCallable<T> {

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
	 * ReflexContextを取得.
	 * @return ReflexContext
	 */
	public ReflexAuthentication getAuth() {
		return reflexContext.getAuth();
	}

	/**
	 * リクエスト情報を取得.
	 * @return リクエスト情報
	 */
	public RequestInfo getRequestInfo() {
		return reflexContext.getRequestInfo();
	}

	/**
	 * コネクション情報を取得.
	 * @return コネクション情報
	 */
	public ConnectionInfo getConnectionInfo() {
		return reflexContext.getConnectionInfo();
	}

	/**
	 * サービス名を取得.
	 * @return サービス名
	 */
	public String getServiceName() {
		return reflexContext.getServiceName();
	}

	/**
	 * 名前空間を取得.
	 * @return 名前空間
	 */
	public String getNamespace()
	throws IOException, TaggingException {
		return reflexContext.getNamespace();
	}

	/**
	 * 非同期処理.
	 */
	public abstract T call() throws IOException, TaggingException;

}
