package jp.reflexworks.taggingservice.exception;

import java.io.IOException;

/**
 * リトライ数制限超えエラー
 */
public class RetryExceededException extends IOException {

	/** エラーメッセージ */
	public static final String MESSAGE = "Retry exceeded.";

	/** serialVersionUID */
	private static final long serialVersionUID = -8591389769351254510L;

	/**
	 * コンストラクタ.
	 * @param message エラーメッセージ
	 */
	public RetryExceededException(String message) {
		super(message);
	}

}
