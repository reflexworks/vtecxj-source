package jp.reflexworks.taggingservice.exception;

/**
 * 採番枠を超えるエラー.
 */
public class OutOfRangeException extends TaggingException {

	/** serialVersionUID */
	private static final long serialVersionUID = 1882864185336122535L;
	
	/** メッセージ接頭辞 */
	public static final String MESSAGE = "Increment is out of range. Key = ";
	/** メッセージ接頭辞 */
	public static final String MESSAGE_SETID = "Setid is out of range. number = ";
	
	/**
	 * コンストラクタ.
	 * @param uri キー
	 */
	public OutOfRangeException(String uri) {
		super(MESSAGE + uri);
	}
	
	/**
	 * コンストラクタ.
	 * @param msg メッセージ
	 * @param uri キー
	 * @param num 値
	 */
	public OutOfRangeException(String msg, String uri, long num) {
		super(msg + num + " Key = " + uri);
	}

}
