package jp.reflexworks.taggingservice.migrate;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.util.LogUtil;

/**
 * サービスステータス更新に伴うデータ移行　Entryごとの処理.
 */
public class MigrateMinForEachEntryCallable extends ReflexCallable<Boolean> {

	/** 対象サービス名 */
	private String serviceName;
	/** ID */
	private String id;
	/** 旧EntryサーバのURL */
	private String oldServerEntryUrl;
	/** 旧名前空間 */
	private String oldNamespace;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param serviceName 対象サービス名
	 * @param id ID
	 * @param oldServerEntryUrl 旧EntryサーバのURL
	 */
	MigrateMinForEachEntryCallable(String serviceName, String id,
			String oldServerEntryUrl, String oldNamespace) {
		this.serviceName = serviceName;
		this.id = id;
		this.oldServerEntryUrl = oldServerEntryUrl;
		this.oldNamespace = oldNamespace;
	}

	/**
	 * Entryサーバごとのデータ移行処理.
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[migrate for each entry by change serviceStatus call] start. ");
			sb.append(getEntryInfo());
			logger.debug(sb.toString());
		}

		try {
			MigrateMinimumManager migreateMinManager = new MigrateMinimumManager();
			migreateMinManager.migrateForEachEntry(serviceName, id, oldServerEntryUrl,
					oldNamespace, getAuth(), requestInfo, getConnectionInfo());
			if (logger.isTraceEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[migrate for each entry by change serviceStatus call] end. ");
				sb.append(getEntryInfo());
				logger.debug(sb.toString());
			}
			return true;

		} finally {
		}
	}

	/**
	 * このクラスのEntry情報(ログ用)
	 * @return このクラスのEntry情報
	 */
	private String getEntryInfo() {
		StringBuilder sb = new StringBuilder();
		sb.append("serviceName=");
		sb.append(serviceName);
		sb.append(", oldNamespace=");
		sb.append(oldNamespace);
		sb.append(", oldServerEntryUrl=");
		sb.append(oldServerEntryUrl);
		sb.append(", ID=");
		sb.append(id);
		return sb.toString();
	}

}
