package jp.reflexworks.taggingservice.exception;

public class PayloadTooLargeException extends IllegalArgumentException {
	
	/** serialVersionUID */
	private static final long serialVersionUID = -4409758325414082403L;

	public PayloadTooLargeException() {
		super("Payload too large.");
	}

	public PayloadTooLargeException(String s) {
		super(s);
	}
	
}
