package jp.reflexworks.taggingservice.bdbclient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.UpdatedInfo;
import jp.reflexworks.taggingservice.requester.BDBRequesterUtil;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.taskqueue.TaskQueueUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.Constants.OperationType;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;

/**
 * データコミット後の非同期処理.
 * 更新前Entryの削除処理
 */
public class BDBClientDeletePrevEntryCallable extends ReflexCallable<Boolean> {

	/** 更新情報リスト */
	private List<UpdatedInfo> updatedInfos;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param updatedInfos 更新情報リスト
	 */
	public BDBClientDeletePrevEntryCallable(List<UpdatedInfo> updatedInfos) {
		this.updatedInfos = updatedInfos;
	}

	/**
	 * 非同期処理登録.
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報 (ReadEntryMap共有のため使用)
	 * @return Future
	 */
	public Future<Boolean> addTask(ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		return (Future<Boolean>)TaskQueueUtil.addTask(this, 0, auth, requestInfo, connectionInfo);
	}

	/**
	 * コミット後の非同期処理.
	 * 更新前Entryの削除処理
	 * @return true/false
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
					"[afterCommit call] start.");
		}

		// 更新前Entryの削除処理を実装する。
		deletePrevEntries();
		return true;
	}

	/**
	 * 更新前Entryの削除処理.
	 */
	private void deletePrevEntries()
	throws IOException, TaggingException {
		String serviceName = getServiceName();
		RequestInfo requestInfo = getRequestInfo();
		ConnectionInfo connectionInfo = getConnectionInfo();
		ReflexAuthentication auth = getAuth();

		String entryDeleteMethod = Constants.DELETE;
		String entryMultipleUri = BDBClientUtil.getEntryMultipleUri();
		Map<String, List<String>> urlDeleteIdsMap = new HashMap<>();
		for (UpdatedInfo updatedInfo : updatedInfos) {
			if (updatedInfo.getFlg() == OperationType.INSERT) {
				continue;
			}

			// ID URIでEntryサーバを振り分ける
			String currentId = updatedInfo.getPrevEntry().id;
			String entryServerUrl = BDBClientUtil.getEntryServerUrl(
					TaggingEntryUtil.getUriById(currentId), serviceName, requestInfo, connectionInfo);
			List<String> deleteIds = urlDeleteIdsMap.get(entryServerUrl);
			if (deleteIds == null) {
				deleteIds = new ArrayList<>();
				urlDeleteIdsMap.put(entryServerUrl, deleteIds);
			}
			deleteIds.add(currentId);
		}

		// 古いリビジョンのEntry削除
		// Entryサーバごとに非同期で処理する。
		int limit = BDBClientUtil.getEntryserverPutLimit();
		for (Map.Entry<String, List<String>> mapEntry : urlDeleteIdsMap.entrySet()) {
			String entryServerUrl = mapEntry.getKey();
			List<String> deleteIds = mapEntry.getValue();

			// Entryを一定数ごとに区切ってリクエストする。
			// リクエストヘッダのサイズ制限に引っかからないようにするため。(Header is too large)
			int size = deleteIds.size();
			int idx = 0;
			while (idx < size) {
				List<String> reqDeleteIds = null;
				int toIdx = 0;
				if (size - idx > limit) {
					toIdx = idx + limit;
				} else {
					toIdx = size;
				}
				reqDeleteIds = deleteIds.subList(idx, toIdx);
				idx = toIdx;

				Map<String, String> additionalHeaders = BDBRequesterUtil.getEntryMultipleHeader(
						reqDeleteIds, null);
				BDBClientUpdateEntriesCallable callable = new BDBClientUpdateEntriesCallable(
						entryServerUrl, entryMultipleUri, entryDeleteMethod, null, additionalHeaders);
				callable.addTask(auth, requestInfo, connectionInfo);
			}
		}
	}

}
