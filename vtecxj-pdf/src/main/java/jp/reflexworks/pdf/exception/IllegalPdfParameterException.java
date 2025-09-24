package jp.reflexworks.pdf.exception;

import jp.reflexworks.taggingservice.exception.IllegalParameterException;

/**
 * パラメータ内容不正.
 * <p>
 * 入力した内容が誤っている場合、この例外をスローします。
 * </p>
 */
public class IllegalPdfParameterException extends IllegalParameterException {

	/** serialVersionUID */
	private static final long serialVersionUID = 962645550171886580L;

	/**
	 * コンストラクタ
	 * @param s エラーメッセージ
	 */
	public IllegalPdfParameterException(String s) {
		super(s);
	}

	/**
	 * コンストラクタ
	 * @param s エラーメッセージ
	 * @param e 原因例外
	 */
	public IllegalPdfParameterException(String s, Throwable e) {
		super(s, e);
	}
	
}
