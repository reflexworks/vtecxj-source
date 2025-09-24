package jp.reflexworks.taggingservice.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jp.reflexworks.atom.api.AtomConst;
import jp.reflexworks.atom.api.Condition;
import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.taggingservice.api.RequestType;
import jp.reflexworks.taggingservice.servlet.InnerIndexRequest;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * リクエストパラメータ情報
 */
public class InnerIndexRequestParam implements RequestType {

	/** Encoding */
	public static final String ENCODING = AtomConst.ENCODING;

	/** キー */
	private String uri;
	/** クエリ文字列 */
	private String queryString;

	/**
	 * オプション格納Map.
	 * パラメータ名が1文字か、"_"で始まる文字の場合、パラメータの名前と値をこのMapに格納します。
	 */
	private Map<String, String> options = new HashMap<String, String>();
	/** 検索条件 */
	private Condition condition;
	/** 検索条件範囲 */
	private Condition conditionRange;
	/** レスポンスフォーマット 0:XML, 1:JSON, 2:MessagePack */
	private int format = ReflexServletConst.FORMAT_MESSAGEPACK;	// デフォルトはMessagePack

	/**
	 * リクエスト内容を分解.
	 * URLデコードはReflexRequestクラスで実施する。
	 * @param req リクエスト
	 */
	public InnerIndexRequestParam(InnerIndexRequest req) {
		// URI抽出
		String pathInfo = req.getPathInfo();
		extractUri(pathInfo);
		this.queryString = req.getQueryString();

		// 検索条件抽出
		setCondition();

		// レスポンスフォーマットの設定
		if (options.containsKey(PARAM_JSON)) {
			format = ReflexServletConst.FORMAT_JSON;
		} else if (options.containsKey(PARAM_XML)) {
			format = ReflexServletConst.FORMAT_XML;
		}
	}

	/**
	 * Entry検索かどうか返却します.
	 * @return Entry検索の場合true
	 */
	public boolean isEntry() {
		return options.containsKey(RequestType.PARAM_ENTRY);
	}

	/**
	 * Feed検索かどうか返却します.
	 * @return Feed検索の場合true
	 */
	public boolean isFeed() {
		return options.containsKey(RequestType.PARAM_FEED);
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
	 * クエリ文字列を取得.
	 * @return クエリ文字列
	 */
	public String getQueryString() {
		return queryString;
	}

	/**
	 * 検索条件を取得.
	 * @return 検索条件
	 */
	public Condition getCondition() {
		return condition;
	}

	/**
	 * 検索条件(範囲)を取得.
	 * @return 検索条件(範囲)
	 */
	public Condition getConditionRange() {
		return conditionRange;
	}

	/**
	 * 指定されたオプションの値を取得.
	 * QueryStringに指定された一文字のパラメータ、または"_"で始まるパラメータの値を返却します。
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
	 * オプションの値をtrue/falseで取得.
	 * オプションが値なしで指定されている、または値がtrueの場合trueを返却。
	 * それ以外はfalseで返却。
	 * @param name
	 * @return
	 */
	public boolean getOptionBoolean(String name) {
		if (name == null) {
			return false;
		}
		String val = options.get(name);
		// 指定なしはfalseを返却
		if (val == null) {
			return false;
		}
		// オプションが値なしで指定されている、または値がtrueの場合trueを返却
		if ("".equals(val) || "true".equalsIgnoreCase(val)) {
			return true;
		}
		return false;
	}

	/**
	 * クエリパラメータから検索条件オブジェクトを抽出.
	 * QueryStringを使用。
	 * (getParameterNames等は指定順になっていないため)
	 */
	private void setCondition() {
		// QueryStringからオプションまたは検索条件を抽出
		if (!StringUtils.isBlank(queryString)) {
			List<String[]> paramList = new ArrayList<>();	// [0]key、[1]val
			String[] params = queryString.split("\\&");
			for (String pa : params) {
				String key = null;
				String val = null;
				String[] keyVal = pa.split("\\=");
				key = keyVal[0];
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
			createAndSetCondition(paramList);
		}
	}

	/**
	 * 指定された検索条件をオブジェクトに変換して取得.
	 * @param paramList URLパラメータリスト([0]key、[1]val)
	 * @return 検索条件
	 */
	private void createAndSetCondition(List<String[]> paramList) {
		List<Condition> idxConditions = new ArrayList<>();
		for (String[] paramPart : paramList) {
			String key = paramPart[0];
			if (key.length() < 2) {
				// 1文字項目は予約語のため条件としない。
				continue;
			}
			if (key.startsWith(Constants.ADMIN_KEY_PREFIX)) {
				// 先頭に_がついたパラメータはTaggingService使用のため条件としない。
				continue;
			}
			Condition idxCondition = new Condition(key);
			if (idxCondition != null) {
				idxConditions.add(idxCondition);
			}
		}
		int size = idxConditions.size();
		if (size >= 1) {
			this.condition = idxConditions.get(0);
		}
		if (size >= 2) {
			this.conditionRange = idxConditions.get(1);
		}
	}

	/**
	 * URIを取り出してフィールドに設定する.
	 * @param pathInfo PathInfo
	 */
	private void extractUri(String pathInfo) {
		if (StringUtils.isBlank(pathInfo)) {
			return;
		}
		this.uri = TaggingEntryUtil.removeLastSlash(pathInfo);
	}

	/**
	 * 文字列表現を取得.
	 * @param このオブジェクトの文字列表現
	 */
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(uri);
		if (!StringUtils.isBlank(queryString)) {
			sb.append("?");
			sb.append(queryString);
		}
		return sb.toString();
	}

}
