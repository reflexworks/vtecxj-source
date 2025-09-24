package jp.reflexworks.taggingservice.bdb;

import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;

import jp.reflexworks.taggingservice.model.IncrementInfo;

/**
 * BDBのIncrement情報をIncrementクラスに変換、またはその逆を行うクラス.
 */
public class IncrementBinding extends TupleBinding<IncrementInfo> {

	// Implement this abstract method. Used to convert
	// a DatabaseEntry to an Counter object.
	/**
	 * BDBEntryをオブジェクトに変換.
	 * @param ti BDBEntry取得クラス.
	 * @return オブジェクト
	 */
	@Override
	public IncrementInfo entryToObject(TupleInput ti) {
		long _num = ti.readLong();
		String _range = ti.readString();
		
		return new IncrementInfo(_num, _range);
	}

	/**
	 * オブジェクトの内容をBDBEntryに書き込む.
	 * @param incrementInfo オブジェクト
	 * @param to BDBEntry出力クラス
	 */
	// Implement this abstract method. Used to convert a
	// Counter object to a DatabaseEntry object.
	@Override
	public void objectToEntry(IncrementInfo incrementInfo, TupleOutput to) {
		to.writeLong(incrementInfo.getNum());
		to.writeString(incrementInfo.getRange());
	}
}
