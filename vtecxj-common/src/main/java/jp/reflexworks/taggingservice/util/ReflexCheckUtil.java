package jp.reflexworks.taggingservice.util;

import java.text.ParseException;
import java.util.Collection;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.sourceforge.reflex.util.DateUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * チェックユーティリティ.
 */
public class ReflexCheckUtil {

	/** URIに使用可能な文字 : 英数字と$、_(アンダースコア)、.-@'()[]、全角文字 */
	public static final String URI_ALLOWED_CHAR_REGEX = "[a-zA-Z0-9\\$\\_\\.\\-@'\\(\\)\\[\\]\\/[^\\x01-\\x7E]]+";
	/** URIに使用可能な文字パターン */
	public static final Pattern PATTERN_URI_ALLOWED_CHAR =
			Pattern.compile(URI_ALLOWED_CHAR_REGEX);
	/** メールアドレス正規表現(簡易版) */
	public static final String PATTERN_STR_EMAIL = "^[a-zA-Z0-9\\-\\.!#\\$%&'\\*\\+\\/=\\?\\^_`\\{\\|\\}~]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$";
	/** メールアドレスパターン(簡易版) */
	public static final Pattern PATTERN_EMAIL = Pattern.compile(PATTERN_STR_EMAIL);

	/**
	 * 必須チェック
	 * @param val 値
	 * @param name 名前(エラーメッセージに使用)
	 */
	public static void checkNotNull(String val, String name) {
		if (StringUtils.isBlank(val)) {
			throw new IllegalParameterException(name + " is required.");
		}
	}

	/**
	 * 必須チェック
	 * @param val 値
	 * @param name 名前(エラーメッセージに使用)
	 */
	public static void checkNotNull(Object val, String name) {
		if (val == null) {
			throw new IllegalParameterException(name + " is required.");
		}
		if (val instanceof Collection) {
			// コレクションが空の場合もエラー
			Collection<?> collection = (Collection<?>)val;
			if (collection.isEmpty()) {
				throw new IllegalParameterException(name + " element is required.");
			}
		}
	}

	/**
	 * URIチェック
	 * @param uri URI
	 */
	public static void checkUri(String uri) {
		checkUri(uri, "Key");
	}

	/**
	 * URIチェック
	 * @param uri URI
	 * @param name "Key"または"Parent key"
	 */
	public static void checkUri(String uri, String name) {
		// 値なしはエラー
		if (uri == null) {
			throw new IllegalParameterException(name + " is required.");
		}
		// 先頭が/で始まっていない場合は不可
		if (!uri.startsWith("/")) {
			throw new IllegalParameterException(name + " must start with a slash. " + uri);
		}

		// 空 (//)は不可
		if (uri.indexOf("//") > -1) {
			throw new IllegalParameterException(name + " must not contain blank hierarchy. " + uri);
		}

		// 文字種チェック
		if (!StringUtils.isBlank(uri)) {
			Matcher matcher = PATTERN_URI_ALLOWED_CHAR.matcher(uri);
			if (!matcher.matches()) {
				throw new IllegalParameterException(
						name + " must not contain any prohibited characters. " + uri);
			}
		}

		// 階層は10階層まで -> リクエスト元でチェックする。

	}

	/**
	 * selfidチェック
	 * @param uri URI
	 * @param name エラーメッセージで使用する名称
	 */
	public static void checkSelfid(String selfid, String name) {
		// 値なしはエラー
		if (StringUtils.isBlank(selfid)) {
			throw new IllegalParameterException(name + " is required.");
		}
		// /が使用されているとエラー
		if (selfid.indexOf("/") > -1) {
			throw new IllegalParameterException(
					name + " must not contain any prohibited characters. " + selfid);
		}
		// 文字種チェック
		if (!StringUtils.isBlank(selfid)) {
			Matcher matcher = PATTERN_URI_ALLOWED_CHAR.matcher(selfid);
			if (!matcher.matches()) {
				throw new IllegalParameterException(
						name + " must not contain any prohibited characters. " + selfid);
			}
		}
	}

	/**
	 * uriの最後が"/"でないかチェックします.
	 * 最後が"/"の場合エラーをスローします
	 * @param uri URI
	 * @param name 項目名
	 */
	public static void checkLastSlash(String uri, String name) {
		if (uri != null && !"/".equals(uri) && uri.endsWith("/")) {
			throw new IllegalParameterException(name + " must not end with a slash. " + uri);
		}
	}

	/**
	 * 文字列が数値(int型)かどうかチェック.
	 * @param str 文字列
	 */
	public static void checkInt(String str) {
		checkInt(str, null);
	}

	/**
	 * 文字列が数値(int型)かどうかチェック.
	 * @param str 文字列
	 * @param name 項目名
	 */
	public static void checkInt(String str, String name) {
		if (!StringUtils.isInteger(str)) {
			StringBuilder sb = new StringBuilder();
			sb.append("Please set a integer number");
			if (!StringUtils.isBlank(name)) {
				sb.append(" for ");
				sb.append(name);
			}
			sb.append(".");
			throw new IllegalParameterException(sb.toString());
		}
	}

	/**
	 * 文字列が数値(long型)かどうかチェック.
	 * @param str 文字列
	 */
	public static void checkLong(String str) {
		checkLong(str, null);
	}

	/**
	 * 文字列が数値(long型)かどうかチェック.
	 * @param str 文字列
	 * @param name 項目名
	 */
	public static void checkLong(String str, String name) {
		if (!StringUtils.isLong(str)) {
			StringBuilder sb = new StringBuilder();
			sb.append("Please set a long number");
			if (!StringUtils.isBlank(name)) {
				sb.append(" for ");
				sb.append(name);
			}
			sb.append(".");
			throw new IllegalParameterException(sb.toString());
		}
	}

	/**
	 * 文字列がDate型かどうかチェック.
	 * @param str 文字列
	 * @return Date
	 */
	public static Date checkDate(String str) {
		try {
			return DateUtil.getDate(str);
		} catch (ParseException e) {
			throw new IllegalParameterException("Please specify in date format.", e);
		}
	}

	/**
	 * 正の数かどうかチェック.
	 * @param num 数値
	 * @param name 項目名
	 */
	public static void checkPositiveNumber(int num, String name) {
		if (num <= 0) {
			throw new IllegalParameterException("Please set a positive number for " + name + ".");
		}
	}

	/**
	 * 指定されたパターンにマッチするかどうかのチェック
	 * @param pattern パターン
	 * @param str チェック文字列
	 * @param msgName 項目名。エラーの場合エラーメッセージに使用する。
	 * @return Matcher
	 */
	public static Matcher checkPatternMatch(Pattern pattern, String str, String msgName) {
		Matcher matcher = pattern.matcher(str);
		if (!matcher.matches()) {
			throw new IllegalParameterException("Format error: " + msgName);
		}
		return matcher;
	}

	/**
	 * 指定された最小値、最大値の大小が逆転していないかチェック.
	 * @param min 最小値
	 * @param max 最大値
	 * @param allowEqual 最小値と最大値が同じ場合を許す場合はtrue
	 */
	public static void checkCompare(String min, String max, boolean allowEqual) {
		if (StringUtils.isBlank(max)) {
			return;
		}
		checkCompare(Long.parseLong(min), Long.parseLong(max), allowEqual);
	}

	/**
	 * 指定された最小値、最大値の大小が逆転していないかチェック.
	 * @param min 最小値
	 * @param max 最大値
	 * @param allowEqual 最小値と最大値が同じ場合を許す場合はtrue
	 */
	public static void checkCompare(long min, long max, boolean allowEqual) {
		if (!allowEqual && min == max) {
			throw new IllegalParameterException("The minimum and maximum values are equal. min = " + min + ", max = " + max);
		}
		if (min > max) {
			throw new IllegalParameterException("The minimum and maximum values are reversed. min = " + min + ", max = " + max);
		}
	}

	/**
	 * 入力値がメールアドレス形式かどうかチェックする.
	 * nullの場合判定しない.
	 * @param addr メールアドレス
	 */
	public static void checkMailAddress(String addr) {
		if (StringUtils.isBlank(addr)) {
			return;
		}
		Matcher matcher = PATTERN_EMAIL.matcher(addr);
		if (!matcher.matches()) {
			throw new IllegalParameterException("Mail address is invalid. " + addr);
		}
	}

	/**
	 * 入力値がメールアドレスのカンマ区切り形式かどうかチェックする.
	 * @param to 送信先
	 */
	public static void checkMailTo(String[] to) {
		for (String part : to) {
			checkMailAddress(part);
		}
	}

	/**
	 * 入力値がメールアドレスのカンマ区切り形式かどうかチェックする.
	 * @param to 送信先
	 */
	public static void checkMailTo(String to) {
		checkNotNull(to, "Mail address");
		checkMailAddress(to);
	}

	/**
	 * 加算枠フォーマットチェック
	 * @param range 加算枠
	 */
	public static void checkRangeIds(String range) {
		if (!StringUtils.isBlank(range)) {
			// フォーマットチェック
			Matcher matcher = ReflexCheckUtil.checkPatternMatch(
					Constants.PATTERN_RANGE, range, "rangeIds");
			// 範囲が最小値-最大値となっているかチェック
			String minStr = matcher.group(1);
			String maxStr = matcher.group(3);
			ReflexCheckUtil.checkCompare(minStr, maxStr, false);
		}
	}

	/**
	 * IDフォーマットチェック
	 * @param id ID
	 */
	public static void checkId(String id) {
		checkNotNull(id, "ID");
		int idx = id.indexOf(",");
		int len = id.length();
		if (idx == -1 || idx == (len - 1)) {
			throw new IllegalParameterException("Revision is required for ID.");
		} else if (idx == 0) {
			throw new IllegalParameterException("Key is required for ID.");
		}
		int revision = TaggingEntryUtil.getRevisionById(id);
		if (revision <= 0) {
			throw new IllegalParameterException("Specify a value of 1 or higher for Revision.");
		}
		String uri = TaggingEntryUtil.getUriById(id);
		checkUri(uri);
	}

}
