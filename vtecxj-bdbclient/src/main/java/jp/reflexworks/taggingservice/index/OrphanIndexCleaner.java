package jp.reflexworks.taggingservice.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.servlet.util.UrlUtil;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBIndexType;
import jp.reflexworks.taggingservice.requester.BDBRequesterUtil;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.DateUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * 孤立インデックスの削除.
 * <p>
 * Entryが存在しない(Manifestが無い)にもかかわらず、インデックス・全文検索インデックスサーバに
 * 残っているインデックスを削除する。
 * Entry削除後の非同期処理(インデックス削除)が実行されずにAPサーバが停止した場合などに発生する。
 * インデックス再作成(_updateindex)の後処理として使用する。
 * </p>
 * <p>
 * 各サーバのAncestorテーブル(キー: ID URI)を親キーで前方一致検索し、
 * Entryが存在しないID URIのインデックスを、そのサーバに対して削除要求する。
 * 削除要求には各サーバの版情報テーブルに保存された版を設定するため、
 * 判定後に同じキーで再登録された場合は、インデックスサーバ側で削除がスキップされる。
 * </p>
 */
public class OrphanIndexCleaner {

	/** インデックスAncestorテーブル名 (Indexサーバ) */
	private static final String TABLE_INDEX_ANCESTOR = "DBInnerIndexAncestor";
	/** インデックス版情報テーブル名 (Indexサーバ) */
	private static final String TABLE_INDEX_VERSION = "DBInnerIndexVersion";
	/** 全文検索インデックスAncestorテーブル名 (全文検索サーバ) */
	private static final String TABLE_FULLTEXT_ANCESTOR = "DBFullTextIndexAncestor";
	/** 全文検索インデックス版情報テーブル名 (全文検索サーバ) */
	private static final String TABLE_FULLTEXT_VERSION = "DBFullTextIndexVersion";
	/** テーブル一覧の1回の取得件数 */
	private static final int LIST_LIMIT = 100;
	/** 1リクエストで削除するIDの最大数 */
	private static final int DELETE_BATCH_SIZE = 100;
	/** 版情報の区切り文字 */
	private static final String VERSION_DELIMITER = ",";
	/** 版情報が無い場合に設定するリビジョン (版の判定を行わないため値は使用されない) */
	private static final int DEFAULT_REVISION = 1;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 指定された親キー直下のEntryについて、孤立インデックスを削除する.
	 * @param parentUri 親キー
	 * @param reflexContext ReflexContext (システム権限)
	 * @return 削除要求したID URIの件数 (インデックス・全文検索インデックスの合計)
	 */
	public int deleteOrphanIndexes(String parentUri, ReflexContext reflexContext)
	throws IOException, TaggingException {
		int cnt = deleteOrphanIndexes(parentUri, BDBIndexType.INDEX,
				TABLE_INDEX_ANCESTOR, TABLE_INDEX_VERSION, reflexContext);
		cnt += deleteOrphanIndexes(parentUri, BDBIndexType.FULLTEXT,
				TABLE_FULLTEXT_ANCESTOR, TABLE_FULLTEXT_VERSION, reflexContext);
		return cnt;
	}

	/**
	 * 指定されたインデックス種別の各サーバについて、孤立インデックスを削除する.
	 * @param parentUri 親キー
	 * @param indexType インデックス種別
	 * @param ancestorTable Ancestorテーブル名
	 * @param versionTable 版情報テーブル名
	 * @param reflexContext ReflexContext
	 * @return 削除要求したID URIの件数
	 */
	private int deleteOrphanIndexes(String parentUri, BDBIndexType indexType,
			String ancestorTable, String versionTable, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();

		List<String> serverUrls = null;
		if (BDBIndexType.FULLTEXT.equals(indexType)) {
			serverUrls = BDBRequesterUtil.getFtServerUrls(serviceName, requestInfo, connectionInfo);
		} else {
			serverUrls = BDBRequesterUtil.getIdxServerUrls(serviceName, requestInfo, connectionInfo);
		}
		if (serverUrls == null || serverUrls.isEmpty()) {
			return 0;
		}

		String editParentUri = TaggingEntryUtil.removeLastSlash(parentUri);
		String keyprefix = editParentUri + "/";
		// Entryの存在確認結果 (サーバ間で共有) キー:ID URI、値:存在する場合true
		Map<String, Boolean> existsMap = new HashMap<>();

		int cnt = 0;
		for (String serverUrl : serverUrls) {
			// インデックスが登録されているID URI
			List<String> idUris = getKeys(serverUrl, ancestorTable, keyprefix, reflexContext);
			if (idUris.isEmpty()) {
				continue;
			}
			// 版情報 キー:ID URI、値:版情報文字列
			Map<String, String> versions = getKeyValues(serverUrl, versionTable, keyprefix,
					reflexContext);

			List<EntryBase> deleteInfos = new ArrayList<>();
			for (String idUri : idUris) {
				// 親キー直下のみ対象
				String tmpParentUri = TaggingEntryUtil.removeLastSlash(
						TaggingEntryUtil.getParentUri(idUri));
				if (!editParentUri.equals(tmpParentUri)) {
					continue;
				}
				Boolean exists = existsMap.get(idUri);
				if (exists == null) {
					exists = reflexContext.getEntry(idUri, false) != null;
					existsMap.put(idUri, exists);
				}
				if (exists) {
					continue;
				}
				deleteInfos.add(createDeleteInfo(idUri, versions.get(idUri)));
			}
			if (deleteInfos.isEmpty()) {
				continue;
			}

			if (logger.isInfoEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[deleteOrphanIndexes] indexType=");
				sb.append(indexType);
				sb.append(", serverUrl=");
				sb.append(serverUrl);
				sb.append(", parentUri=");
				sb.append(editParentUri);
				sb.append(", count=");
				sb.append(deleteInfos.size());
				logger.info(sb.toString());
			}
			requestDelete(serverUrl, deleteInfos, reflexContext);
			cnt += deleteInfos.size();
		}
		return cnt;
	}

	/**
	 * 削除要求用のインデックス情報を生成.
	 * @param idUri ID URI
	 * @param versionStr インデックスサーバに保存された版情報 ({updatedのエポックミリ秒},{リビジョン},{削除フラグ})
	 * @return 削除要求用のインデックス情報
	 */
	private EntryBase createDeleteInfo(String idUri, String versionStr) {
		int revision = DEFAULT_REVISION;
		String updated = null;
		if (!StringUtils.isBlank(versionStr)) {
			String[] parts = versionStr.split(VERSION_DELIMITER);
			if (parts.length == 3) {
				try {
					long updatedTime = Long.parseLong(parts[0]);
					revision = Integer.parseInt(parts[1]);
					// 保存された版と同じ版で削除する。判定後に再登録された場合はサーバ側でスキップされる。
					updated = DateUtil.getDateTimeMillisec(new Date(updatedTime),
							TimeZone.getDefault().getID());
				} catch (NumberFormatException e) {
					logger.warn("[createDeleteInfo] version is invalid. idUri=" + idUri +
							", version=" + versionStr);
				}
			}
		}
		IndexCommonManager indexCommonManager = new IndexCommonManager();
		EntryBase entry = indexCommonManager.createAtomEntry();
		entry.id = idUri + "," + revision;
		entry.updated = updated;
		return entry;
	}

	/**
	 * インデックスサーバに削除要求を送る.
	 * @param serverUrl インデックスサーバURL
	 * @param deleteInfos 削除要求用のインデックス情報
	 * @param reflexContext ReflexContext
	 */
	private void requestDelete(String serverUrl, List<EntryBase> deleteInfos,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		IndexCommonManager indexCommonManager = new IndexCommonManager();
		String putUrl = indexCommonManager.addPutParam(serverUrl, false, true);
		int size = deleteInfos.size();
		for (int i = 0; i < size; i += DELETE_BATCH_SIZE) {
			FeedBase feed = indexCommonManager.createAtomFeed();
			feed.entry = new ArrayList<>(deleteInfos.subList(i, Math.min(i + DELETE_BATCH_SIZE, size)));
			indexCommonManager.requestPut(putUrl, feed, reflexContext.getServiceName(),
					reflexContext.getRequestInfo(), reflexContext.getConnectionInfo());
		}
	}

	/**
	 * テーブルのキーを前方一致で全件取得.
	 * @param serverUrl インデックスサーバURL
	 * @param tableName テーブル名
	 * @param keyprefix キーの前方一致条件
	 * @param reflexContext ReflexContext
	 * @return キーリスト
	 */
	private List<String> getKeys(String serverUrl, String tableName, String keyprefix,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		return new ArrayList<>(getKeyValues(serverUrl, tableName, keyprefix, reflexContext).keySet());
	}

	/**
	 * テーブルのキーと値を前方一致で全件取得.
	 * インデックスサーバの _list (titleにキー、summaryに値) を使用する。
	 * @param serverUrl インデックスサーバURL
	 * @param tableName テーブル名
	 * @param keyprefix キーの前方一致条件
	 * @param reflexContext ReflexContext
	 * @return キー:テーブルのキー、値:テーブルの値(文字列)
	 */
	private Map<String, String> getKeyValues(String serverUrl, String tableName,
			String keyprefix, ReflexContext reflexContext)
	throws IOException, TaggingException {
		IndexCommonManager indexCommonManager = new IndexCommonManager();
		Map<String, String> keyValues = new LinkedHashMap<>();
		String cursorStr = null;
		do {
			String requestUri = "/";
			requestUri = UrlUtil.addParam(requestUri, RequestParam.PARAM_LIST, tableName);
			requestUri = UrlUtil.addParam(requestUri, RequestParam.PARAM_KEYPREFIX, keyprefix);
			requestUri = UrlUtil.addParam(requestUri, RequestParam.PARAM_LIMIT,
					String.valueOf(LIST_LIMIT));
			if (!StringUtils.isBlank(cursorStr)) {
				requestUri = UrlUtil.addParam(requestUri, RequestParam.PARAM_NEXT, cursorStr);
			}
			FeedBase feed = indexCommonManager.requestGet(serverUrl, requestUri, null, null, null,
					reflexContext.getServiceName(), reflexContext.getRequestInfo(),
					reflexContext.getConnectionInfo());
			cursorStr = TaggingEntryUtil.getCursorFromFeed(feed);
			if (feed != null && feed.entry != null) {
				for (EntryBase entry : feed.entry) {
					if (!StringUtils.isBlank(entry.title)) {
						keyValues.put(entry.title, entry.summary);
					}
				}
			}
		} while (!StringUtils.isBlank(cursorStr));
		return keyValues;
	}

}
