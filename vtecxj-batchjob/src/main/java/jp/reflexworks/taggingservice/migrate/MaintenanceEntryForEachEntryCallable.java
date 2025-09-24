package jp.reflexworks.taggingservice.migrate;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.util.LogUtil;

/**
 * Entryサーバ追加・削除に伴うデータ移行　Entryごとの処理.
 */
public class MaintenanceEntryForEachEntryCallable extends ReflexCallable<Boolean> {

	/** 対象サービス名 */
	private String serviceName;
	/** ID */
	private String id;
	/** 旧サーバのURL */
	private String oldServerUrl;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param serviceName 対象サービス名
	 * @param id ID
	 * @param oldServerUrl 旧サーバのURL
	 */
	MaintenanceEntryForEachEntryCallable(String serviceName, String id,
			String oldServerUrl) {
		this.serviceName = serviceName;
		this.id = id;
		this.oldServerUrl = oldServerUrl;
	}

	/**
	 * Entryごとのデータ移行処理.
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
					"[maintenance entry server for each entry call] start.");
		}

		try {
			MaintenanceManager maintenanceManager = new MaintenanceManager();
			maintenanceManager.maintenanceEntryForEachEntry(serviceName, id,
					oldServerUrl, getAuth(), requestInfo, getConnectionInfo());
			return true;

		} finally {
			if (logger.isTraceEnabled()) {
				logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
						"[maintenance entry server for each entry call] end.");
			}
		}
	}

}
