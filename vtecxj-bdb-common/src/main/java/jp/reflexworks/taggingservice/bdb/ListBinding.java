package jp.reflexworks.taggingservice.bdb;

import java.util.ArrayList;
import java.util.List;

import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;

/**
 * BDBの文字列値をStringクラスに変換、またはその逆を行うクラス.
 */
public class ListBinding extends TupleBinding<List<String>> {

	// Implement this abstract method. Used to convert
	// a DatabaseEntry to an Counter object.
	/**
	 * BDBEntryをオブジェクトに変換.
	 * @param ti BDBEntry取得クラス.
	 * @return オブジェクト
	 */
	@Override
	public List<String> entryToObject(TupleInput ti) {
		if (ti == null) {
			return null;
		}
		List<String> list = new ArrayList<String>();
		while (ti.available() > 0) {
			list.add(ti.readString());
			
			//System.out.println("[ListBinding] ti.available() = " + ti.available());
			
		}
		return list;
	}

	/**
	 * オブジェクトの内容をBDBEntryに書き込む.
	 * @param incrementInfo オブジェクト
	 * @param to BDBEntry出力クラス
	 */
	// Implement this abstract method. Used to convert a
	// Counter object to a DatabaseEntry object.
	@Override
	public void objectToEntry(List<String> list, TupleOutput to) {
		if (list == null) {
			return;
		}
		for (String str : list) {
			to.writeString(str);
		}
	}
}
