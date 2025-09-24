package jp.reflexworks.taggingservice.util;

import java.util.Comparator;
import java.util.Map;

/**
 * アクセス時刻でソートする.
 */
public class AccessTimeComparator implements Comparator<String> {

	private Map<String, Long> accessTimeMap;
	
	/**
	 * コンストラクタ.
	 * @param accessTimeMap アクセス時刻マップ(キー:カーソルリストキー、値:アクセス時刻ミリ秒)
	 */
	public AccessTimeComparator(Map<String, Long> accessTimeMap) {
		this.accessTimeMap = accessTimeMap;
	}
	
	/**
	 * 比較.
	 * @param o1 カーソルリストキー
	 * @param o2 カーソルリストキー
	 * @return 最初の引数が2番目の引数より小さい場合は負の整数、
	 *         両方が等しい場合は0、
	 *         最初の引数が2番目の引数より大きい場合は正の整数。
	 */
	@Override
	public int compare(String o1, String o2) {
		Long accessTime1 = accessTimeMap.get(o1);
		Long accessTime2 = accessTimeMap.get(o2);
		if (accessTime1 == null) {
			if (accessTime2 == null) {
				return 0;
			} else {
				return 1;
			}
		} else if (accessTime2 == null) {
			return -1;
		}
		// 時間が大きい方が新しい
		if (accessTime1 > accessTime2) {
			return -1;
		} else if (accessTime1 < accessTime2) {
			return 1;
		} else {
			return 0;
		}
	}

}
