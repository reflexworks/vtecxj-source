package jp.reflexworks.taggingservice.bdbclient;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.index.FullTextIndexPutCallable;
import jp.reflexworks.taggingservice.index.InnerIndexPutCallable;
import jp.reflexworks.taggingservice.model.UpdatedInfo;
import jp.reflexworks.taggingservice.plugin.CallingAfterCommit;
import jp.reflexworks.taggingservice.service.BDBClientInitMainThreadAfterCommitCallable;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.taskqueue.TaskQueueUtil;
import jp.reflexworks.taggingservice.util.LogUtil;

/**
 * データ登録後の非同期処理.
 */
public class AfterCommitCallable extends ReflexCallable<Boolean> {

	/** 更新情報リスト */
	private List<UpdatedInfo> updatedInfos;
	/** 実行元サービス名 */
	private String originalServiceName;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param updatedInfos 更新情報リスト
	 * @param originalServiceName 実行元サービス名
	 */
	public AfterCommitCallable(List<UpdatedInfo> updatedInfos, String originalServiceName) {
		this.updatedInfos = updatedInfos;
		this.originalServiceName = originalServiceName;
	}

	/**
	 * 非同期処理登録.
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param sharingConnectionInfo 共有部分のみのコネクション情報 (ReadEntryMapを利用)
	 * @return Future
	 */
	public Future<Boolean> addTask(ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo sharingConnectionInfo)
	throws IOException, TaggingException {
		return (Future<Boolean>)TaskQueueUtil.addTask(this, 0, auth, requestInfo,
				sharingConnectionInfo);
	}

	/**
	 * コミット後の非同期処理.
	 * @return true/false
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		ReflexContext reflexContext = (ReflexContext)getReflexContext();
		ReflexAuthentication auth = getAuth();
		RequestInfo requestInfo = getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();

		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
					"[afterCommit call] start.");
		}

		// 古いリビジョンのEntry削除
		BDBClientDeletePrevEntryCallable deletePrevEntryCallable =
				new BDBClientDeletePrevEntryCallable(updatedInfos);
		deletePrevEntryCallable.addTask(auth, requestInfo, connectionInfo);

		// インデックス登録更新スレッド実行
		InnerIndexPutCallable innerIndexPutCallable = new InnerIndexPutCallable(
				updatedInfos);
		innerIndexPutCallable.addTask(auth, requestInfo, connectionInfo);

		// 全文検索インデックス登録更新スレッド実行
		FullTextIndexPutCallable fullTextIndexPutCallable = new FullTextIndexPutCallable(
				updatedInfos);
		fullTextIndexPutCallable.addTask(auth, requestInfo, connectionInfo);

		// リクエスト・メインスレッドキャッシュの更新スレッド実行
		BDBClientInitMainThreadAfterCommitCallable initMainThreadCallable =
				new BDBClientInitMainThreadAfterCommitCallable(updatedInfos,
						originalServiceName);
		initMainThreadCallable.addTask(auth, requestInfo, connectionInfo);
		
		// 削除Entryのコンテンツ削除
		BDBClientDeleteContentCallable deleteContentCallable = 
				new BDBClientDeleteContentCallable(updatedInfos);
		deleteContentCallable.addTask(auth, requestInfo, connectionInfo);
		
		// エントリー更新後に呼び出されるプラグイン
		List<CallingAfterCommit> callingAfterCommitList = TaggingEnvUtil.getCallingAfterCommitList();
		if (callingAfterCommitList != null) {
			for (CallingAfterCommit callingAfterCommit : callingAfterCommitList) {
				AfterCommitPluginCallable pluginCallable = 
						new AfterCommitPluginCallable(updatedInfos, callingAfterCommit);
				pluginCallable.addTask(auth, requestInfo, connectionInfo);
			}
		}
		return true;
	}

}
