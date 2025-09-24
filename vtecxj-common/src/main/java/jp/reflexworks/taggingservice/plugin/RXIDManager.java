package jp.reflexworks.taggingservice.plugin;

import java.io.IOException;

import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;

/**
 * RXID生成・管理インターフェース
 */
public interface RXIDManager extends ReflexPlugin {

	/**
	 * RXIDの使用回数を加算する.
	 * @param rxid RXID
	 * @param rxidExpireSec RXID有効時間(秒)
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return RXIDの使用回数
	 */
	public long incrementRXID(String rxid, int rxidExpireSec, String serviceName,
			String namespace, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

}
