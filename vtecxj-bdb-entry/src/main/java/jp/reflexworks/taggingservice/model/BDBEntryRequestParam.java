package jp.reflexworks.taggingservice.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jp.reflexworks.atom.api.AtomConst;
import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.taggingservice.api.RequestType;
import jp.reflexworks.taggingservice.servlet.BDBEntryRequest;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * リクエストパラメータ情報
 */
public class BDBEntryRequestParam implements RequestType {

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
	/** レスポンスフォーマット 0:XML, 1:JSON, 2:MessagePack */
	private int format = ReflexServletConst.FORMAT_MESSAGEPACK;	// デフォルトはMessagePack

	/**
	 * リクエスト内容を分解.
	 * URLデコードはReflexRequestクラスで実施する。
	 * @param req リクエスト
	 */
	public BDBEntryRequestParam(BDBEntryRequest req) {
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
				key = keyVal[0];	// queryStringはURLDecodeされているので再度行うことはしない。
				if (keyVal.length > 1) {
					val = keyVal[1];	// queryStringはURLDecodeされているので再度行うことはしない。
				} else {
					val = "";
				}

				if (!StringUtils.isBlank(key)) {
					// オプション
					this.options.put(key, val);
				}

				paramList.add(new String[] {key, val});
			}
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
