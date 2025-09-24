package jp.reflexworks.taggingservice.memorysort.comparator;

import java.util.Comparator;
import java.util.Date;

import jp.reflexworks.atom.entry.EntryBase;

/**
 * Date型項目のコンパレータクラス
 */
public class DateComparator implements Comparator<EntryBase> {
	
	/** 比較対象項目 */
	private String fieldName;
	/** 降順の場合true */
	private boolean isDesc;
	
	/**
	 * Date型項目用コンパレータ
	 * @param name 比較対象項目
	 * @param isDesc 降順の場合true
	 */
	public DateComparator(String fieldName, boolean isDesc) {
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
		Date value1 = (Date)entry1.getValue(fieldName);
		Date value2 = (Date)entry2.getValue(fieldName);
		
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
