package jp.reflexworks.taggingservice.api;

import java.util.Map;

import jp.reflexworks.taggingservice.conn.ReflexConnection;
import jp.sourceforge.reflex.util.DeflateUtil;

/**
 * コネクション情報保持インターフェース.
 */
public interface ConnectionInfo {

	/**
	 * コネクションを格納.
	 * @param name キー
	 * @param conn コネクションオブジェクト
	 */
	public void put(String name, ReflexConnection<?> conn);

	/**
	 * スレッド間共有コネクションを格納.
	 * @param name キー
	 * @param conn コネクションオブジェクト
	 */
	public void putSharing(String name, ReflexConnection<?> conn);

	/**
	 * コネクションを取得.
	 * @param name キー
	 * @return コネクションオブジェクト
	 */
	public ReflexConnection<?> get(String name);

	/**
	 * スレッド間共有コネクションを取得.
	 * @param name キー
	 * @return コネクションオブジェクト
	 */
	public ReflexConnection<?> getSharing(String name);

	/**
	 * スレッド間共有コネクションを取得.
	 * @return コネクションオブジェクトリスト
	 */
	public Map<String, ReflexConnection<?>> getSharings();

	/**
	 * DeflateUtilを取得.
	 * @return DeflateUtil
	 */
	public DeflateUtil getDeflateUtil();

	/**
	 * 本クラスが保持する全てのコネクションのクローズ処理.
	 */
	public void close();

	/**
	 * 指定されたコネクションのクローズ処理.
	 * コネクションエラー時のクローズ
	 * @param name コネクション名
	 */
	public void close(String name);

}
