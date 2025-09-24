package jp.reflexworks.taggingservice.model;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.taggingservice.api.ReflexContentInfo;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * エントリーのコンテント情報.
 */
public class EntryContentInfo implements ReflexContentInfo {
	
	/** ヘッダ情報 */
	private Map<String, String> headers;
	/** URI */
	private String uri;
	/** データ */
	private byte[] data;
	/** Etag */
	private String etag;
	
	/**
	 * コンストラクタ.
	 * @param uri URI
	 * @param data データ
	 * @param headers ヘッダ情報
	 * @param etag Etag
	 */
	public EntryContentInfo(String uri, byte[] data, Map<String, String> headers,
			String etag) {
		this.uri = uri;
		this.data = data;
		this.headers = headers;
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
		return data;
	}

	/**
	 * ヘッダ情報を取得.
	 * @return ヘッダ情報
	 */
	@Override
	public Map<String, String> getHeaders() {
		return headers;
	}

	/**
	 * ヘッダ情報を取得.
	 * @param name ヘッダ情報のキー
	 * @return ヘッダ情報の値
	 */
	@Override
	public String getHeader(String name) {
		if (headers != null) {
			return headers.get(name);
		}
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
		if (headers != null) {
			return headers.get(ReflexServletConst.HEADER_CONTENT_TYPE);
		}
		return null;
	}

	/**
	 * InputStreamを取得.
	 * @return InputStream
	 */
	@Override
	public InputStream getInputStream() throws IOException {
		if (data != null) {
			return new ByteArrayInputStream(data);
		}
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
