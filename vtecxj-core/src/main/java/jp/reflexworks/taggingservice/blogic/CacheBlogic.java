package jp.reflexworks.taggingservice.blogic;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.PermissionException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.CacheManager;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;

/**
 * キャッシュを扱うビジネスロジック
 */
public class CacheBlogic {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * キャッシュにFeedを登録.
	 * 既に値が登録されている場合は上書きする。
	 * @param uri 登録キー
	 * @param feed Feed
	 * @param sec 有効時間(秒)
	 * @param reflexContext ReflexContext
	 * @return 登録したFeed
	 */
	public FeedBase setFeed(String uri, FeedBase feed, Integer sec,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 引数チェック
		checkUri(uri);
		checkFeed(feed);
		checkExpireOrNull(sec);

		// ACLチェック
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAcl(uri, AclConst.ACL_TYPE_UPDATE, auth,
				requestInfo, connectionInfo);

		// 登録
		CacheManager cacheManager = TaggingEnvUtil.getCacheManager();
		return cacheManager.setFeed(uri, feed, sec, reflexContext);
	}

	/**
	 * キャッシュにEntryを登録.
	 * 既に値が登録されている場合は上書きする。
	 * @param uri 登録キー
	 * @param entry Entry
	 * @param sec 有効時間(秒)
	 * @param reflexContext ReflexContext
	 * @return 登録したFeed
	 */
	public EntryBase setEntry(String uri, EntryBase entry, Integer sec,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 引数チェック
		checkUri(uri);
		checkEntry(entry);
		checkExpireOrNull(sec);

		// ACLチェック
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAcl(uri, AclConst.ACL_TYPE_UPDATE, auth,
				requestInfo, connectionInfo);

		// 登録
		CacheManager cacheManager = TaggingEnvUtil.getCacheManager();
		return cacheManager.setEntry(uri, entry, sec, reflexContext);
	}

	/**
	 * キャッシュに文字列を登録.
	 * 既に値が登録されている場合は上書きする。
	 * @param uri 登録キー
	 * @param text 文字列
	 * @param sec 有効時間(秒)
	 * @param reflexContext ReflexContext
	 * @return 登録した文字列
	 */
	public String setString(String uri, String text, Integer sec,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 引数チェック
		checkUri(uri);
		checkString(text);
		checkExpireOrNull(sec);

		// ACLチェック
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAcl(uri, AclConst.ACL_TYPE_UPDATE, auth,
				requestInfo, connectionInfo);

		// 登録
		CacheManager cacheManager = TaggingEnvUtil.getCacheManager();
		return cacheManager.setString(uri, text, sec, reflexContext);
	}

	/**
	 * キャッシュに数値を登録.
	 * 既に値が登録されている場合は上書きする。
	 * @param uri 登録キー
	 * @param num 数値
	 * @param sec 有効時間(秒)
	 * @param reflexContext ReflexContext
	 * @return 登録した数値
	 */
	public long setLong(String uri, long num, Integer sec,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 引数チェック
		checkUri(uri);
		checkExpireOrNull(sec);

		// ACLチェック
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAcl(uri, AclConst.ACL_TYPE_UPDATE, auth,
				requestInfo, connectionInfo);

		// 登録
		CacheManager cacheManager = TaggingEnvUtil.getCacheManager();
		return cacheManager.setLong(uri, num, sec, reflexContext);
	}

	/**
	 * データが存在しない場合のみキャッシュにFeedを登録.
	 * @param uri 登録キー
	 * @param feed Feed
	 * @param reflexContext ReflexContext
	 * @return 登録できた場合true、失敗した場合false
	 */
	public boolean setFeedIfAbsent(String uri, FeedBase feed,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 引数チェック
		checkUri(uri);
		checkFeed(feed);

		// ACLチェック
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAcl(uri, AclConst.ACL_TYPE_CREATE, auth,
				requestInfo, connectionInfo);

		// 登録
		CacheManager cacheManager = TaggingEnvUtil.getCacheManager();
		return cacheManager.setFeedIfAbsent(uri, feed, reflexContext);
	}

	/**
	 * データが存在しない場合のみキャッシュにEntryを登録.
	 * @param uri 登録キー
	 * @param entry Entry
	 * @param reflexContext ReflexContext
	 * @return 登録できた場合true、失敗した場合false
	 */
	public boolean setEntryIfAbsent(String uri, EntryBase entry,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 引数チェック
		checkUri(uri);
		checkEntry(entry);

		// ACLチェック
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAcl(uri, AclConst.ACL_TYPE_CREATE, auth,
				requestInfo, connectionInfo);

		// 登録
		CacheManager cacheManager = TaggingEnvUtil.getCacheManager();
		return cacheManager.setEntryIfAbsent(uri, entry, reflexContext);
	}

	/**
	 * データが存在しない場合のみキャッシュに文字列を登録.
	 * @param uri 登録キー
	 * @param text 文字列
	 * @param reflexContext ReflexContext
	 * @return 登録できた場合true、失敗した場合false
	 */
	public boolean setStringIfAbsent(String uri, String text,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 引数チェック
		checkUri(uri);
		checkString(text);

		// ACLチェック
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAcl(uri, AclConst.ACL_TYPE_CREATE, auth,
				requestInfo, connectionInfo);

		// 登録
		CacheManager cacheManager = TaggingEnvUtil.getCacheManager();
		return cacheManager.setStringIfAbsent(uri, text, reflexContext);
	}

	/**
	 * データが存在しない場合のみキャッシュに数値を登録.
	 * @param uri 登録キー
	 * @param num 数値
	 * @param reflexContext ReflexContext
	 * @return 登録できた場合true、失敗した場合false
	 */
	public boolean setLongIfAbsent(String uri, long num,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 引数チェック
		checkUri(uri);

		// ACLチェック
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAcl(uri, AclConst.ACL_TYPE_CREATE, auth,
				requestInfo, connectionInfo);

		// 登録
		CacheManager cacheManager = TaggingEnvUtil.getCacheManager();
		return cacheManager.setLongIfAbsent(uri, num, reflexContext);
	}

	/**
	 * キャッシュからFeedを削除.
	 * @param uri 登録キー
	 * @param reflexContext ReflexContext
	 * @return 削除できた場合true、データが存在しない場合false
	 */
	public boolean deleteFeed(String uri, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 引数チェック
		checkUri(uri);

		// ACLチェック
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAcl(uri, AclConst.ACL_TYPE_DELETE, auth,
				requestInfo, connectionInfo);

		// 削除
		CacheManager cacheManager = TaggingEnvUtil.getCacheManager();
		return cacheManager.deleteFeed(uri, reflexContext);
	}

	/**
	 * キャッシュからEntryを削除.
	 * @param uri 登録キー
	 * @param reflexContext ReflexContext
	 * @return 削除できた場合true、データが存在しない場合false
	 */
	public boolean deleteEntry(String uri, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 引数チェック
		checkUri(uri);

		// ACLチェック
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAcl(uri, AclConst.ACL_TYPE_DELETE, auth,
				requestInfo, connectionInfo);

		// 削除
		CacheManager cacheManager = TaggingEnvUtil.getCacheManager();
		return cacheManager.deleteEntry(uri, reflexContext);
	}

	/**
	 * キャッシュから文字列を削除.
	 * @param uri 登録キー
	 * @param reflexContext ReflexContext
	 * @return 削除できた場合true、データが存在しない場合false
	 */
	public boolean deleteString(String uri, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 引数チェック
		checkUri(uri);

		// ACLチェック
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAcl(uri, AclConst.ACL_TYPE_DELETE, auth,
				requestInfo, connectionInfo);

		// 削除
		CacheManager cacheManager = TaggingEnvUtil.getCacheManager();
		return cacheManager.deleteString(uri, reflexContext);
	}

	/**
	 * キャッシュから整数値を削除.
	 * @param uri 登録キー
	 * @param reflexContext ReflexContext
	 * @return 削除できた場合true、データが存在しない場合false
	 */
	public boolean deleteLong(String uri, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 引数チェック
		checkUri(uri);

		// ACLチェック
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAcl(uri, AclConst.ACL_TYPE_DELETE, auth,
				requestInfo, connectionInfo);

		// 削除
		CacheManager cacheManager = TaggingEnvUtil.getCacheManager();
		return cacheManager.deleteLong(uri, reflexContext);
	}

	/**
	 * キャッシュからFeedを取得.
	 * @param uri 登録キー
	 * @param reflexContext ReflexContext
	 * @return Feed
	 */
	public FeedBase getFeed(String uri, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 引数チェック
		checkUri(uri);

		// ACLチェック
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAcl(uri, AclConst.ACL_TYPE_RETRIEVE, auth,
				requestInfo, connectionInfo);

		// 検索
		CacheManager cacheManager = TaggingEnvUtil.getCacheManager();
		return cacheManager.getFeed(uri, reflexContext);
	}

	/**
	 * キャッシュからEntryを取得.
	 * @param uri 登録キー
	 * @param reflexContext ReflexContext
	 * @return 登録したFeed
	 */
	public EntryBase getEntry(String uri, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 引数チェック
		checkUri(uri);

		// ACLチェック
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAcl(uri, AclConst.ACL_TYPE_RETRIEVE, auth,
				requestInfo, connectionInfo);

		// 検索
		CacheManager cacheManager = TaggingEnvUtil.getCacheManager();
		return cacheManager.getEntry(uri, reflexContext);
	}

	/**
	 * キャッシュから文字列を取得.
	 * @param uri 登録キー
	 * @param reflexContext ReflexContext
	 * @return 登録した文字列
	 */
	public String getString(String uri, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 引数チェック
		checkUri(uri);

		// ACLチェック
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAcl(uri, AclConst.ACL_TYPE_RETRIEVE, auth,
				requestInfo, connectionInfo);

		// 検索
		CacheManager cacheManager = TaggingEnvUtil.getCacheManager();
		return cacheManager.getString(uri, reflexContext);
	}

	/**
	 * キャッシュから整数値を取得.
	 * @param uri 登録キー
	 * @param reflexContext ReflexContext
	 * @return 登録した整数値
	 */
	public Long getLong(String uri, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 引数チェック
		checkUri(uri);

		// ACLチェック
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAcl(uri, AclConst.ACL_TYPE_RETRIEVE, auth,
				requestInfo, connectionInfo);

		// 検索
		CacheManager cacheManager = TaggingEnvUtil.getCacheManager();
		return cacheManager.getLong(uri, reflexContext);
	}

	/**
	 * Feedキャッシュの有効時間を設定.
	 * @param uri 登録キー
	 * @param sec 有効時間(秒)
	 * @param reflexContext ReflexContext
	 * @return 設定できた場合true、データが存在しない場合false
	 */
	public boolean setExpireFeed(String uri, int sec, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 引数チェック
		checkUri(uri);
		checkExpire(sec);

		// ACLチェック
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAcl(uri, AclConst.ACL_TYPE_UPDATE, auth,
				requestInfo, connectionInfo);

		// 有効期限設定
		CacheManager cacheManager = TaggingEnvUtil.getCacheManager();
		return cacheManager.setExpireFeed(uri, sec, reflexContext);
	}

	/**
	 * Entryキャッシュの有効時間を設定.
	 * @param uri 登録キー
	 * @param sec 有効時間(秒)
	 * @param reflexContext ReflexContext
	 * @return 設定できた場合true、データが存在しない場合false
	 */
	public boolean setExpireEntry(String uri, int sec, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 引数チェック
		checkUri(uri);
		checkExpire(sec);

		// ACLチェック
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAcl(uri, AclConst.ACL_TYPE_UPDATE, auth,
				requestInfo, connectionInfo);

		// 有効期限設定
		CacheManager cacheManager = TaggingEnvUtil.getCacheManager();
		return cacheManager.setExpireEntry(uri, sec, reflexContext);
	}

	/**
	 * 文字列キャッシュの有効時間を設定.
	 * @param uri 登録キー
	 * @param sec 有効時間(秒)
	 * @param reflexContext ReflexContext
	 * @return 設定できた場合true、データが存在しない場合false
	 */
	public boolean setExpireString(String uri, int sec, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 引数チェック
		checkUri(uri);
		checkExpire(sec);

		// ACLチェック
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAcl(uri, AclConst.ACL_TYPE_UPDATE, auth,
				requestInfo, connectionInfo);

		// 有効期限設定
		CacheManager cacheManager = TaggingEnvUtil.getCacheManager();
		return cacheManager.setExpireString(uri, sec, reflexContext);
	}

	/**
	 * 整数値キャッシュの有効時間を設定.
	 * @param uri 登録キー
	 * @param sec 有効時間(秒)
	 * @param reflexContext ReflexContext
	 * @return 設定できた場合true、データが存在しない場合false
	 */
	public boolean setExpireLong(String uri, int sec, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 引数チェック
		checkUri(uri);
		checkExpire(sec);

		// ACLチェック
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAcl(uri, AclConst.ACL_TYPE_UPDATE, auth,
				requestInfo, connectionInfo);

		// 有効期限設定
		CacheManager cacheManager = TaggingEnvUtil.getCacheManager();
		return cacheManager.setExpireLong(uri, sec, reflexContext);
	}

	/**
	 * キャッシュの整数値に値を加算.
	 * @param uri 登録キー
	 * @param num 加算値
	 * @param reflexContext ReflexContext
	 * @return 加算後の値
	 */
	public long increment(String uri, long num,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 引数チェック
		checkUri(uri);

		// ACLチェック
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAcl(uri, AclConst.ACL_TYPE_UPDATE, auth,
				requestInfo, connectionInfo);

		// インクリメント
		CacheManager cacheManager = TaggingEnvUtil.getCacheManager();
		return cacheManager.increment(uri, num, reflexContext);
	}

	/**
	 * Redisの全てのデータを削除.
	 * メンテナンス用
	 * @param reflexContext ReflexContext
	 * @return 削除できた場合true
	 */
	public boolean flushAll(ReflexContext reflexContext)
	throws IOException, TaggingException {
		// システム管理サービスでなければエラー
		String serviceName = reflexContext.getServiceName();
		if (!TaggingEnvUtil.getSystemService().equals(serviceName)) {
			throw new PermissionException();
		}
		// ACLチェック
		// システム管理サービスの管理者でなければエラー
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		AclBlogic aclBlogic = new AclBlogic();
		if (!aclBlogic.isInTheGroup(auth, Constants.URI_GROUP_ADMIN)) {
			throw new PermissionException();
		}

		// 全て削除
		if (logger.isWarnEnabled()) {
			logger.warn(LogUtil.getRequestInfoStr(requestInfo) + "[flushAll] start.");
		}
		CacheManager cacheManager = TaggingEnvUtil.getCacheManager();
		String msg = cacheManager.flushAll(reflexContext);
		if (logger.isWarnEnabled()) {
			logger.warn(LogUtil.getRequestInfoStr(requestInfo) + "[flushAll] end. msg = " + msg);
		}
		return true;
	}

	/**
	 * キーのフォーマットチェック.
	 * @param uri URI
	 */
	private void checkUri(String uri) {
		CheckUtil.checkUri(uri);
	}

	/**
	 * Feedチェック.
	 * nullでないかのみチェックする。
	 * @param feed Feed
	 */
	private void checkFeed(FeedBase feed) {
		CheckUtil.checkNotNull(feed, "Feed");
	}

	/**
	 * Entryチェック.
	 * nullでないかのみチェックする。
	 * @param entry Entry
	 */
	private void checkEntry(EntryBase entry) {
		CheckUtil.checkNotNull(entry, "Entry");
	}

	/**
	 * 文字列チェック.
	 * nullでないかのみチェックする。
	 * @param entry Entry
	 */
	private void checkString(String text) {
		CheckUtil.checkNotNull(text, "String");
	}

	/**
	 * 有効時間チェック.
	 * 正の数かどうかチェックする。
	 * 0は不可。
	 * @param sec 有効時間
	 */
	private void checkExpire(int sec) {
		CheckUtil.checkPositiveNumber(sec, "expire");
	}

	/**
	 * 有効時間チェック.
	 * 正の数かどうかチェックする。
	 * 0は指定なし(null)とみなす。
	 * @param sec 有効時間
	 */
	private void checkExpireOrNull(Integer sec) {
		if (sec != null && sec != 0) {
			CheckUtil.checkPositiveNumber(sec, "expire");
		}
	}

}
