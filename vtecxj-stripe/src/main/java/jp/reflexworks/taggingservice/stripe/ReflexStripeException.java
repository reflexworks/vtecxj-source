package jp.reflexworks.taggingservice.stripe;

import jp.reflexworks.taggingservice.exception.TaggingException;

/**
 * Stripeからの致命的でない例外
 */
public class ReflexStripeException extends TaggingException {

	/**
	 * コンストラクタ
	 * @param cause 原因例外
	 */
	public ReflexStripeException(Throwable cause) {
		super(cause);
	}

}
