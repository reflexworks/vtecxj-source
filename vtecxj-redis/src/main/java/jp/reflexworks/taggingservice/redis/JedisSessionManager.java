package jp.reflexworks.taggingservice.redis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.taggingservice.api.BaseReflexContext;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.SessionManager;
import jp.reflexworks.taggingservice.util.CookieUtil;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.sourceforge.reflex.util.NumberingUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * Redisセッション管理クラス.
 */
public class JedisSessionManager extends JedisCommonManager implements SessionManager {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 初期処理
	 */
	@Override
	public void init() {
		// 初期処理
		JedisUtil.init();
	}

	/**
	 * シャットダウン処理
	 */
	@Override
	public void close() {
		JedisUtil.close();
	}

	/**
	 * キャッシュにFeedを登録.
	 * 既にデータが存在する場合は上書き。
	 * @param sid SID
	 * @param name キー
	 * @param feed Feed
	 * @param reflexContext ReflexContext
	 * @return 登録したFeed
	 */
	public FeedBase setFeed(String sid, String name, FeedBase feed,
			BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		FeedTemplateMapper mapper = reflexContext.getResourceMapper();
		byte[] key1 = createSidKeyByte(sid, serviceName, namespace, requestInfo, connectionInfo);
		byte[] key2 = createFeedKey(name, serviceName, namespace, requestInfo, connectionInfo);
		byte[] val = serializeFeed(feed, mapper);
		hsetBytesProc(key1, key2, val, requestInfo, connectionInfo);
		return feed;
	}

	/**
	 * キャッシュにFeedを登録.
	 * 値が登録されていない場合のみ登録される。
	 * @param sid SID
	 * @param name キー
	 * @param feed Feed
	 * @param reflexContext ReflexContext
	 * @return true:成功、false:失敗
	 */
	public boolean setFeedIfAbsent(String sid, String name, FeedBase feed,
			BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		FeedTemplateMapper mapper = reflexContext.getResourceMapper();
		byte[] key1 = createSidKeyByte(sid, serviceName, namespace, requestInfo, connectionInfo);
		byte[] key2 = createFeedKey(name, serviceName, namespace, requestInfo, connectionInfo);
		byte[] val = serializeFeed(feed, mapper);
		Long ret = hsetnxBytesProc(key1, key2, val, requestInfo, connectionInfo);
		return ret != null && ret == 1;
	}

	/**
	 * キャッシュにEntryを登録.
	 * 既にデータが存在する場合は上書き。
	 * @param sid SID
	 * @param name キー
	 * @param entry Entry
	 * @param reflexContext ReflexContext
	 * @return 登録したEntry
	 */
	public EntryBase setEntry(String sid, String name, EntryBase entry, BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		FeedTemplateMapper mapper = reflexContext.getResourceMapper();
		byte[] key1 = createSidKeyByte(sid, serviceName, namespace, requestInfo, connectionInfo);
		byte[] key2 = createEntryKey(name, serviceName, namespace, requestInfo, connectionInfo);
		byte[] val = serializeEntry(entry, mapper);
		hsetBytesProc(key1, key2, val, requestInfo, connectionInfo);
		return entry;
	}

	/**
	 * キャッシュにEntryを登録.
	 * 値が登録されていない場合のみ登録される。
	 * @param sid SID
	 * @param name キー
	 * @param entry Entry
	 * @param reflexContext ReflexContext
	 * @return true:成功、false:失敗
	 */
	public boolean setEntryIfAbsent(String sid, String name, EntryBase entry, BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		FeedTemplateMapper mapper = reflexContext.getResourceMapper();
		byte[] key1 = createSidKeyByte(sid, serviceName, namespace, requestInfo, connectionInfo);
		byte[] key2 = createEntryKey(name, serviceName, namespace, requestInfo, connectionInfo);
		byte[] val = serializeEntry(entry, mapper);
		Long ret = hsetnxBytesProc(key1, key2, val, requestInfo, connectionInfo);
		return ret != null && ret == 1;
	}

	/**
	 * キャッシュに文字列を登録.
	 * 既にデータが存在する場合は上書き。
	 * @param sid SID
	 * @param name キー
	 * @param text 文字列
	 * @param reflexContext ReflexContext
	 * @return 登録した文字列
	 */
	public String setString(String sid, String name, String text,
			BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		String key1 = createSidKeyString(sid, serviceName, namespace, requestInfo, connectionInfo);
		String key2 = createStringKey(name, serviceName, namespace, requestInfo, connectionInfo);
		hsetStringProc(key1, key2, text, requestInfo, connectionInfo);
		return text;
	}

	/**
	 * キャッシュに文字列を登録.
	 * 値が登録されていない場合のみ登録される。
	 * @param sid SID
	 * @param name キー
	 * @param text 文字列
	 * @param reflexContext ReflexContext
	 * @return 成功の場合null、失敗の場合登録済みの値
	 */
	public String setStringIfAbsent(String sid, String name, String text,
			BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		String key1 = createSidKeyString(sid, serviceName, namespace, requestInfo, connectionInfo);
		String key2 = createStringKey(name, serviceName, namespace, requestInfo, connectionInfo);
		Long ret = hsetnxStringProc(key1, key2, text, requestInfo, connectionInfo);
		if (ret != null && ret == 1) {
			return null;
		} else {
			return hgetStringProc(key1, key2, requestInfo, connectionInfo);
		}
	}

	/**
	 * キャッシュに数値を登録.
	 * 既にデータが存在する場合は上書き。
	 * @param sid SID
	 * @param name キー
	 * @param num 数値
	 * @param reflexContext ReflexContext
	 * @return 登録した数値
	 */
	public long setLong(String sid, String name, long num,
			BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		String key1 = createSidKeyString(sid, serviceName, namespace, requestInfo, connectionInfo);
		String key2 = createLongKey(name, serviceName, namespace, requestInfo, connectionInfo);
		String text = convertLongToString(num);
		hsetStringProc(key1, key2, text, requestInfo, connectionInfo);
		return num;
	}

	/**
	 * セッションに数値を登録.
	 * 値が登録されていない場合のみ登録される。
	 * @param sid SID
	 * @param name キー
	 * @param num 数値
	 * @param reflexContext ReflexContext
	 * @return 成功の場合null、失敗の場合登録済みの値
	 */
	public Long setLongIfAbsent(String sid, String name, long num,
			BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		String key1 = createSidKeyString(sid, serviceName, namespace, requestInfo, connectionInfo);
		String key2 = createLongKey(name, serviceName, namespace, requestInfo, connectionInfo);
		String text = convertLongToString(num);
		Long ret = hsetnxStringProc(key1, key2, text, requestInfo, connectionInfo);
		if (ret != null && ret == 1) {
			return null;
		} else {
			String str = hgetStringProc(key1, key2, requestInfo, connectionInfo);
			return convertStringToLong(str);
		}
	}

	/**
	 * キャッシュの数値に値を加算.
	 * 既にデータが存在する場合は上書き。
	 * @param sid SID
	 * @param name キー
	 * @param num 数値
	 * @param reflexContext ReflexContext
	 * @return 加算後の数値
	 */
	public long increment(String sid, String name, long num,
			BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		String key1 = createSidKeyString(sid, serviceName, namespace, requestInfo, connectionInfo);
		String key2 = createLongKey(name, serviceName, namespace, requestInfo, connectionInfo);
		return hincrByProc(key1, key2, num, requestInfo, connectionInfo);
	}

	/**
	 * キャッシュからFeedを削除.
	 * @param sid SID
	 * @param name キー
	 * @param reflexContext ReflexContext
	 */
	public void deleteFeed(String sid, String name, BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		byte[] key1 = createSidKeyByte(sid, serviceName, namespace, requestInfo, connectionInfo);
		byte[] key2 = createFeedKey(name, serviceName, namespace, requestInfo, connectionInfo);
		hdelBytesProc(key1, key2, requestInfo, connectionInfo);
	}

	/**
	 * キャッシュからEntryを削除.
	 * @param sid SID
	 * @param name キー
	 * @param reflexContext ReflexContext
	 */
	public void deleteEntry(String sid, String name, BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		byte[] key1 = createSidKeyByte(sid, serviceName, namespace, requestInfo, connectionInfo);
		byte[] key2 = createEntryKey(name, serviceName, namespace, requestInfo, connectionInfo);
		hdelBytesProc(key1, key2, requestInfo, connectionInfo);
	}

	/**
	 * キャッシュから文字列を削除.
	 * @param sid SID
	 * @param name キー
	 * @param reflexContext ReflexContext
	 */
	public void deleteString(String sid, String name, BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		String key1 = createSidKeyString(sid, serviceName, namespace, requestInfo, connectionInfo);
		String key2 = createStringKey(name, serviceName, namespace, requestInfo, connectionInfo);
		hdelStringProc(key1, key2, requestInfo, connectionInfo);
	}

	/**
	 * キャッシュから文字列を削除.
	 * @param sid SID
	 * @param name キー
	 * @param reflexContext ReflexContext
	 */
	public void deleteLong(String sid, String name, BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		String key1 = createSidKeyString(sid, serviceName, namespace, requestInfo, connectionInfo);
		String key2 = createLongKey(name, serviceName, namespace, requestInfo, connectionInfo);
		hdelStringProc(key1, key2, requestInfo, connectionInfo);
	}

	/**
	 * キャッシュからFeedを取得.
	 * @param sid SID
	 * @param name キー
	 * @param reflexContext ReflexContext
	 * @return Feed
	 */
	public FeedBase getFeed(String sid, String name, BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		FeedTemplateMapper mapper = reflexContext.getResourceMapper();
		byte[] key1 = createSidKeyByte(sid, serviceName, namespace, requestInfo, connectionInfo);
		byte[] key2 = createFeedKey(name, serviceName, namespace, requestInfo, connectionInfo);
		byte[] data = hgetBytesProc(key1, key2, requestInfo, connectionInfo);
		if (data == null) {
			return null;
		}
		return deserializeFeed(data, mapper);
	}

	/**
	 * キャッシュからEntryを取得.
	 * @param sid SID
	 * @param name キー
	 * @param reflexContext ReflexContext
	 * @return Entry
	 */
	public EntryBase getEntry(String sid, String name, BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		FeedTemplateMapper mapper = reflexContext.getResourceMapper();
		byte[] key1 = createSidKeyByte(sid, serviceName, namespace, requestInfo, connectionInfo);
		byte[] key2 = createEntryKey(name, serviceName, namespace, requestInfo, connectionInfo);
		byte[] data = hgetBytesProc(key1, key2, requestInfo, connectionInfo);
		return deserializeEntry(data, mapper);
	}

	/**
	 * キャッシュから文字列を取得.
	 * @param sid SID
	 * @param name キー
	 * @param reflexContext ReflexContext
	 */
	public String getString(String sid, String name, BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		String key1 = createSidKeyString(sid, serviceName, namespace, requestInfo, connectionInfo);
		String key2 = createStringKey(name, serviceName, namespace, requestInfo, connectionInfo);
		return hgetStringProc(key1, key2, requestInfo, connectionInfo);
	}

	/**
	 * キャッシュから数値を取得.
	 * @param sid SID
	 * @param name キー
	 * @param reflexContext ReflexContext
	 */
	public Long getLong(String sid, String name, BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		String key1 = createSidKeyString(sid, serviceName, namespace, requestInfo, connectionInfo);
		String key2 = createLongKey(name, serviceName, namespace, requestInfo, connectionInfo);
		String str = hgetStringProc(key1, key2, requestInfo, connectionInfo);
		return convertStringToLong(str);
	}

	/**
	 * セッションへのFeed格納キー一覧を取得.
	 * @param sid SID
	 * @param reflexContext ReflexContext
	 * @return セッションへのFeed格納キーリスト
	 */
	public List<String> getFeedKeys(String sid, BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		Map<String, List<String>> keys = getKeys(sid, reflexContext);
		if (keys != null && keys.containsKey(JedisConst.FEED)) {
			return keys.get(JedisConst.FEED);
		}
		return null;
	}

	/**
	 * セッションへのEntry格納キー一覧を取得.
	 * @param sid SID
	 * @param reflexContext ReflexContext
	 * @return セッションへのEntry格納キーリスト
	 */
	public List<String> getEntryKeys(String sid, BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		Map<String, List<String>> keys = getKeys(sid, reflexContext);
		if (keys != null && keys.containsKey(JedisConst.ENTRY)) {
			return keys.get(JedisConst.ENTRY);
		}
		return null;
	}

	/**
	 * セッションへの文字列格納キー一覧を取得.
	 * @param sid SID
	 * @param reflexContext ReflexContext
	 * @return セッションへの文字列格納キーリスト
	 */
	public List<String> getStringKeys(String sid, BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		Map<String, List<String>> keys = getKeys(sid, reflexContext);
		if (keys != null && keys.containsKey(JedisConst.STRING)) {
			return keys.get(JedisConst.STRING);
		}
		return null;
	}

	/**
	 * セッションへの数値格納キー一覧を取得.
	 * @param sid SID
	 * @param reflexContext ReflexContext
	 * @return セッションへの数値格納キーリスト
	 */
	public List<String> getLongKeys(String sid, BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		Map<String, List<String>> keys = getKeys(sid, reflexContext);
		if (keys != null && keys.containsKey(JedisConst.LONG)) {
			return keys.get(JedisConst.LONG);
		}
		return null;
	}

	/**
	 * セッションへの格納キー一覧を取得.
	 * @param sid SID
	 * @param reflexContext ReflexContext
	 * @return セッションへの値格納キーリスト。
	 *         キー: feed, entry, string, longのいずれか
	 *         値: キーリスト
	 */
	public Map<String, List<String>> getKeys(String sid,
			BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		String key1 = createSidKeyString(sid, serviceName, namespace, requestInfo, connectionInfo);
		Set<String> keys = hkeysProc(key1, reflexContext.getRequestInfo(),
				reflexContext.getConnectionInfo());
		if (keys == null || keys.isEmpty()) {
			return null;
		}

		// Feed、Entry、String、Longのキープリフィックスを取得
		String feedKeyPrefix = createFeedKeyPrefix(serviceName, namespace, requestInfo, connectionInfo);
		String entryKeyPrefix = createEntryKeyPrefix(serviceName, namespace, requestInfo, connectionInfo);
		String stringKeyPrefix = createStringKeyPrefix(serviceName, namespace, requestInfo, connectionInfo);
		String longKeyPrefix = createLongKeyPrefix(serviceName, namespace, requestInfo, connectionInfo);
		int feedKeyPrefixLen = feedKeyPrefix.length();
		int entryKeyPrefixLen = entryKeyPrefix.length();
		int stringKeyPrefixLen = stringKeyPrefix.length();
		int longKeyPrefixLen = longKeyPrefix.length();

		List<String> feedKeys = new ArrayList<String>();
		List<String> entryKeys = new ArrayList<String>();
		List<String> stringKeys = new ArrayList<String>();
		List<String> longKeys = new ArrayList<String>();

		// キーの型による仕分け
		for (String key : keys) {
			if (key.startsWith(feedKeyPrefix)) {
				feedKeys.add(key.substring(feedKeyPrefixLen));
			} else if (key.startsWith(entryKeyPrefix)) {
				entryKeys.add(key.substring(entryKeyPrefixLen));
			} else if (key.startsWith(stringKeyPrefix)) {
				stringKeys.add(key.substring(stringKeyPrefixLen));
			} else if (key.startsWith(longKeyPrefix)) {
				longKeys.add(key.substring(longKeyPrefixLen));
			}
		}

		Map<String, List<String>> retKeys = new HashMap<String, List<String>>();
		if (!feedKeys.isEmpty()) {
			retKeys.put(JedisConst.FEED, feedKeys);
		}
		if (!entryKeys.isEmpty()) {
			retKeys.put(JedisConst.ENTRY, entryKeys);
		}
		if (!stringKeys.isEmpty()) {
			retKeys.put(JedisConst.STRING, stringKeys);
		}
		if (!longKeys.isEmpty()) {
			retKeys.put(JedisConst.LONG, longKeys);
		}
		if (retKeys.isEmpty()) {
			return null;
		}
		return retKeys;
	}

	/**
	 * SIDを発行し、認証情報を登録 (セッションを生成).
	 * @param auth 認証情報
	 * @param expire 有効期間(秒)
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return SID
	 */
	public String createSession(ReflexAuthentication auth, int expire,
			String serviceName, String namespace, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException {
		// SID重複リトライ回数
		int sidNumRetries = JedisUtil.getSidRetryCount();
		// SIDの長さ
		int sidLen = JedisUtil.getSidLength();

		String uid = auth.getUid();
		if (StringUtils.isBlank(uid)) {
			if (logger.isDebugEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[createSession] uid is null. uid=");
				sb.append(uid);
				logger.debug(sb.toString());
			}
			return null;
		}

		String keyUid = getAuthUidKey();

		for (int s = 0; s <= sidNumRetries; s++) {
			String sid = NumberingUtil.randomString(sidLen);
			String key1 = createSidKeyString(sid, serviceName, namespace, requestInfo, connectionInfo);

			// UID登録
			Long retUid = hsetnxStringProc(key1, keyUid, uid, requestInfo,
					connectionInfo);
			if (retUid != null && retUid == 1) {
				// 先にexpire設定
				setExpireProc(key1, expire, requestInfo, connectionInfo);
				// SID発行成功。SIDを返却。
				if (logger.isDebugEnabled()) {
					StringBuilder sb = new StringBuilder();
					sb.append(LogUtil.getRequestInfoStr(requestInfo));
					sb.append("[createSession] uid=");
					sb.append(uid);
					sb.append(", sid=");
					sb.append(sid);
					logger.debug(sb.toString());
				}
				return sid;
			}
			if (logger.isDebugEnabled()) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[createSession] SID duplicated.");
			}
		}

		// SID発行に失敗
		throw new IOException("Number of retries exceeded in SID generation");
	}

	/**
	 * セッションを削除
	 * @param sid SID
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void deleteSession(String sid, String serviceName, String namespace,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		String key = createSidKeyString(sid, serviceName, namespace, requestInfo, connectionInfo);
		deleteStringProc(key, requestInfo, connectionInfo);
	}

	/**
	 * リクエストからSIDを取得.
	 * Cookieのキー"SID"の値を返却します。
	 * @param req リクエスト
	 * @return SID
	 */
	public String getSessionId(ReflexRequest req) {
		if (req != null) {
			return CookieUtil.getCookieValue(req, ReflexServletConst.COOKIE_SID);
		}
		return null;
	}

	/**
	 * セッションからUIDを取得.
	 * @param sid SID
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return UID
	 */
	public String getUidBySession(String sid, String serviceName, String namespace,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		String key1 = createSidKeyString(sid, serviceName, namespace, requestInfo, connectionInfo);
		String keyUid = getAuthUidKey();
		return hgetStringProc(key1, keyUid, requestInfo, connectionInfo);
	}

	/**
	 * セッションの有効期限を指定
	 * @param sid SID
	 * @param expire 有効期間(秒)
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 正常に設定された場合true。データ存在なしの場合false
	 */
	public boolean setExpire(String sid, int expire, String serviceName, String namespace,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		String key = createSidKeyString(sid, serviceName, namespace, requestInfo, connectionInfo);
		return setExpireProc(key, expire, requestInfo, connectionInfo);
	}

	/**
	 * SIDキーを取得
	 * @param sid SID
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return SIDキー
	 */
	private byte[] createSidKeyByte(String sid, String serviceName, String namespace,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		String tmp = createSidKeyString(sid, serviceName, namespace,
				requestInfo, connectionInfo);
		return JedisUtil.getBytes(tmp);
	}

	/**
	 * SIDキーを取得
	 * @param sid SID
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return SIDキー
	 */
	private String createSidKeyString(String sid, String serviceName, String namespace,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append(getKeyPrefix(serviceName, namespace, requestInfo, connectionInfo));
		sb.append(JedisConst.TBL_SESSION);
		sb.append(sid);
		return sb.toString();
	}

	/**
	 * セッション第ニキー(Feed)を取得
	 * @param name キー
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return セッション第ニキー(Feed)
	 */
	private byte[] createFeedKey(String name, String serviceName, String namespace,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append(createFeedKeyPrefix(serviceName, namespace, requestInfo, connectionInfo));
		sb.append(name);
		return JedisUtil.getBytes(sb.toString());
	}

	/**
	 * セッション第二キー(Feed)の接頭辞を取得
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return セッション第二キー(Feed)の接頭辞
	 */
	private String createFeedKeyPrefix(String serviceName, String namespace,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append(getKeyPrefix(serviceName, namespace, requestInfo, connectionInfo));
		sb.append(JedisConst.TBL_SESSION_FEED);
		return sb.toString();
	}

	/**
	 * セッション第ニキー(Entry)を取得
	 * @param name キー
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return セッション第ニキー(Entry)
	 */
	private byte[] createEntryKey(String name, String serviceName, String namespace,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append(createEntryKeyPrefix(serviceName, namespace, requestInfo, connectionInfo));
		sb.append(name);
		return JedisUtil.getBytes(sb.toString());
	}

	/**
	 * セッション第二キー(Entry)の接頭辞を取得
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return セッション第二キー(Entry)の接頭辞
	 */
	private String createEntryKeyPrefix(String serviceName, String namespace,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append(getKeyPrefix(serviceName, namespace, requestInfo, connectionInfo));
		sb.append(JedisConst.TBL_SESSION_ENTRY);
		return sb.toString();
	}

	/**
	 * セッション第ニキー(文字列)を取得
	 * @param name キー
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return セッション第ニキー(文字列)
	 */
	private String createStringKey(String name, String serviceName, String namespace,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append(createStringKeyPrefix(serviceName, namespace, requestInfo, connectionInfo));
		sb.append(name);
		return sb.toString();
	}

	/**
	 * セッション第二キー(文字列)の接頭辞を取得
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return セッション第二キー(文字列)の接頭辞
	 */
	private String createStringKeyPrefix(String serviceName, String namespace,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append(getKeyPrefix(serviceName, namespace, requestInfo, connectionInfo));
		sb.append(JedisConst.TBL_SESSION_TEXT);
		return sb.toString();
	}

	/**
	 * セッション第ニキー(数値)を取得
	 * @param name キー
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return セッション第ニキー(数値)
	 */
	private String createLongKey(String name, String serviceName, String namespace,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append(createLongKeyPrefix(serviceName, namespace, requestInfo, connectionInfo));
		sb.append(name);
		return sb.toString();
	}

	/**
	 * セッション第二キー(数値)の接頭辞を取得
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return セッション第二キー(数値)の接頭辞
	 */
	private String createLongKeyPrefix(String serviceName, String namespace,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append(getKeyPrefix(serviceName, namespace, requestInfo, connectionInfo));
		sb.append(JedisConst.TBL_SESSION_LONG);
		return sb.toString();
	}

	/**
	 * セッション第ニキー(UID)を取得
	 * @return セッション第ニキー(UID)
	 */
	private String getAuthUidKey() {
		return JedisConst.TBL_SESSION_UID;
	}

}
