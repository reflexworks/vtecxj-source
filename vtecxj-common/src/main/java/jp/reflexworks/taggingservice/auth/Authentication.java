package jp.reflexworks.taggingservice.auth;

import java.util.ArrayList;
import java.util.List;

import jp.reflexworks.taggingservice.api.ReflexAuthentication;

/**
 * 認証情報保持クラス.
 */
public class Authentication implements ReflexAuthentication {
	
	/** アカウント */
	private String account;
	/** UID */
	private String uid;
	/** ReflexContextから処理を実行された場合true */
	private boolean isExternal;
	/** セッションID */
	private String sessionId;
	/** 
	 * グループリスト.
	 * varidate処理で有効にするには空のリストを指定する必要がある。
	 * (nullだと項目ACLのチェックが行われない)
	 */
	private List<String> groups = new ArrayList<String>();
	/** サービス名 */
	private String serviceName;
	/** リンクトークン */
	private String linkToken;
	/** リンクトークン許可URIリスト */
	private List<String> linkTokenUris;
	/** 認証方法 */
	private String authType;
	
	/**
	 * コンストラクタ
	 * @param account アカウント
	 * @param uid UID
	 * @param sessionId セッションID
	 * @param authType 認証方法
	 * @param serviceName サービス名
	 */
	public Authentication(String account, String uid, String sessionId,
			String authType, String serviceName) {
		this(account, uid, sessionId, null, authType, serviceName);
	}
	
	/**
	 * コンストラクタ
	 * @param account アカウント
	 * @param uid UID
	 * @param sessionId セッションID
	 * @param linkToken リンクトークン
	 * @param authType 認証方法
	 * @param serviceName サービス名
	 */
	public Authentication(String account, String uid, String sessionId,
			String linkToken, String authType, String serviceName) {
		this(account, uid, sessionId, linkToken, authType, null, serviceName);
	}

	/**
	 * コンストラクタ
	 * @param account アカウント
	 * @param uid UID
	 * @param sessionId セッションID
	 * @param linkToken リンクトークン
	 * @param authType 認証方法
	 * @param groups 参加グループリスト
	 * @param serviceName サービス名
	 */
	public Authentication(String account, String uid, String sessionId,
			String linkToken, String authType, List<String> groups, 
			String serviceName) {
		this.account = account;
		this.uid = uid;
		this.sessionId = sessionId;
		this.linkToken = linkToken;
		this.authType = authType;
		if (groups != null && !groups.isEmpty()) {
			this.groups.addAll(groups);
		}
		this.serviceName = serviceName;
	}
	
	/**
	 * コンストラクタ
	 * @param auth 認証情報
	 */
	public Authentication(ReflexAuthentication auth) {
		if (auth != null) {
			this.account = auth.getAccount();
			this.uid = auth.getUid();
			this.isExternal = auth.isExternal();
			this.sessionId = auth.getSessionId();
			this.groups = auth.getGroups();
			this.serviceName = auth.getServiceName();
			this.linkToken = auth.getLinkToken();
			this.linkTokenUris = auth.getLinkTokenUris();
			this.authType = auth.getAuthType();
		}
	}

	/**
	 * アカウントを取得.
	 * @return アカウント
	 */
	public String getAccount() {
		return account;
	}
	
	/**
	 * UIDを取得.
	 * @return UID
	 */
	public String getUid() {
		return uid;
	}
	
	/**
	 * サーバサイドJS、あるいは内部サービス実行かどうかのフラグを取得.
	 * @return サーバサイドJS、または内部サービスから実行された場合true
	 */
	public boolean isExternal() {
		return isExternal;
	}
	
	/**
	 * サーバサイドJS、あるいは内部サービス実行かどうかのフラグを設定.
	 * @param サーバサイドJS、あるいは内部サービス実行かどうかのフラグ
	 */
	public void setExternal(boolean isExternal) {
		this.isExternal = isExternal;
	}

	/**
	 * セッションIDを取得
	 * @return セッションID
	 */
	public String getSessionId() {
		return sessionId;
	}
	
	/**
	 * このユーザが所属しているグループリストを取得.
	 * @return グループリスト
	 */
	public List<String> getGroups() {
		return groups;
	}
	
	/**
	 * 所属グループを追加
	 * @param group グループ
	 */
	public void addGroup(String group) {
		groups.add(group);
	}
	
	/**
	 * 所属グループをクリア
	 */
	public void clearGroup() {
		groups.clear();
	}
	
	/**
	 * サービス名を取得.
	 * @return サービス名
	 */
	public String getServiceName() {
		return serviceName;
	}
	
	/**
	 * 認証方法を取得.
	 * <ul>
	 *   <li>WSSE</li>
	 *   <li>RXID</li>
	 *   <li>AccessToken</li>
	 *   <li>LinkToken</li>
	 *   <li>Session</li>
	 *   <li>System : システム内で認証情報生成</li>
	 * </ul>
	 * @return 認証情報
	 */
	public String getAuthType() {
		return authType;
	}
	
	/**
	 * リンクトークン認証かどうかを判定.
	 * @return リンクトークン認証の場合true
	 */
	public boolean isLinkToken() {
		return linkToken != null;
	}
	
	/**
	 * リンクトークンを取得.
	 * @return リンクトークン
	 */
	public String getLinkToken() {
		return linkToken;
	}
	
	/**
	 * リンクトークン認証の場合の許可URIを返却
	 * @return 許可URIリスト。リンクトークンでない場合はnull。
	 */
	public List<String> getLinkTokenUris() {
		return linkTokenUris;
	}
	
	/**
	 * リンクトークン認証の場合の認可URIをリストに追加.
	 * @param uri リンクトークン認証の場合の認可URI
	 */
	public void addLinkTokenUri(String uri) {
		if (linkTokenUris == null) {
			linkTokenUris = new ArrayList<String>();
		}
		linkTokenUris.add(uri);
	}

}
