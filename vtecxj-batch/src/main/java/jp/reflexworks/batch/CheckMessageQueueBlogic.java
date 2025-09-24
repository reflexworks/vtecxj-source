package jp.reflexworks.batch;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.api.Condition;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.servlet.util.UrlUtil;
import jp.reflexworks.taggingservice.api.ReflexBlogic;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.bdbclient.BDBClientUtil;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.requester.BDBRequesterUtil;
import jp.reflexworks.taggingservice.service.TaggingServiceUtil;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.taskqueue.TaskQueueUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.Requester;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * メッセージキュー未送信チェック処理のリクエスト処理.
 *  ・有効なサービス一覧を取得
 *  ・バッチジョブサーバにリクエストする
 */
public class CheckMessageQueueBlogic implements ReflexBlogic<ReflexContext, Boolean> {
	
	/** バッチジョブサーバリクエスト Method */
	static final String REQUEST_METHOD = Constants.PUT;
	/** バッチジョブサーバリクエスト URL設定 */
	static final String URL_BATCHJOB = "_url.batchjob";
	/** サービスステータス設定項目 */
	private static final String FIELD_SERVICESTATUS = "subtitle";

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 実行処理.
	 * @param reflexContext ReflexContext
	 * @param args 引数 (ReflexApplication実行時の引数の4番目以降)
	 */
	public Boolean exec(ReflexContext reflexContext, String[] args) {
		// サービス名がシステム管理サービスでなければエラー
		String systemService = reflexContext.getServiceName();
		if (!systemService.equals(TaggingEnvUtil.getSystemService())) {
			throw new IllegalStateException("Please specify system service for the service name.");
		}

		// SystemContext作成
		SystemContext systemContext = new SystemContext(systemService,
				reflexContext.getRequestInfo(), reflexContext.getConnectionInfo());

		try {
			// サービス一覧を取得
			String baseUri = getRequestUriBase();
			String cursorStr = null;
			List<Future<Boolean>> futures = new ArrayList<Future<Boolean>>();
			do {
				String requestUri = TaggingEntryUtil.addCursorToUri(baseUri, cursorStr);
				FeedBase serviceFeed = systemContext.getFeed(requestUri);
				cursorStr = TaggingEntryUtil.getCursorFromFeed(serviceFeed);
				if (serviceFeed != null && serviceFeed.entry != null) {
					// サービスごとにアクセスカウンタのバッチ処理を行う。
					for (EntryBase serviceEntry : serviceFeed.entry) {
						// メッセージキューチェック
						Future<Boolean> future = execForEachService(systemContext, serviceEntry);
						futures.add(future);
						// BDBQリトライチェック
						future = execForEachService2(systemContext, serviceEntry);
						futures.add(future);
					}
				}

			} while (!StringUtils.isBlank(cursorStr));
			
			// 終了確認 (全ての対象サービスのリクエストを送信できれば終了)
			for (Future<Boolean> future : futures) {
				try {
					future.get();
				} catch (InterruptedException e) {
					logger.error("InterruptedException: " + e.getMessage(), e);
				} catch (ExecutionException e) {
					Throwable cause = e.getCause();
					if (cause == null) {
						cause = e;
					}
					logger.error(cause.getClass().getSimpleName() + ": " + e.getMessage(), e);
				}
			}

		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (TaggingException e) {
			throw new RuntimeException(e);
		}
		return true;
	}
	
	/**
	 * サービス一覧取得URIを取得
	 * @return サービス一覧取得URI
	 */
	private String getRequestUriBase() {
		StringBuilder sb = new StringBuilder();
		sb.append(Constants.URI_SERVICE);
		sb.append("?");
		sb.append(FIELD_SERVICESTATUS);
		sb.append("-");
		sb.append(Condition.REGEX);
		sb.append("-");
		sb.append("^");
		sb.append(Constants.SERVICE_STATUS_PRODUCTION);
		sb.append("$|^");
		sb.append(Constants.SERVICE_STATUS_STAGING);
		sb.append("$");
		return sb.toString();
	}

	/**
	 * サービスごとの処理
	 * @param systemContext SystemContext
	 * @param serviceEntry サービスエントリー
	 */
	private Future<Boolean> execForEachService(SystemContext systemContext, EntryBase serviceEntry)
	throws IOException, TaggingException {
		String serviceName = TaggingServiceUtil.getServiceNameFromServiceUri(serviceEntry.getMyUri());
		CheckMessageQueueCallable callable = new CheckMessageQueueCallable(serviceName);
		return (Future<Boolean>)TaskQueueUtil.addTask(
				callable, 0, systemContext.getAuth(), systemContext.getRequestInfo(), 
				systemContext.getConnectionInfo());
	}

	/**
	 * サービスごとの処理
	 * BDBQリトライ処理のチェックリクエストを送信
	 * @param systemContext SystemContext
	 * @param serviceEntry サービスエントリー
	 */
	private Future<Boolean> execForEachService2(SystemContext systemContext, EntryBase serviceEntry)
	throws IOException, TaggingException {
		String serviceName = TaggingServiceUtil.getServiceNameFromServiceUri(serviceEntry.getMyUri());
		CheckRetryBdbqCallable callable = new CheckRetryBdbqCallable(serviceName);
		return (Future<Boolean>)TaskQueueUtil.addTask(
				callable, 0, systemContext.getAuth(), systemContext.getRequestInfo(), 
				systemContext.getConnectionInfo());
	}

	/**
	 * バッチジョブサーバへリクエスト
	 */
	void request(String serviceName) throws IOException {
		Requester requester = new Requester();
		String urlStr = getUrl();
		String method = REQUEST_METHOD;
		Map<String, String> reqHeader = new HashMap<>();
		reqHeader.put(Constants.HEADER_SERVICENAME, serviceName);
		
		int timeoutMillis = BDBRequesterUtil.getBDBRequestTimeoutMillis();
		int numRetries = BDBRequesterUtil.getBDBRequestRetryCount();
		int waitMillis = BDBRequesterUtil.getBDBRequestRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				// リクエスト
				HttpURLConnection http = requester.prepare(urlStr, method, 
						reqHeader, timeoutMillis);

				int status = http.getResponseCode();

				if (logger.isTraceEnabled()) {
					StringBuilder sb = new StringBuilder();
					sb.append("[request] serviceName=");
					sb.append(serviceName);
					sb.append(" status=");
					sb.append(status);
					logger.debug(sb.toString());
				}

			} catch (IOException e) {
				if (logger.isDebugEnabled()) {
					StringBuilder sb = new StringBuilder();
					sb.append("[request] serviceName=");
					sb.append(serviceName);
					sb.append(" ");
					sb.append(e.getClass().getName());
					sb.append(" ");
					sb.append(e.getMessage());
					sb.append(" [request info] ");
					sb.append(method);
					sb.append(" ");
					sb.append(urlStr);
					logger.debug(sb.toString());
				}
				// リトライ判定、入力エラー判定
				BDBClientUtil.convertError(e, method, null);
				if (r >= numRetries) {
					// リトライ対象だがリトライ回数を超えた場合
					throw e;
				}
				if (logger.isInfoEnabled()) {
					StringBuilder sb = new StringBuilder();
					sb.append("[request] serviceName=");
					sb.append(serviceName);
					sb.append(" ");
					sb.append("[request info] ");
					sb.append(method);
					sb.append(" ");
					sb.append(urlStr);
					sb.append(" ");
					sb.append(BDBClientUtil.getRetryLog(e, r));
					logger.info(sb.toString());
				}
				BDBClientUtil.sleep(waitMillis);
			}
		}
	}
	
	/**
	 * バッチジョブURLを取得.
	 * @return バッチジョブURL
	 */
	private String getUrl() {
		String urlBatchjob = TaggingEnvUtil.getSystemProp(URL_BATCHJOB, null);
		if (StringUtils.isBlank(urlBatchjob)) {
			throw new IllegalStateException("No BatchJob URL setting.");
		}
		return UrlUtil.addParam(urlBatchjob, RequestParam.PARAM_CHECK_MQ, null);
	}

}
