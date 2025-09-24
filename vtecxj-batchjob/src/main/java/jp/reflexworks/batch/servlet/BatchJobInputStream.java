package jp.reflexworks.batch.servlet;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;

/**
 * ServletInputStreamを継承したクラス.
 */
public class BatchJobInputStream extends ServletInputStream {

	/** InputStream */
	private InputStream in;

	/**
	 * コンストラクタ.
	 * @param payload データ
	 */
	public BatchJobInputStream(byte[] payload) {
		this.in = new BufferedInputStream(
				new ByteArrayInputStream(payload));
	}

	/**
	 * read.
	 * @return int
	 */
	@Override
	public int read() throws IOException {
		return in.read();
	}

	@Override
	public boolean isFinished() {
		// 自動生成されたメソッド・スタブ
		return false;
	}

	@Override
	public boolean isReady() {
		// 自動生成されたメソッド・スタブ
		return false;
	}

	@Override
	public void setReadListener(ReadListener readListener) {
		// 自動生成されたメソッド・スタブ

	}

}
