package jp.reflexworks.taggingservice.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import jp.reflexworks.atom.entry.Category;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.servlet.util.UrlUtil;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBIndexType;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.reflexworks.taggingservice.util.TaggingIndexUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * インデックス登録・更新・削除　管理クラス
 */
public class UpdateIndexManager {

	/**
	 * インデックス更新非同期処理登録
	 * @param indexFeed インデックス更新情報.
	 *                  link rel="self の href : 親キー
	 *                  title : 項目名 (任意)
	 *                  subtitle : インデックスサーバ名 (任意)
	 *                  category schema : DISTKEY項目名 (任意)
	 * @param isDelete 削除の場合true
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void addTaskPutIndex(FeedBase indexFeed, boolean isDelete,
			ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		UpdateIndexCallable callable = new UpdateIndexCallable(indexFeed, isDelete);
		UpdateIndexUtil.addTask(callable, auth, requestInfo, connectionInfo);
	}

	/**
	 * インデックス更新
	 * @param indexFeed インデックス更新情報.
	 *                  link rel="self の href : 親キー
	 *                  title : 項目名 (任意)
	 *                  subtitle : インデックスサーバ名 (任意)
	 *                  category schema : DISTKEY項目名 (任意)
	 * @param isDelete 削除の場合true
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void putIndex(FeedBase indexFeed, boolean isDelete,
			ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();

		// インデックス情報 キー:項目、値:親キーのパターン
		Map<String, Pattern> templateIndexMap =
				TaggingEnvUtil.getTemplateIndexMap(serviceName);
		Map<String, Pattern> templateFullTextIndexMap =
				TaggingEnvUtil.getTemplateFullTextIndexMap(serviceName);
		Map<String, Pattern> templateDistkeyMap =
				TaggingEnvUtil.getTemplateDistkeyMap(serviceName);

		// 親階層ごとにインデックス更新情報を分ける
		// キー:親階層、値:インデックス更新情報(入力値)
		Map<String, List<EntryBase>> indexEntryMap = new LinkedHashMap<>();

		// 指定された更新インデックス情報ごとの、実際のインデックス項目リスト・DISTKEY項目リスト
		Set<EntryBase> isAllSet = new HashSet<>();
		Set<EntryBase> isOnlyDistkeySet = new HashSet<>();
		Map<EntryBase, List<String>> itemsMap = new HashMap<>();
		Map<EntryBase, List<String>> fulltextItemsMap = new HashMap<>();
		Map<EntryBase, List<String>> distkeyItemsMap = new HashMap<>();

		for (EntryBase indexEntry : indexFeed.entry) {
			String parentUri = TaggingEntryUtil.removeLastSlash(indexEntry.getMyUri());
			List<EntryBase> indexEntries = null;
			if (indexEntryMap.containsKey(parentUri)) {
				indexEntries = indexEntryMap.get(parentUri);
			} else {
				indexEntries = new ArrayList<>();
				indexEntryMap.put(parentUri, indexEntries);
			}
			indexEntries.add(indexEntry);

			// 更新項目
			List<String> indexItems = null;
			List<String> fulltextIndexItems = null;
			List<String> distkeyItems = null;

			// まずは指定されたDISTKEYをセット
			if (indexEntry.category != null && !indexEntry.category.isEmpty()) {
				distkeyItems = new ArrayList<>();
				for (Category category : indexEntry.category) {
					if (!StringUtils.isBlank(category._$scheme)) {
						distkeyItems.add(category._$scheme);
					}
				}
			}

			String itemName = indexEntry.title;
			if (StringUtils.isBlank(itemName)) {
				if (distkeyItems == null) {
					// インデックス項目・DISTKEYのいずれも設定されていない場合、すべてのインデックス項目が更新対象
					isAllSet.add(indexEntry);
				} else {
					// DISTKEYのみ指定されている場合、DISTKEYのみのEntryと、インデックス+DISTKEYを指定。
					isOnlyDistkeySet.add(indexEntry);
					indexItems = TaggingIndexUtil.getIndexItemNames(parentUri, templateIndexMap);
					fulltextIndexItems = TaggingIndexUtil.getIndexItemNames(parentUri,
							templateFullTextIndexMap);
				}
			} else {
				// インデックス項目が指定されている場合
				boolean hasIndex = TaggingIndexUtil.useIndex(parentUri, itemName, templateIndexMap);
				boolean hasFulltextIndex = TaggingIndexUtil.useIndex(parentUri, itemName,
						templateFullTextIndexMap);
				// インデックス
				if (hasIndex && !StringUtils.isBlank(itemName)) {
					indexItems = new ArrayList<>();
					indexItems.add(itemName);
				}
				// 全文検索インデックス
				if (hasFulltextIndex && !StringUtils.isBlank(itemName)) {
					fulltextIndexItems = new ArrayList<>();
					fulltextIndexItems.add(itemName);
				}
				// DISTKEYが指定されていない場合は、対象のDISTKEYをすべて更新
				if (distkeyItems == null) {
					distkeyItems = TaggingIndexUtil.getIndexItemNames(parentUri,
							templateDistkeyMap);
				}
			}

			if (indexItems != null && !indexItems.isEmpty()) {
				itemsMap.put(indexEntry, indexItems);
			}
			if (fulltextIndexItems != null && !fulltextIndexItems.isEmpty()) {
				fulltextItemsMap.put(indexEntry, fulltextIndexItems);
			}
			if (distkeyItems != null && !distkeyItems.isEmpty()) {
				distkeyItemsMap.put(indexEntry, distkeyItems);
			}
		}

		InnerIndexManager indexManager = new InnerIndexManager();
		FullTextSearchManager fulltextSearchManager = new FullTextSearchManager();
		SystemContext systemContext = new SystemContext(auth, requestInfo, connectionInfo);

		for (Map.Entry<String, List<EntryBase>> mapEntry : indexEntryMap.entrySet()) {
			String parentUri = mapEntry.getKey();
			List<EntryBase> indexEntries = mapEntry.getValue();

			// Feed検索
			String cursorStr = null;
			do {
				String editParentUri = addCursorStr(parentUri, cursorStr);
				FeedBase feed = systemContext.getFeed(editParentUri, false);
				cursorStr = TaggingEntryUtil.getCursorFromFeed(feed);
				if (!TaggingEntryUtil.isExistData(feed)) {
					continue;
				}

				// 指定されたインデックスごとに処理
				for (EntryBase indexEntry : indexEntries) {
					if (isAllSet.contains(indexEntry)) {
						// すべて更新
						for (EntryBase entry : feed.entry) {
							// インデックス
							List<EntryBase> tmpIndexes = indexManager.createInnerIndexInfos(
									entry, isDelete, serviceName, requestInfo);
							List<EntryBase> innerIndexInfos = null;
							List<EntryBase> deleteIndexInfos = null;
							if (!isDelete) {
								innerIndexInfos = tmpIndexes;
							} else {
								deleteIndexInfos = tmpIndexes;
							}
							indexManager.putIndex(innerIndexInfos, deleteIndexInfos, false,
									BDBIndexType.INDEX, systemContext);

							// 全文検索インデックス
							tmpIndexes = fulltextSearchManager.createFullTextIndexInfos(
									entry, isDelete, serviceName, requestInfo);
							innerIndexInfos = null;
							deleteIndexInfos = null;
							if (!isDelete) {
								innerIndexInfos = tmpIndexes;
							} else {
								deleteIndexInfos = tmpIndexes;
							}
							indexManager.putIndex(innerIndexInfos, deleteIndexInfos, false,
									BDBIndexType.FULLTEXT, systemContext);
						}

					} else {
						// 指定されたインデックス項目のみ更新
						boolean isOnlyDistkey = isOnlyDistkeySet.contains(indexEntry);
						List<String> items = itemsMap.get(indexEntry);
						List<String> fulltextItems = fulltextItemsMap.get(indexEntry);
						List<String> distkeyItems = distkeyItemsMap.get(indexEntry);

						// インデックス
						if (items != null && !items.isEmpty()) {
							indexManager.putIndexPartially(parentUri, feed, items,
									distkeyItems, isDelete, serviceName, systemContext);
						}
						// 全文検索インデックス
						if (fulltextItems != null && !fulltextItems.isEmpty()) {
							fulltextSearchManager.putIndexPartially(editParentUri, feed,
									fulltextItems, distkeyItems, isDelete, serviceName,
									systemContext);

						}
						// DISTKEYのみの更新がある場合
						if (isOnlyDistkey) {
							indexManager.putIndexPartially(parentUri, feed, null,
									distkeyItems, isDelete, serviceName, systemContext);

						}
					}
				}
			} while (!StringUtils.isBlank(cursorStr));
		}
	}

	/**
	 * URIにカーソルを付加.
	 * @param parentUri 親URI
	 * @param cursorStr カーソル
	 * @return 親URI + カーソル
	 */
	private String addCursorStr(String parentUri, String cursorStr) {
		if (StringUtils.isBlank(cursorStr)) {
			return parentUri;
		}
		return UrlUtil.addParam(parentUri, RequestParam.PARAM_NEXT, cursorStr);
	}

}
