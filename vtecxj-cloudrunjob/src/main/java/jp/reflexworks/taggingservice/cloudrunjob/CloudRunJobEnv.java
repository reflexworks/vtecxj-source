package jp.reflexworks.taggingservice.cloudrunjob;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.cloud.run.v2.JobsClient;

import jp.reflexworks.taggingservice.plugin.ClosingForShutdown;

/**
 * Cloud Run Job用staticクラス
 */
public class CloudRunJobEnv implements ClosingForShutdown {
	
	/** jobs client */
	private JobsClient jobsClient;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * clientを取得
	 * @return jobs client
	 */
	public JobsClient getJobsClient() {
		return jobsClient;
	}

	/**
	 * clientを設定
	 * @param jobsClient jobs client
	 */
	void setJobsClient(JobsClient jobsClient) {
		this.jobsClient = jobsClient;
	}

	@Override
	public void close() {
		try {
			if (jobsClient != null) {
				jobsClient.close();
			}
		} catch (Throwable e) {
			StringBuilder sb = new StringBuilder();
			sb.append("[close] Error occured. ");
			sb.append(e.getClass().getName());
			sb.append(": ");
			sb.append(e.getMessage());
			logger.warn(sb.toString(), e);
		}
	}

}
