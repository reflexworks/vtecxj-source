package jp.reflexworks.taggingservice.util;

/**
 * ログ出力用マスクユーティリティ.
 * 認証情報・セッションIDなどの機密情報を伏せ字にする。
 */
public class MaskUtil {

	/** マスク前に表示するトークン文字数 */
	public static final int BEFORE_HIDDEN_LEN = 5;

	/** 伏せ字文字列 */
	public static final String HIDDEN_TEXT = "*****";

	private MaskUtil() {}

	/**
	 * Authorization ヘッダ値をマスク.
	 * 認証スキームとトークン先頭5文字のみ表示する。
	 * 例: "Bearer abcdefghijk" → "Bearer abcde*****"
	 */
	public static String maskAuthorizationHeader(String value) {
		if (value == null) {
			return null;
		}
		int idx = value.indexOf(" ");
		if (idx > 0) {
			int subEnd = idx + 1 + BEFORE_HIDDEN_LEN;
			if (value.length() > subEnd) {
				return value.substring(0, subEnd) + HIDDEN_TEXT;
			}
			return value.substring(0, idx + 1) + HIDDEN_TEXT;
		}
		return HIDDEN_TEXT;
	}

	/**
	 * Cookie ヘッダ値内の SID をマスク.
	 * 例: "SID=abcdefghijk; other=val" → "SID=abcde*****; other=val"
	 */
	public static String maskCookieSid(String cookieValue) {
		if (cookieValue == null) {
			return null;
		}
		return maskKeyValue(cookieValue, "SID", ";");
	}

	/**
	 * Set-Cookie ヘッダ値の SID をマスク.
	 * 例: "SID=abcdefghijk; Path=/; HttpOnly" → "SID=abcde*****; Path=/; HttpOnly"
	 */
	public static String maskSetCookieSid(String setCookieValue) {
		if (setCookieValue == null) {
			return null;
		}
		return maskKeyValue(setCookieValue, "SID", ";");
	}

	/**
	 * トークン値をマスク（先頭5文字 + *****）.
	 * 例: "abcdefghijk" → "abcde*****"
	 */
	public static String maskToken(String token) {
		if (token == null) {
			return null;
		}
		if (token.length() > BEFORE_HIDDEN_LEN) {
			return token.substring(0, BEFORE_HIDDEN_LEN) + HIDDEN_TEXT;
		}
		return HIDDEN_TEXT;
	}

	/**
	 * "KEY=value; ..." 形式の文字列中の指定キーの値をマスク.
	 * キー名は大文字小文字を無視して検索する。
	 */
	private static String maskKeyValue(String src, String key, String delimiter) {
		String srcLower = src.toLowerCase();
		String prefixLower = key.toLowerCase() + "=";
		int start = srcLower.indexOf(prefixLower);
		if (start < 0) {
			return src;
		}
		int valueStart = start + prefixLower.length();
		int valueEnd = src.indexOf(delimiter, valueStart);
		if (valueEnd < 0) {
			valueEnd = src.length();
		}
		String rawValue = src.substring(valueStart, valueEnd);
		String masked;
		if (rawValue.length() > BEFORE_HIDDEN_LEN) {
			masked = rawValue.substring(0, BEFORE_HIDDEN_LEN) + HIDDEN_TEXT;
		} else {
			masked = HIDDEN_TEXT;
		}
		return src.substring(0, valueStart) + masked + src.substring(valueEnd);
	}

}
