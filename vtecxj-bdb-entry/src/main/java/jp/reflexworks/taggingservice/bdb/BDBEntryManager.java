package jp.reflexworks.taggingservice.bdb;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sleepycat.bind.EntryBinding;
import com.sleepycat.je.DatabaseException;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.BDBEnvManager;
import jp.reflexworks.taggingservice.env.BDBEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.BDBCondition;
import jp.reflexworks.taggingservice.model.BDBEntryRequestParam;
import jp.reflexworks.taggingservice.model.FetchInfo;
import jp.reflexworks.taggingservice.util.IndexUtil;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.PointerUtil;
import jp.reflexworks.taggingservice.util.ReflexBDBLogUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * BDB操作クラス.
 * 実際の処理は更新、検索、加算、採番担当の各クラスが実装。
 */
public class BDBEntryManager {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * Entry登録・更新
	 * @param namespace 名前空間
	 * @param id ID
	 * @param data Entryのバイト配列データ
	 * @param serviceName サービス名(ログ用)
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void put(String namespace, String id, byte[] data, String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		if (StringUtils.isBlank(id)) {
			throw new IllegalParameterException("ID is required.");
		}
		if (data == null || data.length == 0) {
			throw new IllegalParameterException("Entry data is required.");
		}

		int numRetries = BDBEnvUtil.getBDBRetryCount();
		int waitMillis = BDBEnvUtil.getBDBRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				// BDB環境情報取得
				BDBEnv bdbEnv = getBDBEnvByNamespace(namespace, true);

				BDBDatabase dbEntry = bdbEnv.getDb(BDBEntryConst.DB_ENTRY);
				ByteArrayBinding byteArrayBinding = BDBUtil.getByteArrayBinding();

				// トランザクション開始
				BDBTransaction bdbTxn = bdbEnv.beginTransaction();
				try {
					BDBPut<byte[]> bdbPut = new BDBPut<>();
					bdbPut.put(serviceName, bdbTxn, dbEntry, byteArrayBinding, id, data,
							requestInfo, connectionInfo);

					// コミット
					bdbTxn.commit();
					bdbTxn = null;

				} finally {
					if (bdbTxn != null) {
						try {
							bdbTxn.abort();
						} catch (DatabaseException e) {
							logger.warn(LogUtil.getRequestInfoStr(requestInfo) +
									"[put] " + e.getClass().getName(), e);
						}
					}
				}
				return;

			} catch (DatabaseException e) {
				// リトライ判定、入力エラー判定
				BDBUtil.convertError(e, id, requestInfo);
				if (r >= numRetries) {
					// リトライ対象だがリトライ回数を超えた場合
					BDBUtil.convertIOError(e, id);
				}
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) +
							"[put] " + ReflexBDBLogUtil.getRetryLog(e, r));
				}
				BDBUtil.sleep(waitMillis + r * 10);
			}
		}
		throw new IllegalStateException("Unreachable code.");
	}

	/**
	 * Entry削除
	 * @param namespace 名前空間
	 * @param id ID
	 * @param serviceName サービス名(ログ用)
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void delete(String namespace, String id, String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		if (StringUtils.isBlank(id)) {
			throw new IllegalParameterException("ID is required.");
		}

		int numRetries = BDBEnvUtil.getBDBRetryCount();
		int waitMillis = BDBEnvUtil.getBDBRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				// BDB環境情報取得
				BDBEnv bdbEnv = getBDBEnvByNamespace(namespace, true);

				BDBDatabase dbEntry = bdbEnv.getDb(BDBEntryConst.DB_ENTRY);

				// トランザクション開始
				BDBTransaction bdbTxn = bdbEnv.beginTransaction();
				try {
					BDBDelete bdbDelete = new BDBDelete();
					bdbDelete.delete(serviceName, bdbTxn, dbEntry, id,
							requestInfo, connectionInfo);

					// コミット
					bdbTxn.commit();
					bdbTxn = null;

				} finally {
					if (bdbTxn != null) {
						try {
							bdbTxn.abort();
						} catch (DatabaseException e) {
							logger.warn(LogUtil.getRequestInfoStr(requestInfo) +
									"[delete] " + e.getClass().getName(), e);
						}
					}
				}
				return;

			} catch (DatabaseException e) {
				// リトライ判定、入力エラー判定
				BDBUtil.convertError(e, id, requestInfo);
				if (r >= numRetries) {
					// リトライ対象だがリトライ回数を超えた場合
					BDBUtil.convertIOError(e, id);
				}
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) +
							"[delete] " + ReflexBDBLogUtil.getRetryLog(e, r));
				}
				BDBUtil.sleep(waitMillis + r * 10);
			}
		}
		throw new IllegalStateException("Unreachable code.");
	}

	/**
	 * Entry取得
	 * @param namespace 名前空間
	 * @param id ID
	 * @param serviceName サービス名(ログ用)
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public byte[] get(String namespace, String id, String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		if (StringUtils.isBlank(id)) {
			throw new IllegalParameterException("ID is required.");
		}

		int numRetries = BDBEnvUtil.getBDBRetryCount();
		int waitMillis = BDBEnvUtil.getBDBRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				// BDB環境情報取得
				BDBEnv bdbEnv = getBDBEnvByNamespace(namespace, true);

				BDBDatabase dbEntry = bdbEnv.getDb(BDBEntryConst.DB_ENTRY);
				ByteArrayBinding byteArrayBinding = BDBUtil.getByteArrayBinding();
				BDBGet<byte[]> bdbGet = new BDBGet<>();
				return bdbGet.get(serviceName, null, dbEntry, byteArrayBinding, 
						BDBUtil.getLockMode(), id, requestInfo, connectionInfo);

			} catch (DatabaseException e) {
				// リトライ判定、入力エラー判定
				BDBUtil.convertError(e, id, requestInfo);
				if (r >= numRetries) {
					// リトライ対象だがリトライ回数を超えた場合
					BDBUtil.convertIOError(e, id);
				}
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) +
							"[get] " + ReflexBDBLogUtil.getRetryLog(e, r));
				}
				BDBUtil.sleep(waitMillis + r * 10);
			}
		}
		throw new IllegalStateException("Unreachable code.");
	}

	/**
	 * テーブルデータ全件取得.
	 * キーリストを返却する。
	 * @param namespace 名前空間
	 * @param param テーブル名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return テーブルデータ全件リスト
	 *         続きがある場合はカーソルを返す。(Feedのlink rel="next"のhrefに設定)
	 */
	public FetchInfo<?> getList(String namespace, BDBEntryRequestParam param,
			RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[getList] namespace=");
			sb.append(namespace);
			logger.trace(sb.toString());
		}

		BDBQuery bdbQuery = new BDBQuery();
		int limit = getLimit(param.getOption(BDBEntryRequestParam.PARAM_LIMIT));
		String pointerStr = PointerUtil.decode(
				param.getOption(BDBEntryRequestParam.PARAM_NEXT));

		int numRetries = BDBEnvUtil.getBDBRetryCount();
		int waitMillis = BDBEnvUtil.getBDBRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				// テーブル判定
				BDBDatabase db = null;
				EntryBinding<?> binding = null;
				BDBEnv bdbEnv = getBDBEnvByNamespace(namespace);
				if (bdbEnv == null) {
					return null;
				}
				String tableName = param.getOption(BDBEntryRequestParam.PARAM_LIST);
				if (BDBEntryConst.DB_ENTRY.equals(tableName)) {
					db = bdbEnv.getDb(BDBEntryConst.DB_ENTRY);
					binding = BDBUtil.getByteArrayBinding();

				} else {
					throw new IllegalArgumentException("The specified table does not exist. " + tableName);
				}

				String keyprefix = param.getOption(BDBEntryRequestParam.PARAM_KEYPREFIX);
				String keyend = IndexUtil.getEndKeyStr(keyprefix);
				BDBCondition bdbCondition = new BDBCondition(keyprefix, keyend, pointerStr);

				FetchInfo<?> fetchInfo = bdbQuery.getByQuery(namespace, null, db, binding,
						bdbCondition, limit, requestInfo);
				return fetchInfo;

			} catch (DatabaseException e) {
				// リトライ判定、入力エラー判定
				BDBUtil.convertError(e, null, requestInfo);
				if (r >= numRetries) {
					// リトライ対象だがリトライ回数を超えた場合
					BDBUtil.convertIOError(e, null);
				}
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) +
							"[getList] " + ReflexBDBLogUtil.getRetryLog(e, r));
				}
				BDBUtil.sleep(waitMillis + r * 10);
			}
		}
		throw new IllegalStateException("Unreachable code.");
	}

	/**
	 * リクエストパラメータから指定件数を取得.
	 * @param limitStr lパラメータの値
	 * @return 指定件数
	 */
	private int getLimit(String limitStr) {
		int defLimit = BDBEnvUtil.getEntryNumberLimit();
		return StringUtils.intValue(limitStr, defLimit);
	}

	/**
	 * BDB環境統計情報取得.
	 * @param namespace 名前空間
	 * @param param テーブル名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return BDB環境統計情報 (Feedのsubtitleに設定)
	 */
	public FeedBase getStats(String namespace, BDBEntryRequestParam param,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		BDBEnvManager bdbEnvManager = new BDBEnvManager();
		return bdbEnvManager.getStatsByNamespace(BDBEntryConst.DB_NAMES, namespace,
				requestInfo, connectionInfo);
	}

	/**
	 * ディスク使用率取得.
	 * @param param URLパラメータ
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return ディスク使用率(%) (Feedのsubtitleに設定)
	 */
	public FeedBase getDiskUsage(BDBEntryRequestParam param,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		BDBEnvManager bdbEnvManager = new BDBEnvManager();
		return bdbEnvManager.getDiskUsage(requestInfo, connectionInfo);
	}

	/**
	 * BDB環境クローズ処理.
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void closeBDBEnv(String namespace,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		BDBEnvManager bdbEnvManager = new BDBEnvManager();
		bdbEnvManager.closeBDBEnv(namespace);
	}

	/**
	 * 名前空間からBDB環境情報を取得.
	 * 参照用。指定された名前空間のBDB環境が存在しない時は新規作成せずエラーとする。
	 * @param namespace 名前空間
	 * @return BDB環境情報
	 */
	private BDBEnv getBDBEnvByNamespace(String namespace)
	throws IOException, TaggingException {
		return getBDBEnvByNamespace(namespace, false);
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
		boolean setAccesstime = false;	// TODO
		BDBEnvManager bdbEnvManager = new BDBEnvManager();
		return bdbEnvManager.getBDBEnvByNamespace(BDBEntryConst.DB_NAMES,
				namespace, isCreate, setAccesstime);
	}

}
