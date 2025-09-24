package jp.reflexworks.taggingservice.plugin;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.BaseReflexContext;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;

/**
 * セッション管理インタフェース.
 * このクラスを実装するクラスは起動時に1つのインスタンスが生成され、staticに保持されます。
 */
public interface SessionManager extends ReflexPlugin {

	/**
	 * セッションにFeedを登録.
	 * 既にデータが存在する場合は上書き。
	 * @param sid SID
	 * @param name キー
	 * @param feed Feed
	 * @param reflexContext ReflexContext
	 * @return 登録したFeed
	 */
	public FeedBase setFeed(String sid, String name, FeedBase feed,
			BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * セッションにEntryを登録.
	 * 既にデータが存在する場合は上書き。
	 * @param sid SID
	 * @param name キー
	 * @param entry Entry
	 * @param reflexContext ReflexContext
	 * @return 登録したEntry
	 */
	public EntryBase setEntry(String sid, String name, EntryBase entry, BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * セッションに文字列を登録.
	 * 既にデータが存在する場合は上書き。
	 * @param sid SID
	 * @param name キー
	 * @param text 文字列
	 * @param reflexContext ReflexContext
	 * @return 登録した文字列
	 */
	public String setString(String sid, String name, String text, BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * セッションに文字列を登録.
	 * 値が登録されていない場合のみ登録される。
	 * @param sid SID
	 * @param name キー
	 * @param text 文字列
	 * @param reflexContext ReflexContext
	 * @return 成功の場合null、失敗の場合登録済みの値
	 */
	public String setStringIfAbsent(String sid, String name, String text, BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * セッションに数値を登録.
	 * 既にデータが存在する場合は上書き。
	 * @param sid SID
	 * @param name キー
	 * @param num 数値
	 * @param reflexContext ReflexContext
	 * @return 登録した数値
	 */
	public long setLong(String sid, String name, long num, BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * セッションに数値を登録.
	 * 値が登録されていない場合のみ登録される。
	 * @param sid SID
	 * @param name キー
	 * @param num 数値
	 * @param reflexContext ReflexContext
	 * @return 成功の場合null、失敗の場合登録済みの値
	 */
	public Long setLongIfAbsent(String sid, String name, long num, BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * セッションの指定された数値を加算.
	 * @param sid SID
	 * @param name キー
	 * @param num 数値
	 * @param reflexContext ReflexContext
	 * @return 加算後の数値
	 */
	public long increment(String sid, String name, long num, BaseReflexContext reflexContext)
			throws IOException, TaggingException;

	/**
	 * セッションからFeedを削除.
	 * @param sid SID
	 * @param name キー
	 * @param reflexContext ReflexContext
	 */
	public void deleteFeed(String sid, String name, BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * セッションからEntryを削除.
	 * @param sid SID
	 * @param name キー
	 * @param reflexContext ReflexContext
	 */
	public void deleteEntry(String sid, String name, BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * セッションから文字列を削除.
	 * @param sid SID
	 * @param name キー
	 * @param reflexContext ReflexContext
	 */
	public void deleteString(String sid, String name, BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * セッションから数値を削除.
	 * @param sid SID
	 * @param name キー
	 * @param reflexContext ReflexContext
	 */
	public void deleteLong(String sid, String name, BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * セッションからFeedを取得.
	 * @param sid SID
	 * @param name キー
	 * @param reflexContext ReflexContext
	 * @return Feed
	 */
	public FeedBase getFeed(String sid, String name, BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * セッションからEntryを取得.
	 * @param sid SID
	 * @param name キー
	 * @param reflexContext ReflexContext
	 * @return Entry
	 */
	public EntryBase getEntry(String sid, String name, BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * セッションから文字列を取得.
	 * @param sid SID
	 * @param name キー
	 * @param reflexContext ReflexContext
	 * @return セッションに登録された文字列。登録がない場合はnull。
	 */
	public String getString(String sid, String name, BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * セッションから数値を取得.
	 * @param sid SID
	 * @param name キー
	 * @param reflexContext ReflexContext
	 * @return セッションに登録された数値。登録がない場合はnull。
	 */
	public Long getLong(String sid, String name, BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * セッションへのFeed格納キー一覧を取得.
	 * @param sid SID
	 * @param reflexContext ReflexContext
	 * @return セッションへのFeed格納キーリスト
	 */
	public List<String> getFeedKeys(String sid, BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * セッションへのEntry格納キー一覧を取得.
	 * @param sid SID
	 * @param reflexContext ReflexContext
	 * @return セッションへのEntry格納キーリスト
	 */
	public List<String> getEntryKeys(String sid, BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * セッションへの文字列格納キー一覧を取得.
	 * @param sid SID
	 * @param reflexContext ReflexContext
	 * @return セッションへの文字列格納キーリスト
	 */
	public List<String> getStringKeys(String sid, BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * セッションへの数値格納キー一覧を取得.
	 * @param sid SID
	 * @param reflexContext ReflexContext
	 * @return セッションへの数値格納キーリスト
	 */
	public List<String> getLongKeys(String sid, BaseReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * セッションへの格納キー一覧を取得.
	 * @param sid SID
	 * @param reflexContext ReflexContext
	 * @return セッションへの値格納キーリスト。
	 *         キー: feed, entry, string, longのいずれか
	 *         値: キーリスト
	 */
	public Map<String, List<String>> getKeys(String sid, BaseReflexContext reflexContext)
	throws IOException, TaggingException;

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
			String serviceName, String namespace,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException;

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
	throws IOException;

	/**
	 * リクエストからSIDを取得.
	 * @param req リクエスト
	 * @return SID
	 */
	public String getSessionId(ReflexRequest req);

	/**
	 * セッションから認証情報を取得.
	 * @param sid SID
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 認証情報
	 */
	//public ReflexAuthentication getAuth(String sid, String serviceName, String namespace,
	//		RequestInfo requestInfo, ConnectionInfo connectionInfo)
	//throws IOException;

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
	throws IOException;

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
	throws IOException;

}
