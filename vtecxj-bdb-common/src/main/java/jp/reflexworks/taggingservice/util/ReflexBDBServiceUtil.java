package jp.reflexworks.taggingservice.util;

import java.io.IOException;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletRequest;
import jp.reflexworks.servlet.util.UrlUtil;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * サービス取得ユーティリティ
 */
public class ReflexBDBServiceUtil {

	/** サービス名指定リクエストヘッダ */
	private static final String HEADER_SERVICENAME = Constants.HEADER_SERVICENAME;
	/** 名前空間指定リクエストヘッダ */
	private static final String HEADER_NAMESPACE = Constants.HEADER_NAMESPACE;
	/** DISTKEY項目リクエストヘッダ */
	private static final String HEADER_DISTKEY_ITEM = Constants.HEADER_DISTKEY_ITEM;
	/** DISTKEYの値リクエストヘッダ */
	private static final String HEADER_DISTKEY_VALUE = Constants.HEADER_DISTKEY_VALUE;
	/** SID指定リクエストヘッダ */
	private static final String HEADER_SID = Constants.HEADER_SID;

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(ReflexBDBServiceUtil.class);

	/**
	 * リクエストからサービス名を取得します.
	 * @param req リクエスト
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return サービス名
	 */
	public static String getMyServiceName(HttpServletRequest req,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		String serviceName = req.getHeader(HEADER_SERVICENAME);

		// サービス名は小文字のみ
		serviceName = editServiceName(serviceName);
		if (logger.isTraceEnabled()) {
			logger.trace("MyServiceName = " + serviceName);
		}
		return serviceName;
	}

	/**
	 * リクエストから名前空間を取得します.
	 * @param req リクエスト
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 名前空間
	 */
	public static String getMyNamespace(HttpServletRequest req,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		String namespace = req.getHeader(HEADER_NAMESPACE);
		if (logger.isTraceEnabled()) {
			logger.trace("MyNamespace = " + namespace);
		}
		return namespace;
	}

	/**
	 * サービス名を小文字変換.
	 * @param serviceName サービス名
	 * @return 小文字変換したサービス名
	 */
	public static String editServiceName(String serviceName) {
		if (!StringUtils.isBlank(serviceName)) {
			return serviceName.toLowerCase(Locale.ENGLISH);
		}
		return null;
	}

	/**
	 * リクエストからDISTKEY項目を取得します.
	 * @param req リクエスト
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return DISTKEY項目
	 */
	public static String getDistkeyItem(HttpServletRequest req,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		String distkeyItem = req.getHeader(HEADER_DISTKEY_ITEM);
		if (logger.isTraceEnabled()) {
			logger.trace("distkeyItem = " + distkeyItem);
		}
		return distkeyItem;
	}

	/**
	 * リクエストからDISTKEYの値を取得します.
	 * @param req リクエスト
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return DISTKEYの値
	 */
	public static String getDistkeyValue(HttpServletRequest req,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		// DISTKEY値はURLデコードを行う
		String distkeyValue = UrlUtil.urlDecode(req.getHeader(HEADER_DISTKEY_VALUE));
		if (logger.isTraceEnabled()) {
			logger.trace("distkeyValue = " + distkeyValue);
		}
		return distkeyValue;
	}

	/**
	 * リクエストからSIDを取得します.
	 * @param req リクエスト
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return SID
	 */
	public static String getSid(HttpServletRequest req,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		String sid = req.getHeader(HEADER_SID);
		if (logger.isTraceEnabled()) {
			logger.trace("MySid = " + sid);
		}
		return sid;
	}

}
