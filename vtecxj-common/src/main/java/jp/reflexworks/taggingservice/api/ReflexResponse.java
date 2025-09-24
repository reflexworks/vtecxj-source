package jp.reflexworks.taggingservice.api;

import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

/**
 * TaggingService レスポンスクラス
 */
public abstract class ReflexResponse extends HttpServletResponseWrapper {
	
	/**
	 * コンストラクタ
	 * @param httpResp レスポンス
	 */
	public ReflexResponse(ReflexRequest req, HttpServletResponse httpResp) 
	throws IOException {
		super(httpResp);
	}
	
	/**
	 * レスポンスCookieを追加
	 * @param cookie Cookie
	 */
	@Override
	public void addCookie(Cookie cookie) {
		super.addCookie(cookie);
	}
	
	/**
	 * レスポンスヘッダを追加
	 * @param name 名前
	 * @param value 値
	 */
	@Override
	public void addHeader(String name, String value) {
		super.addHeader(name, value);
	}
	
	/**
	 * レスポンスヘッダを設定.
	 * このヘッダが既に設定されている場合は上書きされます。
	 * @param name 名前
	 * @param value 値
	 */
	@Override
	public void setHeader(String name, String value) {
		super.setHeader(name, value);
	}
	
	/**
	 * ステータス設定
	 * @param sc ステータス
	 */
	@Override
	public void setStatus(int sc) {
		super.setStatus(sc);
	}
	
	/**
	 * ステータス取得
	 * @return ステータス
	 */
	public abstract int getStatus();
	
	/**
	 * レスポンスヘッダ名を取得.
	 * @return レスポンスヘッダリスト
	 */
	public abstract Collection<String> getHeaderNames();
	
	/**
	 * HeaderNamesのEnumerationを返却
	 * @return HeaderNames
	 */
	public abstract Enumeration<String> getHeaderNameEnumeration();

	/**
	 * レスポンスヘッダの値を取得.
	 * @return 値
	 */
	public abstract Collection<String> getHeaders(String name);
	
	/**
	 * HeadersのEnumerationを返却
	 * @param name ヘッダのキー
	 * @return Headers
	 */
	public abstract Enumeration<String> getHeaderEnumeration(String name);
	
	/**
	 * 出力済みかどうかを返却.
	 * @return trueの場合レスポンスデータ出力済み。
	 */
	public abstract boolean isWritten();

}
