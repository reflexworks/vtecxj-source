package jp.reflexworks.taggingservice.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.api.Condition;
import jp.reflexworks.atom.mapper.FeedTemplateMapper.Meta;
import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * リクエストパラメータ情報
 */
public class RequestParamInfo implements RequestParam {

	/** ワイルドカードで全指定(/*) -> 通常のFeed検索 */
	private static final String SLASH_WILDCARD = "/" + WILDCARD;
	/** 演算子の文字列長 : OR */
	private static final int OR_LEN = Condition.OR.length();
	/** 演算子の文字列長 : OR開始 */
	private static final int OR_START_LEN = Condition.OR_START.length();
	/** 演算子の文字列長 : OR終了 */
	private static final int OR_END_LEN = Condition.OR_END.length();

	/** キー */
	private String uri;
	/** キー前方一致の場合true */
	private boolean isUrlForwardMatch;
	/** クエリ文字列 */
	private String queryString;

	/**
	 * オプション格納Map.
	 * パラメータ名が1文字か、"_"で始まる文字の場合、パラメータの名前と値をこのMapに格納します。
	 * 順不同。重複値は1個のみ。(")"など)
	 */
	private Map<String, String> options = new HashMap<String, String>();
	/**
	 * 検索条件
	 * 外側のListはOR条件、内側のListはAND条件
	 */
	private List<List<Condition>> conditions;
	/**
	 * ソート条件.
	 * インメモリソート条件のみ設定する。
	 * インデックス検索でソートできる条件であれば検索条件に設定する。
	 */
	private Condition memorySortCondition;
	/** レスポンスフォーマット 0:XML, 1:JSON, 2:MessagePack */
	private int format = ReflexServletConst.FORMAT_JSON;
	/** サービス名 (検索条件除外の判定に使用) */
	private String serviceName;
	/** 検索条件フォーマットエラーの場合の例外 */
	private IllegalParameterException paramException;
	/** 検索条件フォーマットエラーの場合の例外をスローしたかどうか */
	private boolean isThrownParamException;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * リクエスト内容を分解.
	 * URLデコードはReflexRequestクラスで実施する。
	 * @param req リクエスト
	 */
	public RequestParamInfo(ReflexRequest req) {
		// サービス名
		this.serviceName = req.getServiceName();
		// URI抽出
		String pathInfo = req.getPathInfo();
		extractUri(pathInfo);
		this.queryString = req.getQueryString();

		// オプション・検索条件抽出
		setConditions();

		// レスポンスフォーマットの設定
		if (options.containsKey(PARAM_MESSAGEPACK)) {
			format = ReflexServletConst.FORMAT_MESSAGEPACK;
		} else if (options.containsKey(PARAM_XML)) {
			format = ReflexServletConst.FORMAT_XML;
		}
	}

	/**
	 * コンストラクタ.
	 * 外部からTaggingServiceクラスのメソッドを呼ばれた場合に使用する。
	 * パラメータの値についてURLデコードを行う。(URIはURLエンコードされる文字が登録されないためデコードしない。)
	 * @param requestUri リクエストURL
	 * @param serviceName サービス名
	 */
	public RequestParamInfo(String requestUri, String serviceName) {
		if (requestUri == null) {
			return;
		}
		// サービス名
		this.serviceName = serviceName;

		// URI抽出
		String[] parts = null;
		int partIdx = requestUri.indexOf("?");
		if (partIdx == -1) {
			parts = new String[1];
			parts[0] = requestUri;
		} else {
			parts = new String[2];
			parts[0] = requestUri.substring(0, partIdx);
			int partIdx1 = partIdx + 1;
			if (requestUri.length() < partIdx1) {
				parts[1] = "";
			} else {
				parts[1] = requestUri.substring(partIdx1);
			}
			this.queryString = parts[1];	// QueryStringはエンコード状態のもの (リクエストも同じ)
		}

		String pathInfo = parts[0];
		// URI抽出
		extractUri(pathInfo);

		// QueryStringからオプションまたは検索条件を抽出
		setConditions();
	}

	/**
	 * Entry検索かどうか返却します.
	 * @return Entry検索の場合true
	 */
	public boolean isEntry() {
		return options.containsKey(PARAM_ENTRY);
	}

	/**
	 * Feed検索かどうか返却します.
	 * @return Feed検索の場合true
	 */
	public boolean isFeed() {
		return options.containsKey(PARAM_FEED);
	}

	/**
	 * レスポンスフォーマットを返却
	 * <p>
	 * <ul>
	 *   <li>1 : XML</li>
	 *   <li>2 : JSON</li>
	 *   <li>3 : MessagePack</li>
	 *   <li>4 : multipart/form-data</li>
	 *   <li>0 : Text</li>
	 * </ul>
	 * </p>
	 * @return レスポンスフォーマット
	 */
	public int getFormat() {
		return format;
	}

	/**
	 * PathInfoを取得.
	 * ただし末尾の"*"は除去されています。
	 * 末尾に"*"が設定されているかどうかは、isUrlFowardMatchメソッドで取得してください。
	 * @return PathInfo
	 */
	public String getUri() {
		return uri;
	}

	/**
	 * URL前方一致指定かどうかを取得.
	 * @return PathInfoの末尾に"*"が設定されている場合true
	 */
	public boolean isUrlForwardMatch() {
		return isUrlForwardMatch;
	}

	/**
	 * クエリ文字列を取得.
	 * @return クエリ文字列
	 */
	public String getQueryString() {
		return queryString;
	}

	/**
	 * 検索条件リストを取得.
	 * @return 検索条件リスト
	 */
	public List<List<Condition>> getConditionsList() {
		return conditions;
	}

	/**
	 * ソート条件を取得.
	 * @return インメモリソート条件
	 */
	public Condition getSort() {
		return memorySortCondition;
	}

	/**
	 * 指定されたオプションの値を取得.
	 * QueryStringに指定されたパラメータの値を返却します。
	 * @param name オプション名
	 * @return 値
	 */
	public String getOption(String name) {
		if (name == null) {
			return null;
		}
		return options.get(name);
	}

	/**
	 * オプションの値を指定.
	 * ReflexContext内で不要なオプションを削除したり、必要なオプションをセットしたりするのに使用。
	 * @param name オプション名
	 * @param val 値。nullの場合は値の削除。
	 */
	public void setOption(String name, String val) {
		if (name == null) {
			return;
		}
		if (val == null) {
			options.remove(name);
		} else {
			options.put(name,  val);
		}
	}

	/**
	 * 条件指定フォーマットエラーの場合、例外を返却する.
	 */
	public void throwExceptionIfInvalidParameter() {
		if (paramException != null && !isThrownParamException) {
			isThrownParamException = true;
			throw paramException;
		}
	}

	/**
	 * クエリパラメータから検索条件オブジェクトを抽出.
	 * QueryStringを使用。
	 * (getParameterNames等は指定順になっていないため)
	 */
	private void setConditions() {
		if (StringUtils.isBlank(queryString)) {
			return;
		}

		// QueryStringからオプションまたは検索条件を抽出
		List<String[]> paramList = new ArrayList<>();	// [0]key、[1]val
		String[] params = queryString.split("\\&");
		for (String pa : params) {
			String key = null;
			String val = null;
			String[] keyVal = pa.split("\\=");
			key = keyVal[0];	// queryStringはURLデコードされた状態
			if (keyVal.length > 1) {
				val = keyVal[1];
			} else {
				val = "";
			}

			if (!StringUtils.isBlank(key)) {
				// オプション
				this.options.put(key, val);
			}

			paramList.add(new String[] {key, val});
		}
		// 検索条件抽出
		try {
			setConditions(paramList);
		} catch (IllegalParameterException e) {
			// 例外は一旦変数に格納する。
			this.paramException = e;
		}
	}

	/**
	 * 指定された検索条件をオブジェクトに変換し、本オブジェクトのリストに格納する.
	 *   OR条件は"&|("と"&)"、または括弧なし"&|"で表現。
	 *   例) &|title-fm-aaa&|(title-fm-bbb&flg-eq-3&)&|(title-fm-bbb&flg-eq-4&)
	 *   ソート条件は抽出しない。(この後の処理で抽出する。)
	 * @param paramList リクエストのパラメータマップ。[0]キー,[1]値
	 * @return 検索条件リスト
	 *         外側のListはOR条件、内側のListはAND条件
	 */
	private void setConditions(List<String[]> paramList) {
		List<List<Condition>> conditions = new ArrayList<>();
		boolean isOrStart = false;
		// すべてのOR条件に含まれる条件
		List<Condition> conditionsAll = new ArrayList<>();
		// 現在処理中の条件
		List<Condition> conditionsBlock = new ArrayList<>();
		Condition tmpSortCondition = null;	// インメモリソート条件　ワーク項目
		List<Meta> metalist = TaggingEnvUtil.getResourceMapper(serviceName).getMetalist();
		for (String[] paramPart : paramList) {
			String key = paramPart[0];
			String value = paramPart[1];
			boolean isOr = false;

			// OR開始、終了チェック
			boolean isSymbol = false;
			if (key.startsWith(Condition.OR_START)) {
				// 二重括弧はエラー
				if (isOrStart) {
					throw new IllegalParameterException("Double parentheses can not be specified.");
				}
				isOrStart = true;
				isOr = true;
				key = key.substring(OR_START_LEN);
				// OR開始時に処理中条件が残っている場合、その条件はAND条件
				// 1つ前に指定された条件について、全てのOR条件に加える。
				if (!conditionsBlock.isEmpty()) {
					for (Condition tmpCondition : conditionsBlock) {
						// 既出のOR条件に加える
						for (List<Condition> conditionList : conditions) {
							conditionList.add(tmpCondition);
						}
						// この条件以降の条件に加える
						conditionsAll.add(tmpCondition);
					}
					conditionsBlock = new ArrayList<>();
				}
			} else if (key.startsWith(Condition.OR)) {
				// カッコ内のOR指定はエラー
				if (isOrStart) {
					throw new IllegalParameterException("You can not specify OR within parentheses.");
				}
				isOr = true;
				key = key.substring(OR_LEN);
				if (!conditionsBlock.isEmpty()) {
					for (Condition tmpCondition : conditionsBlock) {
						// 既出のOR条件に加える
						for (List<Condition> conditionList : conditions) {
							conditionList.add(tmpCondition);
						}
						// この条件以降の条件に加える
						conditionsAll.add(tmpCondition);
					}
					conditionsBlock = new ArrayList<>();
				}
			} else if (key.startsWith(Condition.OR_END)) {
				if (!isOrStart) {
					throw new IllegalParameterException("The end parenthese can not be specified.");
				}
				if (key.length() != OR_END_LEN) {
					throw new IllegalParameterException("The end parenthese format error.");
				}
				isOrStart = false;
				isSymbol = true;
				if (!conditionsBlock.isEmpty()) {
					// 先に条件指定されたAND条件を先頭に指定
					if (!conditionsAll.isEmpty()) {
						conditionsBlock.addAll(0, conditionsAll);
					}
					conditions.add(conditionsBlock);
				}
				conditionsBlock = new ArrayList<>();
			}
			if (!isSymbol) {
				Condition condition = createCondition(key, value, metalist);
				if (condition != null) {
					boolean isSortCondition = isSortCondition(condition);
					if (isSortCondition) {
						// ソート指定を2個以上行った場合はエラー
						if (tmpSortCondition != null) {
							throw new IllegalParameterException("Only one sort condition can be specified.");
						}
						tmpSortCondition = condition;
					}
					// 全文検索の場合、値のカンマ区切りでAND条件とする。
					List<Condition> andConditions = parseAndConditions(condition);
					if (isOr) {
						// OR条件内でソート指定はエラー
						if (isSortCondition) {
							throw new IllegalParameterException("Sort specification can not be performed with OR condition.");
						}

						// 今処理中の条件
						conditionsBlock.addAll(andConditions);
						// 単独のOR条件の場合
						if (!isOrStart) {
							if (!conditionsAll.isEmpty()) {
								conditionsBlock.addAll(0, conditionsAll);
							}
							conditions.add(conditionsBlock);
							conditionsBlock = new ArrayList<>();
						}
					} else if (isOrStart) {
						// OR条件でソート指定はエラー
						if (isSortCondition) {
							throw new IllegalParameterException("Sort specification can not be performed with OR condition.");
						}
						conditionsBlock.addAll(andConditions);
					} else {
						// 1つ前に指定された条件について、全てのOR条件に加える。
						if (!conditionsBlock.isEmpty()) {
							for (Condition tmpCondition : conditionsBlock) {
								for (List<Condition> conditionList : conditions) {
									conditionList.add(tmpCondition);
								}
								// この条件以降の条件に加えるもの
								conditionsAll.add(tmpCondition);
							}
							conditionsBlock = new ArrayList<>();
						}
						// 今回の条件
						conditionsBlock.addAll(andConditions);
					}
				}
			}
		}

		if (isOrStart) {
			// OR条件の閉じ括弧が無い場合は文法エラー
			throw new IllegalParameterException("The closing parenthesis of the OR condition is not specified.");
		}

		// 最後に残っているconditionsBlockはand条件
		if (!conditionsBlock.isEmpty()) {
			if (conditions.isEmpty()) {
				List<Condition> conditionList = new ArrayList<>();
				conditions.add(conditionList);
				if (!conditionsAll.isEmpty()) {
					conditionList.addAll(conditionsAll);
				}
			}
			for (Condition tmpCondition : conditionsBlock) {
				// この条件以前に指定された各OR条件の末尾に加える。
				for (List<Condition> conditionList : conditions) {
					conditionList.add(tmpCondition);
				}
			}
		}

		// ソート指定がインメモリソートか、インデックス検索かを判定する。
		// インメモリソートの場合、conditionsリストから外し、インメモリソート条件項目に設定する。
		if (tmpSortCondition != null) {
			int indexSortIdx = -1;
			List<Condition> conditionList0 = null;
			// インデックスソートの条件
			// ・URL前方一致検索ではない
			// ・OR検索ではない
			// ・昇順ソートである
			// ・インデックス指定されている
			// ・全文検索指定がない
			// ・ソートの指定前にインデックス項目の検索条件がない

			// URL前方一致検索ではなく、OR検索ではなく、昇順ソートであるか判定
			if (!isUrlForwardMatch && conditions.size() == 1 &&
					Condition.ASC.equals(tmpSortCondition.getEquations())) {
				// 全文検索指定されていないか判定
				boolean isFullTextSearch = false;
				conditionList0 = conditions.get(0);
				for (Condition tmpCondition : conditionList0) {
					if (Condition.FULL_TEXT_SEARCH.equals(tmpCondition.getEquations())) {
						isFullTextSearch = true;
						break;
					}
				}
				if (!isFullTextSearch) {
					// インデックス指定されているか判定
					Map<String, Pattern> templateIndexMap =
							TaggingEnvUtil.getTemplateIndexMap(serviceName);
					Pattern pattern = templateIndexMap.get(tmpSortCondition.getProp());
					if (pattern != null) {
						Matcher matcher = pattern.matcher(uri);
						if (matcher.matches()) {
							// ソートの指定前にインデックス項目の検索条件がないか判定
							//for (Condition tmpCondition : conditionList) {
							int size = conditionList0.size();
							for (int i = 0; i < size; i++) {
								Condition tmpCondition = conditionList0.get(i);
								if (tmpSortCondition.equals(tmpCondition)) {
									indexSortIdx = i;
									break;
								} else {
									// ソート指定前の条件指定
									// インデックス項目かどうかチェック
									String equation = tmpCondition.getEquations();
									if (equation.equals(Condition.EQUAL) ||
											equation.equals(Condition.GREATER_THAN) ||
											equation.equals(Condition.GREATER_THAN_OR_EQUAL) ||
											equation.equals(Condition.LESS_THAN) ||
											equation.equals(Condition.LESS_THAN_OR_EQUAL) ||
											equation.equals(Condition.FORWARD_MATCH)) {
										Pattern tmpPattern = templateIndexMap.get(tmpCondition.getProp());
										if (tmpPattern != null) {
											Matcher tmpMatcher = tmpPattern.matcher(uri);
											if (tmpMatcher.matches()) {
												// インデックス項目
												// ソート条件よりインデックス条件が先に指定されているため、
												// インデックスソートではない。
												break;
											}
										}
									}
								}
							}
						}
					}
				}
			}

			if (indexSortIdx > -1) {
				// インデックスソート
				// ソート条件を先頭に移動させる
				if (indexSortIdx > 0) {
					conditionList0.remove(indexSortIdx);
					conditionList0.add(0, tmpSortCondition);
				}
			} else {
				// インメモリソート
				this.memorySortCondition = tmpSortCondition;
				Deque<Integer> emptyConditionIdxDeque = new ArrayDeque<>();
				int conditionIdx = -1;
				// 検索条件からソート条件を除去する。
				for (List<Condition> conditionList : conditions) {
					conditionIdx++;
					int size = conditionList.size();
					for (int i = 0; i < size; i++) {
						Condition tmpCondition = conditionList.get(i);
						if (tmpSortCondition.equals(tmpCondition)) {
							conditionList.remove(i);
							break;
						}
					}
					if (conditionList.isEmpty()) {
						if (logger.isTraceEnabled()) {
							logger.debug("[setConditions] empty condition index=" + conditionIdx + " queryString: " + queryString);
						}
						emptyConditionIdxDeque.push(conditionIdx);
					}
				}
				// 空になった条件があれば削除
				Iterator<Integer> it = emptyConditionIdxDeque.iterator();
				while (it.hasNext()) {
					int idx = it.next();
					conditions.remove(idx);
				}
			}
		}

		if (!conditions.isEmpty()) {
			this.conditions = conditions;
		}
	}

	/**
	 * 指定された検索条件をオブジェクトに変換して取得.
	 * @param key 名前
	 * @param value 値
	 * @param metalist テンプレート情報
	 * @return 検索条件オブジェクト
	 */
	private Condition createCondition(String key, String value, List<Meta> metalist) {
		// s指定の場合は昇順ソート
		Condition sortCondition = convertSortCondition(key, value);
		if (sortCondition != null) {
			// テンプレートに登録されている項目であればソート条件とする。
			// (インデックス指定ではなく、項目が指定されていればOK)
			// もしくは"@key"(キーでソート)の場合
			if (isExistTemplateOrReservedWord(sortCondition, metalist)) {
				return sortCondition;
			} else {
				//return null;
				throw new IllegalParameterException("The sort fieldname is not specified in the template. " + sortCondition.toString());
			}
		}

		if (key.length() < 2) {
			// 1文字項目は予約語のため条件としない。
			return null;
		}

		if (key.startsWith(Constants.ADMIN_KEY_PREFIX)) {
			// 先頭に_がついたパラメータはTaggingService使用のため条件としない。
			return null;
		}

		// テンプレートに登録されていない項目、または予約語以外は条件としない。
		Condition condition = new Condition(key, value);

		// Conditionに追加
		if (condition.getProp() != null) {
			if (!isExistTemplateOrReservedWord(condition, metalist)) {
				// 条件の形式になっていない、項目がテンプレートに定義されていない
				if (!Condition.EQUAL.equals(condition.getEquations())) {
					throw new IllegalParameterException(
							"The fieldname is not specified in the template. " +
							condition.toString());
				}
				return null;
			}

			return condition;
		}
		return null;
	}

	/**
	 * URIを取り出してフィールドに設定する.
	 * @param pathInfo PathInfo
	 */
	private void extractUri(String pathInfo) {
		if (StringUtils.isBlank(pathInfo)) {
			return;
		}
		if (pathInfo.endsWith(WILDCARD)) {
			// 全指定(/*)は前方一致でないので、それ以外かどうか判定。
			if (!pathInfo.endsWith(SLASH_WILDCARD)) {
				this.isUrlForwardMatch = true;
			}
			pathInfo = pathInfo.substring(0, pathInfo.length() - 1);
		}
		this.uri = pathInfo;
		this.uri = TaggingEntryUtil.removeLastSlash(this.uri);
	}

	/**
	 * ソート指定であればCondition形式にして返却する.
	 * @param key パラメータのキー
	 * @param value パラメータの値
	 * @return ソート指定のCondition
	 */
	private Condition convertSortCondition(String key, String value) {
		if (PARAM_SORT.equals(key)) {
			return new Condition(value, Condition.ASC, "");
		}
		return null;
	}

	/**
	 * 指定された項目がテンプレートに登録されているかどうか
	 * @param condition 条件
	 * @param metalist テンプレート項目リスト
	 * @return 指定された項目がテンプレートに登録されている場合true
	 */
	private boolean isExistTemplateOrReservedWord(Condition condition, List<Meta> metalist) {
		String name = condition.getProp();
		if (StringUtils.isBlank(name)) {
			return false;
		}
		if (isSortByKey(condition)) {
			return true;
		}
		for (Meta meta : metalist) {
			if (name.equals(meta.name)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * ソート指定かどうかチェック.
	 * 判定がascかdescであればソート指定
	 * @param condition 条件
	 * @return ソート指定の場合true
	 */
	private boolean isSortCondition(Condition condition) {
		if (condition != null &&
				(Condition.ASC.equals(condition.getEquations()) ||
						Condition.DESC.equals(condition.getEquations()))) {
			return true;
		}
		return false;
	}

	/**
	 * キーソート指定かどうかチェック
	 * @param condition 条件
	 * @return キーソート指定の場合true
	 */
	private boolean isSortByKey(Condition condition) {
		if (isSortCondition(condition) && KEYSORT.equals(condition.getProp())) {
			return true;
		}
		return false;
	}

	/**
	 * 全文検索の場合、値のカンマ区切りでAND条件とする。
	 * この場合、条件を2個の条件に分割する。
	 * 対象でない条件はそのまま返却する。
	 * @param condition 条件
	 * @return 値のカンマ区切りのAND条件を分割した条件オブジェクト
	 */
	private List<Condition> parseAndConditions(Condition condition) {
		List<Condition> andConditions = new ArrayList<>();
		if (Condition.FULL_TEXT_SEARCH.equals(condition.getEquations())) {
			String val = condition.getValue();
			if (val.indexOf(Condition.FT_AND) > 0) {
				String[] valParts = val.split(Condition.FT_AND);
				for (String valPart : valParts) {
					Condition andCondition = new Condition(condition.getProp(),
							condition.getEquations(), valPart);
					andConditions.add(andCondition);
				}
			} else {
				andConditions.add(condition);
			}

		} else {
			andConditions.add(condition);
		}
		return andConditions;
	}

	/**
	 * 文字列表現を取得.
	 * @param このオブジェクトの文字列表現
	 */
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(uri);
		if (isUrlForwardMatch) {
			sb.append(RequestParam.WILDCARD);
		}
		if (!StringUtils.isBlank(queryString)) {
			sb.append("?");
			sb.append(queryString);
		}
		return sb.toString();
	}

}
