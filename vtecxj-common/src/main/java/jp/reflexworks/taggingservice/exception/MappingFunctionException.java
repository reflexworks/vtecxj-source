package jp.reflexworks.taggingservice.exception;

/**
 * MappingFunction実行時の例外.
 * RuntimeExceptionしかスローできないため、RuntimeExceptionを継承する。
 */
public class MappingFunctionException extends RuntimeException {

	/**
	 * コンストラクタ
	 * @param cause 例外
	 */
	public MappingFunctionException(Throwable cause) {
		super(cause);
	}

}
