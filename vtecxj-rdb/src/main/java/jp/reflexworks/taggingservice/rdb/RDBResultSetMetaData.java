package jp.reflexworks.taggingservice.rdb;

import java.io.IOException;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;

/**
 * ResultSetオブジェクトの列の型とプロパティに関する情報を取得するのに使用できるオブジェクト.
 */
public class RDBResultSetMetaData {
	
	/** ResultSetオブジェクトの列の型とプロパティに関する情報取得のためのオブジェクト */
	private ResultSetMetaData rsmeta;
	
	/** サービス名 */
	private String serviceName;

	/** リクエスト情報(ログ用) */
	private RequestInfo requestInfo;
	
	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());
	
	/**
	 * コンストラクタ.
	 * @param stmt ResultSetオブジェクトの列の型とプロパティに関する情報取得のためのオブジェクト
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 */
	public RDBResultSetMetaData(ResultSetMetaData rsmeta, String serviceName, 
			RequestInfo requestInfo) {
		this.rsmeta = rsmeta;
		this.serviceName = serviceName;
		this.requestInfo = requestInfo;
	}
	
	/**
	 * ResultSetオブジェクトの列の型とプロパティに関する情報取得のためのオブジェクトを取得.
	 * @return ResultSetオブジェクトの列の型とプロパティに関する情報取得のためのオブジェクト
	 */
	public ResultSetMetaData getResultSetMetaData() {
		return rsmeta;
	}
	
	/**
	 * ResultSetオブジェクトの列数を返却.
	 * @return このResultSetオブジェクトの列数
	 */
	public int getColumnCount() 
	throws IOException, TaggingException {
		int numRetries = ReflexRDBUtil.getRdbRetryCount(serviceName);
		int waitMillis = ReflexRDBUtil.getRdbRetryWaitmillis(serviceName);
		for (int r = 0; r <= numRetries; r++) {
			try {
				return rsmeta.getColumnCount();
				
			} catch (SQLException se) {
				ReflexRDBUtil.checkRetry(se, r, numRetries, waitMillis, "getColumnCount", requestInfo);
			}
		}
		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}
	
	/**
	 * 指定された列の名前を取得する.
	 * @param column 列数 (1から開始)
	 * @return 指定された列の名前
	 */
	public String getColumnName(int column) 
	throws IOException, TaggingException {
		int numRetries = ReflexRDBUtil.getRdbRetryCount(serviceName);
		int waitMillis = ReflexRDBUtil.getRdbRetryWaitmillis(serviceName);
		for (int r = 0; r <= numRetries; r++) {
			try {
				return rsmeta.getColumnName(column);

			} catch (SQLException se) {
				ReflexRDBUtil.checkRetry(se, r, numRetries, waitMillis, "getColumnName", requestInfo);
			}
		}
		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}
	
	/**
	 * Javaクラスの完全指定された名前を返す.
	 * @param column 列数 (1から開始)
	 * @return 指定された列の値を取り出すためにResultSet.getObjectメソッドによって使用されるJavaプログラミング言語のクラスの完全指定された名前。
	 */
	public String getColumnClassName(int column) 
	throws IOException, TaggingException {
		int numRetries = ReflexRDBUtil.getRdbRetryCount(serviceName);
		int waitMillis = ReflexRDBUtil.getRdbRetryWaitmillis(serviceName);
		for (int r = 0; r <= numRetries; r++) {
			try {
				return rsmeta.getColumnClassName(column);

			} catch (SQLException se) {
				ReflexRDBUtil.checkRetry(se, r, numRetries, waitMillis, "getColumnClassName", requestInfo);
			}
		}
		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}
	
	/**
	 * 指定された列の表名を取得する.
	 * @param column 列数 (1から開始)
	 * @return 指定された列の表名
	 */
	public String getTableName(int column) 
	throws IOException, TaggingException {
		int numRetries = ReflexRDBUtil.getRdbRetryCount(serviceName);
		int waitMillis = ReflexRDBUtil.getRdbRetryWaitmillis(serviceName);
		for (int r = 0; r <= numRetries; r++) {
			try {
				return rsmeta.getTableName(column);

			} catch (SQLException se) {
				ReflexRDBUtil.checkRetry(se, r, numRetries, waitMillis, "getTableName", requestInfo);
			}
		}
		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}

}
