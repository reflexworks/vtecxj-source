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
import jp.reflexworks.servlet.ReflexServletUtil;
import jp.reflexworks.servlet.util.UrlUtil;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.conn.ReflexBDBConnectionInfo;
import jp.reflexworks.taggingservice.env.BDBEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.model.ReflexBDBRequestInfo;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.ReflexBDBServiceUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.exception.JSONException;
import jp.sourceforge.reflex.exception.XMLException;
import jp.sourceforge.reflex.util.DeflateUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * BDBインデックスサーバ用リクエスト.
 */
public abstract class ReflexBDBRequest extends ReflexRequest
implements ReflexBDBServletConst {

	/**
	 * payload.
	 */
	protected byte[] payload;

	/**
	 * payloadの文字列型.
	 * FeedまたはEntryに変換するために一時的に使用する。
	 */
	protected String payloadStr;

	/**
	 * payloadをデシリアライズしたオブジェクト.
	 * Feed。
	 */
	protected Object requestObj;

	/** Payload取得済みかどうか。trueの場合Payload取得済み。*/
	protected boolean isGetPayload;
	/** オブジェクトデシリアライズ済みかどうか。trueの場合デシリアライズ済み。*/
	protected boolean isGetObject;

	/** リクエストのPOSTデータのフォーマット(RequestTypeは戻り値なので別) */
	protected int reqFormat = -1;

	/** URLパラメータ保持マップ */
	protected Map<String, String[]> parameterMap;
	/** Method */
	protected String method;
	/** PathInfo */
	protected String pathInfo;
	/** QueryString */
	protected String queryString;

	/** サービス名 */
	protected String serviceName;
	/** 名前空間 */
	protected String namespace;

	/** リクエストパラメータ情報 */
	//private InnerIndexRequestParam param;
	/** コネクション情報 */
	protected ReflexBDBConnectionInfo connectionInfo;
	/** リクエスト情報 */
	protected ReflexBDBRequestInfo requestInfo;
	/** クライアントIPアドレス */
	protected String lastForwardedAddr;
	/** DISTKEY項目 */
	protected String distkeyItem;
	/** DISTKEYの値 */
	protected String distkeyValue;

	/** アクセス開始時間 */
	protected long startTime;

	/** ロガー. */
	protected Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ.
	 * @param req リクエスト
	 */
	public ReflexBDBRequest(HttpServletRequest httpReq)
	throws IOException {
		super(httpReq);

		// Method判定
		method = httpReq.getMethod();

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

		requestInfo = new ReflexBDBRequestInfo(this);
		// DeflateUtil、コネクション情報、リクエスト情報生成
		DeflateUtil deflateUtil = new DeflateUtil();
		connectionInfo = new ReflexBDBConnectionInfo(deflateUtil, requestInfo);

		// サービス判定
		serviceName = ReflexBDBServiceUtil.getMyServiceName(this,
				requestInfo, connectionInfo);
		namespace = ReflexBDBServiceUtil.getMyNamespace(this,
				requestInfo, connectionInfo);

		// DISTKEY
		distkeyItem = ReflexBDBServiceUtil.getDistkeyItem(this, requestInfo, connectionInfo);
		distkeyValue = ReflexBDBServiceUtil.getDistkeyValue(this, requestInfo, connectionInfo);

		requestInfo.setServiceName(serviceName);
		requestInfo.setNamespace(namespace);
	}

	/**
	 * サービス名を取得.
	 * @return サービス名
	 */
	public String getServiceName() {
		return serviceName;
	}

	/**
	 * 名前空間を取得.
	 * @return 名前空間
	 */
	public String getNamespace() {
		return namespace;
	}

	/**
	 * リクエストデータをFeedまたはEntryオブジェクトに変換したものを取得.
	 * @return リクエストデータをFeedまたはEntryオブジェクトに変換したもの
	 */
	public Object getObject()
	throws IOException, ClassNotFoundException, DataFormatException {
		if (!isGetObject) {
			// PayloadをInputStreamから取得
			getPayload();
			// Mapper (標準ATOM形式で受け付ける)
			FeedTemplateMapper mapper = BDBEnvUtil.getAtomResourceMapper();
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
							throw new IllegalParameterException(e.getMessage());
						}
					} else if (ReflexServletUtil.isJSON(reqFormat)) {
						// JSON
						try {
							requestObj = mapper.fromJSON(payloadStr);
						} catch (JSONException e) {
							throw new IllegalParameterException(e.getMessage());
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
	 * @param targetServiceName サービス名
	 * @return リクエストデータをFeedオブジェクトに変換したもの
	 * @throws IllegalParameterException このメソッド実行時、リクエストデータがFeedまたはEntryでない場合。
	 */
	public FeedBase getFeed(String targetServiceName)
	throws IOException, ClassNotFoundException, DataFormatException {
		// BDBで使用するのは標準ATOM形式なので、このメソッドは使用しない。
		return getFeed();
	}

	/**
	 * リクエストデータをFeedオブジェクトに変換したものを取得.
	 * @return リクエストデータをFeedオブジェクトに変換したもの
	 * @throws IllegalParameterException このメソッド実行時、リクエストデータがFeedまたはEntryでない場合。
	 */
	public FeedBase getFeed()
	throws IOException, ClassNotFoundException, DataFormatException {
		Object obj = getObject();
		if (obj == null) {
			return null;
		}
		if (obj instanceof FeedBase) {
			return (FeedBase)obj;
		} else if (obj instanceof EntryBase) {
			return TaggingEntryUtil.createAtomFeed((EntryBase)obj);
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
	 * RequestURL + QueryString を取得.
	 * @return RequestURL + QueryString
	 */
	public String getRequestURLWithQueryString() {
		return UrlUtil.getRequestURLWithQueryString(this);
	}

	/**
	 * リクエストパラメータ情報を取得.
	 * @return リクエストパラメータ情報
	 */
	/*
	public InnerIndexRequestParam getRequestType() {
		if (param == null) {
			param = new InnerIndexRequestParam(this);
		}
		return param;
	}
	*/

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
	public ReflexBDBRequestInfo getRequestInfo() {
		return requestInfo;
	}

	/**
	 * コネクション情報を取得.
	 * @return コネクション情報
	 */
	public ReflexBDBConnectionInfo getConnectionInfo() {
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
	/*
	public int getResponseFormat() {
		InnerIndexRequestParam tmpParam = getRequestType();
		return tmpParam.getFormat();
	}
	*/

	/**
	 * 圧縮用辞書窓メモリを解放.
	 * データストア等のコネクションをクローズ
	 */
	public void close() {
		connectionInfo.close();
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
	 * DISTKEY項目を取得.
	 * @return DISTKEY項目
	 */
	public String getDistkeyItem() {
		return distkeyItem;
	}

	/**
	 * DISTKEYの値を取得.
	 * @return DISTKEYの値
	 */
	public String getDistkeyValue() {
		return distkeyValue;
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
			byte[] decodeByte = Base64.decodeBase64(value.getBytes(ENCODING));
			return new String(decodeByte, ENCODING);
		} catch (UnsupportedEncodingException e) {
			logger.warn("[decodeBase64] UnsupportedEncodingException", e);
		}	// Do nothing.

		return null;
	}

	/**
	 * リクエストのInputStreamからPayloadを取得する.
	 */
	private void receivePayload()
	throws IOException {
		// Content-Typeからフォーマット判定
		setReqFormatByContentType();
		// Payload取得
		if (POST.equalsIgnoreCase(method) ||
				PUT.equalsIgnoreCase(method) ||
				DELETE.equalsIgnoreCase(method)) {
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
							} else if (payloadStr.startsWith("<")) {
								reqFormat = FORMAT_XML;
							} else if (isOmittedJson(payloadStr)) {
								// JSONのfeed、entry省略記法
								reqFormat = FORMAT_JSON;
								payloadStr = convertFeedJson(payloadStr);
							} else {
								// TODO テキストでない可能性もあり
								reqFormat = FORMAT_TEXT;
							}
						}
					} else if (!ReflexServletUtil.isMessagePack(reqFormat)) {
						// Content-Type指定でpayloadが文字列の場合
						payloadStr = convertStringAndTrim(payload);
						// JSONの省略形を本来の形に編集
						if (ReflexServletUtil.isJSON(reqFormat) &&
								isOmittedJson(payloadStr)) {
							payloadStr = convertFeedJson(payloadStr);
						}
					}
				}
			}
		}
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
				// TODO Content-Typeのcharsetを判定すべき？
				return new String(bytes, AtomConst.ENCODING);
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
	 * 省略形JSONにfeed・entryを付加する。
	 * @param omittedJson 省略形JSON
	 * @return 編集したJSON
	 */
	private String convertFeedJson(String omittedJson) {
		return "{\"feed\": {\"entry\": " + omittedJson + "}}";
	}

	@Override
	public ReflexAuthentication getAuth() {
		// 
		return null;
	}

}
