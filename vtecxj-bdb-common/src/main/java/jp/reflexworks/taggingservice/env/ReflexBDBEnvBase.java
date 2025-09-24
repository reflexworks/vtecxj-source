package jp.reflexworks.taggingservice.env;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.servlet.util.ServletContextUtil;
import jp.reflexworks.taggingservice.api.BaseReflexEnv;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.reflexworks.taggingservice.exception.PluginException;
import jp.reflexworks.taggingservice.plugin.ClosingForShutdown;
import jp.reflexworks.taggingservice.plugin.PluginUtil;
import jp.reflexworks.taggingservice.plugin.ReflexPlugin;
import jp.reflexworks.taggingservice.plugin.TaskQueueManager;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * Tagging BDB 環境情報
 */
public abstract class ReflexBDBEnvBase implements BaseReflexEnv {

	/** web.xmlとプロパティファイルから値を取得するクラス */
	private ServletContextUtil contextUtil;

	/** システムサービス名 */
	private String systemService;

	/** TaskQueue manager */
	protected Class<? extends TaskQueueManager> taskQueueManagerClass =
			jp.reflexworks.taggingservice.taskqueue.ReflexBDBTaskQueueManager.class;

	/** クローズ処理が必要なManagerリスト */
	private final List<Class<? extends ClosingForShutdown>> closingForShutdownList =
			new ArrayList<Class<? extends ClosingForShutdown>>();

	/** 起動中かどうか */
	private boolean isRunning;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ.
	 * @param contextUtil ServletContextUtil
	 */
	public ReflexBDBEnvBase(ServletContextUtil contextUtil) {
		this.contextUtil = contextUtil;

		// システムサービスの設定
		systemService = contextUtil.get(ReflexEnvConst.SYSTEM_SERVICE);
		if (StringUtils.isBlank(systemService)) {
			systemService = ReflexEnvConst.SYSTEM_SERVICE_DEFAULT;
		}
	}

	/**
	 * 初期処理.
	 */
	public void init() {
		if (logger.isInfoEnabled()) {
			logger.info("[init] start.");
		}

		// TimeZone設定
		initTimezone();

		// ResourceMapper初期設定
		initResourceMapper();

		// BDB初期設定
		initBDB();

		// TaskQueue初期設定
		initTaskQueue();

		// Plugin
		initPlugin();

		// 稼働中
		isRunning = true;

		if (logger.isInfoEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append("[init] Locale = ");
			sb.append(Locale.getDefault());
			sb.append(", TimeZone = ");
			sb.append(TimeZone.getDefault().getID());
			sb.append(", file.encoding = " + System.getProperty("file.encoding"));
			logger.debug(sb.toString());
			logger.info("[init] end.");
		}
	}

	/**
	 * 終了処理.
	 */
	public void close() {
		isRunning = false;

		// Plugin終了
		for (Class<? extends ClosingForShutdown> cls : closingForShutdownList) {
			try {
				ClosingForShutdown manager = (ClosingForShutdown)PluginUtil.newInstance(cls);
				manager.close();
			} catch (Throwable e) {
				logger.warn("[close] Error occurred.", e);
			}
		}

		// BDB終了処理
		try {
			closeBDB();
		} catch (Throwable e) {
			// Do nothing.
			logger.warn("[close] Error occured.", e);
		}
	}

	/**
	 * タイムゾーン設定
	 */
	private void initTimezone() {
		String timezone = getSystemProp(ReflexEnvConst.TIMEZONE);
		if (!StringUtils.isBlank(timezone)) {
			TimeZone.setDefault(TimeZone.getTimeZone(timezone));
		}
		if (logger.isTraceEnabled()) {
			logger.debug("[initTimezone] TimeZone=" + TimeZone.getDefault().getID());
		}

		// ロケールの設定
		String localeStr = getSystemProp(ReflexEnvConst.LOCALE);
		if (!StringUtils.isBlank(localeStr)) {
			String[] localeParts = localeStr.split(ReflexEnvConst.LOCALE_DELIMITER);
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
	 * ResourceMapper初期設定
	 */
	private void initResourceMapper() {
		String secretKey = "";	// BDBでは暗号化キーはブランク
		AtomResourceMapperManager resourceMapperManager = new AtomResourceMapperManager();
		resourceMapperManager.init(contextUtil, secretKey);
	}

	/**
	 * BDB環境初期設定
	 */
	private void initBDB() {
		BDBEnvManager bdbEnvManager = new BDBEnvManager();
		bdbEnvManager.init();
	}

	/**
	 * TaskQueue初期設定.
	 */
	private void initTaskQueue() {
		initPluginProc(taskQueueManagerClass);
	}

	/**
	 * プラグイン機能.
	 * 各継承クラスで実装すること。
	 */
	protected abstract void initPlugin();

	/**
	 * プラグインクラス名からクラスオブジェクト、インスタンスを生成する。
	 * @param managerClass プラグインクラス
	 */
	protected Class<? extends ReflexPlugin> initPluginProc(Class<? extends ReflexPlugin> managerClass) {
		try {
			// 初期処理
			ReflexPlugin manager = (ReflexPlugin)PluginUtil.newInstance(managerClass);
			manager.init();
			setManagerList(managerClass);
			return managerClass;

		} catch (PluginException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * 特定のインターフェースを継承している管理クラスの場合、リストに格納する。
	 * <ul>
	 *   <li>ClosingForShutdown</li>
	 * </ul>
	 * @param cls 管理クラス
	 */
	private void setManagerList(Class cls) {
		if (cls != null) {
			if (ClosingForShutdown.class.isAssignableFrom(cls)) {
				closingForShutdownList.add(cls);
			}
		}
	}

	/**
	 * BDB環境シャットダウン処理
	 */
	private void closeBDB() {
		BDBEnvManager bdbEnvManager = new BDBEnvManager();
		bdbEnvManager.close();
	}

	/**
	 * 標準ATOM形式のResourceMapperを返却.
	 * @return 標準ATOM形式のResourceMapper
	 */
	public FeedTemplateMapper getAtomResourceMapper() {
		AtomResourceMapperManager resourceMapperManager = new AtomResourceMapperManager();
		return resourceMapperManager.getAtomResourceMapper();
	}

	/**
	 * 指定されたキーの環境設定情報を取得.
	 * <p>
	 * 取得順は以下の通り。
	 * <ol>
	 * <li>web.xml</li>
	 * <li>プロパティファイル</li>
	 * </ol>
	 * </p>
	 * @param key キー
	 * @return 環境設定情報。環境変数の設定は変換されず、そのまま返却されます。
	 */
	public String getSystemProp(String key) {
		return getPropValue(key);
	}

	/**
	 * 指定されたキーの環境設定情報を取得.
	 * <p>
	 * 取得順は以下の通り。
	 * <ol>
	 * <li>web.xml</li>
	 * <li>プロパティファイル</li>
	 * </ol>
	 * </p>
	 * @param key キー
	 * @param def デフォルト値
	 * @return 環境設定情報。値がnullの場合はデフォルト値。
	 */
	public String getSystemProp(String key, String def) {
		String ret = getPropValue(key);
		if (!StringUtils.isBlank(ret)) {
			return ret;
		}
		return def;
	}

	/**
	 * 指定されたキーの環境設定情報を取得.
	 * @param key キー
	 * @param def デフォルト値
	 * @return 環境設定情報。値がnullの場合はデフォルト値。
	 */
	public int getSystemPropInteger(String key, int def) {
		String str = getPropValue(key);
		if (!StringUtils.isBlank(str)) {
			try {
				return Integer.parseInt(str);
			} catch (NumberFormatException e) {
				StringBuilder sb = new StringBuilder();
				sb.append("[SystemProperty] NumberFormatException: ");
				sb.append(e.getMessage());
				sb.append(" name = ");
				sb.append(key);
				throw new IllegalStateException(sb.toString());
			}
		}
		return def;
	}

	/**
	 * 指定されたキーの環境設定情報を取得.
	 * @param key キー
	 * @param def デフォルト値
	 * @return 環境設定情報。値がnullの場合はデフォルト値。
	 */
	public long getSystemPropLong(String key, long def) {
		String str = getPropValue(key);
		if (!StringUtils.isBlank(str)) {
			try {
				return Long.parseLong(str);
			} catch (NumberFormatException e) {
				StringBuilder sb = new StringBuilder();
				sb.append("[SystemProperty] NumberFormatException: ");
				sb.append(e.getMessage());
				sb.append(" name = ");
				sb.append(key);
				throw new IllegalStateException(sb.toString());
			}
		}
		return def;
	}

	/**
	 * 指定されたキーの環境設定情報を取得.
	 * @param key キー
	 * @param def デフォルト値
	 * @return 環境設定情報。値がnullの場合はデフォルト値。
	 */
	public double getSystemPropDouble(String key, double def) {
		String str = getPropValue(key);
		if (!StringUtils.isBlank(str)) {
			try {
				return Double.parseDouble(str);
			} catch (NumberFormatException e) {
				StringBuilder sb = new StringBuilder();
				sb.append("[SystemProperty] NumberFormatException: ");
				sb.append(e.getMessage());
				sb.append(" name = ");
				sb.append(key);
				throw new IllegalStateException(sb.toString());
			}
		}
		return def;
	}

	/**
	 * 指定されたキーの環境設定情報を取得.
	 * @param key キー
	 * @param def デフォルト値
	 * @return 環境設定情報。値がnullの場合はデフォルト値。
	 */
	public boolean getSystemPropBoolean(String key, boolean def) {
		String str = getPropValue(key);
		if (!StringUtils.isBlank(str)) {
			return Boolean.parseBoolean(str);
		}
		return def;
	}

	/**
	 * 先頭が指定されたキーと等しい環境設定情報一覧を取得
	 * @param prefix キーの先頭部分
	 * @return 環境設定情報一覧。環境変数の設定は変換されず、そのまま返却されます。
	 */
	public Map<String, String> getSystemPropMap(String prefix) {
		// 1. web.xml
		Map<String, String> value = contextUtil.getContextParams(prefix);
		if (value == null || value.size() == 0) {
			// 2. プロパティファイル
			value = contextUtil.getPropertyParams(prefix);
		}
		return value;
	}

	/**
	 * 先頭が指定されたキーと等しい環境設定情報一覧を取得
	 * @param prefix キーの先頭部分
	 * @return 環境設定情報一覧をSortedMapで返却。環境変数の設定は変換されず、そのまま返却されます。
	 */
	public SortedMap<String, String> getSystemPropSortedMap(String prefix) {
		// 1. web.xml
		SortedMap<String, String> value = contextUtil.getContextSortedParams(prefix);
		if (value == null || value.size() == 0) {
			// 2. プロパティファイル
			value = contextUtil.getPropertySortedParams(prefix);
		}
		return value;
	}

	/**
	 * 先頭が指定されたキーと等しい環境設定情報一覧を取得
	 * @param prefix キーの先頭部分
	 * @return 環境設定情報一覧をSetで返却。環境変数の設定は変換されず、そのまま返却されます。
	 */
	public Set<String> getSystemPropSet(String prefix) {
		// 1. web.xml
		Set<String> value = contextUtil.getContextSet(prefix);
		if (value == null || value.size() == 0) {
			// 2. プロパティファイル
			value = contextUtil.getPropertySet(prefix);
		}
		return value;
	}

	/**
	 * web.xmlまたはプロパティファイルの値を取得.
	 * @param key キー
	 * @return 値
	 */
	private String getPropValue(String key) {
		// 1. web.xml
		String value = contextUtil.getContext(key);
		if (value == null) {
			// 2. プロパティファイル
			value = contextUtil.getProperty(key);
		}
		return value;
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
	 * システムサービスを取得
	 * @return システムサービス名
	 */
	public String getSystemService() {
		return systemService;
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
	 * ResourceMapperを取得.
	 * Atom標準ResourceMapperを返却する。
	 */
	@Override
	public FeedTemplateMapper getResourceMapper(String serviceName) {
		return getAtomResourceMapper();
	}

	/**
	 * 指定されたキーの環境設定情報を取得.
	 * @param key キー
	 * @return 環境設定情報。値がnullの場合はそのまま返す。
	 */
	@Override
	public Integer getSystemPropInteger(String key) throws InvalidServiceSettingException {
		String str = getPropValue(key);
		if (!StringUtils.isBlank(str)) {
			try {
				return Integer.parseInt(str);
			} catch (NumberFormatException e) {
				StringBuilder sb = new StringBuilder();
				sb.append("[SystemProperty] NumberFormatException: ");
				sb.append(e.getMessage());
				sb.append(" name = ");
				sb.append(key);
				throw new IllegalStateException(sb.toString());
			}
		}
		return null;
	}

	/**
	 * 指定されたキーの環境設定情報を取得.
	 * @param key キー
	 * @return 環境設定情報。値がnullの場合はそのまま返す。
	 */
	@Override
	public Long getSystemPropLong(String key) throws InvalidServiceSettingException {
		String str = getPropValue(key);
		if (!StringUtils.isBlank(str)) {
			try {
				return Long.parseLong(str);
			} catch (NumberFormatException e) {
				StringBuilder sb = new StringBuilder();
				sb.append("[SystemProperty] NumberFormatException: ");
				sb.append(e.getMessage());
				sb.append(" name = ");
				sb.append(key);
				throw new IllegalStateException(sb.toString());
			}
		}
		return null;
	}

	/**
	 * 指定されたキーの環境設定情報を取得.
	 * @param key キー
	 * @return 環境設定情報。値がnullの場合そのまま返す。
	 */
	@Override
	public Double getSystemPropDouble(String key) throws InvalidServiceSettingException {
		String str = getPropValue(key);
		if (!StringUtils.isBlank(str)) {
			try {
				return Double.parseDouble(str);
			} catch (NumberFormatException e) {
				StringBuilder sb = new StringBuilder();
				sb.append("[SystemProperty] NumberFormatException: ");
				sb.append(e.getMessage());
				sb.append(" name = ");
				sb.append(key);
				throw new IllegalStateException(sb.toString());
			}
		}
		return null;

	}

	/**
	 * 指定されたキーの環境設定情報を取得.
	 * @param key キー
	 * @return 環境設定情報。値がnullの場合そのまま返す。
	 */
	@Override
	public Boolean getSystemPropBoolean(String key) throws InvalidServiceSettingException {
		String str = getPropValue(key);
		if (!StringUtils.isBlank(str)) {
			return Boolean.parseBoolean(str);
		}
		return null;
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

}
