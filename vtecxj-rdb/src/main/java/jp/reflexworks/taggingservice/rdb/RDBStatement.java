package jp.reflexworks.taggingservice.rdb;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;

/**
 * SQL文をデータベースに送るためのStatementオブジェクト.
 */
public class RDBStatement {
	
	/** RDBステートメント */
	private Statement stmt;
	
	/** サービス名 */
	private String serviceName;
	
	/** リクエスト情報(ログ用) */
	private RequestInfo requestInfo;
	
	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());
	
	/**
	 * コンストラクタ.
	 * @param stmt RDBステートメント
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 */
	public RDBStatement(Statement stmt, String serviceName, RequestInfo requestInfo) {
		this.stmt = stmt;
		this.serviceName = serviceName;
		this.requestInfo = requestInfo;
	}
	
	/**
	 * ステートメントを取得.
	 * @return RDBステートメント
	 */
	public Statement getStatement() {
		return stmt;
	}
	
	/**
	 * クローズ処理.
	 */
	public void close() {
		if (stmt == null) {
			return;
		}
		int numRetries = ReflexRDBUtil.getRdbRetryCount(serviceName);
		int waitMillis = ReflexRDBUtil.getRdbRetryWaitmillis(serviceName);
		for (int r = 0; r <= numRetries; r++) {
			try {
				stmt.close();
				stmt = null;
				return;
				
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
					return;
				}
			}
		}
	}
	
	/**
	 * 複数の結果を返す可能性のある指定されたSQL文を実行する.
	 * @param sql SQL
	 * @return 最初の結果がResultSetオブジェクトの場合はtrue。更新カウントであるか、または結果がない場合はfalse
	 */
	public boolean execute(String sql) 
	throws IOException, TaggingException {
		int numRetries = ReflexRDBUtil.getRdbRetryCount(serviceName);
		int waitMillis = ReflexRDBUtil.getRdbRetryWaitmillis(serviceName);
		for (int r = 0; r <= numRetries; r++) {
			try {
				return stmt.execute(sql);

			} catch (SQLException se) {
				ReflexRDBUtil.checkRetry(se, r, numRetries, waitMillis, "execute", requestInfo);
			}
		}
		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}
	
	/**
	 * 指定されたSQL文を実行する.
	 * SQL文は、INSERT文、UPDATE文、DELETE文、またはSQL DDL文のような何も返さないSQL文の場合がある。
	 * @param sql SQL
	 * @return (1) SQLデータ操作言語(DML)文の場合は行数、(2)何も返さないSQL文の場合は0
	 */
	public int executeUpdate(String sql) 
	throws IOException, TaggingException {
		int numRetries = ReflexRDBUtil.getRdbRetryCount(serviceName);
		int waitMillis = ReflexRDBUtil.getRdbRetryWaitmillis(serviceName);
		for (int r = 0; r <= numRetries; r++) {
			try {
				return stmt.executeUpdate(sql);

			} catch (SQLException se) {
				ReflexRDBUtil.checkRetry(se, r, numRetries, waitMillis, "executeUpdate", requestInfo);
			}
		}
		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}
	
	/**
	 * 単一のResultSetオブジェクトを返す、指定されたSQL文を実行する.
	 * @param sql SQL
	 * @return 指定されたクエリーによって作成されたデータを含むResultSetオブジェクト。nullにはならない
	 */
	public RDBResultSet executeQuery(String sql) 
	throws IOException, TaggingException {
		int numRetries = ReflexRDBUtil.getRdbRetryCount(serviceName);
		int waitMillis = ReflexRDBUtil.getRdbRetryWaitmillis(serviceName);
		for (int r = 0; r <= numRetries; r++) {
			try {
				ResultSet rset = stmt.executeQuery(sql);
				return new RDBResultSet(rset, serviceName, requestInfo);

			} catch (SQLException se) {
				ReflexRDBUtil.checkRetry(se, r, numRetries, waitMillis, "executeQuery", requestInfo);
			}
		}
		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}

}
