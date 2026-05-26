package jp.reflexworks.batch;

import java.io.IOException;
import java.text.ParseException;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.script.ScriptException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.js.JsContext;
import jp.reflexworks.js.JsExec;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.JobManager;
import jp.reflexworks.taggingservice.plugin.ServiceManager;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;

/**
 * バッチジョブ処理.
 */
public class BatchJobCallable extends ReflexCallable<Boolean> {

	/** POD名 */
	private String podName;
	/** サーバサイドJS名 */
	private String jsFunction;
	/** バッチジョブ管理エントリー */
	private EntryBase batchJobTimeEntry;
	/** リクエスト */
	private ReflexRequest req;
	/** レスポンス */
	private ReflexResponse resp;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ.
	 * @param podName POD名
	 * @param jsFunction サーバサイドJS名
	 * @param batchJobTimeEntry バッチジョブ管理エントリー
	 * @param req リクエスト
	 * @param resp レスポンス
	 */
	public BatchJobCallable(String podName, String jsFunction, EntryBase batchJobTimeEntry,
			ReflexRequest req, ReflexResponse resp) {
		this.podName = podName;
		this.jsFunction = jsFunction;
		this.batchJobTimeEntry = batchJobTimeEntry;
		this.req = req;
		this.resp = resp;
	}

	/**
	 * バッチジョブ処理 実行
	 */
	@Override
	public Boolean call()
	throws IOException, TaggingException {
		ReflexContext reflexContext = (ReflexContext)getReflexContext();
		String serviceName = reflexContext.getServiceName();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		SystemContext systemContext = new SystemContext(serviceName,
				requestInfo, connectionInfo);

		long startTime = 0;
		String info = null;
		if (isEnabledAccessLog()) {
			StringBuilder sb = new StringBuilder();
			sb.append(" jsFunction=");
			sb.append(jsFunction);
			sb.append(", batchJobTimeUri=");
			sb.append(batchJobTimeEntry.getMyUri());
			info = sb.toString();
			
			sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[call] start.");
			sb.append(info);
			logger.info(sb.toString());
			startTime = new Date().getTime();
		}

		String methodName = "call";
		boolean isSucceeded = false;
		String batchJobTimeUri = batchJobTimeEntry.getMyUri();
		EntryBase tmpBatchJobTimeEntry = null;
		JobManager jobManager = null;
		// 開始時刻
		long startJobTime = 0L;
		long endJobTime = 0L;
		Future future = null;
		ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
		try {
			// ステータスをrunningに更新
			this.batchJobTimeEntry.title = BatchJobConst.JOB_STATUS_RUNNING;
			tmpBatchJobTimeEntry = systemContext.put(this.batchJobTimeEntry);
			transfer(tmpBatchJobTimeEntry);

			// アクセス数カウント
			serviceManager.incrementAccessCounter(serviceName, requestInfo, connectionInfo);
			
			// サービスステータスがstagingの場合、1日の累計実行時間が最大値を超えていないかチェック
			serviceManager.checkBatchjobExecSec(serviceName, requestInfo, connectionInfo);

			// /_html/batchjob 配下にバッチジョブが登録されている場合、Cloud Run Jobで実行する
			String cloudRunJobUri = getCloudRunJobUri();
			EntryBase cloudrunjobEntry = reflexContext.getEntry(cloudRunJobUri);
			// 開始時刻
			startJobTime = new Date().getTime();
			if (cloudrunjobEntry != null) {
				// Cloud Run Jobでバッチジョブ実行
				jobManager = TaggingEnvUtil.getJobManager();
				future = jobManager.runJob(jsFunction, reflexContext);
				
			} else {
				// サーバサイドJSでバッチジョブ実行
				JsContext jscontext = new JsContext(reflexContext, req, resp, BatchJobConst.METHOD);
				future = JsExec.submit(jscontext, req, resp, jsFunction,
						BatchJobConst.METHOD, 0, null, reflexContext);
			}

			// 結果を受け取る
			int timeout = BatchJobUtil.getJsTimeout(serviceName, requestInfo, connectionInfo);
			future.get(timeout, TimeUnit.SECONDS);
			isSucceeded = true;
			
		// TODO Java17では jdk.nashorn.internal.runtime.ECMAException がない。
		} catch (ParseException | InterruptedException | TimeoutException |
				TaggingException | IOException e) {
			StringBuilder sb = new StringBuilder();
			sb.append(BatchJobUtil.editErrorMessage(e));
			sb.append(" target=");
			sb.append(batchJobTimeUri);
			sb.append(", jsFunction=");
			sb.append(jsFunction);
			String msg = sb.toString();
			logger.warn(getLoggerPrefix(methodName, serviceName) + msg);
			reflexContext.log(BatchJobConst.LOG_TITLE, Constants.WARN, msg);

		} catch (ExecutionException e) {
			logger.warn(getLoggerPrefix(methodName, serviceName) + " ExecutionException: " + e.getMessage());
			// ExecutionExceptionはcauseを使用する。
			Throwable cause = e.getCause();
			if (cause == null) {
				cause = e;
			} else {
				if (cause instanceof ScriptException) {
					// JS実行エラーはScriptExceptionでラップされているので取り出す。
					Throwable seCause = cause.getCause();
					if (seCause instanceof IOException ||
							seCause instanceof TaggingException) {
						cause = seCause;
					}
				}
			}
			String msg = BatchJobUtil.editErrorMessage(cause);
			logger.warn(getLoggerPrefix(methodName, serviceName) + msg);
			reflexContext.log(BatchJobConst.LOG_TITLE, Constants.WARN, msg);

		} finally {
			// 終了時刻
			endJobTime = new Date().getTime();
			String jobStatus = null;
			if (isSucceeded) {
				// 正常に終了した場合、titleにsucceeded(ジョブ実行ステータス: 成功)を設定
				jobStatus = BatchJobConst.JOB_STATUS_SUCCEEDED;
			} else {
				// 異常終了した場合、titleにfailed(ジョブ実行ステータス: 失敗)を設定
				jobStatus = BatchJobConst.JOB_STATUS_FAILED;
			}
			batchJobTimeEntry.title = jobStatus;
			if (jobManager != null) {
				// Cloud Run Jobの実行IDを設定
				jobManager.setJobInfo(future, batchJobTimeEntry);
			}

			try {
				tmpBatchJobTimeEntry = systemContext.put(batchJobTimeEntry);
				transfer(tmpBatchJobTimeEntry);
			} catch (IOException | TaggingException e) {
				String msg = BatchJobUtil.editErrorMessage(batchJobTimeUri, e);
				logger.warn(getLoggerPrefix(methodName, serviceName) + msg);
				systemContext.log(BatchJobConst.LOG_TITLE, Constants.WARN, msg);
			}
			// 実行時間の加算
			if (startJobTime > 0) {
				long execTimeSec = (endJobTime - startJobTime) / 1000;
				serviceManager.incrementBatchjoExecSec(execTimeSec, serviceName, 
						requestInfo, connectionInfo);
			}
		}

		if (logger.isInfoEnabled()) {
			long finishTime = new Date().getTime();
			long time = finishTime - startTime;
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[call] end - ");
			sb.append(time);
			sb.append("ms");
			sb.append(" isSucceeded=");
			sb.append(isSucceeded);
			sb.append(info);
			logger.info(sb.toString());
		}

		return isSucceeded;
	}

	/**
	 * 項目移送.
	 * このオブジェクトに保持しているバッチジョブエントリーに、更新内容を移送する。
	 * シャットダウン時に処理判定のためこのバッチジョブエントリーを使用するため、ポインタを外さないよう移送処理を行う。
	 * @param tmpBatchJobTimeEntry 更新後のエントリー
	 */
	private void transfer(EntryBase tmpBatchJobTimeEntry) {
		if (tmpBatchJobTimeEntry != null) {
			this.batchJobTimeEntry.title = tmpBatchJobTimeEntry.title;
			this.batchJobTimeEntry.id = tmpBatchJobTimeEntry.id;
			this.batchJobTimeEntry.author = tmpBatchJobTimeEntry.author;
			this.batchJobTimeEntry.updated = tmpBatchJobTimeEntry.updated;
		}
	}

	/**
	 * ロガー出力用接頭辞を編集
	 * @param methodName Javaメソッド名
	 * @param serviceName サービス名
	 * @return ロガー出力用接頭辞
	 */
	private String getLoggerPrefix(String methodName, String serviceName) {
		return BatchJobUtil.getLoggerPrefix(methodName, serviceName);
	}

	/**
	 * Cloud Run Jobで実行するバッチジョブのキーを取得
	 * @return Cloud Run Jobで実行するバッチジョブのキー
	 */
	private String getCloudRunJobUri() {
		StringBuilder sb = new StringBuilder();
		sb.append(BatchJobConst.URI_CLOUDRUNJOB);
		sb.append("/");
		sb.append(jsFunction);
		sb.append(".js");
		return sb.toString();
	}
	
	/**
	 * アクセスログを出力する場合true
	 * @return アクセスログを出力する場合true
	 */
	private boolean isEnabledAccessLog() {
		return BatchJobUtil.isEnableAccessLog();
	}

}
