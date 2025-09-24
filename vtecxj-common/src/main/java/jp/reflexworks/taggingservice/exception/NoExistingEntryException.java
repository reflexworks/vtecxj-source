package jp.reflexworks.taggingservice.exception;

/**
 * データ存在無し.
 * <p>
 * 更新・削除で指定されたキーのデータが存在しない場合この例外をスローします。
 * 検索でデータが存在しない場合、NoEntryExceptionを使用します。
 * </p>
 */
public class NoExistingEntryException extends TaggingException {
	
	/** serialVersionUID */
	private static final long serialVersionUID = -6904531685934822220L;
	
	/** Message */
	public static final String MSG = "No entry.";
	/** Message */
	public static final String MSG_PREFIX = MSG + " Key = ";
	
	/**
	 * コンストラクタ.
	 */
	public NoExistingEntryException() {
		super(MSG);
	}

	/**
	 * コンストラクタ.
	 * @param s メッセージ
	 */
	public NoExistingEntryException(String s) {
		super(s);
	}

}
