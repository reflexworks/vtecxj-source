package jp.reflexworks.taggingservice.bdb;

/**
 * クエリ検索の取得済みデータ判定インタフェース.
 * 全文検索インデックス検索で、前ページまでに取得済みのデータを読み飛ばすために使用する。
 */
@FunctionalInterface
public interface BDBQueryFetchedChecker {

	/**
	 * 検索されたデータが前ページまでに取得済みかどうか判定する.
	 * このリクエスト内で初めて検索された値についてのみ呼び出される。
	 * @param foundKeyStr 検索されたキー
	 * @param val 検索された値
	 * @return 取得済みの場合true
	 */
	boolean isFetched(String foundKeyStr, String val);

}
