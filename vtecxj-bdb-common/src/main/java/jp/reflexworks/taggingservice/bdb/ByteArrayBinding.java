package jp.reflexworks.taggingservice.bdb;

import com.sleepycat.bind.EntryBinding;
import com.sleepycat.je.DatabaseEntry;

/**
 * DatabaseEntryをバイト配列に変換、またはその逆を行うクラス.
 */
public class ByteArrayBinding implements EntryBinding<byte[]> {
	
	/**
	 * コンストラクタ.
	 */
	public ByteArrayBinding() {
	}
	
	/**
	 * BDB格納データをバイト配列オブジェクトに変換
	 * @param dbData BDB形式データ
	 * @return バイト配列オブジェクト
	 */
	@Override
	public byte[] entryToObject(DatabaseEntry dbData) {
		if (dbData == null) {
			return null;
		}
		return dbData.getData();
	}
	
	/**
	 * オブジェクトをBDB格納データに変換
	 * @param bytes オブジェクト
	 * @param data BDB形式データ(呼び出し時はnewしただけの空オブジェクト)
	 */
	@Override
	public void objectToEntry(byte[] bytes, DatabaseEntry data) {
		if (bytes == null) {
			return;
		}
		data.setData(bytes);
	}

}
