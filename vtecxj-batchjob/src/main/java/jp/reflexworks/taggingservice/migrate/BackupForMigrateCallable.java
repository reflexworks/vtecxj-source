package jp.reflexworks.taggingservice.migrate;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBServerType;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.util.LogUtil;

/**
 * データ移行の前のBDBバックアップ 並列実行のための非同期処理.
 */
public class BackupForMigrateCallable extends ReflexCallable<Boolean> {

	/** BDBサーバ名 */
	private String bdbServerUrl;
	/** BDBサーバ名 */
	private BDBServerType bdbServerType;
	/** BDBサーバ名 */
	private String bdbServerName;
	/** 現在日時文字列 */
	private String datetimeStr;
	/** 名前空間 */
	private String namespace;
	/** 対象サービス名 */
	private String serviceName;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param bdbServerUrl BDBサーバURL
	 * @param bdbServerType BDBサーバタイプ
	 * @param bdbServerName BDBサーバ名
	 * @param datetimeStr 現在日時文字列
	 * @param namespace 名前空間
	 * @param serviceName サービス名
	 */
	BackupForMigrateCallable(String bdbServerUrl, BDBServerType bdbServerType,
			String bdbServerName, String datetimeStr, String namespace, String serviceName) {
		this.bdbServerUrl = bdbServerUrl;
		this.bdbServerType = bdbServerType;
		this.bdbServerName = bdbServerName;
		this.datetimeStr = datetimeStr;
		this.namespace = namespace;
		this.serviceName = serviceName;
	}

	/**
	 * データ移行の前のBDBバックアップ処理.
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		if (logger.isDebugEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[backup for migrate] start. bdbServerName=" + bdbServerName);
		}

		BackupForMigrateUtil.backupBDBProc(bdbServerUrl, bdbServerType, bdbServerName,
				datetimeStr, namespace, serviceName, getAuth(), requestInfo, getConnectionInfo());

		if (logger.isDebugEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[backup for migrate] end. bdbServerName=" + bdbServerName);
		}

		return true;
	}

}
