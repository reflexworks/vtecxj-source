package jp.reflexworks.taggingservice.bdb;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sleepycat.bind.EntryBinding;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.OperationStatus;

import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.BDBEnvUtil;
import jp.reflexworks.taggingservice.exception.FetchExceededException;
import jp.reflexworks.taggingservice.model.BDBCondition;
import jp.reflexworks.taggingservice.model.FetchInfo;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.PointerUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * クエリ実行クラス.
 * <T> 取得結果データクラス
 */
public class BDBQuery<T> {

	/** ログ出力用メソッド名 */
	private static final String LOG_METHOD = "fetch";

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * クエリ検索
	 * @param namespace 名前空間 (ログ用)
	 * @param bdbEnv BDB環境
	 * @param txn トランザクション
	 * @param db データベース
	 * @param binding バインディングクラス
	 * @param bdbCondition 検索条件リスト
	 *        (開始キー、終了キー、デコード済みカーソル)
	 * @param limit 結果データ最大数
	 * @param requestInfo リクエスト情報
	 */
	FetchInfo<T> getByQuery(String namespace, BDBTransaction txn,
			BDBDatabase db, EntryBinding<T> binding, BDBCondition bdbCondition,
			int limit, RequestInfo requestInfo) {
		return getByQuery(namespace, txn, db, binding, bdbCondition, limit, null,
				requestInfo);
	}

	/**
	 * クエリ検索
	 * @param namespace 名前空間 (ログ用)
	 * @param bdbEnv BDB環境
	 * @param txn トランザクション
	 * @param db データベース
	 * @param binding バインディングクラス
	 * @param bdbCondition 検索条件リスト
	 *        (開始キー、終了キー、デコード済みカーソル)
	 * @param limit 結果データ最大数
	 * @param currentCache 同じ条件で読み込み済みキャッシュ(全文検索インデックス検索で使用)
	 *                     キャッシュを使用する場合はnullでなくオブジェクトを引き渡すこと。
	 *                     キー:値、値:検索キー
	 * @param requestInfo リクエスト情報
	 */
	FetchInfo<T> getByQuery(String namespace, BDBTransaction txn,
			BDBDatabase db, EntryBinding<T> binding, BDBCondition bdbCondition,
			int limit, Map<String, String> currentCache, RequestInfo requestInfo) {
		String newPointerStr = null;
		int fetchCnt = 0;
		int fetchLimit = BDBEnvUtil.getFetchLimit();

		Map<String, T> results = new LinkedHashMap<>();
		boolean isCheckCache = false;
		Map<String, String> newCache = null;
		if (currentCache != null) {
			newCache = new LinkedHashMap<>();
			isCheckCache = true;
		}

		if (BDBUtil.isEnableAccessLog()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[getByQuery] ");
			sb.append(db.getDatabaseName());
			sb.append(" ");
			sb.append(bdbCondition);
			logger.debug(sb.toString());
		}

		long startTime = 0;
		BDBCursor cursor = null;
		try {
			OperationStatus retVal = null;
			String foundKeyStr = null;
			//String foundDataStr = null;
			Object foundDataVal = null;

			// open
			DatabaseEntry foundKey = new DatabaseEntry();
			DatabaseEntry foundData = new DatabaseEntry();
			String keyStr = bdbCondition.startKeyStr;
			String pointerStr = bdbCondition.cursorStr;

			if (BDBUtil.isEnableAccessLog()) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
						BDBUtil.getStartLogNs(namespace, LOG_METHOD, db, keyStr));
				startTime = new Date().getTime();
			}

			cursor = db.openCursor(txn, BDBConst.CURSOR_CONFIG);

			// search
			if (!StringUtils.isBlank(keyStr) || !StringUtils.isBlank(pointerStr)) {
				// 条件指定検索
				// 親キーの前方一致検索
				DatabaseEntry searchKey = null;
				if (!StringUtils.isBlank(pointerStr)) {
					searchKey = BDBUtil.getDbKey(pointerStr);
				} else {
					searchKey = BDBUtil.getDbKey(keyStr);
				}
				foundKey.setData(searchKey.getData());
				retVal = cursor.getSearchKeyRange(foundKey, foundData,
						BDBConst.LOCK_MODE);
			} else {
				// 先頭から検索
				retVal = cursor.getFirst(foundKey, foundData,
						BDBConst.LOCK_MODE);
			}
			if (BDBUtil.isEnableAccessLog()) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
						"[getByQuery] getSearchKeyRange OperationStatus: " + retVal);
			}

			if (retVal == OperationStatus.SUCCESS) {
				// 範囲指定で指定された値を含まない場合(GREATER_THAN)、次のデータからを対象にする。
				if (bdbCondition.excludeStartKey) {
					int keyLen = 0;
					if (keyStr != null) {
						keyLen = keyStr.length();
					}
					while (retVal == OperationStatus.SUCCESS) {
						String searchKeyStr = BDBUtil.getDbString(foundKey);
						if (!(searchKeyStr.startsWith(keyStr) &&
								searchKeyStr.length() > keyLen &&
								BDBConst.INDEX_SELF.equals(
										searchKeyStr.substring(
										keyLen, keyLen + 1)))) {
							break;
						}
						retVal = cursor.getNext(foundKey, foundData, BDBConst.LOCK_MODE);
						fetchCnt++;
						if (fetchCnt > fetchLimit) {
							// フェッチ制限超え
							throw new FetchExceededException(
									FetchExceededException.MESSAGE);
						}
					}
				}

				// キー範囲チェック
				foundKeyStr = BDBUtil.getDbString(foundKey);
				//if (!isIntheRange(foundKeyStr, bdbCondition.endKeyStr)) {
				if (!isIntheRange(foundKeyStr, bdbCondition.endKeyStr,
						bdbCondition.excludeEndKey)) {
					retVal = OperationStatus.NOTFOUND;
					foundKeyStr = null;
					//foundDataStr = null;
				} else {
					T tmpFoundDataVal = binding.entryToObject(foundData);
					foundDataVal = tmpFoundDataVal;
					//foundDataStr = getDataStr(tmpFoundDataVal);
				}
			}

			// 全ての条件のfoundDataが等しければ採用
			// 異なる場合、一番小さい条件のcursorをnextする。

			// next
			while (retVal == OperationStatus.SUCCESS) {

				newPointerStr = foundKeyStr;

				// フェッチ数チェック
				fetchCnt++;
				if (fetchCnt > fetchLimit) {
					// フェッチ制限超え
					throw new FetchExceededException(FetchExceededException.MESSAGE);
				}

				if (BDBUtil.isEnableAccessLog()) {
					StringBuilder sb = new StringBuilder();
					sb.append(LogUtil.getRequestInfoStr(requestInfo));
					sb.append("[getByQuery] foundKeyStr: ");
					sb.append(foundKeyStr);
					sb.append(" , foundDataVal: ");
					sb.append(foundDataVal);
					logger.debug(sb.toString());
				}

				// 戻り値データ格納
				boolean hasCache = false;
				String val = null;
				if (isCheckCache) {
					val = (String)foundDataVal;
					hasCache = hasCache(val, foundKeyStr, currentCache, newCache);
				}
				if (!hasCache) {
					// 件数チェック
					if (results.size() >= limit) {
						// 件数一杯
						break;
					}

					results.put(foundKeyStr, (T)foundDataVal);
					if (isCheckCache) {
						newCache.put(val, foundKeyStr);
					}
				}

				// カーソルをnext
				retVal = cursor.getNext(foundKey, foundData,
						BDBConst.LOCK_MODE);
				newPointerStr = null;

				if (retVal == OperationStatus.SUCCESS) {
					// キー範囲チェック
					foundKeyStr = BDBUtil.getDbString(foundKey);
					//if (!isIntheRange(foundKeyStr, bdbCondition.endKeyStr)) {
					if (!isIntheRange(foundKeyStr, bdbCondition.endKeyStr,
							bdbCondition.excludeEndKey)) {
						retVal = OperationStatus.NOTFOUND;
						foundKeyStr = null;
						//foundDataStr = null;
					} else {
						T tmpFoundDataVal = binding.entryToObject(foundData);
						foundDataVal = tmpFoundDataVal;
						//foundDataStr = getDataStr(tmpFoundDataVal);
					}
				}
			}

			String newEncodingPointerStr = PointerUtil.encode(newPointerStr);
			if (BDBUtil.isEnableAccessLog()) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
						// TODO keyStrは仮
						BDBUtil.getEndLogNs(namespace, LOG_METHOD, db, "", startTime));
			}
			return new FetchInfo<T>(results, newEncodingPointerStr);

		} catch (FetchExceededException e) {
			// フェッチ数超過の場合
			String newEncodingPointerStr = PointerUtil.encode(newPointerStr);
			FetchInfo<T> fetchInfo = new FetchInfo<T>(results, newEncodingPointerStr);
			fetchInfo.setFetchExceeded(true);
			if (BDBUtil.isEnableAccessLog()) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
						// TODO keyStrは仮
						BDBUtil.getEndLogNs(namespace, LOG_METHOD, db, "", startTime));
			}
			return fetchInfo;

		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
	}

	/**
	 * 検索されたキーが取得対象かどうか.
	 * @param foundKeyStr 検索されたキー
	 * @param matchingEnd 検索範囲の終端
	 * @param isLessThan 検索範囲の終端を条件に含むかどうか (Index用)
	 * @return 検索されたキーが取得対象の場合true
	 */
	/*
	private boolean isIntheRange(String foundKeyStr, String matchingEnd) {
		if (StringUtils.isBlank(matchingEnd)) {
			// 検索範囲の終端指定がない場合は全てが対象。
			return true;
		}
		if (matchingEnd.compareTo(foundKeyStr) > 0) {
			return true;
		}
		return false;
	}
	*/

	/**
	 * 検索されたキーが取得対象かどうか.
	 * @param foundKeyStr 検索されたキー
	 * @param matchingEnd 検索範囲の終端
	 * @param excludeEndKey 検索範囲の終端を条件に含まないかどうか
	 * @return 検索されたキーが取得対象の場合true
	 */
	private boolean isIntheRange(String foundKeyStr, String matchingEnd, boolean excludeEndKey) {
		if (StringUtils.isBlank(matchingEnd)) {
			// 検索範囲の終端指定がない場合は全てが対象。
			return true;
		}
		if (matchingEnd.compareTo(foundKeyStr) > 0) {
			if (excludeEndKey) {
				// LESS_THAN は終了条件と同じであれば対象外
				String tmpMatchingEnd = matchingEnd.substring(0, matchingEnd.length() - 1);
				// INDEX_SELF文字の2番目までを抽出する。
				int idx1 = foundKeyStr.indexOf(BDBConst.INDEX_SELF);
				String tmpFoundKeyStr = foundKeyStr.substring(0,
						foundKeyStr.indexOf(BDBConst.INDEX_SELF, idx1 + 1));
				if (tmpFoundKeyStr.startsWith(tmpMatchingEnd)) {
					return false;
				}
			}
			return true;
		}
		return false;
	}


	/**
	 * 検索した値が読み込み済みキャッシュに存在するかどうかチェック
	 * @param val 値
	 * @param foundKeyStr 全文検索インデックス値
	 * @param currentCache セッション登録キャッシュ (キー:BDBの値、値:インデックスキー)
	 * @param newCache このリクエストで追加予定キャッシュ
	 * @return キャッシュに存在する場合true、存在しない場合false
	 */
	private boolean hasCache(String val, String foundKeyStr, Map<String, String> currentCache,
			Map<String, String> newCache) {
		// セッションキャッシュに存在するかどうか
		if (currentCache != null && currentCache.containsKey(val)) {
			String cacheIndex = currentCache.get(val);
			int compare = foundKeyStr.compareTo(cacheIndex);
			if (compare <= 0) {
				return false;	// 自分自身はデータ返却対象。< は論理上なし。
			} else if (compare > 0) {
				return true;	// キャッシュに存在済み
			}
		}
		// 今回読み込んだ追加キャッシュ分に存在するかどうか
		if (newCache != null && newCache.containsKey(val)) {
			String cacheIndex = newCache.get(val);
			int compare = foundKeyStr.compareTo(cacheIndex);
			if (compare <= 0) {
				return false;	// 論理上なし。
			} else if (compare > 0) {
				return true;	// キャッシュに存在済み
			}
		}
		return false;
	}

}
