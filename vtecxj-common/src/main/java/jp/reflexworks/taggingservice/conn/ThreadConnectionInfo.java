package jp.reflexworks.taggingservice.conn;

import jp.sourceforge.reflex.util.DeflateUtil;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * スレッドごとのコネクション情報.
 */
public class ThreadConnectionInfo {

	/** ConnectionInfo key : DeflateUtil */
	static final String DEFLATE_UTIL = "_DeflateUtil";

	/**
	 * コネクション格納Map.
	 * メインスレッドキャッシュはスレッド間共有のため、ConcurrentHashMapとする。
	 */
	private final Map<String, ReflexConnection<?>> connections =
			new ConcurrentHashMap<String, ReflexConnection<?>>();

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ.
	 * @param deflateUtil DeflateUtil
	 */
	public ThreadConnectionInfo(DeflateUtil deflateUtil) {
		// DeflateUtil
		setDeflateUtil(deflateUtil);
	}

	/**
	 * コネクションを格納.
	 * @param name キー
	 * @param conn コネクションオブジェクト
	 */
	public void put(String name, ReflexConnection<?> conn) {
		if (name == null) {
			return;
		}
		if (connections.containsKey(name)) {
			// 上書きする場合はクローズ
			ReflexConnection<?> rConn = connections.get(name);
			rConn.close();
		}
		connections.put(name, conn);
	}

	/**
	 * コネクションを取得.
	 * @param name キー
	 * @return コネクションオブジェクト
	 */
	public ReflexConnection<?> get(String name) {
		if (name == null) {
			return null;
		}
		return connections.get(name);
	}

	/**
	 * コネクションからコネクション名を取得.
	 * @param conn コネクション
	 * @return コネクション名
	 */
	public String getConnectionName(ReflexConnection<?> conn) {
		for (Map.Entry<String, ReflexConnection<?>> mapEntry : connections.entrySet()) {
			ReflexConnection<?> rConn = mapEntry.getValue();
			if (conn.equals(rConn.getConnection())) {
				return mapEntry.getKey();
			}
		}
		return null;
	}

	/**
	 * コネクション名リストを取得.
	 * @return コネクション名リスト
	 */
	public Set<String> getConnectionNames() {
		return connections.keySet();
	}

	/**
	 * DeflateUtilを設定.
	 * @param deflateUtil DeflateUtil
	 */
	private void setDeflateUtil(DeflateUtil deflateUtil) {
		ConnDeflateUtil rConn = new ConnDeflateUtil(deflateUtil);
		put(DEFLATE_UTIL, rConn);
	}

	/**
	 * DeflateUtilを取得.
	 * @return DeflateUtil
	 */
	public DeflateUtil getDeflateUtil() {
		ConnDeflateUtil rConn = (ConnDeflateUtil)get(DEFLATE_UTIL);
		return rConn.getConnection();
	}

	/**
	 * クローズ処理.
	 */
	public void close() {
		for (Map.Entry<String, ReflexConnection<?>> mapEntry : connections.entrySet()) {
			try {
				ReflexConnection<?> conn = mapEntry.getValue();
				conn.close();
				//logger.info("close : " + mapEntry.getKey());
			} catch (Throwable e) {
				logger.warn(getErrorMesssage(mapEntry.getKey(), e));
			}
		}
		connections.clear();
	}

	/**
	 * クローズ処理.
	 * コネクションエラー時のクローズ
	 * @param name コネクション名
	 */
	public void close(String name) {
		if (name == null) {
			return;
		}
		ReflexConnection<?> conn = connections.get(name);
		if (conn != null) {
			try {
				conn.close();
			} catch (Throwable e) {
				logger.warn(getErrorMesssage(name, e));
			}
			connections.remove(name);
		}
	}

	/**
	 * リセット処理.
	 * DeflateUtil以外をクローズします。
	 */
	public void reset() {
		for (Map.Entry<String, ReflexConnection<?>> mapEntry : connections.entrySet()) {
			try {
				String key = mapEntry.getKey();
				if (DEFLATE_UTIL.equals(key)) {
					continue;
				}
				ReflexConnection<?> conn = mapEntry.getValue();
				conn.close();
				//logger.info("close : " + mapEntry.getKey());
			} catch (Throwable e) {
				logger.warn(getErrorMesssage(mapEntry.getKey(), e));
			}
		}
		connections.clear();
	}

	/**
	 * エラーメッセージを取得
	 * @param name 名前
	 * @param e 例外
	 * @return エラーメッセージ
	 */
	private String getErrorMesssage(String name, Throwable e) {
		StringBuilder sb = new StringBuilder();
		sb.append("connection name = ");
		sb.append(name);
		sb.append(" ");
		sb.append(e.getClass().getName());
		sb.append(" : ");
		sb.append(e.getMessage());
		return sb.toString();
	}

	/**
	 * このオブジェクトの文字列表現を取得
	 * @return このオブジェクトの文字列表現
	 */
	@Override
	public String toString() {
		return "ThreadConnectionInfo [connections=" + connections + "]";
	}

}
