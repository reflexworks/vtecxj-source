package jp.reflexworks.taggingservice.bigquery;

import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.cloud.bigquery.BigQuery.TableOption;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.InsertAllRequest.RowToInsert;
import com.google.cloud.bigquery.InsertAllResponse;
import com.google.cloud.bigquery.Table;
import com.google.cloud.bigquery.TableDefinition;

/**
 * BigQuery テーブルオブジェクト.
 */
public class BigQueryTable {
	
	/** アクセスログのプリフィックス */
	private static final String LOGGER_PREFIX = "[Table] ";

	/** テーブルオブジェクト */
	private Table table;
	
	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param table テーブルオブジェクト
	 */
	public BigQueryTable(Table table) {
		this.table = table;
	}
	
	/**
	 * 登録.
	 * @param rows 行情報
	 * @return 登録結果
	 */
	public InsertAllResponse insert(List<RowToInsert> rows) 
	throws ReflexBigQueryException {
		String command = LOGGER_PREFIX + "insert";
		String datasetId = getDatasetId();
		String tableId = getTableId();
		long startTime = 0;
		try {
			if (BigQueryUtil.isEnableAccessLog()) {
				logger.debug(BigQueryUtil.getStartLog(command, datasetId, tableId));
				startTime = new Date().getTime();
			}
			InsertAllResponse ret = table.insert(rows);
			if (BigQueryUtil.isEnableAccessLog()) {
				logger.debug(BigQueryUtil.getEndLog(command, datasetId, tableId, startTime));
				startTime = new Date().getTime();
			}
			return ret;
			
		} catch (BigQueryException e) {
			throw new ReflexBigQueryException(e);
		}
	}
	
	/**
	 * このテーブルの情報でテーブルの情報を更新します.
	 * データセットとテーブルのユーザー定義 ID は変更できません。
	 * 新しい Table オブジェクトが返されます。
	 * @param options 
	 * @return テーブル
	 */
	public BigQueryTable update(TableOption... options) 
	throws ReflexBigQueryException {
		String command = LOGGER_PREFIX + "upate";
		String datasetId = getDatasetId();
		String tableId = getTableId();
		long startTime = 0;
		try {
			if (BigQueryUtil.isEnableAccessLog()) {
				logger.debug(BigQueryUtil.getStartLog(command, datasetId, tableId));
				startTime = new Date().getTime();
			}
			Table updTable = table.update(options);
			if (BigQueryUtil.isEnableAccessLog()) {
				logger.debug(BigQueryUtil.getEndLog(command, datasetId, tableId, startTime));
				startTime = new Date().getTime();
			}
			return new BigQueryTable(updTable);
		} catch (BigQueryException e) {
			throw new ReflexBigQueryException(e);
		}
	}
	
	/**
	 * データセット名を取得.
	 * @return データセット名
	 */
	public String getDatasetId() {
		return table.getTableId().getDataset();
	}
	
	/**
	 * テーブル名を取得.
	 * @return テーブル名
	 */
	public String getTableId() {
		return table.getTableId().getTable();
	}
	
	/**
	 * テーブル定義を取得.
	 * @return テーブル定義
	 */
	public TableDefinition getTableDefinition() {
		return table.getDefinition();
	}
	
	/**
	 * Builderを返却
	 * @return Builder
	 */
	public Builder toBuilder() {
		return new Builder(table.toBuilder());
	}
	
	/**
	 * Table Builderクラス
	 */
	public static class Builder {

		/** Builderオブジェクト */
		private Table.Builder builder;
		
		/**
		 * コンストラクタ
		 * @param builder Builder
		 */
		public Builder(Table.Builder builder) {
			this.builder = builder;
		}
		
		/**
		 * テーブル定義を設定.
		 * @param definition テーブル定義
		 * @return Builder
		 */
		public Builder setDefinition(TableDefinition definition) {
			builder.setDefinition(definition);
			return this;
		}
		
		/**
		 * テーブルオブジェクトを生成.
		 * @return Table
		 */
		public BigQueryTable build() {
			return new BigQueryTable(builder.build());
		}
		
	}

}
