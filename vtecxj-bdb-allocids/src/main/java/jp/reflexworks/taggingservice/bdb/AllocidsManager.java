package jp.reflexworks.taggingservice.bdb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sleepycat.bind.EntryBinding;
import com.sleepycat.je.DatabaseException;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.BDBEnvManager;
import jp.reflexworks.taggingservice.env.BDBEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.AllocidsRequestParam;
import jp.reflexworks.taggingservice.model.BDBCondition;
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
public class AllocidsManager {

	/** ログ出力用メソッド名 */
	private static final String LOG_ALLOCIDS = "allocids";

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 採番処理.
	 * @param namespace 名前空間
	 * @param uri キー
	 * @param num 採番数
	 * @param serviceName サービス名(ログ用)
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 採番値。複数の場合はカンマ区切り。
	 */
	public String allocids(String namespace, String uri, int num, String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		int numRetries = BDBEnvUtil.getBDBRetryCount();
		int waitMillis = BDBEnvUtil.getBDBRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			BDBTransaction bdbTxn = null;
			BDBSequence sequence = null;
			try {
				// BDB環境情報取得
				BDBEnv bdbEnv = getBDBEnvByNamespace(namespace, true);

				// トランザクション開始
				bdbTxn = bdbEnv.beginTransaction();

				List<String> allocidsList = new ArrayList<String>();
				sequence = bdbEnv.getSequence(AllocidsConst.DB_ALLOCIDS, bdbTxn, uri);

				// sequenceの最初は0なので、0であれば飛ばして1から返すようにする。
				int start = 0;
				long startTime = 0;
				if (BDBUtil.isEnableAccessLog()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
							BDBUtil.getStartLog(serviceName, LOG_ALLOCIDS, sequence, uri));
					startTime = new Date().getTime();
				}
				long allocids = sequence.get(bdbTxn, 1);
				if (BDBUtil.isEnableAccessLog()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
							BDBUtil.getEndLog(serviceName, LOG_ALLOCIDS, sequence, uri, startTime));
				}

				if (allocids > 0) {
					start = 1;
					allocidsList.add(String.valueOf(allocids));
				}
				for (int i = start; i < num; i++) {
					allocids = sequence.get(bdbTxn, 1);
					allocidsList.add(String.valueOf(allocids));
				}
				bdbTxn.commit();
				bdbTxn = null;

				// 戻り値編集
				StringBuilder sb = new StringBuilder();
				boolean isFirst = true;
				for (String allocid : allocidsList) {
					if (isFirst) {
						isFirst = false;
					} else {
						sb.append(",");
					}
					sb.append(allocid);
				}
				return sb.toString();

			} catch (DatabaseException e) {
				// リトライ判定、入力エラー判定
				BDBUtil.convertError(e, uri, requestInfo);
				if (r >= numRetries) {
					// リトライ対象だがリトライ回数を超えた場合
					BDBUtil.convertIOError(e, uri);
				}
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) +
							"[allocids] " + ReflexBDBLogUtil.getRetryLog(e, r));
				}
				BDBUtil.sleep(waitMillis + r * 10);

			} finally {
				if (bdbTxn != null) {
					try {
						bdbTxn.abort();
					} catch (Throwable e) {
						logger.warn("[allocids] abort error.", e);
					}
				}
				if (sequence != null) {
					sequence.close();
				}
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
	public FetchInfo<?> getList(String namespace, AllocidsRequestParam param,
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
		int limit = getLimit(param.getOption(AllocidsRequestParam.PARAM_LIMIT));
		String pointerStr = PointerUtil.decode(
				param.getOption(AllocidsRequestParam.PARAM_NEXT));

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
				String tableName = param.getOption(AllocidsRequestParam.PARAM_LIST);
				if (AllocidsConst.DB_ALLOCIDS.equals(tableName)) {
					db = bdbEnv.getDb(AllocidsConst.DB_ALLOCIDS);
					binding = BDBUtil.getLongBinding();
				} else if (AllocidsConst.DB_INCREMENT.equals(tableName)) {
					db = bdbEnv.getDb(AllocidsConst.DB_INCREMENT);
					binding = BDBUtil.getIncrementBinding();

				} else {
					throw new IllegalArgumentException("The specified table does not exist. " + tableName);
				}

				String keyprefix = param.getOption(AllocidsRequestParam.PARAM_KEYPREFIX);
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
	public FeedBase getStats(String namespace, AllocidsRequestParam param,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		BDBEnvManager bdbEnvManager = new BDBEnvManager();
		return bdbEnvManager.getStatsByNamespace(AllocidsConst.DB_NAMES, namespace,
				requestInfo, connectionInfo);
	}

	/**
	 * ディスク使用率取得.
	 * @param param URLパラメータ
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return ディスク使用率(%) (Feedのsubtitleに設定)
	 */
	public FeedBase getDiskUsage(AllocidsRequestParam param,
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
		boolean setAccesstime = false;	// 
		BDBEnvManager bdbEnvManager = new BDBEnvManager();
		return bdbEnvManager.getBDBEnvByNamespace(AllocidsConst.DB_NAMES,
				namespace, isCreate, setAccesstime);
	}

}
