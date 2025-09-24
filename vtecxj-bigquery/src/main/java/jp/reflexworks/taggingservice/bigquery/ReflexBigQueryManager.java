package jp.reflexworks.taggingservice.bigquery;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryError;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.DatasetInfo;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldList;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.InsertAllRequest.RowToInsert;
import com.google.cloud.bigquery.InsertAllResponse;
import com.google.cloud.bigquery.JobId;
import com.google.cloud.bigquery.JobInfo;
import com.google.cloud.bigquery.LegacySQLTypeName;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardTableDefinition;
import com.google.cloud.bigquery.TableDefinition;
import com.google.cloud.bigquery.TableResult;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.mapper.FeedTemplateMapper.Meta;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContentInfo;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.reflexworks.taggingservice.exception.NoExistingEntryException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.BigQueryManager;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.taskqueue.TaskQueueUtil;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.RetryUtil;
import jp.sourceforge.reflex.util.FieldMapper;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * Google BigQuery管理クラス.
 */
public class ReflexBigQueryManager implements BigQueryManager {

	/** フィールド名の先頭が_の場合、getterのメソッド名から_を外すかどうか */
	private static final boolean isReflexField = true;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 初期起動時の処理.
	 */
	@Override
	public void init() {
		// Do nothing.
	}

	/**
	 * シャットダウン時の処理.
	 */
	@Override
	public void close() {
		// Do nothing.
	}

	/**
	 * BigQueryへデータ登録.
	 * @param feed Feed
	 * @param tableNames エンティティの第一階層と異なる名前をテーブル名にする場合に指定.
	 *                   キー:エンティティの第一階層名、値:BigQuery登録テーブル名
	 * @param async 非同期の場合true、同期の場合false
	 * @param addPrefixToFieldname フィールド名に接頭辞(第一階層)を付加する場合true
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return Future 同期の場合はnull
	 */
	@Override
	public Future postBq(FeedBase feed, Map<String, String> tableNames, boolean async,
			boolean addPrefixToFieldname, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		String accessLogUri = null;
		if (isEnableAccessLog()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[postBq] start uri = ");
			boolean isFirst = true;
			StringBuilder asb = new StringBuilder();
			for (EntryBase entry : feed.entry) {
				if (isFirst) {
					isFirst = false;
				} else {
					asb.append(", ");
				}
				String uri = entry.getMyUri();
				asb.append(uri);
			}
			accessLogUri = asb.toString();
			sb.append(accessLogUri);
			logger.debug(sb.toString());
		}

		FieldMapper fieldMapper = new FieldMapper(isReflexField);
		List<Meta> metalist = BigQueryUtil.getMetalist(serviceName);

		// 入力チェック、項目抽出
		// キー: URI、値: (キー: Entryの第一階層フィールド名、値: テーブル名(Entryの第一階層オブジェクトか、変換値))
		Map<String, Map<String, Object>> valuesMap =
				new LinkedHashMap<String, Map<String, Object>>();
		// キー: URI、値: ID
		Map<String, String> idMap = new HashMap<>();
		IllegalParameterException tmpE = null;
		int bqCnt = 0;
		for (EntryBase entry : feed.entry) {
			String uri = entry.getMyUri();
			try {
				// 入力チェック、項目抽出
				Map<String, Object> values = getPostField(entry, serviceName, fieldMapper, requestInfo);
				valuesMap.put(uri, values);
				if (!StringUtils.isBlank(entry.id)) {
					idMap.put(uri, entry.id);
				}
				bqCnt++;
			} catch (IllegalParameterException e) {
				if (isEnableAccessLog()) {
					StringBuilder sb = new StringBuilder();
					sb.append(LogUtil.getRequestInfoStr(requestInfo));
					sb.append("[postBq] IllegalParameterException: ");
					sb.append(e.getMessage());
					logger.debug(sb.toString());
				}
				if (tmpE == null) {
					tmpE = e;
				}
			}
		}
		// BigQueryの登録対象が1件もなければエラー
		if (bqCnt == 0) {
			throw tmpE;
		}

		// BigQuery接続情報を取得
		BigQueryInfo bigQueryInfo = getBigQueryInfo(serviceName,
				requestInfo, connectionInfo);

		// コネクションを取得
		BigQueryConnection bigQuery = getConnection(bigQueryInfo, serviceName,
				requestInfo, connectionInfo);
		// データセットを取得
		BigQueryDataset dataset = getDataset(bigQueryInfo, bigQuery, serviceName,
				requestInfo, connectionInfo);
		Map<String, BigQueryTable> tableMap = new HashMap<String, BigQueryTable>();

		// 現在日時
		String nowStr = BigQueryUtil.getNowByMicrosec();

		// テーブルごとの登録情報に分割
		// キー: テーブル名、値: BigQuery登録行リスト
		Map<String, List<RowToInsert>> rowToInsertsMap =
				new LinkedHashMap<String, List<RowToInsert>>();
		for (Map.Entry<String, Map<String, Object>> mapEntry : valuesMap.entrySet()) {
			String uri = mapEntry.getKey();
			String id = idMap.get(uri);
			Map<String, Object> values = mapEntry.getValue();
			for (Map.Entry<String, Object> valuesMapEntry : values.entrySet()) {
				String tableFieldName = valuesMapEntry.getKey();
				Object value = valuesMapEntry.getValue();

				// テーブルを取得
				BigQueryTable table = tableMap.get(tableFieldName);
				if (table == null) {
					String tableName = getTableName(tableFieldName, tableNames);
					table = getTable(bigQueryInfo, dataset, tableFieldName, tableName,
							metalist, addPrefixToFieldname, serviceName, 
							requestInfo, connectionInfo);
					tableMap.put(tableFieldName, table);
				}

				// テーブル定義を元に登録データオブジェクトを作成
				TableDefinition tableDefinition = table.getTableDefinition();
				RowToInsert rowToInsert = createRowToInsert(tableDefinition, tableFieldName,
						uri, value, nowStr, id, fieldMapper, addPrefixToFieldname);

				List<RowToInsert> rowToInserts = rowToInsertsMap.get(tableFieldName);
				if (rowToInserts == null) {
					rowToInserts = new ArrayList<RowToInsert>();
					rowToInsertsMap.put(tableFieldName, rowToInserts);
				}
				rowToInserts.add(rowToInsert);
			}
		}

		// 登録
		if (!async) {
			postBqProc(tableMap, rowToInsertsMap, requestInfo);
			// 同期の場合は戻り値null
			return null;
		} else {
			// 非同期処理
			BigQueryPostCallable callable = new BigQueryPostCallable(bigQueryInfo,
					rowToInsertsMap, metalist, tableNames, addPrefixToFieldname);
			return TaskQueueUtil.addTask(callable, 0, auth, requestInfo, connectionInfo);
		}
	}

	/**
	 * BigQueryへデータ登録.
	 * Feedでなく、テーブル名と値のMapを指定するメソッド
	 * @param tableName テーブル名
	 * @param list 行 (キー:項目名、値:値) のリスト
	 * @param async 非同期の場合true、同期の場合false
	 * @param addPrefixToFieldname フィールド名に接頭辞(第一階層)を付加する場合true
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return Future 同期の場合はnull
	 */
	@Override
	public Future postBq(String tableName, List<Map<String, Object>> list,
			boolean async, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		if (isEnableAccessLog()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[postBq(map)] start");
		}

		// BigQuery接続情報を取得
		BigQueryInfo bigQueryInfo = getBigQueryInfo(serviceName,
				requestInfo, connectionInfo);

		// コネクションを取得
		BigQueryConnection bigQuery = getConnection(bigQueryInfo, serviceName,
				requestInfo, connectionInfo);

		// データセットを取得
		BigQueryDataset dataset = getDataset(bigQueryInfo, bigQuery, serviceName,
				requestInfo, connectionInfo);

		// テーブル
		Map<String, Object> firstRowMap = list.get(0);
		BigQueryTable table = getTable(bigQueryInfo, dataset, tableName, firstRowMap,
				serviceName, requestInfo, connectionInfo);
		TableDefinition tableDefinition = table.getTableDefinition();
		List<RowToInsert> rowToInserts = new ArrayList<RowToInsert>();
		Map<String, BigQueryTable> tableMap = new HashMap<String, BigQueryTable>();
		tableMap.put(tableName, table);

		// 現在日時
		String nowStr = BigQueryUtil.getNowByMicrosec();

		// データ
		for (Map<String, Object> rowMap : list) {
			// テーブル定義を元に登録データオブジェクトを作成
			RowToInsert rowToInsert = createRowToInsert(tableDefinition, tableName,
					rowMap, nowStr);
			rowToInserts.add(rowToInsert);
		}
		Map<String, List<RowToInsert>> rowToInsertsMap =
				new LinkedHashMap<String, List<RowToInsert>>();
		rowToInsertsMap.put(tableName, rowToInserts);

		// 登録
		if (!async) {
			postBqProc(tableMap, rowToInsertsMap, requestInfo);
			// 同期の場合は戻り値null
			return null;
		} else {
			// 非同期処理
			BigQueryPostCallable callable = new BigQueryPostCallable(bigQueryInfo,
					rowToInsertsMap, firstRowMap);
			return TaskQueueUtil.addTask(callable, 0, auth, requestInfo, connectionInfo);
		}
	}

	/**
	 * BigQueryへ削除データ登録.
	 * @param uris 削除対象キー
	 * @param tableNames エンティティの第一階層と異なる名前をテーブル名にする場合に指定.
	 *                   キー:エンティティの第一階層名、値:BigQuery登録テーブル名
	 * @param async 非同期の場合true、同期の場合false
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return Future 同期の場合はnull
	 */
	@Override
	public Future deleteBq(String[] uris, Map<String, String> tableNames, boolean async,
			ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		// Metalistを取得
		List<Meta> metalist = BigQueryUtil.getMetalist(serviceName);
		// テンプレートからBigQueryテーブル対象を抽出する。
		List<String> tablesByMetalist = getTablesByMetalist(metalist, tableNames);
		if (tablesByMetalist == null || tablesByMetalist.isEmpty()) {
			throw new InvalidServiceSettingException("There is no target table.");
		}

		// BigQuery接続情報を取得
		BigQueryInfo bigQueryInfo = getBigQueryInfo(serviceName,
				requestInfo, connectionInfo);

		// コネクションを取得
		BigQueryConnection bigQuery = getConnection(bigQueryInfo, serviceName,
				requestInfo, connectionInfo);
		// データセットを取得
		BigQueryDataset dataset = getDataset(bigQueryInfo, bigQuery, serviceName,
				requestInfo, connectionInfo);

		// データセットから対象テーブルを抽出し、テンプレートから抽出したテーブルと突き合わせる。
		Map<String, BigQueryTable> tableMap = getBigQueryTables(dataset, tablesByMetalist,
				requestInfo);
		// 対象テーブルが1件も存在しない場合エラー
		if (tableMap == null || tableMap.isEmpty()) {
			throw new NoExistingEntryException("There is no target table.");
		}

		// 対象データ検索
		// キー: テーブル名、値: キーリスト
		Map<String, List<String>> keysMap = new LinkedHashMap<String, List<String>>();
		Set<String> uriSet = new HashSet<String>();

		for (Map.Entry<String, BigQueryTable> mapEntry : tableMap.entrySet()) {
			String tableName = mapEntry.getKey();
			BigQueryTable table = mapEntry.getValue();
			String sql = createSQLSelectByKey(table, uris);
			if (isEnableAccessLog()) {
				logger.debug("[deleteBq] check SQL: " + sql);
			}
			List<Map<String, Object>> result = queryBqProc(bigQueryInfo, bigQuery, sql,
					requestInfo);
			if (result != null && !result.isEmpty()) {
				List<String> keys = new ArrayList<String>();
				for (Map<String, Object> row : result) {
					String key = (String)row.get(BigQueryConst.BQFIELD_KEY);
					if (!StringUtils.isBlank(key)) {
						Boolean deleted = (Boolean)row.get(BigQueryConst.BQFIELD_DELETED);
						if (deleted == null || deleted) {
							throw new NoExistingEntryException(NoExistingEntryException.MSG_PREFIX + key);
						}
						keys.add(key);
						uriSet.add(key);
					}
				}
				if (!keys.isEmpty()) {
					keysMap.put(tableName, keys);
				}
			}
		}
		// 指定されたURIのうちデータが存在しないものがあればエラー
		for (String uri : uris) {
			if (!uri.endsWith(RequestParam.WILDCARD) && !uriSet.contains(uri)) {
				throw new NoExistingEntryException(NoExistingEntryException.MSG_PREFIX + uri);
			}
		}
		// 対象データが1件も存在しない場合エラー
		if (keysMap.isEmpty()) {
			throw new NoExistingEntryException();
		}

		// 現在日時
		Date now = new Date();

		// 削除データを作成
		// キー: テーブル名、値: BigQuery登録行リスト
		Map<String, List<RowToInsert>> rowToInsertsMap =
				new LinkedHashMap<String, List<RowToInsert>>();
		for (Map.Entry<String, List<String>> mapEntry : keysMap.entrySet()) {
			String tableName = mapEntry.getKey();
			List<String> keys = mapEntry.getValue();
			List<RowToInsert> rowToInserts = new ArrayList<RowToInsert>();
			for (String key : keys) {
				RowToInsert rowToInsert = createRowToInsertForDelete(key, now);
				rowToInserts.add(rowToInsert);
			}
			rowToInsertsMap.put(tableName, rowToInserts);
		}

		// 削除データ登録
		if (!async) {
			postBqProc(tableMap, rowToInsertsMap, requestInfo);
			// 同期の場合は戻り値null
			return null;
		} else {
			// 非同期処理
			BigQueryPostCallable callable = new BigQueryPostCallable(bigQueryInfo,
					rowToInsertsMap, metalist, tableNames, false);
			return TaskQueueUtil.addTask(callable, 0, auth, requestInfo, connectionInfo);
		}

	}

	/**
	 * BigQueryに対し指定された検索SQLを実行し結果を取得する.
	 * @param sql SQL
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 検索結果
	 */
	@Override
	public List<Map<String, Object>> queryBq(String sql, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// SELECT文以外はエラーとする。
		checkSelect(sql);

		String serviceName = auth.getServiceName();
		// BigQuery接続情報を取得
		BigQueryInfo bigQueryInfo = getBigQueryInfo(serviceName,
				requestInfo, connectionInfo);

		// コネクションを取得
		BigQueryConnection bigQuery = getConnection(bigQueryInfo, serviceName,
				requestInfo, connectionInfo);

		// 検索処理
		return queryBqProc(bigQueryInfo, bigQuery, sql, requestInfo);
	}

	/**
	 * BigQueryへデータ登録する際の入力チェック.
	 * エラーの場合例外がスローされる。(すべてのentryが対象外である場合のみエラースローされる。)
	 * @param feed Feed。deleteの場合null。
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	@Override
	public void checkBq(FeedBase feed, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		String accessLogUri = null;
		if (isEnableAccessLog()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[checkBq] start uri = ");
			if (feed != null && feed.entry != null) {
				boolean isFirst = true;
				StringBuilder asb = new StringBuilder();
				for (EntryBase entry : feed.entry) {
					if (isFirst) {
						isFirst = false;
					} else {
						asb.append(", ");
					}
					String uri = entry.getMyUri();
					asb.append(uri);
				}
				accessLogUri = asb.toString();
			}
			sb.append(accessLogUri);
			logger.debug(sb.toString());
		}

		if (feed != null && feed.entry != null) {
			IllegalParameterException tmpE = null;
			int bqCnt = 0;
			FieldMapper fieldMapper = new FieldMapper(isReflexField);
			// 入力チェック、項目抽出
			// キー: URI、値: (キー: Entryの第一階層フィールド名、値: テーブル名(Entryの第一階層オブジェクトか、変換値))
			for (EntryBase entry : feed.entry) {
				// 入力チェック、項目抽出
				try {
					getPostField(entry, serviceName, fieldMapper, requestInfo);
					bqCnt++;
				} catch (IllegalParameterException e) {
					if (isEnableAccessLog()) {
						StringBuilder sb = new StringBuilder();
						sb.append(LogUtil.getRequestInfoStr(requestInfo));
						sb.append("[checkBq] IllegalParameterException: ");
						sb.append(e.getMessage());
						logger.debug(sb.toString());
					}
					if (tmpE == null) {
						tmpE = e;
					}
				}
			}
			// BigQueryの登録対象が1件もなければエラー
			if (bqCnt == 0) {
				throw tmpE;
			}
		}

		// BigQuery接続情報を取得
		BigQueryInfo bigQueryInfo = getBigQueryInfo(serviceName,
				requestInfo, connectionInfo);

		// コネクションを取得
		BigQueryConnection bigQuery = getConnection(bigQueryInfo, serviceName,
				requestInfo, connectionInfo);
		// データセットを取得
		BigQueryDataset dataset = getDataset(bigQueryInfo, bigQuery, serviceName,
				requestInfo, connectionInfo);

		if (isEnableAccessLog()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[checkBq] end uri = ");
			sb.append(accessLogUri);
			logger.debug(sb.toString());
		}
	}

	/**
	 * 登録処理.
	 * @param tableMap テーブルリスト (キー:テーブル名、値:テーブルオブジェクト)
	 * @param rowToInsertsMap 登録データ (キー:テーブル名、値:BigQuery登録行リスト)
	 * @param requestInfo リクエスト情報
	 */
	void postBqProc(Map<String, BigQueryTable> tableMap,
			Map<String, List<RowToInsert>> rowToInsertsMap,
			RequestInfo requestInfo)
	throws IOException, TaggingException {
		// リトライ回数
		int numRetries = BigQueryUtil.getBigQueryRetryCount();
		int waitMillis = BigQueryUtil.getBigQueryRetryWaitmillis();

		for (Map.Entry<String, List<RowToInsert>> mapEntry : rowToInsertsMap.entrySet()) {
			// テーブルごとに登録処理
			String tableName = mapEntry.getKey();
			List<RowToInsert> rowToInserts = mapEntry.getValue();
			BigQueryTable table = tableMap.get(tableName);

			for (int r = 0; r <= numRetries; r++) {
				try {
					// 登録
					InsertAllResponse insResp = table.insert(rowToInserts);
					if (insResp.hasErrors()) {
						Map<Long, List<BigQueryError>> errors = insResp.getInsertErrors();
						if (logger.isInfoEnabled()) {
							logger.info("insert error: " + errors);
						}

						// エラースロー
						BigQueryUtil.throwBigQueryException(errors);
					}
					break;

				} catch (ReflexBigQueryException e) {
					checkRetry(e, false, r, numRetries, waitMillis, requestInfo);
				}
			}
		}
	}

	/**
	 * 検索処理.
	 * @param bigQueryInfo BigQuery接続情報
	 * @param bigQuery BigQueryコネクション
	 * @param sql SQL
	 * @param requestInfo リクエスト情報
	 * @return 検索結果
	 */
	private List<Map<String, Object>> queryBqProc(BigQueryInfo bigQueryInfo,
			BigQueryConnection bigQuery, String sql, RequestInfo requestInfo)
	throws IOException, TaggingException {
		// クエリを生成
		QueryJobConfiguration queryConfig =
				QueryJobConfiguration.newBuilder(sql)
				// Use standard SQL syntax for queries.
				// See: https://cloud.google.com/bigquery/sql-reference/
				.setUseLegacySql(false)
				.build();

		String location = BigQueryUtil.getLocation(bigQueryInfo);
		// リトライ回数
		int numRetries = BigQueryUtil.getBigQueryRetryCount();
		int waitMillis = BigQueryUtil.getBigQueryRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				// Create a job ID so that we can safely retry.
				JobId jobId = JobId.newBuilder().setLocation(location).build();
				BigQueryJob queryJob = bigQuery.create(
						JobInfo.newBuilder(queryConfig).setJobId(jobId).build());

				// Wait for the query to complete.
				queryJob = queryJob.waitFor();

				// Check for errors
				BigQueryUtil.checkJob(queryJob);

				List<Map<String, Object>> retRows = new ArrayList<Map<String, Object>>();

				// Get the results.
				TableResult result = queryJob.getQueryResults();
				if (result != null) {
					// Schema
					Schema schema = result.getSchema();
					FieldList fieldList = null;
					if (schema != null) {
						fieldList = schema.getFields();
					}
					if (fieldList != null) {
						for (FieldValueList row : result.iterateAll()) {	// 行
							Map<String, Object> cols = new LinkedHashMap<String, Object>();
							for (Field field : fieldList) {	// 列
								String name = field.getName();
								FieldValue fieldValue = row.get(name);
								LegacySQLTypeName type = field.getType();
								Object val = getFieldValueObject(fieldValue, type);
								cols.put(name, val);
							}
							retRows.add(cols);
						}
					}
				}

				if (retRows.isEmpty()) {
					return null;
				}
				return retRows;

			} catch (InterruptedException e) {
				checkRetry(e, r, numRetries, waitMillis, requestInfo);
			} catch (ReflexBigQueryException e) {
				checkRetry(e, true, r, numRetries, waitMillis, requestInfo);
			} catch (BigQueryException e) {
				logger.warn("[getBq] BigQueryException: " + e.getMessage(), e);
				checkRetry(new ReflexBigQueryException(e), true, r, numRetries, waitMillis,
						requestInfo);
			}
		}
		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}
	
	/**
	 * Fieldの値を適切な型で取得
	 * @param fieldValue FieldValue
	 * @param type FieldのType
	 * @return Fieldの値
	 */
	private Object getFieldValueObject(FieldValue fieldValue, LegacySQLTypeName type) 
	throws IOException {
		Object val = null;
		Object tmpVal = fieldValue.getValue();
		if (tmpVal != null) {
			if (type.equals(LegacySQLTypeName.BOOLEAN)) {
				val = fieldValue.getBooleanValue();
			} else if (type.equals(LegacySQLTypeName.DATETIME)) {
				val = BigQueryUtil.convertDateFromBigquery(tmpVal);
			} else if (type.equals(LegacySQLTypeName.INTEGER)) {
				val = fieldValue.getLongValue();
			} else if (type.equals(LegacySQLTypeName.FLOAT)) {
				val = fieldValue.getDoubleValue();
			} else if (type.equals(LegacySQLTypeName.RECORD)) {
				val = getRecordValue(fieldValue.getRecordValue());
			}
			if (val == null) {
				val = tmpVal;
			}
		}
		return val;
	}
	
	/**
	 * RECORDタイプを返却
	 * @param fieldValueList FieldValueList
	 * @return RECORD
	 */
	private Object getRecordValue(FieldValueList fieldValueList) {
		List<Object> values = new ArrayList<>();
		for (FieldValue fieldValue : fieldValueList) {
			Object val = null;
			FieldValue.Attribute attribute = fieldValue.getAttribute();
			if (logger.isTraceEnabled()) {
				logger.debug("[getRecordValue] fieldValue = " + fieldValue + ", attribute = " + attribute);
			}
			if (attribute.equals(FieldValue.Attribute.RECORD)) {
				val = getRecordValue(fieldValue.getRecordValue());
			} else {
				val = fieldValue.getValue();
			}
			values.add(val);
		}
		return values.toArray(new Object[0]);
	}

	/**
	 * BigQuery接続情報を取得.
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return BigQuery接続情報
	 */
	private BigQueryInfo getBigQueryInfo(String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		SystemContext systemContext = new SystemContext(serviceName,
				requestInfo, connectionInfo);
		String projectId = TaggingEnvUtil.getProp(serviceName, BigQueryConst.BIGQUERY_PROJECTID, null);
		String datasetId = TaggingEnvUtil.getProp(serviceName, BigQueryConst.BIGQUERY_DATASET, null);
		String location = TaggingEnvUtil.getProp(serviceName, BigQueryConst.BIGQUERY_LOCATION, null);
		byte[] secret = null;
		ReflexContentInfo contentInfo = systemContext.getContent(BigQueryConst.URI_SECRET_JSON);
		if (contentInfo != null) {
			secret = contentInfo.getData();
		}
		if (secret != null && secret.length > 0 && !StringUtils.isBlank(datasetId)) {
			return new BigQueryInfo(projectId, datasetId, location, secret);
		}

		throw new InvalidServiceSettingException(BigQueryConst.MSG_NO_SETTINGS);
	}

	/**
	 * BigQuery接続オブジェクトを取得.
	 * @param bigQueryInfo BigQuery接続情報
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return BigQuery接続オブジェクト
	 */
	BigQueryConnection getConnection(BigQueryInfo bigQueryInfo,
			String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String name = BigQueryConst.CONNECTION_INFO_BIGQUERY;
		// リクエスト・スレッド内で取得済みのコネクションがあれば使い回す。
		BigQueryConnection conn = (BigQueryConnection)connectionInfo.get(name);
		if (conn != null) {
			return conn;
		}

		// コネクション取得
		int numRetries = BigQueryUtil.getBigQueryRetryCount();
		int waitMillis = BigQueryUtil.getBigQueryRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				BigQuery bigQuery = getBigQuery(bigQueryInfo, requestInfo, connectionInfo);
				conn = new BigQueryConnection(bigQuery);
				connectionInfo.put(name, conn);
				return conn;

			} catch (IOException e) {
				checkRetry(e, r, numRetries, waitMillis, requestInfo);
			} catch (ReflexBigQueryException e) {
				checkRetry(e, r, numRetries, waitMillis, requestInfo);
			}
		}
		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}

	/**
	 * BigQueryデータセットオブジェクトを取得.
	 * @param bigQueryInfo BigQuery接続情報
	 * @param bigQuery BigQueryコネクション
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return BigQueryデータセットオブジェクト
	 */
	BigQueryDataset getDataset(BigQueryInfo bigQueryInfo,
			BigQueryConnection bigQuery, String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String datasetId = bigQueryInfo.getDatasetId();
		BigQueryDataset dataset = getDatasetProc(bigQuery, datasetId,
				requestInfo, connectionInfo);
		if (dataset == null) {
			// データセットを生成
			// Prepares a new dataset
			String location = BigQueryUtil.getLocation(bigQueryInfo);
			dataset = createDatasetProc(bigQuery, datasetId, location,
					requestInfo, connectionInfo);
		}
		return dataset;
	}

	/**
	 * BigQueryのデータセットオブジェクトを取得.
	 * @param bigQuery BigQueryコネクション
	 * @param datasetId データセットID
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return テーブルオブジェクト
	 */
	private BigQueryDataset getDatasetProc(BigQueryConnection bigQuery, String datasetId,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		int numRetries = BigQueryUtil.getBigQueryRetryCount();
		int waitMillis = BigQueryUtil.getBigQueryRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				return bigQuery.getDataset(datasetId);

			} catch (ReflexBigQueryException e) {
				checkRetry(e, r, numRetries, waitMillis, requestInfo);
			}
		}
		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}

	/**
	 * BigQueryのデータセットオブジェクトを取得.
	 * @param bigQuery BigQueryコネクション
	 * @param datasetId データセットID
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return テーブルオブジェクト
	 */
	private BigQueryDataset createDatasetProc(BigQueryConnection bigQuery, String datasetId,
			String location,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		DatasetInfo.Builder builder = DatasetInfo.newBuilder(datasetId);
		builder = builder.setLocation(location);
		DatasetInfo datasetInfo = builder.build();
		int numRetries = BigQueryUtil.getBigQueryRetryCount();
		int waitMillis = BigQueryUtil.getBigQueryRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				// Creates the dataset
				return bigQuery.create(datasetInfo);

			} catch (ReflexBigQueryException e) {
				String msg = e.getMessage();
				if (msg.indexOf(BigQueryConst.BIGQUERYEXCEPTION_ALREADY_EXISTS) > -1) {
					// データセットが既に存在する場合取得する
					return getDatasetProc(bigQuery, datasetId, requestInfo, connectionInfo);
				}
				checkRetry(e, r, numRetries, waitMillis, requestInfo);
			}
		}
		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}

	/**
	 * BigQueryテーブルオブジェクトを取得.
	 * @param bigQueryInfo BigQuery接続情報
	 * @param dataset データセットオブジェクト
	 * @param tableFieldName テーブルとなるフィールド名
	 * @param tableName テーブル名
	 * @param metalist テンプレート情報
	 * @param tableNames エンティティの第一階層と異なる名前をテーブル名にする場合に指定.
	 *                   キー:エンティティの第一階層名、値:BigQuery登録テーブル名
	 * @param addPrefixToFieldname フィールド名に接頭辞(第一階層)を付加する場合true
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return BigQueryテーブルオブジェクト
	 */
	BigQueryTable getTable(BigQueryInfo bigQueryInfo, BigQueryDataset dataset,
			String tableFieldName, String tableName, List<Meta> metalist,
			boolean addPrefixToFieldname, String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		BigQueryTable table = getTableProc(dataset, tableName, requestInfo, connectionInfo);
		String prefixFieldname = BigQueryUtil.getPrefixFieldname(tableFieldName, 
				addPrefixToFieldname);
		TableDefinition tableDefinition = BigQueryUtil.createTableDefinition(tableFieldName,
				metalist, prefixFieldname, serviceName);
		if (table == null) {
			// テーブルを生成
			table = createTableProc(dataset, tableName, tableDefinition,
					requestInfo, connectionInfo);
		} else {
			// tableに定義されているフィールドと、生成したフィールド定義を比較する。
			TableDefinition currentTableDefinition = table.getTableDefinition();
			Map<String, Field> currentBgFieldMap = getBqFields(currentTableDefinition);
			
			List<Field> addFields = new ArrayList<>();
			Schema schema = tableDefinition.getSchema();
			FieldList bqFieldList = schema.getFields();
			ListIterator<Field> it = bqFieldList.listIterator();
			while (it.hasNext()) {
				Field bqField = it.next();
				String bqFieldName = bqField.getName();
				if (!currentBgFieldMap.containsKey(bqFieldName)) {
					// 追加
					addFields.add(bqField);
				}
			}
			if (!addFields.isEmpty()) {
				FieldList fields = currentTableDefinition.getSchema().getFields();
				List<Field> fieldList = new ArrayList<Field>();
				fields.forEach(fieldList::add);
				fieldList.addAll(addFields);
				Schema newSchema = Schema.of(fieldList);

				// Update the table with the new schema
				BigQueryTable updatedTable = table.toBuilder().setDefinition(
						StandardTableDefinition.of(newSchema)).build();
				int numRetries = BigQueryUtil.getBigQueryRetryCount();
				int waitMillis = BigQueryUtil.getBigQueryRetryWaitmillis();
				for (int r = 0; r <= numRetries; r++) {
					try {
						table = updatedTable.update();
						break;

					} catch (ReflexBigQueryException e) {
						checkRetry(e, r, numRetries, waitMillis, requestInfo);
					}
				}
			}
		
		}
		return table;
	}

	/**
	 * BigQueryテーブルオブジェクトを取得.
	 * @param bigQueryInfo BigQuery接続情報
	 * @param dataset データセットオブジェクト
	 * @param tableName テーブル名
	 * @param rowMap データmap(1行分)
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return BigQueryテーブルオブジェクト
	 */
	BigQueryTable getTable(BigQueryInfo bigQueryInfo, BigQueryDataset dataset,
			String tableName, Map<String, Object> rowMap, String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		BigQueryTable table = getTableProc(dataset, tableName, requestInfo, connectionInfo);
		if (table == null) {
			// テーブルを生成
			TableDefinition tableDefinition = createTableDefinition(tableName,
					rowMap, serviceName);
			table = createTableProc(dataset, tableName, tableDefinition,
					requestInfo, connectionInfo);
		}
		return table;
	}

	/**
	 * BigQueryのテーブルオブジェクトを取得.
	 * @param dataset データセット
	 * @param tableName テーブル名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return テーブルオブジェクト
	 */
	private BigQueryTable getTableProc(BigQueryDataset dataset, String tableName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		int numRetries = BigQueryUtil.getBigQueryRetryCount();
		int waitMillis = BigQueryUtil.getBigQueryRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				return dataset.get(tableName);

			} catch (ReflexBigQueryException e) {
				checkRetry(e, r, numRetries, waitMillis, requestInfo);
			}
		}
		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}

	/**
	 * テーブルを生成.
	 * @param dataset データセット
	 * @param tableName テーブル名
	 * @param tableDefinition テーブル定義
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return テーブルオブジェクト
	 */
	private BigQueryTable createTableProc(BigQueryDataset dataset, String tableName,
			TableDefinition tableDefinition,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		int numRetries = BigQueryUtil.getBigQueryRetryCount();
		int waitMillis = BigQueryUtil.getBigQueryRetryWaitmillis();
		// テーブルを生成
		for (int r = 0; r <= numRetries; r++) {
			try {
				return dataset.create(tableName, tableDefinition);

			} catch (ReflexBigQueryException e) {
				String msg = e.getMessage();
				if (msg.indexOf(BigQueryConst.BIGQUERYEXCEPTION_ALREADY_EXISTS) > -1) {
					// テーブルが既に存在する場合取得する
					return getTableProc(dataset, tableName, requestInfo, connectionInfo);
				}
				checkRetry(e, r, numRetries, waitMillis, requestInfo);
			}
		}
		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}

	/**
	 * BigQuery接続オブジェクトを取得.
	 * @param bigQueryInfo BigQuery接続情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return BigQuery接続オブジェクト
	 */
	private BigQuery getBigQuery(BigQueryInfo bigQueryInfo,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, ReflexBigQueryException {
		// json秘密鍵を読み込む
		InputStream jsonFile = null;
		try {
			jsonFile = new ByteArrayInputStream(bigQueryInfo.getSecret());

			GoogleCredentials credentials = GoogleCredentials.fromStream(jsonFile);
			BigQueryOptions bigqueryOptions = BigQueryOptions.newBuilder().setCredentials(credentials).build();

			return bigqueryOptions.getService();

		} catch (BigQueryException e) {
			throw new ReflexBigQueryException(e);
		} finally {
			if (jsonFile != null) {
				jsonFile.close();
			}
		}
	}

	/**
	 * テーブル名を取得.
	 * エンティティの第一階層名に紐づくテーブル名が指定されている場合、指定されたテーブル名を返却.
	 * @param tableFieldName エンティティ第一階層名
	 * @param tableNames エンティティの第一階層と異なる名前をテーブル名にする場合に指定.
	 *                   キー:エンティティの第一階層名、値:BigQuery登録テーブル名
	 * @return テーブル名
	 */
	private String getTableName(String tableFieldName, Map<String, String> tableNames) {
		if (tableNames != null && tableNames.containsKey(tableFieldName)) {
			return tableNames.get(tableFieldName);
		}
		return tableFieldName;
	}

	/**
	 * BigQueryへの登録項目を取得.
	 * 登録項目を抽出しながら入力チェックを行う。
	 * @param entry Entry
	 * @param serviceName サービス名
	 * @param fieldMapper FieldMapper
	 * @param requestInfo リクエスト情報(ログ用)
	 * @return BigQueryへの登録項目
	 */
	private Map<String, Object> getPostField(EntryBase entry,
			String serviceName, FieldMapper fieldMapper, RequestInfo requestInfo) {
		CheckUtil.checkNotNull(entry, "Entry");
		Map<String, Object> values = new LinkedHashMap<String, Object>();

		IllegalParameterException tmpE = null;
		// 値が設定されている第一階層項目の抽出
		java.lang.reflect.Field[] fields = entry.getClass().getFields();
		for (java.lang.reflect.Field field : fields) {
			// static項目は飛ばす
			if (Modifier.isStatic(field.getModifiers())) {
				continue;
			}
			// 標準Atom項目は飛ばす。
			if (BigQueryUtil.isAtomField(field)) {
				continue;
			}

			try {
				String getter = FieldMapper.getGetter(field, isReflexField);
				Object obj = fieldMapper.getValue(entry, getter);
				if (obj != null) {
					// 第一階層入力項目のエンティティ定義がリスト指定であればエラー
					if (Collection.class.isInstance(obj)) {
						throw new IllegalParameterException("This entity can not be registered in BigQuery. (The first hierarchical item is listed.) " + field.getName());
					}
					// 第一階層がレコード形式でなければエラー
					if (BigQueryUtil.isPlainType(obj.getClass())) {
						throw new IllegalParameterException("This entity can not be registered in BigQuery. (The first hierarchical item is not in record format.) " + field.getName());
					}
	
					values.put(field.getName(), obj);
				}
			} catch (IllegalParameterException e) {
				if (isEnableAccessLog()) {
					StringBuilder sb = new StringBuilder();
					sb.append(LogUtil.getRequestInfoStr(requestInfo));
					sb.append("[getPostField] IllegalParameterException: ");
					sb.append(e.getMessage());
					logger.debug(sb.toString());
				}
				if (tmpE == null) {
					tmpE = e;
				}
			}
		}

		// 入力無しはエラー
		if (values.isEmpty()) {
			if (tmpE != null) {
				// エラー発生かつBigQueryの登録対象が1件もなければエラー
				throw tmpE;
			} else {
				throw new IllegalParameterException("A value is required for the entry field.");
			}
		}

		// 第一階層項目ごとに、第三階層が存在しないかチェック
		for (Map.Entry<String, Object> mapEntry : values.entrySet()) {
			Object obj = mapEntry.getValue();
			java.lang.reflect.Field[] vFields = obj.getClass().getFields();
			for (java.lang.reflect.Field vField : vFields) {
				Class<?> vFieldType = vField.getType();
				if (!BigQueryUtil.isPlainType(vFieldType)) {
					throw new IllegalParameterException("This entity can not be registered in BigQuery. (You can not specify the list or record format in the second hierarchy item.) " + mapEntry.getKey());
				}
			}
		}

		return values;
	}

	/**
	 * リトライチェック
	 * @param e 例外
	 * @param r リトライ回数
	 * @param numRetries リトライ総数
	 * @param waitMillis リトライ時の待ち時間(ミリ秒)
	 * @param requestInfo リクエスト情報
	 */
	private void checkRetry(IOException e, int r, int numRetries, int waitMillis,
			RequestInfo requestInfo)
	throws IOException, TaggingException {

		// 設定エラーかどうか
		if (BigQueryUtil.isSettingError(e, requestInfo)) {
			if (logger.isInfoEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("BigQuery setting error: ");
				sb.append(e.getClass().getName());
				sb.append(" ");
				sb.append(e.getMessage());
				logger.info(sb.toString());
			}
			throw new InvalidServiceSettingException(e);
		}

		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append(e.getClass().getName());
			sb.append(" ");
			sb.append(e.getMessage());
			logger.debug(sb.toString());
		}

		// 入力エラーかどうか

		// リトライエラーかどうか
		boolean retryable = BigQueryUtil.isRetryError(e, requestInfo);

		// その他はIOエラー
		if (!retryable) {
			throw e;
		}
		// リトライ回数を超えるとエラー
		if (r >= numRetries) {
			throw e;
		}
		if (logger.isInfoEnabled()) {
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + RetryUtil.getRetryLog(e, r));
		}
		RetryUtil.sleep(waitMillis);
	}

	/**
	 * リトライチェック
	 * @param e 例外
	 * @param r リトライ回数
	 * @param numRetries リトライ総数
	 * @param waitMillis リトライ時の待ち時間(ミリ秒)
	 * @param requestInfo リクエスト情報
	 */
	private void checkRetry(InterruptedException e, int r, int numRetries, int waitMillis,
			RequestInfo requestInfo)
	throws IOException, TaggingException {
		if (isEnableAccessLog()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo), e);
		}

		// リトライエラーかどうか
		boolean retryable = BigQueryUtil.isRetryError(e, requestInfo);

		// その他はIOエラー
		if (!retryable) {
			throw new IOException(e);
		}
		// リトライ回数を超えるとエラー
		if (r >= numRetries) {
			throw new IOException(e);
		}
		if (logger.isInfoEnabled()) {
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + RetryUtil.getRetryLog(e, r));
		}
		RetryUtil.sleep(waitMillis);
	}

	/**
	 * リトライチェック
	 * @param e 例外
	 * @param r リトライ回数
	 * @param numRetries リトライ総数
	 * @param waitMillis リトライ時の待ち時間(ミリ秒)
	 * @param requestInfo リクエスト情報
	 */
	private void checkRetry(ReflexBigQueryException e, int r,
			int numRetries, int waitMillis, RequestInfo requestInfo)
	throws IOException, TaggingException {
		checkRetry(e, false ,r, numRetries, waitMillis, requestInfo);
	}

	/**
	 * リトライチェック
	 * @param e 例外
	 * @param isSelect 検索の場合true
	 * @param r リトライ回数
	 * @param numRetries リトライ総数
	 * @param waitMillis リトライ時の待ち時間(ミリ秒)
	 * @param requestInfo リクエスト情報
	 */
	private void checkRetry(ReflexBigQueryException e, boolean isSelect, int r,
			int numRetries, int waitMillis, RequestInfo requestInfo)
	throws IOException, TaggingException {
		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append(e.getClass().getSimpleName());
			sb.append(" ");
			sb.append(e.getMessage());
			logger.debug(sb.toString());
		}

		// ReflexBigQueryExceptionはRuntimeExceptionをスローしないための例外なので、実際のエラーはcause。
		Throwable cause = e.getCause();
		boolean retryable = false;
		if (cause instanceof BigQueryException) {
			BigQueryException be = (BigQueryException)cause;
			// 入力エラーかどうか
			if (BigQueryUtil.isInputError(be, isSelect, requestInfo)) {
				throw new IllegalParameterException(be.getMessage());
			}

			// 設定エラーかどうか
			if (BigQueryUtil.isSettingError(be, requestInfo)) {
				throw new InvalidServiceSettingException(be);
			}

			// リトライエラーかどうか
			retryable = BigQueryUtil.isRetryError(be, requestInfo);
		}
		// その他はIOエラー
		if (!retryable) {
			throw new IOException(cause);
		}
		// リトライ回数を超えるとエラー
		if (r >= numRetries) {
			throw new IOException(cause);
		}
		if (logger.isInfoEnabled()) {
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + RetryUtil.getRetryLog(e, r));
		}
		RetryUtil.sleep(waitMillis);
	}

	/**
	 * 登録レコードを生成
	 * @param tableDefinition テーブル定義情報
	 * @param tableFieldName テーブルフィールド名
	 * @param uri URI
	 * @param value Entry第一階層オブジェクト
	 * @param nowStr 現在日時文字列
	 * @param id ID
	 * @param fieldMapper FieldMapper
	 * @param addPrefixToFieldname フィールド名に接頭辞(第一階層)を付加する場合true
	 * @return 登録レコード
	 */
	private RowToInsert createRowToInsert(TableDefinition tableDefinition,
			String tableFieldName, String uri, Object value, String nowStr, String id,
			FieldMapper fieldMapper, boolean addPrefixToFieldname) {
		Schema schema = tableDefinition.getSchema();
		FieldList bqFieldList = schema.getFields();
		ListIterator<Field> it = bqFieldList.listIterator();
		Map<String, Object> cols = new HashMap<String, Object>();
		String prefixStr = null;
		int prefixLen = 0;
		if (addPrefixToFieldname) {
			prefixStr = BigQueryUtil.getPrefixFieldname(tableFieldName, addPrefixToFieldname);
			prefixLen = prefixStr.length();
		}
		
		while (it.hasNext()) {
			Field bqField = it.next();

			Object val = null;
			String bqFieldName = bqField.getName();
			if (BigQueryConst.BQFIELD_KEY.equals(bqFieldName)) {
				val = uri;
			} else if (BigQueryConst.BQFIELD_UPDATED.equals(bqFieldName)) {
				val = nowStr;
			} else if (BigQueryConst.BQFIELD_DELETED.equals(bqFieldName)) {
				val = false;
			} else if (BigQueryConst.BQFIELD_ID.equals(bqFieldName)) {
				val = id;
			} else {
				// このgetGetterはbooleanかどうかの情報が必要なだけなので、ひとまずBigQueryからの情報を使用。
				Class<?> typeCls = BigQueryUtil.getClass(bqField.getType());
				// フィールド名に接頭辞(第一階層)を付加する場合、接頭辞のないフィールドは無視する。
				if (addPrefixToFieldname && !bqFieldName.startsWith(prefixStr)) {
					continue;
				}
				
				// フィールド名に接頭辞(第一階層)を付加する場合、接頭辞を取り除いて項目名とする。
				String tmpBqFieldName = bqFieldName;
				if (prefixLen > 0) {
					tmpBqFieldName = bqFieldName.substring(prefixLen);
				} else {
					tmpBqFieldName = bqFieldName;
				}
				// tmpBqFieldName がFieldにあるかどうか判定
				if (FieldMapper.hasField(value, tmpBqFieldName)) {
					String getter = FieldMapper.getGetter(tmpBqFieldName, typeCls, isReflexField);
					val = fieldMapper.getValue(value, getter);
				}
			}

			if (val != null) {
				if (val instanceof Date) {
					val = BigQueryUtil.convertDateForBigquery((Date)val);
				}
				cols.put(bqFieldName, val);
			}
		}

		return RowToInsert.of(cols);
	}

	/**
	 * 登録レコードを生成
	 * @param tableDefinition テーブル定義情報
	 * @param tableName テーブル名
	 * @param uri URI
	 * @param rowMap 1行分の項目名と値
	 * @param nowStr 現在日時文字列
	 * @return 登録レコード
	 */
	private RowToInsert createRowToInsert(TableDefinition tableDefinition,
			String tableName, Map<String, Object> rowMap, String nowStr) {
		Schema schema = tableDefinition.getSchema();
		FieldList bqFieldList = schema.getFields();
		ListIterator<Field> it = bqFieldList.listIterator();
		Map<String, Object> cols = new HashMap<String, Object>();
		while (it.hasNext()) {
			Field bqField = it.next();

			Object val = null;
			String bqFieldName = bqField.getName();
			if (rowMap.containsKey(bqFieldName)) {
				val = rowMap.get(bqFieldName);
			} else {
				if (BigQueryConst.BQFIELD_UPDATED.equals(bqFieldName)) {
					val = nowStr;
				} else if (BigQueryConst.BQFIELD_DELETED.equals(bqFieldName)) {
					val = false;
				}
			}

			if (val != null) {
				if (val instanceof Date) {
					val = BigQueryUtil.convertDateForBigquery((Date)val);
				}
				cols.put(bqFieldName, val);
			}
		}

		return RowToInsert.of(cols);
	}

	/**
	 * 削除データ登録レコードを生成
	 * @param uri URI
	 * @param now 現在日時
	 * @return 削除データ登録レコード
	 */
	private RowToInsert createRowToInsertForDelete(String uri, Date now) {
		Map<String, Object> cols = new HashMap<String, Object>();
		// 追加項目のセット内容
		// key: キー
		cols.put(BigQueryConst.BQFIELD_KEY, uri);
		// updated: 現在日時
		cols.put(BigQueryConst.BQFIELD_UPDATED, BigQueryUtil.convertDateForBigquery(now));
		// deleted: true (削除)
		cols.put(BigQueryConst.BQFIELD_DELETED, true);

		return RowToInsert.of(cols);
	}

	/**
	 * テーブル定義オブジェクトを生成(map版)
	 * @param tableName テーブル名
	 * @param map キー:項目名、値:値
	 * @param serviceName サービス名
	 * @return テーブル定義オブジェクト
	 */
	private TableDefinition createTableDefinition(String tableName, Map<String, Object> map,
			String serviceName) {
		List<Field> bqFields = new ArrayList<Field>();

		// Field
		Field bqField = null;

		// 標準項目
		bqField = Field.of(BigQueryConst.BQFIELD_KEY, LegacySQLTypeName.STRING, new Field[0]);
		bqFields.add(bqField);
		bqField = Field.of(BigQueryConst.BQFIELD_UPDATED, LegacySQLTypeName.DATETIME, new Field[0]);
		bqFields.add(bqField);
		bqField = Field.of(BigQueryConst.BQFIELD_DELETED, LegacySQLTypeName.BOOLEAN, new Field[0]);
		bqFields.add(bqField);
		bqField = Field.of(BigQueryConst.BQFIELD_ID, LegacySQLTypeName.STRING, new Field[0]);
		bqFields.add(bqField);

		// 各項目
		for (Map.Entry<String, Object> mapEntry : map.entrySet()) {
			String name = mapEntry.getKey();
			if (!BigQueryConst.BQFIELD_KEY.equals(name) &&
					!BigQueryConst.BQFIELD_UPDATED.equals(name) &&
					!BigQueryConst.BQFIELD_DELETED.equals(name)) {
				Object val = mapEntry.getValue();
				bqField = Field.of(name, BigQueryUtil.getLegacySQLTypeName(val),
						new Field[0]);
				bqFields.add(bqField);
			}
		}

		// Schema
		Schema schema = Schema.of(bqFields);
		StandardTableDefinition.Builder builder = StandardTableDefinition.newBuilder();
		builder.setSchema(schema);

		TableDefinition tableDefinition = builder.build();
		return tableDefinition;
	}

	/**
	 * SQL文がselectから始まっていない場合はエラー.
	 * 更新処理をSQL文で実行されないようにするためのチェック
	 * @param sql SQL文
	 */
	private void checkSelect(String sql) {
		// 2022.4.22 "create" から始まる、仮テーブルを作成するような検索もあるため、チェックは行わないようにする。
	}

	/**
	 * BigQueryの対象テーブルを取得.
	 * @param dataset データセット
	 * @param tablesByMetalist テンプレートから抽出したテーブル名
	 * @param requestInfo リクエスト情報
	 * @return BigQueryの対象テーブルリスト (キー:テーブル名、値:テーブルオブジェクト)
	 */
	private Map<String, BigQueryTable> getBigQueryTables(BigQueryDataset dataset,
			List<String> tablesByMetalist, RequestInfo requestInfo)
	throws IOException, TaggingException {
		// データセットからテーブルリストを取得
		List<BigQueryTable> tablesByDataset = getTablesByDataset(dataset, requestInfo);
		// 一致するものを抽出
		Map<String, BigQueryTable> tables = new LinkedHashMap<String, BigQueryTable>();
		if (tablesByDataset != null) {
			for (BigQueryTable tableByDataset : tablesByDataset) {
				String tableId = tableByDataset.getTableId();
				if (tablesByMetalist.contains(tableId)) {
					tables.put(tableId, tableByDataset);
				}
			}
		}
		if (tables.isEmpty()) {
			return null;
		}
		return tables;
	}

	/**
	 * テンプレートからテーブル一覧を取得する.
	 * @param metalist Metalist
	 * @param tableNames エンティティの第一階層と異なる名前をテーブル名にする場合に指定.
	 *                   キー:エンティティの第一階層名、値:BigQuery登録テーブル名
	 * @return テーブル名一覧
	 */
	private List<String> getTablesByMetalist(List<Meta> metalist, Map<String, String> tableNames) {
		List<String> tables = new ArrayList<String>();
		String tmpName = null;
		boolean isTable = false;
		for (Meta meta : metalist) {
			int level = meta.level;
			if (level == 0) {
				// 最初はentry
				continue;

			} else if (level == 1) {
				// 前のテーブル処理
				if (!StringUtils.isBlank(tmpName) && isTable) {
					String tableName = getTableName(tmpName, tableNames);
					tables.add(tableName);
				}
				// 現項目
				tmpName = meta.name;
				// レコード型かつリストでない項目が対象
				// 標準ATOM項目は対象外
				isTable = meta.isrecord && !meta.repeated &&
						!tmpName.equals(BigQueryConst.ATOM_CONTENT);

			} else if (level == 2) {
				if (isTable) {
					// 第二階層にリスト、テーブル項目があれば対象外
					isTable = !meta.isrecord && !meta.repeated;
				}
			} else if (level > 2) {
				// 第三階層以降があれば対象外
				isTable = false;
			}
		}
		// 最後のテーブルについて判定
		if (!StringUtils.isBlank(tmpName) && isTable) {
			String tableName = getTableName(tmpName, tableNames);
			tables.add(tableName);
		}
		return tables;
	}

	/**
	 * データセット内のテーブル一覧を取得.
	 * @param dataset データセット
	 * @return テーブル一覧
	 */
	private List<BigQueryTable> getTablesByDataset(BigQueryDataset dataset,
			RequestInfo requestInfo)
	throws IOException, TaggingException {
		int numRetries = BigQueryUtil.getBigQueryRetryCount();
		int waitMillis = BigQueryUtil.getBigQueryRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				return dataset.list();

			} catch (ReflexBigQueryException e) {
				checkRetry(e, r, numRetries, waitMillis, requestInfo);
			}
		}
		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}

	/**
	 * 対象テーブルの、キー指定検索SQLを生成.
	 * @param table テーブル
	 * @param uris キーリスト
	 * @return SQL
	 */
	private String createSQLSelectByKey(BigQueryTable table, String[] uris) {
		String datasetId = table.getDatasetId();
		String tableName = table.getTableId();
		String MAX_VIEW = "max_view";
		String MAX_UPDATED = "max_updated";

		StringBuilder sql = new StringBuilder();
		sql.append("select ");
		sql.append(MAX_VIEW);
		sql.append(".");
		sql.append(BigQueryConst.BQFIELD_KEY);
		sql.append(", ");
		sql.append(MAX_VIEW);
		sql.append(".");
		sql.append(MAX_UPDATED);
		sql.append(", ");
		sql.append(tableName);
		sql.append(".");
		sql.append(BigQueryConst.BQFIELD_DELETED);

		sql.append(" from `");
		sql.append(datasetId);
		sql.append(".");
		sql.append(tableName);
		sql.append("` as ");
		sql.append(tableName);

		sql.append(" inner join ");
		sql.append("(select ");
		sql.append(BigQueryConst.BQFIELD_KEY);
		sql.append(", ");
		sql.append("max(");
		sql.append(BigQueryConst.BQFIELD_UPDATED);
		sql.append(") as ");
		sql.append(MAX_UPDATED);
		sql.append(" from `");
		sql.append(datasetId);
		sql.append(".");
		sql.append(tableName);
		sql.append("` ");
		sql.append("where ");

		List<String> equalUris = new ArrayList<String>();
		List<String> likeUris = new ArrayList<String>();
		for (String uri : uris) {
			if (uri.endsWith(RequestParam.WILDCARD)) {
				likeUris.add(uri.substring(0, uri.length() - 1));
			} else {
				equalUris.add(uri);
			}
		}
		int equalUrisSize = equalUris.size();
		if (equalUrisSize > 0) {
			if (equalUrisSize == 1) {
				sql.append(BigQueryConst.BQFIELD_KEY);
				sql.append(" = '");
				sql.append(equalUris.get(0));
				sql.append("' ");
			} else {
				sql.append(BigQueryConst.BQFIELD_KEY);
				sql.append(" in (");
				boolean isFirst = true;
				for (String equalUri : equalUris) {
					if (isFirst) {
						isFirst = false;
					} else {
						sql.append(", ");
					}
					sql.append("'");
					sql.append(equalUri);
					sql.append("'");
				}
				sql.append(") ");
			}
		}

		int likeUrisSize = likeUris.size();
		if (likeUrisSize > 0) {
			if (equalUrisSize > 0) {
				sql.append("or ");
			}
			boolean isFirst = true;
			for (String likeUri : likeUris) {
				if (isFirst) {
					isFirst = false;
				} else {
					sql.append("or ");
				}
				sql.append(BigQueryConst.BQFIELD_KEY);
				sql.append(" like '");
				sql.append(likeUri);
				sql.append("%' ");
			}
		}

		sql.append("group by ");
		sql.append(BigQueryConst.BQFIELD_KEY);
		sql.append(") as ");
		sql.append(MAX_VIEW);
		sql.append(" on ");
		sql.append(tableName);
		sql.append(".");
		sql.append(BigQueryConst.BQFIELD_KEY);
		sql.append("=");
		sql.append(MAX_VIEW);
		sql.append(".");
		sql.append(BigQueryConst.BQFIELD_KEY);
		sql.append(" and ");
		sql.append(tableName);
		sql.append(".");
		sql.append(BigQueryConst.BQFIELD_UPDATED);
		sql.append("=");
		sql.append(MAX_VIEW);
		sql.append(".");
		sql.append(MAX_UPDATED);

		return sql.toString();
	}

	/**
	 * BigQueryテーブルのscheme fieldをMapにして返却.
	 * @param tableDefinition テーブル定義
	 * @return BigQueryテーブルのscheme fieldをMapにしたもの(キー:フィールド名、値:フィールドオブジェクト)
	 */
	private Map<String, Field> getBqFields(TableDefinition tableDefinition) {
		Map<String, Field> bqFieldMap = new HashMap<>();
		Schema schema = tableDefinition.getSchema();
		FieldList bqFieldList = schema.getFields();
		ListIterator<Field> it = bqFieldList.listIterator();
		while (it.hasNext()) {
			Field bqField = it.next();
			String bqFieldName = bqField.getName();
			bqFieldMap.put(bqFieldName, bqField);
		}
		return bqFieldMap;
	}
	
	/**
	 * BigQueryへのアクセスログを出力するかどうかを取得.
	 * @return BigQueryへのアクセスログを出力する場合true
	 */
	private boolean isEnableAccessLog() {
		return BigQueryUtil.isEnableAccessLog();
	}

}
