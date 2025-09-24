package jp.reflexworks.taggingservice.storage;

import com.google.cloud.storage.StorageException;

/**
 * StorageException ラップクラス.
 * StorageException は RuntimeException を継承しているため、予期しないThrowを防ぐためのクラス。
 */
public class CloudStorageException extends Exception {

	/** StorageException */
	private StorageException se;
	
	/**
	 * コンストラクタ.
	 * @param se StorageException
	 */
	public CloudStorageException(StorageException se) {
		this.se = se;
		initCause(se);
	}
	
	/**
	 * エラーメッセージを取得.
	 * @return エラーメッセージ
	 */
	public String getMessage() {
		if (se == null) {
			return null;
		}
		return se.getMessage();
	}
	
	/**
	 * 原因例外を取得.
	 * @return 原因となる例外
	 */
	public Throwable getCause() {
		if (se == null) {
			return null;
		}
		return se.getCause();
	}
	
	/**
	 * Returns the code associated with this exception.
	 * @return the code
	 */
	public int getCode() {
		if (se == null) {
			return 0;
		}
		return se.getCode();
	}
	
	/**
	 * Returns the reason that caused the exception.
	 * @return the reason
	 */
	public String getReason() {
		if (se == null) {
			return null;
		}
		return se.getReason();
	}
	
	/**
	 * Returns {@code true} when it is safe to retry the operation that caused this exception.
	 * @return true / false
	 */
	public boolean isRetryable() {
		if (se == null) {
			return false;
		}
		return se.isRetryable();
	}

}
