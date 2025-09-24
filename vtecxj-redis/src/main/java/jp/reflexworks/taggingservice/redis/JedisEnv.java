package jp.reflexworks.taggingservice.redis;

import java.io.IOException;
import java.time.Duration;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.BaseReflexEnv;
import jp.reflexworks.taggingservice.api.ReflexStatic;
import jp.reflexworks.taggingservice.exception.ConnectionException;
import jp.reflexworks.taggingservice.util.RetryUtil;
import jp.sourceforge.reflex.util.DateUtil;
import jp.sourceforge.reflex.util.StringUtils;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisException;

/**
 * Jedisに使用するstatic情報保持クラス
 */
public class JedisEnv {

	/** Redis host Master */
	private String redisHostMaster;
	/** Redis host Slave */
	private String redisHostSlave;
	/** Redis password. */
	private String redisPassword;
	/** Redis pool作成待ち時間(ミリ秒) */
	private int redisMaxWaitmillis;
	/** Redis pool最大コネクション数 */
	private int redisMaxTotal;

	/** Jedis pool (Master) */
	private JedisPool poolJedisMaster;
	/** Jedis pool (Slave) */
	private JedisPool poolJedisSlave;
	/** Jedis pool config */
	private JedisPoolConfig poolConfig;

	/**
	 * 処理中フラグ.
	 * 0はロックなし、1以上は別のスレッドで処理中
	 */
	private AtomicInteger isProsessingNum = new AtomicInteger();
	
	/** Jedis pool クローズ時間 */
	private AtomicLong closeTime = new AtomicLong();
	/** Jedis pool 生成時間 */
	private AtomicLong createTime = new AtomicLong();

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 初期処理
	 */
	void init() {
		BaseReflexEnv env = ReflexStatic.getEnv();
		this.redisHostMaster = env.getSystemProp(JedisConst.PROP_REDIS_HOST_MASTER);
		this.redisHostSlave = env.getSystemProp(JedisConst.PROP_REDIS_HOST_SLAVE);
		this.redisPassword = env.getSystemProp(JedisConst.PROP_REDIS_PASSWORD);
		String tmp = null;
		tmp = env.getSystemProp(JedisConst.PROP_REDIS_MAX_WAITMILLIS);
		this.redisMaxWaitmillis = StringUtils.intValue(tmp, JedisConst.REDIS_MAX_WAITMILLIS_DEFAULT);
		tmp = env.getSystemProp(JedisConst.PROP_REDIS_MAX_TOTAL);
		this.redisMaxTotal = StringUtils.intValue(tmp, JedisConst.REDIS_MAX_TOTAL_DEFAULT);

		createJedisPoolConfig();
		createJedisPool();
	}

	/**
	 * JedisPoolの設定.
	 */
	private void createJedisPoolConfig() {
		// JedisPoolConfig
		poolConfig = new JedisPoolConfig();
		poolConfig.setMaxWait(Duration.ofMillis(redisMaxWaitmillis));
		poolConfig.setMaxTotal(redisMaxTotal);

		// log
		if (isEnableAccessLog()) {
			logger.info("[createJedisPool] EvictionPolicyClassName : "
					+ poolConfig.getEvictionPolicyClassName());
			logger.info("[createJedisPool] JmxNameBase : "
					+ poolConfig.getJmxNameBase());
			logger.info("[createJedisPool] JmxNamePrefix : "
					+ poolConfig.getJmxNamePrefix());
			logger.info("[createJedisPool] MaxIdle : " + poolConfig.getMaxIdle());
			logger.info("[createJedisPool] MaxTotal : " + poolConfig.getMaxTotal());
			logger.info("[createJedisPool] MaxWaitMillis : "
					+ poolConfig.getMaxWaitDuration().toMillis());
			logger.info("[createJedisPool] MinEvictableIdleTimeMillis : "
					+ poolConfig.getMinEvictableIdleDuration().toMillis());
			logger.info("[createJedisPool] MinIdle : " + poolConfig.getMinIdle());
			logger.info("[createJedisPool] NumTestsPerEvictionRun : "
					+ poolConfig.getNumTestsPerEvictionRun());
			logger.info("[createJedisPool] SoftMinEvictableIdleTimeMillis : "
					+ poolConfig.getSoftMinEvictableIdleDuration().toMillis());
			logger.info("[createJedisPool] TimeBetweenEvictionRunsMillis : "
					+ poolConfig.getDurationBetweenEvictionRuns().toMillis());
			logger.info("[createJedisPool] BlockWhenExhausted : "
					+ poolConfig.getBlockWhenExhausted());
			logger.info("[createJedisPool] Fairness : "
					+ poolConfig.getFairness());
			logger.info("[createJedisPool] JmxEnabled : "
					+ poolConfig.getJmxEnabled());
			logger.info("[createJedisPool] Lifo : " + poolConfig.getLifo());
		}
	}

	/**
	 * Jedis poolの生成.
	 */
	private void createJedisPool() {
		BaseReflexEnv env = ReflexStatic.getEnv();
		// エラー時のリトライ回数、スリープ時間
		String rtmp = env.getSystemProp(JedisConst.PROP_REDIS_GETCONNECTION_RETRY_COUNT);
		int retries = StringUtils.intValue(rtmp, JedisConst.REDIS_GETCONNECTION_RETRY_COUNT_DEFAULT);
		rtmp = env.getSystemProp(JedisConst.PROP_REDIS_GETCONNECTION_RETRY_WAITMILLIS);
		int waitmillis = StringUtils.intValue(rtmp, JedisConst.REDIS_GETCONNECTION_RETRY_WAITMILLIS_DEFAULT);

		// 設定バリデーションチェック
		if (StringUtils.isBlank(redisHostMaster)) {
			throw new IllegalStateException("Property is required. " + JedisConst.PROP_REDIS_HOST_MASTER);
		}

		// 処理中ロック取得
		boolean isLock = isLock();
		if (logger.isDebugEnabled()) {
			logger.debug("[createJedisPool] isLock=" + isLock);
		}
		if (isLock) {
			if (logger.isDebugEnabled()) {
				logger.debug("[createJedisPool] create jedisPool start.");
			}
			try {
				long nowTime = new Date().getTime();
				// Master JedisPoolを生成
				poolJedisMaster = newJedisPool(redisHostMaster, retries, waitmillis);
				if (poolJedisMaster == null) {
					throw new IllegalStateException("Redis pool could not be created. host = " + redisHostMaster);
				}
				// Slave JedisPoolを生成
				if (!StringUtils.isBlank(redisHostSlave)) {
					poolJedisSlave = newJedisPool(redisHostSlave, retries, waitmillis);
					if (poolJedisSlave == null) {
						if (isEnableAccessLog()) {
							logger.debug("[createJedisPool] poolJedisSlave = poolJedisMaster");
						}
						poolJedisSlave = poolJedisMaster;
					}
				} else {
					if (isEnableAccessLog()) {
						logger.debug("[createJedisPool] poolJedisSlave = poolJedisMaster");
					}
					poolJedisSlave = poolJedisMaster;
				}
				createTime.set(nowTime);

			} catch (IOException e) {
				throw new IllegalStateException(e);
			} finally {
				isProsessingNum.set(0);
			}
		} else {
			logger.info("[createJedisPool] It's locked.");
		}
	}

	/**
	 * クローズ処理
	 */
	public void close() {
		closeJedisPool();
	}

	/**
	 * JedisPoolをclose.
	 */
	private void closeJedisPool() {
		boolean isLock = isLock();
		if (logger.isDebugEnabled()) {
			logger.debug("[closeJedisPool] isLock=" + isLock);
		}
		if (isLock) {
			if (logger.isDebugEnabled()) {
				logger.debug("[closeJedisPool] destroy jedisPool start.");
			}
			try {
				long nowTime = new Date().getTime();
				boolean isClosed = false;
				if (poolJedisMaster != null) {
					try {
						if (logger.isDebugEnabled()) {
							logger.debug("[closeJedisPool] poolJedisMaster.destroy() start");
						}
						poolJedisMaster.destroy();
						if (logger.isDebugEnabled()) {
							logger.debug("[closeJedisPool] poolJedisMaster.destroy() end");
						}
						isClosed = true;
					} catch (Throwable e) {
						logger.warn("[closeJedisPool] Env close failed. (Redis Master)", e);
					}
				}
				if (poolJedisSlave != null && poolJedisSlave != poolJedisMaster) {
					try {
						if (logger.isDebugEnabled()) {
							logger.debug("[closeJedisPool] poolJedisSlave.destroy() start");
						}
						poolJedisSlave.destroy();
						if (logger.isDebugEnabled()) {
							logger.debug("[closeJedisPool] poolJedisSlave.destroy() end");
						}
						isClosed = true;
					} catch (Throwable e) {
						logger.warn("[closeJedisPool] Env close failed. (Redis Slave)", e);
					}
				}
				if (isClosed) {
					closeTime.set(nowTime);
				}
			} finally {
				isProsessingNum.set(0);
				if (logger.isDebugEnabled()) {
					logger.debug("[closeJedisPool] finally. isProsessingNum=" + isProsessingNum.get());
				}
			}
		} else {
			logger.warn("[closeJedisPool] It's locked.");
		}
	}

	/**
	 * コネクション取得.
	 * Masterのコネクションを取得
	 * @return コネクション
	 */
	public Jedis getMasterConnection() throws IOException {
		try {
			String command = "getResource(master)";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command));
				startTime = new Date().getTime();
			}
			Jedis ret = poolJedisMaster.getResource();
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, startTime, ret.hashCode()));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, JedisConst.CONN_NAME_WRITE);
		}
	}

	/**
	 * Slaveのコネクションを取得
	 * @return コネクション
	 */
	public Jedis getSlaveConnection() throws IOException {
		if (poolJedisSlave == null) {
			return null;
		}
		try {
			String command = "getResource(slave)";
			long startTime = 0;
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getStartLog(command));
				startTime = new Date().getTime();
			}
			Jedis ret = poolJedisSlave.getResource();
			if (isEnableAccessLog()) {
				logger.debug(JedisUtil.getEndLog(command, startTime, ret.hashCode()));
			}
			return ret;
		} catch (JedisException e) {
			throw JedisUtil.convertException(e, JedisConst.CONN_NAME_READ);
		}
	}

	/**
	 * Jedis poolの生成.
	 * @param redisHost Redisホスト、ポート
	 * @param retries pool生成エラー時のリトライ回数
	 * @param waitmillis pool生成エラー時のリトライ待ち時間(ミリ秒)
	 * @return JedisPool
	 * @throws IOException Pool生成エラー
	 */
	private JedisPool newJedisPool(String redisHost, int retries, int waitmillis)
			throws IOException {
		HostAndPort hostAndPort = getHostAndPort(redisHost);
		JedisPool tmpPool = null;
		if (hostAndPort != null) {
			for (int r = 0; r <= retries; r++) {
				try {
					if (hostAndPort.getPort() > 0) {
						tmpPool = new JedisPool(poolConfig, hostAndPort.getHost(),
								hostAndPort.getPort());
					} else {
						tmpPool = new JedisPool(poolConfig, hostAndPort.getHost());
					}

					// Jedisを取得して接続確認
					Jedis tmpJedis = null;
					boolean isReturn = false;
					try {
						tmpJedis = tmpPool.getResource();
						if (!StringUtils.isBlank(redisPassword)) {
							tmpJedis.auth(redisPassword);
						}

						// master/slave確認
						String info = tmpJedis.info(JedisConst.REDIS_INFO_REPLICATION);
						if (logger.isInfoEnabled()) {
							StringBuilder sb = new StringBuilder();
							sb.append("[newJedisPool] redisHost = ");
							sb.append(redisHost);
							if (isEnableAccessLog()) {
								sb.append(", replication info = ");
								sb.append(info);
							}
							logger.info(sb.toString());
						}
						isReturn = true;

					} finally {
						if (tmpJedis != null) {
							tmpJedis.close();
						}
						if (!isReturn) {
							try {
								tmpPool.destroy();
							} catch (Throwable ee) {
								logger.warn("[newJedisPool] JedisException - close error", ee);
							} finally {
								tmpPool = null;
							}
						}
					}

					break;

				} catch (JedisException e) {
					if (tmpPool != null) {
						try {
							tmpPool.destroy();
						} catch (Throwable ee) {
							logger.warn("[newJedisPool] JedisException - close error", ee);
						} finally {
							tmpPool = null;
						}
					}
					StringBuilder sb = new StringBuilder();
					sb.append("[newJedisPool] ");
					sb.append(e.getClass().getName());
					sb.append(": ");
					sb.append(e.getMessage());
					logger.warn(sb.toString());
					if (logger.isDebugEnabled()) {
						logger.debug("[newJedisPool] redisHost:" + redisHost, e);
					}
					checkRetry(e, r, retries, waitmillis);
				}
			}
		}
		return tmpPool;
	}

	/**
	 * 文字列をホスト名とポート番号に分ける.
	 * @param str 文字列
	 * @return ホスト名とポート番号
	 */
	private HostAndPort getHostAndPort(final String str) {
		if (StringUtils.isBlank(str)) {
			return null;
		}
		String host = null;
		int port = 0;
		int idx = str.indexOf(JedisConst.PORT_DELIMITER);
		if (idx > 0) {
			host = str.substring(0, idx);
			port = StringUtils.intValue(str.substring(idx + 1));
		} else {
			host = str;
		}
		return new HostAndPort(host, port);
	}

	/**
	 * リトライチェック.
	 * @param e 例外
	 * @param r 現在のリトライ回数
	 * @param retries 総リトライ回数
	 * @param waitMillis リトライ時のスリープ時間
	 * @throws IOException 例外
	 */
	private void checkRetry(JedisException e, int r, int retries, int waitMillis)
			throws IOException {
		// コネクションエラーでない場合はエラーを投げる
		if (!JedisUtil.isConnectionError(e)) {
			throw new IOException(e);
		}
		// リトライ回数超過
		if (r >= retries) {
			throw new ConnectionException(e);
		}
		// リトライ前のスリープ
		if (logger.isInfoEnabled()) {
			logger.info("Retry (" + r + ")");
		}
		sleep(waitMillis);
	}

	/**
	 * sleep.
	 * @param millisec スリープ時間(ミリ秒)
	 */
	private void sleep(long millisec) {
		RetryUtil.sleep(millisec);
	}

	/**
	 * Redisのアクセスログを出力するかどうか
	 * @return Redisのアクセスログを出力する場合true;
	 */
	private boolean isEnableAccessLog() {
		return JedisUtil.isEnableAccessLog() && logger.isDebugEnabled();
	}

	/**
	 * Jedis Pool 再作成
	 */
	void reCreateJedisPool() {
		// 直前にクローズされた場合、再度クローズは行わない。
		long nowTime = new Date().getTime();
		long withoutRecreateMillis = JedisUtil.getRedisWithoutRecreateMillisec();
		if (nowTime - withoutRecreateMillis < closeTime.get()) {
			if (logger.isDebugEnabled()) {
				logger.debug("[reCreateJedisPool] without recreate. closeTime = " +
						DateUtil.getDateTimeFormat(new Date(closeTime.get())));
			}
		} else {
			if (logger.isDebugEnabled()) {
				logger.debug("[reCreateJedisPool] closeJedisPool start.");
			}
			closeJedisPool();
			if (logger.isDebugEnabled()) {
				logger.debug("[reCreateJedisPool] closeJedisPool end.");
			}
		}

		// 直前にプール生成された場合、再度生成処理は行わない。
		if (nowTime - withoutRecreateMillis < createTime.get()) {
			if (logger.isDebugEnabled()) {
				logger.debug("[reCreateJedisPool] without recreate. createTime = " +
						DateUtil.getDateTimeFormat(new Date(createTime.get())));
			}
		} else {
			if (logger.isDebugEnabled()) {
				logger.debug("[reCreateJedisPool] createJedisPool start.");
			}
			createJedisPool();
			if (logger.isDebugEnabled()) {
				logger.debug("[reCreateJedisPool] createJedisPool end.");
			}
		}
	}
	
	/**
	 * ロックの取得処理
	 * @return ロック取得の場合true、他スレッドでロック取得中の場合false
	 */
	private boolean isLock() {
		boolean isLock = false;
		int isLockNum = isProsessingNum.addAndGet(1);
		if (isLockNum == 1) {
			isLock = true;
		} else {
			// 何らかの原因でロックが解除されなかった場合、決められた数値の倍数を取得した場合にロックを取得したものとする。
			int clearLockfailedNum = JedisUtil.getRedisClearLockfailedNum();
			if (clearLockfailedNum > 0) {
				int remainder = isLockNum % clearLockfailedNum;
				if (remainder == 0) {
					isLock = true;
				}
			}
		}
		return isLock;
	}

}
