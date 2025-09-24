package jp.reflexworks.taggingservice.bigquery;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.cloud.bigquery.BigQueryError;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.DatasetId;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.LegacySQLTypeName;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardTableDefinition;
import com.google.cloud.bigquery.TableDefinition;

import jp.reflexworks.atom.api.AtomConst;
import jp.reflexworks.atom.mapper.FeedTemplateMapper.Meta;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.ConnectionException;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.sourceforge.reflex.util.DateUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * BigQueryユーティリティ
 */
public class BigQueryUtil {

	/** BigQueryException メッセージの正規表現 */
	private static final String REGEX_SETTINGERROR =
			"^Error getting access token for service account" +
			"|^Access Denied";

	/** Pattern (BigQueryException) */
	private static Pattern PATTERN_SETTINGERROR = Pattern.compile(REGEX_SETTINGERROR);

	/** IOException メッセージの正規表現 */
	private static final String REGEX_SETTINGERROR_IO =
			"^Unexpected exception reading" +
			"|^Unrecognized";

	/** Pattern (IOException) */
	private static Pattern PATTERN_SETTINGERROR_IO = Pattern.compile(REGEX_SETTINGERROR_IO);

	/** BigQueryException メッセージの正規表現 (検索) */
	private static final String REGEX_INPUTERROR_SELECT =
			"^Not found" +
			"|is missing" +
			"|^Unrecognized" +
			"|^Syntax error" +
			"|which is neither grouped nor aggregated at" +
			"|^Invalid" +
			"| invalid" +
			"|^No matching" +
			"| argument types" +
			"|400 Bad Request" +
			"|Bad int64 value";

	/** Pattern (BigQueryException) (検索) */
	private static Pattern PATTERN_INPUTERROR_SELECT = Pattern.compile(REGEX_INPUTERROR_SELECT);

	/** BigQueryException メッセージの正規表現 (検索以外) */
	private static final String REGEX_INPUTERROR_EXEC =
			"^Not found" +
			"|is missing" +
			"|^Unrecognized" +
			"|^Syntax error" +
			"|^Invalid" +
			"| invalid" +
			"|^No matching" +
			"|400 Bad Request";

	/** Pattern (BigQueryException) (検索以外) */
	private static Pattern PATTERN_INPUTERROR_EXEC = Pattern.compile(REGEX_INPUTERROR_EXEC);

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(BigQueryUtil.class);

	/**
	 * DatasetIdオブジェクトからデータセット名を取得.
	 * @param datasetId DatasetIdオブジェクト
	 * @return データセット名
	 */
	public static String getDatasetId(DatasetId datasetId) {
		if (datasetId == null) {
			return null;
		}
		return datasetId.getDataset();
	}

	/**
	 * BigQueryへのアクセスログを出力するかどうかを取得.
	 * @return BigQueryへのアクセスログを出力する場合true
	 */
	public static boolean isEnableAccessLog() {
		return TaggingEnvUtil.getSystemPropBoolean(
				BigQueryConst.BIGQUERY_ENABLE_ACCESSLOG, false) &&
				logger.isDebugEnabled();
	}

	/**
	 * 実行開始ログ編集
	 * @param command コマンド
	 * @param datasetId データセット名
	 * @return 実行開始ログ文字列
	 */
	public static String getStartLog(String command, String datasetId) {
		return getStartLog(command, datasetId, null);
	}

	/**
	 * 実行開始ログ編集
	 * @param command コマンド
	 * @param datasetId データセット名
	 * @param tableId テーブル名
	 * @return 実行開始ログ文字列
	 */
	public static String getStartLog(String command, String datasetId, String tableId) {
		StringBuilder sb = new StringBuilder();
		sb.append("[BigQuery] ");
		sb.append(command);
		sb.append(" start");
		if (datasetId != null) {
			sb.append(" : datasetId=");
			sb.append(datasetId);
		}
		if (tableId != null) {
			sb.append(", tableId=");
			sb.append(tableId);
		}
		return sb.toString();
	}

	/**
	 * 実行開始ログ編集
	 * @param command コマンド
	 * @param datasetId データセット名
	 * @param startTime Redisコマンド実行直前の時間
	 * @return 実行終了ログ文字列
	 */
	public static String getEndLog(String command, String datasetId, long startTime) {
		return getEndLog(command, datasetId, null, startTime);
	}

	/**
	 * 実行開始ログ編集
	 * @param command コマンド
	 * @param datasetId データセット名
	 * @param tableId テーブル名
	 * @param startTime Redisコマンド実行直前の時間
	 * @return 実行終了ログ文字列
	 */
	public static String getEndLog(String command, String datasetId, String tableId, long startTime) {
		StringBuilder sb = new StringBuilder();
		sb.append("[BigQuery] ");
		sb.append(command);
		sb.append(" end");
		if (datasetId != null) {
			sb.append(" : datasetId=");
			sb.append(datasetId);
		}
		if (tableId != null) {
			sb.append(", tableId=");
			sb.append(tableId);
		}
		sb.append(getElapsedTimeLog(startTime));
		return sb.toString();
	}

	/**
	 * 経過時間ログ表記を取得
	 * @param startTime 開始時間
	 * @return 経過時間ログ表記
	 */
	private static String getElapsedTimeLog(long startTime) {
		long finishTime = new Date().getTime();
		long time = finishTime - startTime;
		StringBuilder sb = new StringBuilder();
		sb.append(" - ");
		sb.append(time);
		sb.append("ms");
		return sb.toString();
	}

	/**
	 * BigQueryException (extends RuntimeException) をIOExceptionに変換する.
	 * コネクションエラーは ConnectionException に変換する。
	 * @param e 例外
	 * @param name コネクション情報名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return IOException 変換した例外
	 */
	public static IOException convertException(BigQueryException e,
			String name, RequestInfo requestInfo, ConnectionInfo connectionInfo) {
		String errorMessage = e.getMessage();
		StringBuilder sb = new StringBuilder();
		sb.append(LogUtil.getRequestInfoStr(requestInfo));
		sb.append("[convertException] ");
		sb.append(e.getClass().getName());
		sb.append(" : ");
		sb.append(errorMessage);
		logger.warn(sb.toString());
		if (logger.isDebugEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[convertException] " + errorMessage, e);
		}
		if (isConnectionError(e)) {
			connectionInfo.close(name);
			return doConnectionError(e, name, errorMessage);
		} else {
			return new IOException(errorMessage, e);
		}
	}

	/**
	 * BigQueryException (extends RuntimeException) をIOExceptionに変換する.
	 * コネクションエラーは ConnectionException に変換する。
	 * @param e 例外
	 * @param name コネクション情報名
	 * @param errorMessage エラーメッセージ
	 * @return IOException 変換した例外
	 */
	private static IOException doConnectionError(BigQueryException e, String name,
			String errorMessage) {
		return new ConnectionException(errorMessage, e);
	}

	/**
	 * 例外がコネクションエラーかどうか判定.
	 * @param e 例外
	 * @return 例外がコネクションエラーの場合true;
	 */
	public static boolean isConnectionError(Throwable e) {
		Throwable tmp = e;
		if (e instanceof IOException && e.getCause() != null) {
			tmp = e.getCause();
		}

		// コネクションエラー判定

		return false;
	}

	/**
	 * BigQueryアクセス失敗時リトライ総数を取得.
	 * @return BigQueryアクセス失敗時リトライ総数
	 */
	public static int getBigQueryRetryCount() {
		return TaggingEnvUtil.getSystemPropInt(BigQueryConst.BIGQUERY_RETRY_COUNT,
				BigQueryConst.BIGQUERY_RETRY_COUNT_DEFAULT);
	}

	/**
	 * BigQueryアクセス失敗時リトライ総数を取得.
	 * @return BigQueryアクセス失敗時リトライ総数
	 */
	public static int getBigQueryRetryWaitmillis() {
		return TaggingEnvUtil.getSystemPropInt(BigQueryConst.BIGQUERY_RETRY_WAITMILLIS,
				BigQueryConst.BIGQUERY_RETRY_WAITMILLIS_DEFAULT);
	}

	/**
	 * BigQueryアクセス失敗時リトライ総数を取得.
	 * @return BigQueryアクセス失敗時リトライ総数
	 */
	public static int getBigQueryCallableRetryCount() {
		return TaggingEnvUtil.getSystemPropInt(BigQueryConst.BIGQUERY_CALLABLE_RETRY_COUNT,
				BigQueryConst.BIGQUERY_CALLABLE_RETRY_COUNT_DEFAULT);
	}

	/**
	 * BigQueryアクセス失敗時リトライ総数を取得.
	 * @return BigQueryアクセス失敗時リトライ総数
	 */
	public static int getBigQueryCallableRetryWaitmillis() {
		return TaggingEnvUtil.getSystemPropInt(BigQueryConst.BIGQUERY_CALLABLE_RETRY_WAITMILLIS,
				BigQueryConst.BIGQUERY_CALLABLE_RETRY_WAITMILLIS_DEFAULT);
	}

	/**
	 * DateをBigQueryの日時項目格納形式に変換する。
	 * 正規形式
	 * YYYY-[M]M-[D]D[( |T)[H]H:[M]M:[S]S[.DDDDDD]]
	 *   YYYY: 4 桁の年
	 *   [M]M: 1 桁または 2 桁の月
	 *   [D]D: 1 桁または 2 桁の日
	 *   ( |T): スペースまたは T 区切り文字
	 *   [H]H: 1 桁または 2 桁の時（有効な値は 00～23）
	 *   [M]M: 1 桁または 2 桁の分（有効な値は 00～59）
	 *   [S]S: 1 桁または 2 桁の秒（有効な値は 00～59）
	 *   [.DDDDDD]: 最大で小数第 6 位まで（最大でマイクロ秒の精度）
	 * @param date 日時
	 * @return BigQueryの日時項目格納形式文字列
	 */
	public static String convertDateForBigquery(Date date) {
		return DateUtil.getDateTimeFormat(date, BigQueryConst.BQDATE_FORMAT);
	}

	/**
	 * BigQueryから取得したdatetime型の戻り値をDate型に変換する.
	 * @param datetime BigQueryから取得したdatetime型の値
	 * @return Date型
	 */
	public static Date convertDateFromBigquery(Object datetime)
	throws IOException {
		if (datetime instanceof Date) {
			return (Date)datetime;

		} else if (datetime instanceof String) {
			// yyyy-MM-ddTHH:mm:ss.SSSSSS 形式であれば、マイクロ秒3桁を削って返す。
			String datetimeStr = (String)datetime;
			int len = datetimeStr.length();
			if (len > 23) {
				int idx = datetimeStr.lastIndexOf(".");
				String millis = datetimeStr.substring(idx + 1);
				int millisLen = millis.length();
				if (millisLen > 3) {
					datetimeStr = datetimeStr.substring(0, len - (millisLen - 3));
				}
			}
			try {
				return DateUtil.getDate(datetimeStr);
			} catch (ParseException e) {
				throw new IOException(e);
			}

		} else {
			// Date型に変換不可
			return null;
		}
	}

	/**
	 * BigQueryからのエラー情報を解析して例外をスローする.
	 * @param errors エラー情報
	 */
	public static void throwBigQueryException(Map<Long, List<BigQueryError>> errors) {
		if (errors == null || errors.isEmpty()) {
			return;
		}

		// エラー変換
		StringBuilder msg = new StringBuilder();
		boolean isFirst = true;
		for (Map.Entry<Long, List<BigQueryError>> mapEntry : errors.entrySet()) {
			long num = mapEntry.getKey() + 1;
			if (isFirst) {
				isFirst = false;
			} else {
				msg.append(", ");
				msg.append("[");
				msg.append(num);
				msg.append("]");
			}
			List<BigQueryError> bigQueryErrors = mapEntry.getValue();
			boolean isFirst2 = true;
			for (BigQueryError bigQueryError : bigQueryErrors) {
				if (isFirst2) {
					isFirst2 = false;
				} else {
					msg.append(", ");
				}
				msg.append(bigQueryError.getMessage());
				msg.append(" location=");
				msg.append(bigQueryError.getLocation());
				msg.append(" reason=");
				msg.append(bigQueryError.getReason());
			}
		}
		String errorMsg = msg.toString();
		if (logger.isDebugEnabled()) {
			logger.debug("[throwBigQueryException] " + errorMsg);
		}
		throw new IllegalParameterException(errorMsg);
	}

	/**
	 * BigQueryExceptionの入力エラー判定.
	 * @param e BigQueryException
	 * @param isSelect 検索の場合true
	 * @param requestInfo リクエスト情報
	 * @return 入力エラーの場合true
	 */
	public static boolean isInputError(BigQueryException e, boolean isSelect, RequestInfo requestInfo) {
		Pattern pattern = null;
		if (isSelect) {
			pattern = PATTERN_INPUTERROR_SELECT;
		} else {
			pattern = PATTERN_INPUTERROR_EXEC;
		}

		if (e != null && e.getMessage() != null) {
			Matcher matcher = pattern.matcher(e.getMessage());
			boolean find = matcher.find();
			if (find) {
				return true;
			}
		}
		// 例外のcauseについても同じ判定を行う。
		Throwable cause = e.getCause();
		if (cause != null && cause.getMessage() != null) {
			Matcher matcher = pattern.matcher(cause.getMessage());
			return matcher.find();
		}
		return false;
	}

	/**
	 * BigQueryExceptionの設定エラー判定.
	 * @param e BigQueryException
	 * @param requestInfo リクエスト情報
	 * @return 設定エラーの場合true
	 */
	public static boolean isSettingError(BigQueryException e, RequestInfo requestInfo) {
		if (e != null && e.getMessage() != null) {
			Matcher matcher = PATTERN_SETTINGERROR.matcher(e.getMessage());
			return matcher.find();
		}
		return false;
	}

	/**
	 * IOExceptionの設定エラー判定.
	 * @param e IOException
	 * @param requestInfo リクエスト情報
	 * @return 設定エラーの場合true
	 */
	public static boolean isSettingError(IOException e, RequestInfo requestInfo) {
		if (e != null) {
			if (e instanceof com.fasterxml.jackson.core.JsonParseException) {
				// 秘密鍵JSONのパースエラー
				return true;
			}
			if (e.getMessage() != null) {
				Matcher matcher = PATTERN_SETTINGERROR_IO.matcher(e.getMessage());
				return matcher.find();
			}
		}
		return false;
	}

	/**
	 * リトライエラーの場合trueを返す.
	 * @param e BigQueryException
	 * @param requestInfo リクエスト情報
	 * @return リトライエラーの場合true
	 */
	public static boolean isRetryError(BigQueryException e, RequestInfo requestInfo) {
		if (e != null && e.getMessage() != null) {
			if (logger.isDebugEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[isRetryError] ");
				sb.append(e.getClass().getSimpleName());
				sb.append(": ");
				sb.append(e.getMessage());
				sb.append(" isRetryable = ");
				sb.append(e.isRetryable());
				logger.debug(sb.toString());
			}
			String msg = e.getMessage();
			// タイムアウトの場合リトライ対象とする。(仮)
			// メッセージに"try again"とあればリトライ対象とする。
			// メッセージに"Bad Gateway"とあればリトライ対象とする。
			if (msg.indexOf("timeout") > -1 || msg.indexOf("Timeout") > -1 ||
					msg.indexOf("try again") > -1 ||
					msg.indexOf("Bad Gateway") > -1) {
				return true;
			}
			return e.isRetryable();
		}
		return false;
	}

	/**
	 * リトライエラーの場合trueを返す.
	 * @param e IOException
	 * @param requestInfo リクエスト情報
	 * @return リトライエラーの場合true
	 */
	public static boolean isRetryError(IOException e, RequestInfo requestInfo) {
		if (e != null && e.getMessage() != null) {
			if (logger.isDebugEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[isRetryError] ");
				sb.append(e.getClass().getSimpleName());
				sb.append(": ");
				sb.append(e.getMessage());
				logger.debug(sb.toString());
			}
			String msg = e.getMessage();
			// タイムアウトの場合リトライ対象とする。(仮)
			// メッセージに"try again"とあればリトライ対象とする。
			if (msg.indexOf("timeout") > -1 || msg.indexOf("Timeout") > -1 ||
					msg.indexOf("try again") > -1) {
				return true;
			}
		}
		return false;
	}

	/**
	 * リトライエラーの場合trueを返す.
	 * @param e InterruptedException
	 * @param requestInfo リクエスト情報
	 * @return リトライエラーの場合true
	 */
	public static boolean isRetryError(InterruptedException e, RequestInfo requestInfo) {
		if (e != null && e.getMessage() != null) {
			if (logger.isDebugEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[isRetryError] ");
				sb.append(e.getClass().getSimpleName());
				sb.append(": ");
				sb.append(e.getMessage());
				logger.debug(sb.toString());
			}
			String msg = e.getMessage();
			// タイムアウトの場合リトライ対象とする。(仮)
			// メッセージに"try again"とあればリトライ対象とする。
			if (msg.indexOf("timeout") > -1 || msg.indexOf("Timeout") > -1 ||
					msg.indexOf("try again") > -1) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 終了ジョブのエラー判定.
	 * waitForで終了させたジョブが対象
	 * @param queryJob ジョブ
	 */
	public static void checkJob(BigQueryJob queryJob)
	throws IOException, ReflexBigQueryException {
		if (queryJob == null) {
			throw new IOException("BigQueryJob no longer exists");
		} else if (queryJob.getStatus().getError() != null) {
			// You can also look at queryJob.getStatus().getExecutionErrors() for all
			// errors, not just the latest one.
			throw new IOException(queryJob.getStatus().getError().toString());
		}
	}

	/**
	 * BigQuery情報からロケーションを取得.
	 * 設定がなければデフォルトのロケーションを返却
	 * @param bigQueryInfo BigQuery情報
	 * @return ロケーション
	 */
	public static String getLocation(BigQueryInfo bigQueryInfo) {
		String location = bigQueryInfo.getLocation();
		if (StringUtils.isBlank(location)) {
			location = TaggingEnvUtil.getSystemProp(
					BigQueryConst.BIGQUERY_DEFAULT_LOCATION,
					BigQueryConst.LOCATION_DEFAULT);
		}
		return location;
	}

	/**
	 * Metalistを取得.
	 * @param serviceName サービス名
	 * @return Metalist
	 */
	public static List<Meta> getMetalist(String serviceName) {
		return TaggingEnvUtil.getResourceMapper(serviceName).getMetalist();
	}

	/**
	 * クラスのタイプが標準ATOM項目かどうかを判定.
	 * @param field フィールド
	 * @return フィールドが標準ATOM項目の場合true
	 */
	public static boolean isAtomField(java.lang.reflect.Field field) {
		String name = field.getName();
		Class<?> type = field.getType();
		for (java.lang.reflect.Field atomField : BigQueryConst.ATOM_FIELDS) {
			if (name.equals(atomField.getName()) &&
					type.equals(atomField.getType())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * クラスのタイプが階層でないかどうかを判定.
	 * @param type タイプ
	 * @return クラスのタイプが階層でない場合true
	 */
	public static boolean isPlainType(Class<?> type) {
		if (String.class.equals(type) || Integer.class.equals(type) ||
				Long.class.equals(type) || Date.class.equals(type) ||
				Boolean.class.equals(type) || Double.class.equals(type) ||
				Float.class.equals(type)) {
			return true;
		}
		return false;
	}

	/**
	 * テンプレートの型からBigQueryフィールドの型を取得.
	 * @param meta Meta
	 * @return BigQueryフィールドの型
	 */
	public static LegacySQLTypeName getLegacySQLTypeName(Meta meta) {
		return LegacySQLTypeName.valueOf(meta.bigquerytype);
	}

	/**
	 * オブジェクトの型からBigQueryフィールドの型を取得.
	 * @param obj オブジェクト
	 * @return BigQueryフィールドの型
	 */
	public static LegacySQLTypeName getLegacySQLTypeName(Object obj) {
		if (obj instanceof String) {
			return LegacySQLTypeName.STRING;
		} else if (obj instanceof Boolean) {
			return LegacySQLTypeName.BOOLEAN;
		} else if (obj instanceof Long || obj instanceof Integer || obj instanceof Short) {
			return LegacySQLTypeName.INTEGER;
		} else if (obj instanceof Double || obj instanceof Float) {
			return LegacySQLTypeName.FLOAT;
		} else if (obj instanceof Date) {
			return LegacySQLTypeName.DATETIME;
		} else {
			return LegacySQLTypeName.STRING;
		}
	}

	/**
	 * BigQueryの項目タイプからclassを取得
	 * @param sqlTypeName BigQueryの項目タイプ
	 * @return クラス
	 */
	public static Class<?> getClass(LegacySQLTypeName sqlTypeName) {
		String bqFieldType = sqlTypeName.name();
		if (AtomConst.META_BIGQUERYTYPE_STRING.equals(bqFieldType)) {
			return String.class;
		} else if (AtomConst.META_BIGQUERYTYPE_BOOLEAN.equals(bqFieldType)) {
			return Boolean.class;
		} else if (AtomConst.META_BIGQUERYTYPE_INTEGER.equals(bqFieldType)) {
			return Long.class;
		} else if (AtomConst.META_BIGQUERYTYPE_FLOAT.equals(bqFieldType)) {
			return Double.class;
		} else if (AtomConst.META_BIGQUERYTYPE_DATE.equals(bqFieldType)) {
			return Date.class;
		}
		return String.class;
	}

	/**
	 * 現在日時をマイクロ秒まで取得し、文字列を返します.
	 * @return yyyy-MM-dd'T'HH:mm:ss.SSSSSS 形式の現在日時
	 */
	public static String getNowByMicrosec() {
		Date now = new Date();
		return convertDateForBigquery(now) + DateUtil.getMicrosecondStr();
	}
	
	/**
	 * BigQueryのフィールド名につける接頭辞を取得。接頭辞をつけない場合は空文字を返す。
	 * @param tableFieldName テーブルとなるフィールド名
	 * @param addPrefixToFieldname フィールド名に接頭辞(第一階層)を付加する場合true
	 * @return BigQueryのフィールド名につける接頭辞。接頭辞をつけない場合は空文字。
	 */
	public static String getPrefixFieldname(String tableFieldName, boolean addPrefixToFieldname) {
		if (addPrefixToFieldname) {
			return tableFieldName + "_";
		} else {
			return "";
		}
	}

	/**
	 * テーブル定義オブジェクトを生成
	 * @param tableFieldName テーブルフィールド名
	 * @param metalist Metalist
	 * @param prefixFieldname フィールド名に接頭辞(第一階層)を付加する場合、その値。付加しない場合は空文字。
	 * @param serviceName サービス名
	 * @return テーブル定義オブジェクト
	 */
	public static TableDefinition createTableDefinition(String tableFieldName, List<Meta> metalist,
			String prefixFieldname, String serviceName) {
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

		// テンプレート項目
		String prefix = tableFieldName + ".";
		int startIdx = prefix.length();
		for (Meta meta : metalist) {
			String name = meta.name;
			if (name.startsWith(prefix) && name.indexOf(".", startIdx) == -1) {
				// テーブルのフィールド対象
				String bqFieldName = prefixFieldname + name.substring(startIdx);
				bqField = Field.of(bqFieldName, BigQueryUtil.getLegacySQLTypeName(meta),
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

}
