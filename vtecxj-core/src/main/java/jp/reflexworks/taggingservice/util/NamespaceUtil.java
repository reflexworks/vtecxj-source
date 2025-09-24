package jp.reflexworks.taggingservice.util;

import java.io.IOException;

import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.NamespaceManager;

/**
 * 名前空間取得ユーティリティ
 */
public class NamespaceUtil {

	/**
	 * 名前空間を取得.
	 * static領域から取得する。settingServiceが実行済みであることが前提。
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 名前空間
	 */
	public static String getNamespace(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		NamespaceManager namespaceManager = TaggingEnvUtil.getNamespaceManager();
		return namespaceManager.getNamespace(serviceName, requestInfo, connectionInfo);
	}

}
