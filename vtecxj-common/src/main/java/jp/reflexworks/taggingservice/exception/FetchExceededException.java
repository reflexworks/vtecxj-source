package jp.reflexworks.taggingservice.exception;

/**
 * フェッチ数制限超えエラー
 */
public class FetchExceededException extends TaggingException {
	
	/** エラーメッセージ */
	public static final String MESSAGE = "Fetch exceeded.";

	/** serialVersionUID */
	private static final long serialVersionUID = -2569483976204215216L;

	/**
	 * コンストラクタ.
	 * @param message エラーメッセージ
	 */
	public FetchExceededException(String message) {
		super(message);
	}

}
