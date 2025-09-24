package jp.reflexworks.taggingservice.env;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TimeZone;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.servlet.util.PropertyContextUtil;
import jp.reflexworks.servlet.util.ServletContextUtil;
import jp.reflexworks.taggingservice.api.ReflexEnv;
import jp.reflexworks.taggingservice.api.ReflexStatic;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.reflexworks.taggingservice.exception.PluginException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.AccessTokenManager;
import jp.reflexworks.taggingservice.plugin.AclManager;
import jp.reflexworks.taggingservice.plugin.AllocateIdsManager;
import jp.reflexworks.taggingservice.plugin.AuthenticationManager;
import jp.reflexworks.taggingservice.plugin.BigQueryManager;
import jp.reflexworks.taggingservice.plugin.CacheManager;
import jp.reflexworks.taggingservice.plugin.CallingAfterCommit;
import jp.reflexworks.taggingservice.plugin.CaptchaManager;
import jp.reflexworks.taggingservice.plugin.ClosingForShutdown;
import jp.reflexworks.taggingservice.plugin.ContentManager;
import jp.reflexworks.taggingservice.plugin.DatastoreManager;
import jp.reflexworks.taggingservice.plugin.EMailManager;
import jp.reflexworks.taggingservice.plugin.ExecuteAtCreateService;
import jp.reflexworks.taggingservice.plugin.IncrementManager;
import jp.reflexworks.taggingservice.plugin.LogManager;
import jp.reflexworks.taggingservice.plugin.LoginLogoutManager;
import jp.reflexworks.taggingservice.plugin.MemorySortManager;
import jp.reflexworks.taggingservice.plugin.MessageManager;
import jp.reflexworks.taggingservice.plugin.MonitorManager;
import jp.reflexworks.taggingservice.plugin.NamespaceManager;
import jp.reflexworks.taggingservice.plugin.OAuthManager;
import jp.reflexworks.taggingservice.plugin.PdfManager;
import jp.reflexworks.taggingservice.plugin.PluginUtil;
import jp.reflexworks.taggingservice.plugin.PropertyManager;
import jp.reflexworks.taggingservice.plugin.PushNotificationManager;
import jp.reflexworks.taggingservice.plugin.RDBManager;
import jp.reflexworks.taggingservice.plugin.RXIDManager;
import jp.reflexworks.taggingservice.plugin.ReflexPlugin;
import jp.reflexworks.taggingservice.plugin.ReflexSecurityManager;
import jp.reflexworks.taggingservice.plugin.RequestResponseManager;
import jp.reflexworks.taggingservice.plugin.ResourceMapperManager;
import jp.reflexworks.taggingservice.plugin.SecretManager;
import jp.reflexworks.taggingservice.plugin.ServiceManager;
import jp.reflexworks.taggingservice.plugin.SessionManager;
import jp.reflexworks.taggingservice.plugin.SettingService;
import jp.reflexworks.taggingservice.plugin.TaskQueueManager;
import jp.reflexworks.taggingservice.plugin.UserManager;
import jp.reflexworks.taggingservice.plugin.WebSocketManager;
import jp.reflexworks.taggingservice.util.RetryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * TaggingService環境情報
 */
public class TaggingEnv implements ReflexEnv {

	/** web.xmlとプロパティファイルから値を取得するクラス */
	private ServletContextUtil contextUtil;

	/** システムサービス名 */
	private String systemService;

	/** ResourceMapper mamager */
	private Class<? extends ResourceMapperManager> resourceMapperManagerClass;
	/** Property manager */
	private Class<? extends PropertyManager> propertyManagerClass;
	/** Datastore mamager */
	private Class<? extends DatastoreManager> datastoreManagerClass;
	/** Cache mamager */
	private Class<? extends CacheManager> cacheManagerClass;
	/** Content mamager */
	private Class<? extends ContentManager> contentManagerClass;
	/** AllocateIds manager */
	private Class<? extends AllocateIdsManager> allocateIdsManagerClass;
	/** Increment manager */
	private Class<? extends IncrementManager> incrementManagerClass;

	/** Request-Response manager */
	private Class<? extends RequestResponseManager> reqRespManagerClass;
	/** Authentication manager */
	private Class<? extends AuthenticationManager> authenticationManagerClass;
	/** Acl manager */
	private Class<? extends AclManager> aclManagerClass;
	/** Session manager */
	private Class<? extends SessionManager> sessionManagerClass;
	/** Service manager */
	private Class<? extends ServiceManager> serviceManagerClass;
	/** User manager */
	private Class<? extends UserManager> userManagerClass;
	/** AccessToken manager */
	private Class<? extends AccessTokenManager> accessTokenManagerClass;
	/** RXID manager */
	private Class<? extends RXIDManager> rxidManagerClass;
	/** Login-Logout manager */
	private Class<? extends LoginLogoutManager> loginLogoutManagerClass;
	/** Log manager */
	private Class<? extends LogManager> logManagerClass;
	/** Message manager */
	private Class<? extends MessageManager> messageManagerClass;
	/** Security manager */
	private Class<? extends ReflexSecurityManager> securityManagerClass;
	/** Captcha manager */
	private Class<? extends CaptchaManager> captchaManagerClass;
	/** TaskQueue manager */
	private Class<? extends TaskQueueManager> taskQueueManagerClass;
	/** EMail manager */
	private Class<? extends EMailManager> emailManagerClass;
	/** BigQuery manager */
	private Class<? extends BigQueryManager> bigQueryManagerClass;
	/** Namespace manager */
	private Class<? extends NamespaceManager> namespaceManagerClass;
	/** Monitor manager */
	private Class<? extends MonitorManager> monitorManagerClass;
	/** Memory Sort manager */
	private Class<? extends MemorySortManager> memorySortManagerClass;
	/** Push Notification manager */
	private Class<? extends PushNotificationManager> pushNotificationManagerClass;
	/** WebSocket manager */
	private Class<? extends WebSocketManager> webSocketManagerClass;
	/** PDF manager */
	private Class<? extends PdfManager> pdfManagerClass;
	/** RDB manager */
	private Class<? extends RDBManager> rdbManagerClass;
	/** OAuth manager */
	private Class<? extends OAuthManager> oauthManagerClass;
	/** Secret manager */
	private Class<? extends SecretManager> secretManagerClass;

	/** クローズ処理が必要なManagerリスト */
	private final List<Class<? extends ClosingForShutdown>> closingForShutdownList =
			new ArrayList<Class<? extends ClosingForShutdown>>();

	/** サービスの設定処理が必要なManagerリスト */
	private final List<Class<? extends SettingService>> settingServiceList =
			new ArrayList<Class<? extends SettingService>>();

	/** エントリー更新後に処理が必要なManagerリスト */
	private final List<Class<? extends CallingAfterCommit>> callingAfterCommitList =
			new ArrayList<Class<? extends CallingAfterCommit>>();

	/** サービス登録時に処理が必要なManagerリスト */
	private final List<Class<? extends ExecuteAtCreateService>> executeAtCreateServiceList =
			new ArrayList<Class<? extends ExecuteAtCreateService>>();

	/** 起動中かどうか */
	private boolean isRunning;

	/** サーブレット起動かどうか */
	private boolean isServlet;

	/** 初期データ登録処理どうか */
	private boolean isInitialized;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param contextUtil ServletContextUtil
	 */
	public TaggingEnv(ServletContextUtil contextUtil) {
		this.contextUtil = contextUtil;
		this.isServlet = true;

		// システムサービスの設定
		systemService = contextUtil.get(TaggingEnvConst.SYSTEM_SERVICE);
		if (StringUtils.isBlank(systemService)) {
			systemService = TaggingEnvConst.SYSTEM_SERVICE_DEFAULT;
		}
	}

	/**
	 * コンストラクタ
	 * @param propFile プロパティファイル
	 */
	public TaggingEnv(String propFile) {
		this(propFile, false);
	}

	/**
	 * コンストラクタ
	 * @param propFile プロパティファイル
	 * @param isInitialized 初期データ登録処理の場合true
	 */
	public TaggingEnv(String propFile, boolean isInitialized) {
		try {
			this.contextUtil = new PropertyContextUtil(propFile);
			this.isInitialized = isInitialized;

			// システムサービスの設定
			systemService = contextUtil.get(TaggingEnvConst.SYSTEM_SERVICE);
			if (StringUtils.isBlank(systemService)) {
				systemService = TaggingEnvConst.SYSTEM_SERVICE_DEFAULT;
			}
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * 初期処理.
	 */
	public void init() {
		if (logger.isInfoEnabled()) {
			logger.info("[init] start.");
		}

		// システムサービス用mapperの生成
		initResourceMapper();

		// 各設定項目の保持
		initProperty();

		// 名前空間
		initNamespace();

		// データストアの接続設定
		initDatastore();

		// タイムゾーンの設定
		initTimezone();

		// その他のプラグイン設定
		initPlugin();

		// 初期実行コマンド
		initCommand();

		// 稼働中
		isRunning = true;

		if (logger.isInfoEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append("[init] end. Locale = ");
			sb.append(Locale.getDefault());
			sb.append(", TimeZone = ");
			sb.append(TimeZone.getDefault().getID());
			sb.append(", file.encoding = " + System.getProperty("file.encoding"));
			logger.info(sb.toString());
		}
	}

	/**
	 * 終了処理.
	 */
	public void close() {
		
		// サーブレットに指定されたクローズ処理を待つ
		int numRetries = TaggingEnvUtil.getSystemPropInt(
				TaggingEnvConst.SHUTDOWN_AWAIT_COUNT, 
				TaggingEnvConst.SHUTDOWN_AWAIT_COUNT_DEFAULT);
		int waitMillis = TaggingEnvUtil.getSystemPropInt(
				TaggingEnvConst.SHUTDOWN_AWAIT_WAITMILLIS, 
				TaggingEnvConst.SHUTDOWN_AWAIT_WAITMILLIS_DEFAULT);
		for (int r = 0; r <= numRetries; r++) {
			Map<String, Boolean> awaitShutdownMap = TaggingEnvUtil.getAwaitShutdownMap();
			if (awaitShutdownMap == null || awaitShutdownMap.isEmpty()) {
				// 待ち終了
				if (logger.isInfoEnabled()) {
					logger.info("[close] The servlets shutdown processing is finished.");
				}
				break;
			}
			// 一定時間待つ
			RetryUtil.sleep(waitMillis);
		}

		// 本クラスで保持する処理のクローズ
		for (Class<? extends ClosingForShutdown> cls : closingForShutdownList) {
			try {
				ClosingForShutdown manager = (ClosingForShutdown)PluginUtil.newInstance(cls);
				manager.close();
			} catch (Throwable e) {
				logger.warn("[close] Error occurred.", e);
			}
		}
		if (logger.isInfoEnabled()) {
			logger.info("[close] The manager shutdown processing is finished.");
		}

		// 最後にReflexStatic.closeを実行
		ReflexStatic.close();
		if (logger.isInfoEnabled()) {
			logger.info("[close] The static shutdown processing is finished.");
		}
	}

	/**
	 * ResourceMapper初期設定.
	 */
	private void initResourceMapper() {
		try {
			// Secret管理プラグイン
			secretManagerClass = (Class<? extends SecretManager>)initPluginProc(
					TaggingEnvConst.PLUGIN_SECRETMANAGER,
					null, true);
			SecretManager secretManager = (SecretManager)PluginUtil.newInstance(secretManagerClass);
			secretManager.init();
			String secretKey = secretManager.getSecretKey(contextUtil);
			
			// ResourceMapper管理プラグイン
			resourceMapperManagerClass = (Class<? extends ResourceMapperManager>)initPluginProc(
					TaggingEnvConst.PLUGIN_RESOURCEMAPPERMANAGER,
					TaggingEnvConst.PLUGIN_RESOURCEMAPPERMANAGER_DEFAULT, true);
			if (resourceMapperManagerClass != null) {
				// ResourceMapper管理の初期処理は引数にServletContextUtilを設定する。
				ResourceMapperManager manager = (ResourceMapperManager)PluginUtil.newInstance(resourceMapperManagerClass);
				manager.init(contextUtil, secretKey);
			}

		} catch (IOException | TaggingException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * プロパティ管理の初期処理
	 */
	private void initProperty() {
		try {
			// プロパティ管理プラグイン
			propertyManagerClass = (Class<? extends PropertyManager>)initPluginProc(
					TaggingEnvConst.PLUGIN_PROPERTYMANAGER,
					TaggingEnvConst.PLUGIN_PROPERTYMANAGER_DEFAULT, true);
			if (propertyManagerClass != null) {
				// プロパティ管理の初期処理は引数にServletContextUtilを設定する。
				PropertyManager manager = (PropertyManager)PluginUtil.newInstance(propertyManagerClass);
				manager.init(contextUtil);
			}

		} catch (PluginException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * 名前空間管理の初期処理
	 */
	private void initNamespace() {
		// 名前空間管理プラグイン
		namespaceManagerClass = (Class<? extends NamespaceManager>)initPluginProc(
				TaggingEnvConst.PLUGIN_NAMESPACEMANAGER,
				TaggingEnvConst.PLUGIN_NAMESPACEMANAGER_DEFAULT, true);
	}

	/**
	 * データアクセス管理の初期処理
	 */
	private void initDatastore() {
		// データストア管理プラグイン
		datastoreManagerClass = (Class<? extends DatastoreManager>)initPluginProc(
				TaggingEnvConst.PLUGIN_DATASTOREMANAGER, null, true);
	}

	/**
	 * 各プラグインの設定、初期処理
	 */
	private void initPlugin() {
		// ACL管理プラグイン
		aclManagerClass = (Class<? extends AclManager>)initPluginProc(
				TaggingEnvConst.PLUGIN_ACLMANAGER,
				TaggingEnvConst.PLUGIN_ACLMANAGER_DEFAULT, true);

		// キャッシュ管理プラグイン
		cacheManagerClass = (Class<? extends CacheManager>)initPluginProc(
				TaggingEnvConst.PLUGIN_CACHEMANAGER, null, false);

		// 採番管理プラグイン
		allocateIdsManagerClass = (Class<? extends AllocateIdsManager>)initPluginProc(
				TaggingEnvConst.PLUGIN_ALLOCATEIDSMANAGER, null, false);

		// 加算管理プラグイン
		incrementManagerClass = (Class<? extends IncrementManager>)initPluginProc(
				TaggingEnvConst.PLUGIN_INCREMENTMANAGER, null, false);

		// 認証管理プラグイン
		authenticationManagerClass = (Class<? extends AuthenticationManager>)
				initPluginProc(TaggingEnvConst.PLUGIN_AUTHENTICATIONMANAGER,
						TaggingEnvConst.PLUGIN_AUTHENTICATIONMANAGER_DEFAULT, true);

		// リクエスト・レスポンス管理プラグイン
		reqRespManagerClass = (Class<? extends RequestResponseManager>)initPluginProc(
				TaggingEnvConst.PLUGIN_REQUESTRESPONSEMANAGER,
				TaggingEnvConst.PLUGIN_REQUESTRESPONSEMANAGER_DEFAULT, true);

		// セッション管理プラグイン
		sessionManagerClass = (Class<? extends SessionManager>)initPluginProc(
				TaggingEnvConst.PLUGIN_SESSIONMANAGER, null, false);

		// サービス管理プラグイン
		serviceManagerClass = (Class<? extends ServiceManager>)initPluginProc(
				TaggingEnvConst.PLUGIN_SERVICEMANAGER,
				TaggingEnvConst.PLUGIN_SERVICEMANAGER_DEFAULT, true);

		// ユーザ管理プラグイン
		userManagerClass = (Class<? extends UserManager>)initPluginProc(
				TaggingEnvConst.PLUGIN_USERMANAGER,
				TaggingEnvConst.PLUGIN_USERMANAGER_DEFAULT, true);

		// アクセストークン管理プラグイン
		accessTokenManagerClass = (Class<? extends AccessTokenManager>)initPluginProc(
				TaggingEnvConst.PLUGIN_ACCESSTOKENMANAGER,
				TaggingEnvConst.PLUGIN_ACCESSTOKENMANAGER_DEFAULT, true);

		// RXID管理プラグイン
		rxidManagerClass = (Class<? extends RXIDManager>)initPluginProc(
				TaggingEnvConst.PLUGIN_RXIDMANAGER,
				TaggingEnvConst.PLUGIN_RXIDMANAGER_DEFAULT, true);

		// ログイン・ログアウト管理プラグイン
		loginLogoutManagerClass = (Class<? extends LoginLogoutManager>)initPluginProc(
				TaggingEnvConst.PLUGIN_LOGINLOGOUTMANAGER,
				TaggingEnvConst.PLUGIN_LOGINLOGOUTMANAGER_DEFAULT, true);

		// ログ管理プラグイン
		logManagerClass = (Class<? extends LogManager>)initPluginProc(
				TaggingEnvConst.PLUGIN_LOGMANAGER,
				TaggingEnvConst.PLUGIN_LOGMANAGER_DEFAULT, true);

		// メッセージ管理プラグイン
		messageManagerClass = (Class<? extends MessageManager>)initPluginProc(
				TaggingEnvConst.PLUGIN_MESSAGEMANAGER,
				TaggingEnvConst.PLUGIN_MESSAGEMANAGER_DEFAULT, true);

		// セキュリティ管理プラグイン
		securityManagerClass = (Class<? extends ReflexSecurityManager>)initPluginProc(
				TaggingEnvConst.PLUGIN_SECURITYMANAGER,
				TaggingEnvConst.PLUGIN_SECURITYMANAGER_DEFAULT, true);

		// キャプチャ管理プラグイン
		captchaManagerClass = (Class<? extends CaptchaManager>)initPluginProc(
				TaggingEnvConst.PLUGIN_CAPTCHAMANAGER,
				TaggingEnvConst.PLUGIN_CAPTCHAMANAGER_DEFAULT, false);

		// 非同期処理管理プラグイン
		taskQueueManagerClass = (Class<? extends TaskQueueManager>)initPluginProc(
				TaggingEnvConst.PLUGIN_TASKQUEUEMANAGER,
				TaggingEnvConst.PLUGIN_TASKQUEUEMANAGER_DEFAULT, true);

		// メール管理プラグイン
		emailManagerClass = (Class<? extends EMailManager>)initPluginProc(
				TaggingEnvConst.PLUGIN_EMAILMANAGER,
				TaggingEnvConst.PLUGIN_EMAILMANAGER_DEFAULT, true);

		// コンテンツ管理プラグイン
		contentManagerClass = (Class<? extends ContentManager>)initPluginProc(
				TaggingEnvConst.PLUGIN_CONTENTMANAGER, null, false);

		// BigQuery管理プラグイン
		bigQueryManagerClass = (Class<? extends BigQueryManager>)initPluginProc(
				TaggingEnvConst.PLUGIN_BIGQUERYMANAGER, null, false);

		// モニター管理プラグイン
		monitorManagerClass = (Class<? extends MonitorManager>)initPluginProc(
				TaggingEnvConst.PLUGIN_MONITORMANAGER,
				TaggingEnvConst.PLUGIN_MONITORMANAGER_DEFAULT, false);

		// インメモリソート管理プラグイン
		memorySortManagerClass = (Class<? extends MemorySortManager>)initPluginProc(
				TaggingEnvConst.PLUGIN_MEMORYSORTMANAGER,
				TaggingEnvConst.PLUGIN_MEMORYSORTMANAGER_DEFAULT, false);

		// プッシュ通知管理プラグイン
		pushNotificationManagerClass = (Class<? extends PushNotificationManager>)initPluginProc(
				TaggingEnvConst.PLUGIN_PUSHNOTIFICATIONMANAGER, null, false);

		// WebSocket管理プラグイン
		webSocketManagerClass = (Class<? extends WebSocketManager>)initPluginProc(
				TaggingEnvConst.PLUGIN_WEBSOCKETMANAGER, null, false);

		// PDF生成管理プラグイン
		pdfManagerClass = (Class<? extends PdfManager>)initPluginProc(
				TaggingEnvConst.PLUGIN_PDFMANAGER, null, false);

		// RDB管理プラグイン
		rdbManagerClass = (Class<? extends RDBManager>)initPluginProc(
				TaggingEnvConst.PLUGIN_RDBMANAGER, null, false);

		// OAuth管理プラグイン
		oauthManagerClass = (Class<? extends OAuthManager>)initPluginProc(
				TaggingEnvConst.PLUGIN_OAUTHMANAGER, null, false);
	}

	/**
	 * プラグインクラス名からクラスオブジェクト、インスタンスを生成する。
	 * @param propName プラグインクラス名を設定するプロパティ名
	 * @param defaultCls プラグイン指定が無い場合のクラス
	 * @param isRequired 必須チェックを行うかどうか
	 */
	private Class<? extends ReflexPlugin> initPluginProc(String propName,
			Class<? extends ReflexPlugin> defaultCls, boolean isRequired) {
		// プラグインクラス名を取得
		String clsName = getContextValue(propName);
		try {
			// プラグインクラスを生成
			Class tmpClass = PluginUtil.forName(clsName);
			if (tmpClass == null) {
				tmpClass = defaultCls;
			} else {
				if (!ReflexPlugin.class.isAssignableFrom(tmpClass)) {
					String msg = "Plugin class must implement ReflexPlugin : " + propName;
					logger.warn("[initPluginProc] " + msg);
					throw new PluginException(msg);
				}
			}
			Class<? extends ReflexPlugin> managerClass = tmpClass;
			// 初期処理
			if (managerClass != null) {
				ReflexPlugin manager = (ReflexPlugin)PluginUtil.newInstance(managerClass);
				manager.init();
				setManagerList(managerClass);
			} else {
				if (isRequired) {
					// クラス未設定はエラー
					String msg = "Plugin class is required : " + propName;
					logger.warn("[initPluginProc] " + msg);
					throw new PluginException(msg);
				}
			}
			return managerClass;

		} catch (PluginException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * タイムゾーン設定
	 */
	private void initTimezone() {
		String timezone = getSystemProp(TaggingEnvConst.TIMEZONE);
		if (!StringUtils.isBlank(timezone)) {
			TimeZone.setDefault(TimeZone.getTimeZone(timezone));
		}
		if (logger.isTraceEnabled()) {
			logger.debug("[initTimezone] TimeZone=" + TimeZone.getDefault().getID());
		}

		// ロケールの設定
		String localeStr = getSystemProp(TaggingEnvConst.LOCALE);
		if (!StringUtils.isBlank(localeStr)) {
			String[] localeParts = localeStr.split(TaggingEnvConst.LOCALE_DELIMITER);
			Locale locale = null;
			if (localeParts.length == 1) {
				locale = new Locale(localeParts[0]);
			} else if (localeParts.length == 2) {
				locale = new Locale(localeParts[0], localeParts[1]);
			} else {
				locale = new Locale(localeParts[0], localeParts[1], localeParts[2]);
			}
			Locale.setDefault(locale);
		}
	}

	/**
	 * 特定のインターフェースを継承している管理クラスの場合、リストに格納する。
	 * <ul>
	 *   <li>ClosingForShutdown</li>
	 *   <li>SettingService</li>
	 *   <li>CallingAfterCommit</li>
	 * </ul>
	 * @param cls 管理クラス
	 */
	private void setManagerList(Class cls) {
		if (cls != null) {
			if (ClosingForShutdown.class.isAssignableFrom(cls)) {
				closingForShutdownList.add(cls);
			}
			if (SettingService.class.isAssignableFrom(cls)) {
				settingServiceList.add(cls);
			}
			if (CallingAfterCommit.class.isAssignableFrom(cls)) {
				callingAfterCommitList.add(cls);
			}
			if (ExecuteAtCreateService.class.isAssignableFrom(cls)) {
				executeAtCreateServiceList.add(cls);
			}
		}
	}

	/**
	 * 各プラグインの設定、初期処理
	 */
	private void initCommand() {
		Set<String> commandPathSet = getSystemPropSet(TaggingEnvConst.INIT_COMMANDPATH_PREFIX);
		if (commandPathSet != null && !commandPathSet.isEmpty()) {
			for (String commandPath : commandPathSet) {
				TaggingEnvCommandUtil.initExecCommand(commandPath);
			}
		}
	}

	/**
	 * 標準ATOM形式のResourceMapperを返却.
	 * @return 標準ATOM形式のResourceMapper
	 */
	public FeedTemplateMapper getAtomResourceMapper() {
		ResourceMapperManager resourceMapperManager = getResourceMapperManager();
		return resourceMapperManager.getAtomResourceMapper();
	}

	/**
	 * サービスのResourceMapperを返却.
	 * @param serviceName サービス名
	 * @return サービスのResourceMapper
	 */
	public FeedTemplateMapper getResourceMapper(String serviceName) {
		ResourceMapperManager resourceMapperManager = getResourceMapperManager();
		return resourceMapperManager.getResourceMapper(serviceName);
	}

	/**
	 * テンプレート項目のIndex一覧を取得
	 * @param serviceName サービス名
	 * @return テンプレート項目のIndexを格納したMap
	 */
	public Map<String, Pattern> getTemplateIndexMap(String serviceName) {
		ResourceMapperManager resourceMapperManager = getResourceMapperManager();
		return resourceMapperManager.getTemplateIndexMap(serviceName);
	}

	/**
	 * テンプレート項目の全文検索Index一覧を取得
	 * @param serviceName サービス名
	 * @return テンプレート項目の全文検索Indexを格納したMap
	 */
	public Map<String, Pattern> getTemplateFullTextIndexMap(String serviceName) {
		ResourceMapperManager resourceMapperManager = getResourceMapperManager();
		return resourceMapperManager.getTemplateFullTextIndexMap(serviceName);
	}

	/**
	 * テンプレート項目のDISTKEY一覧を取得
	 * @param serviceName サービス名
	 * @return テンプレート項目のDISTKEYを格納したMap
	 */
	public Map<String, Pattern> getTemplateDistkeyMap(String serviceName) {
		ResourceMapperManager resourceMapperManager = getResourceMapperManager();
		return resourceMapperManager.getTemplateDistkeyMap(serviceName);
	}

	/**
	 * システムサービスの設定値を返却
	 * @param key キー
	 * @return 設定値
	 */
	public String getSystemProp(String key) {
		return getProp(systemService, key);
	}

	/**
	 * システムサービスの設定値を返却
	 * @param key キー
	 * @param def デフォルト値。設定値がnullの場合に適用される。
	 * @return 設定値
	 */
	public String getSystemProp(String key, String def) {
		return getProp(systemService, key, def);
	}

	/**
	 * システムサービスの設定値を返却
	 * Integer型に置き換えられない場合はnullを返却します。
	 * @param key キー
	 * @return 設定値
	 * @throws InvalidServiceSettingException Integerに変換できない値が設定されている場合
	 */
	public Integer getSystemPropInteger(String key)
	throws InvalidServiceSettingException {
		return getPropInteger(systemService, key);
	}

	/**
	 * システムサービスの設定値を返却
	 * Integer型に置き換えられない場合はnullを返却します。
	 * @param key キー
	 * @param def デフォルト値。設定値がnullの場合に適用される。
	 * @return 設定値
	 * @throws InvalidServiceSettingException Integerに変換できない値が設定されている場合
	 */
	public int getSystemPropInteger(String key, int def)
	throws InvalidServiceSettingException {
		return getPropInteger(systemService, key, def);
	}

	/**
	 * システムサービスの設定値を返却
	 * Long型に置き換えられない場合はnullを返却します。
	 * @param key キー
	 * @return 設定値
	 * @throws InvalidServiceSettingException Longに変換できない値が設定されている場合
	 */
	public Long getSystemPropLong(String key)
	throws InvalidServiceSettingException {
		return getPropLong(systemService, key);
	}

	/**
	 * システムサービスの設定値を返却
	 * Long型に置き換えられない場合はnullを返却します。
	 * @param key キー
	 * @param def デフォルト値。設定値がnullの場合に適用される。
	 * @return 設定値
	 * @throws InvalidServiceSettingException Longに変換できない値が設定されている場合
	 */
	public long getSystemPropLong(String key, long def)
	throws InvalidServiceSettingException {
		return getPropLong(systemService, key, def);
	}

	/**
	 * システムサービスの設定値を返却
	 * Double型に置き換えられない場合はnullを返却します。
	 * @param key キー
	 * @param def デフォルト値。設定値がnullの場合に適用される。
	 * @return 設定値
	 * @throws InvalidServiceSettingException Doubleに変換できない値が設定されている場合
	 */
	public Double getSystemPropDouble(String key)
	throws InvalidServiceSettingException {
		return getPropDouble(systemService, key);
	}

	/**
	 * システムサービスの設定値を返却
	 * Double型に置き換えられない場合はnullを返却します。
	 * @param key キー
	 * @param def デフォルト値。設定値がnullの場合に適用される。
	 * @return 設定値
	 * @throws InvalidServiceSettingException Doubleに変換できない値が設定されている場合
	 */
	public double getSystemPropDouble(String key, double def)
	throws InvalidServiceSettingException {
		return getPropDouble(systemService, key, def);
	}

	/**
	 * システムサービスの設定値を返却
	 * Boolean型に置き換えられない場合はnullを返却します。
	 * @param key キー
	 * @return 設定値
	 * @throws InvalidServiceSettingException Booleanに変換できない値が設定されている場合
	 */
	public Boolean getSystemPropBoolean(String key)
	throws InvalidServiceSettingException {
		return getPropBoolean(systemService, key);
	}

	/**
	 * システムサービスの設定値を返却
	 * Boolean型に置き換えられない場合はnullを返却します。
	 * @param key キー
	 * @param def デフォルト値。設定値がnullの場合に適用される。
	 * @return 設定値
	 * @throws InvalidServiceSettingException Booleanに変換できない値が設定されている場合
	 */
	public boolean getSystemPropBoolean(String key, boolean def)
	throws InvalidServiceSettingException {
		return getPropBoolean(systemService, key, def);
	}

	/**
	 * 設定値を返却
	 * @param serviceName サービス名
	 * @param key キー
	 * @return 設定値
	 */
	public String getProp(String serviceName, String key) {
		PropertyManager propertyManager = getPropertyManager();
		return propertyManager.getValue(serviceName, key);
	}

	/**
	 * 設定値を返却
	 * @param serviceName サービス名
	 * @param key キー
	 * @param def デフォルト値。設定値がnullの場合に適用される。
	 * @return 設定値
	 */
	public String getProp(String serviceName, String key, String def) {
		String ret = getProp(serviceName, key);
		if (!StringUtils.isBlank(ret)) {
			return ret;
		}
		return def;
	}

	/**
	 * 設定値を返却
	 * Integer型に置き換えられない場合はnullを返却します。
	 * @param serviceName サービス名
	 * @param key キー
	 * @return 設定値
	 * @throws InvalidServiceSettingException Integerに変換できない値が設定されている場合
	 */
	public Integer getPropInteger(String serviceName, String key)
	throws InvalidServiceSettingException {
		PropertyManager propertyManager = getPropertyManager();
		String str = propertyManager.getValue(serviceName, key);
		if (!StringUtils.isBlank(str)) {
			try {
				return Integer.parseInt(str);
			} catch (NumberFormatException e) {
				StringBuilder sb = new StringBuilder();
				sb.append("Integer value was expected. NumberFormatException: ");
				sb.append(e.getMessage());
				sb.append(" name = ");
				sb.append(key);
				throw new InvalidServiceSettingException(e, sb.toString());
			}
		}
		return null;
	}

	/**
	 * 設定値を返却
	 * Integer型に置き換えられない場合はnullを返却します。
	 * @param serviceName サービス名
	 * @param key キー
	 * @param def デフォルト値。設定値がnullの場合に適用される。
	 * @return 設定値
	 * @throws InvalidServiceSettingException Integerに変換できない値が設定されている場合
	 */
	public Integer getPropInteger(String serviceName, String key, int def)
	throws InvalidServiceSettingException {
		Integer ret = getPropInteger(serviceName, key);
		if (ret != null) {
			return ret;
		}
		return def;
	}

	/**
	 * 設定値を返却
	 * Long型に置き換えられない場合はnullを返却します。
	 * @param serviceName サービス名
	 * @param key キー
	 * @return 設定値
	 * @throws InvalidServiceSettingException Longに変換できない値が設定されている場合
	 */
	public Long getPropLong(String serviceName, String key)
	throws InvalidServiceSettingException {
		PropertyManager propertyManager = getPropertyManager();
		String str = propertyManager.getValue(serviceName, key);
		if (!StringUtils.isBlank(str)) {
			try {
				return Long.parseLong(str);
			} catch (NumberFormatException e) {
				StringBuilder sb = new StringBuilder();
				sb.append("Long value was expected. NumberFormatException: ");
				sb.append(e.getMessage());
				sb.append(" name = ");
				sb.append(key);
				throw new InvalidServiceSettingException(e, sb.toString());
			}
		}
		return null;
	}

	/**
	 * 設定値を返却
	 * Long型に置き換えられない場合はnullを返却します。
	 * @param serviceName サービス名
	 * @param key キー
	 * @param def デフォルト値。設定値がnullの場合に適用される。
	 * @return 設定値
	 * @throws InvalidServiceSettingException Longに変換できない値が設定されている場合
	 */
	public Long getPropLong(String serviceName, String key, long def)
	throws InvalidServiceSettingException {
		Long ret = getPropLong(serviceName, key);
		if (ret != null) {
			return ret;
		}
		return def;
	}

	/**
	 * 設定値を返却
	 * Long型に置き換えられない場合はnullを返却します。
	 * @param serviceName サービス名
	 * @param key キー
	 * @return 設定値
	 * @throws InvalidServiceSettingException Longに変換できない値が設定されている場合
	 */
	public Double getPropDouble(String serviceName, String key)
	throws InvalidServiceSettingException {
		PropertyManager propertyManager = getPropertyManager();
		String str = propertyManager.getValue(serviceName, key);
		if (!StringUtils.isBlank(str)) {
			try {
				return Double.parseDouble(str);
			} catch (NumberFormatException e) {
				StringBuilder sb = new StringBuilder();
				sb.append("Double value was expected. NumberFormatException: ");
				sb.append(e.getMessage());
				sb.append(" name = ");
				sb.append(key);
				throw new InvalidServiceSettingException(e, sb.toString());
			}
		}
		return null;
	}

	/**
	 * 設定値を返却
	 * Long型に置き換えられない場合はnullを返却します。
	 * @param serviceName サービス名
	 * @param key キー
	 * @param def デフォルト値。設定値がnullの場合に適用される。
	 * @return 設定値
	 * @throws InvalidServiceSettingException Longに変換できない値が設定されている場合
	 */
	public Double getPropDouble(String serviceName, String key, double def)
	throws InvalidServiceSettingException {
		Double ret = getPropDouble(serviceName, key);
		if (ret != null) {
			return ret;
		}
		return def;
	}

	/**
	 * 設定値を返却
	 * @param serviceName サービス名
	 * @param key キー
	 * @return 設定値
	 */
	public Boolean getPropBoolean(String serviceName, String key)
	throws InvalidServiceSettingException {
		PropertyManager propertyManager = getPropertyManager();
		String str = propertyManager.getValue(serviceName, key);
		if (str != null) {
			return Boolean.parseBoolean(str);
		}
		return null;
	}

	/**
	 * 設定値を返却
	 * Boolean型に置き換えられない場合はデフォルト値を返却します。
	 * @param serviceName サービス名
	 * @param key キー
	 * @param def デフォルト値。設定値がnullの場合に適用される。
	 * @return 設定値
	 */
	public Boolean getPropBoolean(String serviceName, String key, boolean def)
	throws InvalidServiceSettingException {
		Boolean ret = getPropBoolean(serviceName, key);
		if (ret != null) {
			return ret;
		}
		return def;
	}

	/**
	 * システムサービスの、先頭が指定されたキーと等しい環境設定情報一覧を取得
	 * @param prefix キーの先頭部分
	 * @return 環境設定情報一覧
	 */
	public Map<String, String> getSystemPropMap(String prefix) {
		return getPropMap(systemService, prefix);
	}

	/**
	 * 先頭が指定されたキーと等しい環境設定情報一覧を取得
	 * @param prefix キーの先頭部分
	 * @param seriviceName サービス名
	 * @return 環境設定情報一覧
	 */
	public Map<String, String> getPropMap(String serviceName, String prefix) {
		PropertyManager propertyManager = getPropertyManager();
		return propertyManager.getMap(serviceName, prefix);
	}

	/**
	 * システムサービスの先頭が指定されたキーと等しい環境設定情報一覧を取得
	 * @param prefix キーの先頭部分
	 * @return 環境設定情報一覧をSortedMapで返却
	 */
	public SortedMap<String, String> getSystemPropSortedMap(String prefix) {
		return getPropSortedMap(systemService, prefix);
	}

	/**
	 * 先頭が指定されたキーと等しい環境設定情報一覧を取得
	 * @param seriviceName サービス名
	 * @param prefix キーの先頭部分
	 * @return 環境設定情報一覧をSortedMapで返却
	 */
	public SortedMap<String, String> getPropSortedMap(String serviceName, String prefix) {
		PropertyManager propertyManager = getPropertyManager();
		return propertyManager.getSortedMap(serviceName, prefix);
	}

	/**
	 * システムサービスの、先頭が指定されたキーと等しい環境設定情報一覧を取得
	 * @param prefix キーの先頭部分
	 * @return 環境設定情報一覧をSetで返却
	 */
	public Set<String> getSystemPropSet(String prefix) {
		return getPropSet(systemService, prefix);
	}

	/**
	 * 先頭が指定されたキーと等しい環境設定情報一覧を取得
	 * @param seriviceName サービス名
	 * @param prefix キーの先頭部分
	 * @return 環境設定情報一覧をSetで返却
	 */
	public Set<String> getPropSet(String serviceName, String prefix) {
		PropertyManager propertyManager = getPropertyManager();
		return propertyManager.getSet(serviceName, prefix);
	}

	/**
	 * 値のPatternオブジェクトを取得.
	 * @param serviceName サービス名
	 * @param name プロパティ名
	 * @return 値のPatternオブジェクト
	 */
	public Pattern getPropPattern(String serviceName, String name) {
		PropertyManager propertyManager = getPropertyManager();
		return propertyManager.getPattern(serviceName, name);
	}

	/**
	 * システムサービスを取得
	 * @return システムサービス名
	 */
	public String getSystemService() {
		return systemService;
	}

	/**
	 * web.xmlまたはプロパティファイルから値を取得.
	 * @param name 名前
	 * @return web.xmlまたはプロパティファイルに設定された値
	 */
	public String getContextValue(String name) {
		return contextUtil.get(name);
	}

	/**
	 * サービス設定処理を実装する管理クラスリストを取得.
	 * @return サービス設定処理を実装する管理クラスリスト
	 */
	public List<SettingService> getSettingServiceList() {
		List<SettingService> list = new ArrayList<>();
		try {
			for (Class<? extends SettingService> cls : settingServiceList) {
				SettingService manager = (SettingService)PluginUtil.newInstance(cls);
				list.add(manager);
			}
		} catch (PluginException e) {
			throw new IllegalStateException(e);
		}
		return list;
	}

	/**
	 * エントリー更新後に必要な処理を実装する管理クラスリストを取得.
	 * @return エントリー更新後に必要な処理を実装する管理クラスリスト
	 */
	public List<CallingAfterCommit> getCallingAfterCommitList() {
		List<CallingAfterCommit> list = new ArrayList<>();
		try {
			for (Class<? extends CallingAfterCommit> cls : callingAfterCommitList) {
				CallingAfterCommit manager = (CallingAfterCommit)PluginUtil.newInstance(cls);
				list.add(manager);
			}
		} catch (PluginException e) {
			throw new IllegalStateException(e);
		}
		return list;
	}

	/**
	 * サービス登録時に必要な処理を実装する管理クラスリストを取得.
	 * @return サービス登録時に必要な処理を実装する管理クラスリスト
	 */
	public List<ExecuteAtCreateService> getExecuteAtCreateServiceList() {
		List<ExecuteAtCreateService> list = new ArrayList<>();
		try {
			for (Class<? extends ExecuteAtCreateService> cls : executeAtCreateServiceList) {
				ExecuteAtCreateService manager = (ExecuteAtCreateService)PluginUtil.newInstance(cls);
				list.add(manager);
			}
		} catch (PluginException e) {
			throw new IllegalStateException(e);
		}
		return list;
	}

	/**
	 * サーバ稼働中かどうか.
	 * 起動処理中の場合falseを返す。
	 * @return サーバ稼働中の場合true
	 */
	public boolean isRunning() {
		return isRunning;
	}

	/**
	 * サーブレット起動かどうか.
	 * @return サーブレット起動の場合true、バッチ処理の場合false
	 */
	public boolean isServlet() {
		return isServlet;
	}

	/**
	 * 初期データ登録処理かどうか.
	 * @return 初期データ登録処理の場合true
	 */
	public boolean isInitialized() {
		return isInitialized;
	}

	/**
	 * ResourcMapper管理プラグインを取得.
	 * @return ResourcMapper manager
	 */
	public ResourceMapperManager getResourceMapperManager() {
		if (resourceMapperManagerClass == null) {
			return null;
		}
		try {
			return (ResourceMapperManager)PluginUtil.newInstance(resourceMapperManagerClass);
		} catch (PluginException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * プロパティ管理プラグインを取得.
	 * @return property manager
	 */
	public PropertyManager getPropertyManager() {
		if (propertyManagerClass == null) {
			return null;
		}
		try {
			return (PropertyManager)PluginUtil.newInstance(propertyManagerClass);
		} catch (PluginException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Datastore managerを取得.
	 * @return Datastore manager
	 */
	public DatastoreManager getDatastoreManager() {
		if (datastoreManagerClass == null) {
			return null;
		}
		try {
			return (DatastoreManager)PluginUtil.newInstance(datastoreManagerClass);
		} catch (PluginException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Cache managerを取得.
	 * @return Cache manager
	 */
	public CacheManager getCacheManager() {
		if (cacheManagerClass == null) {
			return null;
		}
		try {
			return (CacheManager)PluginUtil.newInstance(cacheManagerClass);
		} catch (PluginException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Content managerを取得.
	 * @return Content manager
	 */
	public ContentManager getContentManager() {
		if (contentManagerClass == null) {
			return null;
		}
		try {
			return (ContentManager)PluginUtil.newInstance(contentManagerClass);
		} catch (PluginException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * リクエスト・レスポンス管理プラグインを取得.
	 * @return リクエスト・レスポンス管理プラグイン
	 */
	public RequestResponseManager getRequestResponseManager() {
		if (reqRespManagerClass == null) {
			return null;
		}
		try {
			return (RequestResponseManager)PluginUtil.newInstance(reqRespManagerClass);
		} catch (PluginException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * 採番管理プラグインを取得.
	 * @return AllocateIds manager
	 */
	public AllocateIdsManager getAllocateIdsManager() {
		if (allocateIdsManagerClass == null) {
			return null;
		}
		try {
			return (AllocateIdsManager)PluginUtil.newInstance(allocateIdsManagerClass);
		} catch (PluginException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * 加算管理プラグインを取得.
	 * @return Increment manager
	 */
	public IncrementManager getIncrementManager() {
		if (incrementManagerClass == null) {
			return null;
		}
		try {
			return (IncrementManager)PluginUtil.newInstance(incrementManagerClass);
		} catch (PluginException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * 認証管理プラグインを取得.
	 * @return Authentication manager
	 */
	public AuthenticationManager getAuthenticationManager() {
		if (authenticationManagerClass == null) {
			return null;
		}
		try {
			return (AuthenticationManager)PluginUtil.newInstance(authenticationManagerClass);
		} catch (PluginException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * ACL管理プラグインを取得.
	 * @return ACL manager
	 */
	public AclManager getAclManager() {
		if (aclManagerClass == null) {
			return null;
		}
		try {
			return (AclManager)PluginUtil.newInstance(aclManagerClass);
		} catch (PluginException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * セッション管理プラグインを取得.
	 * @return Session manager
	 */
	public SessionManager getSessionManager() {
		if (sessionManagerClass == null) {
			return null;
		}
		try {
			return (SessionManager)PluginUtil.newInstance(sessionManagerClass);
		} catch (PluginException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * サービス管理プラグインを取得.
	 * @return Service manager
	 */
	public ServiceManager getServiceManager() {
		if (serviceManagerClass == null) {
			return null;
		}
		try {
			return (ServiceManager)PluginUtil.newInstance(serviceManagerClass);
		} catch (PluginException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * ユーザ管理プラグインを取得.
	 * @return User manager
	 */
	public UserManager getUserManager() {
		if (userManagerClass == null) {
			return null;
		}
		try {
			return (UserManager)PluginUtil.newInstance(userManagerClass);
		} catch (PluginException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * アクセストークン管理プラグインを取得.
	 * @return access token manager
	 */
	public AccessTokenManager getAccessTokenManager() {
		if (accessTokenManagerClass == null) {
			return null;
		}
		try {
			return (AccessTokenManager)PluginUtil.newInstance(accessTokenManagerClass);
		} catch (PluginException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * RXID管理プラグインを取得.
	 * @return RXID manager
	 */
	public RXIDManager getRXIDManager() {
		if (rxidManagerClass == null) {
			return null;
		}
		try {
			return (RXIDManager)PluginUtil.newInstance(rxidManagerClass);
		} catch (PluginException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * ログイン・ログアウト管理プラグインを取得.
	 * @return Login-Logout manager
	 */
	public LoginLogoutManager getLoginLogoutManager() {
		if (loginLogoutManagerClass == null) {
			return null;
		}
		try {
			return (LoginLogoutManager)PluginUtil.newInstance(loginLogoutManagerClass);
		} catch (PluginException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * ログ管理プラグインを取得.
	 * @return Log manager
	 */
	public LogManager getLogManager() {
		if (logManagerClass == null) {
			return null;
		}
		try {
			return (LogManager)PluginUtil.newInstance(logManagerClass);
		} catch (PluginException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * メッセージ管理プラグインを取得.
	 * @return Message manager
	 */
	public MessageManager getMessageManager() {
		if (messageManagerClass == null) {
			return null;
		}
		try {
			return (MessageManager)PluginUtil.newInstance(messageManagerClass);
		} catch (PluginException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * セキュリティ管理プラグインを取得.
	 * @return Security manager
	 */
	public ReflexSecurityManager getSecurityManager() {
		if (securityManagerClass == null) {
			return null;
		}
		try {
			return (ReflexSecurityManager)PluginUtil.newInstance(securityManagerClass);
		} catch (PluginException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * キャプチャ管理プラグインを取得.
	 * @return Captcha manager
	 */
	public CaptchaManager getCaptchaManager() {
		if (captchaManagerClass == null) {
			return null;
		}
		try {
			return (CaptchaManager)PluginUtil.newInstance(captchaManagerClass);
		} catch (PluginException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * 非同期処理管理プラグインを取得.
	 * @return Task queue manager
	 */
	public TaskQueueManager getTaskQueueManager() {
		if (taskQueueManagerClass == null) {
			return null;
		}
		try {
			return (TaskQueueManager)PluginUtil.newInstance(taskQueueManagerClass);
		} catch (PluginException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * メール管理プラグインを取得.
	 * @return EMail manager
	 */
	public EMailManager getEMailManager() {
		if (emailManagerClass == null) {
			return null;
		}
		try {
			return (EMailManager)PluginUtil.newInstance(emailManagerClass);
		} catch (PluginException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * BigQuery管理プラグインを取得.
	 * @return bigquery manager
	 */
	public BigQueryManager getBigQueryManager() {
		if (bigQueryManagerClass == null) {
			return null;
		}
		try {
			return (BigQueryManager)PluginUtil.newInstance(bigQueryManagerClass);
		} catch (PluginException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * 名前空間管理プラグインを取得.
	 * @return namespace manager
	 */
	public NamespaceManager getNamespaceManager() {
		if (namespaceManagerClass == null) {
			return null;
		}
		try {
			return (NamespaceManager)PluginUtil.newInstance(namespaceManagerClass);
		} catch (PluginException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * モニター管理プラグインを取得.
	 * @return monitor manager
	 */
	public MonitorManager getMonitorManager() {
		if (monitorManagerClass == null) {
			return null;
		}
		try {
			return (MonitorManager)PluginUtil.newInstance(monitorManagerClass);
		} catch (PluginException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * インメモリソート管理プラグインを取得.
	 * @return memory sort manager
	 */
	public MemorySortManager getMemorySortManager() {
		if (memorySortManagerClass == null) {
			return null;
		}
		try {
			return (MemorySortManager)PluginUtil.newInstance(memorySortManagerClass);
		} catch (PluginException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * プッシュ通知管理プラグインを取得.
	 * @return push notification manager
	 */
	public PushNotificationManager getPushNotificationManager() {
		if (pushNotificationManagerClass == null) {
			return null;
		}
		try {
			return (PushNotificationManager)PluginUtil.newInstance(pushNotificationManagerClass);
		} catch (PluginException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * WebSocket管理プラグインを取得.
	 * @return websocket manager
	 */
	public WebSocketManager getWebSocketManager() {
		if (webSocketManagerClass == null) {
			return null;
		}
		try {
			return (WebSocketManager)PluginUtil.newInstance(webSocketManagerClass);
		} catch (PluginException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * PDF生成管理プラグインを取得.
	 * @return pdf manager
	 */
	public PdfManager getPdfManager() {
		if (pdfManagerClass == null) {
			return null;
		}
		try {
			return (PdfManager)PluginUtil.newInstance(pdfManagerClass);
		} catch (PluginException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * RDB管理プラグインを取得.
	 * @return rdb manager
	 */
	public RDBManager getRDBManager() {
		if (rdbManagerClass == null) {
			return null;
		}
		try {
			return (RDBManager)PluginUtil.newInstance(rdbManagerClass);
		} catch (PluginException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * OAuth管理プラグインを取得.
	 * @return oauth manager
	 */
	public OAuthManager getOAuthManager() {
		if (oauthManagerClass == null) {
			return null;
		}
		try {
			return (OAuthManager)PluginUtil.newInstance(oauthManagerClass);
		} catch (PluginException e) {
			throw new IllegalStateException(e);
		}
	}

}
