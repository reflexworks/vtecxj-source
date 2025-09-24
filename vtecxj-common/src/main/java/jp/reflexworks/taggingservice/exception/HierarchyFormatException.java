package jp.reflexworks.taggingservice.exception;

/**
 * 階層チェックエラー.
 * <p>
 * キーやエイリアスの階層構造に違反する場合、この例外をスローします。<br>
 * 具体的には以下の場合です。
 * <ul>
 * <li>登録時、上位階層にデータが存在しない場合。</li>
 * <li>削除時、下位階層にデータが存在する場合。</li>
 * <li>エイリアスが重複する場合。</li>
 * </ul>
 * </p>
 */
public class HierarchyFormatException extends TaggingException {

	/** serialVersionUID */
	private static final long serialVersionUID = 50769715420236743L;
	
	/** メッセージ : 上位階層が必要 */
	public static final String MESSAGE_REQUIRE_PARENT = "Parent path is required.";
	/** メッセージ : 下位階層が存在している */
	public static final String MESSAGE_EXIST_CHILD = "Can't delete for the child entries exist.";
	
	/**
	 * コンストラクタ.
	 * @param msg メッセージ
	 */
	public HierarchyFormatException(String msg) {
		super(msg);
	}

}
