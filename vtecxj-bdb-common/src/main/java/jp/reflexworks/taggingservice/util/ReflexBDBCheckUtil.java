package jp.reflexworks.taggingservice.util;

import jp.reflexworks.taggingservice.env.ReflexEnvUtil;
import jp.reflexworks.taggingservice.env.BDBEnvConst;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;

/**
 * BDBチェックユーティリティ
 */
public class ReflexBDBCheckUtil {

	/**
	 * Entryのサイズチェック
	 * @param data Entryのバイト配列オブジェクト
	 * @param keyStr キー (メッセージ用)
	 */
	public static void checkEntryMaxBytes(byte[] data, String keyStr) {
		long maxSize = ReflexEnvUtil.getSystemPropLong(BDBEnvConst.ENTRY_MAX_BYTES,
				BDBEnvConst.ENTRY_MAX_BYTES_DEFAULT);
		long dataSize = data.length;
		if (dataSize > maxSize) {
			throw new IllegalParameterException("The size of the Entry is too large. " + keyStr);
		}
	}

}
