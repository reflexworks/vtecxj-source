package jp.reflexworks.taggingservice.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.reflexworks.atom.api.Condition;
import jp.reflexworks.atom.api.EntryUtil;
import jp.reflexworks.atom.mapper.FeedTemplateConst;
import jp.reflexworks.atom.mapper.FeedTemplateMapper.Meta;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.model.EditedCondition;
import jp.sourceforge.reflex.util.DateUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * Indexユーティリティ.
 */
public class IndexUtil {

	/** Date型インデックス項目のフォーマット */
	public static final String INDEX_DATE_FORMAT = "yyyyMMddHHmmssSSS";

	/**
	 * フィード検索の条件指定を、各テーブルに合わせた形に変換する。
	 * @param parentUri 親階層またはURI前方一致条件
	 * @param isUriForwardMatch URI前方一致検索の場合true
	 * @param conditions 検索条件
	 * @param metalist Metalist
	 * @param templateIndexMap インデックス情報 キー:項目、値:URLパターン
	 * @param templateFullTextIndexMap 全文検索インデックス情報 キー:項目、値:URLパターン
	 * @param templateDistkeyMap DISTKEY情報 キー:項目、値:URLパターン
	 * @return BDB用の検索条件
	 */
	public static EditedCondition editCondition(String parentUri,
			boolean isUriForwardMatch, List<Condition> conditions,
			List<Meta> metalist, Map<String, Pattern> templateIndexMap,
			Map<String, Pattern> templateFullTextIndexMap,
			Map<String, Pattern> templateDistkeyMap) {

		int conditionSize = 0;
		if (conditions != null) {
			conditionSize = conditions.size();
		}
		parentUri = TaggingEntryUtil.removeLastSlash(parentUri);

		Condition indexCondition = null;
		Condition indexConditionRange = null;
		Meta indexMeta = null;
		Condition[] innerConditions = null;
		List<Condition> tmpFtConditions = new ArrayList<Condition>();
		Condition ftCondition = null;
		String distkeyItem = null;
		String distkeyValue = null;

		// 指定されたURIのDISTKEY項目定義
		List<String> distkeyItemsByUri = IndexUtil.getDistkeyItems(parentUri, templateDistkeyMap);

		// 最初に全文検索条件があるかどうかチェック
		if (conditions != null) {
			for (Condition condition : conditions) {
				if (Condition.FULL_TEXT_SEARCH.equals(condition.getEquations())) {
					tmpFtConditions.add(condition);
				}
			}
		}

		if (isUriForwardMatch) {
			// URI前方一致検索
			// 全文検索条件があればエラー
			if (!tmpFtConditions.isEmpty()) {
				throw new IllegalParameterException("Key forward match and full text search can not be specified at the same time.");
			}
			// ソート指定はエラー
			if (conditions != null) {
				for (Condition condition : conditions) {
					if (Condition.ASC.equals(condition.getEquations())) {
						throw new IllegalParameterException("You can not specify sorting in the case of URL forward search.");
					}
				}
			}

			// 検索条件は全てインメモリ検索条件となる。
			if (conditionSize > 0) {
				innerConditions = conditions.toArray(new Condition[0]);
			}
		} else if (!tmpFtConditions.isEmpty()) {
			// 全文検索
			// 全文検索指定されていない項目・URIであればエラー
			for (Condition tmpFtCondition : tmpFtConditions) {
				String prop = tmpFtCondition.getProp();
				Pattern pattern = templateFullTextIndexMap.get(prop);
				if (pattern == null) {
					throw new IllegalParameterException("The item is not specified for full text search. " + prop);
				}
				Matcher matcher = pattern.matcher(parentUri);
				if (!matcher.matches()) {
					throw new IllegalParameterException("The key is not specified for full text search. item=" + prop + ", key=" + parentUri);
				}
			}
			// ソート指定はエラー
			if (conditions != null) {
				for (Condition condition : conditions) {
					if (Condition.ASC.equals(condition.getEquations())) {
						throw new IllegalParameterException("You can not specify sorting in the case of full text search.");
					}
				}
			}

			// DISTKEYがあれば抽出する。
			// その他の検索条件は全てインメモリ検索条件となる。
			List<Condition> innerConditionList = new ArrayList<>();
			for (Condition condition : conditions) {
				if (!tmpFtConditions.contains(condition)) {
					// DISTKEYチェック
					if (StringUtils.isBlank(distkeyItem) &&
							//isDistkeyCondition(parentUri, condition, templateDistkeyMap)) {
							distkeyItemsByUri.contains(condition.getProp())) {
						distkeyItem = condition.getProp();
						//distkeyValue = condition.getValue();
						distkeyValue = editIndexValue(distkeyItem, condition.getValue(), metalist);
					} else {
						innerConditionList.add(condition);
					}
				} else {
					if (ftCondition == null) {
						// 先頭の全文検索条件は全文検索サーバで検索
						ftCondition = condition;
					} else {
						// 2番目以降の全文検索条件は正規表現検索に変換
						Condition rgFtCondition = convertFtCondition(condition);
						innerConditionList.add(rgFtCondition);
					}
				}
			}
			// URIがDISTKEY指定である場合、DISTKEYが条件に設定されていなければエラー
			if (distkeyItemsByUri != null && !distkeyItemsByUri.isEmpty() &&
					StringUtils.isBlank(distkeyItem)) {
				throw new IllegalParameterException("Distkey is required in the case of the key and full text search.");
			}

			if (!innerConditionList.isEmpty()) {
				innerConditions = innerConditionList.toArray(new Condition[0]);
			}

		} else if (conditionSize > 0) {
			// 先頭の検索条件がDISTKEY指定かどうかチェック
			int i = 0;
			Condition firstCondition = conditions.get(i);
			i++;
			//if (isDistkeyCondition(parentUri, firstCondition, templateDistkeyMap)) {
			if (distkeyItemsByUri != null && distkeyItemsByUri.contains(firstCondition.getProp())) {
				distkeyItem = firstCondition.getProp();
				//distkeyValue = firstCondition.getValue();
				distkeyValue = editIndexValue(distkeyItem, firstCondition.getValue(), metalist);

				if (conditionSize > 1) {
					firstCondition = conditions.get(i);
					i++;
				} else {
					firstCondition = null;
				}
			}

			// 先頭の検索条件がインデックスに合致するか確認
			if (firstCondition != null) {
				String firstProp = firstCondition.getProp();
				String firstOperation = firstCondition.getEquations();
				boolean useIndex = false;
				if (templateIndexMap != null && templateIndexMap.containsKey(firstProp)) {
					// DISTKEY対象URIであり、DISTKEYの指定がない場合はインデックスが作成されていないのでインメモリ検索
					if (distkeyItemsByUri != null && !distkeyItemsByUri.isEmpty() &&
							StringUtils.isBlank(distkeyItem)) {
						// インデックスではない。インメモリ検索。
					} else {
						// 以下の演算子のみインデックス対象
						if (firstOperation.equals(Condition.EQUAL) ||
								firstOperation.equals(Condition.GREATER_THAN) ||
								firstOperation.equals(Condition.GREATER_THAN_OR_EQUAL) ||
								firstOperation.equals(Condition.LESS_THAN) ||
								firstOperation.equals(Condition.LESS_THAN_OR_EQUAL) ||
								firstOperation.equals(Condition.FORWARD_MATCH) ||
								firstOperation.equals(Condition.ASC)) {
							// 親階層がインデックスパターンに合致するかチェック
							Matcher matcher = templateIndexMap.get(firstProp).matcher(parentUri);
							useIndex = matcher.matches();
						}
					}
				}
				if (useIndex) {
					List<Condition> subConditions = conditions.subList(i, conditionSize);
					// 2番目以降の条件にソートが指定されていればエラー
					for (Condition condition : subConditions) {
						if (Condition.ASC.equals(condition.getEquations())) {
							throw new IllegalParameterException("You can not specify sorting for the second and subsequent conditions.");
						}
					}

					if (firstOperation.equals(Condition.ASC)) {
						// ソート項目の追加条件があるかどうかチェックする
						List<Condition> tmpSubConditions = new ArrayList<Condition>();
						int j = 0;
						for (Condition subCondition : subConditions) {
							if (firstProp.equals(subCondition.getProp()) &&
									(subCondition.getEquations().equals(Condition.GREATER_THAN) ||
									 subCondition.getEquations().equals(Condition.GREATER_THAN_OR_EQUAL) ||
									 subCondition.getEquations().equals(Condition.LESS_THAN) ||
									 subCondition.getEquations().equals(Condition.LESS_THAN_OR_EQUAL) ||
									 subCondition.getEquations().equals(Condition.FORWARD_MATCH) ||
									 subCondition.getEquations().equals(Condition.EQUAL))) {
								// インデックス条件を書き換える
								firstCondition = subCondition;
								firstOperation = firstCondition.getEquations();

								// 条件リストを書き換える
								tmpSubConditions.addAll(subConditions.subList(j + 1,
										subConditions.size()));
								subConditions = tmpSubConditions;
								break;
							}
							tmpSubConditions.add(subCondition);
							j++;
						}
					}
					indexCondition = firstCondition;
					indexMeta = TaggingEntryUtil.getMeta(metalist, firstProp);
					List<Condition> innerConditionList = new ArrayList<Condition>();
					// インデックス検索の追加条件があるかどうかチェックする
					if (firstOperation.equals(Condition.GREATER_THAN) ||
							firstOperation.equals(Condition.GREATER_THAN_OR_EQUAL)) {
						for (Condition subCondition : subConditions) {
							if (firstProp.equals(subCondition.getProp()) &&
									(subCondition.getEquations().equals(Condition.LESS_THAN) ||
									 subCondition.getEquations().equals(Condition.LESS_THAN_OR_EQUAL))) {
								// インデックス検索の追加条件
								indexConditionRange = subCondition;
							} else {
								// インメモリ検索
								innerConditionList.add(subCondition);
							}
						}

					} else if (firstOperation.equals(Condition.LESS_THAN) ||
							firstOperation.equals(Condition.LESS_THAN_OR_EQUAL)) {
						for (Condition subCondition : subConditions) {
							if (firstProp.equals(subCondition.getProp()) &&
									(subCondition.getEquations().equals(Condition.GREATER_THAN) ||
									 subCondition.getEquations().equals(Condition.GREATER_THAN_OR_EQUAL))) {
								// インデックス検索の追加条件
								indexConditionRange = subCondition;
							} else {
								// インメモリ検索
								innerConditionList.add(subCondition);
							}
						}

					} else {
						// 残りの条件は全てインメモリ検索
						innerConditionList.addAll(subConditions);
					}

					// DISTKEYチェック
					List<Condition> tmpInnerConditionList = new ArrayList<>();
					for (Condition condition : innerConditionList) {
						if (StringUtils.isBlank(distkeyItem) &&
								isDistkeyCondition(parentUri, condition, templateDistkeyMap)) {
							distkeyItem = condition.getProp();
							//distkeyValue = condition.getValue();
							distkeyValue = editIndexValue(distkeyItem, condition.getValue(), metalist);
						} else {
							tmpInnerConditionList.add(condition);
						}
					}
					innerConditions = tmpInnerConditionList.toArray(new Condition[0]);

				} else {
					// ソートが指定されていればエラー
					for (Condition condition : conditions) {
						if (Condition.ASC.equals(condition.getEquations())) {
							throw new IllegalParameterException("You can not specify sorting for the item that is not indexed. " + condition.getProp());
						}
					}

					// 検索条件は全てインメモリ検索
					//innerConditions = conditions.toArray(new Condition[0]);
					// DISTKEYチェック
					List<Condition> tmpInnerConditionList = new ArrayList<>();
					for (Condition condition : conditions) {
						if (StringUtils.isBlank(distkeyItem) &&
								isDistkeyCondition(parentUri, condition, templateDistkeyMap)) {
							distkeyItem = condition.getProp();
							//distkeyValue = condition.getValue();
							distkeyValue = editIndexValue(distkeyItem, condition.getValue(), metalist);
						} else {
							tmpInnerConditionList.add(condition);
						}
					}
					innerConditions = tmpInnerConditionList.toArray(new Condition[0]);
				}
			}
		}

		return new EditedCondition(parentUri, isUriForwardMatch, indexCondition,
				indexConditionRange, indexMeta, innerConditions, ftCondition,
				distkeyItem, distkeyValue);
	}

	/**
	 * 2番目以降の全文検索条件を正規表現検索に変換
	 * @param ftCondition 全文検索条件
	 * @return 全文検索条件を正規表現検索に変換したもの
	 */
	private static Condition convertFtCondition(Condition ftCondition) {
		String prop = ftCondition.getProp();
		String val = escapeRegex(ftCondition.getValue());
		return new Condition(prop, Condition.REGEX, val);
	}

	/**
	 * 正規表現の予約文字をエスケープ
	 * @param str 変換元文字列
	 * @return 編集した文字列
	 */
	private static String escapeRegex(String str) {
		// 最初はエスケープ文字をエスケープ
		String tmpStr = escapeProc(str, "\\");
		// 以降は、正規表現の予約文字をエスケープ
		tmpStr = escapeProc(str, "*");
		tmpStr = escapeProc(str, "+");
		tmpStr = escapeProc(str, ".");
		tmpStr = escapeProc(str, "?");
		tmpStr = escapeProc(str, "{");
		tmpStr = escapeProc(str, "}");
		tmpStr = escapeProc(str, "(");
		tmpStr = escapeProc(str, ")");
		tmpStr = escapeProc(str, "[");
		tmpStr = escapeProc(str, "]");
		tmpStr = escapeProc(str, "^");
		tmpStr = escapeProc(str, "$");
		tmpStr = escapeProc(str, "-");
		tmpStr = escapeProc(str, "|");
		tmpStr = escapeProc(str, "/");

		return ".*" + tmpStr + ".*";
	}

	/**
	 * 指定された文字をエスケープ
	 * @param text 変換元文字列
	 * @param escapeStr エスケープ文字列
	 * @return 編集した文字列
	 */
	private static String escapeProc(String text, String escapeStr) {
		return text.replace(escapeStr, "\\" + escapeStr);
	}

	/**
	 * 条件がDISTKEYに当たるかどうか
	 * @param parentUri 親キー
	 * @param condition 条件
	 * @param templateDistkeyMap テンプレートのDISTKEY指定パターン
	 * @return 条件がDISTKEYに当たる場合true
	 */
	public static boolean isDistkeyCondition(String parentUri, Condition condition,
			Map<String, Pattern> templateDistkeyMap) {
		// DISTKEYはequal指定のみ
		if (!Condition.EQUAL.equals(condition.getEquations())) {
			return false;
		}
		// テンプレートに条件項目がDISTKEY指定されていること
		String item = condition.getProp();
		if (templateDistkeyMap == null || !templateDistkeyMap.containsKey(item)) {
			return false;
		}
		// テンプレートのDISTKEY指定のキーパターンが一致すること
		Pattern pattern = templateDistkeyMap.get(item);
		Matcher matcher = pattern.matcher(parentUri);
		return matcher.matches();
	}

	/**
	 * 指定されたキーのDISTKEY項目を取得.
	 * @param parentUri 親キー
	 * @param templateDistkeyMap テンプレートのDISTKEY指定パターン
	 * @return キーがDISTKEYに当たる場合true
	 */
	public static List<String> getDistkeyItems(String parentUri, Map<String, Pattern> templateDistkeyMap) {
		List<String> items = new ArrayList<>();
		for (Map.Entry<String, Pattern> mapEntry : templateDistkeyMap.entrySet()) {
			Pattern pattern = mapEntry.getValue();
			Matcher matcher = pattern.matcher(parentUri);
			if (matcher.matches()) {
				items.add(mapEntry.getKey());
			}
		}
		return items;
	}

	/**
	 * インデックスの値編集
	 * @param name 項目名
	 * @param valStr 値
	 * @param metalist 型情報リスト
	 * @return 編集した文字列
	 */
	private static String editIndexValue(String name, String valStr, List<Meta> metalist) {
		Meta meta = getMeta(metalist, name);
		Object obj = convertIndexValueByType(valStr, meta);
		return editIndexValue(obj);
	}

	/**
	 * インデックスの値編集
	 * @param obj 値
	 * @return 編集した文字列
	 */
	public static String editIndexValue(Object obj) {
		if (obj == null) {
			return "";
		}
		if (obj instanceof Long ||
				obj instanceof Integer ||
				obj instanceof Short ||
				obj instanceof Float ||
				obj instanceof Double) {
			return EntryUtil.editNumberIndexValue((Number)obj);
		} else if (obj instanceof Date) {
			SimpleDateFormat format = new SimpleDateFormat(INDEX_DATE_FORMAT);
			return format.format((Date)obj);
		} else if (obj instanceof Boolean) {
			return convertIndexValueByBoolean(obj.toString());
		} else {
			return obj.toString();
		}
	}

	/**
	 * 文字列で指定された値を、テンプレートの型に変換する。
	 * @param condition 条件
	 * @param meta 条件項目の型情報
	 * @return 型を変換した値
	 */
	public static Object convertIndexValueByType(Condition condition, Meta meta) {
		if (condition != null && !StringUtils.isBlank(condition.getValue()) && meta != null) {
			String valStr = condition.getValue();
			return convertIndexValueByType(valStr, meta);
			/*
			try {
				String valStr = condition.getValue();
				if (FeedTemplateConst.META_TYPE_LONG.equals(meta.type)) {
					return Long.parseLong(valStr);
				} else if (FeedTemplateConst.META_TYPE_INTEGER.equals(meta.type)) {
					return Integer.parseInt(valStr);
				} else if (FeedTemplateConst.META_TYPE_DOUBLE.equals(meta.type)) {
					return Double.parseDouble(valStr);
				} else if (FeedTemplateConst.META_TYPE_FLOAT.equals(meta.type)) {
					return Float.parseFloat(valStr);
				} else if (FeedTemplateConst.META_TYPE_DATE.equals(meta.type)) {
					try {
						return DateUtil.getDate(valStr);
					} catch (ParseException e) {
						throw new IllegalParameterException(e.getMessage());
					}
				} else if (FeedTemplateConst.META_TYPE_BOOLEAN.equals(meta.type)) {
					return convertIndexValueByBoolean(valStr);
				} else {
					return valStr;
				}
			} catch (NumberFormatException e) {
				throw new IllegalParameterException("Number format is invalid. " + e.getMessage(), e);
			}
			*/
		}
		return null;
	}

	/**
	 * 文字列で指定された値を、テンプレートの型に変換する。
	 * @param valStr 条件値文字列
	 * @param meta 条件項目の型情報
	 * @return 型を変換した値
	 */
	public static Object convertIndexValueByType(String valStr, Meta meta) {
		//if (condition != null && !StringUtils.isBlank(condition.getValue()) && meta != null) {
		if (!StringUtils.isBlank(valStr)) {
			try {
				//String valStr = condition.getValue();
				if (FeedTemplateConst.META_TYPE_LONG.equals(meta.type)) {
					return Long.parseLong(valStr);
				} else if (FeedTemplateConst.META_TYPE_INTEGER.equals(meta.type)) {
					return Integer.parseInt(valStr);
				} else if (FeedTemplateConst.META_TYPE_DOUBLE.equals(meta.type)) {
					return Double.parseDouble(valStr);
				} else if (FeedTemplateConst.META_TYPE_FLOAT.equals(meta.type)) {
					return Float.parseFloat(valStr);
				} else if (FeedTemplateConst.META_TYPE_DATE.equals(meta.type)) {
					try {
						return DateUtil.getDate(valStr);
					} catch (ParseException e) {
						throw new IllegalParameterException(e.getMessage());
					}
				} else if (FeedTemplateConst.META_TYPE_BOOLEAN.equals(meta.type)) {
					return convertIndexValueByBoolean(valStr);
				} else {
					return valStr;
				}
			} catch (NumberFormatException e) {
				throw new IllegalParameterException("Number format is invalid. " + e.getMessage(), e);
			}
		}
		return null;
	}

	/**
	 * boolean値をインデックス値に変換する.
	 * @param bVal true or false
	 * @return trueの場合0、falseの場合1
	 */
	private static String convertIndexValueByBoolean(String bVal) {
		if ("true".equals(bVal)) {
			return "0";
		} else {
			return "1";
		}
	}

	/**
	 * インデックスの終了条件を取得
	 * @param str インデックスの開始条件
	 * @return インデックスの終了条件
	 */
	public static String getEndKeyStr(String str) {
		return StringUtils.null2blank(str) + Constants.END_STRING;
	}

	/**
	 * metalistから指定された項目のmeta情報を取得.
	 * @param metalist metalist
	 * @param name 項目名
	 * @return 項目のmeta情報
	 */
	private static Meta getMeta(List<Meta> metalist, String name) {
		for (Meta meta : metalist) {
			if (name.equals(meta.name)) {
				return meta;
			}
		}
		return null;
	}

}
