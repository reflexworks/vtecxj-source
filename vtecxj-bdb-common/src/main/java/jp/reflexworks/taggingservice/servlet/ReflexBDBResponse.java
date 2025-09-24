package jp.reflexworks.taggingservice.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jp.reflexworks.servlet.HttpStatus;
import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.sourceforge.reflex.util.DateUtil;

/**
 * TaggingService用レスポンス.
 */
public class ReflexBDBResponse extends ReflexResponse
implements ReflexServletConst {

	/** レスポンスヘッダ */
	private Map<String, List<String>> headers;
	/** レスポンスに設定するCookie */
	private List<Cookie> cookies;
	/** ステータス */
	private int status = HttpStatus.SC_OK;
	/** レスポンスデータ出力済みかどうか */
	private boolean isWritten;

	/**
	 * コンストラクタ.
	 * @param req リクエスト
	 * @param httpResp レスポンス
	 */
	public ReflexBDBResponse(ReflexRequest req, HttpServletResponse httpResp)
	throws IOException {
		super(req, httpResp);
		this.headers = new HashMap<String, List<String>>();
		this.cookies = new ArrayList<Cookie>();
	}

	/**
	 * 指定された Cookie をレスポンスに追加します.
	 * <p>
	 * レスポンスデータを設定する前に実行してください。
	 * </p>
	 * @param cookie レスポンスにセットするCookie
	 */
	@Override
	public void addCookie(Cookie cookie) {
		cookies.add(cookie);
		super.addCookie(cookie);	// レスポンスデータより先に登録する必要があるため、即時実行する。
	}

	/**
	 * 指定されたキーと値をレスポンスヘッダに追加します.
	 * <p>
	 * このメソッドを用いることで複数の値を持つようなレスポンスヘッダを設定することができます。 <br>
	 * レスポンスデータを設定する前に実行してください。
	 * </p>
	 * @param name レスポンスヘッダにセットするキー
	 * @param value レスポンスヘッダにセットする値
	 */
	@Override
	public void addHeader(String name, String value) {
		List<String> values = null;
		if (containsHeader(name)) {
			values = headers.get(name);
			if (values == null) {
				values = new ArrayList<String>();
			}
		} else {
			values = new ArrayList<String>();
			headers.put(name, values);
		}
		values.add(value);
		super.addHeader(name, value);	// レスポンスデータより先に登録する必要があるため、即時実行する。
	}

	/**
	 * 指定されたキーと値をレスポンスヘッダに追加します.
	 * <p>
	 * このメソッドを用いることで複数の値を持つようなレスポンスヘッダを設定することができます。 <br>
	 * レスポンスデータを設定する前に実行してください。
	 * </p>
	 * @param name レスポンスヘッダにセットするキー
	 * @param date レスポンスヘッダにセットする値
	 */
	@Override
	public void addDateHeader(String name, long date) {
		Date dt = new Date(date);
		String formatDt = DateUtil.getDateTime(dt);
		this.addHeader(name, formatDt);
	}

	/**
	 * 指定されたキーと値をレスポンスヘッダに追加します.
	 * <p>
	 * このメソッドを用いることで複数の値を持つようなレスポンスヘッダを設定することができます。 <br>
	 * レスポンスデータを設定する前に実行してください。
	 * </p>
	 * @param name レスポンスヘッダにセットするキー
	 * @param value レスポンスヘッダにセットする値
	 */
	@Override
	public void addIntHeader(String name, int value) {
		this.addHeader(name, String.valueOf(value));
	}

	/**
	 * 指定されたキーと値をレスポンスヘッダに追加します.
	 * <p>
	 * このヘッダが既に設定されている場合は上書きされます。<br>
	 * レスポンスデータを設定する前に実行してください。
	 * </p>
	 * @param name レスポンスヘッダにセットするキー
	 * @param value レスポンスヘッダにセットする値
	 */
	@Override
	public void setHeader(String name, String value) {
		List<String> values = new ArrayList<String>();
		values.add(value);
		headers.put(name, values);
		super.setHeader(name, value);	// レスポンスデータより先に登録する必要があるため、即時実行する。
	}

	/**
	 * 指定されたキーと値をレスポンスヘッダに追加します.
	 * <p>
	 * このヘッダが既に設定されている場合は上書きされます。<br>
	 * レスポンスデータを設定する前に実行してください。
	 * </p>
	 * @param name レスポンスヘッダにセットするキー
	 * @param date レスポンスヘッダにセットする値
	 */
	@Override
	public void setDateHeader(String name, long date) {
		Date dt = new Date(date);
		String formatDt = DateUtil.getDateTime(dt);
		this.setHeader(name, formatDt);
	}

	/**
	 * 指定されたキーと値をレスポンスヘッダに追加します.
	 * <p>
	 * このヘッダが既に設定されている場合は上書きされます。<br>
	 * レスポンスデータを設定する前に実行してください。
	 * </p>
	 * @param name レスポンスヘッダにセットするキー
	 * @param value レスポンスヘッダにセットする値
	 */
	@Override
	public void setIntHeader(String name, int value) {
		this.setHeader(name, String.valueOf(value));
	}

	/**
	 * 指定されたリダイレクト先の URL を用いて、クライアントに一時的なリダイレクトレスポンスを送信します。
	 * @param location リダイレクト先URL
	 */
	@Override
	public void sendRedirect(String location) {
		setStatus(HttpStatus.SC_MOVED_TEMPORARILY);
		setHeader(HEADER_LOCATION, location);
	}

	/**
	 * このレスポンスのステータスコードを設定します。
	 * <p>
	 * レスポンスデータを設定する前に実行してください。
	 * </p>
	 * @param sc ステータスコード
	 */
	@Override
	public void setStatus(int sc) {
		this.status = sc;
		super.setStatus(sc);	// レスポンスデータより先に登録する必要があるため、即時実行する。
	}

	/**
	 * ステータス取得
	 * @return ステータス
	 */
	public int getStatus() {
		return status;
	}

	/**
	 * レスポンスヘッダ名を取得.
	 * @return レスポンスヘッダリスト
	 */
	public Collection<String> getHeaderNames() {
		List<String> names = new ArrayList<String>();
		names.addAll(headers.keySet());
		if (!cookies.isEmpty() && !names.contains(HEADER_SET_COOKIE)) {
			names.add(HEADER_SET_COOKIE);
		}
		return names;
	}

	/**
	 * HeaderNamesのEnumerationを返却
	 * @return HeaderNames
	 */
	public Enumeration<String> getHeaderNameEnumeration() {
		CollectionEnumeration<String> setEnume = new CollectionEnumeration<String>(
				getHeaderNames());
		return setEnume;
	}

	/**
	 * レスポンスヘッダの値を取得.
	 * @return 値
	 */
	public Collection<String> getHeaders(String name) {
		List<String> values = headers.get(name);
		if (values != null) {
			return values;
		} else if (HEADER_SET_COOKIE.equals(name)) {
			values = new ArrayList<String>();
			for (Cookie cookie : cookies) {
				values.add(getCookieString(cookie));
			}
		}
		return values;
	}

	/**
	 * HeadersのEnumerationを返却
	 * @param name ヘッダのキー
	 * @return Headers
	 */
	public Enumeration<String> getHeaderEnumeration(String name) {
		CollectionEnumeration<String> setEnume = new CollectionEnumeration<String>(
				getHeaders(name));
		return setEnume;
	}

	/**
	 * レスポンスボディ出力用のWriteを取得
	 * @return レスポンスボディ出力用のWriter
	 */
	@Override
	public PrintWriter getWriter()
	throws IOException {
		isWritten = true;
		return super.getWriter();
	}

	/**
	 * レスポンスボディ出力用のOutputStreamを取得
	 * @return レスポンスボディ出力用のOutputStream
	 */
	@Override
	public ServletOutputStream getOutputStream()
	throws IOException {
		isWritten = true;
		return super.getOutputStream();
	}

	/**
	 * レスポンスボディに出力されたかどうかを返却
	 * @return レスポンスボディに出力された場合true
	 */
	public boolean isWritten() {
		return isWritten;
	}

	/**
	 * Cookie文字列を取得.
	 * @param cookie Cookie
	 * @return Cookieに設定する文字列
	 */
	private String getCookieString(Cookie cookie) {
		StringBuilder buf = new StringBuilder();
		buf.append(cookie.getName());
		buf.append("=");
		buf.append(cookie.getValue());
		buf.append("; ");
		if (cookie.getDomain() != null) {
			buf.append("Domain=");
			buf.append(cookie.getDomain());
			buf.append("; ");
		}
		if (cookie.getVersion() > 0) {
			buf.append("Version=");
			buf.append(cookie.getVersion());
			buf.append("; ");
		}
		if (cookie.getMaxAge() > 0) {
			buf.append("Max-Age=");
			buf.append(cookie.getMaxAge());
			buf.append("; ");
		}
		if (cookie.getPath() != null) {
			buf.append("Path=");
			buf.append(cookie.getPath());
			buf.append("; ");
		}
		if (cookie.getSecure()) {
			buf.append("Secure; ");
		}

		return buf.toString();
	}

	/**
	 * Collection Enumeration
	 * @param <E> Collectionに格納されるオブジェクト
	 */
	private class CollectionEnumeration<E> implements Enumeration<E> {
		/** Iterator */
		private Iterator<E> it;

		/**
		 * コンストラクタ.
		 * @param collection Collection
		 */
		public CollectionEnumeration(Collection<E> collection) {
			it = collection.iterator();
		}

		/**
		 * 続きのデータが存在するかどうか.
		 * @return 続きのデータが存在する場合true
		 */
		public boolean hasMoreElements() {
			return it.hasNext();
		}

		/**
		 * 続きのデータを取得.
		 * @return 続きのデータ
		 */
		public E nextElement() {
			return it.next();
		}
	}

}
