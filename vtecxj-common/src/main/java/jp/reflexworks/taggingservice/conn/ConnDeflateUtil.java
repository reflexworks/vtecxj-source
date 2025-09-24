package jp.reflexworks.taggingservice.conn;

import jp.sourceforge.reflex.util.DeflateUtil;

/**
 * DeflateUtil持ち回りクラス.
 */
public class ConnDeflateUtil implements ReflexConnection<DeflateUtil> {

	/** DeflateUtil */
	private DeflateUtil deflateUtil;

	/**
	 * コンストラクタ
	 * @param deflateUtil DeflateUtil
	 */
	public ConnDeflateUtil(DeflateUtil deflateUtil) {
		this.deflateUtil = deflateUtil;
	}

	/**
	 * コネクションオブジェクトを取得.
	 * @return コネクションオブジェクト
	 */
	public DeflateUtil getConnection() {
		return deflateUtil;
	}

	/**
	 * クローズ処理.
	 */
	public void close() {
		if (deflateUtil != null) {
			deflateUtil.end();
		}
	}

}
