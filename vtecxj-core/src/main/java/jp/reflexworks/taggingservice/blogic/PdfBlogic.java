package jp.reflexworks.taggingservice.blogic;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.PdfManager;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.reflexworks.taggingservice.util.Constants;

/**
 * PDF生成ビジネスロジック.
 */
public class PdfBlogic {
	
	/**
	 * PDFを生成し、レスポンスに出力する.
	 * @param uri URI (認可チェックに使用)
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param reflexContext ReflexContext
	 */
	public void writePdf(String uri, ReflexRequest req, ReflexResponse resp, 
			ReflexContext reflexContext) 
	throws IOException, TaggingException {
		// 入力チェック
		CheckUtil.checkUri(uri);
		// ACLチェック
		ReflexAuthentication auth = reflexContext.getAuth();
		if (!auth.isExternal()) {	// Externalの場合はPDF生成可。
			// 指定されたuriの参照権限があればPDF生成可。
			AclBlogic aclBlogic = new AclBlogic();
			aclBlogic.checkAcl(uri, AclConst.ACL_TYPE_RETRIEVE, auth, 
					reflexContext.getRequestInfo(), reflexContext.getConnectionInfo());
		}

		// PDFテンプレート入力チェック
		byte[] payload = req.getPayload();
		CheckUtil.checkNotNull(payload, "PDF template");
		String htmlTemplate = new String(payload, Constants.ENCODING);
		CheckUtil.checkNotNull(htmlTemplate, "PDF template");
		
		// PDF生成
		byte[] pdfData = reflexContext.toPdf(htmlTemplate);
		// Content-Type
		resp.setContentType(ReflexServletConst.CONTENT_TYPE_PDF);
		// レスポンスにPDFデータを出力
		try (OutputStream out = new BufferedOutputStream(resp.getOutputStream())) {
			out.write(pdfData);
		}
	}

	/**
	 * PDF生成.
	 * @param htmlTemplate HTML形式テンプレート
	 * @param out PDF出力先
	 * @param reflexContext ReflexContext
	 * @return PDFデータ
	 */
	public byte[] toPdf(String htmlTemplate, ReflexContext reflexContext) 
	throws IOException, TaggingException {
		// PDF生成
		PdfManager pdfManager = TaggingEnvUtil.getPdfManager();
		if (pdfManager == null) {
			throw new InvalidServiceSettingException("PDF manager is nothing.");
		}
		return pdfManager.toPdf(htmlTemplate, reflexContext);
	}

}
