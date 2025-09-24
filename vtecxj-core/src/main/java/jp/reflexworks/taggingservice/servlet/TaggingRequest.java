package jp.reflexworks.taggingservice.servlet;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.DataFormatException;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.api.AtomConst;
import jp.reflexworks.atom.api.EntryUtil;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.servlet.ReflexServletUtil;
import jp.reflexworks.servlet.util.UrlUtil;
import jp.reflexworks.servlet.util.WsseAuth;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexEnv;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexStatic;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.blogic.ServiceBlogic;
import jp.reflexworks.taggingservice.conn.ConnectionInfoImpl;
import jp.reflexworks.taggingservice.env.TaggingEnvConst;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.PayloadTooLargeException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.RequestInfoImpl;
import jp.reflexworks.taggingservice.model.RequestParamInfo;
import jp.reflexworks.taggingservice.plugin.AccessTokenManager;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.reflexworks.taggingservice.util.UserUtil;
import jp.sourceforge.reflex.exception.JSONException;
import jp.sourceforge.reflex.exception.XMLException;
import jp.sourceforge.reflex.util.DeflateUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * TaggingService用リクエスト.
 */
public class TaggingRequest extends ReflexRequest
implements ReflexServletConst {

	/** GET + " " の長さ */
	private static final int GET_SPACE_LEN = XHttpMethodOverrideUtil.GET_SPACE.length();
	/** POST + " " の長さ */
	private static final int POST_SPACE_LEN = XHttpMethodOverrideUtil.POST_SPACE.length();
	/** PUT + " " の長さ */
	private static final int PUT_SPACE_LEN = XHttpMethodOverrideUtil.PUT_SPACE.length();
	/** DELETE + " " の長さ */
	private static final int DELETE_SPACE_LEN = XHttpMethodOverrideUtil.DELETE_SPACE.length();

	/** multipart/formdata + ";" */
	private static final String CONTENT_TYPE_MULTIPART_FORMDATA_SEMICOLON =
			CONTENT_TYPE_MULTIPART_FORMDATA + ";";
	/** multipart/formdata + " " */
	private static final String CONTENT_TYPE_MULTIPART_FORMDATA_SPACE =
			CONTENT_TYPE_MULTIPART_FORMDATA + " ";
	/** text/xml + ";" */
	private static final String CONTENT_TYPE_XML_SEMICOLON =
			CONTENT_TYPE_XML + ";";
	/** text/xml + " " */
	private static final String CONTENT_TYPE_XML_SPACE =
			CONTENT_TYPE_XML + " ";
	/** application/json + ";" */
	private static final String CONTENT_TYPE_JSON_SEMICOLON =
			CONTENT_TYPE_JSON + ";";
	/** application/json + " " */
	private static final String CONTENT_TYPE_JSON_SPACE =
			CONTENT_TYPE_JSON + " ";
	/** application/x-msgpack + ";" */
	private static final String CONTENT_TYPE_MESSAGEPACK_SEMICOLON =
			CONTENT_TYPE_MESSAGEPACK + ";";
	/** application/x-msgpack + " " */
	private static final String CONTENT_TYPE_MESSAGEPACK_SPACE =
			CONTENT_TYPE_MESSAGEPACK + " ";

	/**
	 * payload.
	 */
	private byte[] payload;

	/**
	 * payloadの文字列型.
	 * FeedまたはEntryに変換するために一時的に使用する。
	 */
	private String payloadStr;

	/**
	 * payloadをデシリアライズしたオブジェクト.
	 * FeedまたはEntry。
	 */
	private Object requestObj;

	/** Payload取得済みかどうか。trueの場合Payload取得済み。*/
	private boolean isGetPayload;
	/** オブジェクトデシリアライズ済みかどうか。trueの場合デシリアライズ済み。*/
	private boolean isGetObject;

	/** リクエストのPOSTデータのフォーマット(RequestTypeは戻り値なので別) */
	private int reqFormat = -1;

	/** WSSE認証情報 */
	private WsseAuth wsseAuth;
	/** アクセストークン */
	private String accessToken;
	/** リンクトークン */
	private String linkToken;
	/** URLパラメータ保持マップ */
	private Map<String, String[]> parameterMap;
	/** Method */
	private String method;
	/** PathInfo */
	private String pathInfo;
	/** QueryString */
	private String queryString;
	/** MethodOverride指定かどうか。MethodOverrideの場合true。 */
	private boolean isXHttpMethodOverride;

	/** 認証情報 */
	private ReflexAuthentication auth;
	/** サービス名 */
	private String serviceName;
	/** リクエストパラメータ情報 */
	private RequestParam param;
	/** コネクション情報 */
	private ConnectionInfo connectionInfo;
	/** リクエスト情報 */
	private RequestInfo requestInfo;
	/** クライアントIPアドレス */
	private String lastForwardedAddr;

	/** アクセス開始時間 */
	private long startTime;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ.
	 * @param req リクエスト
	 */
	public TaggingRequest(HttpServletRequest httpReq)
	throws IOException {
		super(httpReq);

		// Method判定
		method = httpReq.getMethod();
		String overrideMethod = XHttpMethodOverrideUtil.getOverrideMethod(httpReq);
		if (PUT.equalsIgnoreCase(overrideMethod)) {
			method = PUT;
		} else if (DELETE.equalsIgnoreCase(overrideMethod)) {
			method = DELETE;
		} else if (GET.equalsIgnoreCase(overrideMethod)) {
			method = GET;
		}

		init(httpReq);
	}

	/**
	 * TaggingService用リクエスト生成処理.
	 * @param httpReq リクエスト
	 */
	private void init(HttpServletRequest httpReq) throws IOException {
		// アクセス開始時間
		this.startTime = new Date().getTime();

		// リクエストのPathInfoとQueryStringを保持
		setPathInfoQuery();
		// リクエストパラメータをMapに保持
		setParameterMap();

		// WSSE情報を抽出
		wsseAuth = UserUtil.getWsseAuth(this);

		try {
			// アクセストークンを抽出
			AccessTokenManager accessTokenManager =
					TaggingEnvUtil.getAccessTokenManager();
			accessToken = accessTokenManager.getAccessTokenFromRequest(this);
			linkToken = accessTokenManager.getLinkTokenFromRequest(this);

			requestInfo = new RequestInfoImpl(this);
			// DeflateUtil、コネクション情報、リクエスト情報生成
			DeflateUtil deflateUtil = new DeflateUtil();
			connectionInfo = new ConnectionInfoImpl(deflateUtil, requestInfo);

			// サービス判定
			ServiceBlogic serviceBlogic = new ServiceBlogic();
			serviceName = serviceBlogic.getMyServiceName(this,
					requestInfo, connectionInfo);

		} catch (TaggingException e) {
			throw new IOException(e);
		}
		((RequestInfoImpl)requestInfo).setServiceName(serviceName);
	}

	/**
	 * サービス名を取得.
	 * @return サービス名
	 */
	public String getServiceName() {
		return serviceName;
	}

	/**
	 * リクエストデータをFeedまたはEntryオブジェクトに変換したものを取得.
	 * @param targetServiceName サービス名
	 * @return リクエストデータをFeedまたはEntryオブジェクトに変換したもの
	 */
	public Object getObject(String targetServiceName)
	throws IOException, ClassNotFoundException, DataFormatException {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();
		if (!isGetObject) {
			// _content指定の場合、オブジェクト変換しない。
			if (getParameter(RequestParam.PARAM_CONTENT) != null) {
				return null;
			}
			// PayloadをInputStreamから取得
			getPayload();
			// Mapper
			FeedTemplateMapper mapper = env.getResourceMapper(targetServiceName);
			// オブジェクト変換
			if (payload != null && payload.length > 0) {
				if (ReflexServletUtil.isMessagePack(reqFormat)) {
					// payloadはconvertObjectで解凍済み
					byte[] msgData = (byte[])payload;
					// 2. MessagePack形式バイナリからオブジェクト変換
					requestObj = mapper.fromMessagePack(msgData, AtomConst.MSGPACK_FEED);
				} else {
					if (logger.isTraceEnabled()) {
						logger.trace(LogUtil.getRequestInfoStr(requestInfo) + "[getObject] payloadStr = " + payloadStr);
					}

					if (ReflexServletUtil.isXML(reqFormat)) {
						// XML
						try {
							requestObj = mapper.fromXML(payloadStr);
						} catch (XMLException e) {
							throw new IllegalParameterException("XMLException: " + e.getMessage());
						}
					} else if (ReflexServletUtil.isJSON(reqFormat)) {
						// JSON
						try {
							requestObj = mapper.fromJSON(payloadStr);
						} catch (JSONException e) {
							throw new IllegalParameterException("JSONException: " + e.getMessage());
						}
					} else {
						// 文字列
						requestObj = payloadStr;
					}
					// 一時項目なのでクリアする。
					payloadStr = null;
				}
			}
			isGetObject = true;
		}

		return requestObj;
	}

	/**
	 * リクエストデータをFeedオブジェクトに変換したものを取得.
	 * @return リクエストデータをFeedオブジェクトに変換したもの
	 * @throws IllegalParameterException このメソッド実行時、リクエストデータがFeedまたはEntryでない場合。
	 */
	public FeedBase getFeed()
	throws IOException, ClassNotFoundException, DataFormatException {
		return getFeed(serviceName);
	}

	/**
	 * リクエストデータをFeedオブジェクトに変換したものを取得.
	 * @param targetServiceName サービス名
	 * @return リクエストデータをFeedオブジェクトに変換したもの
	 * @throws IllegalParameterException このメソッド実行時、リクエストデータがFeedまたはEntryでない場合。
	 */
	public FeedBase getFeed(String targetServiceName)
	throws IOException, ClassNotFoundException, DataFormatException {
		if (StringUtils.isBlank(targetServiceName)) {
			targetServiceName = serviceName;
		}
		Object obj = getObject(targetServiceName);
		if (obj == null) {
			return null;
		}
		if (obj instanceof FeedBase) {
			return (FeedBase)obj;
		} else if (obj instanceof EntryBase) {
			return TaggingEntryUtil.createFeed(targetServiceName, (EntryBase)obj);
		} else {
			throw new IllegalParameterException("Request body is not Feed. " + obj.getClass().getSimpleName());
		}
	}

	/**
	 * リクエストデータのバイト配列を取得.
	 * @return リクエストデータのバイト配列
	 */
	public byte[] getPayload() throws IOException {
		if (!isGetPayload) {
			receivePayload();
			isGetPayload = true;
		}
		return payload;
	}

	/**
	 * WSSE認証情報を取得.
	 * @return WSSE認証情報
	 */
	public WsseAuth getWsseAuth() {
		return wsseAuth;
	}

	/**
	 * アクセストークンを取得.
	 * @return アクセストークン
	 */
	public String getAccessToken() {
		return accessToken;
	}

	/**
	 * リンクトークンを取得.
	 * @return リンクトークン
	 */
	public String getLinkToken() {
		return linkToken;
	}

	/**
	 * Taggingservice認証情報を取得.
	 * @return 認証情報
	 */
	public ReflexAuthentication getAuth() {
		return auth;
	}

	/**
	 * 認証情報設定
	 * @param auth 認証情報
	 */
	public void setAuth(ReflexAuthentication auth) {
		this.auth = auth;
	}

	/**
	 * リクエストパラメータ情報を取得.
	 * @return リクエストパラメータ情報
	 */
	public RequestParam getRequestType() {
		if (param == null) {
			param = new RequestParamInfo(this);
		}
		if (auth != null) {
			// 例外があれば、認証処理が終わってから1回だけスローする。
			((RequestParamInfo)param).throwExceptionIfInvalidParameter();
		}
		return param;
	}

	/**
	 * PathInfo + QueryStringを取得.
	 * @return PathInfo + QueryString
	 */
	public String getPathInfoQuery() {
		StringBuilder sb = new StringBuilder();
		sb.append(pathInfo);
		if (queryString != null) {
			sb.append("?");
			sb.append(queryString);
		}
		return sb.toString();
	}

	/**
	 * ログ出力のためのリクエスト情報を取得.
	 * @return リクエスト情報
	 */
	public RequestInfo getRequestInfo() {
		return requestInfo;
	}

	/**
	 * コネクション情報を取得.
	 * @return コネクション情報
	 */
	public ConnectionInfo getConnectionInfo() {
		return connectionInfo;
	}

	/**
	 * レスポンスフォーマットを取得.
	 * <ul>
	 * <li>1: XML</li>
	 * <li>2: JSON</li>
	 * <li>3: MessagePack</li>
	 * </ul>
	 */
	public int getResponseFormat() {
		RequestParam tmpParam = getRequestType();
		return tmpParam.getFormat();
	}

	/**
	 * 圧縮用辞書窓メモリを解放.
	 * データストア等のコネクションをクローズ
	 */
	public void close() {
		connectionInfo.close();
	}

	/**
	 * ヘッダにX-HTTP-Method-Override指定されたかどうかのフラグを取得します。
	 * @return ヘッダにX-HTTP-Method-Override指定された場合、true
	 */
	public boolean isXHttpMethodOverride() {
		return isXHttpMethodOverride;
	}

	/**
	 * クライアントIPアドレスを取得.
	 * @return クライアントIPアドレス
	 */
	public String getLastForwardedAddr() {
		if (lastForwardedAddr == null) {
			lastForwardedAddr = UrlUtil.getLastForwarded(this);
		}
		return lastForwardedAddr;
	}

	/**
	 * 経過時間を取得
	 * @return 経過時間(ミリ秒)
	 */
	public long getElapsedTime() {
		long now = new Date().getTime();
		return now - startTime;
	}

	/**
	 * Content-Typeの判定
	 */
	private void setReqFormatByContentType() {
		String contentType = this.getContentType();
		if (contentType != null) {
			if (contentType.equals(CONTENT_TYPE_MESSAGEPACK) ||
					contentType.startsWith(CONTENT_TYPE_MESSAGEPACK_SEMICOLON) ||
					contentType.startsWith(CONTENT_TYPE_MESSAGEPACK_SPACE)) {
				// application/x-msgpack
				reqFormat = FORMAT_MESSAGEPACK;
			} else if (contentType.equals(CONTENT_TYPE_JSON) ||
					contentType.startsWith(CONTENT_TYPE_JSON_SEMICOLON) ||
					contentType.startsWith(CONTENT_TYPE_JSON_SPACE)) {
				// application/json
				reqFormat = FORMAT_JSON;
			} else if (contentType.equals(CONTENT_TYPE_XML) ||
					contentType.startsWith(CONTENT_TYPE_XML_SEMICOLON) ||
					contentType.startsWith(CONTENT_TYPE_XML_SPACE)) {
				// text/xml
				reqFormat = FORMAT_XML;
			} else if (contentType.equals(CONTENT_TYPE_MULTIPART_FORMDATA) ||
					contentType.startsWith(CONTENT_TYPE_MULTIPART_FORMDATA_SEMICOLON) ||
					contentType.startsWith(CONTENT_TYPE_MULTIPART_FORMDATA_SPACE)) {
				// multipart/formdata
				reqFormat = FORMAT_MULTIPART_FORMDATA;
			}
		}
	}

	/**
	 * PathInfoとQueryStringの設定.
	 * Override Methodの場合、リクエストヘッダから取得する。
	 */
	private void setPathInfoQuery() {
		String pathInfoQuery = null;

		// Cookie
		String cookiePath = getCookieValue(COOKIE_METHOD_OVERRIDE_KEY_PATH);
		if (cookiePath != null && cookiePath.length() > 0) {
			pathInfoQuery = decodeBase64(cookiePath);
		}

		// X-HTTP-Method-Override
		if (pathInfoQuery == null && Constants.POST.equalsIgnoreCase(super.getMethod())) {
			String override = getHeader(HEADER_METHOD_OVERRIDE);
			if (override != null) {
				if (override.startsWith(XHttpMethodOverrideUtil.GET_SPACE)) {
					pathInfoQuery = override.substring(GET_SPACE_LEN);
				} else if (override.startsWith(XHttpMethodOverrideUtil.POST_SPACE)) {
					pathInfoQuery = override.substring(POST_SPACE_LEN);
				} else if (override.startsWith(XHttpMethodOverrideUtil.PUT_SPACE)) {
					pathInfoQuery = override.substring(PUT_SPACE_LEN);
				} else if (override.startsWith(XHttpMethodOverrideUtil.DELETE_SPACE)) {
					pathInfoQuery = override.substring(DELETE_SPACE_LEN);
				}
				if (pathInfoQuery != null) {
					isXHttpMethodOverride = true;
				}
			}
		}

		if (pathInfoQuery != null) {
			int idx = pathInfoQuery.indexOf("?");
			if (idx == -1) {
				idx = pathInfoQuery.length();
			}
			pathInfo = pathInfoQuery.substring(0, idx);
			if (idx < pathInfoQuery.length()) {
				queryString = pathInfoQuery.substring(idx + 1);
			} else {
				queryString = "";
			}

		} else {
			pathInfo = StringUtils.null2blank(super.getPathInfo());
			queryString = StringUtils.null2blank(super.getQueryString());
		}
	}

	/**
	 * リクエストパラメータをMapに設定
	 */
	private void setParameterMap() {
		parameterMap = new LinkedHashMap<String, String[]>();

		// 必ずQueryStringから取得する
		String encodeQueryString = getQueryString();
		String queryString = null;
		if (encodeQueryString != null) {
			queryString = encodeQueryString;	// エンコードのまま
		}
		if (queryString != null && queryString.length() > 0) {
			String[] params = queryString.split("&");
			for (String param : params) {
				String key = null;
				String value = null;
				int idx = param.indexOf("=");
				if (idx == -1) {
					key = param;
					value = "";
				} else {
					key = param.substring(0, idx);
					value = param.substring(idx + 1);
				}
				String[] values = null;
				int vidx = 0;
				if (parameterMap.containsKey(key)) {
					String[] oldValues = parameterMap.get(key);
					vidx = oldValues.length;
					values = new String[vidx + 1];
					System.arraycopy(oldValues, 0, values, 0, vidx);

				} else {
					values = new String[1];
				}
				values[vidx] = value;
				parameterMap.put(key, values);
			}
		}
	}

	/**
	 * Cookieの値を取得.
	 * @param key Cookieの名前
	 * @return Cookieの値
	 */
	private String getCookieValue(String key) {
		if (key == null) {
			return null;
		}
		Cookie[] cookies = getCookies();
		if (cookies != null) {
			for (Cookie cookie : cookies) {
				if (key.equals(cookie.getName())) {
					return cookie.getValue();
				}
			}
		}
		return null;
	}

	/**
	 * Base64デコード.
	 * @param value Base64エンコードされた文字列
	 * @return Base64にデコードした文字列
	 */
	private String decodeBase64(String value) {
		try {
			// Base64デコード
			byte[] decodeByte = Base64.decodeBase64(value.getBytes(Constants.ENCODING));
			return new String(decodeByte, Constants.ENCODING);
		} catch (UnsupportedEncodingException e) {
			logger.warn("[decodeBase64] UnsupportedEncodingException", e);
		}	// Do nothing.

		return null;
	}

	/**
	 * リクエストのInputStreamからPayloadを取得する.
	 */
	private void receivePayload()
	throws IOException, PayloadTooLargeException {
		// Content-Typeからフォーマット判定
		setReqFormatByContentType();
		// Payload取得
		if (!ReflexServletUtil.isFileUpload(reqFormat) &&
				(Constants.POST.equalsIgnoreCase(method) ||
						Constants.PUT.equalsIgnoreCase(method) ||
						Constants.DELETE.equalsIgnoreCase(method))) {
			int limit = TaggingEnvUtil.getSystemPropInt(TaggingEnvConst.REQUEST_PAYLOAD_SIZE,
					TaggingEnvConst.REQUEST_PAYLOAD_SIZE_DEFAULT);
			InputStream in = getInputStream();
			if (in != null) {
				ByteArrayOutputStream bout = new ByteArrayOutputStream();
				BufferedInputStream bin = null;
				try {
					bin = new BufferedInputStream(in);
					byte[] buffer = new byte[4096];
					int size;
					while ((size = in.read(buffer)) != -1) {
						bout.write(buffer, 0, size);
						if (bout.size() > limit) {
							if (logger.isDebugEnabled()) {
								StringBuilder sb = new StringBuilder();
								sb.append(LogUtil.getRequestInfoStr(requestInfo));
								sb.append("[receivePayload] Request payload size over. max=");
								sb.append(limit);
								sb.append(", size=");
								sb.append(bout.size());
								logger.debug(sb.toString());
							}
							break;
						}
					}

				} finally {
					try {
						if (bin != null) {
							bin.close();
						}
					} catch (Exception e) {
						logger.warn(LogUtil.getRequestInfoStr(requestInfo) + "[receivePayload] close error.", e);
					}
					try {
						in.close();
					} catch (Exception e) {
						logger.warn(LogUtil.getRequestInfoStr(requestInfo) + "[receivePayload] close error.", e);
					}
				}

				if (bout.size() > limit) {
					throw new PayloadTooLargeException();
				}
				payload = bout.toByteArray();

				// リクエストデータ判定
				DeflateUtil deflateUtil = getDeflateUtil();
				if (payload != null && payload.length > 0) {
					if (isContentDeflate()) {
						try {
							payload = deflateUtil.inflate(payload);
						} catch (DataFormatException e) {
							throw new IOException(e);
						}
					}
					if (reqFormat < 0) {
						if (EntryUtil.isMessagePack(payload)) {
							// MessagePack
							reqFormat = FORMAT_MESSAGEPACK;
						} else {
							// 文字列
							payloadStr = convertStringAndTrim(payload);
							if (payloadStr.startsWith("{")) {
								reqFormat = FORMAT_JSON;
								if (isOmittedJsonEntry(payloadStr)) {
									payloadStr = convertFeedJsonEntry(payloadStr);
								}
							} else if (payloadStr.startsWith("<")) {
								reqFormat = FORMAT_XML;
							} else if (isOmittedJson(payloadStr)) {
								// JSONのfeed、entry省略記法
								reqFormat = FORMAT_JSON;
								payloadStr = convertFeedJson(payloadStr);
							} else {
								// テキストでない可能性もあり
								reqFormat = FORMAT_TEXT;
							}
						}
					} else if (!ReflexServletUtil.isMessagePack(reqFormat)) {
						// Content-Type指定でpayloadが文字列の場合
						payloadStr = convertStringAndTrim(payload);
						// JSONの省略形を本来の形に編集
						if (ReflexServletUtil.isJSON(reqFormat)) {
							if (isOmittedJson(payloadStr)) {
								payloadStr = convertFeedJson(payloadStr);
							} else if (isOmittedJsonEntry(payloadStr)) {
								payloadStr = convertFeedJsonEntry(payloadStr);
							}
						}
					}
				}
			}
		}
	}

	/**
	 * 圧縮解凍ユーティリティを返却
	 * @return 圧縮解凍ユーティリティ
	 */
	private DeflateUtil getDeflateUtil() {
		return connectionInfo.getDeflateUtil();
	}

	/**
	 * リクエストデータがDeflate圧縮されているかどうか.
	 * 「Content-Encoding: deflate」が指定されていればDeflate圧縮されているとみなす。
	 * @return リクエストデータがDeflate圧縮されている場合true
	 */
	private boolean isContentDeflate() {
		return ReflexServletUtil.isSetHeader(this, HEADER_CONTENT_ENCODING,
				HEADER_VALUE_DEFLATE);
	}

	/**
	 * バイト配列である文字列をtrimする。
	 * @param bytes バイト配列
	 * @return trimした文字列
	 */
	private String convertStringAndTrim(byte[] bytes) {
		String dataStr = convertString(bytes);
		if (dataStr != null) {
			return dataStr.trim();
		}
		return dataStr;
	}

	/**
	 * バイト配列を文字列に変換する.
	 * @param bytes バイト配列
	 * @return 文字列
	 */
	private String convertString(byte[] bytes) {
		if (bytes != null && bytes.length > 0) {
			try {
				return new String(bytes, Constants.ENCODING);
			} catch (UnsupportedEncodingException e) {
				logger.warn(LogUtil.getRequestInfoStr(requestInfo) +
						"[convertString] UnsupportedEncodingException", e);
			}
		}
		return null;
	}

	/**
	 * JSON文字列が省略形かどうかチェックする.
	 * @param json JSON
	 * @return JSON文字列が省略形の場合true
	 */
	private boolean isOmittedJson(String json) {
		if (json.startsWith("[") && json.endsWith("]")) {
			return true;
		}
		return false;
	}

	/**
	 * JSON文字列がEntry省略形かどうかチェックする.
	 * 最初が`{`で始まり、次に続く文字が`"feed"`でない場合。
	 * @param json JSON
	 * @return JSON文字列がEntry省略形の場合true
	 */
	private boolean isOmittedJsonEntry(String json) {
		if (json.startsWith("{") && json.endsWith("}")) {
			String tmp = json.substring(1).trim();
			if (!tmp.startsWith("\"feed\"") &&
					!tmp.startsWith("feed") &&
					!tmp.startsWith("\"entry\"") &&
					!tmp.startsWith("entry")) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 省略形JSONにfeed・entryを付加する。
	 * @param omittedJson 省略形JSON
	 * @return 編集したJSON
	 */
	private String convertFeedJson(String omittedJson) {
		return "{\"feed\": {\"entry\": " + omittedJson + "}}";
	}

	/**
	 * 省略形JSONにfeed・entryを付加する。
	 * @param omittedJson 省略形JSON
	 * @return 編集したJSON
	 */
	private String convertFeedJsonEntry(String omittedJson) {
		return "{\"feed\": {\"entry\": [" + omittedJson + "]}}";
	}

	/**
	 * サービス名を設定.
	 * このメッセージは、このクラスを継承して使用する。
	 * @param serviceName サービス名
	 */
	protected void setServiceName(String serviceName) {
		this.serviceName = serviceName;
		((RequestInfoImpl)requestInfo).setServiceName(serviceName);
	}

}
