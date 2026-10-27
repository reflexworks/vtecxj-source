package jp.reflexworks.taggingservice.blogic;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.SettingConst;
import jp.reflexworks.taggingservice.env.TaggingEnvConst;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.SessionManager;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * ページング機能・インメモリソート機能のデータ格納先.
 * <ul>
 * <li>セッションが存在する場合 : セッションに格納する。(従来の処理)</li>
 * <li>未ログインユーザの場合 : 未ログインユーザ用共有ストア(予約SIDのハッシュ)に格納する。</li>
 * </ul>
 * 未ログインユーザはACLが全員同じであるため、検索結果をサービス・検索条件単位で共有する。
 */
public class PaginationStore {

	/** ReflexContext */
	private ReflexContext reflexContext;
	/** 未ログインユーザ用共有ストアの場合true */
	private boolean anonymous;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ.
	 * @param reflexContext ReflexContext
	 * @param anonymous 未ログインユーザ用共有ストアの場合true
	 */
	private PaginationStore(ReflexContext reflexContext, boolean anonymous) {
		this.reflexContext = reflexContext;
		this.anonymous = anonymous;
	}

	/**
	 * データ格納先を取得.
	 * 認証情報が未ログイン(UID、SIDともになし)の場合は未ログインユーザ用共有ストア、
	 * それ以外はセッションを格納先とする。
	 * @param reflexContext ReflexContext
	 * @return データ格納先
	 */
	public static PaginationStore of(ReflexContext reflexContext) {
		return new PaginationStore(reflexContext, isAnonymous(reflexContext.getAuth()));
	}

	/**
	 * 未ログインユーザかどうか.
	 * UID、SIDともに設定されていない場合、未ログインユーザとする。
	 * (アクセストークン・リンクトークン認証はUIDが設定されているため対象外)
	 * @param auth 認証情報
	 * @return 未ログインユーザの場合true
	 */
	public static boolean isAnonymous(ReflexAuthentication auth) {
		return auth != null && StringUtils.isBlank(auth.getSessionId()) &&
				StringUtils.isBlank(auth.getUid());
	}

	/**
	 * 未ログインユーザ用共有ストアかどうか.
	 * @return 未ログインユーザ用共有ストアの場合true
	 */
	public boolean isAnonymous() {
		return anonymous;
	}

	/**
	 * Feedを取得.
	 * @param name キー
	 * @return Feed
	 */
	public FeedBase getFeed(String name)
	throws IOException, TaggingException {
		if (anonymous) {
			return getSessionManager().getFeed(PaginationConst.ANONYMOUS_SID, name, reflexContext);
		}
		return reflexContext.getSessionFeed(name);
	}

	/**
	 * Feedを登録.
	 * @param name キー
	 * @param feed Feed
	 * @return 登録したFeed
	 */
	public FeedBase setFeed(String name, FeedBase feed)
	throws IOException, TaggingException {
		if (anonymous) {
			FeedBase ret = getSessionManager().setFeed(PaginationConst.ANONYMOUS_SID, name,
					feed, reflexContext);
			resetExpire();
			return ret;
		}
		return reflexContext.setSessionFeed(name, feed);
	}

	/**
	 * Feedを削除.
	 * @param name キー
	 */
	public void deleteFeed(String name)
	throws IOException, TaggingException {
		if (anonymous) {
			getSessionManager().deleteFeed(PaginationConst.ANONYMOUS_SID, name, reflexContext);
			return;
		}
		reflexContext.deleteSessionFeed(name);
	}

	/**
	 * Feed格納キー一覧を取得.
	 * @return Feed格納キーリスト
	 */
	public List<String> getFeedKeys()
	throws IOException, TaggingException {
		if (anonymous) {
			return getSessionManager().getFeedKeys(PaginationConst.ANONYMOUS_SID, reflexContext);
		}
		return reflexContext.getSessionFeedKeys();
	}

	/**
	 * 文字列を取得.
	 * @param name キー
	 * @return 文字列
	 */
	public String getString(String name)
	throws IOException, TaggingException {
		if (anonymous) {
			return getSessionManager().getString(PaginationConst.ANONYMOUS_SID, name, reflexContext);
		}
		return reflexContext.getSessionString(name);
	}

	/**
	 * 文字列を登録.
	 * @param name キー
	 * @param text 文字列
	 * @return 登録した文字列
	 */
	public String setString(String name, String text)
	throws IOException, TaggingException {
		if (anonymous) {
			String ret = getSessionManager().setString(PaginationConst.ANONYMOUS_SID, name,
					text, reflexContext);
			resetExpire();
			return ret;
		}
		return reflexContext.setSessionString(name, text);
	}

	/**
	 * 文字列を削除.
	 * @param name キー
	 */
	public void deleteString(String name)
	throws IOException, TaggingException {
		if (anonymous) {
			getSessionManager().deleteString(PaginationConst.ANONYMOUS_SID, name, reflexContext);
			return;
		}
		reflexContext.deleteSessionString(name);
	}

	/**
	 * 数値を取得.
	 * @param name キー
	 * @return 数値
	 */
	public Long getLong(String name)
	throws IOException, TaggingException {
		if (anonymous) {
			return getSessionManager().getLong(PaginationConst.ANONYMOUS_SID, name, reflexContext);
		}
		return reflexContext.getSessionLong(name);
	}

	/**
	 * 数値を登録.
	 * @param name キー
	 * @param num 数値
	 * @return 登録した数値
	 */
	public long setLong(String name, long num)
	throws IOException, TaggingException {
		if (anonymous) {
			long ret = getSessionManager().setLong(PaginationConst.ANONYMOUS_SID, name,
					num, reflexContext);
			resetExpire();
			return ret;
		}
		return reflexContext.setSessionLong(name, num);
	}

	/**
	 * 数値を登録.
	 * 値が登録されていない場合のみ登録される。
	 * @param name キー
	 * @param num 数値
	 * @return 成功の場合null、失敗の場合登録済みの値
	 */
	public Long setLongIfAbsent(String name, long num)
	throws IOException, TaggingException {
		if (anonymous) {
			Long ret = getSessionManager().setLongIfAbsent(PaginationConst.ANONYMOUS_SID, name,
					num, reflexContext);
			resetExpire();
			return ret;
		}
		return reflexContext.setSessionLongIfAbsent(name, num);
	}

	/**
	 * 数値を削除.
	 * @param name キー
	 */
	public void deleteLong(String name)
	throws IOException, TaggingException {
		if (anonymous) {
			getSessionManager().deleteLong(PaginationConst.ANONYMOUS_SID, name, reflexContext);
			return;
		}
		reflexContext.deleteSessionLong(name);
	}

	/**
	 * 数値に値を加算.
	 * @param name キー
	 * @param num 加算値
	 * @return 加算後の数値
	 */
	public long increment(String name, long num)
	throws IOException, TaggingException {
		if (anonymous) {
			long ret = getSessionManager().increment(PaginationConst.ANONYMOUS_SID, name,
					num, reflexContext);
			resetExpire();
			return ret;
		}
		return reflexContext.incrementSession(name, num);
	}

	/**
	 * 未ログインユーザ用共有ストアの有効期間(秒)を取得.
	 * カーソルリスト作成からこの時間を過ぎたデータは古いデータとして扱う。
	 * @return 未ログインユーザ用共有ストアの有効期間(秒)
	 */
	public int getAnonymousExpireSec() {
		String serviceName = reflexContext.getServiceName();
		try {
			return TaggingEnvUtil.getPropInt(serviceName,
					SettingConst.PAGINATION_ANONYMOUS_EXPIRE_SEC,
					TaggingEnvConst.PAGINATION_ANONYMOUS_EXPIRE_SEC_DEFAULT);
		} catch (InvalidServiceSettingException e) {
			RequestInfo requestInfo = reflexContext.getRequestInfo();
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[getAnonymousExpireSec] InvalidServiceSettingException: ");
			sb.append(SettingConst.PAGINATION_ANONYMOUS_EXPIRE_SEC);
			logger.warn(sb.toString(), e);
			return TaggingEnvConst.PAGINATION_ANONYMOUS_EXPIRE_SEC_DEFAULT;
		}
	}

	/**
	 * 未ログインユーザ用共有ストアのロック有効時間(ミリ秒)を取得.
	 * 処理が異常終了した場合にロックが残り続けないよう、この時間を過ぎたロックは無効とする。
	 * @return ロック有効時間(ミリ秒)
	 */
	public static long getAnonymousLockExpireMillis() {
		return TaggingEnvUtil.getSystemPropLong(
				TaggingEnvConst.PAGINATION_ANONYMOUS_LOCK_EXPIRE_MILLIS,
				TaggingEnvConst.PAGINATION_ANONYMOUS_LOCK_EXPIRE_MILLIS_DEFAULT);
	}

	/**
	 * 未ログインユーザ用共有ストアの有効期限を延長する.
	 * Redisのhsetは有効期限を設定しないため、書き込みのたびに有効期限を設定する。
	 */
	private void resetExpire()
	throws IOException, TaggingException {
		// データの有効期間の2倍、かつロック有効時間以上とする。
		int expireSec = getAnonymousExpireSec() * 2;
		int lockExpireSec = (int)(getAnonymousLockExpireMillis() / 1000);
		if (expireSec < lockExpireSec) {
			expireSec = lockExpireSec;
		}
		getSessionManager().setExpire(PaginationConst.ANONYMOUS_SID, expireSec,
				reflexContext.getServiceName(), reflexContext.getNamespace(),
				reflexContext.getRequestInfo(), reflexContext.getConnectionInfo());
	}

	/**
	 * SessionManagerを取得.
	 * @return SessionManager
	 */
	private SessionManager getSessionManager() {
		return TaggingEnvUtil.getSessionManager();
	}

}
