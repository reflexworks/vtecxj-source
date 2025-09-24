package jp.reflexworks.taggingservice.util;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.api.AtomConst;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.servlet.util.UrlUtil;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexEnv;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.ReflexStatic;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.api.SettingConst;
import jp.reflexworks.taggingservice.context.ReflexContextUtil;
import jp.reflexworks.taggingservice.env.TaggingEnvConst;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * エラーページユーティリティ.
 */
public class ErrorPageUtil implements ReflexServletConst, AtomConst {

	public static final String COOKIE_ERROR_STATUS = "ERROR_STATUS";
	public static final String COOKIE_ERROR_MESSAGE = "ERROR_MESSAGE";

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(ErrorPageUtil.class);

	/**
	 * エラーページへのリダイレクトをレスポンスする.
	 * @return リダイレクトを行う場合true。エラーページが存在しない場合false。
	 */
	public static boolean doErrorPageRedirect(ReflexRequest req, ReflexResponse resp,
			String errorpageSelfid, int responseCode, String message)
	throws IOException {
		// リダイレクトを行うのはGETリクエストの場合のみ
		if (!Constants.GET.equalsIgnoreCase(req.getMethod())) {
			return false;
		}

		// Cookieにステータスとメッセージをセットする。
		int maxAge = TaggingEnvUtil.getSystemPropInt(TaggingEnvConst.ERRORCOOKIE_MAXAGE,
				TaggingEnvConst.ERRORCOOKIE_MAXAGE_DEFAULT);
		CookieUtil.setCookie(resp, COOKIE_ERROR_STATUS, String.valueOf(responseCode),
				maxAge);
		if (!StringUtils.isBlank(message)) {
			CookieUtil.setCookie(resp, COOKIE_ERROR_MESSAGE, urlEncode(message), maxAge);
		}

		// リダイレクトURL
		// "/d/_html"は省略した形でURLを組み立てる。
		StringBuilder url = new StringBuilder();
		// コンテキストパスまで
		url.append(UrlUtil.getFromSchemaToContextPath(req));
		// PathInfo
		String errorpageUri = getErrorPageUri(errorpageSelfid);

		// エラーページが存在するか、ユーザが参照できるかどうかチェック
		boolean exists = false;
		ReflexContext reflexContext = ReflexContextUtil.getReflexContext(req);
		try {
			EntryBase errorEntry = reflexContext.getEntry(errorpageUri, true);	// use cache
			if (errorEntry != null && errorEntry.content != null &&
					(!StringUtils.isBlank(errorEntry.content._$src) ||
							!StringUtils.isBlank(errorEntry.content._$$text))) {
				exists = true;
			}
		} catch (Exception e) {
			if (logger.isInfoEnabled()) {
				logger.info(LogUtil.getRequestInfoStr(req.getRequestInfo()) +
						"[ErrorPage] error occurd.", e);
			}
		}
		if (!exists) {
			return false;
		}

		if (isIntactHtmlUrl()) {
			// サーブレットパス
			url.append(RequestParam.SERVLET_PATH);
			// PathInfo
			url.append(errorpageUri);
		} else {
			// PathInfoの先頭が /_html の場合除去する。
			// この場合サーブレットパスは付加しない。
			String cutHtmlUri = TaggingEntryUtil.cutHtmlUri(errorpageUri);
			url.append(cutHtmlUri);
		}

		resp.sendRedirect(url.toString());
		return true;
	}

	/**
	 * エラー時にエラーページを返す設定を取得.
	 * _errorpage.{適用順(数字)}.{エラーページselfid}={PathInfoの正規表現}
	 * @param serviceName サービス名
	 * @return エラーページ設定Map (キー: 対象URIの正規表現、値: エラーページselfid)
	 */
	public static Map<Pattern, String> getErrorPagePatterns(String serviceName)
	throws InvalidServiceSettingException {
		if (!StringUtils.isBlank(serviceName)) {
			final String DELIMITER = ".";
			final int MAPIDX_SELFID = 0;
			final int MAPIDX_PATTERN = 1;
			ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
			Map<String, String> errorpageMap = env.getPropMap(serviceName,
					SettingConst.ERRORPAGE_PREFIX);
			if (errorpageMap != null && errorpageMap.size() > 0) {
				Map<Integer, String[]> tmpTreeMap = new TreeMap<Integer, String[]>();
				for (Map.Entry<String, String> mapEntry : errorpageMap.entrySet()) {
					String order = null;
					String selfid = null;
					String key = mapEntry.getKey();
					int idx1 = key.indexOf(DELIMITER);
					int idx2 = key.indexOf(DELIMITER, idx1 + 1);
					if (idx1 > 0 && idx2 > idx1) {
						order = key.substring(idx1 + 1, idx2);
						selfid = key.substring(idx2 + 1);
					}

					if (!StringUtils.isBlank(order) && !StringUtils.isBlank(selfid)) {
						if (StringUtils.isInteger(order)) {
							tmpTreeMap.put(Integer.parseInt(order),
									new String[]{selfid, mapEntry.getValue()});
						}
					}
				}
				// 適用順で取得
				Map<Pattern, String> patternMap = new LinkedHashMap<Pattern, String>();
				for (Map.Entry<Integer, String[]> mapEntry : tmpTreeMap.entrySet()) {
					String[] parts = mapEntry.getValue();
					if (parts[MAPIDX_PATTERN] != null) {
						try {
							Pattern pattern = Pattern.compile(parts[MAPIDX_PATTERN]);
							patternMap.put(pattern, parts[MAPIDX_SELFID]);
						} catch (PatternSyntaxException e) {
							StringBuilder sb = new StringBuilder();
							sb.append("'");
							sb.append(serviceName);
							sb.append("' ");
							sb.append("PatternSyntaxException : ");
							sb.append(e.getMessage());
							sb.append(", ");
							sb.append(SettingConst.ERRORPAGE_PREFIX);
							sb.append(String.valueOf(mapEntry.getKey()));
							sb.append(".");
							sb.append(parts[MAPIDX_SELFID]);
							sb.append("=");
							sb.append(parts[MAPIDX_PATTERN]);
							logger.warn(sb.toString());
						}
					}
				}
				if (patternMap.size() > 0) {
					return patternMap;
				}
			}
		}
		return null;
	}

	/**
	 * URLエンコードを行う.
	 * Cookieの値に空白文字を指定できないため
	 * @param msg メッセージ
	 * @return URLエンコードしたメッセージ
	 */
	private static String urlEncode(String msg) {
		return UrlUtil.urlEncode(msg);
	}

	/**
	 * エラーページURIを取得
	 * @param selfid selfid
	 * @return エラーページURI
	 */
	private static String getErrorPageUri(String selfid) {
		if (StringUtils.isBlank(selfid)) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		if (selfid.startsWith("/")) {
			// キー指定
			sb.append(selfid);
		} else {
			// /_html配下の相対指定
			sb.append(AtomConst.URI_HTML);
			sb.append("/");
			sb.append(selfid);
		}
		return sb.toString();
	}

	/**
	 * HTML格納フォルダ(/d/_html)を省略せず元のままとするかどうか、を取得.
	 * @return HTML格納フォルダ(/d/_html)を省略せず元のままとする場合true
	 */
	private static boolean isIntactHtmlUrl() {
		return TaggingEnvUtil.getSystemPropBoolean(TaggingEnvConst.IS_INTACT_HTMLURL, false);
	}

}
