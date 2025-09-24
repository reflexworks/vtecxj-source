package jp.reflexworks.taggingservice.exception;

/**
 * TaggingserviceSessionで例外が発生した場合にスローする例外.
 * HttpSessionの各メソッドはthrows定義が無いため、RuntimeExceptionをスローする。
 */
public class TaggingSessionException extends RuntimeException {

	/** serialVersionUID */
	private static final long serialVersionUID = 7262866541269364927L;

	/**
	 * コンストラクタ
	 * @param e 原因例外
	 */
	public TaggingSessionException(Throwable e) {
		super(e);
	}
	
}
