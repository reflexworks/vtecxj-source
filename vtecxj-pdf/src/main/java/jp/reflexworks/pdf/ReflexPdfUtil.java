package jp.reflexworks.pdf;

import java.io.IOException;

import jp.reflexworks.pdf.exception.IllegalPdfParameterException;
import jp.reflexworks.taggingservice.api.ReflexContentInfo;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * ReflexPdf ユーティリティクラス.
 */
public class ReflexPdfUtil {
	
	/**
	 * コンテンツ取得.
	 * TaggingServiceのコンテントデータを取得し、バイト配列で返却する。
	 * @param uri URI
	 * @param reflexContext ReflexContext
	 */
	public static byte[] getContent(String uri, ReflexContext reflexContext) 
	throws IOException, TaggingException {
		if (StringUtils.isBlank(uri)) {
			throw new IllegalPdfParameterException("Uri is required.");
		}
		ReflexContentInfo contentInfo = reflexContext.getContent(uri);
		if (contentInfo == null || contentInfo.getData() == null) {
			throw new IllegalPdfParameterException("Content is not found. " + uri);
		}
		return contentInfo.getData();
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
			throw new IllegalPdfParameterException("Uri is required.");
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
	
	/**
	 * タイムスタンプサーバURLの入力チェック
	 * @param url タイムスタンプサーバURL
	 * @param reflexContext ReflexContext
	 */
	public static void checkTimestampUrl(String url, ReflexContext reflexContext) {
		if (StringUtils.isBlank(url)) {
			throw new IllegalParameterException("Timestamp URL is required.");
		}
		if (!url.startsWith(ReflexPdfConst.SCHEMA_HTTP_COLON_SLASH) &&
				!url.startsWith(ReflexPdfConst.SCHEMA_HTTPS_COLON_SLASH)) {
			throw new IllegalParameterException("Invalid URL scheme of timestamp.");
		}
		String serviceName = reflexContext.getServiceName();
		String timestampUrl = TaggingEnvUtil.getProp(serviceName, 
				ReflexPdfSettingConst.PDF_TIMESTAMP_URL, null);
		if (StringUtils.isBlank(timestampUrl)) {
			throw new IllegalParameterException("The timestamp URL is not set.");
		}
		if (!url.equals(timestampUrl)) {
			throw new IllegalParameterException("Timestamp URLs are not allowed.");
		}
	}

}
