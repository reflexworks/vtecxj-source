package jp.reflexworks.taggingservice.exception;

/**
 * タイムアウトエラー.
 * <p>
 * 各処理のタイムアウトが発生すると、この例外をスローします。
 * </p>
 */
public class TimeoutException extends RuntimeException {

	/** serialVersionUID */
	private static final long serialVersionUID = -5407105379413879240L;

	/**
	 * コンストラクタ
	 * @param e 原因例外
	 */
	public TimeoutException(Throwable e) {
		super(e.getMessage(), e);
	}

	/**
	 * コンストラクタ
	 * @param message メッセージ
	 */
	public TimeoutException(String message) {
		super(message);
	}

}
