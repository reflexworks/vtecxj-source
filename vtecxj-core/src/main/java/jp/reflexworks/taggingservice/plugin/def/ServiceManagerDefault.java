package jp.reflexworks.taggingservice.plugin.def;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.api.AtomConst;
import jp.reflexworks.atom.entry.Contributor;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.servlet.util.UrlUtil;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexEnv;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.ReflexStatic;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.auth.AuthenticationConst;
import jp.reflexworks.taggingservice.blogic.SecurityConst;
import jp.reflexworks.taggingservice.blogic.ServiceBlogic;
import jp.reflexworks.taggingservice.env.StaticInfoUtil;
import jp.reflexworks.taggingservice.env.TaggingEnvConst;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.EntryDuplicatedException;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.MethodNotAllowedException;
import jp.reflexworks.taggingservice.exception.NoExistingEntryException;
import jp.reflexworks.taggingservice.exception.PaymentRequiredException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.AuthenticationManager;
import jp.reflexworks.taggingservice.plugin.DatastoreManager;
import jp.reflexworks.taggingservice.plugin.ExecuteAtCreateService;
import jp.reflexworks.taggingservice.plugin.NamespaceManager;
import jp.reflexworks.taggingservice.plugin.ServiceManager;
import jp.reflexworks.taggingservice.plugin.UserManager;
import jp.reflexworks.taggingservice.service.ServiceConst;
import jp.reflexworks.taggingservice.service.TaggingServiceUtil;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.taskqueue.TaskQueueUtil;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.reflexworks.taggingservice.util.UserUtil;
import jp.sourceforge.reflex.util.DateUtil;
import jp.sourceforge.reflex.util.NumberingUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * サービス管理クラス.
 */
public class ServiceManagerDefault implements ServiceManager {

	/** createservice引数チェックに使用。接頭辞の長さ */
	private static final int URI_SERVICE_PREFIX_LEN =
			Constants.URI_SERVICE_PREFIX.length();
	/** サービス名に使用できる文字パターンオブジェクト */
	public static final Pattern PATTERN_SERVICENAME =
			Pattern.compile(ServiceManagerDefaultConst.PATTERN_STR_SERVICENAME);
	/** APIKeyの文字列長 */
	private static final int APIKEY_STRING_LEN = 36;
	/** サービスキーの文字列長 */
	private static final int SERVICEKEY_STRING_LEN = 40;

	/** /_html/ */
	private static final String URI_HTML_SLASH = Constants.URI_HTML + "/";

	/** Header : X-SERVICENAME */
	public static final String HEADER_SERVICENAME = Constants.HEADER_SERVICENAME;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 初期処理.
	 * サーバ起動時に一度だけ呼び出されます。
	 */
	public void init() {
		ServiceManagerDefaultSetting serviceSetter = new ServiceManagerDefaultSetting();
		serviceSetter.init();
	}

	/**
	 * シャットダウン処理.
	 */
	public void close() {
		// Do nothing.
	}

	/**
	 * 指定されたサービスが有効かどうかチェックする。
	 * @param req リクエスト (メンテナンス失敗再処理時に使用)
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 有効である場合true
	 */
	public boolean isEnabled(ReflexRequest req, String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException {
		if (StringUtils.isBlank(serviceName)) {
			return false;
		}
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		String systemService = env.getSystemService();
		// サービス初期設定の前処理
		DatastoreManager datastoreManager = TaggingEnvUtil.getDatastoreManager();
		if (isEnableAccessLog()) {
			logger.debug("[isEnabled] initMainThreadPreparation start.");
		}
		try {
			datastoreManager.initMainThreadPreparation(serviceName, requestInfo,
					connectionInfo);
		} catch (TaggingException e) {
			throw new IOException(e);
		}
		if (isEnableAccessLog()) {
			logger.debug("[isEnabled] initMainThreadPreparation end.");
		}
		// システム管理サービスの場合true
		if (systemService.equals(serviceName)) {
			return true;
		}
		// サービスエントリーを取得
		String serviceStatus = null;
		try {
			serviceStatus = getServiceStatus(serviceName,
					requestInfo, connectionInfo);

			// サービスの状態は以下の通り。subtitleに設定される。
			//   creating : サービス新規作成中
			//   staging : サービス非公開 (開発中)
			//   production : サービス公開中
			//   blocked : システム管理者による強制停止
			//   resetting : リセット中
			//   deleting : 削除中
			//   failure : サービス登録途中で失敗

			if (Constants.SERVICE_STATUS_STAGING.equals(serviceStatus) ||
					Constants.SERVICE_STATUS_PRODUCTION.equals(serviceStatus)) {
				// サービス稼働中
				return true;
			}

			// メンテナンス失敗の場合の再処理は、再処理リクエストであれば受け付ける。
			if (Constants.SERVICE_STATUS_MAINTENANCE_FAILURE.equals(serviceStatus) &&
					req != null && req.getRequestType() != null) {
				String serverType = TaggingEnvUtil.getSystemProp(TaggingEnvConst.REFLEX_SERVERTYPE, null);
				if (Constants.SERVERTYPE_AP.equals(serverType)) {
					// APサーバ
					String method = requestInfo.getMethod();
					RequestParam param = (RequestParam)req.getRequestType();
					if ((Constants.POST.equals(method) &&
							param.getOption(RequestParam.PARAM_ADDSERVER) != null) ||
						(Constants.DELETE.equals(method) &&
								param.getOption(RequestParam.PARAM_REMOVESERVER) != null))  {
						// サーバ追加かサーバ削除のリトライ処理
						return true;
					}

				} else {
					// その他のサーバはリクエスト判定できないため、ひとまず受け付ける。
					return true;
				}
			}

		} catch (TaggingException e) {
			logger.warn(LogUtil.getRequestInfoStr(requestInfo) +
					"[isEnabled] " + e.getMessage(), e);
		}

		if (logger.isDebugEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[isEnabled] return false. serviceName=");
			sb.append(serviceName);
			sb.append(", serviceStatus=");
			sb.append(serviceStatus);
			logger.debug(sb.toString());
		}
		return false;
	}

	/**
	 * サービス情報の設定がない場合に設定.
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void settingServiceIfAbsent(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		ServiceManagerDefaultSetting serviceSetter = new ServiceManagerDefaultSetting();
		serviceSetter.settingServiceIfAbsent(serviceName, requestInfo, connectionInfo);
	}

	/**
	 * サービス情報クローズ.
	 * static領域にある指定されたサービスの情報を削除する。
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void closeService(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		ServiceManagerDefaultSetting serviceSetter = new ServiceManagerDefaultSetting();
		serviceSetter.closeService(serviceName, requestInfo, connectionInfo);
	}

	/**
	 * static領域からサーバ名・コンテキストパスを取得.
	 * @return サーバ名・コンテキストパス
	 */
	private String getServerContextpath() {
		ServiceManagerDefaultSetting setting = new ServiceManagerDefaultSetting();
		return setting.getServerContextpath();
	}

	/**
	 * static領域からサーバ名・コンテキストパスのサービス名抽出パターンを取得.
	 * @return サーバ名・コンテキストパスのサービス名抽出パターン
	 */
	private Pattern getServerContextpathPattern() {
		ServiceManagerDefaultSetting setting = new ServiceManagerDefaultSetting();
		return setting.getServerContextpathPattern();
	}

	/**
	 * APIKeyを取得.
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return APIKey
	 */
	public String getAPIKey(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		ServiceManagerDefaultSetting setting = new ServiceManagerDefaultSetting();
		ConcurrentMap<String, String> apiKeyMap = setting.getAPIKeyMap();
		if (apiKeyMap.containsKey(serviceName)) {
			return apiKeyMap.get(serviceName);
		}
		return null;
	}

	/**
	 * サービスキーを取得.
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return サービスキー
	 */
	public String getServiceKey(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		ServiceManagerDefaultSetting setting = new ServiceManagerDefaultSetting();
		ConcurrentMap<String, String> serviceKeyMap = setting.getServiceKeyMap();
		if (serviceKeyMap.containsKey(serviceName)) {
			return serviceKeyMap.get(serviceName);
		}
		return null;
	}

	/**
	 * リクエストからサービス名を取得します.
	 * @param req リクエスト
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return サービス名
	 */
	public String getMyServiceName(HttpServletRequest req,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {

		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		// URLのパターンからサービス名を取得
		String serviceName = getPatternName(req);
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
					"MyServiceName by url = " + serviceName);
		}

		// デバッグ用処理
		if (StringUtils.isBlank(serviceName) && getServerContextpathPattern() == null) {
			if (serviceName == null) {
				// X-SERVICENAME指定可 (デバッグ用)
				serviceName = req.getHeader(HEADER_SERVICENAME);
			}
			if (serviceName == null) {
				// サービス名の設定が無い場合は管理サービス
				serviceName = env.getSystemService();
			}
		}

		// サービス名は小文字のみ
		serviceName = editServiceName(serviceName);
		if (logger.isTraceEnabled()) {
			logger.trace("MyServiceName (2) = " + serviceName);
		}
		return serviceName;
	}

	/**
	 * URLパターンからサービス名を取得.
	 * @param req リクエスト
	 * @return サービス名
	 */
	private String getPatternName(HttpServletRequest req) {
		if (req != null) {
			Pattern serverContextpathPattern = getServerContextpathPattern();
			if (serverContextpathPattern != null) {
				String serverContextpath = UrlUtil.getFromServerToContextPath(req);
				Matcher matcher = serverContextpathPattern.matcher(serverContextpath);
				if (matcher.find()) {
					int groupCount = matcher.groupCount();
					if (groupCount > 0) {
						return matcher.group(1);
					}
				}
				// パターンの設定があり、サービス名を抽出できなかった場合はシステム管理サービス
				return TaggingEnvUtil.getSystemService();
			}
		}
		return null;
	}

	/**
	 * サービスエントリーのキーを取得
	 *  /_service/{サービス名} を返却します。
	 * @param serviceName サービス名
	 * @return サービスエントリーのキー
	 */
	public String getServiceUri(String serviceName) {
		return TaggingServiceUtil.getServiceUri(serviceName);
	}

	/**
	 * APIKeyを保持するEntryのURIを取得.
	 *  / (ルートエントリー) を返却します。
	 * @return APIKeyを保持するEntryのURI
	 */
	public static String getAPIKeyUri() {
		return Constants.URI_ROOT;
	}

	/**
	 * APIKeyを更新.
	 * @param reflexContext ReflexContext
	 * @return APIKey
	 */
	public String changeAPIKey(ReflexContext reflexContext)
	throws IOException, TaggingException {
		String apikeyUri = getAPIKeyUri();
		// 検索
		EntryBase entry = reflexContext.getEntry(apikeyUri);
		if (entry == null) {
			throw new IllegalStateException("The entry is null. " + apikeyUri);
		}

		// APIKey
		String apiKey = createAPIKey();
 		List<Contributor> newContributors = new ArrayList<Contributor>();
 		if (entry.contributor != null) {
 			for (Contributor contributor : entry.contributor) {
 				if (contributor.uri != null &&
 						contributor.uri.startsWith(Constants.URN_PREFIX_APIKEY)) {
 					continue;
 				}
 				newContributors.add(contributor);
 			}
 		}
		Contributor apikeyContributor = createAPIKeyContributor(apiKey);
		newContributors.add(apikeyContributor);
		entry.contributor = newContributors;

		// 更新
		reflexContext.put(entry);
		// APIKeyを返却
		return apiKey;
	}

	/**
	 * サービスキーを更新.
	 * @param reflexContext ReflexContext
	 * @return サービスキー
	 */
	public String changeServiceKey(ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceKeyUri = getAPIKeyUri();	// サービスキーとAPIKeyは同じEntryに登録される。
		// 検索
		EntryBase entry = reflexContext.getEntry(serviceKeyUri);
		if (entry == null) {
			throw new IllegalStateException("The entry is null. " + serviceKeyUri);
		}

		// サービスキー
		String serviceKey = createServiceKey();
 		List<Contributor> newContributors = new ArrayList<Contributor>();
 		if (entry.contributor != null) {
 			for (Contributor contributor : entry.contributor) {
 				if (contributor.uri != null &&
 						contributor.uri.startsWith(Constants.URN_PREFIX_SERVICEKEY)) {
 					continue;
 				}
 				newContributors.add(contributor);
 			}
 		}
		Contributor servicekeyContributor = createServiceKeyContributor(serviceKey);
		newContributors.add(servicekeyContributor);
		entry.contributor = newContributors;

		// 更新
		reflexContext.put(entry);
		// サービスキーを返却
		return serviceKey;
	}

	/**
	 * 各サービスのHost名を取得.
	 * @param serviceName サービス名
	 * @return Host名
	 */
	public String getHost(String serviceName) {
		String serverContextpath = getServiceServerContextpath(serviceName);
		return UrlUtil.getHost(serverContextpath);
	}

	/**
	 * 各サービスのHost名+ContextPathを取得.
	 * @param serviceName サービス名
	 * @return Host名+ContextPath
	 */
	@Override
	public String getServiceServerContextpath(String serviceName) {
		String serverContextpath = getServerContextpath();
		if (serverContextpath != null) {
			return serverContextpath.replaceAll("\\@", serviceName);
		}
		return null;
	}

	/**
	 * システム管理サービスから指定されたサービスへリダイレクトする際のURLを取得.
	 * URLはコンテキストパスまでを返却する。
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 指定されたサービスのURL(コンテキストパスまで)
	 */
	public String getRedirectUrlContextPath(String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// ホスト名+コンテキストパスを取得
		String serviceServerContextpath = getServiceServerContextpath(serviceName);
		
		String protocol = null;
		if (serviceServerContextpath != null && 
				serviceServerContextpath.startsWith(SecurityConst.LOCALHOST_PREFIX)) {
			// localhostの場合、プロトコルをhttpにする。
			protocol = ReflexServletConst.SCHEMA_HTTP;
		} else {
			// サービスステータスからプロトコルを取得
			protocol = getProtocolByService(serviceName, requestInfo,
					connectionInfo);
		}

		StringBuilder sb = new StringBuilder();
		sb.append(protocol);
		sb.append("://");
		sb.append(serviceServerContextpath);

		return sb.toString();
	}

	/**
	 * サービスステータスからプロトコルを取得.
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return プロトコル
	 */
	private String getProtocolByService(String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// localhostでなければhttps通信
		// localhostかどうかはこのメソッド呼び出し前に判定済み。
		return ReflexServletConst.SCHEMA_HTTPS;
	}

	/**
	 * サービス生成処理
	 * @param feed サービス情報
	 * @param reflexContext ReflexContext
	 * @return 作成したサービス名
	 */
	@Override
	public String createservice(FeedBase feed, ReflexContext reflexContext)
			throws IOException, TaggingException {
		String systemService = reflexContext.getServiceName();
		// フィードのフォーマットチェック
		String newServiceName = checkCreateService(feed);

		ReflexAuthentication auth = reflexContext.getAuth();
		String uid = auth.getUid();

		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		SystemContext systemContext = new SystemContext(auth,
				requestInfo, connectionInfo);
		NamespaceManager namespaceManager = TaggingEnvUtil.getNamespaceManager();

		// サービス存在チェック
		String uri = getServiceUri(newServiceName);
		EntryBase entry = systemContext.getEntry(uri, false);
		if (entry != null) {
			// 既に登録済みの場合、削除済み(deleted)・登録失敗(failure)かつ
			// サービス登録ユーザと同一であれば再登録
			String serviceStatus = entry.subtitle;
			if ((Constants.SERVICE_STATUS_DELETED.equals(serviceStatus) ||
					Constants.SERVICE_STATUS_FAILURE.equals(serviceStatus)) &&
					uid.equals(entry.rights)) {
				if (logger.isInfoEnabled()) {
					StringBuilder sb = new StringBuilder();
					sb.append(LogUtil.getRequestInfoStr(requestInfo));
					sb.append("[createservice] re-creating the service : ");
					sb.append(newServiceName);
					sb.append(" status=");
					sb.append(serviceStatus);
					logger.info(sb.toString());
				}

				// 処理失敗ステータスの場合、サービス削除処理を実行してからサービス登録処理を行う。
				if (Constants.SERVICE_STATUS_FAILURE.equals(serviceStatus)) {
					// 削除できるのはシステム管理サービス管理者か、指定されたサービスの管理者のみ
					ServiceBlogic serviceBlogic = new ServiceBlogic();
					serviceBlogic.checkOperateServiceAuth(newServiceName, false,
							reflexContext.getRequest(), auth, requestInfo, connectionInfo);
					try {
						deleteserviceProc(newServiceName, false, reflexContext);
					} catch (IOException e) {
						// エラーが発生した場合、とりあえず無視してサービス登録処理を行う。
						StringBuilder sb = new StringBuilder();
						sb.append(LogUtil.getRequestInfoStr(requestInfo));
						sb.append("[createservice] deleteservice due to failure failed. ");
						sb.append(e.getClass().getName());
						logger.warn(sb.toString(), e);
					}
				}

				// 名前空間の再設定
				namespaceManager.changeNamespace(newServiceName, requestInfo, connectionInfo);

				// ステータスを"creating"に更新
				setServiceStatus(entry, Constants.SERVICE_STATUS_CREATING);
				entry = systemContext.put(entry);

			} else if (Constants.SERVICE_STATUS_DELETING.equals(serviceStatus)) {
				// 削除中エラー
				throw new IllegalParameterException("The service is being deleted. " + newServiceName);

			} else {
				StringBuilder sb = new StringBuilder();
				sb.append(EntryDuplicatedException.MESSAGE);
				sb.append(" ");
				sb.append(uri);
				throw new EntryDuplicatedException(sb.toString());
			}
		} else {
			// 新規登録の場合、サービス名にアンダースコアも不可
			checkUnderscore(newServiceName);
			
			// システム管理サービスに登録中ステータスを登録
			entry = TaggingEntryUtil.createEntry(systemService);
			entry.setMyUri(uri);
			setServiceStatus(entry, Constants.SERVICE_STATUS_CREATING);
			entry.rights = uid;	// サービス登録ユーザを仮登録
			// サービス作成者は参照権限のみ
			entry.addContributor(TaggingEntryUtil.getAclContributor(
					Constants.URI_GROUP_ADMIN, Constants.ACL_TYPE_CRUD));
			entry.addContributor(TaggingEntryUtil.getAclContributor(
					uid, Constants.ACL_TYPE_RETRIEVE));

			entry = systemContext.post(entry);
			// 名前空間の設定
			namespaceManager.setNamespace(newServiceName, newServiceName,
					requestInfo, connectionInfo);
		}

		String serviceStatus = null;
		if (TaggingServiceUtil.isBaaS()) {
			serviceStatus = Constants.SERVICE_STATUS_STAGING;
		} else {
			serviceStatus = Constants.SERVICE_STATUS_PRODUCTION;
		}
		try {
			// 新しいサービスのデータストア設定
			createserviceDatastore(newServiceName, serviceStatus, auth, systemContext);

			// 新しいサービスの環境にデータ登録
			createserviceInService(newServiceName, serviceStatus, auth, systemContext);

			// システム管理サービスにデータ登録
			createserviceInSystem(newServiceName, serviceStatus, auth, systemContext);
			
			// プラグイン実行
			createservicePlugin(newServiceName, serviceStatus, auth, systemContext);

			// システム管理サービスに保持する、サービスステータスを「稼働中」にする。
			setServiceStatus(entry, serviceStatus);
			systemContext.put(entry);

		} catch (IOException | TaggingException | RuntimeException | Error e) {
			// 失敗
			setServiceStatus(entry, Constants.SERVICE_STATUS_FAILURE);
			systemContext.put(entry);
			throw e;
		}

		// 新規サービス名を返す
		return newServiceName;
	}

	/**
	 * 新しいサービスのデータストア環境設定.
	 * @param newServiceName サービス名
	 * @param serviceStatus サービスステータス (staging/production)
	 * @param auth 実行ユーザ認証情報
	 * @param reflexContext システム管理サービスのSystemContext
	 */
	private void createserviceDatastore(String newServiceName, String serviceStatus,
			ReflexAuthentication auth, SystemContext reflexContext)
	throws IOException, TaggingException {
		DatastoreManager datastoreManager = TaggingEnvUtil.getDatastoreManager();
		datastoreManager.createservice(newServiceName, serviceStatus, auth, 
				reflexContext.getRequestInfo(), reflexContext.getConnectionInfo());
	}

	/**
	 * 新しいサービスの環境にデータ登録.
	 * @param newServiceName サービス名
	 * @param serviceStatus サービスステータス (staging/production)
	 * @param auth 実行ユーザ認証情報
	 * @param reflexContext システム管理サービスのSystemContext
	 */
	private void createserviceInService(String newServiceName, String serviceStatus,
			ReflexAuthentication auth, SystemContext reflexContext)
	throws IOException, TaggingException {
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		// サービス環境設定
		settingService(newServiceName, requestInfo, connectionInfo);

		// 新しいサービスのSystemContextを生成
		SystemContext newSystemContext = new SystemContext(newServiceName,
				requestInfo, connectionInfo);

		// サービスに必要なエントリーを登録
		FeedBase feed = createCreateservicePostFeed(newServiceName);
		newSystemContext.post(feed);

		// システム管理サービスからユーザ情報を取得する。
		feed = createAdduserParam(auth, newServiceName, reflexContext);
		// 新サービス管理ユーザ登録
		UserManager userManager = TaggingEnvUtil.getUserManager();
		FeedBase retFeed = userManager.adduserByAdmin(feed, newSystemContext);

		if (retFeed == null || retFeed.entry == null || retFeed.entry.isEmpty()) {
			throw new NoExistingEntryException("create service failed. admin user couldn't regist.");
		}

		// 新サービスに登録されたサービス管理ユーザのUIDを取得
		EntryBase newTopEntry = retFeed.entry.get(0);
		String newUid = userManager.getUidByUri(newTopEntry.getMyUri());
		if (logger.isDebugEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
					"[createservice] newUid = " + newUid);
		}

		// サービス管理ユーザに権限設定
		FeedBase postGroupFeed = editGroupEntry(newUid, newServiceName);
		newSystemContext.post(postGroupFeed);
	}

	/**
	 * サービス作成時、システム管理サービスへの初期データ登録
	 * @param newServiceName サービス名
	 * @param serviceStatus サービスステータス (staging/production)
	 * @param auth 実行ユーザ認証情報
	 * @param reflexContext システム管理サービスのSystemContext
	 */
	private void createserviceInSystem(String newServiceName, String serviceStatus,
			ReflexAuthentication auth, SystemContext reflexContext)
	throws IOException, TaggingException {
		String systemService = reflexContext.getServiceName();
		FeedBase putFeed = TaggingEntryUtil.createFeed(systemService);
		String uid = reflexContext.getAuth().getUid();

		// サービスのエイリアスを付加
		String serviceUri = getServiceUri(newServiceName);
		EntryBase entry = reflexContext.getEntry(serviceUri, true);
		setUserAlias(entry, uid, newServiceName);
		putFeed.addEntry(entry);

		// 課金のためのエントリー
		// アクセスカウンタ
		entry = TaggingEntryUtil.createEntry(systemService);
		entry.setMyUri(TaggingServiceUtil.getAccessCountUri(newServiceName));
		entry.contributor = createACLReadonlyContributors(uid);
		putFeed.addEntry(entry);

		// ストレージ合計容量
		entry = TaggingEntryUtil.createEntry(systemService);
		entry.setMyUri(TaggingServiceUtil.getStorageTotalsizeUri(newServiceName));
		entry.contributor = createACLReadonlyContributors(uid);
		putFeed.addEntry(entry);

		reflexContext.put(putFeed);
	}

	/**
	 * サービス作成時、プラグインの実行
	 * @param newServiceName サービス名
	 * @param serviceStatus サービスステータス (staging/production)
	 * @param auth 実行ユーザ認証情報
	 * @param reflexContext システム管理サービスのSystemContext
	 */
	private void createservicePlugin(String newServiceName, String serviceStatus,
			ReflexAuthentication auth, SystemContext reflexContext)
	throws IOException, TaggingException {
		if (logger.isDebugEnabled()) {
			logger.debug("[createservicePlugin] start.");
		}
		// ExecuteAtCreateService インターフェースを実行
		List<ExecuteAtCreateService> executeAtCreateServiceList = 
				TaggingEnvUtil.getExecuteAtCreateServiceList();
		if (executeAtCreateServiceList != null) {
			for (ExecuteAtCreateService executeAtCreateService : executeAtCreateServiceList) {
				if (logger.isDebugEnabled()) {
					logger.debug("[createservicePlugin] doCreateService. " + executeAtCreateService.getClass().getName());
				}
				executeAtCreateService.doCreateService(newServiceName, serviceStatus, auth, reflexContext);
			}
		}
	}

	/**
	 * サービスエントリーにユーザのエイリアスを追加.
	 *   /_service/{サービス名} エントリーのエイリアスに
	 *   /_user/{UID}/service/{サービス名} を付ける。
	 * @param entry エントリー
	 * @param uid UID
	 * @param newServiceName サービス名
	 */
	private void setUserAlias(EntryBase entry, String uid, String newServiceName) {
		String userServiceUri = TaggingServiceUtil.getUserServiceUri(uid);
		StringBuilder sb = new StringBuilder();
		sb.append(userServiceUri);
		sb.append("/");
		sb.append(newServiceName);

		entry.addAlternate(sb.toString());
	}

	/**
	 * サービス登録処理の入力チェック.
	 * @param feed 入力Feed
	 * @return サービス名
	 */
	private String checkCreateService(FeedBase feed) {
		// 値が設定されていること
		CheckUtil.checkFeed(feed, false);

		// entry、link self必須
		EntryBase entry = TaggingEntryUtil.getFirstEntry(feed);
		CheckUtil.checkEntryKey(entry);
		// キーは /@{サービス名} であること
		String myUri = entry.getMyUri();
		if (!isServiceUri(myUri)) {
			throw new IllegalParameterException("Request format is invalid: Key.");
		}

		// サービス名は英数字、ハイフン、アンダースコアのみの2文字以上であること。
		// 予約語と合致しないこと。
		String serviceName = getServiceNameFromUri(myUri);
		checkServicename(serviceName);

		// サービス名は小文字のみとする
		serviceName = editServiceName(serviceName);

		return serviceName;
	}

	/**
	 * サービス名を小文字変換.
	 * @param serviceName サービス名
	 * @return 小文字変換したサービス名
	 */
	public String editServiceName(String serviceName) {
		if (!StringUtils.isBlank(serviceName)) {
			return serviceName.toLowerCase(Locale.ENGLISH);
		}
		return null;
	}

	/**
	 * サービスのURI(/@{サービス名})かどうかを返却します.
	 * @param uri URI
	 * @return サービスのURIの場合true
	 */
	private boolean isServiceUri(String uri) {
		if (!StringUtils.isBlank(uri)) {
			int idx = uri.indexOf("/", 1);
			if (idx == -1) {
				if (uri.startsWith(Constants.URI_SERVICE_PREFIX) &&
						uri.length() > URI_SERVICE_PREFIX_LEN) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * URI(/@で始まる)からサービス名を取得.
	 * @param uri URI
	 * @return サービス名
	 */
	protected String getServiceNameFromUri(String uri) {
		if (uri == null || !uri.startsWith(Constants.URI_SERVICE_PREFIX)) {
			return null;
		}
		int idx = uri.indexOf("/", 1);
		if (idx < 0) {
			idx = uri.length();
		}
		return uri.substring(2, idx);
	}

	/**
	 * サービス名チェック.
	 * 文字種をチェックする。
	 * @param str サービス名
	 */
	public void checkServicename(String str) {
		// 入力なしはエラー
		if (StringUtils.isBlank(str)) {
			// return;
			throw new IllegalParameterException("A service name is required.");
		}
		// 1文字はエラー
		if (str.length() < 2) {
			throw new IllegalParameterException("Please enter at least two characters in the service name.");
		}
		// 文字種チェック
		Matcher matcher = PATTERN_SERVICENAME.matcher(str);
		if (!matcher.matches()) {
			throw new IllegalParameterException("Please enter the alphanumeric or '-'. : " + str);
		}
		// 予約語はエラー
		Set<String> reservedNames = TaggingEnvUtil.getSystemPropSet(
				ServiceManagerDefaultConst.PROP_SERVICE_RESERVED_PREFIX);
		if (reservedNames.contains(str)) {
			throw new IllegalParameterException("The service name is reserved. " + str);
		}
	}

	/**
	 * サービス名チェック.
	 * 文字種をチェックする。
	 * @param str サービス名
	 */
	private void checkUnderscore(String str) {
		if (str.indexOf("_") > -1) {
			throw new IllegalParameterException("Please enter the alphanumeric or '-'. : " + str);
		}
	}
	
	/**
	 * エントリーに指定されたサービスステータスを設定する.
	 * @param entry エントリー
	 * @param status サービスステータス
	 */
	private void setServiceStatus(EntryBase entry, String status) {
		entry.subtitle = status;
		entry.id = null;	// 楽観的排他チェックを行わない
	}

	/**
	 * サービスステータスを取得.
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return サービスステータス。サービスが存在しない場合はnull。
	 */
	public String getServiceStatus(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// サービスエントリーを取得
		EntryBase serivceEntry = getServiceEntry(serviceName, true,
				requestInfo, connectionInfo);
		// サービスステータスを取得
		return getServiceStatus(serivceEntry);
	}

	/**
	 * サービスエントリーを取得.
	 * subtitleにサービスステータスが設定されている。
	 * @param serviceName サービス名
	 * @param useCache キャッシュを使用する場合true
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return サービスエントリー。サービスが存在しない場合はnull。
	 */
	public EntryBase getServiceEntry(String serviceName, boolean useCache,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// サービスエントリーを取得
		// サービスステータスを保持するサービスエントリーはシステム管理サービスにある。
		String systemService = TaggingEnvUtil.getSystemService();
		SystemContext systemContext = new SystemContext(systemService,
				requestInfo, connectionInfo);
		String serviceUri = getServiceUri(serviceName);
		return systemContext.getEntry(serviceUri, useCache);
	}

	/**
	 * エントリーから指定されたサービスステータスを設定する.
	 * @param entry エントリー
	 * @return サービスステータス
	 */
	private String getServiceStatus(EntryBase entry) {
		return TaggingServiceUtil.getServiceStatus(entry);
	}

	/**
	 * サービス登録で登録するエントリーを作成.
	 * @param serviceName サービス名
	 * @return サービス登録で登録するエントリー
	 */
	private FeedBase createCreateservicePostFeed(String serviceName) {
		List<EntryBase> entries = new ArrayList<EntryBase>();

		// / (contributorのuriにAPIKey、サービスキー、ACL)
		EntryBase entry = TaggingEntryUtil.createEntry(serviceName);
		entry.setMyUri(Constants.URI_ROOT);
		// APIKey
		Contributor contributor = createAPIKeyContributor();
		entry.addContributor(contributor);
		// サービスキー
		contributor = createServiceKeyContributor();
		entry.addContributor(contributor);
		// ACL
		contributor = createACLAdminContributor();
		entry.addContributor(contributor);
		entries.add(entry);

		// プロパティファイルに指定されたフォルダ
		List<EntryBase> folders = getCreateservicePostFolders(serviceName);
		if (folders != null && !folders.isEmpty()) {
			entries.addAll(folders);
		}

		FeedBase feed = TaggingEntryUtil.createFeed(serviceName);
		feed.entry = entries;
		return feed;
	}

	/**
	 * 定数に指定された初期登録フォルダ情報をエントリーにして返却
	 * @param serviceName サービス
	 * @return 初期登録フォルダのエントリーリスト
	 */
	private List<EntryBase> getCreateservicePostFolders(String serviceName) {
		List<EntryBase> entries = new ArrayList<EntryBase>();
		String[][] initFoldersArray = ServiceManagerDefaultConst.CREATESERVICE_POSTFOLDER;
		String[][] initFoldersAclArray = ServiceManagerDefaultConst.CREATESERVICE_POSTFOLDER_ACL;
		if (initFoldersArray != null) {
			for (String[] initFolderInfo : initFoldersArray) {
				String uri = initFolderInfo[1];
				EntryBase entry = TaggingEntryUtil.createEntry(serviceName);
				entry.setMyUri(uri);

				// ACL設定があれば設定
				String number = initFolderInfo[0];

				String[] folderAcls = getFolderAcls(initFoldersAclArray, number);
				if (folderAcls != null) {
					for (String folderAcl : folderAcls) {
						Contributor contributor = createACLContributor(folderAcl);
						entry.addContributor(contributor);
					}
				}

				entries.add(entry);
			}
		}
		return entries;
	}
	
	/**
	 * 指定された番号のフォルダACL情報を抽出して配列にして返却
	 * @param initFoldersAclArray フォルダACL情報配列
	 * @param number フォルダ番号
	 * @return 指定された番号のフォルダACL配列
	 */
	private String[] getFolderAcls(String[][] initFoldersAclArray, String number) {
		List<String> folderAcls = new ArrayList<>();
		if (initFoldersAclArray != null) {
			for (String[] initFolderAclInfo : initFoldersAclArray) {
				if (number.equals(initFolderAclInfo[0])) {
					folderAcls.add(initFolderAclInfo[2]);
				}
			}
		}
		if (!folderAcls.isEmpty()) {
			return folderAcls.toArray(new String[folderAcls.size()]);
		}
		return null;
	}

	/**
	 * APIKeyをセットしたContributor生成
	 * @return APIKeyをセットしたContributor
	 */
	private Contributor createAPIKeyContributor() {
		return createAPIKeyContributor(createAPIKey());
	}

	/**
	 * サービスキーをセットしたContributor生成
	 * @return サービスキーをセットしたContributor
	 */
	private Contributor createServiceKeyContributor() {
		return createServiceKeyContributor(createServiceKey());
	}

	/**
	 * APIKeyをセットしたContributor生成
	 * @param apiKey APIKey
	 * @return APIKeyをセットしたContributor
	 */
	private Contributor createAPIKeyContributor(String apiKey) {
		StringBuilder sb = new StringBuilder();
		sb.append(Constants.URN_PREFIX_APIKEY);
		sb.append(apiKey);

		Contributor contributor = new Contributor();
		contributor.uri = sb.toString();
		return contributor;
	}

	/**
	 * サービスキーをセットしたContributor生成
	 * @param serviceKey サービスキー
	 * @return サービスキーをセットしたContributor
	 */
	private Contributor createServiceKeyContributor(String serviceKey) {
		StringBuilder sb = new StringBuilder();
		sb.append(Constants.URN_PREFIX_SERVICEKEY);
		sb.append(serviceKey);

		Contributor contributor = new Contributor();
		contributor.uri = sb.toString();
		return contributor;
	}

	/**
	 * APIKey発行
	 * @return
	 */
	private String createAPIKey() {
		return NumberingUtil.randomString(APIKEY_STRING_LEN);
	}

	/**
	 * サービスキー発行
	 * @return
	 */
	private String createServiceKey() {
		return NumberingUtil.randomString(SERVICEKEY_STRING_LEN);
	}

	/**
	 * ACL指定contributorを生成
	 * @param acl ACL
	 * @return ACL指定contributor
	 */
	private Contributor createACLContributor(String acl) {
		StringBuilder sb = new StringBuilder();
		sb.append(Constants.URN_PREFIX_ACL);
		sb.append(acl);

		Contributor contributor = new Contributor();
		contributor.uri = sb.toString();
		return contributor;
	}

	/**
	 * サービス管理者ACLをセットしたContributorを生成
	 * @return サービス管理者ACLをセットしたContributor
	 */
	private Contributor createACLAdminContributor() {
		StringBuilder sb = new StringBuilder();
		sb.append(Constants.URI_GROUP_ADMIN);
		sb.append(Constants.ACL_DELIMITER);
		sb.append(Constants.ACL_TYPE_CRUD);

		return createACLContributor(sb.toString());
	}

	/**
	 * createserviceでサービス管理ユーザを登録するための引数を生成
	 * @param auth 認証情報
	 * @param systemContext SystemContext (システム管理サービスのReflexContext)
	 * @return ユーザ登録引数
	 */
	private FeedBase createAdduserParam(ReflexAuthentication auth,
			String newServiceName, SystemContext systemContext)
	throws IOException, TaggingException {
		// ユーザ登録には、メールアドレスとパスワードが必要
		String uid = auth.getUid();

		UserManager userManager = TaggingEnvUtil.getUserManager();
		String password = userManager.getPasswordByUid(uid, systemContext);
		String nickname = userManager.getNicknameByUid(uid, systemContext);
		String email = userManager.getEmailByUid(uid, systemContext);
		Contributor contributor = createAdduserContributor(email, password,
				nickname);
		EntryBase entry = TaggingEntryUtil.createEntry(newServiceName);
		entry.addContributor(contributor);
		FeedBase feed = TaggingEntryUtil.createFeed(newServiceName);
		feed.addEntry(entry);
		return feed;
	}

	/**
	 * AdduserをセットしたContributor生成
	 * @param account アカウント
	 * @param password パスワード
	 * @param nickname ニックネーム
	 * @return AdduserをセットしたContributor
	 */
	private Contributor createAdduserContributor(String account, String password,
			String nickname) {
		StringBuilder sb = new StringBuilder();
		sb.append(Constants.URN_PREFIX_AUTH);
		sb.append(account);
		sb.append(UserUtil.URN_AUTH_PASSWORD_START);
		sb.append(password);

		Contributor contributor = new Contributor();
		contributor.uri = sb.toString();
		contributor.name = nickname;

		return contributor;
	}

	/**
	 * グループエントリーにサービス管理者の権限を追加.
	 * @param postFeed Feed
	 * @param uid UID
	 * @param newServiceName サービス名
	 * @return グループエントリーリスト
	 */
	private FeedBase editGroupEntry(String uid, String newServiceName) {
		List<EntryBase> entries = new ArrayList<EntryBase>();

		// /_group/$admin/{UID}
		EntryBase entry = TaggingEntryUtil.createEntry(newServiceName);
		entry.setMyUri(editGroupUri(uid, Constants.URI_GROUP_ADMIN));
		entry.addAlternate(editGroupAlias(uid, Constants.URI_$ADMIN));
		entries.add(entry);

		// /_group/$content/{UID}
		entry = TaggingEntryUtil.createEntry(newServiceName);
		entry.setMyUri(editGroupUri(uid, Constants.URI_GROUP_CONTENT));
		entry.addAlternate(editGroupAlias(uid, Constants.URI_$CONTENT));
		entries.add(entry);

		// /_group/$useradmin/{UID}
		entry = TaggingEntryUtil.createEntry(newServiceName);
		entry.setMyUri(editGroupUri(uid, Constants.URI_GROUP_USERADMIN));
		entry.addAlternate(editGroupAlias(uid, Constants.URI_$USERADMIN));
		entries.add(entry);

		FeedBase putFeed = TaggingEntryUtil.createFeed(newServiceName);
		putFeed.entry = entries;
		return putFeed;
	}

	/**
	 * グループURI配下のURIを生成
	 * @param uid UID
	 * @param groupUri グループURI
	 * @return グループURI配下のURI
	 */
	private String editGroupUri(String uid, String groupUri) {
		StringBuilder sb = new StringBuilder();
		sb.append(groupUri);
		sb.append("/");
		sb.append(uid);
		return sb.toString();
	}

	/**
	 * 自ユーザ情報配下のグループ情報エイリアスを生成
	 * @param uid UID
	 * @param groupName グループ名
	 * @return 自ユーザ情報配下のグループ情報エイリアス
	 */
	private String editGroupAlias(String uid, String groupName) {
		UserManager userManager = TaggingEnvUtil.getUserManager();
		StringBuilder sb = new StringBuilder();
		sb.append(userManager.getGroupUriByUid(uid));
		sb.append(groupName);
		return sb.toString();
	}

	/**
	 * サービス管理者ACLをセットしたContributorを生成
	 * @param uid サービス管理者UID
	 * @return サービス管理者ACLをセットしたContributor
	 */
	private List<Contributor> createACLReadonlyContributors(String uid) {
		List<Contributor> contributors = new ArrayList<Contributor>();

		StringBuilder sb = new StringBuilder();
		sb.append(Constants.URN_PREFIX_ACL);
		sb.append(Constants.URI_GROUP_ADMIN);
		sb.append(Constants.ACL_DELIMITER);
		sb.append(Constants.ACL_TYPE_RETRIEVE);
		Contributor contributor = new Contributor();
		contributor.uri = sb.toString();
		contributors.add(contributor);

		sb = new StringBuilder();
		sb.append(Constants.URN_PREFIX_ACL);
		sb.append(uid);
		sb.append(Constants.ACL_DELIMITER);
		sb.append(Constants.ACL_TYPE_RETRIEVE);
		contributor = new Contributor();
		contributor.uri = sb.toString();
		contributors.add(contributor);

		return contributors;
	}

	/**
	 * サービス削除.
	 * @param delServiceName 削除サービス名
	 * @param reflexContext 削除サービスのReflexContext
	 */
	@Override
	public void deleteservice(String delServiceName, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 同期処理にする
		deleteserviceProc(delServiceName, false, reflexContext);
	}

	/**
	 * サービス削除.
	 * @param delServiceName 削除サービス名
	 * @param async 非同期処理の場合true
	 * @param reflexContext 削除サービスのReflexContext
	 * @param delServiceAuth 削除サービスの管理者認証情報
	 */
	private void deleteserviceProc(String delServiceName, boolean async,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		ReflexAuthentication systemServiceAuth = reflexContext.getAuth();
		SystemContext systemContext = new SystemContext(systemServiceAuth,
				requestInfo, connectionInfo);

		// サービスステータスが削除済み、削除中の場合は処理しない。
		String uri = getServiceUri(delServiceName);
		EntryBase entry = systemContext.getEntry(uri, false);
		String serviceStatus = getServiceStatus(entry);
		if (StringUtils.isBlank(serviceStatus) || Constants.SERVICE_STATUS_DELETED.equals(serviceStatus)) {
			throw new IllegalParameterException("The service has been deleted. " + delServiceName);
		}
		if (Constants.SERVICE_STATUS_DELETING.equals(serviceStatus)) {
			throw new IllegalParameterException("The service is being deleted. " + delServiceName);
		}
		// サービス作成処理失敗状態かどうか
		boolean isFailure = Constants.SERVICE_STATUS_FAILURE.equals(serviceStatus);

		// サービスステータスを「削除中」に更新。(システム管理サービス)
		setServiceStatus(entry, Constants.SERVICE_STATUS_DELETING);
		entry = systemContext.put(entry);

		if (isFailure) {
			// サービス作成処理失敗状態の場合、割り当てたBDBサーバを削除する。
			DatastoreManager datastoreManager = TaggingEnvUtil.getDatastoreManager();
			datastoreManager.resetCreateservice(delServiceName, systemServiceAuth,
					requestInfo, connectionInfo);
		}

		if (async) {
			// ここから非同期処理
			DeleteServiceDefaultCallable callable = new DeleteServiceDefaultCallable(
					delServiceName, entry);
			TaskQueueUtil.addTask(callable, 0, systemServiceAuth, requestInfo,
					connectionInfo);
		} else {
			// createservice実行時、削除失敗サービスを再削除する場合は同期処理
			deleteserviceCallable(delServiceName, entry, reflexContext);
		}
	}

	/**
	 * サービス削除.
	 * 非同期処理から呼ばれる処理
	 * @param delServiceName 削除サービス名
	 * @param entry サービスエントリー (システム管理サービスの /_service/{サービス名} エントリー)
	 * @param reflexContext 削除サービスのReflexContext
	 */
	public void deleteserviceCallable(String delServiceName, EntryBase entry,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		ReflexAuthentication systemServiceAuth = reflexContext.getAuth();
		SystemContext systemContext = new SystemContext(systemServiceAuth,
				requestInfo, connectionInfo);

		try {
			// サービスステータスを削除済みにする。(システム管理サービス)
			setServiceStatus(entry, Constants.SERVICE_STATUS_DELETED);
			systemContext.put(entry);

		} catch (IOException | TaggingException e) {
			// 削除失敗
			setServiceStatus(entry, Constants.SERVICE_STATUS_FAILURE);
			systemContext.put(entry);
			throw e;
		}
	}

	/**
	 * サービス管理ユーザの認証情報を取得
	 * @param serviceName サービス名
	 * @return サービス管理ユーザの認証情報
	 */
	public ReflexAuthentication createServiceAdminAuth(String serviceName) {
		AuthenticationManager authManager = TaggingEnvUtil.getAuthenticationManager();
		ReflexAuthentication auth = authManager.createAuth(
				AuthenticationConst.ACCOUNT_SERVICEADMIN,
				AuthenticationConst.UID_SERVICEADMIN, null,
				Constants.AUTH_TYPE_SYSTEM, serviceName);
		auth.addGroup(AtomConst.URI_GROUP_ADMIN);
		auth.addGroup(AtomConst.URI_GROUP_CONTENT);
		auth.addGroup(AtomConst.URI_GROUP_USERADMIN);
		return auth;
	}

	/**
	 * http・httpsリクエストチェック.
	 * (2025.11.20)当初はサービスステータスごとのチェックだったが、サービスステータスに関係なくhttps通信とするよう修正。
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @return 処理継続可能な場合true、終了(リダイレクト)の場合false
	 */
	public boolean checkServiceStatus(ReflexRequest req, ReflexResponse resp)
	throws IOException, TaggingException {
		String serviceName = req.getServiceName();
		String serviceStatus = getServiceStatus(serviceName,
				req.getRequestInfo(), req.getConnectionInfo());
		String scheme = req.getScheme();
		if (Constants.SERVICE_STATUS_PRODUCTION.equals(serviceStatus)) {
			// productionにおいて、https以外でアクセスしてきた場合
			
			// テスト環境(localhost)は処理を抜ける。
			String host = req.getHeader(ReflexServletConst.HEADER_HOST);
			if (host != null && host.startsWith(SecurityConst.LOCALHOST_PREFIX)) {
				// Do nothing.
			} else {
				if (!ReflexServletConst.SCHEMA_HTTPS.equals(scheme)) {
					// httpsリクエストでない場合
					if (ReflexServletConst.GET.equalsIgnoreCase(req.getMethod())) {
						// GETメソッドであればhttpsに強制的にリダイレクトする。
						String location = getHttpsUrl(req);
						resp.sendRedirect(location);
						return false;
	
					} else {
						// その他のメソッドであればステータス405(Method Not Allowed)を返す。
						throw new MethodNotAllowedException("Not Accepted. " + serviceName);
					}
				}
			}
		}	// stagingの場合はチェックしない

		return true;
	}

	/**
	 * リクエストのURLのうち、プロトコルをhttpsに変換したものを返却する.
	 * @param req リクエスト
	 * @return httpsに変換したURL
	 */
	private String getHttpsUrl(HttpServletRequest req) {
		StringBuilder sb = new StringBuilder();
		sb.append(ReflexServletConst.SCHEMA_HTTPS);
		sb.append("://");
		sb.append(UrlUtil.getFromServerToContextPath(req));

		String servletPath = req.getServletPath();
		String pathInfo = req.getPathInfo();
		if (StringUtils.isBlank(pathInfo)) {
			sb.append(StringUtils.null2blank(servletPath));
		} else {
			if (!isIntactHtmlUrl() && RequestParam.SERVLET_PATH.equals(servletPath) &&
					(pathInfo.equals(Constants.URI_HTML) ||
							pathInfo.startsWith(URI_HTML_SLASH))) {
				// ServletPath + PathInfoの先頭が /d/_html の場合除去する。
				// この場合サーブレットパスは付加しない。
				String cutHtmlUri = TaggingEntryUtil.cutHtmlUri(pathInfo);
				sb.append(cutHtmlUri);
			} else {
				// サーブレットパス
				sb.append(servletPath);
				// PathInfo
				sb.append(pathInfo);
			}
		}

		String queryString = req.getQueryString();
		if (!StringUtils.isBlank(queryString)) {
			sb.append("?");
			sb.append(queryString);
		}

		return sb.toString();
	}

	/**
	 * HTML格納フォルダ(/d/_html)を省略せず元のままとするかどうか、を取得.
	 * @return HTML格納フォルダ(/d/_html)を省略せず元のままとする場合true
	 */
	private boolean isIntactHtmlUrl() {
		return TaggingEnvUtil.getSystemPropBoolean(TaggingEnvConst.IS_INTACT_HTMLURL, false);
	}

	/**
	 * サービス公開.
	 * @param targetServiceName 対象サービス名
	 * @param reflexContext システム管理サービスのReflexContext
	 * @return 公開したサービス名
	 */
	public String serviceToProduction(String targetServiceName, ReflexContext reflexContext)
	throws IOException, TaggingException {
		return changeServiceStatus(targetServiceName, Constants.SERVICE_STATUS_PRODUCTION,
				Constants.SERVICE_STATUS_STAGING, reflexContext);
	}

	/**
	 * サービス非公開.
	 * @param targetServiceName 対象サービス名
	 * @param reflexContext システム管理サービスのReflexContext
	 * @return 非公開にしたサービス名
	 */
	public String serviceToStaging(String targetServiceName, ReflexContext reflexContext)
	throws IOException, TaggingException {
		return changeServiceStatus(targetServiceName, Constants.SERVICE_STATUS_STAGING,
				Constants.SERVICE_STATUS_PRODUCTION, reflexContext);
	}

	/**
	 * サービスステータス変更.
	 * @param targetServiceName 対象サービス名
	 * @param serviceStatus 設定するサービスステータス
	 * @param expectedServiceStatus 現在のサービスステータス。このステータスでなければエラーとする。
	 * @param reflexContext システム管理サービスのReflexContext
	 * @return サービス名
	 */
	private String changeServiceStatus(String targetServiceName, String serviceStatus,
			String expectedServiceStatus, ReflexContext reflexContext)
	throws IOException, TaggingException {
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		ReflexAuthentication systemServiceAuth = reflexContext.getAuth();
		SystemContext systemContext = new SystemContext(systemServiceAuth,
				requestInfo, connectionInfo);

		// 指定サービスが存在しなければエラー
		String uri = getServiceUri(targetServiceName);
		EntryBase entry = systemContext.getEntry(uri, false);
		String currentServiceStatus = getServiceStatus(entry);
		if (StringUtils.isBlank(currentServiceStatus)) {
			throw new IllegalParameterException("The service does not exist. " + targetServiceName);
		}

		// 指定サービスが期待するステータスでなければエラー
		if (!expectedServiceStatus.equals(currentServiceStatus)) {
			StringBuilder sb = new StringBuilder();
			sb.append("The service status is not '");
			sb.append(expectedServiceStatus);
			sb.append("'. serviceStatus = ");
			sb.append(currentServiceStatus);
			sb.append(", target serviceName = ");
			sb.append(targetServiceName);
			throw new IllegalParameterException(sb.toString());
		}

		if (TaggingEnvUtil.getSystemService().equals(targetServiceName)) {
			// 指定サービスがシステム管理サービスの場合、ステータスを更新して終了。
			setServiceStatus(entry, serviceStatus);
			entry = systemContext.put(entry);

		} else {
			// 指定サービスが一般サービスの場合、BDB再割り当て、データ移行処理
			DatastoreManager datastoreManager = TaggingEnvUtil.getDatastoreManager();
			datastoreManager.changeServiceStatus(targetServiceName, serviceStatus,
					reflexContext);
		}

		return targetServiceName;
	}

	/**
	 * アクセスカウンタをインクリメント.
	 * アクセス数が制限値を超えている場合、PaymentRequiredExceptionを返す。
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void incrementAccessCounter(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// BaaSでない場合処理を抜ける
		if (!TaggingServiceUtil.isBaaS()) {
			return;
		}
		// システム管理サービスにてアクセスカウンタをインクリメント
		SystemContext systemContext = new SystemContext(TaggingEnvUtil.getSystemService(),
				requestInfo, connectionInfo);
		String uri = TaggingServiceUtil.getAccessCountTodayUri(serviceName);
		if (logger.isTraceEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
					"[incrementAccessCounter] uri: " + uri + " start.");
		}
		long count = systemContext.incrementCache(uri, 1);
		if (logger.isTraceEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[incrementAccessCounter] uri: " + uri + " , count: " + count);
		}
		// 本日分アクセスカウンタの有効期限を設定
		int expireHour = TaggingEnvUtil.getSystemPropInt(
				ServiceManagerDefaultConst.PROP_ACCESSCOUNT_EXPIRE_HOUR,
				ServiceManagerDefaultConst.ACCESSCOUNT_EXPIRE_HOUR_DEFAULT);
		int expireSec = expireHour * 60 * 60;
		systemContext.setExpireCacheLong(uri, expireSec);

		// アクセス数制限チェック
		// サービスステータスを確認
		String serviceStatus = getServiceStatus(serviceName, requestInfo, connectionInfo);
		if (Constants.SERVICE_STATUS_PRODUCTION.equals(serviceStatus)) {
			// productionサービスはアクセス数制限を行わない。
			return;
		}
		long maxCount = TaggingEnvUtil.getSystemPropLong(ServiceConst.PROP_MAX_ACCESS_COUNT,
				ServiceConst.MAX_ACCESS_COUNT_DEFAULT);
		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append("[incrementAccessCounter] uri: ");
			sb.append(uri);
			sb.append(" , maxCount: ");
			sb.append(maxCount);
			sb.append(" , count: ");
			sb.append(count);
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + sb.toString());
		}
		if (count > maxCount) {
			throw new PaymentRequiredException(ServiceConst.MSG_OVER_ACCESS_COUNT);
		}
	}

	/**
	 * サービスに関するStatic情報を更新する必要があるかどうかチェックする.
	 * @param targetServiceName 対象サービス名
	 * @param accessTime Static情報更新時間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return サービスエントリー
	 */
	public boolean isNeedToUpdateStaticInfo(String targetServiceName, Date accessTime,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		boolean getFromBDB = StaticInfoUtil.isExceeded(accessTime);
		if (!getFromBDB) {
			// サービスエントリー更新時間をチェック (サービスステータスが更新されているとみなす)
			EntryBase serviceEntry = getServiceEntry(targetServiceName, true,
					requestInfo, connectionInfo);
			if (serviceEntry == null) {
				return true;
			}
			Date updatedDate = null;
			try {
				updatedDate = DateUtil.getDate(serviceEntry.updated);
			} catch (ParseException e) {
				throw new IllegalStateException(e);
			}
			// サービスエントリー更新時間がStatic情報設定の後であれば要更新。
			if (updatedDate.after(accessTime)) {
				getFromBDB = true;
				if (logger.isTraceEnabled()) {
					StringBuilder sb = new StringBuilder();
					sb.append(LogUtil.getRequestInfoStr(requestInfo));
					sb.append("[isNeedToUpdateStaticInfo] true. updatedDate=");
					sb.append(serviceEntry.updated);
					sb.append(", accessTime=");
					sb.append(DateUtil.getDateTimeFormat(accessTime, "yyyy-MM-dd HH:mm:ss.SSS"));
					logger.debug(sb.toString());
				}
			}
		}
		return getFromBDB;
	}

	/**
	 * サービス初期設定に必要なURIリスト.
	 * サービス初期設定に必要なシステム管理サービスのURIリストを返却する。
	 * @param serviceName サービス名
	 * @return URIリスト
	 */
	@Override
	public List<String> getSettingEntryUrisBySystem(String serviceName) {
		List<String> uris = new ArrayList<>();
		uris.add(getServiceUri(serviceName));
		return uris;
	}

	/**
	 * サービス初期設定に必要なFeed検索用URIリスト.
	 * サービス初期設定に必要なシステム管理サービスのFeed検索用URIリストを返却する。
	 * @param serviceName サービス名
	 * @return URIリスト
	 */
	@Override
	public List<String> getSettingFeedUrisBySystem(String serviceName) {
		return null;
	}

	/**
	 * サービス初期設定に共通して必要なURIリスト.
	 * サービス初期設定に共通して必要なシステム管理サービスのURIリストを返却する。
	 * @return URIリスト
	 */
	@Override
	public List<String> getSettingEntryUrisBySystem() {
		List<String> uris = new ArrayList<>();
		uris.add(Constants.URI_SERVICE);
		return uris;
	}

	/**
	 * サービス初期設定に共通して必要なURIリスト.
	 * サービス初期設定に共通して必要なシステム管理サービスのFeed検索用URIリストを返却する。
	 * @return URIリスト
	 */
	@Override
	public List<String> getSettingFeedUrisBySystem() {
		return null;
	}

	/**
	 * サービス初期設定に共通して必要なURIリスト.
	 * サービス初期設定に共通して必要な自サービスのURIリストを返却する。
	 * @return URIリスト
	 */
	@Override
	public List<String> getSettingEntryUris() {
		// 汎用システムディレクトリをここで定義する。
		List<String> uris = new ArrayList<>();
		uris.add(Constants.URI_HTML);	// /_html
		uris.add(Constants.URI_LOG);	// /_log
		uris.add(Constants.URI_LOGIN_HISTORY);	// /_login_history
		uris.add(Constants.URI_USER);	// /_user
		return uris;
	}

	/**
	 * サービス初期設定に共通して必要なURIリスト.
	 * サービス初期設定に共通して必要な自サービスのFeed検索用URIリストを返却する。
	 * @return URIリスト
	 */
	@Override
	public List<String> getSettingFeedUris() {
		// 汎用システムディレクトリをここで定義する。
		List<String> uris = new ArrayList<>();
		uris.add(Constants.URI_GROUP);	// /_group
		return uris;
	}

	/**
	 * サービス初期設定時の処理.
	 * 実行ノードで指定されたサービスが初めて実行された際に呼び出されます。
	 * この処理は他のスレッドを排他して呼ばれます。
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void settingService(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// APIKeyの設定
		ServiceManagerDefaultSetting setting = new ServiceManagerDefaultSetting();
		setting.checkSettingAPIKey(serviceName, requestInfo, connectionInfo);
	}

	/**
	 * サービス設定のアクセスログ（処理経過ログ）を出力するかどうか.
	 * テスト用
	 * @return サービス設定のアクセスログ（処理経過ログ）を出力する場合true
	 */
	private boolean isEnableAccessLog() {
		return TaggingEnvUtil.getSystemPropBoolean(
				ServiceManagerDefaultConst.SERVICESETTING_ENABLE_ACCESSLOG, false);
	}

}
