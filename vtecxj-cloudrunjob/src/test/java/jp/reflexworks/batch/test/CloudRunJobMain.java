package jp.reflexworks.batch.test;

import java.io.ByteArrayInputStream;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.longrunning.OperationFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.run.v2.EnvVar;
import com.google.cloud.run.v2.Execution;
import com.google.cloud.run.v2.JobName;
import com.google.cloud.run.v2.JobsClient;
import com.google.cloud.run.v2.JobsSettings;
import com.google.cloud.run.v2.RunJobRequest;
import com.google.cloud.run.v2.RunJobRequest.Overrides;
import com.google.cloud.run.v2.RunJobRequest.Overrides.ContainerOverride;
import com.google.protobuf.Duration;

import jp.reflexworks.taggingservice.cloudrunjob.CloudRunJobConst;
import jp.sourceforge.reflex.util.FileUtil;
import jp.sourceforge.reflex.util.StringUtils;

public class CloudRunJobMain {

	/**
	 * Cloud Run Job 実行
	 * @param args
	 *   [0] Google Cloud プロジェクトID
	 *   [1] リージョン
	 *   [2] Cloud Run Job名
	 *   [3] タイムアウト秒
	 *   [4] 引数1:サービス名
	 *   [5] 引数2:APIキー
	 *   [6] 引数3:アクセストークン
	 *   [7] 引数4:スクリプト名
	 *   [8] サービスアカウントJSONファイルパス
	 */
	public static void main(String[] args) {
		try {
			if (args == null) {
				throw new IllegalArgumentException("Arguments are required.");
			}
			if (args.length < 8) {
				throw new IllegalArgumentException("Insufficient arguments.");
			}
			
			String projectId = args[0];
			String region = args[1];
			String jobName = args[2];
			String timeoutSecStr = args[3];
			long timeoutSec = StringUtils.longValue(timeoutSecStr);
			String serviceName = args[4];
			String apiKey = args[5];
			String accesstoken = args[6];
			String scriptName = args[7];
			String credentialJsonPath = null;
			if (args.length > 8) {
				credentialJsonPath = args[8];
			}

			StringBuilder sb = new StringBuilder();
			sb.append("[CloudRunJobMain] start. projectId=");
			sb.append(projectId);
			sb.append(", region=");
			sb.append(region);
			sb.append(", jobName=");
			sb.append(jobName);
			sb.append(", timeoutSec=");
			sb.append(timeoutSec);
			sb.append(", serviceName=");
			sb.append(serviceName);
			sb.append(", apiKey=");
			sb.append(apiKey);
			sb.append(", accesstoken=");
			sb.append(accesstoken);
			sb.append(", scriptName=");
			sb.append(scriptName);
			sb.append(", credentialJsonPath=");
			sb.append(credentialJsonPath);
			System.out.println(sb.toString());
			
			GoogleCredentials credentials = null;
			if (credentialJsonPath != null) {
				byte[] secret = FileUtil.readFile(credentialJsonPath);
				ByteArrayInputStream bin = new ByteArrayInputStream(secret);
				credentials = GoogleCredentials.fromStream(bin);
			}

			JobsSettings.Builder builder = JobsSettings.newBuilder();
			if (credentials != null) {
				builder = builder.setCredentialsProvider(FixedCredentialsProvider.create(credentials));
			}
			
			JobsSettings settings = builder.build();
	
			try (JobsClient jobsClient = JobsClient.create(settings)) {
				String name = JobName.of(projectId, region, jobName).toString();

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
												.setValue(getVtecxUrl(serviceName))
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
				
				OperationFuture<Execution, Execution> future = jobsClient.runJobAsync(request);
	
				// ここでは Job 完了までは待たない
				System.out.println("Cloud Run Job execution requested.");
				System.out.println("operationName=" + future.getName());
	
				// 動作確認で完了まで待ちたい場合だけ、以下を有効化
				Execution execution = future.get();
				System.out.println("executionName=" + execution.getName());
			}
			
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * vte.cx URL を取得
	 * @param serviceName サービス名
	 * @return vte.cx URL
	 */
	private static String getVtecxUrl(String serviceName) {
		StringBuilder sb = new StringBuilder();
		sb.append("https://");
		sb.append(serviceName);
		sb.append(".vte.cx");
		return sb.toString();
	}

}
