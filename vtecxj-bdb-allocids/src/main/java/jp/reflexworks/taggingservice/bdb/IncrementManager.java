package jp.reflexworks.taggingservice.bdb;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sleepycat.je.DatabaseException;

import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.BDBEnvManager;
import jp.reflexworks.taggingservice.env.BDBEnvUtil;
import jp.reflexworks.taggingservice.exception.OutOfRangeException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.IncrementInfo;
import jp.reflexworks.taggingservice.model.IncrementRangeInfo;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.ReflexBDBLogUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * 加算処理クラス
 */
public class IncrementManager {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 現在の加算値を取得.
	 * @param uri キー
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @param setAccesstime BDBへのアクセス時間を更新する場合true
	 * @return 現在の加算値
	 */
	public long getids(String namespace, String uri, String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		int numRetries = BDBEnvUtil.getBDBRetryCount();
		int waitMillis = BDBEnvUtil.getBDBRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				BDBEnv bdbEnv = getBDBEnvByNamespace(namespace, true);
				BDBDatabase dbIncrement = bdbEnv.getDb(AllocidsConst.DB_INCREMENT);
				IncrementBinding binding = BDBUtil.getIncrementBinding();
				BDBGet<IncrementInfo> bdbGet = new BDBGet<IncrementInfo>();
				IncrementInfo incrementInfo = bdbGet.get(serviceName, null, dbIncrement, binding,
						BDBUtil.getLockMode(), uri, requestInfo, connectionInfo);
				return editNumber(incrementInfo, true, uri);

			} catch (DatabaseException e) {
				// リトライ判定、入力エラー判定
				BDBUtil.convertError(e, uri, requestInfo);
				if (r >= numRetries) {
					// リトライ対象だがリトライ回数を超えた場合
					BDBUtil.convertIOError(e, uri);
				}
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) +
							"[getids] " + ReflexBDBLogUtil.getRetryLog(e, r));
				}
				BDBUtil.sleep(waitMillis + r * 10);
			}
		}
		throw new IllegalStateException("Unreachable code.");
	}

	/**
	 * 加算枠を取得.
	 * @param uri キー
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @param setAccesstime BDBへのアクセス時間を更新する場合true
	 * @return 加算枠
	 */
	public String getRangeids(String namespace, String uri, String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		int numRetries = BDBEnvUtil.getBDBRetryCount();
		int waitMillis = BDBEnvUtil.getBDBRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				BDBEnv bdbEnv = getBDBEnvByNamespace(namespace, true);
				BDBDatabase dbIncrement = bdbEnv.getDb(AllocidsConst.DB_INCREMENT);
				IncrementBinding binding = BDBUtil.getIncrementBinding();
				BDBGet<IncrementInfo> bdbGet = new BDBGet<IncrementInfo>();
				IncrementInfo incrementInfo = bdbGet.get(serviceName, null, dbIncrement, binding,
						BDBUtil.getLockMode(), uri, requestInfo, connectionInfo);
				String range = null;
				if (incrementInfo != null) {
					range = incrementInfo.getRange();
				}
				return range;

			} catch (DatabaseException e) {
				// リトライ判定、入力エラー判定
				BDBUtil.convertError(e, uri, requestInfo);
				if (r >= numRetries) {
					// リトライ対象だがリトライ回数を超えた場合
					BDBUtil.convertIOError(e, uri);
				}
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) +
							"[getRangeids] " + ReflexBDBLogUtil.getRetryLog(e, r));
				}
				BDBUtil.sleep(waitMillis + r * 10);
			}
		}
		throw new IllegalStateException("Unreachable code.");
	}

	/**
	 * 加算処理.
	 * @param uri キー
	 * @param num 加算値
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @param setAccesstime BDBへのアクセス時間を更新する場合true
	 * @return 加算結果
	 */
	public long addids(String namespace, String uri, long num,
			String serviceName, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		int numRetries = BDBEnvUtil.getBDBRetryCount();
		int waitMillis = BDBEnvUtil.getBDBRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			BDBTransaction bdbTxn = null;
			try {
				// BDB環境情報取得
				BDBEnv bdbEnv = getBDBEnvByNamespace(namespace, true);
				BDBDatabase dbIncrement = bdbEnv.getDb(AllocidsConst.DB_INCREMENT);
				IncrementBinding binding = BDBUtil.getIncrementBinding();

				// トランザクション開始
				bdbTxn = bdbEnv.beginTransaction();

				// まず取得
				BDBGet<IncrementInfo> bdbGet = new BDBGet<IncrementInfo>();
				IncrementInfo incrInfo = bdbGet.get(serviceName, bdbTxn, dbIncrement, binding,
						BDBUtil.getLockModeRMW(), uri, requestInfo, connectionInfo);

				// 現在値と枠を抽出
				Long currentNum = null;
				IncrementRangeInfo rangeInfo = null;
				if (incrInfo != null) {
					currentNum = incrInfo.getNum();
					rangeInfo = getIncrementRangeInfo(incrInfo.getRange());
				}

				long newNum = 0;
				if (currentNum != null) {
					newNum = currentNum + num;
				} else {
					newNum = num;
				}

				// 範囲チェック
				checkWithinRange(newNum, rangeInfo, uri, false);

				// インクリメント更新
				String range = getIncrementRange(rangeInfo);
				IncrementInfo newIncrInfo = new IncrementInfo(newNum, range);
				BDBPut<IncrementInfo> bdbPut = new BDBPut<IncrementInfo>();
				bdbPut.put(serviceName, bdbTxn, dbIncrement, binding, uri, newIncrInfo,
						requestInfo, connectionInfo);
				bdbTxn.commit();
				bdbTxn = null;

				return editNumber(newIncrInfo, false, uri);

			} catch (DatabaseException e) {
				// リトライ判定、入力エラー判定
				BDBUtil.convertError(e, uri, requestInfo);
				if (r >= numRetries) {
					// リトライ対象だがリトライ回数を超えた場合
					BDBUtil.convertIOError(e, uri);
				}
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) +
							"[addids] " + ReflexBDBLogUtil.getRetryLog(e, r));
				}
				BDBUtil.sleep(waitMillis + r * 10);

			} finally {
				if (bdbTxn != null) {
					try {
						bdbTxn.abort();
					} catch (Throwable e) {
						logger.warn("[addids] abort error.", e);
					}
				}
			}
		}
		throw new IllegalStateException("Unreachable code.");
	}

	/**
	 * 加算処理に使用する値を設定.
	 * @param uri キー
	 * @param num 設定値
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @param setAccesstime BDBへのアクセス時間を更新する場合true
	 */
	public void setids(String namespace, String uri, long num,
			String serviceName, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		int numRetries = BDBEnvUtil.getBDBRetryCount();
		int waitMillis = BDBEnvUtil.getBDBRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			BDBTransaction bdbTxn = null;
			try {
				// BDB環境情報取得
				BDBEnv bdbEnv = getBDBEnvByNamespace(namespace, true);
				BDBDatabase dbIncrement = bdbEnv.getDb(AllocidsConst.DB_INCREMENT);
				IncrementBinding binding = BDBUtil.getIncrementBinding();

				// トランザクション開始
				bdbTxn = bdbEnv.beginTransaction();

				// まず取得
				BDBGet<IncrementInfo> bdbGet = new BDBGet<IncrementInfo>();
				IncrementInfo incrInfo = bdbGet.get(serviceName, bdbTxn, dbIncrement, binding,
						BDBUtil.getLockModeRMW(), uri, requestInfo, connectionInfo);
				// 枠を抽出
				IncrementRangeInfo rangeInfo = null;
				if (incrInfo != null) {
					rangeInfo = getIncrementRangeInfo(incrInfo.getRange());
				}

				// 範囲チェック
				checkWithinRange(num, rangeInfo, uri, true);

				// 更新
				String range = getIncrementRange(rangeInfo);
				IncrementInfo newIncrInfo = new IncrementInfo(num, range);
				BDBPut<IncrementInfo> bdbPut = new BDBPut<IncrementInfo>();
				bdbPut.put(serviceName, bdbTxn, dbIncrement, binding, uri, newIncrInfo,
						requestInfo, connectionInfo);

				bdbTxn.commit();
				bdbTxn = null;
				return;

			} catch (DatabaseException e) {
				// リトライ判定、入力エラー判定
				BDBUtil.convertError(e, uri, requestInfo);
				if (r >= numRetries) {
					// リトライ対象だがリトライ回数を超えた場合
					BDBUtil.convertIOError(e, uri);
				}
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) +
							"[setids] " + ReflexBDBLogUtil.getRetryLog(e, r));
				}
				BDBUtil.sleep(waitMillis + r * 10);

			} finally {
				if (bdbTxn != null) {
					try {
						bdbTxn.abort();
					} catch (Throwable e) {
						logger.warn("[setids] abort error.", e);
					}
				}
			}
		}
		throw new IllegalStateException("Unreachable code.");
	}

	/**
	 * 加算範囲を設定.
	 * @param uri キー
	 * @param range 加算範囲
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @param setAccesstime BDBへのアクセス時間を更新する場合true
	 */
	public void rangeids(String namespace, String uri, String range,
			String serviceName, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		int numRetries = BDBEnvUtil.getBDBRetryCount();
		int waitMillis = BDBEnvUtil.getBDBRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			BDBTransaction bdbTxn = null;
			try {
				// BDB環境情報取得
				BDBEnv bdbEnv = getBDBEnvByNamespace(namespace, true);
				BDBDatabase dbIncrement = bdbEnv.getDb(AllocidsConst.DB_INCREMENT);
				IncrementBinding binding = BDBUtil.getIncrementBinding();

				// トランザクション開始
				bdbTxn = bdbEnv.beginTransaction();

				// まず取得
				BDBGet<IncrementInfo> bdbGet = new BDBGet<IncrementInfo>();
				IncrementInfo incrInfo = bdbGet.get(serviceName, bdbTxn, dbIncrement, binding,
						BDBUtil.getLockModeRMW(), uri, requestInfo, connectionInfo);

				// rangeに値がセットされている時と空の時
				long num = 0;
				if (!StringUtils.isBlank(range)) {
					// 番号はrangeの最初の値-1とする。
					IncrementRangeInfo rangeInfo = new IncrementRangeInfo(range);
					num = rangeInfo.getStart() - 1;
				} else if (incrInfo != null) {
					// 番号は現在値のまま
					num = incrInfo.getNum();
				}
				IncrementInfo newIncrInfo = new IncrementInfo(num, range);
				// 更新
				BDBPut<IncrementInfo> bdbPut = new BDBPut<IncrementInfo>();
				bdbPut.put(serviceName, bdbTxn, dbIncrement, binding, uri, newIncrInfo,
						requestInfo, connectionInfo);

				bdbTxn.commit();
				bdbTxn = null;
				return;

			} catch (DatabaseException e) {
				// リトライ判定、入力エラー判定
				BDBUtil.convertError(e, uri, requestInfo);
				if (r >= numRetries) {
					// リトライ対象だがリトライ回数を超えた場合
					BDBUtil.convertIOError(e, uri);
				}
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) +
							"[rangeids] " + ReflexBDBLogUtil.getRetryLog(e, r));
				}
				BDBUtil.sleep(waitMillis + r * 10);

			} finally {
				if (bdbTxn != null) {
					try {
						bdbTxn.abort();
					} catch (Throwable e) {
						logger.warn("[rangeids] abort error.", e);
					}
				}
			}
		}
		throw new IllegalStateException("Unreachable code.");
	}

	/**
	 * 返却番号を編集
	 * @param number データストア登録値
	 * @param rangeInfo 範囲情報
	 * @param isGet 現在値取得の場合true
	 * @param uri URI
	 * @return 返却番号
	 */
	private long editNumber(IncrementInfo incrInfo, boolean isGet, String uri)
	throws OutOfRangeException {
		long retNum = 0;
		if (incrInfo != null) {
			retNum = incrInfo.getNum();
			IncrementRangeInfo rangeInfo = getIncrementRangeInfo(incrInfo.getRange());
			if (rangeInfo != null) {
				Long start = rangeInfo.getStart();
				Long end = rangeInfo.getEnd();
				if (end != null && retNum > end) {
					if (rangeInfo.isNoRotation()) {
						if (!isGet) {
							throw new OutOfRangeException(uri);
						}
						// 現在番号取得の場合はそのままの値を返す。
					} else {
						long rangeNum = rangeInfo.getRangeNum();
						retNum = start + (retNum - start) % rangeNum;
					}
				}
			}
		}
		return retNum;
	}

	/**
	 * 加算枠文字列を取得.
	 * @param rangeInfo 加算枠情報
	 * @return 加算枠文字列
	 */
	private String getIncrementRange(IncrementRangeInfo rangeInfo) {
		if (rangeInfo != null) {
			return rangeInfo.getRange();
		}
		return null;
	}

	/**
	 * 加算枠情報を取得.
	 * @param range 加算枠文字列
	 * @return 加算枠情報
	 */
	private IncrementRangeInfo getIncrementRangeInfo(String range) {
		if (!StringUtils.isBlank(range)) {
			return new IncrementRangeInfo(range);
		}
		return null;
	}

	/**
	 * 範囲チェック
	 * @param number データストア登録値
	 * @param rangeInfo 範囲情報
	 * @param uri URI
	 * @param isSetid 値設定の場合true
	 */
	private void checkWithinRange(Long number, IncrementRangeInfo rangeInfo, String uri,
			boolean isSetid)
	throws OutOfRangeException {
		if (rangeInfo == null || number == null) {
			return;
		}
		Long start = rangeInfo.getStart();
		if (start != null && number < start) {
			if (isSetid) {
				throw new OutOfRangeException(OutOfRangeException.MESSAGE_SETID, uri, number);
			} else {
				throw new OutOfRangeException(uri);
			}
		}
		Long end = rangeInfo.getEnd();
		if (end != null && number > end) {
			if (isSetid) {
				throw new OutOfRangeException(OutOfRangeException.MESSAGE_SETID, uri, number);
			} else if (rangeInfo.isNoRotation()) {
				throw new OutOfRangeException(uri);
			}
		}
	}

	/**
	 * 名前空間からBDB環境情報を取得.
	 * @param namespace 名前空間
	 * @param isCreate 指定された名前空間のBDB環境が存在しない時作成する場合true
	 * @return BDB環境情報
	 */
	private BDBEnv getBDBEnvByNamespace(String namespace,
			boolean isCreate)
	throws IOException, TaggingException {
		boolean setAccesstime = false;	// 
		BDBEnvManager bdbEnvManager = new BDBEnvManager();
		return bdbEnvManager.getBDBEnvByNamespace(AllocidsConst.DB_NAMES,
				namespace, isCreate, setAccesstime);
	}

}
