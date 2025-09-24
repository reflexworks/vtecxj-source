package jp.reflexworks.batch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexBlogic;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.ServiceManager;
import jp.reflexworks.taggingservice.sys.SystemAuthentication;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.taskqueue.TaskQueueUtil;
import jp.sourceforge.reflex.util.DateUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * ログエントリーをBigQueryに移動させる処理.
 */
public class MoveLogToBigQueryBlogic implements ReflexBlogic<ReflexContext, Boolean> {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 実行処理.
	 * @param reflexContext ReflexContext
	 * @param args 引数 (ReflexApplication実行時の引数の4番目以降)
	 *             [0]有効なサービス・名前空間マップファイル名(サービス名:名前空間)(フルパス)
	 */
	public Boolean exec(ReflexContext reflexContext, String[] args) {
		// 引数チェック
		if (args == null || args.length < 1) {
			throw new IllegalArgumentException("引数を指定してください。[0]有効なサービス・名前空間マップファイル名");
		}
		String validNamespacesFilename = args[0];
		if (StringUtils.isBlank(validNamespacesFilename)) {
			throw new IllegalArgumentException("引数を指定してください。[0]有効なサービス・名前空間マップファイル名");
		}

		// サービス名がシステム管理サービスでなければエラー
		String systemService = reflexContext.getServiceName();
		if (!systemService.equals(TaggingEnvUtil.getSystemService())) {
			throw new IllegalStateException("Please specify system service for the service name.");
		}

		// SystemContext作成
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		SystemContext systemContext = new SystemContext(systemService,
				requestInfo, reflexContext.getConnectionInfo());

		try {
			// 有効なサービス名・名前空間を取得
			Map<String, String> validNamespaces = VtecxBatchUtil.getKeyValueList(
					validNamespacesFilename, VtecxBatchConst.DELIMITER_SERVICE_NAMESPACE);

			execProc(systemContext, validNamespaces);

		} catch (IOException | TaggingException e) {
			throw new RuntimeException(e);
		}
		return true;
	}

	/**
	 * BDB Entryをデータストアにコピーする処理.
	 * システム管理サービスで実行してください。
	 * @param systemContext SystemContext
	 * @param validNamespaces サービスと名前空間の一覧
	 */
	public void execProc(SystemContext systemContext, Map<String, String> validNamespaces)
	throws IOException, TaggingException {
		// サービス名がシステム管理サービスでなければエラー
		String systemService = systemContext.getServiceName();
		if (!systemService.equals(TaggingEnvUtil.getSystemService())) {
			throw new IllegalStateException("Please specify system service for the service name.");
		}
		if (validNamespaces == null || validNamespaces.isEmpty()) {
			throw new IllegalStateException("有効なサービスがありません。");
		}

		// 現在日時を取得し、現在日時の1日前(ログ残存期間)を計算する。これをログ削除日時とする。
		String logKeepDate = getLogKeepDate();
		List<Future<Boolean>> futures = new ArrayList<Future<Boolean>>();
		for (String serviceName : validNamespaces.keySet()) {
			RequestInfo requestInfo = systemContext.getRequestInfo();
			ConnectionInfo connectionInfo = systemContext.getConnectionInfo();
			// サービスごとに処理実行
			// サービス情報の設定処理を行う。
			ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
			serviceManager.settingServiceIfAbsent(serviceName, requestInfo,
						connectionInfo);

			// ※ TaskQueueにする。
			ReflexAuthentication sysAuth = new SystemAuthentication(
					systemContext.getAccount(), systemContext.getUid(),
					serviceName);
			MoveLogToBigQueryCallable callable = new MoveLogToBigQueryCallable(
					logKeepDate);

			Future<Boolean> future = (Future<Boolean>)TaskQueueUtil.addTask(
					callable, 0, sysAuth, requestInfo, connectionInfo);
			futures.add(future);
		}

		// 終了確認
		for (Future<Boolean> future : futures) {
			try {
				future.get();
			} catch (InterruptedException e) {
				logger.error("InterruptedException: " + e.getMessage(), e);
			} catch (ExecutionException e) {
				Throwable cause = e.getCause();
				if (cause == null) {
					cause = e;
				}
				logger.error(cause.getClass().getSimpleName() + ": " + e.getMessage(), e);
			}
		}
	}

	/**
	 * ログ残存日時を取得
	 * @return ログ残存日時
	 */
	private String getLogKeepDate() {
		// ログ残存期間(日)
		int logKeepDay = getLogKeepDay();
		Date now = new Date();
		// ログ残存日時
		Date logKeepDate = DateUtil.addTime(now, 0, 0, 0 - logKeepDay, 0, 0, 0, 0);
		TimeZone timeZone = TimeZone.getTimeZone(BatchBDBConst.TIMEZONE_ID);	// タイムゾーンを+09:00にする
		return DateUtil.getDateTimeMillisec(logKeepDate, timeZone.getID());
	}

	/**
	 * ログを残す期間(日)を取得.
	 * @return ログを残す期間(日)
	 */
	private int getLogKeepDay() {
		return TaggingEnvUtil.getSystemPropInt(BatchBDBConst.LOG_KEEP_DAY,
				BatchBDBConst.LOG_KEEP_DAY_DEFAULT);
	}

}
