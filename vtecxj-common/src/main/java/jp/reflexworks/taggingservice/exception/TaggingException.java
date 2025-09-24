package jp.reflexworks.taggingservice.exception;

import jp.sourceforge.reflex.IReflexException;

/**
 * Reflexで発生した例外.
 * <p>
 * 他の例外をラップします。
 * 例外発生時に処理していたキーを取得できます。(getUriメソッド)
 * </p>
 */
public abstract class TaggingException extends IReflexException {

	/** serialVersionUID */
	private static final long serialVersionUID = 2896341380009290153L;
	
	/** URI */
	protected String uri;
	/** Feed指定の場合の対象インデックス */
	protected int num;
	
	/**
	 * コンストラクタ
	 * @param cause 原因例外
	 * @param uri URI
	 */
	public TaggingException(Throwable cause, String uri) {
		super(cause);
		this.uri = uri;
	}
	
	/**
	 * コンストラクタ
	 * @param cause 原因例外
	 * @param uri URI
	 * @param message メッセージ
	 */
	public TaggingException(Throwable cause, String uri, 
			String message) {
		super(message, cause);
		this.uri = uri;
	}
	
	/**
	 * コンストラクタ
	 * @param cause 原因例外
	 */
	public TaggingException(Throwable cause) {
		super(cause);
	}
	
	/**
	 * コンストラクタ
	 * @param message メッセージ
	 */
	public TaggingException(String message) {
		super(message);
	}

	/**
	 * 例外発生時のキーを取得
	 * @return キー
	 */
	public String getUri() {
		return uri;
	}

	/**
	 * 例外発生の原因となるFeed内Entryのインデックスを取得
	 * @return 例外発生の原因となるFeed内Entryのインデックス
	 */
	public Integer getNum() {
		return num;
	}

	/**
	 * 例外発生の原因となるFeed内Entryのインデックスを設定
	 * @param num 例外発生の原因となるFeed内Entryのインデックス(1から開始)
	 */
	public void setNum(int num) {
		this.num = num + 1;
	}
	
	/**
	 * メッセージを取得.
	 * メッセージに
	 */
	public String getMessage() {
		StringBuilder sb = new StringBuilder();
		sb.append(super.getMessage());
		if (uri != null) {
			sb.append(" key = ");
			sb.append(uri);
		}
		if (num != 0) {
			sb.append(", num = ");
			sb.append(num);
		}
		return sb.toString();
	}

}
