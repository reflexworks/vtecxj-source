package jp.reflexworks.taggingservice.batch.requester;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.batch.BatchJobConst;
import jp.reflexworks.servlet.HttpStatus;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.blogic.ServiceBlogic;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.JobManager;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.Requester;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * ジョブ実行管理プラグイン バッチジョブ実行サーバ(runner)実装クラス.
 * <p>
 * 常駐するバッチジョブ実行サーバ(vtecxbatchjob-runner)へ、ジョブ実行リクエストをPOSTする。
 * 実行サーバは非同期処理のため、ステータス202が返ればジョブは受け付けられており、
 * 実行結果は実行サーバからの終了通知({@link jp.reflexworks.batch.BatchJobResultBlogic})で受信する。
 * </p>
 */
public class JobManagerDefault implements JobManager {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/** 文字コード. */
	private static final String ENCODING = "UTF-8";

	@Override
	public void init() {
		// Do nothing. (リクエストは都度Requesterで実行)
	}

	@Override
	public void close() {
		// Do nothing.
	}

	/**
	 * 非同期実行.
	 * runJobの戻り後もジョブは完了していない(ステータスはrunningのまま)。
	 * @return true
	 */
	@Override
	public boolean isAsyncJob() {
		return true;
	}

	/**
	 * バッチジョブの実行.
	 * バッチジョブ実行サーバへPOSTリクエストを行う。
	 * @param jobName ジョブ名(サーバサイドJS名)
	 * @param batchJobTimeEntry バッチジョブ管理テーブル
	 * @param reflexContext ReflexContext
	 * @return 完了済みのダミーFuture (完了検知は終了通知で行う)
	 */
	@Override
	public Future runJob(String jobName, EntryBase batchJobTimeEntry,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();

		String runnerUrl = getRunnerUrl();
		if (StringUtils.isBlank(runnerUrl)) {
			throw new IllegalStateException("Batchjob runner url setting is required. (" +
					BatchJobConst.PROP_BATCHJOB_RUNNER_URL + ")");
		}
		String webhookUrl = getResponseUrl();
		if (StringUtils.isBlank(webhookUrl)) {
			throw new IllegalStateException("Batchjob response url setting is required. (" +
					BatchJobConst.PROP_BATCHJOB_RESPONSE_URL + ")");
		}

		ServiceBlogic serviceBlogic = new ServiceBlogic();
		String apserverUrl = serviceBlogic.getRedirectUrlContextPath(serviceName,
				requestInfo, connectionInfo);
		String apiKey = serviceBlogic.getAPIKey(serviceName, requestInfo, connectionInfo);
		String accesstoken = reflexContext.getAccessToken();
		String jobId = getJobId(batchJobTimeEntry);

		JSONObject body = new JSONObject();
		body.put(BatchJobConst.JSON_URL, apserverUrl);
		body.put(BatchJobConst.JSON_SERVICE_NAME, serviceName);
		body.put(BatchJobConst.JSON_APIKEY, apiKey);
		body.put(BatchJobConst.JSON_ACCESSTOKEN, accesstoken);
		body.put(BatchJobConst.JSON_SCRIPT_NAME, jobName);
		body.put(BatchJobConst.JSON_JOB_ID, jobId);
		body.put(BatchJobConst.JSON_WEBHOOK_URL, webhookUrl);
		byte[] payload = body.toString().getBytes(ENCODING);

		Map<String, String> reqHeader = new HashMap<>();
		reqHeader.put("Content-Type", "application/json; charset=" + ENCODING);

		if (logger.isInfoEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append("[runJob] start. serviceName=");
			sb.append(serviceName);
			sb.append(", jobName=");
			sb.append(jobName);
			sb.append(", jobId=");
			sb.append(jobId);
			sb.append(", runnerUrl=");
			sb.append(runnerUrl);
			logger.info(sb.toString());
		}

		int timeoutMillis = getRequestTimeoutMillis();
		int numRetries = getRetryCount();
		int waitMillis = getRetryWaitmillis();
		Requester requester = new Requester();
		IOException lastError = null;
		for (int r = 0; r <= numRetries; r++) {
			try {
				HttpURLConnection http = requester.request(runnerUrl,
						BatchJobConst.METHOD_BATCHJOB_RUNNER, payload, reqHeader, timeoutMillis);
				int status = http.getResponseCode();
				if (status == HttpStatus.SC_ACCEPTED) {
					// 受付成功。ステータスはrunningのまま。結果は終了通知で受信。
					return CompletableFuture.completedFuture(Boolean.TRUE);
				}
				// 202以外はエラー
				String respStr = requester.getResponseString(http);
				lastError = new IOException("Batchjob runner request failed. status=" + status +
						(StringUtils.isBlank(respStr) ? "" : ", message=" + respStr));
			} catch (IOException e) {
				lastError = e;
			}
			if (r < numRetries) {
				if (logger.isInfoEnabled()) {
					logger.info("[runJob] retry " + (r + 1) + "/" + numRetries + ". " +
							lastError.getMessage());
				}
				sleep(waitMillis + r * 100L);
			}
		}
		throw lastError;
	}

	/**
	 * ジョブの情報をバッチジョブ管理テーブルに設定.
	 * runnerは同期で実行IDを返さず、ジョブ実行IDはsubtitleに格納済みのため何もしない。
	 */
	@Override
	public void setJobInfo(Future future, EntryBase entry)
	throws IOException, TaggingException {
		// Do nothing.
	}

	/**
	 * ジョブ実行IDを取得.
	 * バッチジョブ管理テーブルのsubtitle、無ければキー末尾(ジョブ実行時刻)。
	 * @param batchJobTimeEntry バッチジョブ管理テーブル
	 * @return ジョブ実行ID
	 */
	private String getJobId(EntryBase batchJobTimeEntry) {
		if (batchJobTimeEntry != null) {
			if (!StringUtils.isBlank(batchJobTimeEntry.subtitle)) {
				return batchJobTimeEntry.subtitle;
			}
			String myUri = batchJobTimeEntry.getMyUri();
			if (!StringUtils.isBlank(myUri)) {
				return TaggingEntryUtil.getSelfidUri(myUri);
			}
		}
		return null;
	}

	/**
	 * スリープ処理.
	 * @param waitMillis スリープ時間(ミリ秒)
	 */
	private void sleep(long waitMillis) {
		try {
			Thread.sleep(waitMillis);
		} catch (InterruptedException e) {
			logger.warn("[sleep] InterruptedException: " + e.getMessage());
		}
	}

	/**
	 * バッチジョブ実行サーバのジョブ実行リクエストURLを取得.
	 * @return バッチジョブ実行サーバのジョブ実行リクエストURL
	 */
	private String getRunnerUrl() {
		return TaggingEnvUtil.getSystemProp(BatchJobConst.PROP_BATCHJOB_RUNNER_URL, null);
	}

	/**
	 * バッチジョブ実行サーバへ渡す終了通知リクエストURLを取得.
	 * @return 終了通知リクエストURL
	 */
	private String getResponseUrl() {
		return TaggingEnvUtil.getSystemProp(BatchJobConst.PROP_BATCHJOB_RESPONSE_URL, null);
	}

	/**
	 * バッチジョブ実行サーバへのリクエストタイムアウト時間(ミリ秒)を取得.
	 * @return リクエストタイムアウト時間(ミリ秒)
	 */
	private int getRequestTimeoutMillis() {
		return TaggingEnvUtil.getSystemPropInt(
				BatchJobConst.PROP_BATCHJOB_RUNNER_REQUEST_TIMEOUT_MILLIS,
				BatchJobConst.BATCHJOB_RUNNER_REQUEST_TIMEOUT_MILLIS_DEFAULT);
	}

	/**
	 * バッチジョブ実行サーバへのリクエスト失敗時リトライ総数を取得.
	 * @return リトライ総数
	 */
	private int getRetryCount() {
		return TaggingEnvUtil.getSystemPropInt(BatchJobConst.PROP_BATCHJOB_RUNNER_RETRY_COUNT,
				BatchJobConst.BATCHJOB_RUNNER_RETRY_COUNT_DEFAULT);
	}

	/**
	 * バッチジョブ実行サーバへのリクエスト失敗時リトライ時のスリープ時間(ミリ秒)を取得.
	 * @return スリープ時間(ミリ秒)
	 */
	private int getRetryWaitmillis() {
		return TaggingEnvUtil.getSystemPropInt(BatchJobConst.PROP_BATCHJOB_RUNNER_RETRY_WAITMILLIS,
				BatchJobConst.BATCHJOB_RUNNER_RETRY_WAITMILLIS_DEFAULT);
	}

}
