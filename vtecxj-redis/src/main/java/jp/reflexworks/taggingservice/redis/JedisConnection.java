package jp.reflexworks.taggingservice.redis;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.conn.ReflexConnection;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Response;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.params.SortingParams;

/**
 * Redisコネクション.
 * Reflexでコネクションを管理するためのオブジェクト
 */
public class JedisConnection implements ReflexConnection<Jedis> {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/** Redisコネクション */
	private Jedis jedis;
	/** Redisトランザクション */
	private Transaction tran;
	/** コネクション情報名 */
	private String connName;
	/** コネクション情報 */
	private ConnectionInfo connectionInfo;
	/** プール名 */
	private String poolName;

	/**
	 * コンストラクタ.
	 * @param pJedis Redisコネクション
	 * @param connName コネクション情報名
	 * @param connectionInfo コネクション情報
	 * @param poolName プール名
	 */
	public JedisConnection(Jedis pJedis, String connName, ConnectionInfo connectionInfo,
			String poolName) {
		this.jedis = pJedis;
		this.connName = connName;
		this.connectionInfo = connectionInfo;
		this.poolName = poolName;
	}

	/**
	 * コネクションオブジェクトを取得.
	 * @return コネクションオブジェクト
	 */
	public Jedis getConnection() {
		return jedis;
	}

	/**
	 * コネクション情報名を取得.
	 * @return コネクション情報名
	 */
	public String getName() {
		return connName;
	}

	/**
	 * クローズ処理.
	 * この処理はTaggingServiceが行いますので、サービスでは実行しないでください。
	 */
	public void close() {
		try {
			String command = "close";
			long startTime = 0;
			int hash = 0;
			if (isEnableAccessLog()) {
				hash = jedis.hashCode();
				logger.debug(JedisUtil.getStartLog(command, hash));
				startTime = new Date().getTime();
			}
			jedis.close();
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, startTime, hash));
			}

		} catch (Throwable e) {
			logger.warn("Jedis close error ", e);
		}
	}

	/**
	 * トランザクション開始.
	 * @param keys watch対象キー
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public void beginTransaction(final byte[] ... keys)
			throws IOException {
		try {
			if (keys != null && keys.length > 0) {
				String command = "watch";
				long startTime = 0;
				if (isEnableAccessLog()) {
					logger.debug(JedisUtil.getStartLog(command));
					startTime = new Date().getTime();
				}
				jedis.watch(keys);
				if (isEnableAccessLog()) {
					logger.debug(JedisUtil.getEndLog(command, startTime));
				}
			}
			String command = "multi";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command));
				startTime = new Date().getTime();
			}
			tran = jedis.multi();
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, startTime));
			}
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * コミット.
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public List<Object> exec()
			throws IOException {
		if (tran != null) {
			try {
				String command = "exec";
				long startTime = 0;
				if (isEnableAccessLog()) {
					logger.debug(JedisUtil.getStartLog(command));
					startTime = new Date().getTime();
				}
				List<Object> ret = tran.exec();
				if (isEnableAccessLog()) {
					logger.debug(JedisUtil.getEndLog(command, startTime));
				}
				tran = null;
				return ret;
			} catch (JedisException e) {
				throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
			}
		}
		return null;
	}

	/**
	 * ロールバック.
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public void discard()
			throws IOException {
		if (tran != null) {
			try {
				String command = "discard";
				long startTime = 0;
				if (isEnableAccessLog()) {
					logger.debug(JedisUtil.getStartLog(command));
					startTime = new Date().getTime();
				}
				tran.discard();
				if (isEnableAccessLog()) {
					logger.debug(JedisUtil.getEndLog(command, startTime));
				}
				tran = null;
			} catch (JedisException e) {
				throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
			}
		}
	}

	/**
	 * トランザクションによる削除.
	 * @param key キー
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public Response<Long> delByTran(String key)
			throws IOException {
		if (tran != null) {
			try {
				String command = "del(tran)";
				long startTime = 0;
				if (isEnableAccessLog()) {
					logger.debug(JedisUtil.getStartLog(command, key));
					startTime = new Date().getTime();
				}
				Response<Long> ret = tran.del(key);
				if (isEnableAccessLog()) {
					logger.debug(JedisUtil.getEndLog(command, key, startTime));
				}
				return ret;
			} catch (JedisException e) {
				throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
			}
		}
		return null;
	}

	/**
	 * トランザクションによる削除.
	 * @param key キー
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public Response<Long> delByTran(byte[] key)
			throws IOException {
		if (tran != null) {
			try {
				String command = "del(tran)";
				long startTime = 0;
				if (isEnableAccessLog()) {
					logger.debug(JedisUtil.getStartLog(command, key));
					startTime = new Date().getTime();
				}
				Response<Long> ret = tran.del(key);
				if (isEnableAccessLog()) {
					logger.debug(JedisUtil.getEndLog(command, key, startTime));
				}
				return ret;
			} catch (JedisException e) {
				throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
			}
		}
		return null;
	}

	/**
	 * 削除.
	 * @param key キー
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public Long del(String key)
			throws IOException {
		try {
			String command = "del";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key));
				startTime = new Date().getTime();
			}
			Long ret = jedis.del(key);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * 削除.
	 * @param key キー
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public Long del(byte[] key)
			throws IOException {
		try {
			String command = "del";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key));
				startTime = new Date().getTime();
			}
			Long ret = jedis.del(key);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * 削除.
	 * @param key キー
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public Long del(String[] key)
			throws IOException {
		try {
			String command = "del";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key));
				startTime = new Date().getTime();
			}
			Long ret = jedis.del(key);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * 文字列型の値を登録.
	 * @param key キー
	 * @param val 値
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public Response<String> setByTran(String key, String val)
			throws IOException {
		if (tran != null) {
			try {
				String command = "set(tran)";
				long startTime = 0;
				if (isEnableAccessLog()) {
					logger.debug(JedisUtil.getStartLog(command, key, val));
					startTime = new Date().getTime();
				}
				Response<String> ret = tran.set(key, val);
				if (isEnableAccessLog()) {
					logger.debug(JedisUtil.getEndLog(command, key, startTime));
				}
				return ret;
			} catch (JedisException e) {
				throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
			}
		}
		return null;
	}

	/**
	 * 文字列型の値を登録.
	 * @param key キー
	 * @param val 値
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public Response<String> setByTran(byte[] key, byte[] val)
			throws IOException {
		if (tran != null) {
			try {
				String command = "set(tran)";
				long startTime = 0;
				if (isEnableAccessLog()) {
					logger.debug(JedisUtil.getStartLog(command, key));
					startTime = new Date().getTime();
				}
				Response<String> ret = tran.set(key, val);
				if (isEnableAccessLog()) {
					logger.debug(JedisUtil.getEndLog(command, key, startTime));
				}
				return ret;
			} catch (JedisException e) {
				throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
			}
		}
		return null;
	}

	/**
	 * 文字列型の値を登録.
	 * @param key キー
	 * @param val 値
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public String set(String key, String val)
			throws IOException {
		try {
			String command = "set";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key, val));
				startTime = new Date().getTime();
			}
			String ret = jedis.set(key, val);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * バイト配列型の値を登録.
	 * @param key キー
	 * @param val 値
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public String set(byte[] key, byte[] val)
			throws IOException {
		try {
			String command = "set";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key));
				startTime = new Date().getTime();
			}
			String ret = jedis.set(key, val);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * 文字列型の値を、登録がない場合に登録.
	 * @param key キー
	 * @param val 値
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public Long setnx(String key, String val)
			throws IOException {
		try {
			String command = "setnx";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key, val));
				startTime = new Date().getTime();
			}
			Long ret = jedis.setnx(key, val);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * 文字列型の値を、登録がない場合に登録.
	 * @param key キー
	 * @param val 値
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public Long setnx(byte[] key, byte[] val)
			throws IOException {
		try {
			String command = "setnx";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key, val));
				startTime = new Date().getTime();
			}
			Long ret = jedis.setnx(key, val);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * 文字列型の値を登録.
	 * @param key キー
	 * @param expire 有効時間(秒)
	 * @param val 値
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public String setex(String key, int expire, String val)
			throws IOException {
		try {
			String command = "setex";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key, val, expire));
				startTime = new Date().getTime();
			}
			String ret = jedis.setex(key, expire, val);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * 文字列型の値を登録.
	 * @param key キー
	 * @param expire 有効時間(秒)
	 * @param val 値
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public String setex(byte[] key, int expire, byte[] val)
			throws IOException {
		try {
			String command = "setex";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key, expire));
				startTime = new Date().getTime();
			}
			String ret = jedis.setex(key, expire, val);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * 文字列型の値をまとめて登録.
	 * @param keyValues キーと値
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public String mset(byte[][] keyValues)
			throws IOException {
		try {
			String command = "mset";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLogForMset(command, keyValues));
				startTime = new Date().getTime();
			}
			String ret = jedis.mset(keyValues);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLogForMset(command, keyValues, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * 文字列型の値をまとめて登録.
	 * @param keyValues キーと値
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public String mset(String[] keyValues)
			throws IOException {
		try {
			String command = "mset";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLogForMset(command, keyValues));
				startTime = new Date().getTime();
			}
			String ret = jedis.mset(keyValues);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLogForMset(command, keyValues, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * セットから値を削除.
	 * @param key キー
	 * @param val 値
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public Response<Long> sremByTran(String key, String val)
			throws IOException {
		if (tran != null) {
			try {
				String command = "srem(tran)";
				long startTime = 0;
				if (isEnableAccessLog()) {
					logger.debug(JedisUtil.getStartLog(command, key, val));
					startTime = new Date().getTime();
				}
				Response<Long> ret = tran.srem(key, val);
				if (isEnableAccessLog()) {
					logger.debug(JedisUtil.getEndLog(command, key, val, startTime));
				}
				return ret;
			} catch (JedisException e) {
				throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
			}
		}
		return null;
	}

	/**
	 * セットから値を削除.
	 * @param key キー
	 * @param val 値
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public Long srem(String key, String val)
			throws IOException {
		try {
			String command = "srem";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key, val));
				startTime = new Date().getTime();
			}
			Long ret = jedis.srem(key, val);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, val, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * セットから値を削除.
	 * @param key キー
	 * @param val 値
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public Long srem(String key, String[] val)
			throws IOException {
		try {
			String command = "srem";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key, val));
				startTime = new Date().getTime();
			}
			Long ret = jedis.srem(key, val);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, val, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * セットに値を登録.
	 * @param key キー
	 * @param val 値
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public Response<Long> saddByTran(String key, String val)
			throws IOException {
		if (tran != null) {
			try {
				String command = "sadd(tran)";
				long startTime = 0;
				if (isEnableAccessLog()) {
					logger.debug(JedisUtil.getStartLog(command, key, val));
					startTime = new Date().getTime();
				}
				Response<Long> ret = tran.sadd(key, val);
				if (isEnableAccessLog()) {
					logger.debug(JedisUtil.getEndLog(command, key, val, startTime));
				}
				return ret;
			} catch (JedisException e) {
				throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
			}
		}
		return null;
	}

	/**
	 * セットに値を登録.
	 * @param key キー
	 * @param val 値
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public Long sadd(String key, String val)
			throws IOException {
		try {
			String command = "sadd";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key, val));
				startTime = new Date().getTime();
			}
			Long ret = jedis.sadd(key, val);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, val, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * セットに値を登録.
	 * @param key キー
	 * @param val 値
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public Long sadd(String key, String[] val)
			throws IOException {
		try {
			String command = "sadd";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key, val));
				startTime = new Date().getTime();
			}
			Long ret = jedis.sadd(key, val);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, val, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * ハッシュに値を登録.
	 * @param key キー
	 * @param inKey ハッシュ内のキー
	 * @param val 値
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public Long hset(String key, String inKey, String val)
			throws IOException {
		try {
			String command = "hset";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key, inKey, val));
				startTime = new Date().getTime();
			}
			Long ret = jedis.hset(key, inKey, val);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, inKey, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * ハッシュに値を登録.
	 * @param key キー
	 * @param inKey ハッシュ内のキー
	 * @param val 値
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public Long hset(byte[] key, byte[] inKey, byte[] val)
			throws IOException {
		try {
			String command = "hset";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key, inKey));
				startTime = new Date().getTime();
			}
			Long ret = jedis.hset(key, inKey, val);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, inKey, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * ハッシュに値が存在しない場合のみ値を登録.
	 * @param key キー
	 * @param inKey ハッシュ内のキー
	 * @param val 値
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public Long hsetnx(String key, String inKey, String val)
			throws IOException {
		try {
			String command = "hsetnx";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key, inKey, val));
				startTime = new Date().getTime();
			}
			Long ret = jedis.hsetnx(key, inKey, val);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, inKey, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * ハッシュに値を登録.
	 * @param key キー
	 * @param inKey ハッシュ内のキー
	 * @param val 値
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public Long hsetnx(byte[] key, byte[] inKey, byte[] val)
			throws IOException {
		try {
			String command = "hsetnx";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key, inKey));
				startTime = new Date().getTime();
			}
			Long ret = jedis.hsetnx(key, inKey, val);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, inKey, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * ハッシュの値を削除.
	 * @param key キー
	 * @param inKey ハッシュ内のキー
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public Long hdel(String key, String inKey)
			throws IOException {
		try {
			String command = "hdel";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key, inKey));
				startTime = new Date().getTime();
			}
			Long ret = jedis.hdel(key, inKey);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, inKey, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * ハッシュの値を削除.
	 * @param key キー
	 * @param inKey ハッシュ内のキー
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public Long hdel(byte[] key, byte[] inKey)
			throws IOException {
		try {
			String command = "hdel";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key, inKey));
				startTime = new Date().getTime();
			}
			Long ret = jedis.hdel(key, inKey);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, inKey, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * ハッシュの値を削除.
	 * @param key キー
	 * @param inKey ハッシュ内のキー
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public Long hdel(String key, String[] inKey)
			throws IOException {
		try {
			String command = "hdel";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key, inKey));
				startTime = new Date().getTime();
			}
			Long ret = jedis.hdel(key, inKey);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, inKey, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * ゼットセットから値を削除.
	 * @param key キー
	 * @param val 値
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public Response<Long> zremByTran(String key, String val)
			throws IOException {
		if (tran != null) {
			try {
				String command = "zrem(tran)";
				long startTime = 0;
				if (isEnableAccessLog()) {
					logger.debug(JedisUtil.getStartLog(command, key, val));
					startTime = new Date().getTime();
				}
				 Response<Long> ret = tran.zrem(key, val);
				if (isEnableAccessLog()) {
					logger.debug(JedisUtil.getEndLog(command, key, val, startTime));
				}
				return ret;
			} catch (JedisException e) {
				throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
			}
		}
		return null;
	}

	/**
	 * ゼットセットから値を削除.
	 * @param key キー
	 * @param val 値
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public Long zrem(String key, String val)
			throws IOException {
		try {
			String command = "zrem";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key, val));
				startTime = new Date().getTime();
			}
			Long ret = jedis.zrem(key, val);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, val, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * ゼットセットに値を登録.
	 * @param key キー
	 * @param score スコア
	 * @param val 値
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public Response<Long> zaddByTran(String key, double score, String val)
			throws IOException {
		if (tran != null) {
			try {
				String command = "zadd(tran)";
				long startTime = 0;
				if (isEnableAccessLog()) {
					logger.debug(JedisUtil.getStartLog(command, key, val));
					startTime = new Date().getTime();
				}
				Response<Long> ret = tran.zadd(key, score, val);
				if (isEnableAccessLog()) {
					logger.debug(JedisUtil.getEndLog(command, key, val, startTime));
				}
				return ret;
			} catch (JedisException e) {
				throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
			}
		}
		return null;
	}

	/**
	 * ゼットセットに値を登録.
	 * @param key キー
	 * @param score スコア
	 * @param val 値
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public Long zadd(String key, double score, String val)
			throws IOException {
		try {
			String command = "zadd";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key, val));
				startTime = new Date().getTime();
			}
			Long ret = jedis.zadd(key, score, val);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, val, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * インクリメント.
	 * @param key キー
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public Long incr(String key)
			throws IOException {
		try {
			String command = "incr";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key));
				startTime = new Date().getTime();
			}
			Long ret = jedis.incr(key);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * インクリメント.
	 * @param key キー
	 * @param val 値
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public Long incrBy(String key, long val)
			throws IOException {
		try {
			String command = "incrBy";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key));
				startTime = new Date().getTime();
			}
			Long ret = jedis.incrBy(key, val);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * ハッシュ項目のインクリメント.
	 * @param key キー
	 * @param inKey 第ニキー
	 * @param val 値
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public Long hincrBy(String key, String inKey, long val)
			throws IOException {
		try {
			String command = "hincrBy";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key));
				startTime = new Date().getTime();
			}
			Long ret = jedis.hincrBy(key, inKey, val);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * 文字列の値を取得.
	 * @param key キー
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public String get(String key)
			throws IOException {
		try {
			String command = "get";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key));
				startTime = new Date().getTime();
			}
			String ret = jedis.get(key);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * 文字列の値を取得.
	 * @param key キー
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public byte[] get(byte[] key)
			throws IOException {
		try {
			String command = "get";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key));
				startTime = new Date().getTime();
			}
			byte[] ret = jedis.get(key);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * 文字列の値を取得.
	 * @param key キー
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public List<String> mget(String[] key)
			throws IOException {
		try {
			String command = "mget";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key));
				startTime = new Date().getTime();
			}
			List<String> ret = jedis.mget(key);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * 文字列の値を取得.
	 * @param key キー
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public List<byte[]> mget(byte[][] key)
			throws IOException {
		try {
			String command = "mget";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key));
				startTime = new Date().getTime();
			}
			List<byte[]> ret = jedis.mget(key);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * セットから値をソートして取得.
	 * @param key キー
	 * @param sortingParams ソート条件
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public List<String> sort(String key,
			SortingParams sortingParams)
					throws IOException {
		try {
			String command = "sort";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key));
				startTime = new Date().getTime();
			}
			List<String> ret = jedis.sort(key, sortingParams);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * セットの内容を一括取得.
	 * @param key キー
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public Set<String> smembers(String key)
			throws IOException {
		try {
			String command = "smembers";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key));
				startTime = new Date().getTime();
			}
			Set<String> ret = jedis.smembers(key);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * セットの件数取得.
	 * @param key キー
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public Long scard(String key)
			throws IOException {
		try {
			String command = "scard";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key));
				startTime = new Date().getTime();
			}
			Long ret = jedis.scard(key);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * ハッシュの値を取得.
	 * @param key キー
	 * @param inKey ハッシュ内のキー
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public String hget(String key, String inKey)
			throws IOException {
		try {
			String command = "hget";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key, inKey));
				startTime = new Date().getTime();
			}
			String ret = jedis.hget(key, inKey);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, inKey, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * ハッシュの値を取得.
	 * @param key キー
	 * @param inKey ハッシュ内のキー
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public byte[] hget(byte[] key, byte[] inKey)
			throws IOException {
		try {
			String command = "hget";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key, inKey));
				startTime = new Date().getTime();
			}
			byte[] ret = jedis.hget(key, inKey);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, inKey, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * ハッシュのキー一覧を取得.
	 * @param key キー
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public Set<String> hkeys(String key)
			throws IOException {
		try {
			String command = "hkeys";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key));
				startTime = new Date().getTime();
			}
			Set<String> ret = jedis.hkeys(key);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * ゼットセットから指定された範囲の値を取得.
	 * @param key キー
	 * @param min 最小値
	 * @param max 最大値
	 * @param offset 開始位置
	 * @param limit 最大数
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public List<String> zrangeByScore(String key,
			String min, String max, int offset, int limit)
			throws IOException {
		try {
			String command = "zrangeByScore";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key));
				startTime = new Date().getTime();
			}
			List<String> ret = jedis.zrangeByScore(key, min, max, offset,
					limit);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * 存在チェック.
	 * @param key キー
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public boolean exists(String key)
			throws IOException {
		try {
			String command = "exists";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key));
				startTime = new Date().getTime();
			}
			boolean ret = jedis.exists(key);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * 有効時間設定.
	 * @param key キー
	 * @param sec 有効時間(秒)
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public Long expire(String key, int sec)
			throws IOException {
		try {
			String command = "expire";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key));
				startTime = new Date().getTime();
			}
			Long ret = jedis.expire(key, sec);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * 有効時間設定.
	 * @param key キー
	 * @param sec 有効時間(秒)
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public Long expire(byte[] key, int sec)
			throws IOException {
		try {
			String command = "expire";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key));
				startTime = new Date().getTime();
			}
			Long ret = jedis.expire(key, sec);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * 有効時間無しを設定.
	 * @param key キー
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public Long persist(String key)
			throws IOException {
		try {
			String command = "persist";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, key));
				startTime = new Date().getTime();
			}
			Long ret = jedis.persist(key);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, key, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * 現在トランザクション中の場合trueを返す.
	 * @return 現在トランザクション中の場合true
	 */
	public boolean isTransaction() {
		return tran != null;
	}

	/**
	 * キー一覧を取得.
	 * @param pattern キーのパターン
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public Set<String> keys(String pattern)
			throws IOException {
		try {
			String command = "keys";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command, pattern));
				startTime = new Date().getTime();
			}
			Set<String> ret = jedis.keys(pattern);
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, pattern, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * Redisの全てのデータを削除.
	 * メンテナンス用
	 * @return 実行結果
	 * @throws IOException IOエラー。causeにJedisExceptionが格納されます。
	 */
	public String flushAll()
			throws IOException {
		try {
			String command = "flushAll";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command));
				startTime = new Date().getTime();
			}
			String ret = jedis.flushAll();
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, startTime));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, jedis, connName, connectionInfo);
		}
	}

	/**
	 * このクラスの文字列表現
	 * @return このクラスの文字列表現
	 */
	@Override
	public String toString() {
		return connName;
	}

	/**
	 * Redisのアクセスログを出力するかどうか
	 * @return Redisのアクセスログを出力する場合true;
	 */
	private boolean isEnableAccessLog() {
		return JedisUtil.isEnableAccessLog() && logger.isDebugEnabled();
	}

}
