package jp.reflexworks.taggingservice.exception;

/**
 * 排他エラー.
 */
public class LockingException extends TaggingException {
	
	/** serialVersionUID */
	private static final long serialVersionUID = 1546733179095448263L;

	/**
	 * コンストラクタ
	 * @param msg
	 */
	public LockingException(String msg) {
		super(msg);
	}

}
