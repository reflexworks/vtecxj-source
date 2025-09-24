package jp.reflexworks.taggingservice.migrate;

import java.io.IOException;
import java.io.InputStream;
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
import jp.reflexworks.taggingservice.bdbclient.BDBClientConst;
import jp.reflexworks.taggingservice.bdbclient.BDBClientUtil;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.requester.BDBRequesterUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.sourceforge.reflex.util.DeflateUtil;
import jp.sourceforge.reflex.util.Requester;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * データ移行リクエストのためのユーティリティ.
 */
public class BDBClientMigrateUtil {

	/** ロガー. */
	private static final Logger logger = LoggerFactory.getLogger(BDBClientMigrateUtil.class);

	/**
	 * バッチジョブサーバへデータ移行リクエスト.
	 * @param serviceName 対象サービス名
	 * @param requestUri リクエストURI
	 * @param method メソッド
	 * @param feed リクエストデータ
	 * @param reflexContext システム管理サービスのReflexContext (ログイン情報含む)
	 */
	static void requestMigrate(String serviceName, String requestUri, String method, FeedBase feed,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		byte[] payload = null;
		if (feed != null) {
			FeedTemplateMapper mapper = TaggingEnvUtil.getResourceMapper(
					reflexContext.getServiceName());
			payload = getBytes(feed, mapper, reflexContext.getConnectionInfo());
		}
		requestMigrate(serviceName, requestUri, method, payload, reflexContext);
	}

	/**
	 * データ移行リクエスト
	 * @param serviceName サービス名
	 * @param requestUri リクエストURI
	 * @param method メソッド
	 * @param payload リクエストデータ
	 * @param reflexContext システム管理サービスのReflexContext (ログイン情報含む)
	 */
	static void requestMigrate(String serviceName, String requestUri, String method, byte[] payload,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();

		// リクエストURL(サーブレットパスまで)
		String migrateUrl = TaggingEnvUtil.getSystemProp(BDBClientConst.URL_MIGRATE, null);
		if (StringUtils.isBlank(migrateUrl)) {
			throw new IllegalStateException("migrate url setting is requred.");
		}

		boolean setOutput = payload != null && payload.length > 0;
		String url = migrateUrl + requestUri;
		Map<String, String> headers = getHeader(setOutput, reflexContext);

		Requester requester = new Requester();
		if (logger.isDebugEnabled()) {
			logger.debug("[requestMigrate] Request URL: " + url);
		}

		int numRetries = BDBRequesterUtil.getBDBRequestRetryCount();
		int waitMillis = BDBRequesterUtil.getBDBRequestRetryWaitmillis();
		int timeoutMillis = getMigrateRequestTimeoutMillis();
		for (int r = 0; r <= numRetries; r++) {
			// レスポンスを受け取って終了する。(内容は特に返却しない)
			// エラーの場合は例外スロー
			try {
				HttpURLConnection http = requester.request(url, method, payload, headers,
						timeoutMillis);
				int status = http.getResponseCode();
				if (logger.isDebugEnabled()) {
					logger.debug("[requestMigrate] Response status: " + status);
				}

				if (status >= 400) {
					// エラー
					FeedBase errFeed = (FeedBase)BDBRequesterUtil.getObject(url,
							http.getErrorStream(), http.getContentType(), serviceName,
							connectionInfo.getDeflateUtil(), true, false);
					BDBRequesterUtil.doException(url, method, status, errFeed, serviceName,
							requestInfo);
				}
				break;

			} catch (IOException e) {
				if (logger.isDebugEnabled()) {
					StringBuilder sb = new StringBuilder();
					sb.append(LogUtil.getRequestInfoStr(requestInfo));
					sb.append("[requestMigrate] ");
					sb.append(e.getClass().getName());
					sb.append(" ");
					sb.append(e.getMessage());
					sb.append(" [request] ");
					sb.append(method);
					sb.append(" ");
					sb.append(url);
					logger.debug(sb.toString());
				}
				// リトライ判定、入力エラー判定
				BDBClientUtil.convertError(e, method, requestInfo);
				if (r >= numRetries) {
					// リトライ対象だがリトライ回数を超えた場合
					throw e;
				}
				if (logger.isInfoEnabled()) {
					StringBuilder sb = new StringBuilder();
					sb.append(LogUtil.getRequestInfoStr(requestInfo));
					sb.append("[requestMigrate] ");
					sb.append(method);
					sb.append(" ");
					sb.append(url);
					sb.append(" ");
					sb.append(BDBClientUtil.getRetryLog(e, r));
					logger.info(sb.toString());
				}
				BDBClientUtil.sleep(waitMillis);

			} catch (org.msgpack.MessageTypeException e) {
				// このエラーはスロー先で入力エラーとみなされてしまうので、致命的例外でラップする。
				throw new IllegalStateException(e);
			}
		}
	}

	/**
	 * 認証情報ヘッダを生成
	 * @param setOutput リクエストデータを設定する場合true
	 * @param reflexContext ReflexContext
	 * @return 認証情報ヘッダ
	 */
	static Map<String, String> getHeader(boolean setOutput, ReflexContext reflexContext)
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
	static byte[] getBytes(FeedBase feed, FeedTemplateMapper mapper, ConnectionInfo connectionInfo)
	throws IOException {
		return BDBRequesterUtil.toRequestData(feed, mapper,
				connectionInfo.getDeflateUtil());
	}

	/**
	 * レスポンスデータのストリームからオブジェクトを取得する。
	 * MessagePack形式で受信。
	 * Entryオブジェクトとして受け取る場合、復号化する。
	 * @param urlStr URL (エラー時のログ出力用)
	 * @param in InputStream
	 * @param contentType Content-Typeヘッダ
	 * @param serviceName サービス名
	 * @param deflateUtil DeflateUtil
	 * @param isDecrypt 暗号項目の復号化をする場合true
	 * @return オブジェクト
	 */
	private static FeedBase getFeed(String urlStr, InputStream in, String contentType,
			String serviceName, DeflateUtil deflateUtil, boolean isDecrypt)
	throws IOException {
		if (in == null) {
			return null;
		}
		return (FeedBase)BDBRequesterUtil.getObject(urlStr, in, contentType, serviceName,
				deflateUtil, true, isDecrypt);
	}

	/**
	 * バッチジョブサーバへのデータ移行リクエストタイムアウト時間(ミリ秒)を取得.
	 * @return バッチジョブサーバへのデータ移行リクエストタイムアウト時間(ミリ秒)
	 */
	private static int getMigrateRequestTimeoutMillis() {
		return TaggingEnvUtil.getSystemPropInt(
				BDBClientMaintenanceConst.MIGRATE_REQUEST_TIMEOUT_MILLIS,
				BDBClientMaintenanceConst.MIGRATE_REQUEST_TIMEOUT_MILLIS_DEFAULT);
	}

}
