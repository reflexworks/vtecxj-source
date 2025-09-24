package jp.reflexworks.taggingservice.api;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Map;
import java.util.zip.DataFormatException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.servlet.util.UrlUtil;

/**
 * Taggingservice リクエストクラス.
 */
public abstract class ReflexRequest extends HttpServletRequestWrapper {

	/**
	 * コンストラクタ.
	 * @param httpReq リクエスト
	 */
	public ReflexRequest(HttpServletRequest httpReq)
	throws IOException {
		super(httpReq);
	}

	/**
	 * メソッドを取得.
	 * @return メソッド
	 */
	public String getMethod() {
		return super.getMethod();
	}

	/**
	 * PathInfoを取得.
	 * @return PathInfo
	 */
	public String getPathInfo() {
		// URLデコードされた状態で返却される。
		return super.getPathInfo();
	}

	/**
	 * QueryStringを取得
	 * @return QueryString
	 */
	public String getQueryString() {
		// URLデコードされないので、ここで変換する。
		return UrlUtil.urlDecode(super.getQueryString());
	}

	/**
	 * RequestURIを取得
	 * @return RequestURI
	 */
	public String getRequestURI() {
		return super.getRequestURI();
	}

	/**
	 * RequestURLを取得
	 * @return RequestURL
	 */
	public StringBuffer getRequestURL() {
		return super.getRequestURL();
	}

	/**
	 * URLパラメータを取得
	 * @param name URLパラメータ名
	 * @return URLパラメータ名に対応する値
	 */
	public String getParameter(String name) {
		return super.getParameter(name);
	}

	/**
	 * URLパラメータの一覧を取得
	 * @return URLパラメータ一覧
	 */
	public Map<String, String[]> getParameterMap() {
		return super.getParameterMap();
	}

	/**
	 * URLパラメータを取得.
	 * @param name URLパラメータ名
	 * @return URLパラメータ名に対応する値の一覧
	 */
	public String[] getParameterValues(String name) {
		return super.getParameterValues(name);
	}

	/**
	 * URLパラメータ名一覧を取得.
	 * @return URLパラメータ名一覧
	 */
	public Enumeration<String> getParameterNames() {
		return super.getParameterNames();
	}

	/**
	 * サービス名を取得.
	 * @return サービス名
	 */
	public abstract String getServiceName();

	/**
	 * リクエストデータをFeedオブジェクトに変換したものを取得.
	 * @return リクエストデータをFeedまたはEntryオブジェクトに変換したもの
	 */
	public abstract FeedBase getFeed()
	throws IOException, ClassNotFoundException, DataFormatException;

	/**
	 * リクエストデータをFeedオブジェクトに変換したものを取得.
	 * @param targetServiceName サービス名
	 * @return リクエストデータをFeedまたはEntryオブジェクトに変換したもの
	 */
	public abstract FeedBase getFeed(String targetServiceName)
	throws IOException, ClassNotFoundException, DataFormatException;

	/**
	 * リクエストデータのバイト配列を取得.
	 * @return リクエストデータのバイト配列
	 */
	public abstract byte[] getPayload() throws IOException;

	/**
	 * Taggingservice認証情報を取得.
	 * @return 認証情報
	 */
	public abstract ReflexAuthentication getAuth();

	/**
	 * リクエストパラメータ情報を取得.
	 * @return リクエストパラメータ情報
	 */
	public abstract RequestType getRequestType();

	/**
	 * PathInfo + QueryStringを取得.
	 * @return PathInfo + QueryString
	 */
	public abstract String getPathInfoQuery();

	/**
	 * ログ出力のためのリクエスト情報を取得.
	 * @return リクエスト情報
	 */
	public abstract RequestInfo getRequestInfo();

	/**
	 * コネクション情報を取得.
	 * @return コネクション情報
	 */
	public abstract ConnectionInfo getConnectionInfo();

	/**
	 * レスポンスフォーマットを取得.
	 * <ul>
	 * <li>1: XML</li>
	 * <li>2: JSON</li>
	 * <li>3: MessagePack</li>
	 * </ul>
	 */
	public abstract int getResponseFormat();

	/**
	 * コネクションをクローズ.
	 */
	public abstract void close();

	/**
	 * RequestURL + QueryString を取得.
	 * @return RequestURL + QueryString
	 */
	public String getRequestURLWithQueryString() {
		return UrlUtil.getRequestURLWithQueryString(this);
	}

	/**
	 * 経過時間を取得
	 * @return 経過時間(ミリ秒)
	 */
	public abstract long getElapsedTime();

	/**
	 * クライアントIPアドレスを取得.
	 * @return クライアントIPアドレス
	 */
	public abstract String getLastForwardedAddr();

}
