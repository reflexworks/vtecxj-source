package jp.reflexworks.taggingservice.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.cloud.storage.Blob.BlobSourceOption;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.taggingservice.api.ReflexContentInfo;
import jp.reflexworks.taggingservice.blogic.ContentBlogic;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * Google Cloud Storage用コンテンツ情報.
 */
public class CloudStorageInfo implements ReflexContentInfo {

	/** Blob */
	private CloudStorageBlob blob;
	/** ヘッダ情報 */
	private Map<String, String> headers;
	/** URI */
	private String uri;
	/** データ */
	private byte[] data;
	/** データ取得済フラグ */
	private boolean isDataAcquired;
	/** Etag */
	private String etag;
	
	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param uri URI
	 * @param blob Blob
	 * @param entry Entry
	 */
	public CloudStorageInfo(String uri, CloudStorageBlob blob, EntryBase entry) {
		this.uri = uri;
		this.blob = blob;
		
		if (blob != null) {
			headers = new HashMap<String, String>();
			String contentType = blob.getContentType();
			if (contentType != null) {
				headers.put(ReflexServletConst.HEADER_CONTENT_TYPE, contentType);
			}
			String contentDisposition = blob.getContentDisposition();
			if (contentDisposition != null) {
				headers.put(ReflexServletConst.HEADER_CONTENT_DISPOSITION, contentDisposition);
			}
			String contentEncoding = blob.getContentEncoding();
			if (contentEncoding != null) {
				headers.put(ReflexServletConst.HEADER_CONTENT_ENCODING, contentEncoding);
			}
			String contentLanguage = blob.getContentLanguage();
			if (contentLanguage != null) {
				headers.put(ReflexServletConst.HEADER_CONTENT_LANGUAGE, contentLanguage);
			}
			Long contentSize = blob.getSize();
			if (contentSize != null) {
				headers.put(ReflexServletConst.HEADER_CONTENT_LENGTH, String.valueOf(contentSize));
			}
			// Etag: EntryのID
			etag = ContentBlogic.getEtagByEntry(entry);
			// LastModified
			Date date = null;
			OffsetDateTime blobOffsetDateTime = blob.getUpdateTimeOffsetDateTime();
			if (blobOffsetDateTime != null) {
				date = Date.from(blobOffsetDateTime.toInstant());
			}
			String lastModified = ContentBlogic.getLastModified(date);
			headers.put(ReflexServletConst.HEADER_LAST_MODIFIED, lastModified);
		}
	}

	/**
	 * URIを取得.
	 * @return URI
	 */
	public String getUri() {
		return uri;
	}

	/**
	 * コンテンツデータを取得.
	 * @return コンテンツデータ
	 */
	public byte[] getData() throws IOException {
		if (!isDataAcquired) {
			try {
				this.data = blob.getContent(BlobSourceOption.generationMatch());
				isDataAcquired = true;
			} catch (CloudStorageException e) {
				logger.debug("[upload] CloudStorageException. " + e.getMessage());
				CloudStorageUtil.convertIOError(e);
			}
		}
		return data;
	}

	/**
	 * ヘッダ情報を取得.
	 * @return ヘッダ情報
	 */
	public Map<String, String> getHeaders() {
		return headers;
	}
	
	/**
	 * 指定された名前のヘッダ情報を取得.
	 * @param name 名前
	 * @return 値
	 */
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
	public String getEtag() {
		return etag;
	}

	/**
	 * 文字列表現を取得
	 * @return このオブジェクトの文字列表現
	 */
	public String toString() {
		return uri;
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
		byte[] bytes = getData();
		if (bytes != null) {
			return new ByteArrayInputStream(bytes);
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
