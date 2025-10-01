package jp.reflexworks.taggingservice.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.api.Condition;
import jp.reflexworks.atom.entry.Category;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.atom.mapper.FeedTemplateMapper.Meta;
import jp.reflexworks.servlet.util.UrlUtil;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.bdbclient.BDBClientUtil;
import jp.reflexworks.taggingservice.bdbclient.BDBResponseInfo;
import jp.reflexworks.taggingservice.env.ReflexEnvConst;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.EditedCondition;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBIndexType;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBResponseType;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBServerType;
import jp.reflexworks.taggingservice.requester.BDBRequester;
import jp.reflexworks.taggingservice.requester.BDBRequesterUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.IndexUtil;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.ConsistentHash;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * インデックス・全文検索インデックス共通クラス.
 */
public class IndexCommonManager {

	/** Feed検索条件の符号区切り文字をエンコードしたもの */
	//private static final String ENCODED_EQUATIONS_DELIMITER =
	//		UrlUtil.urlEncode(Condition.DELIMITER);
	/** Feed検索条件の符号区切り文字 */
	private static final String ENCODED_EQUATIONS = Condition.DELIMITER;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * インデックス検索
	 * @param editedCondition 検索条件
	 * @param cursorStr カーソル
	 * @param limit 最大取得件数
	 * @param isCount 件数取得の場合true
	 * @param indexType ft or idx
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 検索結果
	 */
	public FeedBase getFeedByIndex(EditedCondition editedCondition,
			String cursorStr, int limit, boolean isCount, BDBIndexType indexType,
			ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		// 検索条件
		String uri = editedCondition.getConditionUri();
		Condition condition = null;
		Condition conditionRange = null;
		String distkeyItem = editedCondition.getDistkeyItem();
		String distkeyValue = editedCondition.getDistkeyValue();

		if (BDBIndexType.FULLTEXT.equals(indexType)) {
			condition = editedCondition.getFtCondition();
			if (condition == null) {
				return null;
			}
		} else if (BDBIndexType.INDEX.equals(indexType)) {
			Condition tmpConditionRange = editedCondition.getIndexConditionRange();
			Condition idxCondition = null;
			Condition idxConditionRange = null;
			if (tmpConditionRange != null) {
				String equationRange = tmpConditionRange.getEquations();
				if (Condition.GREATER_THAN.equals(equationRange) ||
						Condition.GREATER_THAN_OR_EQUAL.equals(equationRange)) {
					// 範囲指定の順番調整
					idxCondition = tmpConditionRange;
					idxConditionRange = editedCondition.getIndexCondition();
				} else {
					idxCondition = editedCondition.getIndexCondition();
					idxConditionRange = tmpConditionRange;
				}
			} else {
				idxCondition = editedCondition.getIndexCondition();
			}

			// 条件をインデックス検索用に編集する。
			if (idxCondition != null) {
				Meta indexMeta = editedCondition.getIndexMeta();
				condition = editIndexCondition(idxCondition, indexMeta);
				if (idxConditionRange != null) {
					conditionRange = editIndexCondition(idxConditionRange, indexMeta);
				}
			}
			if (condition == null && StringUtils.isBlank(distkeyItem)) {
				return null;
			}
		} else {
			// ManifestはここではURIの編集なし
		}
		// インメモリ検索情報
		Condition[] innerConditions = editedCondition.getInnerConditions();
		boolean checkInnerConditions = (innerConditions != null && innerConditions.length > 0);
		boolean isRequestCount = isCount && !checkInnerConditions;

		// SID
		String sid = auth.getSessionId();
		// インデックスサーバURL
		String parentUri = editedCondition.getConditionUri();
		String indexItem = null;
		if (condition != null) {
			indexItem = condition.getProp();
		}

		String assignVal = getServerAssignValue(parentUri, indexItem, distkeyItem, distkeyValue);
		List<String> serverUrls = getServerUrls(indexType, serviceName, requestInfo,
				connectionInfo);
		String serverUrl = BDBRequesterUtil.assignServer(BDBRequesterUtil.getServerType(indexType),
				serverUrls, assignVal, serviceName, connectionInfo);

		if (StringUtils.isBlank(serverUrl)) {
			if (logger.isDebugEnabled()) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
						"[getFeedByIndex] serverUrl is null.");
			}
			return null;
		}
		String requestUri = null;
		if (BDBIndexType.FULLTEXT.equals(indexType)) {
			requestUri = getGetFeedUri(uri, condition, isRequestCount);
		} else if (BDBIndexType.INDEX.equals(indexType)) {
			requestUri = getGetFeedUri(uri, condition, conditionRange, isRequestCount);
		} else {	// MANIFEST
			requestUri = getGetFeedUri(uri, editedCondition.isUriForwardMatch(), isRequestCount);
		}

		String tmpRequestUri = addLimitAndCursorToUri(requestUri, limit, cursorStr);

		// インデックス検索リクエスト
		return requestGet(serverUrl, tmpRequestUri, sid,
				editedCondition.getDistkeyItem(), editedCondition.getDistkeyValue(),
				serviceName, requestInfo, connectionInfo);
	}

	/**
	 * インデックスの登録更新
	 * @param indexInfos インデックス更新情報
	 * @param deleteIndexInfos インデックス削除情報
	 * @param isPartial 部分更新の場合true
	 * @param indexType ft or idx
	 * @param reflexContext ReflexContext
	 * @return 登録更新を行った場合true、該当データが無い場合false
	 */
	public boolean putIndex(List<EntryBase> indexInfos, List<EntryBase> deleteIndexInfos,
			boolean isPartial, BDBIndexType indexType, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		if (logger.isTraceEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
					"[putIndex] start.");
		}

		// インデックス対象がなければ処理終了。
		if ((indexInfos == null || indexInfos.isEmpty()) &&
				(deleteIndexInfos == null || deleteIndexInfos.isEmpty())) {
			if (logger.isTraceEnabled()) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
						"[putIndex] end. (IndexInfos is empty.)");
			}
			return false;
		}

		// インデックスサーバごとにEntryを分ける。
		// キー: serverUrl、値: そのサーバに送信するインデックス更新情報
		List<String> serverUrls = getServerUrls(indexType, serviceName, requestInfo,
				connectionInfo);
		// 登録更新
		BDBServerType serverType = BDBRequesterUtil.getServerType(indexType);
		ConsistentHash<String> consistentHash = BDBRequesterUtil.getConsistentHash(serverType,
				serverUrls, serviceName, connectionInfo);
		Map<String, List<EntryBase>> indexInfoMap = getIndexesForEachServer(indexInfos,
				isPartial, consistentHash, serviceName, requestInfo, connectionInfo);

		// 削除
		// キー1: serverUrl、値: インデックス更新情報
		Map<String, List<EntryBase>> deleteIndexInfoMap = getDeleteIndexesForEachServer(
				deleteIndexInfos, isPartial, serverUrls, indexType, serviceName, requestInfo,
				connectionInfo);

		// サーバごとにリクエストを送る。
		ConnectionInfo sharingConnectionInfo = BDBClientUtil.copySharingConnectionInfo(
				requestInfo, connectionInfo);
		// 更新
		for (Map.Entry<String, List<EntryBase>> mapEntry : indexInfoMap.entrySet()) {
			String serverUrl = mapEntry.getKey();
			FeedBase reqFeed = createAtomFeed();
			reqFeed.entry = mapEntry.getValue();
			if (logger.isTraceEnabled()) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
						"[putIndex] requestPut start. serverUrl=" + serverUrl + ", feed=" + reqFeed.toString());
			}

			// 更新は非同期処理
			IndexPutCallable callable = new IndexPutCallable(serverUrl, indexType, reqFeed);
			callable.addTask(reflexContext.getAuth(), requestInfo, sharingConnectionInfo);
		}

		// 削除
		for (Map.Entry<String, List<EntryBase>> mapEntry : deleteIndexInfoMap.entrySet()) {
			String serverUrl = mapEntry.getKey();
			FeedBase reqFeed = createAtomFeed();
			reqFeed.entry = mapEntry.getValue();
			if (logger.isTraceEnabled()) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
						"[putIndex(delete)] requestPut start. serverUrl=" + serverUrl + ", feed=" + reqFeed.toString());
			}

			// 更新は非同期処理
			IndexPutCallable callable = new IndexPutCallable(serverUrl, indexType, reqFeed);
			callable.addTask(reflexContext.getAuth(), requestInfo, sharingConnectionInfo);
		}

		if (logger.isTraceEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
					"[putIndex] end.");
		}

		return true;
	}

	/**
	 * 更新インデックスをサーバごとに区分けする.
	 * @param indexInfos インデックス更新情報
	 * @param isPartial 部分更新の場合true
	 * @param consistentHash ConsistentHash
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return サーバごとにまとめたインデックスMap(キー:サーバURL、値:インデックスリスト)
	 */
	public Map<String, List<EntryBase>> getIndexesForEachServer(List<EntryBase> indexInfos,
			boolean isPartial, ConsistentHash<String> consistentHash,
			String serviceName, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		Map<String, List<EntryBase>> indexInfoMap = new LinkedHashMap<>();
		FeedTemplateMapper mapper = TaggingEnvUtil.getResourceMapper(ReflexEnvConst.ATOM_STANDARD);
		if (indexInfos != null) {
			for (EntryBase indexInfo : indexInfos) {
				String parentUri = TaggingEntryUtil.removeLastSlash(
						TaggingEntryUtil.getParentUri(indexInfo.getMyUri()));
				String itemName = indexInfo.title;

				// link rel="self"のhrefにキー
				// titleに項目名
				// summaryに値
				// idにID
				// category schemeにDISTKEY
				// category labelにDISTKEYの値
				// (DISTKEYは複数指定可)

				List<Category> categories = indexInfo.getCategory();
				if (categories == null || categories.isEmpty()) {
					// DISTKEYなし
					String assignVal = getServerAssignValue(parentUri, itemName, null, null);
					String serverUrl = BDBRequesterUtil.assign(consistentHash, assignVal);
					// indexパラメータ追加。部分更新の場合URLパラメータに追加。
					serverUrl = addPutParam(serverUrl, isPartial, false);
					List<EntryBase> tmpIndexInfos = indexInfoMap.get(serverUrl);
					if (tmpIndexInfos == null) {
						tmpIndexInfos = new ArrayList<>();
						indexInfoMap.put(serverUrl, tmpIndexInfos);
					}
					tmpIndexInfos.add(indexInfo);

				} else {
					// DISTKEYあり
					// 担当サーバごとにインデックス情報を複製する。
					// キー:サーバURL、値:Categoryリスト
					Map<String, List<Category>> categoriesByServer = new HashMap<>();
					for (Category category : categories) {
						// category schemeにDISTKEY
						// category labelにDISTKEYの値
						String distkeyItem = category._$scheme;
						String distkeyValue = category._$label;
						String assignVal = getServerAssignValue(parentUri, itemName,
								distkeyItem, distkeyValue);
						String serverUrl = BDBRequesterUtil.assign(consistentHash, assignVal);
						// indexパラメータ追加。部分更新の場合URLパラメータに追加。
						serverUrl = addPutParam(serverUrl, isPartial, false);
						List<Category> tmpCategories = categoriesByServer.get(serverUrl);
						if (tmpCategories == null) {
							tmpCategories = new ArrayList<>();
							categoriesByServer.put(serverUrl, tmpCategories);
						}
						tmpCategories.add(category);
					}
					for (Map.Entry<String, List<Category>> mapEntry : categoriesByServer.entrySet()) {
						String serverUrl = mapEntry.getKey();
						List<Category> tmpCategories = mapEntry.getValue();
						EntryBase tmpIndexInfo = TaggingEntryUtil.copyEntry(indexInfo, mapper);
						tmpIndexInfo.category = tmpCategories;

						List<EntryBase> tmpIndexInfos = indexInfoMap.get(serverUrl);
						if (tmpIndexInfos == null) {
							tmpIndexInfos = new ArrayList<>();
							indexInfoMap.put(serverUrl, tmpIndexInfos);
						}
						tmpIndexInfos.add(tmpIndexInfo);
					}
				}
			}
		}
		return indexInfoMap;
	}

	/**
	 * 削除インデックスをサーバごとに区分けする.
	 * @param indexInfos インデックス更新情報
	 * @param isPartial 部分更新の場合true
	 * @param serverUrls サーバURLリスト
	 * @param indexType インデックスタイプ
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return サーバごとにまとめたインデックスMap(キー:サーバURL、値:削除インデックスリスト)
	 */
	public Map<String, List<EntryBase>> getDeleteIndexesForEachServer(
			List<EntryBase> deleteIndexInfos, boolean isPartial, List<String> serverUrls,
			BDBIndexType indexType, String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		Map<String, List<EntryBase>> deleteIndexInfoMap = new LinkedHashMap<>();
		if (deleteIndexInfos != null) {
			FeedTemplateMapper mapper = TaggingEnvUtil.getResourceMapper(
					ReflexEnvConst.ATOM_STANDARD);
			// キー: serverUrl、値: idリスト
			Map<String, Set<String>> deleteIndexIdMap = null;
			if (!isPartial) {
				deleteIndexIdMap = new HashMap<>();
			}
			BDBServerType serverType = BDBRequesterUtil.getServerType(indexType);
			for (EntryBase indexInfo : deleteIndexInfos) {
				String parentUri = TaggingEntryUtil.removeLastSlash(
						TaggingEntryUtil.getParentUri(indexInfo.getMyUri()));
				String itemName = indexInfo.title;

				indexInfo.subtitle = "";
				if (!isPartial) {
					// Entry全体のインデックス更新の場合、不要な項目の削除
					indexInfo.setMyUri("");
					indexInfo.title = null;
				}

				List<Category> categories = indexInfo.getCategory();
				if (categories == null || categories.isEmpty()) {
					// DISTKEYなし
					String assignVal = getServerAssignValue(parentUri, itemName, null, null);
					String serverUrl = BDBRequesterUtil.assignServer(serverType, serverUrls,
							assignVal, serviceName, connectionInfo);
					// indexパラメータ追加。部分更新の場合URLパラメータに追加。
					serverUrl = addPutParam(serverUrl, isPartial, true);
					List<EntryBase> tmpIndexInfos = deleteIndexInfoMap.get(serverUrl);
					if (tmpIndexInfos == null) {
						tmpIndexInfos = new ArrayList<>();
						deleteIndexInfoMap.put(serverUrl, tmpIndexInfos);
					}
					if (!isPartial) {
						// 同じidの削除データが無ければ追加
						Set<String> tmpIndexIds = deleteIndexIdMap.get(serverUrl);
						if (tmpIndexIds == null) {
							tmpIndexIds = new HashSet<>();
							deleteIndexIdMap.put(serverUrl, tmpIndexIds);
						}
						if (!tmpIndexIds.contains(indexInfo.id)) {
							tmpIndexInfos.add(indexInfo);
							tmpIndexIds.add(indexInfo.id);
						}
					} else {
						tmpIndexInfos.add(indexInfo);
					}

				} else {
					// DISTKEYあり
					// 担当サーバごとにインデックス情報を複製する。
					// キー:サーバURL、値:Categoryリスト
					Map<String, List<Category>> categoriesByServer = new HashMap<>();
					for (Category category : categories) {
						// category schemeにDISTKEY
						// category labelにDISTKEYの値
						String distkeyItem = category._$scheme;
						String distkeyValue = category._$label;
						String assignVal = getServerAssignValue(parentUri, itemName,
								distkeyItem, distkeyValue);
						String serverUrl = BDBRequesterUtil.assignServer(serverType, serverUrls,
								assignVal, serviceName, connectionInfo);
						// indexパラメータ追加。部分更新の場合URLパラメータに追加。
						serverUrl = addPutParam(serverUrl, isPartial, true);
						List<Category> tmpCategories = categoriesByServer.get(serverUrl);
						if (tmpCategories == null) {
							tmpCategories = new ArrayList<>();
							categoriesByServer.put(serverUrl, tmpCategories);
						}
						tmpCategories.add(category);
					}
					for (Map.Entry<String, List<Category>> mapEntry : categoriesByServer.entrySet()) {
						String serverUrl = mapEntry.getKey();
						List<Category> tmpCategories = mapEntry.getValue();
						EntryBase tmpIndexInfo = TaggingEntryUtil.copyEntry(indexInfo, mapper);
						tmpIndexInfo.category = tmpCategories;

						List<EntryBase> tmpIndexInfos = deleteIndexInfoMap.get(serverUrl);
						if (tmpIndexInfos == null) {
							tmpIndexInfos = new ArrayList<>();
							deleteIndexInfoMap.put(serverUrl, tmpIndexInfos);
						}
						if (!isPartial) {
							// 同じidの削除データが無ければ追加
							Set<String> tmpIndexIds = deleteIndexIdMap.get(serverUrl);
							if (tmpIndexIds == null) {
								tmpIndexIds = new HashSet<>();
								deleteIndexIdMap.put(serverUrl, tmpIndexIds);
							}
							if (!tmpIndexIds.contains(indexInfo.id)) {
								tmpIndexInfos.add(indexInfo);
								tmpIndexIds.add(indexInfo.id);
							}
						} else {
							tmpIndexInfos.add(indexInfo);
						}
					}
				}
			}
		}
		return deleteIndexInfoMap;
	}

	/**
	 * URLに_indexパラメータを追加。
	 * 部分更新・削除の場合、リクエストURLにオプションを追加.
	 * @param serverUrl リクエストURL
	 * @param isPartial 部分更新の場合true
	 * @param isDelete 削除の場合true
	 * @return 編集したリクエストURL
	 */
	public String addPutParam(String serverUrl, boolean isPartial, boolean isDelete) {
		String tmpUrl = UrlUtil.addParam(serverUrl, RequestParam.PARAM_INDEX, null);
		if (isPartial) {
			tmpUrl = UrlUtil.addParam(tmpUrl, RequestParam.PARAM_PARTIAL, null);
		}
		if (isDelete) {
			tmpUrl = UrlUtil.addParam(tmpUrl, RequestParam.PARAM_DELETE, null);
		}
		return tmpUrl;
	}

	/**
	 * ATOM標準ResourceMapperを取得.
	 * @return ATOM標準ResourceMapper
	 */
	public FeedTemplateMapper getAtomResourceMapper() {
		return TaggingEnvUtil.getAtomResourceMapper();
	}

	/**
	 * ATOM標準Entryを取得.
	 * @return ATOM標準Entry
	 */
	public EntryBase createAtomEntry() {
		return TaggingEntryUtil.createEntry(getAtomResourceMapper());
	}

	/**
	 * ATOM標準Feedを取得.
	 * @return ATOM標準Feed
	 */
	public FeedBase createAtomFeed() {
		return TaggingEntryUtil.createFeed(getAtomResourceMapper());
	}

	/**
	 * 条件をインデックス検索用に編集する.
	 * 値が数値やDate型の場合、検索用の値に変換する。
	 * @param indexCondition 条件
	 * @param indexMeta 条件項目
	 * @return 検索用の値に変換した条件
	 */
	private Condition editIndexCondition(Condition indexCondition, Meta indexMeta) {
		Object obj = IndexUtil.convertIndexValueByType(indexCondition, indexMeta);
		String val = IndexUtil.editIndexValue(obj);
		return new Condition(indexCondition.getProp(), indexCondition.getEquations(), val);
	}

	/**
	 * インデックスの環境クローズ
	 * @param indexType インデックスタイプ (idx, ft or mnf)
	 * @param reflexContext ReflexContext
	 * @return リクエストを送った場合true、対象サーバが無い場合false
	 */
	public boolean closeIndex(BDBIndexType indexType, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
					"[closeIndex] start.");
		}

		List<String> serverUrls = null;
		if (BDBIndexType.FULLTEXT.equals(indexType)) {
			serverUrls = BDBRequesterUtil.getFtServerUrls(serviceName, requestInfo, connectionInfo);
		} else if (BDBIndexType.INDEX.equals(indexType)) {
			serverUrls = BDBRequesterUtil.getIdxServerUrls(serviceName, requestInfo, connectionInfo);
		} else {
			// Manifestは別
		}

		if (serverUrls == null || serverUrls.isEmpty()) {
			return false;
		}

		for (String serverUrl : serverUrls) {
			ConnectionInfo sharingConnectionInfo = BDBClientUtil.copySharingConnectionInfo(
					requestInfo, connectionInfo);
			IndexCloseCallable indexCloseCallable = new IndexCloseCallable(serverUrl, indexType);
			indexCloseCallable.addTask(reflexContext.getAuth(), requestInfo, sharingConnectionInfo);
		}

		return true;
	}

	/**
	 * インデックスの環境クローズ
	 * @param serviceName サービス名
	 * @param indexType ft or idx
	 * @param reflexContext ReflexContext
	 * @return リクエストを送った場合true、対象サーバが無い場合false
	 */
	boolean closeIndexProc(String serverUrl, BDBIndexType indexType, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
					"[closeIndexProc] start.");
		}

		String uriStr = "?" + RequestParam.PARAM_CLOSE;
		requestDelete(serverUrl, uriStr, serviceName, requestInfo, connectionInfo);
		return true;
	}

	/**
	 * 検索処理.
	 * インデックスサーバにリクエストする。
	 * @param bdbServerUrl インデックスサーバURL
	 * @param requestUri PathInfo + QueryString
	 * @param sid SID
	 * @param distkeyItem DISTKEY項目
	 * @param distkeyValue DISTKEYの値
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public FeedBase requestGet(String bdbServerUrl, String requestUri,
			String sid, String distkeyItem, String distkeyValue, String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// BDBサーバにリクエスト
		try {
			// リクエスト情報設定
			String method = Constants.GET;
			FeedTemplateMapper mapper = TaggingEnvUtil.getAtomResourceMapper();

			BDBRequester<FeedBase> requester = new BDBRequester<>(BDBResponseType.FEED);
			BDBResponseInfo<FeedBase> bdbResponseInfo = requester.request(
					bdbServerUrl, requestUri, method, null, sid, distkeyItem, distkeyValue,
					null, mapper, serviceName, requestInfo, connectionInfo);
			return bdbResponseInfo.data;

		} finally {
			// 何かあれば処理する
		}
	}

	/**
	 * 更新処理.
	 * インデックスサーバにリクエストする。
	 * @param bdbServerUrl インデックスサーバURL (QueryString含む)
	 * @param feed Feed
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void requestPut(String bdbServerUrl, FeedBase feed,
			String serviceName, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// BDBサーバにリクエスト
		try {
			// リクエスト情報設定
			String method = Constants.PUT;
			FeedTemplateMapper mapper = TaggingEnvUtil.getAtomResourceMapper();

			BDBRequester<FeedBase> requester = new BDBRequester<>(BDBResponseType.FEED);
			requester.request(bdbServerUrl, null, method, feed, mapper, serviceName,
					requestInfo, connectionInfo);

		} finally {
			// 何かあれば処理する
		}
	}

	/**
	 * DELETEリクエスト処理.
	 * インデックスサーバにリクエストする。
	 * @param bdbServerUrl インデックスサーバURL
	 * @param uriStr pathInfo + QueryString
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void requestDelete(String bdbServerUrl, String uriStr,
			String serviceName, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// BDBサーバにリクエスト
		try {
			// リクエスト情報設定
			String method = Constants.DELETE;
			FeedTemplateMapper mapper = TaggingEnvUtil.getAtomResourceMapper();

			BDBRequester<FeedBase> requester = new BDBRequester<>(BDBResponseType.FEED);
			requester.request(bdbServerUrl, uriStr, method, null, mapper, serviceName,
					requestInfo, connectionInfo);

		} finally {
			// 何かあれば処理する
		}
	}

	/**
	 * サーバ振り分け判定値を取得
	 * @param parentUri 親キー
	 * @param item インデックス項目
	 * @param distkeyItem DISTKEY項目
	 * @param distkeyValue DISTKEY値
	 * @return サーバ振り分け判定値
	 */
	private String getServerAssignValue(String parentUri, String item,
			String distkeyItem, String distkeyValue) {
		StringBuilder sb = new StringBuilder();
		if (!StringUtils.isBlank(distkeyItem)) {
			sb.append(distkeyItem);
			sb.append("#");
			sb.append(StringUtils.null2blank(distkeyValue));
			sb.append(":");
		}
		sb.append(parentUri);
		if (!StringUtils.isBlank(item)) {
			sb.append("#");
			sb.append(item);
		}
		return sb.toString();
	}

	/**
	 * サーバURLリストを取得
	 * @param indexType ft, idx or mnf
	 * @param serviceName サーバ名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return サーバURLリスト
	 */
	private List<String> getServerUrls(BDBIndexType indexType,
			String serviceName, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		List<String> serverUrls = null;
		if (BDBIndexType.MANIFEST.equals(indexType)) {
			serverUrls = new ArrayList<>();
			String serverUrl = BDBRequesterUtil.getMnfServerUrl(serviceName,
					requestInfo, connectionInfo);
			serverUrls.add(serverUrl);
		} else {
			if (BDBIndexType.FULLTEXT.equals(indexType)) {
				serverUrls = BDBRequesterUtil.getFtServerUrls(serviceName,
						requestInfo, connectionInfo);
			} else {	// INDEX
				serverUrls = BDBRequesterUtil.getIdxServerUrls(serviceName,
						requestInfo, connectionInfo);
			}
		}
		return serverUrls;
	}

	/**
	 * GET Feed リクエストURLを編集.
	 * @param uri URI
	 * @param isUrlForwardMatch URL前方一致指定の場合true
	 * @param conditions 検索条件
	 * @param isCount 件数取得の場合true
	 * @return リクエストURL
	 */
	private String getGetFeedUri(String uri, boolean isUrlForwardMatch,
			List<Condition> conditions, boolean isCount)
	throws IOException, TaggingException {
		StringBuilder sb = new StringBuilder();
		sb.append(uri);
		if (isUrlForwardMatch) {
			sb.append(RequestParam.WILDCARD);
		}
		sb.append("?");
		if (isCount) {
			sb.append(RequestParam.PARAM_COUNT);
		} else {
			sb.append(RequestParam.PARAM_FEED);
		}
		if (conditions != null) {
			for (Condition condition : conditions) {
				sb.append("&");
				//sb.append(UrlUtil.urlEncode(condition.getProp()));
				//sb.append(ENCODED_EQUATIONS_DELIMITER);
				//sb.append(UrlUtil.urlEncode(condition.getEquations()));
				sb.append(condition.getProp());
				sb.append(ENCODED_EQUATIONS);
				sb.append(condition.getEquations());
				String val = condition.getValue();
				if (!StringUtils.isBlank(val)) {
					//sb.append(ENCODED_EQUATIONS_DELIMITER);
					//sb.append(UrlUtil.urlEncode(val));
					sb.append(ENCODED_EQUATIONS);
					sb.append(val);
				}
			}
		}
		return sb.toString();
	}

	/**
	 * リクエストURLを編集.
	 * 全文検索時に使用
	 * @param uri URI
	 * @param condition 検索条件
	 * @param isCount 件数のみ取得の場合true
	 * @return リクエストURL
	 */
	private String getGetFeedUri(String uri, Condition condition, boolean isCount)
	throws IOException, TaggingException {
		List<Condition> conditions = new ArrayList<>();
		conditions.add(condition);
		return getGetFeedUri(uri, false, conditions, isCount);
	}

	/**
	 * リクエストURLを編集.
	 * @param uri URI
	 * @param condition 検索条件
	 * @param conditionRange 検索条件2番目
	 * @param isCount 件数のみ取得の場合true
	 * @return リクエストURL
	 */
	private String getGetFeedUri(String uri, Condition condition,
			Condition conditionRange, boolean isCount)
	throws IOException, TaggingException {
		List<Condition> conditions = null;
		if (condition != null) {
			conditions = new ArrayList<>();
			conditions.add(condition);
			if (conditionRange != null) {
				conditions.add(conditionRange);
			}
		}
		return getGetFeedUri(uri, false, conditions, isCount);
	}

	/**
	 * リクエストURLを編集.
	 * @param uri URI
	 * @param isUrlForwardMatch キー前方一致検索の場合true
	 * @param isCount 件数のみ取得の場合true
	 * @return リクエストURL
	 */
	private String getGetFeedUri(String uri, boolean isUrlForwardMatch, boolean isCount)
	throws IOException, TaggingException {
		return getGetFeedUri(uri, isUrlForwardMatch, null, isCount);
	}

	/**
	 * リクエストURIに最大取得件数とカーソル条件を追加.
	 * @param uri リクエストURI
	 * @param limit 最大取得件数
	 * @param cursorStr カーソル
	 * @return 編集したリクエストURI
	 */
	private String addLimitAndCursorToUri(String uri, int limit, String cursorStr) {
		StringBuilder sb = new StringBuilder();
		sb.append(uri);
		sb.append("&");
		sb.append(RequestParam.PARAM_LIMIT);
		sb.append("=");
		sb.append(limit);
		if (!StringUtils.isBlank(cursorStr)) {
			sb.append("&");
			sb.append(RequestParam.PARAM_NEXT);
			sb.append("=");
			//sb.append(UrlUtil.urlEncode(cursorStr));
			sb.append(cursorStr);
		}
		return sb.toString();
	}

}
