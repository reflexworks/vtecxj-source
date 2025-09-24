package jp.reflexworks.taggingservice.bdb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sleepycat.bind.EntryBinding;
import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.OperationStatus;

import jp.reflexworks.atom.api.Condition;
import jp.reflexworks.atom.api.EntryUtil.UriPair;
import jp.reflexworks.atom.entry.Category;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.context.InnerIndexContext;
import jp.reflexworks.taggingservice.env.BDBEnvManager;
import jp.reflexworks.taggingservice.env.BDBEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.RetryExceededException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.BDBCondition;
import jp.reflexworks.taggingservice.model.FetchInfo;
import jp.reflexworks.taggingservice.model.InnerIndexRequestParam;
import jp.reflexworks.taggingservice.util.InnerIndexCheckUtil;
import jp.reflexworks.taggingservice.util.InnerIndexUtil;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.PointerUtil;
import jp.reflexworks.taggingservice.util.ReflexBDBLogUtil;
import jp.reflexworks.taggingservice.util.ReflexCheckUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * BDB操作クラス.
 * 実際の処理は更新、検索、加算、採番担当の各クラスが実装。
 */
public class InnerIndexBDBManager {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * インデックス登録・更新
	 * @param namespace 名前空間
	 * @param feed Feed
	 * @param isPartial 指定されたキー・項目のみの更新の場合true
	 * @param isDelete 削除の場合true
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void put(String namespace, FeedBase feed, boolean isPartial,
			boolean isDelete, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		if (!TaggingEntryUtil.isExistData(feed)) {
			throw new IllegalParameterException("Feed is required.");
		}

		// キー: id、値: インデックス値
		Map<String, Set<String>> putIndexes = new LinkedHashMap<>();
		// インデックス項目名の短縮値 一度取得したものを格納するMap
		Map<String, String> indexItemMap = new HashMap<>();
		// DISTKEYの項目名の短縮値 一度取得したものを格納するMap
		Map<String, String> distkeyItemMap = new HashMap<>();

		for (EntryBase entry : feed.entry) {
			String id = entry.id;
			// link rel="self"のhrefにキー
			String uri = entry.getMyUri();
			// titleに項目名
			String item = entry.title;
			// summaryに値
			String text = entry.summary;
			// DISTKEYマップ キー:DISTKEY項目の短縮値、値:DISTKEYの値
			Map<String, String> distkeys = null;
			if (entry.category != null && !entry.category.isEmpty()) {
				distkeys = new LinkedHashMap<>();
				for (Category category : entry.category) {
					String distkeyItem = category._$scheme;
					if (!StringUtils.isBlank(distkeyItem)) {
						// distkeyの短縮名を取得
						String distkeyShortening = distkeyItemMap.get(distkeyItem);
						if (distkeyShortening == null) {
							distkeyShortening = getDistkeyItemShortening(namespace, distkeyItem,
									requestInfo, connectionInfo);
							distkeyItemMap.put(distkeyItem, distkeyShortening);
						}
						distkeys.put(distkeyShortening, StringUtils.null2blank(category._$label));
					}
				}
			}

			// idチェック
			ReflexCheckUtil.checkId(id);

			if (!isDelete || isPartial) {
				// 登録更新、部分削除
				InnerIndexCheckUtil.checkUri(uri);
				boolean hasDistkey = distkeys != null && !distkeys.isEmpty();
				if (!hasDistkey) {
					InnerIndexCheckUtil.checkNotNull(item, "Item name");
				}

				Set<String> indexes = putIndexes.get(id);
				if (indexes == null) {
					indexes = new LinkedHashSet<>();
					putIndexes.put(id, indexes);
				}
				if (!(isDelete && isPartial) &&
						StringUtils.isBlank(text) && !hasDistkey) {
					// 登録更新で、項目に値なし
					continue;
				}

				UriPair uriPair = TaggingEntryUtil.getUriPair(uri);
				String selfid = uriPair.selfid;
				String editItem = StringUtils.null2blank(item);
				String parentItem = getParentItem(
						TaggingEntryUtil.removeLastSlash(uriPair.parent), editItem);

				// インデックス項目名の短縮値を取得
				String parentItemShortening = indexItemMap.get(parentItem);
				if (parentItemShortening == null) {
					parentItemShortening = getInnerIndexItemShortening(
							namespace, parentItem, requestInfo, connectionInfo);
					indexItemMap.put(parentItem, parentItemShortening);
				}

				if (hasDistkey) {
					// DISTKEYマップ キー:DISTKEY項目の短縮値、値:DISTKEYの値
					for (Map.Entry<String, String> mapEntry : distkeys.entrySet()) {
						String distkeyShortening = mapEntry.getKey();
						String distkeyValue = mapEntry.getValue();
						String index = InnerIndexUtil.getInnerIndex(parentItemShortening,
								selfid, text, distkeyShortening, distkeyValue);
						indexes.add(index);
					}
				} else {
					// DISTKEYなし
					String index = InnerIndexUtil.getInnerIndex(parentItemShortening,
							selfid, text, null, null);
					indexes.add(index);
				}

			} else {
				// Entry削除
				putIndexes.put(id, null);
			}
		}

		for (Map.Entry<String, Set<String>> mapEntry : putIndexes.entrySet()) {
			String id = mapEntry.getKey();
			List<String> indexes = null;
			Set<String> values = mapEntry.getValue();
			if (!isDelete || isPartial) {
				int size = values.size();
				indexes = new ArrayList<>(size);
				if (size > 0) {
					indexes.addAll(values);
				}
			}
			updateIndexes(namespace, id, indexes, isPartial, isDelete,
					requestInfo, connectionInfo);
		}
	}

	/**
	 * インデックスを更新.
	 * @param namespace 名前空間
	 * @param id ID
	 * @param newIndexes インデックスリスト
	 * @param isPartial 指定されたキー・項目のみの更新の場合true
	 * @param isDelete 削除の場合true
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	private void updateIndexes(String namespace, String id, List<String> newIndexes,
			boolean isPartial, boolean isDelete,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		int numRetries = BDBEnvUtil.getBDBRetryCount();
		int waitMillis = BDBEnvUtil.getBDBRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				// BDB環境情報取得
				BDBEnv bdbEnv = getBDBEnvByNamespace(
						namespace, true);

				BDBDatabase dbIndex = bdbEnv.getDb(InnerIndexBDBConst.DB_INNER_INDEX);
				BDBDatabase dbIndexAncestor = bdbEnv.getDb(InnerIndexBDBConst.DB_INNER_INDEX_ANCESTOR);

				// トランザクション開始
				BDBTransaction bdbTxn = bdbEnv.beginTransaction();
				try {
					// インデックスを更新
					updateIndexesProc(namespace, bdbTxn, dbIndex, dbIndexAncestor, id,
							newIndexes, isPartial, isDelete, requestInfo, connectionInfo);

					// コミット
					bdbTxn.commit();
					bdbTxn = null;

				} finally {
					if (bdbTxn != null) {
						try {
							bdbTxn.abort();
						} catch (DatabaseException e) {
							logger.warn(LogUtil.getRequestInfoStr(requestInfo) +
									"[updateIndexes] " + e.getClass().getName(), e);
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
							"[updateIndexes] " + ReflexBDBLogUtil.getRetryLog(e, r));
				}
				BDBUtil.sleep(waitMillis + r * 10);
			}
		}
		throw new IllegalStateException("Unreachable code.");
	}

	/**
	 * インデックス情報を更新
	 * @param namespace 名前空間
	 * @param bdbTxn トランザクション
	 * @param db インデックステーブル
	 * @param dbAncestor インデックスAncestor
	 * @param id ID
	 * @param indexList インデックスリスト (Entry削除の場合はnull)
	 * @param isPartial 指定されたキー・項目のみの更新の場合true
	 * @param isDelete 削除の場合true
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	private void updateIndexesProc(String namespace, BDBTransaction bdbTxn,
			BDBDatabase db, BDBDatabase dbAncestor, String id,
			List<String> indexList, boolean isPartial, boolean isDelete,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		BDBGet<List<String>> bdbGetAncestor = new BDBGet<>();
		BDBPut<String> bdbPutString = new BDBPut<>();
		BDBPut<List<String>> bdbPutAncestor = new BDBPut<>();
		BDBDelete bdbDelete = new BDBDelete();

		ListBinding listBinding = new ListBinding();
		StringBinding stringBinding = BDBUtil.getStringBinding();

		// 現在のIndexを取得
		String idUri = TaggingEntryUtil.getUriById(id);
		List<String> currentIndexes = bdbGetAncestor.get(namespace, bdbTxn, dbAncestor,
				listBinding, BDBUtil.getLockMode(), idUri, requestInfo, connectionInfo);

		// 今回のIndexを生成
		boolean isPutAncestor = false;
		List<String> newIndexes = new ArrayList<>();
		if (indexList != null && !isDelete) {
			// 登録更新
			for (String index : indexList) {
				newIndexes.add(index);
				// 登録
				bdbPutString.put(namespace, bdbTxn, db, stringBinding, index, id,
						requestInfo, connectionInfo);
				if (!isPutAncestor) {
					if (currentIndexes == null || !currentIndexes.contains(index)) {
						isPutAncestor = true;
					}
				}
			}
		}

		// 除去されたインデックスを削除
		if (currentIndexes != null) {
			List<String> remainingCurrentIndexes = new ArrayList<>();
			for (String currentIndex : currentIndexes) {
				if (newIndexes.contains(currentIndex)) {
					continue;
				}
				boolean isDeleteCurrent = true;
				if (isPartial) {
					if (!isDelete) {
						// 部分更新
						// インデックス項目短縮値が等しく、
						// 新しく登録したインデックスに合致しないものがあれば削除する。
						if (!containsShorting(currentIndex, newIndexes)) {
							// isPartial=trueでインデックス項目短縮値が合致しない場合は、
							// 変更対象でないので残す。
							isDeleteCurrent = false;
							remainingCurrentIndexes.add(currentIndex);
						}
					} else {
						// 部分削除
						if (!containsShorting(currentIndex, indexList)) {
							// 変更対象でないので残す。
							isDeleteCurrent = false;
							remainingCurrentIndexes.add(currentIndex);
						}
					}
				}

				if (isDeleteCurrent) {
					bdbDelete.delete(namespace, bdbTxn, db, currentIndex,
							requestInfo, connectionInfo);
					if (!isPutAncestor) {
						isPutAncestor = true;
					}
				}
			}
			if (!remainingCurrentIndexes.isEmpty()) {
				newIndexes.addAll(remainingCurrentIndexes);
			}
		}

		// Ancestorを上書き
		if (isPutAncestor) {
			if (isDelete && !isPartial) {
				// 削除
				bdbDelete.delete(namespace, bdbTxn, dbAncestor, idUri, requestInfo,
						connectionInfo);
			} else {
				// 更新または部分削除
				bdbPutAncestor.put(namespace, bdbTxn, dbAncestor, listBinding, idUri,
						newIndexes, requestInfo, connectionInfo);
			}
		}
	}

	/**
	 * 条件検索.
	 * キーリストを返却する。
	 * @param namespace 名前空間
	 * @param param 親URI、検索条件
	 * @param distkeyItem DISTKEY項目
	 * @param distkeyValue DISTKEYの値
	 * @param reflexContext ReflexContext
	 * @return EntryのIDリストと、続きがある場合はカーソルを返す。
	 */
	public FetchInfo<String> getFeedKeys(String namespace,
			InnerIndexRequestParam param,
			String distkeyItem, String distkeyValue,
			InnerIndexContext reflexContext)
	throws IOException, TaggingException {
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();

		BDBQuery<String> bdbQuery = new BDBQuery<String>();
		StringBinding binding = BDBUtil.getStringBinding();
		String uri = param.getUri();
		InnerIndexCheckUtil.checkUri(uri);
		Condition condition = param.getCondition();
		Condition conditionRange = param.getConditionRange();
		checkCondition(condition, conditionRange, distkeyItem, distkeyValue);

		int limit = getLimit(param.getOption(InnerIndexRequestParam.PARAM_LIMIT));

		String cursorStr = PointerUtil.decode(
				param.getOption(InnerIndexRequestParam.PARAM_NEXT));
		BDBCondition bdbCondition = createBDBCondition(namespace, uri,
				condition, conditionRange, distkeyItem, distkeyValue, cursorStr,
				requestInfo, connectionInfo);

		int numRetries = BDBEnvUtil.getBDBRetryCount();
		int waitMillis = BDBEnvUtil.getBDBRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				BDBEnv bdbEnv = getBDBEnvByNamespace(
						namespace);
				if (bdbEnv == null) {
					// 環境が存在しない場合はデータなし
					return null;
				}
				BDBDatabase db = bdbEnv.getDb(InnerIndexBDBConst.DB_INNER_INDEX);
				FetchInfo<String> fetchInfo = bdbQuery.getByQuery(namespace, null, db,
						binding, bdbCondition, limit, requestInfo);

				return fetchInfo;

			} catch (DatabaseException e) {
				// リトライ判定、入力エラー判定
				BDBUtil.convertError(e, uri, requestInfo);
				if (r >= numRetries) {
					// リトライ対象だがリトライ回数を超えた場合
					BDBUtil.convertIOError(e, uri);
				}
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) +
							"[getFeedKeys] " + ReflexBDBLogUtil.getRetryLog(e, r));
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
	public FetchInfo<?> getList(String namespace, InnerIndexRequestParam param,
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
		int limit = getLimit(param.getOption(InnerIndexRequestParam.PARAM_LIMIT));
		String pointerStr = PointerUtil.decode(
				param.getOption(InnerIndexRequestParam.PARAM_NEXT));

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
				String tableName = param.getOption(InnerIndexRequestParam.PARAM_LIST);
				if (InnerIndexBDBConst.DB_INNER_INDEX.equals(tableName)) {
					db = bdbEnv.getDb(InnerIndexBDBConst.DB_INNER_INDEX);
					binding = BDBUtil.getStringBinding();
				} else if (InnerIndexBDBConst.DB_INNER_INDEX_ANCESTOR.equals(tableName)) {
					db = bdbEnv.getDb(InnerIndexBDBConst.DB_INNER_INDEX_ANCESTOR);
					binding = BDBUtil.getListBinding();
				} else if (InnerIndexBDBConst.DB_INNER_INDEX_ITEM.equals(tableName)) {
					db = bdbEnv.getDb(InnerIndexBDBConst.DB_INNER_INDEX_ITEM);
					binding = BDBUtil.getStringBinding();
				} else if (InnerIndexBDBConst.DB_DISTKEY_ITEM.equals(tableName)) {
					db = bdbEnv.getDb(InnerIndexBDBConst.DB_DISTKEY_ITEM);
					binding = BDBUtil.getStringBinding();
				} else if (InnerIndexBDBConst.DB_ALLOCIDS.equals(tableName)) {
					db = bdbEnv.getDb(InnerIndexBDBConst.DB_ALLOCIDS);
					binding = BDBUtil.getIntegerBinding();

				} else {
					throw new IllegalArgumentException("The specified table does not exist. " + tableName);
				}

				String keyprefix = param.getOption(InnerIndexRequestParam.PARAM_KEYPREFIX);
				String keyend = InnerIndexUtil.getEndKeyStr(keyprefix);
				BDBCondition bdbCondition = new BDBCondition(keyprefix, keyend, pointerStr);

				FetchInfo<?> fetchInfo = bdbQuery.getByQuery(namespace, null, db, binding,
						bdbCondition, limit, requestInfo);

				if (InnerIndexBDBConst.DB_ALLOCIDS.equals(tableName)) {
					// allocidsの場合、sequenceから取得しrollbackする。
					if (fetchInfo == null) {
						return null;
					}
					return getAllocidsList(bdbEnv, fetchInfo.getKeys(), fetchInfo.getPointerStr(),
							requestInfo, connectionInfo);

				} else {
					return fetchInfo;
				}

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
	 * 検索条件チェック.
	 * @param condition 検索条件
	 * @param conditionRange 検索条件(範囲)
	 * @param distkeyItem DISTKEY項目
	 * @param distkeyValue DISTKEYの値
	 */
	private void checkCondition(Condition condition, Condition conditionRange,
			String distkeyItem, String distkeyValue) {
		boolean hasDistkey = !StringUtils.isBlank(distkeyItem);
		if (!hasDistkey || condition != null) {
			InnerIndexCheckUtil.checkCondition(condition, conditionRange);
		}
		if (hasDistkey) {
			InnerIndexCheckUtil.checkNotNull(distkeyValue, "distkey value");
		}
	}

	/**
	 * BDB環境統計情報取得.
	 * @param namespace 名前空間
	 * @param param テーブル名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return BDB環境統計情報 (Feedのsubtitleに設定)
	 */
	public FeedBase getStats(String namespace, InnerIndexRequestParam param,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		BDBEnvManager bdbEnvManager = new BDBEnvManager();
		return bdbEnvManager.getStatsByNamespace(InnerIndexBDBConst.DB_NAMES, namespace,
				requestInfo, connectionInfo);
	}

	/**
	 * ディスク使用率取得.
	 * @param param URLパラメータ
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return ディスク使用率(%) (Feedのsubtitleに設定)
	 */
	public FeedBase getDiskUsage(InnerIndexRequestParam param,
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
	 * {親階層}#{Index項目} を取得する.
	 * @param parentUri 親階層
	 * @param indexItem Index項目
	 * @return {親階層}#{Index項目}
	 */
	private String getParentItem(String parentUri, String indexItem) {
		StringBuilder item = new StringBuilder();
		item.append(parentUri);
		item.append(InnerIndexUtil.ITEM_PREFIX);
		item.append(indexItem);
		return item.toString();
	}

	/**
	 * インデックス項目名の短縮値を取得.
	 * {親階層}#{Index項目} の短縮値を取得する。
	 * @param namespace 名前空間
	 * @param item {親階層}#{Index項目}
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return インデックス項目の短縮値
	 */
	private String getInnerIndexItemShortening(String namespace, String item,
			RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		return getShortening(namespace, false, item, requestInfo, connectionInfo);
	}

	/**
	 * DISTKEY項目の短縮値を取得
	 * @param namespace 名前空間
	 * @param distkeyItem DISTKEY項目名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return DISTKEY項目の短縮値
	 */
	private String getDistkeyItemShortening(String namespace,
			String distkeyItem, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		return getShortening(namespace, true, distkeyItem, requestInfo, connectionInfo);
	}

	/**
	 * 短縮値を取得
	 * @param namespace 名前空間
	 * @param isDistkey Distkeyの場合true、全文検索インデックス項目名の場合false
	 * @param dbItem 項目と短縮値を格納しているテーブル
	 * @param item 項目名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 短縮値
	 */
	private String getShortening(String namespace, boolean isDistkey,
			String item, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		if (item == null) {
			return null;
		}

		StringBinding stringBinding = BDBUtil.getStringBinding();

		int numRetries = BDBEnvUtil.getBDBRetryCount();
		int waitMillis = BDBEnvUtil.getBDBRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			BDBTransaction bdbTxn = null;
			try {
				// まずItemテーブルから値を取得
				// BDB環境情報取得
				BDBEnv bdbEnv = getBDBEnvByNamespace(namespace, true);

				BDBDatabase dbItem = null;
				if (isDistkey) {
					dbItem = bdbEnv.getDb(InnerIndexBDBConst.DB_DISTKEY_ITEM);
				} else {
					dbItem = bdbEnv.getDb(InnerIndexBDBConst.DB_INNER_INDEX_ITEM);
				}
				StringBinding binding = BDBUtil.getStringBinding();
				BDBGet<String> bdbGet = new BDBGet<>();
				String shortening = bdbGet.get(namespace, null, dbItem, binding,
						BDBUtil.getLockMode(), item, requestInfo, connectionInfo);
				if (!StringUtils.isBlank(shortening)) {
					return shortening;
				}

				// Itemテーブルに存在しない場合、登録
				// 短縮値を取得
				shortening = allocids(bdbEnv, InnerIndexBDBConst.KEY_SHORTENING,
						false, requestInfo, connectionInfo);

				// トランザクション開始
				bdbTxn = bdbEnv.beginTransaction();
				try {
					BDBPutNoOverwrite<String> bdbPutNoOverwrite = new BDBPutNoOverwrite<String>();
					OperationStatus operationStatus = bdbPutNoOverwrite.putNoOverwrite(
							namespace, bdbTxn, dbItem, stringBinding,
							item, shortening, requestInfo, connectionInfo);
					if (operationStatus == OperationStatus.SUCCESS) {
						// コミット
						bdbTxn.commit();
						bdbTxn = null;
						if (logger.isDebugEnabled()) {
							StringBuilder sb = new StringBuilder();
							sb.append(LogUtil.getRequestInfoStr(requestInfo));
							sb.append("[getShortening] putNoOverwrite succeeded.");
							sb.append(" item=");
							sb.append(item);
							sb.append(", shortening=");
							sb.append(shortening);
							logger.debug(sb.toString());
						}
						return shortening;
					}

					// OperationStatus.KEYEXISTの場合は後続処理
					if (logger.isTraceEnabled()) {
						StringBuilder sb = new StringBuilder();
						sb.append(LogUtil.getRequestInfoStr(requestInfo));
						sb.append("[getShortening] putNoOverwrite failed.");
						sb.append(" item=");
						sb.append(item);
						sb.append(", distkeyShortening=");
						sb.append(shortening);
						sb.append(", OperationStatus=");
						sb.append(operationStatus.name());
						logger.debug(sb.toString());
					}

				} finally {
					if (bdbTxn != null) {
						try {
							bdbTxn.abort();
						} catch (DatabaseException e) {
							logger.warn(LogUtil.getRequestInfoStr(requestInfo) +
									"[getShortening] " + e.getClass().getName(), e);
						}
					}
				}

				// 登録できなかった場合は再度Itemテーブルから値を取得
				shortening = bdbGet.get(namespace, null, dbItem, binding,
						BDBUtil.getLockMode(), item, requestInfo, connectionInfo);
				if (!StringUtils.isBlank(shortening)) {
					return shortening;
				}
				BDBUtil.sleep(waitMillis + r * 10);

			} catch (DatabaseException e) {
				// リトライ判定、入力エラー判定
				BDBUtil.convertError(e, item, requestInfo);
				if (r >= numRetries) {
					// リトライ対象だがリトライ回数を超えた場合
					BDBUtil.convertIOError(e, item);
				}
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) +
							"[getShortening] " + ReflexBDBLogUtil.getRetryLog(e, r));
				}
				BDBUtil.sleep(waitMillis + r * 10);
			}
		}

		StringBuilder msg = new StringBuilder();
		msg.append("get item shortening retry exceeded.");
		msg.append(" item=");
		msg.append(item);
		if (isDistkey) {
			msg.append(" (distkey)");
		} else {
			msg.append(" (fulltextindex)");
		}
		throw new RetryExceededException(msg.toString());
	}

	/**
	 * 採番し、短縮値を取得する.
	 * @param bdbEnv BDB環境
	 * @param key キー
	 * @param refer 参照の場合true、採番の場合false
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 短縮値
	 */
	private String allocids(BDBEnv bdbEnv, String key, boolean refer,
			RequestInfo requestInfo, ConnectionInfo connectionInfo) {
		BDBTransaction bdbTxn = null;
		BDBSequence sequence = null;
		try {
			// トランザクション開始
			bdbTxn = bdbEnv.beginTransaction();
			sequence = bdbEnv.getSequence(InnerIndexBDBConst.DB_ALLOCIDS, bdbTxn, key);
			long allocids = sequence.get(bdbTxn, 1);

			if (!refer) {
				// sequenceの最初は0なので、0であれば飛ばして1から返すようにする。
				if (allocids <= 0) {
					allocids = sequence.get(bdbTxn, 1);
				}
				bdbTxn.commit();
				bdbTxn = null;
			} else {
				if (allocids > 0) {
					allocids--;	// 参照時は加算分をマイナス
				}
			}

			return String.valueOf(allocids);

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

	/**
	 * 検索のためのインデックスを取得.
	 * @param namespace 名前空間
	 * @param parentUri キー
	 * @param item 検索項目
	 * @param value 検索項目の値
	 * @param distkeyItem DISTKEY項目
	 * @param distkeyValue DISTKEYの値
	 * @param isEqual equal条件の場合true。selfidの\u0001まで付けて返す。
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 検索のためのインデックス
	 */
	private String getInnerIndexByGet(String namespace, String parentUri,
			String item, String value, String distkeyItem, String distkeyValue,
			boolean isEqual, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// インデックス項目名の短縮値を取得.
		String editItem = StringUtils.null2blank(item);
		String parentItem = getParentItem(parentUri, editItem);
		String parentItemShortening = getInnerIndexItemShortening(
				namespace, parentItem, requestInfo, connectionInfo);
		Map<String, String> distkeys = null;
		if (!StringUtils.isBlank(distkeyItem)) {
			distkeys = new HashMap<>(1);
			distkeys.put(distkeyItem, distkeyValue);
		}
		String distkeyShortening = getDistkeyItemShortening(namespace, distkeyItem,
				requestInfo, connectionInfo);

		return InnerIndexUtil.getInnerIndexByGet(parentItemShortening,
				value, distkeyShortening, distkeyValue, isEqual);
	}

	/**
	 * Allocidsの値リスト取得.
	 * @param bdbEnv BDB環境
	 * @param keys キーリスト
	 * @param pointerStr カーソル文字列
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return Allocidsの値リスト
	 */
	private FetchInfo<String> getAllocidsList(BDBEnv bdbEnv,
			List<String> keys, String pointerStr,
			RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		if (keys == null) {
			return null;
		}
		Map<String, String> result = new LinkedHashMap<>();
		for (String key : keys) {
			String val = allocids(bdbEnv, key,
					true, requestInfo, connectionInfo);	// 参照モード
			result.put(key, val);
		}
		return new FetchInfo<String>(result, pointerStr);
	}

	/**
	 * 現在のインデックスが、新しく登録したインデックスのうち項目短縮値に合致するかどうかチェック
	 * @param currentIndex 現在登録されているインデックス
	 * @param newIndexes 新しいインデックスリスト
	 * @return インデックス項目短縮値が等しい場合true
	 */
	private boolean containsShorting(String currentIndex, List<String> newIndexes) {
		String currentIndexShorting = InnerIndexUtil.getShortingByIndexUri(currentIndex);
		for (String newIndex : newIndexes) {
			String newIndexShorting = InnerIndexUtil.getShortingByIndexUri(newIndex);
			if (currentIndexShorting.equals(newIndexShorting)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * BDB検索条件オブジェクトを生成
	 * @param namespace 名前空間
	 * @param uri 親URI
	 * @param condition 検索条件
	 * @param conditionRange 検索条件(範囲)
	 * @param distkeyItem DISTKEY項目
	 * @param distkeyValue DISTKEYの値
	 * @param cursorStr カーソル
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return BDB検索条件オブジェクト
	 */
	private BDBCondition createBDBCondition(String namespace, String uri,
			Condition condition, Condition conditionRange,
			String distkeyItem, String distkeyValue, String cursorStr,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String startKeyStr = null;	// 開始キー
		String endKeyStr = null;	// 終了キー
		boolean excludeStartKey = false;	// 開始キーを含まないかどうか (デフォルトfalse=含む)
		boolean excludeEndKey = false;	// 終了キーを含まないかどうか (デフォルトfalse=含む)

		// eq gt ge lt le fm asc

		// 開始条件
		String item = null;
		String equation = null;
		boolean isEqual = false;
		String startValue = null;
		if (condition != null) {
			item = condition.getProp();
			equation = condition.getEquations();
			isEqual = Condition.EQUAL.equals(equation);
			if (isEqual ||
					Condition.GREATER_THAN.equals(equation) ||
					Condition.GREATER_THAN_OR_EQUAL.equals(equation) ||
					Condition.FORWARD_MATCH.equals(equation)) {
				// 値を条件に含む
				startValue = condition.getValue();
				// 開始条件自身を含むかどうか
				if (Condition.GREATER_THAN.equals(equation)) {
					excludeStartKey = true;
				}
			}
		}
		startKeyStr = getInnerIndexByGet(namespace, uri, item, startValue,
				distkeyItem, distkeyValue, isEqual, requestInfo, connectionInfo);

		// eq gt ge lt le fm asc

		// 終了条件
		String endValue = null;
		boolean setLessThanEnd = false;
		if (Condition.EQUAL.equals(equation) ||
				Condition.LESS_THAN.equals(equation) ||
				Condition.LESS_THAN_OR_EQUAL.equals(equation) ||
				Condition.FORWARD_MATCH.equals(equation)) {
			endValue = condition.getValue();
			// 終了条件自身を含むかどうか
			if (Condition.LESS_THAN.equals(equation)) {
				excludeEndKey = true;
			}
			// 終了文字を付加するかどうか
			if (Condition.LESS_THAN.equals(equation) ||
					Condition.LESS_THAN_OR_EQUAL.equals(equation)) {
				setLessThanEnd = true;
			}

		}
		if (conditionRange != null) {
			endValue = conditionRange.getValue();
			// 終了条件自身を含むかどうか
			if (Condition.LESS_THAN.equals(conditionRange.getEquations())) {
				excludeEndKey = true;
			}
			setLessThanEnd = true;
		}
		endKeyStr = getInnerIndexByGet(namespace, uri, item, endValue,
				distkeyItem, distkeyValue, isEqual, requestInfo, connectionInfo);

		if (setLessThanEnd) {
			endKeyStr = endKeyStr + BDBConst.LESS_THAN_END;
		} else {
			endKeyStr = endKeyStr + BDBConst.FOWARD_MATCHING_END;
		}

		return new BDBCondition(startKeyStr, endKeyStr, cursorStr,
				excludeStartKey, excludeEndKey);
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
		return bdbEnvManager.getBDBEnvByNamespace(InnerIndexBDBConst.DB_NAMES,
				namespace, isCreate, setAccesstime);
	}

}
