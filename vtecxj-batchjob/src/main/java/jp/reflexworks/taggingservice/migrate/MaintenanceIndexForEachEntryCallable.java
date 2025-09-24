package jp.reflexworks.taggingservice.migrate;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBIndexType;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.sourceforge.reflex.util.ConsistentHash;

/**
 * インデックスサーバ追加・削除に伴うデータ移行　Entryごとの処理.
 */
public class MaintenanceIndexForEachEntryCallable extends ReflexCallable<Boolean> {

	/** 対象サービス名 */
	private String serviceName;
	/** ID URI */
	private String idUri;
	/** インデックスタイプ */
	private BDBIndexType indexType;
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
	 * @param idUri ID URI
	 * @param indexType インデックスタイプ
	 * @param sourceServerUrl 対象サーバURL
	 * @param newServerUrls 新サーバURLリスト
	 * @param oldConsistentHash 旧サーバURL振り分けConsistentHash
	 */
	MaintenanceIndexForEachEntryCallable(String serviceName, String idUri, BDBIndexType indexType,
			String sourceServerUrl, List<String> newServerUrls,
			ConsistentHash<String> oldConsistentHash) {
		this.serviceName = serviceName;
		this.idUri = idUri;
		this.indexType = indexType;
		this.sourceServerUrl = sourceServerUrl;
		this.newServerUrls = newServerUrls;
		this.oldConsistentHash = oldConsistentHash;
	}

	/**
	 * インデックスサーバごと・Entryごとのデータ移行処理.
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
					"[maintenance index server for each entry call] start.");
		}

		try {
			MaintenanceManager maintenanceManager = new MaintenanceManager();
			maintenanceManager.maintenanceIndexForEachEntry(serviceName, idUri, indexType,
					sourceServerUrl, newServerUrls, oldConsistentHash, getAuth(),
					requestInfo, getConnectionInfo());
			return true;

		} finally {
			if (logger.isTraceEnabled()) {
				logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
						"[maintenance index server for each entry call] end.");
			}
		}
	}

}
