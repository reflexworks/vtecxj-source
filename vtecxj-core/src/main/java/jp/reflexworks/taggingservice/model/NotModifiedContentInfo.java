package jp.reflexworks.taggingservice.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import jp.reflexworks.taggingservice.api.ReflexContentInfo;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * 変更なしの場合のコンテント情報.
 */
public class NotModifiedContentInfo implements ReflexContentInfo {
	
	/** URI */
	private String uri;
	/** Etag */
	private String etag;
	
	/**
	 * コンストラクタ.
	 * @param uri URI
	 * @param etag Etag
	 */
	public NotModifiedContentInfo(String uri, String etag) {
		this.uri = uri;
		// Etag設定
		this.etag = etag;
	}

	/**
	 * URIを取得.
	 * @return URI
	 */
	@Override
	public String getUri() {
		return uri;
	}

	/**
	 * データを取得.
	 * @return データ
	 */
	@Override
	public byte[] getData() throws IOException {
		return null;
	}

	/**
	 * ヘッダ情報を取得.
	 * @return ヘッダ情報
	 */
	@Override
	public Map<String, String> getHeaders() {
		return null;
	}

	/**
	 * ヘッダ情報を取得.
	 * @param name ヘッダ情報のキー
	 * @return ヘッダ情報の値
	 */
	@Override
	public String getHeader(String name) {
		return null;
	}
	
	/**
	 * コンテンツのEtagを取得.
	 * @return Etag
	 */
	public String getEtag() {
		return etag;
	}

	/**
	 * Content-Typeを取得.
	 * @return Content-Type
	 */
	@Override
	public String getContentType() {
		return null;
	}

	/**
	 * InputStreamを取得.
	 * @return InputStream
	 */
	@Override
	public InputStream getInputStream() throws IOException {
		return null;
	}

	/**
	 * ファイル名を取得.
	 * uriのselfidを返却.
	 * @return ファイル名
	 */
	@Override
	public String getName() {
		if (!StringUtils.isBlank(uri)) {
			return TaggingEntryUtil.getSelfidUri(uri);
		}
		return null;
	}

	/**
	 * OutputStreamを取得.
	 * nullを返却します。
	 * @return null
	 */
	@Override
	public OutputStream getOutputStream() throws IOException {
		return null;
	}

}
