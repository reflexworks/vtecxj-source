package jp.reflexworks.taggingservice.env;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentStats;
import com.sleepycat.je.StatsConfig;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexStatic;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.batch.ReflexBDBDiskUsageManager;
import jp.reflexworks.taggingservice.bdb.BDBConst;
import jp.reflexworks.taggingservice.bdb.BDBEnv;
import jp.reflexworks.taggingservice.bdb.BDBUtil;
import jp.reflexworks.taggingservice.exception.StaticDuplicatedException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * BDB環境情報管理クラス
 */
public class BDBEnvManager {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 初期処理.
	 */
	public void init() {
		// BDB環境情報Mapを格納
		// キー: 名前空間、値: BDB環境情報
		ConcurrentMap<String, BDBEnv> bdbEnvMap =
				new ConcurrentHashMap<String, BDBEnv>();
		try {
			ReflexStatic.setStatic(BDBConst.STATIC_NAME_BDBENV_MAP, bdbEnvMap);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " +
					BDBConst.STATIC_NAME_BDBENV_MAP, e);
		}
		// BDB環境へのロック時間Mapを格納
		// キー: 名前空間、値: true
		ConcurrentMap<String, Boolean> bdbEnvLockMap =
				new ConcurrentHashMap<String, Boolean>();
		try {
			ReflexStatic.setStatic(BDBConst.STATIC_NAME_BDBENV_LOCK_MAP, bdbEnvLockMap);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " +
					BDBConst.STATIC_NAME_BDBENV_LOCK_MAP, e);
		}
		// BDB環境へのアクセス時間Mapを格納
		// キー: 名前空間、値: アクセス時間
		ConcurrentMap<String, Long> bdbEnvAccesstimeMap =
				new ConcurrentHashMap<String, Long>();
		try {
			ReflexStatic.setStatic(BDBConst.STATIC_NAME_BDBENV_ACCESSTIME_MAP,
					bdbEnvAccesstimeMap);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " +
					BDBConst.STATIC_NAME_BDBENV_ACCESSTIME_MAP, e);
		}
	}

	/**
	 * シャットダウン処理.
	 */
	public void close() {
		ConcurrentMap<String, BDBEnv> bdbEnvMap =
				(ConcurrentMap<String, BDBEnv>)ReflexStatic.getStatic(
						BDBConst.STATIC_NAME_BDBENV_MAP);
		if (bdbEnvMap != null) {
			for (Map.Entry<String, BDBEnv> mapEntry : bdbEnvMap.entrySet()) {
				try {
					BDBEnv bdbEnv = mapEntry.getValue();
					bdbEnv.close();

				} catch (Throwable e) {
					// Do nothing.
					logger.warn("[close] Error occured.", e);
				}
			}
		}
	}

	/**
	 * 名前空間からBDB環境情報を取得.
	 * @param dbNames テーブル名リスト
	 * @param namespace 名前空間
	 * @param isCreate 指定された名前空間のBDB環境が存在しない時、環境を作成する場合true。
	 * @param setAccesstime アクセス時間を設定する場合true。(モニタリングやサービス削除の場合はアクセス時間を設定しない)
	 * @return BDB環境情報
	 */
	public BDBEnv getBDBEnvByNamespace(List<String> dbNames, String namespace,
			boolean isCreate, boolean setAccesstime) {
		if (!StringUtils.isBlank(namespace)) {
			ConcurrentMap<String, BDBEnv> bdbEnvMap =
					(ConcurrentMap<String, BDBEnv>)ReflexStatic.getStatic(
							BDBConst.STATIC_NAME_BDBENV_MAP);
			ConcurrentMap<String, Boolean> bdbLockMap =
					(ConcurrentMap<String, Boolean>)ReflexStatic.getStatic(
							BDBConst.STATIC_NAME_BDBENV_LOCK_MAP);
			ConcurrentMap<String, Long> bdbAccesstimeMap =
					(ConcurrentMap<String, Long>)ReflexStatic.getStatic(
							BDBConst.STATIC_NAME_BDBENV_ACCESSTIME_MAP);

			// アクセス時間を設定
			if (setAccesstime) {
				bdbAccesstimeMap.put(namespace, new Date().getTime());
			}

			BDBEnv bdbEnv = null;
			int numRetries = BDBEnvUtil.getBDBEnvRetryCount();
			int waitMillis = BDBEnvUtil.getBDBEnvRetryWaitmillis();
			for (int r = 0; r <= numRetries; r++) {
				Boolean isLock = bdbLockMap.get(namespace);
				boolean isCreateEnv = false;
				if (isLock == null) {	// ロックされていない場合環境情報を取得
					bdbEnv = bdbEnvMap.get(namespace);
					if (bdbEnv == null) {
						String bdbDir = BDBEnvUtil.getBDBDirByNamespace(namespace);
						if (isCreate) {
							isCreateEnv = true;
						} else {
							// BDB環境を新規作成しない場合、既にディレクトリが存在すればBDB環境オブジェクトを生成する。
							File bdbDirFile = new File(bdbDir);
							if (bdbDirFile.exists() && bdbDirFile.isDirectory()) {
								isCreateEnv = true;
							}
						}

						if (isCreateEnv) {
							// BDB環境生成ロックを取得
							Boolean ret = false;
							try {
								ret = bdbLockMap.putIfAbsent(namespace, true);
								if (ret == null) {
									// 新しくBDB環境情報を生成
									bdbEnv = new BDBEnv(bdbDir, dbNames);
									bdbEnvMap.put(namespace, bdbEnv);
								}
							} finally {
								if (ret == null) {
									bdbLockMap.remove(namespace);
								}
							}
						}
					}
					if (bdbEnv != null || !isCreateEnv) {
						break;
					}
				}
				// 他スレッドによるBDB環境情報生成中。少し待つ。
				BDBUtil.sleep(waitMillis + r * 10);
			}
			if (bdbEnv != null) {
				return bdbEnv;
			}
		}
		if (isCreate) {
			throw new IllegalStateException("Could not get or generate BDB environment. namespace: " + namespace);
		}
		return null;
	}

	/**
	 * BDBクリーナー実行
	 */
	public void clean() {
		ConcurrentMap<String, BDBEnv> bdbEnvMap =
				(ConcurrentMap<String, BDBEnv>)ReflexStatic.getStatic(
						BDBConst.STATIC_NAME_BDBENV_MAP);
		if (bdbEnvMap != null) {
			for (Map.Entry<String, BDBEnv> mapEntry : bdbEnvMap.entrySet()) {
				String namespace = mapEntry.getKey();
				BDBEnv bdbEnv = mapEntry.getValue();
				cleanProc(namespace, bdbEnv);
			}
		}
	}

	/**
	 * 名前空間を指定してBDBクリーナー実行
	 * @paran namespace 名前空間
	 */
	public void clean(String namespace) {
		ConcurrentMap<String, BDBEnv> bdbEnvMap =
				(ConcurrentMap<String, BDBEnv>)ReflexStatic.getStatic(
						BDBConst.STATIC_NAME_BDBENV_MAP);
		if (bdbEnvMap != null) {
			BDBEnv bdbEnv = bdbEnvMap.get(namespace);
			if (bdbEnv != null) {
				cleanProc(namespace, bdbEnv);
			}
		}
	}

	/**
	 * BDBクリーナー実行
	 * 環境ごとの処理.
	 * @param namespace 名前空間
	 * @param bdbEnv BDB環境情報
	 */
	private void cleanProc(String namespace, BDBEnv bdbEnv) {
		try {
			String logPrefix = "[cleanProc] namespace=" + namespace + " ";
			if (BDBEnvUtil.isEnableStatsLog()) {
				// 統計情報
				EnvironmentStats jeEnvStats = getJeEnvStats(bdbEnv);

				StringBuilder sb = new StringBuilder();
				sb.append(logPrefix);
				sb.append("EnvironmentStats : ");
				sb.append(jeEnvStats.toString());
				logger.debug(sb.toString());
			}

			if (BDBEnvUtil.isEnableStatsLog()) {
				logger.debug(logPrefix + "start.");
			}

			// BDBクリーナー実行
			Environment jeEnv = bdbEnv.getJeEnv();
			jeEnv.cleanLog();

			if (BDBEnvUtil.isEnableStatsLog()) {
				logger.debug(logPrefix + "end.");
			}

		} catch (DatabaseException | IOException | IllegalStateException e) {
			logger.warn("[cleanProc] namespace=" + namespace + " Error occured.", e);
		}
	}

	/**
	 * BDB環境統計情報を取得
	 * @param bdbEnv BDB環境オブジェクト
	 * @return BDB環境統計情報
	 */
	protected EnvironmentStats getJeEnvStats(BDBEnv bdbEnv)
	throws IOException {
		if (bdbEnv == null) {
			return null;
		}
		try {
			Environment jeEnv = bdbEnv.getJeEnv();
			StatsConfig config = new StatsConfig();
			config.setClear(true);
			return jeEnv.getStats(config);
		} catch (DatabaseException e) {
			throw new IOException(e);
		}
	}

	/**
	 * 指定された名前空間のBDB環境最終アクセス日時を取得.
	 * @param namespace 名前空間
	 * @return 指定された名前空間のBDB環境最終アクセス日時
	 */
	public Date getBDBAccesstimeByNamespace(String namespace) {
		if (StringUtils.isBlank(namespace)) {
			return null;
		}
		ConcurrentMap<String, Long> bdbAccesstimeMap =
				(ConcurrentMap<String, Long>)ReflexStatic.getStatic(
						BDBConst.STATIC_NAME_BDBENV_ACCESSTIME_MAP);
		Long time = bdbAccesstimeMap.get(namespace);
		if (time != null) {
			return new Date(time);
		}
		return null;
	}

	/**
	 * BDB環境最終アクセス日時を設定.
	 * @param アクセス日時
	 * @param namespace 名前空間
	 */
	public void setBDBAccesstimeByNamespace(String namespace, Date date) {
		if (StringUtils.isBlank(namespace)) {
			return;
		}
		ConcurrentMap<String, Long> bdbAccesstimeMap =
				(ConcurrentMap<String, Long>)ReflexStatic.getStatic(
						BDBConst.STATIC_NAME_BDBENV_ACCESSTIME_MAP);
		Long time = null;
		if (date != null) {
			time = date.getTime();
		}
		bdbAccesstimeMap.put(namespace, time);
	}

	/**
	 * BDB環境クローズ処理.
	 * @param namespace 名前空間
	 */
	public void closeBDBEnv(String namespace) {
		ConcurrentMap<String, BDBEnv> bdbEnvMap =
				(ConcurrentMap<String, BDBEnv>)ReflexStatic.getStatic(
						BDBConst.STATIC_NAME_BDBENV_MAP);
		if (bdbEnvMap != null && bdbEnvMap.containsKey(namespace)) {
			BDBEnv bdbEnv = bdbEnvMap.get(namespace);
			if (logger.isInfoEnabled()) {
				logger.info("[closeBDBEnv] close bdbEnv. namespace=" + namespace);
			}
			bdbEnv.close();
			bdbEnvMap.remove(namespace);
		} else {
			if (logger.isInfoEnabled()) {
				logger.info("[closeBDBEnv] The bdbEnv does not exist. namespace=" + namespace);
			}
		}
	}

	/**
	 * BDB環境統計情報取得.
	 * @param dbNames テーブル名リスト
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return BDB環境統計情報 (Feedのsubtitleに設定)
	 */
	public FeedBase getStatsByNamespace(List<String> dbNames, String namespace,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		FeedBase retFeed = TaggingEntryUtil.createAtomFeed();
		// サービス名からBDB環境オブジェクトを取得
		BDBEnv bdbEnv = getBDBEnvByNamespace(dbNames, namespace, false, false);
		if (bdbEnv != null) {
			// 名前空間も出力
			EnvironmentStats jeEnvStats = getJeEnvStats(bdbEnv);
			StringBuilder sb = new StringBuilder();
			sb.append("BDB environment stat. namespace=");
			sb.append(namespace);
			retFeed.title = sb.toString();
			retFeed.subtitle = jeEnvStats.toString();
		} else {
			retFeed.title = "The BDB environment is not opened. namespace=" + namespace;
		}
		return retFeed;
	}
	
	/**
	 * ディスク使用率を取得.
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return ディスク使用率(%) (Feedのsubtitleに設定)
	 */
	public FeedBase getDiskUsage(RequestInfo requestInfo, ConnectionInfo connectionInfo) 
	throws IOException {
		ReflexBDBDiskUsageManager diskUsageManager = new ReflexBDBDiskUsageManager();
		String diskUsage = diskUsageManager.getDiskUsage(requestInfo, connectionInfo);
		FeedBase retFeed = TaggingEntryUtil.createAtomFeed();
		retFeed.title = diskUsage;
		return retFeed;
	}
	

}
