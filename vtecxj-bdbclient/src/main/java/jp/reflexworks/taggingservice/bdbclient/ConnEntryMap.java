package jp.reflexworks.taggingservice.bdbclient;

import java.util.Map;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.taggingservice.conn.ReflexConnection;
import jp.reflexworks.taggingservice.model.Value;

/**
 * 読み込み済みEntryの格納Map
 */
public class ConnEntryMap implements ReflexConnection<Map<String, Value<EntryBase>>> {

	/** 読み込み済みEntryの格納Map */
	private Map<String, Value<EntryBase>> readEntryMap;

	/**
	 * コンストラクタ.
	 * @param readEntryMap 読み込み済みEntryの格納Map
	 */
	public ConnEntryMap(Map<String, Value<EntryBase>> readEntryMap) {
		this.readEntryMap = readEntryMap;
	}

	/**
	 * コネクションオブジェクトを取得.
	 * @return コネクションオブジェクト
	 */
	public Map<String, Value<EntryBase>> getConnection() {
		return readEntryMap;
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
		if (readEntryMap != null) {
			readEntryMap.clear();
		}
	}

}
