package jp.reflexworks.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import jp.reflexworks.pdf.exception.IllegalPdfParameterException;
import jp.reflexworks.taggingservice.api.ReflexContentInfo;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * ReflexPdf ユーティリティクラス.
 */
public class ReflexPdfUtil {
	
	/**
	 * コンテンツ取得.
	 * URLリクエスト、またはTaggingServiceのHTMLコンテントデータを取得し、バイト配列で返却する。
	 * @param uri URI
	 * @param reflexContext ReflexContext
	 */
	public static byte[] getContent(String uri, ReflexContext reflexContext) 
	throws IOException, TaggingException {
		if (StringUtils.isBlank(uri)) {
			throw new IllegalPdfParameterException("uri is required.");
		}
		byte[] content = null;
		if (uri.startsWith("http:") || uri.startsWith("https:")) {
			URL url = new URL(uri);
			URLConnection conn = url.openConnection();
			try (InputStream is = conn.getInputStream()) {
				if (is != null) {
					content = is.readAllBytes();
				}
			}
		} else {
			//content = reflexContext.getHtmlContent(uri);
			ReflexContentInfo contentInfo = reflexContext.getContent(uri);
			if (contentInfo == null || contentInfo.getData() == null) {
				throw new IllegalPdfParameterException("content is not found. " + uri);
			}
			content = contentInfo.getData();
		}
		return content;
	}
	
	/**
	 * 署名のためのPKCS#12形式ファイル取得.
	 * TaggingServiceのコンテントデータを取得し、バイト配列で返却する。
	 * @param uri URI
	 * @param reflexContext ReflexContext
	 */
	public static byte[] getSignatureContent(String uri, ReflexContext reflexContext) 
	throws IOException, TaggingException {
		if (StringUtils.isBlank(uri)) {
			throw new IllegalPdfParameterException("uri is required.");
		}
		ReflexContentInfo contentInfo = reflexContext.getContent(uri);
		byte[] ret = null;
		if (contentInfo != null) {
			ret = contentInfo.getData();
		}
		if (ret == null || ret.length <= 0) {
			throw new IllegalPdfParameterException("No content. " + uri);
		}
		return ret;
	}

}
