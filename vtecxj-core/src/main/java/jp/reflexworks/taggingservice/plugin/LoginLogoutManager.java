package jp.reflexworks.taggingservice.plugin;

import java.io.IOException;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.exception.AuthenticationException;
import jp.reflexworks.taggingservice.exception.TaggingException;

/**
 * ログイン・ログアウト管理インターフェース.
 */
public interface LoginLogoutManager extends ReflexPlugin {
	
	/**
	 * ログイン
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @return メッセージ
	 */
	public FeedBase login(ReflexRequest req, ReflexResponse resp)
	throws IOException, TaggingException;
	
	/**
	 * 他サービスにログイン.
	 * 他サービスへのリダイレクトレスポンスを返す。
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param serviceName ログイン先サービス名
	 * @return メッセージ
	 */
	public FeedBase loginService(ReflexRequest req, ReflexResponse resp,
			String serviceName)
	throws IOException, TaggingException;

	/**
	 * ログアウト
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @return メッセージ
	 */
	public FeedBase logout(ReflexRequest req, ReflexResponse resp)
	throws IOException, TaggingException;
	
	/**
	 * ログイン履歴出力.
	 * @param req リクエスト
	 */
	public void writeLoginHistory(ReflexRequest req);
	
	/**
	 * ログイン失敗履歴出力.
	 * @param req リクエスト
	 * @param ae AuthenticationException
	 */
	public void writeAuthError(ReflexRequest req, AuthenticationException ae);

	/**
	 * ユーザごとのログイン履歴フォルダエントリーのキーを取得.
	 *  /_user/{UID}/login_history
	 * @param uid UID
	 * @return ユーザごとのログイン履歴フォルダエントリーのキー
	 */
	public String getLoginHistoryUserFolderUri(String uid);

}
