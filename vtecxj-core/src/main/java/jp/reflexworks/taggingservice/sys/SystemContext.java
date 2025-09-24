package jp.reflexworks.taggingservice.sys;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import jakarta.activation.DataSource;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContentInfo;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.blogic.LogBlogic;
import jp.reflexworks.taggingservice.context.ReflexContextUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.UpdatedInfo;
import jp.reflexworks.taggingservice.util.Constants;

/**
 * ReflexContextをシステムユーザで使用するクラス.
 * TaggingService内部処理で使用します。
 * 各メソッドの説明は、ReflexContextクラスを参照してください。
 */
public class SystemContext implements ReflexContext {

	/** ReflexContext */
	private ReflexContext reflexContext;

	/**
	 * コンストラクタ
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public SystemContext(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo) {
		ReflexAuthentication auth = new SystemAuthentication(null, null, serviceName);
		this.reflexContext = ReflexContextUtil.getReflexContext(auth,
				requestInfo, connectionInfo);
	}

	/**
	 * コンストラクタ
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public SystemContext(ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo) {
		ReflexAuthentication sysAuth = new SystemAuthentication(auth);
		this.reflexContext = ReflexContextUtil.getReflexContext(sysAuth,
				requestInfo, connectionInfo);
	}

	/**
	 * 認証情報取得.
	 * @return Reflex内で使用する認証情報
	 */
	public ReflexAuthentication getAuth() {
		return reflexContext.getAuth();
	}

	/**
	 * UID取得.
	 * @return UID
	 */
	public String getUid() {
		return reflexContext.getUid();
	}

	/**
	 * アカウント取得.
	 * @return アカウント
	 */
	public String getAccount() {
		return reflexContext.getAccount();
	}

	/**
	 * サービス名取得.
	 * @return サービス名
	 */
	public String getServiceName() {
		return reflexContext.getServiceName();
	}

	/**
	 * ResourceMapper取得.
	 * <p>
	 * 起動時に生成したResourceMapperを返却します。
	 * </p>
	 * @return ResourceMapper
	 */
	public FeedTemplateMapper getResourceMapper() {
		return reflexContext.getResourceMapper();
	}

	/**
	 * ResourceMapper取得.
	 * <p>
	 * 指定されたサービスのResourceMapperを返却します。
	 * </p>
	 * @param targetServiceName 対象サービス
	 * @return ResourceMapper
	 */
	public FeedTemplateMapper getResourceMapper(String targetServiceName)
	throws IOException, TaggingException {
		return reflexContext.getResourceMapper(targetServiceName);
	}

	@Override
	public FeedBase whoami() throws IOException, TaggingException {
		return reflexContext.whoami();
	}

	@Override
	public FeedBase getGroups() throws IOException, TaggingException {
		return reflexContext.getGroups();
	}

	@Override
	public boolean isGroupMember(String group) throws IOException, TaggingException {
		return reflexContext.isGroupMember(group);
	}

	@Override
	public EntryBase getEntry(String requestUri) throws IOException, TaggingException {
		return reflexContext.getEntry(requestUri);
	}

	@Override
	public EntryBase getEntry(String requestUri, boolean useCache)
	throws IOException, TaggingException {
		return reflexContext.getEntry(requestUri, useCache);
	}

	@Override
	public EntryBase getEntry(RequestParam param) throws IOException, TaggingException {
		return reflexContext.getEntry(param);
	}

	@Override
	public EntryBase getEntry(RequestParam param, boolean useCache) throws IOException, TaggingException {
		return reflexContext.getEntry(param, useCache);
	}

	@Override
	public EntryBase getEntry(String requestUri, String targetServiceName, String targetApiKey) throws IOException, TaggingException {
		return reflexContext.getEntry(requestUri, targetServiceName, targetApiKey);
	}

	@Override
	public EntryBase getEntry(String requestUri, boolean useCache, String targetServiceName, String targetApiKey)
	throws IOException, TaggingException {
		return reflexContext.getEntry(requestUri, useCache, targetServiceName, targetApiKey);
	}

	@Override
	public EntryBase getEntry(RequestParam param, String targetServiceName, String targetApiKey) throws IOException, TaggingException {
		return reflexContext.getEntry(param, targetServiceName, targetApiKey);
	}

	@Override
	public EntryBase getEntry(RequestParam param, boolean useCache, String targetServiceName, String targetApiKey) throws IOException, TaggingException {
		return reflexContext.getEntry(param, useCache, targetServiceName, targetApiKey);
	}

	@Override
	public FeedBase getFeed(String requestUri) throws IOException, TaggingException {
		return reflexContext.getFeed(requestUri);
	}

	@Override
	public FeedBase getFeed(String requestUri, boolean useCache)
	throws IOException, TaggingException {
		return reflexContext.getFeed(requestUri, useCache);
	}

	@Override
	public FeedBase getFeed(RequestParam param) throws IOException, TaggingException {
		return reflexContext.getFeed(param);
	}

	@Override
	public FeedBase getFeed(RequestParam param, boolean useCache) throws IOException, TaggingException {
		return reflexContext.getFeed(param, useCache);
	}

	@Override
	public FeedBase getFeed(String requestUri, String targetServiceName, String targetApiKey) throws IOException, TaggingException {
		return reflexContext.getFeed(requestUri, targetServiceName, targetApiKey);
	}

	@Override
	public FeedBase getFeed(String requestUri, boolean useCache, String targetServiceName, String targetApiKey)
	throws IOException, TaggingException {
		return reflexContext.getFeed(requestUri, useCache, targetServiceName, targetApiKey);
	}

	@Override
	public FeedBase getFeed(RequestParam param, String targetServiceName, String targetApiKey) throws IOException, TaggingException {
		return reflexContext.getFeed(param, targetServiceName, targetApiKey);
	}

	@Override
	public FeedBase getFeed(RequestParam param, boolean useCache, String targetServiceName, String targetApiKey) throws IOException, TaggingException {
		return reflexContext.getFeed(param, useCache, targetServiceName, targetApiKey);
	}

	@Override
	public FeedBase getCount(String requestUri) throws IOException, TaggingException {
		return reflexContext.getCount(requestUri);
	}

	@Override
	public FeedBase getCount(String requestUri, boolean useCache) throws IOException, TaggingException {
		return reflexContext.getCount(requestUri, useCache);
	}

	@Override
	public FeedBase getCount(RequestParam param) throws IOException, TaggingException {
		return reflexContext.getCount(param);
	}

	@Override
	public FeedBase getCount(RequestParam param, boolean useCache) throws IOException, TaggingException {
		return reflexContext.getCount(param, useCache);
	}

	@Override
	public FeedBase getCount(String requestUri, String targetServiceName, String targetApiKey) throws IOException, TaggingException {
		return reflexContext.getCount(requestUri, targetServiceName, targetApiKey);
	}

	@Override
	public FeedBase getCount(String requestUri, boolean useCache, String targetServiceName, String targetApiKey) throws IOException, TaggingException {
		return reflexContext.getCount(requestUri, useCache, targetServiceName, targetApiKey);
	}

	@Override
	public FeedBase getCount(RequestParam param, String targetServiceName, String targetApiKey) throws IOException, TaggingException {
		return reflexContext.getCount(param, targetServiceName, targetApiKey);
	}

	@Override
	public FeedBase getCount(RequestParam param, boolean useCache, String targetServiceName, String targetApiKey) throws IOException, TaggingException {
		return reflexContext.getCount(param, useCache, targetServiceName, targetApiKey);
	}

	@Override
	public FeedBase allocids(String uri, int num) throws IOException, TaggingException {
		return reflexContext.allocids(uri, num);
	}

	@Override
	public FeedBase allocids(String uri, int num, String targetServiceName, String targetApiKey) throws IOException, TaggingException {
		return reflexContext.allocids(uri, num, targetServiceName, targetApiKey);
	}

	@Override
	public FeedBase addids(String uri, long num) throws IOException, TaggingException {
		return reflexContext.addids(uri, num);
	}

	@Override
	public FeedBase addids(String uri, long num, String targetServiceName, String targetApiKey) throws IOException, TaggingException {
		return reflexContext.addids(uri, num, targetServiceName, targetApiKey);
	}

	@Override
	public FeedBase getids(String uri) throws IOException, TaggingException {
		return reflexContext.getids(uri);
	}

	@Override
	public FeedBase getids(String uri, String targetServiceName, String targetApiKey) throws IOException, TaggingException {
		return reflexContext.getids(uri, targetServiceName, targetApiKey);
	}

	@Override
	public FeedBase setids(String uri, long value) throws IOException, TaggingException {
		return reflexContext.setids(uri, value);
	}

	@Override
	public FeedBase setids(String uri, long value, String targetServiceName, String targetApiKey) throws IOException, TaggingException {
		return reflexContext.setids(uri, value, targetServiceName, targetApiKey);
	}

	@Override
	public FeedBase rangeids(String uri, String value) throws IOException, TaggingException {
		return reflexContext.rangeids(uri, value);
	}

	@Override
	public FeedBase getRangeids(String uri) throws IOException, TaggingException {
		return reflexContext.getRangeids(uri);
	}

	@Override
	public EntryBase post(EntryBase entry)
	throws IOException, TaggingException {
		return reflexContext.post(entry);
	}

	@Override
	public EntryBase post(EntryBase entry, RequestParam param) throws IOException, TaggingException {
		return reflexContext.post(entry, param);
	}

	@Override
	public EntryBase post(EntryBase entry, String uri) throws IOException, TaggingException {
		return reflexContext.post(entry, uri);
	}

	@Override
	public EntryBase postWithExtension(EntryBase entry, String uri, String ext) throws IOException, TaggingException {
		return reflexContext.postWithExtension(entry, uri, ext);
	}

	@Override
	public FeedBase post(FeedBase feed)
	throws IOException, TaggingException {
		return reflexContext.post(feed);
	}

	@Override
	public FeedBase post(FeedBase feed, RequestParam param) throws IOException, TaggingException {
		return reflexContext.post(feed, param);
	}

	@Override
	public FeedBase post(FeedBase feed, String uri) throws IOException, TaggingException {
		return reflexContext.post(feed, uri);
	}

	@Override
	public FeedBase postWithExtension(FeedBase feed, String uri, String ext) throws IOException, TaggingException {
		return reflexContext.postWithExtension(feed, uri, ext);
	}

	@Override
	public EntryBase post(EntryBase entry, String targetServiceName, String targetApiKey)
	throws IOException, TaggingException {
		return reflexContext.post(entry, targetServiceName, targetApiKey);
	}

	@Override
	public EntryBase post(EntryBase entry, RequestParam param, String targetServiceName, String targetApiKey) throws IOException, TaggingException {
		return reflexContext.post(entry, param, targetServiceName, targetApiKey);
	}

	@Override
	public EntryBase post(EntryBase entry, String uri, String targetServiceName, String targetApiKey) throws IOException, TaggingException {
		return reflexContext.post(entry, uri, targetServiceName, targetApiKey);
	}

	@Override
	public FeedBase post(FeedBase feed, String targetServiceName, String targetApiKey)
	throws IOException, TaggingException {
		return reflexContext.post(feed, targetServiceName, targetApiKey);
	}

	@Override
	public FeedBase post(FeedBase feed, RequestParam param, String targetServiceName, String targetApiKey) throws IOException, TaggingException {
		return reflexContext.post(feed, param, targetServiceName, targetApiKey);
	}

	@Override
	public FeedBase post(FeedBase feed, String uri, String targetServiceName, String targetApiKey) throws IOException, TaggingException {
		return reflexContext.post(feed, uri, targetServiceName, targetApiKey);
	}

	@Override
	public EntryBase put(EntryBase entry)
	throws IOException, TaggingException {
		return reflexContext.put(entry);
	}

	@Override
	public EntryBase put(EntryBase entry, String requestUri) throws IOException, TaggingException {
		return reflexContext.put(entry, requestUri);
	}

	@Override
	public EntryBase put(EntryBase entry, RequestParam param) throws IOException, TaggingException {
		return reflexContext.put(entry, param);
	}

	@Override
	public FeedBase put(FeedBase feed)
	throws IOException, TaggingException {
		return reflexContext.put(feed);
	}

	@Override
	public FeedBase put(FeedBase feed, String requestUri) throws IOException, TaggingException {
		return reflexContext.put(feed, requestUri);
	}

	@Override
	public FeedBase put(FeedBase feed, RequestParam param) throws IOException, TaggingException {
		return reflexContext.put(feed, param);
	}

	@Override
	public EntryBase put(EntryBase entry, String targetServiceName, String targetApiKey)
	throws IOException, TaggingException {
		return reflexContext.put(entry, targetServiceName, targetApiKey);
	}

	@Override
	public EntryBase put(EntryBase entry, String requestUri, String targetServiceName, String targetApiKey) throws IOException, TaggingException {
		return reflexContext.put(entry, requestUri, targetServiceName, targetApiKey);
	}

	@Override
	public EntryBase put(EntryBase entry, RequestParam param, String targetServiceName, String targetApiKey) throws IOException, TaggingException {
		return reflexContext.put(entry, param, targetServiceName, targetApiKey);
	}

	@Override
	public FeedBase put(FeedBase feed, String targetServiceName, String targetApiKey)
	throws IOException, TaggingException {
		return reflexContext.put(feed, targetServiceName, targetApiKey);
	}

	@Override
	public FeedBase put(FeedBase feed, String requestUri, String targetServiceName, String targetApiKey) throws IOException, TaggingException {
		return reflexContext.put(feed, requestUri, targetServiceName, targetApiKey);
	}

	@Override
	public FeedBase put(FeedBase feed, RequestParam param, String targetServiceName, String targetApiKey) throws IOException, TaggingException {
		return reflexContext.put(feed, param, targetServiceName, targetApiKey);
	}

	@Override
	public List<Future<List<UpdatedInfo>>> bulkPut(FeedBase feed, boolean async)
	throws IOException, TaggingException {
		return reflexContext.bulkPut(feed, async);
	}

	@Override
	public List<Future<List<UpdatedInfo>>> bulkPut(FeedBase feed, String requestUri, boolean async)
	throws IOException, TaggingException {
		return reflexContext.bulkPut(feed, requestUri, async);
	}

	@Override
	public List<Future<List<UpdatedInfo>>> bulkPut(FeedBase feed, RequestParam param, boolean async)
	throws IOException, TaggingException {
		return reflexContext.bulkPut(feed, param, async);
	}

	@Override
	public List<Future<List<UpdatedInfo>>> bulkPut(FeedBase feed, RequestParam param, boolean async,
			String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException {
		return reflexContext.bulkPut(feed, param, async, targetServiceName, targetServiceKey);
	}

	@Override
	public Future<List<UpdatedInfo>> bulkSerialPut(FeedBase feed, boolean async)
	throws IOException, TaggingException {
		return reflexContext.bulkSerialPut(feed, async);
	}

	@Override
	public Future<List<UpdatedInfo>> bulkSerialPut(FeedBase feed, String requestUri, boolean async)
	throws IOException, TaggingException {
		return reflexContext.bulkSerialPut(feed, requestUri, async);
	}

	@Override
	public Future<List<UpdatedInfo>> bulkSerialPut(FeedBase feed, RequestParam param, boolean async)
	throws IOException, TaggingException {
		return reflexContext.bulkSerialPut(feed, param, async);
	}

	@Override
	public Future<List<UpdatedInfo>> bulkSerialPut(FeedBase feed, RequestParam param, boolean async,
			String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException {
		return reflexContext.bulkSerialPut(feed, param, async, targetServiceName, targetServiceKey);
	}

	@Override
	public EntryBase delete(String uri, int revision) throws IOException, TaggingException {
		return reflexContext.delete(uri, revision);
	}

	@Override
	public EntryBase delete(String uri)
	throws IOException, TaggingException {
		return reflexContext.delete(uri);
	}

	@Override
	public EntryBase delete(RequestParam param) throws IOException, TaggingException {
		return reflexContext.delete(param);
	}

	@Override
	public FeedBase delete(FeedBase feed) throws IOException, TaggingException {
		return reflexContext.delete(feed);
	}

	@Override
	public FeedBase delete(List<String> ids)
	throws IOException, TaggingException {
		return reflexContext.delete(ids);
	}

	@Override
	public EntryBase delete(String uri, int revision, String targetServiceName, String targetApiKey) throws IOException, TaggingException {
		return reflexContext.delete(uri, revision, targetServiceName, targetApiKey);
	}

	@Override
	public EntryBase delete(String uri, String targetServiceName, String targetApiKey)
	throws IOException, TaggingException {
		return reflexContext.delete(uri, targetServiceName, targetApiKey);
	}

	@Override
	public EntryBase delete(RequestParam param, String targetServiceName, String targetApiKey) throws IOException, TaggingException {
		return reflexContext.delete(param, targetServiceName, targetApiKey);
	}

	@Override
	public FeedBase delete(FeedBase feed, String targetServiceName, String targetApiKey) throws IOException, TaggingException {
		return reflexContext.delete(feed, targetServiceName, targetApiKey);
	}

	@Override
	public FeedBase delete(List<String> ids, String targetServiceName, String targetApiKey)
	throws IOException, TaggingException {
		return reflexContext.delete(ids, targetServiceName, targetApiKey);
	}

	@Override
	public List<Future<List<UpdatedInfo>>> bulkDelete(FeedBase feed, boolean async)
	throws IOException, TaggingException {
		return reflexContext.bulkDelete(feed, async);
	}

	@Override
	public Future deleteFolder(String uri, boolean async, boolean isParallel)
	throws IOException, TaggingException {
		return reflexContext.deleteFolder(uri, async, isParallel);
	}

	@Override
	public Future deleteFolder(RequestParam param, boolean async, boolean isParallel)
	throws IOException, TaggingException {
		return reflexContext.deleteFolder(param, async, isParallel);
	}

	@Override
	public Future deleteFolder(RequestParam param, boolean async, boolean isParallel,
			String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException {
		return reflexContext.deleteFolder(param, async, isParallel, targetServiceName, targetServiceKey);
	}

	@Override
	public Future clearFolder(String uri, boolean async, boolean isParallel)
	throws IOException, TaggingException {
		return reflexContext.clearFolder(uri, async, isParallel);
	}

	@Override
	public Future clearFolder(RequestParam param, boolean async, boolean isParallel)
	throws IOException, TaggingException {
		return reflexContext.clearFolder(param, async, isParallel);
	}

	@Override
	public Future clearFolder(RequestParam param, boolean async, boolean isParallel,
			String targetServiceName, String targetServiceKey)
	throws IOException, TaggingException {
		return reflexContext.clearFolder(param, async, isParallel, targetServiceName, targetServiceKey);
	}

	@Override
	public FeedBase putContent() throws IOException, TaggingException {
		return reflexContext.putContent();
	}

	@Override
	public FeedBase putContentBySize() throws IOException, TaggingException {
		return reflexContext.putContentBySize();
	}

	@Override
	public FeedBase putContent(Map<String, String> namesAndKeys) throws IOException, TaggingException {
		return reflexContext.putContent(namesAndKeys);
	}

	@Override
	public FeedBase putContentBySize(Map<String, String> namesAndKeys) throws IOException, TaggingException {
		return reflexContext.putContentBySize(namesAndKeys);
	}

	@Override
	public FeedBase putContentSignedUrl()
	throws IOException, TaggingException {
		return reflexContext.putContentSignedUrl();
	}

	@Override
	public FeedBase postContent(String parentUri) throws IOException, TaggingException {
		return reflexContext.postContent(parentUri);
	}
	
	@Override
	public FeedBase postContent(String parentUri, String ext) throws IOException, TaggingException {
		return reflexContext.postContent(parentUri, ext);
	}

	@Override
	public FeedBase postContentSignedUrl(String parentUri, String ext)
	throws IOException, TaggingException {
		return reflexContext.postContentSignedUrl(parentUri, ext);
	}

	@Override
	public ReflexContentInfo getContent(String uri) throws IOException, TaggingException {
		return reflexContext.getContent(uri);
	}

	@Override
	public ReflexContentInfo getContent(String uri, boolean checkEtag)
	throws IOException, TaggingException {
		return reflexContext.getContent(uri, checkEtag);
	}

	@Override
	public FeedBase getContentSignedUrl(String uri) throws IOException, TaggingException {
		return reflexContext.getContentSignedUrl(uri);
	}

	@Override
	public EntryBase deleteContent(String uri) throws IOException, TaggingException {
		return reflexContext.deleteContent(uri);
	}

	@Override
	public byte[] getHtmlContent(String requestUri) throws IOException, TaggingException {
		return reflexContext.getHtmlContent(requestUri);
	}

	@Override
	public ReflexRequest getRequest() {
		return reflexContext.getRequest();
	}

	@Override
	public ConnectionInfo getConnectionInfo() {
		return reflexContext.getConnectionInfo();
	}

	@Override
	public RequestInfo getRequestInfo() {
		return reflexContext.getRequestInfo();
	}

	@Override
	public String getRXID()
	throws IOException, TaggingException {
		return reflexContext.getRXID();
	}

	@Override
	public String getAccessToken()
	throws IOException, TaggingException {
		return reflexContext.getAccessToken();
	}

	@Override
	public String getLinkToken(String uri)
	throws IOException, TaggingException {
		return reflexContext.getLinkToken(uri);
	}

	@Override
	public void changeAccessKey()
	throws IOException, TaggingException {
		reflexContext.changeAccessKey();
	}

	@Override
	public ReflexContext getServiceAdminContext() {
		return reflexContext.getServiceAdminContext();
	}

	@Override
	public FeedBase setCacheFeed(String uri, FeedBase feed) throws IOException, TaggingException {
		return reflexContext.setCacheFeed(uri, feed);
	}

	@Override
	public FeedBase setCacheFeed(String uri, FeedBase feed, Integer sec) throws IOException, TaggingException {
		return reflexContext.setCacheFeed(uri, feed, sec);
	}

	@Override
	public boolean setCacheFeedIfAbsent(String uri, FeedBase feed)
	throws IOException, TaggingException {
		return reflexContext.setCacheFeedIfAbsent(uri, feed);
	}

	@Override
	public EntryBase setCacheEntry(String uri, EntryBase entry) throws IOException, TaggingException {
		return reflexContext.setCacheEntry(uri, entry);
	}

	@Override
	public EntryBase setCacheEntry(String uri, EntryBase entry, Integer sec)
			throws IOException, TaggingException {
		return reflexContext.setCacheEntry(uri, entry, sec);
	}

	@Override
	public boolean setCacheEntryIfAbsent(String uri, EntryBase entry)
	throws IOException, TaggingException {
		return reflexContext.setCacheEntryIfAbsent(uri, entry);
	}

	@Override
	public String setCacheString(String uri, String text) throws IOException, TaggingException {
		return reflexContext.setCacheString(uri, text);
	}

	@Override
	public String setCacheString(String uri, String text, Integer sec) throws IOException, TaggingException {
		return reflexContext.setCacheString(uri, text, sec);
	}

	@Override
	public boolean setCacheStringIfAbsent(String uri, String text)
	throws IOException, TaggingException {
		return reflexContext.setCacheStringIfAbsent(uri, text);
	}

	@Override
	public long setCacheLong(String uri, long num) throws IOException, TaggingException {
		return reflexContext.setCacheLong(uri, num);
	}

	@Override
	public long setCacheLong(String uri, long num, Integer sec) throws IOException, TaggingException {
		return reflexContext.setCacheLong(uri, num, sec);
	}

	@Override
	public boolean setCacheLongIfAbsent(String uri, long num)
	throws IOException, TaggingException {
		return reflexContext.setCacheLongIfAbsent(uri, num);
	}

	@Override
	public long incrementCache(String uri, long num) throws IOException, TaggingException {
		return reflexContext.incrementCache(uri, num);
	}

	@Override
	public boolean deleteCacheFeed(String uri) throws IOException, TaggingException {
		return reflexContext.deleteCacheFeed(uri);
	}

	@Override
	public boolean deleteCacheEntry(String uri) throws IOException, TaggingException {
		return reflexContext.deleteCacheEntry(uri);
	}

	@Override
	public boolean deleteCacheString(String uri) throws IOException, TaggingException {
		return reflexContext.deleteCacheString(uri);
	}

	@Override
	public boolean deleteCacheLong(String uri) throws IOException, TaggingException {
		return reflexContext.deleteCacheLong(uri);
	}

	@Override
	public FeedBase getCacheFeed(String uri) throws IOException, TaggingException {
		return reflexContext.getCacheFeed(uri);
	}

	@Override
	public EntryBase getCacheEntry(String uri) throws IOException, TaggingException {
		return reflexContext.getCacheEntry(uri);
	}

	@Override
	public String getCacheString(String uri) throws IOException, TaggingException {
		return reflexContext.getCacheString(uri);
	}

	@Override
	public Long getCacheLong(String uri) throws IOException, TaggingException {
		return reflexContext.getCacheLong(uri);
	}

	@Override
	public boolean setExpireCacheFeed(String uri, int sec) throws IOException, TaggingException {
		return reflexContext.setExpireCacheFeed(uri, sec);
	}

	@Override
	public boolean setExpireCacheEntry(String uri, int sec) throws IOException, TaggingException {
		return reflexContext.setExpireCacheEntry(uri, sec);
	}

	@Override
	public boolean setExpireCacheString(String uri, int sec) throws IOException, TaggingException {
		return reflexContext.setExpireCacheString(uri, sec);
	}

	@Override
	public boolean setExpireCacheLong(String uri, int sec) throws IOException, TaggingException {
		return reflexContext.setExpireCacheLong(uri, sec);
	}

	@Override
	public boolean cacheFlushAll()
	throws IOException, TaggingException {
		return reflexContext.cacheFlushAll();
	}

	@Override
	public FeedBase setSessionFeed(String name, FeedBase feed) throws IOException, TaggingException {
		return reflexContext.setSessionFeed(name, feed);
	}

	@Override
	public EntryBase setSessionEntry(String name, EntryBase entry) throws IOException, TaggingException {
		return reflexContext.setSessionEntry(name, entry);
	}

	@Override
	public String setSessionString(String name, String text) throws IOException, TaggingException {
		return reflexContext.setSessionString(name, text);
	}

	@Override
	public String setSessionStringIfAbsent(String name, String text) throws IOException, TaggingException {
		return reflexContext.setSessionString(name, text);
	}

	@Override
	public long setSessionLong(String name, long num) throws IOException, TaggingException {
		return reflexContext.setSessionLong(name, num);
	}

	@Override
	public Long setSessionLongIfAbsent(String name, long num) throws IOException, TaggingException {
		return reflexContext.setSessionLongIfAbsent(name, num);
	}

	@Override
	public long incrementSession(String name, long num)
	throws IOException, TaggingException {
		return reflexContext.incrementSession(name, num);
	}

	@Override
	public void deleteSessionFeed(String name) throws IOException, TaggingException {
		reflexContext.deleteSessionFeed(name);
	}

	@Override
	public void deleteSessionEntry(String name) throws IOException, TaggingException {
		reflexContext.deleteSessionEntry(name);
	}

	@Override
	public void deleteSessionString(String name) throws IOException, TaggingException {
		reflexContext.deleteSessionString(name);
	}

	@Override
	public void deleteSessionLong(String name) throws IOException, TaggingException {
		reflexContext.deleteSessionLong(name);
	}

	@Override
	public FeedBase getSessionFeed(String name) throws IOException, TaggingException {
		return reflexContext.getSessionFeed(name);
	}

	@Override
	public EntryBase getSessionEntry(String name) throws IOException, TaggingException {
		return reflexContext.getSessionEntry(name);
	}

	@Override
	public String getSessionString(String name) throws IOException, TaggingException {
		return reflexContext.getSessionString(name);
	}

	@Override
	public Long getSessionLong(String name) throws IOException, TaggingException {
		return reflexContext.getSessionLong(name);
	}

	@Override
	public List<String> getSessionFeedKeys()
	throws IOException, TaggingException {
		return reflexContext.getSessionFeedKeys();
	}

	@Override
	public List<String> getSessionEntryKeys()
	throws IOException, TaggingException {
		return reflexContext.getSessionEntryKeys();
	}

	@Override
	public List<String> getSessionStringKeys()
	throws IOException, TaggingException {
		return reflexContext.getSessionStringKeys();
	}

	@Override
	public List<String> getSessionLongKeys()
	throws IOException, TaggingException {
		return reflexContext.getSessionLongKeys();
	}

	@Override
	public Map<String, List<String>> getSessionKeys()
	throws IOException, TaggingException {
		return reflexContext.getSessionKeys();
	}

	@Override
	public void resetExpire() throws IOException, TaggingException {
		reflexContext.resetExpire();
	}

	@Override
	public boolean checkSignature(String uri) throws IOException, TaggingException {
		return reflexContext.checkSignature(uri);
	}

	@Override
	public EntryBase putSignature(String uri, Integer revision) throws IOException, TaggingException {
		return reflexContext.putSignature(uri, revision);
	}

	@Override
	public FeedBase putSignatures(FeedBase feed) throws IOException, TaggingException {
		return reflexContext.putSignatures(feed);
	}

	@Override
	public void deleteSignature(String uri, Integer revision) throws IOException, TaggingException {
		reflexContext.deleteSignature(uri, revision);
	}

	@Override
	public FeedBase adduserByAdmin(FeedBase feed)
	throws IOException, TaggingException {
		return reflexContext.adduserByAdmin(feed);
	}

	@Override
	public FeedBase adduserByGroupadmin(FeedBase feed, String groupName)
	throws IOException, TaggingException {
		return reflexContext.adduserByGroupadmin(feed, groupName);
	}

	@Override
	public FeedBase changepassByAdmin(FeedBase feed)
	throws IOException, TaggingException {
		return reflexContext.changepassByAdmin(feed);
	}
	
	@Override
	public FeedBase createGroupadmin(FeedBase feed)
	throws IOException, TaggingException {
		return reflexContext.createGroupadmin(feed);
	}

	@Override
	public void deleteGroupadmin(String groupName, boolean async)
	throws IOException, TaggingException {
		reflexContext.deleteGroupadmin(groupName, async);
	}

	@Override
	public void deleteGroupadmin(FeedBase feed, boolean async)
	throws IOException, TaggingException {
		reflexContext.deleteGroupadmin(feed, async);
	}

	@Override
	public void log(String title, String subtitle, String message) {
		reflexContext.log(title, subtitle, message);
	}

	@Override
	public void log(FeedBase feed) throws IOException, TaggingException {
		reflexContext.log(feed);
	}

	/**
	 * エラーログを出力します.
	 * @param exceptionName 例外名
	 * @param message メッセージ
	 */
	public void errorLog(String exceptionName, String message) {
		log(exceptionName, Constants.WARN, message);
	}

	/**
	 * エラーログを出力します.
	 * @param e 例外
	 */
	public void errorLog(Throwable e) {
		String exceptionName = e.getClass().getSimpleName();
		LogBlogic logBlogic = new LogBlogic();
		String message = logBlogic.getErrorMessage(e);
		errorLog(exceptionName, message);
	}

	@Override
	public String getSettingValue(String key) {
		return reflexContext.getSettingValue(key);
	}

	@Override
	public void sendMail(String title, String textMessage,
			String to, List<DataSource> attachments)
	throws IOException, TaggingException {
		reflexContext.sendMail(title, textMessage, to, attachments);
	}

	@Override
	public void sendMail(String title, String textMessage,
			String[] to, String[] cc, String[] bcc, List<DataSource> attachments)
	throws IOException, TaggingException {
		reflexContext.sendMail(title, textMessage, to, cc, bcc, attachments);
	}

	@Override
	public void sendHtmlMail(String title, String textMessage, String htmlMessage,
			String to, List<DataSource> attachments)
	throws IOException, TaggingException {
		reflexContext.sendHtmlMail(title, textMessage, htmlMessage, to, attachments);
	}

	@Override
	public void sendHtmlMail(String title, String textMessage, String htmlMessage,
			String[] to, String[] cc, String[] bcc, List<DataSource> attachments)
	throws IOException, TaggingException {
		reflexContext.sendHtmlMail(title, textMessage, htmlMessage, to,
				cc, bcc, attachments);
	}

	@Override
	public void sendMail(EntryBase entry, String to, String[] attachments)
	throws IOException, TaggingException {
		reflexContext.sendMail(entry, to, attachments);
	}

	@Override
	public void sendMail(EntryBase entry, String[] to,
			String[] cc, String[] bcc, String[] attachments)
	throws IOException, TaggingException {
		reflexContext.sendMail(entry, to, cc, bcc, attachments);
	}

	@Override
	public FeedBase pagination(String requestUri, String pageNum)
	throws IOException, TaggingException {
		return reflexContext.pagination(requestUri, pageNum);
	}

	@Override
	public FeedBase pagination(RequestParam param)
	throws IOException, TaggingException {
		return reflexContext.pagination(param);
	}

	@Override
	public FeedBase pagination(String requestUri, String pageNum, String targetServiceName, String targetApiKey)
	throws IOException, TaggingException {
		return reflexContext.pagination(requestUri, pageNum, targetServiceName, targetApiKey);
	}

	@Override
	public FeedBase pagination(RequestParam param, String targetServiceName, String targetApiKey)
	throws IOException, TaggingException {
		return reflexContext.pagination(param, targetServiceName, targetApiKey);
	}

	@Override
	public FeedBase getPage(String requestUri, String pageNum)
	throws IOException, TaggingException {
		return reflexContext.getPage(requestUri, pageNum);
	}

	@Override
	public FeedBase getPage(RequestParam param)
	throws IOException, TaggingException {
		return reflexContext.getPage(param);
	}

	@Override
	public FeedBase getPage(String requestUri, String pageNum, String targetServiceName, String targetApiKey)
	throws IOException, TaggingException {
		return reflexContext.getPage(requestUri, pageNum, targetServiceName, targetApiKey);
	}

	@Override
	public FeedBase getPage(RequestParam param, String targetServiceName, String targetApiKey)
	throws IOException, TaggingException {
		return reflexContext.getPage(param, targetServiceName, targetApiKey);
	}

	@Override
	public EntryBase getUserstatus(String email)
	throws IOException, TaggingException {
		return reflexContext.getUserstatus(email);
	}

	@Override
	public FeedBase getUserstatusList(RequestParam param)
	throws IOException, TaggingException {
		return reflexContext.getUserstatusList(param);
	}

	@Override
	public FeedBase getUserstatusList(String limitStr, String cursorStr)
	throws IOException, TaggingException {
		return reflexContext.getUserstatusList(limitStr, cursorStr);
	}

	@Override
	public EntryBase revokeUser(String email, boolean isDeleteGroups)
	throws IOException, TaggingException {
		return reflexContext.revokeUser(email, isDeleteGroups);
	}

	@Override
	public FeedBase revokeUser(FeedBase feed, boolean isDeleteGroups)
	throws IOException, TaggingException {
		return reflexContext.revokeUser(feed, isDeleteGroups);
	}

	@Override
	public EntryBase activateUser(String email)
	throws IOException, TaggingException {
		return reflexContext.activateUser(email);
	}

	@Override
	public FeedBase activateUser(FeedBase feed)
	throws IOException, TaggingException {
		return reflexContext.activateUser(feed);
	}

	@Override
	public EntryBase deleteUser(String email, boolean async)
	throws IOException, TaggingException {
		return reflexContext.deleteUser(email, async);
	}

	@Override
	public FeedBase deleteUser(FeedBase feed, boolean async)
	throws IOException, TaggingException {
		return reflexContext.deleteUser(feed, async);
	}

	@Override
	public FeedBase addAlias(FeedBase feed)
	throws IOException, TaggingException {
		return reflexContext.addAlias(feed);
	}

	@Override
	public FeedBase removeAlias(FeedBase feed)
	throws IOException, TaggingException {
		return reflexContext.removeAlias(feed);
	}

	@Override
	public FeedBase addAcl(FeedBase feed)
	throws IOException, TaggingException {
		return reflexContext.addAcl(feed);
	}

	@Override
	public FeedBase removeAcl(FeedBase feed)
	throws IOException, TaggingException {
		return reflexContext.removeAcl(feed);
	}

	@Override
	public Future postBq(FeedBase feed, boolean async)
	throws IOException, TaggingException {
		return reflexContext.postBq(feed, async);
	}

	@Override
	public Future postBq(FeedBase feed, Map<String, String> tableNames, boolean async)
	throws IOException, TaggingException {
		return reflexContext.postBq(feed, tableNames, async);
	}

	@Override
	public Future postBq(String tableName, List<Map<String, Object>> list, boolean async)
	throws IOException, TaggingException {
		return reflexContext.postBq(tableName, list, async);
	}

	@Override
	public Future deleteBq(String uri, boolean async)
	throws IOException, TaggingException {
		return reflexContext.deleteBq(uri, async);
	}

	@Override
	public Future deleteBq(String uri, Map<String, String> tableNames, boolean async)
	throws IOException, TaggingException {
		return reflexContext.deleteBq(uri, tableNames, async);
	}

	@Override
	public Future deleteBq(String[] uris, boolean async)
	throws IOException, TaggingException {
		return reflexContext.deleteBq(uris, async);
	}

	@Override
	public Future deleteBq(String[] uris, Map<String, String> tableNames, boolean async)
	throws IOException, TaggingException {
		return reflexContext.deleteBq(uris, tableNames, async);
	}

	@Override
	public List<Map<String, Object>> queryBq(String sql)
	throws IOException, TaggingException {
		return reflexContext.queryBq(sql);
	}

	@Override
	public FeedBase callSDK(String name, String args[])
	throws IOException, TaggingException {
		return reflexContext.callSDK(name, args);
	}

	@Override
	public String getNamespace() throws IOException, TaggingException {
		return reflexContext.getNamespace();
	}

	@Override
	public void putIndex(FeedBase feed, boolean isDelete)
	throws IOException, TaggingException {
		reflexContext.putIndex(feed, isDelete);
	}

	@Override
	public FeedBase checkIndex(RequestParam param)
	throws IOException, TaggingException {
		return reflexContext.checkIndex(param);
	}

	@Override
	public void pushNotification(String body, String[] to)
	throws IOException, TaggingException {
		reflexContext.pushNotification(body, to);
	}

	@Override
	public void pushNotification(FeedBase feed, String[] to)
	throws IOException, TaggingException {
		reflexContext.pushNotification(feed, to);
	}
	
	@Override
	public void sendWebSocket(FeedBase messageFeed, String channel)
	throws IOException, TaggingException {
		reflexContext.sendWebSocket(messageFeed, channel);
	}
	
	@Override
	public void closeWebSocket(String channel)
	throws IOException, TaggingException {
		reflexContext.closeWebSocket(channel);
	}

	@Override
	public FeedBase logout(ReflexRequest req, ReflexResponse resp)
	throws IOException, TaggingException {
		return reflexContext.logout(req, resp);
	}

	@Override
	public FeedBase getNoGroupMember(String uri) 
	throws IOException, TaggingException {
		return reflexContext.getNoGroupMember(uri);
	}
	
	@Override
	public byte[] toPdf(String htmlTemplate) 
	throws IOException, TaggingException {
		return reflexContext.toPdf(htmlTemplate);
	}

	@Override
	public void setMessageQueueStatus(boolean flag, String channel)
	throws IOException, TaggingException {
		reflexContext.setMessageQueueStatus(flag, channel);
	}

	@Override
	public boolean getMessageQueueStatus(String channel)
	throws IOException, TaggingException {
		return reflexContext.getMessageQueueStatus(channel);
	}

	@Override
	public void setMessageQueue(FeedBase feed, String channel)
	throws IOException, TaggingException {
		reflexContext.setMessageQueue(feed, channel);
	}
	
	@Override
	public FeedBase getMessageQueue(String channel)
	throws IOException, TaggingException {
		return reflexContext.getMessageQueue(channel);
	}
	
	@Override
	public EntryBase addGroup(String group, String selfid)
	throws IOException, TaggingException {
		return reflexContext.addGroup(group, selfid);
	}
	
	@Override
	public FeedBase addGroupByAdmin(String group, String selfid, FeedBase feed)
	throws IOException, TaggingException {
		return reflexContext.addGroupByAdmin(group, selfid, feed);
	}

	@Override
	public EntryBase joinGroup(String group, String selfid)
	throws IOException, TaggingException {
		return reflexContext.joinGroup(group, selfid);
	}

	@Override
	public EntryBase leaveGroup(String group)
	throws IOException, TaggingException {
		return reflexContext.leaveGroup(group);
	}

	@Override
	public FeedBase leaveGroupByAdmin(String group, FeedBase feed)
	throws IOException, TaggingException {
		return reflexContext.leaveGroupByAdmin(group, feed);
	}

	@Override
	public int[] execRdb(String[] sqls)
	throws IOException, TaggingException {
		return reflexContext.execRdb(sqls);
	}

	@Override
	public int[] bulkExecRdb(String[] sqls)
	throws IOException, TaggingException {
		return reflexContext.bulkExecRdb(sqls);
	}

	@Override
	public Future<int[]> execRdbAsync(String[] sqls)
	throws IOException, TaggingException {
		return reflexContext.execRdbAsync(sqls);
	}

	@Override
	public Future<int[]> bulkExecRdbAsync(String[] sqls)
	throws IOException, TaggingException {
		return reflexContext.bulkExecRdbAsync(sqls);
	}

	@Override
	public boolean getAutoCommitRdb()
	throws IOException, TaggingException {
		return reflexContext.getAutoCommitRdb();
	}
	
	@Override
	public void setAutoCommitRdb(boolean autoCommit)
	throws IOException, TaggingException {
		reflexContext.setAutoCommitRdb(autoCommit);
	}

	@Override
	public void commitRdb()
	throws IOException, TaggingException {
		reflexContext.commitRdb();
	}
	
	@Override
	public void rollbackRdb()
	throws IOException, TaggingException {
		reflexContext.rollbackRdb();
	}

	@Override
	public List<Map<String, Object>> queryRdb(String sql)
	throws IOException, TaggingException {
		return reflexContext.queryRdb(sql);
	}

	@Override
	public FeedBase postBdbq(FeedBase feed, String parentUri, Map<String, String> tableNames, boolean async)
	throws IOException, TaggingException{
		return reflexContext.postBdbq(feed, parentUri, tableNames, async);
	}

	@Override
	public FeedBase putBdbq(FeedBase feed, String parentUri, Map<String, String> tableNames, boolean async)
	throws IOException, TaggingException {
		return reflexContext.putBdbq(feed, parentUri, tableNames, async);
	}

	@Override
	public FeedBase deleteBdbq(String[] uris, Map<String, String> tableNames, boolean async)
	throws IOException, TaggingException {
		return reflexContext.deleteBdbq(uris, tableNames, async);
	}

	@Override
	public List<Future<List<UpdatedInfo>>> bulkPutBdbq(
			FeedBase feed, String parentUri, Map<String, String> tableNames, boolean async)
	throws IOException, TaggingException {
		return reflexContext.bulkPutBdbq(feed, parentUri, tableNames, async);
	}

}
