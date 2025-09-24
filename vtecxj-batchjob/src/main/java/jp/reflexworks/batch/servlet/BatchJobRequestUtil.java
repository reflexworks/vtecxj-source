package jp.reflexworks.batch.servlet;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import jp.reflexworks.batch.BatchJobConst;
import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.plugin.ServiceManager;

/**
 * バッチジョブ実行用リクエスト生成のためのユーティリティ
 */
public class BatchJobRequestUtil {

	/**
	 * バッチジョブ実行用リクエスト作成.
	 * @param pathinfoAndQuerystring pathInfoとQueryString
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return バッチジョブ実行用リクエスト
	 */
	public static ReflexRequest createRequest(String pathinfoAndQuerystring,
			ReflexAuthentication auth, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		String serviceName = auth.getServiceName();
		Map<String, String> headers = getHeaders(serviceName);
		if (!pathinfoAndQuerystring.startsWith("/")) {
			pathinfoAndQuerystring = "/" + pathinfoAndQuerystring;
		}
		// HttpServletRequest
		BatchJobHttpRequest httpReq = new BatchJobHttpRequest(BatchJobConst.METHOD,
				pathinfoAndQuerystring, headers, requestInfo.getIp(), serviceName);
		// ReflexRequest
		BatchJobReflexRequest req = new BatchJobReflexRequest(httpReq);
		req.setServiceName(serviceName);
		req.setAuth(auth);
		req.setConnectionInfo(connectionInfo);
		return req;
	}

	/**
	 * サーバ名を取得.
	 * @param serviceName サーバ名
	 * @return サーバ名
	 */
	public static String getServerName(String serviceName) {
		ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
		String serverAndContextpath = serviceManager.getServiceServerContextpath(serviceName);
		int idx = serverAndContextpath.indexOf("/");
		if (idx > 0) {
			return serverAndContextpath.substring(0, idx);
		} else {
			return serverAndContextpath;
		}
	}

	/**
	 * リクエストヘッダを取得.
	 * @param serviceName サービス名
	 * @return リクエストヘッダ
	 */
	public static Map<String, String> getHeaders(String serviceName) {
		Map<String, String> headers = new HashMap<>();
		headers.put(ReflexServletConst.X_REQUESTED_WITH, BatchJobConst.LOG_TITLE);

		return headers;
	}

}
