package jp.reflexworks.taggingservice.api;

import java.util.Map;

/**
 * レスポンス情報.
 */
public class ResponseInfo<T> {
	
	/** ステータス */
	public int status;
	
	/** レスポンス(FeedまたはEntry) */
	public T data;
	
	/** ヘッダ情報 */
	public Map<String, String> headers;
	
	/**
	 * コンストラクタ.
	 * @param status ステータス
	 * @param data FeedまたはEntry
	 */
	public ResponseInfo(int status, T data) {
		this.status = status;
		this.data = data;
	}

}
