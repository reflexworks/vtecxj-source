package jp.reflexworks.taggingservice.conn;

import java.util.Map;

/**
 * 状況保持クラス
 */
public class ConnStatusMap implements ReflexConnection<Map<String, String>> {

	/** 状況保持Map */
	private Map<String, String> statusMap;

	/**
	 * コンストラクタ.
	 * @param statusMap 状況保持Map
	 */
	public ConnStatusMap(Map<String, String> statusMap) {
		this.statusMap = statusMap;
	}

	/**
	 * コネクションオブジェクトを取得.
	 * @return コネクションオブジェクト
	 */
	public Map<String, String> getConnection() {
		return statusMap;
	}

	/**
	 * クローズ処理.
	 */
	public void close() {
		// Do nothing.
	}

	/**
	 * クリア処理.
	 */
	public void clear() {
		if (statusMap != null) {
			statusMap.clear();
		}
	}

}
