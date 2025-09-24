package jp.reflexworks.taggingservice.migrate;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.util.LogUtil;

/**
 * 採番・カウンタサーバ追加・削除に伴うデータ移行　採番ごとの処理.
 */
public class MaintenanceAlForEachAllocidsCallable extends ReflexCallable<Boolean> {

	/** 対象サービス名 */
	private String serviceName;
	/** URI */
	private String uri;
	/** 旧サーバのURL */
	private String oldServerUrl;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param serviceName 対象サービス名
	 * @param uri URI
	 * @param oldServerUrl 旧サーバのURL
	 */
	MaintenanceAlForEachAllocidsCallable(String serviceName, String uri,
			String oldServerUrl) {
		this.serviceName = serviceName;
		this.uri = uri;
		this.oldServerUrl = oldServerUrl;
	}

	/**
	 * 採番ごとのデータ移行処理.
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
					"[maintenance al server for each allocids call] start.");
		}

		try {
			MaintenanceManager maintenanceManager = new MaintenanceManager();
			maintenanceManager.maintenanceAlForEachAllocids(serviceName, uri,
					oldServerUrl, getAuth(), requestInfo, getConnectionInfo());
			return true;

		} finally {
			if (logger.isTraceEnabled()) {
				logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
						"[maintenance al server for each allocids call] end.");
			}
		}
	}

}
