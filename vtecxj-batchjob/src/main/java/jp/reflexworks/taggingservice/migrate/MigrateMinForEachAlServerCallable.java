package jp.reflexworks.taggingservice.migrate;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.util.LogUtil;

/**
 * サービスステータス更新に伴うデータ移行　採番・カウンタサーバごとの処理.
 */
public class MigrateMinForEachAlServerCallable extends ReflexCallable<Boolean> {

	/** 対象サービス名 */
	private String serviceName;
	/** 旧名前空間 */
	private String oldNamespace;
	/** 旧採番・カウンタサーバ情報Entry */
	private EntryBase alOldServerEntry;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param serviceName 対象サービス名
	 * @param oldNamespace 旧名前空間
	 * @param alOldServerEntry 旧採番・カウンタサーバ情報Entry
	 */
	MigrateMinForEachAlServerCallable(String serviceName, String oldNamespace,
			EntryBase alOldServerEntry) {
		this.serviceName = serviceName;
		this.oldNamespace = oldNamespace;
		this.alOldServerEntry = alOldServerEntry;
	}

	/**
	 * 採番・カウンタサーバごとのデータ移行処理.
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
					"[migrate for each entry server call] start.");
		}

		try {
			MigrateMinimumManager migreateMinManager = new MigrateMinimumManager();
			migreateMinManager.migrateForEachAlServer(serviceName, oldNamespace, alOldServerEntry,
					getAuth(), requestInfo, getConnectionInfo());
			return true;

		} finally {
			if (logger.isTraceEnabled()) {
				logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
						"[migrate for each entry server call] end.");
			}
		}
	}

}
