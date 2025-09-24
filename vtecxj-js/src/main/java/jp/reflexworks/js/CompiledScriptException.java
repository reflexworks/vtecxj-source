package jp.reflexworks.js;

/**
 * CompiledScript生成時の例外
 */
public class CompiledScriptException extends RuntimeException {

	/** serialVersionUID */
	private static final long serialVersionUID = -1766724591801939007L;

	/**
	 * コンストラクタ.
	 * @param cause 原因例外
	 */
	CompiledScriptException(Throwable cause) {
		super(cause);
	}

}
