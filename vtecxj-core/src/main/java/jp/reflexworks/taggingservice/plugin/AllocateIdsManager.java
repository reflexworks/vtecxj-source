package jp.reflexworks.taggingservice.plugin;

import java.io.IOException;
import java.util.List;

import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;

/**
 * 採番管理インタフェース.
 * このクラスを実装するクラスは起動時に1つのインスタンスが生成され、staticに保持されます。
 */
public interface AllocateIdsManager extends ReflexPlugin {

	/**
	 * 採番.
	 * @param uri URI
	 * @param num 採番数。0は現在番号の取得。
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 採番結果
	 */
	public List<String> allocateIds(String uri, int num, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

	/**
	 * 全ての採番情報を削除.
	 * サービス削除時に使用
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void deleteAll(String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

}
