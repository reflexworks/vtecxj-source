package jp.reflexworks.taggingservice.env;

import java.text.ParseException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.api.AtomConst;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.servlet.util.ServletContextUtil;
import jp.reflexworks.taggingservice.api.ReflexStatic;
import jp.reflexworks.taggingservice.exception.StaticDuplicatedException;

/**
 * ResourceMapper管理実装クラス.
 * 標準ATOM形式のみ扱う。
 */
public class AtomResourceMapperManager {

	/** メモリ上のstaticオブジェクト格納キー : 暗号化のsecret key */
	private static final String STATIC_NAME_MAPPER_SECRETKEY ="_mapper_secretkey";
	/** メモリ上のstaticオブジェクト格納キー : ResourceMapper保持Map */
	private static final String STATIC_NAME_MAPPER_RESOURCEMAPPERMAP ="_mapper_resorcemappermap";

	/** Atom standard */
	public static final String ATOM_STANDARD = ReflexEnvConst.ATOM_STANDARD;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 初期処理に呼ばれるメソッド.
	 * TaggingEnv.getPropはまだ使用できない。
	 * web.xmlかプロパティファイルに設定された値の取得は、TaggingEnv.getContextValueを使用する。
	 * @param contextUtil ServletContextUtil
	 * @param secretKey 暗号化キー
	 */
	public void init(ServletContextUtil contextUtil, String secretKey) {
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

		// 標準ATOMのResourceMapperを生成
		String[] template = AtomConst.TEMPLATE_DEFAULT_ARRAY;
		//String[] rights = getDefaultRights();
		String[] rights = null;
		FeedTemplateMapper atomMapper = createResourceMapper(template, rights,
				ReflexEnvConst.INDEX_LIMIT_DEFAULT);
		resourceMappers.put(ATOM_STANDARD, atomMapper);
	}

	/**
	 * 標準ATOM形式のResourceMapperを返却.
	 * @reutrn 標準ATOM形式のResourceMapper
	 */
	public FeedTemplateMapper getAtomResourceMapper() {
		return getResourceMapper(ATOM_STANDARD);
	}

	/**
	 * サービスのResourceMapperを返却.
	 * @param serviceName サービス名
	 * @reutrn ResourceMapper
	 */
	private FeedTemplateMapper getResourceMapper(String serviceName) {
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
	 * @param rights Index、暗号化、項目ACL情報
	 * @param indexLmit 最大インデックス数
	 */
	private FeedTemplateMapper createResourceMapper(String[] template, String[] rights,
			int indexLmit) {
		try {
			String secretKey = getSecretKey();
			return new FeedTemplateMapper(template, rights, indexLmit, secretKey);

		} catch (ParseException e) {
			throw new IllegalStateException(e);	// 通常エラーとならない
		}
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

}
