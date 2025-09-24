package jp.reflexworks.taggingservice.api;

import java.io.IOException;
import java.util.Map;

import jakarta.activation.DataSource;

/**
 * コンテンツ登録・取得のための情報引き渡しインターフェース.
 */
public interface ReflexContentInfo extends DataSource {

	/**
	 * コンテンツのファイル名を取得.
	 * @return ファイル名
	 */
	public String getUri();

	/**
	 * コンテンツデータを取得.
	 * @return コンテンツデータ
	 */
	public byte[] getData() throws IOException;

	/**
	 * ヘッダ情報を取得.
	 * @return ヘッダ情報
	 */
	public Map<String, String> getHeaders();
	
	/**
	 * 指定された名前のヘッダ情報を取得.
	 * @param name 名前
	 * @return 値
	 */
	public String getHeader(String name);

	/**
	 * コンテンツのEtagを取得.
	 * @return Etag
	 */
	public String getEtag();

}
