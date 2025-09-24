package jp.reflexworks.taggingservice.model;

import java.io.Serializable;

/**
 * Index情報
 */
public class InnerIndex implements Serializable {

	/** serialVersionUID */
	private static final long serialVersionUID = 6466985576647180828L;

	/** Indexの対象URI */
	private String indexUri;

	/** EntryのID */
	private String id;

	/** Index項目名 */
	private String name;

	/** Indexの値 */
	private Object indexObj;

	/** Index項目のType */
	private String type;

	/** 登録・更新されたかどうか */
	private boolean check;

	/**
	 * コンストラクタ.
	 * Manifestで使用
	 * @param indexUri Index URI
	 * @param id ID
	 */
	public InnerIndex(String indexUri, String id) {
		this.indexUri = indexUri;
		this.id = id;
	}

	/**
	 * コンストラクタ.
	 * Indexで使用
	 * @param indexUri Index URI
	 * @param id ID
	 * @param indexObj Index項目値
	 * @param name Index項目名
	 * @param type Index項目のType
	 */
	public InnerIndex(String indexUri, String id, Object indexObj, String name,
			String type) {
		this.indexUri = indexUri;
		this.id = id;
		this.indexObj = indexObj;
		this.name = name;
		this.type = type;
	}

	/**
	 * Index URIを取得
	 * @return Index URI
	 */
	public String getIndexUri() {
		return indexUri;
	}

	/**
	 * IDを取得
	 * @return ID
	 */
	public String getId() {
		return id;
	}

	/**
	 * Index項目の値を取得
	 * @return Index項目の値
	 */
	public Object getIndexObj() {
		return indexObj;
	}

	/**
	 * Index項目の型を取得
	 * @return Index項目の型
	 */
	public String getType() {
		return type;
	}

	/**
	 * Index項目名を取得
	 * @return Index項目名
	 */
	public String getName() {
		return name;
	}

	/**
	 * 登録・更新済みかどうか取得.
	 * 更新時に同一インデックスが登録・更新されているかどうかを取得
	 * @return 登録・更新したかどうか
	 */
	public boolean isCheck() {
		return check;
	}

	/**
	 * 登録済みであることを設定
	 */
	public void checked() {
		this.check = true;
	}

	@Override
	public String toString() {
		return "indexUri=" + indexUri + ", id=" + id;
	}

}
