package jp.reflexworks.taggingservice.exception;

/**
 * サーバシャットダウン時の例外.
 */
public class ShutdownException extends RuntimeException {

	/** serialVersionUID */
	private static final long serialVersionUID = -5644964376344544324L;

	/**
	 * コンストラクタ
	 * @param message エラーメッセージ
	 * @param e 発生例外
	 */
	public ShutdownException(String message, Throwable e) {
		super(message, e);
	}

}
