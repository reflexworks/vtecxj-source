package jp.reflexworks.taggingservice.auth;

import java.util.List;

import jp.reflexworks.taggingservice.api.ReflexAuthentication;

/**
 * 認証情報.
 * 仮認証状態を持つ
 */
public class TaggingAuthentication extends Authentication {

	/** 仮認証フラグ */
	private boolean isTemporary;
	/** 信頼される端末の場合、値を設定する */
	private String tdid;

	/**
	 * コンストラクタ
	 * @param account アカウント
	 * @param uid UID
	 * @param sessionId セッションID
	 * @param authType 認証方法
	 * @param serviceName サービス名
	 */
	public TaggingAuthentication(String account, String uid, String sessionId,
			String authType, String serviceName) {
		super(account, uid, sessionId, authType, serviceName);
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
	public TaggingAuthentication(String account, String uid, String sessionId,
			String linkToken, String authType, String serviceName) {
		super(account, uid, sessionId, linkToken, authType, serviceName);
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
	public TaggingAuthentication(String account, String uid, String sessionId,
			String linkToken, String authType, List<String> groups,
			String serviceName) {
		super(account, uid, sessionId, linkToken, authType, groups, serviceName);
	}

	/**
	 * コンストラクタ
	 * @param auth 認証情報
	 */
	public TaggingAuthentication(ReflexAuthentication auth) {
		super(auth);
	}

	/**
	 * 仮認証かどうか.
	 * @return 仮認証の場合true
	 */
	public boolean isTemporary() {
		return isTemporary;
	}

	/**
	 * 仮認証フラグをセット
	 * @param isTemporary 仮認証の場合true
	 */
	public void setTemporary(boolean isTemporary) {
		this.isTemporary = isTemporary;
	}

	/**
	 * 信頼される端末の場合Cookie設定値を取得
	 * @return 信頼される端末のCookieに設定する値
	 */
	public String getTdid() {
		return tdid;
	}

	/**
	 * 信頼される端末のCookieに設定する値をセット.
	 * @param tdid 信頼される端末のCookieに設定する値
	 */
	public void setTdid(String tdid) {
		this.tdid = tdid;
	}

}
