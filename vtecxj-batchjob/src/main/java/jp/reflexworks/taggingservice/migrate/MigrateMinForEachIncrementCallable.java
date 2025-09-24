package jp.reflexworks.taggingservice.migrate;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.util.LogUtil;

/**
 * サービスステータス更新に伴うデータ移行　加算データごとの処理.
 */
public class MigrateMinForEachIncrementCallable extends ReflexCallable<Boolean> {

	/** 対象サービス名 */
	private String serviceName;
	/** URI */
	private String uri;
	/** 旧名前空間 */
	private String oldNamespace;
	/** 旧採番・カウンタサーバのURL */
	private String oldServerAlUrl;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param serviceName 対象サービス名
	 * @param oldNamespace 旧名前空間
	 * @param uri URI
	 * @param oldServerAlUrl 旧採番・カウンタサーバのURL
	 */
	MigrateMinForEachIncrementCallable(String serviceName, String oldNamespace, String uri,
			String oldServerAlUrl) {
		this.serviceName = serviceName;
		this.uri = uri;
		this.oldNamespace = oldNamespace;
		this.oldServerAlUrl = oldServerAlUrl;
	}

	/**
	 * 加算データごとのデータ移行処理.
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
					"[migrate for each increment by change serviceStatus call] start.");
		}

		try {
			MigrateMinimumManager migreateMinManager = new MigrateMinimumManager();
			migreateMinManager.migrateForEachIncrement(serviceName, oldNamespace, uri,
					oldServerAlUrl, getAuth(), requestInfo, getConnectionInfo());
			return true;

		} finally {
			if (logger.isTraceEnabled()) {
				logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
						"[migrate for each increment by change serviceStatus call] end.");
			}
		}
	}

}
