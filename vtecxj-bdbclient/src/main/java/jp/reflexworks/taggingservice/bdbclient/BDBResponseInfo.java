package jp.reflexworks.taggingservice.bdbclient;

import java.util.Map;

/**
 * BDBレスポンス情報.
 */
public class BDBResponseInfo<T> {
	
	/** ステータス */
	public int status;
	
	/** レスポンスデータ */
	public T data;
	
	/** ヘッダ情報 */
	public Map<String, String> headers;
	
	/**
	 * コンストラクタ.
	 * @param status ステータス
	 * @param data レスポンスデータ
	 */
	public BDBResponseInfo(int status, T data) {
		this.status = status;
		this.data = data;
	}

}
