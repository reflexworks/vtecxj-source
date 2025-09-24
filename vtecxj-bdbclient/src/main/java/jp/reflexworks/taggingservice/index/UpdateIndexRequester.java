package jp.reflexworks.taggingservice.index;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.bdbclient.BDBClientConst;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.requester.BDBRequesterUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.sourceforge.reflex.util.Requester;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * インデックス登録・更新・削除リクエスト処理クラス.
 * バッチジョブサーバへのリクエスト
 */
public class UpdateIndexRequester {

	/** ロガー. */
	private static final Logger logger = LoggerFactory.getLogger(UpdateIndexRequester.class);

	/**
	 * インデックス登録・更新・削除リクエスト.
	 * バッチジョブサーバへのリクエストを行う
	 * @param indexFeed インデックス更新情報
	 * @param isDelete インデックス削除の場合true
	 * @param reflexContext ReflexContext
	 */
	public static void requestUpdateIndex(FeedBase indexFeed, boolean isDelete,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		// リクエストURL(サーブレットパスまで)
		String updateIndexUrl = TaggingEnvUtil.getSystemProp(BDBClientConst.URL_UPDATEINDEX, null);
		if (StringUtils.isBlank(updateIndexUrl)) {
			throw new IllegalStateException("update index url setting is requred.");
		}

		byte[] payload = null;
		if (indexFeed != null) {
			FeedTemplateMapper mapper = TaggingEnvUtil.getResourceMapper(serviceName);
			payload = getBytes(indexFeed, mapper, reflexContext.getConnectionInfo());
		}

		StringBuilder sb = new StringBuilder();
		sb.append("?");
		if (isDelete) {
			sb.append(RequestParam.PARAM_DELETEINDEX);
		} else {
			sb.append(RequestParam.PARAM_UPDATEINDEX);
		}
		String requestUri = sb.toString();

		boolean setOutput = payload != null && payload.length > 0;
		String url = updateIndexUrl + requestUri;
		Map<String, String> headers = getHeader(setOutput, reflexContext);
		String method = Constants.PUT;

		Requester requester = new Requester();
		if (logger.isDebugEnabled()) {
			logger.debug("[requestUpdateIndex] Request URL: " + url);
		}
		// レスポンスを受け取って終了する。
		// エラーの場合は例外スロー
		try {
			int timeoutMillis = BDBRequesterUtil.getBDBRequestTimeoutMillis();
			HttpURLConnection http = requester.request(url, method, payload, headers, timeoutMillis);
			int status = http.getResponseCode();
			if (logger.isDebugEnabled()) {
				logger.debug("[requestUpdateIndex] Response status: " + status);
			}

		} catch (org.msgpack.MessageTypeException e) {
			// このエラーはスロー先で入力エラーとみなされてしまうので、致命的例外でラップする。
			throw new IllegalStateException(e);
		}
	}

	/**
	 * 認証情報ヘッダを生成
	 * @param setOutput リクエストデータを設定する場合true
	 * @param reflexContext ReflexContext
	 * @return 認証情報ヘッダ
	 */
	private static Map<String, String> getHeader(boolean setOutput, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String systemService = reflexContext.getServiceName();
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		// 認証情報
		Map<String, String> headers = new HashMap<>();
		headers.put(ReflexServletConst.X_REQUESTED_WITH, BDBClientConst.X_REQUESTED_WITH_MIGRATE);
		headers.put(Constants.HEADER_SERVICENAME, systemService);
		if (Constants.AUTH_TYPE_ACCESSTOKEN.equals(auth.getAuthType())) {
			// AccessTokenを設定
			String accesstoken = reflexContext.getAccessToken();
			headers.put(ReflexServletConst.HEADER_AUTHORIZATION,
					ReflexServletConst.HEADER_AUTHORIZATION_TOKEN + accesstoken);

		} else if (!StringUtils.isBlank(auth.getSessionId())) {
			// SIDを設定
			StringBuilder sb = new StringBuilder();
			sb.append(ReflexServletConst.COOKIE_SID);
			sb.append("=");
			sb.append(auth.getSessionId());
			headers.put(ReflexServletConst.HEADER_COOKIE, sb.toString());
		}

		// IPアドレスを設定
		String ipAddr = requestInfo.getIp();
		headers.put(Constants.HEADER_IP_ADDR, ipAddr);

		// リクエストデータを設定する場合
		if (setOutput) {
			headers.put(ReflexServletConst.HEADER_CONTENT_ENCODING,
					ReflexServletConst.HEADER_VALUE_DEFLATE);
			headers.put(ReflexServletConst.HEADER_CONTENT_TYPE,
					ReflexServletConst.CONTENT_TYPE_MESSAGEPACK);
		}

		return headers;
	}

	/**
	 * Feedをバイト配列に変換する.
	 * @param feed Feed
	 * @param mapper FeedTemplateMapper
	 * @param connectionInfo コネクション情報
	 * @return Feedをバイト配列に変換したオブジェクト
	 */
	private static byte[] getBytes(FeedBase feed, FeedTemplateMapper mapper, ConnectionInfo connectionInfo)
	throws IOException {
		return BDBRequesterUtil.toRequestData(feed, mapper,
				connectionInfo.getDeflateUtil());
	}

}
