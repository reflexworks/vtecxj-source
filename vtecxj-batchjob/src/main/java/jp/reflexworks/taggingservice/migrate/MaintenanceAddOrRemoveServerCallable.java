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
 * サーバ追加・削除処理.
 */
public class MaintenanceAddOrRemoveServerCallable extends ReflexCallable<Boolean> {

	/** 対象サービス名 */
	private String serviceName;
	/** サービスエントリー */
	private EntryBase serviceEntry;
	/** メンテナンスステータス */
	private String maintenanceStatus;
	/** サーバ削除の場合true */
	private boolean isRemove;
	/** 新サーバリスト キー:サーバタイプ、値:新サーバ名リスト */
	private Map<BDBServerType, List<String>> newServersMap;
	/** 現在の割り当てサーバリスト キー:サーバタイプ、値:現在のサーバ名リスト */
	private Map<BDBServerType, List<String>> currentServersMap;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param serviceName 対象サービス名
	 * @param serviceEntry サービスエントリー
	 * @param maintenanceStatus メンテナンスステータス
	 * @param isRemove サーバ削除の場合true
	 * @param newServersMap 新サーバリスト
	 * @param currentServersMap 現在の割り当てサーバリスト
	 */
	MaintenanceAddOrRemoveServerCallable(String serviceName, EntryBase serviceEntry,
			String maintenanceStatus, boolean isRemove, Map<BDBServerType,
			List<String>> newServersMap, Map<BDBServerType, List<String>> currentServersMap) {
		this.serviceName = serviceName;
		this.serviceEntry = serviceEntry;
		this.maintenanceStatus = maintenanceStatus;
		this.isRemove = isRemove;
		this.newServersMap = newServersMap;
		this.currentServersMap = currentServersMap;
	}

	/**
	 * サーバ追加・削除に伴うデータ移行処理.
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		String name = null;
		if (isRemove) {
			name = "[maintenance remove server call] ";
		} else {
			name = "[maintenance add server call] ";
		}
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) + name + "start.");
		}

		try {
			MaintenanceManager maintenanceManager = new MaintenanceManager();
			maintenanceManager.maintenanceAddOrRemoveServer(serviceName, serviceEntry,
					maintenanceStatus, isRemove, newServersMap, currentServersMap, getAuth(),
					requestInfo, getConnectionInfo());
			return true;

		} finally {
			if (logger.isTraceEnabled()) {
				logger.trace(LogUtil.getRequestInfoStr(requestInfo) + name + "end.");
			}
		}
	}

}
