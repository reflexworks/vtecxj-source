package jp.reflexworks.taggingservice.plugin.def;

import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.servlet.HttpStatus;
import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.servlet.util.UrlUtil;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.ResponseInfo;
import jp.reflexworks.taggingservice.api.SettingConst;
import jp.reflexworks.taggingservice.blogic.EMailBlogic;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.SubMessage;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.LogManager;
import jp.reflexworks.taggingservice.provider.ProviderUtil;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.Constants.LogLevel;
import jp.reflexworks.taggingservice.util.CookieUtil;
import jp.reflexworks.taggingservice.util.ErrorPageUtil;
import jp.reflexworks.taggingservice.util.ExceptionUtil;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.MetadataUtil;
import jp.reflexworks.taggingservice.util.ReflexExceptionUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.reflexworks.taggingservice.util.TaggingLogUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * ログ管理クラス.
 */
public class LogManagerDefault implements LogManager {

	/** メールアドレス区切り文字 */
	public static final String LOGALERT_ADDRESS_DELIMITER = ",";
	/** ログエントリーのupdated。yyyy-MM-dd HH:mm:ss形式。 */
	public static final String LOGALERT_REPLACE_UPDATED = "${UPDATED}";
	/** ログエントリーのtitle */
	public static final String LOGALERT_REPLACE_COMPONENT = "${COMPONENT}";
	/** ログエントリーのsubtitle */
	public static final String LOGALERT_REPLACE_LEVEL = "${LEVEL}";
	/** ログエントリーのsummary */
	public static final String LOGALERT_REPLACE_MESSAGE = "${MESSAGE}";
	/** ログメッセージの文字数オーバー時区切り文字 */
	public static final String CUTLOGMESSAGE_CHAR = ",";

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 初期処理.
	 */
	public void init() {
		// Do nothing.
	}

	/**
	 * シャットダウン処理.
	 */
	public void close() {
		// Do nothing.
	}

	/**
	 * ログファイル出力、およびログエントリー出力 (リクエストから実行)
	 * @param statusCode レスポンスステータスコード
	 * @param message ログメッセージ
	 * @param logLevel ログレベル
	 * @param e 例外
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	@Override
	public void writeLogger(Integer statusCode, String message, LogLevel logLevel,
			Throwable e, String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo) {
		String task = null;

		// ログメッセージ取得
		String logStr = getLogMessage(requestInfo, task,
				statusCode, message, logLevel, e);

		// ログメッセージにサービス情報を付加
		String logFileStr = getServiceLogMessage(requestInfo.getServiceName()) + logStr;

		// ログファイル出力
		writeUtilLog(logLevel, logFileStr, e, requestInfo);

		// ログエントリー出力
		if (logLevel != null && (LogLevel.WARN.equals(logLevel) ||
				LogLevel.ERROR.equals(logLevel))) {
			String title = e.getClass().getName();
			String subtitle = logLevel.name();
			String summary = getErrorMessage(e);
			writeLogEntry(title, subtitle, summary, serviceName, requestInfo, connectionInfo);
		}
	}

	/**
	 * ログエントリーを出力.
	 * @param title タイトル
	 * @param subtitle サブタイトル
	 * @param message メッセージ
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	@Override
	public void writeLogEntry(String title, String subtitle, String message,
			String serviceName, RequestInfo requestInfo, ConnectionInfo connectionInfo) {
		// ログエントリー
		EntryBase entry = TaggingEntryUtil.createEntry(serviceName);
		entry.title = title;
		entry.subtitle = subtitle;
		entry.summary = message;
		String requestInfoStr = getLogMessage(requestInfo, null, null, null, null, null);
		entry.rights = requestInfoStr;

		try {
			SystemContext systemContext = new SystemContext(serviceName,
					requestInfo, connectionInfo);
			// 採番
			String num = getLogNum(systemContext);
			if (StringUtils.isBlank(num)) {
				logger.warn(getRequestInfoStr(requestInfo) + "logger num is null.");
				return;
			}

			String logEntryUri = getLogEntryUri(num);
			entry.setMyUri(logEntryUri);

			// ログエントリー登録
			EntryBase logEntry = systemContext.post(entry);
			if (logEntry == null) {
				logger.warn(getRequestInfoStr(requestInfo) + "LogEntry was not post. " + logEntryUri);
				logEntry = entry;
				String currentTime = MetadataUtil.getCurrentTime();
				logEntry.published = currentTime;
				logEntry.updated = currentTime;
			}

			// ログエントリーがアラート条件に合致した場合、通知を行う。
			Pattern logAlertPattern = TaggingEnvUtil.getPropPattern(serviceName,
					SettingConst.LOGALERT_LEVEL_PATTERN);
			if (logAlertPattern != null && !StringUtils.isBlank(logEntry.subtitle)) {
				Matcher matcher = logAlertPattern.matcher(logEntry.subtitle);
				if (matcher.matches()) {
					alert(logEntry, systemContext);
				}
			}

		} catch (IOException e) {
			logger.warn(getRequestInfoStr(requestInfo) + "[writeLogEntry] Error occurred. ", e);
		} catch (TaggingException e) {
			logger.warn(getRequestInfoStr(requestInfo) + "[writeLogEntry] Error occurred. ", e);
		}

	}

	/**
	 * ログファイルにメッセージ出力
	 * @param logLevel ログレベル
	 * @param logFileStr ログメッセージ
	 * @param e 例外
	 * @param requestInfo リクエスト情報
	 */
	private void writeUtilLog(LogLevel logLevel, String logFileStr, Throwable e,
			RequestInfo requestInfo) {
		String msg = getRequestInfoStr(requestInfo) + logFileStr;
		if (LogLevel.DEBUG.equals(logLevel)) {
			logger.debug(msg);
		} else if (LogLevel.INFO.equals(logLevel)) {
			logger.info(msg);
		} else if (LogLevel.WARN.equals(logLevel)) {
			logger.warn(msg, e);
		} else if (LogLevel.ERROR.equals(logLevel)) {
			logger.error(msg, e);
		}
	}

	/**
	 * ログ番号を取得.
	 * @param systemContext ReflexContext
	 * @return ログ番号
	 */
	private String getLogNum(SystemContext systemContext)
	throws IOException, TaggingException {
		String uri = getLogFolderUri();
		return TaggingLogUtil.getLogNum(systemContext, uri);
	}

	/**
	 * ログエントリーの親階層を取得.
	 * @return ログエントリーの親階層
	 */
	private String getLogFolderUri() {
		return Constants.URI_LOG;
	}

	/**
	 * ログエントリーのURIを取得.
	 * @param num 番号
	 * @return ログエントリーのURI
	 */
	private String getLogEntryUri(String num) {
		StringBuilder sb = new StringBuilder();
		sb.append(getLogFolderUri());
		sb.append("/");
		sb.append(num);
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
	private String getLogMessage(RequestInfo requestInfo, String task,
			Integer statusCode, String message, LogLevel logLevel, Throwable e) {
		String ip = requestInfo.getIp();
		String uid = requestInfo.getUid();
		String account = requestInfo.getAccount();
		String method = requestInfo.getMethod();
		String url = requestInfo.getUrl();
		return getLogMessage(ip, uid, account, method, url, task, statusCode,
				message, logLevel, e);
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
	private String getLogMessage(String ip, String uid, String account,
			String method, String url, String task,
			Integer statusCode, String message, LogLevel logLevel, Throwable e) {
		StringBuilder sb = new StringBuilder();
		sb.append("[ip]");
		sb.append(ip);
		sb.append(", ");
		sb.append("[uid]");
		sb.append(StringUtils.null2blank(uid));
		sb.append(", ");
		sb.append("[account]");
		sb.append(StringUtils.null2blank(account));
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
		if (e != null && e instanceof SubMessage) {
			String subMessage = ((SubMessage)e).getSubMessage();
			if (!StringUtils.isBlank(subMessage)) {
				sb.append(", ");
				sb.append("[subMessage]");
				sb.append(subMessage);
			}
		}
		return sb.toString();
	}

	/**
	 * サービスログメッセージ.
	 * @param serviceName サービス名
	 * @return ログメッセージ
	 */
	private String getServiceLogMessage(String serviceName) {
		StringBuilder sb = new StringBuilder();
		sb.append("[service]");
		sb.append(serviceName);
		sb.append(", ");
		return sb.toString();
	}

	/**
	 * エラー時のログエントリーに出力するメッセージを取得する.
	 * causeがある場合、エラークラスとメッセージを取得する。
	 * @param e 例外
	 * @return メッセージ
	 */
	public String getErrorMessage(Throwable e) {
		if (e == null) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		sb.append(e.getMessage());
		Throwable cause = e.getCause();
		if (cause != null) {
			sb.append(" - Caused by: ");
			sb.append(cause.getClass().getName());
			sb.append(": ");
			sb.append(cause.getMessage());

			// さらにcauseがあれば出力 (ここまで)
			Throwable cause2 = cause.getCause();
			if (cause2 != null) {
				sb.append(" - Caused by: ");
				sb.append(cause2.getClass().getName());
				sb.append(": ");
				sb.append(cause2.getMessage());
			}
		}
		return sb.toString();
	}

	/**
	 * アクセス開始ログを出力.
	 * リクエストデータが大きい場合時間がかかるので非同期とする。
	 * @param req リクエスト
	 * @return Future
	 */
	public Future<Boolean> writeAccessStart(ReflexRequest req)
	throws IOException, TaggingException {
		RequestInfo requestInfo = req.getRequestInfo();
		String serviceName = req.getServiceName();
		String mainThreadName = Thread.currentThread().getName();
		String method = req.getMethod().toUpperCase();
		String lastForwarded = UrlUtil.getLastForwarded(req);
		String requestUrl = UrlUtil.getRequestURLWithQueryString(req);
		String protocol = req.getProtocol();
		String requestHeadersString = getRequestHeadersString(req);
		FeedBase feed = null;
		String targetService = getTargetService(req);
		try {
			feed = req.getFeed(targetService);
		} catch (Exception e) {
			ResponseInfo<FeedBase> responseInfo = ReflexExceptionUtil.getStatus(
					e, method, false, serviceName, requestInfo);
			if (ExceptionUtil.getLevelError().equals(responseInfo.data.rights)) {
				StringBuilder emsg = new StringBuilder();
				emsg.append(getRequestInfoStr(requestInfo));
				emsg.append("[writeAccessStart] ");
				emsg.append(e.getClass().getName());
				emsg.append(" ");
				emsg.append(e.getMessage());
				String msg = emsg.toString();
				if (msg.indexOf("EOF") > -1 || msg.indexOf("Eof") > -1 ||
						msg.indexOf("Close") > -1 || msg.indexOf("close") > -1) {
					logger.warn(emsg.toString());
				} else {
					logger.warn(emsg.toString(), e);
				}
			}
		}
		int payloadLen = 0;
		if (Constants.POST.equals(method) ||
				Constants.PUT.equals(method) ||
				Constants.DELETE.equals(method)) {
			if (req.getPayload() != null) {
				payloadLen = req.getPayload().length;
			}
		}

		// 同期
		writeAccessStart(mainThreadName, method, lastForwarded, requestUrl, protocol, 
				requestHeadersString, feed, payloadLen, serviceName, requestInfo);
		return null;
	}

	/**
	 * アクセス開始ログを出力.
	 * @param mainThreadName メインスレッド名
	 * @param method メソッド
	 * @param lastForwarded リモートIPアドレス
	 * @param requestUrl リクエストURL (+QueryString)
	 * @param protocol プロトコル
	 * @param requestHeadersString リクエストヘッダを文字列に変換したもの
	 * @param feed Feed
	 * @param payloadLen リクエストデータサイズ
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 */
	void writeAccessStart(String mainThreadName, String method, String lastForwarded,
			String requestUrl, String protocol, String requestHeadersString,
			FeedBase feed, int payloadLen, String serviceName, RequestInfo requestInfo) {
		if (logger.isInfoEnabled()) {
			StringBuilder ssb = new StringBuilder();
			ssb.append(getRequestInfoStr(requestInfo));
			ssb.append("Request START ");
			ssb.append(lastForwarded);
			ssb.append(" \"");
			ssb.append(method);
			ssb.append(" ");
			ssb.append(requestUrl);
			ssb.append("\" ");
			ssb.append(protocol);
			ssb.append(" ");
			String msgRequestStart = ssb.toString();

			// リクエストヘッダ出力
			StringBuilder sb = new StringBuilder();
			sb.append(msgRequestStart);
			sb.append(requestHeadersString);

			String continuationOfFeedJson = null;
			int limitLen = LogUtil.getLogMessageWordcountLimit();

			// リクエストボディ出力
			if (Constants.POST.equals(method) ||
					Constants.PUT.equals(method) ||
					Constants.DELETE.equals(method)) {
				if (feed != null) {
					sb.append(" [feed] ");
					FeedTemplateMapper mapper = TaggingEnvUtil.getResourceMapper(serviceName);
					String json = mapper.toJSON(feed);

					// リクエストFeedの文字数が多い場合は切る。
					int lenReq = sb.length();
					int maxLen = limitLen - lenReq;
					String wJson = cutLogMessage(json, maxLen);
					if (!json.equals(wJson)) {
						continuationOfFeedJson = json.substring(wJson.length());
					}
					sb.append(wJson);

				} else {
					sb.append(" [payload size] ");
					sb.append(payloadLen);
				}
			}

			logger.info(sb.toString());

			// Feed JSONが規定文字数を超えていた場合、続きの情報もログ出力する。
			while (!StringUtils.isBlank(continuationOfFeedJson)) {
				sb = new StringBuilder();
				sb.append(msgRequestStart);
				sb.append(" [continuation of feed] ");

				int lenReq = sb.length();
				int maxLen = limitLen - lenReq;
				String wJson = cutLogMessage(continuationOfFeedJson, maxLen);
				if (!continuationOfFeedJson.equals(wJson)) {
					continuationOfFeedJson = continuationOfFeedJson.substring(wJson.length());
				} else {
					continuationOfFeedJson = null;
				}
				sb.append(wJson);
				logger.info(sb.toString());
			}
		}
	}

	/**
	 * アクセス終了ログを出力.
	 * @param req リクエスト
	 * @param resp レスポンス
	 */
	public void writeAccessEnd(ReflexRequest req, ReflexResponse resp) {
		if (logger.isInfoEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(getRequestInfoStr(req.getRequestInfo()));
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

			if (resp != null) {
				// ステータス
				sb.append(" [STATUS] ");
				int status = resp.getStatus();
				sb.append(status);

				// エラーページ転送の場合、Cookieに設定するステータスとメッセージをログ出力する。
				if (status == HttpStatus.SC_MOVED_TEMPORARILY) {
					Map<String, String> setCookies = CookieUtil.parseSetCookie(resp);
					if (setCookies != null) {
						String causeStatus = setCookies.get(ErrorPageUtil.COOKIE_ERROR_STATUS);
						if (!StringUtils.isBlank(causeStatus)) {
							sb.append(" ERROR_STATUS=");
							sb.append(causeStatus);
						}
						String causeMessage = setCookies.get(ErrorPageUtil.COOKIE_ERROR_MESSAGE);
						if (!StringUtils.isBlank(causeMessage)) {
							sb.append(" ERROR_MESSAGE=");
							sb.append(causeMessage);
						}
					}
				}
				// ステータスが300番台の場合、Locationを出力する。
				if (status >= 300 && status < 400) {
					String location = resp.getHeader(ReflexServletConst.HEADER_LOCATION);
					if (location != null || 
							status == HttpStatus.SC_MOVED_PERMANENTLY ||
							status == HttpStatus.SC_MOVED_TEMPORARILY) {
						sb.append(" [");
						sb.append(ReflexServletConst.HEADER_LOCATION);
						sb.append("] ");
						sb.append(location);
					}
				}
				
				// レスポンスヘッダを出力
				sb.append(" ");
				sb.append(getResponseHeadersString(resp));
			}

			logger.info(sb.toString());
		}
	}

	/**
	 * リクエストヘッダのログ出力内容を取得.
	 * @param req リクエスト
	 * @return リクエストヘッダのログ出力内容
	 */
	private String getRequestHeadersString(ReflexRequest req) {
		StringBuilder sb = new StringBuilder();
		sb.append("[Headers] ");
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
	 * レスポンスヘッダのログ出力内容を取得.
	 * @param resp レスポンス
	 * @return レスポンスヘッダのログ出力内容
	 */
	private String getResponseHeadersString(ReflexResponse resp) {
		StringBuilder sb = new StringBuilder();
		sb.append("[Headers] ");
		Enumeration<String> enu = resp.getHeaderNameEnumeration();
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
			Collection<String> values = resp.getHeaders(name);
			Iterator<String> it = values.iterator();
			while (it.hasNext()) {
				valNum++;
				if (valNum > 1) {
					tmp.append(",");
				}
				String value = it.next();
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
	 * ログ用リクエスト情報表記を取得.
	 * @param requestInfo リクエスト情報
	 * @return ログ用リクエスト情報文字列
	 */
	public String getRequestInfoStr(RequestInfo requestInfo) {
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
	 * ログアラート通知処理.
	 * メール送信は別スレッドで実行される。
	 * @param logEntry ログエントリー
	 * @param systemContext SystemContext
	 */
	private void alert(EntryBase logEntry, SystemContext systemContext)
	throws IOException, TaggingException {
		String serviceName = systemContext.getServiceName();
		RequestInfo requestInfo = systemContext.getRequestInfo();

		// アラート通知メールアドレスを取得
		String tmpAddresses = TaggingEnvUtil.getProp(serviceName,
				SettingConst.LOGALERT_MAIL_ADDRESS, null);
		if (StringUtils.isBlank(tmpAddresses)) {
			StringBuilder sb = new StringBuilder();
			sb.append(getRequestInfoStr(requestInfo));
			sb.append("log alert email address setting does not exist.");
			logger.warn(sb.toString());
			return;
		}
		String[] to = tmpAddresses.split(LOGALERT_ADDRESS_DELIMITER);

		// アラート通知メールエントリーを取得
		EntryBase logAlertMailEntry = systemContext.getEntry(Constants.URI_SETTINGS_LOGALERT, true);
		if (logAlertMailEntry == null || StringUtils.isBlank(logAlertMailEntry.title) ||
				StringUtils.isBlank(logAlertMailEntry.summary)) {
			StringBuilder sb = new StringBuilder();
			sb.append(getRequestInfoStr(requestInfo));
			sb.append("log alert mail setting does not exist.");
			logger.warn(sb.toString());
			return;
		}

		// 本文を編集
		String textMessage = convertMessage(logEntry, logAlertMailEntry.summary);
		logAlertMailEntry.summary = textMessage;
		if (logAlertMailEntry.content != null &&
				!StringUtils.isBlank(logAlertMailEntry.content._$$text)) {
			String htmlMessage = convertMessage(logEntry, logAlertMailEntry.content._$$text);
			logAlertMailEntry.content._$$text = htmlMessage;
		}

		// アラート通知メール送信
		EMailBlogic emailBlogic = new EMailBlogic();
		emailBlogic.sendMail(logAlertMailEntry, null, to, null, null, systemContext);
	}

	/**
	 * メール本文を編集.
	 * @param logEntry ログエントリー
	 * @param message メール本文
	 * @return 編集したメール本文
	 */
	private String convertMessage(EntryBase logEntry, String message) {
		message = message.replace(LOGALERT_REPLACE_UPDATED, convertUpdated(logEntry.updated));
		message = message.replace(LOGALERT_REPLACE_COMPONENT, logEntry.title);
		message = message.replace(LOGALERT_REPLACE_LEVEL, logEntry.subtitle);
		message = message.replace(LOGALERT_REPLACE_MESSAGE, logEntry.summary);
		return message;
	}

	/**
	 * updatedをメール設定用更新日時にフォーマット変更する.
	 * yyyy-MM-dd'T'HH:mm:ss.sss+09:00 → yyyy-MM-dd HH:mm:ss
	 * @param updated
	 * @return
	 */
	private String convertUpdated(String updated) {
		StringBuilder sb = new StringBuilder();
		sb.append(updated.substring(0, 10));
		sb.append(" ");
		sb.append(updated.substring(11, 19));
		return sb.toString();
	}

	/**
	 * ログメッセージのFeed JSON部分を規定の文字数のうち、きりのいい箇所で切って返却する。
	 * @param json Feed JSON
	 * @param maxLen 最大文字数
	 * @return 最大文字数のうちきりのいい箇所で切った文字列
	 */
	private String cutLogMessage(String json, int maxLen) {
		if (StringUtils.isBlank(json) || maxLen <= 0) {
			return json;
		}
		// 最大文字数に満たない場合はそのまま返却する。
		int jsonLen = json.length();
		if (jsonLen <= maxLen) {
			return json;
		}

		// JSONの","で区切る。
		String tmpJson = json.substring(0, maxLen + 1);
		int idx = tmpJson.lastIndexOf(CUTLOGMESSAGE_CHAR);
		int acceptableRange = maxLen / 2;
		if (idx > acceptableRange) {
			return tmpJson.substring(0, idx);
		} else {
			return tmpJson.substring(0, maxLen);
		}
	}
	
	/**
	 * 対象サービス名を取得.
	 * リクエストデータの内容ログ出力の際のシリアライズに使用するサービス名
	 * @param req リクエスト
	 * @return 対象サービス名
	 */
	private String getTargetService(ReflexRequest req) 
	throws IOException {
		String targetServiceName = null;
		// 汎用APIの他サービス連携かどうか判定
		try {
			ProviderUtil.checkAPIKey(req);
			targetServiceName = ProviderUtil.getServiceLinkage(req);
		} catch (TaggingException e) {
			// Do nothing.
		}
		if (StringUtils.isBlank(targetServiceName)) {
			targetServiceName = req.getServiceName();
		}
		return targetServiceName;
	}

}
