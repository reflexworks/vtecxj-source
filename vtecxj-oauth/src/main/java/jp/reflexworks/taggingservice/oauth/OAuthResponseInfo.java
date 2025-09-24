package jp.reflexworks.taggingservice.oauth;

import java.util.Map;

/**
 * レスポンス情報.
 */
public class OAuthResponseInfo {
	
	/** ステータス */
	public int status;
	
	/** レスポンスデータ */
	public String data;
	
	/** ヘッダ情報 */
	public Map<String, String> headers;
	
	/**
	 * コンストラクタ.
	 * @param status ステータス
	 * @param data レスポンスデータ
	 * @param headers レスポンスヘッダ
	 */
	public OAuthResponseInfo(int status, String data, Map<String, String> headers) {
		this.status = status;
		this.data = data;
		this.headers = headers;
	}

	/**
	 * このオブジェクトの文字列表現.
	 * @return このオブジェクトの文字列表現
	 */
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("status=");
		sb.append(status);
		sb.append(" ");
		sb.append(data);
		return sb.toString();
	}
	
}
