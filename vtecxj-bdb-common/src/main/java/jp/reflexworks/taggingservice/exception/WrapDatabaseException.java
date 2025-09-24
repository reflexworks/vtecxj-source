package jp.reflexworks.taggingservice.exception;

import com.sleepycat.je.DatabaseException;

import jp.reflexworks.taggingservice.bdb.BDBUtil;

/**
 * BDBの発生例外ラッパー.
 * メッセージにキーを設定するために使用。
 */
public class WrapDatabaseException extends DatabaseException {

	/** serialVersionUID */
	private static final long serialVersionUID = 6209760413944190302L;

	/** メッセージ */
	private String message;

	/**
	 * コンストラクタ
	 * @param key エラー発生キー
	 * @param cause 例外
	 */
	public WrapDatabaseException(String key, DatabaseException cause) {
		super(cause);
		// メッセージ編集
		this.message = BDBUtil.editErrorMsgWithKey(cause, key);
	}

	/**
	 * エラーメッセージを取得.
	 * @return エラーメッセージ
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * このオブジェクトの文字列表現を取得.
	 * @return このオブジェクトの文字列表現
	 */
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(getCause().getClass().getName());
		sb.append(": ");
		sb.append(message);
		return sb.toString();
	}

}
