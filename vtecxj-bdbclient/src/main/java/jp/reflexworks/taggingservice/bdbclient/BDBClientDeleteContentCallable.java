package jp.reflexworks.taggingservice.bdbclient;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.UpdatedInfo;
import jp.reflexworks.taggingservice.plugin.ContentManager;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.taskqueue.TaskQueueUtil;
import jp.reflexworks.taggingservice.util.Constants.OperationType;
import jp.reflexworks.taggingservice.util.LogUtil;

/**
 * データコミット後の非同期処理.
 * 削除Entryのコンテンツ削除処理
 */
public class BDBClientDeleteContentCallable extends ReflexCallable<Boolean> {

	/** 更新情報リスト */
	private List<UpdatedInfo> updatedInfos;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param updatedInfos 更新情報リスト
	 */
	public BDBClientDeleteContentCallable(List<UpdatedInfo> updatedInfos) {
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

		// 削除Entryのコンテンツ削除処理を実装する。
		deleteContents();
		return true;
	}

	/**
	 * 更新前Entryの削除処理.
	 */
	private void deleteContents()
	throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		ConnectionInfo connectionInfo = getConnectionInfo();
		ReflexAuthentication auth = getAuth();

		ContentManager contentManager = TaggingEnvUtil.getContentManager();
		SystemContext systemContext = new SystemContext(auth, requestInfo, connectionInfo);
		IOException ie = null;
		TaggingException te = null;
		for (UpdatedInfo updatedInfo : updatedInfos) {
			if (updatedInfo.getFlg() != OperationType.DELETE) {
				continue;
			}
			EntryBase prevEntry = updatedInfo.getPrevEntry();
			try {
				// コンテンツが登録されていたかどうかは、contentManager内で判断する。
				contentManager.afterDeleteEntry(prevEntry, systemContext);
				
			// エラー発生時は、一旦全ての対象データを処理する。最後に例外をスローする。
			} catch (IOException e) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[deleteContents] Error occured. ");
				sb.append(e.getMessage());
				sb.append(" id=");
				sb.append(prevEntry.id);
				logger.warn(sb.toString(), e);
				if (ie == null) {
					ie = e;
				}
			} catch (TaggingException e) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[deleteContents] Error occured. ");
				sb.append(e.getMessage());
				sb.append(" id=");
				sb.append(prevEntry.id);
				logger.warn(sb.toString(), e);
				if (te == null) {
					te = e;
				}
			}
		}
		if (ie != null) {
			throw ie;
		}
		if (te != null) {
			throw te;
		}
	}

}
