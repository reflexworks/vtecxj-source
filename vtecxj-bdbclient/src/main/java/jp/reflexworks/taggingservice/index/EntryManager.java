package jp.reflexworks.taggingservice.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.bdbclient.BDBClientRetrieveManager;
import jp.reflexworks.taggingservice.bdbclient.BDBClientUpdateEntriesCallable;
import jp.reflexworks.taggingservice.bdbclient.BDBClientUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBIndexType;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;

/**
 * Entry管理クラス.
 */
public class EntryManager extends IndexCommonManager {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * Entry検索.
	 * Entryサーバへ一括で取得する
	 * @param ids IDリスト
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 検索結果
	 */
	public List<EntryBase> getEntiesByIds(List<String> ids,
			ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		if (ids == null || ids.isEmpty()) {
			return null;
		}

		BDBClientRetrieveManager retrieveManager = new BDBClientRetrieveManager();
		return retrieveManager.requestGetEntriesByIds(ids, auth, requestInfo, connectionInfo);
	}

	/**
	 * Entry登録更新.
	 * @param entries Entryリスト
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void putEntries(List<EntryBase> entries, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		String entryPutMethod = Constants.PUT;
		String entryMultipleUri = BDBClientUtil.getEntryMultipleUri();

		// EntryのID URIによりEntryサーバを振り分ける。
		Map<String, List<EntryBase>> urlEntriesMap = new HashMap<>();
		for (EntryBase entry : entries) {
			String entryServerUrl = BDBClientUtil.getEntryServerUrl(
					TaggingEntryUtil.getUriById(entry.id), serviceName, requestInfo, connectionInfo);
			List<EntryBase> tmpEntries = urlEntriesMap.get(entryServerUrl);
			if (tmpEntries == null) {
				tmpEntries = new ArrayList<>();
				urlEntriesMap.put(entryServerUrl, tmpEntries);
			}
			tmpEntries.add(entry);
		}

		// Entryサーバごとに並列処理
		int limit = BDBClientUtil.getEntryserverPutLimit();
		List<Future<Boolean>> futures = new ArrayList<>();
		for (Map.Entry<String, List<EntryBase>> mapEntry : urlEntriesMap.entrySet()) {
			String entryServerUrl = mapEntry.getKey();
			List<EntryBase> tmpEntries = mapEntry.getValue();
			int size = tmpEntries.size();
			int idx = 0;
			while (idx < size) {
				// Entryを一定数ごとに区切ってリクエストする。
				// リクエストヘッダのサイズ制限に引っかからないようにするため。(Header is too large)
				List<EntryBase> reqEntries = null;
				int toIdx = 0;
				if (size - idx > limit) {
					toIdx = idx + limit;
				} else {
					toIdx = size;
				}
				reqEntries = tmpEntries.subList(idx, toIdx);
				idx = toIdx;

				BDBClientUpdateEntriesCallable callable = new BDBClientUpdateEntriesCallable(
						entryServerUrl, entryMultipleUri, entryPutMethod, reqEntries, null);
				Future<Boolean> future = callable.addTask(auth, requestInfo, connectionInfo);
				futures.add(future);
			}
		}

		// 処理の終了を待つ
		for (Future<Boolean> future : futures) {
			try {
				future.get();
			} catch (ExecutionException e) {
				Throwable cause = e.getCause();
				if (logger.isDebugEnabled()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
							"[putEntries] ExecutionException: " +
							cause.getMessage());
				}
				if (cause instanceof IOException) {
					throw (IOException)cause;
				} else if (cause instanceof TaggingException) {
					throw (TaggingException)cause;
				} else if (cause instanceof IllegalParameterException) {
					throw (IllegalParameterException)cause;
				} else {
					throw new IOException(cause);
				}
			} catch (InterruptedException e) {
				if (logger.isDebugEnabled()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
							"[putEntries] InterruptedException: " +
							e.getMessage());
				}
				throw new IOException(e);
			}
		}
	}

	/**
	 * EntryサーバのBDB環境クローズ
	 * @param serviceName サービス名
	 * @param reflexContext ReflexContext
	 * @return リクエストを送った場合true、対象サーバが無い場合false
	 */
	public boolean closeEntry(String serviceName, ReflexContext reflexContext)
	throws IOException, TaggingException {
		return closeIndex(BDBIndexType.MANIFEST, reflexContext);
	}

}
