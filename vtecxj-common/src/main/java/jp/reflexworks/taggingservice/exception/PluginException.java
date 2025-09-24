package jp.reflexworks.taggingservice.exception;

/**
 * プラグインクラスの生成例外.
 */
public class PluginException extends TaggingException {
	
	/** serialVersionUID */
	private static final long serialVersionUID = 2940822462148192508L;

	/**
	 * コンストラクタ
	 * @param cause 例外
	 */
	public PluginException(Throwable cause) {
		super(cause);
	}
	
	/**
	 * コンストラクタ
	 * @param message メッセージ
	 */
	public PluginException(String message) {
		super(message);
	}

}
