package jp.reflexworks.taggingservice.secret;

import java.io.IOException;

/**
 * Secret Manager例外.
 * Google Secret Managerへのアクセスに失敗した場合(リトライ対象外のエラー、
 * またはリトライ回数を超えたエラー)にスローする。
 */
public class ReflexSecretException extends IOException {

	/** serialVersionUID */
	private static final long serialVersionUID = -6274108539201735062L;

	public ReflexSecretException() {
		super();
	}

	public ReflexSecretException(String msg) {
		super(msg);
	}

	public ReflexSecretException(Throwable e) {
		super(e);
	}

	public ReflexSecretException(String msg, Throwable e) {
		super(msg, e);
	}

}
