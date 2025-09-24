package jp.reflexworks.taggingservice.blogic;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.RDBManager;
import jp.reflexworks.taggingservice.util.CheckUtil;

/**
 * RDB操作ビジネスロジック
 */
public class RDBBlogic {

	/**
	 * RDBへ更新系SQLを実行する.
	 * INSERT、UPDATE、DELETE、CREATE TABLE等を想定。
	 * SQLインジェクション対応は呼び出し元で行ってください。
	 * @param sqls SQLリスト
	 * @param isBulk 大量データ登録の場合にtrueを指定。trueの場合、このメソッド内でトランザクションの操作を行わない。
	 *             (AutoCommit ONでトランザクションで括らず大量データを登録していくことを想定)
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return (1) SQLデータ操作言語(DML)文の場合は行数、(2)何も返さないSQL文の場合は0 のリスト
	 */
	public int[] execRdb(String[] sqls, boolean isBulk, ReflexAuthentication auth,  
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// 入力チェック
		CheckUtil.checkNotNull(sqls, "SQL");
		for (String sql : sqls) {
			CheckUtil.checkNotNull(sql, "SQL");
		}

		RDBManager rdbManager = TaggingEnvUtil.getRDBManager();
		if (rdbManager == null) {
			throw new InvalidServiceSettingException("RDB manager is nothing.");
		}
		return rdbManager.execSql(sqls, isBulk, auth, requestInfo, connectionInfo);
	}

	/**
	 * 非同期でRDBへ更新系SQLを実行する.
	 * INSERT、UPDATE、DELETE、CREATE TABLE等を想定。
	 * SQLインジェクション対応は呼び出し元で行ってください。
	 * @param sqls SQLリスト
	 * @param isBulk 大量データ登録の場合にtrueを指定。trueの場合、このメソッド内でトランザクションの操作を行わない。
	 *             (AutoCommit ONでトランザクションで括らず大量データを登録していくことを想定)
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return (1) SQLデータ操作言語(DML)文の場合は行数、(2)何も返さないSQL文の場合は0 のリストのFUture
	 */
	public Future<int[]> execRdbAsync(String[] sqls, boolean isBulk, ReflexAuthentication auth,  
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// 入力チェック
		CheckUtil.checkNotNull(sqls, "SQL");
		for (String sql : sqls) {
			CheckUtil.checkNotNull(sql, "SQL");
		}

		RDBManager rdbManager = TaggingEnvUtil.getRDBManager();
		if (rdbManager == null) {
			throw new InvalidServiceSettingException("RDB manager is nothing.");
		}
		return rdbManager.execSqlAsync(sqls, isBulk, auth, requestInfo, connectionInfo);
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
		RDBManager rdbManager = TaggingEnvUtil.getRDBManager();
		if (rdbManager == null) {
			throw new InvalidServiceSettingException("RDB manager is nothing.");
		}
		return rdbManager.getAutoCommit(auth, requestInfo, connectionInfo);
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
		RDBManager rdbManager = TaggingEnvUtil.getRDBManager();
		if (rdbManager == null) {
			throw new InvalidServiceSettingException("RDB manager is nothing.");
		}
		rdbManager.setAutoCommit(autoCommit, auth, requestInfo, connectionInfo);
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
		RDBManager rdbManager = TaggingEnvUtil.getRDBManager();
		if (rdbManager == null) {
			throw new InvalidServiceSettingException("RDB manager is nothing.");
		}
		rdbManager.commit(auth, requestInfo, connectionInfo);
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
		RDBManager rdbManager = TaggingEnvUtil.getRDBManager();
		if (rdbManager == null) {
			throw new InvalidServiceSettingException("RDB manager is nothing.");
		}
		rdbManager.rollback(auth, requestInfo, connectionInfo);
	}

	/**
	 * RDBに対しSQLを実行し、結果を取得する.
	 * @param sql SQL
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 検索結果
	 */
	public List<Map<String, Object>> queryRdb(String sql, ReflexAuthentication auth,  
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// 入力チェック
		CheckUtil.checkNotNull(sql, "SQL");

		RDBManager rdbManager = TaggingEnvUtil.getRDBManager();
		if (rdbManager == null) {
			throw new InvalidServiceSettingException("RDB manager is nothing.");
		}
		return rdbManager.querySql(sql, auth, requestInfo, connectionInfo);
	}

}
