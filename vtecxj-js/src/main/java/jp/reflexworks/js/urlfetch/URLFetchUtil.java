package jp.reflexworks.js.urlfetch;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.servlet.util.UrlUtil;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.SettingConst;
import jp.reflexworks.taggingservice.blogic.ServiceBlogic;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.sourceforge.reflex.util.FileUtil;
import jp.sourceforge.reflex.util.Requester;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * URLFetch処理クラス.
 */
public class URLFetchUtil {

	/** URLフェッチタイムアウト時間(ミリ秒) デフォルト値 */
	private static final int URLFETCH_TIMEOUTMILLIS_DEFAULT = 60000;

	/** ロガー. */
	private static final Logger logger = LoggerFactory.getLogger(URLFetchUtil.class);

	/**
	 * URLFetch
	 * @param reflexContext ReflexContext
	 * @param url URL
	 * @param method メソッド
	 * @param reqData リクエストデータ
	 * @param headers リクエストヘッダ
	 * @param timeoutMillis タイムアウト時間(ミリ秒)。0以下はデフォルト値。
	 * @return レスポンス情報.以下の形式のJSONで返却。
	 *     {
	 *         "status": レスポンスステータス,
	 *         "headers":  {"キー": "値", "キー2": "値2", ... },
	 *         "data": "レスポンスデータ"
	 *     }
	 */
	public static String request(ReflexContext reflexContext, String url,
			String method, String reqData, Map<String, String> reqHeaders,
			int timeoutMillis)
	throws IOException, TaggingException {
		// 自サービスへのリクエストは不可
		checkUrl(reflexContext, url);
		checkMethod(method);
		String serviceName = reflexContext.getServiceName();

		// タイムアウト時間が0以下の場合はデフォルト値とする。
		if (timeoutMillis <= 0) {
			timeoutMillis = TaggingEnvUtil.getPropInt(serviceName,
					SettingConst.URLFETCH_TIMEOUTMILLIS, URLFETCH_TIMEOUTMILLIS_DEFAULT);
		}

		byte[] inputData = null;
		if (!StringUtils.isBlank(reqData)) {
			inputData = reqData.getBytes(Constants.ENCODING);
			// Content-Typeの指定がない場合、JSONであれば指定を付加する。
			if ((reqData.startsWith("{") || reqData.startsWith("[")) &&
					(reqHeaders == null || 
					!reqHeaders.containsKey(ReflexServletConst.HEADER_CONTENT_TYPE) ||
					!reqHeaders.containsKey(ReflexServletConst.HEADER_CONTENT_TYPE_LOWERCASE))) {
				if (reqHeaders == null) {
					reqHeaders = new HashMap<>();
				}
				reqHeaders.put(ReflexServletConst.HEADER_CONTENT_TYPE, 
						ReflexServletConst.CONTENT_TYPE_JSON);
			}
		}
		try {
			Requester requester = new Requester();
			HttpURLConnection http = requester.request(url, method, inputData, reqHeaders,
					timeoutMillis);
			if (logger.isDebugEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(reflexContext.getRequestInfo()));
				sb.append("[request] ");
				sb.append(method);
				sb.append(" ");
				sb.append(url);
				sb.append(" connectTimeout=");
				sb.append(http.getConnectTimeout());
				sb.append(", readTimeout=");
				sb.append(http.getReadTimeout());
				if (reqHeaders != null && !reqHeaders.isEmpty()) {
					sb.append(" [headers]");
					boolean isFirst = true;
					for (Map.Entry<String, String> mapEntry : reqHeaders.entrySet()) {
						if (isFirst) {
							isFirst = false;
						} else {
							sb.append(", ");
						}
						sb.append(mapEntry.getKey());
						sb.append(":");
						sb.append(mapEntry.getValue());
					}
				}
				
				logger.debug(sb.toString());
			}

			int status = http.getResponseCode();
			InputStream in = null;
			if (status >= 400) {
				in = http.getErrorStream();
			} else {
				in = http.getInputStream();
			}
			String respData = null;
			if (in != null) {
				respData = FileUtil.readString(in);
			}
			Map<String, String> respHeaders = new HashMap<String, String>();
			Map<String, List<String>> headerFields = http.getHeaderFields();
			if (headerFields != null) {
				for (Map.Entry<String, List<String>> mapEntry : headerFields.entrySet()) {
					String key = mapEntry.getKey();
					List<String> values = mapEntry.getValue();
					StringBuilder sb = new StringBuilder();
					boolean isFirst = true;
					for (String val : values) {
						if (isFirst) {
							isFirst = false;
						} else {
							sb.append(",");
						}
						sb.append(val);
					}
					respHeaders.put(key, sb.toString());
				}
			}

			String result = convertToJson(status, respHeaders, respData);
			if (logger.isDebugEnabled()) {
				logger.debug("[request] result = " + result);
			}
			return result;

		} catch (IOException e) {
			// リクエストのIOExceptionはユーザ原因なので、400エラーとなる例外を返す。
			throw new InvalidServiceSettingException(e);
		}
	}

	/**
	 * レスポンス情報をJSONに変換.
	 * @param status ステータス
	 * @param respHeaders レスポンスヘッダ
	 * @param respData レスポンスデータ
	 *     {
	 *         "status": レスポンスステータス,
	 *         "headers":  {"キー": "値", "キー2": "値2", ... },
	 *         "data": "レスポンスデータ"
	 *     }
	 */
	private static String convertToJson(int status, Map<String, String> respHeaders,
			String respData) {
		StringBuilder sb = new StringBuilder();
		sb.append("{");
		sb.append("\"status\": ");
		sb.append(status);
		if (respHeaders != null && !respHeaders.isEmpty()) {
			sb.append(", \"headers\": {");
			boolean isFirst = true;
			for (Map.Entry<String, String> mapEntry : respHeaders.entrySet()) {
				if (isFirst) {
					isFirst = false;
				} else {
					sb.append(", ");
				}
				sb.append(jsonQuote(mapEntry.getKey()));
				sb.append(": ");
				sb.append(jsonQuote(mapEntry.getValue()));
			}
			sb.append("}");
		}
		if (!StringUtils.isBlank(respData)) {
			sb.append(", \"data\": ");
			sb.append(jsonQuote(respData));
		}
		sb.append("}");
		return sb.toString();
	}

	/**
	 * JSONの値をエスケープし、クォートで囲む.
	 * @param str JSONの値
	 * @return 編集した値
	 */
	private static String jsonQuote(String str) {
		if (str == null) {
			return "\"null\"";
		}
		return JSONObject.quote(str);
	}

	/**
	 * URLチェック.
	 * 自サービスへのリクエストは不可。
	 * @param reflexContext ReflexContext
	 * @param url URL
	 */
	private static void checkUrl(ReflexContext reflexContext, String url)
	throws IOException, TaggingException {
		CheckUtil.checkNotNull(url, "URL");
		String host = UrlUtil.getHost(url);
		CheckUtil.checkNotNull(host, "URL host");
		String serviceName = reflexContext.getServiceName();
		ServiceBlogic serviceBlogic = new ServiceBlogic();
		String serviceHost = UrlUtil.getHostWithoutPort(serviceBlogic.getHost(
				serviceName));
		if (host.equals(serviceHost)) {
			StringBuilder sb = new StringBuilder();
			sb.append("You can not request this service URL. serviceName=");
			sb.append(serviceName);
			sb.append(", URL=");
			sb.append(url);
			throw new IllegalParameterException(sb.toString());
		}
	}

	/**
	 * メソッド入力チェック.
	 * @param method method
	 */
	private static void checkMethod(String method) {
		CheckUtil.checkNotNull(method, "method");
	}

}
