package jp.reflexworks.taggingservice.blogic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.Category;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.entry.Link;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.UpdatedInfo;
import jp.reflexworks.taggingservice.plugin.BigQueryManager;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.taskqueue.TaskQueueUtil;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.reflexworks.taggingservice.util.Constants.OperationType;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * BigQueryデータを登録・検索するビジネスロジック
 */
public class BigQueryBlogic {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * BigQueryにEntryを登録する.
	 * @param feed Feed
	 * @param tableNames エンティティの第一階層と異なる名前をテーブル名にする場合に指定.
	 *                   キー:エンティティの第一階層名、値:BigQuery登録テーブル名
	 * @param async 非同期の場合true、同期の場合false
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return Future 同期の場合はnull
	 */
	public Future postBq(FeedBase feed, Map<String, String> tableNames, boolean async,
			ReflexAuthentication auth,  RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		// 入力チェック
		CheckUtil.checkFeed(feed, false, true);
		for (EntryBase entry : feed.entry) {
			CheckUtil.checkNotNull(entry, "Entry");
			String uri = entry.getMyUri();
			CheckUtil.checkUri(uri);
			CheckUtil.checkCommonUri(uri, serviceName);
		}

		BigQueryManager manager = TaggingEnvUtil.getBigQueryManager();
		if (manager == null) {
			throw new InvalidServiceSettingException("BigQuery manager is nothing.");
		}
		return manager.postBq(feed, tableNames, async, false, auth, requestInfo, connectionInfo);
	}

	/**
	 * BigQueryにデータを登録する.
	 * ログデータ用。Feedでなく、テーブル名と、項目名と値のMapを詰めたリストを指定する。
	 * @param tableName テーブル名
	 * @param list 項目名と値のリスト
	 * @param async 非同期の場合true、同期の場合false
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return Future 同期の場合はnull
	 */
	public Future postBq(String tableName, List<Map<String, Object>> list,
			boolean async, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// 入力チェック
		CheckUtil.checkNotNull(list, "BigQuery data");
		int i = 0;
		for (Map<String, Object> mapEntry : list) {
			i++;
			CheckUtil.checkNotNull(mapEntry, "BigQuery data (" + i + ")");
		}

		BigQueryManager manager = TaggingEnvUtil.getBigQueryManager();
		if (manager == null) {
			throw new InvalidServiceSettingException("BigQuery manager is nothing.");
		}
		return manager.postBq(tableName, list, async, auth, requestInfo, connectionInfo);
	}

	/**
	 * Key-Value形式のデータストア格納Entryを削除する.
	 * @param uris キーリスト。末尾に*指定を行うとキーの前方一致データを全て削除する。
	 * @param tableNames エンティティの第一階層と異なる名前をテーブル名にする場合に指定.
	 *                   キー:エンティティの第一階層名、値:BigQuery登録テーブル名
	 * @param async 非同期の場合true、同期の場合false
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return Future 同期の場合はnull
	 */
	public Future deleteBq(String[] uris, Map<String, String> tableNames, boolean async,
			ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		// 入力チェック
		CheckUtil.checkNotNull(uris, "Key");
		for (String uri : uris) {
			String tmpUri = uri;
			if (uri != null && uri.endsWith(RequestParam.WILDCARD)) {
				tmpUri = uri.substring(0, uri.length() - 1);
			}
			CheckUtil.checkUri(tmpUri);
			CheckUtil.checkCommonUri(uri, serviceName);
		}

		BigQueryManager manager = TaggingEnvUtil.getBigQueryManager();
		if (manager == null) {
			throw new InvalidServiceSettingException("BigQuery manager is nothing.");
		}
		return manager.deleteBq(uris, tableNames, async, auth, requestInfo, connectionInfo);
	}

	/**
	 * BigQueryに対し指定された検索SQLを実行し結果を取得する.
	 * @param sql SQL
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 検索結果
	 */
	public List<Map<String, Object>> queryBq(String sql, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// 入力チェック
		CheckUtil.checkNotNull(sql, "SQL");

		BigQueryManager manager = TaggingEnvUtil.getBigQueryManager();
		if (manager == null) {
			throw new InvalidServiceSettingException("BigQuery manager is nothing.");
		}
		return manager.queryBq(sql, auth, requestInfo, connectionInfo);
	}

	/**
	 * BDBとBigQueryにEntryを登録する.
	 * @param feed Feed
	 * @param parentUri 自動採番用親キー
	 * @param tableNames エンティティの第一階層と異なる名前をテーブル名にする場合に指定.
	 *                   キー:エンティティの第一階層名、値:BigQuery登録テーブル名
	 * @param async BDBへの登録について非同期の場合true、同期の場合false
	 * @param reflexContext ReflexContext
	 * @return 同期の場合BDBに登録したFeed、非同期の場合はnull。
	 */
	public FeedBase postBdbq(FeedBase feed, String parentUri, Map<String, String> tableNames, 
			boolean async, ReflexContext reflexContext)
	throws IOException, TaggingException {
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();

		BigQueryManager bqManager = TaggingEnvUtil.getBigQueryManager();
		if (bqManager == null) {
			throw new InvalidServiceSettingException("BigQuery manager is nothing.");
		}
		
		// BigQuery登録のための入力・設定チェック
		bqManager.checkBq(feed, auth, requestInfo, connectionInfo);
		
		if (async) {
			// 非同期でBDBに登録し、その後BigQueryに登録
			BigQueryAsyncBdbqCallable callable = new BigQueryAsyncBdbqCallable(OperationType.INSERT, 
					feed, parentUri, tableNames);
			TaskQueueUtil.addTask(callable, 0, auth, requestInfo, connectionInfo);
			return null;
		} else {
			// 同期でBDBに登録し、その後非同期でBigQueryに登録
			return postBdbqProc(feed, parentUri, tableNames, reflexContext);
		}
	}

	/**
	 * BDB+BigQuery登録処理
	 * @param feed Feed
	 * @param parentUri 自動採番用親キー
	 * @param tableNames エンティティの第一階層と異なる名前をテーブル名にする場合に指定.
	 *                   キー:エンティティの第一階層名、値:BigQuery登録テーブル名
	 * @param reflexContext ReflexContext
	 * @return 登録結果Feed
	 */
	FeedBase postBdbqProc(FeedBase feed, String parentUri, Map<String, String> tableNames, 
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();

		// まずBDBに登録
		FeedBase retFeed = reflexContext.post(feed, parentUri);
		
		// 次にBigQueryに非同期で登録
		BigQueryBdbqCallable callable = new BigQueryBdbqCallable(retFeed,
				tableNames);
		TaskQueueUtil.addTask(callable, 0, auth, requestInfo, connectionInfo);
		
		return retFeed;
	}

	/**
	 * BDBのEntryを更新し、BigQueryにEntryを登録する.
	 * @param feed Feed
	 * @param parentUri 自動採番用親キー
	 * @param tableNames エンティティの第一階層と異なる名前をテーブル名にする場合に指定.
	 *                   キー:エンティティの第一階層名、値:BigQuery登録テーブル名
	 * @param async BDBへの更新について非同期の場合true、同期の場合false
	 * @param reflexContext ReflexContext
	 * @return 同期の場合BDBに更新したFeed、非同期の場合はnull。
	 */
	public FeedBase putBdbq(FeedBase feed, String parentUri, Map<String, String> tableNames, 
			boolean async, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();

		BigQueryManager bqManager = TaggingEnvUtil.getBigQueryManager();
		if (bqManager == null) {
			throw new InvalidServiceSettingException("BigQuery manager is nothing.");
		}
		
		List<EntryBase> checkEntries = new ArrayList<>();
		if (TaggingEntryUtil.isExistData(feed)) {
			for (EntryBase entry : feed.entry) {
				if (entry.id != null && 
						RequestParam.PARAM_DELETE.equals(TaggingEntryUtil.getParamById(entry.id))) {
					// 削除
				} else {
					// 更新
					checkEntries.add(entry);
				}
			}
		}
		
		FeedBase checkFeed = null;
		if (!checkEntries.isEmpty()) {
			checkFeed = TaggingEntryUtil.createFeed(serviceName);
			checkFeed.entry = checkEntries;
		}
		// BigQuery登録のための入力・設定チェック
		bqManager.checkBq(checkFeed, auth, requestInfo, connectionInfo);

		if (async) {
			// 非同期でBDBを更新し、その後BigQueryに登録
			BigQueryAsyncBdbqCallable callable = new BigQueryAsyncBdbqCallable(OperationType.UPDATE, 
					feed, parentUri, tableNames);
			TaskQueueUtil.addTask(callable, 0, auth, requestInfo, connectionInfo);
			return null;
		} else {
			// 同期でBDBを更新し、その後非同期でBigQueryに登録
			return putBdbqProc(feed, parentUri, tableNames, reflexContext);
		}
	}
	
	/**
	 * BDB+BigQuery更新処理
	 * @param feed Feed
	 * @param parentUri 自動採番用親キー
	 * @param tableNames エンティティの第一階層と異なる名前をテーブル名にする場合に指定.
	 *                   キー:エンティティの第一階層名、値:BigQuery登録テーブル名
	 * @param reflexContext ReflexContext
	 * @return 登録結果Feed
	 */
	FeedBase putBdbqProc(FeedBase feed, String parentUri, Map<String, String> tableNames, 
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		ReflexAuthentication auth = reflexContext.getAuth();
		String serviceName = auth.getServiceName();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		
		// 「登録・更新」か「削除」かの判定を先に行っておく。
		// reflexContext.put を実行すると、idの"_delete"は削除されてしまうため。
		List<Boolean> isPostList = new ArrayList<>();
		if (TaggingEntryUtil.isExistData(feed)) {
			for (EntryBase entry : feed.entry) {
				boolean isPost = true;
				if (entry.id != null && 
						RequestParam.PARAM_DELETE.equals(TaggingEntryUtil.getParamById(entry.id))) {
					// 削除
					isPost = false;
				}
				isPostList.add(isPost);
			}
		}

		// まずBDBのエントリーを更新
		FeedBase retFeed = reflexContext.put(feed, parentUri);
		
		// 処理エントリーを「登録・更新」と「削除」に分ける
		List<EntryBase> updateEntries = new ArrayList<>();
		List<String> deleteKeys = new ArrayList<>();
		int idx = -1;
		for (boolean isPost : isPostList) {
			idx++;
			EntryBase retEntry = retFeed.entry.get(idx);
			if (isPost) {
				// 登録・更新
				updateEntries.add(retEntry);
			} else {
				// 削除
				deleteKeys.add(TaggingEntryUtil.getUriById(retEntry.id));
			}
		}
		
		// 次にBigQueryに非同期で登録
		if (!updateEntries.isEmpty()) {
			FeedBase postBqFeed = TaggingEntryUtil.createFeed(serviceName);
			postBqFeed.entry = updateEntries;
			BigQueryBdbqCallable callable = new BigQueryBdbqCallable(postBqFeed,
					tableNames);
			TaskQueueUtil.addTask(callable, 0, auth, requestInfo, connectionInfo);
		}
		if (!deleteKeys.isEmpty()) {
			String[] uris = deleteKeys.toArray(new String[0]);
			BigQueryBdbqCallable callable = new BigQueryBdbqCallable(uris,
					tableNames);
			TaskQueueUtil.addTask(callable, 0, auth, requestInfo, connectionInfo);
		}
		
		return retFeed;
	}

	/**
	 * BDBのEntryを削除し、BigQueryに削除データを登録する.
	 * @param uris キーリスト。ワイルドカード不可。
	 * @param tableNames エンティティの第一階層と異なる名前をテーブル名にする場合に指定.
	 *                   キー:エンティティの第一階層名、値:BigQuery登録テーブル名
	 * @param async BDBへの削除について非同期の場合true、同期の場合false
	 * @param reflexContext ReflexContext
	 */
	public FeedBase deleteBdbq(String[] uris, Map<String, String> tableNames, 
			boolean async, ReflexContext reflexContext)
	throws IOException, TaggingException {
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();

		BigQueryManager bqManager = TaggingEnvUtil.getBigQueryManager();
		if (bqManager == null) {
			throw new InvalidServiceSettingException("BigQuery manager is nothing.");
		}
		
		// BigQuery登録のための入力・設定チェック
		bqManager.checkBq(null, auth, requestInfo, connectionInfo);
		
		if (async) {
			// 非同期でBDBから削除し、その後BigQueryに削除データ登録
			BigQueryAsyncBdbqCallable callable = new BigQueryAsyncBdbqCallable(OperationType.DELETE, 
					uris, tableNames);
			TaskQueueUtil.addTask(callable, 0, auth, requestInfo, connectionInfo);
			return null;
		} else {
			// 同期でBDBから削除し、その後BigQueryに削除データ登録
			return deleteBdbqProc(uris, tableNames, reflexContext);
		}
	}

	/**
	 * BDB+BigQuery登録処理
	 * @param feed Feed
	 * @param parentUri 自動採番用親キー
	 * @param tableNames エンティティの第一階層と異なる名前をテーブル名にする場合に指定.
	 *                   キー:エンティティの第一階層名、値:BigQuery登録テーブル名
	 * @param reflexContext ReflexContext
	 * @return 登録結果Feed
	 */
	FeedBase deleteBdbqProc(String[] uris, Map<String, String> tableNames, 
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();

		// まずBDBのエントリーを削除
		List<String> uriList = null;
		if (uris != null && uris.length > 0) {
			uriList = Arrays.asList(uris);
		}
		FeedBase retFeed = reflexContext.delete(uriList);
		
		// 次にBigQueryに非同期で削除データ登録
		BigQueryBdbqCallable callable = new BigQueryBdbqCallable(uris,
				tableNames);
		TaskQueueUtil.addTask(callable, 0, auth, requestInfo, connectionInfo);
		
		return retFeed;
	}

	/**
	 * BDBQリトライチェック
	 * @param systemContext SystemContext
	 */
	public void checkRetryBdbq(SystemContext systemContext) 
	throws IOException, TaggingException {
		// /_bdbq 配下にデータが存在する場合、BigQueryへリトライ登録を行う。
		String cursorStr = null;
		do {
			String requestUri = TaggingEntryUtil.addCursorToUri(BigQueryBdbqConst.URI_BDBQ, cursorStr);
			FeedBase feed = systemContext.getFeed(requestUri);
			cursorStr = TaggingEntryUtil.getCursorFromFeed(feed);
			
			if (TaggingEntryUtil.isExistData(feed)) {
				for (EntryBase entry : feed.entry) {
					retryBdbq(entry, systemContext);
				}
			}
		} while (!StringUtils.isBlank(cursorStr));
	}
	
	// BigQueryへの登録または削除処理でエラーが発生した場合、
	// キー(id)とtableNamesをBDBに登録しておく。
	// BDBQエラーエントリー
	//   * キー: /_bdbq/{自動採番}
	//   * link 
	//       _$hrefにキー
	//       _$typeに登録/削除区分
	//   * categoryにtableNamesリスト
	//       _$scheme: スキーマ第一階層名
	//       _$label: テーブル名

	/**
	 * BDBデータをBigQueryへ登録するリトライ処理
	 * @param bdbqEntry BDBQリトライ情報
	 * @param systemContext SystemContext
	 */
	private void retryBdbq(EntryBase bdbqEntry, SystemContext systemContext)
	throws IOException, TaggingException {
		String serviceName = systemContext.getServiceName();
		ReflexAuthentication auth = systemContext.getAuth();
		RequestInfo requestInfo = systemContext.getRequestInfo();
		ConnectionInfo connectionInfo = systemContext.getConnectionInfo();
		
		BigQueryManager manager = TaggingEnvUtil.getBigQueryManager();
		if (manager == null) {
			throw new InvalidServiceSettingException("BigQuery manager is nothing.");
		}

		List<EntryBase> postEntryList = new ArrayList<>();
		List<String> deleteUriList = new ArrayList<>();
		Map<String, String> tableNames = convertTableNames(bdbqEntry);
		// BDBデータを取得
		for (Link link : bdbqEntry.link) {
			if (!Link.REL_VIA.equals(link._$rel)) {
				continue;
			}
			String uri = link._$href;
			String type = link._$type;
			EntryBase entry = systemContext.getEntry(uri);
			if (type.equals(BigQueryBdbqConst.TYPE_DELETE)) {
				if (entry == null) {
					// BigQueryへ削除データ登録
					deleteUriList.add(uri);
				} else {
					// 再登録済みのため何もしない。
					if (logger.isDebugEnabled()) {
						logger.debug("[retryBdbq] delete entry is updated. uri = " + uri);
					}
				}
			} else {	// INSERT
				if (entry != null) {
					// BigQueryへ登録
					postEntryList.add(entry);
				} else {
					// 削除済みのため何もしない。
					if (logger.isDebugEnabled()) {
						logger.debug("[retryBdbq] post entry is deleted. uri = " + uri);
					}
				}
			}
		}
		
		if (!postEntryList.isEmpty()) {
			FeedBase feed = TaggingEntryUtil.createFeed(serviceName);
			feed.entry = postEntryList;
			manager.postBq(feed, tableNames, false, false, 
					auth, requestInfo, connectionInfo);
		}
		if (!deleteUriList.isEmpty()) {
			String[] uris = deleteUriList.toArray(new String[0]);
			manager.deleteBq(uris, tableNames, false, auth, requestInfo, connectionInfo);
		}
		
		// BDBQリトライ情報の削除
		systemContext.delete(bdbqEntry.getMyUri());
	}
	
	/**
	 * Entryからテーブル名変換リストを取得し、Map形式に変換する.
	 * @param bdbqEntry Entry
	 * @return テーブル名変換Map
	 */
	private Map<String, String> convertTableNames(EntryBase bdbqEntry) {
		//   * categoryにtableNamesリスト
		//       _$scheme: スキーマ第一階層名
		//       _$label: テーブル名
		
		List<Category> categories = bdbqEntry.category;
		if (categories == null || categories.isEmpty()) {
			return null;
		}
		Map<String, String> tableNames = new HashMap<>();
		for (Category category : categories) {
			tableNames.put(category._$scheme, category._$label);
		}
		return tableNames;
	}

	/**
	 * BDBのEntryを更新し、BigQueryにEntryを登録する.
	 * Feedの一貫性は保証しない。
	 * @param feed Feed
	 * @param parentUri 自動採番用親キー
	 * @param tableNames エンティティの第一階層と異なる名前をテーブル名にする場合に指定.
	 *                   キー:エンティティの第一階層名、値:BigQuery登録テーブル名
	 * @param reflexContext ReflexContext
	 * @param async BDBへの登録について非同期の場合true、同期の場合false
	 * @return BDBへの登録Future
	 */
	public List<Future<List<UpdatedInfo>>> bulkPutBdbq(FeedBase feed, String parentUri, 
			Map<String, String> tableNames, ReflexContext reflexContext, boolean async)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();

		BigQueryManager bqManager = TaggingEnvUtil.getBigQueryManager();
		if (bqManager == null) {
			throw new InvalidServiceSettingException("BigQuery manager is nothing.");
		}
		
		// 「登録・更新」か「削除」かの判定を先に行っておく。
		// reflexContext.put を実行すると、idの"_delete"は削除されてしまうため。
		List<Boolean> isPostList = new ArrayList<>();
		List<EntryBase> checkEntries = new ArrayList<>();
		if (TaggingEntryUtil.isExistData(feed)) {
			for (EntryBase entry : feed.entry) {
				boolean isPost = true;
				if (entry.id != null && 
						RequestParam.PARAM_DELETE.equals(TaggingEntryUtil.getParamById(entry.id))) {
					// 削除
					isPost = false;
				} else {
					// 更新
					checkEntries.add(entry);
				}
				isPostList.add(isPost);
			}
		}
		
		FeedBase checkFeed = null;
		if (!checkEntries.isEmpty()) {
			checkFeed = TaggingEntryUtil.createFeed(serviceName);
			checkFeed.entry = checkEntries;
		}
		// BigQuery登録のための入力・設定チェック
		bqManager.checkBq(checkFeed, auth, requestInfo, connectionInfo);

		// まずBDBのエントリーを更新
		List<Future<List<UpdatedInfo>>> futures = reflexContext.bulkPut(feed, parentUri, async);
		
		// BigQueryに登録または削除
		if (async) {
			// BDBの更新処理結果を非同期で確認し、BigQuery更新処理
			BigQueryBulkBdbqCallable callable = new BigQueryBulkBdbqCallable(futures, tableNames);
			TaskQueueUtil.addTask(callable, 0, auth, requestInfo, connectionInfo);

		} else {
			// BDBの更新処理結果を同期で確認し、BigQuery更新処理
			bulkPutBqProc(futures, tableNames, reflexContext);
		}
		
		return futures;
	}
	
	/**
	 * BDBの更新結果を確認し、BigQuery更新処理を行う
	 * @param futures bulkPutでのBDB更新結果
	 * @param tableNames エンティティの第一階層と異なる名前をテーブル名にする場合に指定.
	 *                   キー:エンティティの第一階層名、値:BigQuery登録テーブル名
	 * @param reflexContext ReflexContext
	 */
	void bulkPutBqProc(List<Future<List<UpdatedInfo>>> futures, Map<String, String> tableNames, 
			ReflexContext reflexContext) 
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();

		// 処理エントリーを「登録・更新」と「削除」に分ける
		List<EntryBase> updateEntries = new ArrayList<>();
		List<String> deleteKeys = new ArrayList<>();

		IOException ie = null;
		TaggingException te = null;
		RuntimeException re = null;
		Error er = null;
		
		for (Future<List<UpdatedInfo>> future : futures) {
			try {
				List<UpdatedInfo> updatedInfos = future.get();
				for (UpdatedInfo updatedInfo : updatedInfos) {
					if (updatedInfo.getFlg().equals(OperationType.DELETE)) {
						// 削除
						deleteKeys.add(TaggingEntryUtil.getUriById(updatedInfo.getPrevEntry().id));
					} else {
						// 登録・更新
						updateEntries.add(updatedInfo.getUpdEntry());
					}
				}
				
			} catch (ExecutionException | InterruptedException | RuntimeException | Error e) {
				if (logger.isInfoEnabled()) {
					StringBuilder sb = new StringBuilder();
					sb.append(LogUtil.getRequestInfoStr(requestInfo));
					sb.append("[bulkPutBq] Error occured. ");
					sb.append(e.getClass().getSimpleName());
					sb.append(" : ");
					sb.append(e.getMessage());
					logger.info(sb.toString());
				}
				if (e instanceof ExecutionException) {
					Throwable cause = e.getCause();
					if (cause instanceof IOException) {
						if (ie == null) {
							ie = (IOException)cause;
						}
					} else if (cause instanceof TaggingException) {
						if (te == null) {
							te = (TaggingException)cause;
						}
					} else {
						if (ie == null) {
							ie = new IOException(cause);
						}
					}
				} else if (e instanceof InterruptedException) {
					if (ie == null) {
						ie = new IOException(e);
					}
				} else if (e instanceof RuntimeException) {
					if (re == null) {
						re = (RuntimeException)e;
					}
				} else {
					if (er == null) {
						er = (Error)e;
					}
				}
			}
		}
		
		// 次にBigQueryに非同期で登録
		if (!updateEntries.isEmpty()) {
			FeedBase postBqFeed = TaggingEntryUtil.createFeed(serviceName);
			postBqFeed.entry = updateEntries;
			BigQueryBdbqCallable callable = new BigQueryBdbqCallable(postBqFeed,
					tableNames);
			TaskQueueUtil.addTask(callable, 0, auth, requestInfo, connectionInfo);
		}
		if (!deleteKeys.isEmpty()) {
			String[] uris = deleteKeys.toArray(new String[0]);
			BigQueryBdbqCallable callable = new BigQueryBdbqCallable(uris,
					tableNames);
			TaskQueueUtil.addTask(callable, 0, auth, requestInfo, connectionInfo);
		}
		
		// エラーの場合は1件のみスローする
		if (er != null) {
			throw er;
		} else if (re != null) {
			throw re;
		} else if (ie != null) {
			throw ie;
		} else if (te != null) {
			throw te;
		}
	}

}
