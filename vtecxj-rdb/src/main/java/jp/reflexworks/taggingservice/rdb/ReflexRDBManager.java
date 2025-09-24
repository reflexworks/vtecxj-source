package jp.reflexworks.taggingservice.rdb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.RDBManager;
import jp.reflexworks.taggingservice.taskqueue.TaskQueueUtil;

/**
 * RDB管理クラス.
 */
public class ReflexRDBManager implements RDBManager {

	/**
	 * 初期処理.
	 */
	@Override
	public void init() {
		// 初期処理
		ReflexRDBUtil.init();
	}

	/**
	 * シャットダウン時の処理.
	 */
	@Override
	public void close() {
		ReflexRDBUtil.close();
	}

	/**
	 * RDB対し更新SQLを実行する.
	 * INSERT、UPDATE、DELETE、CREATE TABLE等を想定。
	 * SQLインジェクション対応は呼び出し元で行うことを想定。
	 * @param sqls SQLリスト
	 * @param isBulk 大量データ登録の場合にtrueを指定。trueの場合、このメソッド内でトランザクションの操作を行わない。
	 *             (AutoCommit ONでトランザクションで括らず大量データを登録していくことを想定)
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return (1) SQLデータ操作言語(DML)文の場合は行数、(2)何も返さないSQL文の場合は0 のリスト
	 */
	@Override
	public int[] execSql(String[] sqls, boolean isBulk, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		return execSqlProc(sqls, isBulk, auth, requestInfo, connectionInfo);
	}

	/**
	 * 非同期でRDB対し更新SQLを実行する.
	 * INSERT、UPDATE、DELETE、CREATE TABLE等を想定。
	 * SQLインジェクション対応は呼び出し元で行うことを想定。
	 * @param sqls SQLリスト
	 * @param isBulk 大量データ登録の場合にtrueを指定。trueの場合、このメソッド内でトランザクションの操作を行わない。
	 *             (AutoCommit ONでトランザクションで括らず大量データを登録していくことを想定)
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return (1) SQLデータ操作言語(DML)文の場合は行数、(2)何も返さないSQL文の場合は0 のリストのFuture
	 */
	@Override
	public Future<int[]> execSqlAsync(String[] sqls, boolean isBulk, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// 非同期処理
		RDBExecSqlCallable callable = new RDBExecSqlCallable(sqls, isBulk);
		return (Future<int[]>)TaskQueueUtil.addTask(callable, 0, auth, 
				requestInfo, connectionInfo);
	}

	/**
	 * RDB対し更新SQLを実行する.
	 * @param sqls SQLリスト
	 * @param isBulk 大量データ登録の場合にtrueを指定。trueの場合、このメソッド内でトランザクションの操作を行わない。
	 *             (AutoCommit ONを想定)
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return (1) SQLデータ操作言語(DML)文の場合は行数、(2)何も返さないSQL文の場合は0 のリスト
	 */
	int[] execSqlProc(String[] sqls, boolean isBulk, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		boolean isCommit = false;
		boolean isBulkAutoCommit = false;
		RDBConnection conn = ReflexRDBUtil.getRDBConnection(serviceName, 
				requestInfo, connectionInfo);
		if (conn == null) {
			throw new IOException("database connection is null.");
		}
		
		// close処理は行わない。(ConnectionInfoに格納されるので、スレッド終了時に行われる。)
		// トランザクションをサーバサイドJSで管理する場合、closeするとロールバックされてしまう。
		
		if (isBulk && !conn.getAutoCommit()) {
			// bulk処理でAutoCommit=falseの場合、一旦trueにする。
			conn.setAutoCommit(true);
			isBulkAutoCommit = true;
		} else if (sqls.length > 1 && !isBulk && conn.getAutoCommit()) {
			// SQLが複数あり、AutoCommitの場合は、トランザクションを切る。
			conn.setAutoCommit(false);
			isCommit = true;
		}
		
		try {
			int[] results = new int[sqls.length];
			int i = 0;
			for (String sql : sqls) {
				RDBStatement stmt = null;
				try {
					stmt = conn.createStatement();
					results[i] = stmt.executeUpdate(sql);
					i++;
					
				} finally {
					if (stmt != null) {
						stmt.close();
					}
				}
			}
			if (isCommit) {
				conn.commit();
			}
		
			return results;

		} finally {
			if (isBulkAutoCommit) {
				conn.setAutoCommit(false);	// 設定を元に戻す
			}
			if (isCommit) {
				conn.setAutoCommit(true);	// 設定を元に戻す
			}
		}
	}
	
	/**
	 * AutoCommit設定を取得する.
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return autoCommit true:AutoCommit、false:明示的なコミットが必要
	 */
	public boolean getAutoCommit(ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		RDBConnection conn = ReflexRDBUtil.getRDBConnection(serviceName, requestInfo, connectionInfo);
		return conn.getAutoCommit();
	}
	
	/**
	 * AutoCommitを設定する.
	 * デフォルトはtrue。
	 * execSqlにおいて非同期処理(async=true)の場合、この設定は無効になる。(非同期処理の場合AutoCommit=true)
	 * @param autoCommit true:AutoCommit、false:明示的なコミットが必要
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void setAutoCommit(boolean autoCommit, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		RDBConnection conn = ReflexRDBUtil.getRDBConnection(serviceName, requestInfo, connectionInfo);
		conn.setAutoCommit(autoCommit);
	}

	/**
	 * コミットを実行する.
	 * AutoCommit=falseの場合に有効。
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void commit(ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		RDBConnection conn = ReflexRDBUtil.getRDBConnection(serviceName, requestInfo, connectionInfo);
		conn.commit();
	}
	
	/**
	 * ロールバックを実行する.
	 * AutoCommit=falseの場合に有効。
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void rollback(ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		RDBConnection conn = ReflexRDBUtil.getRDBConnection(serviceName, requestInfo, connectionInfo);
		conn.rollback();
	}

	/**
	 * RDBに対し指定された検索SQLを実行し結果を取得する.
	 * @param sql SQL
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 検索結果(キー:select項目名、値:検索結果、のリスト)
	 */
	@Override
	public List<Map<String, Object>> querySql(String sql, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		RDBStatement stmt = null;
		try {
			RDBConnection conn = ReflexRDBUtil.getRDBConnection(serviceName, 
					requestInfo, connectionInfo);
			stmt = conn.createStatement();
			RDBResultSet rset = stmt.executeQuery(sql);
			RDBResultSetMetaData rsmeta = rset.getMetaData();
			int columnCnt = rsmeta.getColumnCount();
			List<Map<String, Object>> result = new ArrayList<>();
			// SELECT結果の受け取り
			while (rset.next()) {
				Map<String, Object> row = new LinkedHashMap<>();
				for (int i = 1; i <= columnCnt; i++) {
					String name = rsmeta.getColumnName(i);
					Object val = getResultSetObject(rset, rsmeta, i);
					row.put(name, val);
				}
				result.add(row);
			}
			return result;
			
		} finally {
			if (stmt != null) {
				stmt.close();
			}
		}
	}
	
	/**
	 * 検索結果をテーブル型に合わせて取得する.
	 * @param rset ResultSet
	 * @param rsmeta RDBResultSetMetaData
	 * @param i 処理中の列数
	 * @return 指定された行・列の検索結果
	 */
	private Object getResultSetObject(RDBResultSet rset, RDBResultSetMetaData rsmeta, int i) 
	throws IOException, TaggingException {
		return rset.getObject(i);
	}

}
