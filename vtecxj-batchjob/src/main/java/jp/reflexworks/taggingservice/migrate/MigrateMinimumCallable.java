package jp.reflexworks.taggingservice.migrate;

import java.io.IOException;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.taskqueue.TaskQueueUtil;
import jp.reflexworks.taggingservice.util.LogUtil;

/**
 * サービスステータス変更に伴うのBDB割り当て変更.
 */
public class MigrateMinimumCallable extends ReflexCallable<Boolean> {

	/** 更新対象サービス名 */
	private String targetServiceName;
	/** サービスステータス */
	private String newServiceStatus;
	/** サービスエントリー */
	private EntryBase serviceEntry;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param targetServiceName 更新対象サービス名
	 * @param newServiceStatus サービスステータス
	 * @param serviceEntry サービスエントリー
	 */
	public MigrateMinimumCallable(String targetServiceName, String newServiceStatus,
			EntryBase serviceEntry) {
		this.targetServiceName = targetServiceName;
		this.newServiceStatus = newServiceStatus;
		this.serviceEntry = serviceEntry;
	}

	/**
	 * 非同期処理登録.
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報 (スレッド間共有オブジェクトを抽出するため設定)
	 * @return Future
	 */
	public Future<Boolean> addTask(ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// TaggingExceptionをログ出力する
		return (Future<Boolean>)TaskQueueUtil.addTask(this, true, 0, auth,
				requestInfo, connectionInfo);
	}

	/**
	 * サービスステータス変更に伴うのBDB割り当て変更処理.
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		if (logger.isDebugEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[change service status] start. newServiceStatus=" + newServiceStatus);
		}

		MigrateMinimumManager migrateMinManager = new MigrateMinimumManager();
		migrateMinManager.assignAndMigrateMinimum(targetServiceName, newServiceStatus,
				serviceEntry, getAuth(), requestInfo, getConnectionInfo());

		if (logger.isDebugEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[change service status] end. newServiceStatus=" + newServiceStatus);
		}

		return true;
	}

}
