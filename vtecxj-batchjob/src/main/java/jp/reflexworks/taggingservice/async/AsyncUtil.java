package jp.reflexworks.taggingservice.async;

import java.util.Date;

import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.reflexworks.taggingservice.bdbclient.BDBClientConst;

/**
 * バッチジョブサーバでの非同期処理ユーティリティ
 */
public class AsyncUtil {

	/**
	 * 開始ログエントリー登録
	 * @param req リクエスト
	 * @param reflexContext ReflexContext
	 */
	public static void logStart(ReflexRequest req, ReflexContext reflexContext) {
		String msg = getStartLog(req);
		log(msg, reflexContext);
	}

	/**
	 * 終了ログエントリー登録
	 * @param req リクエスト
	 * @param startTime 開始時間
	 * @param reflexContext ReflexContext
	 */
	public static void logEnd(ReflexRequest req, long startTime, ReflexContext reflexContext) {
		String msg = getEndLog(req, startTime);
		log(msg, reflexContext);
	}

	/**
	 * エラーログエントリー登録
	 * @param e 例外
	 * @param reflexContext ReflexContext
	 */
	public static void logError(Throwable e, ReflexContext reflexContext) {
		StringBuilder sb = new StringBuilder();
		sb.append(e.getClass().getName());
		sb.append(" ");
		sb.append(e.getMessage());
		log(sb.toString(), AsyncConst.LOG_SUBTITLE_WARN, reflexContext);
	}

	/**
	 * ログ登録
	 * @param msg メッセージ
	 * @param reflexContext ReflexContext
	 */
	public static void log(String msg, ReflexContext reflexContext) {
		log(msg, AsyncConst.LOG_SUBTITLE, reflexContext);
	}

	/**
	 * ログ登録
	 * @param msg メッセージ
	 * @param subtitle subtitle
	 * @param reflexContext ReflexContext
	 */
	public static void log(String msg, String subtitle, ReflexContext reflexContext) {
		reflexContext.log(AsyncConst.LOG_TITLE, subtitle, msg);
	}

	/**
	 * 開始ログ文字列を取得.
	 * @param req リクエスト
	 * @return 開始ログ文字列
	 */
	private static String getStartLog(ReflexRequest req) {
		String func = getFunctionName(req);
		StringBuilder msg = new StringBuilder();
		msg.append("Asynchronous processing in batchjob server started. ");
		msg.append(func);
		return msg.toString();
	}

	/**
	 * 終了ログ文字列を取得
	 * @param req リクエスト
	 * @param startTime 開始時間 (Date.getTime)
	 * @return 終了ログ文字列
	 */
	private static String getEndLog(ReflexRequest req, long startTime) {
		String func = getFunctionName(req);
		StringBuilder msg = new StringBuilder();
		msg.append("Asynchronous processing in batchjob server end. ");
		msg.append(func);
		msg.append(getElapsedTimeLog(startTime));
		return msg.toString();
	}

	/**
	 * URLからサーバサイドJS名を取得.
	 * @param req リクエスト
	 * @return サーバサイドJS名、または移行処理の場合QueryString
	 */
	private static String getFunctionName(ReflexRequest req) {
		// サーブレットパスにより処理分岐
		String servletPath = req.getServletPath();
		String servletPathMigrate = getServletPathMigrate();
		if (servletPathMigrate.equals(servletPath)) {
			// データ移行
			return req.getQueryString();
		} else {
			// サーバサイドJS
			String pathInfo = req.getPathInfo();
			if (pathInfo != null && pathInfo.length() > 1) {
				return pathInfo.substring(1);
			} else {
				return "";
			}
		}
	}

	/**
	 * データ移行処理のサーブレットパスを取得.
	 * @return データ移行処理のサーブレットパス
	 */
	private static String getServletPathMigrate() {
		String urlMigrate = TaggingEntryUtil.removeLastSlash(
				TaggingEnvUtil.getSystemProp(BDBClientConst.URL_MIGRATE, null));
		int idx = urlMigrate.lastIndexOf("/");
		return urlMigrate.substring(idx);
	}

	/**
	 * 経過時間ログ表記を取得
	 * @param startTime 開始時間
	 * @return 経過時間ログ表記
	 */
	private static String getElapsedTimeLog(long startTime) {
		long finishTime = new Date().getTime();
		long time = finishTime - startTime;
		StringBuilder sb = new StringBuilder();
		sb.append(" - ");
		sb.append(time);
		sb.append("ms");
		return sb.toString();
	}

}
