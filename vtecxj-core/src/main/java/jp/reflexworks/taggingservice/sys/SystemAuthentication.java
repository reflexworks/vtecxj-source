package jp.reflexworks.taggingservice.sys;

import java.util.List;

import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.util.Constants;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * システムユーザ認証情報.
 */
public class SystemAuthentication implements ReflexAuthentication {
	
	/** System account **/
	public static final String ACCOUNT_SYSTEM = "_system_";
	/** System uid **/
	public static final String UID_SYSTEM = "0";
	
	/** UID */
	private String uid;
	/** アカウント */
	private String account;
	/** サービス名 */
	private String serviceName;
	/** ReflexContext呼び出しかどうか */
	private boolean isExternal;
	
	/**
	 * コンストラクタ
	 * @param account アカウント。nullの場合はデフォルト値。
	 * @param uid UID。nullの場合はデフォルト値。
	 * @param serviceName サービス名
	 */
	public SystemAuthentication(String account, String uid, String serviceName) {
		if (!StringUtils.isBlank(uid)) {
			this.uid = uid;
		} else {
			this.uid = UID_SYSTEM;
		}
		if (!StringUtils.isBlank(account)) {
			this.account = account;
		} else {
			this.account = ACCOUNT_SYSTEM;
		}
		this.serviceName = serviceName;
	}

	/**
	 * コンストラクタ.
	 * @param auth 認証情報
	 */
	public SystemAuthentication(ReflexAuthentication auth) {
		this.uid = auth.getUid();
		this.account = auth.getAccount();
		this.serviceName = auth.getServiceName();
		if (StringUtils.isBlank(this.uid)) {
			this.uid = UID_SYSTEM;
		}
		if (StringUtils.isBlank(this.account)) {
			this.uid = ACCOUNT_SYSTEM;
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
		return null;
	}
	
	/**
	 * このユーザが所属しているグループリストを取得.
	 * @return グループリスト
	 */
	public List<String> getGroups() {
		// nullを返すことでEntryBase#validateの項目ACLチェックを抜けられる。
		return null;
	}
	
	/**
	 * 所属グループを追加
	 * @param group グループ
	 */
	public void addGroup(String group) {
		// Do nothing.
	}
	
	/**
	 * 所属グループをクリア
	 */
	public void clearGroup() {
		// Do nothing.
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
	 * システム認証
	 * @return 認証情報
	 */
	public String getAuthType() {
		return Constants.AUTH_TYPE_SYSTEM;
	}
	
	/**
	 * リンクトークン認証かどうかを判定.
	 * システムユーザはリンクトークン認証ではない。
	 * @return リンクトークン認証の場合true
	 */
	public boolean isLinkToken() {
		return false;
	}
	
	/**
	 * リンクトークンを取得.
	 * システムユーザ認証はリンクトークン認証ではない。
	 * @return リンクトークン
	 */
	public String getLinkToken() {
		return null;
	}

	/**
	 * リンクトークン認証の場合の許可URIを返却
	 * システムユーザ認証はリンクトークン認証ではない。
	 * @return 許可URIリスト。リンクトークンでない場合はnull。
	 */
	public List<String> getLinkTokenUris() {
		return null;
	}
	
	/**
	 * リンクトークン認証の場合の認可URIをリストに追加.
	 * システムユーザ認証はリンクトークン認証ではない。
	 * @param uri リンクトークン認証の場合の認可URI
	 */
	public void addLinkTokenUri(String uri) {
		return;
	}

}
