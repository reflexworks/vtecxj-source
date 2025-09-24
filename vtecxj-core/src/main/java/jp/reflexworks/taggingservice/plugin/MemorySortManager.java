package jp.reflexworks.taggingservice.plugin;

import java.io.IOException;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.exception.TaggingException;

/**
 * インメモリソート管理インターフェース.
 */
public interface MemorySortManager extends ReflexPlugin {

	/**
	 * インメモリソート.
	 * @param param 検索条件
	 * @param conditionName 検索条件文字列 (セッション格納時のキーに使用)
	 * @param tmpAuth 対象サービスの認証情報
	 * @param reflexContext reflexContext
	 */
	public void sort(RequestParam param, String conditionName, ReflexAuthentication tmpAuth,
			ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * インメモリソートリクエスト.
	 * インメモリソートTaskQueueから呼び出される想定のメソッド
	 * @param param 検索条件
	 * @param conditionName 検索条件文字列 (セッション格納時のキーに使用)
	 * @param tmpAuth 対象サービスの認証情報
	 * @param reflexContext reflexContext
	 */
	public void requestSort(RequestParam param, String conditionName, ReflexAuthentication tmpAuth,
			ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * インメモリソートされた結果の指定ページFeedを取得.
	 * @param param 検索条件
	 * @param num ページ番号
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 指定ページFeed
	 */
	public FeedBase getPage(RequestParam param, int num, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

	/**
	 * インメモリソート実行可能かどうか判定.
	 * インメモリソートの環境設定が行われていればtrueを返す。
	 * @param serviceName サービス名
	 * @return インメモリソートが実行可能であればtrue
	 */
	public boolean isEnabledMemorySort(String serviceName)
	throws IOException, TaggingException;

}
