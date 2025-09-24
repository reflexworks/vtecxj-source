package jp.reflexworks.taggingservice.bdbclient;

import java.util.Map;

import jp.reflexworks.taggingservice.conn.ReflexConnection;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBServerType;
import jp.sourceforge.reflex.util.ConsistentHash;

/**
 * ConsistentHashの格納Map
 */
public class ConnConsistentHashMap implements ReflexConnection<Map<BDBServerType, ConsistentHash<String>>> {

	/**
	 * ConsistentHashの格納Map
	 * キー:サーバ種別、値:ConsistentHash
	 */
	private Map<BDBServerType, ConsistentHash<String>> consistentHashMap;

	/**
	 * コンストラクタ.
	 * @param consistentHashMap ConsistentHashの格納Map
	 */
	public ConnConsistentHashMap(Map<BDBServerType, ConsistentHash<String>> consistentHashMap) {
		this.consistentHashMap = consistentHashMap;
	}

	/**
	 * コネクションオブジェクトを取得.
	 * @return コネクションオブジェクト
	 */
	public Map<BDBServerType, ConsistentHash<String>> getConnection() {
		return consistentHashMap;
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
		if (consistentHashMap != null) {
			consistentHashMap.clear();
		}
	}

}
