package jp.reflexworks.taggingservice.cloudrunjob;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.ApiException;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.run.v2.EnvVar;
import com.google.cloud.run.v2.JobName;
import com.google.cloud.run.v2.JobsClient;
import com.google.cloud.run.v2.JobsSettings;
import com.google.cloud.run.v2.RunJobRequest;
import com.google.cloud.run.v2.RunJobRequest.Overrides;
import com.google.cloud.run.v2.RunJobRequest.Overrides.ContainerOverride;
import com.google.protobuf.Duration;

import jp.reflexworks.js.JsExec;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexEnv;
import jp.reflexworks.taggingservice.api.ReflexStatic;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.blogic.ServiceBlogic;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.StaticDuplicatedException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.JobManager;
import jp.sourceforge.reflex.util.FileUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * ジョブ実行管理プラグイン　実装クラス
 */
public class CloudRunJobManager implements JobManager {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	@Override
	public void init() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();

		try {
			// JobsClientはsingletonにする
			byte[] secret = null;
			String secretFilename = env.getSystemProp(
					CloudRunJobConst.CLOUDRUNJOB_FILE_SECRET);
			String jsonPath = FileUtil.getResourceFilename(secretFilename);
			if (!StringUtils.isBlank(jsonPath)) {
				secret = FileUtil.readFile(jsonPath);
			}
			
			JobsClient jobsClient = createJobsClient(secret);
			CloudRunJobEnv cloudRunJobEnv = new CloudRunJobEnv();
			cloudRunJobEnv.setJobsClient(jobsClient);
			
			ReflexStatic.setStatic(CloudRunJobConst.STATIC_NAME_CLOUDRUNJOB, cloudRunJobEnv);

		} catch (StaticDuplicatedException e) {
			// Do nothing.
			if (logger.isInfoEnabled()) {
				logger.info("[init] StaticDuplicatedException: " + e.getMessage());
			}

		} catch (IOException e) {
			StringBuilder sb = new StringBuilder();
			sb.append("[init] Error occured. ");
			sb.append(e.getClass().getName());
			sb.append(": ");
			sb.append(e.getMessage());
			logger.warn(sb.toString(), e);
		}
	}

	@Override
	public void close() {
		// JobsClient の close は CloudRunJobEnv 内で行うためここでは何もしない。
	}

	/**
	 * バッチジョブの実行.
	 * Cloud Run Job を実行する。
	 * @param scriptName サーバサイドJS名
	 * @param reflexContext ReflexContext
	 */
	@Override
	public Future runJob(String scriptName, ReflexContext reflexContext) 
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();

		try {
			String projectId = getGcpProjectId();
			String region = getGcpRegion();
			String jobName = getCloudRunJobName();
			String name = JobName.of(projectId, region, jobName).toString();
			int timeoutSec = getTimeoutSec(serviceName, requestInfo, connectionInfo);
			String apserverUrl = getApserverUrl(serviceName, requestInfo, connectionInfo);
			
			// APIKey、アクセストークンの取得
			ServiceBlogic serviceBlogic = new ServiceBlogic();
			String apiKey = serviceBlogic.getAPIKey(serviceName, requestInfo, connectionInfo);
			String accesstoken = reflexContext.getAccessToken();
			
			// jobsClient
			JobsClient jobsClient = getJobsClient();
			
			if (logger.isInfoEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append("[runJob] start. serviceName=");
				sb.append(serviceName);
				sb.append(", scriptName=");
				sb.append(scriptName);
				sb.append(", JobName.of=");
				sb.append(name);
				logger.info(sb.toString());
			}
	
			RunJobRequest request = RunJobRequest.newBuilder()
					.setName(name)
					.setOverrides(
							Overrides.newBuilder()
							.setTaskCount(1)
							.setTimeout(
									Duration.newBuilder()
									.setSeconds(timeoutSec)
									.build()
									)
							.addContainerOverrides(
									ContainerOverride.newBuilder()
									.addEnv(
											EnvVar.newBuilder()
											.setName(CloudRunJobConst.VTECX_URL)
											.setValue(apserverUrl)
											.build()
											)
									.addEnv(
											EnvVar.newBuilder()
											.setName(CloudRunJobConst.VTECX_APIKEY)
											.setValue(apiKey)
											.build()
											)
									.addEnv(
											EnvVar.newBuilder()
											.setName(CloudRunJobConst.ACCESS_TOKEN)
											.setValue(accesstoken)
											.build()
											)
									.addEnv(
											EnvVar.newBuilder()
											.setName(CloudRunJobConst.SCRIPT_NAME)
											.setValue(scriptName)
											.build()
											)
									.build()
									)
							.build()
							)
					.build();
			
			return jobsClient.runJobAsync(request);

		} catch (ApiException e) {
			convertApiException(e);
			// Unreachable code.
			throw e;
		}
	}
	

	/**
	 * Storage接続インタフェースを取得.
	 * @param secret 秘密鍵
	 * @return Storage接続インタフェース
	 */
	private JobsClient createJobsClient(byte[] secret) throws IOException {
		// リトライ回数
		int numRetries = getCreateJobsClientRetryCount();
		int waitMillis = getCreateJobsClientRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				return createJobsClientProc(secret);

			} catch (IOException e) {
				// リトライ判定、入力エラー判定 TODO
				if (r >= numRetries) {
					// リトライ対象だがリトライ回数を超えた場合
					throw e;
				}
				sleep(waitMillis + r * 10);
			}
		}

		throw new IllegalStateException("Unreachable code. (createStorage)");
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
	 * Storage接続インタフェースを取得.
	 * @param secret 秘密鍵
	 * @return Storage接続インタフェース
	 */
	private JobsClient createJobsClientProc(byte[] secret) throws IOException {
		GoogleCredentials credentials = null;
		if (secret != null) {
			ByteArrayInputStream bin = new ByteArrayInputStream(secret);
			credentials = GoogleCredentials.fromStream(bin);
		}

		JobsSettings.Builder builder = JobsSettings.newBuilder();
		if (credentials != null) {
			builder = builder.setCredentialsProvider(FixedCredentialsProvider.create(credentials));
		}
		
		JobsSettings settings = builder.build();
		return JobsClient.create(settings);
	}
	
	/**
	 * JobsClientオブジェクトを取得
	 * singletonオブジェクト
	 * @return JobsClientオブジェクト
	 */
	private JobsClient getJobsClient() {
		CloudRunJobEnv cloudRunJobEnv = (CloudRunJobEnv)ReflexStatic.getStatic(
				CloudRunJobConst.STATIC_NAME_CLOUDRUNJOB);
		if (cloudRunJobEnv != null) {
			return cloudRunJobEnv.getJobsClient();
		}
		return null;
	}

	/**
	 * JobsClient生成失敗時リトライ総数を取得.
	 * @return JobsClient生成失敗時リトライ総数
	 */
	private int getCreateJobsClientRetryCount() {
		return TaggingEnvUtil.getSystemPropInt(CloudRunJobConst.CLOUDRUNJOB_CREATEJOBSCLIENT_RETRY_COUNT,
				CloudRunJobConst.CLOUDRUNJOB_CREATEJOBSCLIENT_RETRY_COUNT_DEFAULT);
	}

	/**
	 * JobsClient生成失敗時リトライ時のスリープ時間(ミリ秒)を取得.
	 * @return JobsClient生成失敗時リトライ時のスリープ時間(ミリ秒)
	 */
	private int getCreateJobsClientRetryWaitmillis() {
		return TaggingEnvUtil.getSystemPropInt(CloudRunJobConst.CLOUDRUNJOB_CREATEJOBSCLIENT_RETRY_WAITMILLIS,
				CloudRunJobConst.CLOUDRUNJOB_CREATEJOBSCLIENT_RETRY_WAITMILLIS_DEFAULT);
	}

	/**
	 * Google Cloud プロジェクトIDを取得.
	 * @return Google Cloud プロジェクトID
	 */
	private String getGcpProjectId() {
		return TaggingEnvUtil.getSystemProp(CloudRunJobConst.GCP_PROJECT_ID, null);
	}

	/**
	 * Google Cloud Run Job 実行リージョンを取得.
	 * @return Google Cloud Run Job 実行リージョン
	 */
	private String getGcpRegion() {
		return TaggingEnvUtil.getSystemProp(CloudRunJobConst.GCP_REGION, null);
	}

	/**
	 * Google Cloud Run Job 名を取得.
	 * @return Google Cloud Run Job 名
	 */
	private String getCloudRunJobName() {
		return TaggingEnvUtil.getSystemProp(CloudRunJobConst.CLOUDRUNJOB_NAME, null);
	}

	/**
	 * サーバサイドJS実行タイムアウト時間(秒)を取得.
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return サーバサイドJS実行タイムアウト時間(秒)
	 */
	private int getTimeoutSec(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo) {
		return JsExec.getTimeout(serviceName, requestInfo, connectionInfo);
	}


	/**
	 * 各サービスのAPサーバHost名+ContextPathを取得.
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 各サービスのAPサーバHost名+ContextPath
	 */
	private String getApserverUrl(String serviceName, RequestInfo requestInfo, 
			ConnectionInfo connectionInfo) 
	throws IOException, TaggingException {
		ServiceBlogic serviceBlogic = new ServiceBlogic();
		return serviceBlogic.getRedirectUrlContextPath(serviceName, requestInfo, connectionInfo);
	}

	/**
	 * ApiException をcatchできる型に変換する.
	 * @param e ApiException
	 * @throws IOException または TaggingException
	 */
	private void convertApiException(ApiException e) 
	throws IOException, TaggingException {
		// TODO
		throw new IOException(e);
	}
	
}

