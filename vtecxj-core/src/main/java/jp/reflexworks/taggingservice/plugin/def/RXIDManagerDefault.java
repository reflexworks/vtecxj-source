package jp.reflexworks.taggingservice.plugin.def;

import java.io.IOException;

import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.RXIDManager;

/**
 * RXID管理クラス.
 */
public class RXIDManagerDefault implements RXIDManager {

	/**
	 * 初期処理.
	 */
	public void init() {
		// Do nothing.
	}

	/**
	 * シャットダウン処理.
	 */
	public void close() {
		// Do nothing.
	}

	/**
	 * RXIDの使用回数を加算する.
	 * @param rxid RXID
	 * @param rxidExpireSec RXID有効時間(秒)
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return RXIDの使用回数(今回分を加算)
	 */
	@Override
	public long incrementRXID(String rxid, int rxidExpireSec, String serviceName,
			String namespace, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// このクラスを使用する場合、RXIDの使用回数チェックは行えない。
		return 1;
	}

}
