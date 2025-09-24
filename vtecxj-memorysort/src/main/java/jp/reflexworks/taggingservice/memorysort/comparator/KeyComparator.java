package jp.reflexworks.taggingservice.memorysort.comparator;

import java.util.Comparator;

import jp.reflexworks.atom.entry.EntryBase;

/**
 * キー項目のコンパレータクラス
 */
public class KeyComparator implements Comparator<EntryBase> {
	
	/** 降順の場合true */
	private boolean isDesc;
	
	/**
	 * String型項目用コンパレータ
	 * @param isDesc 降順の場合true
	 */
	public KeyComparator(boolean isDesc) {
		this.isDesc = isDesc;
	}

	/**
	 * 比較
	 * @param entry1 引数1
	 * @param entry2 引数2
	 * @return 最初の引数が2番目の引数より小さい場合は負の整数、
	 *         両方が等しい場合は0、
	 *         最初の引数が2番目の引数より大きい場合は正の整数。
	 */
	@Override
	public int compare(EntryBase entry1, EntryBase entry2) {
		String value1 = entry1.getMyUri();
		String value2 = entry2.getMyUri();
		
		// nullチェック (nullは最後尾)
		if (value2 == null) {
			if (value1 == null) {
				return 0;
			}
			return -1;
		} else if (value1 == null) {
			return 1;
		}
		
		// 比較
		int compare = value1.compareTo(value2);
		if (compare == 0) {
			return 0;
		}
		if (isDesc) {
			if (compare < 0) {
				return 1;
			} else {
				return -1;
			}
		} else {
			return compare;
		}
	}

}
