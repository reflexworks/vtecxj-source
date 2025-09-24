package jp.reflexworks.taggingservice.migrate;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBIndexType;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBServerType;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.sourceforge.reflex.util.ConsistentHash;

/**
 * サーバ追加・削除処理.
 * 追加サーバごとの処理
 */
public class MaintenanceForEachServerCallable extends ReflexCallable<Boolean> {

	/** 対象サービス名 */
	private String serviceName;
	/** サーバタイプ */
	private BDBServerType serverType;
	/** 対象サーバURL */
	private String sourceServerUrl;
	/** 新サーバURLリスト */
	private List<String> newServerUrls;
	/** 旧サーバURLリスト */
	//private List<String> oldServerUrls;
	/** 旧サーバURL振り分けConsistentHash */
	private ConsistentHash<String> oldConsistentHash;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param serviceName 対象サービス名
	 * @param serverType サーバタイプ
	 * @param sourceServerUrl 対象サーバURL
	 * @param newServerUrls 新サーバURLリスト
	 * @param oldConsistentHash 旧サーバURL ConsistentHash
	 */
	MaintenanceForEachServerCallable(String serviceName, BDBServerType serverType,
			String sourceServerUrl, List<String> newServerUrls,
			ConsistentHash<String> oldConsistentHash) {
		this.serviceName = serviceName;
		this.serverType = serverType;
		this.sourceServerUrl = sourceServerUrl;
		this.newServerUrls = newServerUrls;
		this.oldConsistentHash = oldConsistentHash;
	}

	/**
	 * サーバ追加処理.
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		if (logger.isDebugEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
					"[maintenance for each server call] start. serverType=" + serverType.name());
		}

		try {
			// getAuth() は対象サービスの認証情報
			MaintenanceManager maintenanceManager = new MaintenanceManager();
			if (BDBServerType.ENTRY.equals(serverType)) {
				maintenanceManager.maintenanceEntryServer(serviceName, sourceServerUrl,
						newServerUrls, getAuth(), requestInfo, getConnectionInfo());
			} else if (BDBServerType.INDEX.equals(serverType)) {
				maintenanceManager.maintenanceIndexServer(serviceName, BDBIndexType.INDEX,
						sourceServerUrl, newServerUrls, oldConsistentHash, getAuth(),
						requestInfo, getConnectionInfo());
			} else if (BDBServerType.FULLTEXT.equals(serverType)) {
				maintenanceManager.maintenanceIndexServer(serviceName, BDBIndexType.FULLTEXT,
						sourceServerUrl, newServerUrls, oldConsistentHash, getAuth(),
						requestInfo, getConnectionInfo());
			} else if (BDBServerType.ALLOCIDS.equals(serverType)) {
				maintenanceManager.maintenanceAlServer(serviceName, sourceServerUrl,
						newServerUrls, getAuth(), requestInfo, getConnectionInfo());
			} else {
				logger.warn(LogUtil.getRequestInfoStr(requestInfo) +
						"[maintenance for each server call] server type is not valid. " + serverType);
			}
			return true;

		} finally {
			if (logger.isDebugEnabled()) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
						"[maintenance for each server call] end. serverType=" + serverType.name());
			}
		}
	}

}
