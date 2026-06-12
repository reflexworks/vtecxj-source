package jp.reflexworks.taggingservice.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * サービスのユーティリティ.
 * APサーバ等だけでなく、各BDBサーバにも必要なサービスの共通処理
 */
public class ServiceCommonUtil {

	/** サービス名に使用できる文字パターン */
	public static final String PATTERN_STR_SERVICENAME = "^[0-9a-zA-Z_-]+$";
	/** サービス名に使用できる文字パターンオブジェクト */
	public static final Pattern PATTERN_SERVICENAME = Pattern.compile(PATTERN_STR_SERVICENAME);

	/**
	 * サービス名に使用できる文字種かどうか
	 * @param str サービス名文字列
	 * @return サービス名に使用できる文字種の場合true
	 */
	public static boolean matchServiceNamePattern(String str) {
		// 文字種チェック
		Matcher matcher = PATTERN_SERVICENAME.matcher(str);
		return matcher.matches();
	}
	
}
