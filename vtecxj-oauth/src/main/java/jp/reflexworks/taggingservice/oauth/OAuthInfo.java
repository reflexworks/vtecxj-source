package jp.reflexworks.taggingservice.oauth;

import java.io.Serializable;

/**
 * OAuth認証で取得したユーザ情報.
 */
public class OAuthInfo implements Serializable {
	
	/** serialVersionUID */
	private static final long serialVersionUID = 9156054220192316328L;
	
	/** ユーザ識別子 (ソーシャルアカウント) */
	private String oAuthId;
	/** メールアドレス */
	private String oAuthEmail;
	/** ニックネーム */
	private String nickname;

	/**
	 * コンストラクタ.
	 * @param oAuthId ユーザ識別子
	 * @param oAuthEmail メールアドレス
	 * @param nickname ニックネーム
	 */
	public OAuthInfo(String oAuthId, String oAuthEmail,
			String nickname) {
		this.oAuthId = oAuthId;
		this.oAuthEmail = oAuthEmail;
		this.nickname = nickname;
	}

	/**
	 * ユーザ識別子を取得.
	 * @return ユーザ識別子 (ソーシャルアカウント)
	 */
	public String getOAuthId() {
		return oAuthId;
	}

	/**
	 * メールアドレスを取得.
	 * @return メールアドレス
	 */
	public String getOAuthEmail() {
		return oAuthEmail;
	}

	/**
	 * ニックネームを取得.
	 * @return ニックネーム
	 */
	public String getNickname() {
		return nickname;
	}
	
}
