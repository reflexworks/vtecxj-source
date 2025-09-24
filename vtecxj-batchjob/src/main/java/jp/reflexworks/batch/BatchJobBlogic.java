package jp.reflexworks.batch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.batch.servlet.BatchJobRequestUtil;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.bdbclient.BDBClientUtil;
import jp.reflexworks.taggingservice.blogic.SessionBlogic;
import jp.reflexworks.taggingservice.context.ReflexContextUtil;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.EntryDuplicatedException;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.RequestInfoImpl;
import jp.reflexworks.taggingservice.plugin.AuthenticationManager;
import jp.reflexworks.taggingservice.plugin.ServiceManager;
import jp.reflexworks.taggingservice.plugin.UserManager;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.DateUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * バッチジョブ ビジネスロジック.
 */
public class BatchJobBlogic {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * バッチジョブ実行管理処理をTaskQueueに登録
	 * @param reflexContext ReflexContext
	 */
	public void callManagement(ReflexContext reflexContext)
	throws IOException, TaggingException {
		// Redisの正常起動確認
		checkRedis(reflexContext);
		// バッチジョブ実行管理処理
		ConnectionInfo sharingConnectionInfo = BDBClientUtil.copySharingConnectionInfo(
				reflexContext.getRequestInfo(), reflexContext.getConnectionInfo());
		BatchJobUtil.addTaskOfManagement(reflexContext.getAuth(),
				reflexContext.getRequestInfo(), sharingConnectionInfo);
	}

	/**
	 * バッチジョブ実行管理処理.
	 * @param podName Pod名
	 * @param reflexContext ReflexContext (システム管理サービス)
	 */
	public void execManagement(String podName, ReflexContext reflexContext) {
		if (StringUtils.isBlank(podName)) {
			throw new IllegalStateException("Pod name is required.");
		}

		// サービス名がシステム管理サービスでなければエラー
		String systemService = reflexContext.getServiceName();
		if (!systemService.equals(TaggingEnvUtil.getSystemService())) {
			throw new IllegalStateException("Please specify system service for the service name.");
		}

		if (logger.isTraceEnabled()) {
			logger.debug("[BatchJobBlogic] exec start. podName=" + podName);
		}
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		try {
			// Redisの正常起動確認
			checkRedis(reflexContext);

			// 有効なサービス名・名前空間を取得
			Map<String, String> validNamespaces = getNamespaceMap(reflexContext);
			// 現在時刻
			Date now = new Date();
			int minute = Integer.parseInt(DateUtil.getDateTimeFormat(now, "m"));
			int hour = Integer.parseInt(DateUtil.getDateTimeFormat(now, "H"));
			int date = Integer.parseInt(DateUtil.getDateTimeFormat(now, "d"));
			int month = Integer.parseInt(DateUtil.getDateTimeFormat(now, "M"));
			int year = Integer.parseInt(DateUtil.getDateTimeFormat(now, "Y"));
			int day = Integer.parseInt(DateUtil.getDateTimeFormat(now, "u"));	// 曜日の番号(1 =月曜、...、7 =日曜)
			Integer[] nowParts = new Integer[] {minute, hour, date, month, year, day};
			long nowTime = now.getTime();

			// バッチジョブの現在からの実行範囲(秒)
			int batchjobExecRangeSec = BatchJobUtil.getBatchjobExecRangeSec();
			Date rangeDate = DateUtil.addTime(now, 0, 0, 0, 0, 0, batchjobExecRangeSec, 0);
			String rangeDateStr = DateUtil.getDateTimeFormat(rangeDate, "yyyyMMddHHmm");

			// サービスごとにバッチジョブを実行
			// 実行するバッチジョブが1個見つかったら、あとは行わないでバッチジョブサーバにリクエストを投げる。
			// (負荷分散のため)
			for (String serviceName : validNamespaces.keySet()) {
				List<BatchJobFuture> futures = execBatchJobByService(podName,
						now, nowParts, nowTime, rangeDateStr,
						serviceName, requestInfo, connectionInfo);
				if (futures != null && !futures.isEmpty()) {
					BatchJobUtil.setBatchJobFutureList(serviceName, futures);

					// バッチジョブサーバにリクエスト
					BatchJobUtil.requestBatchJob();
					// 続きはリクエスト先で実行
					break;
				}
			}

		} catch (IOException | TaggingException e) {
			throw new RuntimeException(e);
		} finally {
			// Do nothing.
		}

		if (logger.isTraceEnabled()) {
			logger.debug("[BatchJobBlogic] exec end.");
		}
	}

	/**
	 * 名前空間一覧を取得
	 * @param reflexContext ReflexContext
	 * @return 名前空間一覧 (キー:サービス名、値:名前空間)
	 */
	private Map<String, String> getNamespaceMap(ReflexContext reflexContext)
	throws IOException {
		Set<String> validServiceStatuses = BatchJobUtil.getValidServiceStatuses();
		WriteNamespacesBlogic namespacesBlogic = new WriteNamespacesBlogic();
		return namespacesBlogic.getNamespaceMap(reflexContext, validServiceStatuses);
	}

	/**
	 * サービスごとにバッチジョブを実行する.
	 * @param podName POD名
	 * @param now 現在時刻
	 * @param nowParts 現在時刻の[0]分[1]時[2]日[3]月[4]年[5]曜日
	 * @param nowTime 現在時刻のミリ秒表現(1970年1月1日00:00:00 GMTからのミリ秒数)
	 * @param rangeDateStr 現在時刻にジョブ実行範囲秒を加えた日時のyyyyMMddHHmm形式文字列
	 * @param serviceName サービス名
	 * @param adminRequestInfo リクエスト情報 (システム管理サービス)
	 * @param connectionInfo コネクション情報
	 * @return BatchJobFutureリスト
	 */
	private List<BatchJobFuture> execBatchJobByService(String podName,
			Date now, Integer[] nowParts, long nowTime, String rangeDateStr,
			String serviceName,
			RequestInfo systemRequestInfo, ConnectionInfo connectionInfo) {
		String methodName = "execBatchJobByService";
		// サービスのRequestInfoを生成
		RequestInfo requestInfo = new RequestInfoImpl(serviceName,
				systemRequestInfo.getIp(),
				systemRequestInfo.getUid(),
				systemRequestInfo.getAccount(),
				systemRequestInfo.getMethod(),
				systemRequestInfo.getUrl());
		// サービスのSystemContextを生成
		SystemContext systemContext = new SystemContext(serviceName,
				requestInfo, connectionInfo);
		try {
			// サービスの初期化処理
			initService(serviceName, requestInfo, connectionInfo);

			// サービス管理者権限のReflexContextを生成
			ReflexAuthentication serviceAdminAuth = createServiceAdminAuth(systemContext);

			// /_settings/propertiesのバッチジョブ設定を取得
			Map<String, String> batchJobs = TaggingEnvUtil.getPropMap(serviceName,
					BatchJobConst.PROP_BATCHJOB_PREFIX);
			if (batchJobs == null || batchJobs.isEmpty()) {
				// バッチジョブ設定なし
				if (logger.isTraceEnabled()) {
					logger.debug(getLoggerPrefix(methodName, serviceName) + "no settings.");
				}
				return null;
			}

			// サービス管理者権限のRequestInfo、systemContextを再生成
			requestInfo = new RequestInfoImpl(serviceName,
					systemRequestInfo.getIp(),
					serviceAdminAuth.getUid(),
					serviceAdminAuth.getAccount(),
					systemRequestInfo.getMethod(),
					systemRequestInfo.getUrl());
			systemContext = new SystemContext(serviceName,
					requestInfo, connectionInfo);

			// バッチジョブ管理エントリーのフォルダ存在チェック
			// キー: /_batchjob
			String batchJobTopFolderUri = getBatchJobTopFolderUri();
			postFolder(batchJobTopFolderUri, systemContext);

			List<BatchJobFuture> futures = new ArrayList<>();
			for (Map.Entry<String, String> mapEntry : batchJobs.entrySet()) {
				String propName = mapEntry.getKey();
				String propVal = mapEntry.getValue();

				// ジョブ定義ごとの処理
				List<BatchJobFuture> futuresByJobDefinition = execBatchJobByJobDefinition(
						podName, propName, propVal,
						now, nowParts, nowTime, rangeDateStr, systemContext, serviceAdminAuth);
				if (futuresByJobDefinition != null && !futuresByJobDefinition.isEmpty()) {
					futures.addAll(futuresByJobDefinition);
				}
			}
			return futures;

		} catch (IOException | TaggingException | IllegalArgumentException e) {
			// エラーの場合も後続の処理を実行するため例外をスローしない。
			String msg = BatchJobUtil.editErrorMessage(e);
			logger.warn(getLoggerPrefix(methodName, serviceName) + msg);
			systemContext.log(BatchJobConst.LOG_TITLE, Constants.WARN, msg);
			return null;
		}
	}

	/**
	 * バッチジョブ実行 (ジョブ定義単位)
	 * @param podName POD名
	 * @param propName プロパティの名前
	 * @param propVal ジョブの実行時間とサーバサイドJS
	 *                書式: {分} {時} {日} {月} {曜日} {サーバサイドJSのキー}
	 * @param now 現在時刻
	 * @param nowParts 現在時刻の[0]分[1]時[2]日[3]月[4]年[5]曜日
	 * @param nowTime 現在時刻のミリ秒表現(1970年1月1日00:00:00 GMTからのミリ秒数)
	 * @param rangeDateStr 現在時刻にジョブ実行範囲秒を加えた日時のyyyyMMddHHmm形式文字列
	 * @param systemContext 対象サービスのSystemContext
	 * @param serviceAdminAuth サービス管理者の認証情報
	 * @return BatchJobFutureリスト
	 */
	private List<BatchJobFuture> execBatchJobByJobDefinition(String podName,
			String propName, String propVal,
			Date now, Integer[] nowParts, long nowTime, String rangeDateStr,
			SystemContext systemContext, ReflexAuthentication serviceAdminAuth) {
		String methodName = "execBatchJobByJobDefinition";
		String serviceName = systemContext.getServiceName();
		try {
			// プロパティ設定チェック
			// ジョブ名取得
			String jobName = getJobNameFromPropname(propName);
			if (StringUtils.isBlank(jobName)) {
				String msg = BatchJobUtil.editErrorMessage("Job name is not defined.", propName, propVal);
				throw new IllegalParameterException(msg);
			}
			// ジョブの実行時間とサーバサイドJS
			if (StringUtils.isBlank(propVal)) {
				String msg = BatchJobUtil.editErrorMessage("BatchJob setting is not defined.", propName, propVal);
				throw new IllegalParameterException(msg);
			}
			// 書式: {分} {時} {日} {月} {曜日} {サーバサイドJSのキー}
			String[] batchjobSetting = propVal.split(" ");
			if (batchjobSetting.length < 6) {
				String msg = BatchJobUtil.editErrorMessage("BatchJob format is invalid.", propName, propVal);
				throw new IllegalParameterException(msg);
			}

			try {
				// ジョブ実行時刻を求める。
				List<String> nextJobDates = BatchJobUtil.getNextJobDates(batchjobSetting,
						now, nowParts, rangeDateStr);
				if (nextJobDates == null || nextJobDates.isEmpty()) {
					if (logger.isTraceEnabled()) {
						logger.debug(getLoggerPrefix(methodName, serviceName) +
								BatchJobUtil.editErrorMessage("The batchjob is not scheduled yet.", propName, propVal));
					}
					return null;
				}

				// バッチジョブ起動処理を行う。
				String pathinfoAndQuerystring = batchjobSetting[5];
				List<BatchJobFuture> futuresByJobDefinition = new ArrayList<>();
				for (String jobDateStr : nextJobDates) {
					BatchJobFuture future = execBatchJobByJob(podName, propName, propVal,
							now, nowTime, jobName, pathinfoAndQuerystring, jobDateStr,
							systemContext, serviceAdminAuth);
					if (future != null) {
						futuresByJobDefinition.add(future);
						// (2020.8.25)バッチジョブ実行は1リクエスト1件とする。
						break;
					}
				}
				return futuresByJobDefinition;

			} catch (IllegalParameterException e) {
				// エラーメッセージにプロパティの内容を付加する。
				String msg = BatchJobUtil.editErrorMessage(e.getMessage(), propName, propVal);
				throw new IllegalParameterException(msg);
			}
		} catch (IllegalParameterException e) {
			// 設定エラーの場合、ログに出力して次の設定を読み込み、後続の処理を実行するため例外をスローしない。
			String msg = BatchJobUtil.editErrorMessage(e);
			logger.warn(getLoggerPrefix(methodName, serviceName) + msg);
			systemContext.log(BatchJobConst.LOG_TITLE, Constants.WARN, msg);
			return null;
		}
	}

	/**
	 * バッチジョブ実行 (ジョブ単位)
	 * @param podName POD名
	 * @param propName プロパティの名前
	 * @param propVal ジョブの実行時間とサーバサイドJS
	 *                書式: {分} {時} {日} {月} {曜日} {サーバサイドJSのキー}
	 * @param now 現在時刻
	 * @param nowParts 現在時刻の[0]分[1]時[2]日[3]月[4]年[5]曜日
	 * @param nowTime 現在時刻のミリ秒表現(1970年1月1日00:00:00 GMTからのミリ秒数)
	 * @param rangeDateStr 現在時刻にジョブ実行範囲秒を加えた日時のyyyyMMddHHmm形式文字列
	 * @param systemContext 対象サービスのSystemContext
	 * @param pServiceAdminAuth サービス管理者の認証情報
	 * @return BatchJobFuture
	 */
	private BatchJobFuture execBatchJobByJob(String podName,
			String propName, String propVal, Date now, long nowTime,
			String jobName, String pathinfoAndQuerystring, String jobDateStr,
			SystemContext systemContext, ReflexAuthentication pServiceAdminAuth) {
		String methodName = "execBatchJobByJob";
		String serviceName = systemContext.getServiceName();
		// バッチジョブ起動処理を行う。
		String jsFunction = getJsFunction(pathinfoAndQuerystring);
		try {
			CheckUtil.checkNotNull(jsFunction, "JS function name");
		} catch (IllegalParameterException e) {
			String msg = BatchJobUtil.editErrorMessage(e.getMessage(), propName, propVal);
			throw new IllegalParameterException(msg);
		}

		EntryBase batchJobTimeEntry = null;
		String currentExecUri = null;
		try {
			// バッチジョブ管理エントリーのフォルダ存在チェック
			// キー: /_batchjob/{ジョブ名}
			String batchJobFolderUri = getBatchJobFolderUri(jobName);
			currentExecUri = batchJobFolderUri;
			postFolder(batchJobFolderUri, systemContext);

			// システムのリクエスト情報
			RequestInfo systemRequestInfo = systemContext.getRequestInfo();
			// コネクション情報
			ConnectionInfo connectionInfo = systemContext.getConnectionInfo();

			// バッチジョブ起動チェック
			String batchJobTimeUri = getBatchJobTimeUri(jobName, jobDateStr);
			currentExecUri = batchJobTimeUri;
			batchJobTimeEntry = systemContext.getEntry(batchJobTimeUri);
			if (batchJobTimeEntry != null) {
				// バッチジョブ管理データが存在する場合、他のPodによってバッチジョブが実行されているため実行しない。
				if (logger.isTraceEnabled()) {
					String msg = "The batch job has been executed. " + batchJobTimeUri;
					logger.debug(getLoggerPrefix(methodName, serviceName) + msg);
				}
				batchJobTimeEntry = null;	// finallyでバッチジョブ管理エントリーを更新しない。
				return null;
			}

			try {
				// バッチジョブ排他
				EntryBase tmpBatchJobTimeEntry = createBatchJobTimeEntry(
						podName, batchJobTimeUri, serviceName);
				currentExecUri = batchJobTimeUri;
				batchJobTimeEntry = systemContext.post(tmpBatchJobTimeEntry);
			} catch (EntryDuplicatedException e) {
				// すでに同じキーのエントリーが存在する場合、他のPodによってバッチジョブが実行されているため、
				// 該当ジョブは実行しない。
				if (logger.isDebugEnabled()) {
					logger.debug(getLoggerPrefix(methodName, serviceName) +
							"EntryDuplicatedException: " + e.getMessage());
				}
				return null;
			}

			// セッション付き認証情報を生成
			ReflexAuthentication serviceAdminAuth = createServiceAdminAuthWithSession(
					pServiceAdminAuth, systemRequestInfo, connectionInfo);
			// リクエスト
			ReflexRequest req = BatchJobRequestUtil.createRequest(pathinfoAndQuerystring,
					serviceAdminAuth, systemRequestInfo, connectionInfo);
			// リクエスト情報
			RequestInfo requestInfo = req.getRequestInfo();
			// ReflexContext
			ReflexContext reflexContext = ReflexContextUtil.getReflexContext(serviceAdminAuth,
					requestInfo, connectionInfo);
			// レスポンスはnull
			ReflexResponse resp = null;

			// 実行待ち時間を計算
			int delay = BatchJobUtil.getDiffMilliSec(nowTime, jobDateStr);
			if (logger.isDebugEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(getLoggerPrefix(methodName, serviceName));
				sb.append("now=");
				sb.append(now);
				sb.append(", jobDateStr=");
				sb.append(jobDateStr);
				sb.append(", delay=");
				sb.append(delay);
				logger.debug(sb.toString());
			}

			// バッチジョブ実行TaskQueueを登録
			Future<Boolean> future = BatchJobUtil.addTaskOfBatchJob(jsFunction,
					batchJobTimeEntry, req, resp, delay);
			return new BatchJobFuture(jsFunction, batchJobTimeEntry, future, now, delay,
					reflexContext);

		} catch (Throwable e) {
			// エラーの場合も後続の処理を実行するため例外をスローしない。
			String msg = BatchJobUtil.editErrorMessage(currentExecUri, e);
			if (e instanceof TaggingException || e instanceof IllegalParameterException) {
				logger.warn(getLoggerPrefix(methodName, serviceName) + msg);
			} else {
				logger.warn(getLoggerPrefix(methodName, serviceName) + msg);
			}
			systemContext.log(BatchJobConst.LOG_TITLE, Constants.WARN, msg);
			return null;
		}
	}

	/**
	 * プロパティの名前からジョブ名を取得
	 * @param name プロパティの名前
	 * @return ジョブ名
	 */
	private String getJobNameFromPropname(String name) {
		return name.substring(BatchJobConst.PROP_BATCHJOB_PREFIX_LEN);
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
	 * サービス管理者の認証情報を生成.
	 * @param systemContext SystemContext
	 * @return サービス管理者の認証情報
	 */
	private ReflexAuthentication createServiceAdminAuth(SystemContext systemContext)
	throws IOException, TaggingException {
		String serviceName = systemContext.getServiceName();
		// サービス管理者のUIDを取得
		FeedBase feed = systemContext.getFeed(Constants.URI_GROUP_ADMIN);
		if (feed != null && feed.entry != null && !feed.entry.isEmpty()) {
			String uri = feed.entry.get(0).getMyUri();
			String uid = TaggingEntryUtil.getSelfidUri(uri);
			String sessionId = null;	// このメソッドではセッションを生成しない。

			UserManager userManager = TaggingEnvUtil.getUserManager();
			String account = userManager.getAccountByUid(uid, systemContext);
			//ReflexAuthentication auth = new Authentication(account, uid, sessionId,
			//		Constants.AUTH_TYPE_SYSTEM, serviceName);
			AuthenticationManager authManager = TaggingEnvUtil.getAuthenticationManager();
			ReflexAuthentication auth = authManager.createAuth(account, uid, sessionId,
					Constants.AUTH_TYPE_SYSTEM, serviceName);
			// グループ追加
			List<String> groups = userManager.getGroupsByUid(uid, systemContext);
			if (groups != null) {
				for (String group : groups) {
					auth.addGroup(group);
				}
			}
			return auth;
		}
		return null;
	}

	/**
	 * セッション付きサービス管理者の認証情報を生成.
	 * @param serviceAdminAuth サービス管理者の認証情報
	 * @param systemContext SystemContext
	 * @return サービス管理者の認証情報
	 */
	private ReflexAuthentication createServiceAdminAuthWithSession(
			ReflexAuthentication serviceAdminAuth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// セッション付き認証情報を作成
		SessionBlogic sessionBlogic = new SessionBlogic();
		ReflexAuthentication auth = sessionBlogic.createSession(
				serviceAdminAuth.getAccount(), serviceAdminAuth.getUid(), serviceAdminAuth.getAuthType(),
				serviceAdminAuth.getServiceName(), requestInfo, connectionInfo);
		// グループ追加
		List<String> groups = serviceAdminAuth.getGroups();
		if (groups != null) {
			for (String group : groups) {
				auth.addGroup(group);
			}
		}
		return auth;
	}

	/**
	 * PathInfoとQueryStringからPathInfo部分だけを取り出す.
	 * @param pathinfoAndQuerystring PathInfo?QueryString
	 * @return PathInfo
	 */
	private String getJsFunction(String pathinfoAndQuerystring) {
		if (StringUtils.isBlank(pathinfoAndQuerystring)) {
			return pathinfoAndQuerystring;
		}
		int idx = pathinfoAndQuerystring.indexOf("?");
		String jsUri = null;
		if (idx == -1) {
			jsUri = pathinfoAndQuerystring;
		} else {
			jsUri = pathinfoAndQuerystring.substring(0, idx);
		}
		return jsUri;
	}

	/**
	 * バッチジョブ管理親URIを取得
	 * @return バッチジョブ管理親URI
	 */
	private String getBatchJobTopFolderUri() {
		return BatchJobConst.URI_BATCHJOB;
	}

	/**
	 * バッチジョブ管理URI(ジョブ名まで)を取得
	 * @param jobName ジョブ名
	 * @return バッチジョブ管理URI(ジョブ名まで)
	 */
	private String getBatchJobFolderUri(String jobName) {
		StringBuilder sb = new StringBuilder();
		sb.append(getBatchJobTopFolderUri());
		sb.append("/");
		sb.append(jobName);
		return sb.toString();
	}

	/**
	 * バッチジョブ管理URI(ジョブ実行時刻まで)を取得
	 * @param jobName ジョブ名
	 * @param jobDateStr ジョブ実行時刻文字列
	 * @return バッチジョブ管理URI(ジョブ実行時刻まで)
	 */
	private String getBatchJobTimeUri(String jobName, String jobDateStr) {
		StringBuilder sb = new StringBuilder();
		sb.append(getBatchJobFolderUri(jobName));
		sb.append("/");
		sb.append(jobDateStr);
		return sb.toString();
	}

	/**
	 * バッチジョブ管理エントリーのフォルダ登録.
	 * フォルダを参照し、存在しなければ登録する。
	 * @param folderUri フォルダURI
	 * @param systemContext SystemContext
	 */
	private void postFolder(String folderUri, SystemContext systemContext)
	throws IOException, TaggingException {
		String methodName = "postFolder";
		String serviceName = systemContext.getServiceName();
		EntryBase entry = systemContext.getEntry(folderUri);
		if (entry == null) {
			try {
				entry = TaggingEntryUtil.createEntry(serviceName);
				entry.setMyUri(folderUri);
				systemContext.post(entry);

			} catch (EntryDuplicatedException e) {
				// 登録の重複は問題なし
				if (logger.isDebugEnabled()) {
					logger.debug(getLoggerPrefix(methodName, serviceName) +
							"EntryDuplicatedException: " + e.getMessage());
				}
			}
		}
	}

	/**
	 * バッチジョブ管理エントリー生成.
	 * @param podName POD名
	 * @param uri URI
	 * @param serviceName サービス名
	 * @return バッチジョブ管理エントリー
	 */
	private EntryBase createBatchJobTimeEntry(String podName, String uri,
			String serviceName) {
		// キー: /_batchjob/{ジョブ名}/{ジョブ実行時刻(yyyyMMddHHmm)}
		// 値
		//    titleにrunning(ジョブ実行ステータス: 実行中)
		//    subtitleにPod名(環境変数HOSTNAMEで取得可)
		EntryBase batchJobTimeEntry = TaggingEntryUtil.createEntry(serviceName);
		batchJobTimeEntry.setMyUri(uri);
		batchJobTimeEntry.title = BatchJobConst.JOB_STATUS_WAITING;
		batchJobTimeEntry.subtitle = podName;
		return batchJobTimeEntry;
	}

	/**
	 * サービスの初期処理.
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	void initService(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// サービス情報の設定
		ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
		serviceManager.settingServiceIfAbsent(serviceName, requestInfo, connectionInfo);
	}
	
	/**
	 * Redisの正常起動確認.
	 * @param reflexContext ReflexContext
	 */
	private void checkRedis(ReflexContext reflexContext) throws IOException, TaggingException {
		// エラーが発生しなければ、Redisが問題なく使用できるということなのでOK
		reflexContext.getCacheString("/_batchjobdummy");
	}

}
