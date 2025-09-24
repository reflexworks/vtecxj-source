package jp.reflexworks.taggingservice.redis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexStatic;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.ReflexEnvUtil;
import jp.reflexworks.taggingservice.exception.ConnectionException;
import jp.reflexworks.taggingservice.exception.StaticDuplicatedException;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.RetryUtil;
import jp.sourceforge.reflex.util.StringUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisException;

/**
 * Jedisのエラーハンドリングを行うクラス.
 * JedisExceptionをcatchしてIOExceptionに変換する。
 * JedisConnectionExceptionの場合、リトライできる場合は一定回数リトライする。
 */
public final class JedisUtil {

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(JedisUtil.class);

	/**
	 * コンストラクタ.
	 */
	private JedisUtil() { }

	/**
	 * 初期処理
	 * @return Redis操作のためのstatic情報
	 */
	public static JedisEnv init() {
		// Redis用static情報をメモリに格納
		JedisEnv jedisEnv = new JedisEnv();
		try {
			ReflexStatic.setStatic(JedisConst.STATIC_NAME_REDIS, jedisEnv);
			// Redis用static情報新規生成
			jedisEnv.init();

		} catch (StaticDuplicatedException e) {
			// Redis用static情報は他の処理ですでに生成済み
			jedisEnv = getJedisEnv();
		}
		return jedisEnv;
	}

	/**
	 * Redis操作のためのstatic情報クローズ処理
	 */
	public static void close() {
		JedisEnv jedisEnv = getJedisEnv();
		if (jedisEnv != null) {
			jedisEnv.close();
		}
	}

	/**
	 * Redis操作のためのstatic情報を取得.
	 * @return Redis操作のためのstatic情報
	 */
	public static JedisEnv getJedisEnv() {
		return (JedisEnv)ReflexStatic.getStatic(JedisConst.STATIC_NAME_REDIS);
	}

	/**
	 * JedisConnectionオブジェクトを取得し、ConnectionInfoに格納(参照用).
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return JedisConnection
	 * @throws IOException IOエラー。causeはJedisException。
	 */
	public static JedisConnection getReadConnection(RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException {
		return getJedisConnection(JedisConst.CONN_NAME_READ, requestInfo, connectionInfo);
	}

	/**
	 * JedisConnectionオブジェクトを取得し、ConnectionInfoに格納(更新用).
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return JedisConnection
	 * @throws IOException IOエラー。causeはJedisException。
	 */
	public static JedisConnection getWriteConnection(RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException {
		return getJedisConnection(JedisConst.CONN_NAME_WRITE, requestInfo, connectionInfo);
	}

	/**
	 * JedisConnectionオブジェクトを取得し、ConnectionInfoに格納.
	 * @param name 取得名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return JedisConnection
	 * @throws IOException IOエラー。causeはJedisException。
	 */
	public static JedisConnection getJedisConnection(String name,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		JedisConnection jConn = (JedisConnection)getConnectionInfo(connectionInfo, name);
		if (jConn == null) {
			jConn = createJedis(name, requestInfo, connectionInfo);
		}
		return jConn;
	}

	/**
	 * Jedisオブジェクトを生成.
	 * コネクション情報に追加。
	 * @param name コネクション情報名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return Jedisコネクション
	 * @throws IOException IOエラー。causeはJedisException。
	 */
	public static JedisConnection createJedis(String name, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException {
		JedisEnv jedisEnv = getJedisEnv();
		Jedis jedis = null;
		int numRetries = JedisUtil.getRedisGetconnectionRetryCount();
		int waitMillis = JedisUtil.getRedisGetconnectionRetryWaitmillis();
		boolean retried = false;
		for (int r = 0; r <= numRetries; r++) {
			try {
				if (JedisConst.CONN_NAME_READ.equals(name)) {
					jedis = jedisEnv.getSlaveConnection();
				}
				if (jedis == null) {
					jedis = jedisEnv.getMasterConnection();
				}
				JedisConnection jConn = new JedisConnection(jedis,
						name, connectionInfo, name);
				setConnectionInfo(connectionInfo, name, jConn);
				return jConn;

			} catch (IOException e) {
				if (r >= numRetries) {
					if (logger.isDebugEnabled()) {
						logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
								"[createJedis] check reCreateJedisPool");
					}
					// リトライ対象だがリトライ回数を超えた場合
					// サーバ起動処理中の場合エラーを返す。
					if (!isRunning() || retried) {
						throw e;
					}
					if (logger.isDebugEnabled()) {
						logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
								"[createJedis] do reCreateJedisPool");
					}
					// サーバ稼働中の場合、コネクションプール再作成。
					reCreateJedisPool(requestInfo, connectionInfo);
					retried = true;
					numRetries++;	// 繰り返しを1回追加
				}
				if (logger.isDebugEnabled()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
							RetryUtil.getRetryLog(e, r));
				}
				RetryUtil.sleep(waitMillis + r * 10);
			}
		}
		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}

	/**
	 * コネクション情報用コネクションを取得.
	 * @param connectionInfo コネクション情報
	 * @param name 名前
	 * @return コネクション情報用コネクション
	 */
	public static JedisConnection getConnectionInfo(ConnectionInfo connectionInfo,
			String name) {
		return (JedisConnection) connectionInfo.get(name);
	}

	/**
	 * コネクション情報用コネクションをコネクション情報に格納.
	 * @param connectionInfo コネクション情報
	 * @param name 名前
	 * @param jConn コネクション情報用コネクション
	 */
	public static void setConnectionInfo(ConnectionInfo connectionInfo,
			String name, JedisConnection jConn) {
		connectionInfo.put(name, jConn);
	}

	/**
	 * 例外がコネクションエラーかどうか判定.
	 * @param e 例外
	 * @return 例外がコネクションエラーの場合true;
	 */
	public static boolean isConnectionError(Throwable e) {
		Throwable tmp = e;
		if (e instanceof IOException && e.getCause() != null) {
			tmp = e.getCause();
		}
		if (tmp instanceof JedisConnectionException) {
			return true;
		}
		return false;
	}

	/**
	 * JedisException (extends RuntimeException) をIOExceptionに変換する.
	 * コネクションエラーは ConnectionException に変換する。
	 * @param e 例外
	 * @param jedis Jedisコネクション
	 * @param name コネクション情報名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return IOException 変換した例外
	 */
	public static IOException convertException(JedisException e, Jedis jedis,
			String name, ConnectionInfo connectionInfo) {
		String errorMessage = editErrorMessage(e, jedis);
		StringBuilder sb = new StringBuilder();
		sb.append("[convertException] ");
		sb.append(e.getClass().getName());
		sb.append(" : ");
		sb.append(errorMessage);
		logger.warn(sb.toString());
		if (logger.isDebugEnabled()) {
			logger.debug("[convertException] " + errorMessage, e);
		}
		if (isConnectionError(e)) {
			connectionInfo.close(name);
			return doConnectionError(e, name, errorMessage);
		} else {
			return new IOException(errorMessage, e);
		}
	}

	/**
	 * JedisException (extends RuntimeException) をIOExceptionに変換する.
	 * コネクションエラーは ConnectionException に変換する。
	 * PoolからJedisコネクション取得時のエラーに使用。
	 * @param e 例外
	 * @param name コネクション情報名
	 * @return IOException 変換した例外
	 */
	public static IOException convertException(JedisException e,
			String name) {
		String errorMessage = e.getMessage();
		StringBuilder sb = new StringBuilder();
		sb.append("[convertException] ");
		sb.append(e.getClass().getName());
		sb.append(" : ");
		sb.append(errorMessage);
		if (logger.isDebugEnabled()) {
			logger.debug(sb.toString(), e);
		}
		if (isConnectionError(e)) {
			return doConnectionError(e, name, errorMessage);
		} else {
			return new IOException(errorMessage, e);
		}
	}

	/**
	 * JedisException (extends RuntimeException) をIOExceptionに変換する.
	 * コネクションエラーは ConnectionException に変換する。
	 * @param e 例外
	 * @param name コネクション情報名
	 * @param errorMessage エラーメッセージ
	 * @return IOException 変換した例外
	 */
	private static IOException doConnectionError(JedisException e, String name,
			String errorMessage) {
		return new ConnectionException(errorMessage, e);
	}

	/**
	 * JedisExceptionをIOExceptionに変換する際のメッセージを編集.
	 * @param e 例外
	 * @param jedis Jedisコネクション
	 * @return エラーメッセージ
	 */
	private static String editErrorMessage(JedisException e, Jedis jedis) {
		StringBuilder sb = new StringBuilder();
		sb.append(e.getMessage());
		sb.append(" : Jedis.hashCode = ");
		sb.append(jedis.hashCode());
		return sb.toString();
	}

	/**
	 * 実行開始ログ編集
	 * @param command コマンド
	 * @return 実行開始ログ文字列
	 */
	public static String getStartLog(String command) {
		return getStartLog(command, (String) null, (String) null, null, null, null);
	}

	/**
	 * 実行開始ログ編集
	 * @param command コマンド
	 * @param hash オブジェクトのハッシュコード
	 * @return 実行開始ログ文字列
	 */
	public static String getStartLog(String command, int hash) {
		return getStartLog(command, (String) null, (String) null, null, null, hash);
	}

	/**
	 * 実行開始ログ編集
	 * @param command コマンド
	 * @param key キー
	 * @return 実行開始ログ文字列
	 */
	public static String getStartLog(String command, String key) {
		return getStartLog(command, key, (String) null, null, null, null);
	}

	/**
	 * 実行開始ログ編集
	 * @param command コマンド
	 * @param key キー
	 * @param value 値
	 * @return 実行開始ログ文字列
	 */
	public static String getStartLog(String command, String key, String value) {
		return getStartLog(command, key, (String) null, value, null, null);
	}

	/**
	 * 実行開始ログ編集
	 * @param command コマンド
	 * @param keyBytes キー
	 * @return 実行開始ログ文字列
	 */
	public static String getStartLog(String command, byte[] keyBytes) {
		String key = getString(keyBytes);
		return getStartLog(command, key);
	}

	/**
	 * 実行開始ログ編集
	 * @param command コマンド
	 * @param key キー
	 * @param expire 有効時間
	 * @return 実行開始ログ文字列
	 */
	public static String getStartLog(String command, String key, Integer expire) {
		return getStartLog(command, key, (String) null, null, expire, null);
	}

	/**
	 * 実行開始ログ編集
	 * @param command コマンド
	 * @param keyBytes キー
	 * @param expire 有効時間
	 * @return 実行開始ログ文字列
	 */
	public static String getStartLog(String command, byte[] keyBytes, Integer expire) {
		String key = getString(keyBytes);
		return getStartLog(command, key, expire);
	}

	/**
	 * 実行開始ログ編集
	 * @param command コマンド
	 * @param keyBytes キー
	 * @param nameBytes 第ニキー
	 * @return 実行開始ログ文字列
	 */
	public static String getStartLog(String command, byte[] keyBytes, byte[] nameBytes) {
		String key = getString(keyBytes);
		String name = getString(nameBytes);
		return getStartLog(command, key, name);
	}

	/**
	 * 実行開始ログ編集
	 * @param command コマンド
	 * @param key キー
	 * @param value 値
	 * @param expire 有効時間
	 * @return 実行開始ログ文字列
	 */
	public static String getStartLog(String command, String key, String value, Integer expire) {
		return getStartLog(command, key, (String) null, value, expire, null);
	}

	/**
	 * 実行開始ログ編集
	 * @param command コマンド
	 * @param key キー
	 * @param name 名前
	 * @param value 値
	 * @return 実行開始ログ文字列
	 */
	public static String getStartLog(String command, String key, String name, String value) {
		return getStartLog(command, key, name, value, null, null);
	}

	/**
	 * 実行開始ログ編集
	 * @param command コマンド
	 * @param key キー
	 * @param name 名前
	 * @param value 値
	 * @param expire 有効時間
	 * @param hash オブジェクトのハッシュコード
	 * @return 実行開始ログ文字列
	 */
	public static String getStartLog(String command, String key, String name, String value,
			Integer expire, Integer hash) {
		StringBuilder sb = new StringBuilder();
		sb.append("[Redis] ");
		sb.append(command);
		sb.append(" start");
		if (key != null) {
			sb.append(" : key=");
			sb.append(key);
		}
		if (name != null) {
			sb.append(", name=");
			sb.append(name);
		}
		if (value != null) {
			sb.append(", value=");
			sb.append(value);
		}
		if (expire != null) {
			sb.append(", expire=");
			sb.append(expire);
		}
		if (hash != null && hash > 0) {
			sb.append(", hash=");
			sb.append(hash);
		}
		return sb.toString();
	}

	/**
	 * 実行開始ログ編集
	 * @param command コマンド
	 * @param keys キー
	 * @return 実行開始ログ文字列
	 */
	public static String getStartLog(String command, String[] keys) {
		return getStartLog(command, keys, null, null);
	}

	/**
	 * 実行開始ログ編集
	 * @param command コマンド
	 * @param keys キー
	 * @return 実行開始ログ文字列
	 */
	public static String getStartLog(String command, byte[][] keysBytes) {
		String[] keys = new String[keysBytes.length];
		for (int i = 0; i < keys.length; i++) {
			keys[i] = getString(keysBytes[i]);
		}
		return getStartLog(command, keys);
	}

	/**
	 * 実行開始ログ編集.
	 * msetコマンド用
	 * @param command コマンド
	 * @param keyValues キー
	 * @return 実行開始ログ文字列
	 */
	public static String getStartLogForMset(String command, String[] keyValues) {
		String[] keys = new String[keyValues.length / 2];
		for (int i = 0; i < keys.length; i++) {
			keys[i] = keyValues[i * 2];
		}
		return getStartLog(command, keys);
	}

	/**
	 * 実行開始ログ編集.
	 * msetコマンド用
	 * @param command コマンド
	 * @param keyValues キーと値の配列
	 * @return 実行開始ログ文字列
	 */
	public static String getStartLogForMset(String command, byte[][] keyValues) {
		String[] keys = new String[keyValues.length / 2];
		for (int i = 0; i < keys.length; i++) {
			keys[i] = getString(keyValues[i * 2]);
		}
		return getStartLog(command, keys);
	}

	/**
	 * 実行開始ログ編集
	 * @param command コマンド
	 * @param keys キー
	 * @param values 値
	 * @param expire 有効時間
	 * @return 実行開始ログ文字列
	 */
	public static String getStartLog(String command, String[] keys, String[] values,
			Integer expire) {
		StringBuilder sb = new StringBuilder();
		sb.append("[Redis] ");
		sb.append(command);
		sb.append(" start : keys=[");
		boolean isFirst = true;
		for (String key : keys) {
			if (isFirst) {
				isFirst = false;
			} else {
				sb.append(", ");
			}
			sb.append(key);
		}
		sb.append("]");
		if (values != null) {
			sb.append(", values=[");
			isFirst = true;
			for (String value : values) {
				if (isFirst) {
					isFirst = false;
				} else {
					sb.append(", ");
				}
				sb.append(value);
			}
			sb.append("]");
		}
		if (expire != null) {
			sb.append(", expire=");
			sb.append(expire);
		}
		return sb.toString();
	}

	/**
	 * 実行開始ログ編集
	 * @param command コマンド
	 * @param key キー
	 * @param names 名前
	 * @return 実行開始ログ文字列
	 */
	public static String getStartLog(String command, String key, String[] names) {
		StringBuilder sb = new StringBuilder();
		sb.append("[Redis] ");
		sb.append(command);
		sb.append(" start : key=");
		sb.append(key);
		if (names != null) {
			sb.append(", names=[");
			boolean isFirst = true;
			for (String name : names) {
				if (isFirst) {
					isFirst = false;
				} else {
					sb.append(", ");
				}
				sb.append(name);
			}
			sb.append("]");
		}
		return sb.toString();
	}

	/**
	 * 実行終了ログ編集
	 * @param command コマンド
	 * @param startTime Redisコマンド実行直前の時間
	 * @return 実行終了ログ文字列
	 */
	public static String getEndLog(String command, long startTime) {
		return getEndLog(command, (String) null, (String) null, startTime, null);
	}

	/**
	 * 実行終了ログ編集
	 * @param command コマンド
	 * @param startTime Redisコマンド実行直前の時間
	 * @param hash オブジェクトのハッシュコード
	 * @return 実行終了ログ文字列
	 */
	public static String getEndLog(String command, long startTime, int hash) {
		return getEndLog(command, (String) null, (String) null, startTime, hash);
	}

	/**
	 * 実行終了ログ編集
	 * @param command コマンド
	 * @param key キー
	 * @param startTime Redisコマンド実行直前の時間
	 * @return 実行終了ログ文字列
	 */
	public static String getEndLog(String command, String key, long startTime) {
		return getEndLog(command, key, (String) null, startTime, null);
	}

	/**
	 * 実行終了ログ編集
	 * @param command コマンド
	 * @param keyBytes キー
	 * @param startTime Redisコマンド実行直前の時間
	 * @return 実行終了ログ文字列
	 */
	public static String getEndLog(String command, byte[] keyBytes, long startTime) {
		String key = getString(keyBytes);
		return getEndLog(command, key, (String)null, startTime, null);
	}

	/**
	 * 実行終了ログ編集
	 * @param command コマンド
	 * @param keyBytes キー
	 * @param nameBytes 第ニキー
	 * @param startTime Redisコマンド実行直前の時間
	 * @return 実行終了ログ文字列
	 */
	public static String getEndLog(String command, byte[] keyBytes, byte[] nameBytes, long startTime) {
		String key = getString(keyBytes);
		String name = getString(nameBytes);
		return getEndLog(command, key, name, startTime, null);
	}

	/**
	 * 実行終了ログ編集.
	 * msetコマンド用
	 * @param command コマンド
	 * @param keyValues キー
	 * @param startTime Redisコマンド実行直前の時間
	 * @return 実行開始ログ文字列
	 */
	public static String getEndLogForMset(String command, String[] keyValues, long startTime) {
		String[] keys = new String[keyValues.length / 2];
		for (int i = 0; i < keys.length; i++) {
			keys[i] = keyValues[i * 2];
		}
		return getEndLog(command, keys, startTime);
	}

	/**
	 * 実行終了ログ編集.
	 * msetコマンド用
	 * @param command コマンド
	 * @param keyValues キーと値の配列
	 * @param startTime Redisコマンド実行直前の時間
	 * @return 実行開始ログ文字列
	 */
	public static String getEndLogForMset(String command, byte[][] keyValues, long startTime) {
		String[] keys = new String[keyValues.length / 2];
		for (int i = 0; i < keys.length; i++) {
			keys[i] = getString(keyValues[i * 2]);
		}
		return getEndLog(command, keys, startTime);
	}

	/**
	 * 実行終了ログ編集
	 * @param command コマンド
	 * @param key キー
	 * @param name 名前
	 * @param startTime Redisコマンド実行直前の時間
	 * @return 実行終了ログ文字列
	 */
	public static String getEndLog(String command, String key, String name,
			long startTime) {
		return getEndLog(command, key, name, startTime, null);
	}

	/**
	 * 実行終了ログ編集
	 * @param command コマンド
	 * @param key キー
	 * @param name 名前
	 * @param startTime Redisコマンド実行直前の時間
	 * @param hash オブジェクトのハッシュコード
	 * @return 実行終了ログ文字列
	 */
	public static String getEndLog(String command, String key, String name,
			long startTime, Integer hash) {
		StringBuilder sb = new StringBuilder();
		sb.append("[Redis] ");
		sb.append(command);
		sb.append(" end");
		if (key != null) {
			sb.append(" : key=");
			sb.append(key);
		}
		if (name != null) {
			sb.append(", name=");
			sb.append(name);
		}
		if (hash != null && hash > 0) {
			sb.append(", hash=");
			sb.append(hash);
		}

		sb.append(getElapsedTimeLog(startTime));
		return sb.toString();
	}

	/**
	 * 実行終了ログ編集
	 * @param command コマンド
	 * @param key キー
	 * @param names 名前
	 * @param startTime Redisコマンド実行直前の時間
	 * @return 実行終了ログ文字列
	 */
	public static String getEndLog(String command, String key, String[] names, long startTime) {
		StringBuilder sb = new StringBuilder();
		sb.append("[Redis] ");
		sb.append(command);
		sb.append(" end : key=");
		sb.append(key);
		if (names != null) {
			sb.append(", name=[");
			boolean isFirst = true;
			for (String name : names) {
				if (isFirst) {
					isFirst = false;
				} else {
					sb.append(", ");
				}
				sb.append(name);
			}
			sb.append("]");
		}

		sb.append(getElapsedTimeLog(startTime));
		return sb.toString();
	}

	/**
	 * 実行終了ログ編集
	 * @param command コマンド
	 * @param keys キー
	 * @param startTime Redisコマンド実行直前の時間
	 * @return 実行終了ログ文字列
	 */
	public static String getEndLog(String command, String[] keys, long startTime) {
		StringBuilder sb = new StringBuilder();
		sb.append("[Redis] ");
		sb.append(command);
		sb.append(" end : keys=[");
		boolean isFirst = true;
		for (String key : keys) {
			if (isFirst) {
				isFirst = false;
			} else {
				sb.append(", ");
			}
			sb.append(key);
		}
		sb.append("]");

		sb.append(getElapsedTimeLog(startTime));
		return sb.toString();
	}

	/**
	 * 実行終了ログ編集
	 * @param command コマンド
	 * @param keys キー
	 * @return 実行開始ログ文字列
	 */
	public static String getEndLog(String command, byte[][] keysBytes, long startTime) {
		String[] keys = new String[keysBytes.length];
		for (int i = 0; i < keys.length; i++) {
			keys[i] = getString(keysBytes[i]);
		}
		return getEndLog(command, keys, startTime);
	}

	/**
	 * 経過時間ログ表記を取得
	 * @param startTime 開始時間
	 * @return 経過時間ログ表記
	 */
	private static String getElapsedTimeLog(long startTime) {
		long finishTime = new Date().getTime();
		long time = finishTime - startTime;
		StringBuilder sb = new StringBuilder();
		//sb.append(", elapsed time=");
		sb.append(" - ");
		sb.append(time);
		sb.append("ms");
		return sb.toString();
	}

	/**
	 * バイト配列を文字列に変換.
	 * @param bytes バイト配列
	 * @return 文字列
	 */
	private static String getString(byte[] bytes) {
		String ret = null;
		try {
			ret = new String(bytes, Constants.ENCODING);
		} catch (UnsupportedEncodingException e) {
			if (logger.isDebugEnabled()) {
				logger.debug("UnsupportedEncodingException: " + e.getMessage());
			}
		}
		return ret;
	}

	/**
	 * Jedis.info情報をMapに変換.
	 * 以下の形式の情報を、コロンでキーと値に分ける。
	 *
	 * # Replication
	 * role:master
	 * connected_slaves:0
	 * master_repl_offset:0
	 * repl_backlog_active:0
	 * repl_backlog_size:1048576
	 * repl_backlog_first_byte_offset:0
	 * repl_backlog_histlen:0
	 *
	 * @param infoStr Jedis.infoの値
	 * @return Jedis.infoの値をMapに変換したもの
	 */
	public static Map<String, String> convertInfo(String infoStr) {
		if (StringUtils.isBlank(infoStr)) {
			return null;
		}
		BufferedReader reader = null;
		Map<String, String> infoMap = new HashMap<String, String>();
		try {
			reader = new BufferedReader(new StringReader(infoStr));
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.length() == 0) {
					continue;
				}
				if (line.startsWith(JedisConst.REDIS_INFO_COMMENT)) {
					continue;
				}
				int idx = line.indexOf(JedisConst.REDIS_INFO_DELIMITER);
				String name = null;
				String value = null;
				if (idx > 0) {
					name = line.substring(0, idx);
					value = line.substring(idx + 1);
				} else {
					name = line;
					value = "";
				}
				infoMap.put(name, value);
			}
		} catch (IOException e) {
			logger.warn("[convertInfo] Error occured.", e);
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					logger.warn("[convertInfo] Error occured.", e);
				}
			}
		}
		return infoMap;
	}

	/**
	 * 文字列をバイト配列に変換する.
	 * エンコーディングエラーの場合、IllegalStateExceptionをスローする。
	 * @param str 文字列
	 * @return バイト配列
	 */
	public static byte[] getBytes(String str) {
		if (StringUtils.isBlank(str)) {
			return null;
		}
		try {
			return str.getBytes(Constants.ENCODING);
		} catch (UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * feedをバイト配列に変換
	 * @param feed Feed
	 * @param mapper ResourceMapper
	 * @return バイト配列
	 */
	public static byte[] serializeFeed(FeedBase feed, FeedTemplateMapper mapper)
	throws IOException {
		if (feed == null || mapper == null) {
			return null;
		}
		return mapper.toMessagePack(feed);
	}

	/**
	 * entryをバイト配列に変換
	 * @param entry Entry
	 * @param mapper ResourceMapper
	 * @return バイト配列
	 */
	public static byte[] serializeEntry(EntryBase entry, FeedTemplateMapper mapper)
	throws IOException {
		if (entry == null || mapper == null) {
			return null;
		}
		return mapper.toMessagePack(entry);
	}

	/**
	 * バイト配列をfeedに変換
	 * @param data バイト配列
	 * @param mapper ResourceMapper
	 * @return Feed
	 */
	public static FeedBase deserializeFeed(byte[] data, FeedTemplateMapper mapper)
	throws IOException {
		if (data == null || mapper == null) {
			return null;
		}
		try {
			return (FeedBase)mapper.fromMessagePack(data, true);
		} catch (ClassNotFoundException e) {
			throw new IOException(e);
		}
	}

	/**
	 * バイト配列をentryに変換
	 * @param data バイト配列
	 * @param mapper ResourceMapper
	 * @return Entry
	 */
	public static EntryBase deserializeEntry(byte[] data, FeedTemplateMapper mapper)
	throws IOException {
		if (data == null || mapper == null) {
			return null;
		}
		try {
			return (EntryBase)mapper.fromMessagePack(data, false);
		} catch (ClassNotFoundException e) {
			throw new IOException(e);
		}
	}

	/**
	 * Redisアクセス失敗時リトライ総数を取得.
	 * @return Redisアクセス失敗時リトライ総数
	 */
	public static int getRedisRetryCount() {
		return ReflexEnvUtil.getSystemPropInt(JedisConst.PROP_REDIS_RETRY_COUNT,
				JedisConst.REDIS_RETRY_COUNT_DEFAULT);
	}

	/**
	 * Redisアクセス失敗時リトライ総数を取得.
	 * @return Redisアクセス失敗時リトライ総数
	 */
	public static int getRedisRetryWaitmillis() {
		return ReflexEnvUtil.getSystemPropInt(JedisConst.PROP_REDIS_RETRY_WAITMILLIS,
				JedisConst.REDIS_RETRY_WAITMILLIS_DEFAULT);
	}

	/**
	 * Redisコネクション取得失敗時リトライ総数を取得.
	 * @return Redisコネクション取得失敗時リトライ総数
	 */
	public static int getRedisGetconnectionRetryCount() {
		return ReflexEnvUtil.getSystemPropInt(JedisConst.PROP_REDIS_GETCONNECTION_RETRY_COUNT,
				JedisConst.REDIS_GETCONNECTION_RETRY_COUNT_DEFAULT);
	}

	/**
	 * Redisコネクション取得失敗時リトライ総数を取得.
	 * @return Redisコネクション取得失敗時リトライ総数
	 */
	public static int getRedisGetconnectionRetryWaitmillis() {
		return ReflexEnvUtil.getSystemPropInt(JedisConst.PROP_REDIS_GETCONNECTION_RETRY_WAITMILLIS,
				JedisConst.REDIS_GETCONNECTION_RETRY_WAITMILLIS_DEFAULT);
	}

	/**
	 * SIDの長さを取得.
	 * @return SIDの長さ
	 */
	public static int getSidLength() {
		return ReflexEnvUtil.getSystemPropInt(JedisConst.PROP_SID_LENGTH,
				JedisConst.SID_LENGTH_DEFAULT);
	}

	/**
	 * SID発行時の重複時リトライ総数を取得.
	 * @return SID発行時の重複時リトライ総数
	 */
	public static int getSidRetryCount() {
		return ReflexEnvUtil.getSystemPropInt(JedisConst.PROP_SID_RETRY_COUNT,
				JedisConst.SID_RETRY_COUNT_DEFAULT);
	}

	/**
	 * Redisプールの再生成を行わない期間(ミリ秒)を取得.
	 * @return Redisプールの再生成を行わない期間(ミリ秒)
	 */
	public static int getRedisWithoutRecreateMillisec() {
		return ReflexEnvUtil.getSystemPropInt(JedisConst.PROP_REDIS_WITHOUT_RECREATE_SEC,
				JedisConst.REDIS_WITHOUT_RECREATE_SEC_DEFAULT) * 1000;
	}

	/**
	 * Redisの再起動ロック失敗をクリアする回数を取得.
	 * @return Redisの再起動ロック失敗をクリアする回数
	 */
	public static int getRedisClearLockfailedNum() {
		return ReflexEnvUtil.getSystemPropInt(JedisConst.PROP_REDIS_CLEAR_LOCKFAILED_NUM,
				JedisConst.REDIS_CLEAR_LOCKFAILED_NUM_DEFAULT) * 1000;
	}

	/**
	 * コネクションオープン・クローズ時のログメッセージ
	 * @param action open/close
	 * @param poolName コネクションプール名
	 * @param jedis Jedisオブジェクト
	 * @param jedisCnts コネクションオープン数
	 * @return メッセージ
	 */
	public static String getConnectionLogMessage(String action, String poolName,
			Jedis jedis, int[] jedisCnts) {
		StringBuilder sb = new StringBuilder();
		// プールごとのコネクションオープン数
		sb.append("[jedis ");
		sb.append(action);
		sb.append(" (");
		sb.append(poolName);
		sb.append(")] JedisCnt of open = ");
		sb.append(jedisCnts[0]);
		// 総コネクションオープン数
		sb.append(" (total = ");
		sb.append(jedisCnts[1]);
		sb.append("), hashCode = ");
		sb.append(jedis.hashCode());
		return sb.toString();
	}

	/**
	 * Redisへのアクセスログを出力するかどうかを取得.
	 * @return Redisへのアクセスログを出力する場合true
	 */
	public static boolean isEnableAccessLog() {
		return ReflexEnvUtil.getSystemPropBoolean(
				JedisConst.PROP_REDIS_ENABLE_ACCESSLOG, false);
	}

	/**
	 * サーバ稼働中かどうか.
	 * 起動処理中の場合falseを返す。
	 * @return サーバ稼働中の場合true
	 */
	public static boolean isRunning() {
		return ReflexEnvUtil.isRunning();
	}

	/**
	 * Jedisオブジェクトを生成.
	 * コネクション情報に追加。
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return Jedisコネクション
	 * @throws IOException IOエラー。causeはJedisException。
	 */
	public static void reCreateJedisPool(RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException {
		JedisEnv jedisEnv = getJedisEnv();
		jedisEnv.reCreateJedisPool();
	}

}
