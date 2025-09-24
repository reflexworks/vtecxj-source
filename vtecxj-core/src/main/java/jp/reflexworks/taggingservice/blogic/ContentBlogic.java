package jp.reflexworks.taggingservice.blogic;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Part;
import jp.reflexworks.atom.api.AtomConst;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.servlet.util.HeaderUtil;
import jp.reflexworks.servlet.util.MultipartUtil;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContentInfo;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.EntryContentInfo;
import jp.reflexworks.taggingservice.model.NotModifiedContentInfo;
import jp.reflexworks.taggingservice.plugin.ContentManager;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.DateUtil;
import jp.sourceforge.reflex.util.FileUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * コンテンツを扱うビジネスロジック.
 */
public class ContentBlogic implements ReflexServletConst {

	/** 時刻フォーマット */
	public static final String DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
	/** タイムゾーン */
	public static final String DATE_ID = "GMT";
	/** Locale */
	public static final Locale DATE_LOCALE = Locale.ENGLISH;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * Conent-Typeがコンテント登録対象かどうか判定する。
	 * @param contentType Content-Type
	 * @return コンテント登録対象の場合true
	 */
	public static boolean isPutContent(String contentType) {
		/*
		if (!StringUtils.isBlank(contentType)) {
			if (contentType.startsWith(CONTENT_TYPE_IMAGE)) {
				return true;
			}
		}
		return false;
		*/
		return true;	// とりあえずアップロードされたものは全て受け付ける。
	}

	/**
	 * コンテンツを持つEntryかどうかを判定.
	 * @param entry Entry
	 * @return コンテンツを持つ場合true
	 */
	public static boolean isContentEntry(EntryBase entry) {
		if (entry != null && entry.content != null &&
				!StringUtils.isBlank(entry.content._$src)) {
			return true;
		}
		return false;
	}

	/**
	 * lastModifiedに設定する時刻文字列を返します.
	 * @param date 時刻
	 * @return lastModifiedに設定する時刻文字列
	 */
	public static String getLastModified(Date date) {
		if (date != null) {
			return DateUtil.getDateTimeFormat(date, DATE_FORMAT,
					DATE_ID, DATE_LOCALE);
		}
		return null;
	}

	/**
	 * コンテンツEntryからEtagを取得.
	 * ID + updatedを返却する。
	 * @param contentEntry コンテンツEntry
	 * @return Etag (ID + updated)
	 */
	public static String getEtagByEntry(EntryBase contentEntry) {
		if (contentEntry != null) {
			StringBuilder sb = new StringBuilder();
			sb.append(contentEntry.id);
			sb.append(",");
			sb.append(contentEntry.updated);
			return sb.toString();
		}
		return null;
	}

	/**
	 * リクエストデータをコンテント登録します.
	 * @param reflexContext ReflexContext
	 * @param isBySize 画像ファイルのサイズ展開を行う場合true
	 * @param isSignedUrl 署名付きURL取得の場合true
	 * @return ファイル名
	 *         feedのlinkリストにキーとContent-Typeを設定して返却します.
	 *         署名付きURL取得の場合は署名付きURLを返却します.
	 */
	public FeedBase putContent(ReflexContext reflexContext, boolean isBySize, boolean isSignedUrl)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		ReflexRequest req = reflexContext.getRequest();
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();

		// リクエストnullチェック
		CheckUtil.checkRequest(req);

		// Content-Typeチェック
		// multipart/formdataの場合、専用のメソッドを実行する。
		String contentType = req.getContentType();
		if (contentType != null && contentType.startsWith(
				ReflexServletConst.CONTENT_TYPE_MULTIPART_FORMDATA)) {
			return putMultipartContent(reflexContext, null, isBySize);
		}

		String uri = req.getRequestType().getUri();
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

		// リクエストヘッダを取得
		Map<String, String> headers = getHeaders(req);
		if (isSignedUrl) {
			// 署名付きURL取得
			return getSignedUrl(reflexContext, Constants.PUT, uri, headers);
		} else {
			// アップロード
			InputStream in = req.getInputStream();
			// ストリーム入力チェック
			CheckUtil.checkRequestPayload(in);

			EntryBase entry = null;
			byte[] data = req.getPayload();
			if (isEmptyPayload(data)) {
				// リクエストデータが0バイトの場合、コンテンツ登録せず、Entryのみ作成する。
				entry = putFolderEntry(uri, reflexContext);
			} else {
				// アップロード処理
				entry = upload(uri, data, headers, isBySize, reflexContext);
			}

			if (entry == null) {
				return null;
			}
			FeedBase feed = TaggingEntryUtil.createFeed(serviceName);
			feed.addEntry(entry);
			return feed;
		}
	}

	/**
	 * Multipart contentを登録.
	 * @param reflexContext データアクセスコンテキスト
	 * @param namesAndKeys 名前とキーリスト
	 * @param isBySize 画像ファイルのサイズ展開を行う場合true
	 * @return URIリスト
	 */
	public FeedBase putMultipartContent(ReflexContext reflexContext,
			Map<String, String> namesAndKeys, boolean isBySize)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		ReflexRequest req = reflexContext.getRequest();
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();

		// 入力チェック
		CheckUtil.checkRequest(req);
		String parentUri = req.getRequestType().getUri();
		checkPutMultipartContent(parentUri, namesAndKeys);
		boolean isChoice = namesAndKeys != null && !namesAndKeys.isEmpty();

		AclBlogic aclBlogic = new AclBlogic();
		if (!auth.isExternal()) {
			// $contentグループメンバーでなければエラー (リクエストから直接コンテンツ登録の場合)
			aclBlogic.checkAuthedGroup(auth, Constants.URI_GROUP_CONTENT);
		}
		
		if (logger.isTraceEnabled()) {
			Enumeration<String> headerNames = req.getHeaderNames();
			java.util.Iterator<String> it = headerNames.asIterator();
			while (it.hasNext()) {
				String headerName = it.next();
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[putMultipartContent] [headers] ");
				sb.append(headerName);
				sb.append(": ");
				sb.append(req.getHeader(headerName));
				logger.debug(sb.toString());
			}
		}

		List<EntryBase> entries = new ArrayList<EntryBase>();
		try {
			for (Part part : req.getParts()) {
				if (logger.isDebugEnabled()) {
					StringBuilder sb = new StringBuilder();
					sb.append(LogUtil.getRequestInfoStr(requestInfo));
					sb.append("[putMultipartContent] ");
					sb.append(part.getContentType());
					sb.append(": ");
					sb.append(part.getName());
					sb.append("=");
					sb.append(part.getSize());
					sb.append(" bytes");
					logger.debug(sb.toString());
				}

				String partContentType = part.getContentType();
				String name = part.getName();
				if (isPutContent(partContentType)) {
					String uri = null;
					if (isChoice) {
						if (name != null && namesAndKeys.containsKey(name)) {
							uri = namesAndKeys.get(name);
						}
					} else {
						String selfid = MultipartUtil.getFilename(part);
						CheckUtil.checkSelfid(selfid, "Part name");
						uri = getContentKey(parentUri, selfid);
					}

					if (uri != null) {
						// キー入力チェック
						CheckUtil.checkUri(uri);
						CheckUtil.checkCommonUri(uri, serviceName);

						// ACLチェック
						aclBlogic.checkAcl(uri, AtomConst.ACL_TYPE_UPDATE, auth,
								requestInfo, connectionInfo);

						Map<String, String> headers = getHeaders(part);
						// ストリーム入力チェック
						InputStream in = null;
						byte[] data = null;
						try {
							in = part.getInputStream();
							CheckUtil.checkRequestPayload(in);
							data = FileUtil.readInputStream(in);
						} finally {
							if (in != null) {
								in.close();
							}
						}
						EntryBase entry = null;
						if (isEmptyPayload(data)) {
							// リクエストデータが0バイトの場合、コンテンツ登録せず、Entryのみ作成する。
							entry = putFolderEntry(uri, reflexContext);
						} else {
							// アップロード
							entry = upload(uri, data, headers, isBySize, reflexContext);
						}
						entries.add(entry);
					}
				}
			}
		} catch (ServletException e) {
			throw new IOException(e);
		} catch (IllegalStateException e) {
			// "No multipart config for servlet"エラーの場合この例外をスローする
			if (logger.isTraceEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[putMultipartContent] Error occured. ");
				sb.append(e.getClass().getName());
				sb.append(" ");
				sb.append(e.getMessage());
				logger.debug(sb.toString(), e);
			}
			throw new IllegalParameterException(e.getMessage(), e);
		}

		if (entries.isEmpty()) {
			return null;
		}
		FeedBase feed = TaggingEntryUtil.createFeed(serviceName);
		feed.entry = entries;
		return feed;
	}

	/**
	 * リクエストデータをコンテント登録します.
	 * @param reflexContext ReflexContext
	 * @param parentUri 親キー
	 * @param ext 拡張子(任意)
	 * @param isSignedUrl 署名付きURL取得の場合true
	 * @return コンテントエントリー 署名付きURL取得の場合は署名付きURL
	 */
	public FeedBase postContent(ReflexContext reflexContext, String parentUri, String ext,
			boolean isSignedUrl)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		ReflexRequest req = reflexContext.getRequest();
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();

		// リクエストnullチェック
		CheckUtil.checkRequest(req);

		// 入力チェック
		CheckUtil.checkUri(parentUri);
		CheckUtil.checkCommonUri(parentUri, serviceName);

		AclBlogic aclBlogic = new AclBlogic();
		if (!auth.isExternal()) {
			// $contentグループメンバーでなければエラー (リクエストから直接コンテンツ登録の場合)
			aclBlogic.checkAuthedGroup(auth, Constants.URI_GROUP_CONTENT);
		}
		// ACLチェック
		String aclUri = aclBlogic.getDummySelfidUri(parentUri);
		aclBlogic.checkAcl(aclUri, AtomConst.ACL_TYPE_CREATE, auth,
				requestInfo, connectionInfo);

		Map<String, String> headers = getHeaders(req);
		if (isSignedUrl) {
			// 自動採番を行い、キーを生成
			String uri = allocidsUri(reflexContext, parentUri, ext);
			// 署名付きURL取得
			return getSignedUrl(reflexContext, Constants.PUT, uri, headers);
			
		} else {
			// アップロード
			InputStream in = req.getInputStream();
			// ストリーム入力チェック
			CheckUtil.checkRequestPayload(in);
			
			// 空ファイルは不可
			byte[] data = req.getPayload();
			if (isEmptyPayload(data)) {
				throw new IllegalParameterException("The file is empty.");
			}
			
			// エントリーを自動採番で作成
			EntryBase entry = TaggingEntryUtil.createEntry(serviceName);
			if (!StringUtils.isBlank(ext)) {
				entry = reflexContext.postWithExtension(entry, parentUri, ext);
			} else {
				entry = reflexContext.post(entry, parentUri);
			}
			String uri = entry.getMyUri();
			if (logger.isDebugEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[postContent] created uri = ");
				sb.append(uri);
				logger.debug(sb.toString());
			}

			// アップロード処理
			entry = upload(uri, data, headers, false, reflexContext);

			if (entry == null) {
				return null;
			}
			FeedBase feed = TaggingEntryUtil.createFeed(serviceName);
			feed.addEntry(entry);
			return feed;
		}
	}

	/**
	 * コンテンツ取得
	 * @param uri URI
	 * @param checkEtag Etagチェックを行う場合true
	 *                  リクエストのEtagが等しい場合、コンテンツ本体を返さずEtagのみ返します。
	 * @param reflexContext データアクセスコンテキスト
	 * @return コンテンツ情報 (本体、ヘッダ情報)
	 */
	public ReflexContentInfo getContent(String uri, boolean checkEtag,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 入力チェック
		CheckUtil.checkUri(uri);

		// 先頭が/@/で始まるURIの場合、システム管理サービスのデータを読む。
		ServiceBlogic serviceBlogic = new ServiceBlogic();
		ReflexContext tmpReflexContext = serviceBlogic.getReflexContextForGet(uri,
				reflexContext);
		ReflexAuthentication tmpAuth = tmpReflexContext.getAuth();

		RequestInfo requestInfo = tmpReflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = tmpReflexContext.getConnectionInfo();

		// ACLチェック
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAcl(uri, AtomConst.ACL_TYPE_RETRIEVE, tmpAuth,
				requestInfo, connectionInfo);

		// まずEntryを検索
		EntryBase entry = tmpReflexContext.getEntry(uri, true);

		// Entryが登録されていない場合はnullを返す。
		if (entry == null) {
			return null;
		}
		// ETag判定
		if (checkEtag) {
			String contentEtag = getEtagByEntry(entry);
			if (isNotModified(tmpReflexContext.getRequest(), contentEtag)) {
				return getNotModifiedInfo(uri, contentEtag);
			}
		}

		// content srcタグによるコンテント取得判定
		boolean isContentEntry = ContentBlogic.isContentEntry(entry);

		if (isContentEntry) {
			// コンテンツ取得
			return download(entry, tmpReflexContext);
		} else {
			// Entryのcontentを返却
			return getContentByEntry(entry, tmpReflexContext);
		}
	}

	/**
	 * コンテンツ取得のための署名付きURL取得
	 * @param uri URI
	 * @param checkEtag Etagチェックを行う場合true
	 *                  リクエストのEtagが等しい場合、コンテンツ本体を返さずEtagのみ返します。
	 * @param reflexContext データアクセスコンテキスト
	 * @return 署名付きURL
	 */
	public FeedBase getContentSignedUrl(String uri, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 入力チェック
		CheckUtil.checkUri(uri);

		// 先頭が/@/で始まるURIの場合、システム管理サービスのデータを読む。
		ServiceBlogic serviceBlogic = new ServiceBlogic();
		ReflexContext tmpReflexContext = serviceBlogic.getReflexContextForGet(uri,
				reflexContext);
		ReflexAuthentication tmpAuth = tmpReflexContext.getAuth();

		RequestInfo requestInfo = tmpReflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = tmpReflexContext.getConnectionInfo();

		// ACLチェック
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAcl(uri, AtomConst.ACL_TYPE_RETRIEVE, tmpAuth,
				requestInfo, connectionInfo);

		// 署名付きURL取得
		return getSignedUrl(reflexContext, Constants.GET, uri, null);
	}

	/**
	 * コンテンツ削除
	 * @param uri URI
	 * @param reflexContext データアクセスコンテキスト
	 * @return コンテンツEntry
	 */
	public EntryBase deleteContent(String uri, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		// 入力チェック
		CheckUtil.checkUri(uri);
		CheckUtil.checkCommonUri(uri, serviceName);

		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();

		AclBlogic aclBlogic = new AclBlogic();
		if (!auth.isExternal()) {
			// $contentグループメンバーでなければエラー (リクエストから直接コンテンツ登録の場合)
			aclBlogic.checkAuthedGroup(auth, Constants.URI_GROUP_CONTENT);
		}
		// ACLチェック
		aclBlogic.checkAcl(uri, AtomConst.ACL_TYPE_DELETE, auth,
				requestInfo, connectionInfo);

		return delete(uri, reflexContext);
	}

	/**
	 * コンテンツが更新されていないかどうかチェック.
	 * リクエストのEtagとコンテンツのEtagを比較します。
	 * @param req リクエスト
	 * @para contentInfo コンテンツ情報
	 * @return コンテンツが更新されていない場合true
	 */
	public boolean isNotModified(ReflexRequest req, String contentEtag) {
		String reqEtag = getEtagByRequest(req);
		return contentEtag != null && contentEtag.equals(reqEtag);
	}

	/**
	 * リクエストからEtagを取得.
	 * @param req リクエスト
	 * @return Etag
	 */
	public String getEtagByRequest(ReflexRequest req) {
		if (req != null) {
			return HeaderUtil.removeDoubleQuotation(
					req.getHeader(ReflexServletConst.HEADER_IF_NONE_MATCH));
		}
		return null;
	}

	/**
	 * コンテンツアップロード
	 * @param uri URI
	 * @param data アップロードコンテンツ
	 * @param headers アップロードコンテンツのヘッダ情報
	 * @param isBySize 画像ファイルのサイズ展開を行う場合true
	 * @param reflexContext ReflexContext
	 */
	private EntryBase upload(String uri, byte[] data, Map<String, String> headers,
			boolean isBySize, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// Content-Typeの編集
		headers = editContentType(headers, uri, reflexContext);

		// コンテンツアップロード
		ContentManager contentManager = TaggingEnvUtil.getContentManager();
		if (contentManager == null) {
			throw new InvalidServiceSettingException("Content manager is nothing.");
		}
		return contentManager.upload(uri, data, headers, isBySize, reflexContext);
	}

	/**
	 * コンテンツダウンロード
	 * @param entry Entry
	 * @param reflexContext ReflexContext
	 */
	private ReflexContentInfo download(EntryBase entry, ReflexContext reflexContext)
	throws IOException, TaggingException {
		ContentManager contentManager = TaggingEnvUtil.getContentManager();
		if (contentManager == null) {
			throw new InvalidServiceSettingException("Content manager is nothing.");
		}
		if (entry == null || StringUtils.isBlank(entry.id)) {
			return null;
		}
		// EntryのIDとキーが異なる場合、キーにIDキーをセットする。
		EntryBase copyEntry = TaggingEntryUtil.copyEntry(entry, reflexContext.getResourceMapper());
		String idUri = TaggingEntryUtil.getUriById(copyEntry.id);
		String myUri = copyEntry.getMyUri();
		if (!idUri.equals(myUri)) {
			copyEntry.setMyUri(idUri);
		}
		return contentManager.download(copyEntry, reflexContext);
	}

	/**
	 * コンテンツ削除.
	 * @param uri URI
	 * @param reflexContext ReflexContext
	 * @return Entry
	 */
	private EntryBase delete(String uri, ReflexContext reflexContext)
	throws IOException, TaggingException {
		ContentManager contentManager = TaggingEnvUtil.getContentManager();
		if (contentManager == null) {
			throw new InvalidServiceSettingException("Content manager is nothing.");
		}
		return contentManager.delete(uri, reflexContext);
	}

	/**
	 * Partのヘッダ情報をMap形式で返却します.
	 * @param part Part
	 * @return ヘッダ情報
	 */
	private Map<String, String> getHeaders(Part part) {
		Map<String, String> headers = null;
		Collection<String> headerNames = part.getHeaderNames();
		if (headerNames != null && !headerNames.isEmpty()) {
			headers = new HashMap<String, String>();
			for (String headerName : headerNames) {
				String val = part.getHeader(headerName);
				headers.put(headerName, val);
			}
		}
		return headers;
	}

	/**
	 * Partのヘッダ情報をMap形式で返却します.
	 * @param part Part
	 * @return ヘッダ情報
	 */
	private Map<String, String> getHeaders(ReflexRequest req) {
		Map<String, String> headers = null;
		Enumeration<String> enu = req.getHeaderNames();
		if (enu != null && enu.hasMoreElements()) {
			headers = new HashMap<String, String>();
			while (enu.hasMoreElements()) {
				String headerName = enu.nextElement();
				String val = req.getHeader(headerName);
				headers.put(headerName, val);
			}
		}
		return headers;
	}

	/**
	 * PathInfoからコンテンツのURIを取得
	 * @param pathInfo PathInfo
	 * @param name 名前
	 * @return コンテンツURI
	 */
	private String getContentKey(String pathInfo, String name) {
		StringBuilder sb = new StringBuilder();
		sb.append(pathInfo);
		sb.append("/");
		sb.append(name);
		return sb.toString();
	}

	/**
	 * 入力チェック
	 * @param uri URI
	 * @param nameAndKeys Part名とURIのMap
	 */
	private void checkPutMultipartContent(String uri, Map<String, String> namesAndKeys) {
		if (namesAndKeys == null || namesAndKeys.isEmpty()) {
			CheckUtil.checkUri(uri);
		} else {
			for (Map.Entry<String, String> mapEntry : namesAndKeys.entrySet()) {
				String partUri = mapEntry.getValue();
				CheckUtil.checkUri(partUri, "Part key");
			}
		}
	}

	/**
	 * エントリーのcontentを返却.
	 * @param entry Entry
	 * @param reflexContext ReflexContext
	 * @return コンテント情報
	 */
	private ReflexContentInfo getContentByEntry(EntryBase entry, ReflexContext reflexContext)
	throws IOException {
		if (entry != null && entry.content != null &&
				entry.content._$$text != null) {
			String uri = entry.getMyUri();
			byte[] data = entry.content._$$text.getBytes(Constants.ENCODING);
			Map<String, String> headers = new HashMap<String, String>();
			if (!StringUtils.isBlank(entry.content._$type)) {
				headers.put(ReflexServletConst.HEADER_CONTENT_TYPE, entry.content._$type);
			}
			// lastModified
			Date updated = null;
			try {
				updated = DateUtil.getDate(entry.getUpdated());
			} catch (ParseException e) {
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(reflexContext.getRequestInfo()) +
							"[getContentByEntry] updated date parse error.", e);
				}
			}
			String updatedStr = getLastModified(updated);
			headers.put(ReflexServletConst.HEADER_LAST_MODIFIED, updatedStr);
			return new EntryContentInfo(uri, data, headers, entry.id);
		}
		return null;
	}

	/**
	 * Etagのみ返すコンテンツ情報オブジェクトを取得.
	 * @param uri URI
	 * @param contentEtag Etag
	 * @return Etagのみ返すコンテンツ情報オブジェクト
	 */
	private ReflexContentInfo getNotModifiedInfo(String uri, String contentEtag) {
		return new NotModifiedContentInfo(uri, contentEtag);
	}

	/**
	 * リクエストデータが空かどうかチェック.
	 * @param req リクエストデータ
	 * @return リクエストデータが空の場合true
	 */
	private boolean isEmptyPayload(byte[] payload) {
		return payload == null || payload.length == 0;
	}

	/**
	 * フォルダEntryを作成.
	 * 空のコンテンツをアップロードされた場合に行う処理.
	 * @param uri URI
	 * @param reflexContext ReflexContext
	 * @return フォルダEntry
	 */
	private EntryBase putFolderEntry(String uri, ReflexContext reflexContext)
	throws IOException, TaggingException {
		if (logger.isDebugEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(reflexContext.getRequestInfo()));
			sb.append("[putFolderEntry] start. uri = ");
			sb.append(uri);
			logger.debug(sb.toString());
		}
		String serviceName = reflexContext.getServiceName();
		EntryBase entry = TaggingEntryUtil.createEntry(serviceName);
		entry.setMyUri(uri);
		return reflexContext.put(entry);
	}

	/**
	 * Content-Typeの編集.
	 * externalの場合、Content-Typeは一律 application/octet-stream にする。
	 * @param headers リクエストヘッダ
	 * @param uri
	 * @param reflexContext
	 * @return 編集したContent-Typeを格納したリクエストヘッダ
	 */
	private Map<String, String> editContentType(Map<String, String> headers, String uri, 
			ReflexContext reflexContext) {
		// Content-Typeの指定がなければ自動設定
		String contentType = null;
		if (reflexContext.getAuth().isExternal()) {
			// externalの場合、Content-Typeは一律 application/octet-stream にする。
			contentType = ReflexServletConst.CONTENT_TYPE_APPLICATION_OCTET_STREAM;
		} else {
			if (headers != null) {
				contentType = headers.get(ReflexServletConst.HEADER_CONTENT_TYPE);
			}
			if (StringUtils.isBlank(contentType)) {
				if (headers != null) {
					contentType = headers.get(ReflexServletConst.HEADER_CONTENT_TYPE_LOWERCASE);
				}
				if (StringUtils.isBlank(contentType)) {
					contentType = HeaderUtil.getContentTypeByFilename(uri);
				}
			}
		}
		if (!StringUtils.isBlank(contentType)) {
			if (headers == null) {
				headers = new HashMap<String, String>();
			}
			headers.put(ReflexServletConst.HEADER_CONTENT_TYPE, contentType);
		}
		return headers;
	}
	
	/**
	 * 署名付きURLを取得.
	 * @param reflexContext ReflexContext
	 * @param method コンテンツ取得の場合GET、コンテンツ登録の場合PUT、自動採番登録の場合POST
	 * @param uri キー
	 * @param headers リクエストヘッダ
	 * @return 署名付きURL
	 */
	private FeedBase getSignedUrl(ReflexContext reflexContext, String method, 
			String uri, Map<String, String> headers)
	throws IOException, TaggingException {
		// Content-Typeの編集
		headers = editContentType(headers, uri, reflexContext);
		// 署名付きURLの発行
		ContentManager contentManager = TaggingEnvUtil.getContentManager();
		if (contentManager == null) {
			throw new InvalidServiceSettingException("Content manager is nothing.");
		}
		return contentManager.getSignedUrl(method, uri, headers, reflexContext);
	}
	
	/**
	 * 自動採番でキーを採番する.
	 * @param reflexContext ReflexContext
	 * @param parentUri 親キー
	 * @param ext 拡張子
	 * @return 生成したキー
	 */
	private String allocidsUri(ReflexContext reflexContext, String parentUri, String ext)
	throws IOException, TaggingException {
		FeedBase feed = reflexContext.allocids(parentUri, 1);
		String allocid = feed.title;
		StringBuilder sb = new StringBuilder();
		sb.append(parentUri);
		sb.append("/");
		sb.append(allocid);
		if (!StringUtils.isBlank(ext)) {
			sb.append(".");
			sb.append(ext);
		}
		return sb.toString();
	}

}
