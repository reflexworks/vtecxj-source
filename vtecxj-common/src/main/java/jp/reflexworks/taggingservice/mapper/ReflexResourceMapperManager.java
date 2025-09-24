package jp.reflexworks.taggingservice.mapper;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.api.AtomConst;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.atom.mapper.FeedTemplateMapper.Meta;
import jp.reflexworks.servlet.util.ServletContextUtil;
import jp.reflexworks.taggingservice.api.BaseReflexContext;
import jp.reflexworks.taggingservice.api.BaseReflexEnv;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexStatic;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.ReflexEnvConst;
import jp.reflexworks.taggingservice.env.ReflexEnvUtil;
import jp.reflexworks.taggingservice.exception.StaticDuplicatedException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.Template;
import jp.reflexworks.taggingservice.plugin.ResourceMapperManager;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.FileReaderUtil;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * ResourceMapper管理実装クラス.
 */
public class ReflexResourceMapperManager implements ResourceMapperManager,
		ReflexEnvConst {

	/** メモリ上のstaticオブジェクト格納キー : 暗号化のsecret key */
	private static final String STATIC_NAME_MAPPER_SECRETKEY ="_mapper_secretkey";
	/** メモリ上のstaticオブジェクト格納キー : ResourceMapper保持Map */
	private static final String STATIC_NAME_MAPPER_RESOURCEMAPPERMAP ="_mapper_resourcemappermap";
	/** メモリ上のstaticオブジェクト格納キー : テンプレートIndex保持Map */
	private static final String STATIC_NAME_MAPPER_TEMPLATEINDEXESMAP ="_mapper_templateindexesmap";
	/** メモリ上のstaticオブジェクト格納キー : テンプレート全文検索Index保持Map */
	private static final String STATIC_NAME_MAPPER_TEMPLATEFULLTEXTINDEXESMAP ="_mapper_templatefulltextindexesmap";
	/** メモリ上のstaticオブジェクト格納キー : テンプレートEntryのリビジョン保持Map */
	private static final String STATIC_NAME_MAPPER_TEMPLATEREVISIONSMAP ="_mapper_templaterevisionsmap";
	/** メモリ上のstaticオブジェクト格納キー : テンプレートEntryのupdated保持Map */
	private static final String STATIC_NAME_MAPPER_TEMPLATEUPDATEDSMAP ="_mapper_templateupdatedsmap";
	/** メモリ上のstaticオブジェクト格納キー : テンプレートDISTKEY保持Map */
	private static final String STATIC_NAME_MAPPER_TEMPLATEDISTKEYSMAP ="_mapper_templatedistkeysmap";

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 初期処理.
	 * このメソッドは使用しないが、Managerリストの一括初期処理でまとめて呼ばれるため何もしない。
	 */
	public void init() {
		// 使用しない
	}

	/**
	 * 初期処理に呼ばれるメソッド.
	 * TaggingEnv.getPropはまだ使用できない。
	 * web.xmlかプロパティファイルに設定された値の取得は、TaggingEnv.getContextValueを使用する。
	 * @param contextUtil ServletContextUtil
	 * @param secretKey 暗号化キー
	 */
	public void init(ServletContextUtil contextUtil, String secretKey) {
		BaseReflexEnv env = ReflexStatic.getEnv();

		// secretKeyをstatic領域に格納
		try {
			ReflexStatic.setStatic(STATIC_NAME_MAPPER_SECRETKEY, secretKey);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_MAPPER_SECRETKEY, e);
		}

		// ResorceMapper保持Map
		ConcurrentMap<String, FeedTemplateMapper> resourceMappers =
						new ConcurrentHashMap<String, FeedTemplateMapper>();
		try {
			ReflexStatic.setStatic(STATIC_NAME_MAPPER_RESOURCEMAPPERMAP, resourceMappers);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_MAPPER_RESOURCEMAPPERMAP, e);
		}
		// テンプレートIndex保持Map
		ConcurrentMap<String, Map<String, Pattern>> templateIndexes =
						new ConcurrentHashMap<String, Map<String, Pattern>>();
		try {
			ReflexStatic.setStatic(STATIC_NAME_MAPPER_TEMPLATEINDEXESMAP, templateIndexes);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_MAPPER_TEMPLATEINDEXESMAP, e);
		}
		// テンプレート全文検索Index保持Map
		ConcurrentMap<String, Map<String, Pattern>> templateFullTextIndexes =
						new ConcurrentHashMap<String, Map<String, Pattern>>();
		try {
			ReflexStatic.setStatic(STATIC_NAME_MAPPER_TEMPLATEFULLTEXTINDEXESMAP, templateFullTextIndexes);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_MAPPER_TEMPLATEFULLTEXTINDEXESMAP, e);
		}
		// テンプレートEntryのリビジョン保持Map
		ConcurrentMap<String, Integer> revisions =
						new ConcurrentHashMap<String, Integer>();
		try {
			ReflexStatic.setStatic(STATIC_NAME_MAPPER_TEMPLATEREVISIONSMAP, revisions);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_MAPPER_TEMPLATEREVISIONSMAP, e);
		}
		// テンプレートEntryのupdated保持Map
		ConcurrentMap<String, String> updateds =
						new ConcurrentHashMap<String, String>();
		try {
			ReflexStatic.setStatic(STATIC_NAME_MAPPER_TEMPLATEUPDATEDSMAP, updateds);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_MAPPER_TEMPLATEUPDATEDSMAP, e);
		}
		// テンプレートDISTKEY保持Map
		ConcurrentMap<String, Map<String, Pattern>> templateDistkeys =
						new ConcurrentHashMap<String, Map<String, Pattern>>();
		try {
			ReflexStatic.setStatic(STATIC_NAME_MAPPER_TEMPLATEDISTKEYSMAP, templateDistkeys);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_MAPPER_TEMPLATEDISTKEYSMAP, e);
		}

		String systemService = env.getSystemService();
		// 標準ATOMのResourceMapperを生成
		String[] template = AtomConst.TEMPLATE_DEFAULT_ARRAY;
		String[] rights = getDefaultRights(contextUtil);
		FeedTemplateMapper atomMapper = createResourceMapper(template, rights,
				ReflexEnvConst.INDEX_LIMIT_DEFAULT);
		resourceMappers.put(ATOM_STANDARD, atomMapper);
		resourceMappers.put(systemService, atomMapper);

		// テンプレート情報
		putTemplateIndex(ATOM_STANDARD);
	}

	/**
	 * シャットダウン時の処理.
	 */
	@Override
	public void close() {
		// Do nothing.
	}

	/**
	 * 標準ATOM形式のResourceMapperを返却.
	 * @reutrn 標準ATOM形式のResourceMapper
	 */
	public FeedTemplateMapper getAtomResourceMapper() {
		Map<String, FeedTemplateMapper> resourceMappers = getResourceMappers();
		return resourceMappers.get(ATOM_STANDARD);
	}

	/**
	 * サービスのResourceMapperを返却.
	 * @param serviceName サービス名
	 * @reutrn ResourceMapper
	 */
	public FeedTemplateMapper getResourceMapper(String serviceName) {
		Map<String, FeedTemplateMapper> resourceMappers = getResourceMappers();
		FeedTemplateMapper mapper = resourceMappers.get(serviceName);
		if (mapper == null) {
			if (logger.isTraceEnabled()) {
				logger.trace("[getResourceMapper] mapper is null. serviceName = " + serviceName);
			}
			// デフォルトのResourceMapperを返却
			mapper = getAtomResourceMapper();
		}
		return mapper;
	}
	/**
	 * FeedTemplateMapper設定
	 * テンプレート指定用
	 * @param template テンプレート情報
	 */
	private FeedTemplateMapper createResourceMapper(Template template) {
		if (template != null) {
			return createResourceMapper(template.template, template.rights,
					getIndexLimit());
		}
		return null;
	}

	/**
	 * FeedTemplateMapper設定
	 * テンプレート指定用
	 * @param template テンプレート情報
	 * @param rights Index、暗号化、項目ACL情報
	 * @param indexLimit 最大インデックス数
	 */
	private FeedTemplateMapper createResourceMapper(String[] template, String[] rights,
			int indexLimit) {
		try {
			String secretKey = getSecretKey();
			return new FeedTemplateMapper(template, rights, indexLimit, secretKey);

		} catch (ParseException e) {
			throw new IllegalStateException(e);	// 通常エラーとならない
		}
	}

	/**
	 * Index最大数を取得.
	 * @return Index最大数
	 */
	private int getIndexLimit() {
		return ReflexEnvUtil.getSystemPropInt(ReflexEnvConst.INDEX_LIMIT,
				ReflexEnvConst.INDEX_LIMIT_DEFAULT);
	}

	/**
	 * Feed内のEntry最大値を取得.
	 * @return Feed内のEntry最大値
	 */
	private int getEntryNumberLimit() {
		return ReflexEnvUtil.getSystemPropInt(ReflexEnvConst.ENTRY_NUMBER_LIMIT,
				ReflexEnvConst.ENTRY_NUMBER_LIMIT_DEFAULT);
	}

	/**
	 * テンプレートのIndex情報を設定
	 * 全文検索Index情報についても設定する。
	 * @param serviceName サービス名
	 */
	private void putTemplateIndex(String serviceName) {
		Map<String, Pattern> templateIndexMap =
				new ConcurrentHashMap<String, Pattern>();
		Map<String, Pattern> templateFullTextIndexMap =
				new ConcurrentHashMap<String, Pattern>();
		Map<String, Pattern> templateDistkeyMap =
				new ConcurrentHashMap<String, Pattern>();
		// テンプレートのメタ情報取得
		Map<String, FeedTemplateMapper> resourceMappers = getResourceMappers();
		List<Meta> metalist = resourceMappers.get(serviceName).getMetalist();
		if (metalist != null) {
			for (Meta meta : metalist) {
				if (meta.index != null) {
					// インデックス対象項目
					Pattern pattern = compilePattern("InnerIndex : " + meta.name,
							meta.index, serviceName);
					if (pattern != null) {
						templateIndexMap.put(meta.name, pattern);
					}
				}
				if (meta.search != null) {
					// 全文検索インデックス対象項目
					Pattern pattern = compilePattern("FullTextIndex : " + meta.name,
							meta.search, serviceName);
					if (pattern != null) {
						templateFullTextIndexMap.put(meta.name, pattern);
					}
				}
				if (meta.distkey != null) {
					// DISTKEYインデックス対象項目
					Pattern pattern = compilePattern("DISTKEY : " + meta.name,
							meta.distkey, serviceName);
					if (pattern != null) {
						templateDistkeyMap.put(meta.name, pattern);
					}
				}
			}
		}
		Map<String, Map<String, Pattern>> templateIndexes = getTemplateIndexes();
		templateIndexes.put(serviceName, templateIndexMap);
		Map<String, Map<String, Pattern>> templateFullTextIndexes = getTemplateFullTextIndexes();
		templateFullTextIndexes.put(serviceName, templateFullTextIndexMap);
		Map<String, Map<String, Pattern>> templateDistkeys = getTemplateDistkeys();
		templateDistkeys.put(serviceName, templateDistkeyMap);
	}

	/**
	 * Patternを生成
	 * @param key キー (エラー時のログに使用)
	 * @param regex 正規表現
	 * @param serviceName サービス名 (エラー時のログに使用)
	 * @return Pattern
	 */
	private Pattern compilePattern(String key, String regex, String serviceName) {
		try {
			return Pattern.compile(regex);
		} catch (PatternSyntaxException e) {
			StringBuilder sb = new StringBuilder();
			sb.append("(");
			sb.append(serviceName);
			sb.append(")");
			sb.append("PatternSyntaxException : ");
			sb.append(e.getMessage());
			sb.append(", ");
			sb.append(key);
			sb.append("=");
			sb.append(regex);
			logger.warn(sb.toString());
		}
		return null;
	}

	/**
	 * テンプレート項目のIndex一覧を取得
	 * @param serviceName サービス名
	 * @return テンプレート項目のIndexを格納したMap
	 */
	public Map<String, Pattern> getTemplateIndexMap(String serviceName) {
		Map<String, Pattern> templateIndexMap = null;
		Map<String, Map<String, Pattern>> templateIndexes = getTemplateIndexes();
		if (serviceName != null) {
			templateIndexMap = templateIndexes.get(serviceName);
		}
		if (templateIndexMap == null) {
			templateIndexMap = templateIndexes.get(ATOM_STANDARD);
		}
		return templateIndexMap;
	}

	/**
	 * テンプレート項目の全文検索Index一覧を取得
	 * @param serviceName サービス名
	 * @return テンプレート項目の全文検索Indexを格納したMap
	 */
	public Map<String, Pattern> getTemplateFullTextIndexMap(String serviceName) {
		Map<String, Pattern> templateFullTextIndexMap = null;
		Map<String, Map<String, Pattern>> templateFullTextIndexes = getTemplateFullTextIndexes();
		if (serviceName != null) {
			templateFullTextIndexMap = templateFullTextIndexes.get(serviceName);
		}

		// test log
		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append("[getTemplateFullTextIndexMap] serviceName = ");
			sb.append(serviceName);
			sb.append(" , templateFullTextIndexes = ");
			sb.append(templateFullTextIndexes);
			sb.append(" , templateFullTextIndexMap = ");
			sb.append(templateFullTextIndexMap);
			logger.debug(sb.toString());
		}

		if (templateFullTextIndexMap == null) {
			templateFullTextIndexMap = templateFullTextIndexes.get(ATOM_STANDARD);
		}
		return templateFullTextIndexMap;
	}

	/**
	 * テンプレート項目のDISTKEY一覧を取得
	 * @param serviceName サービス名
	 * @return テンプレート項目のDISTKEYを格納したMap
	 */
	public Map<String, Pattern> getTemplateDistkeyMap(String serviceName) {
		Map<String, Pattern> templateDistkeyMap = null;
		Map<String, Map<String, Pattern>> templateDistkeys = getTemplateDistkeys();
		if (serviceName != null) {
			templateDistkeyMap = templateDistkeys.get(serviceName);
		}
		if (templateDistkeyMap == null) {
			templateDistkeyMap = templateDistkeys.get(ATOM_STANDARD);
		}
		return templateDistkeyMap;
	}

	/**
	 * テンプレートURIを取得
	 * @return テンプレートURI
	 */
	private String getTemplateUri() {
		return Constants.URI_SETTINGS_TEMPLATE;
	}

	/**
	 * デフォルトのIndex、暗号化、項目ACL情報を配列にして返却.
	 * FeedTemplateMapper生成時の引数に使用する。
	 * <ul>
	 *   <li>contributor=@+RW,/_group/$admin+RW</li>
	 *   <li>contributor.uri#</li>
	 *   <li>rights#=@+RW,/_group/$admin+RW</li>
	 *   <li>title:^/_user/.+$</li>
	 * </ul>
	 * @param serviceName サービス名
	 * @return Index、暗号化、項目ACL情報
	 */
	private String[] getDefaultRights(ServletContextUtil contextUtil) {
		// 初期処理時はPropertyManagerが起動していないので、TaggingEnvUtil.getSystemPropSetが使用できない。
		Set<String> defaultRights = contextUtil.getSet(
				ReflexEnvConst.INDEXENCRYPTFIELDACL_PRESET_PREFIX);
		return defaultRights.toArray(new String[0]);
	}

	/**
	 * デフォルトのIndex、暗号化、項目ACL情報を配列にして返却.
	 * FeedTemplateMapper生成時の引数に使用する。
	 * <ul>
	 *   <li>contributor=@+RW,/_group/$admin+RW</li>
	 *   <li>contributor.uri#</li>
	 *   <li>rights#=@+RW,/_group/$admin+RW</li>
	 *   <li>title:^/_user/.+$</li>
	 * </ul>
	 * @param serviceName サービス名
	 * @return Index、暗号化、項目ACL情報
	 */
	private String[] getDefaultRights() {
		Set<String> defaultRights = ReflexEnvUtil.getSystemPropSet(
				ReflexEnvConst.INDEXENCRYPTFIELDACL_PRESET_PREFIX);
		return defaultRights.toArray(new String[0]);
	}

	/**
	 * テンプレートエントリーのキーであればtrueを返却します.
	 * <p>
	 * キーが /_settings/template であればテンプレートエントリーです。
	 * </p>
	 * @param uri キー
	 * @return テンプレートエントリーのキーであればtrue
	 */
	public boolean isTemplateUri(String uri) {
		return Constants.URI_SETTINGS_TEMPLATE.equals(uri);
	}

	/**
	 * テンプレート情報を取得.
	 * テンプレート保持Entry /_settings/template のcontextとrightsから
	 * テンプレート情報を取得します。
	 * @param settingsEntry テンプレート保持Entry (/_settings/template)
	 * @param serviceName サービス名
	 * @return テンプレート情報
	 */
	public Template getTemplate(EntryBase settingsEntry, String serviceName) {
		String templateStr = null;
		String rightsStr = null;
		if (settingsEntry != null) {
			templateStr = settingsEntry.getContentText();
			rightsStr = settingsEntry.rights;
		}
		return getTemplate(templateStr, rightsStr, getEntryNumberLimit(),
				serviceName);
	}

	/**
	 * テンプレート情報を取得.
	 * @param templateStr エンティティ定義文字列
	 * @param rightsStr Index・暗号化・項目ACL文字列
	 * @param entryNumberLimit 1Feedあたりの最大Entry数
	 * @param serviceName サービス名
	 * @return テンプレート情報
	 */
	private Template getTemplate(String templateStr, String rightsStr,
			int entryNumberLimit, String serviceName) {
		// 先にデフォルトpropAclsの設定
		String[] defaultRights = getDefaultRights();

		if (StringUtils.isBlank(templateStr)) {
			return new Template(AtomConst.TEMPLATE_DEFAULT_ARRAY, defaultRights);
		}

		String[] tmpTemplate = FileReaderUtil.convertArray(templateStr);
		String firstLine = getTemplateFirstLine(serviceName, entryNumberLimit);
		String[] template = null;
		if (tmpTemplate != null) {
			template = new String[tmpTemplate.length + 1];
			template[0] = firstLine;
			System.arraycopy(tmpTemplate, 0, template, 1, tmpTemplate.length);
		}

		if (template == null) {
			return new Template(AtomConst.TEMPLATE_DEFAULT_ARRAY, defaultRights);
		}

		String[] tmpRights = FileReaderUtil.convertArray(rightsStr);
		String[] rights = null;
		if (defaultRights != null) {
			if (tmpRights != null) {
				rights = new String[defaultRights.length + tmpRights.length];
				System.arraycopy(defaultRights, 0, rights, 0, defaultRights.length);
				System.arraycopy(tmpRights, 0, rights, defaultRights.length, tmpRights.length);
			} else {
				rights = defaultRights;
			}
		} else {
			if (tmpRights != null) {
				rights = tmpRights;
			}
		}
		return new Template(template, rights);
	}

	/**
	 * エンティティ情報の最初の1行を取得する.
	 * @param pkg package名。Entryクラスのパッケージとなる。
	 * @param entryNumberLimit 1Feedあたりの最大Entry数
	 * @return エンティティ情報の最初の1行
	 */
	private String getTemplateFirstLine(String pkg, int entryNumberLimit) {
		// ハイフンを$に変換する
		String editPkg = pkg.replace("-", "$");
		StringBuilder buf = new StringBuilder();
		buf.append(editPkg);
		buf.append("{");
		buf.append(entryNumberLimit);
		buf.append("}");
		return buf.toString();
	}

	/**
	 * static領域からシークレットキーを取得.
	 * @return シークレットキー
	 */
	private String getSecretKey() {
		return (String)ReflexStatic.getStatic(STATIC_NAME_MAPPER_SECRETKEY);
	}

	/**
	 * static領域からResorceMapper保持Mapを取得.
	 * @return ResorceMapper保持Map
	 */
	private ConcurrentMap<String, FeedTemplateMapper> getResourceMappers() {
		return (ConcurrentMap<String, FeedTemplateMapper>)ReflexStatic.getStatic(
				STATIC_NAME_MAPPER_RESOURCEMAPPERMAP);
	}

	/**
	 * static領域からテンプレートIndex保持Mapを取得.
	 * @return テンプレートIndex保持Map
	 */
	private ConcurrentMap<String, Map<String, Pattern>> getTemplateIndexes() {
		return (ConcurrentMap<String, Map<String, Pattern>>)ReflexStatic.getStatic(
				STATIC_NAME_MAPPER_TEMPLATEINDEXESMAP);
	}

	/**
	 * static領域からテンプレート全文検索Index保持Mapを取得.
	 * @return テンプレートト全文検索Index保持Map
	 */
	private ConcurrentMap<String, Map<String, Pattern>> getTemplateFullTextIndexes() {
		return (ConcurrentMap<String, Map<String, Pattern>>)ReflexStatic.getStatic(
				STATIC_NAME_MAPPER_TEMPLATEFULLTEXTINDEXESMAP);
	}

	/**
	 * static領域からテンプレートEntryのリビジョン保持Mapを取得.
	 * @return テンプレートEntryのリビジョン保持Map
	 */
	private ConcurrentMap<String, Integer> getRevisions() {
		return (ConcurrentMap<String, Integer>)ReflexStatic.getStatic(
				STATIC_NAME_MAPPER_TEMPLATEREVISIONSMAP);
	}

	/**
	 * static領域からテンプレートEntryのupdated保持Mapを取得.
	 * @return テンプレートEntryのupdated保持Map
	 */
	private ConcurrentMap<String, String> getUpdateds() {
		return (ConcurrentMap<String, String>)ReflexStatic.getStatic(
				STATIC_NAME_MAPPER_TEMPLATEUPDATEDSMAP);
	}

	/**
	 * static領域からテンプレートDISTKEY保持Mapを取得.
	 * @return テンプレートトDISTKEY保持Map
	 */
	private ConcurrentMap<String, Map<String, Pattern>> getTemplateDistkeys() {
		return (ConcurrentMap<String, Map<String, Pattern>>)ReflexStatic.getStatic(
				STATIC_NAME_MAPPER_TEMPLATEDISTKEYSMAP);
	}

	/**
	 * サービスごとの設定処理.
	 * 引数が BaseReflexContext のメソッドが呼ばれるため、このメソッドは使用しない。
	 */
	@Override
	public void settingService(String serviceName, RequestInfo requestInfo, ConnectionInfo connectionInfo)
			throws IOException, TaggingException {
		// Do nothing.
	}

	/**
	 * サービスごとの設定処理.
	 * この処理は他のスレッドを排他して呼ばれます。
	 * @param systemService システム権限のReflexContext
	 * @return サービス初期設定で内部情報が更新された場合true
	 */
	public boolean settingService(BaseReflexContext systemContext)
	throws IOException, TaggingException {
		boolean ret = false;
		String serviceName = systemContext.getServiceName();
		RequestInfo requestInfo = systemContext.getRequestInfo();
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) + "[settingService] start.");
		}
		EntryBase templateEntry = systemContext.getEntry(getTemplateUri(), true);	// useCache=true
		if (templateEntry != null) {
			Map<String, Integer> revisions = getRevisions();
			Map<String, String> updateds = getUpdateds();
			// リビジョンのチェック
			boolean needCreate = false;
			Integer currentRev = revisions.get(serviceName);
			String currentUpdated = updateds.get(serviceName);
			Integer newRev = TaggingEntryUtil.getRevisionById(templateEntry.id);
			String newUpdated = templateEntry.updated;
			if (newUpdated == null) {
				newUpdated = templateEntry.published;
			}
			if (currentRev == null) {
				needCreate = true;
			} else {
				if (newRev > currentRev || (newUpdated != null &&
						newUpdated.compareTo(currentUpdated) > 0)) {
					needCreate = true;
				}
			}

			if (needCreate) {
				Template template = getTemplate(templateEntry, serviceName);
				FeedTemplateMapper mapper = createResourceMapper(template);
				if (mapper != null) {
					ConcurrentMap<String, FeedTemplateMapper> resourceMappers =
							getResourceMappers();
					resourceMappers.put(serviceName, mapper);
					putTemplateIndex(serviceName);
					revisions.put(serviceName, newRev);
					updateds.put(serviceName, newUpdated);
					ret = true;
					if (currentRev != null && logger.isDebugEnabled()) {
						StringBuilder sb = new StringBuilder();
						sb.append(LogUtil.getRequestInfoStr(requestInfo));
						sb.append("ResourceMapper has changed. service=");
						sb.append(serviceName);
						sb.append(", rev:");
						sb.append(currentRev);
						sb.append(" -> ");
						sb.append(newRev);
						sb.append(", updated:");
						sb.append(currentUpdated);
						sb.append(" -> ");
						sb.append(newUpdated);
						logger.debug(sb.toString());
					}

				} else {
					logger.warn(LogUtil.getRequestInfoStr(requestInfo) + "mapper is not created.");
				}
			}
		}
		return ret;
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
		ConcurrentMap<String, FeedTemplateMapper> resourceMappers =
				getResourceMappers();
		if (resourceMappers != null && resourceMappers.containsKey(serviceName)) {
			resourceMappers.remove(serviceName);
		}
		Map<String, Integer> revisions = getRevisions();
		if (revisions != null && revisions.containsKey(serviceName)) {
			revisions.remove(serviceName);
		}
		Map<String, String> updateds = getUpdateds();
		if (updateds != null && updateds.containsKey(serviceName)) {
			updateds.remove(serviceName);
		}
		Map<String, Pattern> templateIndexMap =
				new ConcurrentHashMap<String, Pattern>();
		if (templateIndexMap != null && templateIndexMap.containsKey(serviceName)) {
			templateIndexMap.remove(serviceName);
		}
		Map<String, Pattern> templateFullTextIndexMap =
				new ConcurrentHashMap<String, Pattern>();
		if (templateFullTextIndexMap != null && templateFullTextIndexMap.containsKey(serviceName)) {
			templateFullTextIndexMap.remove(serviceName);
		}
	}

	/**
	 * サービス初期設定に必要なURIリスト.
	 * サービス初期設定に必要なシステム管理サービスのURIリストを返却する。
	 * @param serviceName サービス名
	 * @return URIリスト
	 */
	public List<String> getSettingEntryUrisBySystem(String serviceName) {
		return null;
	}

	/**
	 * サービス初期設定に必要なFeed検索用URIリスト.
	 * サービス初期設定に必要なシステム管理サービスのFeed検索用URIリストを返却する。
	 * @param serviceName サービス名
	 * @return URIリスト
	 */
	public List<String> getSettingFeedUrisBySystem(String serviceName) {
		return null;
	}

	/**
	 * サービス初期設定に共通して必要なURIリスト.
	 * サービス初期設定に共通して必要なシステム管理サービスのURIリストを返却する。
	 * @return URIリスト
	 */
	public List<String> getSettingEntryUrisBySystem() {
		List<String> uris = new ArrayList<>();
		uris.add(Constants.URI_SETTINGS_TEMPLATE);
		return uris;
	}

	/**
	 * サービス初期設定に共通して必要なURIリスト.
	 * サービス初期設定に共通して必要なシステム管理サービスのFeed検索用URIリストを返却する。
	 * @return URIリスト
	 */
	public List<String> getSettingFeedUrisBySystem() {
		return null;
	}

	/**
	 * サービス初期設定に共通して必要なURIリスト.
	 * サービス初期設定に共通して必要な自サービスのURIリストを返却する。
	 * @return URIリスト
	 */
	public List<String> getSettingEntryUris() {
		List<String> uris = new ArrayList<>();
		uris.add(Constants.URI_SETTINGS_TEMPLATE);
		return uris;
	}

	/**
	 * サービス初期設定に共通して必要なURIリスト.
	 * サービス初期設定に共通して必要な自サービスのFeed検索用URIリストを返却する。
	 * @return URIリスト
	 */
	public List<String> getSettingFeedUris() {
		return null;
	}

}
