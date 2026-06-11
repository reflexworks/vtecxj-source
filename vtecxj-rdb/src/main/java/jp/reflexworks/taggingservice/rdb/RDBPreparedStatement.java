package jp.reflexworks.taggingservice.rdb;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;

/**
 * プリコンパイルされたSQL文をデータベースに送るためのPreparedStatementオブジェクト.
 */
public class RDBPreparedStatement {

	/** RDBプリペアードステートメント */
	private PreparedStatement pstmt;

	/** サービス名 */
	private String serviceName;

	/** リクエスト情報(ログ用) */
	private RequestInfo requestInfo;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ.
	 * @param pstmt RDBプリペアードステートメント
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 */
	public RDBPreparedStatement(PreparedStatement pstmt, String serviceName, RequestInfo requestInfo) {
		this.pstmt = pstmt;
		this.serviceName = serviceName;
		this.requestInfo = requestInfo;
	}

	/**
	 * プリペアードステートメントを取得.
	 * @return RDBプリペアードステートメント
	 */
	public PreparedStatement getPreparedStatement() {
		return pstmt;
	}

	/**
	 * クローズ処理.
	 */
	public void close() {
		if (pstmt == null) {
			return;
		}
		int numRetries = ReflexRDBUtil.getRdbRetryCount(serviceName);
		int waitMillis = ReflexRDBUtil.getRdbRetryWaitmillis(serviceName);
		for (int r = 0; r <= numRetries; r++) {
			try {
				pstmt.close();
				pstmt = null;
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
	 * 指定されたパラメータを指定されたObjectに設定する.
	 * @param parameterIndex バインドパラメータのインデックス (1から開始)
	 * @param x バインドするオブジェクト
	 */
	public void setObject(int parameterIndex, Object x)
	throws IOException, TaggingException {
		int numRetries = ReflexRDBUtil.getRdbRetryCount(serviceName);
		int waitMillis = ReflexRDBUtil.getRdbRetryWaitmillis(serviceName);
		for (int r = 0; r <= numRetries; r++) {
			try {
				pstmt.setObject(parameterIndex, x);
				return;

			} catch (SQLException se) {
				ReflexRDBUtil.checkRetry(se, r, numRetries, waitMillis, "setObject(" + parameterIndex + ")", requestInfo);
			}
		}
		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}

	/**
	 * この PreparedStatement オブジェクトの SQL 文を実行する.
	 * SQL文は INSERT文、UPDATE文、DELETE文、またはDDL文のような何も返さないSQL文の場合がある。
	 * @return (1) SQLデータ操作言語(DML)文の場合は行数、(2)何も返さないSQL文の場合は0
	 */
	public int executeUpdate()
	throws IOException, TaggingException {
		int numRetries = ReflexRDBUtil.getRdbRetryCount(serviceName);
		int waitMillis = ReflexRDBUtil.getRdbRetryWaitmillis(serviceName);
		for (int r = 0; r <= numRetries; r++) {
			try {
				return pstmt.executeUpdate();

			} catch (SQLException se) {
				ReflexRDBUtil.checkRetry(se, r, numRetries, waitMillis, "executeUpdate", requestInfo);
			}
		}
		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}

	/**
	 * この PreparedStatement オブジェクトの SQL クエリーを実行し、クエリーによって生成された ResultSet オブジェクトを返す.
	 * @return クエリーによって生成されたデータを含むResultSetオブジェクト。nullにはならない
	 */
	public RDBResultSet executeQuery()
	throws IOException, TaggingException {
		int numRetries = ReflexRDBUtil.getRdbRetryCount(serviceName);
		int waitMillis = ReflexRDBUtil.getRdbRetryWaitmillis(serviceName);
		for (int r = 0; r <= numRetries; r++) {
			try {
				ResultSet rset = pstmt.executeQuery();
				return new RDBResultSet(rset, serviceName, requestInfo);

			} catch (SQLException se) {
				ReflexRDBUtil.checkRetry(se, r, numRetries, waitMillis, "executeQuery", requestInfo);
			}
		}
		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}

}
