package jp.reflexworks.taggingservice.rdb;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.PooledConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.conn.ReflexConnection;
import jp.reflexworks.taggingservice.exception.TaggingException;

/**
 * RDBコネクション.
 */
public class RDBConnection implements ReflexConnection<Connection> {
	
	/** RDBプールコネクション */
	private PooledConnection pooledConn;
	/** RDBコネクション */
	private Connection conn;
	
	/** サービス名 */
	private String serviceName;
	
	/** リクエスト情報(ログ用) */
	private RequestInfo requestInfo;
	
	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());
	
	/**
	 * コンストラクタ.
	 * @param conn RDBコネクション
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 */
	public RDBConnection(Connection conn, String serviceName, RequestInfo requestInfo) {
		this.conn = conn;
		this.serviceName = serviceName;
		this.requestInfo = requestInfo;
	}
	
	/**
	 * コンストラクタ.
	 * @param pooledConn RDBプールコネクション
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 */
	public RDBConnection(PooledConnection pooledConn, String serviceName, RequestInfo requestInfo) {
		this.pooledConn = pooledConn;
		this.serviceName = serviceName;
		this.requestInfo = requestInfo;
		this.conn = getConnectionByPool();
	}

	/**
	 * コネクションを取得.
	 * @return RDBコネクション
	 */
	public Connection getConnection() {
		return conn;
	}
	
	/**
	 * プールコネクションからRDBコネクションを取得.
	 * @return RDBコネクション
	 */
	private Connection getConnectionByPool() {
		int numRetries = ReflexRDBUtil.getRdbRetryCount(serviceName);
		int waitMillis = ReflexRDBUtil.getRdbRetryWaitmillis(serviceName);
		for (int r = 0; r <= numRetries; r++) {
			try {
				return pooledConn.getConnection();
				
			} catch (SQLException se) {
				try {
					ReflexRDBUtil.checkRetry(se, r, numRetries, waitMillis, "getConnectionByPool", requestInfo);
				} catch (IOException | TaggingException ce) {
					StringBuilder sb = new StringBuilder();
					sb.append("[getConnectionByPool] Error occured. ");
					sb.append(ce.getClass().getName());
					sb.append(": ");
					sb.append(ce.getMessage());
					logger.warn(sb.toString(), ce);
				}
			}
		}
		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}
	
	/**
	 * クローズ処理.
	 */
	public void close() {
		// RDBコネクションのクローズ
		if (conn != null) {
			int numRetries = ReflexRDBUtil.getRdbRetryCount(serviceName);
			int waitMillis = ReflexRDBUtil.getRdbRetryWaitmillis(serviceName);
			for (int r = 0; r <= numRetries; r++) {
				try {
					conn.close();
					if (logger.isTraceEnabled()) {
						logger.debug("[close] succeeded.");
					}
					conn = null;
					break;
					
				} catch (SQLException se) {
					try {
						ReflexRDBUtil.checkRetry(se, r, numRetries, waitMillis, "close", requestInfo);
					} catch (IOException | TaggingException ce) {
						StringBuilder sb = new StringBuilder();
						sb.append("[close] Error occured. ");
						sb.append(ce.getClass().getName());
						sb.append(": ");
						sb.append(ce.getMessage());
						logger.warn(sb.toString(), ce);
						break;
					}
				}
			}
		}
		// プールコネクションのクローズ
		closePooledConn();
	}
	
	/**
	 * プールコネクションのクローズ処理
	 */
	private void closePooledConn() {
		if (pooledConn == null) {
			return;
		}
		int numRetries = ReflexRDBUtil.getRdbRetryCount(serviceName);
		int waitMillis = ReflexRDBUtil.getRdbRetryWaitmillis(serviceName);
		for (int r = 0; r <= numRetries; r++) {
			try {
				pooledConn.close();
				if (logger.isTraceEnabled()) {
					logger.debug("[closePooledConn] succeeded.");
				}
				pooledConn = null;
				return;
				
			} catch (SQLException se) {
				try {
					ReflexRDBUtil.checkRetry(se, r, numRetries, waitMillis, "closePooledConn", requestInfo);
				} catch (IOException | TaggingException ce) {
					StringBuilder sb = new StringBuilder();
					sb.append("[closePooledConn] Error occured. ");
					sb.append(ce.getClass().getName());
					sb.append(": ");
					sb.append(ce.getMessage());
					logger.warn(sb.toString(), ce);
					return;
				}
			}
		}
	}
	
	/**
	 * AutoCommit設定を取得する.
	 * @return autoCommit true:AutoCommit、false:明示的なコミットが必要
	 */
	public boolean getAutoCommit() 
	throws IOException, TaggingException {
		int numRetries = ReflexRDBUtil.getRdbRetryCount(serviceName);
		int waitMillis = ReflexRDBUtil.getRdbRetryWaitmillis(serviceName);
		for (int r = 0; r <= numRetries; r++) {
			try {
				return conn.getAutoCommit();

			} catch (SQLException se) {
				ReflexRDBUtil.checkRetry(se, r, numRetries, waitMillis, "getAutoCommit", requestInfo);
			}
		}
		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}
	
	/**
	 * AutoCommitを設定する.
	 * デフォルトはtrue。
	 * execSqlにおいて非同期処理(async=true)の場合、この設定は無効になる。(非同期処理の場合AutoCommit=true)
	 * @param autoCommit true:AutoCommit、false:明示的なコミットが必要
	 */
	public void setAutoCommit(boolean autoCommit)
	throws IOException, TaggingException {
		int numRetries = ReflexRDBUtil.getRdbRetryCount(serviceName);
		int waitMillis = ReflexRDBUtil.getRdbRetryWaitmillis(serviceName);
		for (int r = 0; r <= numRetries; r++) {
			try {
				conn.setAutoCommit(autoCommit);
				return;

			} catch (SQLException se) {
				ReflexRDBUtil.checkRetry(se, r, numRetries, waitMillis, "setAutoCommit", requestInfo);
			}
		}
		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}
	
	/**
	 * コミットを実行する.
	 */
	public void commit()
	throws IOException, TaggingException {
		int numRetries = ReflexRDBUtil.getRdbRetryCount(serviceName);
		int waitMillis = ReflexRDBUtil.getRdbRetryWaitmillis(serviceName);
		for (int r = 0; r <= numRetries; r++) {
			try {
				conn.commit();
				return;

			} catch (SQLException se) {
				ReflexRDBUtil.checkRetry(se, r, numRetries, waitMillis, "commit", requestInfo);
			}
		}
		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}
	
	/**
	 * ロールバックを実行する.
	 */
	public void rollback()
	throws IOException, TaggingException {
		int numRetries = ReflexRDBUtil.getRdbRetryCount(serviceName);
		int waitMillis = ReflexRDBUtil.getRdbRetryWaitmillis(serviceName);
		for (int r = 0; r <= numRetries; r++) {
			try {
				conn.rollback();
				return;

			} catch (SQLException se) {
				ReflexRDBUtil.checkRetry(se, r, numRetries, waitMillis, "rollback", requestInfo);
			}
		}
		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}
	
	/**
	 * SQL文をデータベースに送るためのStatementオブジェクトを生成する.
	 * @return Statementオブジェクト
	 */
	public RDBStatement createStatement() 
	throws IOException, TaggingException {
		int numRetries = ReflexRDBUtil.getRdbRetryCount(serviceName);
		int waitMillis = ReflexRDBUtil.getRdbRetryWaitmillis(serviceName);
		for (int r = 0; r <= numRetries; r++) {
			try {
				Statement stmt = conn.createStatement();
				return new RDBStatement(stmt, serviceName, requestInfo);

			} catch (SQLException se) {
				ReflexRDBUtil.checkRetry(se, r, numRetries, waitMillis, "createStatement", requestInfo);
			}
		}
		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}

}
