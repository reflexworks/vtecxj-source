package jp.reflexworks.taggingservice.bdbclient;

import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.HierarchyFormatException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.requester.BDBRequesterUtil;
import jp.reflexworks.taggingservice.sys.SystemAuthentication;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;

/**
 * フォルダ削除非同期処理.
 */
public class BDBClientDeleteFolderCallable extends ReflexCallable<Boolean> {

	/** Entry */
	private EntryBase entry;
	/** URI */
	private String uri;
	/** 並列削除を行う場合true */
	private boolean isParallel;
	/** フォルダエントリー自体を削除しない場合true */
	private boolean noDeleteSelf;
	/** 削除対象ID URIリスト */
	private Map<String, String> deleteFolderIdUris;
	/** 実行元サービス名 */
	private String originalServiceName;
	/** システム認証情報 */
	private SystemAuthentication systemAuth;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param entry Entry
	 * @param uri URI
	 * @param noDeleteSelf フォルダエントリー自体を削除しない場合true
	 * @param isParallel 並列削除を行う場合true
	 * @param deleteFolderIdUris 削除対象ID URIリスト
	 * @param originalServiceName 実行元サービス名
	 * @param systemAuth システム認証情報
	 */
	public BDBClientDeleteFolderCallable(EntryBase entry, String uri, boolean noDeleteSelf, 
			boolean isParallel, Map<String, String> deleteFolderIdUris,
			String originalServiceName, SystemAuthentication systemAuth) {
		this.entry = entry;
		this.uri = uri;
		this.noDeleteSelf = noDeleteSelf;
		this.isParallel = isParallel;
		this.deleteFolderIdUris = deleteFolderIdUris;
		this.originalServiceName = originalServiceName;
		this.systemAuth = systemAuth;
	}

	/**
	 * フォルダ削除処理.
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		ConnectionInfo connectionInfo = getConnectionInfo();
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
					"[DeleteFolder call] start.");
		}

		deleteFolderProc(requestInfo, connectionInfo);

		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
					"[DeleteFolder call] end.");
		}
		return true;
	}

	/**
	 * フォルダ削除
	 * @param deleteFolderIdUris フォルダ削除対象ID URIリスト
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void deleteFolderProc(RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = systemAuth.getServiceName();
		// フォルダ削除
		BDBClientUpdateManager updateManager = new BDBClientUpdateManager();
		BDBClientRetrieveManager retrieveManager = new BDBClientRetrieveManager();
		FeedTemplateMapper mapper = TaggingEnvUtil.getResourceMapper(serviceName);

		// インデックス・全文検索インデックス更新はまとめて行う
		DeleteFolderIndexBuffer indexBuffer = new DeleteFolderIndexBuffer(systemAuth,
				requestInfo, connectionInfo);
		try {
			deleteFolderProc(updateManager, retrieveManager, mapper, indexBuffer,
					requestInfo, connectionInfo);
		} finally {
			// 削除済みEntryのインデックス更新を送信する (エラー時も削除済み分は送信する)
			try {
				indexBuffer.flush();
			} catch (IOException | TaggingException | RuntimeException e) {
				logger.warn(LogUtil.getRequestInfoStr(requestInfo) +
						"[deleteFolder] index flush failed. uri=" + uri + " " +
						e.getClass().getName() + ": " + e.getMessage(), e);
			}
		}
	}

	/**
	 * フォルダ削除 (リトライ処理)
	 * @param updateManager 更新処理
	 * @param retrieveManager 検索処理
	 * @param mapper FeedTemplateMapper
	 * @param indexBuffer インデックス更新をまとめて行うバッファ
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	private void deleteFolderProc(BDBClientUpdateManager updateManager,
			BDBClientRetrieveManager retrieveManager, FeedTemplateMapper mapper,
			DeleteFolderIndexBuffer indexBuffer,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// リトライ対応
		int numRetries = BDBRequesterUtil.getBDBRequestRetryCount();
		int waitMillis = BDBRequesterUtil.getBDBRequestRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				// リトライを考慮しコピーして処理する。
				EntryBase tmpEntry = TaggingEntryUtil.copyEntry(entry, mapper);
				updateManager.deleteFolder(tmpEntry, uri, noDeleteSelf, isParallel, 
						deleteFolderIdUris, retrieveManager, originalServiceName, 
						indexBuffer, systemAuth, requestInfo, connectionInfo);
				break;

			} catch (IOException e) {
				// リトライ判定、入力エラー判定
				BDBClientUtil.convertError(e, Constants.DELETE, requestInfo);
				if (r >= numRetries) {
					// リトライ対象だがリトライ回数を超えた場合
					throw e;
				}
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) +
							"[deleteFolder] " + BDBClientUtil.getRetryLog(e, r));
				}
				BDBClientUtil.sleep(waitMillis + r * 10);

			} catch (HierarchyFormatException e) {
				// "Can't delete for the child entries exist." の場合、リトライを行う。
				if (r >= numRetries) {
					// リトライ対象だがリトライ回数を超えた場合
					throw e;
				}
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) +
							"[deleteFolder] " + BDBClientUtil.getRetryLog(e, r));
				}
				BDBClientUtil.sleep(waitMillis + r * 10);
			}
		}

	}

}
