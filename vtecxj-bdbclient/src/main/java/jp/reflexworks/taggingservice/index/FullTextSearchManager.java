package jp.reflexworks.taggingservice.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.Category;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.EditedCondition;
import jp.reflexworks.taggingservice.model.InnerIndex;
import jp.reflexworks.taggingservice.model.UpdatedInfo;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBIndexType;
import jp.reflexworks.taggingservice.util.Constants.OperationType;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;

/**
 * 全文検索管理クラス.
 */
public class FullTextSearchManager extends IndexCommonManager {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 全文検索
	 * @param editedCondition 検索条件
	 * @param cursorStr カーソル
	 * @param limit 最大取得件数
	 * @param isCount 件数取得の場合true
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 検索結果
	 */
	public FeedBase getByFullTextIndex(EditedCondition editedCondition,
			String cursorStr, int limit, boolean isCount,
			ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		return getFeedByIndex(editedCondition, cursorStr, limit, isCount,
				BDBIndexType.FULLTEXT, auth, requestInfo, connectionInfo);
	}

	/**
	 * 全文検索インデックスの登録更新
	 * @param updatedInfos エントリー更新情報
	 * @param reflexContext ReflexContext
	 * @return 登録更新を行った場合true、該当データが無い場合false
	 */
	public boolean putFullTextIndex(List<UpdatedInfo> updatedInfos, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		RequestInfo requestInfo = reflexContext.getRequestInfo();

		List<EntryBase> fullTextIndexInfos = new ArrayList<EntryBase>();
		List<EntryBase> deleteIndexInfos = new ArrayList<EntryBase>();
		for (UpdatedInfo updatedInfo : updatedInfos) {
			OperationType flg = updatedInfo.getFlg();
			if (flg != OperationType.DELETE) {
				// 登録更新
				List<EntryBase> tmpFullTextIndexInfos = createFullTextIndexInfos(
						updatedInfo.getUpdEntry(), false, serviceName, requestInfo);
				if (tmpFullTextIndexInfos != null && !tmpFullTextIndexInfos.isEmpty()) {
					fullTextIndexInfos.addAll(tmpFullTextIndexInfos);
				}
			} else {
				// 削除
				List<EntryBase> tmpFullTextIndexInfos = createFullTextIndexInfos(
						updatedInfo.getPrevEntry(), true, serviceName, requestInfo);
				if (tmpFullTextIndexInfos != null && !tmpFullTextIndexInfos.isEmpty()) {
					deleteIndexInfos.addAll(tmpFullTextIndexInfos);
				}
			}
		}

		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[putFullTextIndex] updatedInfos = ");
			sb.append(updatedInfos);
			sb.append(" , fullTextIndexInfos = ");
			sb.append(fullTextIndexInfos);
			logger.debug(sb.toString());
		}

		return putIndex(fullTextIndexInfos, deleteIndexInfos, false, BDBIndexType.FULLTEXT,
				reflexContext);
	}

	/**
	 * 全文検索インデックス情報リストを生成.
	 * @param entry Entry
	 * @param isDelete 削除の場合true
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @return 全文検索インデックス情報インデックスリスト
	 */
	public List<EntryBase> createFullTextIndexInfos(EntryBase entry, boolean isDelete,
			String serviceName, RequestInfo requestInfo) {
		List<EntryBase> indexInfos = new ArrayList<EntryBase>();
		String idUri = entry.getMyUri();
		List<String> aliases = entry.getAlternate();
		// id uri
		List<InnerIndex> indexes = BDBClientIndexUtil.getFullTextIndexesByUri(idUri, entry,
				serviceName, requestInfo);
		List<Category> distkeys = BDBClientIndexUtil.getDistkeysByUri(idUri, entry,
				serviceName, requestInfo);
		for (InnerIndex innerIndex : indexes) {
			indexInfos.add(createFullTextIndexInfo(innerIndex, distkeys, isDelete));
		}
		// alias
		if (aliases != null) {
			for (String alias : aliases) {
				indexes = BDBClientIndexUtil.getFullTextIndexesByUri(alias, entry,
						serviceName, requestInfo);
				distkeys = BDBClientIndexUtil.getDistkeysByUri(alias, entry,
						serviceName, requestInfo);
				for (InnerIndex innerIndex : indexes) {
					indexInfos.add(createFullTextIndexInfo(innerIndex, distkeys, isDelete));
				}
			}
		}

		if (indexInfos.isEmpty()) {
			return null;
		}
		return indexInfos;
	}

	/**
	 * 全文検索インデックス情報をEntry形式にして生成.
	 * @param innerIndex 全文検索インデックス情報
	 * @param distkeys DISTKEY情報リスト
	 * @param isDelete 削除の場合true
	 * @return 全文検索インデックス情報をEntry形式にしたもの
	 */
	public EntryBase createFullTextIndexInfo(InnerIndex innerIndex, List<Category> distkeys,
			boolean isDelete) {
		EntryBase entry = createAtomEntry();
		// link rel="self"のhrefにキー
		entry.setMyUri(innerIndex.getIndexUri());
		// titleに項目名
		entry.title = innerIndex.getName();
		if (!isDelete) {
			// summaryに値
			String text = null;
			Object indexObj = innerIndex.getIndexObj();
			if (indexObj == null) {
				text = "";
			} else {
				if (indexObj instanceof String) {
					text = (String)indexObj;
				} else {
					text = indexObj.toString();
				}
			}
			entry.summary = text;
		}
		// idにID
		entry.id = innerIndex.getId();

		// DISTKEY
		entry.category = distkeys;

		return entry;
	}

	/**
	 * インデックス部分更新処理
	 * @param parentUri 親キー
	 * @param feed 更新対象Entryリスト
	 * @param items 更新対象項目リスト
	 * @param distkeyItems 更新対象DISTKEY項目リスト
	 * @param isDelete 削除の場合true
	 * @param serviceName サービス名
	 * @param reflexContext ReflexContext
	 */
	public void putIndexPartially(String parentUri, FeedBase feed, List<String> items,
			List<String> distkeyItems, boolean isDelete,
			String serviceName, ReflexContext reflexContext)
	throws IOException, TaggingException {
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		List<EntryBase> tmpIndexInfos = new ArrayList<>();
		for (EntryBase entry : feed.entry) {
			// Entryを1件ずつ読み、指定された項目に値がある場合はインデックス登録を行う。
			String uri = TaggingEntryUtil.getUriByParent(parentUri, entry);
			try {
				List<InnerIndex> indexes = BDBClientIndexUtil.getFullTextIndexesByUri(uri, entry,
						items, serviceName, requestInfo);
				List<Category> distkeys = BDBClientIndexUtil.getDistkeysByUri(uri, entry,
						distkeyItems, serviceName, requestInfo);
				for (InnerIndex innerIndex : indexes) {
					tmpIndexInfos.add(createFullTextIndexInfo(innerIndex, distkeys, isDelete));
				}
			} catch (IllegalParameterException e) {
				String msg = e.getMessage();
				// "The item name is not defined. "エラーであれば読み飛ばす。その他はスローする。
				if (msg == null || !msg.startsWith(BDBClientIndexUtil.MSG_NOT_DEFINED_PREFIX)) {
					throw e;
				}
			}
		}

		// 部分更新
		List<EntryBase> innerIndexInfos = null;
		List<EntryBase> deleteIndexInfos = null;
		if (isDelete) {
			deleteIndexInfos = tmpIndexInfos;
		} else {
			innerIndexInfos = tmpIndexInfos;
		}
		putIndex(innerIndexInfos, deleteIndexInfos, true, BDBIndexType.FULLTEXT, reflexContext);
	}

	/**
	 * 全文検索インデックスの環境クローズ
	 * @param reflexContext ReflexContext
	 * @return リクエストを送った場合true、対象サーバが無い場合false
	 */
	public boolean closeFullTextIndex(ReflexContext reflexContext)
	throws IOException, TaggingException {
		return closeIndex(BDBIndexType.FULLTEXT, reflexContext);
	}

}
