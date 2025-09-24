package jp.reflexworks.js.async;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.sourceforge.reflex.util.Requester;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * サーバサイドJS非同期処理.
 */
public class JsAsyncCallable extends ReflexCallable<Boolean> {

	/** メソッド */
	private String method;
	/** PathInfoとQuery */
	private String pathInfoQuery;
	/** リクエストヘッダ */
	private Map<String, String> headers;
	/** リクエストデータ */
	private byte[] payload;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param method メソッド
	 * @param pathInfoQuery PathInfoとQuery
	 * @param headers リクエストヘッダ
	 * @param payload リクエストデータ
	 */
	public JsAsyncCallable(String method, String pathInfoQuery, Map<String, String> headers,
			 byte[] payload) {
		this.method = method;
		this.pathInfoQuery = pathInfoQuery;
		this.headers = headers;
		this.payload = payload;
	}

	/**
	 * サーバサイドJS非同期処理
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		if (logger.isDebugEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[call] start.");
		}

		requestJsAsync();

		return true;
	}

	/**
	 * バッチジョブ実行管理リクエスト.
	 */
	public void requestJsAsync()
	throws IOException, TaggingException {
		// 非同期リクエストURL(サーブレットパスまで)
		String jsAsyncUrl = TaggingEnvUtil.getSystemProp(JsAsyncConst.PROP_URL_JSASYNC, null);
		int timeoutMillis = TaggingEnvUtil.getSystemPropInt(
				JsAsyncConst.JSASYNC_REQUEST_TIMEOUT_MILLIS,
				JsAsyncConst.JSASYNC_REQUEST_TIMEOUT_MILLIS_DEFAULT);
		if (StringUtils.isBlank(jsAsyncUrl)) {
			throw new IllegalStateException("JS async server url setting is requred.");
		}

		//try {
		String url = editUrl(jsAsyncUrl);

		Requester requester = new Requester();
		if (logger.isInfoEnabled()) {
			logger.info("[requestJsAsync] Request URL: " + url);
		}
		HttpURLConnection http = requester.request(url, method, payload, headers, timeoutMillis);
		if (logger.isInfoEnabled()) {
			logger.info("[requestJsAsync] Response status: " + http.getResponseCode());
		}

		//} catch (IOException | TaggingException | RuntimeException e) {
		//	logger.warn("[requestJsAsync] " + e.getClass().getName(), e);
		//}
	}

	/**
	 * リクエストURLから非同期オプション(_async)を除外し、URLエンコードを行う.
	 * @param jsAsyncUrl 非同期リクエストURL(サーブレットパスまで)
	 * @return 編集したURL
	 */
	private String editUrl(String jsAsyncUrl) {
		StringBuilder sb = new StringBuilder();
		sb.append(jsAsyncUrl);
		sb.append(pathInfoQuery);
		return sb.toString();
	}

}
