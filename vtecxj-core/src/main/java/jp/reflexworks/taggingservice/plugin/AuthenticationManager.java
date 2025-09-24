package jp.reflexworks.taggingservice.plugin;

import java.io.IOException;
import java.util.List;

import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.sys.SystemContext;

/**
 * 認証処理インターフェース
 */
public interface AuthenticationManager extends ReflexPlugin {

	/**
	 * 認証.
	 * @param req リクエスト
	 * @return 認証情報
	 */
	public ReflexAuthentication autheticate(ReflexRequest req)
	throws IOException, TaggingException;

	/**
	 * 認証後の処理.
	 * 仮認証に対応。
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param auth 認証情報
	 * @return 処理を継続する場合true
	 */
	public boolean afterAutheticate(ReflexRequest req, ReflexResponse resp, ReflexAuthentication auth)
	throws IOException, TaggingException;

	/**
	 * セッションIDをレスポンスに設定
	 * @param req リクエスト
	 * @param resp レスポンス
	 */
	public void setSessionIdToResponse(ReflexRequest req, ReflexResponse resp)
	throws IOException, TaggingException;

	/**
	 * セッションIDをレスポンスから削除
	 * @param req リクエスト
	 * @param resp レスポンス
	 */
	public void deleteSessionIdFromResponse(ReflexRequest req, ReflexResponse resp)
	throws IOException, TaggingException;

	/**
	 * サービス連携認証.
	 * 対象サービス名とそのAPIKeyのチェック、及びログインユーザの登録チェックを行う。
	 * @param targetServiceName 対象サービス名
	 * @param targetApiKey 対象サービスのAPIKey
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 対象サービスでのログインユーザ認証情報
	 */
	public ReflexAuthentication authenticateByCooperationService(
			String targetServiceName, String targetApiKey,
			ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

	/**
	 * 認証情報の複製.
	 * ReflexContextでExternalを設定するために使用する。
	 * @param auth 認証情報
	 * @return 複製した認証オブジェクト
	 */
	public ReflexAuthentication copyAuth(ReflexAuthentication auth);

	/**
	 * 認証情報の生成.
	 * @param account アカウント
	 * @param uid UID
	 * @param sessionId セッションID
	 * @param authType 認証方法
	 * @param serviceName サービス名
	 * @return 生成した認証情報
	 */
	public ReflexAuthentication createAuth(String account, String uid, String sessionId,
			String authType, String serviceName);

	/**
	 * 認証情報の生成.
	 * @param account アカウント
	 * @param uid UID
	 * @param sessionId セッションID
	 * @param linkToken リンクトークン
	 * @param authType 認証方法
	 * @param serviceName サービス名
	 * @return 生成した認証情報
	 */
	public ReflexAuthentication createAuth(String account, String uid, String sessionId,
			String linkToken, String authType, String serviceName);

	/**
	 * 認証情報の生成.
	 * @param account アカウント
	 * @param uid UID
	 * @param sessionId セッションID
	 * @param linkToken リンクトークン
	 * @param authType 認証方法
	 * @param groups 参加グループリスト
	 * @param serviceName サービス名
	 * @return 生成した認証情報
	 */
	public ReflexAuthentication createAuth(String account, String uid, String sessionId,
			String linkToken, String authType, List<String> groups, String serviceName);

	/**
	 * 認証情報にグループを設定
	 * @param auth 認証情報
	 * @param systemContext SystemContext
	 */
	public void setGroups(ReflexAuthentication auth, SystemContext systemContext)
	throws IOException, TaggingException;

}
