package jp.reflexworks.taggingservice.api;

import java.io.IOException;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.exception.TaggingException;

/**
 * SDK呼び出しビジネスロジックインターフェース
 */
public interface ReflexExternalSdkCaller {

	/**
	 * SDK呼び出し.
	 * @param args 引数
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 実行結果
	 */
	public FeedBase call(String[] args, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

}
