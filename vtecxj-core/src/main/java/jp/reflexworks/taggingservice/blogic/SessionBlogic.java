package jp.reflexworks.taggingservice.blogic;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.AuthenticationManager;
import jp.reflexworks.taggingservice.plugin.SessionManager;
import jp.reflexworks.taggingservice.plugin.UserManager;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.NamespaceUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * セッションを扱うビジネスロジック
 */
public class SessionBlogic {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * セッションにFeedを登録.
	 * 既に値が登録されている場合は上書きする。
	 * @param name 登録キー
	 * @param feed Feed
	 * @param reflexContext ReflexContext
	 * @return 登録したFeed
	 */
	public FeedBase setFeed(String name, FeedBase feed,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 引数チェック
		CheckUtil.checkNotNull(name, "name");
		checkFeed(feed);

		// SID取得
		ReflexAuthentication auth = reflexContext.getAuth();
		String sid = auth.getSessionId();
		// セッションが存在しない場合エラー
		CheckUtil.checkNotNull(sid, "The session");

		// 登録
		SessionManager sessionManager = TaggingEnvUtil.getSessionManager();
		return sessionManager.setFeed(sid, name, feed, reflexContext);
	}

	/**
	 * セッションにEntryを登録.
	 * 既に値が登録されている場合は上書きする。
	 * @param name 登録キー
	 * @param entry Entry
	 * @param reflexContext ReflexContext
	 * @return 登録したFeed
	 */
	public EntryBase setEntry(String name, EntryBase entry,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 引数チェック
		CheckUtil.checkNotNull(name, "name");
		checkEntry(entry);

		// SID取得
		ReflexAuthentication auth = reflexContext.getAuth();
		String sid = auth.getSessionId();
		// セッションが存在しない場合エラー
		CheckUtil.checkNotNull(sid, "The session");

		// 登録
		SessionManager sessionManager = TaggingEnvUtil.getSessionManager();
		return sessionManager.setEntry(sid, name, entry, reflexContext);
	}

	/**
	 * セッションに文字列を登録.
	 * 既に値が登録されている場合は上書きする。
	 * @param name 登録キー
	 * @param text 文字列
	 * @param reflexContext ReflexContext
	 * @return 登録した文字列
	 */
	public String setString(String name, String text,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 引数チェック
		CheckUtil.checkNotNull(name, "name");
		checkString(text);

		// SID取得
		ReflexAuthentication auth = reflexContext.getAuth();
		String sid = auth.getSessionId();
		// セッションが存在しない場合エラー
		CheckUtil.checkNotNull(sid, "The session");

		// 登録
		SessionManager sessionManager = TaggingEnvUtil.getSessionManager();
		return sessionManager.setString(sid, name, text, reflexContext);
	}

	/**
	 * セッションに文字列を登録.
	 * 値が登録されていない場合のみ登録される。
	 * @param name 登録キー
	 * @param text 文字列
	 * @param reflexContext ReflexContext
	 * @return 成功の場合null、失敗の場合登録済みの値
	 */
	public String setStringIfAbsent(String name, String text,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 引数チェック
		CheckUtil.checkNotNull(name, "name");

		// SID取得
		ReflexAuthentication auth = reflexContext.getAuth();
		String sid = auth.getSessionId();
		// セッションが存在しない場合エラー
		CheckUtil.checkNotNull(sid, "The session");

		// 登録
		SessionManager sessionManager = TaggingEnvUtil.getSessionManager();
		return sessionManager.setStringIfAbsent(sid, name, text, reflexContext);
	}

	/**
	 * セッションに数値を登録.
	 * 既に値が登録されている場合は上書きする。
	 * @param name 登録キー
	 * @param num 数値
	 * @param reflexContext ReflexContext
	 * @return 登録した数値
	 */
	public long setLong(String name, long num,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 引数チェック
		CheckUtil.checkNotNull(name, "name");

		// SID取得
		ReflexAuthentication auth = reflexContext.getAuth();
		String sid = auth.getSessionId();
		// セッションが存在しない場合エラー
		CheckUtil.checkNotNull(sid, "The session");

		// 登録
		SessionManager sessionManager = TaggingEnvUtil.getSessionManager();
		return sessionManager.setLong(sid, name, num, reflexContext);
	}

	/**
	 * セッションに数値を登録.
	 * 値が登録されていない場合のみ登録される。
	 * @param name 登録キー
	 * @param num 数値
	 * @param reflexContext ReflexContext
	 * @return 成功の場合null、失敗の場合登録済みの値
	 */
	public Long setLongIfAbsent(String name, long num,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 引数チェック
		CheckUtil.checkNotNull(name, "name");

		// SID取得
		ReflexAuthentication auth = reflexContext.getAuth();
		String sid = auth.getSessionId();
		// セッションが存在しない場合エラー
		CheckUtil.checkNotNull(sid, "The session");

		// 登録
		SessionManager sessionManager = TaggingEnvUtil.getSessionManager();
		return sessionManager.setLongIfAbsent(sid, name, num, reflexContext);
	}

	/**
	 * セッションの指定された数値に値を加算.
	 * @param name 登録キー
	 * @param num 数値
	 * @param reflexContext ReflexContext
	 * @return 加算後の数値
	 */
	public long increment(String name, long num,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 引数チェック
		CheckUtil.checkNotNull(name, "name");

		// SID取得
		ReflexAuthentication auth = reflexContext.getAuth();
		String sid = auth.getSessionId();
		// セッションが存在しない場合エラー
		CheckUtil.checkNotNull(sid, "The session");

		// 登録
		SessionManager sessionManager = TaggingEnvUtil.getSessionManager();
		return sessionManager.increment(sid, name, num, reflexContext);
	}

	/**
	 * セッションからFeedを削除.
	 * @param name 登録キー
	 * @param reflexContext ReflexContext
	 */
	public void deleteFeed(String name, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 引数チェック
		CheckUtil.checkNotNull(name, "name");

		// SID取得
		ReflexAuthentication auth = reflexContext.getAuth();
		String sid = auth.getSessionId();
		// セッションが存在しない場合エラー
		CheckUtil.checkNotNull(sid, "The session");

		// 削除
		SessionManager sessionManager = TaggingEnvUtil.getSessionManager();
		sessionManager.deleteFeed(sid, name, reflexContext);
	}

	/**
	 * セッションからEntryを削除.
	 * @param name 登録キー
	 * @param reflexContext ReflexContext
	 */
	public void deleteEntry(String name, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 引数チェック
		CheckUtil.checkNotNull(name, "name");

		// SID取得
		ReflexAuthentication auth = reflexContext.getAuth();
		String sid = auth.getSessionId();
		// セッションが存在しない場合エラー
		CheckUtil.checkNotNull(sid, "The session");

		// 削除
		SessionManager sessionManager = TaggingEnvUtil.getSessionManager();
		sessionManager.deleteEntry(sid, name, reflexContext);
	}

	/**
	 * セッションから文字列を削除.
	 * @param name 登録キー
	 * @param reflexContext ReflexContext
	 */
	public void deleteString(String name, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 引数チェック
		CheckUtil.checkNotNull(name, "name");

		// SID取得
		ReflexAuthentication auth = reflexContext.getAuth();
		String sid = auth.getSessionId();
		// セッションが存在しない場合エラー
		CheckUtil.checkNotNull(sid, "The session");

		// 削除
		SessionManager sessionManager = TaggingEnvUtil.getSessionManager();
		sessionManager.deleteString(sid, name, reflexContext);
	}

	/**
	 * セッションから数値を削除.
	 * @param name 登録キー
	 * @param reflexContext ReflexContext
	 */
	public void deleteLong(String name, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 引数チェック
		CheckUtil.checkNotNull(name, "name");

		// SID取得
		ReflexAuthentication auth = reflexContext.getAuth();
		String sid = auth.getSessionId();
		// セッションが存在しない場合エラー
		CheckUtil.checkNotNull(sid, "The session");

		// 削除
		SessionManager sessionManager = TaggingEnvUtil.getSessionManager();
		sessionManager.deleteLong(sid, name, reflexContext);
	}

	/**
	 * セッションからFeedを取得.
	 * @param name 登録キー
	 * @param reflexContext ReflexContext
	 * @return Feed
	 */
	public FeedBase getFeed(String name, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 引数チェック
		CheckUtil.checkNotNull(name, "name");

		// SID取得
		ReflexAuthentication auth = reflexContext.getAuth();
		String sid = auth.getSessionId();
		// セッションが存在しない場合エラー
		CheckUtil.checkNotNull(sid, "The session");

		// 検索
		SessionManager sessionManager = TaggingEnvUtil.getSessionManager();
		return sessionManager.getFeed(sid, name, reflexContext);
	}

	/**
	 * セッションからEntryを取得.
	 * @param name 登録キー
	 * @param reflexContext ReflexContext
	 * @return 登録したFeed
	 */
	public EntryBase getEntry(String name, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 引数チェック
		CheckUtil.checkNotNull(name, "name");

		// SID取得
		ReflexAuthentication auth = reflexContext.getAuth();
		String sid = auth.getSessionId();
		// セッションが存在しない場合エラー
		CheckUtil.checkNotNull(sid, "The session");

		// 検索
		SessionManager sessionManager = TaggingEnvUtil.getSessionManager();
		return sessionManager.getEntry(sid, name, reflexContext);
	}

	/**
	 * セッションから文字列を取得.
	 * @param name 登録キー
	 * @param reflexContext ReflexContext
	 * @return 登録した文字列
	 */
	public String getString(String name, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 引数チェック
		CheckUtil.checkNotNull(name, "name");

		// SID取得
		ReflexAuthentication auth = reflexContext.getAuth();
		String sid = auth.getSessionId();
		// セッションが存在しない場合エラー
		CheckUtil.checkNotNull(sid, "The session");

		// 検索
		SessionManager sessionManager = TaggingEnvUtil.getSessionManager();
		return sessionManager.getString(sid, name, reflexContext);
	}

	/**
	 * セッションから数値を取得.
	 * @param name 登録キー
	 * @param reflexContext ReflexContext
	 * @return 登録した数値
	 */
	public Long getLong(String name, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 引数チェック
		CheckUtil.checkNotNull(name, "name");

		// SID取得
		ReflexAuthentication auth = reflexContext.getAuth();
		String sid = auth.getSessionId();
		// セッションが存在しない場合エラー
		CheckUtil.checkNotNull(sid, "The session");

		// 検索
		SessionManager sessionManager = TaggingEnvUtil.getSessionManager();
		return sessionManager.getLong(sid, name, reflexContext);
	}

	/**
	 * セッションへのFeed格納キー一覧を取得.
	 * @param reflexContext ReflexContext
	 * @return セッションへのFeed格納キーリスト
	 */
	public List<String> getFeedKeys(ReflexContext reflexContext)
	throws IOException, TaggingException {
		// SID取得
		ReflexAuthentication auth = reflexContext.getAuth();
		String sid = auth.getSessionId();
		// セッションが存在しない場合エラー
		CheckUtil.checkNotNull(sid, "The session");

		// キー一覧を取得
		SessionManager sessionManager = TaggingEnvUtil.getSessionManager();
		return sessionManager.getFeedKeys(sid, reflexContext);
	}

	/**
	 * セッションへのEntry格納キー一覧を取得.
	 * @param reflexContext ReflexContext
	 * @return セッションへのEntry格納キーリスト
	 */
	public List<String> getEntryKeys(ReflexContext reflexContext)
	throws IOException, TaggingException {
		// SID取得
		ReflexAuthentication auth = reflexContext.getAuth();
		String sid = auth.getSessionId();
		// セッションが存在しない場合エラー
		CheckUtil.checkNotNull(sid, "The session");

		// キー一覧を取得
		SessionManager sessionManager = TaggingEnvUtil.getSessionManager();
		return sessionManager.getEntryKeys(sid, reflexContext);
	}

	/**
	 * セッションへの文字列格納キー一覧を取得.
	 * @param reflexContext ReflexContext
	 * @return セッションへの文字列格納キーリスト
	 */
	public List<String> getStringKeys(ReflexContext reflexContext)
	throws IOException, TaggingException {
		// SID取得
		ReflexAuthentication auth = reflexContext.getAuth();
		String sid = auth.getSessionId();
		// セッションが存在しない場合エラー
		CheckUtil.checkNotNull(sid, "The session");

		// キー一覧を取得
		SessionManager sessionManager = TaggingEnvUtil.getSessionManager();
		return sessionManager.getStringKeys(sid, reflexContext);
	}

	/**
	 * セッションへの数値格納キー一覧を取得.
	 * @param reflexContext ReflexContext
	 * @return セッションへの数値格納キーリスト
	 */
	public List<String> getLongKeys(ReflexContext reflexContext)
	throws IOException, TaggingException {
		// SID取得
		ReflexAuthentication auth = reflexContext.getAuth();
		String sid = auth.getSessionId();
		// セッションが存在しない場合エラー
		CheckUtil.checkNotNull(sid, "The session");

		// キー一覧を取得
		SessionManager sessionManager = TaggingEnvUtil.getSessionManager();
		return sessionManager.getLongKeys(sid, reflexContext);
	}

	/**
	 * セッションへの格納キー一覧を取得.
	 * @param reflexContext ReflexContext
	 * @return セッションへの値格納キーリスト。
	 *         キー: Feed, Entry, String, Longのいずれか
	 *         値: キーリスト
	 */
	public Map<String, List<String>> getKeys(ReflexContext reflexContext)
	throws IOException, TaggingException {
		// SID取得
		ReflexAuthentication auth = reflexContext.getAuth();
		String sid = auth.getSessionId();
		// セッションが存在しない場合エラー
		CheckUtil.checkNotNull(sid, "The session");

		// キー一覧を取得
		SessionManager sessionManager = TaggingEnvUtil.getSessionManager();
		return sessionManager.getKeys(sid, reflexContext);
	}

	/**
	 * セッションの有効時間を設定.
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 正常に設定された場合true。データ存在なしの場合false
	 */
	public boolean resetExpire(ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// SID取得
		String sid = null;
		if (auth != null) {
			sid = auth.getSessionId();
		}
		// セッションが存在しない場合エラー
		if (StringUtils.isBlank(sid)) {
			return false;
		}

		// セッション有効時間
		String serviceName = auth.getServiceName();
		int sec = getSessionExpire(serviceName);

		// 名前空間取得
		String namespace = NamespaceUtil.getNamespace(serviceName, requestInfo, connectionInfo);

		// 有効時間設定
		SessionManager sessionManager = TaggingEnvUtil.getSessionManager();
		return sessionManager.setExpire(sid, sec, serviceName, namespace,
				requestInfo, connectionInfo);
	}

	/**
	 * セッションを生成.
	 * @param account アカウント
	 * @param uid UID
	 * @param authType 認証方法
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 認証情報
	 */
	public ReflexAuthentication createSession(String account, String uid,
			String authType, String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// 仮の認証情報生成
		AuthenticationManager authManager = TaggingEnvUtil.getAuthenticationManager();
		ReflexAuthentication tmpAuth = authManager.createAuth(account, uid, null,
				Constants.AUTH_TYPE_SYSTEM, serviceName);

		// セッション有効時間
		int sec = getSessionExpire(serviceName);

		// 名前空間取得
		String namespace = NamespaceUtil.getNamespace(serviceName, requestInfo, connectionInfo);

		// セッション生成
		SessionManager sessionManager = TaggingEnvUtil.getSessionManager();
		String sid = sessionManager.createSession(tmpAuth, sec, serviceName,
				namespace, requestInfo, connectionInfo);

		return authManager.createAuth(account, uid, sid, authType, serviceName);
	}

	/**
	 * セッションを削除.
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void deleteSession(ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		String sid = auth.getSessionId();
		if (StringUtils.isBlank(sid)) {
			if (logger.isDebugEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[deleteSession] sid is null. uid=");
				sb.append(auth.getUid());
				logger.debug(sb.toString());
			}
			return;
		}

		// 名前空間取得
		String namespace = NamespaceUtil.getNamespace(serviceName, requestInfo, connectionInfo);

		// セッション削除
		SessionManager sessionManager = TaggingEnvUtil.getSessionManager();
		sessionManager.deleteSession(sid, serviceName, namespace, requestInfo, connectionInfo);
	}

	/**
	 * セッション有効時間を取得
	 * @param serviceName サービス名
	 * @return セッション有効時間(秒)
	 */
	public int getSessionExpire(String serviceName)
			throws InvalidServiceSettingException {
		int min = TaggingEnvUtil.getSessionMinute(serviceName);
		return min * 60;
	}

	/**
	 * リクエストからセッションIDを取得.
	 * @param req リクエスト
	 * @return セッションID
	 */
	public String getSessionIdFromRequest(ReflexRequest req) {
		SessionManager sessionManager = TaggingEnvUtil.getSessionManager();
		return sessionManager.getSessionId(req);
	}

	/**
	 * セッションから認証情報を取得.
	 * @param systemContext SystemContext
	 * @param sessionId セッションID
	 * @return 認証情報
	 */
	public String getUidFromSession(SystemContext systemContext, String sessionId)
	throws IOException, TaggingException {
		String serviceName = systemContext.getServiceName();
		RequestInfo requestInfo = systemContext.getRequestInfo();
		ConnectionInfo connectionInfo = systemContext.getConnectionInfo();

		// 名前空間取得
		String namespace = NamespaceUtil.getNamespace(serviceName, requestInfo, connectionInfo);

		// セッションからUIDを取得
		SessionManager sessionManager = TaggingEnvUtil.getSessionManager();
		return sessionManager.getUidBySession(sessionId, serviceName, namespace,
				requestInfo, connectionInfo);
	}

	/**
	 * セッションから取得したUIDを元に認証情報を取得.
	 * @param uid UID
	 * @param systemContext SystemContext
	 * @param sessionId セッションID
	 * @return 認証情報
	 */
	public ReflexAuthentication getAuthFromSession(String uid, SystemContext systemContext,
			String sessionId)
	throws IOException, TaggingException {
		String serviceName = systemContext.getServiceName();
		// アカウントはデータから取得
		UserManager userManager = TaggingEnvUtil.getUserManager();
		String account = userManager.getAccountByUid(uid, systemContext);
		if (StringUtils.isBlank(account)) {
			return null;
		}
		// 認証情報を生成
		AuthenticationManager authManager = TaggingEnvUtil.getAuthenticationManager();
		return authManager.createAuth(account, uid, sessionId,
				Constants.AUTH_TYPE_SESSION, serviceName);
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

}
