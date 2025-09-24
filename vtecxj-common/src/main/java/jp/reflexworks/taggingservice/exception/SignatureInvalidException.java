package jp.reflexworks.taggingservice.exception;

/**
 * 署名不正例外.
 */
public class SignatureInvalidException extends TaggingException {

	/** serialVersionUID */
	private static final long serialVersionUID = 9032968356631444301L;

	/**
	 * コンストラクタ.
	 */
	public SignatureInvalidException() {
		super("The signature is invalid.");
	}
	
	/**
	 * コンストラクタ.
	 * @param msg メッセージ
	 */
	public SignatureInvalidException(String msg) {
		super(msg);
	}
	
	/**
	 * コンストラクタ.
	 * @param e 原因例外
	 */
	public SignatureInvalidException(Throwable e) {
		super(e);
	}

}
