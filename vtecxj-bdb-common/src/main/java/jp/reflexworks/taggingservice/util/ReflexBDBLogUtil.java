package jp.reflexworks.taggingservice.util;

import java.util.Enumeration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.servlet.util.UrlUtil;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.ReflexEnvConst;
import jp.reflexworks.taggingservice.env.ReflexEnvUtil;
import jp.reflexworks.taggingservice.util.Constants.LogLevel;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * ログ編集用ユーティリティ.
 */
public class ReflexBDBLogUtil extends LogUtil {

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(ReflexBDBLogUtil.class);

	/**
	 * アクセス開始ログを出力.
	 * @param req リクエスト
	 */
	public static void writeAccessStart(ReflexRequest req) {
		if (isEnableRequestLog() && logger.isDebugEnabled()) {
			String method = req.getMethod().toUpperCase();
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(req.getRequestInfo()));
			sb.append("Request START ");
			sb.append(UrlUtil.getLastForwarded(req));
			sb.append(" \"");
			sb.append(method);
			sb.append(" ");
			sb.append(UrlUtil.getRequestURLWithQueryString(req));
			sb.append("\" ");
			sb.append(req.getProtocol());
			sb.append(" ");

			// リクエストヘッダ出力
			sb.append(getRequestHeadersString(req));

			logger.debug(sb.toString());
		}
	}

	/**
	 * アクセス終了ログを出力.
	 * @param req リクエスト
	 * @param resp レスポンス
	 */
	public static void writeAccessEnd(ReflexRequest req, ReflexResponse resp) {
		if (isEnableRequestLog() && logger.isDebugEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(req.getRequestInfo()));
			sb.append("Request END ");
			sb.append(req.getRemoteAddr());
			sb.append(" \"");
			sb.append(req.getMethod().toUpperCase());
			sb.append(" ");
			sb.append(UrlUtil.getRequestURLWithQueryString(req));
			sb.append("\" ");
			sb.append(req.getProtocol());
			sb.append(" - ");
			sb.append(req.getElapsedTime());
			sb.append("ms");

			// ステータス
			sb.append(" [STATUS] ");
			sb.append(resp.getStatus());

			logger.debug(sb.toString());
		}
	}

	/**
	 * リクエストヘッダのログ出力内容を取得.
	 * @param req リクエスト
	 * @return リクエストヘッダのログ出力内容
	 */
	private static String getRequestHeadersString(ReflexRequest req) {
		StringBuilder sb = new StringBuilder();
		sb.append("[Headeres] ");
		Enumeration<String> enu = req.getHeaderNames();
		boolean isNameFirst = true;
		while (enu.hasMoreElements()) {
			if (isNameFirst) {
				isNameFirst = false;
			} else {
				sb.append(", ");
			}
			String name = enu.nextElement();
			sb.append(name);
			sb.append("=");

			int valNum = 0;
			StringBuilder tmp = new StringBuilder();
			Enumeration<String> values = req.getHeaders(name);
			while (values.hasMoreElements()) {
				valNum++;
				if (valNum > 1) {
					tmp.append(",");
				}
				String value = values.nextElement();
				tmp.append(value);
			}
			if (valNum > 1) {
				sb.append("[");
			}
			sb.append(tmp.toString());
			if (valNum > 1) {
				sb.append("]");
			}
		}
		return sb.toString();
	}

	/**
	 * ログファイル出力、およびログエントリー出力 (リクエストから実行)
	 * @param statusCode レスポンスステータスコード
	 * @param message ログメッセージ
	 * @param logLevel ログレベル
	 * @param e 例外
	 * @param requestInfo リクエスト情報
	 */
	public static void writeLogger(Integer statusCode, String message, LogLevel logLevel,
			Throwable e, RequestInfo requestInfo) {
		String task = null;

		// ログメッセージ取得
		String logStr = getLogMessage(requestInfo, task,
				statusCode, message, logLevel, e);

		// ログメッセージにサービス情報を付加
		String logFileStr = getServiceLogMessage(requestInfo.getServiceName()) + logStr;

		// ログファイル出力
		writeUtilLog(logLevel, logFileStr, e, requestInfo);
	}

	/**
	 * サービスログメッセージ.
	 * @param serviceName サービス名
	 * @return ログメッセージ
	 */
	private static String getServiceLogMessage(String serviceName) {
		StringBuilder sb = new StringBuilder();
		sb.append("[service]");
		sb.append(serviceName);
		sb.append(", ");
		return sb.toString();
	}

	/**
	 * ログメッセージを取得.
	 * @param requestInfo リクエスト情報
	 * @param task タスク
	 * @param statusCode レスポンスステータス
	 * @param message メッセージ
	 * @param logLevel ログレベル
	 * @param e 例外
	 * @return ログメッセージ
	 */
	private static String getLogMessage(RequestInfo requestInfo, String task,
			Integer statusCode, String message, LogLevel logLevel, Throwable e) {
		String ip = requestInfo.getIp();
		String method = requestInfo.getMethod();
		String url = requestInfo.getUrl();
		return getLogMessage(ip, method, url, task, statusCode, message, logLevel, e);
	}

	/**
	 * ログメッセージを取得
	 * @param ip IPアドレス
	 * @param uid UID
	 * @param account アカウント
	 * @param method メソッド
	 * @param url URL
	 * @param task タスク
	 * @param statusCode レスポンスステータス
	 * @param message メッセージ
	 * @param logLevel ログレベル
	 * @param e 例外 (サブメッセージを出力)
	 * @return ログメッセージ
	 */
	private static String getLogMessage(String ip, String method, String url, String task,
			Integer statusCode, String message, LogLevel logLevel, Throwable e) {
		StringBuilder sb = new StringBuilder();
		sb.append("[ip]");
		sb.append(ip);
		sb.append(", ");
		sb.append("[request]");
		sb.append(method);
		sb.append(" ");
		sb.append(url);
		if (!StringUtils.isBlank(task)) {
			sb.append(", ");
			sb.append("[task]");
			sb.append(task);
		}

		if (statusCode != null) {
			sb.append(", ");
			sb.append("[status]");
			sb.append(statusCode);
		}
		if (message != null) {
			sb.append(", ");
			sb.append(message);
		}
		return sb.toString();
	}

	/**
	 * ログファイルにメッセージ出力
	 * @param logLevel ログレベル
	 * @param logFileStr ログメッセージ
	 * @param e 例外
	 * @param requestInfo リクエスト情報
	 */
	private static void writeUtilLog(LogLevel logLevel, String logFileStr, Throwable e,
			RequestInfo requestInfo) {
		if (LogLevel.DEBUG.equals(logLevel)) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + logFileStr);
		} else if (LogLevel.INFO.equals(logLevel)) {
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + logFileStr);
		} else if (LogLevel.WARN.equals(logLevel)) {
			logger.warn(LogUtil.getRequestInfoStr(requestInfo) + logFileStr, e);
		} else if (LogLevel.ERROR.equals(logLevel)) {
			logger.error(LogUtil.getRequestInfoStr(requestInfo) + logFileStr, e);
		}
	}

	/**
	 * リトライログメッセージを取得
	 * @param e 例外
	 * @param r リトライ回数
	 * @return リトライログメッセージ
	 */
	public static String getRetryLog(Throwable e, int r) {
		StringBuilder sb = new StringBuilder();
		sb.append(e.getClass().getSimpleName());
		sb.append(": ");
		sb.append(e.getMessage());
		sb.append(" retry (");
		sb.append(r);
		sb.append(") ...");
		return sb.toString();
	}

	/**
	 * BDBサーバへのリクエストログを出力するかどうか.
	 * @return BDBサーバへのリクエストログを出力する場合true
	 */
	public static boolean isEnableRequestLog() {
		return ReflexEnvUtil.getSystemPropBoolean(
				ReflexEnvConst.BDB_ENABLE_REQUESTLOG, false);
	}

}
