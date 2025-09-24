package jp.reflexworks.taggingservice.util;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.ReflexEnvUtil;

/**
 * ログ編集用ユーティリティ.
 */
public class LogUtil {

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(LogUtil.class);

	/**
	 * ログ用リクエスト情報表記を取得.
	 * @param requestInfo リクエスト情報
	 * @return ログ用リクエスト情報文字列
	 */
	public static String getRequestInfoStr(RequestInfo requestInfo) {
		if (requestInfo != null) {
			StringBuilder sb = new StringBuilder();
			sb.append("(");
			sb.append(requestInfo.getServiceName());
			sb.append(") ");
			return sb.toString();
		}
		return "";
	}

	/**
	 * 経過時間ログ表記を取得
	 * @param startTime 開始時間
	 * @return 経過時間ログ表記
	 */
	public static String getElapsedTimeLog(long startTime) {
		long finishTime = new Date().getTime();
		long time = finishTime - startTime;
		StringBuilder sb = new StringBuilder();
		sb.append(" - ");
		sb.append(time);
		sb.append("ms");
		return sb.toString();
	}

	/**
	 * ログメッセージ最大文字数を取得.
	 * @return ログメッセージ最大文字数
	 */
	public static int getLogMessageWordcountLimit() {
		return ReflexEnvUtil.getSystemPropInt(LogConst.LOG_MESSAGE_WORDCOUNT_LIMIT,
				LogConst.LOG_MESSAGE_WORDCOUNT_LIMIT_DEFAULT);
	}

}
