package jp.reflexworks.taggingservice.storage;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.taggingservice.api.ReflexContentInfo;
import jp.reflexworks.taggingservice.blogic.ContentBlogic;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;
import jp.sourceforge.reflex.util.FileUtil;

/**
 * ローカルキャッシュ用コンテンツ情報.
 */
public class LocalCacheInfo implements ReflexContentInfo {

	/** ローカルコンテンツファイル */
	private File file;
	/** ヘッダ情報 */
	private Map<String, String> headers;
	/** URI */
	private String uri;
	/** Etag */
	private String etag;
	/** ローカルコンテンツのバイト配列 */
	private byte[] data;
	/** データ取得済フラグ */
	private boolean isDataAcquired;

	/**
	 * コンストラクタ.
	 * @param uri URI
	 * @param file ローカルコンテンツファイル.
	 * @param headers コンテンツのヘッダ情報
	 * @param entry コンテンツのEntry
	 */
	public LocalCacheInfo(String uri, File file, Map<String, String> headers,
			EntryBase entry) {
		this.uri = uri;
		this.file = file;
		this.headers = headers;
		// EtagはEntryのID
		this.etag = ContentBlogic.getEtagByEntry(entry);
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
		if (isDataAcquired) {
			return new ByteArrayInputStream(data);
		} else {
			return new FileInputStream(file);
		}
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

	/**
	 * URIを取得.
	 * @return URI
	 */
	@Override
	public String getUri() {
		return uri;
	}

	/**
	 * コンテンツデータを取得.
	 * @return コンテンツデータ
	 */
	@Override
	public byte[] getData() throws IOException {
		if (!isDataAcquired) {
			data = FileUtil.readFile(file);
			isDataAcquired = true;
		}
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
	 * 指定された名前のヘッダ情報を取得.
	 * @param name 名前
	 * @return 値
	 */
	@Override
	public String getHeader(String name) {
		if (headers == null || name == null) {
			return null;
		}
		return headers.get(name);
	}

	/**
	 * コンテンツのEtagを取得.
	 * @return Etag
	 */
	@Override
	public String getEtag() {
		return etag;
	}

}
