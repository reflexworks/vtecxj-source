package jp.reflexworks.taggingservice.bigquery;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQuery.DatasetOption;
import com.google.cloud.bigquery.BigQuery.JobOption;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.Dataset;
import com.google.cloud.bigquery.DatasetInfo;
import com.google.cloud.bigquery.Job;
import com.google.cloud.bigquery.JobInfo;

import jp.reflexworks.taggingservice.conn.ReflexConnection;

/**
 * BigQueryコネクション.
 */
public class BigQueryConnection implements ReflexConnection<BigQuery> {
	
	/** BigQueryコネクション */
	private BigQuery bigQuery;
	
	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());
	
	/**
	 * コンストラクタ.
	 * @param bigQuery BigQuery
	 */
	public BigQueryConnection(BigQuery bigQuery) {
		this.bigQuery = bigQuery;
	}
	
	/**
	 * コネクションを取得.
	 * @return BigQueryコネクション
	 */
	public BigQuery getConnection() {
		return bigQuery;
	}
	
	/**
	 * クローズ処理.
	 */
	public void close() {
		// BigQueryオブジェクトにcloseメソッドはない。
		// Do nothing.
	}

	/**
	 * データセットを取得.
	 * @param datasetId データセット名.
	 * @param options オプション
	 * @return データセットオブジェクト
	 */
	public BigQueryDataset getDataset(String datasetId, DatasetOption... options) 
	throws ReflexBigQueryException {
		String command = "getDataset";
		long startTime = 0;
		try {
			if (BigQueryUtil.isEnableAccessLog()) {
				logger.debug(BigQueryUtil.getStartLog(command, datasetId, null));
				startTime = new Date().getTime();
			}
			Dataset dataset = bigQuery.getDataset(datasetId, options);
			if (BigQueryUtil.isEnableAccessLog()) {
				logger.debug(BigQueryUtil.getEndLog(command, datasetId, null, startTime));
				startTime = new Date().getTime();
			}
			if (dataset == null) {
				return null;
			}
			return new BigQueryDataset(dataset);
			
		} catch (BigQueryException e) {
			throw new ReflexBigQueryException(e);
		}
	}
	
	/**
	 * データセットの生成.
	 * @param datasetInfo データセット情報
	 * @param options オプション
	 * @return データセットオブジェクト
	 */
	public BigQueryDataset create(DatasetInfo datasetInfo, DatasetOption... options) 
	throws ReflexBigQueryException {
		String command = "create";
		String datasetId = BigQueryUtil.getDatasetId(datasetInfo.getDatasetId());
		long startTime = 0;
		try {
			if (BigQueryUtil.isEnableAccessLog()) {
				logger.debug(BigQueryUtil.getStartLog(command, datasetId, null));
				startTime = new Date().getTime();
			}
			Dataset dataset = bigQuery.create(datasetInfo, options);
			if (BigQueryUtil.isEnableAccessLog()) {
				logger.debug(BigQueryUtil.getEndLog(command, datasetId, null, startTime));
				startTime = new Date().getTime();
			}
			if (dataset == null) {
				return null;
			}
			return new BigQueryDataset(dataset);
			
		} catch (BigQueryException e) {
			throw new ReflexBigQueryException(e);
		}
	}
	
	/**
	 * ジョブの生成.
	 * @param jobInfo ジョブ情報
	 * @param options オプション
	 * @return ジョブオブジェクト
	 */
	public BigQueryJob create(JobInfo jobInfo, JobOption... options) 
	throws ReflexBigQueryException {
		String command = "create";
		long startTime = 0;
		try {
			if (BigQueryUtil.isEnableAccessLog()) {
				logger.debug(BigQueryUtil.getStartLog(command, null, null));	// 
				startTime = new Date().getTime();
			}
			Job job = bigQuery.create(jobInfo, options);
			if (BigQueryUtil.isEnableAccessLog()) {
				logger.debug(BigQueryUtil.getEndLog(command, null, null, startTime));
				startTime = new Date().getTime();
			}
			if (job == null) {
				return null;
			}
			return new BigQueryJob(job);
			
		} catch (BigQueryException e) {
			throw new ReflexBigQueryException(e);
		}
	}

}
