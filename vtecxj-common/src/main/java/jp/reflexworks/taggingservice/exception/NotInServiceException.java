package jp.reflexworks.taggingservice.exception;

import java.io.IOException;

/**
 * サービス停止状態、または未登録の場合このエラーをスローします.
 */
public class NotInServiceException extends IOException {
	
	/** serialVersionUID */
	private static final long serialVersionUID = -4825402765710090742L;
	
	/** メッセージ接頭辞 */
	public static final String MESSAGE_PREFIX = "Not in service: ";
	/** サービス無しメッセージ */
	public static final String MESSAGE_NULL = MESSAGE_PREFIX + "null";

	/**
	 * コンストラクタ
	 * @param serviceName サービス名
	 */
	public NotInServiceException(String serviceName) {
		super(MESSAGE_PREFIX + serviceName);
	}

}
