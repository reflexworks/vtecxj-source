package jp.reflexworks.taggingservice.migrate;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.ConsistentHash;

/**
 * サービスステータス更新に伴うデータ移行　Entryサーバごとの処理.
 */
public class MigrateMinForEachMnfServerCallable extends ReflexCallable<Boolean> {

	/** 対象サービス名 */
	private String serviceName;
	/** 旧名前空間 */
	private String oldNamespace;
	/** 旧Manifestサーバ情報Entry */
	private EntryBase mnfOldServerEntry;
	/** 旧EntryサーバURL ConsistentHash */
	private ConsistentHash<String> entryOldConsistentHash;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param serviceName 対象サービス名
	 * @param oldNamespace 旧名前空間
	 * @param mnfOldServerEntry 旧Manifestサーバ情報Entry
	 * @param entryOldServerUrls 旧EntryサーバURLリスト
	 * @param entryOldConsistentHash 旧EntryサーバURL ConsistentHash
	 */
	MigrateMinForEachMnfServerCallable(String serviceName, String oldNamespace,
			EntryBase mnfOldServerEntry, ConsistentHash<String> entryOldConsistentHash) {
		this.serviceName = serviceName;
		this.oldNamespace = oldNamespace;
		this.mnfOldServerEntry = mnfOldServerEntry;
		this.entryOldConsistentHash = entryOldConsistentHash;
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
			sb.append("[migrate for each entry server call] start. ");
			sb.append(getEntryServer());
			logger.debug(sb.toString());
		}

		try {
			MigrateMinimumManager migreateMinManager = new MigrateMinimumManager();
			migreateMinManager.migrateForEachMnfServer(serviceName, oldNamespace,
					mnfOldServerEntry, entryOldConsistentHash, getAuth(),
					requestInfo, getConnectionInfo());

			if (logger.isTraceEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[migrate for each entry server call] end. ");
				sb.append(getEntryServer());
				logger.debug(sb.toString());
			}
			return true;

		} finally {
		}
	}

	/**
	 * このクラスのManifestサーバ情報(ログ用)
	 * @return このクラスのManifestサーバ情報
	 */
	private String getEntryServer() {
		StringBuilder sb = new StringBuilder();
		String oldServerMnfName = TaggingEntryUtil.getSelfidUri(mnfOldServerEntry.getMyUri());
		sb.append("serviceName=");
		sb.append(serviceName);
		sb.append(", oldNamespace=");
		sb.append(oldNamespace);
		sb.append(", oldServerMnfName=");
		sb.append(oldServerMnfName);
		return sb.toString();
	}

}
