package jp.reflexworks.taggingservice.util;

import java.io.UnsupportedEncodingException;

import org.apache.commons.codec.binary.Base64;

import jp.sourceforge.reflex.util.StringUtils;

/**
 * カーソルのBase64変換ユーティリティ.
 */
public class PointerUtil {

	private static final String ENCODING = Constants.ENCODING;

	/**
	 * エンコード
	 * @param pointerStr カーソル
	 * @return エンコードされたカーソル
	 */
	public static final String encode(String pointerStr) {
		if (StringUtils.isBlank(pointerStr)) {
			return null;
		}
		try {
			byte[] pointerBytes = pointerStr.getBytes(ENCODING);
			return encode(pointerBytes);

		} catch (UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * エンコード
	 * @param pointerBytes カーソル
	 * @return エンコードされたカーソル
	 */
	public static final String encode(byte[] pointerBytes) {
		if (pointerBytes == null || pointerBytes.length == 0) {
			return null;
		}
		try {
			return new String(Base64.encodeBase64(pointerBytes), ENCODING);

		} catch (UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * デコード
	 * @param encodingStr エンコードされたカーソル
	 * @return デコードされたカーソル
	 */
	public static final String decode(String encodingStr) {
		if (StringUtils.isBlank(encodingStr)) {
			return null;
		}
		try {
			byte[] encodingBytes = encodingStr.getBytes(ENCODING);
			return decode(encodingBytes);

		} catch (UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * デコード
	 * @param encodingBytes エンコードされたカーソル
	 * @return デコードされたカーソル
	 */
	public static final String decode(byte[] encodingBytes) {
		if (encodingBytes == null || encodingBytes.length == 0) {
			return null;
		}
		try {
			return new String(Base64.decodeBase64(encodingBytes), ENCODING);

		} catch (UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * カーソル文字列の編集
	 * 半角空白を+に置き換える。(URLエンコードされていないカーソルに対応)
	 * @param str カーソル文字列
	 * @return 編集したカーソル文字列
	 */
	public static String editPointerStr(String str) {
		String ret = null;
		if (str != null) {
			ret = str.replace(" ", "+");

		} else {
			ret = str;
		}
		return ret;
	}

}
