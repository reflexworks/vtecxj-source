package jp.reflexworks.taggingservice.plugin.def;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.servlet.HttpStatus;
import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.servlet.util.UrlUtil;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.blogic.PaginationBlogic;
import jp.reflexworks.taggingservice.blogic.PaginationConst;
import jp.reflexworks.taggingservice.blogic.ServiceBlogic;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.MemorySortManager;
import jp.reflexworks.taggingservice.provider.ProviderConst;
import jp.reflexworks.taggingservice.taskqueue.MemorySortCallable;
import jp.reflexworks.taggingservice.taskqueue.TaskQueueUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.RetryUtil;
import jp.sourceforge.reflex.util.DeflateUtil;
import jp.sourceforge.reflex.util.Requester;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * インメモリソート管理クラス.
 */
public class MemorySortManagerDefault implements MemorySortManager {

	/** 設定 : インメモリソートサーバURL */
	public static final String MEMORYSORT_URL = "_memorysort.url";
	/** 設定 : インメモリソートリクエストのタイムアウト(ミリ秒) */
	public static final String MEMORYSORTREQUEST_TIMEOUT_MILLIS = "_memorysort.timeout.millis";
	/** 設定 : インメモリソートリクエストに失敗したときのリトライ回数 */
	public static final String MEMORYSORTREQUEST_RETRY_COUNT = "_memorysort.retry.count";
	/** 設定 : インメモリソートリクエストリトライ時のスリープ時間(ミリ秒) */
	public static final String MEMORYSORTREQUEST_RETRY_WAITMILLIS = "_memorysort.retry.waitmillis";
	/** 設定 : インメモリソートサーバへのアクセスログを出力するかどうか */
	public static final String MEMORYSORTREQUEST_ENABLE_ACCESSLOG = "_memorysort.enable.accesslog";

	/** 設定デフォルト : リクエストのタイムアウト(ミリ秒) */
	static final int MEMORYSORTREQUEST_TIMEOUT_MILLIS_DEFAULT = 120000;
	/** 設定デフォルト : リトライ総数 */
	static final int MEMORYSORTREQUEST_RETRY_COUNT_DEFAULT = 2;
	/** 設定デフォルト : リトライ時のスリープ時間(ミリ秒) */
	static final int MEMORYSORTREQUEST_RETRY_WAITMILLIS_DEFAULT = 80;

	/** Header : X-SERVICENAME */
	static final String HEADER_SERVICENAME = ServiceManagerDefault.HEADER_SERVICENAME;
	/** Header : X-SERVICELINKAGE */
	static final String HEADER_SERVICELINKAGE = ProviderConst.HEADER_SERVICELINKAGE;
	/** Header : X-SERVICEKEY */
	static final String HEADER_SERVICEKEY = ProviderConst.HEADER_SERVICEKEY;
	/** Header : Cookie */
	static final String HEADER_COOKIE = ReflexServletConst.HEADER_COOKIE;
	/** Header Cookie value : SID */
	static final String COOKIE_SID = ReflexServletConst.COOKIE_SID;
	/** ログエントリーのTITLE */
	private static final String TITLE = "MemorySort";

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 初期処理.
	 */
	@Override
	public void init() {
		// Do nothing.
	}

	/**
	 * シャットダウン処理.
	 */
	@Override
	public void close() {
		// Do nothing.
	}

	/**
	 * インメモリソート
	 * @param param 検索条件
	 * @param conditionName 検索条件文字列 (セッション格納時のキーに使用)
	 * @param tmpAuth 対象サービスの認証情報
	 * @param reflexContext ReflexContext
	 */
	@Override
	public void sort(RequestParam param, String conditionName, ReflexAuthentication tmpAuth,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		String targetServiceName = tmpAuth.getServiceName();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		boolean isTask = false;
		try {
			if (!isEnabledMemorySort(tmpAuth.getServiceName())) {
				reflexContext.log(TITLE, Constants.WARN, "can't sort in memory.");
				reflexContext.deleteSessionString(PaginationConst.SESSION_KEY_MEMORYSORT_LOCK);
				// エラーを返す
				throw new IllegalParameterException("can't sort in memory.");
			}
			// インメモリソート処理をTaskQueueに登録
			MemorySortCallable callable = new MemorySortCallable(param, conditionName, tmpAuth);
			TaskQueueUtil.addTask(callable, 0, reflexContext.getAuth(), requestInfo,
					reflexContext.getConnectionInfo());
			isTask = true;

		} finally {
			if (!isTask) {
				// 処理中フラグをOFFにする。
				PaginationBlogic paginationBlogic = new PaginationBlogic();
				paginationBlogic.releaseLock(conditionName, targetServiceName, reflexContext);
				if (logger.isDebugEnabled()) {
					StringBuilder sb = new StringBuilder();
					sb.append(LogUtil.getRequestInfoStr(requestInfo));
					sb.append("[sort] releaseLock end. conditionName=");
					sb.append(conditionName);
					logger.debug(sb.toString());
				}
			}
		}
	}

	/**
	 * インメモリソートリクエスト処理
	 * インメモリソートTaskQueueから呼び出されるメソッド
	 * @param param 検索条件
	 * @param conditionName 検索条件文字列 (セッション格納時のキーに使用)
	 * @param tmpAuth 対象サービスの認証情報
	 * @param reflexContext reflexContext
	 */
	public void requestSort(RequestParam param, String conditionName, ReflexAuthentication tmpAuth,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		String targetServiceName = tmpAuth.getServiceName();
		RequestInfo requestInfo = reflexContext.getRequestInfo();

		if (logger.isDebugEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(reflexContext.getRequestInfo()));
			sb.append("[requestSort] start. conditionName=");
			sb.append(conditionName);
			logger.debug(sb.toString());
		}
		try {
			StringBuilder url = new StringBuilder();
			url.append(getMemorySortUrl());
			url.append(UrlUtil.urlEncodePathInfoQuery(conditionName));
			String urlStr = url.toString();

			String method = Constants.GET;

			request(urlStr, method, targetServiceName, reflexContext);

		} finally {
			// 処理中フラグをOFFにする。
			PaginationBlogic paginationBlogic = new PaginationBlogic();
			paginationBlogic.releaseLock(conditionName, targetServiceName, reflexContext);
			if (logger.isDebugEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[requestSort] releaseLock end. conditionName=");
				sb.append(conditionName);
				logger.debug(sb.toString());
			}
		}

		if (logger.isDebugEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(reflexContext.getRequestInfo()));
			sb.append("[requestSort] end. conditionName=");
			sb.append(conditionName);
			logger.debug(sb.toString());
		}
	}

	/**
	 * インメモリソートされた結果の指定ページFeedを取得.
	 * @param param 検索条件
	 * @param num ページ番号
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 指定ページFeed
	 */
	@Override
	public FeedBase getPage(RequestParam param, int num, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// 使用しない
		return null;
	}

	/**
	 * インメモリソート実行可能かどうか判定.
	 * インメモリソートの環境設定が行われていればtrueを返す。
	 * @param serviceName サービス名
	 * @return インメモリソートが実行可能であればtrue
	 */
	@Override
	public boolean isEnabledMemorySort(String serviceName) {
		String memorySortUrl = getMemorySortUrl();
		if (!StringUtils.isBlank(memorySortUrl)) {
			return true;
		}
		return false;
	}

	/**
	 * インメモリサーバへリクエスト
	 * @param urlStr URL
	 * @param method Method
	 * @param targetServiceName 対象サービス名
	 * @param reflexContext ReflexContext
	 * @return レスポンスされたFeed (正常レスポンスのみ返却)
	 */
	private FeedBase request(String urlStr, String method, String targetServiceName,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		String sid = reflexContext.getAuth().getSessionId();
		String serviceName = reflexContext.getServiceName();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();

		Map<String, String> reqHeader = new HashMap<>();
		reqHeader.put(HEADER_SERVICENAME, serviceName);
		if (!serviceName.equals(targetServiceName)) {
			reqHeader.put(HEADER_SERVICELINKAGE, targetServiceName);
			ServiceBlogic serviceBlogic = new ServiceBlogic();
			String serviceKey = serviceBlogic.getServiceKey(targetServiceName,
					requestInfo, connectionInfo);
			reqHeader.put(HEADER_SERVICEKEY, serviceKey);
		}
		StringBuilder cookieVal = new StringBuilder();
		cookieVal.append(COOKIE_SID);
		cookieVal.append("=");
		cookieVal.append(sid);
		reqHeader.put(HEADER_COOKIE, cookieVal.toString());

		Requester requester = new Requester();
		int timeoutMillis = getMemorySortRequestTimeoutMillis();
		int numRetries = getMemorySortRequestRetryCount();
		int waitMillis = getMemorySortRequestRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				long startTime = 0;
				if (isEnableAccessLog()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
							getStartLog(serviceName, method, urlStr));
					startTime = new Date().getTime();
				}

				// リクエスト
				HttpURLConnection http = requester.prepare(urlStr, method,
						reqHeader, timeoutMillis);
				int status = http.getResponseCode();

				if (isEnableAccessLog()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
							getEndLog(serviceName, method, urlStr, status, startTime));
					startTime = new Date().getTime();
				}

				if (status < 400) {
					// 成功
					FeedBase feed = null;
					if (status != HttpStatus.SC_NO_CONTENT) {
						feed = getFeed(http.getInputStream(),
								http.getContentType(),
								serviceName, connectionInfo.getDeflateUtil());
					} else {
						if (logger.isDebugEnabled()) {
							logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
									"[request] memory store response status = " + status);
						}
					}
					return feed;

				} else {
					// エラー
					FeedBase errFeed = getFeed(http.getErrorStream(), http.getContentType(),
							serviceName, connectionInfo.getDeflateUtil());
					StringBuilder errMsg = new StringBuilder();
					errMsg.append("[request] status=");
					errMsg.append(status);
					if (errFeed != null && !StringUtils.isBlank(errFeed.title)) {
						errMsg.append(", message=");
						errMsg.append(errFeed.title);
					}
					String errMsgStr = errMsg.toString();
					logger.warn(LogUtil.getRequestInfoStr(requestInfo) + errMsgStr);
					reflexContext.log(TITLE, Constants.WARN, errMsgStr);
					return null;
				}

			} catch (IOException e) {
				// リトライ判定、入力エラー判定
				convertError(e, requestInfo);
				if (r >= numRetries) {
					// リトライ対象だがリトライ回数を超えた場合
					throw e;
				}
				if (logger.isDebugEnabled()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
							"[request] " + getRetryLog(e, r));
				}
				sleep(waitMillis);

			} catch (org.msgpack.MessageTypeException e) {
				// このエラーはスロー先で入力エラーとみなされてしまうので、致命的例外でラップする。
				throw new IllegalStateException(e);
			}
		}

		// 到達しない
		throw new IllegalStateException("Unreachable code.");
	}

	/**
	 * インメモリソートサーバのURLを取得.
	 * サーブレットパスまで.
	 * @return インメモリソートサーバのURL (サーブレットパスまで)
	 */
	private String getMemorySortUrl() {
		return TaggingEnvUtil.getSystemProp(MEMORYSORT_URL, null);
	}

	/**
	 * インメモリソートリクエストのアクセス失敗時リトライ総数を取得.
	 * @return インメモリソートリクエストのアクセス失敗時リトライ総数
	 */
	private int getMemorySortRequestRetryCount() {
		return TaggingEnvUtil.getSystemPropInt(MEMORYSORTREQUEST_RETRY_COUNT,
				MEMORYSORTREQUEST_RETRY_COUNT_DEFAULT);
	}

	/**
	 * インメモリソートリクエストのアクセス失敗時リトライ時のスリープ時間を取得.
	 * @return インメモリソートリクエストのアクセス失敗時のスリープ時間(ミリ秒)
	 */
	private int getMemorySortRequestRetryWaitmillis() {
		return TaggingEnvUtil.getSystemPropInt(MEMORYSORTREQUEST_RETRY_WAITMILLIS,
				MEMORYSORTREQUEST_RETRY_WAITMILLIS_DEFAULT);
	}

	/**
	 * インメモリソートリクエストのタイムアウト(ミリ秒)を取得.
	 * @return インメモリソートリクエストのタイムアウト(ミリ秒)
	 */
	private int getMemorySortRequestTimeoutMillis() {
		return TaggingEnvUtil.getSystemPropInt(MEMORYSORTREQUEST_TIMEOUT_MILLIS,
				MEMORYSORTREQUEST_TIMEOUT_MILLIS_DEFAULT);
	}

	/**
	 * データストアへのアクセスログを出力するかどうか.
	 * @return データストアへのアクセスログを出力する場合true
	 */
	private boolean isEnableAccessLog() {
		return TaggingEnvUtil.getSystemPropBoolean(
				MEMORYSORTREQUEST_ENABLE_ACCESSLOG, false);
	}

	/**
	 * リクエスト開始ログを編集
	 * @param serviceName サービス名
	 * @param method メソッド
	 * @param url UR
	 * @return ログ文字列
	 */
	private String getStartLog(String serviceName, String method, String url) {
		StringBuilder sb = new StringBuilder();
		sb.append("[request] ");
		sb.append(method);
		sb.append(" ");
		sb.append(url);
		sb.append(" start");
		sb.append(" : svc=");
		sb.append(serviceName);
		return sb.toString();
	}

	/**
	 * リクエスト終了ログを編集
	 * @param serviceName サービス名
	 * @param method メソッド
	 * @param url UR
	 * @param status ステータス
	 * @return ログ文字列
	 */
	private String getEndLog(String serviceName, String method, String url,
			int status, long startTime) {
		StringBuilder sb = new StringBuilder();
		sb.append("[request] ");
		sb.append(method);
		sb.append(" ");
		sb.append(url);
		sb.append(" end");
		sb.append(" : status=");
		sb.append(status);
		sb.append(", svc=");
		sb.append(serviceName);
		sb.append(getElapsedTimeLog(startTime));
		return sb.toString();
	}

	/**
	 * 経過時間ログ表記を取得
	 * @param startTime 開始時間
	 * @return 経過時間ログ表記
	 */
	private String getElapsedTimeLog(long startTime) {
		long finishTime = new Date().getTime();
		long time = finishTime - startTime;
		StringBuilder sb = new StringBuilder();
		sb.append(" - ");
		sb.append(time);
		sb.append("ms");
		return sb.toString();
	}

	/**
	 * 例外を変換する.
	 * リトライ対象の場合例外をスローしない。
	 * @param e データストア例外
	 * @param requestInfo リクエスト情報
	 */
	private void convertError(IOException e, RequestInfo requestInfo)
	throws IOException {
		if (RetryUtil.isRetryError(e, Constants.GET)) {
			return;
		}
		throw e;
	}

	/**
	 * リトライログメッセージを取得
	 * @param e 例外
	 * @param r リトライ回数
	 * @return リトライログメッセージ
	 */
	private String getRetryLog(Throwable e, int r) {
		return RetryUtil.getRetryLog(e, r);
	}

	/**
	 * スリープ処理.
	 * @param waitMillis スリープ時間(ミリ秒)
	 */
	private void sleep(long waitMillis) {
		RetryUtil.sleep(waitMillis);
	}

	/**
	 * レスポンスデータのストリームからFeedを取得する。
	 * MessagePack形式で受信
	 * @param in InputStream
	 * @param contentType Content-Typeヘッダ
	 * @param serviceName サービス名
	 * @param deflateUtil DeflateUtil
	 * @return Feed
	 */
	private FeedBase getFeed(InputStream in, String contentType, String serviceName,
			DeflateUtil deflateUtil)
	throws IOException {
		if (in == null) {
			return null;
		}
		FeedTemplateMapper mapper = TaggingEnvUtil.getResourceMapper(serviceName);
		FeedBase ret = null;
		if (contentType != null && contentType.startsWith(ReflexServletConst.CONTENT_TYPE_JSON)) {
			// JSON
			BufferedReader reader = null;
			try {
				reader = new BufferedReader(new InputStreamReader(in, Constants.ENCODING));
				ret = (FeedBase)mapper.fromJSON(reader);

			} finally {
				if (reader != null) {
					try {
						reader.close();
					} catch (IOException e) {
						logger.warn("[getObject] close error.", e);
					}
				}
			}

		} else {
			// MessagePack
			BufferedInputStream bin = null;
			try {
				bin = new BufferedInputStream(in);
				ret = (FeedBase)mapper.fromMessagePack(bin, true);

			} catch (EOFException e) {
				// レスポンスデータなし
				if (logger.isDebugEnabled()) {
					logger.debug("[getFeed] EOFException: " + e.getMessage());
				}
				return null;
			} catch (ClassNotFoundException e) {
				throw new IOException(e);
			} finally {
				if (bin != null) {
					try {
						bin.close();
					} catch (IOException e) {
						logger.warn("[getFeed] close error.", e);
					}
				}
			}
		}
		return ret;
	}

}
