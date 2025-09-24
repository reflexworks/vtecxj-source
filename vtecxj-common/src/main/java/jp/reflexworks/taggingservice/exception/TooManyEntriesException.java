package jp.reflexworks.taggingservice.exception;

/**
 * エントリー数超過エラー.
 * <p>
 * 設定で指定されたエントリー最大数(limit.max)を超える場合、この例外をスローします。<br>
 * 具体的には以下の場合です。
 * <ul>
 * <li>検索時、エントリー取得最大パラメータ(l)に、エントリー最大数(limit.max)を超える値を指定した場合</li>
 * <li>登録・更新時、エントリー最大数(limit.max)を超える数のエントリーを設定した場合</li>
 * </ul>
 * </p>
 */
public class TooManyEntriesException extends IllegalArgumentException {
	
	/** serialVersionUID */
	private static final long serialVersionUID = -7007743553463409691L;

	/** 
	 * コンストラクタ
	 */
	public TooManyEntriesException() {
		super("Too many entries.");
	}
	
	/**
	 * コンストラクタ
	 * @param s メッセージ
	 */
	public TooManyEntriesException(String s) {
		super(s);
	}

}
