package jp.reflexworks.taggingservice.exception;

/**
 * BDBサーバからの受け取り値例外.
 */
public class BDBClientException extends TaggingException {

	/** serialVersionUID */
	private static final long serialVersionUID = 7241416498209656730L;

	/**
	 * コンストラクタ.
	 * @param message エラーメッセージ
	 */
	public BDBClientException(String message) {
		super(message);
	}

	/**
	 * コンストラクタ.
	 * @param cause 原因例外
	 */
	public BDBClientException(Throwable cause) {
		super(cause);
	}

}
