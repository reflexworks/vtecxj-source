package jp.reflexworks.taggingservice.api;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.regex.Pattern;

import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
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
 * 環境情報.
 * 実装クラスはサービス起動時に１つだけ生成される。
 */
public interface ReflexEnv extends BaseReflexEnv {

	/**
	 * テンプレート項目のIndex一覧を取得
	 * @param serviceName サービス名
	 * @return テンプレート項目のIndexを格納したMap
	 */
	public Map<String, Pattern> getTemplateIndexMap(String serviceName);

	/**
	 * テンプレート項目の全文検索Index一覧を取得
	 * @param serviceName サービス名
	 * @return テンプレート項目の全文検索Indexを格納したMap
	 */
	public Map<String, Pattern> getTemplateFullTextIndexMap(String serviceName);

	/**
	 * テンプレート項目のDISTKEY一覧を取得
	 * @param serviceName サービス名
	 * @return テンプレート項目のDISTKEYを格納したMap
	 */
	public Map<String, Pattern> getTemplateDistkeyMap(String serviceName);

	/**
	 * 設定値を返却
	 * @param serviceName サービス名
	 * @param key キー
	 * @return 設定値
	 */
	public String getProp(String serviceName, String key);

	/**
	 * 設定値を返却
	 * 設定値が無い場合はデフォルト値を返却します。
	 * @param serviceName サービス名
	 * @param key キー
	 * @param def デフォルト値。設定値がnullの場合に適用される。
	 * @return 設定値
	 */
	public String getProp(String serviceName, String key, String def);

	/**
	 * 設定値を返却
	 * @param serviceName サービス名
	 * @param key キー
	 * @return 設定値
	 * @throws InvalidServiceSettingException Integerに変換できない値が設定されている場合
	 */
	public Integer getPropInteger(String serviceName, String key)
			throws InvalidServiceSettingException;

	/**
	 * 設定値を返却
	 * 設定値が無い場合、Integer型に置き換えられない場合はデフォルト値を返却します。
	 * 設定値が無い場合はデフォルト値を返却します。
	 * @param serviceName サービス名
	 * @param key キー
	 * @param def デフォルト値。設定値がnullの場合に適用される。
	 * @return 設定値
	 * @throws InvalidServiceSettingException Integerに変換できない値が設定されている場合
	 */
	public Integer getPropInteger(String serviceName, String key, int def)
			throws InvalidServiceSettingException;

	/**
	 * 設定値を返却
	 * @param serviceName サービス名
	 * @param key キー
	 * @return 設定値
	 * @throws InvalidServiceSettingException Longに変換できない値が設定されている場合
	 */
	public Long getPropLong(String serviceName, String key)
	throws InvalidServiceSettingException;

	/**
	 * 設定値を返却
	 * 設定値が無い場合、Long型に置き換えられない場合はデフォルト値を返却します。
	 * @param serviceName サービス名
	 * @param key キー
	 * @param def デフォルト値。設定値がnullの場合に適用される。
	 * @return 設定値
	 * @throws InvalidServiceSettingException Longに変換できない値が設定されている場合
	 */
	public Long getPropLong(String serviceName, String key, long def)
			throws InvalidServiceSettingException;

	/**
	 * 設定値を返却
	 * @param serviceName サービス名
	 * @param key キー
	 * @return 設定値
	 * @throws InvalidServiceSettingException Booleanに変換できない値が設定されている場合
	 */
	public Boolean getPropBoolean(String serviceName, String key)
			throws InvalidServiceSettingException;

	/**
	 * 設定値を返却
	 * 設定値が無い場合、Boolean型に置き換えられない場合はデフォルト値を返却します。
	 * @param serviceName サービス名
	 * @param key キー
	 * @param def デフォルト値。設定値がnullの場合に適用される。
	 * @return 設定値
	 * @throws InvalidServiceSettingException Booleanに変換できない値が設定されている場合
	 */
	public Boolean getPropBoolean(String serviceName, String key, boolean def)
			throws InvalidServiceSettingException;

	/**
	 * 先頭が指定されたキーと等しい環境設定情報一覧を取得
	 * @param seriviceName サービス名
	 * @param prefix キーの先頭部分
	 * @return 環境設定情報一覧
	 */
	public Map<String, String> getPropMap(String serviceName, String prefix);

	/**
	 * 先頭が指定されたキーと等しい環境設定情報一覧を取得
	 * @param seriviceName サービス名
	 * @param prefix キーの先頭部分
	 * @return 環境設定情報一覧をSortedMapで返却
	 */
	public SortedMap<String, String> getPropSortedMap(String serviceName, String prefix);

	/**
	 * 先頭が指定されたキーと等しい環境設定情報一覧を取得
	 * @param seriviceName サービス名
	 * @param prefix キーの先頭部分
	 * @return 環境設定情報一覧をSetで返却
	 */
	public Set<String> getPropSet(String serviceName, String prefix);

	/**
	 * 値のPatternオブジェクトを取得.
	 * @param serviceName サービス名
	 * @param name プロパティ名
	 * @return 値のPatternオブジェクト
	 */
	public Pattern getPropPattern(String serviceName, String name);

	/**
	 * システムサービスを取得
	 * @return システムサービス名
	 */
	public String getSystemService();

	/**
	 * web.xmlまたはプロパティファイルから値を取得.
	 * @param name 名前
	 * @return web.xmlまたはプロパティファイルに設定された値
	 */
	public String getContextValue(String name);

	/**
	 * サービス設定処理を実装する管理クラスリストを取得.
	 * @return サービス設定処理を実装する管理クラスリスト
	 */
	public List<SettingService> getSettingServiceList();

	/**
	 * エントリー更新後の処理を実装する管理クラスリストを取得.
	 * @return エントリー更新後の処理を実装する管理クラスリスト
	 */
	public List<CallingAfterCommit> getCallingAfterCommitList();

	/**
	 * サービス登録時の処理を実装する管理クラスリストを取得.
	 * @return サービス登録時の処理を実装する管理クラスリスト
	 */
	public List<ExecuteAtCreateService> getExecuteAtCreateServiceList();

	/**
	 * Datastore managerを取得.
	 * @return Datastore manager
	 */
	public DatastoreManager getDatastoreManager();

	/**
	 * Content managerを取得.
	 * @return Content manager
	 */
	public ContentManager getContentManager();

	/**
	 * リクエスト・レスポンス管理プラグインを取得.
	 * @return リクエスト・レスポンス管理プラグイン
	 */
	public RequestResponseManager getRequestResponseManager();

	/**
	 * 採番管理プラグインを取得.
	 * @return 採番管理プラグイン
	 */
	public AllocateIdsManager getAllocateIdsManager();

	/**
	 * 数値加算管理プラグインを取得.
	 * @return 数値加算管理プラグイン
	 */
	public IncrementManager getIncrementManager();

	/**
	 * 認証管理プラグインを取得.
	 * @return 認証管理プラグイン
	 */
	public AuthenticationManager getAuthenticationManager();

	/**
	 * ACL管理プラグインを取得.
	 * @return ACL管理プラグイン
	 */
	public AclManager getAclManager();

	/**
	 * セッション管理プラグインを取得.
	 * @return セッション管理プラグイン
	 */
	public SessionManager getSessionManager();

	/**
	 * キャッシュ管理プラグインを取得.
	 * @return キャッシュ管理プラグイン
	 */
	public CacheManager getCacheManager();

	/**
	 * サービス管理プラグインを取得.
	 * @return サービス管理プラグイン
	 */
	public ServiceManager getServiceManager();

	/**
	 * ユーザ管理プラグインを取得.
	 * @return ユーザ管理プラグイン
	 */
	public UserManager getUserManager();

	/**
	 * アクセストークン管理プラグインを取得.
	 * @return アクセストークン管理プラグイン
	 */
	public AccessTokenManager getAccessTokenManager();

	/**
	 * RXID管理プラグインを取得.
	 * @return RXID管理プラグイン
	 */
	public RXIDManager getRXIDManager();

	/**
	 * ResourceMapper管理プラグインを取得.
	 * @return ResourceMapper管理プラグイン
	 */
	public ResourceMapperManager getResourceMapperManager();

	/**
	 * ログイン・ログアウト管理プラグインを取得.
	 * @return ログイン・ログアウト管理プラグイン
	 */
	public LoginLogoutManager getLoginLogoutManager();

	/**
	 * ログ管理プラグインを取得.
	 * @return ログ管理プラグイン
	 */
	public LogManager getLogManager();

	/**
	 * メッセージ管理プラグインを取得.
	 * @return メッセージ管理プラグイン
	 */
	public MessageManager getMessageManager();

	/**
	 * 設定管理プラグインを取得.
	 * @return 設定管理プラグイン
	 */
	public PropertyManager getPropertyManager();

	/**
	 * セキュリティ管理プラグインを取得.
	 * @return セキュリティ管理プラグイン
	 */
	public ReflexSecurityManager getSecurityManager();

	/**
	 * キャプチャ管理プラグインを取得.
	 * @return キャプチャ管理プラグイン
	 */
	public CaptchaManager getCaptchaManager();

	/**
	 * メール管理プラグインを取得.
	 * @return メール管理プラグイン
	 */
	public EMailManager getEMailManager();

	/**
	 * BigQuery管理プラグインを取得.
	 * @return BigQuery管理プラグイン
	 */
	public BigQueryManager getBigQueryManager();

	/**
	 * 非同期処理管理プラグインを取得.
	 * @return 非同期処理管理プラグイン
	 */
	public TaskQueueManager getTaskQueueManager();

	/**
	 * 名前空間管理プラグインを取得.
	 * @return 名前空間管理プラグイン
	 */
	public NamespaceManager getNamespaceManager();

	/**
	 * モニター管理プラグインを取得.
	 * @return モニター管理プラグイン
	 */
	public MonitorManager getMonitorManager();

	/**
	 * インメモリソート管理プラグインを取得.
	 * @return インメモリソート管理プラグイン
	 */
	public MemorySortManager getMemorySortManager();

	/**
	 * メッセージ通知管理プラグインを取得.
	 * @return メッセージ通知管理プラグイン
	 */
	public PushNotificationManager getPushNotificationManager();

	/**
	 * WebSocket管理プラグインを取得.
	 * @return WebSocket管理プラグイン
	 */
	public WebSocketManager getWebSocketManager();

	/**
	 * PDF生成管理プラグインを取得.
	 * @return PDF生成管理プラグイン
	 */
	public PdfManager getPdfManager();

	/**
	 * RDB管理プラグインを取得.
	 * @return RDB管理プラグイン
	 */
	public RDBManager getRDBManager();

	/**
	 * OAuth管理プラグインを取得.
	 * @return OAuth管理プラグイン
	 */
	public OAuthManager getOAuthManager();

	/**
	 * Secret管理プラグインを取得.
	 * @return Secret管理プラグイン
	 */
	public SecretManager getSecretManager();

	/**
	 * サーバ稼働中かどうか.
	 * 起動処理中の場合falseを返す。
	 * @return サーバ稼働中の場合true
	 */
	public boolean isRunning();

	/**
	 * サーブレット起動かどうか.
	 * @return サーブレット起動の場合true、バッチ処理の場合false
	 */
	public boolean isServlet();

	/**
	 * 初期データ登録処理かどうか.
	 * @return 初期データ登録処理の場合true
	 */
	public boolean isInitialized();
	
	/**
	 * シャットダウン時のクローズ処理.
	 */
	public void close();

}
