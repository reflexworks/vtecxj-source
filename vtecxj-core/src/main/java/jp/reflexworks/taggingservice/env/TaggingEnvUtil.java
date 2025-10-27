package jp.reflexworks.taggingservice.env;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.taggingservice.api.ReflexEnv;
import jp.reflexworks.taggingservice.api.ReflexStatic;
import jp.reflexworks.taggingservice.api.SettingConst;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.reflexworks.taggingservice.exception.StaticDuplicatedException;
import jp.reflexworks.taggingservice.plugin.AccessTokenManager;
import jp.reflexworks.taggingservice.plugin.AclManager;
import jp.reflexworks.taggingservice.plugin.AllocateIdsManager;
import jp.reflexworks.taggingservice.plugin.AuthenticationManager;
import jp.reflexworks.taggingservice.plugin.BigQueryManager;
import jp.reflexworks.taggingservice.plugin.CacheManager;
import jp.reflexworks.taggingservice.plugin.CallingAfterCommit;
import jp.reflexworks.taggingservice.plugin.CaptchaManager;
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
import jp.reflexworks.taggingservice.plugin.PropertyManager;
import jp.reflexworks.taggingservice.plugin.PushNotificationManager;
import jp.reflexworks.taggingservice.plugin.RDBManager;
import jp.reflexworks.taggingservice.plugin.RXIDManager;
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

/**
 * 設定値取得ユーティリティ
 */
public final class TaggingEnvUtil {

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(TaggingEnvUtil.class);

	/**
	 * コンストラクタ(生成不可).
	 */
	private TaggingEnvUtil() {}

	/**
	 * ステージを取得.
	 * @return ステージ
	 */
	public static String getStage() {
		return TaggingEnvUtil.getSystemProp(TaggingEnvConst.ENV_STAGE, TaggingEnvConst.ENV_STAGE_DEFAULT);
	}

	/**
	 * FeedTemplateMapperを取得.
	 * すでにメモリ上に保持されているものを取得します。データストア参照は行いません。
	 * @param serviceName サービス名
	 * @return FeedTemplateMapper
	 */
	public static FeedTemplateMapper getResourceMapper(String serviceName) {
		return ReflexStatic.getEnv().getResourceMapper(serviceName);
	}

	/**
	 * テンプレート項目のIndex一覧を取得
	 * @param serviceName サービス名
	 * @return テンプレート項目のIndexを格納したMap
	 */
	public static Map<String, Pattern> getTemplateIndexMap(String serviceName) {
		return ((ReflexEnv)ReflexStatic.getEnv()).getTemplateIndexMap(serviceName);
	}

	/**
	 * テンプレート項目の全文検索Index一覧を取得
	 * @param serviceName サービス名
	 * @return テンプレート項目の全文検索Indexを格納したMap
	 */
	public static Map<String, Pattern> getTemplateFullTextIndexMap(String serviceName) {
		return ((ReflexEnv)ReflexStatic.getEnv()).getTemplateFullTextIndexMap(serviceName);
	}

	/**
	 * テンプレート項目のDISTKEY一覧を取得
	 * @param serviceName サービス名
	 * @return テンプレート項目のDISTKEYを格納したMap
	 */
	public static Map<String, Pattern> getTemplateDistkeyMap(String serviceName) {
		return ((ReflexEnv)ReflexStatic.getEnv()).getTemplateDistkeyMap(serviceName);
	}

	/**
	 * 標準ATOM形式のResourceMapperを返却.
	 * @return 標準ATOM形式のResourceMapper
	 */
	public static FeedTemplateMapper getAtomResourceMapper() {
		return ReflexStatic.getEnv().getAtomResourceMapper();
	}

	/**
	 * システム管理サービス名を取得
	 * @return システム管理サービス名
	 */
	public static String getSystemService() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getSystemService();
	}

	/**
	 * Feed内のEntry最大値を取得.
	 * @return Feed内のEntry最大値
	 */
	public static int getEntryNumberLimit() {
		return getSystemPropInt(TaggingEnvConst.ENTRY_NUMBER_LIMIT,
				TaggingEnvConst.ENTRY_NUMBER_LIMIT_DEFAULT);
	}

	/**
	 * Feed内のEntry最大デフォルト値を取得.
	 * @return Feed内のEntry最大デフォルト値
	 */
	public static int getEntryNumberDefault(String serviceName)
			throws InvalidServiceSettingException{
		return getPropInt(serviceName, SettingConst.ENTRY_NUMBER_DEFAULT,
				TaggingEnvConst.ENTRY_NUMBER_DEFAULT_DEFAULT);

	}

	/**
	 * エイリアス取得最大値を取得.
	 * @return エイリアス取得最大値
	 */
	public static int getAliasNumberLimit() {
		return getSystemPropInt(TaggingEnvConst.ALIAS_NUMBER_LIMIT,
				TaggingEnvConst.ALIAS_NUMBER_LIMIT_DEFAULT);
	}

	/**
	 * 更新Feed内のEntry最大値を取得.
	 * @return 更新Feed内のEntry最大値
	 */
	public static int getAllocidsLimit() {
		return getSystemPropInt(TaggingEnvConst.ALLOCIDS_LIMIT,
				TaggingEnvConst.ALLOCIDS_LIMIT_DEFAULT);
	}

	/**
	 * 更新Feed内のEntry最大値を取得.
	 * @return 更新Feed内のEntry最大値
	 */
	public static int getUpdateEntryNumberLimit() {
		return getSystemPropInt(TaggingEnvConst.UPDATE_ENTRY_NUMBER_LIMIT,
				TaggingEnvConst.UPDATE_ENTRY_NUMBER_LIMIT_DEFAULT);
	}

	/**
	 * フェッチ最大数を取得.
	 * @return フェッチ最大数
	 */
	public static int getFetchLimit() {
		return getSystemPropInt(TaggingEnvConst.FETCH_LIMIT,
				TaggingEnvConst.FETCH_LIMIT_DEFAULT);
	}

	/**
	 * スーパーユーザ名を取得.
	 * @return スーパーユーザ名
	 */
	public static String getSuperuser() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getSystemProp(TaggingEnvConst.SUPER_USER,
				TaggingEnvConst.SUPER_USER_DEFAULT);
	}

	/**
	 * Index最大数を取得.
	 * @return Index最大数
	 */
	public static int getIndexLimit() {
		return getSystemPropInt(TaggingEnvConst.INDEX_LIMIT,
				TaggingEnvConst.INDEX_LIMIT_DEFAULT);
	}

	/**
	 * セッション有効時間を取得
	 * @param serviceName サービス名
	 * @return セッション有効時間(分)
	 */
	public static int getSessionMinute(String serviceName)
	throws InvalidServiceSettingException{
		return getPropInt(serviceName, SettingConst.SESSION_MINUTE,
				TaggingEnvConst.SESSION_MINUTE_DEFAULT);
	}

	/**
	 * RXID有効時間を取得
	 * @param serviceName サービス名
	 * @return RXID有効時間(分)
	 */
	public static int getRxidMinute(String serviceName)
	throws InvalidServiceSettingException{
		return getPropInt(serviceName, SettingConst.RXID_MINUTE,
				TaggingEnvConst.RXID_MINUTE_DEFAULT);
	}

	/**
	 * String型の値を取得.
	 * システム管理サービスの値を取得します。
	 * @param name プロパティ名
	 * @param defVal デフォルト値
	 * @return 値
	 */
	public static String getSystemProp(String name, String defVal) {
		return ReflexEnvUtil.getSystemProp(name, defVal);
	}

	/**
	 * int型の値を取得.
	 * システム管理サービスの値を取得します。
	 * @param name プロパティ名
	 * @param defVal デフォルト値
	 * @return 値
	 * @throws InvalidServiceSettingException Integerに変換できない値が設定されている場合
	 */
	public static int getSystemPropInt(String name, int defVal) {
		return ReflexEnvUtil.getSystemPropInt(name, defVal);
	}

	/**
	 * long型の値を取得.
	 * システム管理サービスの値を取得します。
	 * @param name プロパティ名
	 * @param defVal デフォルト値
	 * @return 値
	 * @throws InvalidServiceSettingException Longに変換できない値が設定されている場合
	 */
	public static long getSystemPropLong(String name, long defVal) {
		return ReflexEnvUtil.getSystemPropLong(name, defVal);
	}

	/**
	 * double型の値を取得.
	 * システム管理サービスの値を取得します。
	 * @param name プロパティ名
	 * @param defVal デフォルト値
	 * @return 値
	 * @throws InvalidServiceSettingException Doubleに変換できない値が設定されている場合
	 */
	public static double getSystemPropDouble(String name, double defVal) {
		return ReflexEnvUtil.getSystemPropDouble(name, defVal);
	}

	/**
	 * boolean型の値を取得.
	 * システム管理サービスの値を取得します。
	 * @param name プロパティ名
	 * @param defVal デフォルト値
	 * @return 値
	 * @throws InvalidServiceSettingException Booleanに変換できない値が設定されている場合
	 */
	public static boolean getSystemPropBoolean(String name, boolean defVal) {
		return ReflexEnvUtil.getSystemPropBoolean(name, defVal);
	}

	/**
	 * システムサービスの、先頭が指定されたキーと等しい環境設定情報一覧を取得
	 * @param prefix キーの先頭部分
	 * @return 環境設定情報一覧
	 */
	public static Map<String, String> getSystemPropMap(String prefix) {
		return ReflexEnvUtil.getSystemPropMap(prefix);
	}

	/**
	 * システムサービスの先頭が指定されたキーと等しい環境設定情報一覧を取得
	 * @param prefix キーの先頭部分
	 * @return 環境設定情報一覧をSortedMapで返却
	 */
	public static SortedMap<String, String> getSystemPropSortedMap(String prefix) {
		return ReflexEnvUtil.getSystemPropSortedMap(prefix);
	}

	/**
	 * システムサービスの、先頭が指定されたキーと等しい環境設定情報一覧を取得
	 * @param prefix キーの先頭部分
	 * @return 環境設定情報一覧をSetで返却
	 */
	public static Set<String> getSystemPropSet(String prefix) {
		return ReflexEnvUtil.getSystemPropSet(prefix);
	}

	/**
	 * String型の値を取得.
	 * @param serviceName サービス名
	 * @param name プロパティ名
	 * @param defVal デフォルト値
	 * @return 値
	 */
	public static String getProp(String serviceName, String name, String defVal) {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getProp(serviceName, name, defVal);
	}

	/**
	 * int型の値を取得.
	 * @param serviceName サービス名
	 * @param name プロパティ名
	 * @param defVal デフォルト値
	 * @return 値
	 */
	public static int getPropInt(String serviceName, String name, int defVal)
	throws InvalidServiceSettingException {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getPropInteger(serviceName, name, defVal);
	}

	/**
	 * long型の値を取得.
	 * @param serviceName サービス名
	 * @param name プロパティ名
	 * @param defVal デフォルト値
	 * @return 値
	 */
	public static long getPropLong(String serviceName, String name, long defVal)
	throws InvalidServiceSettingException {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getPropLong(serviceName, name, defVal);
	}

	/**
	 * boolean型の値を取得.
	 * @param serviceName サービス名
	 * @param name プロパティ名
	 * @param defVal デフォルト値
	 * @return 値
	 */
	public static boolean getPropBoolean(String serviceName, String name, boolean defVal)
	throws InvalidServiceSettingException {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getPropBoolean(serviceName, name, defVal);
	}

	/**
	 * 先頭が指定されたキーと等しい環境設定情報一覧を取得
	 * @param seriviceName サービス名
	 * @param prefix キーの先頭部分
	 * @return 環境設定情報一覧
	 */
	public static Map<String, String> getPropMap(String serviceName, String prefix)
	throws InvalidServiceSettingException {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getPropMap(serviceName, prefix);
	}

	/**
	 * 先頭が指定されたキーと等しい環境設定情報一覧を取得
	 * @param seriviceName サービス名
	 * @param prefix キーの先頭部分
	 * @return 環境設定情報一覧をSortedMapで返却
	 */
	public static SortedMap<String, String> getPropSortedMap(String serviceName,
			String prefix)
	throws InvalidServiceSettingException {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getPropSortedMap(serviceName, prefix);
	}

	/**
	 * 先頭が指定されたキーと等しい環境設定情報一覧を取得
	 * @param seriviceName サービス名
	 * @param prefix キーの先頭部分
	 * @return 環境設定情報一覧をSetで返却
	 */
	public static Set<String> getPropSet(String serviceName, String prefix)
			throws InvalidServiceSettingException {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getPropSet(serviceName, prefix);
	}

	/**
	 * 値のPatternオブジェクトを取得.
	 * @param serviceName サービス名
	 * @param name プロパティ名
	 * @return 値のPatternオブジェクト
	 */
	public static Pattern getPropPattern(String serviceName, String name) {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getPropPattern(serviceName, name);
	}

	/**
	 * サーバ稼働中かどうか.
	 * 起動処理中の場合falseを返す。
	 * @return サーバ稼働中の場合true
	 */
	public static boolean isRunning() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.isRunning();
	}

	/**
	 * サーブレット起動かどうか.
	 * @return サーブレット起動の場合true、バッチ処理の場合false
	 */
	public static boolean isServlet() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.isServlet();
	}

	/**
	 * Datastore managerを取得.
	 * @return Datastore manager
	 */
	public static DatastoreManager getDatastoreManager() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getDatastoreManager();
	}

	/**
	 * Cache managerを取得.
	 * @return Cache manager
	 */
	public static CacheManager getCacheManager() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getCacheManager();
	}

	/**
	 * Content managerを取得.
	 * @return Content manager
	 */
	public static ContentManager getContentManager() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getContentManager();
	}

	/**
	 * リクエスト・レスポンス管理プラグインを取得.
	 * @return リクエスト・レスポンス管理プラグイン
	 */
	public static RequestResponseManager getRequestResponseManager() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getRequestResponseManager();
	}

	/**
	 * 採番管理プラグインを取得.
	 * @return AllocateIds manager
	 */
	public static AllocateIdsManager getAllocateIdsManager() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getAllocateIdsManager();
	}

	/**
	 * 加算管理プラグインを取得.
	 * @return Increment manager
	 */
	public static IncrementManager getIncrementManager() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getIncrementManager();
	}

	/**
	 * 認証管理プラグインを取得.
	 * @return Authentication manager
	 */
	public static AuthenticationManager getAuthenticationManager() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getAuthenticationManager();
	}

	/**
	 * ACL管理プラグインを取得.
	 * @return ACL manager
	 */
	public static AclManager getAclManager() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getAclManager();
	}

	/**
	 * セッション管理プラグインを取得.
	 * @return Session manager
	 */
	public static SessionManager getSessionManager() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getSessionManager();
	}

	/**
	 * サービス管理プラグインを取得.
	 * @return Service manager
	 */
	public static ServiceManager getServiceManager() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getServiceManager();
	}

	/**
	 * ユーザ管理プラグインを取得.
	 * @return User manager
	 */
	public static UserManager getUserManager() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getUserManager();
	}

	/**
	 * アクセストークン管理プラグインを取得.
	 * @return access token manager
	 */
	public static AccessTokenManager getAccessTokenManager() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getAccessTokenManager();
	}

	/**
	 * RXID管理プラグインを取得.
	 * @return RXID manager
	 */
	public static RXIDManager getRXIDManager() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getRXIDManager();
	}

	/**
	 * ResourcMapper管理プラグインを取得.
	 * @return ResourcMapper manager
	 */
	public static ResourceMapperManager getResourceMapperManager() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getResourceMapperManager();
	}

	/**
	 * ログイン・ログアウト管理プラグインを取得.
	 * @return Login-Logout manager
	 */
	public static LoginLogoutManager getLoginLogoutManager() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getLoginLogoutManager();
	}
	
	/**
	 * 初期処理のアクセスログを出力するかどうかを取得.
	 * @return 初期処理のアクセスログを出力する場合true
	 */
	public static boolean isEnableAccesslog() {
		return TaggingEnvUtil.getSystemPropBoolean(
				TaggingEnvConst.INIT_ENABLE_ACCESSLOG, false);
	}

	/**
	 * ログ管理プラグインを取得.
	 * @return Log manager
	 */
	public static LogManager getLogManager() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getLogManager();
	}

	/**
	 * メッセージ管理プラグインを取得.
	 * @return Message manager
	 */
	public static MessageManager getMessageManager() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getMessageManager();
	}

	/**
	 * 設定管理プラグインを取得.
	 * @return Property manager
	 */
	public static PropertyManager getPropertyManager() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getPropertyManager();
	}

	/**
	 * セキュリティ管理プラグインを取得.
	 * @return Security manager
	 */
	public static ReflexSecurityManager getSecurityManager() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getSecurityManager();
	}

	/**
	 * キャプチャ管理プラグインを取得.
	 * @return Captcha manager
	 */
	public static CaptchaManager getCaptchaManager() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getCaptchaManager();
	}

	/**
	 * 非同期処理管理プラグインを取得.
	 * @return Task queue manager
	 */
	public static TaskQueueManager getTaskQueueManager() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getTaskQueueManager();
	}

	/**
	 * メール管理プラグインを取得.
	 * @return EMail manager
	 */
	public static EMailManager getEMailManager() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getEMailManager();
	}

	/**
	 * BigQuery管理プラグインを取得.
	 * @return BigQuery manager
	 */
	public static BigQueryManager getBigQueryManager() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getBigQueryManager();
	}

	/**
	 * 名前空間管理プラグインを取得.
	 * @return Namespace manager
	 */
	public static NamespaceManager getNamespaceManager() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getNamespaceManager();
	}

	/**
	 * モニター管理プラグインを取得.
	 * @return Monitor manager
	 */
	public static MonitorManager getMonitorManager() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getMonitorManager();
	}

	/**
	 * インメモリソート管理プラグインを取得.
	 * @return Memory sort manager
	 */
	public static MemorySortManager getMemorySortManager() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getMemorySortManager();
	}

	/**
	 * メッセージ通知管理プラグインを取得.
	 * @return push notification manager
	 */
	public static PushNotificationManager getPushNotificationManager() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getPushNotificationManager();
	}

	/**
	 * WebSocket管理プラグインを取得.
	 * @return websocket manager
	 */
	public static WebSocketManager getWebSocketManager() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getWebSocketManager();
	}

	/**
	 * PDF生成管理プラグインを取得.
	 * @return pdf manager
	 */
	public static PdfManager getPdfManager() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getPdfManager();
	}

	/**
	 * RDB管理プラグインを取得.
	 * @return RDB manager
	 */
	public static RDBManager getRDBManager() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getRDBManager();
	}

	/**
	 * OAuth管理プラグインを取得.
	 * @return oauth manager
	 */
	public static OAuthManager getOAuthManager() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getOAuthManager();
	}

	/**
	 * Secret管理プラグインを取得.
	 * @return secret manager
	 */
	public static SecretManager getSecretManager() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getSecretManager();
	}

	/**
	 * SettingService継承クラスを取得.
	 * @return SettingService継承クラス
	 */
	public static List<SettingService> getSettingServiceList() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getSettingServiceList();
	}

	/**
	 * CallingAfterCommit継承クラスを取得.
	 * @return CallingAfterCommit継承クラス
	 */
	public static List<CallingAfterCommit> getCallingAfterCommitList() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getCallingAfterCommitList();
	}

	/**
	 * ExecuteAtCreateService継承クラスを取得.
	 * @return ExecuteAtCreateService継承クラス
	 */
	public static List<ExecuteAtCreateService> getExecuteAtCreateServiceList() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		return env.getExecuteAtCreateServiceList();
	}

	/**
	 * シャットダウン処理.
	 */
	public static void destroy() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		env.close();
	}

	/**
	 * シャットダウン時の待ち処理を追加.
	 * @param servletName シャットダウン時の待ちサーブレット名
	 */
	public static void setAwaitShutdownOn(String servletName) {
		Map<String, Boolean> awaitShutdownMap = (Map<String, Boolean>)ReflexStatic.getStatic(
				TaggingEnvConst.NAME_AWAIT_SHUTDOWN);
		if (awaitShutdownMap == null) {
			Map<String, Boolean> tmpAwaitShutdownMap = new ConcurrentHashMap<>();
			try {
				ReflexStatic.setStatic(TaggingEnvConst.NAME_AWAIT_SHUTDOWN, tmpAwaitShutdownMap);
				awaitShutdownMap = tmpAwaitShutdownMap;
			} catch (StaticDuplicatedException e) {
				// 他スレッドでMap生成、格納
				awaitShutdownMap = (Map<String, Boolean>)ReflexStatic.getStatic(
						TaggingEnvConst.NAME_AWAIT_SHUTDOWN);
			}
		}
		awaitShutdownMap.put(servletName, true);
	}

	/**
	 * シャットダウン時の待ち処理を削除.
	 * @param servletName シャットダウン時の待ちサーブレット名
	 */
	public static void removeAwaitShutdown(String servletName) {
		Map<String, Boolean> awaitShutdownMap = (Map<String, Boolean>)ReflexStatic.getStatic(
				TaggingEnvConst.NAME_AWAIT_SHUTDOWN);
		if (awaitShutdownMap != null && awaitShutdownMap.containsKey(servletName)) {
			awaitShutdownMap.remove(servletName);
		}
	}

	/**
	 * シャットダウン時の待ち処理リストを取得.
	 * @return シャットダウン時の待ち処理リスト
	 */
	static Map<String, Boolean> getAwaitShutdownMap() {
		return (Map<String, Boolean>)ReflexStatic.getStatic(TaggingEnvConst.NAME_AWAIT_SHUTDOWN);
	}

}
