package jp.reflexworks.batch.servlet;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.Part;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.servlet.util.EnumerationConverter;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * バッチジョブ用リクエストクラス
 */
public class BatchJobHttpRequest implements HttpServletRequest {

	/** attribute */
	private Map<String, Object> attributes = new HashMap<String, Object>();
	/** Charactor-Encoding */
	private String encoding = "UTF-8";
	/** Content-Length */
	private int contentLength;
	/** parameter */
	private Map<String, String[]> parameters = new HashMap<String, String[]>();
	/** header */
	private Map<String, List<String>> headers = new HashMap<String, List<String>>();
	private String method;
	/** pathInfo */
	private String pathInfo;
	/** QueryString */
	private String queryString;
	/** payload */
	private byte[] payload;
	/** part */
	private List<Part> parts;
	/** server name */
	private String serverName;
	/** ip */
	private String ip;

	// 今のところ固定値
	/** protocol */
	private String protocol = "HTTP/1.1";
	/** schema */
	private String schema = "http";
	/** server port */
	private int serverPort = 80;
	/** context path */
	private String contextPath = "";
	/** servlet path */
	private String servletPath = "/s";

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param method method
	 * @param pathInfoQuery PathInfo + QueryString
	 * @param pHeaders headers
	 * @param serviceName サービス名
	 */
	public BatchJobHttpRequest(String method, String pathInfoQuery, Map<String, String> pHeaders,
			String ip, String serviceName) {
		this(method, pathInfoQuery, pHeaders, null, null, ip, serviceName);
	}

	/**
	 * コンストラクタ
	 * @param method method
	 * @param pathInfoQuery PathInfo + QueryString
	 * @param pHeaders headers
	 * @param payload payload
	 * @param parts Part情報
	 * @param serviceName サービス名
	 */
	public BatchJobHttpRequest(String method, String pathInfoQuery, Map<String, String> pHeaders,
			byte[] payload, List<Part> parts, String ip, String serviceName) {
		// method
		this.method = method;
		// server name
		this.serverName = BatchJobRequestUtil.getServerName(serviceName);
		// pathInfo, queryString
		if (pathInfoQuery != null) {
			int idx = pathInfoQuery.indexOf("?");
			if (idx == -1) {
				pathInfo = pathInfoQuery;
			} else {
				pathInfo = pathInfoQuery.substring(0, idx);
				if (pathInfoQuery.length() > idx) {
					queryString = pathInfoQuery.substring(idx + 1);
				}
				String paramsStr = pathInfoQuery.substring(idx + 1);
				String[] params = paramsStr.split("&");
				for (String param : params) {
					String name = null;
					String val = null;
					int pidx = param.indexOf("=");
					if (pidx == -1) {
						name = param;
						val = "";
					} else {
						name = param.substring(0, pidx);
						val = param.substring(pidx + 1);
					}
					parameters.put(name, new String[]{val});
				}
			}
		}
		// header
		if (pHeaders != null) {
			for (Map.Entry<String, String> mapEntry : pHeaders.entrySet()) {
				String hKey = mapEntry.getKey();
				List<String> hVal = new ArrayList<String>();
				hVal.add(mapEntry.getValue());
				headers.put(hKey, hVal);
			}
		}
		// payload
		this.payload = payload;
		// part
		this.parts = parts;
		// ip
		this.ip = ip;
	}

	public Object getAttribute(String name) {
		return attributes.get(name);
	}

	public Enumeration<String> getAttributeNames() {
		return new EnumerationConverter(attributes);
	}

	public String getCharacterEncoding() {
		return encoding;
	}

	public void setCharacterEncoding(String env) throws UnsupportedEncodingException {
		encoding = env;
	}

	public int getContentLength() {
		return contentLength;
	}

	public String getContentType() {
		return getHeader("Content-Type");
	}

	public ServletInputStream getInputStream() throws IOException {
		if (payload != null) {
			return new BatchJobInputStream(payload);
		}
		return null;
	}

	public String getParameter(String name) {
		String[] values = parameters.get(name);
		if (values != null && values.length > 0) {
			return values[0];
		}
		return null;
	}

	public Enumeration<String> getParameterNames() {
		return new EnumerationConverter(parameters);
	}

	public String[] getParameterValues(String name) {
		return parameters.get(name);
	}

	public Map<String, String[]> getParameterMap() {
		return parameters;
	}

	public String getProtocol() {
		return protocol;
	}

	public String getScheme() {
		return schema;
	}

	public String getServerName() {
		return serverName;
	}

	public int getServerPort() {
		return serverPort;
	}

	public BufferedReader getReader() throws IOException {
		if (payload != null) {
			return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(payload)));
		}
		return null;
	}

	/**
	 * リモートアドレスを取得.
	 * RequestInfoのip項目となる。
	 * @return ip
	 */
	public String getRemoteAddr() {
		return ip;
	}

	public String getRemoteHost() {
		// 自動生成されたメソッド・スタブ
		return null;
	}

	public void setAttribute(String name, Object o) {
		attributes.put(name, o);
	}

	public void removeAttribute(String name) {
		attributes.remove(name);
	}

	public Locale getLocale() {
		// 自動生成されたメソッド・スタブ
		return null;
	}

	public Enumeration<Locale> getLocales() {
		// 自動生成されたメソッド・スタブ
		return null;
	}

	public boolean isSecure() {
		// 自動生成されたメソッド・スタブ
		return false;
	}

	public RequestDispatcher getRequestDispatcher(String path) {
		// 自動生成されたメソッド・スタブ
		return null;
	}

	public String getRealPath(String path) {
		// 自動生成されたメソッド・スタブ
		return null;
	}

	public int getRemotePort() {
		// 自動生成されたメソッド・スタブ
		return 0;
	}

	public String getLocalName() {
		// 自動生成されたメソッド・スタブ
		return null;
	}

	public String getLocalAddr() {
		// 自動生成されたメソッド・スタブ
		return null;
	}

	public int getLocalPort() {
		// 自動生成されたメソッド・スタブ
		return 0;
	}

	public ServletContext getServletContext() {
		// 自動生成されたメソッド・スタブ
		return null;
	}

	public AsyncContext startAsync() throws IllegalStateException {
		// 自動生成されたメソッド・スタブ
		return null;
	}

	public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse)
			throws IllegalStateException {
		// 自動生成されたメソッド・スタブ
		return null;
	}

	public boolean isAsyncStarted() {
		// 自動生成されたメソッド・スタブ
		return false;
	}

	public boolean isAsyncSupported() {
		// 自動生成されたメソッド・スタブ
		return false;
	}

	public AsyncContext getAsyncContext() {
		// 自動生成されたメソッド・スタブ
		return null;
	}

	public DispatcherType getDispatcherType() {
		// 自動生成されたメソッド・スタブ
		return null;
	}

	public String getAuthType() {
		// 自動生成されたメソッド・スタブ
		return null;
	}

	public Cookie[] getCookies() {
		// 自動生成されたメソッド・スタブ
		return null;
	}

	public long getDateHeader(String name) {
		// 自動生成されたメソッド・スタブ
		return 0;
	}

	public String getHeader(String name) {
		List<String> values = headers.get(name);
		if (values != null && values.size() > 0) {
			return values.get(0);
		}
		return null;
	}

	public Enumeration<String> getHeaders(String name) {
		List<String> values = headers.get(name);
		return new EnumerationConverter(values);
	}

	public Enumeration<String> getHeaderNames() {
		return new EnumerationConverter(headers);
	}

	public int getIntHeader(String name) {
		List<String> values = headers.get(name);
		if (values != null && values.size() > 0) {
			return StringUtils.intValue(values.get(0), 0);
		}
		return 0;
	}

	public String getMethod() {
		return method;
	}

	public String getPathInfo() {
		return pathInfo;
	}

	public String getPathTranslated() {
		// 自動生成されたメソッド・スタブ
		return null;
	}

	public String getContextPath() {
		return contextPath;
	}

	public String getQueryString() {
		return queryString;
	}

	public String getRemoteUser() {
		// 自動生成されたメソッド・スタブ
		return null;
	}

	public boolean isUserInRole(String role) {
		// 自動生成されたメソッド・スタブ
		return false;
	}

	public Principal getUserPrincipal() {
		// 自動生成されたメソッド・スタブ
		return null;
	}

	public String getRequestedSessionId() {
		// 自動生成されたメソッド・スタブ
		return null;
	}

	public String getRequestURI() {
		StringBuilder sb = new StringBuilder();
		sb.append(contextPath);
		sb.append(servletPath);
		sb.append(pathInfo);
		return sb.toString();
	}

	public StringBuffer getRequestURL() {
		StringBuffer sb = new StringBuffer();
		sb.append(schema);
		sb.append("://");
		sb.append(serverName);
		if (serverPort != 0 && serverPort != 80) {
			sb.append(":");
			sb.append(serverPort);
		}
		sb.append(contextPath);
		sb.append(servletPath);
		sb.append(pathInfo);
		return sb;
	}

	public String getServletPath() {
		return servletPath;
	}

	public HttpSession getSession(boolean create) {
		// 自動生成されたメソッド・スタブ
		return null;
	}

	public HttpSession getSession() {
		// 自動生成されたメソッド・スタブ
		return null;
	}

	public boolean isRequestedSessionIdValid() {
		// 自動生成されたメソッド・スタブ
		return false;
	}

	public boolean isRequestedSessionIdFromCookie() {
		// 自動生成されたメソッド・スタブ
		return false;
	}

	public boolean isRequestedSessionIdFromURL() {
		// 自動生成されたメソッド・スタブ
		return false;
	}

	public boolean isRequestedSessionIdFromUrl() {
		// 自動生成されたメソッド・スタブ
		return false;
	}

	public boolean authenticate(HttpServletResponse response) throws IOException, ServletException {
		// 自動生成されたメソッド・スタブ
		return false;
	}

	public void login(String username, String password) throws ServletException {
		// 自動生成されたメソッド・スタブ

	}

	public void logout() throws ServletException {
		// 自動生成されたメソッド・スタブ

	}

	public Collection<Part> getParts() throws IOException, ServletException {
		return parts;
	}

	public Part getPart(String name) throws IOException, ServletException {
		if (parts != null && !StringUtils.isBlank(name)) {
			for (Part part : parts) {
				if (name.equals(part.getName())) {
					return part;
				}
			}
		}
		return null;
	}

	/**
	 * PathInfoを設定
	 * @param pathInfo PathInfo
	 */
	public void setPathInfo(String pathInfo) {
		this.pathInfo = pathInfo;
	}

	@Override
	public long getContentLengthLong() {
		// 自動生成されたメソッド・スタブ
		return 0;
	}

	@Override
	public String changeSessionId() {
		// 自動生成されたメソッド・スタブ
		return null;
	}

	@Override
	public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) throws IOException, ServletException {
		// 自動生成されたメソッド・スタブ
		return null;
	}

}
