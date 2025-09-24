package jp.reflexworks.taggingservice.model;

/**
 * 値オブジェクト
 * nullを格納したいができない場合に、本クラスでラップする。
 * @param <T> 値
 */
public class Value<T> {

	/** 値 */
	public T value;

	/**
	 * コンストラクタ
	 * @param value 値
	 */
	public Value(T value) {
		this.value = value;
	}

	/**
	 * このオブジェクトの文字列表現
	 */
	@Override
	public String toString() {
		return "" + value;
	}

}
