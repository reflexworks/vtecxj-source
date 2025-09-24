package jp.reflexworks.taggingservice.memorysort.comparator;

import java.util.Comparator;

import jp.reflexworks.atom.entry.EntryBase;

/**
 * Boolean型項目のコンパレータクラス
 */
public class BooleanComparator implements Comparator<EntryBase> {

	/** 比較対象項目 */
	private String fieldName;
	/** 降順の場合true */
	private boolean isDesc;

	/**
	 * Boolean型項目用コンパレータ
	 * @param name 比較対象項目
	 * @param isDesc 降順の場合true
	 */
	public BooleanComparator(String fieldName, boolean isDesc) {
		this.fieldName = fieldName;
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
		Boolean tmpValue1 = (Boolean)entry1.getValue(fieldName);
		Boolean tmpValue2 = (Boolean)entry2.getValue(fieldName);

		// nullチェック (nullは最後尾)
		if (tmpValue2 == null) {
			if (tmpValue1 == null) {
				return 0;
			}
			return -1;
		} else if (tmpValue1 == null) {
			return 1;
		}

		boolean value1 = tmpValue1.booleanValue();
		boolean value2 = tmpValue2.booleanValue();

		// 比較
		// true、falseの順
		if (value1 == value2) {
			return 0;
		} else if (value1) {
			if (isDesc) {
				return 1;
			} else {
				return -1;
			}
		} else {
			if (isDesc) {
				return -1;
			} else {
				return 1;
			}
		}
	}

}
