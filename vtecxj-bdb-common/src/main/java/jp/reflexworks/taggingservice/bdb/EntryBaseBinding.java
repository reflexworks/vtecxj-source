package jp.reflexworks.taggingservice.bdb;

import java.io.IOException;

import com.sleepycat.bind.EntryBinding;
import com.sleepycat.je.DatabaseEntry;

import jp.sourceforge.reflex.util.DeflateUtil;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.mapper.CipherUtil;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.taggingservice.exception.BindException;
import jp.reflexworks.taggingservice.util.EntrySerializer;

/**
 * DatabaseEntryをEntryBaseに変換、またはその逆を行うクラス.
 */
public class EntryBaseBinding implements EntryBinding<EntryBase> {
	
	/** デフレート圧縮を行わない場合true */
	private boolean isDisableDeflate = BDBConst.IS_DISABLE_DEFLATE;
	/** FeedTemplateMapper */
	private FeedTemplateMapper mapper;
	/** DeflateUtil */
	private DeflateUtil deflateUtil;
	
	/**
	 * コンストラクタ.
	 * @param mapper ResourceMapper
	 * @param deflateUtil DeflateUtil
	 */
	public EntryBaseBinding(FeedTemplateMapper mapper, DeflateUtil deflateUtil) {
		this.mapper = mapper;
		if (!isDisableDeflate) {
			this.deflateUtil = deflateUtil;
		}
	}
	
	/**
	 * BDB格納データをEntryBaseオブジェクトに変換
	 * @param dbData BDB形式データ
	 * @return Entryオブジェクト
	 */
	@Override
	public EntryBase entryToObject(DatabaseEntry dbData) {
		if (dbData == null) {
			return null;
		}
		byte[] data = dbData.getData();
		if (data == null || data.length == 0) {
			return null;
		}
		
		try {
			CipherUtil cipherUtil = new CipherUtil();
			return EntrySerializer.deserializeEntry(mapper, data, cipherUtil,
					deflateUtil);
			
		} catch (IOException e) {
			throw new BindException(e);
		}
	}
	
	/**
	 * EntryBaseオブジェクトをBDB格納データに変換
	 * @param entry Entry
	 * @param data BDB形式データ(呼び出し時はnewしただけの空オブジェクト)
	 */
	@Override
	public void objectToEntry(EntryBase entry, DatabaseEntry data) {
		if (entry == null) {
			return;
		}
		try {
			// EntryオブジェクトをMessagePack形式に変換、Deflate圧縮。
			CipherUtil cipherUtil = new CipherUtil();
			byte[] regData = EntrySerializer.serialize(mapper, entry, 
					cipherUtil, deflateUtil);
			
			// 3. DatabaseEntryオブジェクトにセット
			data.setData(regData);
		
		} catch (IOException e) {
			throw new BindException(e);
		}
	}

}
