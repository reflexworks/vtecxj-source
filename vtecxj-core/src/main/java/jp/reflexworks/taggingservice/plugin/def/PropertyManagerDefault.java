package jp.reflexworks.taggingservice.plugin.def;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.servlet.util.ServletContextUtil;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexStatic;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.SettingConst;
import jp.reflexworks.taggingservice.env.TaggingEnvConst;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.reflexworks.taggingservice.exception.StaticDuplicatedException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.StaticInfo;
import jp.reflexworks.taggingservice.plugin.PropertyManager;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.PropertyUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * プロパティ管理クラス.
 */
public class PropertyManagerDefault implements PropertyManager {

	/** システム設定接頭辞 */
	public static final String SYSTEM_PROP_PREFIX = "_";

	/** メモリ上のstaticオブジェクト格納キー : web.xmlとプロパティから値を取得するクラス */
	private static final String STATIC_NAME_PROPERTY_CONTEXTUTIL ="_property_contextutil";
	/** メモリ上のstaticオブジェクト格納キー : システムプロパティ */
	private static final String STATIC_NAME_PROPERTY_SYSTEMMAP ="_property_systemmap";
	/** メモリ上のstaticオブジェクト格納キー : サービスプロパティ */
	private static final String STATIC_NAME_PROPERTY_SERVICEMAPS ="_property_servicemaps";
	/** メモリ上のstaticオブジェクト格納キー : サービスプロパティのPatternオブジェクト */
	private static final String STATIC_NAME_PROPERTY_SERVICEPATTERNMAPS ="_property_servicepatternmaps";
	/** メモリ上のstaticオブジェクト格納キー : サービスプロパティのrevision + updated */
	private static final String STATIC_NAME_PROPERTY_SERVICEMAP_REVISIONS ="_property_servicemap_revisions";
	/** メモリ上のstaticオブジェクト格納キー : サービスプロパティ項目 */
	private static final String STATIC_NAME_PROPERTY_EACHSERVICEPROPS ="_property_eachserviceprops";

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 初期処理.
	 * このメソッドは使用しません。
	 */
	public void init() {
		// 使用しない
	}

	/**
	 * 初期処理
	 * サーバ起動時に一度だけ呼び出されます。
	 */
	public void init(ServletContextUtil contextUtil) {
		// static領域にオブジェクトを格納
		// ServletContextUtil
		try {
			ReflexStatic.setStatic(STATIC_NAME_PROPERTY_CONTEXTUTIL, contextUtil);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_PROPERTY_CONTEXTUTIL, e);
		}
		// systemMap
		ConcurrentMap<String, String> systemMap = new ConcurrentHashMap<String, String>();
		try {
			ReflexStatic.setStatic(STATIC_NAME_PROPERTY_SYSTEMMAP, systemMap);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_PROPERTY_SYSTEMMAP, e);
		}
		// serviceMap
		ConcurrentMap<String, Map<String, String>> serviceMaps =
				new ConcurrentHashMap<String, Map<String, String>>();
		try {
			ReflexStatic.setStatic(STATIC_NAME_PROPERTY_SERVICEMAPS, serviceMaps);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_PROPERTY_SERVICEMAPS, e);
		}
		// servicePatternMap
		ConcurrentMap<String, Map<String, Pattern>> servicePatternMaps =
				new ConcurrentHashMap<String, Map<String, Pattern>>();
		try {
			ReflexStatic.setStatic(STATIC_NAME_PROPERTY_SERVICEPATTERNMAPS, servicePatternMaps);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_PROPERTY_SERVICEPATTERNMAPS, e);
		}
		// serviceMap revisions
		ConcurrentMap<String, String> serviceMapRevisionAndUpdateds =
				new ConcurrentHashMap<String, String>();
		try {
			ReflexStatic.setStatic(STATIC_NAME_PROPERTY_SERVICEMAP_REVISIONS, serviceMapRevisionAndUpdateds);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_PROPERTY_SERVICEMAP_REVISIONS, e);
		}
		// eachServiceProps
		Set<String> eachServiceProps = initEachServiceProps();
		try {
			ReflexStatic.setStatic(STATIC_NAME_PROPERTY_EACHSERVICEPROPS, eachServiceProps);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_PROPERTY_EACHSERVICEPROPS, e);
		}
	}

	/**
	 * シャットダウン時の処理
	 */
	public void close() {
		// Do nothing.
	}

	/**
	 * 設定値を返却.
	 * @param serviceName サービス名
	 * @param key キー
	 * @return 設定値
	 */
	public String getValue(String serviceName, String key) {
		if (StringUtils.isBlank(key) || StringUtils.isBlank(serviceName)) {
			return null;
		}

		// まずサービスごとの設定
		boolean isEachService = isEachServiceProp(key);
		if (isEachService) {
			String val = getServiceSetting(key, serviceName);
			if (val != null) {
				return val;
			}
			String propPrefix = getEachServicePropPrefix(key);
			if (propPrefix != null) {
				// 前方一致設定で、サービスに他の設定がある場合、
				// システム設定の内容を返してはいけない。(メール等)
				if (hasOtherInfoByService(propPrefix, serviceName)) {
					return null;
				}
			}
		}
		// サービスごとの設定を適用しない場合、システム設定
		return get(key);
	}

	/**
	 * 設定値のPatternオブジェクトを返却.
	 * @param serviceName サービス名
	 * @param key キー
	 * @return 設定値のPatternオブジェクト
	 */
	public Pattern getPattern(String serviceName, String key) {
		if (StringUtils.isBlank(key) || StringUtils.isBlank(serviceName)) {
			return null;
		}

		// まずサービスごとの設定
		boolean isEachService = isEachServiceProp(key);
		if (isEachService) {
			Pattern val = getServiceSettingPattern(key, serviceName);
			if (val != null) {
				return val;
			}
			String propPrefix = getEachServicePropPrefix(key);
			if (propPrefix != null) {
				// 前方一致設定で、サービスに他の設定がある場合、
				// システム設定の内容を返してはいけない。(メール等)
				if (hasOtherInfoByService(propPrefix, serviceName)) {
					return null;
				}
			}
		}
		// サービスごとの設定を適用しない場合、システム設定
		return getServiceSettingPattern(key, TaggingEnvUtil.getSystemService());
	}

	/**
	 * 先頭が指定されたキーと等しい環境設定情報一覧を取得
	 * @param seriviceName サービス名
	 * @param prefix キーの先頭部分
	 * @return 環境設定情報一覧
	 */
	public Map<String, String> getMap(String serviceName, String prefix) {
		if (StringUtils.isBlank(prefix) || StringUtils.isBlank(serviceName)) {
			return null;
		}

		// まずサービスごとの設定
		boolean isEachService = isEachServiceProp(prefix);
		Map<String, String> serviceValues = null;
		if (isEachService) {
			serviceValues = getServiceSettingParams(prefix, serviceName);
			String propPrefix = getEachServicePropPrefix(prefix);
			if (propPrefix != null) {
				// 前方一致設定で、サービスに他の設定がある場合、システム設定の内容を返してはいけない。(メール等)
				if (propPrefix.equals(prefix)) {
					if (isIgnoreSystemIfExistServiceInfo(propPrefix) &&
							serviceValues != null && serviceValues.size() > 0) {
						return serviceValues;
					}
				} else {
					if (hasOtherInfoByService(propPrefix, serviceName)) {
						return serviceValues;
					}
				}
			}
		}

		// システム設定
		Map<String, String> systemValues = getMap(prefix);
		if (isEachService) {
			if (systemValues == null || systemValues.size() == 0) {
				return serviceValues;
			}
			if (serviceValues != null && serviceValues.size() > 0) {
				// サービスの設定値が優先
				systemValues.putAll(serviceValues);
			}
		}
		return systemValues;
	}

	/**
	 * 先頭が指定されたキーと等しい環境設定情報一覧を取得
	 * @param seriviceName サービス名
	 * @param prefix キーの先頭部分
	 * @return 環境設定情報一覧をSortedMapで返却
	 */
	public SortedMap<String, String> getSortedMap(String serviceName, String prefix) {
		if (StringUtils.isBlank(prefix) || StringUtils.isBlank(serviceName)) {
			return null;
		}

		// まずサービスごとの設定
		boolean isEachService = isEachServiceProp(prefix);
		SortedMap<String, String> serviceValues = null;
		if (isEachService) {
			serviceValues = getServiceSettingSortedParams(prefix, serviceName);
			String propPrefix = getEachServicePropPrefix(prefix);
			if (propPrefix != null) {
				// 前方一致設定で、サービスに他の設定がある場合、システム設定の内容を返してはいけない。(メール等)
				if (propPrefix.equals(prefix)) {
					if (isIgnoreSystemIfExistServiceInfo(propPrefix) &&
							serviceValues != null && serviceValues.size() > 0) {
						return serviceValues;
					}
				} else {
					if (hasOtherInfoByService(propPrefix, serviceName)) {
						return serviceValues;
					}
				}
			}
		}

		// システム設定
		SortedMap<String, String> systemValues = getSortedMap(prefix);
		if (isEachService) {
			if (systemValues == null || systemValues.size() == 0) {
				return serviceValues;
			}
			if (serviceValues != null && serviceValues.size() > 0) {
				// サービスの設定値が優先
				systemValues.putAll(serviceValues);
			}
		}
		return systemValues;
	}

	/**
	 * 先頭が指定されたキーと等しい環境設定情報一覧を取得
	 * @param seriviceName サービス名
	 * @param prefix キーの先頭部分
	 * @return 環境設定情報一覧をSetで返却
	 */
	public Set<String> getSet(String serviceName, String prefix) {
		if (StringUtils.isBlank(prefix) || StringUtils.isBlank(serviceName)) {
			return null;
		}

		// まずサービスごとの設定
		boolean isEachService = isEachServiceProp(prefix);
		Set<String> serviceValues = null;
		if (isEachService) {
			serviceValues = getServiceSettingSet(prefix, serviceName);
			String propPrefix = getEachServicePropPrefix(prefix);
			if (propPrefix != null) {
				// 前方一致設定で、サービスに他の設定がある場合、システム設定の内容を返してはいけない。(メール等)
				if (propPrefix.equals(prefix)) {
					if (isIgnoreSystemIfExistServiceInfo(propPrefix) &&
							serviceValues != null && serviceValues.size() > 0) {
						return serviceValues;
					}
				} else {
					if (hasOtherInfoByService(propPrefix, serviceName)) {
						return serviceValues;
					}
				}
			}
		}

		// システム設定
		Set<String> systemValues = getSet(prefix);
		if (isEachService) {
			if (systemValues == null || systemValues.size() == 0) {
				return serviceValues;
			}
			if (serviceValues != null && serviceValues.size() > 0) {
				// サービスの設定値が優先
				systemValues.addAll(serviceValues);
			}
		}
		return systemValues;
	}

	/**
	 * 指定されたキーの環境設定情報を取得.
	 * <p>
	 * 取得順は以下の通り。
	 * <ol>
	 * <li>web.xml</li>
	 * <li>/@/_system/settings</li>
	 * <li>プロパティファイル</li>
	 * </ol>
	 * </p>
	 * @param key キー
	 * @return 環境設定情報。環境変数の設定は変換されず、そのまま返却されます。
	 */
	private String get(String key) {
		ServletContextUtil contextUtil = getContextUtil();
		// 1. web.xml
		String value = contextUtil.getContext(key);
		if (value == null) {
			// 2. データストア
			Map<String, String> systemMap = getSystemMap();
			if (systemMap != null) {
				value = systemMap.get(key);
			}
			if (value == null) {
				// 3. プロパティファイル
				value = contextUtil.getProperty(key);
			}
		}
		return value;
	}

	/**
	 * 先頭が指定されたキーと等しい環境設定情報一覧を取得
	 * @param prefix キーの先頭部分
	 * @return 環境設定情報一覧。環境変数の設定は変換されず、そのまま返却されます。
	 */
	private Map<String, String> getMap(String prefix) {
		ServletContextUtil contextUtil = getContextUtil();
		// 1. web.xml
		Map<String, String> value = contextUtil.getContextParams(prefix);
		if (value == null || value.size() == 0) {
			Map<String, String> systemMap = getSystemMap();
			// 2. データストア
			if (systemMap != null) {
				value = new HashMap<String, String>();
				setParams(prefix, systemMap, value);
			}
			if (value == null || value.size() == 0) {
				// 3. プロパティファイル
				value = contextUtil.getPropertyParams(prefix);
			}
		}
		return value;
	}

	/**
	 * 先頭が指定されたキーと等しい環境設定情報一覧を取得
	 * @param prefix キーの先頭部分
	 * @return 環境設定情報一覧をSortedMapで返却。環境変数の設定は変換されず、そのまま返却されます。
	 */
	private SortedMap<String, String> getSortedMap(String prefix) {
		ServletContextUtil contextUtil = getContextUtil();
		// 1. web.xml
		SortedMap<String, String> value = contextUtil.getContextSortedParams(prefix);
		if (value == null || value.size() == 0) {
			// 2. データストア
			Map<String, String> systemMap = getSystemMap();
			if (systemMap != null) {
				value = new TreeMap<String, String>();
				setParams(prefix, systemMap, value);
			}
			if (value == null || value.size() == 0) {
				// 3. プロパティファイル
				value = contextUtil.getPropertySortedParams(prefix);
			}
		}
		return value;
	}

	/**
	 * 先頭が指定されたキーと等しい環境設定情報一覧を取得
	 * @param prefix キーの先頭部分
	 * @return 環境設定情報一覧をSetで返却。環境変数の設定は変換されず、そのまま返却されます。
	 */
	private Set<String> getSet(String prefix) {
		ServletContextUtil contextUtil = getContextUtil();
		// 1. web.xml
		Set<String> value = contextUtil.getContextSet(prefix);
		if (value == null || value.size() == 0) {
			// 2. データストア
			Map<String, String> systemMap = getSystemMap();
			if (systemMap != null) {
				value = new HashSet<String>();
				setSet(prefix, systemMap, value);
			}
			if (value == null || value.size() == 0) {
				// 3. プロパティファイル
				value = contextUtil.getPropertySet(prefix);
			}
		}
		return value;
	}

	/**
	 * サービスのプロパティかどうかを取得
	 * @param name プロパティキー
	 * @return サービスのプロパティであればtrue
	 */
	private boolean isEachServiceProp(String name) {
		if (StringUtils.isBlank(name)) {
			return false;
		}
		if (name.startsWith(SYSTEM_PROP_PREFIX)) {
			Set<String> eachServiceProps = getEachServiceProps();
			for (String prop : eachServiceProps) {
				if (prop.endsWith(".")) {
					if (name.startsWith(prop)) {
						return true;
					}
				} else {
					if (name.equals(prop)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * サービスのプロパティの接頭辞を取得
	 * @param name プロパティのキー
	 * @return サービスのプロパティの接頭辞
	 */
	private String getEachServicePropPrefix(String name) {
		if (StringUtils.isBlank(name)) {
			return null;
		}
		if (name.startsWith(SYSTEM_PROP_PREFIX)) {
			Set<String> eachServiceProps = getEachServiceProps();
			for (String prop : eachServiceProps) {
				if (prop.endsWith(".")) {
					if (name.startsWith(prop)) {
						return prop;
					}
				}
			}
		}
		return null;
	}

	/**
	 * serviceMapより指定されたキーの値を取得
	 * @param key プロパティのキー
	 * @return serviceMapより抽出された情報
	 */
	private String getServiceSetting(String key, String serviceName) {
		if (key != null) {
			if (!StringUtils.isBlank(serviceName)) {
				ConcurrentMap<String, Map<String, String>> serviceMaps = getServiceMaps();
				Map<String, String> serviceMap = serviceMaps.get(serviceName);
				if (serviceMap != null) {
					String value = serviceMap.get(key);
					if (value != null) {
						return value;
					}
				}
			}
		}
		return null;
	}

	/**
	 * servicePatternMapより指定されたキーの値のPatternオブジェクトを取得
	 * @param key プロパティのキー
	 * @return servicePatternMapより抽出されたPatternオブジェクト
	 */
	private Pattern getServiceSettingPattern(String key, String serviceName) {
		if (key != null) {
			if (!StringUtils.isBlank(serviceName)) {
				ConcurrentMap<String, Map<String, Pattern>> servicePatternMaps =
						getServicePatternMaps();
				Map<String, Pattern> servicePatternMap = servicePatternMaps.get(serviceName);
				if (servicePatternMap != null) {
					Pattern value = servicePatternMap.get(key);
					if (value != null) {
						return value;
					}
				}
			}
		}
		return null;
	}

	/**
	 * settingsMapより、キーの先頭がprefixの情報のみ抽出して返却する
	 * @param prefix キーの先頭
	 * @return settingsMapより抽出された情報
	 */
	private Map<String, String> getServiceSettingParams(String prefix, String serviceName) {
		if (StringUtils.isBlank(prefix) || StringUtils.isBlank(serviceName)) {
			return null;
		}
		Map<String, String> params = new HashMap<String, String>();
		ConcurrentMap<String, Map<String, String>> serviceMaps = getServiceMaps();
		Map<String, String> serviceMap = serviceMaps.get(serviceName);
		// サービス
		if (serviceMap != null) {
			setParams(prefix, serviceMap, params);
		}
		return params;
	}

	/**
	 * serviceMapより、キーの先頭が指定されたprefixの情報をSortedMapにして返却します.
	 * @param prefix 接頭辞
	 * @param serviceName サービス名
	 * @return 設定情報
	 */
	private SortedMap<String, String> getServiceSettingSortedParams(
			String prefix, String serviceName) {
		if (StringUtils.isBlank(prefix) || StringUtils.isBlank(serviceName)) {
			return null;
		}
		SortedMap<String, String> params = new TreeMap<String, String>();
		ConcurrentMap<String, Map<String, String>> serviceMaps = getServiceMaps();
		Map<String, String> serviceMap = serviceMaps.get(serviceName);
		// サービス
		if (serviceMap != null) {
			setParams(prefix, serviceMap, params);
		}
		return params;
	}

	/**
	 * settingsMapより、キーの先頭が指定されたprefixの情報のparam-valueをSetにして返却します。
	 * @param prefix キーの先頭
	 * @return settingsMapより抽出された情報
	 */
	private Set<String> getServiceSettingSet(String prefix, String serviceName) {
		if (StringUtils.isBlank(prefix) || StringUtils.isBlank(serviceName)) {
			return null;
		}
		Set<String> params = new HashSet<String>();
		ConcurrentMap<String, Map<String, String>> serviceMaps = getServiceMaps();
		Map<String, String> serviceMap = serviceMaps.get(serviceName);
		// サービス
		if (serviceMap != null) {
			setSet(prefix, serviceMap, params);
		}
		return params;
	}

	/**
	 * settingMapの値から接頭辞に一致するものを抽出し、paramsに格納する.
	 * @param prefix 接頭辞
	 * @param settingMap 設定情報Map
	 * @param params 対象の設定情報の格納先
	 */
	private void setParams(String prefix, Map<String, String> settingMap,
			Map<String, String> params) {
		if (prefix != null) {
			for (Map.Entry<String, String> mapEntry : settingMap.entrySet()) {
				String key = mapEntry.getKey();
				if (key.startsWith(prefix) && !params.containsKey(key)) {
					params.put(key, mapEntry.getValue());
				}
			}
		}
	}

	/**
	 * settingMapの値から接頭辞に一致するものを抽出し、paramsに格納する.
	 * @param prefix 接頭辞
	 * @param settingMap 設定情報Map
	 * @param params 対象の設定情報の格納先
	 */
	private void setSet(String prefix, Map<String, String> settingMap,
			Set<String> params) {
		if (prefix != null) {
			for (Map.Entry<String, String> mapEntry : settingMap.entrySet()) {
				String key = mapEntry.getKey();
				if (key.startsWith(prefix)) {
					params.add(mapEntry.getValue());
				}
			}
		}
	}

	/**
	 * サービスの情報が存在する場合、システムの情報を無視する設定かどうかチェックする。
	 * メールの設定対応
	 * @param prefix 接頭辞
	 * @param serviceName サービス名
	 * @return
	 */
	private boolean hasOtherInfoByService(String prefix, String serviceName) {
		// サービスの情報が存在する場合、システムの情報を無視する設定かどうかチェックする。
		boolean isIgnoreSystem = isIgnoreSystemIfExistServiceInfo(prefix);
		if (!isIgnoreSystem) {
			return false;
		}

		// 前方一致設定で、サービスに他の設定がある場合、システム設定の内容を返してはいけない。(メール等)
		Map<String, String> tmp = getServiceSettingParams(
				prefix, serviceName);
		if (tmp != null && tmp.size() > 0) {
			return true;
		}
		return false;
	}

	/**
	 * システム情報から除く接頭辞かどうかチェックする。
	 * mailの設定を除く。
	 * @param prefix 接頭辞
	 * @return システム情報から除く接頭辞の場合true
	 */
	private boolean isIgnoreSystemIfExistServiceInfo(String prefix) {
		return SettingConst.IGNORE_SYSTEM_IF_EXIST_SERVICE_INFO.contains(prefix);
	}

	/**
	 * static領域からServletContextUtilを取得.
	 * @return ServletContextUtil
	 */
	private ServletContextUtil getContextUtil() {
		return (ServletContextUtil)ReflexStatic.getStatic(
				STATIC_NAME_PROPERTY_CONTEXTUTIL);
	}

	/**
	 * static領域からsystemMapを取得.
	 * @return systemMap
	 */
	private ConcurrentMap<String, String> getSystemMap() {
		return (ConcurrentMap<String, String>)ReflexStatic.getStatic(
				STATIC_NAME_PROPERTY_SYSTEMMAP);
	}

	/**
	 * static領域からserviceMapを取得.
	 * @return serviceMap
	 */
	private ConcurrentMap<String, Map<String, String>> getServiceMaps() {
		return (ConcurrentMap<String, Map<String, String>>)ReflexStatic.getStatic(
				STATIC_NAME_PROPERTY_SERVICEMAPS);
	}

	/**
	 * static領域からservicePatternMapを取得.
	 * @return servicePatternMap
	 */
	private ConcurrentMap<String, Map<String, Pattern>> getServicePatternMaps() {
		return (ConcurrentMap<String, Map<String, Pattern>>)ReflexStatic.getStatic(
				STATIC_NAME_PROPERTY_SERVICEPATTERNMAPS);
	}

	/**
	 * static領域からserviceの revision + updated Mapを取得.
	 * @return serviceの revision + updated Map
	 */
	private ConcurrentMap<String, StaticInfo<String>> getServiceMapRevisionAndUpdateds() {
		return (ConcurrentMap<String, StaticInfo<String>>)ReflexStatic.getStatic(
				STATIC_NAME_PROPERTY_SERVICEMAP_REVISIONS);
	}

	/**
	 * static領域からeachServicePropsを取得.
	 * @return eachServiceProps
	 */
	private Set<String> getEachServiceProps() {
		return (Set<String>)ReflexStatic.getStatic(
				STATIC_NAME_PROPERTY_EACHSERVICEPROPS);
	}

	/**
	 * サービスごとに設定できるシステム設定項目を設定
	 * @return サービスごとに設定できるシステム設定項目リスト
	 */
	private Set<String> initEachServiceProps() {
		Set<String> eachServiceProps = new ConcurrentSkipListSet<String>();
		Field[] fields = SettingConst.class.getDeclaredFields();
		initEachServicePropsProc(eachServiceProps, fields);
		// 追加のサービス設定項目クラス
		Set<String> serviceSettingClassNames = getSet(
				TaggingEnvConst.SERVICESETTING_CLASS_PREFIX);
		if (serviceSettingClassNames != null) {
			for (String serviceSettingClassName : serviceSettingClassNames) {
				try {
					Class<?> serviceSettingClass = Class.forName(serviceSettingClassName);
					Field[] serviceSettingFields = serviceSettingClass.getDeclaredFields();
					initEachServicePropsProc(eachServiceProps, serviceSettingFields);

				} catch (ClassNotFoundException e) {
					logger.warn("[initEachServiceProps] ClassNotFoundException: " + e.getMessage());
				}
			}
		}

		return eachServiceProps;
	}

	/**
	 * サービスごとの設定項目を指定されたリストに登録
	 * @param eachServiceProps サービスごとの設定項目を格納するリスト
	 * @param fields サービスごとの設定項目を定義したクラスのフィールド配列
	 */
	private void initEachServicePropsProc(Set<String> eachServiceProps, Field[] fields) {
		if (fields == null) {
			return;
		}
		StringBuilder sbProps = new StringBuilder();
		for (Field field : fields) {
			int modifiers = field.getModifiers();
			if (Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers)) {
				try {
					Object fld = field.get(null);
					if (fld instanceof String) {
						String prop = (String)fld;
						if (prop != null && prop.startsWith(SYSTEM_PROP_PREFIX)) {
							eachServiceProps.add(prop);
							sbProps.append(prop);
							sbProps.append(", ");
						}
					}
				} catch (IllegalAccessException e) {
					logger.warn("[initEachServicePropsProc] IllegalAccessException: " + e.getMessage());
				}
			}
		}
		if (logger.isTraceEnabled()) {
			logger.debug("[initEachServicePropsProc] service props = " + sbProps.toString());
		}
	}

	/**
	 * サービス固有の設定値を返却.
	 *   /_settings/properties に設定された値のみ参照し返却.
	 * @param serviceName サービス名
	 * @param key キー
	 * @return 設定値
	 */
	public String getServiceSettingValue(String serviceName, String key) {
		ConcurrentMap<String, Map<String, String>> serviceMaps = getServiceMaps();
		Map<String, String> serviceMap = serviceMaps.get(serviceName);
		if (serviceMap != null) {
			return serviceMap.get(key);
		}
		return null;
	}

	/**
	 * サービスごとの設定処理.
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	@Override
	public void settingService(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// 設定処理が必要かチェック
		if (!isSettingUpdate(serviceName, requestInfo, connectionInfo)) {
			return;
		}

		ConcurrentMap<String, Map<String, String>> serviceMaps = getServiceMaps();
		ConcurrentMap<String, Map<String, Pattern>> servicePatternMaps = getServicePatternMaps();
		ConcurrentMap<String, StaticInfo<String>> serviceMapRevisionAndUpdateds =
				getServiceMapRevisionAndUpdateds();
		Map<String, String> serviceMap = serviceMaps.get(serviceName);
		if (serviceMap == null) {
			serviceMap = new ConcurrentHashMap<String, String>();
			serviceMaps.put(serviceName, serviceMap);
		}
		Map<String, Pattern> servicePatternMap = servicePatternMaps.get(serviceName);
		if (servicePatternMap == null) {
			servicePatternMap = new ConcurrentHashMap<String, Pattern>();
			servicePatternMaps.put(serviceName, servicePatternMap);
		}

		// /_settings/properties を読み、rightに指定された設定情報をstaticメモリに格納する。
		SystemContext systemContext = new SystemContext(serviceName, requestInfo,
				connectionInfo);
		EntryBase propEntry = systemContext.getEntry(Constants.URI_SETTINGS_PROPERTIES, true);
		Date now = new Date();
		if (propEntry != null && !StringUtils.isBlank(propEntry.rights)) {
			setPropertiesMap(propEntry.rights, serviceMap);
			setPropertiesPatternMap(serviceMap, servicePatternMap);
		} else {
			// サービスの設定なし
			serviceMap.clear();
			servicePatternMap.clear();
		}
		// revision + updated設定
		String revUpd = "";
		if (propEntry != null) {
			revUpd = TaggingEntryUtil.getUpdatedAndRevision(propEntry);
		}
		serviceMapRevisionAndUpdateds.put(serviceName, new StaticInfo<String>(revUpd, now));
	}

	/**
	 * 設定情報文字列を各設定情報にパースし、Mapに格納します.
	 * @param contextStr 設定情報文字列
	 * @param map Map
	 */
	private void setPropertiesMap(String contextStr, Map<String, String> map) {
		PropertyUtil.setPropertiesMap(contextStr, map);
	}

	/**
	 * サービスの設定情報から、Patternオブジェクト作成対象の設定があればPatternオブジェクトを作成し、Mapに格納する.
	 * @param serviceMap サービスの設定情報
	 * @param servicePatternMap Patternオブジェクト格納Map
	 */
	private void setPropertiesPatternMap(Map<String, String> serviceMap,
			Map<String, Pattern> servicePatternMap)
	throws InvalidServiceSettingException {
		PatternSyntaxException pse = null;
		servicePatternMap.clear();
		for (String patternName : SettingConst.SETTING_PATTERNS) {
			if (serviceMap.containsKey(patternName)) {
				String regex = serviceMap.get(patternName);
				if (!StringUtils.isBlank(regex)) {
					try {
						Pattern pattern = Pattern.compile(regex);
						servicePatternMap.put(patternName, pattern);
					} catch (PatternSyntaxException e) {
						logger.warn("[setPropertiesPatternMap] PatternSyntaxException: " + e.getMessage());
						pse = e;
					}
				}
			}
		}
		if (pse != null) {
			throw new InvalidServiceSettingException(pse);
		}
	}

	/**
	 * サービスごとの情報更新チェック.
	 * この処理は他のスレッドを排他して呼ばれます。
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	private boolean isSettingUpdate(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		boolean isSetting = false;
		ConcurrentMap<String, StaticInfo<String>> serviceMapRevisionAndUpdateds =
				getServiceMapRevisionAndUpdateds();
		StaticInfo<String> staticInfo = serviceMapRevisionAndUpdateds.get(serviceName);
		// StaticInfoの設定時刻が更新不要期間内であれば何もしない。
		String memRevUpd = null;
		if (staticInfo != null) {
			memRevUpd = staticInfo.getInfo();
		}
		SystemContext systemContext = new SystemContext(serviceName, requestInfo,
				connectionInfo);
		EntryBase propEntry = systemContext.getEntry(Constants.URI_SETTINGS_PROPERTIES, true);
		String currentRevUpd = null;
		if (propEntry != null) {
			currentRevUpd = TaggingEntryUtil.getUpdatedAndRevision(propEntry);
		}
		boolean update = false;
		if (!StringUtils.isBlank(memRevUpd)) {
			if (!memRevUpd.equals(currentRevUpd)) {
				update = true;
			}
		} else {
			if (!StringUtils.isBlank(currentRevUpd)) {
				update = true;
			}
		}

		if (update) {
			isSetting = true;
		} else {
			// StaticInfoのアクセス時間のみ更新
			Date now = new Date();
			staticInfo = new StaticInfo<String>(currentRevUpd, now);
			serviceMapRevisionAndUpdateds.put(serviceName, staticInfo);
		}

		return isSetting;
	}

	/**
	 * サービス情報クローズ.
	 * static領域にある指定されたサービスの情報を削除する。
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	@Override
	public void closeService(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		ConcurrentMap<String, Map<String, String>> serviceMaps = getServiceMaps();
		if (serviceMaps != null && serviceMaps.containsKey(serviceName)) {
			serviceMaps.remove(serviceName);
		}
		ConcurrentMap<String, Map<String, Pattern>> servicePatternMaps = getServicePatternMaps();
		if (servicePatternMaps != null && servicePatternMaps.containsKey(serviceName)) {
			servicePatternMaps.remove(serviceName);
		}
		ConcurrentMap<String, StaticInfo<String>> serviceMapRevisionAndUpdateds =
				getServiceMapRevisionAndUpdateds();
		if (serviceMapRevisionAndUpdateds != null && serviceMapRevisionAndUpdateds.containsKey(serviceName)) {
			serviceMapRevisionAndUpdateds.remove(serviceName);
		}
	}

	/**
	 * サービス初期設定に必要なURIリスト.
	 * サービス初期設定に必要なシステム管理サービスのURIリストを返却する。
	 * @param serviceName サービス名
	 * @return URIリスト
	 */
	@Override
	public List<String> getSettingEntryUrisBySystem(String serviceName) {
		return null;
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
		uris.add(Constants.URI_SETTINGS_PROPERTIES);
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
		List<String> uris = new ArrayList<>();
		uris.add(Constants.URI_SETTINGS_PROPERTIES);
		return uris;
	}

	/**
	 * サービス初期設定に共通して必要なURIリスト.
	 * サービス初期設定に共通して必要な自サービスのFeed検索用URIリストを返却する。
	 * @return URIリスト
	 */
	@Override
	public List<String> getSettingFeedUris() {
		return null;
	}

}
