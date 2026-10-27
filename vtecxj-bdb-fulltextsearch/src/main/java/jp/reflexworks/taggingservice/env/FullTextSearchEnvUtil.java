package jp.reflexworks.taggingservice.env;

/**
 * 設定値取得ユーティリティ
 */
public class FullTextSearchEnvUtil {

	/**
	 * コンストラクタ(生成不可).
	 */
	private FullTextSearchEnvUtil() {}

	/**
	 * 全文検索インデックスの最大文字数を取得.
	 * @return 全文検索インデックスの最大文字数
	 */
	public static int getFulltextindexWordcountLimit() {
		return ReflexEnvUtil.getSystemPropInt(
				FullTextSearchEnvConst.FULLTEXTINDEX_WORDCOUNT_LIMIT,
				FullTextSearchEnvConst.FULLTEXTINDEX_WORDCOUNT_LIMIT_DEFAULT);
	}
}
