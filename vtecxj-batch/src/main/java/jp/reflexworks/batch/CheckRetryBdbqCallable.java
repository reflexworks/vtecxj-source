package jp.reflexworks.batch;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.servlet.util.UrlUtil;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.bdbclient.BDBClientUtil;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.requester.BDBRequesterUtil;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.sourceforge.reflex.util.Requester;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * BDB+BigQuery登録(BDBQ)リトライチェック処理のリクエスト処理.
 * サービスごとに行う処理
 *  ・バッチジョブサーバにリクエストする
 */
public class CheckRetryBdbqCallable extends ReflexCallable<Boolean> {

	/** サービス名 */
	private String serviceName;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param serviceName サービス名
	 */
	public CheckRetryBdbqCallable(String serviceName) {
		this.serviceName = serviceName;
	}

	/**
	 * バッチジョブサーバにリクエストする処理.
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		if (logger.isTraceEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[CheckRetryBdbqCallable] start. serviceName=" + serviceName);
		}
		
		request(serviceName);

		return true;
	}

	/**
	 * バッチジョブサーバへリクエスト
	 */
	void request(String serviceName) throws IOException {
		Requester requester = new Requester();
		String urlStr = getUrl();
		String method = CheckMessageQueueBlogic.REQUEST_METHOD;
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
		String urlBatchjob = TaggingEnvUtil.getSystemProp(CheckMessageQueueBlogic.URL_BATCHJOB, null);
		if (StringUtils.isBlank(urlBatchjob)) {
			throw new IllegalStateException("No BatchJob URL setting.");
		}
		return UrlUtil.addParam(urlBatchjob, RequestParam.PARAM_CHECK_BDBQ, null);
	}

}
