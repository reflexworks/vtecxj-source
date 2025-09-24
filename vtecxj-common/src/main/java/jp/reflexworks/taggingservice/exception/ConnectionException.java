package jp.reflexworks.taggingservice.exception;

import java.io.IOException;

/**
 * データストアコネクションエラー.
 * この例外をキャッチした場合リトライを行う。
 */
public class ConnectionException extends IOException {
	
	/** serialVersionUID */
	private static final long serialVersionUID = -5013263765731780637L;

	public ConnectionException() {
		super();
	}

	public ConnectionException(String msg) {
		super(msg);
	}

	public ConnectionException(Throwable e) {
		super(e);
	}
	
	public ConnectionException(String msg, Throwable e) {
		super(msg, e);
	}

}
