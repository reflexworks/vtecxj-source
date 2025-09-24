package jp.reflexworks.taggingservice.exception;

/**
 * サービス設定が不正な場合の例外.
 */
public class InvalidServiceSettingException extends TaggingException {

	/** serialVersionUID */
	private static final long serialVersionUID = 9128344625765012798L;

	/**
	 * コンストラクタ
	 * @param msg メッセージ
	 */
	public InvalidServiceSettingException(String msg) {
		super(msg);
	}
	
	/**
	 * コンストラクタ.
	 * @param e 例外
	 * @param msg メッセージ
	 */
	public InvalidServiceSettingException(Throwable e, String msg) {
		super(e, null, msg);
	}
	
	/**
	 * コンストラクタ.
	 * @param e 例外
	 */
	public InvalidServiceSettingException(Throwable e) {
		super(e);
	}

}
