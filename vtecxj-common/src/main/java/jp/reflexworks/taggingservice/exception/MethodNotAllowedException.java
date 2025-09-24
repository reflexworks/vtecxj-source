package jp.reflexworks.taggingservice.exception;

import java.io.IOException;

/**
 * メソッドを許可しない例外.
 */
public class MethodNotAllowedException extends IOException {
	
	/** serialVersionUID */
	private static final long serialVersionUID = -4072069256348605697L;

	/**
	 * コンストラクタ
	 * @param msg メッセージ
	 */
	public MethodNotAllowedException(String msg) {
		super(msg);
	}

}
