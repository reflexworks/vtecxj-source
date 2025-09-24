package jp.reflexworks.taggingservice.requester;

import java.io.IOException;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.taskqueue.TaskQueueUtil;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBServerType;

/**
 * BDBサーバ情報取得処理.
 */
public class BDBClientSettingServerCallable extends ReflexCallable<Boolean> {

	/** 対象サービス名 */
	private String serviceName;
	/** サーバタイプ */
	private BDBServerType serverType;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param serviceName 対象サービス名
	 * @param serverType サーバタイプ
	 */
	public BDBClientSettingServerCallable(String serviceName, BDBServerType serverType) {
		this.serviceName = serviceName;
		this.serverType = serverType;
	}

	/**
	 * 非同期処理登録.
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報 (ReadEntryMapを利用)
	 * @return Future
	 */
	public Future<Boolean> addTask(ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		return (Future<Boolean>)TaskQueueUtil.addTask(this, 0, auth, requestInfo, connectionInfo);
	}

	/**
	 * BDBサーバ情報取得処理.
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		ConnectionInfo connectionInfo = getConnectionInfo();
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
					"[Setting BDB server call] start.");
		}

		try {
			// 取得処理を行い、Static情報を更新するのが目的。
			BDBClientServerManager serverManager = new BDBClientServerManager();
			if (BDBServerType.MANIFEST.equals(serverType)) {
				serverManager.getMnfServerUrl(serviceName, requestInfo, connectionInfo);
			} else if (BDBServerType.ENTRY.equals(serverType)) {
				serverManager.getEntryServerUrls(serviceName, requestInfo, connectionInfo);
			} else if (BDBServerType.INDEX.equals(serverType)) {
				serverManager.getIdxServerUrls(serviceName, requestInfo, connectionInfo);
			} else if (BDBServerType.FULLTEXT.equals(serverType)) {
				serverManager.getFtServerUrls(serviceName, requestInfo, connectionInfo);
			} else if (BDBServerType.ALLOCIDS.equals(serverType)) {
				serverManager.getAlServerUrls(serviceName, requestInfo, connectionInfo);
			}
			return true;

		} finally {
			if (logger.isTraceEnabled()) {
				logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
						"[Setting BDB server call] end.");
			}
		}
	}

}
