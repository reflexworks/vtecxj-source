package jp.reflexworks.taggingservice.exception;

/**
 * パラメータ内容不正.
 * <p>
 * 入力した内容が誤っている場合、この例外をスローします。
 * </p>
 */
public class IllegalParameterException extends IllegalArgumentException {

	/** serialVersionUID */
	private static final long serialVersionUID = 962645550171886580L;

	public IllegalParameterException(String s) {
		super(s);
	}

	public IllegalParameterException(String s, Throwable e) {
		super(s, e);
	}
	
}
