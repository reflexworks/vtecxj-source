package jp.reflexworks.taggingservice.bigquery;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.cloud.bigquery.InsertAllRequest.RowToInsert;

import jp.reflexworks.atom.mapper.FeedTemplateMapper.Meta;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.RetryUtil;

/**
 * BigQuery登録非同期処理.
 */
public class BigQueryPostCallable extends ReflexCallable<Boolean> {

	/** BigQuery接続情報 */
	private BigQueryInfo bigQueryInfo;
	/** RowToInsert map (キー:テーブル、値:登録データリスト) */
	private Map<String, List<RowToInsert>> rowToInsertsMap;
	/** metalist */
	private List<Meta> metalist;
	/** 1行分の項目名とデータ */
	private Map<String, Object> rowMap;
	/**
	 * エンティティの第一階層と異なる名前をテーブル名にする場合に指定.
	 *   キー:エンティティの第一階層名、値:BigQuery登録テーブル名
	 */
	private Map<String, String> tableNames;
	/** フィールド名に接頭辞(第一階層)を付加する場合true */
	private boolean addPrefixToFieldname = false;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param bigQueryInfo BigQuery接続情報
	 * @param rowToInsertsMap 登録データリスト格納Map
	 * @param metalist Metalist
	 * @param tableNames テーブルとなるフィールド名とテーブル名のMap
	 * @param addPrefixToFieldname フィールド名に接頭辞(第一階層)を付加する場合true
	 */
	public BigQueryPostCallable(BigQueryInfo bigQueryInfo,
			Map<String, List<RowToInsert>> rowToInsertsMap, List<Meta> metalist,
			Map<String, String> tableNames, boolean addPrefixToFieldname) {
		this.bigQueryInfo = bigQueryInfo;
		this.rowToInsertsMap = rowToInsertsMap;
		this.metalist = metalist;
		this.tableNames = tableNames;
		this.addPrefixToFieldname = addPrefixToFieldname;
	}

	/**
	 * コンストラクタ.
	 * ログデータ用。(Feedからの登録でなくMapに項目名と値を設定)
	 * @param bigQueryInfo BigQuery接続情報
	 * @param rowToInsertsMap 登録データリスト格納Map
	 * @param rowMap 1行分の項目名とデータ
	 */
	public BigQueryPostCallable(BigQueryInfo bigQueryInfo,
			Map<String, List<RowToInsert>> rowToInsertsMap, Map<String, Object> rowMap) {
		this.bigQueryInfo = bigQueryInfo;
		this.rowToInsertsMap = rowToInsertsMap;
		this.rowMap = rowMap;
	}

	/**
	 * BigQuery登録.
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) + "[BigQueryPost call] start.");
		}

		// ReflexContextを取得
		ReflexContext reflexContext = (ReflexContext)getReflexContext();
		String serviceName = getServiceName();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();

		ReflexBigQueryManager bigQueryManager = new ReflexBigQueryManager();

		// コネクションを取得
		BigQueryConnection bigQuery = bigQueryManager.getConnection(bigQueryInfo,
				serviceName, requestInfo, connectionInfo);
		// データセットを取得
		BigQueryDataset dataset = bigQueryManager.getDataset(bigQueryInfo, bigQuery,
				serviceName, requestInfo, connectionInfo);
		// テーブルオブジェクトを生成
		Map<String, BigQueryTable> tableMap = new HashMap<String, BigQueryTable>();
		if (metalist != null) {
			for (String tableFieldName : rowToInsertsMap.keySet()) {
				String tableName = getTableName(tableFieldName);
				BigQueryTable table = bigQueryManager.getTable(bigQueryInfo, dataset,
						tableFieldName, tableName, metalist, addPrefixToFieldname, 
						serviceName, requestInfo, connectionInfo);
				tableMap.put(tableFieldName, table);
			}
		} else {
			for (String tableName : rowToInsertsMap.keySet()) {
				BigQueryTable table = bigQueryManager.getTable(bigQueryInfo, dataset,
						tableName, rowMap, serviceName, 
						requestInfo, connectionInfo);
				tableMap.put(tableName, table);
			}
		}

		// 登録
		int numRetries = BigQueryUtil.getBigQueryCallableRetryCount();
		int waitMillis = BigQueryUtil.getBigQueryCallableRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				bigQueryManager.postBqProc(tableMap, rowToInsertsMap,
						requestInfo);
				break;

			} catch (IOException e) {
				if (r >= numRetries) {
					// 更新失敗 (ログエントリーは呼び出し元で書く。)
					throw e;
				}
				boolean isRetry = BigQueryUtil.isRetryError(e, requestInfo);
				if (isRetry) {
					if (logger.isDebugEnabled()) {
						logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[BigQueryPost call] retry: " + r);
					}
					RetryUtil.sleep(waitMillis + r * 10);
				}
			}
		}

		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) + "[BigQueryPost call] end.");
		}
		return true;
	}

	/**
	 * テーブル名を取得.
	 * エンティティの第一階層名に紐づくテーブル名が指定されている場合、指定されたテーブル名を返却.
	 * @param tableFieldName エンティティ第一階層名
	 * @return テーブル名
	 */
	private String getTableName(String tableFieldName) {
		if (tableNames != null && tableNames.containsKey(tableFieldName)) {
			return tableNames.get(tableFieldName);
		}
		return tableFieldName;
	}

}
