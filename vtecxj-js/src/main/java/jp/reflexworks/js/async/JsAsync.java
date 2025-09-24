package jp.reflexworks.js.async;

import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.Cookie;
import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.servlet.util.UrlUtil;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.context.ReflexContextUtil;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.ReflexSecurityManager;
import jp.reflexworks.taggingservice.taskqueue.TaskQueueUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * サーバサイドJavaScript 非同期処理
 */
public class JsAsync {

	/** 非同期処理リクエスト時の除外パラメータ */
	private static final Set<String> IGNORE_PARAMS = new HashSet<String>();
	static {
		IGNORE_PARAMS.add(RequestParam.PARAM_ASYNC);
	}
	/** 非同期処理リクエスト時の除外ヘッダ */
	private static final Set<String> IGNORE_HEADERS = new HashSet<String>();
	static {
		IGNORE_HEADERS.add(ReflexServletConst.X_REQUESTED_WITH);
		IGNORE_HEADERS.add(Constants.HEADER_SERVICENAME);
		IGNORE_HEADERS.add(ReflexServletConst.HEADER_AUTHORIZATION);
		IGNORE_HEADERS.add(Constants.HEADER_IP_ADDR);
		IGNORE_HEADERS.add(ReflexServletConst.HEADER_COOKIE);
	}

	/**
	 * サーバサイドJS非同期処理.
	 * スレッドを起動する。
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param func サーバサイドJS名
	 * @param method メソッド
	 */
	public void doAsync(ReflexRequest req, ReflexResponse resp, String func, String method)
	throws IOException {
		try {
			ReflexContext reflexContext = ReflexContextUtil.getReflexContext(req, true);	// external
			String pathInfoQuery = getPathInfoQuery(req);
			Map<String, String> headers = getHeader(req, reflexContext);
			byte[] payload = getPayload(req);

			JsAsyncCallable callable = new JsAsyncCallable(method, pathInfoQuery, headers,
					payload);
			TaskQueueUtil.addTask(callable, 0, req.getAuth(), req.getRequestInfo(),
					req.getConnectionInfo());
		} catch (TaggingException e) {
			throw new IOException(e);
		}
	}

	/**
	 * リクエストのPathInfoとQueryString部分を抽出.
	 * _asyncオプションは除外する。
	 * @param req リクエスト
	 * @return リクエストのPathInfoとQueryString部分
	 */
	private String getPathInfoQuery(ReflexRequest req) {
		return UrlUtil.editPathInfoQuery(req, IGNORE_PARAMS, null, true);
	}

	/**
	 * 認証情報ヘッダを生成
	 * @param req リクエスト
	 * @return 認証情報ヘッダ
	 */
	private Map<String, String> getHeader(ReflexRequest req, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 認証情報
		Map<String, String> headers = new HashMap<>();
		String cookieSid = null;
		headers.put(ReflexServletConst.X_REQUESTED_WITH, JsAsyncConst.X_REQUESTED_WITH_JSASYNC);
		headers.put(Constants.HEADER_SERVICENAME, req.getServiceName());
		ReflexAuthentication auth = req.getAuth();
		if (Constants.AUTH_TYPE_ACCESSTOKEN.equals(auth.getAuthType())) {
			// AccessTokenを設定
			String accesstoken = reflexContext.getAccessToken();
			headers.put(ReflexServletConst.HEADER_AUTHORIZATION,
					ReflexServletConst.HEADER_AUTHORIZATION_TOKEN + accesstoken);

		} else if (!StringUtils.isBlank(auth.getSessionId())) {
			// SIDを設定
			/*
			StringBuilder sb = new StringBuilder();
			sb.append(ReflexServletConst.COOKIE_SID);
			sb.append("=");
			sb.append(auth.getSessionId());
			headers.put(ReflexServletConst.HEADER_COOKIE, sb.toString());
			*/
			cookieSid = auth.getSessionId();
		}

		// IPアドレスを設定
		ReflexSecurityManager securityManager = TaggingEnvUtil.getSecurityManager();
		String ipAddr = securityManager.getIPAddr(req);
		headers.put(Constants.HEADER_IP_ADDR, ipAddr);

		// その他のリクエストヘッダを設定
		Enumeration<String> headerNames = req.getHeaderNames();
		while (headerNames.hasMoreElements()) {
			String headerName = headerNames.nextElement();
			if (!IGNORE_HEADERS.contains(headerName)) {
				headers.put(headerName, req.getHeader(headerName));
			}
		}

		// Cookie
		StringBuilder sb = new StringBuilder();
		boolean isFirst = true;
		if (!StringUtils.isBlank(cookieSid)) {
			sb.append(ReflexServletConst.COOKIE_SID);
			sb.append("=");
			sb.append(cookieSid);
			isFirst = false;
		}

		Cookie[] cookies = req.getCookies();
		if (cookies != null) {
			for (Cookie cookie : cookies) {
				String name = cookie.getName();
				if (!ReflexServletConst.COOKIE_SID.equals(name)) {
					if (isFirst) {
						isFirst = false;
					} else {
						sb.append("; ");
					}
					sb.append(name);
					sb.append("=");
					sb.append(cookie.getValue());
				}
			}
		}

		String cookieValue = sb.toString();
		if (!StringUtils.isBlank(cookieValue)) {
			headers.put(ReflexServletConst.HEADER_COOKIE, cookieValue);
		}

		return headers;
	}

	/**
	 * リクエストデータを取得.
	 * @param req リクエスト
	 * @return リクエストデータ
	 */
	private byte[] getPayload(ReflexRequest req)
	throws IOException {
		return req.getPayload();
	}

}
