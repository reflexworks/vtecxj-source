package jp.reflexworks.taggingservice.blogic;

import java.io.IOException;

import jp.reflexworks.atom.api.AtomConst;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.PdfManager;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;

/**
 * PDF生成ビジネスロジック.
 */
public class PdfBlogic {

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

	/**
	 * PDF生成+コンテント登録.
	 * @param uri キー
	 * @param htmlTemplate HTML形式テンプレート
	 * @param reflexContext ReflexContext
	 * @return ファイル名
	 */
	public FeedBase putPdf(String uri, String htmlTemplate, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();

		// 入力チェック
		CheckUtil.checkUri(uri);
		CheckUtil.checkCommonUri(uri, serviceName);

		AclBlogic aclBlogic = new AclBlogic();
		if (!auth.isExternal()) {
			// $contentグループメンバーでなければエラー (リクエストから直接コンテンツ登録の場合)
			aclBlogic.checkAuthedGroup(auth, Constants.URI_GROUP_CONTENT);
		}
		// ACLチェック
		aclBlogic.checkAcl(uri, AtomConst.ACL_TYPE_UPDATE, auth,
				requestInfo, connectionInfo);
		
		// PDF生成
		byte[] data = toPdf(htmlTemplate, reflexContext);

		ContentBlogic contentBlogic = new ContentBlogic();
		// コンテンツ登録
		EntryBase entry = contentBlogic.upload(uri, data, null, false, reflexContext);

		if (entry == null) {
			return null;
		}
		FeedBase feed = TaggingEntryUtil.createFeed(serviceName);
		feed.addEntry(entry);
		return feed;
	}

}
