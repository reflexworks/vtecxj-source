package jp.reflexworks.taggingservice.bdb;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.Durability;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentLockedException;
import com.sleepycat.je.SequenceConfig;
import com.sleepycat.je.TransactionConfig;

import jp.reflexworks.taggingservice.env.BDBEnvUtil;

/**
 * BDB 環境情報保持クラス.
 * 各サービスごとに本クラスが生成される。
 */
public class BDBEnv {

	/** BDB JEの環境クラス */
	private Environment jeEnv;

	/** テーブルリスト */
	private ConcurrentMap<String, BDBDatabase> dbMap = new ConcurrentHashMap<>();

	/** TransactionConfig */
	private TransactionConfig tranConfig;
	/** SequenceConfig */
	private SequenceConfig sequenceConfig;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/** BDBデータ格納ディレクトリ */
	private String bdbDir;

	/**
	 * BDBの環境設定.
	 * サービスごとに本クラスが生成される。
	 * @param bdbDir BDBデータ格納ディレクトリ.
	 *               {bdbdir}/{名前空間}
	 * @param dbNames テーブル名リスト
	 */
	public BDBEnv(String bdbDir, List<String> dbNames) {
		this.bdbDir = bdbDir;
		setup(dbNames);
	}

	/**
	 * BDBの環境設定.
	 * @param dbNames テーブル名リスト
	 */
	private void setup(List<String> dbNames) {
		// BDB環境ディレクトリ
		File bdbHome = new File(bdbDir);
		if (bdbHome.exists()) {
			if (!bdbHome.isDirectory()) {
				throw new IllegalStateException("The bdb home is not directory : " + bdbDir);
			}
		} else {
			bdbHome.mkdirs();
		}

		// BDB環境情報プロパティファイルがあれば読む。
		EnvironmentConfig myEnvConfig = null;
		Properties bdbProperties = BDBEnvUtil.getBDBProperties();
		if (bdbProperties != null) {
			if (logger.isTraceEnabled()) {
				logger.debug("[setup] read je properties.");
			}
			myEnvConfig = new EnvironmentConfig(bdbProperties);
		} else {
			logger.warn("[setup] no je properties.");
			myEnvConfig = new EnvironmentConfig();
		}
		DatabaseConfig myDbConfig = new DatabaseConfig();

		// 各種設定 : 必要なものはプロパティから読むようにする。
		boolean readOnly = false;
		//boolean runCleaner = true;
		//int cachePercent = 25;
		//long cacheSize = 0;

		int numRetries = 3;
		int sleepMillis = 200;

		// Durability.SyncPolicy.WRITE_NO_SYNC
		// Durability.SyncPolicy.NO_SYNC
		// Durability.SyncPolicy.SYNC
		Durability.SyncPolicy syncPolicy = Durability.SyncPolicy.WRITE_NO_SYNC;

		// If the environment is read-only, then
		// make the databases read-only too.
		myEnvConfig.setReadOnly(readOnly);
		myDbConfig.setReadOnly(readOnly);

		// If the environment is opened for write, then we want to be
		// able to create the environment and databases if
		// they do not exist.
		myEnvConfig.setAllowCreate(!readOnly);
		myDbConfig.setAllowCreate(!readOnly);

		//myEnvConfig.setConfigParam(EnvironmentConfig.ENV_RUN_CLEANER,
		//		String.valueOf(runCleaner));

		// Allow transactions if we are writing to the database
		myEnvConfig.setTransactional(!readOnly);
		myDbConfig.setTransactional(!readOnly);

		// Shared Cache
		myEnvConfig.setSharedCache(true);

		// lock debug
		//myEnvConfig.setConfigParam(EnvironmentConfig.TXN_DEADLOCK_STACK_TRACE, "true");
		//myEnvConfig.setConfigParam(EnvironmentConfig.TXN_DUMP_LOCKS, "true");
		//myEnvConfig.setTxnTimeout(500, TimeUnit.MILLISECONDS);

		// cacheの割合、またはサイズ (割合設定が優先)
		//if (cacheSize > 0) {
		//	myEnvConfig.setCacheSize(cacheSize);
		//} else if (cachePercent > 0) {
		//	myEnvConfig.setCachePercent(cachePercent);
		//}

		// デッドロック調査(デバッグ時のみ有効にする)
		//myEnvConfig.setConfigParam(EnvironmentConfig.TXN_DEADLOCK_STACK_TRACE, "true");
		//myEnvConfig.setConfigParam(EnvironmentConfig.TXN_DUMP_LOCKS, "true");

		// Open the environment
		for (int r = 0; r <= numRetries; r++) {
			try {
				jeEnv = new Environment(bdbHome, myEnvConfig);
				break;

			} catch (EnvironmentLockedException e) {
				logger.warn("[setup] EnvironmentLockedException: " + e.getMessage());
				if (r >= numRetries) {
					throw e;
				}
				sleep(sleepMillis);
			}
		}

		// Now open, or create and open, our databases
		// Open the entry databases
		for (String dbName : dbNames) {
			Database jeDb = jeEnv.openDatabase(null,
					dbName,
					myDbConfig);
			BDBDatabase db = new BDBDatabase(jeDb);
			dbMap.put(dbName, db);
		}

		// Need a tuple binding for the Inventory class.
		// We use the InventoryBinding class
		// that we implemented for this purpose.
		//entryBinding = new EntryBaseBinding();
		//incrementBinding = new IncrementBinding();
		//stringBinding = new StringBinding();
		//integerBinding = new IntegerBinding();

		// Open the secondary database. We use this to create a
		// secondary index for the inventory database

		if (syncPolicy != null) {
			tranConfig = new TransactionConfig();
			tranConfig.setDurability(new Durability(syncPolicy, null, null));
		}

		sequenceConfig = new SequenceConfig();
		sequenceConfig.setAllowCreate(true);
	}

	/**
	 * トランザクション開始.
	 * commitと、finallyでabortを実行してください。
	 * @return トランザクションオブジェクト
	 */
	public BDBTransaction beginTransaction() {
		return new BDBTransaction(jeEnv.beginTransaction(null, tranConfig));
	}

	/**
	 * Close the store and environment.
	 */
	public void close() {
		if (jeEnv != null) {
			for (Map.Entry<String, BDBDatabase> mapEntry : dbMap.entrySet()) {
				BDBDatabase db = mapEntry.getValue();
				try {
					db.close();
				} catch (RuntimeException dbe) {
					logger.error(getCloseErrorLogMessage(dbe));
				}
			}

			// Finally, close environment.
			if (jeEnv != null) {
				try {
					jeEnv.close();
				} catch (RuntimeException dbe) {
					logger.error(getCloseErrorLogMessage(dbe));
				}
			}

			//logger.info("MyDbEnv close. " + toString());
		}
	}

	/**
	 * クローズエラーのログ出力.
	 * @param dbe 例外
	 */
	private String getCloseErrorLogMessage(RuntimeException dbe) {
		StringBuilder sb = new StringBuilder();
		sb.append("[close] ");
		sb.append(dbe.getClass().getName());
		sb.append(": ");
		sb.append(dbe.getMessage());
		sb.append(" (continue) bdbDir=");
		sb.append(bdbDir);
		return sb.toString();
	}

	/**
	 * 指定されたテーブルを取得.
	 * @param dbName テーブル名
	 * @return テーブル
	 */
	public BDBDatabase getDb(String dbName) {
		return dbMap.get(dbName);
	}

	/**
	 * シーケンスを取得.
	 * finallyでcloseすること。
	 * @param dbName テーブル名
	 * @param txn トランザクション
	 * @param keyStr キー
	 * @return シーケンス
	 */
	public BDBSequence getSequence(String dbName, BDBTransaction txn, String keyStr) {
		BDBDatabase db = getDb(dbName);
		if (db != null) {
			return db.openSequence(txn, BDBUtil.getDbKey(keyStr), sequenceConfig);
		} else {
			return null;
		}
	}

	/**
	 * スリープ処理.
	 * @param msec スリープ時間(ミリ秒)
	 */
    private synchronized void sleep(long msec) {
		try {
			wait(msec);
		} catch (InterruptedException e) {}
	}

	/**
	 * BDBが配置されたディレクトリを取得.
	 * @return BDBが配置されたディレクトリ
	 */
	public String getBdbDir() {
		return bdbDir;
	}

	/**
	 * BDB環境オブジェクトを取得.
	 * @return BDB環境オブジェクト
	 */
	public Environment getJeEnv() {
		return jeEnv;
	}

	/**
	 * 文字列表現を取得.
	 * @return このインスタンスの文字列表現
	 */
	@Override
	public String toString() {
		return bdbDir;
	}

}
