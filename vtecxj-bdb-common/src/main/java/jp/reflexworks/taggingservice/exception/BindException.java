package jp.reflexworks.taggingservice.exception;

/**
 * データバインド時の例外.
 */
public class BindException extends IllegalStateException {
	
	/** serialVersionUID */
	private static final long serialVersionUID = 7543747129329871815L;

	/**
	 * コンストラクタ
	 * @param cause 原因例外
	 */
	public BindException(Throwable cause) {
		super(cause);
	}

}
