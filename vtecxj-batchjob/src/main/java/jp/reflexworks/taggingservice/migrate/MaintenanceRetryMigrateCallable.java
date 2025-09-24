package jp.reflexworks.taggingservice.migrate;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBServerType;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.util.LogUtil;

/**
 * サーバ追加・削除のデータ移行リトライ処理.
 */
public class MaintenanceRetryMigrateCallable extends ReflexCallable<Boolean> {

	/** 対象サービス名 */
	private String serviceName;
	/** サービスエントリー */
	private EntryBase serviceEntry;
	/** メンテナンスステータス */
	private String maintenanceStatus;
	/** 現在の割り当てサーバリスト (新サーバリスト) */
	private Map<BDBServerType, List<String>> newServersMap;
	/** バックアップサーバリスト (旧サーバリスト) */
	private Map<BDBServerType, List<String>> backupServersMap;
	/** サーバ削除の場合true */
	private boolean isRemove;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param serviceName 対象サービス名
	 * @param serviceEntry サービスエントリー
	 * @param maintenanceStatus メンテナンスステータス
	 * @param newServersMap 現在の割り当てサーバリスト(新サーバリスト)
	 * @param backupServersMap バックアップサーバリスト(旧サーバリスト)
	 * @param isRemove サーバ削除の場合true
	 */
	MaintenanceRetryMigrateCallable(String serviceName, EntryBase serviceEntry,
			String maintenanceStatus, Map<BDBServerType, List<String>> newServersMap,
			Map<BDBServerType, List<String>> backupServersMap,
			boolean isRemove) {
		this.serviceName = serviceName;
		this.serviceEntry = serviceEntry;
		this.maintenanceStatus = maintenanceStatus;
		this.newServersMap = newServersMap;
		this.backupServersMap = backupServersMap;
		this.isRemove = isRemove;
	}

	/**
	 * サーバ追加・削除のデータ移行リトライ処理.
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
					"[maintenance retry migrate call] start.");
		}

		try {
			MaintenanceManager maintenanceManager = new MaintenanceManager();
			maintenanceManager.maintenanceRetryMigrate(serviceName, serviceEntry,
					maintenanceStatus, newServersMap, backupServersMap, isRemove,
					getAuth(), requestInfo, getConnectionInfo());
			return true;

		} finally {
			if (logger.isTraceEnabled()) {
				logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
						"[maintenance retry migrate call] end.");
			}
		}
	}

}
