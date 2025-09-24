package jp.reflexworks.taggingservice.redis;

import java.io.IOException;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.util.RetryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * Jedis基本操作クラス.
 * リトライ処理を組み込み。
 */
public class JedisCommonManager {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * キャッシュにバイト配列を登録.
	 * 既にデータが存在する場合は上書き。
	 * @param key キー
	 * @param val バイト配列
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 登録したバイト配列
	 */
	protected byte[] setBytesProc(byte[] key, byte[] val,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		return setBytesProc(key, val, null, requestInfo, connectionInfo);
	}

	/**
	 * キャッシュにバイト配列を登録.
	 * 既にデータが存在する場合は上書き。
	 * @param key キー
	 * @param val バイト配列
	 * @param sec 有効時間(秒)
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 登録したバイト配列
	 */
	protected byte[] setBytesProc(byte[] key, byte[] val, Integer sec,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		// リトライ回数
		int numRetries = JedisUtil.getRedisRetryCount();
		int waitMillis = JedisUtil.getRedisRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				JedisConnection jedisConn = JedisUtil.getWriteConnection(requestInfo, connectionInfo);
				if (sec != null && sec > 0) {
					jedisConn.setex(key, sec, val);
				} else {
					jedisConn.set(key, val);
				}
				return val;

			} catch (IOException e) {
				checkRetry(e, r, numRetries, waitMillis, requestInfo);
			}
		}

		// Unreachable code
		throw new IllegalStateException("Unreachable code");
	}

	/**
	 * キャッシュに文字列を登録.
	 * 既にデータが存在する場合は上書き。
	 * @param key キー
	 * @param val 文字列
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 登録した文字列
	 */
	protected String setStringProc(String key, String val,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		return setStringProc(key, val, null, requestInfo, connectionInfo);
	}

	/**
	 * キャッシュに文字列を登録.
	 * 既にデータが存在する場合は上書き。
	 * @param key キー
	 * @param val 文字列
	 * @param sec 有効時間(秒)
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 登録した文字列
	 */
	protected String setStringProc(String key, String val, Integer sec,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		// リトライ回数
		int numRetries = JedisUtil.getRedisRetryCount();
		int waitMillis = JedisUtil.getRedisRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				JedisConnection jedisConn = JedisUtil.getWriteConnection(requestInfo, connectionInfo);
				if (sec != null && sec > 0) {
					jedisConn.setex(key, sec, val);
				} else {
					jedisConn.set(key, val);
				}
				return val;

			} catch (IOException e) {
				checkRetry(e, r, numRetries, waitMillis, requestInfo);
			}
		}

		// Unreachable code
		throw new IllegalStateException("Unreachable code");
	}

	/**
	 * データが存在しない場合のみキャッシュにFeedを登録.
	 * @param name キー
	 * @param feed Feed
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 登録できた場合true、すでにデータが存在する場合false
	 */
	protected boolean setBytesIfAbsentProc(byte[] key, byte[] val,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		// リトライ回数
		int numRetries = JedisUtil.getRedisRetryCount();
		int waitMillis = JedisUtil.getRedisRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				JedisConnection jedisConn = JedisUtil.getWriteConnection(requestInfo, connectionInfo);
				Long retLong = jedisConn.setnx(key, val);
				// 登録成功は1、失敗は0が返る。
				boolean ret = false;
				if (retLong == 1) {
					ret = true;
				}
				return ret;

			} catch (IOException e) {
				checkRetry(e, r, numRetries, waitMillis, requestInfo);
			}
		}

		// Unreachable code
		throw new IllegalStateException("Unreachable code");
	}

	/**
	 * データが存在しない場合のみキャッシュに文字列を登録.
	 * @param key キー
	 * @param val 文字列
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 登録できた場合true、すでにデータが存在する場合false
	 */
	protected boolean setStringIfAbsentProc(String key, String val,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		// リトライ回数
		int numRetries = JedisUtil.getRedisRetryCount();
		int waitMillis = JedisUtil.getRedisRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				JedisConnection jedisConn = JedisUtil.getWriteConnection(requestInfo, connectionInfo);
				Long retLong = jedisConn.setnx(key, val);
				// 登録成功は1、失敗は0が返る。
				boolean ret = false;
				if (retLong == 1) {
					ret = true;
				}
				return ret;

			} catch (IOException e) {
				checkRetry(e, r, numRetries, waitMillis, requestInfo);
			}
		}

		// Unreachable code
		throw new IllegalStateException("Unreachable code");
	}

	/**
	 * キャッシュに加算.
	 * @param key キー
	 * @param val 加算値
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	protected Long incrementProc(String key, long val, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException {
		// リトライ回数
		int numRetries = JedisUtil.getRedisRetryCount();
		int waitMillis = JedisUtil.getRedisRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				JedisConnection jedisConn = JedisUtil.getWriteConnection(requestInfo, connectionInfo);
				if (val == 1) {
					return jedisConn.incr(key);
				} else {
					return jedisConn.incrBy(key, val);
				}

			} catch (IOException e) {
				checkRetry(e, r, numRetries, waitMillis, requestInfo);
			}
		}

		// Unreachable code
		throw new IllegalStateException("Unreachable code");
	}

	/**
	 * キャッシュからバイト配列データを削除.
	 * @param key キー
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 正常に削除の場合true、データ存在なしの場合false
	 */
	protected boolean deleteBytesProc(byte[] key, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException {
		// リトライ回数
		int numRetries = JedisUtil.getRedisRetryCount();
		int waitMillis = JedisUtil.getRedisRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				JedisConnection jedisConn = JedisUtil.getWriteConnection(requestInfo, connectionInfo);
				Long ret = jedisConn.del(key);
				return ret != null && ret.longValue() > 0;

			} catch (IOException e) {
				checkRetry(e, r, numRetries, waitMillis, requestInfo);
			}
		}

		// Unreachable code
		throw new IllegalStateException("Unreachable code");
	}

	/**
	 * キャッシュから文字列データを削除.
	 * @param key キー
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo ConnectionInfo
	 * @return 正常に削除の場合true、データ存在なしの場合false
	 */
	protected boolean deleteStringProc(String key, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException {
		// リトライ回数
		int numRetries = JedisUtil.getRedisRetryCount();
		int waitMillis = JedisUtil.getRedisRetryWaitmillis();

		for (int r = 0; r <= numRetries; r++) {
			try {
				JedisConnection jedisConn = JedisUtil.getWriteConnection(requestInfo, connectionInfo);
				Long ret = jedisConn.del(key);
				return ret != null && ret.longValue() > 0;

			} catch (IOException e) {
				checkRetry(e, r, numRetries, waitMillis, requestInfo);
			}
		}

		// Unreachable code
		throw new IllegalStateException("Unreachable code");
	}

	/**
	 * キャッシュからバイト配列を取得.
	 * @param key キー
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return バイト配列
	 */
	protected byte[] getBytesProc(byte[] key, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException {
		// リトライ回数
		int numRetries = JedisUtil.getRedisRetryCount();
		int waitMillis = JedisUtil.getRedisRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				JedisConnection jedisConn = JedisUtil.getReadConnection(requestInfo, connectionInfo);
				return jedisConn.get(key);

			} catch (IOException e) {
				checkRetry(e, r, numRetries, waitMillis, requestInfo);
			}
		}

		// Unreachable code
		throw new IllegalStateException("Unreachable code");
	}

	/**
	 * キャッシュから文字列を取得.
	 * @param key キー
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 文字列
	 */
	protected String getStringProc(String key, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException {
		// リトライ回数
		int numRetries = JedisUtil.getRedisRetryCount();
		int waitMillis = JedisUtil.getRedisRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				JedisConnection jedisConn = JedisUtil.getReadConnection(requestInfo, connectionInfo);
				return jedisConn.get(key);

			} catch (IOException e) {
				checkRetry(e, r, numRetries, waitMillis, requestInfo);
			}
		}

		// Unreachable code
		throw new IllegalStateException("Unreachable code");
	}

	/**
	 * Entryキャッシュの有効期限を指定
	 * @param name キー
	 * @param expire 有効期間(秒)
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 正常に設定の場合true、データ存在なしの場合false
	 */
	protected boolean setExpireProc(byte[] key, int expire, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException {
		// リトライ回数
		int numRetries = JedisUtil.getRedisRetryCount();
		int waitMillis = JedisUtil.getRedisRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				JedisConnection jedisConn = JedisUtil.getWriteConnection(requestInfo, connectionInfo);
				Long ret = jedisConn.expire(key, expire);
				return ret != null && ret.longValue() > 0;

			} catch (IOException e) {
				checkRetry(e, r, numRetries, waitMillis, requestInfo);
			}
		}

		// Unreachable code
		throw new IllegalStateException("Unreachable code");
	}

	/**
	 * Entryキャッシュの有効期限を指定
	 * @param name キー
	 * @param expire 有効期間(秒)
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 正常に設定の場合true、データ存在なしの場合false
	 */
	protected boolean setExpireProc(String key, int expire, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException {
		// リトライ回数
		int numRetries = JedisUtil.getRedisRetryCount();
		int waitMillis = JedisUtil.getRedisRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				JedisConnection jedisConn = JedisUtil.getWriteConnection(requestInfo, connectionInfo);
				Long ret = jedisConn.expire(key, expire);
				return ret != null && ret.longValue() > 0;

			} catch (IOException e) {
				checkRetry(e, r, numRetries, waitMillis, requestInfo);
			}
		}

		// Unreachable code
		throw new IllegalStateException("Unreachable code");
	}

	/**
	 * ハッシュ型キャッシュにバイト配列データを登録.
	 * 既にデータが存在する場合は上書き。
	 * @param sid SID
	 * @param name キー
	 * @param feed Feed
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 登録したバイト配列データ
	 */
	protected byte[] hsetBytesProc(byte[] key1, byte[] key2, byte[] val,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		// リトライ回数
		int numRetries = JedisUtil.getRedisRetryCount();
		int waitMillis = JedisUtil.getRedisRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				JedisConnection jedisConn = JedisUtil.getWriteConnection(requestInfo, connectionInfo);
				jedisConn.hset(key1, key2, val);
				return val;

			} catch (IOException e) {
				checkRetry(e, r, numRetries, waitMillis, requestInfo);
			}
		}

		// Unreachable code
		throw new IllegalStateException("Unreachable code");
	}

	/**
	 * ハッシュ型キャッシュにバイト配列データを登録.
	 * 既にデータが存在する場合は登録しない。
	 * @param sid SID
	 * @param name キー
	 * @param feed Feed
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 登録数 (成功:1、失敗:0)
	 */
	protected Long hsetnxBytesProc(byte[] key1, byte[] key2, byte[] val,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		// リトライ回数
		int numRetries = JedisUtil.getRedisRetryCount();
		int waitMillis = JedisUtil.getRedisRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				JedisConnection jedisConn = JedisUtil.getWriteConnection(requestInfo, connectionInfo);
				return jedisConn.hsetnx(key1, key2, val);

			} catch (IOException e) {
				checkRetry(e, r, numRetries, waitMillis, requestInfo);
			}
		}

		// Unreachable code
		throw new IllegalStateException("Unreachable code");
	}

	/**
	 * ハッシュ型キャッシュに文字列を登録.
	 * 既にデータが存在する場合は上書き。
	 * @param sid SID
	 * @param name キー
	 * @param text 文字列
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 登録した文字列
	 */
	protected String hsetStringProc(String key1, String key2, String text,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		// リトライ回数
		int numRetries = JedisUtil.getRedisRetryCount();
		int waitMillis = JedisUtil.getRedisRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				JedisConnection jedisConn = JedisUtil.getWriteConnection(requestInfo, connectionInfo);
				jedisConn.hset(key1, key2, text);
				return text;

			} catch (IOException e) {
				checkRetry(e, r, numRetries, waitMillis, requestInfo);
			}
		}

		// Unreachable code
		throw new IllegalStateException("Unreachable code");
	}

	/**
	 * ハッシュ型キャッシュに文字列を登録.
	 * 既にデータが存在する場合は登録しない。
	 * @param sid SID
	 * @param name キー
	 * @param text 文字列
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 登録数 (成功:1、失敗:0)
	 */
	protected Long hsetnxStringProc(String key1, String key2, String text,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		// リトライ回数
		int numRetries = JedisUtil.getRedisRetryCount();
		int waitMillis = JedisUtil.getRedisRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				JedisConnection jedisConn = JedisUtil.getWriteConnection(requestInfo, connectionInfo);
				return jedisConn.hsetnx(key1, key2, text);

			} catch (IOException e) {
				checkRetry(e, r, numRetries, waitMillis, requestInfo);
			}
		}

		// Unreachable code
		throw new IllegalStateException("Unreachable code");
	}

	/**
	 * ハッシュ型キャッシュに数値を加算.
	 * 既にデータが存在する場合は上書き。
	 * @param sid SID
	 * @param name キー
	 * @param num 数値
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 加算後の数値
	 */
	protected Long hincrByProc(String key1, String key2, long num,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		// リトライ回数
		int numRetries = JedisUtil.getRedisRetryCount();
		int waitMillis = JedisUtil.getRedisRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				JedisConnection jedisConn = JedisUtil.getWriteConnection(requestInfo, connectionInfo);
				return jedisConn.hincrBy(key1, key2, num);

			} catch (IOException e) {
				checkRetry(e, r, numRetries, waitMillis, requestInfo);
			}
		}

		// Unreachable code
		throw new IllegalStateException("Unreachable code");
	}

	/**
	 * ハッシュ型キャッシュからバイト配列データを削除.
	 * @param key1 第一キー
	 * @param key2 第ニキー
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	protected void hdelBytesProc(byte[] key1, byte[] key2,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
			throws IOException {
		// リトライ回数
		int numRetries = JedisUtil.getRedisRetryCount();
		int waitMillis = JedisUtil.getRedisRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				JedisConnection jedisConn = JedisUtil.getWriteConnection(requestInfo, connectionInfo);
				jedisConn.hdel(key1, key2);
				return;

			} catch (IOException e) {
				checkRetry(e, r, numRetries, waitMillis, requestInfo);
			}
		}

		// Unreachable code
		throw new IllegalStateException("Unreachable code");
	}

	/**
	 * ハッシュ型キャッシュから文字列を削除.
	 * @param key1 第一キー
	 * @param key2 第ニキー
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	protected void hdelStringProc(String key1, String key2,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		// リトライ回数
		int numRetries = JedisUtil.getRedisRetryCount();
		int waitMillis = JedisUtil.getRedisRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				JedisConnection jedisConn = JedisUtil.getWriteConnection(requestInfo, connectionInfo);
				jedisConn.hdel(key1, key2);
				return;

			} catch (IOException e) {
				checkRetry(e, r, numRetries, waitMillis, requestInfo);
			}
		}

		// Unreachable code
		throw new IllegalStateException("Unreachable code");
	}

	/**
	 * ハッシュ型キャッシュからバイト配列データを取得.
	 * @param key1 第一キー
	 * @param key2 第ニキー
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return byte[]データ
	 */
	protected byte[] hgetBytesProc(byte[] key1, byte[] key2,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		// リトライ回数
		int numRetries = JedisUtil.getRedisRetryCount();
		int waitMillis = JedisUtil.getRedisRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				JedisConnection jedisConn = JedisUtil.getReadConnection(requestInfo, connectionInfo);
				return jedisConn.hget(key1, key2);

			} catch (IOException e) {
				checkRetry(e, r, numRetries, waitMillis, requestInfo);
			}
		}

		// Unreachable code
		throw new IllegalStateException("Unreachable code");
	}

	/**
	 * ハッシュ型キャッシュから文字列を取得.
	 * @param key1 第一キー
	 * @param key2 第ニキー
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 文字列
	 */
	protected String hgetStringProc(String key1, String key2,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		// リトライ回数
		int numRetries = JedisUtil.getRedisRetryCount();
		int waitMillis = JedisUtil.getRedisRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				JedisConnection jedisConn = JedisUtil.getReadConnection(requestInfo, connectionInfo);
				return jedisConn.hget(key1, key2);

			} catch (IOException e) {
				checkRetry(e, r, numRetries, waitMillis, requestInfo);
			}
		}

		// Unreachable code
		throw new IllegalStateException("Unreachable code");
	}

	/**
	 * ハッシュ型キャッシュから第ニキー一覧を取得.
	 * @param key1 第一キー
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return ハッシュ型キャッシュの第ニキー一覧
	 */
	protected Set<String> hkeysProc(String key1,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		// リトライ回数
		int numRetries = JedisUtil.getRedisRetryCount();
		int waitMillis = JedisUtil.getRedisRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				JedisConnection jedisConn = JedisUtil.getReadConnection(requestInfo, connectionInfo);
				return jedisConn.hkeys(key1);

			} catch (IOException e) {
				checkRetry(e, r, numRetries, waitMillis, requestInfo);
			}
		}

		// Unreachable code
		throw new IllegalStateException("Unreachable code");
	}

	/**
	 * 指定されたパターンのキーリストを取得.
	 * @param pattern キーのパターン
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return キーリスト
	 */
	protected Set<String> keys(String pattern,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		// リトライ回数
		int numRetries = JedisUtil.getRedisRetryCount();
		int waitMillis = JedisUtil.getRedisRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				JedisConnection jedisConn = JedisUtil.getReadConnection(requestInfo, connectionInfo);
				return jedisConn.keys(pattern);

			} catch (IOException e) {
				checkRetry(e, r, numRetries, waitMillis, requestInfo);
			}
		}

		// Unreachable code
		throw new IllegalStateException("Unreachable code");
	}

	/**
	 * Redisの全てのデータを削除.
	 * メンテナンス用
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 実行結果
	 */
	protected String flushAll(RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		// リトライ回数
		int numRetries = JedisUtil.getRedisRetryCount();
		int waitMillis = JedisUtil.getRedisRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				JedisConnection jedisConn = JedisUtil.getWriteConnection(requestInfo, connectionInfo);
				return jedisConn.flushAll();

			} catch (IOException e) {
				checkRetry(e, r, numRetries, waitMillis, requestInfo);
			}
		}

		// Unreachable code
		throw new IllegalStateException("Unreachable code");
	}

	/**
	 * Feedをバイト配列に変換する.
	 * @param feed Feed
	 * @param mapper FeedTemplateMapper
	 * @return バイト配列
	 */
	protected byte[] serializeFeed(FeedBase feed, FeedTemplateMapper mapper)
	throws IOException {
		return JedisUtil.serializeFeed(feed, mapper);
	}

	/**
	 * Entryをバイト配列に変換する.
	 * @param entry Entry
	 * @param mapper FeedTemplateMapper
	 * @return バイト配列
	 */
	protected byte[] serializeEntry(EntryBase entry, FeedTemplateMapper mapper)
	throws IOException {
		return JedisUtil.serializeEntry(entry, mapper);
	}

	/**
	 * バイト配列をFeedに変換する.
	 * @param data FeedのMessagePack
	 * @param mapper FeedTemplateMapper
	 * @return Feed
	 */
	protected FeedBase deserializeFeed(byte[] data, FeedTemplateMapper mapper)
	throws IOException {
		return JedisUtil.deserializeFeed(data, mapper);
	}

	/**
	 * バイト配列をEntryに変換する.
	 * @param data EntryのMessagePack
	 * @param mapper FeedTemplateMapper
	 * @return Entry
	 */
	protected EntryBase deserializeEntry(byte[] data, FeedTemplateMapper mapper)
	throws IOException {
		return JedisUtil.deserializeEntry(data, mapper);
	}

	/**
	 * キーのサービス名接頭辞を取得.
	 *  ・サービス名から名前空間を取得し、「{名前空間}#」を返す。
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return サービス名接頭辞
	 */
	protected String getKeyPrefix(String serviceName, String namespace, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException {
		return namespace + JedisConst.TBL_SERVICENAME_DELIMITER;
	}

	/**
	 * 整数を文字列に変換
	 * @param val 整数
	 * @return 文字列
	 */
	protected String convertLongToString(long val) {
		return String.valueOf(val);
	}

	/**
	 * 文字列を整数に変換.
	 * nullの場合nullを返却
	 * @param val 整数文字列
	 * @return 整数
	 */
	protected Long convertStringToLong(String val) {
		if (val == null) {
			return null;
		}
		return StringUtils.longValue(val, 0);
	}

	/**
	 * リトライチェック
	 * @param e 例外
	 * @param r リトライ回数
	 * @param numRetries リトライ総数
	 * @param waitMillis リトライ時の待ち時間(ミリ秒)
	 * @param requestInfo リクエスト情報
	 */
	private void checkRetry(IOException e, int r, int numRetries, int waitMillis,
			RequestInfo requestInfo)
	throws IOException {
		// コネクション取得時の混み合いエラーはリトライ対象
		if (r < numRetries) {
			/* jedis-4.2.2では JedisExhaustedPoolException 例外クラスがない。
			Throwable cause = e.getCause();
			if (cause != null && cause instanceof
					redis.clients.jedis.exceptions.JedisExhaustedPoolException) {
				if (logger.isDebugEnabled()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
							RetryUtil.getRetryLog(e, r));
				}
				RetryUtil.sleep(waitMillis);
				return;
			}
			*/
		}
		RetryUtil.checkRetry(e, r, numRetries, waitMillis, requestInfo);
	}

}
