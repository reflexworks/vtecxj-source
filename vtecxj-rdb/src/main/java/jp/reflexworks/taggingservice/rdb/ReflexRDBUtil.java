package jp.reflexworks.taggingservice.rdb;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import javax.sql.ConnectionPoolDataSource;
import javax.sql.PooledConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexStatic;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.reflexworks.taggingservice.exception.StaticDuplicatedException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.rdb.postgres.PostgresUtil;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.RetryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * RDBユーティリティ.
 */
public final class ReflexRDBUtil {

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(ReflexRDBUtil.class);

	/**
	 * コンストラクタ.
	 */
	private ReflexRDBUtil() { }

	/**
	 * 初期処理
	 * @return RDB操作のためのstatic情報
	 */
	public static ReflexRDBEnv init() {
		// RDB用static情報をメモリに格納
		ReflexRDBEnv rdbEnv = new ReflexRDBEnv();
		try {
			ReflexStatic.setStatic(ReflexRDBConst.STATIC_NAME_RDB, rdbEnv);
			// Redis用static情報新規生成
			rdbEnv.init();

		} catch (StaticDuplicatedException e) {
			// Redis用static情報は他の処理ですでに生成済み
			rdbEnv = getRdbEnv();
		}
		return rdbEnv;
	}

	/**
	 * RDB操作のためのstatic情報クローズ処理
	 */
	public static void close() {
		ReflexRDBEnv rdbEnv = getRdbEnv();
		if (rdbEnv != null) {
			rdbEnv.close();
		}
	}

	/**
	 * RDB操作のためのstatic情報を取得.
	 * @return RDB操作のためのstatic情報
	 */
	public static ReflexRDBEnv getRdbEnv() {
		return (ReflexRDBEnv)ReflexStatic.getStatic(ReflexRDBConst.STATIC_NAME_RDB);
	}
	
	/**
	 * コネクションプールデータソースを取得.
	 * @param serviceName サービス名
	 * @return コネクションプールデータソース
	 */
	public static ConnectionPoolDataSource getConnectionPoolDataSource(String serviceName) {
		ReflexRDBEnv rdbEnv = getRdbEnv();
		if (rdbEnv != null) {
			return rdbEnv.getDataSource(serviceName);
		}
		return null;
	}
	
	/**
	 * RDBコネクションを取得.
	 * コネクション情報に存在しない場合は、RDBコネクションを生成する。
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return RDBコネクション
	 */
	public static RDBConnection getRDBConnection(String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		return getRDBConnection(true, serviceName, requestInfo, connectionInfo);
	}

	/**
	 * RDBコネクションを取得.
	 * @param createIfNoExist コネクション情報に存在しない場合にRDBコネクションを生成する場合true
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return RDBコネクション
	 */
	public static RDBConnection getRDBConnection(boolean createIfNoExist, String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// リクエスト・スレッド内で取得済みのコネクションがあれば使い回す。
		RDBConnection rdbConn = (RDBConnection)connectionInfo.get(
				ReflexRDBConst.CONNECTION_INFO_RDB);
		if (rdbConn != null) {
			return rdbConn;
		} else if (createIfNoExist) {
			return createConnection(serviceName, requestInfo, connectionInfo);
		}
		return null;
	}
	
	/**
	 * RDBコネクションを生成する。(コネクションプールから取得)
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return RDBコネクション
	 */
	private static RDBConnection createConnection(String serviceName, 
			RequestInfo requestInfo, ConnectionInfo connectionInfo) 
	throws IOException, TaggingException {
		ConnectionPoolDataSource dataSource = getConnectionPoolDataSource(serviceName);
		RDBConnection rdbConn = null;
		if (dataSource != null) {
			// コネクションプール設定あり
			PooledConnection pooledConn = getConnectionByPool(dataSource, serviceName, requestInfo);
			rdbConn = new RDBConnection(pooledConn, serviceName, requestInfo);
		} else {
			// コネクションプール設定なし
			Connection conn = getConnectionByDriverManager(serviceName, requestInfo);
			rdbConn = new RDBConnection(conn, serviceName, requestInfo);
		}
		connectionInfo.put(ReflexRDBConst.CONNECTION_INFO_RDB, rdbConn);
		return rdbConn;
	}
	
	/**
	 * コネクションプールを取得
	 * @param dataSource データソース
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @return プールコネクション
	 */
	private static PooledConnection getConnectionByPool(ConnectionPoolDataSource dataSource, 
			String serviceName, RequestInfo requestInfo) 
	throws IOException, TaggingException {
		int numRetries = ReflexRDBUtil.getRdbRetryCount(serviceName);
		int waitMillis = ReflexRDBUtil.getRdbRetryWaitmillis(serviceName);
		for (int r = 0; r <= numRetries; r++) {
			try {
				return dataSource.getPooledConnection();

			} catch (SQLException se) {
				checkRetry(se, r, numRetries, waitMillis, "createConnectionByPool", requestInfo);
			}
		}
		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}
	
	/**
	 * ドライバーマネージャーからRDBコネクションを取得.
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @return RDBコネクション
	 */
	private static Connection getConnectionByDriverManager(String serviceName, 
			RequestInfo requestInfo) 
	throws IOException, TaggingException {
		String jdbcUrl = PostgresUtil.getJdbcUrl(serviceName);
		String user = TaggingEnvUtil.getProp(serviceName, ReflexRDBConst.RDB_USER, null);
		String pass = TaggingEnvUtil.getProp(serviceName, ReflexRDBConst.RDB_PASS, null);
		if (StringUtils.isBlank(jdbcUrl) && StringUtils.isBlank(user) && StringUtils.isBlank(pass)) {
			// JDBC設定なし
			throw new InvalidServiceSettingException("Database settings are required.");
		}
		CheckUtil.checkNotNull(jdbcUrl, "RDB host and database setting");
		CheckUtil.checkNotNull(jdbcUrl, "RDB user setting");
		CheckUtil.checkNotNull(jdbcUrl, "RDB pass setting");
		
		// コネクション取得
		int numRetries = ReflexRDBUtil.getRdbRetryCount(serviceName);
		int waitMillis = ReflexRDBUtil.getRdbRetryWaitmillis(serviceName);
		for (int r = 0; r <= numRetries; r++) {
			try {
				return DriverManager.getConnection(jdbcUrl, user, pass);

			} catch (SQLException se) {
				checkRetry(se, r, numRetries, waitMillis, "createConnectionByDriverManager", 
						requestInfo);
			}
		}
		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}

	/**
	 * リトライチェック
	 * @param e 例外
	 * @param r リトライ回数
	 * @param numRetries リトライ総数
	 * @param waitMillis リトライ時の待ち時間(ミリ秒)
	 * @param type 処理タイプ(ログ出力用)
	 * @param requestInfo リクエスト情報
	 */
	public static void checkRetry(SQLException e, int r, int numRetries, int waitMillis,
			String type, RequestInfo requestInfo)
	throws IOException, TaggingException {

		// 設定エラーかどうか
		if (isSettingError(e, requestInfo)) {
			if (logger.isDebugEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[");
				sb.append(type);
				sb.append("] ");
				sb.append("RDB setting error: ");
				sb.append(e.getClass().getName());
				sb.append(" SQLState=");
				sb.append(e.getSQLState());
				sb.append(" ");
				sb.append(e.getMessage());
				logger.debug(sb.toString());
			}
			throw new InvalidServiceSettingException(e);
		}

		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[");
			sb.append(type);
			sb.append("] ");
			sb.append(e.getClass().getName());
			sb.append(" SQLState=");
			sb.append(e.getSQLState());
			sb.append(" ");
			sb.append(e.getMessage());
			logger.debug(sb.toString());
		}

		// 入力エラーかどうか
		checkInputError(e, requestInfo);

		// リトライエラーかどうか
		boolean retryable = isRetryError(e, requestInfo);

		// その他はIOエラー
		if (!retryable) {
			throw newIOException(e);
		}
		// リトライ回数を超えるとエラー
		if (r >= numRetries) {
			throw newIOException(e);
		}
		if (logger.isDebugEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[");
			sb.append(type);
			sb.append("] SQLState=");
			sb.append(e.getSQLState());
			sb.append(" ");
			sb.append(RetryUtil.getRetryLog(e, r));
			logger.debug(sb.toString());
		}
		RetryUtil.sleep(waitMillis);
	}

	/**
	 * SQLExceptionの設定エラー判定.
	 * @param e IOException
	 * @param requestInfo リクエスト情報
	 * @return 設定エラーの場合true
	 */
	public static boolean isSettingError(SQLException e, RequestInfo requestInfo) {
		return PostgresUtil.isSettingError(e, requestInfo);
	}

	/**
	 * リトライエラーの場合trueを返す.
	 * @param e IOException
	 * @param requestInfo リクエスト情報
	 * @return リトライエラーの場合true
	 */
	public static boolean isRetryError(SQLException e, RequestInfo requestInfo) {
		return PostgresUtil.isRetryError(e, requestInfo);
	}
	/**
	 * SQLExceptionの入力エラー判定.
	 * 入力エラーの場合、適切な例外に変換してスローする。
	 * @param e
	 * @param requestInfo
	 */
	public static void checkInputError(SQLException e, RequestInfo requestInfo) 
	throws TaggingException {
		PostgresUtil.checkInputError(e, requestInfo);
	}

	/**
	 * RDBアクセス失敗時リトライ総数を取得.
	 * @return RDBアクセス失敗時リトライ総数
	 */
	public static int getRdbRetryCount(String serviceName) {
		int defVal = TaggingEnvUtil.getSystemPropInt(ReflexRDBConst.RDB_RETRY_COUNT,
				ReflexRDBConst.RDB_RETRY_COUNT_DEFAULT);
		try {
			return TaggingEnvUtil.getPropInt(serviceName, ReflexRDBConst.RDB_RETRY_COUNT, 
					defVal);
		} catch (InvalidServiceSettingException e) {
			StringBuilder sb = new StringBuilder();
			sb.append("[getRdbRetryCount] serviceName=");
			sb.append(serviceName);
			sb.append(" InvalidServiceSettingException: ");
			sb.append(e.getMessage());
			logger.warn(sb.toString());
			return defVal;
		}
	}

	/**
	 * RDBアクセス失敗時リトライ総数を取得.
	 * @param serviceName サービス名
	 * @return RDBアクセス失敗時リトライ総数
	 */
	public static int getRdbRetryWaitmillis(String serviceName) {
		int defVal = TaggingEnvUtil.getSystemPropInt(ReflexRDBConst.RDB_RETRY_WAITMILLIS,
				ReflexRDBConst.RDB_RETRY_WAITMILLIS_DEFAULT);
		try {
			return TaggingEnvUtil.getPropInt(serviceName, ReflexRDBConst.RDB_RETRY_WAITMILLIS, 
					defVal);
		} catch (InvalidServiceSettingException e) {
			StringBuilder sb = new StringBuilder();
			sb.append("[getRdbRetryWaitmillis] serviceName=");
			sb.append(serviceName);
			sb.append(" InvalidServiceSettingException: ");
			sb.append(e.getMessage());
			logger.warn(sb.toString());
			return defVal;
		}
	}
	
	/**
	 * SQLExceptionをIOExceptionに変換.
	 * SQLStateをメッセージにする。
	 * @param e SQLException
	 * @return IOException
	 */
	public static IOException newIOException(SQLException e) {
		StringBuilder sb = new StringBuilder();
		sb.append("SQLState=");
		sb.append(e.getSQLState());
		sb.append(" ");
		sb.append(e.getMessage());
		return new IOException(sb.toString(), e);
	}

}
