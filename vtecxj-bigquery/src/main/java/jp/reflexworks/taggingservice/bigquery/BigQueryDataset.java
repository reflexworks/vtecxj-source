package jp.reflexworks.taggingservice.bigquery;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.gax.paging.Page;
import com.google.cloud.bigquery.BigQuery.TableListOption;
import com.google.cloud.bigquery.BigQuery.TableOption;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.Dataset;
import com.google.cloud.bigquery.Table;
import com.google.cloud.bigquery.TableDefinition;

/**
 * データセットラッパークラス.
 */
public class BigQueryDataset {
	
	/** アクセスログのプリフィックス */
	private static final String LOGGER_PREFIX = "[Dataset] ";
	
	/** データセット */
	private Dataset dataset;
	
	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());
	
	/**
	 * コンストラクタ.
	 * @param dataset データセット
	 */
	public BigQueryDataset(Dataset dataset) {
		this.dataset = dataset;
	}
	
	/**
	 * テーブルオブジェクトを取得.
	 * @param tableId テーブル名
	 * @param options オプション
	 * @return テーブルオブジェクト
	 */
	public BigQueryTable get(String tableId, TableOption... options) 
	throws ReflexBigQueryException {
		String command = LOGGER_PREFIX + "get";
		String datasetId = getDatasetId();
		long startTime = 0;
		try {
			if (BigQueryUtil.isEnableAccessLog()) {
				logger.debug(BigQueryUtil.getStartLog(command, datasetId, tableId));
				startTime = new Date().getTime();
			}
			Table table = dataset.get(tableId, options);
			if (BigQueryUtil.isEnableAccessLog()) {
				logger.debug(BigQueryUtil.getEndLog(command, datasetId, tableId, startTime));
				startTime = new Date().getTime();
			}
			if (table == null) {
				return null;
			}
			return new BigQueryTable(table);
			
		} catch (BigQueryException e) {
			throw new ReflexBigQueryException(e);
		}
	}
	
	/**
	 * テーブル作成.
	 * @param tableId テーブル名
	 * @param tableDefinition テーブル定義
	 * @param options オプション
	 * @return テーブルオブジェクト
	 */
	public BigQueryTable create(String tableId, TableDefinition tableDefinition,
			TableOption... options) 
	throws ReflexBigQueryException {
		String command = LOGGER_PREFIX + "create";
		String datasetId = getDatasetId();
		long startTime = 0;
		try {
			if (BigQueryUtil.isEnableAccessLog()) {
				logger.debug(BigQueryUtil.getStartLog(command, datasetId, tableId));
				startTime = new Date().getTime();
			}
			Table table = dataset.create(tableId, tableDefinition, options);
			if (BigQueryUtil.isEnableAccessLog()) {
				logger.debug(BigQueryUtil.getEndLog(command, datasetId, tableId, startTime));
				startTime = new Date().getTime();
			}
			if (table == null) {
				return null;
			}
			return new BigQueryTable(table);
			
		} catch (BigQueryException e) {
			throw new ReflexBigQueryException(e);
		}
	}
	
	/**
	 * データセット名を取得.
	 * @return データセット名
	 */
	public String getDatasetId() {
		return BigQueryUtil.getDatasetId(dataset.getDatasetId());
	}
	
	/**
	 * テーブルオブジェクトを取得.
	 * @param tableId テーブル名
	 * @param options オプション
	 * @return テーブルオブジェクト
	 */
	public List<BigQueryTable> list(TableListOption... options) 
	throws ReflexBigQueryException {
		String command = LOGGER_PREFIX + "list";
		String datasetId = getDatasetId();
		long startTime = 0;
		try {
			if (BigQueryUtil.isEnableAccessLog()) {
				logger.debug(BigQueryUtil.getStartLog(command, datasetId));
				startTime = new Date().getTime();
			}
			Page<Table> tablePage = dataset.list(options);
			if (BigQueryUtil.isEnableAccessLog()) {
				logger.debug(BigQueryUtil.getEndLog(command, datasetId, startTime));
			}
			if (tablePage == null) {
				return null;
			}
			List<BigQueryTable> tables = new ArrayList<BigQueryTable>();
			for (Table table : tablePage.iterateAll()) {
				tables.add(new BigQueryTable(table));
			}
			return tables;
			
		} catch (BigQueryException e) {
			throw new ReflexBigQueryException(e);
		}
	}

}
