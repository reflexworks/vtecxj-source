package jp.reflexworks.taggingservice.plugin.def;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.servlet.ReflexServletUtil;
import jp.reflexworks.servlet.util.WsseAuth;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.api.SettingConst;
import jp.reflexworks.taggingservice.blogic.ServiceBlogic;
import jp.reflexworks.taggingservice.env.TaggingEnvConst;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.AuthenticationException;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.RXIDManager;
import jp.reflexworks.taggingservice.plugin.ReflexSecurityManager;
import jp.reflexworks.taggingservice.servlet.TaggingRequest;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.ExceptionUtil;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.NamespaceUtil;
import jp.reflexworks.taggingservice.util.UserUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * セキュリティ管理クラス.
 */
public class ReflexSecurityManagerDefault implements ReflexSecurityManager {

	/** 1分=60秒. */
	private static final int MIN_SEC = 60;
	/** 追加時間(秒) : 5分. */
	private static final int MARGIN_SEC = 300;
	/** localhost */
	private static final String LOCALHOST_PREFIX = "localhost:";

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 初期起動時の処理.
	 */
	public void init() {
		// Do nothing.
	}

	/**
	 * シャットダウン処理.
	 */
	public void close() {
		// Do nothing.
	}

	/**
	 * Hostヘッダのチェック (DNSリバインディング対策).
	 * エラーの場合AuthenticationExceptionをスローします。
	 * @param req リクエスト
	 * @param resp レスポンス
	 */
	public void checkRequestHost(ReflexRequest req, ReflexResponse resp)
	throws TaggingException {
		checkRequestHost(req, resp, false);
	}

	/**
	 * Hostヘッダのチェック (DNSリバインディング対策).
	 * エラーの場合AuthenticationExceptionをスローします。
	 * @param req リクエスト
	 * @param resp レスポンス
	 */
	public void checkRequestHostByInside(ReflexRequest req, ReflexResponse resp)
	throws TaggingException {
		checkRequestHost(req, resp, true);
	}

	/**
	 * Hostヘッダのチェック (DNSリバインディング対策).
	 * エラーの場合AuthenticationExceptionをスローします。
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param isInternal 内部ネットワークからのリクエストの場合true
	 */
	private void checkRequestHost(ReflexRequest req, ReflexResponse resp, boolean isInternal)
	throws TaggingException {
		RequestInfo requestInfo = req.getRequestInfo();
		// Hostチェック
		String host = req.getHeader(ReflexServletConst.HEADER_HOST);
		if (StringUtils.isBlank(host)) {
			String subMsg = "Host header is nothing.";
			AuthenticationException ae = new AuthenticationException();
			ae.setSubMessage(subMsg);
			throw ae;
		}

		String serviceName = req.getServiceName();
		// 標準Hostと比較
		ServiceBlogic serviceBlogic = new ServiceBlogic();
		String reflexHost = serviceBlogic.getHost(serviceName);
		if (!isInternal) {
			String editHost = removePort(host);
			String editRequestHost = removePort(reflexHost);
			if (editHost.equals(editRequestHost)) {
				return;
			}

			if (logger.isDebugEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[checkRequestHost]");
				sb.append(" The host name of the request is different from the standard host name.");
				sb.append(" reflexHost = ");
				sb.append(reflexHost);
				sb.append(" URL host = ");
				sb.append(host);
				logger.debug(sb.toString());
			}
			// リクエスト許可ホストに指定されているかチェックする。
			Set<String> allowOrigins = getAllowOrigins(serviceName);
			if (allowOrigins != null && allowOrigins.contains(editHost) && resp != null) {
				// 許可されている場合、レスポンスヘッダを設定する。
				resp.addHeader(ReflexServletConst.ACCESS_CONTROL_ALLOW_ORIGIN, editHost);
				resp.addHeader(ReflexServletConst.ACCESS_CONTROL_ALLOW_METHODS,
						ReflexServletConst.ACCESS_CONTROL_ALLOW_METHODS_VALUE);
				resp.addHeader(ReflexServletConst.ACCESS_CONTROL_ALLOW_CREDENTIALS,
						ReflexServletConst.ACCESS_CONTROL_ALLOW_CREDENTIALS_VALUE);
				return;
			}

		} else {
			// 内部リクエストの場合、プロパティ指定ホストだとエラー
			if (!host.equals(reflexHost)) {
				return;
			}
			// テスト環境(localhost)は処理を抜ける。
			if (host.startsWith(LOCALHOST_PREFIX)) {
				return;
			}
		}

		// 認証エラー
		String subMsg = "Host header is not allowed.";
		AuthenticationException ae = new AuthenticationException();
		ae.setSubMessage(subMsg);
		throw ae;
	}

	/**
	 * リクエストを許可するHost一覧を取得.
	 * @return リクエストを許可するHost一覧
	 */
	private Set<String> getAllowOrigins(String serviceName)
	throws TaggingException {
		return TaggingEnvUtil.getPropSet(serviceName,
				TaggingEnvConst.REQUEST_ALLOWORIGIN_PREFIX);
	}

	/**
	 * X-Requested-Withヘッダチェック.
	 * 以下の場合、リクエストヘッダにX-Requested-Withの指定がないとエラー。
	 * <ul>
	 *   <li>POST、PUT、DELETE</li>
	 *   <li>戻り値がJSON</li>
	 * </ul>
	 * @param req リクエスト
	 * @throws AuthenticationException セキュリティチェックエラー
	 */
	public void checkRequestedWith(ReflexRequest req)
	throws TaggingException {
		String method = req.getMethod();
		int responseFormat = req.getResponseFormat();
		boolean isCheck = false;
		if (ReflexServletConst.POST.equalsIgnoreCase(method) ||
				ReflexServletConst.PUT.equalsIgnoreCase(method) ||
				ReflexServletConst.DELETE.equalsIgnoreCase(method)) {
			// POST、PUT、DELETE の場合はチェックする。
			isCheck = true;
		} else if (ReflexServletConst.OPTIONS.equalsIgnoreCase(method)) {
			// OPTIONS の場合はチェックしない。
			isCheck = false;
		} else {
			// 戻り値がJSONの場合はチェックする。
			if (responseFormat == ReflexServletConst.FORMAT_JSON) {
				isCheck = isReturnedJson(req);
			}
		}
		if (isCheck && !ReflexServletUtil.hasXRequestedWith(req)) {
			String subMsg = "X-Requested-With header is required.";
			AuthenticationException ae = new AuthenticationException();
			ae.setSubMessage(subMsg);
			throw ae;
		}
	}

	/**
	 * JSONを返すリクエストパラメータかどうかチェック.
	 * @param req リクエスト
	 * @return JSONを返すリクエストパラメータの場合true
	 */
	private boolean isReturnedJson(ReflexRequest req) {
		// リクエストパラメータに1文字・先頭が_のもの(_content、_RXID 他以外)が設定されていれば
		// チェック対象とする。
		Map<String, String[]> params = req.getParameterMap();
		for (String paramName : params.keySet()) {
			// 特定のGETリクエストはX-Requested-Withチェックを行わない。
			if (paramName.equals(RequestParam.PARAM_CONTENT) ||
					paramName.equals(RequestParam.PARAM_REDIRECT_APP) ||
					paramName.equals(RequestParam.PARAM_XML) ||
					paramName.equals(RequestParam.PARAM_MESSAGEPACK) ||
					paramName.equals(RequestParam.PARAM_LOGIN) ||
					paramName.equals(RequestParam.PARAM_LOGOUT)) {
				return false;
			}
			if (paramName.length() == 1 ||
					(paramName.startsWith("_") &&
							!paramName.equals(RequestParam.PARAM_RXID) &&
							!paramName.equals(RequestParam.PARAM_TOKEN) &&
							!paramName.equals(RequestParam.PARAM_PASSRESET_TOKEN))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 認証失敗回数を取得
	 * @param user ユーザ識別値
	 * @param req リクエスト
	 * @param systemContext SystemContext
	 * @return ユーザ識別値+IPアドレスでの認証失敗回数
	 */
	public long getAuthFailureCount(String user, ReflexRequest req,
			SystemContext systemContext)
	throws IOException, TaggingException {
		String uri = getAuthFailuerCountUri(user, req);
		Long ret = systemContext.getCacheLong(uri);
		if (ret != null) {
			return ret.longValue();
		}
		return 0;
	}

	/**
	 * 認証失敗回数を加算
	 * @param user ユーザ識別値
	 * @param req リクエスト
	 * @param systemContext SystemContext
	 * @return ユーザ識別値+IPアドレスでの認証失敗回数
	 */
	public long incrementAuthFailureCount(String user, ReflexRequest req,
			SystemContext systemContext)
	throws IOException, TaggingException {
		String uri = getAuthFailuerCountUri(user, req);
		// 認証失敗回数加算
		long ret = systemContext.incrementCache(uri, 1);
		// 認証失敗回数保持の有効時間(秒)を設定
		int expireSec = TaggingEnvUtil.getSystemPropInt(
				SettingConst.AUTH_FAILED_COUNT_EXPIRE,
				TaggingEnvConst.AUTH_FAILED_COUNT_EXPIRE_DEFAULT);
		systemContext.setExpireCacheLong(uri, expireSec);
		return ret;
	}

	/**
	 * 認証失敗回数をクリア
	 * @param user ユーザ識別値
	 * @param req リクエスト
	 * @param systemContext SystemContext
	 */
	public void clearAuthFailureCount(String user, ReflexRequest req,
			SystemContext systemContext)
	throws IOException, TaggingException {
		String uri = getAuthFailuerCountUri(user, req);
		systemContext.deleteCacheLong(uri);
	}

	/**
	 * 認証失敗回数カウントURIを取得
	 * @param user ユーザ識別値
	 * @param req リクエスト
	 * @return 認証失敗回数カウントURI
	 */
	private String getAuthFailuerCountUri(String user, ReflexRequest req) {
		String editUser = UserUtil.editAccount(user);

		StringBuilder sb = new StringBuilder();
		sb.append(Constants.URI_SECURITY);
		sb.append("/");
		sb.append(editUser);
		sb.append("/");
		sb.append(getIPUri(req));
		return sb.toString();
	}

	/**
	 * IPアドレスキーを取得
	 * IPv6の場合、コロンはアンダースコアに変換する。
	 * @param req リクエスト
	 * @return IPアドレスキー
	 */
	private String getIPUri(ReflexRequest req) {
		String ip = getIPAddr(req);
		if (ip != null && ip.length() > 0) {
			if (ip.indexOf(".") > 0) {
				// IPv4 (変換なし)
			}
			if (ip.indexOf(":") > 0) {
				// IPv6
				ip = ip.replaceAll(":", "_");	// コロンをアンダースコアに変換
			}
		} else {
			ip = "null";
		}
		return ip;
	}

	/**
	 * IPアドレスを取得.
	 * @param req リクエスト
	 * @return IPアドレス
	 */
	public String getIPAddr(ReflexRequest req) {
		// バッチジョブサーバで、リクエストヘッダにIPアドレスが指定されている場合はそちらを使う。
		String serverType = TaggingEnvUtil.getSystemProp(TaggingEnvConst.REFLEX_SERVERTYPE, null);
		if (Constants.SERVERTYPE_BATCHJOB.equals(serverType)) {
			String xIpAddr = req.getHeader(Constants.HEADER_IP_ADDR);
			if (!StringUtils.isBlank(xIpAddr)) {
				return xIpAddr;
			}
		}
		return req.getLastForwardedAddr();
	}

	/**
	 * ホワイトリストチェック.
	 * サービス管理者の場合、このメソッドを呼び出します。
	 * ホワイトリストが設定されている場合、設定にないリモートアドレスからのリクエストを認証エラーとします。
	 * @param req リクエスト
	 * @param systemContext SystemContext
	 * @throws AuthenticationException ホワイトリストチェックエラー
	 */
	public void checkWhiteRemoteAddress(ReflexRequest req, SystemContext systemContext)
	throws IOException, TaggingException {
		String serviceName = req.getServiceName();
		Set<String> whiteAddresses = TaggingEnvUtil.getPropSet(serviceName,
				SettingConst.WHITE_REMOTEADDR_PREFIX);
		if (whiteAddresses != null && !whiteAddresses.isEmpty()) {
			boolean exists = false;
			String lastFowardedAddr = req.getLastForwardedAddr();
			if (!StringUtils.isBlank(lastFowardedAddr)) {
				for (String whiteAddr : whiteAddresses) {
					if (lastFowardedAddr.equals(whiteAddr)) {
						exists = true;
						break;
					}
				}
			}
			if (!exists) {
				String subMsg = "The IP address is not included in the service admin's white list. " + lastFowardedAddr;
				AuthenticationException ae = new AuthenticationException();
				ae.setSubMessage(subMsg);
				throw ae;
			}
		}
	}

	/**
	 * RXID使用回数チェック.
	 * GETでPathInfoが指定されたパターンと合致しているものは、指定された回数。その他は1回。
	 * RXIDの使用回数が上記を超えている場合はAuthenticationExceptionをスローします。
	 * @param rxid RXID
	 * @param reflexReq リクエスト
	 * @param systemContext SystemContext
	 */
	public void checkRXIDCount(String rxid, ReflexRequest reflexReq,
			SystemContext systemContext)
	throws IOException, TaggingException {
		TaggingRequest req = (TaggingRequest)reflexReq;
		String serviceName = req.getServiceName();
		RequestInfo requestInfo = req.getRequestInfo();
		ConnectionInfo connectionInfo = req.getConnectionInfo();
		// リクエストURLについてのRXID最大使用回数を取得
		long max = 1;
		if (Constants.GET.equalsIgnoreCase(req.getMethod())) {
			String myUri = req.getRequestType().getUri();
			Map<String, String> rxidCountPatterns = TaggingEnvUtil.getPropMap(serviceName,
					SettingConst.RXID_COUNTER_PREFIX);
			if (rxidCountPatterns != null && !rxidCountPatterns.isEmpty()) {
				for (Map.Entry<String, String> mapEntry : rxidCountPatterns.entrySet()) {
					String key = mapEntry.getKey();
					String[] parts = key.split("\\.");	// rxid.counter.{連番}.{回数}
					long maxCount = StringUtils.longValue(parts[3], 0);
					String patternStr = mapEntry.getValue();
					if (maxCount > 0 && !StringUtils.isBlank(patternStr)) {
						try {
							Pattern pattern = Pattern.compile(patternStr);
							Matcher matcher = pattern.matcher(myUri);
							if (matcher.matches()) {
								if (logger.isDebugEnabled()) {
									logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
											"[checkRXIDCount]");
								}
								max = maxCount;
								break;
							}

						} catch (PatternSyntaxException e) {
							StringBuilder msb = new StringBuilder();
							msb.append(e.getMessage());
							msb.append(", ");
							msb.append(key);
							msb.append("=");
							msb.append(patternStr);
							String title = "PatternSyntaxException";
							String msg = msb.toString();

							StringBuilder sb = new StringBuilder();
							sb.append(LogUtil.getRequestInfoStr(requestInfo));
							sb.append("[checkRXIDCount] ");
							sb.append(title);
							sb.append(" : ");
							sb.append(msg);
							logger.warn(sb.toString());
							// サービスごとの設定エラーのためログ出力
							systemContext.errorLog(title, msg);
						}
					}
				}
			}
 		}

		// 名前空間取得
		String namespace = NamespaceUtil.getNamespace(serviceName, requestInfo, connectionInfo);

		// RXID使用回数チェック
		int rxidExpireSec = getRxidExpireSec(serviceName, MARGIN_SEC, systemContext);
		RXIDManager rxidManager = TaggingEnvUtil.getRXIDManager();
		long cnt = rxidManager.incrementRXID(rxid, rxidExpireSec, serviceName, namespace,
				requestInfo, connectionInfo);
		if (cnt > max) {
			StringBuilder msgBld = new StringBuilder();
			WsseAuth wsseAuth = req.getWsseAuth();
			if (wsseAuth.isRxid) {
				msgBld.append("RXID has been used more than once. RXID=");
			} else {
				msgBld.append("WSSE has been used more than once. WSSE=");
			}
			msgBld.append(ExceptionUtil.getAuthErrorSubMessageValue(req, wsseAuth));
			String msg = msgBld.toString();
			AuthenticationException ae = new AuthenticationException(msg);
			ae.setSubMessage(msg);
			throw ae;
		}
	}

	/**
	 * RXID有効時間を設定
	 * @param serviceName サービス名
	 * @param margin 制限時間に追加する時間(秒)
	 * @param systemContext SystemContext
	 * @return RXID有効時間(秒)
	 */
	private int getRxidExpireSec(String serviceName, int margin,
			SystemContext systemContext) {
		int rxidMinute = 0;
		int systemRxidMinute = TaggingEnvUtil.getSystemPropInt(
							SettingConst.RXID_MINUTE, TaggingEnvConst.RXID_MINUTE_DEFAULT);
		try {
			rxidMinute = TaggingEnvUtil.getPropInt(serviceName, SettingConst.RXID_MINUTE,
					systemRxidMinute);
		} catch (InvalidServiceSettingException e) {
			String title = "InvalidServiceSettingException";
			StringBuilder msb = new StringBuilder();
			msb.append(e.getMessage());
			msb.append(" name=");
			msb.append(SettingConst.RXID_MINUTE);
			String msg = msb.toString();

			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(systemContext.getRequestInfo()));
			sb.append("[getRxidExpireSec] ");
			sb.append(title);
			sb.append(": ");
			sb.append(msg);
			logger.warn(sb.toString());
			// サービスの設定エラーなのでログ出力
			systemContext.log(title, Constants.WARN, msg);
			// デフォルト値設定
			rxidMinute = systemRxidMinute;
		}
		return rxidMinute * MIN_SEC + margin;
	}
	
	/**
	 * ホスト名からPort番号を除去する.
	 * @param host ホスト名
	 * @return Port番号を除去したホスト名
	 */
	private String removePort(String host) {
		int idx = host.lastIndexOf(":");
		if (idx > -1) {
			return host.substring(0, idx);
		} else {
			return host;
		}
	}

	/**
	 * 登録されたクロスドメインの場合、Origin URLを許可するレスポンスヘッダを付加する.
	 * @param req リクエスト
	 * @param resp レスポンス
	 */
	public void checkCORS(ReflexRequest req, ReflexResponse resp)
	throws IOException, TaggingException {
		String serviceName = req.getServiceName();
		String corsOrigin = TaggingEnvUtil.getProp(serviceName, SettingConst.CORS_ORIGIN, null);
		if (!StringUtils.isBlank(corsOrigin)) {
			String origin = req.getHeader(ReflexServletConst.HEADER_ORIGIN);
			if (corsOrigin.equals(origin)) {
				resp.addHeader(ReflexServletConst.ACCESS_CONTROL_ALLOW_ORIGIN, corsOrigin);
				resp.addHeader(ReflexServletConst.ACCESS_CONTROL_ALLOW_METHODS, "POST, PUT, GET, DELETE, OPTIONS");
				resp.addHeader(ReflexServletConst.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
				resp.addHeader(ReflexServletConst.ACCESS_CONTROL_ALLOW_HEADERS, "Authorization, X-WSSE, X-Requested-With, Content-Type, X-SERVICENAME");
			}
		}
	}

}
