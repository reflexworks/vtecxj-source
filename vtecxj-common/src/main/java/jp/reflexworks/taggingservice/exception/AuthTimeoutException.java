package jp.reflexworks.taggingservice.exception;

/**
 * 認証タイムアウトエラー.
 * <p>
 * Reflexで認証タイムアウトエラーが発生した場合、この例外をスローします。
 * </p>
 */
public class AuthTimeoutException extends AuthenticationException {

	/** serialVersionUID */
	private static final long serialVersionUID = 4558288627009400767L;

	public AuthTimeoutException() {
		super("Authentication time out.");
	}

	public AuthTimeoutException(String s) {
		super(s);
	}

}
