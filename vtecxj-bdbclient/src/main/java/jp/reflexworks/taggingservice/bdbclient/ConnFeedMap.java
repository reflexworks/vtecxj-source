package jp.reflexworks.taggingservice.bdbclient;

import java.util.Map;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.conn.ReflexConnection;
import jp.reflexworks.taggingservice.model.Value;

/**
 * 読み込み済みFeedの格納Map
 */
public class ConnFeedMap implements ReflexConnection<Map<String, Value<FeedBase>>> {

	/** 読み込み済みFeedの格納Map */
	private Map<String, Value<FeedBase>> feedMap;

	/**
	 * コンストラクタ.
	 * @param readEntryMap 読み込み済みEntryの格納Map
	 */
	public ConnFeedMap(Map<String, Value<FeedBase>> feedMap) {
		this.feedMap = feedMap;
	}

	/**
	 * コネクションオブジェクトを取得.
	 * @return コネクションオブジェクト
	 */
	public Map<String, Value<FeedBase>> getConnection() {
		return feedMap;
	}

	/**
	 * クローズ処理.
	 */
	public void close() {
		// Do nothing.
	}

}
