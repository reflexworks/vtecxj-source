package jp.reflexworks.taggingservice.bdb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jp.reflexworks.servlet.util.UrlUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.servlet.BDBEntryRequest;
import jp.reflexworks.taggingservice.util.Constants;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * Entryサーバ ユーティリティ
 */
public class BDBEntryUtil {

	/**
	 * リクエストヘッダからIDリストを取得.
	 * @param req リクエスト
	 * @return IDリスト
	 */
	public static List<String> getIdsFromHeader(BDBEntryRequest req) {
		String idsStr = UrlUtil.urlDecode(req.getHeader(Constants.HEADER_ID));
		if (StringUtils.isBlank(idsStr)) {
			return null;
		}
		String[] idArray = idsStr.split(Constants.HEADER_VALUE_SEPARATOR);
		return Arrays.asList(idArray);
	}

	/**
	 * リクエストから複数Entryを分割しリストにして返却.
	 * @param req リクエスト
	 * @return Entryバイト配列リスト
	 */
	public static List<byte[]> getDataListFromRequest(BDBEntryRequest req)
	throws IOException {
		byte[] payload = req.getPayload();
		if (payload == null || payload.length == 0) {
			return null;
		}
		String entryLengthsStr = req.getHeader(Constants.HEADER_ENTRY_LENGTH);
		if (StringUtils.isBlank(entryLengthsStr)) {
			return null;
		}

		List<byte[]> dataList = new ArrayList<>();
		String[] entryLengthArray = entryLengthsStr.split(Constants.HEADER_VALUE_SEPARATOR);
		int srcPos = 0;
		for (String entryLengthStr : entryLengthArray) {
			try {
				int entryLength = Integer.parseInt(entryLengthStr);
				byte[] data = new byte[entryLength];
				if (entryLength > 0) {
					System.arraycopy(payload, srcPos, data, 0, entryLength);
					srcPos += entryLength;
				}
				dataList.add(data);

			} catch (NumberFormatException e) {
				throw new IllegalParameterException("NumberFormatException: " + entryLengthStr, e);
			}
		}
		return dataList;
	}

	/**
	 * レスポンスヘッダ用 Entryデータバイト配列長文字列を取得.
	 * {データ長};{データ長}; ...
	 * @param dataList レスポンスデータリスト
	 * @return レスポンスヘッダ用 Entryデータバイト配列長文字列
	 */
	public static String getHeaderEntryLength(List<byte[]> dataList) {
		if (dataList == null || dataList.isEmpty()) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		boolean isFirst = true;
		for (byte[] data : dataList) {
			if (isFirst) {
				isFirst = false;
			} else {
				sb.append(Constants.HEADER_VALUE_SEPARATOR);
			}
			if (data != null) {
				sb.append(data.length);
			} else {
				sb.append(0);
			}
		}
		return sb.toString();
	}

	/**
	 * Entryバイト配列リストを1つのバイト配列に並べる.
	 * @param dataList Entryバイト配列
	 * @return Entryバイト配列リストを1つのバイト配列に並べたバイト配列
	 */
	public static byte[] lineupData(List<byte[]> dataList) {
		if (dataList == null || dataList.isEmpty()) {
			return null;
		}
		int size = 0;
		for (byte[] data : dataList) {
			if (data != null) {
				size += data.length;
			}
		}
		byte[] allData = new byte[size];
		int destPos = 0;
		for (byte[] data : dataList) {
			if (data != null) {
				System.arraycopy(data, 0, allData, destPos, data.length);
				destPos += data.length;
			}
		}
		return allData;
	}

}
