package jp.reflexworks.taggingservice.util;

import java.io.EOFException;
import java.io.IOException;
import java.util.ConcurrentModificationException;
import java.util.regex.PatternSyntaxException;
import java.util.zip.DataFormatException;

import javax.script.ScriptException;

import org.msgpack.MessageTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.servlet.HttpStatus;
import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.ResponseInfo;
import jp.reflexworks.taggingservice.exception.AuthLockedException;
import jp.reflexworks.taggingservice.exception.AuthTimeoutException;
import jp.reflexworks.taggingservice.exception.AuthenticationException;
import jp.reflexworks.taggingservice.exception.EntryDuplicatedException;
import jp.reflexworks.taggingservice.exception.HierarchyFormatException;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.reflexworks.taggingservice.exception.MethodNotAllowedException;
import jp.reflexworks.taggingservice.exception.NoEntryException;
import jp.reflexworks.taggingservice.exception.NoExistingEntryException;
import jp.reflexworks.taggingservice.exception.NotInServiceException;
import jp.reflexworks.taggingservice.exception.OptimisticLockingException;
import jp.reflexworks.taggingservice.exception.OutOfRangeException;
import jp.reflexworks.taggingservice.exception.PayloadTooLargeException;
import jp.reflexworks.taggingservice.exception.PaymentRequiredException;
import jp.reflexworks.taggingservice.exception.PermissionException;
import jp.reflexworks.taggingservice.exception.RequestSecurityException;
import jp.reflexworks.taggingservice.exception.ShutdownException;
import jp.reflexworks.taggingservice.exception.SignatureInvalidException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.exception.TooManyEntriesException;
import jp.reflexworks.taggingservice.util.Constants.LogLevel;

/**
 * 例外判定ユーティリティ.
 */
public class ReflexExceptionUtil implements ReflexServletConst {

	/** エラーメッセージにキーを付ける場合 */
	public static final String ERROR_ENTRY = " Key : ";
	/** 致命的なエラー */
	public static final String ERROR = LogLevel.ERROR.name();

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(ReflexExceptionUtil.class);

	/**
	 * 例外に対応するステータスを取得.
	 * @param e 例外
	 * @param method メソッド
	 * @param isLog
	 * @param serviceName サービス名
	 * @return レスポンス情報
	 */
	public static ResponseInfo<FeedBase> getStatus(Throwable e,
			String method, boolean isLog, String serviceName, RequestInfo requestInfo) {
		ResponseInfo<FeedBase> responseInfo = null;
		if (e instanceof TaggingException) {
			responseInfo = judgeException(e, method, serviceName);
			TaggingException te = (TaggingException)e;
			String message = responseInfo.data.title;
			StringBuilder sb = new StringBuilder();
			if (message != null) {
				sb.append(message);
			}

			String uri = te.getUri();
			if (uri != null && uri.length() > 0) {
				sb.append(ERROR_ENTRY);
				sb.append(uri);
			}
			responseInfo.data.title = sb.toString();

			if (te.getNum() > 0) {
				responseInfo.data.title_$type = String.valueOf(te.getNum());
			}

		} else {
			responseInfo = judgeException(e, method, serviceName);
		}

		if (ERROR.equals(responseInfo.data.rights) && isLog) {	// 致命的エラー
			logger.error(LogUtil.getRequestInfoStr(requestInfo) + e.getMessage(), e);
		}

		return responseInfo;
	}

	/**
	 * 例外判定.
	 * @param e 例外
	 * @param method メソッド
	 * @param serviceName サービス名
	 * @return レスポンス情報
	 */
	public static ResponseInfo<FeedBase> judgeException(Throwable e,
			String method, String serviceName) {
		int statusCode = HttpStatus.SC_OK;
		String message = null;
		String errorStr = null;

		if (e instanceof IllegalParameterException) {
			statusCode = HttpStatus.SC_BAD_REQUEST;
			message = e.getMessage();

		} else if (e instanceof PatternSyntaxException) {
			// 正規表現エラー
			statusCode = HttpStatus.SC_BAD_REQUEST;
			message = e.getMessage();

		} else if (e instanceof AuthTimeoutException) {
			// 認証タイムアウトエラー
			//statusCode = HttpStatus.SC_REQUEST_TIMEOUT;	// クライアントに結果が返らない
			statusCode = HttpStatus.SC_NOT_ACCEPTABLE;
			message = e.getMessage();

		} else if (e instanceof AuthLockedException) {
			// 二重ログインエラー
			statusCode = HttpStatus.SC_LOCKED;
			message = e.getMessage();

		} else if (e instanceof AuthenticationException) {
			// 認証エラー
			statusCode = HttpStatus.SC_UNAUTHORIZED;
			message = e.getMessage();

		} else if (e instanceof NotInServiceException) {
			// サービス停止または未登録エラー
			statusCode = HttpStatus.SC_FAILED_DEPENDENCY;
			message = e.getMessage();

		} else if (e instanceof PermissionException) {
			// 権限エラー
			statusCode = HttpStatus.SC_FORBIDDEN;
			message = e.getMessage();

		} else if (e instanceof HierarchyFormatException) {
			// 階層エラー
			statusCode = HttpStatus.SC_BAD_REQUEST;
			message = e.getMessage();

		} else if (e instanceof TooManyEntriesException) {
			// Limitオーバー
			statusCode = HttpStatus.SC_NOT_ACCEPTABLE;
			message = e.getMessage();

		/* TODO xml
		} else if (e instanceof BaseException) {
			// XMLフォーマットエラー
			statusCode = HttpStatus.SC_BAD_REQUEST;
			message = "Request format is invalid. " + e.getMessage();
			*/

		} else if (e instanceof DataFormatException) {
			statusCode = HttpStatus.SC_BAD_REQUEST;
			message = e.getMessage();

		} else if (e instanceof MessageTypeException) {
			// MessagePackフォーマットエラー
			statusCode = HttpStatus.SC_BAD_REQUEST;
			message = e.getMessage();

		/*
		} else if (e instanceof ConversionException) {
			statusCode = HttpStatus.SC_BAD_REQUEST;
			message = "Request format is invalid. " + e.getMessage();

		} else if (e instanceof StreamException) {
			statusCode = HttpStatus.SC_BAD_REQUEST;
			message = "Request format is invalid. " + e.getMessage();

		} else if (e instanceof CannotResolveClassException) {
			statusCode = HttpStatus.SC_BAD_REQUEST;
			message = "Request format is invalid. " + e.getMessage();

		} else if (e instanceof ObjectAccessException) {
			statusCode = HttpStatus.SC_BAD_REQUEST;
			message = "Request format is invalid. " + e.getMessage();
		*/

		} else if (e instanceof EntryDuplicatedException) {
			// すでに登録されている場合
			statusCode = HttpStatus.SC_CONFLICT;
			message = e.getMessage();

		} else if (e instanceof NoEntryException) {
			// 検索等でデータなし
			statusCode = HttpStatus.SC_NO_CONTENT;
			message = "No entry.";

		} else if (e instanceof NoExistingEntryException) {
			// 更新等でデータなし
			statusCode = HttpStatus.SC_NOT_FOUND;
			String emsg = e.getMessage();
			if (emsg != null && emsg.length() > 0) {
				message = emsg;
			} else {
				message = "No entry.";
			}

		} else if (e instanceof ConcurrentModificationException) {
			// 楽観的ロック失敗
			statusCode = HttpStatus.SC_CONFLICT;
			message = e.getMessage();

		} else if (e instanceof OptimisticLockingException) {
			// 楽観的ロック失敗
			statusCode = HttpStatus.SC_CONFLICT;
			message = e.getMessage();

		} else if (e instanceof OutOfRangeException) {
			// 採番枠を超えたエラー
			statusCode = HttpStatus.SC_BAD_REQUEST;
			message = e.getMessage();

		} else if (e instanceof RequestSecurityException) {
			statusCode = HttpStatus.SC_EXPECTATION_FAILED;
			// メッセージなし

		} else if (e instanceof SignatureInvalidException) {
			// 署名認証エラー
			statusCode = HttpStatus.SC_PRECONDITION_FAILED;
			message = e.getMessage();

		} else if (e instanceof PayloadTooLargeException) {
			// リクエストデータのサイズ超過
			statusCode = HttpStatus.SC_PAYLOAD_TOO_LARGE;
			message = e.getMessage();

		} else if (e instanceof ScriptException) {
			// サーバサイドJSエラー
			statusCode = HttpStatus.SC_FAILED_DEPENDENCY;
			message = e.getMessage();
			errorStr = ERROR;	// ログは出力する

		} else if (e instanceof InvalidServiceSettingException) {
			// サービス設定エラー
			statusCode = HttpStatus.SC_UPGRADE_REQUIRED;
			message = e.getMessage();

		} else if (e instanceof MethodNotAllowedException) {
			// メソッドを許可しないエラー
			statusCode = HttpStatus.SC_METHOD_NOT_ALLOWED;
			message = e.getMessage();

		} else if (e instanceof PaymentRequiredException) {
			// サービス使用の制限オーバー
			statusCode = HttpStatus.SC_PAYMENT_REQUIRED;
			message = e.getMessage();

		} else if (e instanceof EOFException) {
			// リクエストpayloadの予想外の終了
			statusCode = HttpStatus.SC_BAD_REQUEST;
			message = e.getMessage();

		} else if (e instanceof IOException) {

			message = e.getMessage();
			if (message != null &&
					(message.startsWith("Close org.eclipse.jetty.server.HttpConnection$SendCallback") ||
					(message.startsWith("Close SendCallback") && message.indexOf("org.eclipse.jetty.server.HttpChannel$SendCallback") > -1) ||
					(message.indexOf("java.util.concurrent.TimeoutException") > -1 &&
							message.indexOf("Idle timeout expired") > -1) ||
					message.equals("FilterChain is null.")
					)) {
				// メッセージが以下の場合、クライアントからの切断
				// "Close org.eclipse.jetty.server.HttpConnection$SendCallback" で始まる場合
				// "java.util.concurrent.TimeoutException: Idle timeout expired: 30001/30000 ms" (時間は場合によって変わる)
				logger.warn("[judgeException] IOException: " + message, e);
				// ステータスはとりあえず400
				statusCode = HttpStatus.SC_BAD_REQUEST;
			} else {
				// 致命的エラー
				statusCode = HttpStatus.SC_INTERNAL_SERVER_ERROR;
				message = getStackTrace(e);
				errorStr = ERROR;
			}

		} else if (e instanceof NullPointerException) {
			// エラースタックトレースの先頭パッケージが"org.eclipse.jetty.servlet."の場合、Podの再起動など、致命的でないエラー。
			StackTraceElement[] stackTraceElements = e.getStackTrace();
			boolean isSevere = true;
			if (stackTraceElements != null && stackTraceElements.length > 0) {
				String stackTraceClsName = stackTraceElements[0].getClassName();
				if (stackTraceClsName.startsWith("org.eclipse.jetty.servlet.")) {
					isSevere = false;
					logger.warn("[judgeException] NullPointerException: " + message, e);
					// ステータスはとりあえず400
					statusCode = HttpStatus.SC_BAD_REQUEST;
					message = "NullPointerException by " + stackTraceClsName;
				}
			}
			if (isSevere) {
				// 致命的エラー
				statusCode = HttpStatus.SC_INTERNAL_SERVER_ERROR;
				message = getStackTrace(e);
				errorStr = ERROR;
			}

		} else if (e instanceof ShutdownException) {
			// Podの再起動など、致命的でないエラー。
			logger.warn("[judgeException] ShutdownException: " + message, e);
			// ステータスはとりあえず400
			statusCode = HttpStatus.SC_BAD_REQUEST;
			message = e.getMessage();

		} else {
			statusCode = HttpStatus.SC_INTERNAL_SERVER_ERROR;
			message = getStackTrace(e);
			errorStr = ERROR;
		}

		FeedBase status = TaggingEntryUtil.createFeed(serviceName);
		status.title = message;
		status.rights = errorStr;

		return new ResponseInfo<FeedBase>(statusCode, status);
	}

	/**
	 * 例外スタックトレースを1行の文字列に連結.
	 * @param e 例外
	 * @return 例外スタックトレースを1行に連結した文字列
	 */
	private static String getStackTrace(Throwable e) {
		StringBuilder sb = new StringBuilder();
		sb.append(e.getClass().getName());
		sb.append(" : ");
		sb.append(e.getMessage());
		return sb.toString();
	}

}
