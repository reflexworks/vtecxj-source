package jp.reflexworks.taggingservice.model;

import java.util.Map;

/**
 * Entryサーバへの複数指定情報
 */
public class EntryMultipleInfo {

	/** Entryバイト配列を並べたもの */
	private byte[] entriesData;
	/** 追加リクエストヘッダ（IDリスト、バイト長リスト） */
	private Map<String, String> additionalHeaders;

	/**
	 * コンストラクタ.
	 * @param entriesData Entryバイト配列を並べたもの
	 * @param additionalHeaders 追加リクエストヘッダ（IDリスト、バイト長リスト）
	 */
	public EntryMultipleInfo(byte[] entriesData,  Map<String, String> additionalHeaders) {
		this.entriesData = entriesData;
		this.additionalHeaders = additionalHeaders;
	}

	/**
	 * Entryバイト配列を並べたものを取得
	 * @return Entryバイト配列を並べたもの
	 */
	public byte[] getEntriesData() {
		return entriesData;
	}

	/**
	 * IDリスト文字列を取得
	 * @return IDリスト文字列
	 */
	public Map<String, String> getAdditionalHeaders() {
		return additionalHeaders;
	}

	/**
	 * このクラスの文字列表現.
	 * @return このクラスの文字列表現
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		if (additionalHeaders != null) {
			boolean isFirst = true;
			for (Map.Entry<String, String> mapEntry : additionalHeaders.entrySet()) {
				if (isFirst) {
					isFirst = false;
				} else {
					sb.append(", ");
				}
				sb.append(mapEntry.getKey());
				sb.append(":");
				sb.append(mapEntry.getValue());
			}
		}
		return sb.toString();
	}

}
