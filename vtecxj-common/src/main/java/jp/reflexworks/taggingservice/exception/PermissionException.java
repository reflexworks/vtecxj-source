package jp.reflexworks.taggingservice.exception;

/**
 * 認可エラー.
 * <p>
 * ACLチェックで、リクエストされた操作の権限が無かった場合この例外をスローします。
 * </p>
 */
public class PermissionException extends TaggingException implements SubMessage {

	/** serialVersionUID */
	private static final long serialVersionUID = 714735314412137203L;

	/**
	 * サブメッセージ
	 * (ユーザには返さず、ログにのみ出力するメッセージ)
	 */
	protected String subMessage;

	/**
	 * コンストラクタ.
	 */
	public PermissionException() {
		super("Access denied.");
	}

	/**
	 * コンストラクタ.
	 * @param uid UID
	 * @param uri URI
	 */
	public PermissionException(String uid, String uri) {
		super("Access denied. uid = " + uid + ", uri = " + uri);
	}

	/**
	 * コンストラクタ.
	 * @param s メッセージ
	 */
	public PermissionException(String s) {
		super(s);
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
