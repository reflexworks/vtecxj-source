package jp.reflexworks.batch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.def.LoginLogoutManagerDefault;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.DateUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * ログエントリーをBigQueryに移動させる処理.
 * サービスごとに行う処理
 */
public class MoveLogToBigQueryCallable extends ReflexCallable<Boolean> {
	
	/** URI : /_batchjob/ */
	private static final String URI_BATCHJOB_SLASH = Constants.URI_BATCHJOB + "/";
	/** URIの文字列長 : /_batchjob/ */
	private static final int URI_BATCHJOB_SLASH_LEN = URI_BATCHJOB_SLASH.length();

	/** ログ残存日時 */
	private String logKeepDate;
	/** バッチジョブ履歴残存日時 */
	private String batchjobKeepDate;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param logKeepDate ログ残存日時
	 * @param batchjobKeepDate バッチジョブ履歴残存日時
	 */
	public MoveLogToBigQueryCallable(String logKeepDate, String batchjobKeepDate) {
		this.logKeepDate = logKeepDate;
		this.batchjobKeepDate = batchjobKeepDate;
	}

	/**
	 * ログエントリーをBigQueryに移動させる処理.
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		String serviceName = getServiceName();
		if (isEnableAccessLogBdbbatch()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[MoveLogToBigQueryCallable] start. serviceName=");
			sb.append(serviceName);
			sb.append(", logKeepDate=");
			sb.append(logKeepDate);
			sb.append(", batchjobKeepDate");
			sb.append(batchjobKeepDate);
			logger.info(sb.toString());
		}

		// ReflexContextを取得 (セッションも含まれる。)
		ReflexContext reflexContext = (ReflexContext)getReflexContext();
		// SystemContextを生成
		SystemContext systemContext = new SystemContext(reflexContext.getAuth(),
				reflexContext.getRequestInfo(), reflexContext.getConnectionInfo());
		execEachService(systemContext, systemContext.getServiceName());
		return true;
	}

	/**
	 * サービスごとの処理.
	 * @param systemContext SystemContext
	 * @param serviceName サービス名
	 */
	private void execEachService(SystemContext systemContext, String serviceName) {
		// 最大取得件数
		int limit = getLogEntryNumberLimit();

		// ログエントリーの移動
		moveLog(serviceName, limit, systemContext);

		// ログイン履歴エントリーの移動
		moveLoginHistory(serviceName, limit, systemContext);

		// バッチジョブ履歴エントリーの移動
		moveBatchjob(serviceName, limit, systemContext);
	}
	
	/**
	 * ログエントリーの移動
	 * @param serviceName サービス名
	 * @param limit Feed検索最大取得件数
	 * @param systemContext SystemContext
	 */
	private void moveLog(String serviceName, int limit, SystemContext systemContext) {
		try {
			// ログエントリーの移動
			String requestUriBase = getLogRequestUri(logKeepDate, limit);
			String cursorStr = null;
			do {
				cursorStr = null;
				// BDBからログエントリーを取得。
				String requestUri = addCursorStr(requestUriBase, cursorStr);
	
				if (isEnableAccessLogBdbbatch()) {
					StringBuilder sb = new StringBuilder();
					sb.append("[moveLog] (");
					sb.append(serviceName);
					sb.append(")");
					sb.append("[get log] uri=");
					sb.append(requestUri);
					logger.info(sb.toString());
				}
	
				FeedBase feed = systemContext.getFeed(requestUri);
	
				if (TaggingEntryUtil.isExistData(feed)) {
					if (isEnableAccessLogBdbbatch()) {
						StringBuilder sb = new StringBuilder();
						sb.append("[moveLog] (");
						sb.append(serviceName);
						sb.append(")");
						sb.append("[get log] entry.size=");
						sb.append(feed.entry.size());
						logger.info(sb.toString());
					}
					// サービスにBigQueryの指定がある場合、BigQueryに内容を登録する。
					List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
					for (EntryBase entry : feed.entry) {
						Map<String, Object> rowMap = convertBqLog(entry);
						list.add(rowMap);
					}
					try {
						systemContext.postBq(BatchBDBConst.BQ_TABLENAME_LOG,
								list, false);
					} catch (InvalidServiceSettingException se) {
						// BigQueryの設定が無い場合
						StringBuilder sb = new StringBuilder();
						sb.append("[moveLog] (");
						sb.append(serviceName);
						sb.append(") BigQuery setting is not exist. ");
						sb.append(se.getMessage());
						logger.info(sb.toString());
					}
	
					// ログエントリーを削除する。
					systemContext.delete(feed);
	
					// カーソル取得
					cursorStr = TaggingEntryUtil.getCursorFromFeed(feed);
	
				} else {
					if (isEnableAccessLogBdbbatch()) {
						StringBuilder sb = new StringBuilder();
						sb.append("[moveLog] (");
						sb.append(serviceName);
						sb.append(")");
						sb.append("[get log] no entry.");
						logger.info(sb.toString());
					}
				}
	
			} while (!StringUtils.isBlank(cursorStr));

		} catch (Throwable e) {
			// ログ出力し処理継続
			StringBuilder sb = new StringBuilder();
			sb.append("[moveLog] (");
			sb.append(serviceName);
			sb.append(") Error occured. ");
			sb.append(e.getClass().getName());
			sb.append(": ");
			sb.append(e.getMessage());
			if (e instanceof IllegalParameterException || 
					e instanceof InvalidServiceSettingException) {
				logger.warn(sb.toString(), e);
			} else {
				logger.error(sb.toString(), e);
			}
		}
	}
	
	/**
	 * ログイン履歴エントリーの移動
	 * @param serviceName サービス名
	 * @param limit Feed検索最大取得件数
	 * @param systemContext SystemContext
	 */
	private void moveLoginHistory(String serviceName, int limit, SystemContext systemContext) {
		try {
			// ログイン履歴エントリーの移動
			String requestUriBase = getLoginHistoryRequestUri(logKeepDate, limit);
			String cursorStr = null;
			do {
				cursorStr = null;
				// BDBからログイン履歴エントリーを取得。
				String requestUri = addCursorStr(requestUriBase, cursorStr);

				if (isEnableAccessLogBdbbatch()) {
					StringBuilder sb = new StringBuilder();
					sb.append("[moveLoginHistory] (");
					sb.append(serviceName);
					sb.append(")");
					sb.append("[get login_history] uri=");
					sb.append(requestUri);
					logger.info(sb.toString());
				}
				FeedBase feed = systemContext.getFeed(requestUri);

				if (TaggingEntryUtil.isExistData(feed)) {
					if (isEnableAccessLogBdbbatch()) {
						StringBuilder sb = new StringBuilder();
						sb.append("[moveLoginHistory] (");
						sb.append(serviceName);
						sb.append(")");
						sb.append("[get login_history] entry.size=");
						sb.append(feed.entry.size());
						logger.info(sb.toString());
					}
					// サービスにBigQueryの指定がある場合、BigQueryに内容を登録する。
					List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
					for (EntryBase entry : feed.entry) {
						Map<String, Object> rowMap = convertBqLoginHistory(entry);
						list.add(rowMap);
					}
					try {
						systemContext.postBq(
								BatchBDBConst.BQ_TABLENAME_LOGIN_HISTORY,
								list, false);
					} catch (InvalidServiceSettingException se) {
						// BigQueryの設定が無い場合
						StringBuilder sb = new StringBuilder();
						sb.append("[moveLoginHistory] (");
						sb.append(serviceName);
						sb.append(") BigQuery setting is not exist. ");
						sb.append(se.getMessage());
						logger.info(sb.toString());
					}

					// ログイン履歴エントリーを削除する。
					systemContext.delete(feed);

					// カーソル取得
					cursorStr = TaggingEntryUtil.getCursorFromFeed(feed);

				} else {
					if (isEnableAccessLogBdbbatch()) {
						StringBuilder sb = new StringBuilder();
						sb.append("[moveLoginHistory] (");
						sb.append(serviceName);
						sb.append(")");
						sb.append("[get login_history] no entry.");
						logger.info(sb.toString());
					}
				}

			} while (!StringUtils.isBlank(cursorStr));

		} catch (Throwable e) {
			// ログ出力し処理継続
			StringBuilder sb = new StringBuilder();
			sb.append("[moveLoginHistory] (");
			sb.append(serviceName);
			sb.append(") Error occured. ");
			sb.append(e.getClass().getName());
			if (e instanceof IllegalParameterException || 
					e instanceof InvalidServiceSettingException) {
				logger.warn(sb.toString());
			} else {
				logger.error(sb.toString(), e);
			}
		}
	}
	
	/**
	 * バッチジョブ履歴エントリーの移動
	 * @param serviceName サービス名
	 * @param limit Feed検索最大取得件数
	 * @param systemContext SystemContext
	 */
	private void moveBatchjob(String serviceName, int limit, SystemContext systemContext) {
		try {
			// バッチジョブ履歴エントリーの移動
			String requestUriBase = getBatchjobRequestUri(batchjobKeepDate, limit);
			String cursorStr = null;
			do {
				cursorStr = null;
				// BDBからバッチジョブ履歴エントリーを取得。
				String requestUri = addCursorStr(requestUriBase, cursorStr);

				if (isEnableAccessLogBdbbatch()) {
					StringBuilder sb = new StringBuilder();
					sb.append("[moveBatchjob] (");
					sb.append(serviceName);
					sb.append(")");
					sb.append("[get batchjob] uri=");
					sb.append(requestUri);
					logger.info(sb.toString());
				}
				FeedBase feed = systemContext.getFeed(requestUri);

				if (TaggingEntryUtil.isExistData(feed)) {
					if (isEnableAccessLogBdbbatch()) {
						StringBuilder sb = new StringBuilder();
						sb.append("[moveBatchjob] (");
						sb.append(serviceName);
						sb.append(")");
						sb.append("[get batchjob] entry.size=");
						sb.append(feed.entry.size());
						logger.info(sb.toString());
					}
					// サービスにBigQueryの指定がある場合、BigQueryに内容を登録する。
					List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
					for (EntryBase entry : feed.entry) {
						Map<String, Object> rowMap = convertBqBatchjob(entry);
						list.add(rowMap);
					}
					try {
						systemContext.postBq(
								BatchBDBConst.BQ_TABLENAME_BATCHJOB,
								list, false);
					} catch (InvalidServiceSettingException se) {
						// BigQueryの設定が無い場合
						StringBuilder sb = new StringBuilder();
						sb.append("[moveBatchjob] (");
						sb.append(serviceName);
						sb.append(") BigQuery setting is not exist. ");
						sb.append(se.getMessage());
						logger.info(sb.toString());
					}

					// バッチジョブ履歴エントリーを削除する。
					systemContext.delete(feed);

					// カーソル取得
					cursorStr = TaggingEntryUtil.getCursorFromFeed(feed);

				} else {
					if (isEnableAccessLogBdbbatch()) {
						StringBuilder sb = new StringBuilder();
						sb.append("[moveBatchjob] (");
						sb.append(serviceName);
						sb.append(")");
						sb.append("[get batchjob] no entry.");
						logger.info(sb.toString());
					}
				}

			} while (!StringUtils.isBlank(cursorStr));

		} catch (Throwable e) {
			// ログ出力し処理継続
			StringBuilder sb = new StringBuilder();
			sb.append("[moveBatchjob] (");
			sb.append(serviceName);
			sb.append(") Error occured. ");
			sb.append(e.getClass().getName());
			if (e instanceof IllegalParameterException || 
					e instanceof InvalidServiceSettingException) {
				logger.warn(sb.toString());
			} else {
				logger.error(sb.toString(), e);
			}
		}
	}

	/**
	 * ログEntry最大取得件数を取得.
	 * @return ログEntry最大取得件数
	 */
	private int getLogEntryNumberLimit() {
		return TaggingEnvUtil.getSystemPropInt(BatchBDBConst.LOG_ENTRY_NUMBER_LIMIT,
				BatchBDBConst.LOG_ENTRY_NUMBER_LIMIT_DEFAULT);
	}

	/**
	 * ログ検索URIを取得
	 *   /_log?f&l={最大取得件数}&updated-lt-{ログ削除日時}
	 * @param logKeepDate ログ残存日時
	 * @param limit 最大取得件数
	 * @return ログ検索URI
	 */
	private String getLogRequestUri(String logKeepDate, int limit) {
		StringBuilder sb = new StringBuilder();
		sb.append(Constants.URI_LOG);
		sb.append("?");
		sb.append(RequestParam.PARAM_LIMIT);
		sb.append("=");
		sb.append(limit);
		sb.append("&updated-lt-");
		sb.append(logKeepDate);
		return sb.toString();
	}

	/**
	 * ログイン履歴検索URIを取得
	 *   /_login_history?f&l={最大取得件数}&updated-lt-{ログ削除日時}
	 * @param logKeepDate ログ残存日時
	 * @param limit 最大取得件数
	 * @return ログイン履歴検索URI
	 */
	private String getLoginHistoryRequestUri(String logKeepDate, int limit) {
		StringBuilder sb = new StringBuilder();
		sb.append(Constants.URI_LOGIN_HISTORY);
		sb.append("?");
		sb.append(RequestParam.PARAM_LIMIT);
		sb.append("=");
		sb.append(limit);
		sb.append("&updated-lt-");
		sb.append(logKeepDate);
		return sb.toString();
	}

	/**
	 * バッチジョブ履歴検索URIを取得
	 *   /_batchjob_alias?f&l={最大取得件数}&summary-lt-{バッチジョブ履歴削除日時(yyyyMMddHHmm)}
	 * @param batchjobKeepDate バッチジョブ履歴残存日時
	 * @param limit 最大取得件数
	 * @return バッチジョブ履歴検索URI
	 */
	private String getBatchjobRequestUri(String batchjobKeepDate, int limit) {
		StringBuilder sb = new StringBuilder();
		sb.append(Constants.URI_BATCHJOB_ALIAS);
		sb.append("?");
		sb.append(RequestParam.PARAM_LIMIT);
		sb.append("=");
		sb.append(limit);
		sb.append("&summary-lt-");
		sb.append(batchjobKeepDate);
		return sb.toString();
	}

	/**
	 * リクエストURIにカーソルを付加.
	 * @param uri リクエストURI
	 * @param cursorStr カーソル
	 * @return 編集したURI
	 */
	private String addCursorStr(String uri, String cursorStr) {
		if (StringUtils.isBlank(cursorStr)) {
			return uri;
		}
		StringBuilder sb = new StringBuilder();
		sb.append(uri);
		sb.append("&");
		sb.append(RequestParam.PARAM_NEXT);
		sb.append("=");
		//sb.append(UrlUtil.urlEncode(cursorStr));
		sb.append(cursorStr);
		return sb.toString();
	}

	/**
	 * ログエントリーの内容をBigQuery用に変換
	 * @param entry ログエントリー
	 * @return BigQuery用ログ
	 */
	private Map<String, Object> convertBqLog(EntryBase entry)
	throws java.text.ParseException {
		Map<String, Object> rowMap = new LinkedHashMap<String, Object>();
		rowMap.put(BatchBDBConst.BQ_LOG_KEY, entry.getMyUri());
		rowMap.put(BatchBDBConst.BQ_LOG_TITLE, entry.title);
		rowMap.put(BatchBDBConst.BQ_LOG_SUBTITLE, entry.subtitle);
		rowMap.put(BatchBDBConst.BQ_LOG_MESSAGE, substringLog(entry.summary));
		rowMap.put(BatchBDBConst.BQ_LOG_UPDATED, convertDate(entry.updated));
		rowMap.put(BatchBDBConst.BQ_LOG_INFORMATION, entry.rights);
		return rowMap;
	}

	/**
	 * ログイン履歴エントリーの内容をBigQuery用に変換
	 * @param entry ログイン履歴エントリー
	 * @return BigQuery用ログ
	 */
	private Map<String, Object> convertBqLoginHistory(EntryBase entry)
	throws ParseException, java.text.ParseException {
		Map<String, Object> rowMap = new LinkedHashMap<String, Object>();
		rowMap.put(BatchBDBConst.BQ_LOG_KEY, entry.getMyUri());
		rowMap.put(BatchBDBConst.BQ_LOG_UPDATED, convertDate(entry.published));
		if (!StringUtils.isBlank(entry.title)) {
			rowMap.put(BatchBDBConst.BQ_LOGINHISTORY_TYPE, entry.title);
		}

		String json = entry.summary;
		if (!StringUtils.isBlank(json)) {
			try {
				JSONParser parser = new JSONParser();
				JSONObject jsonObj = (JSONObject)parser.parse(json);
				String ip = (String)jsonObj.get(LoginLogoutManagerDefault.LOGIN_IP);
				if (!StringUtils.isBlank(ip)) {
					rowMap.put(BatchBDBConst.BQ_LOGINHISTORY_IP, ip);
				}
				String uid = (String)jsonObj.get(LoginLogoutManagerDefault.LOGIN_UID);
				if (!StringUtils.isBlank(uid)) {
					rowMap.put(BatchBDBConst.BQ_LOGINHISTORY_UID, uid);
				}
				String account = (String)jsonObj.get(LoginLogoutManagerDefault.LOGIN_ACCOUNT);
				if (!StringUtils.isBlank(account)) {
					rowMap.put(BatchBDBConst.BQ_LOGINHISTORY_ACCOUNT, account);
				}
				String useragent = (String)jsonObj.get(LoginLogoutManagerDefault.LOGIN_USERAGENT);
				if (!StringUtils.isBlank(useragent)) {
					rowMap.put(BatchBDBConst.BQ_LOGINHISTORY_USERAGENT, useragent);
				}
				String cause = (String)jsonObj.get(LoginLogoutManagerDefault.LOGIN_CAUSE);
				if (!StringUtils.isBlank(cause)) {
					rowMap.put(BatchBDBConst.BQ_LOGINHISTORY_CAUSE, cause);
				}

			} catch (ParseException e) {
				logger.warn("[convertBqLoginHistory] ParseException: " + e.getMessage());
			}
		}

		return rowMap;
	}

	/**
	 * バッチジョブ履歴エントリーの内容をBigQuery用に変換
	 * @param entry バッチジョブ履歴エントリー
	 * @return BigQuery用ログ
	 */
	private Map<String, Object> convertBqBatchjob(EntryBase entry)
	throws ParseException, java.text.ParseException {
		String[] tmp = dividePodCloudrunjob(entry.subtitle);
		String pod = tmp[0];
		String jobId = tmp[1];
		Map<String, Object> rowMap = new LinkedHashMap<String, Object>();
		rowMap.put(BatchBDBConst.BQ_LOG_KEY, entry.getMyUri());
		rowMap.put(BatchBDBConst.BQ_BATCHJOB_STATUS, entry.title);
		rowMap.put(BatchBDBConst.BQ_BATCHJOB_SCRIPT, entry.rights);
		rowMap.put(BatchBDBConst.BQ_BATCHJOB_START, entry.summary);
		if (!StringUtils.isBlank(pod)) {
			rowMap.put(BatchBDBConst.BQ_BATCHJOB_POD, pod);
		}
		if (!StringUtils.isBlank(jobId)) {
			rowMap.put(BatchBDBConst.BQ_BATCHJOB_CLOUDRUNJOB, jobId);
		}
		rowMap.put(BatchBDBConst.BQ_LOG_UPDATED, convertDate(entry.updated));
		return rowMap;
	}
	
	/**
	 * subtitleからPod名とCloudRunJob実行IDを分離する
	 * @param subtitle {Pod名}[,{CloudRunJob実行ID}]
	 * @return [0]Pod名、[1]CloudRunJob実行ID
	 */
	private String[] dividePodCloudrunjob(String subtitle) {
		String pod = "";
		String jobId = "";
		if (!StringUtils.isBlank(subtitle)) {
			int idx = subtitle.indexOf(",");
			if (idx > 0) {
				pod = subtitle.substring(0, idx);
				jobId = subtitle.substring(idx + 1);
			} else {
				pod = subtitle;
			}
		}
		return new String[]{pod, jobId};
	}

	/**
	 * 日時文字列をDate型に変換する.
	 * @param dateStr 日時文字列
	 * @return Date型オブジェクト
	 */
	private Date convertDate(String dateStr)
	throws java.text.ParseException {
		return DateUtil.getDate(dateStr, BatchBDBConst.TIMEZONE);
	}
	
	/**
	 * キー: /_batchjob/{ジョブ名}/{ジョブ実行時刻(yyyyMMddHHmm)} からジョブ名を抽出
	 * @param uri キー
	 * @return ジョブ名
	 */
	private String getScript(String uri) {
		if (uri.startsWith(URI_BATCHJOB_SLASH)) {
			return uri.substring(URI_BATCHJOB_SLASH_LEN, uri.indexOf("/", URI_BATCHJOB_SLASH_LEN + 1));
		}
		return null;
	}
	
	/**
	 * キー: /_batchjob/{ジョブ名}/{ジョブ実行時刻(yyyyMMddHHmm)} からジョブ実行時刻を抽出
	 * @param uri キー
	 * @return ジョブ実行時刻(yyyyMMddHHmm)
	 */
	private String getStart(String uri) {
		if (uri.startsWith(URI_BATCHJOB_SLASH)) {
			return uri.substring(uri.lastIndexOf("/") + 1);
		}
		return null;
	}

	/**
	 * メッセージ文字数を規定数で切る.
	 * @param message ログメッセージ
	 * @return 編集したログメッセージ
	 */
	private String substringLog(String message) {
		if (StringUtils.isBlank(message)) {
			return message;
		}
		int wordcountLimit = LogUtil.getLogMessageWordcountLimit();
		int len = message.length();
		if (len > wordcountLimit) {
			return message.substring(0, wordcountLimit);
		} else {
			return message;
		}
	}

	/**
	 * BDBバッチのアクセスログを出力するかどうかを取得.
	 * @return BDBバッチへのアクセスログを出力する場合true
	 */
	private boolean isEnableAccessLogBdbbatch() {
		return VtecxBatchUtil.isEnableAccessLogBdbbatch();
	}

}
