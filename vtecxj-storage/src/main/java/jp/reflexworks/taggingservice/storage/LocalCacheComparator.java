package jp.reflexworks.taggingservice.storage;

import java.io.File;
import java.util.Comparator;

/**
 * ローカルキャッシュを更新日時の古いもの順に並べる.
 */
public class LocalCacheComparator implements Comparator<File> {

	/**
	 * ソート時の比較処理.
	 * @return 最初の引数が2番目の引数より小さい場合は負の整数、両方が等しい場合は0、最初の引数が2番目の引数より大きい場合は正の整数。
	 *         f1 < f2 : -1、f1 == f2 : 0、f1 > f2 : 1
	 */
	@Override
	public int compare(File f1, File f2) {
		long lastModified1 = f1.lastModified();
		long lastModified2 = f2.lastModified();
		if (lastModified1 < lastModified2) {
			return -1;
		} else if (lastModified1 > lastModified2) {
			return 1;
		} else {	// equal
			return 0;
		}
	}

}
