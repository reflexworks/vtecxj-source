package jp.reflexworks.js;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;

import jakarta.mail.MessagingException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.Part;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.entry.Link;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.atom.util.MailReceiver;
import jp.reflexworks.js.urlfetch.URLFetchUtil;
import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.taggingservice.api.ReflexContentInfo;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.ReflexSecurityManager;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.IResourceMapper;
import jp.sourceforge.reflex.exception.JSONException;
import jp.sourceforge.reflex.util.StringUtils;

public class JsContext {

	private ReflexContext reflexContext;
	private ReflexRequest req;
	private ReflexResponse resp;
	private IResourceMapper templateMapper;

	private int sc;
	public Object result;
	private String method;
	public FeedBase respFeed;	// Response feed

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	public JsContext(ReflexContext reflexContext, ReflexRequest req,
			ReflexResponse resp, String method) throws ParseException {
		this.reflexContext = reflexContext;
		this.req = req;
		this.resp = resp;
		this.method = method;
		this.templateMapper = reflexContext.getResourceMapper();
		this.sc = -1;
	}

	public void log(String message) {
		reflexContext.log("JavaScript", "INFO", message);
	}

	public void log(String title, String message) {
		reflexContext.log(title, "INFO", message);
	}

	public void log(String title, String subtitle, String message) {
		reflexContext.log(title, subtitle, message);
	}

	public void setStatus(int sc) throws InvalidServiceSettingException {
		if (resp == null) {
			throw new InvalidServiceSettingException("Cannot be set in response.");
		}
		resp.setStatus(sc);
		this.sc = sc;
	}

	public int getStatus() {
		return sc;
	}

	public void sendError(int sc, String msg)
	throws NumberFormatException, IOException, InvalidServiceSettingException {
		if (resp == null) {
			throw new InvalidServiceSettingException("Cannot be set in response.");
		}
		resp.sendError(sc, msg);
	}

	public void sendMessage(int sc, String msg)
	throws NumberFormatException, IOException, InvalidServiceSettingException {
		this.sc = sc;
		respFeed = TaggingEntryUtil.createFeed(reflexContext.getServiceName());
		respFeed.title = msg;
	}

	public void sendError(int sc)
	throws NumberFormatException, IOException, InvalidServiceSettingException {
		if (resp == null) {
			throw new InvalidServiceSettingException("Cannot be set in response.");
		}
		resp.sendError(sc);
	}

	public void sendRedirect(String location) throws IOException, InvalidServiceSettingException {
		if (resp == null) {
			throw new InvalidServiceSettingException("Cannot be set in response.");
		}
		resp.sendRedirect(location);
	}

	public void setHeader(String name, String value) throws InvalidServiceSettingException {
		if (resp == null) {
			throw new InvalidServiceSettingException("Cannot be set in response.");
		}
		resp.setHeader(name, value);
	}

	public String parameter(String param) {
		return req.getParameter(param);
	}

	public String uriquerystring() {
		StringBuffer buf = new StringBuffer();
		buf.append(req.getPathInfo());
		String queryString = req.getQueryString();
		if (queryString != null) {
			buf.append("?");
			buf.append(queryString);
		}
		return buf.toString();
	}

	public String querystring() {
		return req.getQueryString();
	}

	public String pathinfo() {
		return req.getPathInfo();
	}

	public String contenttype() {
		return req.getContentType();
	}

	public byte[] getPayload() throws IOException {
		return req.getPayload();
	}

	public String getPayloadStr() throws IOException {
		byte[] data = req.getPayload();
		if (data == null || data.length == 0) {
			return "";
		}
		return new String(data, Constants.ENCODING);
	}

	public String getPayloadJson() throws IOException {
		// JsUtilで戻り値をJSON.parseする
		return getPayloadStr();
	}

	public String getFeed(String url) throws IOException, TaggingException, ParseException {
		FeedBase feed = reflexContext.getFeed(url);
		return templateMapper.toJSON(feed);
	}

	public String getFeed(String url, String targetServiceName, String targetServiceKey)
			throws IOException, TaggingException, ParseException {
		FeedBase feed = reflexContext.getFeed(url, targetServiceName, targetServiceKey);
		return templateMapper.toJSON(feed);
	}

	public String getEntry(String url) throws IOException, TaggingException, ParseException {
		EntryBase entry = reflexContext.getEntry(url);
		return get(entry);
	}

	public String getEntry(String url, String targetServiceName, String targetServiceKey)
			throws IOException, TaggingException, ParseException {
		EntryBase entry = reflexContext.getEntry(url, targetServiceName, targetServiceKey);
		return get(entry);
	}

	private String get(EntryBase entry) {
		FeedBase feed = TaggingEntryUtil.createFeed(reflexContext.getServiceName());
		feed.entry = new ArrayList<EntryBase>();
		if (entry != null) {
			feed.entry.add(entry);
			return templateMapper.toJSON(feed);
		} else {
			return null;
		}
	}

	public String post(String feedstr, String uri) throws IOException, ParseException, TaggingException {
		FeedBase feed = (FeedBase) templateMapper.fromJSON(JsUtil.addfeedstr(feedstr));
		FeedBase result = reflexContext.post(feed, uri);
		return templateMapper.toJSON(result);
	}

	public String post(String feedstr, String uri, String targetServiceName, String targetServiceKey)
			throws IOException, ParseException, TaggingException {
		FeedBase feed = (FeedBase) templateMapper.fromJSON(JsUtil.addfeedstr(feedstr));
		FeedBase result = reflexContext.post(feed, uri, targetServiceName, targetServiceKey);
		return templateMapper.toJSON(result);
	}

	public String post(String feedstr) throws IOException, ParseException, TaggingException {
		FeedBase feed = (FeedBase) templateMapper.fromJSON(JsUtil.addfeedstr(feedstr));
		FeedBase result = reflexContext.post(feed);
		return templateMapper.toJSON(result);
	}

	public String post(String feedstr, String targetServiceName, String targetServiceKey)
			throws IOException, ParseException, TaggingException {
		FeedBase feed = (FeedBase) templateMapper.fromJSON(JsUtil.addfeedstr(feedstr));
		FeedBase result = reflexContext.post(feed, targetServiceName, targetServiceKey);
		return templateMapper.toJSON(result);
	}

	public String put(String feedstr) throws IOException, ParseException, TaggingException {
		FeedBase feed = (FeedBase) templateMapper.fromJSON(JsUtil.addfeedstr(feedstr));
		FeedBase result = reflexContext.put(feed);
		return templateMapper.toJSON(result);
	}

	public String put(String feedstr, String targetServiceName, String targetServiceKey)
			throws IOException, ParseException, TaggingException {
		FeedBase feed = (FeedBase) templateMapper.fromJSON(JsUtil.addfeedstr(feedstr));
		FeedBase result = reflexContext.put(feed, targetServiceName, targetServiceKey);
		return templateMapper.toJSON(result);
	}

	public void bulkput(String feedstr) throws IOException, ParseException, TaggingException {
		FeedBase feed = (FeedBase) templateMapper.fromJSON(JsUtil.addfeedstr(feedstr));
		reflexContext.bulkPut(feed, true); // parallel & async
	}

	public void bulkput(String feedstr, boolean parallel, boolean async)
			throws IOException, ParseException, TaggingException {
		FeedBase feed = (FeedBase) templateMapper.fromJSON(JsUtil.addfeedstr(feedstr));
		if (parallel) {
			reflexContext.bulkPut(feed, async); // async
		} else {
			reflexContext.bulkSerialPut(feed, async); // async
		}
	}

	// リビジョン指定なし
	public void delete(String uri) throws IOException, ParseException, TaggingException {
		reflexContext.delete(uri);
	}

	// リビジョン指定
	public void delete(String uri, int revision) throws IOException, ParseException, TaggingException {
		reflexContext.delete(uri, revision);
	}

	// リビジョン指定なし
	public void delete(String uri, String targetServiceName, String targetServiceKey)
			throws IOException, ParseException, TaggingException {
		reflexContext.delete(uri, targetServiceName, targetServiceKey);
	}

	// リビジョン指定
	public void delete(String uri, int revision, String targetServiceName, String targetServiceKey)
			throws IOException, ParseException, TaggingException {
		reflexContext.delete(uri, revision, targetServiceName, targetServiceKey);
	}

	public void deleteFolder(String uri) throws IOException, ParseException, TaggingException {
		reflexContext.deleteFolder(uri, false, true);
	}

	public void clearFolder(String uri) throws IOException, ParseException, TaggingException {
		reflexContext.clearFolder(uri, false, true);
	}

	public void setids(String uri, long value) throws IOException, TaggingException {
		reflexContext.setids(uri, value);
	}

	public void setids(String uri, long value, String targetServiceName, String targetServiceKey)
			throws IOException, TaggingException {
		reflexContext.setids(uri, value, targetServiceName, targetServiceKey);
	}

	public String addids(String uri, long num) throws IOException, TaggingException {
		FeedBase feed = reflexContext.addids(uri, num);
		return feed.getTitle();
	}

	public String addids(String uri, long num, String targetServiceName, String targetServiceKey)
			throws IOException, TaggingException {
		FeedBase feed = reflexContext.addids(uri, num, targetServiceName, targetServiceKey);
		return feed.getTitle();
	}

	public String allocids(String uri, int num) throws IOException, TaggingException {
		FeedBase feed = reflexContext.allocids(uri, num);
		return feed.getTitle();
	}

	public String allocids(String uri, int num, String targetServiceName, String targetServiceKey)
			throws IOException, TaggingException {
		FeedBase feed = reflexContext.allocids(uri, num, targetServiceName, targetServiceKey);
		return feed.getTitle();
	}

	public void rangeids(String uri, String value) throws IOException, TaggingException {
		reflexContext.rangeids(uri, value);
	}

	public long count(String uri) throws IOException, TaggingException {
		FeedBase feed = reflexContext.getCount(uri);
		return Long.parseLong(feed.title);
	}

	public long count(String uri, String targetServiceName, String targetServiceKey)
			throws IOException, TaggingException {
		FeedBase feed = reflexContext.getCount(uri, targetServiceName, targetServiceKey);
		return Long.parseLong(feed.title);
	}

	/**
	 * PDF生成.
	 * PDFを生成し、レスポンスに書き込みます。
	 * @param htmlTemplate PDFテンプレート
	 * @param filename ダウンロードファイル名
	 */
	public void toPdf(String htmlTemplate, String filename)
	throws IOException, TaggingException {
		if (resp == null) {
			throw new InvalidServiceSettingException("Cannot be set in response.");
		}
		
		byte[] pdfData = reflexContext.toPdf(htmlTemplate);

		this.sc = -2; // doResponse()を回避
		// Content-Type
		resp.setContentType(ReflexServletConst.CONTENT_TYPE_PDF);
		if (filename != null && !filename.equals("")) {
			resp.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
		}
		// レスポンスにPDFデータを出力
		try (OutputStream out = new BufferedOutputStream(resp.getOutputStream())) {
			out.write(pdfData);
		}
	}
	
	/*
	public void toXls(String feedjson, String template, String filename)
	throws IOException, InvalidServiceSettingException {
		if (resp == null) {
			throw new InvalidServiceSettingException("Cannot be set in response.");
		}
		if (template != null) {
			try {
				resp.setContentType("application/vnd.ms-excel");
				if (filename != null && !filename.equals("")) {
					resp.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
				}

				this.sc = -2; // doResponse()を回避
				JSONObject feed = new JSONObject(JsUtil.addfeedstr(feedjson));
				byte[] templatebin = reflexContext.getHtmlContent(template);
				if (templatebin == null) {
					throw new FileNotFoundException(template + " is not found.");
				}
				new PoiInvoker().toXls(new ByteArrayInputStream(templatebin), feed, resp.getOutputStream());

			} catch (Exception e) {
				reflexContext.log("JavaScript", "toXls", e.getMessage());
				resp.sendError(400, e.getMessage());
				// web.xmlの設定により、error400.htmlが表示される
			}
		}
	}
	*/

	/**
	 * リクエストデータをJSON形式のFeedで取得.
	 * @return Feed(JSON)
	 */
	public String getRequest() throws ClassNotFoundException, IOException, DataFormatException {
		FeedBase feed = req.getFeed();
		if (feed != null) {
			return templateMapper.toJSON(feed);
		} else {
			return "";
		}
	}

	/**
	 * リクエストのCookieをJSON形式で取得.
	 * @return JSON形式のリクエストCookie
	 */
	public String getCookies() {
		Cookie[] cookies = req.getCookies();

		return JsUtil.convertHttpCookies(cookies);
	}

	/**
	 * リクエストヘッダをJSON形式で取得.
	 * @return JSON形式のリクエストヘッダ
	 */
	public String getHeaders() {
		return JsUtil.convertHttpHeaders(req);
	}

	public String getRemoteIP() throws ClassNotFoundException, IOException, DataFormatException {
		// IPアドレス
		ReflexSecurityManager securityManager = TaggingEnvUtil.getSecurityManager();
		return securityManager.getIPAddr(req);
	}

	public Object fromJSON(String json) throws IOException, TaggingException {
		return templateMapper.fromJSON(json);
	}

	public void saveFiles(Map<String, String> props) throws IOException, TaggingException {
		reflexContext.putContent(props);
	}

	public void saveFilesBySize(Map<String, String> props) throws IOException, TaggingException {
		reflexContext.putContentBySize(props);
	}

	public String uid() {
		return reflexContext.getUid();
	}

	public String httpmethod() {
		return method;
	}

	public String settingValue(String key) {
		return reflexContext.getSettingValue(key);
	}

	public void doResponse(String feedstr, int sc) throws IOException, TaggingException {
		this.sc = sc;
		try {
			respFeed = (FeedBase) templateMapper.fromJSON(JsUtil.addfeedstr(feedstr));
		} catch (JSONException e) {
			// JSONExceptionの場合入力エラー
			throw new InvalidServiceSettingException(e);
		}
	}

	public void doResponse(String feedstr) throws IOException, TaggingException {
		doResponse(feedstr, 200);
	}

	public String content(String uri) throws IOException, TaggingException {
		if (!uri.startsWith("/")) {
			uri = "/" + uri;
		}
		byte[] content = reflexContext.getHtmlContent(uri);
		String ret = null;
		if (content != null) {
			ret = new String(content, Constants.ENCODING);
		} else {
			ret = "";
		}
		return ret;
	}

	public String contentbykey(String uri) throws IOException, TaggingException {
		byte[] content = null;
		ReflexContentInfo contentInfo = reflexContext.getContent(uri);
		if (contentInfo != null) {
			content = contentInfo.getData();
		}
		String ret = null;
		if (content != null) {
			ret = new String(content, Constants.ENCODING);
		} else {
			ret = "";
		}
		return ret;
	}

	public String contentjs(String uri) throws IOException, TaggingException {
		if (uri.charAt(0) == '@') {
			return JsUtil.replace(contentbykey("/@/server/" + uri.substring(1)));
		} else {
			if (!uri.startsWith("/")) {
				uri = "/" + uri;
			}
			return JsUtil.replace(content("/server" + uri));
		}
	}

	public String getMail(Map<String, String> settings)
			throws IOException, TaggingException, ParseException, MessagingException {
		MailReceiver receiver = new MailReceiver();
		FeedBase feed = receiver.doReceive((FeedTemplateMapper) templateMapper, settings);
		return templateMapper.toJSON(feed);
	}

	public String getCsv(String[] header, String[] items, String parent, int skip, String encoding)
			throws IOException, ServletException {

		JsCsv jsCsv = new JsCsv();
		Collection<Part> parts = null;
		try {
			parts = req.getParts();
		} catch (ServletException e) {
			throw new IllegalParameterException("get request parts failed: " + e.getMessage());
		}
		for (Part part : parts) {
			// 最初のファイルのみ有効
			return jsCsv.parsecsv(part.getInputStream(), header, items, parent, skip, encoding);
		}
		return null;
	}

	public void doResponseCsv(String[] value, String filename)
	throws IOException, InvalidServiceSettingException {
		if (resp == null) {
			throw new InvalidServiceSettingException("Cannot be set in response.");
		}
		resp.setContentType("text/csv");
		if (filename != null) {
			resp.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
		}

		for (String line : value) {
			resp.getOutputStream().println(new String(toSJIS(line).getBytes("Windows-31J"), "ISO-8859-1"));
		}
		resp.getOutputStream().close();
		this.sc = -2; // doResponse()を回避
	}

	private String toSJIS(String s) {
		StringBuffer sb = new StringBuffer();
		char c;

		for (int i = 0; i < s.length(); i++) {
			c = s.charAt(i);
			switch (c) {
			case 0x301c:
				c = 0xff5e;
				break;
			case 0x2016:
				c = 0x2225;
				break;
			case 0x2212:
				c = 0xff0d;
				break;
			case 0x00a2:
				c = 0xffe0;
				break;
			case 0x00a3:
				c = 0xffe1;
				break;
			case 0x00ac:
				c = 0xffe2;
				break;
			case 0x2014:
				c = 0x2015;
				break;
			default:
				break;
			}

			sb.append(c);
		}
		return new String(sb);
	}

	public void doResponseHtml(String html) throws IOException, InvalidServiceSettingException {
		if (resp == null) {
			throw new InvalidServiceSettingException("Cannot be set in response.");
		}
		resp.setContentType("text/html");
		resp.getOutputStream().println(html);
		resp.getOutputStream().close();
		this.sc = -2; // doResponse()を回避
	}

	public String RXID() throws IOException, TaggingException {
		return reflexContext.getRXID();
	}

	public void sendMail(String entrystr, String[] to) throws IOException, TaggingException {
		EntryBase entry = (EntryBase) templateMapper.fromJSON(entrystr);
		reflexContext.sendMail(entry, to, null, null, null);
	}

	public void sendMail(String entrystr, String[] to, String[] cc) throws IOException, TaggingException {
		EntryBase entry = (EntryBase) templateMapper.fromJSON(entrystr);
		reflexContext.sendMail(entry, to, cc, null, null);
	}

	public void sendMail(String entrystr, String[] to, String[] cc, String[] bcc) throws IOException, TaggingException {
		EntryBase entry = (EntryBase) templateMapper.fromJSON(entrystr);
		reflexContext.sendMail(entry, to, cc, bcc, null);
	}

	public void sendMail(String entrystr, String[] to, String[] cc, String[] bcc, String[] attachments)
			throws IOException, TaggingException {
		EntryBase entry = (EntryBase) templateMapper.fromJSON(entrystr);
		reflexContext.sendMail(entry, to, cc, bcc, attachments);
	}

	public String adduserByAdmin(String feedstr) throws IOException, TaggingException {
		FeedBase feed = (FeedBase) templateMapper.fromJSON(JsUtil.addfeedstr(feedstr));
		FeedBase result = reflexContext.adduserByAdmin(feed);
		return templateMapper.toJSON(result);
	}

	public String deleteUser(String str) throws IOException, TaggingException {
		return deleteUser(str, false);
	}

	public String deleteUser(String str, boolean async) throws IOException, TaggingException {
		boolean isFeed = false;
		if (!StringUtils.isBlank(StringUtils.trim(str))) {
			String firstChar = StringUtils.trim(str).substring(0, 1);
			if ("{".equals(firstChar) || "[".equals(firstChar)) {
				isFeed = true;
			}
		}

		if (isFeed) {
			FeedBase feed = (FeedBase) templateMapper.fromJSON(JsUtil.addfeedstr(str));
			FeedBase retFeed = reflexContext.deleteUser(feed, async);
			return templateMapper.toJSON(retFeed);
		} else {
			EntryBase retEntry = reflexContext.deleteUser(str, async);
			return get(retEntry);
		}
	}

	public void setSessionFeed(String feedstr, String name) throws IOException, TaggingException {
		FeedBase feed = (FeedBase) templateMapper.fromJSON(JsUtil.addfeedstr(feedstr));
		reflexContext.setSessionFeed(name, feed);
	}

	public void setSessionEntry(String entrystr, String name) throws IOException, TaggingException {
		EntryBase entry = (EntryBase) templateMapper.fromJSON(entrystr);
		reflexContext.setSessionEntry(name, entry);
	}

	public void setSessionString(String str, String name) throws IOException, TaggingException {
		reflexContext.setSessionString(name, str);
	}

	public void setSessionLong(long num, String name) throws IOException, TaggingException {
		reflexContext.setSessionLong(name, num);
	}

	public String getSessionString(String name) throws IOException, TaggingException {
		return reflexContext.getSessionString(name);
	}

	public String getSessionFeed(String name) throws IOException, TaggingException {
		FeedBase feed = reflexContext.getSessionFeed(name);
		return templateMapper.toJSON(feed);
	}

	public String getSessionEntry(String name) throws IOException, TaggingException {
		EntryBase entry = reflexContext.getSessionEntry(name);
		return templateMapper.toJSON(entry);
	}

	public long getSessionLong(String name) throws IOException, TaggingException {
		return reflexContext.getSessionLong(name);
	}

	public void deleteSessionString(String name) throws IOException, TaggingException {
		reflexContext.deleteSessionString(name);
	}

	public void deleteSessionFeed(String name) throws IOException, TaggingException {
		reflexContext.deleteSessionFeed(name);
	}

	public void deleteSessionEntry(String name) throws IOException, TaggingException {
		reflexContext.deleteSessionEntry(name);
	}

	public void deleteSessionLong(String name) throws IOException, TaggingException {
		reflexContext.deleteSessionLong(name);
	}

	public void incrementSession(String name, long num) throws IOException, TaggingException {
		reflexContext.incrementSession(name, num);
	}

	public void pagenation(String uri, String num) throws IOException, TaggingException {
		reflexContext.pagination(uri, num);
	}

	public void pagenation(String uri, String num, String targetServiceName, String targetServiceKey)
			throws IOException, TaggingException {
		reflexContext.pagination(uri, num, targetServiceName, targetServiceKey);
	}

	public String getPage(String uri, String num) throws IOException, TaggingException {
		FeedBase feed = reflexContext.getPage(uri, num);
		return templateMapper.toJSON(feed);
	}

	public String getPage(String uri, String num, String targetServiceName, String targetServiceKey)
			throws IOException, TaggingException {
		FeedBase feed = reflexContext.getPage(uri, num, targetServiceName, targetServiceKey);
		return templateMapper.toJSON(feed);
	}

	public void postBQ(String feedstr, boolean async) throws IOException, ParseException, TaggingException {
		CheckUtil.checkNotNull(feedstr, "feed");
		FeedBase feed = (FeedBase) templateMapper.fromJSON(JsUtil.addfeedstr(feedstr));
		reflexContext.postBq(feed, async);
	}

	public void postBQ(String feedstr, Map<String, String> tableNames, boolean async) throws IOException, ParseException, TaggingException {
		CheckUtil.checkNotNull(feedstr, "feed");
		FeedBase feed = (FeedBase) templateMapper.fromJSON(JsUtil.addfeedstr(feedstr));
		reflexContext.postBq(feed, tableNames, async);
	}

	public void deleteBQ(String[] keys, boolean async) throws IOException, ParseException, TaggingException {
		reflexContext.deleteBq(keys, async);
	}

	public void deleteBQ(String[] keys, Map<String, String> tableNames, boolean async) throws IOException, ParseException, TaggingException {
		reflexContext.deleteBq(keys, tableNames,async);
	}

	public String getBQ(String sql) throws IOException, ParseException, TaggingException {
		List<Map<String, Object>> result = reflexContext.queryBq(sql);
		return JsUtil.convertResultBQ(result,null);
	}

	public String getBQ(String sql, String parent) throws IOException, ParseException, TaggingException {
		List<Map<String, Object>> result = reflexContext.queryBq(sql);
		return JsUtil.convertResultBQ(result,parent);
	}

	public void doResponseBQcsv(String sql, String filename, String header) throws IOException, ParseException, TaggingException {
		List<Map<String, Object>> result = reflexContext.queryBq(sql);
		String[] rows = JsUtil.convertResultBQcsv(result,header);
		this.doResponseCsv(rows,filename);
	}

	public String postBDBQ(String feedstr, String uri, Map<String, String> tableNames, boolean async) 
	throws IOException, ParseException, TaggingException {
		CheckUtil.checkNotNull(feedstr, "feed");
		FeedBase feed = (FeedBase) templateMapper.fromJSON(JsUtil.addfeedstr(feedstr));
		FeedBase result = reflexContext.postBdbq(feed, uri, tableNames, async);
		return templateMapper.toJSON(result);
	}

	public String putBDBQ(String feedstr, String uri, Map<String, String> tableNames, boolean async) 
	throws IOException, ParseException, TaggingException {
		CheckUtil.checkNotNull(feedstr, "feed");
		FeedBase feed = (FeedBase) templateMapper.fromJSON(JsUtil.addfeedstr(feedstr));
		FeedBase result = reflexContext.putBdbq(feed, uri, tableNames, async);
		return templateMapper.toJSON(result);
	}

	public void deleteBDBQ(String[] keys, Map<String, String> tableNames, boolean async) 
	throws IOException, ParseException, TaggingException {
		reflexContext.deleteBdbq(keys, tableNames, async);
	}

	public void bulkputBDBQ(String feedstr, String uri, Map<String, String> tableNames, boolean async) 
	throws IOException, ParseException, TaggingException {
		CheckUtil.checkNotNull(feedstr, "feed");
		FeedBase feed = (FeedBase) templateMapper.fromJSON(JsUtil.addfeedstr(feedstr));
		reflexContext.bulkPutBdbq(feed, uri, tableNames, async);
	}

	/**
	 * URLFetch.
	 * @param url URL
	 * @param method メソッド
	 * @param reqData リクエストデータ
	 * @param headers リクエストヘッダ
	 * @return レスポンス情報.以下の形式のJSONで返却。
	 *         {
	 *             "status": レスポンスステータス,
	 *             "headers":  {"キー": "値", "キー2": "値2", ... },
	 *             "data": "レスポンスデータ"
	 *         }
	 */
	public String urlfetch(String url, String method, String reqData,
			Map<String, String> headers)
	throws IOException, TaggingException {
		return urlfetch(url, method, reqData, headers, 0);
	}

	/**
	 * URLFetch
	 * @param url URL
	 * @param method メソッド
	 * @param reqData リクエストデータ
	 * @param headers リクエストヘッダ
	 * @param timeoutMillis タイムアウト時間(ミリ秒)
	 * @return レスポンス情報.以下の形式のJSONで返却。
	 *         {
	 *             "status": レスポンスステータス,
	 *             "headers":  {"キー": "値", "キー2": "値2", ... },
	 *             "data": "レスポンスデータ"
	 *         }
	 */
	public String urlfetch(String url, String method, String reqData,
			Map<String, String> headers, int timeoutMillis)
	throws IOException, TaggingException {
		return URLFetchUtil.request(reflexContext, url, method, reqData, headers, timeoutMillis);
	}

	/**
	 * セッション付きURLFetch
	 * @param url URL
	 * @param method メソッド
	 * @param reqData リクエストデータ
	 * @param headers リクエストヘッダ
	 * @param timeoutMillis タイムアウト時間(ミリ秒)
	 * @return レスポンス情報.以下の形式のJSONで返却。
	 *         {
	 *             "status": レスポンスステータス,
	 *             "headers":  {"キー": "値", "キー2": "値2", ... },
	 *             "data": "レスポンスデータ"
	 *         }
	 */
	public String urlfetchWithSession(String url, String method, String reqData,
			Map<String, String> headers)
	throws IOException, TaggingException {
		return urlfetchWithSession(url, method, reqData, headers, 0);
	}

	/**
	 * セッション付きURLFetch
	 * @param url URL
	 * @param method メソッド
	 * @param reqData リクエストデータ
	 * @param headers リクエストヘッダ
	 * @param timeoutMillis タイムアウト時間(ミリ秒)
	 * @return レスポンス情報.以下の形式のJSONで返却。
	 *         {
	 *             "status": レスポンスステータス,
	 *             "headers":  {"キー": "値", "キー2": "値2", ... },
	 *             "data": "レスポンスデータ"
	 *         }
	 */
	public String urlfetchWithSession(String url, String method, String reqData,
			Map<String, String> headers, int timeoutMillis)
	throws IOException, TaggingException {
		if (reflexContext != null && reflexContext.getAuth() != null) {
			String sid = reflexContext.getAuth().getSessionId();
			if (sid != null) {
				if (headers == null) {
					headers = new HashMap<>();
				}
				StringBuilder sb = new StringBuilder();
				String cookieVal = headers.get(ReflexServletConst.HEADER_COOKIE);
				if (cookieVal != null) {
					sb.append(cookieVal);
					sb.append("; ");
				}
				sb.append(ReflexServletConst.COOKIE_SID);
				sb.append("=");
				sb.append(sid);
				headers.put(ReflexServletConst.HEADER_COOKIE, sb.toString());
				if (!headers.containsKey(ReflexServletConst.X_REQUESTED_WITH)) {
					headers.put(ReflexServletConst.X_REQUESTED_WITH, 
							ReflexServletConst.X_REQUESTED_WITH_WHR);
				}
			}
		}
		return URLFetchUtil.request(reflexContext, url, method, reqData, headers, timeoutMillis);
	}

	/**
	 * SDK呼び出し.
	 * @param name プロパティファイルに設定した、SDK実行クラス名に対応するname
	 * @param args SDK実行クラス実行時の引数
	 * @return 処理結果
	 */
	public String callSDK(String name, String[] args)
	throws IOException, TaggingException {
		FeedBase feed = reflexContext.callSDK(name, args);
		return feed.getTitle();
	}

	/**
	 * メッセージ通知
	 * @param feedstr 通知メッセージ。entryの内容は以下の通り。
	 *          title: Push通知タイトル
	 *          subtitle: PUsh通知サブタイトル
	 *          content: Push通知メッセージ本文(body)
	 *          summary: dataに設定するメッセージ(Expo用)
	 *          category _$scheme="imageurl" _$label={通知イメージURL}(FCM用)
	 *          category _$scheme={dataのキー} _$label={dataの値}(Expo用)
	 * @param to 送信先 (UID, account or group)
	 */
	public void pushNotification(String feedstr, String[] to)
	throws IOException, TaggingException {
		FeedBase feed = (FeedBase) templateMapper.fromJSON(JsUtil.addfeedstr(feedstr));
		reflexContext.pushNotification(feed, to);
	}

	/**
	 * WebSocketメッセージ送信.
	 * @param feedstr メッセージ情報格納Feed。entryの内容は以下の通り。
	 *          summary : メッセージ
	 *          link rel="to"のhref属性 : 送信先。以下のいずれか。複数指定可。
	 *              UID
	 *              アカウント
	 *              グループ(*)
	 *              ポーリング(#)
	 *          title : WebSocket送信ができなかった場合のPush通知のtitle
	 *          subtitle : WebSocket送信ができなかった場合のPush通知のsubtitle(Expo用)
	 *          content : WebSocket送信ができなかった場合のPush通知のbody
	 *          category : WebSocket送信ができなかった場合のPush通知のdata(Expo用、key-value形式)
	 *              dataのキーに_$schemeの値、dataの値に_$labelの値をセットする。
	 *          rights : trueが指定されている場合、WebSocket送信ができなかった場合にPush通知しない。
	 * @param channel チャネル
	 */
	public void sendWebSocket(String feedstr, String channel)
	throws IOException, TaggingException {
		FeedBase feed = (FeedBase) templateMapper.fromJSON(JsUtil.addfeedstr(feedstr));
		reflexContext.sendWebSocket(feed, channel);
	}

	/**
	 * WebSocket接続をクローズ.
	 * 認証ユーザのWebSocketについて、指定されたチャネルの接続をクローズする。
	 * @param channel チャネル
	 */
	public void closeWebSocket(String channel)
	throws IOException, TaggingException {
		reflexContext.closeWebSocket(channel);
	}
	
	/**
	 * ログアウト.
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @return ログアウトメッセージ
	 */
	public String logout()
	throws IOException, TaggingException {
		FeedBase feed = reflexContext.logout(req, resp);
		return templateMapper.toJSON(feed);
	}

	/**
	 * 署名検証.
	 * @param uri キー
	 * @return 署名が正しい場合true
	 */
	public boolean checkSignature(String uri) throws IOException, ParseException, TaggingException {
		boolean ret = reflexContext.checkSignature(uri);
		return ret;
	}

	/**
	 * 署名設定.
	 * すでに署名が設定されている場合は更新します。
	 * @param uri
	 * @param revision リビジョン
	 * @return 正常に署名できた場合true
	 */
	public boolean putSignature(String uri, Integer revision) 
	throws IOException, TaggingException {
		reflexContext.putSignature(uri, revision);
		return true;
	}

	/**
	 * 署名設定.
	 * link rel="self"に署名をします。
	 * Entryが存在しない場合は登録します。
	 * @param feed 署名対象Entryリスト
	 * @return 正常に署名できた場合true
	 */
	public boolean putSignatures(String feedstr) 
	throws IOException, ParseException, TaggingException {
		FeedBase feed = (FeedBase) templateMapper.fromJSON(JsUtil.addfeedstr(feedstr));
		reflexContext.putSignatures(feed);
		return true;
	}

	/**
	 * 署名削除.
	 * @param uri
	 * @param revision リビジョン
	 * @return 正常に署名削除した場合true
	 */
	public boolean deleteSignature(String uri, Integer revision) 
	throws IOException, TaggingException {
		reflexContext.deleteSignature(uri, revision);
		return true;
	}

	/**
	 * メッセージキュー使用ON/OFF設定
	 * @param flag メッセージキューを使用する場合true
	 * @param channel チャネル
	 */
	public void setMessageQueueStatus(boolean flag, String channel)
	throws IOException, TaggingException {
		reflexContext.setMessageQueueStatus(flag, channel);
	}
	
	/**
	 * メッセージキューへメッセージ送信
	 * @param feedstr メッセージ
	 * @param channel チャネル
	 */
	public void setMessageQueue(String feedstr, String channel)
	throws IOException, TaggingException {
		FeedBase feed = (FeedBase) templateMapper.fromJSON(JsUtil.addfeedstr(feedstr));
		reflexContext.setMessageQueue(feed, channel);
	}
	
	/**
	 * メッセージキューからメッセージ受信
	 * @param channel チャネル
	 * @return メッセージ
	 */
	public String getMessageQueue(String channel)
	throws IOException, TaggingException {
		FeedBase feed = reflexContext.getMessageQueue(channel);
		return templateMapper.toJSON(feed);
	}
	
	/**
	 * グループに参加する.
	 * グループ作成者がユーザへの参加承認をしている状態の時、ユーザがグループに参加する。
	 * 自身のグループエイリアス作成(登録がない場合→再加入を想定)と、自身のグループエイリアスへの署名を行う。
	 * @param group グループ名
	 * @param selfid 自身のグループエイリアスのselfid (/_user/{UID}/group/{selfid})
	 * @return グループエントリー
	 */
	public String joinGroup(String group, String selfid)
	throws IOException, TaggingException {
		EntryBase entry = reflexContext.joinGroup(group, selfid);
		FeedBase feed = TaggingEntryUtil.createFeed(reflexContext.getServiceName(), entry);
		return templateMapper.toJSON(feed);
	}
	
	/**
	 * グループから退会する.
	 * グループエントリーの、自身のグループエイリアスを削除する。
	 * @param group グループ名
	 * @return 退会したグループエントリー
	 */
	public String leaveGroup(String group)
	throws IOException, TaggingException {
		EntryBase entry = reflexContext.leaveGroup(group);
		FeedBase feed = TaggingEntryUtil.createFeed(reflexContext.getServiceName(), entry);
		return templateMapper.toJSON(feed);
	}
	
	/**
	 * グループに参加登録する.
	 * 署名はなし。
	 * @param group グループ名
	 * @param selfid 自身のグループエイリアスのselfid (/_user/{UID}/group/{selfid})
	 * @return グループエントリー
	 */
	public String addGroup(String group, String selfid)
	throws IOException, TaggingException {
		EntryBase entry = reflexContext.addGroup(group, selfid);
		return get(entry);
	}
	
	/**
	 * 管理者によるグループの参加登録.
	 * 署名はなし。
	 * @param uids UIDリスト
	 * @param group グループ名
	 * @param selfid 自身のグループエイリアスのselfid (/_user/{UID}/group/{selfid})
	 * @return グループエントリー
	 */
	public String addGroupByAdmin(String[] uids, String group, String selfid)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		FeedBase feed = TaggingEntryUtil.createFeed(serviceName);
		if (uids != null) {
			// [{"link": [{"___rel": "self", "___href": "/_user/{UID}"}]}, ...]
			for (String uid : uids) {
				EntryBase entry = TaggingEntryUtil.createEntry(serviceName);
				Link link = new Link();
				link._$rel = Link.REL_SELF;
				link._$href = Constants.URI_USER + "/" + uid;
				entry.addLink(link);
				feed.addEntry(entry);
			}
		}
		FeedBase result = reflexContext.addGroupByAdmin(group, selfid, feed);
		return templateMapper.toJSON(result);
	}

	/**
	 * グループ管理者登録
	 * @param feedstr ユーザ登録情報
	 * @return グループエントリーリスト
	 */
	public String createGroupadmin(String feedstr) throws IOException, TaggingException {
		FeedBase feed = (FeedBase) templateMapper.fromJSON(JsUtil.addfeedstr(feedstr));
		FeedBase result = reflexContext.createGroupadmin(feed);
		return templateMapper.toJSON(result);
	}

	/**
	 * グループ管理用グループを削除する.
	 * @param groupNames グループ管理用グループリスト
	 * @param async 削除を非同期に行う場合true
	 */
	public void deleteGroupadmin(String[] groupNames, boolean async)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		FeedBase feed = TaggingEntryUtil.createFeed(serviceName);
		if (groupNames != null) {
			// [{"link": [{"___rel": "self", "___href": "/_group/{グループ名}"}]}, ... ]
			for (String groupName : groupNames) {
				EntryBase entry = TaggingEntryUtil.createEntry(serviceName);
				Link link = new Link();
				link._$rel = Link.REL_SELF;
				link._$href = Constants.URI_GROUP + "/" + groupName;
				entry.addLink(link);
				feed.addEntry(entry);
			}
		}
		reflexContext.deleteGroupadmin(feed, async);
	}

	/**
	 * RDBで更新SQLを実行
	 * @param sql SQL
	 * @param async 非同期の場合true
	 */
	public void execRdb(String sql, boolean async) throws IOException, TaggingException {
		String[] sqls = null;
		if (!StringUtils.isBlank(sql)) {
			sqls = new String[] {sql};
		}
		execRdb(sqls, async);
	}

	/**
	 * RDBで更新SQLを実行
	 * @param sqls SQLリスト
	 * @param async 非同期の場合true
	 */
	public void execRdb(String[] sqls, boolean async) throws IOException, TaggingException {
		if (async) {
			reflexContext.execRdbAsync(sqls);
		} else {
			reflexContext.execRdb(sqls);
		}
	}

	/**
	 * RDBで更新SQLを実行
	 * @param sql SQL
	 * @param async 非同期の場合true
	 */
	public void bulkexecRdb(String sql, boolean async) throws IOException, TaggingException {
		String[] sqls = null;
		if (!StringUtils.isBlank(sql)) {
			sqls = new String[] {sql};
		}
		bulkexecRdb(sqls, async);
	}

	/**
	 * RDBで更新SQLを実行
	 * @param sqls SQLリスト
	 * @param async 非同期の場合true
	 */
	public void bulkexecRdb(String[] sqls, boolean async) throws IOException, TaggingException {
		if (async) {
			reflexContext.bulkExecRdbAsync(sqls);
		} else {
			reflexContext.bulkExecRdb(sqls);
		}
	}

	/**
	 * AutoCommit設定を取得する.
	 * @return autoCommit true:AutoCommit、false:明示的なコミットが必要
	 */
	public boolean getAutoCommitRdb()
	throws IOException, TaggingException {
		return reflexContext.getAutoCommitRdb();
	}
	
	/**
	 * AutoCommitを設定する.
	 * デフォルトはtrue。
	 * execSqlにおいて非同期処理(async=true)の場合、この設定は無効になる。(非同期処理の場合AutoCommit=true)
	 * @param autoCommit true:AutoCommit、false:明示的なコミットが必要
	 */
	public void setAutoCommitRdb(boolean autoCommit)
	throws IOException, TaggingException {
		reflexContext.setAutoCommitRdb(autoCommit);
	}

	/**
	 * コミットを実行する.
	 * AutoCommit=falseの場合に有効。
	 */
	public void commitRdb()
	throws IOException, TaggingException {
		reflexContext.commitRdb();
	}
	
	/**
	 * ロールバックを実行する.
	 * AutoCommit=falseの場合に有効。
	 */
	public void rollbackRdb()
	throws IOException, TaggingException {
		reflexContext.rollbackRdb();
	}

	/**
	 * RDBでクエリSQLを実行
	 * @param sql SQL
	 * @return 検索結果
	 */
	public String queryRdb(String sql) throws IOException, TaggingException {
		List<Map<String, Object>> result = reflexContext.queryRdb(sql);
		return JsUtil.convertResultBQ(result, null);
	}

	/**
	 * RDBでクエリSQLを実行
	 * @param sql SQL
	 * @param parent 親項目
	 * @return 検索結果
	 */
	public String queryRdb(String sql, String parent) 
	throws IOException, ParseException, TaggingException {
		List<Map<String, Object>> result = reflexContext.queryRdb(sql);
		return JsUtil.convertResultBQ(result, parent);
	}

	/**
	 * RDBでクエリSQLを実行し、CSV形式にしてレスポンスする.
	 * @param sql SQL
	 * @param filename ファイル名
	 * @param header CSV先頭行
	 */
	public void doResponseQueryRdbCsv(String sql, String filename, String header) 
	throws IOException, ParseException, TaggingException {
		List<Map<String, Object>> result = reflexContext.queryRdb(sql);
		String[] rows = JsUtil.convertResultBQcsv(result, header);
		this.doResponseCsv(rows, filename);
	}

}
