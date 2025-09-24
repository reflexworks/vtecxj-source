package jp.reflexworks.taggingservice.exception;

import jp.reflexworks.taggingservice.api.ReflexAuthentication;

/**
 * 認証ロック
 * <p>
 * 他のユーザが既にログイン済の場合、この例外をスローします。
 * </p>
 */
public class AuthLockedException extends AuthenticationException {
	
	/** serialVersionUID */
	private static final long serialVersionUID = -8935242095479562012L;
	
	private ReflexAuthentication auth;

	public AuthLockedException(ReflexAuthentication auth) {
		super("Authentication is locked.");
		this.auth = auth;
	}

	public AuthLockedException(ReflexAuthentication auth, String s) {
		super(s);
		this.auth = auth;
	}
	
	public AuthLockedException() {
		super("Authentication is locked.");
	}

	public AuthLockedException(String s) {
		super(s);
	}

	public ReflexAuthentication getAuth() {
		return auth;
	}

}
