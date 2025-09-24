package jp.reflexworks.taggingservice.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * fetch結果情報
 */
public class FetchInfo<T> implements Serializable {

	/** serialVersionUID */
	private static final long serialVersionUID = -4290060596352899499L;

	/** フェッチ結果 (キー:BDBのキー、値:BDBの値)  */
	private Map<String, T> result;
	/** カーソル */
	private String pointerStr;
	/** フェッチ制限超えかどうか */
	private boolean isFetchExceeded;

	/**
	 * コンストラクタ.
	 * @param result フェッチ結果 (キー:BDBのキー、値:BDBの値)
	 * @param pointerStr カーソル
	 */
	public FetchInfo(Map<String, T> result, String pointerStr) {
		this.result = result;
		this.pointerStr = pointerStr;
	}

	/**
	 * フェッチ結果を取得
	 * @return フェッチ結果
	 */
	public Map<String, T> getResult() {
		return result;
	}

	/**
	 * フェッチ結果のキーリストを取得.
	 * @return フェッチ結果のキーリスト
	 */
	public List<String> getKeys() {
		if (result == null || result.isEmpty()) {
			return null;
		}
		return new ArrayList<String>(result.keySet());
	}

	/**
	 * フェッチ結果の値リストを取得.
	 * @return フェッチ結果の値リスト
	 */
	public List<T> getValues() {
		if (result == null) {
			return null;
		}
		List<T> values = new ArrayList<T>();
		for (Map.Entry<String, T> mapEntry : result.entrySet()) {
			values.add(mapEntry.getValue());
		}
		return values;
	}

	/**
	 * カーソルを取得.
	 * @return カーソル
	 */
	public String getPointerStr() {
		return pointerStr;
	}

	/**
	 * フェッチ制限超えかどうか
	 * @return フェッチ制限超えの場合true
	 */
	public boolean isFetchExceeded() {
		return isFetchExceeded;
	}

	/**
	 * フェッチ制限超えかどうかを設定する
	 * @param isFetchExceeded フェッチ制限超えかどうか
	 */
	public void setFetchExceeded(boolean isFetchExceeded) {
		this.isFetchExceeded = isFetchExceeded;
	}

}
