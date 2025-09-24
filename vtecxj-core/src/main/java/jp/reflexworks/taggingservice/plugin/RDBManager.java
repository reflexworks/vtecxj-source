package jp.reflexworks.taggingservice.plugin;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;

/**
 * RDB検索・更新インターフェース
 */
public interface RDBManager extends ReflexPlugin {

	/**
	 * RDB対し更新SQLを実行する.
	 * AutoCommitのデフォルトはtrue。
	 * AutoCommit=trueの場合、即時コミットされる。ただしSQLリスト分はトランザクションで括られる。
	 * AutoCommit=falseの場合、本処理だけではRDBに結果がコミットされない。別途commitメソッドの実行が必要。
	 * @param sqls SQLリスト
	 * @param isBulk 大量データ登録の場合にtrueを指定。trueの場合、このメソッド内でトランザクションの操作を行わない。
	 *             (AutoCommit ONでトランザクションで括らず大量データを登録していくことを想定)
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return (1) SQLデータ操作言語(DML)文の場合は行数、(2)何も返さないSQL文の場合は0 のリスト
	 */
	public int[] execSql(String[] sqls, boolean isBulk, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

	/**
	 * 非同期でRDB対し更新SQLを実行する.
	 * AutoCommitの設定はtrueになる。SQLリスト分はトランザクションで括られ、最後にコミットされる。
	 * @param sqls SQL
	 * @param isBulk 大量データ登録の場合にtrueを指定。trueの場合、このメソッド内でトランザクションの操作を行わない。
	 *             (AutoCommit ONでトランザクションで括らず大量データを登録していくことを想定)
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return (1) SQLデータ操作言語(DML)文の場合は行数、(2)何も返さないSQL文の場合は0 のリストのFuture
	 */
	public Future<int[]> execSqlAsync(String[] sqls, boolean isBulk, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

	/**
	 * RDBに対し指定された検索SQLを実行し結果を取得する.
	 * @param sql SQL
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 検索結果(キー:select項目名、値:検索結果、のリスト)
	 */
	public List<Map<String, Object>> querySql(String sql, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;
	
	/**
	 * AutoCommit設定を取得する.
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return autoCommit true:AutoCommit、false:明示的なコミットが必要
	 */
	public boolean getAutoCommit(ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;
	
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
	throws IOException, TaggingException;

	/**
	 * コミットを実行する.
	 * AutoCommit=falseの場合に有効。
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void commit(ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;
	
	/**
	 * ロールバックを実行する.
	 * AutoCommit=falseの場合に有効。
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void rollback(ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

}
