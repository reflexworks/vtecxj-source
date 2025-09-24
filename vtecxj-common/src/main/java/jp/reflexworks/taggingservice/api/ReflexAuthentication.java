package jp.reflexworks.taggingservice.api;

import java.util.List;

/**
 * 認証情報インターフェース.
 */
public interface ReflexAuthentication {
	
	/**
	 * アカウントを取得.
	 * @return アカウント
	 */
	public String getAccount();
	
	/**
	 * UIDを取得.
	 * @return UID
	 */
	public String getUid();
	
	/**
	 * サーバサイドJS、あるいは内部サービス実行かどうかのフラグを取得.
	 * @return サーバサイドJS、または内部サービスから実行された場合true
	 */
	public boolean isExternal();
	
	/**
	 * サーバサイドJS、あるいは内部サービス実行かどうかのフラグを設定.
	 * @param サーバサイドJS、あるいは内部サービス実行かどうかのフラグ
	 */
	void setExternal(boolean isExternal);

	/**
	 * セッションIDを取得
	 * @return セッションID
	 */
	public String getSessionId();
	
	/**
	 * このユーザが所属しているグループリストを取得.
	 * @return グループリスト
	 */
	public List<String> getGroups();
	
	/**
	 * 所属グループを追加
	 * @param group グループ
	 */
	public void addGroup(String group);
	
	/**
	 * 所属グループをクリア
	 */
	public void clearGroup();
	
	/**
	 * サービス名を取得.
	 * @return サービス名
	 */
	public String getServiceName();
	
	/**
	 * 認証方法を取得.
	 * @return 認証方法
	 */
	public String getAuthType();
	
	/**
	 * リンクトークン認証かどうかを判定.
	 * @return リンクトークン認証の場合true
	 */
	public boolean isLinkToken();
	
	/**
	 * リンクトークンを取得.
	 * @return リンクトークン
	 */
	public String getLinkToken();
	
	/**
	 * リンクトークン認証の場合の許可URIを返却.
	 * 一度認可されたURIをリストに追加する。
	 * @return 許可URIリスト。リンクトークンでない場合はnull。
	 */
	public List<String> getLinkTokenUris();
	
	/**
	 * リンクトークン認証の場合の認可URIをリストに追加.
	 * @param uri リンクトークン認証の場合の認可URI
	 */
	public void addLinkTokenUri(String uri);

}
