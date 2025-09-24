package jp.reflexworks.taggingservice.bigquery;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.cloud.RetryOption;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.Job;
import com.google.cloud.bigquery.JobStatus;
import com.google.cloud.bigquery.TableResult;
import com.google.cloud.bigquery.BigQuery.QueryResultsOption;

/**
 * BigQuery ジョブオブジェクト.
 */
public class BigQueryJob {
	
	/** アクセスログのプリフィックス */
	private static final String LOGGER_PREFIX = "[Job] ";

	/** ジョブオブジェクト */
	private Job job;
	
	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param job ジョブオブジェクト
	 */
	public BigQueryJob(Job job) {
		this.job = job;
	}
	
	/**
	 * 実行完了まで待つ.
	 * @param waitOptions オプション
	 * @return ジョブオブジェクト
	 */
	public BigQueryJob waitFor(RetryOption... waitOptions) 
	throws InterruptedException, ReflexBigQueryException {
		if (job == null) {
			return null;
		}
		
		String command = LOGGER_PREFIX + "waitFor";
		long startTime = 0;
		try {
			if (BigQueryUtil.isEnableAccessLog()) {
				logger.debug(BigQueryUtil.getStartLog(command, null, null));
				startTime = new Date().getTime();
			}
			Job ret = job.waitFor(waitOptions);
			if (BigQueryUtil.isEnableAccessLog()) {
				logger.debug(BigQueryUtil.getEndLog(command, null, null, startTime));
				startTime = new Date().getTime();
			}
			return new BigQueryJob(ret);
			
		} catch (BigQueryException e) {
			throw new ReflexBigQueryException(e);
		}
	}
	
	/**
	 * ジョブステータスを取得.
	 * @return ジョブステータス
	 */
	public JobStatus getStatus() 
	throws ReflexBigQueryException {
		if (job == null) {
			return null;
		}
		
		String command = LOGGER_PREFIX + "getStatus";
		long startTime = 0;
		try {
			if (BigQueryUtil.isEnableAccessLog()) {
				logger.debug(BigQueryUtil.getStartLog(command, null, null));
				startTime = new Date().getTime();
			}
			JobStatus ret = job.getStatus();
			if (BigQueryUtil.isEnableAccessLog()) {
				logger.debug(BigQueryUtil.getEndLog(command, null, null, startTime));
				startTime = new Date().getTime();
			}
			return ret;
			
		} catch (BigQueryException e) {
			throw new ReflexBigQueryException(e);
		}
	}
	
	/**
	 * クエリ結果を取得.
	 * @param options オプション
	 * @return クエリ結果
	 */
	public TableResult getQueryResults(QueryResultsOption... options) 
	throws InterruptedException, ReflexBigQueryException {
		if (job == null) {
			return null;
		}
		
		String command = LOGGER_PREFIX + "getQueryResults";
		long startTime = 0;
		try {
			if (BigQueryUtil.isEnableAccessLog()) {
				logger.debug(BigQueryUtil.getStartLog(command, null, null));
				startTime = new Date().getTime();
			}
			TableResult ret = job.getQueryResults(options);
			if (BigQueryUtil.isEnableAccessLog()) {
				logger.debug(BigQueryUtil.getEndLog(command, null, null, startTime));
				startTime = new Date().getTime();
			}
			return ret;
			
		} catch (BigQueryException e) {
			throw new ReflexBigQueryException(e);
		}
	}

}
