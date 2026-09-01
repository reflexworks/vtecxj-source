package jp.reflexworks.batch;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletRequest;

import jp.reflexworks.atom.api.Condition;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.conn.ConnectionInfoImpl;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.AuthenticationException;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.RequestInfoImpl;
import jp.reflexworks.taggingservice.plugin.AccessTokenManager;
import jp.reflexworks.taggingservice.plugin.ServiceManager;
import jp.reflexworks.taggingservice.plugin.def.ServiceManagerDefault;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.DeflateUtil;
import jp.sourceforge.reflex.util.FileUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * バッチジョブ実行サーバからの終了通知 受付処理.
 * <p>
 * バッチジョブ実行サーバ(vtecxbatchjob-runner)が、サーバサイドJS実行完了後に
 * POSTしてくる終了通知を受け付け、バッチジョブ管理テーブルへ結果を書き込み、
 * バッチジョブ実行時間を加算する。
 * </p>
 * <pre>
 * body:
 * {
 *   "ok": true/false,
 *   "message": メッセージ,
 *   "service_name": サービス名,
 *   "accesstoken": ACCESS_TOKEN,
 *   "script_name": ジョブ名,
 *   "job_id": ジョブ実行ID,
 *   "elapsed_time": 経過時間(秒)。エラーの場合も設定する。
 * }
 * </pre>
 */
public class BatchJobResultBlogic {

	/** ジョブ実行ID(指定時刻実行)の形式: yyyyMMddHHmm(12桁数値) */
	private static final String PATTERN_JOB_ID_DATETIME = "\\d{12}";

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 終了通知を受け付ける.
	 * @param httpReq リクエスト
	 */
	public void receive(HttpServletRequest httpReq)
	throws IOException, TaggingException {
		// リクエストボディ(JSON)を取得
		String bodyStr = FileUtil.readString(httpReq.getInputStream());
		if (StringUtils.isBlank(bodyStr)) {
			throw new IllegalParameterException("Request body is required.");
		}
		JSONObject body = null;
		try {
			body = new JSONObject(bodyStr);
		} catch (JSONException e) {
			throw new IllegalParameterException("Request body is not valid JSON. " + e.getMessage());
		}

		boolean ok = body.optBoolean(BatchJobConst.JSON_OK, false);
		String message = body.optString(BatchJobConst.JSON_MESSAGE, null);
		String serviceName = body.optString(BatchJobConst.JSON_SERVICE_NAME, null);
		String accesstoken = body.optString(BatchJobConst.JSON_ACCESSTOKEN, null);
		String scriptName = body.optString(BatchJobConst.JSON_SCRIPT_NAME, null);
		String jobId = body.optString(BatchJobConst.JSON_JOB_ID, null);
		long elapsedTimeSec = body.optLong(BatchJobConst.JSON_ELAPSED_TIME, 0L);

		if (StringUtils.isBlank(serviceName)) {
			throw new IllegalParameterException("service_name is required.");
		}
		if (StringUtils.isBlank(scriptName)) {
			throw new IllegalParameterException("script_name is required.");
		}
		if (StringUtils.isBlank(jobId)) {
			throw new IllegalParameterException("job_id is required.");
		}
		if (StringUtils.isBlank(accesstoken)) {
			throw new AuthenticationException("accesstoken is required.");
		}

		// 対象サービスのSystemContextを生成
		ServiceManager serviceManager = new ServiceManagerDefault();
		ReflexAuthentication auth = serviceManager.createServiceAdminAuth(serviceName);
		RequestInfo requestInfo = new RequestInfoImpl(serviceName,
				BatchJobConst.REQUESTINFO_IP, auth.getUid(), auth.getAccount(),
				BatchJobConst.REQUESTINFO_METHOD, BatchJobConst.REQUESTINFO_URL);
		DeflateUtil deflateUtil = null;
		ConnectionInfo connectionInfo = null;
		try {
			deflateUtil = new DeflateUtil();
			connectionInfo = new ConnectionInfoImpl(deflateUtil, requestInfo);

			// サービス初期処理
			new BatchJobBlogic().initService(serviceName, requestInfo, connectionInfo);

			SystemContext systemContext = new SystemContext(auth, requestInfo, connectionInfo);

			// アクセストークン認証チェック
			AccessTokenManager accessTokenManager = TaggingEnvUtil.getAccessTokenManager();
			if (!accessTokenManager.checkAccessToken(accesstoken, systemContext)) {
				throw new AuthenticationException("AccessToken auth error.");
			}

			// バッチジョブ管理エントリーを取得
			EntryBase batchJobTimeEntry = getBatchJobTimeEntry(systemContext, scriptName, jobId);
			if (batchJobTimeEntry == null) {
				// 多重通知・期限切れ等。ログのみで正常終了扱い。
				logger.warn(BatchJobUtil.getLoggerPrefix("receive", serviceName) +
						"BatchJob management entry not found. scriptName=" + scriptName +
						", jobId=" + jobId);
			} else {
				// 結果を反映
				batchJobTimeEntry.title = ok ?
						BatchJobConst.JOB_STATUS_SUCCEEDED : BatchJobConst.JOB_STATUS_FAILED;
				if (!ok && !StringUtils.isBlank(message)) {
					batchJobTimeEntry.setContentText(message);
				}
				systemContext.put(batchJobTimeEntry);
			}

			// バッチジョブ実行時間を加算 (エラーの場合も加算)
			if (elapsedTimeSec > 0L) {
				serviceManager.incrementBatchjoExecTime(elapsedTimeSec * 1000L, serviceName,
						requestInfo, connectionInfo);
			}

			if (BatchJobUtil.isEnableAccessLog()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[receive] done. scriptName=");
				sb.append(scriptName);
				sb.append(", jobId=");
				sb.append(jobId);
				sb.append(", ok=");
				sb.append(ok);
				sb.append(", elapsedTimeSec=");
				sb.append(elapsedTimeSec);
				logger.info(sb.toString());
			}

		} finally {
			if (connectionInfo != null) {
				try {
					connectionInfo.close();
				} catch (Exception e) {
					if (logger.isInfoEnabled()) {
						logger.info(LogUtil.getRequestInfoStr(requestInfo) +
								"ConnectionInfo close error.", e);
					}
				}
			}
		}
	}

	/**
	 * バッチジョブ管理エントリーを取得.
	 * ジョブ実行IDが指定時刻実行(12桁数値)の場合はキー直接指定、
	 * 手動実行(manual-xxxxxxxx)等の場合はジョブ名配下をジョブ実行ID(subtitle)で検索する。
	 * @param systemContext SystemContext
	 * @param scriptName ジョブ名
	 * @param jobId ジョブ実行ID
	 * @return バッチジョブ管理エントリー。見つからない場合はnull。
	 */
	private EntryBase getBatchJobTimeEntry(SystemContext systemContext, String scriptName,
			String jobId)
	throws IOException, TaggingException {
		String folderUri = BatchJobConst.URI_BATCHJOB + "/" + scriptName;
		if (jobId.matches(PATTERN_JOB_ID_DATETIME)) {
			return systemContext.getEntry(folderUri + "/" + jobId);
		}
		// ジョブ実行ID(subtitle)で検索
		String queryUri = folderUri + "?subtitle-" + Condition.EQUAL + "-" + jobId;
		FeedBase feed = systemContext.getFeed(queryUri);
		if (TaggingEntryUtil.isExistData(feed)) {
			return feed.entry.get(0);
		}
		return null;
	}

}
