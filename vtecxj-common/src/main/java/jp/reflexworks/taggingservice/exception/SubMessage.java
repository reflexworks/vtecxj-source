package jp.reflexworks.taggingservice.exception;

/**
 * サブメッセージを保持する例外が実装するインターフェース.
 * サブメッセージとはログにのみ出力され、レスポンスされないメッセージです。
 */
public interface SubMessage {
	
	/**
	 * サブメッセージを取得.
	 * @return サブメッセージ
	 */
	public String getSubMessage();

}
