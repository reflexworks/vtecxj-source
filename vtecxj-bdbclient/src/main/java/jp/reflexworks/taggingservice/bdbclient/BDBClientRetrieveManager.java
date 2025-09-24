package jp.reflexworks.taggingservice.bdbclient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.api.Condition;
import jp.reflexworks.atom.api.EntryUtil.UriPair;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.entry.Link;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.blogic.AclBlogic;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.index.BDBClientIndexUtil;
import jp.reflexworks.taggingservice.index.EntryManager;
import jp.reflexworks.taggingservice.index.FullTextSearchManager;
import jp.reflexworks.taggingservice.index.InnerIndexManager;
import jp.reflexworks.taggingservice.index.ManifestManager;
import jp.reflexworks.taggingservice.model.EditedCondition;
import jp.reflexworks.taggingservice.model.Value;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBResponseType;
import jp.reflexworks.taggingservice.requester.BDBRequester;
import jp.reflexworks.taggingservice.requester.BDBRequesterUtil;
import jp.reflexworks.taggingservice.sys.SystemAuthentication;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * データストアの検索系処理を行うクラス.
 */
public class BDBClientRetrieveManager {

	/** GETメソッド */
	private static final String METHOD_GET = Constants.GET;
	/** Feed検索条件の符号区切り文字をエンコードしたもの */
	private static final String ENCODED_EQUATIONS_DELIMITER =
			BDBClientUtil.urlEncode(Condition.DELIMITER);

	/** Feed検索結果をインメモリキャッシュから読む場合の条件 */
	private static final Set<String> READMAP_REQUESTURIS = new ConcurrentSkipListSet<>();
	static {
		READMAP_REQUESTURIS.add(Constants.URI_USER + "?title=");
		READMAP_REQUESTURIS.add(Constants.URI_USER + "?title-eq-");
	}

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * システム権限でのEntry検索.
	 * 内部的な検索処理に使用。
	 * @param uri URI
	 * @param useCache データストアキャッシュを検索する場合true
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return Entry
	 */
	public EntryBase getEntryBySystem(String uri, boolean useCache,
			ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		ReflexAuthentication systemAuth = new SystemAuthentication(auth);
		return getEntry(uri, useCache, systemAuth, requestInfo, connectionInfo);
	}

	/**
	 * Entry検索.
	 * @param uri URI
	 * @param useCache キャッシュを検索する場合true
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return Entry
	 */
	public EntryBase getEntry(String uri, boolean useCache,
			ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		return requestGetEntry(uri, useCache, auth, requestInfo, connectionInfo);
	}

	/**
	 * 条件検索
	 * @param uri URI 末尾に"/"指定済み
	 * @param isUrlForwardMatch URL前方一致指定の場合true
	 * @param conditions 検索条件
	 * @param limit 抽出件数
	 * @param cursorStr カーソル
	 * @param useCache キャッシュを使用する場合true (BDB版では使用しない)
	 * @param cacheUri OR検索時にセッションに検索キーを格納するためのキー
	 * @param reflexContext ReflexContext
	 */
	public FeedBase getFeed(String uri, boolean isUrlForwardMatch,
			List<List<Condition>> conditions, int limit, String cursorStr,
			boolean useCache, String cacheUri, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		FeedBase feed = null;

		// データ重複チェックのためのid格納セット
		Set<String> idSet = new HashSet<String>();

		String parentUri = null;
		String uriForwardSelfid = null;
		if (isUrlForwardMatch) {
			UriPair uriPair = TaggingEntryUtil.getUriPair(uri);
			parentUri = uriPair.parent;
			uriForwardSelfid = uriPair.selfid;
		} else {
			parentUri = uri;
		}
		parentUri = TaggingEntryUtil.removeLastSlash(parentUri);

		feed = TaggingEntryUtil.createFeed(serviceName);
		List<EntryBase> entries = new ArrayList<EntryBase>();
		feed.entry = entries;
		int mLimit = limit;
		if (mLimit <= 0) {
			mLimit = TaggingEnvUtil.getEntryNumberDefault(serviceName);
		}
		InnerCursor innerCursor = new InnerCursor(cursorStr);

		boolean isCheckExistOnly = false;
		int tmpLimit = mLimit - entries.size();
		if (tmpLimit <= 0) {
			// 未検索URIにデータがあるかどうかのチェックを行う。
			isCheckExistOnly = true;
			tmpLimit = 1;
		}

		String conditionUri = TaggingEntryUtil.editSlash(parentUri);
		if (isUrlForwardMatch) {
			conditionUri = conditionUri + uriForwardSelfid;
		}
		FeedBase tmpFeed = getPartOfFeed(
				conditionUri, isUrlForwardMatch, conditions, tmpLimit,
				innerCursor, idSet, useCache, cacheUri, reflexContext);

		String next = TaggingEntryUtil.getCursorFromFeed(tmpFeed);
		if (TaggingEntryUtil.isExistData(tmpFeed)) {
			if (!isCheckExistOnly) {
				entries.addAll(tmpFeed.entry);
			}
		}
		if (!StringUtils.isBlank(next)) {
			// カーソルがセットされた時点で検索終了
			// カーソルは「{親階層},{カーソル}」形式とする。
			InnerCursor retInnerCursor = new InnerCursor(parentUri, next);
			TaggingEntryUtil.setCursorToFeed(retInnerCursor.toString(), feed);
			feed.rights = tmpFeed.rights;
		}

		return feed;
	}

	/**
	 * 件数取得
	 * @param uri URI 末尾に"/"指定済み
	 * @param isUrlForwardMatch URL前方一致指定の場合true
	 * @param conditions 検索条件
	 * @param limit 最大件数
	 * @param cursorStr カーソル
	 * @param useCache キャッシュを使用する場合true
	 * @param cacheUri OR検索時にセッションに検索キーを格納するためのキー
	 * @param reflexContext ReflexContext
	 */
	public FeedBase getCount(String uri, boolean isUrlForwardMatch,
			List<List<Condition>> conditions, Integer limit, String cursorStr,
			boolean useCache, String cacheUri, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		FeedBase feed = null;
		long count = 0;

		String parentUri = null;
		String uriForwardSelfid = null;
		if (isUrlForwardMatch) {
			UriPair uriPair = TaggingEntryUtil.getUriPair(uri);
			parentUri = uriPair.parent;
			uriForwardSelfid = uriPair.selfid;
		} else {
			parentUri = uri;
		}
		parentUri = TaggingEntryUtil.removeLastSlash(parentUri);

		feed = TaggingEntryUtil.createFeed(serviceName);
		InnerCursor innerCursor = new InnerCursor(cursorStr);

		Integer tmpLimit = limit;

		String conditionUri = TaggingEntryUtil.editSlash(parentUri);
		if (isUrlForwardMatch) {
			conditionUri = conditionUri + uriForwardSelfid;
		}
		FeedBase tmpFeed = getPartOfCount(
				conditionUri, isUrlForwardMatch, conditions,
				tmpLimit, innerCursor, useCache, cacheUri, reflexContext);

		if (tmpFeed != null) {
			count += StringUtils.longValue(tmpFeed.title, 0);
			if (tmpLimit != null) {
				tmpLimit = tmpLimit - (int)count;
			}
		}

		String next = TaggingEntryUtil.getCursorFromFeed(tmpFeed);
		if (!StringUtils.isBlank(next)) {
			// カーソルがセットされた時点で検索終了
			// カーソルは「{親階層},{カーソル}」形式とする。
			InnerCursor retInnerCursor = new InnerCursor(parentUri, next);
			TaggingEntryUtil.setCursorToFeed(retInnerCursor.toString(), feed);
		}

		feed.title = String.valueOf(count);
		return feed;
	}

	/**
	 * 自階層 + 上位階層のEntryリストを取得
	 * @param uri キー
	 * @param useCache キャッシュを使用する場合true (readEntryMapのキャッシュのみ)
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 上位階層のEntryリスト
	 */
	public List<EntryBase> getParentPathEntries(String uri, boolean useCache,
			ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		if (StringUtils.isBlank(uri)) {
			return null;
		}
		List<String> parentPathUris = TaggingEntryUtil.getParentPathUris(uri);
		if (parentPathUris == null || parentPathUris.isEmpty()) {
			return null;
		}
		return getEntries(parentPathUris, useCache, auth, requestInfo, connectionInfo);
	}

	/**
	 * Entry複数検索
	 * @param uris キーリスト
	 * @param useCache キャッシュを使用する場合true (readEntryMapのキャッシュのみ)
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return Entryリスト
	 */
	public List<EntryBase> getEntries(List<String> uris, boolean useCache,
			ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		if (uris == null || uris.isEmpty()) {
			return null;
		}

		String serviceName = auth.getServiceName();
		Map<String, EntryBase> entriesMap = new HashMap<>();
		List<String> getByManifestUris = null;
		Map<String, Future<EntryBase>> futures = new HashMap<>();
		if (useCache) {
			getByManifestUris = new ArrayList<>();
			for (String uri : uris) {
				Value<EntryBase> tmpEntryValue = getByReadEntryMap(uri, auth, requestInfo,
						connectionInfo);
				if (tmpEntryValue != null) {
					entriesMap.put(uri, tmpEntryValue.value);
				} else {
					getByManifestUris.add(uri);
				}
			}
		} else {
			getByManifestUris = uris;
		}
		if (!getByManifestUris.isEmpty()) {
			// リクエストキャッシュにないEntryを検索する。
			ManifestManager manifestManager = new ManifestManager();
			FeedBase idsFeed = manifestManager.getEntryIds(getByManifestUris, auth,
					requestInfo, connectionInfo);
			Map<String, String> idUris = new LinkedHashMap<>();
			List<String> ids = new ArrayList<>();
			if (idsFeed != null && idsFeed.link != null) {
				for (Link link : idsFeed.link) {
					// <link rel="self" href={キー} title={ID} />
					String tmpUri = link._$href;
					String tmpId = link._$title;
					idUris.put(tmpId, tmpUri);
					if (!StringUtils.isBlank(tmpId)) {
						ids.add(tmpId);
					} else {
						// 検索を行う
						BDBClientGetEntryCallable callable =
								new BDBClientGetEntryCallable(tmpUri, useCache);
						Future<EntryBase> future = callable.addTask(auth, requestInfo, connectionInfo);
						futures.put(tmpUri, future);
					}
				}
			}
			if (!ids.isEmpty()) {
				EntryManager entryManager = new EntryManager();
				List<EntryBase> entriesByRequest = entryManager.getEntiesByIds(ids, auth,
						requestInfo, connectionInfo);
				if (entriesByRequest != null && !entriesByRequest.isEmpty()) {
					for (EntryBase entryByRequest : entriesByRequest) {
						if (entryByRequest != null) {
							String tmpUri = idUris.get(entryByRequest.id);
							entriesMap.put(tmpUri, entryByRequest);
						}
					}
				}
			}
		}

		// 並列検索分
		for (Map.Entry<String, Future<EntryBase>> mapEntry : futures.entrySet()) {
			String uri = mapEntry.getKey();
			Future<EntryBase> future = mapEntry.getValue();
			try {
				EntryBase entry = future.get();
				if (entry != null) {
					entriesMap.put(uri, entry);
				}

			} catch (ExecutionException e) {
				Throwable cause = e.getCause();
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) +
							"[getEntries] ExecutionException: " +
							cause.getMessage());
				}
				if (cause instanceof IOException) {
					throw (IOException)cause;
				} else if (cause instanceof TaggingException) {
					throw (TaggingException)cause;
				} else {
					throw new IOException(cause);
				}
			} catch (InterruptedException e) {
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) +
							"[getEntries] InterruptedException: " +
							e.getMessage());
				}
				throw new IOException(e);
			}
		}

		// リクエストして取得したEntryをReadEntryMapに格納
		for (String uri : getByManifestUris) {
			BDBClientUtil.setReadEntryMap(entriesMap.get(uri), uri, serviceName,
					requestInfo, connectionInfo);
		}

		if (entriesMap.isEmpty()) {
			return null;
		}

		List<EntryBase> retEntries = new ArrayList<>();
		for (String uri : uris) {
			retEntries.add(entriesMap.get(uri));
		}
		return retEntries;
	}

	/**
	 * 条件検索のURI取得処理
	 * @param conditionUri 親階層またはURI前方一致条件。末尾スラッシュ付加済み。
	 * @param isUriForwardMatch URI前方一致の場合のtrue
	 * @param conditions 検索条件
	 * @param limit 取得する最大Entry数
	 * @param innerCursor カーソル
	 * @param idSet 検索済みEntryのID格納リスト
	 * @param useCache キャッシュを使用する場合true
	 * @param cacheUri OR検索時にセッションに検索キーを格納するためのキー
	 * @param reflexContext ReflexContext
	 */
	private FeedBase getPartOfFeed(String conditionUri,
			boolean isUriForwardMatch, List<List<Condition>> conditions, int limit,
			InnerCursor innerCursor, Set<String> idSet, boolean useCache,
			String cacheUri, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		String parentUri = null;
		if (isUriForwardMatch) {
			UriPair uriPair = TaggingEntryUtil.getUriPair(conditionUri);
			parentUri = uriPair.parent;
		} else {
			parentUri = conditionUri;
		}

		// カーソルチェック
		// カーソルが指定されている場合、対象のURIかどうかチェックする
		String retCursorStr = null;
		if (innerCursor != null && innerCursor.getCursorStr() != null && !innerCursor.isUsed()) {
			if (innerCursor.getParentUri() != null &&
					!innerCursor.getParentUri().equals(TaggingEntryUtil.removeLastSlash(parentUri))) {
				if (isEnableAccessLog()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
							"[getPartOfFeed] skip getFeedProc. parentUri = " + parentUri);
				}
				return null;
			} else {
				// カーソル指定
				retCursorStr = innerCursor.getCursorStr();
				innerCursor.setUsed();
			}
		}

		// データ検索
		// カーソルを返すかどうか
		FeedBase retFeed = null;
		// 返却するEntryリスト
		List<EntryBase> retEntries = new ArrayList<EntryBase>();

		// インメモリ検索等の場合のフェッチ制限
		final int fetchLimit = TaggingEnvUtil.getFetchLimit();
		int fetchCnt = 0;

		boolean exceedEntryLimit = false;
		// OR条件から今回の検索に使用する条件を抽出。
		// 未指定は先頭。カーソル指定がある場合は、カーソルより取得。
		int orIdx = 0;
		int orSize = 1;
		if (conditions != null && !conditions.isEmpty()) {
			orSize = conditions.size();
		}
		final int orIdxMax = orSize - 1;

		// OR検索の場合、重複分は除去されるため、次のデータが存在することを確認してカーソルを返す必要がある。
		int searchLimit = 0;
		if (orSize > 1) {
			searchLimit = limit + 1;
		} else {
			searchLimit = limit;
		}

		List<Condition> conditionList = null;
		orIdx = 0;
		if (conditions != null && !conditions.isEmpty() &&
				innerCursor != null && innerCursor.getOrIdx() != null) {
			orIdx = innerCursor.getOrIdx();
			// カーソルのORインデックスチェック
			if (conditions == null || conditions.size() <= orIdx) {
				throw new IllegalParameterException("The cursor is invalid. " + innerCursor);
			}
		}
		orIdx--;	// ループの中で+1する

		// OR条件が設定されている場合
		// → (2020.9.18)Podがfinallyも実行せず落ちることがあるので、ロック処理は行わない。

		// このdoループ1の繰り返し条件
		//   OR条件インデックス(orIdx)がOR条件数(orIdxMax)未満であり、
		//   返却カーソル(retCursorStr)がnullであり、
		//   Entry取得件数がいっぱいでない、fetchLimit超過でない。(!exceedEntryLimit)
		Set<String> searchedUris = new HashSet<>();
		do {
			orIdx++;
			if (conditions != null && !conditions.isEmpty()) {
				conditionList = conditions.get(orIdx);
			}
			String uriStr = getGetFeedUri(serviceName, conditionUri, isUriForwardMatch,
					conditionList, false);
			if (searchedUris.contains(uriStr)) {
				continue;	// OR条件指定で、同じ条件を重複している場合は読み飛ばす。
			}
			searchedUris.add(uriStr);

			// 本ループ1内での返却Entryリスト
			List<EntryBase> tmpRetEntries = new ArrayList<>();
			// 本ループ1内での最大取得件数
			// (最大取得件数 - 返却Entryリストの件数)
			int tmpRetLimit = limit - retEntries.size();

			// このdoループ2の繰り返し条件
			//   返却カーソル(retCursorStr)がnullでなく、
			//   ループ1ごとの返却Entryリスト(tmpRetEntries)の件数が、
			//   ループ1ごとの最大取得件数(tmpRetLimit)未満であり、
			//   Entry取得件数がいっぱいでない、fetchLimit超過でない。(!exceedEntryLimit)
			do {
				// 本ループ2内での最大取得件数
				// (ループ1内での最大取得件数 - ループ1内での返却Entryリストの件数
				int tmpLimit = tmpRetLimit - tmpRetEntries.size();
				String backupCursorStr = retCursorStr;

				FeedBase feed = requestGetFeed(conditionUri, isUriForwardMatch, conditionList,
						searchLimit, retCursorStr, useCache, auth, requestInfo, connectionInfo);

				// キー: Entry、値: 何番目か(1からスタート)
				Map<EntryBase, Integer> tmpEntryMap = new LinkedHashMap<>();
				if (feed == null) {
					retCursorStr = null;
				} else {
					retCursorStr = TaggingEntryUtil.getCursorFromFeed(feed);
					if (TaggingEntryUtil.isExistData(feed)) {
						// 取得済みエントリーとidの比較
						int cnt = 0;
						for (EntryBase tmpEntry : feed.getEntry()) {
							cnt++;
							if (!idSet.contains(tmpEntry.id)) {
								tmpEntryMap.put(tmpEntry, cnt);
								idSet.add(tmpEntry.id);
							}
						}
						List<EntryBase> tmpEntries = null;
						// OR条件が設定されている場合、他の条件との重複チェックを行う。
						if (orSize > 1) {
							// セッションに保存したキーでチェック
							tmpEntries = checkOrDuplicateAndGet(cacheUri, uriStr,
									new ArrayList<EntryBase>(tmpEntryMap.keySet()),
									tmpLimit, reflexContext);
							int tmpLimit1 = tmpLimit + 1;
							int tmpEntriesSize = 0;
							if (tmpEntries != null) {
								tmpEntriesSize = tmpEntries.size();
							}
							if (tmpEntriesSize >= tmpLimit1) {
								// 続きのデータが存在するため、カーソルを再取得する。
								if (tmpLimit == 0) {
									// 返却数はリスト済みで、データ確認のみの検索の場合
									retCursorStr = StringUtils.null2blank(backupCursorStr);

								} else {
									// カーソルをリクエスト
									int nextIdx = tmpLimit - 1;
									EntryBase nextEntry = tmpEntries.get(nextIdx);
									int nextLimit = tmpEntryMap.get(nextEntry);
									if (logger.isTraceEnabled()) {
										logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
												"[getPartOfFeed] nextLimit=" + nextLimit);
									}
									FeedBase feedForGetCursor = requestGetFeed(conditionUri,
											isUriForwardMatch, conditionList,
											nextLimit, backupCursorStr, useCache, auth,
											requestInfo, connectionInfo);

									retCursorStr = TaggingEntryUtil.getCursorFromFeed(
											feedForGetCursor);
								}
								// 続きのデータ分は削除する。
								tmpEntries.remove(tmpLimit);
							}

						} else {
							Set<EntryBase> keySet = tmpEntryMap.keySet();
							if (keySet != null && !keySet.isEmpty()) {
								tmpEntries = new ArrayList<EntryBase>();
								tmpEntries.addAll(keySet);
							}
						}
						if (tmpEntries != null) {
							tmpRetEntries.addAll(tmpEntries);
						}
					}
				}

				// 今回取得件数がいっぱいだったかどうか
				int size = 0;
				if (feed != null && feed.entry != null) {
					size = feed.entry.size();
					exceedEntryLimit = (searchLimit < size);
				}
				// fetchLimitを超えているかどうか
				if (feed != null && BDBClientConst.MARK_FETCH_LIMIT.equals(
						feed.rights)) {
					exceedEntryLimit = true;
				} else {
					// インメモリ検索等で検索を繰り返す場合のためのフェッチ件数チェック
					if (retCursorStr != null) {
						fetchCnt += searchLimit;
					} else {
						fetchCnt += size;
					}
					if (fetchCnt > fetchLimit) {
						exceedEntryLimit = true;
					}
				}

			// カーソルが返ってきた場合で、取得済みエントリーが存在する場合は再取得
			} while (retCursorStr != null &&
					tmpRetEntries.size() < tmpRetLimit &&
					!exceedEntryLimit);

			if (!tmpRetEntries.isEmpty()) {
				retEntries.addAll(tmpRetEntries);
			}

		} while (orIdx < orIdxMax && retCursorStr == null && !exceedEntryLimit);

		retFeed = TaggingEntryUtil.createFeed(serviceName);
		retFeed.setEntry(retEntries);
		if (exceedEntryLimit) {
			retFeed.rights = BDBClientConst.MARK_FETCH_LIMIT;
		}

		// カーソル編集
		// OR条件のインデックスを付加
		if (retCursorStr != null) {
			retCursorStr = orIdx + BDBClientConst.CURSOR_SEPARATOR + retCursorStr;
		}

		TaggingEntryUtil.setCursorToFeed(retCursorStr, retFeed);
		return retFeed;
	}

	/**
	 * 件数取得のURI取得処理
	 * @param conditionUri 親階層またはURI前方一致条件
	 * @param isUriForwardMatch URI前方一致の場合true
	 * @param conditions 検索条件
	 * @param limit 最大件数
	 * @param innerCursor カーソル
	 * @param useCache キャッシュを使用する場合true
	 * @param cacheUri OR検索時にセッションに検索キーを格納するためのキー
	 * @param reflexContext ReflexContext
	 * @return 件数(Feedのtitleに設定)
	 */
	private FeedBase getPartOfCount(String conditionUri,
			boolean isUriForwardMatch, List<List<Condition>> conditions, Integer limit,
			InnerCursor innerCursor, boolean useCache,
			String cacheUri, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		String parentUri = null;
		if (isUriForwardMatch) {
			UriPair uriPair = TaggingEntryUtil.getUriPair(conditionUri);
			parentUri = uriPair.parent;
		} else {
			parentUri = conditionUri;
		}

		// カーソルチェック
		// カーソルが指定されている場合、対象のURIかどうかチェックする
		String retCursorStr = null;
		if (innerCursor != null && innerCursor.getCursorStr() != null && !innerCursor.isUsed()) {
			if (innerCursor.getParentUri() != null &&
					!innerCursor.getParentUri().equals(TaggingEntryUtil.removeLastSlash(parentUri))) {
				if (isEnableAccessLog()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
							"[getPartOfCount] skip getFeedProc. parentUri = " + parentUri);
				}
				return null;
			} else {
				// カーソル指定
				retCursorStr = innerCursor.getCursorStr();
				innerCursor.setUsed();
			}
		}

		// 件数取得
		int retLimit = TaggingEnvUtil.getFetchLimit();	// limitはfetch.limit
		if (limit != null) {
			retLimit = limit;
		}

		boolean exceedEntryLimit = false;
		// OR条件から今回の検索に使用する条件を抽出。
		// 未指定は先頭。カーソル指定がある場合は、カーソルより取得。
		int orIdx = 0;
		int orSize = 1;
		if (conditions != null && !conditions.isEmpty()) {
			orSize = conditions.size();
		}
		final int orIdxMax = orSize - 1;
		int cnt = 0;

		List<Condition> conditionList = null;
		orIdx = 0;
		if (conditions != null && !conditions.isEmpty() &&
				innerCursor != null && innerCursor.getOrIdx() != null) {
			orIdx = innerCursor.getOrIdx();
		}
		orIdx--;	// ループの中で+1する
		// (2020.9.18)Podがfinallyも実行せず落ちることがあるので、OR条件設定時のロック処理は行わない。
		Set<String> searchedUris = new HashSet<>();
		do {
			orIdx++;
			if (conditions != null && !conditions.isEmpty()) {
				conditionList = conditions.get(orIdx);
			}
			String uriStr = getGetFeedUri(serviceName, conditionUri, isUriForwardMatch,
					conditionList, true);	// ?c
			if (searchedUris.contains(uriStr)) {
				continue;	// OR条件指定で、同じ条件を重複している場合は読み飛ばす。
			}
			searchedUris.add(uriStr);
			EditedCondition editedCondition = BDBClientIndexUtil.editCondition(conditionUri,
					isUriForwardMatch, conditionList, serviceName);

			do {
				int tmpLimit = retLimit - cnt;
				// OR検索の重複チェックのため、ID一覧を取得する。
				List<String> tmpIdUris = null;
				// ID一覧のみでは存在しないエントリーも抽出されてしまう場合があるため(更新途中で処理失敗、インデックス更新時の不整合等)
				// 必ずEntry本体を抽出する処理まで行う。
				FeedBase feed = requestGetFeed(editedCondition, tmpLimit, retCursorStr,
						false, conditionUri, auth, requestInfo, connectionInfo);
				if (feed == null) {
					retCursorStr = null;
				} else {
					retCursorStr = TaggingEntryUtil.getCursorFromFeed(feed);
					tmpIdUris = getIdUrisByEntry(feed);

					// fetchLimitを超えているかどうか
					if (feed != null && BDBClientConst.MARK_FETCH_LIMIT.equals(
							feed.rights)) {
						exceedEntryLimit = true;
					}
				}
				if (tmpIdUris != null) {
					// OR条件が設定されている場合、他の条件との重複チェックを行う。
					if (orSize > 1) {
						// セッションに保存したキーでチェック
						List<String> tmpIdUriList = checkOrDuplicateAndGet(
								cacheUri, uriStr, tmpIdUris, retLimit, true,
								reflexContext);
						if (tmpIdUriList != null) {
							cnt += tmpIdUriList.size();
						}
					} else {
						cnt += tmpIdUris.size();
					}
				}

			// カーソルが返ってきた場合で、取得済みエントリーが存在する場合は再取得
			} while (retCursorStr != null
					&& cnt < retLimit
					&& !exceedEntryLimit);

		} while (orIdx < orIdxMax && retCursorStr == null && !exceedEntryLimit);

		FeedBase retFeed = TaggingEntryUtil.createFeed(serviceName);
		retFeed.title = String.valueOf(cnt);
		if (exceedEntryLimit) {
			retFeed.rights = BDBClientConst.MARK_FETCH_LIMIT;
		}

		// カーソル編集
		// OR条件のインデックスを付加
		if (!StringUtils.isBlank(retCursorStr)) {
			retCursorStr = orIdx + BDBClientConst.CURSOR_SEPARATOR + retCursorStr;
		}

		TaggingEntryUtil.setCursorToFeed(retCursorStr, retFeed);
		return retFeed;
	}

	/**
	 * Manifest検索でEntryを1件検索.
	 * 内部処理で使用。
	 * @param uri URI
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return Entry
	 */
	public EntryBase getEntryByManifestBySystem(String uri, boolean useCache,
			ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		ReflexAuthentication systemAuth = new SystemAuthentication(auth);
		return requestGetEntry(uri, useCache, systemAuth, requestInfo, connectionInfo);
	}

	/**
	 * ID指定で複数Entry取得.
	 * @param feed Feedのlink(rel="self"のtitle)にIDを指定
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return Entryを格納したFeed
	 */
	public FeedBase getEntriesByIds(FeedBase feed, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		List<String> ids = TaggingEntryUtil.getIds(feed);
		// Entry本体を取得
		EntryManager entryManager = new EntryManager();
		List<EntryBase> entries = entryManager.getEntiesByIds(ids, auth, requestInfo,
				connectionInfo);
		if (entries == null || entries.isEmpty()) {
			return null;
		}
		FeedBase retFeed = TaggingEntryUtil.createFeed(serviceName);
		retFeed.entry = entries;
		return retFeed;
	}

	/**
	 * Manifest検索でEntryを1件検索.
	 * BDBにリクエストする。
	 * @param uri URI
	 * @param useCache キャッシュを使用する場合true
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return Entry
	 */
	public EntryBase requestGetEntry(String uri, boolean useCache, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		long startTime = 0;
		long totalTime = 0;
		if (isEnableAccessLog()) {
			startTime = new Date().getTime();
			totalTime = new Date().getTime();
		}

		if (isEnableAccessLog()) {
			startTime = new Date().getTime();
		}

		// キャッシュOKの場合、リクエスト内キャッシュから取得する。(同リクエストのスレッドで共有)
		if (useCache) {
			Value<EntryBase> threadCacheEntryValue = getByReadEntryMap(uri, auth, requestInfo,
					connectionInfo);
			if (threadCacheEntryValue != null) {
				return threadCacheEntryValue.value;
			}
		}

		if (isEnableAccessLog()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[requestGetEntry] start. uri=");
			sb.append(uri);
			sb.append(" Elapsed time from the start");
			sb.append(LogUtil.getElapsedTimeLog(totalTime));
			sb.append(" Elapsed time from the get cache");
			sb.append(LogUtil.getElapsedTimeLog(startTime));
			logger.debug(sb.toString());
			startTime = new Date().getTime();
		}

		// BDBサーバにリクエスト
		try {
			// リクエスト情報設定
			String method = METHOD_GET;
			String mnfUriStr = BDBClientUtil.getGetIdByManifestUri(uri);

			// まずManifest検索
			// GET /b{キー}?e
			// 戻り値はFeed <link rel="self" href={キー} title={ID} />
			BDBRequester<FeedBase> requesterMnf = new BDBRequester<>(BDBResponseType.FEED);
			BDBResponseInfo<FeedBase> respMnf = requesterMnf.requestToManifest(
					mnfUriStr, method, null, serviceName, requestInfo, connectionInfo);

			if (isEnableAccessLog()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[requestGetEntry] requestToManifest. uri=");
				sb.append(uri);
				sb.append(LogUtil.getElapsedTimeLog(startTime));
				logger.debug(sb.toString());
				startTime = new Date().getTime();
			}

			FeedBase mnfFeed = respMnf.data;
			String id = null;
			if (mnfFeed == null || mnfFeed.link == null || mnfFeed.link.isEmpty()) {
				// データなし
			} else {
				id = mnfFeed.link.get(0)._$title;
				if (StringUtils.isBlank(id)) {
					// データなし
				}
			}

			EntryBase respEntry = null;
			if (!StringUtils.isBlank(id)) {
				// IDをキーにEntry検索
				String entryUriStr = BDBClientUtil.getEntryUri(id);
				// Entryサーバ検索
				String idUri = TaggingEntryUtil.getUriById(id);
				String entryServerUrl = BDBClientUtil.getEntryServerUrl(idUri, serviceName,
						requestInfo, connectionInfo);
				FeedTemplateMapper mapper = TaggingEnvUtil.getResourceMapper(serviceName);

				BDBRequester<EntryBase> requester = new BDBRequester<>(BDBResponseType.ENTRY);
				BDBResponseInfo<EntryBase> respInfo = requester.request(entryServerUrl,
						entryUriStr, method, null, mapper, serviceName, requestInfo,
						connectionInfo);

				if (isEnableAccessLog()) {
					StringBuilder sb = new StringBuilder();
					sb.append(LogUtil.getRequestInfoStr(requestInfo));
					sb.append("[requestGetEntry] request to entry server. uri=");
					sb.append(uri);
					sb.append(LogUtil.getElapsedTimeLog(startTime));
					logger.debug(sb.toString());
					startTime = new Date().getTime();
				}

				// 成功
				respEntry = respInfo.data;
			}

			// スレッド内キャッシュに格納
			BDBClientUtil.setReadEntryMap(respEntry, uri, serviceName,
					requestInfo, connectionInfo);

			if (respEntry != null) {
				// 自エントリーにACL指定がある場合の認可チェック
				if (hasAuthoritySelf(respEntry, auth)) {
					if (isEnableAccessLog()) {
						StringBuilder sb = new StringBuilder();
						sb.append(LogUtil.getRequestInfoStr(requestInfo));
						sb.append("[requestGetEntry] end. uri=");
						sb.append(respEntry.getMyUri());
						sb.append(LogUtil.getElapsedTimeLog(startTime));
						sb.append(LogUtil.getElapsedTimeLog(totalTime));
						logger.debug(sb.toString());
					}
					return respEntry;
				} else {
					if (isEnableAccessLog()) {
						StringBuilder sb = new StringBuilder();
						sb.append(LogUtil.getRequestInfoStr(requestInfo));
						sb.append("[requestGetEntry] hasAuthoritySelf=false uri=");
						sb.append(respEntry.getMyUri());
						logger.debug(sb.toString());
					}
				}
			}
			return null;

		} finally {
			// 何かあれば処理する
		}
	}

	/**
	 * ID指定でEntryを1件検索.
	 * Entryサーバにリクエストする。
	 * @param id ID
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return Entry
	 */
	public EntryBase requestGetEntryById(String id, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		// BDBサーバにリクエスト
		try {
			// IDをキーにEntry検索
			String entryUriStr = BDBClientUtil.getEntryUri(id);
			// Entryサーバ検索
			String method = METHOD_GET;
			String idUri = TaggingEntryUtil.getUriById(id);
			String entryServerUrl = BDBClientUtil.getEntryServerUrl(idUri, serviceName,
					requestInfo, connectionInfo);
			FeedTemplateMapper mapper = TaggingEnvUtil.getResourceMapper(serviceName);

			BDBRequester<EntryBase> requester = new BDBRequester<>(BDBResponseType.ENTRY);
			BDBResponseInfo<EntryBase> respInfo = requester.request(entryServerUrl,
					entryUriStr, method, null, mapper, serviceName, requestInfo, connectionInfo);
			// 成功
			EntryBase respEntry = respInfo.data;
			return respEntry;

		} finally {
			// 何かあれば処理する
		}
	}

	/**
	 * IDリスト指定でEntryを複数件検索.
	 * Entryサーバにリクエストする。
	 * @param ids IDリスト
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return Entryリスト
	 */
	public List<EntryBase> requestGetEntriesByIds(List<String> ids, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		// BDBサーバにリクエスト
		try {
			long startTime = 0;
			if (logger.isTraceEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[requestGetEntriesByIds] start. ids=");
				sb.append(ids);
				logger.debug(sb.toString());
				startTime = new Date().getTime();
			}

			Map<String, EntryBase> gotEntriesMap = new HashMap<>();
			// IDリストをキーにEntry複数検索
			String entryMultipleUriStr = BDBClientUtil.getEntryMultipleUri();

			if (logger.isTraceEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[requestGetEntriesByIds] divide entry server start.");
				sb.append(LogUtil.getElapsedTimeLog(startTime));
				logger.debug(sb.toString());
				startTime = new Date().getTime();
			}

			// Entryサーバ振り分け
			// キー:URL、値:IDリスト
			Map<String, List<String>> divideServerMap = new HashMap<>();
			for (String id : ids) {
				if (!gotEntriesMap.containsKey(id)) {
					String idUri = TaggingEntryUtil.getUriById(id);
					String entryServerUrl = BDBClientUtil.getEntryServerUrl(idUri, serviceName,
							requestInfo, connectionInfo);
					List<String> idList = null;
					if (divideServerMap.containsKey(entryServerUrl)) {
						idList = divideServerMap.get(entryServerUrl);
					} else {
						idList = new ArrayList<>();
						divideServerMap.put(entryServerUrl, idList);
					}
					idList.add(id);
				}
			}

			if (logger.isTraceEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[requestGetEntriesByIds] divide entry server end.");
				sb.append(LogUtil.getElapsedTimeLog(startTime));
				logger.debug(sb.toString());
				startTime = new Date().getTime();
			}

			int limit = BDBClientUtil.getEntryserverGetLimit();
			List<Future<List<EntryBase>>> futures = new ArrayList<>();
			String method = METHOD_GET;
			for (Map.Entry<String, List<String>> mapEntry : divideServerMap.entrySet()) {
				String entryServerUrl = mapEntry.getKey();
				List<String> idList = mapEntry.getValue();

				int size = idList.size();
				int idx = 0;
				while (idx < size) {
					// Entryを一定数ごとに区切ってリクエストする。
					// リクエストヘッダのサイズ制限に引っかからないようにするため。(Header is too large)
					List<String> reqIdList = null;
					int toIdx = 0;
					if (size - idx > limit) {
						toIdx = idx + limit;
					} else {
						toIdx = size;
					}
					reqIdList = idList.subList(idx, toIdx);
					idx = toIdx;

					Map<String, String> additionalHeaders =
							BDBRequesterUtil.getEntryMultipleHeader(reqIdList, null);

					// Entryサーバ検索 (並列処理)
					BDBClientGetEntriesByIdCallable callable = new BDBClientGetEntriesByIdCallable(
							entryServerUrl, entryMultipleUriStr, method, additionalHeaders);
					Future<List<EntryBase>> future = callable.addTask(auth, requestInfo,
							connectionInfo);
					futures.add(future);
				}
			}

			if (logger.isTraceEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[requestGetEntriesByIds] BDBClientGetEntriesByIdCallable created. futures.size=");
				sb.append(futures.size());
				sb.append(LogUtil.getElapsedTimeLog(startTime));
				logger.debug(sb.toString());
				startTime = new Date().getTime();
			}

			for (Future<List<EntryBase>> future : futures) {
				try {
					long tmpStartTime = new Date().getTime();
					List<EntryBase> respEntries = future.get();
					if (logger.isTraceEnabled()) {
						StringBuilder sb = new StringBuilder();
						sb.append(LogUtil.getRequestInfoStr(requestInfo));
						sb.append("[requestGetEntriesByIds] (for futures) future.get() end.");
						sb.append(LogUtil.getElapsedTimeLog(tmpStartTime));
						logger.debug(sb.toString());
					}

					if (respEntries != null) {
						for (EntryBase respEntry : respEntries) {
							if (respEntry != null) {
								gotEntriesMap.put(respEntry.id, respEntry);
							}
						}
					}

				} catch (ExecutionException e) {
					Throwable cause = e.getCause();
					if (logger.isInfoEnabled()) {
						logger.info(LogUtil.getRequestInfoStr(requestInfo) +
								"[requestGetEntriesByIds] ExecutionException: " +
								cause.getMessage());
					}
					if (cause instanceof IOException) {
						throw (IOException)cause;
					} else if (cause instanceof TaggingException) {
						throw (TaggingException)cause;
					} else {
						throw new IOException(cause);
					}
				} catch (InterruptedException e) {
					if (logger.isInfoEnabled()) {
						logger.info(LogUtil.getRequestInfoStr(requestInfo) +
								"[requestGetEntriesByIds] InterruptedException: " +
								e.getMessage());
					}
					throw new IOException(e);
				}
			}

			// 返却
			List<EntryBase> retEntries = new ArrayList<>(ids.size());
			for (String id : ids) {
				EntryBase retEntry = gotEntriesMap.get(id);
				retEntries.add(retEntry);
			}
			return retEntries;

		} finally {
			// 何かあれば処理する
		}
	}

	/**
	 * Feed検索
	 * BDBにリクエストする。
	 * @param conditionUri 親階層またはURI前方一致条件。
	 * @param isUriForwardMatch URI前方一致の場合のtrue
	 * @param conditions 検索条件リスト
	 * @param limit 最大取得件数
	 * @param cursorStr カーソル
	 * @param useCache キャッシュを使用する場合true
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 検索結果
	 */
	public FeedBase requestGetFeed(String conditionUri, boolean isUriForwardMatch,
			List<Condition> conditions, int limit, String cursorStr, boolean useCache,
			ReflexAuthentication auth, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		EditedCondition editedCondition = BDBClientIndexUtil.editCondition(conditionUri,
				isUriForwardMatch, conditions, serviceName);
		return requestGetFeed(editedCondition, limit, cursorStr, useCache, conditionUri,
				auth, requestInfo, connectionInfo);
	}

	/**
	 * Feed検索
	 * BDBにリクエストする。
	 * @param conditionUri 親階層またはURI前方一致条件。
	 * @param isUriForwardMatch URI前方一致の場合のtrue
	 * @param conditions 検索条件リスト
	 * @param limit 最大取得件数
	 * @param cursorStr カーソル
	 * @param useCache キャッシュを使用する場合true
	 * @param readCacheUri キャッシュ検索URI
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 検索結果
	 */
	public FeedBase requestGetFeed(EditedCondition editedCondition, int limit,
			String cursorStr, boolean useCache, String readCacheUri, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		Map<String, Value<FeedBase>> readFeedMap = BDBClientUtil.getFeedMap(serviceName,
				connectionInfo);
		String conditionUri = editedCondition.getConditionUri();
		boolean useThreadCache = isReadMapByGetFeed(readCacheUri);

		// キャッシュOKの場合、メインスレッド内キャッシュから取得する。
		if (useThreadCache && useCache && readFeedMap.containsKey(readCacheUri)) {
			if (isEnableAccessLog()) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
						"[requestGetFeed] find by readEntryMap (feed) : " + conditionUri);
			}
			FeedBase feed = null;
			Value<FeedBase> cacheValue = readFeedMap.get(readCacheUri);
			if (cacheValue != null) {
				feed = cacheValue.value;
			}
			if (feed != null) {
				FeedTemplateMapper mapper = TaggingEnvUtil.getResourceMapper(serviceName);
				FeedBase retFeed = TaggingEntryUtil.copyFeed(feed, mapper);
				if (retFeed.entry != null) {
					retFeed.entry = null;
					for (EntryBase entry : feed.entry) {
						if (hasAuthoritySelf(entry, auth)) {
							retFeed.addEntry(entry);
						}
					}
				}
				return retFeed;
			} else {
				return null;
			}
		}

		// BDBサーバにリクエスト
		Condition ftCondition = editedCondition.getFtCondition();
		Condition idxCondition = editedCondition.getIndexCondition();
		Condition[] innerConditions = editedCondition.getInnerConditions();
		boolean checkInnerConditions = (innerConditions != null && innerConditions.length > 0);
		boolean exceedEntryLimit = false;
		boolean exitByInnerConditions = false;
		int fetchLimit = TaggingEnvUtil.getFetchLimit();

		int currentLimit = limit;
		int requestLimit = limit;
		int tmpLimit = currentLimit;
		if (checkInnerConditions) {
			requestLimit++;
			tmpLimit++;
		}

		String retCursorStr = cursorStr;
		String prevCursorStr = null;	// 1つ前の検索のカーソル
		int fetchCnt = 0;

		List<EntryBase> resultEntries = new ArrayList<>();
		do {
			long startTime = 0;
			if (logger.isTraceEnabled()) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
						"[requestGetFeed] requestGetFeedIds start.");
				startTime = new Date().getTime();
			}
			FeedBase idsFeed = requestGetFeedIds(editedCondition,
					requestLimit, retCursorStr, auth, requestInfo, connectionInfo);
			if (logger.isTraceEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[requestGetFeed] requestGetFeedIds end. ");
				sb.append(LogUtil.getElapsedTimeLog(startTime));
				logger.debug(sb.toString());
				startTime = new Date().getTime();
			}

			// 戻り値はIDリスト: <link rel="self" title="{ID}">
			// カーソルの戻り値: <link rel="next" href="{カーソル}">
			prevCursorStr = retCursorStr;
			retCursorStr = TaggingEntryUtil.getCursorFromFeed(idsFeed);
			if (idsFeed != null && Constants.MARK_FETCH_LIMIT.equals(idsFeed.rights)) {
				// レスポンスにフェッチ数超過設定があった場合
				fetchCnt = fetchLimit + 1;
			}

			// Entryを取得
			List<String> ids = TaggingEntryUtil.getIds(idsFeed);
			if ((ids == null || ids.isEmpty())) {
				if (StringUtils.isBlank(retCursorStr)) {
					break;
				} else {
					continue;
				}
			}
			fetchCnt += ids.size();

			// Entry本体を取得
			EntryManager entryManager = new EntryManager();
			if (logger.isTraceEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[requestGetFeed] getEntiesByIds start. ");
				sb.append(LogUtil.getElapsedTimeLog(startTime));
				logger.debug(sb.toString());
				startTime = new Date().getTime();
			}
			List<EntryBase> entries = entryManager.getEntiesByIds(ids, auth, requestInfo,
					connectionInfo);
			if (logger.isTraceEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[requestGetFeed] getEntiesByIds end. ");
				sb.append(LogUtil.getElapsedTimeLog(startTime));
				logger.debug(sb.toString());
				startTime = new Date().getTime();
			}
			int i = -1;
			for (EntryBase entry : entries) {
				i++;
				if (entry != null) {
					if (checkInnerConditions) {
						// インメモリ検索の場合
						if (!CheckUtil.isMatchInnerCondition(entry,
								innerConditions)) {
							continue;
						}
					}
					if (resultEntries.size() < limit) {
						if (hasAuthoritySelf(entry, auth)) {
							resultEntries.add(entry);
						}
					} else {
						// 件数が最大+次のデータの存在を発見したためループ終了
						if (checkInnerConditions) {
							exitByInnerConditions = true;
						}
						// 1つ前のカーソルを取得する
						if (i == 0) {
							retCursorStr = prevCursorStr;
						} else if (!checkInnerConditions && (i == tmpLimit - 1)) {
							// retCursorStrをそのまま返すため何もしない。
						} else {
							// 1件前のカーソルを取得
							FeedBase tmpFeed = null;
							if (ftCondition != null) {
								// 全文検索条件が指定されている場合
								FullTextSearchManager ftManager = new FullTextSearchManager();
								tmpFeed = ftManager.getByFullTextIndex(editedCondition, prevCursorStr,
										i, false, auth, requestInfo, connectionInfo);

							} else if (idxCondition != null) {
								// インデックスが指定されている場合
								InnerIndexManager idxManager = new InnerIndexManager();
								tmpFeed = idxManager.getByInnerIndex(editedCondition, prevCursorStr,
										i, false, auth, requestInfo, connectionInfo);
							} else {
								// Manifest検索
								ManifestManager mnfManager = new ManifestManager();
								tmpFeed = mnfManager.getByManifest(editedCondition, prevCursorStr,
										i, false, auth, requestInfo, connectionInfo);
							}
							retCursorStr = TaggingEntryUtil.getCursorFromFeed(tmpFeed);
						}
						break;
					}
				}
			}

			currentLimit = limit - resultEntries.size();
			tmpLimit = currentLimit;
			if (checkInnerConditions) {
				tmpLimit++;
			}
			if (!exceedEntryLimit && retCursorStr != null && fetchCnt > fetchLimit) {
				exceedEntryLimit = true;
			}

		} while (retCursorStr != null &&
				tmpLimit > 0 && !exceedEntryLimit && !exitByInnerConditions);

		FeedBase retFeed = TaggingEntryUtil.createFeed(serviceName);
		retFeed.entry = resultEntries;

		// カーソル
		TaggingEntryUtil.setCursorToFeed(retCursorStr, retFeed);

		// フェッチ制限超えの場合印を付加
		if (exceedEntryLimit) {
			retFeed.rights = Constants.MARK_FETCH_LIMIT;
		}

		return retFeed;
	}

	/**
	 * Feed検索のIDリストを取得.
	 * BDBにリクエストする。
	 * IDリストは存在しないエントリーを含む可能性があるため(更新途中で処理失敗等)、必ずgetIdsとセットで使用する。
	 * @param editedCondition 編集した検索条件
	 * @param limit 最大取得件数
	 * @param cursorStr カーソル
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 検索結果
	 */
	public FeedBase requestGetFeedIds(EditedCondition editedCondition, int limit, String cursorStr,
			ReflexAuthentication auth, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		Condition ftCondition = editedCondition.getFtCondition();
		Condition idxCondition = editedCondition.getIndexCondition();
		String distkeyItem = editedCondition.getDistkeyItem();
		FeedBase idsFeed = null;
		if (ftCondition != null) {
			// 全文検索条件が指定されている場合
			FullTextSearchManager ftManager = new FullTextSearchManager();
			idsFeed = ftManager.getByFullTextIndex(editedCondition, cursorStr, limit,
					false, auth, requestInfo, connectionInfo);

		} else if (idxCondition != null || !StringUtils.isBlank(distkeyItem)) {
			// インデックスが指定されている場合
			InnerIndexManager idxManager = new InnerIndexManager();
			idsFeed = idxManager.getByInnerIndex(editedCondition, cursorStr, limit,
					false, auth, requestInfo, connectionInfo);
		} else {
			// Manifest検索
			ManifestManager mnfManager = new ManifestManager();
			idsFeed = mnfManager.getByManifest(editedCondition, cursorStr, limit,
					false, auth, requestInfo, connectionInfo);
		}
		return idsFeed;
	}

	/**
	 * URIリストの指定された位置にURIを追加
	 * @param i 添字
	 * @param uris URIリスト
	 * @param uri URI
	 * @return 追加位置添字
	 */
	private int addUri(int i, List<String> uris, String uri) {
		if (i >= 0 && i < uris.size() - 1) {
			i++;
			uris.add(i, uri);
			return i;
		}
		uris.add(uri);
		return uris.size();
	}

	/**
	 * 自EntryのACLチェック
	 * @param entry Entry
	 * @param auth 認証情報
	 * @return 自EntryのACL認可OKの場合true
	 */
	private boolean hasAuthoritySelf(EntryBase entry, ReflexAuthentication auth)
	throws IOException {
		AclBlogic aclBlogic = new AclBlogic();
		if (aclBlogic.hasAuthoritySelf(entry, auth)) {
			return true;
		}
		return false;
	}

	/**
	 * データストアのアクセスログを出力するかどうか
	 * @return データストアのアクセスログを出力する場合true;
	 */
	private boolean isEnableAccessLog() {
		return BDBClientUtil.isEnableAccessLog() && logger.isDebugEnabled();
	}

	/**
	 * GET Feed リクエストURLを編集.
	 * @param serviceName サービス名
	 * @param uri URI
	 * @param isUrlForwardMatch URL前方一致指定の場合true
	 * @param conditions 検索条件
	 * @param isCount 件数取得の場合true
	 * @return リクエストURL
	 */
	private String getGetFeedUri(String serviceName, String uri,
			boolean isUrlForwardMatch, List<Condition> conditions,
			boolean isCount)
	throws IOException, TaggingException {
		StringBuilder sb = new StringBuilder();
		sb.append(uri);
		if (isUrlForwardMatch) {
			sb.append(RequestParam.WILDCARD);
		}
		sb.append("?");
		if (isCount) {
			sb.append(RequestParam.PARAM_COUNT);
			sb.append("&");
			sb.append(BDBClientConst.PARAM_IDKEY);
		} else {
			sb.append(RequestParam.PARAM_FEED);
		}
		if (conditions != null) {
			// 昇順ソート条件が指定されている場合先頭に指定する。
			for (Condition condition : conditions) {
				if (Condition.ASC.equals(condition.getEquations())) {
					sb.append("&");
					sb.append(BDBClientUtil.urlEncode(condition.getProp()));
					sb.append(ENCODED_EQUATIONS_DELIMITER);
					sb.append(BDBClientUtil.urlEncode(condition.getEquations()));
				}
			}
			// 次に昇順ソート条件以外を指定。
			for (Condition condition : conditions) {
				if (!Condition.ASC.equals(condition.getEquations())) {
					sb.append("&");
					sb.append(BDBClientUtil.urlEncode(condition.getProp()));
					sb.append(ENCODED_EQUATIONS_DELIMITER);
					sb.append(BDBClientUtil.urlEncode(condition.getEquations()));
					if (!StringUtils.isBlank(condition.getValue())) {
						sb.append(ENCODED_EQUATIONS_DELIMITER);
						sb.append(BDBClientUtil.urlEncode(condition.getValue()));
					}
				}
			}
		}
		return sb.toString();
	}

	/**
	 * Feed検索のメモリキャッシュ対象かどうか判定.
	 * メモリキャッシュ対象
	 *   ・ /_user?title={アカウント} の検索結果
	 * @param requestUri 検索条件
	 * @return メモリキャッシュ対象の場合true
	 */
	private boolean isReadMapByGetFeed(String requestUri) {
		if (!StringUtils.isBlank(requestUri)) {
			for (String readMapUri : READMAP_REQUESTURIS) {
				if (requestUri.startsWith(readMapUri)) {
					int len = readMapUri.length();
					if (requestUri.indexOf("&", len) == -1) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * OR条件の重複キーチェック.
	 * セッションに保存した検索済みキーを確認し、重複したキーを除外する。
	 * 必要なEntry数+1のEntryを返す。
	 * @param cacheUri セッション格納キー
	 * @param uriStr 実行中のOR条件
	 * @param entries 検索結果Entryリスト
	 * @param tmpLimit 必要なEntry数。(検索結果は返却分より多めに取得しているため。)
	 * @param reflexContext ReflexContext
	 * @return 重複を除いた検索結果
	 */
	private List<EntryBase> checkOrDuplicateAndGet(String cacheUri, String uriStr,
			List<EntryBase> entries, int tmpLimit, ReflexContext reflexContext)
	throws IOException, TaggingException {
		if (entries == null || entries.isEmpty()) {
			return null;
		}

		List<String> idUris = new ArrayList<>();
		for (EntryBase entry : entries) {
			idUris.add(entry.getMyUri());
		}
		// まず必要な分のみ取得
		List<String> retIdUris = null;
		if (tmpLimit > 0) {
			retIdUris = checkOrDuplicateAndGet(cacheUri, uriStr, idUris,
					tmpLimit, true, reflexContext);
		} else {
			retIdUris = new ArrayList<>();
		}
		// 次に、続きのデータが存在するかどうかのチェック
		int retIdUrisSize = retIdUris.size();
		if (retIdUrisSize >= tmpLimit) {
			// 最大件数まで取得した場合、続きのデータの存在チェックを行う。
			String lastIdUri = null;
			boolean isNext = false;
			if (retIdUrisSize > 0) {
				lastIdUri = retIdUris.get(retIdUrisSize - 1);
			} else {
				isNext = true;
			}
			List<String> nextIdUriList = new ArrayList<>();
			for (EntryBase entry : entries) {
				String idUri = entry.getMyUri();
				if (isNext) {
					nextIdUriList.add(idUri);
				} else {
					if (idUri.equals(lastIdUri)) {
						isNext = true;	// 次から続きのデータ
					}
				}
			}
			List<String> nextIdUris = checkOrDuplicateAndGet(cacheUri, uriStr,
					nextIdUriList, 1, false, reflexContext);
			if (nextIdUris != null && !nextIdUris.isEmpty()) {
				retIdUris.add(nextIdUris.get(0));	// 続きのデータのID
			}
		}

		List<EntryBase> retEntries = new ArrayList<EntryBase>();
		for (EntryBase entry : entries) {
			if (retIdUris.contains(entry.getMyUri())) {
				retEntries.add(entry);
			}
		}
		return retEntries;
	}

	/**
	 * OR条件の重複キーチェック.
	 * セッションに保存した検索済みキーを確認し、重複したキーを除外する。
	 * @param cacheUri セッション格納キー
	 * @param uriStr 実行中のOR条件
	 * @param idUris 検索結果IDキーリスト
	 * @param limit 戻り値のIDキー最大数
	 * @param addUrisToSession 検索結果IDキーリストをセッションに加える場合true
	 * @param reflexContext ReflexContext
	 * @return 重複を除いた検索結果のIDキー
	 */
	private List<String> checkOrDuplicateAndGet(String cacheUri, String uriStr,
			List<String> idUris, int limit, boolean addUrisToSession, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		String sessionKey = getSessionKey(cacheUri);
		List<Link> additionUriLinks = new ArrayList<>();
		// セッションから検索済みキーを取得
		FeedBase feed = reflexContext.getSessionFeed(sessionKey);
		Set<String> searchedUris = new HashSet<>();	// 他の条件で検索済みのキーリスト
		Set<String> addedUris = new HashSet<>();	// 今回の条件で検索済みのキーリスト
		if (feed != null && feed.link != null) {
			for (Link link : feed.link) {
				if (Link.REL_SELF.equals(link._$rel) &&
						!StringUtils.isBlank(link._$href)) {
					if (!uriStr.equals(link._$title)) {
						searchedUris.add(link._$href);
					} else {
						addedUris.add(link._$href);
					}
				}
			}
		}

		List<String> retIdUris = new ArrayList<>();	// 戻り値
		for (String idUri : idUris) {
			if (searchedUris.contains(idUri)) {
				continue;
			}
			retIdUris.add(idUri);
			if (!addedUris.contains(idUri)) {
				// セッションに格納する検索済みキー情報
				Link additionUriLink = new Link();
				additionUriLink._$rel = Link.REL_SELF;	// rel : self
				additionUriLink._$href = idUri;	// href : ID URI
				additionUriLink._$title = uriStr;	// title : 今回の検索条件 (OR条件のうちの1つ)
				additionUriLinks.add(additionUriLink);
			}
			if (retIdUris.size() >= limit) {
				break;
			}
		}

		if (!additionUriLinks.isEmpty() && addUrisToSession) {
			// セッションに検索結果のIDキーを追加
			if (feed == null) {
				feed = TaggingEntryUtil.createFeed(serviceName);
			}
			if (feed.link == null) {
				feed.link = new ArrayList<>();
			}
			feed.link.addAll(additionUriLinks);
			reflexContext.setSessionFeed(sessionKey, feed);
		}

		return retIdUris;
	}

	/**
	 * FeedのLinkからID URIリストを取得する.
	 * @param feed Feed
	 * @return ID URIリスト
	 */
	private List<String> getIdUrisByLink(FeedBase feed) {
		if (feed == null || feed.link == null) {
			return null;
		}
		List<String> idUriList = new ArrayList<String>();
		for (Link link : feed.link) {
			// インデックスの戻り値: linkのtitle属性にIDが設定されている
			if (Link.REL_SELF.equals(link._$rel) && !StringUtils.isBlank(link._$title)) {
				idUriList.add(TaggingEntryUtil.getUriById(link._$title));
			}
		}
		if (!idUriList.isEmpty()) {
			return idUriList;
		}
		return null;
	}

	/**
	 * FeedのEntryからID URIリストを取得する.
	 * @param feed Feed
	 * @return ID URIリスト
	 */
	private List<String> getIdUrisByEntry(FeedBase feed) {
		if (!TaggingEntryUtil.isExistData(feed)) {
			return null;
		}
		List<String> idUriList = new ArrayList<String>();
		for (EntryBase entry : feed.entry) {
			idUriList.add(TaggingEntryUtil.getUriById(entry.id));
		}
		return idUriList;
	}

	/**
	 * セッション用キーを取得.
	 * @param uriStr 検索条件
	 * @return セッション用キー
	 */
	private String getSessionKey(String uriStr) {
		return BDBClientConst.SESSION_GETFEED_OR + uriStr;
	}

	/**
	 * リクエスト内読み込みキャッシュからEntryを取得.
	 * @param uri URI
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return Entry
	 */
	private Value<EntryBase> getByReadEntryMap(String uri, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		Map<String, Value<EntryBase>> readEntryMap = BDBClientUtil.getEntryMap(serviceName,
				connectionInfo);
		Value<EntryBase> cacheValue = readEntryMap.get(uri);
		if (cacheValue != null) {
			EntryBase threadCacheEntry = cacheValue.value;
			if (threadCacheEntry != null) {
				if (hasAuthoritySelf(threadCacheEntry, auth)) {
					// リクエスト内キャッシュを複製
					FeedTemplateMapper mapper = TaggingEnvUtil.getResourceMapper(serviceName);
					threadCacheEntry = TaggingEntryUtil.copyEntry(threadCacheEntry, mapper);
				} else {
					threadCacheEntry = null;
				}
			}
			// 一度読み込みされている
			return new Value<>(threadCacheEntry);
		} else {
			// このメインスレッドでは読み込みなし
			return null;
		}
	}

}
