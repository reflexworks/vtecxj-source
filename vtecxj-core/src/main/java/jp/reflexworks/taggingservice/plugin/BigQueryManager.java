package jp.reflexworks.taggingservice.plugin;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;

/**
 * BigQueryにEntryを登録・検索する管理インターフェース
 */
public interface BigQueryManager extends ReflexPlugin {

	/**
	 * BigQueryにEntryを登録する.
	 * @param feed Feed
	 * @param tableNames エンティティの第一階層と異なる名前をテーブル名にする場合に指定.
	 *                   キー:エンティティの第一階層名、値:BigQuery登録テーブル名
	 * @param async 非同期の場合true、同期の場合false
	 * @param addPrefixToFieldname フィールド名に接頭辞(第一階層)を付加する
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return Future 同期の場合はnull
	 */
	public Future postBq(FeedBase feed, Map<String, String> tableNames, boolean async,
			boolean addPrefixToFieldname, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

	/**
	 * BigQueryへデータ登録.
	 * Feedでなく、テーブル名と値のMapを指定するメソッド
	 * @param tableName テーブル名
	 * @param list 行 (キー:項目名、値:値) のリスト
	 * @param async 非同期の場合true、同期の場合false
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return Future 同期の場合はnull
	 */
	public Future postBq(String tableName, List<Map<String, Object>> list,
			boolean async, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

	/**
	 * BigQueryに削除データを登録する.
	 * @param uris 削除キーリスト
	 * @param tableNames エンティティの第一階層と異なる名前をテーブル名にする場合に指定.
	 *                   キー:エンティティの第一階層名、値:BigQuery登録テーブル名
	 * @param async 非同期の場合true、同期の場合false
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return Future 同期の場合はnull
	 */
	public Future deleteBq(String[] uris, Map<String, String> tableNames, boolean async,
			ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

	/**
	 * BigQueryに対し指定された検索SQLを実行し結果を取得する.
	 * @param sql SQL
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 検索結果
	 */
	public List<Map<String, Object>> queryBq(String sql, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

	/**
	 * BigQueryへデータ登録する際の入力チェック.
	 * エラーの場合例外がスローされる。
	 * @param feed Feed。deleteの場合null。
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void checkBq(FeedBase feed, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

}
