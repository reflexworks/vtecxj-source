package jp.reflexworks.taggingservice.exception;

/**
 * 楽観的排他エラー.
 */
public class OptimisticLockingException extends LockingException {

	/** serialVersionUID */
	private static final long serialVersionUID = -3014106588621136422L;
	
	/** メッセージ */
	public static final String MSG = "Optimistic locking failed.";
	/** メッセージ */
	public static final String MSG_PREFIX = MSG + " Key = ";
	
	/**
	 * コンストラクタ
	 */
	public OptimisticLockingException() {
		super(MSG);
	}
	
	/**
	 * コンストラクタ
	 * @param msg メッセージ
	 */
	public OptimisticLockingException(String msg) {
		super(msg);
	}

}
