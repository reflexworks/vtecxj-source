package jp.reflexworks.taggingservice.redis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.taggingservice.api.BaseReflexContext;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.CacheManager;

/**
 * Redis管理クラス.
 */
public class JedisCacheManager extends JedisCommonManager
implements CacheManager {

	/**
	 * 初期処理.
	 */
	@Override
	public void init() {
		// 初期処理
		JedisUtil.init();
	}

	/**
	 * シャットダウン時の処理.
	 */
	@Override
	public void close() {
		JedisUtil.close();
	}

	/**
	 * キャッシュにFeedを登録.
	 * 既にデータが存在する場合は上書き。
	 * @param name キー
	 * @param feed Feed
	 * @param sec 有効時間(秒)
	 * @param reflexContext ReflexContext
	 * @return 登録したFeed
	 */
	public FeedBase setFeed(String name, FeedBase feed, Integer sec,
			BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		FeedTemplateMapper mapper = reflexContext.getResourceMapper();
		byte[] key = createFeedKey(name, serviceName, namespace, requestInfo, connectionInfo);
		byte[] val = serializeFeed(feed, mapper);
		setBytesProc(key, val, sec, requestInfo, connectionInfo);
		return feed;
	}

	/**
	 * キャッシュにEntryを登録.
	 * 既にデータが存在する場合は上書き。
	 * @param name キー
	 * @param entry Entry
	 * @param sec 有効時間(秒)
	 * @param reflexContext ReflexContext
	 * @return 登録したEntry
	 */
	public EntryBase setEntry(String name, EntryBase entry, Integer sec,
			BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		FeedTemplateMapper mapper = reflexContext.getResourceMapper();
		byte[] key = createEntryKey(name, serviceName, namespace, requestInfo, connectionInfo);
		byte[] val = serializeEntry(entry, mapper);
		setBytesProc(key, val, sec, requestInfo, connectionInfo);
		return entry;
	}

	/**
	 * キャッシュに文字列を登録.
	 * 既にデータが存在する場合は上書き。
	 * @param name キー
	 * @param text 文字列
	 * @param sec 有効時間(秒)
	 * @param reflexContext ReflexContext
	 * @return 登録した文字列
	 */
	public String setString(String name, String text, Integer sec,
			BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		String key = createStringKey(name, serviceName, namespace, requestInfo, connectionInfo);
		setStringProc(key, text, sec, requestInfo, connectionInfo);
		return text;
	}

	/**
	 * キャッシュに整数値を設定.
	 * @param name キー
	 * @param val 設定値
	 * @param sec 有効時間(秒)
	 * @param reflexContext ReflexContext
	 * @return 設定した整数値
	 */
	public long setLong(String name, long val, Integer sec, BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		String key = createLongKey(name, serviceName, namespace, requestInfo, connectionInfo);
		String text = convertLongToString(val);
		setStringProc(key, text, sec, requestInfo, connectionInfo);
		return val;
	}

	/**
	 * データが存在しない場合のみキャッシュにFeedを登録.
	 * @param name キー
	 * @param feed Feed
	 * @param reflexContext ReflexContext
	 * @return 登録できた場合true、すでにデータが存在する場合false
	 */
	public boolean setFeedIfAbsent(String name, FeedBase feed,
			BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		FeedTemplateMapper mapper = reflexContext.getResourceMapper();
		byte[] key = createFeedKey(name, serviceName, namespace, requestInfo, connectionInfo);
		byte[] val = serializeFeed(feed, mapper);
		return setBytesIfAbsentProc(key, val, requestInfo, connectionInfo);
	}

	/**
	 * データが存在しない場合のみキャッシュにEntryを登録.
	 * @param name キー
	 * @param entry Entry
	 * @param reflexContext ReflexContext
	 * @return 登録できた場合true、すでにデータが存在する場合false
	 */
	public boolean setEntryIfAbsent(String name, EntryBase entry,
			BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		// リトライ回数
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		FeedTemplateMapper mapper = reflexContext.getResourceMapper();
		byte[] key = createEntryKey(name, serviceName, namespace, requestInfo, connectionInfo);
		byte[] val = serializeEntry(entry, mapper);
		return setBytesIfAbsentProc(key, val, requestInfo, connectionInfo);
	}

	/**
	 * データが存在しない場合のみキャッシュに文字列を登録.
	 * @param name キー
	 * @param text 文字列
	 * @param reflexContext ReflexContext
	 * @return 登録できた場合true、すでにデータが存在する場合false
	 */
	public boolean setStringIfAbsent(String name, String text,
			BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		String key = createStringKey(name, serviceName, namespace, requestInfo, connectionInfo);
		return setStringIfAbsentProc(key, text, requestInfo, connectionInfo);
	}

	/**
	 * データが存在しない場合のみキャッシュに整数値を登録.
	 * @param name キー
	 * @param val 整数値
	 * @param reflexContext ReflexContext
	 * @return 登録できた場合true、すでにデータが存在する場合false
	 */
	public boolean setLongIfAbsent(String name, long val,
			BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		String key = createLongKey(name, serviceName, namespace, requestInfo, connectionInfo);
		String text = convertLongToString(val);
		return setStringIfAbsentProc(key, text, requestInfo, connectionInfo);
	}

	/**
	 * キャッシュに加算.
	 * @param name キー
	 * @param val 加算値
	 * @param reflexContext ReflexContext
	 */
	public long increment(String name, long val, BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		String key = createLongKey(name, serviceName, namespace, requestInfo, connectionInfo);
		return incrementProc(key, val, requestInfo, connectionInfo);
	}

	/**
	 * キャッシュからFeedを削除.
	 * @param name キー
	 * @param reflexContext ReflexContext
	 * @return 正常に削除の場合true、データ存在なしの場合false
	 */
	public boolean deleteFeed(String name, BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		byte[] key = createFeedKey(name, serviceName, namespace, requestInfo, connectionInfo);
		return deleteBytesProc(key, requestInfo, connectionInfo);
	}

	/**
	 * キャッシュからEntryを削除.
	 * @param name キー
	 * @param reflexContext ReflexContext
	 * @return 正常に削除の場合true、データ存在なしの場合false
	 */
	public boolean deleteEntry(String name, BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		byte[] key = createEntryKey(name, serviceName, namespace, requestInfo, connectionInfo);
		return deleteBytesProc(key, requestInfo, connectionInfo);
	}

	/**
	 * キャッシュから文字列を削除.
	 * @param name キー
	 * @param reflexContext ReflexContext
	 * @return 正常に削除の場合true、データ存在なしの場合false
	 */
	public boolean deleteString(String name, BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		String key = createStringKey(name, serviceName, namespace, requestInfo, connectionInfo);
		return deleteStringProc(key, requestInfo, connectionInfo);
	}

	/**
	 * キャッシュから整数を削除.
	 * @param name キー
	 * @param reflexContext ReflexContext
	 * @return 正常に削除の場合true、データ存在なしの場合false
	 */
	public boolean deleteLong(String name, BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		String key = createLongKey(name, serviceName, namespace, requestInfo, connectionInfo);
		return deleteStringProc(key, requestInfo, connectionInfo);
	}

	/**
	 * キャッシュからFeedを取得.
	 * @param name キー
	 * @param reflexContext ReflexContext
	 * @return Feed
	 */
	public FeedBase getFeed(String name, BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		FeedTemplateMapper mapper = reflexContext.getResourceMapper();
		byte[] key = createFeedKey(name, serviceName, namespace, requestInfo, connectionInfo);
		byte[] data = getBytesProc(key, requestInfo, connectionInfo);
		if (data == null) {
			return null;
		}
		return deserializeFeed(data, mapper);
	}

	/**
	 * キャッシュからEntryを取得.
	 * @param name キー
	 * @param reflexContext ReflexContext
	 * @return Entry
	 */
	public EntryBase getEntry(String name, BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		FeedTemplateMapper mapper = reflexContext.getResourceMapper();
		byte[] key = createEntryKey(name, serviceName, namespace, requestInfo, connectionInfo);
		byte[] data = getBytesProc(key, requestInfo, connectionInfo);
		return deserializeEntry(data, mapper);
	}

	/**
	 * キャッシュから文字列を取得.
	 * @param name キー
	 * @param reflexContext ReflexContext
	 */
	public String getString(String name, BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		String key = createStringKey(name, serviceName, namespace, requestInfo, connectionInfo);
		return getStringProc(key, requestInfo, connectionInfo);
	}

	/**
	 * キャッシュから整数値を取得.
	 * @param name キー
	 * @param reflexContext ReflexContext
	 * @return 整数値
	 */
	public Long getLong(String name, BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		String key = createLongKey(name, serviceName, namespace, requestInfo, connectionInfo);
		String val = getStringProc(key, requestInfo, connectionInfo);
		return convertStringToLong(val);
	}

	/**
	 * Feedキャッシュの有効期限を指定
	 * @param name キー
	 * @param expire 有効期間(秒)
	 * @return 正常に設定の場合true、データ存在なしの場合false
	 */
	public boolean setExpireFeed(String name, int expire, BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		byte[] key = createFeedKey(name, serviceName, namespace, requestInfo, connectionInfo);
		return setExpireProc(key, expire, requestInfo, connectionInfo);
	}

	/**
	 * Entryキャッシュの有効期限を指定
	 * @param name キー
	 * @param expire 有効期間(秒)
	 * @return 正常に設定の場合true、データ存在なしの場合false
	 */
	public boolean setExpireEntry(String name, int expire, BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		byte[] key = createEntryKey(name, serviceName, namespace, requestInfo, connectionInfo);
		return setExpireProc(key, expire, requestInfo, connectionInfo);
	}

	/**
	 * 文字列キャッシュの有効期限を指定
	 * @param name キー
	 * @param expire 有効期間(秒)
	 * @return 正常に設定の場合true、データ存在なしの場合false
	 */
	public boolean setExpireString(String name, int expire, BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		String key = createStringKey(name, serviceName, namespace, requestInfo, connectionInfo);
		return setExpireProc(key, expire, requestInfo, connectionInfo);
	}

	/**
	 * 整数値キャッシュの有効期限を指定
	 * @param name キー
	 * @param expire 有効期間(秒)
	 * @return 正常に設定の場合true、データ存在なしの場合false
	 */
	public boolean setExpireLong(String name, int expire, BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String namespace = reflexContext.getNamespace();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		String key = createLongKey(name, serviceName, namespace, requestInfo, connectionInfo);
		return setExpireProc(key, expire, requestInfo, connectionInfo);
	}

	/**
	 * Feedキャッシュのキー一覧を取得.
	 * @param pattern パターン
	 * @param reflexContext ReflexContext
	 * @return Feedキャッシュのキー一覧
	 */
	public List<String> keysFeed(String pattern, BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String type = JedisConst.TBL_CACHE_FEED;
		return keys(pattern, type, reflexContext);
	}

	/**
	 * Entryキャッシュのキー一覧を取得.
	 * @param pattern パターン
	 * @param reflexContext ReflexContext
	 * @return Entryキャッシュのキー一覧
	 */
	public List<String> keysEntry(String pattern, BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String type = JedisConst.TBL_CACHE_ENTRY;
		return keys(pattern, type, reflexContext);
	}

	/**
	 * 文字列キャッシュのキー一覧を取得.
	 * @param pattern パターン
	 * @param reflexContext ReflexContext
	 * @return 文字列キャッシュのキー一覧
	 */
	public List<String> keysString(String pattern, BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String type = JedisConst.TBL_CACHE_TEXT;
		return keys(pattern, type, reflexContext);
	}

	/**
	 * 数値キャッシュのキー一覧を取得.
	 * @param pattern パターン
	 * @param reflexContext ReflexContext
	 * @return 数値キャッシュのキー一覧
	 */
	public List<String> keysLong(String pattern, BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String type = JedisConst.TBL_CACHE_LONG;
		return keys(pattern, type, reflexContext);
	}

	/**
	 * キャッシュのキー一覧を取得.
	 * @param pattern パターン
	 * @param type CF(Feed)、CE(Entry)、CT(String)、CL(Long)のいずれか
	 * @param reflexContext ReflexContext
	 * @return キャッシュのキー一覧。順不同。(Redisでkeysコマンドを実行すると順不同のため。)
	 */
	private List<String> keys(String pattern, String type, BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		String namespace = reflexContext.getNamespace();
		String editPattern = editKeysPattern(pattern, type, serviceName,
				namespace, requestInfo, connectionInfo);
		Set<String> keys = keys(editPattern, requestInfo, connectionInfo);
		List<String> retKeys = new ArrayList<String>();
		if (keys != null) {
			for (String key : keys) {
				retKeys.add(editReturnKey(key, type, serviceName,
						namespace, requestInfo, connectionInfo));
			}
		}
		if (retKeys.isEmpty()) {
			return null;
		}
		return retKeys;
	}

	/**
	 * Redisの全てのデータを削除.
	 * メンテナンス用
	 * @param reflexContext ReflexContext
	 * @return 実行結果
	 */
	public String flushAll(BaseReflexContext reflexContext)
	throws IOException, TaggingException {
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		String ret = flushAll(requestInfo, connectionInfo);
		return ret;
	}

	/**
	 * Feedキャッシュキーを取得
	 * @param name キー
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return Feedキャッシュキー
	 */
	private byte[] createFeedKey(String name, String serviceName, String namespace,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append(getKeyPrefix(serviceName, namespace, requestInfo, connectionInfo));
		sb.append(JedisConst.TBL_CACHE_FEED);
		sb.append(name);
		return JedisUtil.getBytes(sb.toString());
	}

	/**
	 * Entryキャッシュキーを取得
	 * @param name キー
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return Entryキャッシュキー
	 */
	private byte[] createEntryKey(String name, String serviceName, String namespace,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append(getKeyPrefix(serviceName, namespace, requestInfo, connectionInfo));
		sb.append(JedisConst.TBL_CACHE_ENTRY);
		sb.append(name);
		return JedisUtil.getBytes(sb.toString());
	}

	/**
	 * 文字列キャッシュキーを取得
	 * @param name キー
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 文字列キャッシュキー
	 */
	private String createStringKey(String name, String serviceName, String namespace,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append(getKeyPrefix(serviceName, namespace, requestInfo, connectionInfo));
		sb.append(JedisConst.TBL_CACHE_TEXT);
		sb.append(name);
		return sb.toString();
	}

	/**
	 * 整数値型キャッシュキーを取得
	 * @param name キー
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 整数キャッシュキー
	 */
	private String createLongKey(String name, String serviceName, String namespace,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append(getKeyPrefix(serviceName, namespace, requestInfo, connectionInfo));
		sb.append(JedisConst.TBL_CACHE_LONG);
		sb.append(name);
		return sb.toString();
	}

	/**
	 * キーリスト取得のパターンを編集
	 * @param pattern パターン
	 * @param type CF(Feed)、CE(Entry)、CT(String)、CL(Long)のいずれか
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return キーリスト取得のパターン
	 */
	private String editKeysPattern(String pattern, String type, String serviceName,
			String namespace, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append(getKeyPrefix(serviceName, namespace, requestInfo, connectionInfo));
		sb.append(type);
		sb.append(pattern);
		return sb.toString();
	}

	/**
	 * Redisのキーからテーブル名、サービス名情報を除去する。
	 * @param key Redisのキー
	 * @param type CF(Feed)、CE(Entry)、CT(String)、CL(Long)のいずれか
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return ユーザが使用する形式のキー
	 */
	private String editReturnKey(String key, String type, String serviceName,
			String namespace, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		String prefix = getKeyPrefix(serviceName, namespace, requestInfo, connectionInfo);
		int prefixLen = prefix.length() + type.length();
		return key.substring(prefixLen);
	}

}
