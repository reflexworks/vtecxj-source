package jp.reflexworks.taggingservice.conn;

import jp.sourceforge.reflex.util.DeflateUtil;

/**
 * スレッドごとのコネクション情報.
 */
public class TaggingThreadConnectionInfo extends ThreadConnectionInfo {

	/**
	 * コンストラクタ.
	 * @param deflateUtil DeflateUtil
	 */
	public TaggingThreadConnectionInfo(DeflateUtil deflateUtil) {
		super(deflateUtil);
	}

}
