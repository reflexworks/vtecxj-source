package jp.reflexworks.taggingservice.storage;

import java.time.OffsetDateTime;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.StorageException;

/**
 * Blobのラップクラス
 */
public class CloudStorageBlob {
	
	/** Blob */
	private Blob blob;
	
	/**
	 * コンストラクタ
	 * @param blob Blob
	 */
	CloudStorageBlob(Blob blob) {
		this.blob = blob;
	}
	
	/**
	 * Content-Typeを取得
	 * @return Content-Type
	 */
	public String getContentType() {
		if (blob == null) {
			return null;
		}
		return blob.getContentType();
	}
	
	/**
	 * Content-Dispositionを取得
	 * @return Content-Disposition
	 */
	public String getContentDisposition() {
		if (blob == null) {
			return null;
		}
		return blob.getContentDisposition();
	}
	
	/**
	 * Content-Encodingを取得
	 * @return Content-Encoding
	 */
	public String getContentEncoding() {
		if (blob == null) {
			return null;
		}
		return blob.getContentEncoding();
	}
	
	/**
	 * Content-Languageを取得
	 * @return Content-Language
	 */
	public String getContentLanguage() {
		if (blob == null) {
			return null;
		}
		return blob.getContentLanguage();
	}
	
	/**
	 * Returns the content length of the data in bytes.
	 * @return Content-Length
	 */
	public Long getSize() {
		if (blob == null) {
			return null;
		}
		return blob.getSize();
	}
	
	/**
	 * LastModifiedを取得
	 * @return LastModified
	 */
	public OffsetDateTime getUpdateTimeOffsetDateTime() {
		if (blob == null) {
			return null;
		}
		return blob.getUpdateTimeOffsetDateTime();
	}
	
	/**
	 * コンテンツを取得.
	 * @param options BlobSourceOption
	 * @return コンテンツ(バイト配列)
	 * @throws CloudStorageException StorageExceptionのラップ
	 */
	public byte[] getContent(Blob.BlobSourceOption... options) 
	throws CloudStorageException {
		if (blob == null) {
			return null;
		}
		try {
			return blob.getContent(options);
		} catch (StorageException e) {
			throw new CloudStorageException(e);
		}
	}

}
