package jp.reflexworks.taggingservice.index;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.api.Condition;
import jp.reflexworks.atom.entry.Category;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.mapper.FeedTemplateMapper.Meta;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.model.EditedCondition;
import jp.reflexworks.taggingservice.model.InnerIndex;
import jp.reflexworks.taggingservice.util.IndexUtil;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * Indexユーティリティ.
 */
public class BDBClientIndexUtil {

	/** 項目OR指定の区切り文字 */
	public static final String ITEM_OR = "|";
	/** 項目OR指定の区切り文字 (正規表現用) */
	public static final String ITEM_OR_RG = "\\" + ITEM_OR;

	/** インデックス定義なしエラー接頭辞 */
	public static final String MSG_NOT_DEFINED_PREFIX = "The item name is not defined. ";

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(BDBClientIndexUtil.class);

	/**
	 * Entryのうち、Index指定されている項目・URIについてインデックス情報を返却します.
	 * <p>
	 * 指定されたuriのみIndex情報を返却します。
	 * </p>
	 * @param uri ID URIまたはエイリアス
	 * @param sourceEntry entry
	 * @param serviceName サービス名
	 * @return Index リスト
	 */
	public static List<InnerIndex> getIndexesByUri(String uri,
			EntryBase sourceEntry, String serviceName) {
		Map<String, Pattern> templateIndexMap = TaggingEnvUtil.getTemplateIndexMap(
				serviceName);
		List<Meta> metalist = TaggingEnvUtil.getResourceMapper(serviceName).getMetalist();
		return getIndexesByUri(uri, sourceEntry, metalist, templateIndexMap);
	}

	/**
	 * Entryのうち、Index指定されている項目・URIについてインデックス情報を返却します.
	 * <p>
	 * 指定されたuriのみIndex情報を返却します。
	 * </p>
	 * @param uri ID URIまたはエイリアス
	 * @param sourceEntry entry
	 * @param metalist Metalist
	 * @param templateIndexMap テンプレートのインデックス設定と対象URIパターン
	 * @return Index リスト
	 */
	public static List<InnerIndex> getIndexesByUri(String uri, EntryBase sourceEntry,
			List<Meta> metalist, Map<String, Pattern> templateIndexMap) {
		List<InnerIndex> innerIndexes = new ArrayList<InnerIndex>();
		if (sourceEntry != null) {
			// ソフトスキーマIndex分を追加
			String id = sourceEntry.id;
			if (templateIndexMap != null) {
				for (Map.Entry<String, Pattern> mapEntry : templateIndexMap.entrySet()) {
					String name = mapEntry.getKey();
					Pattern pattern = mapEntry.getValue();
					String type = getType(metalist, name);
					Object obj = sourceEntry.getValue(name);
					// 値が設定されている場合のみIndexチェック・生成
					if (obj != null) {
						matchInnerIndex(innerIndexes, pattern, name, type, obj, id, uri);
					}
				}
			}
		}
		return innerIndexes;
	}

	/**
	 * Entryのうち、全文検索Index指定されている項目・URIについて全文検索インデックス情報を返却します.
	 * <p>
	 * 指定されたuriのみIndex情報を返却します。
	 * </p>
	 * @param uri ID URIまたはエイリアス
	 * @param sourceEntry entry
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @return 全文検索Index リスト
	 */
	public static List<InnerIndex> getFullTextIndexesByUri(String uri,
			EntryBase sourceEntry, String serviceName, RequestInfo requestInfo) {
		return getFullTextIndexesByUri(uri, sourceEntry, null, serviceName, requestInfo);
	}

	/**
	 * Entryのうち、全文検索Index指定されている項目・URIについて全文検索インデックス情報を返却します.
	 * <p>
	 * 指定されたuriのみIndex情報を返却します。
	 * </p>
	 * @param uri ID URIまたはエイリアス
	 * @param sourceEntry entry
	 * @param items インデックス抽出項目リスト (インデックス部分更新の場合に指定)
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @return 全文検索Index リスト
	 */
	public static List<InnerIndex> getFullTextIndexesByUri(String uri,
			EntryBase sourceEntry, List<String> items, String serviceName,
			RequestInfo requestInfo) {
		Map<String, Pattern> templateFullTextIndexMap =
				TaggingEnvUtil.getTemplateFullTextIndexMap(serviceName);
		Map<String, Pattern> paramTemplateFullTextIndexMap = null;

		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[getFullTextIndexesByUri] uri = ");
			sb.append(uri);
			sb.append(" , templateFullTextIndexMap = ");
			sb.append(templateFullTextIndexMap);
			sb.append(" , paramTemplateFullTextIndexMap = ");
			sb.append(paramTemplateFullTextIndexMap);
			logger.debug(sb.toString());
		}

		if (items != null && !items.isEmpty()) {
			paramTemplateFullTextIndexMap = new HashMap<>();
			for (String item : items) {
				Pattern pattern = templateFullTextIndexMap.get(item);
				if (pattern == null) {
					throw new IllegalParameterException(MSG_NOT_DEFINED_PREFIX + item);
				}
				paramTemplateFullTextIndexMap.put(item, pattern);
			}
		} else {
			paramTemplateFullTextIndexMap = templateFullTextIndexMap;
		}

		List<Meta> metalist = TaggingEnvUtil.getResourceMapper(serviceName).getMetalist();
		return getIndexesByUri(uri, sourceEntry, metalist, paramTemplateFullTextIndexMap,
				requestInfo);
	}

	/**
	 * Entryのうち、インデックス指定されている項目・URIについてインデックス情報を返却します.
	 * <p>
	 * 指定されたuriのみIndex情報を返却します。
	 * </p>
	 * @param uri ID URIまたはエイリアス
	 * @param sourceEntry entry
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @return インデックスリスト
	 */
	public static List<InnerIndex> getInnerIndexesByUri(String uri,
			EntryBase sourceEntry, String serviceName, RequestInfo requestInfo) {
		return getInnerIndexesByUri(uri, sourceEntry, null, serviceName, requestInfo);
	}

	/**
	 * Entryのうち、インデックス指定されている項目・URIについてインデックス情報を返却します.
	 * <p>
	 * 指定されたuriのみIndex情報を返却します。
	 * </p>
	 * @param uri ID URIまたはエイリアス
	 * @param sourceEntry entry
	 * @param items インデックス抽出項目リスト (インデックス部分更新の場合に指定)
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @return インデックスリスト
	 */
	public static List<InnerIndex> getInnerIndexesByUri(String uri, EntryBase sourceEntry,
			List<String> items, String serviceName, RequestInfo requestInfo) {
		Map<String, Pattern> templateInnerIndexMap =
				TaggingEnvUtil.getTemplateIndexMap(serviceName);
		Map<String, Pattern> paramTemplateInnerIndexMap = null;
		if (items != null && !items.isEmpty()) {
			paramTemplateInnerIndexMap = new HashMap<>();
			for (String item : items) {
				Pattern pattern = templateInnerIndexMap.get(item);
				if (pattern == null) {
					throw new IllegalParameterException(MSG_NOT_DEFINED_PREFIX + item);
				}
				paramTemplateInnerIndexMap.put(item, pattern);
			}
		} else {
			paramTemplateInnerIndexMap = templateInnerIndexMap;
		}

		List<Meta> metalist = TaggingEnvUtil.getResourceMapper(serviceName).getMetalist();
		return getIndexesByUri(uri, sourceEntry, metalist, paramTemplateInnerIndexMap, requestInfo);
	}

	/**
	 * Entryのうち、全文検索Index、またはIndex指定されている項目・URIについて全文検索インデックス情報を返却します.
	 * <p>
	 * 指定されたuriのみIndex情報を返却します。
	 * </p>
	 * @param uri ID URIまたはエイリアス
	 * @param sourceEntry entry
	 * @param metalist Metalist
	 * @param templateIndexMap テンプレートの全文検索インデックス、またはインデックス設定と対象URIパターン
	 * @param requestInfo リクエスト情報
	 * @return インデックスリスト
	 */
	public static List<InnerIndex> getIndexesByUri(String uri, EntryBase sourceEntry,
			List<Meta> metalist, Map<String, Pattern> templateIndexMap,
			RequestInfo requestInfo) {
		List<InnerIndex> innerIndexes = new ArrayList<>();
		if (sourceEntry != null) {
			// ソフトスキーマIndex分を追加
			String id = sourceEntry.id;
			if (templateIndexMap != null) {
				for (Map.Entry<String, Pattern> mapEntry : templateIndexMap.entrySet()) {
					// 全文検索項目OR指定
					String name = mapEntry.getKey();
					String[] tmpNames = null;
					if (name.indexOf(ITEM_OR) > 0) {
						tmpNames = name.split(ITEM_OR_RG);
					} else {
						tmpNames = new String[] {name};
					}
					String type = getType(metalist, tmpNames[0]);
					Pattern pattern = mapEntry.getValue();
					for (String tmpName : tmpNames) {
						Object obj = sourceEntry.getValue(tmpName);
						try {
							matchInnerIndex(innerIndexes, pattern, name, type, obj, id, uri);
						} catch (ClassCastException e) {
							logger.warn(LogUtil.getRequestInfoStr(requestInfo) + "ClassCastException", e);
						}
					}
				}
			}
		}
		return innerIndexes;
	}

	/**
	 * インデックス対象の情報を引数のリストに設定する.
	 * @param innerIndexes インデックスリスト
	 * @param pattern インデックス対象URIのパターン
	 * @param name 項目名
	 * @param type 型
	 * @param obj 値
	 * @param id ID
	 * @param uri URI
	 */
	private static void matchInnerIndex(List<InnerIndex> innerIndexes, Pattern pattern,
			String name, String type, Object obj, String id, String uri) {
		String parentUri = TaggingEntryUtil.removeLastSlash(
				TaggingEntryUtil.getParentUri(uri));
		Matcher matcher = pattern.matcher(parentUri);
		if (matcher.matches()) {
			if (obj instanceof Collection) {
				Collection collection = (Collection)obj;
				for (Object element : collection) {
					if (element != null) {
						InnerIndex innerIndex = createInnerIndex(
								id, uri, element, name, type);
						if (innerIndex != null) {
							innerIndexes.add(innerIndex);
						}
					}
				}
			} else {
				InnerIndex innerIndex = createInnerIndex(id, uri, obj,
						name, type);
				if (innerIndex != null) {
					innerIndexes.add(innerIndex);
				}
			}
		}
	}

	/**
	 * Index情報を生成
	 * @param id ID
	 * @param indexUri Index URI
	 * @param obj 値
	 * @param name 名前
	 * @param type 型
	 * @return Index情報
	 */
	private static InnerIndex createInnerIndex(String id, String indexUri, Object obj,
			String name, String type) {
		return new InnerIndex(indexUri, id, obj, name, type);
	}

	/**
	 * 項目の型を取得
	 * @param metalist Metalist
	 * @param name 項目名
	 * @return 項目の型
	 */
	public static String getType(List<Meta> metalist, String name) {
		if (StringUtils.isBlank(name) || metalist == null || metalist.isEmpty()) {
			return null;
		}
		for (Meta meta : metalist) {
			if (name.equals(meta.name)) {
				return meta.type;
			}
		}
		return null;
	}

	/**
	 * リクエストパラメータから指定件数を取得.
	 * @param limitStr lパラメータの値
	 * @return 指定件数
	 */
	public static int getLimit(String limitStr) {
		int defLimit = TaggingEnvUtil.getEntryNumberLimit();
		return StringUtils.intValue(limitStr, defLimit);
	}

	/**
	 * フィード検索の条件指定を、各テーブルに合わせた形に変換する。
	 * @param parentUri 親階層またはURI前方一致条件
	 * @param isUriForwardMatch URI前方一致検索の場合true
	 * @param conditions 検索条件
	 * @param serviceName サービス名
	 * @return BDB用の検索条件
	 */
	public static EditedCondition editCondition(String parentUri,
			boolean isUriForwardMatch, List<Condition> conditions,
			String serviceName) {
		List<Meta> metalist = TaggingEnvUtil.getResourceMapper(serviceName).getMetalist();
		Map<String, Pattern> templateIndexMap =
				TaggingEnvUtil.getTemplateIndexMap(serviceName);
		Map<String, Pattern> templateFullTextIndexMap =
				TaggingEnvUtil.getTemplateFullTextIndexMap(serviceName);
		Map<String, Pattern> templateDistkeyMap =
				TaggingEnvUtil.getTemplateDistkeyMap(serviceName);
		return IndexUtil.editCondition(parentUri, isUriForwardMatch, conditions, metalist,
				templateIndexMap, templateFullTextIndexMap, templateDistkeyMap);
	}

	/**
	 * 指定されたURIでインデックス指定があるかどうか判定
	 * @param uri URI
	 * @param serviceName サービス名
	 * @return 指定されたURIでインデックス指定がある場合true
	 */
	public static boolean haveInnerIndexes(String uri, String serviceName) {
		Map<String, Pattern> templateInnerIndexMap =
				TaggingEnvUtil.getTemplateIndexMap(serviceName);
		if (templateInnerIndexMap != null) {
			String parentUri = TaggingEntryUtil.removeLastSlash(
					TaggingEntryUtil.getParentUri(uri));
			for (Map.Entry<String, Pattern> mapEntry : templateInnerIndexMap.entrySet()) {
				Pattern pattern = mapEntry.getValue();
				Matcher matcher = pattern.matcher(parentUri);
				if (matcher.matches()) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Entryのうち、DISTKEY指定されている項目・URIについてDISTKEY情報を返却します.
	 * <p>
	 * 指定されたuriのみDISTKEY情報を返却します。
	 * </p>
	 * @param uri ID URIまたはエイリアス
	 * @param sourceEntry entry
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @return インデックスリスト
	 */
	public static List<Category> getDistkeysByUri(String uri, EntryBase sourceEntry,
			String serviceName, RequestInfo requestInfo) {
		return getDistkeysByUri(uri, sourceEntry, null, serviceName, requestInfo);
	}

	/**
	 * Entryのうち、DISTKEY指定されている項目・URIについてDISTKEY情報を返却します.
	 * <p>
	 * 指定されたuriのみDISTKEY情報を返却します。
	 * </p>
	 * @param uri ID URIまたはエイリアス
	 * @param sourceEntry entry
	 * @param items DISTKEY抽出項目リスト (インデックス部分更新の場合に指定)
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @return DISTKEYリスト
	 */
	public static List<Category> getDistkeysByUri(String uri, EntryBase sourceEntry,
			List<String> items, String serviceName, RequestInfo requestInfo) {
		Map<String, Pattern> templateDistkeyMap =
				TaggingEnvUtil.getTemplateDistkeyMap(serviceName);
		Map<String, Pattern> paramTemplateDistkeyMap = null;
		if (items != null && !items.isEmpty()) {
			paramTemplateDistkeyMap = new HashMap<>();
			for (String item : items) {
				Pattern pattern = templateDistkeyMap.get(item);
				if (pattern == null) {
					throw new IllegalParameterException(MSG_NOT_DEFINED_PREFIX + item);
				}
				paramTemplateDistkeyMap.put(item, pattern);
			}
		} else {
			paramTemplateDistkeyMap = templateDistkeyMap;
		}

		List<Meta> metalist = TaggingEnvUtil.getResourceMapper(serviceName).getMetalist();
		return getDistkeysByUri(uri, sourceEntry, metalist, paramTemplateDistkeyMap, requestInfo);
	}

	/**
	 * Entryのうち、DISTKEY指定されている項目・URIについてDISTKEY情報を返却します.
	 * <p>
	 * 指定されたuriのみDISTKEY情報を返却します。
	 * </p>
	 * @param uri ID URIまたはエイリアス
	 * @param sourceEntry entry
	 * @param metalist Metalist
	 * @param templateDistkeyMap テンプレートDISTKEY設定と対象URIパターン
	 * @param requestInfo リクエスト情報
	 * @return DISTKEYリスト
	 */
	public static List<Category> getDistkeysByUri(String uri, EntryBase sourceEntry,
			List<Meta> metalist, Map<String, Pattern> templateDistkeyMap,
			RequestInfo requestInfo) {
		List<Category> distkeys = new ArrayList<>();
		if (sourceEntry != null) {
			if (templateDistkeyMap != null) {
				for (Map.Entry<String, Pattern> mapEntry : templateDistkeyMap.entrySet()) {
					String name = mapEntry.getKey();
					String type = getType(metalist, name);
					Pattern pattern = mapEntry.getValue();
					Object obj = sourceEntry.getValue(name);
					try {
						matchDistkey(distkeys, pattern, name, type, obj, uri);
					} catch (ClassCastException e) {
						logger.warn(LogUtil.getRequestInfoStr(requestInfo) + "ClassCastException", e);
					}
				}
			}
		}
		return distkeys;
	}

	/**
	 * DISTKEY対象の情報を引数のリストに設定する.
	 * @param distkeys DISTKEYリスト
	 * @param pattern DISTKEY対象URIのパターン
	 * @param name 項目名
	 * @param type 型
	 * @param obj 値
	 * @param uri URI
	 */
	private static void matchDistkey(List<Category> distkeys, Pattern pattern,
			String name, String type, Object obj, String uri) {
		String parentUri = TaggingEntryUtil.removeLastSlash(
				TaggingEntryUtil.getParentUri(uri));
		Matcher matcher = pattern.matcher(parentUri);
		if (matcher.matches()) {
			if (obj instanceof Collection) {
				Collection collection = (Collection)obj;
				for (Object element : collection) {
					if (element != null) {
						Category distkey = createDistkeyInfo(name, element, type);
						if (distkey != null) {
							distkeys.add(distkey);
						}
					}
				}
			} else {
				Category distkey = createDistkeyInfo(name, obj, type);
				if (distkey != null) {
					distkeys.add(distkey);
				}
			}
		}
	}

	/**
	 * DISTKEY情報を生成
	 * @param name 名前
	 * @param obj 値
	 * @param type 型
	 * @return DISTKEY情報
	 */
	private static Category createDistkeyInfo(String name, Object obj, String type) {
		Category category = new Category();
		// category schemeにDISTKEY
		category._$scheme = name;
		// category labelにDISTKEYの値
		String text = null;
		if (obj == null) {
			text = "";
		} else {
			if (obj instanceof String) {
				text = (String)obj;
			} else {
				// インデックス用の文字列に変換する。
				text = IndexUtil.editIndexValue(obj);
			}
		}
		category._$label = text;

		return category;
	}

}
