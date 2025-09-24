package jp.reflexworks.taggingservice.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.env.ReflexEnvConst;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * Cookie操作ユーティリティ
 */
public class CookieUtil {

	private static final String PATTERN_STR_SETCOOKIE = "(.*?,|.*$)";
	private static final Pattern PATTERN_SETCOOKIE = Pattern.compile(PATTERN_STR_SETCOOKIE);

	/** sameSite指定 : lax */
	private static final String SAME_SITE_LAX = "__SAME_SITE_LAX__";
	
	/**
	 * 指定されたキー、値をレスポンスにSet-Cookieで設定します.
	 * @param resp レスポンス
	 * @param key Cookieのキー
	 * @param value Cookieの値
	 * @param maxAge Cookie存続時間(秒)
	 */
	public static void setCookie(HttpServletResponse resp,
			String key, String value, int maxAge) {
		setCookie(resp, key, value, maxAge, false, false);
	}

	/**
	 * 指定されたキー、値をレスポンスにSet-Cookieで設定します.
	 * @param resp レスポンス
	 * @param key Cookieのキー
	 * @param value Cookieの値
	 * @param maxAge Cookie存続時間(秒)
	 * @param isHttpOnly HttpOnlyを指定する場合true
	 * @param isSecure secureを指定する場合true
	 */
	public static void setCookie(HttpServletResponse resp,
			String key, String value, int maxAge, boolean isHttpOnly,
			boolean isSecure) {
		if (key != null && key.length() > 0) {
			Cookie cookie = new Cookie(key, value);
			setCookie(resp, cookie, maxAge, isHttpOnly, isSecure);
		}
	}

	/**
	 * 指定されたCookieをレスポンスにSet-Cookieで設定します.
	 * @param resp レスポンス
	 * @param cookie Cookie
	 * @param maxAge Cookie存続時間(秒)
	 * @param isHttpOnly HttpOnlyを指定する場合true
	 * @param isSecure Secureを指定する場合true
	 */
	public static void setCookie(HttpServletResponse resp,
			Cookie cookie, int maxAge, boolean isHttpOnly, boolean isSecure) {
		if (cookie != null) {
			if (maxAge <= 0) {
				maxAge = getDefaultMaxAge();
			}
			cookie.setMaxAge(maxAge);
			cookie.setPath("/");
			if (isHttpOnly) {
				cookie.setHttpOnly(isHttpOnly);
			}
			if (isSecure) {
				cookie.setSecure(isSecure);
			}
			cookie.setComment(SAME_SITE_LAX);
			resp.addCookie(cookie);
		}
	}

	/**
	 * デフォルトのMaxAgeを取得.
	 * @return デフォルトのMaxAge
	 */
	private static int getDefaultMaxAge() {
		return ReflexEnvConst.SESSION_MINUTE_DEFAULT * 60;
	}

	/**
	 * 指定されたキーのCookieを削除するようレスポンスにSet-Cookieで設定します.
	 * @param resp レスポンス
	 * @param key Cookieのキー
	 */
	public static void deleteCookie(HttpServletResponse resp,
			String key) {
		if (key != null && key.length() > 0) {
			Cookie cookie = new Cookie(key, "");
			deleteCookie(resp, cookie);
		}
	}

	/**
	 * 指定されたCookieを削除するようレスポンスにSet-Cookieで設定します.
	 * @param resp レスポンス
	 * @param cookie Cookie
	 */
	public static void deleteCookie( HttpServletResponse resp,
			Cookie cookie) {
		if (cookie != null) {
			cookie.setMaxAge(0);
			cookie.setPath("/");
			resp.addCookie(cookie);
		}
	}

	/**
	 * リクエストから指定されたキーのCookieを取得します.
	 * @param req リクエスト
	 * @param key Cookieのキー
	 * @return Cookie
	 */
	public static Cookie getCookie(HttpServletRequest req, String key) {
		if (key == null) {
			return null;
		}
		Cookie[] cookies = req.getCookies();
		if (cookies != null) {
			for (Cookie cookie : cookies) {
				if (key.equals(cookie.getName())) {
					return cookie;
				}
			}
		}
		return null;
	}

	/**
	 * リクエストから指定されたキーのCookieを取得し、値を返却します.
	 * @param req リクエスト
	 * @param key Cookieのキー
	 * @return Cookieの値
	 */
	public static String getCookieValue(HttpServletRequest req, String key) {
		Cookie cookie = getCookie(req, key);
		if (cookie != null) {
			return cookie.getValue();
		}
		return null;
	}

	/**
	 * Set-Cookieで設定されたCookieのキーと値をMapにして返却します.
	 * @param resp レスポンス
	 * @return Cookie情報(key, value)
	 */
	public static Map<String, String> parseSetCookie(ReflexResponse resp) {
		if (resp == null) {
			return null;
		}
		Collection<String> setCookieStrs = resp.getHeaders(ReflexServletConst.HEADER_SET_COOKIE);
		if (setCookieStrs == null || setCookieStrs.isEmpty()) {
			return null;
		}
		Map<String, String> setCookies = new HashMap<String, String>();
		for (String setCookieStr : setCookieStrs) {
			Map<String, String> tmpSetCookies = parseSetCookie(setCookieStr);
			setCookies.putAll(tmpSetCookies);
		}
		return setCookies;
	}

	/**
	 * Set-Cookieで設定されたCookieのキーと値をMapにして返却します.
	 * @param setcookieStr Set-Cookieの文字列
	 * @return Cookie情報(key, value)
	 */
	public static Map<String, String> parseSetCookie(String setcookieStr) {
		Map<String, String> cookieMap = new HashMap<String, String>();
		Matcher matcher = PATTERN_SETCOOKIE.matcher(setcookieStr);
		while (matcher.find()) {
			String str = matcher.group();
			if (str != null) {
				str = str.trim();
				int idx = str.indexOf("=");
				if (idx > 0) {
					int idx2 = str.indexOf(";");
					int idx3 = str.indexOf(",");
					if (idx2 < 0) {
						idx2 = idx3;
					}
					if (idx2 < 0) {
						idx2 = str.length();
					}
					if (idx + 1 < idx2) {
						String key = str.substring(0, idx);
						String val = str.substring(idx + 1, idx2);
						cookieMap.put(key, val);
					}
				}
			}
		}
		return cookieMap;
	}

	/**
	 * Cookieのキー、値が設定されたMapから、リクエストに使用するCookie文字列を返却します.
	 * @param cookieMap Cookieのキー、値が設定されたMap
	 * @return Cookie文字列
	 */
	public static String getCookieString(Map<String, String> cookieMap) {
		if (cookieMap == null || cookieMap.size() == 0) {
			return null;
		}
		boolean isFirst = true;
		StringBuilder buf = new StringBuilder();
		for (Map.Entry<String, String> mapEntry : cookieMap.entrySet()) {
			if (isFirst) {
				isFirst = false;
			} else {
				buf.append("; ");
			}
			buf.append(mapEntry.getKey());
			buf.append("=");
			buf.append(mapEntry.getValue());
		}
		return buf.toString();
	}

	/**
	 * Map形式のヘッダから、Cookieを抽出し、指定されたキーの値を返却します.
	 * @param headers レスポンスヘッダ
	 * @param key Cookieのキー
	 * @return Cookieの値
	 */
	public static String getCookieValueFromHeaders(Map<String, List<String>> headers,
			String key) {
		if (headers == null || StringUtils.isBlank(key)) {
			return null;
		}
		String keyPrefix = key + "=";
		int keyPrefixLen = keyPrefix.length();
		String value = null;
		List<String> cookies = headers.get(ReflexServletConst.HEADER_SET_COOKIE);
		if (cookies != null) {
			for (String cookie : cookies) {
				if (cookie.startsWith(keyPrefix)) {
					int idx = cookie.indexOf(";");
					if (idx == -1) {
						idx = cookie.length();
					}
					value = cookie.substring(keyPrefixLen, idx);
					// ダブルクォーテーションを除去
					value = StringUtils.trimDoubleQuotes(value);
					break;
				}
			}
		}
		return value;
	}

}
