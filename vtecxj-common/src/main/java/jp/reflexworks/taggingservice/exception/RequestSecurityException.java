package jp.reflexworks.taggingservice.exception;

/**
 * セキュリティエラー.
 */
public class RequestSecurityException extends IllegalArgumentException
implements SubMessage {
	
	/** serialVersionUID */
	private static final long serialVersionUID = -1686212031348635614L;
	
	/**
	 * サブメッセージ.
	 * サブメッセージはログにのみ出力され、レスポンスに返されないメッセージです。
	 */
	private String subMessage;

	/**
	 * コンストラクタ
	 */
	public RequestSecurityException() {
		super("Request security error.");
		subMessage = "'X-Requested-With' header is required.";
	}
	
	/**
	 * サブメッセージを設定.
	 * @param sub サブメッセージ
	 */
	public void setSubMessage(String sub) {
		this.subMessage = sub;
	}
	
	/**
	 * サブメッセージを取得.
	 * @return サブメッセージ
	 */
	public String getSubMessage() {
		return subMessage;
	}

}
