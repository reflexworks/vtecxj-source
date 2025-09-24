package jp.reflexworks.taggingservice.conn;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.util.Constants;

/**
 * コネクションユーティリティ
 */
public class ReflexConnectionUtil {

	/** コネクション情報格納キー : Entryメモリキャッシュ */
	static final String CONNECTION_INFO_STATUSMAP = "_statusmap";

	/**
	 * メモリキャッシュからステータスマップを取得.
	 * スレッド内にメモリキャッシュが存在しない場合、メモリキャッシュを登録して返却します。
	 * このステータスマップはスレッド間で共通のオブジェクトです。
	 * @param serviceName サービス名
	 * @param connectionInfo コネクション情報
	 * @return ステータスマップ
	 */
	public static Map<String, String> getStatusMap(String serviceName,
			ConnectionInfo connectionInfo) {
		Map<String, String> statusMap = null;
		String mapKey = getActualConnName(CONNECTION_INFO_STATUSMAP, serviceName);
		ConnStatusMap connStatusMap = (ConnStatusMap)connectionInfo.getSharing(mapKey);
		if (connStatusMap != null) {
			statusMap = connStatusMap.getConnection();
		} else {
			statusMap = new ConcurrentHashMap<>();
			connStatusMap = new ConnStatusMap(statusMap);
			connectionInfo.putSharing(mapKey, connStatusMap);
		}
		return statusMap;
	}

	/**
	 * コネクション格納名を取得.
	 * <p>
	 * 「コネクション格納名@サービス名」
	 * </p>
	 * @param connName
	 * @param serviceName
	 * @return コネクション格納名
	 */
	public static String getActualConnName(String connName, String serviceName) {
		StringBuilder sb = new StringBuilder();
		sb.append(connName);
		sb.append(Constants.SVC_PREFIX_VAL);
		sb.append(serviceName);
		return sb.toString();
	}

}
