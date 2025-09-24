package jp.reflexworks.taggingservice.util;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.ConnectionException;

/**
 * リトライ処理ユーティリティ
 */
public class RetryUtil {

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(RetryUtil.class);

	/**
	 * スリープ処理.
	 * @param millisec スリープ時間(ミリ秒)
	 */
	public static void sleep(long millisec) {
		try {
			Thread.sleep(millisec);
		} catch (InterruptedException e) {
			if (logger.isInfoEnabled()) {
				logger.info("sleep InterruptedException", e);
			}
		}
	}

	/**
	 * リトライチェック.
	 * @param e 例外
	 * @param r 現在のリトライ回数
	 * @param numRetries 総リトライ回数
	 * @param waitMillis リトライ時のスリープ時間(ミリ秒)
	 * @throws IOException 例外
	 */
	public static void checkRetry(IOException e, int r, int numRetries, int waitMillis,
			RequestInfo requestInfo)
	throws IOException {
		// コネクションエラーの場合はリトライを行う
		if (!isRetryError(e)) {
			throw e;
		}
		if (r >= numRetries) {
			throw e;
		}
		if (logger.isDebugEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + getRetryLog(e, r));
		}
		sleep(waitMillis);
	}

	/**
	 * リトライエラーの場合trueを返す.
	 * @param e IOException
	 * @return リトライエラーの場合true
	 */
	public static boolean isRetryError(IOException e) {
		String msg = e.getMessage();
		if (e instanceof ConnectionException) {	// TaggingService コネクションエラー
			return true;
		} else if (e instanceof java.net.UnknownHostException) {
			return true;
		} else if (e instanceof java.net.ConnectException) {
			return true;
		} else if (e instanceof java.net.SocketTimeoutException) {
			return true;
		} else if (e instanceof java.net.NoRouteToHostException) {	// リクエスト負荷がかかったときに発生事例あり
			return true;
		} else if (e instanceof javax.net.ssl.SSLException) {
			if (msg != null && (
					msg.indexOf("Received close_notify during handshake") >= 0 ||
					msg.indexOf("handshake alert:  user_canceled") >= 0 ||
					msg.indexOf("Remote host closed connection during handshake") >= 0
					)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * リトライエラーの場合trueを返す.
	 * @param e IOException
	 * @param method Method
	 * @return リトライエラーの場合true
	 */
	public static boolean isRetryError(IOException e, String method) {
		if (isRetryError(e)) {
			return true;
		}

		if (Constants.GET.equalsIgnoreCase(method)) {
			// 検索の場合のみ、SocketExceptionをリトライ対象とする。
			if (e instanceof java.net.SocketException) {
				return true;
			}
		}

		return false;
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

}
