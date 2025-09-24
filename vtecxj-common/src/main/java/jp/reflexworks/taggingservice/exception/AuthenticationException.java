package jp.reflexworks.taggingservice.exception;

/**
 * 認証エラー.
 * <p>
 * Reflexで認証エラーが発生した場合、この例外をスローします。
 * </p>
 */
public class AuthenticationException extends TaggingException implements SubMessage {
	
	/** serialVersionUID */
	private static final long serialVersionUID = -2771351462522712521L;
	
	/** ユーザ名 */
	protected String username;
	/** サブメッセージ (ユーザには返さず、ログにのみ出力するメッセージ) */
	protected String subMessage;

	/**
	 * コンストラクタ
	 */
	public AuthenticationException() {
		super("Authentication error.");
	}

	/**
	 * コンストラクタ
	 * @param s メッセージ
	 */
	public AuthenticationException(String s) {
		super(s);
	}

	/**
	 * コンストラクタ
	 * @param e cause
	 */
	public AuthenticationException(Throwable e) {
		super(e);
	}

	/**
	 * ユーザ名を取得
	 * @return ユーザ名
	 */
	public String getUsername() {
		return username;
	}

	/**
	 * ユーザ名を設定
	 * @paramm username ユーザ名
	 */
	public void setUsername(String username) {
		this.username = username;
	}
	
	/**
	 * サブメッセージを設定.
	 * @param sub サブメッセージ
	 */
	public void setSubMessage(String sub) {
		this.subMessage = sub;
	}
	
	/**
	 * サブメッセージを取得.
	 * @return サブメッセージ
	 */
	public String getSubMessage() {
		return subMessage;
	}

}
