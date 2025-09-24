package jp.reflexworks.taggingservice.exception;

/**
 * 環境情報が上書きされようとした場合に発生させる例外.
 */
public class StaticDuplicatedException extends TaggingException {
	
	/** serialVersionUID */
	private static final long serialVersionUID = -1054271500226693658L;

	/**
	 * コンストラクタ.
	 */
	public StaticDuplicatedException() {
		super("This static information has already been registered.");
	}

}
