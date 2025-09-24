package jp.reflexworks.taggingservice.plugin;

import java.io.IOException;

import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.exception.TaggingException;

/**
 * PDF生成管理インタフェース.
 * このクラスを実装するクラスは起動時に1つのインスタンスが生成され、staticに保持されます。
 * @param <T> コネクション
 */
public interface PdfManager extends ReflexPlugin {

	/**
	 * PDF生成.
	 * @param htmlTemplate HTML形式テンプレート
	 * @param reflexContext ReflexContext
	 * @return PDFデータ
	 */
	public byte[] toPdf(String htmlTemplate, ReflexContext reflexContext)
	throws IOException, TaggingException;

}
