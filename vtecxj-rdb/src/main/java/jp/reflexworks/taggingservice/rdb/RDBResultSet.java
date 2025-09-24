package jp.reflexworks.taggingservice.rdb;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;

/**
 * データベースの結果セットを表すデータの表.
 * 通常、データベースに照会する文を実行することによって生成される。
 */
public class RDBResultSet {
	
	/** RDB結果セット */
	private ResultSet rset;
	
	/** サービス名 */
	private String serviceName;

	/** リクエスト情報(ログ用) */
	private RequestInfo requestInfo;
	
	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());
	
	/**
	 * コンストラクタ.
	 * @param stmt RDB結果セット
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 */
	public RDBResultSet(ResultSet rset, String serviceName, RequestInfo requestInfo) {
		this.rset = rset;
		this.serviceName = serviceName;
		this.requestInfo = requestInfo;
	}
	
	/**
	 * RDB結果セットを取得.
	 * @return RDB結果セット
	 */
	public ResultSet getResultSet() {
		return rset;
	}
	
	/**
	 * クローズ処理.
	 */
	public void close() {
		if (rset == null) {
			return;
		}
		int numRetries = ReflexRDBUtil.getRdbRetryCount(serviceName);
		int waitMillis = ReflexRDBUtil.getRdbRetryWaitmillis(serviceName);
		for (int r = 0; r <= numRetries; r++) {
			try {
				rset.close();
				rset = null;
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
	 * カーソルを現在の位置から順方向に1行移動する.
	 * @return 新しい現在の行が有効である場合はtrue、行がそれ以上存在しない場合はfalse
	 */
	public boolean next() 
	throws IOException, TaggingException {
		int numRetries = ReflexRDBUtil.getRdbRetryCount(serviceName);
		int waitMillis = ReflexRDBUtil.getRdbRetryWaitmillis(serviceName);
		for (int r = 0; r <= numRetries; r++) {
			try {
				return rset.next();

			} catch (SQLException se) {
				ReflexRDBUtil.checkRetry(se, r, numRetries, waitMillis, "next", requestInfo);
			}
		}
		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}
	
	/**
	 * このResultSetオブジェクトの列の数、型、およびプロパティを取得する.
	 * @return このResultSetオブジェクトの列の記述
	 */
	public RDBResultSetMetaData getMetaData() 
	throws IOException, TaggingException {
		int numRetries = ReflexRDBUtil.getRdbRetryCount(serviceName);
		int waitMillis = ReflexRDBUtil.getRdbRetryWaitmillis(serviceName);
		for (int r = 0; r <= numRetries; r++) {
			try {
				ResultSetMetaData rsmeta = rset.getMetaData();
				return new RDBResultSetMetaData(rsmeta, serviceName, requestInfo);

			} catch (SQLException se) {
				ReflexRDBUtil.checkRetry(se, r, numRetries, waitMillis, "getMetaData", requestInfo);
			}
		}
		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}
	
	/**
	 * このResultSetオブジェクトの現在行にある指定された列の値を、Javaプログラミング言語のObjectとして取り出す.
	 * @param columnIndex 指定列数(最初は1)
	 * @return このResultSetオブジェクトの、指定された列の値
	 */
	public Object getObject(int columnIndex) 
	throws IOException, TaggingException {
		int numRetries = ReflexRDBUtil.getRdbRetryCount(serviceName);
		int waitMillis = ReflexRDBUtil.getRdbRetryWaitmillis(serviceName);
		for (int r = 0; r <= numRetries; r++) {
			try {
				return rset.getObject(columnIndex);

			} catch (SQLException se) {
				ReflexRDBUtil.checkRetry(se, r, numRetries, waitMillis, "getObject(" + columnIndex + ")", requestInfo);
			}
		}
		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}

}
