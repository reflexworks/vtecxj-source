package jp.reflexworks.taggingservice.exception;

/**
 * データ重複エラー.
 * <p>
 * 登録でキーが重複している場合、この例外をスローします。
 * </p>
 */
public class EntryDuplicatedException extends TaggingException {
	
	/** serialVersionUID */
	private static final long serialVersionUID = -2042620944209832255L;
	
	/** デフォルトメッセージ */
	public static final String MESSAGE = "Duplicated key.";

	/**
	 * コンストラクタ
	 */
	public EntryDuplicatedException() {
		super(MESSAGE);
	}
	
	/**
	 * コンストラクタ
	 * @param message メッセージ
	 */
	public EntryDuplicatedException(String message) {
		super(message);
	}
	
}
