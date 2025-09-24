package jp.reflexworks.taggingservice.oauth;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.blogic.ServiceBlogic;
import jp.reflexworks.taggingservice.blogic.SessionBlogic;
import jp.reflexworks.taggingservice.blogic.UserBlogic;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.AuthenticationException;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.FileUtil;
import jp.sourceforge.reflex.util.NumberingUtil;
import jp.sourceforge.reflex.util.Requester;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * OAuth ユーティリティ
 */
public class OAuthUtil {

	/** _user */
	private static final String URI_VALUE_USER = Constants.URI_USER.substring(1);
	/** oauth */
	private static final String URI_VALUE_OAUTH = OAuthConst.URI_USER_OAUTH.substring(1);

	/** ロガー. */
	private static final Logger logger = LoggerFactory.getLogger(OAuthUtil.class);

	/**
	 * コンストラクタ.
	 */
	private OAuthUtil() {}

	/**
	 * OAuthProviderクラスを取得.
	 *   パッケージ名: jp.reflexworks.taggingservice.oauth.provider
	 *   クラス名: プロバイダ名(先頭のみ大文字) + "Provider"
	 * @param provider プロバイダ名
	 * @return
	 */
	public static Class<OAuthProvider> getOAuthProviderClass(String provider) {
		if (StringUtils.isBlank(provider)) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		sb.append(OAuthConst.OAUTHPROVIDER_PACKAGE);
		sb.append(".");
		sb.append(provider.substring(0, 1).toUpperCase(Locale.ENGLISH));
		sb.append(provider.substring(1));
		sb.append(OAuthConst.OAUTHPROVIDER_CLASSNAME_SUFFIX);

		try {
			Class<?> cls = Class.forName(sb.toString());
			if (cls != null && OAuthProvider.class.isAssignableFrom(cls)) {
				return (Class<OAuthProvider>)cls;
			}
			return null;

		} catch (ClassNotFoundException e) {
			if (logger.isInfoEnabled()) {
				logger.info("[getOAuthProviderClass] ClassNotFoundException: " + e.getMessage());
			}
			return null;
		}
	}

	/**
	 * OAuthProviderクラスのインスタンスを取得
	 * @param cls OAuthProviderクラス
	 * @return OAuthProviderクラスのインスタンス
	 */
	public static OAuthProvider getInstance(Class<OAuthProvider> cls) {
		try {
			return cls.getDeclaredConstructor().newInstance();

		} catch (IllegalAccessException | InstantiationException | 
				InvocationTargetException | NoSuchMethodException |
				SecurityException e) {
			StringBuilder sb = new StringBuilder();
			sb.append("[getInstance] ");
			sb.append(e.getClass().getName());
			sb.append(": ");
			sb.append(e.getMessage());
			logger.warn(sb.toString());
			throw new IllegalParameterException("Invalid provider. " + cls.getSimpleName());
		}
	}

	/**
	 * OAuthプロバイダからのリダイレクトURLを取得.
	 *   https://{システム管理サービス}.vte.cx/o/{provider}/callback/
	 * @param req リクエスト
	 * @param provider プロバイダ名
	 * @param redirectService リダイレクト先サービス名
	 * @return OAuthプロバイダからのリダイレクトURL
	 */
	public static String getOAuthRedirectUrl(ReflexRequest req, String provider, 
			String redirectService)
	throws IOException, TaggingException {
		RequestInfo requestInfo = req.getRequestInfo();
		ConnectionInfo connectionInfo = req.getConnectionInfo();
		ServiceBlogic serviceBlogic = new ServiceBlogic();
		String redirectContextpath = serviceBlogic.getRedirectUrlContextPath(
				redirectService, requestInfo, connectionInfo);

		StringBuilder sb = new StringBuilder();
		sb.append(redirectContextpath);
		sb.append(req.getServletPath());
		sb.append("/");
		sb.append(provider);
		sb.append("/");
		sb.append(OAuthConst.ACTION_CALLBACK);
		sb.append("/");
		return sb.toString();
	}

	/**
	 * ランダム値を生成.
	 * @return ランダム値
	 */
	public static String createRandomString() {
		return NumberingUtil.randomString(OAuthConst.SECRET_LEN);
	}

	/**
	 * Redisに格納するsecretとサービス名情報のキーを取得.
	 * キー: /_oauthsecret/{provider}/{secret}
	 * @return URI
	 */
	public static String getSecretUri(String provider, String secret) {
		StringBuilder sb = new StringBuilder();
		sb.append(OAuthConst.URI_OAUTHSECRET);
		sb.append("/");
		sb.append(provider);
		sb.append("/");
		sb.append(secret);
		return sb.toString();
	}

	/**
	 * ソーシャルアカウント登録URIを取得
	 *  /_oauth/{provider}/{ソーシャルアカウント}
	 * @param provider プロバイダ
	 * @return
	 */
	public static String getSocialAccountUri(String provider, String oauthId) {
		StringBuilder sb = new StringBuilder();
		sb.append(getSocialAccountParentUri(provider));
		sb.append("/");
		sb.append(oauthId);
		return sb.toString();
	}

	/**
	 * ソーシャルアカウント登録親URIを取得
	 *  /_oauth/{provider}
	 * @param provider プロバイダ
	 * @return ソーシャルアカウント登録親URI
	 */
	public static String getSocialAccountParentUri(String provider) {
		StringBuilder sb = new StringBuilder();
		sb.append(OAuthConst.URI_OAUTH);
		sb.append("/");
		sb.append(provider);
		return sb.toString();
	}

	/**
	 * ソーシャルアカウントエントリーのエイリアスURIを取得
	 *  /_user/{UID}/oauth/{provider}
	 * @param provider プロバイダ名
	 * @param uid UID
	 * @return UIDを条件としたソーシャルアカウントエントリー検索時のURI (エイリアス)
	 */
	public static final String getSocialAccountAlias(String provider, String uid) {
		StringBuilder sb = new StringBuilder();
		sb.append(getSocialAccountAliasParent(uid));
		sb.append("/");
		sb.append(provider);
		return sb.toString();
	}

	/**
	 * ソーシャルアカウントエントリーのエイリアス親階層URIを取得
	 *  /_user/{UID}/oauth
	 * @param provider プロバイダ名
	 * @param uid UID
	 * @return UIDを条件としたソーシャルアカウントエントリー検索時のURI
	 */
	public static final String getSocialAccountAliasParent(String uid) {
		UserBlogic userBlogic = new UserBlogic();
		String userTopUri = userBlogic.getUserTopUriByUid(uid);

		StringBuilder sb = new StringBuilder();
		sb.append(userTopUri);
		sb.append(OAuthConst.URI_USER_OAUTH);
		return sb.toString();
	}

	/**
	 * secretを生成し、Redisに登録する.
	 * secretはランダムに生成し、RedisにputIfAbsentで登録することで一意であることを保証する。
	 * @param provider プロバイダ名
	 * @param systemContext SystemContext
	 * @return secret
	 */
	public static String createSecret(String provider, SystemContext systemContext)
	throws IOException, TaggingException {
		String serviceName = systemContext.getServiceName();
		// リトライ回数
		int numRetries = getCreateSecretRetryCount();
		String secret = null;
		for (int r = 0; r <= numRetries; r++) {
			// secretを生成
			secret = createRandomString();
			boolean isSet = setCacheIfAbsent(systemContext, provider, secret, serviceName);
			if (isSet) {
				return secret;
			}
		}
		// secret発行に失敗
		throw new IOException("Number of retries exceeded in secret generation.");
	}

	/**
	 * Redisキャッシュにsecretとサービス名を登録.
	 * @param systemContext SystemContext
	 * @param provider プロバイダ名
	 * @param secret secdret
	 * @param serviceName サービス名
	 * @return 正しく登録できた場合true
	 */
	public static boolean setCacheIfAbsent(SystemContext systemContext, String provider,
			String secret, String serviceName)
	throws IOException, TaggingException {
		// Redisにsecretをキーにサービス名を登録
		String secretUri = getSecretUri(provider, secret);
		// secretがかぶらないようifAbsentを使用
		boolean isSet = systemContext.setCacheStringIfAbsent(secretUri, serviceName);
		if (isSet) {
			// expire指定
			systemContext.setExpireCacheString(secretUri, getOAuthExpireSec(serviceName));
		}
		return isSet;
	}

	/**
	 * secret発行時の重複時リトライ総数を取得.
	 * @return secret発行時の重複時リトライ総数
	 */
	private static int getCreateSecretRetryCount() {
		return TaggingEnvUtil.getSystemPropInt(OAuthConst.OAUTH_CREATESECRET_RETRY_COUNT,
				OAuthConst.OAUTH_CREATESECRET_RETRY_COUNT_DEFAULT);
	}

	/**
	 * OAuth情報のexpire(秒)を取得.
	 * システム管理サービスのセッションと同じ長さとする。
	 * @param serviceName サービス名
	 * @return OAuth情報のexpire(秒)
	 */
	public static int getOAuthExpireSec(String serviceName) throws TaggingException {
		SessionBlogic sessionBlogic = new SessionBlogic();
		return sessionBlogic.getSessionExpire(serviceName);
	}

	/**
	 * secretをキーにサービス名を取得.
	 * @param systemContext SystemContext
	 * @param provider プロバイダ名
	 * @param secret secret
	 * @return サービス名
	 */
	public static String getServiceNameBySecret(SystemContext systemContext, String provider,
			String secret)
	throws IOException, TaggingException {
		String secretUri = getSecretUri(provider, secret);
		return systemContext.getCacheString(secretUri);
	}

	/**
	 * リクエスト送信
	 * @param url URL
	 * @param method method
	 * @return 戻り値
	 */
	public static OAuthResponseInfo request(String url, String method)
	throws IOException {
		return request(url, method, null, null);
	}

	/**
	 * リクエスト送信
	 * @param url URL
	 * @param method method
	 * @param headers リクエストヘッダ
	 * @return 戻り値
	 */
	public static OAuthResponseInfo request(String url, String method, Map<String, String> headers)
	throws IOException {
		return request(url, method, headers, null);
	}

	/**
	 * リクエスト送信
	 * @param url URL
	 * @param method method
	 * @param headers リクエストヘッダ
	 * @param inputData リクエストデータ
	 * @return 戻り値
	 */
	public static OAuthResponseInfo request(String url, String method, Map<String, String> headers,
			byte[] inputData)
	throws IOException {
		int timeoutMillis = TaggingEnvUtil.getSystemPropInt(OAuthConst.OAUTH_REQUEST_TIMEOUT_MILLIS,
				OAuthConst.OAUTH_REQUEST_TIMEOUT_MILLIS_DEFAULT);

		Requester requester = new Requester();
		HttpURLConnection http = requester.prepare(url, method, inputData, headers, timeoutMillis);
		int status = http.getResponseCode();
		InputStream in = null;
		try {
			InputStream tmpIn = null;
			if (status < 400) {
				tmpIn = http.getInputStream();
			} else {
				tmpIn = http.getErrorStream();
			}

			Map<String, String> respHeaders = getHeaders(http);
			if (tmpIn != null) {
				if (isGzip(respHeaders)) {
					in = new GZIPInputStream(tmpIn);
				} else {
					in = tmpIn;
				}
			}

			String data = null;
			if (in != null) {
				data = FileUtil.readString(in, OAuthConst.ENCODING);
			}
			return new OAuthResponseInfo(status, data, respHeaders);

		} finally {
			if (in != null) {
				in.close();
			}
		}
	}

	/**
	 * HTTPコネクションからレスポンスヘッダを取得.
	 * @param http HTTPコネクション
	 * @return レスポンスヘッダ
	 */
	public static Map<String, String> getHeaders(HttpURLConnection http) {
		Map<String, String> respHeaders = new HashMap<String, String>();
		if (http != null) {
			Map<String, List<String>> headerFields = http.getHeaderFields();
			if (headerFields != null && !headerFields.isEmpty()) {
				for (Map.Entry<String, List<String>> mapEntry : headerFields.entrySet()) {
					String name = mapEntry.getKey();
					StringBuilder val = new StringBuilder();
					List<String> vals = mapEntry.getValue();
					if (vals != null && !vals.isEmpty()) {
						boolean isFirst = true;
						for (String str : vals) {
							if (isFirst) {
								isFirst = false;
							} else {
								val.append(";");
							}
							val.append(str);
						}
						if (vals.size() > 1) {
							if (logger.isTraceEnabled()) {
								logger.trace("[getHeaders] There are two or more values. name=" + name + ", values=" + vals);
							}
						}
					}
					respHeaders.put(name, val.toString());
				}
			}
		}
		return respHeaders;
	}

	/**
	 * GZIP圧縮されているかどうか.
	 * @param respHeaders レスポンスヘッダ
	 * @return GZIP圧縮されている場合true
	 */
	public static boolean isGzip(Map<String, String> respHeaders) {
		if (respHeaders != null) {
			String contentEncoding = respHeaders.get(ReflexServletConst.HEADER_CONTENT_ENCODING);
			if (contentEncoding == null) {
				contentEncoding = respHeaders.get(ReflexServletConst.HEADER_CONTENT_ENCODING.toLowerCase(Locale.ENGLISH));
			}
			if (contentEncoding != null && contentEncoding.indexOf(ReflexServletConst.HEADER_VALUE_GZIP) > -1) {
				return true;
			}
		}
		return false;
	}

	/**
	 * JSONのキーと値をMapに詰めて返します.
	 * @param provider プロバイダ名(エラーログ用)
	 * @param str JSON
	 * @return キーと値のセット
	 */
	public static Map<String, String> convertFromJson(String provider, String str)
	throws IOException {
		Map<String, String> map = new HashMap<String, String>();
		if (!StringUtils.isBlank(str)) {
			JSONParser jsonParser = new JSONParser();
			try {
				Object o = jsonParser.parse(str);
				if (o instanceof JSONObject) {
					JSONObject jsonObj = (JSONObject)o;
					for (Object elem : jsonObj.entrySet()) {
						Map.Entry<String, Object> mapEntry = (Map.Entry<String, Object>)elem;
						String key = mapEntry.getKey();
						String val = String.valueOf(mapEntry.getValue());
						map.put(key, val);
					}
				} else {
					StringBuilder sb = new StringBuilder();
					sb.append("[convertFromJson] provider=");
					sb.append(provider);
					sb.append(" instance: ");
					sb.append(o.getClass().getName());
					logger.warn(sb.toString());
				}
			} catch (ParseException e) {
				throw new IOException(e);
			}
		}
		return map;
	}

	/**
	 * URLパラメータのキーと値をMapに詰めて返します.
	 * @param str URLパラメータ
	 * @return キーと値のセット
	 */
	public static Map<String, String> convertParam(String str) {
		Map<String, String> params = new HashMap<String, String>();
		if (!StringUtils.isBlank(str)) {
			String[] strParts = str.split("&");
			for (String strPart : strParts) {
				String key = null;
				String val = null;
				int idx = strPart.indexOf("=");
				if (idx == -1) {
					key = strPart;
					val = "";
				} else {
					key = strPart.substring(0, idx);
					val = strPart.substring(idx + 1);
				}
				params.put(key, val);
			}
		}
		return params;
	}

	/**
	 * AuthorizationをセットしたヘッダMapを取得
	 * @param value 認証値
	 * @param type 値の先頭につける文字列
	 * @return ヘッダMap
	 */
	public static Map<String, String> getAuthorizationHeaders(String value, String type) {
		Map<String, String> headers = new HashMap<String, String>();
		headers.put(ReflexServletConst.HEADER_AUTHORIZATION, type + value);
		return headers;
	}

	/**
	 * ソーシャルアカウントエントリーを生成
	 * @param provider プロバイダ名
	 * @param oauthId ソーシャルアカウント
	 * @param uid UID
	 * @param serviceName サービス名
	 * @return ソーシャルアカウントエントリー
	 */
	public static EntryBase createSocialAccountEntry(String provider, String oauthId,
			String uid, String serviceName) {
		String uri = getSocialAccountUri(provider, oauthId);
		EntryBase entry = TaggingEntryUtil.createEntry(serviceName);
		entry.setMyUri(uri);
		// エイリアス /_user/{UID}/oauth/{provider}
		String socialAccountAlias = getSocialAccountAlias(provider, uid);
		entry.addAlternate(socialAccountAlias);

		return entry;
	}

	/**
	 * UIDを検索条件にして、ソーシャルアカウントエントリーを検索.
	 * @param provider プロバイダ名
	 * @param uid UID
	 * @param systemContext SystemContext
	 * @return ソーシャルアカウントエントリー
	 */
	public static EntryBase getSocialAccountEntryByUid(String provider, String uid,
			SystemContext systemContext)
	throws IOException, TaggingException {
		String getSocialAccountUri = getSocialAccountAlias(provider, uid);
		return systemContext.getEntry(getSocialAccountUri, true);
	}

	/**
	 * ソーシャルアカウントエントリーのURIらソーシャルアカウントを取得.
	 *  /_oauth/{provider}/{ソーシャルアカウント}
	 * @param uri URI
	 * @return ソーシャルアカウント
	 */
	public static String getSocialAccountByUri(String uri) {
		String[] uriParts = TaggingEntryUtil.getUriParts(uri);
		if (uriParts.length > 3) {
			return uriParts[3];
		}
		return null;
	}

	/**
	 * ユーザ仮登録時に送信するメール情報エントリーを取得.
	 * @param systemContext SystemContext
	 * @return ユーザ仮登録時に送信するメール情報エントリー
	 */
	public static EntryBase getMailEntry(SystemContext systemContext)
	throws IOException, TaggingException {
		return systemContext.getEntry(Constants.URI_SETTINGS_ADDUSER, true);
	}

	/**
	 * 認証エラーを生成.
	 *  サブメッセージをセットする。
	 * @param provider プロバイダ名
	 * @param subMsg サブメッセージ
	 * @return 認証エラーオブジェクト
	 */
	public static AuthenticationException newAuthenticationException(String provider, String subMsg) {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		sb.append(provider);
		sb.append("] ");
		sb.append(subMsg);
		return newAuthenticationException(sb.toString());
	}

	/**
	 * 認証エラーを生成.
	 *  サブメッセージをセットする。
	 * @param subMsg サブメッセージ
	 * @return 認証エラーオブジェクト
	 */
	public static AuthenticationException newAuthenticationException(String subMsg) {
		AuthenticationException ae = new AuthenticationException();
		ae.setSubMessage(subMsg);
		return ae;
	}
	
	/**
	 * 設定のclient_idを取得
	 * @param provider プロバイダ
	 * @param serviceName サービス名
	 * @return client_id
	 */
	public static String getClientId(String provider, String serviceName) {
		String key = getSettingPrefix(provider) + OAuthConst.PARAM_CLIENT_ID;
		return TaggingEnvUtil.getProp(serviceName, key, null);
	}
	
	/**
	 * 設定のclient_secretを取得
	 * @param provider プロバイダ
	 * @param serviceName サービス名
	 * @return client_secret
	 */
	public static String getClientSecret(String provider, String serviceName) {
		String key = getSettingPrefix(provider) + OAuthConst.PARAM_CLIENT_SECRET;
		return TaggingEnvUtil.getProp(serviceName, key, null);
	}
	
	/**
	 * 設定のredirect_uriを取得
	 * @param provider プロバイダ
	 * @param serviceName サービス名
	 * @return client_secret
	 */
	public static String getRedirectUri(String provider, String serviceName) {
		String key = getSettingPrefix(provider) + OAuthConst.PARAM_REDIRECT_URI;
		return TaggingEnvUtil.getProp(serviceName, key, null);
	}

	/**
	 * 設定の接頭辞を取得.
	 *   _oauthclient.{provider}.
	 * @param provider プロバイダ
	 * @return 設定の接頭辞
	 */
	private static String getSettingPrefix(String provider) {
		StringBuilder sb = new StringBuilder();
		sb.append(OAuthSettingConst.OAUTHCLIENT_PREFIX);
		sb.append(provider);
		sb.append(".");
		return sb.toString();
	}

	/**
	 * 値が"null"であればnullを返す。
	 * @param val 値
	 * @return 値が"null"であればnullを返す。その他は値そのものを返す。
	 */
	public static String getValue(String val) {
		if ("null".equalsIgnoreCase(val)) {
			return null;
		}
		return val;
	}

	/**
	 * クラスからプロバイダ名を取得.
	 * @param cls クラス
	 * @return プロバイダ名
	 */
	public static String getProviderName(Class<?> cls) {
		String simpleName = cls.getSimpleName();
		return simpleName.substring(0, 1).toLowerCase(Locale.ENGLISH) +
				simpleName.substring(1, simpleName.length() - OAuthConst.OAUTHPROVIDER_CLASSNAME_SUFFIX_LEN);
	}

	/**
	 * ソーシャルアカウントエントリーからUIDを取得する
	 *  エイリアスに /_user/{UID}/oauth/{provider} が設定されている
	 * @param provider OAuthプロバイダ
	 * @param entry ソーシャルアカウントエントリー
	 * @return UID
	 */
	public static String getUidBySocialAccountEntry(String provider, EntryBase entry) {
		if (entry != null) {
			List<String> aliases = entry.getAlternate();
			if (aliases != null) {
				for (String alias : aliases) {
					String[] aliasParts = TaggingEntryUtil.getUriParts(alias);
					if (aliasParts.length == 5) {
						if (aliasParts[1].equals(URI_VALUE_USER) &&
								aliasParts[3].equals(URI_VALUE_OAUTH) &&
								aliasParts[4].equals(provider)) {
							return aliasParts[2];
						}
					}
				}
			}
		}
		return null;
	}

	/**
	 * 認証タイプを取得.
	 *  OAuth-{provider} の形式
	 * @param provider
	 * @return 認証タイプ
	 */
	public static String getAuthType(String provider) {
		StringBuilder sb = new StringBuilder();
		sb.append(Constants.AUTH_TYPE_OAUTH);
		sb.append("-");
		sb.append(provider);
		return sb.toString();
	}
	
	/**
	 * TaggingServiceユーザ登録用のアカウントを作成する.
	 *   `{ソーシャルアカウント}@@{provider}`とする。
	 * @param provider OAuthプロバイダ
	 * @param guid ソーシャルアカウント
	 * @return TaggingServiceユーザ登録用のアカウント
	 */
	public static String getTaggingAccount(String provider, String guid) {
		StringBuilder sb = new StringBuilder();
		sb.append(guid);
		sb.append(getTaggingAccountSuffix(provider));
		return sb.toString();
	}
	
	/**
	 * TaggingServiceユーザ登録用のアカウントを作成する.
	 *   `{ソーシャルアカウント}@@{provider}`とする。
	 * @param provider OAuthプロバイダ
	 * @param guid ソーシャルアカウント
	 * @return TaggingServiceユーザ登録用のアカウント
	 */
	public static String getTaggingAccountSuffix(String provider) {
		StringBuilder sb = new StringBuilder();
		sb.append(OAuthConst.MARK_ACCOUNT);
		sb.append(provider);
		return sb.toString();
	}

}
