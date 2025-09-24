package jp.reflexworks.taggingservice.redis;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.RXIDManager;

/**
 * RXID使用回数管理クラス.
 */
public class JedisRXIDManager extends JedisCommonManager implements RXIDManager {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 初期処理.
	 */
	@Override
	public void init() {
		// 初期処理
		JedisUtil.init();
	}

	/**
	 * シャットダウン処理
	 */
	@Override
	public void close() {
		JedisUtil.close();
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
	public long incrementRXID(String rxid, int expireSec, String serviceName,
			String namespace, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String key = createRXIDCountKey(rxid, serviceName, namespace, requestInfo, connectionInfo);
		Long ret = incrementProc(key, 1, requestInfo, connectionInfo);
		long cnt = 0;
		if (ret != null) {
			cnt = ret.longValue();
		}
		if (cnt == 1) {
			// expire指定
			setExpireProc(key, expireSec, requestInfo, connectionInfo);
		}
		return cnt;
	}

	/**
	 * RXID件数キーを取得
	 * @param name キー
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return RXID件数
	 */
	private String createRXIDCountKey(String name, String serviceName, String namespace,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append(getKeyPrefix(serviceName, namespace, requestInfo, connectionInfo));
		sb.append(JedisConst.TBL_RXID);
		sb.append(name);
		return sb.toString();
	}

}
