package jp.reflexworks.taggingservice.exception;

/**
 * データ存在無し.
 * <p>
 * 検索で指定されたキーのデータが存在しない場合この例外をスローします。
 * 更新・削除でデータが存在しない場合、NoExistingEntryExceptionを使用します。
 * </p>
 */
public class NoEntryException extends TaggingException {
	
	
	/** serialVersionUID */
	private static final long serialVersionUID = 2378278149292300761L;
	
	/** Message */
	public static final String MSG = "No entry.";
	/** Message */
	public static final String MSG_PREFIX = MSG + " Key = ";
	
	/**
	 * コンストラクタ.
	 */
	public NoEntryException() {
		super(MSG);
	}

	/**
	 * コンストラクタ.
	 * @param s メッセージ
	 */
	public NoEntryException(String s) {
		super(s);
	}

}
