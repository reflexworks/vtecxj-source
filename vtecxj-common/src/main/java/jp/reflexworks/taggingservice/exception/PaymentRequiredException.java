package jp.reflexworks.taggingservice.exception;

/**
 * サービス使用の制限オーバー例外.
 */
public class PaymentRequiredException extends TaggingException {

	/** serialVersionUID */
	private static final long serialVersionUID = -5770033201713123681L;
	
	/**
	 * コンストラクタ
	 * @param message メッセージ
	 */
	public PaymentRequiredException(String message) {
		super(message);
	}

}
