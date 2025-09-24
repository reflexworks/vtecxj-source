package jp.reflexworks.taggingservice.oauth.provider;

import java.io.IOException;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.servlet.HttpStatus;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.oauth.OAuthConst;
import jp.reflexworks.taggingservice.oauth.OAuthInfo;
import jp.reflexworks.taggingservice.oauth.OAuthProvider;
import jp.reflexworks.taggingservice.oauth.OAuthResponseInfo;
import jp.reflexworks.taggingservice.oauth.OAuthUtil;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.Constants;
import jp.sourceforge.reflex.util.NumberingUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * OAuthプロバイダ : Facebook
 */
public class TwitterProvider implements OAuthProvider {
	
	/** リクエストトークン取得URL (POST) */
	private static final String REQUESTTOKEN_URL = "https://api.twitter.com/oauth/request_token";
	/** リクエストトークン取得メソッド */
	private static final String REQUESTTOKEN_METHOD = Constants.POST;
	
	/** 認証URL (GET) */
	private static final String AUTHENTICATE_URL = "https://api.twitter.com/oauth/authenticate";
	
	/** アクセストークンURL (POST) */
	private static final String ACCESSTOKEN_URL = "https://api.twitter.com/oauth/access_token";
	/** アクセストークン取得メソッド */
	private static final String ACCESSTOKEN_METHOD = Constants.POST;

	/** ユーザ情報 : id (GUID) */
	private static final String GUID = "user_id";
	/** ユーザ情報 : screen_name */
	private static final String SCREEN_NAME = "screen_name";
	
	/** signature作成ハッシュ方式 */
	private static final String HMAC_SHA1 = "HmacSHA1";
	/** nonceの長さ */
	private static final int NONCE_LEN = 16;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());
	
	/** プロバイダ名 */
	private final String providerName = OAuthUtil.getProviderName(this.getClass());
	
	/**
	 * プロバイダ名を取得.
	 * @return プロバイダ名
	 */
	public String getProviderName() {
		return providerName;
	}
	
	/**
	 * OAuthプロバイダへ認証要求開始
	 *   ・secretを生成
	 *   ・Redisにsecretをキーにサービス名を登録
	 *   ・リダイレクトURLに以下を設定 *client_id *client_secret *state (secret)
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param targetService サービス名
	 */
	public void oauth(ReflexRequest req, ReflexResponse resp) 
	throws IOException, TaggingException {
		SystemContext systemContext = new SystemContext(req.getServiceName(), 
				req.getRequestInfo(), req.getConnectionInfo());
		
		// TwitterはOAuth1なので、最初にリクエストトークンを取得する。
		// リクエストヘッダに以下を設定する
		/*
		Authorization:
		    OAuth oauth_callback="http%3A%2F%2Flocalhost%2Fsign-in-with-twitter%2F",
		          oauth_consumer_key="XXXXXXXXXXXXXXXXXXXXXX",
		          oauth_nonce="ea9ec8429b68d6b77cd5600adbbb0456",
		          oauth_signature="F1Li3tvehgcraF8DMJ7OyxO4w9Y%3D",
		          oauth_signature_method="HMAC-SHA1",
		          oauth_timestamp="1318467427",
		          oauth_version="1.0"
		 */
		
		// client_id, client_secret
		String serviceName = systemContext.getServiceName();
		String clientId = OAuthUtil.getClientId(getProviderName(), serviceName);
		String clientSecret = OAuthUtil.getClientSecret(getProviderName(), serviceName);
		// redirect_url
		String callbackUrl = OAuthUtil.getRedirectUri(getProviderName(), serviceName);
		String nonce = createNonce();
		String timestamp = createTimestamp();
		Map<String, String> authMap = getAuthorizationMap(req, nonce, timestamp, clientId, callbackUrl);
		String baseString = createBaseString(REQUESTTOKEN_METHOD, REQUESTTOKEN_URL, authMap);
		
		String signature = doSign(baseString, clientSecret + "&");
		authMap.put("oauth_signature", signature);
		
		// リクエストヘッダは内容を１個にまとめる。
		Map<String, String> requesttokenHeaders = createAuthorizationHeader(authMap);
		
		OAuthResponseInfo requestTokenRespInfo = OAuthUtil.request(REQUESTTOKEN_URL, 
				REQUESTTOKEN_METHOD, requesttokenHeaders);
		if (logger.isDebugEnabled()) {
			logger.debug("[oauth] requestToken response: " + requestTokenRespInfo);
		}
		if (requestTokenRespInfo.status != HttpStatus.SC_OK) {
			throw OAuthUtil.newAuthenticationException(getProviderName(), "OAuth requestToken response: " + requestTokenRespInfo);
		}
		
		// アクセストークンを取得
		// レスポンスデータはパラメータ形式
		/*
			RequestToken Response data: oauth_token=vVZARgAAAAAA9gCwAAABaSPNU6M&oauth_token_secret=cjoPjNkrmX1BVaXlArp0QOCqsbNKXn5B&oauth_callback_confirmed=true
		 */
		// oauth_token の値を抽出する。
		Map<String, String> dataMap = OAuthUtil.convertParam(requestTokenRespInfo.data);
		String oauthToken = dataMap.get(OAuthConst.PARAM_OAUTH_TOKEN);
		if (StringUtils.isBlank(oauthToken)) {
			throw OAuthUtil.newAuthenticationException(getProviderName(), "You could not get OAuth request token.");
		}
		// oauth_callback_confirmed=true かどうかチェック
		String oauthCallbackConfirmed = dataMap.get("oauth_callback_confirmed");
		if (!"true".equals(oauthCallbackConfirmed)) {
			throw OAuthUtil.newAuthenticationException(getProviderName(), "oauth_callback_confirmed is not true.");
		}
		
		// oauth_tokenをRedisに登録
		String secret = oauthToken;
		boolean set = OAuthUtil.setCacheIfAbsent(systemContext, getProviderName(), secret, 
				serviceName);
		if (!set) {
			throw OAuthUtil.newAuthenticationException(getProviderName(), "oauth_token is duplicated.");
		}
		
		// リダイレクト
		String authenticationUrl = getAuthenticationUrl(secret);
		resp.sendRedirect(authenticationUrl);
	}

	/**
	 * OAuthプロバイダからcallback
	 * @param req リクエスト
	 * @return OAuth認証で取得したユーザ情報
	 */
	public OAuthInfo callback(ReflexRequest req) 
	throws IOException, TaggingException {
		String serviceName = req.getServiceName();
		RequestParam param = (RequestParam)req.getRequestType();
		
		/*
		GET /o/twitter/callback/?
		        oauth_token=NPcudxy0yU5T3tBzho7iCotZ3cnetKwcTIRlX0iwRl0&
		        oauth_verifier=uw7NjWHT6OJ1MpJOXsHfNxoAhPKpgI8BlYDhxEjIBY
		 */

		// oauth_tokenとoauth_verifierを取得
		String oauthToken = param.getOption(OAuthConst.PARAM_OAUTH_TOKEN);
		if (StringUtils.isBlank(oauthToken)) {
			throw OAuthUtil.newAuthenticationException(getProviderName(), "OAuth token is not specified.");
		}
		String oauthVerifier = param.getOption(OAuthConst.PARAM_OAUTH_VERIFIER);
		if (StringUtils.isBlank(oauthVerifier)) {
			throw OAuthUtil.newAuthenticationException(getProviderName(), "OAuth verifier is not specified.");
		}
		String secret = oauthToken;
		
		SystemContext systemContext = new SystemContext(serviceName, 
				req.getRequestInfo(), req.getConnectionInfo());
		
		// Redisからstateまたはリクエストトークンをキーにサービス名を取得(存在しなければエラー)
		String targetService = OAuthUtil.getServiceNameBySecret(systemContext, getProviderName(),
				secret);
		if (StringUtils.isBlank(targetService)) {
			throw OAuthUtil.newAuthenticationException(getProviderName(), "The OAuth secret is not registed. " + secret);
		}
		
		// アクセストークンを取得
		/*
		POST /oauth/access_token
		Authorization: OAuth oauth_consumer_key="XXXXXXXXXXXXXXXXXXXXXX",
		                     oauth_nonce="a9900fe68e2573b27a37f10fbad6a755",
		                     oauth_signature="39cipBtIOHEEnybAR4sATQTpl2I%3D",
		                     oauth_signature_method="HMAC-SHA1",
		                     oauth_timestamp="1318467427",
		                     oauth_token="NPcudxy0yU5T3tBzho7iCotZ3cnetKwcTIRlX0iwRl0",
		                     oauth_version="1.0"
		
		リクエストボディ
		oauth_verifier=uw7NjWHT6OJ1MpJOXsHfNxoAhPKpgI8BlYDhxEjIBY
		 */
		
		// client_id, client_secret
		String clientId = OAuthUtil.getClientId(getProviderName(), serviceName);
		String clientSecret = OAuthUtil.getClientSecret(getProviderName(), serviceName);
		// redirect_url
		String callbackUrl = OAuthUtil.getRedirectUri(getProviderName(), serviceName);
		
		String nonce = createNonce();
		String timestamp = createTimestamp();
		Map<String, String> authMap = getAuthorizationMap(req, nonce, timestamp, clientId, callbackUrl);
		// authMap に oauth_token を追加。
		authMap.put("oauth_token", oauthToken);
		
		String baseString = createBaseString(ACCESSTOKEN_METHOD, ACCESSTOKEN_URL, authMap);
		String signature = doSign(baseString, clientSecret + "&");
		authMap.put("oauth_signature", signature);
		
		// リクエストヘッダは内容を１個にまとめる。
		Map<String, String> accesstokenHeaders = createAuthorizationHeader(authMap);
		// リクエストボディ
		byte[] accesstokenData = getAccesstokenRequestData(oauthVerifier);
		
		OAuthResponseInfo accessTokenRespInfo = OAuthUtil.request(ACCESSTOKEN_URL, 
				ACCESSTOKEN_METHOD, accesstokenHeaders, accesstokenData);
		if (logger.isDebugEnabled()) {
			logger.debug("[callback] accessToken response: " + accessTokenRespInfo);
		}
		if (accessTokenRespInfo.status != HttpStatus.SC_OK) {
			throw OAuthUtil.newAuthenticationException(getProviderName(), "OAuth accessToken response: " + accessTokenRespInfo);
		}
		
		// アクセストークンを取得
		// 成功したレスポンスには、oauth_token、oauth_token_secret パラメータが含まれています。
		// 一緒にuser_idも取得。
		/*
		AccessToken Response data: oauth_token=9999999999999999999-ogBondFU42nEKjADi3j1d0i1r9Zwjj&oauth_token_secret=FHCFd00UIXrOHnZC6A7ypIvQE06ohuWqMGZJ1waqQb3pZ&user_id=9999999999999999999&screen_name=myapi
		 */
		Map<String, String> dataMap = OAuthUtil.convertParam(accessTokenRespInfo.data);
		String guid = OAuthUtil.getValue(dataMap.get(GUID));
		String email = null;
		String nickname = OAuthUtil.getValue(dataMap.get(SCREEN_NAME));
		if (StringUtils.isBlank(guid)) {
			throw OAuthUtil.newAuthenticationException(getProviderName(), "You could not get OAuth guid.");
		}
		
		return new OAuthInfo(guid, email, nickname);
	}

	/**
	 * リダイレクトURLを生成.
	 * @param oauthToken secret
	 * @return リダイレクトURL
	 */
	private String getAuthenticationUrl(String oauthToken) 
	throws IOException, TaggingException {
		StringBuilder sb = new StringBuilder();
		sb.append(AUTHENTICATE_URL);
		sb.append("?");
		sb.append(OAuthConst.PARAM_OAUTH_TOKEN);
		sb.append("=");
		sb.append(URLEncoder.encode(oauthToken, OAuthConst.ENCODING));
		return sb.toString();
	}
	
	/**
	 * nonceを生成.
	 * @return nonce
	 */
	private String createNonce() {
		return NumberingUtil.randomString(NONCE_LEN);
	}
	
	/**
	 * タイムスタンンプ文字列を生成
	 * @return タイムスタンプ
	 */
	private String createTimestamp() {
		String tmpTimestamp = String.valueOf(new Date().getTime());
		return tmpTimestamp.substring(0, tmpTimestamp.length() - 3);	// 秒まで
	}

	/**
	 * 認証情報マップを返却
	 * @param req リクエスト
	 * @param nonce nonce
	 * @param timestamp タイムスタンプ
	 * @param clientId client id
	 * @param callbackUrl callback url
	 * @return 認証情報マップ
	 */
	private Map<String, String> getAuthorizationMap(ReflexRequest req, String nonce, 
			String timestamp, String clientId, String callbackUrl) 
	throws IOException, TaggingException {
		/*
		Authorization:
		    OAuth oauth_callback="http%3A%2F%2Flocalhost%2Fsign-in-with-twitter%2F",
		          oauth_consumer_key="XXXXXXXXXXXXXXXXXXXXXX",
		          oauth_nonce="ea9ec8429b68d6b77cd5600adbbb0456",
		          oauth_signature="F1Li3tvehgcraF8DMJ7OyxO4w9Y%3D",
		          oauth_signature_method="HMAC-SHA1",
		          oauth_timestamp="1318467427",
		          oauth_version="1.0"
		 */
		
		// キーを辞書式に並べるマップ
		Map<String, String> authMap = new TreeMap<String, String>();
		authMap.put("oauth_callback", callbackUrl);
		authMap.put("oauth_consumer_key", clientId);
		authMap.put("oauth_nonce", nonce);
		authMap.put("oauth_signature_method", "HMAC-SHA1");
		authMap.put("oauth_timestamp", timestamp);
		authMap.put("oauth_version", "1.0");
		return authMap;
	}
	
	/**
	 * signatureに使用するbase stringを作成
	 * @param authMap 認証情報
	 * @return signatureに使用するbase string
	 */
	private String createBaseString(String method, String url, Map<String, String> authMap) 
	throws IOException {
		StringBuilder sb = new StringBuilder();
		// method
		sb.append(method.toUpperCase(Locale.ENGLISH));
		// url
		sb.append("&");
		sb.append(URLEncoder.encode(url, OAuthConst.ENCODING));
		// headers
		sb.append("&");
		
		StringBuilder hd = new StringBuilder();
		boolean isFirst = true;
		for (Map.Entry<String, String> mapEntry : authMap.entrySet()) {
			if (isFirst) {
				isFirst = false;
			} else {
				hd.append("&");
			}
			hd.append(URLEncoder.encode(mapEntry.getKey(), OAuthConst.ENCODING));
			hd.append("=");
			hd.append(URLEncoder.encode(mapEntry.getValue(), OAuthConst.ENCODING));
		}
		String encHeaders = URLEncoder.encode(hd.toString(), OAuthConst.ENCODING);
		sb.append(encHeaders);
		
		return sb.toString();
	}
	
	/**
	 * Twitter用認証ヘッダを生成.
	 *  Authorization: OAuth {認証情報} の形にして1個にまとめる。
	 * @param authMap 認証情報
	 * @return Twitter用認証ヘッダ
	 */
	private Map<String, String> createAuthorizationHeader(Map<String, String> authMap) 
	throws IOException {
		StringBuilder sb = new StringBuilder();
		boolean isFirst = true;
		
		for (Map.Entry<String, String> mapEntry : authMap.entrySet()) {
			if (isFirst) {
				isFirst = false;
			} else {
				sb.append(", ");
			}
			sb.append(mapEntry.getKey());
			sb.append("=\"");
			sb.append(URLEncoder.encode(mapEntry.getValue(), OAuthConst.ENCODING));
			sb.append("\"");
		}
		return OAuthUtil.getAuthorizationHeaders(sb.toString(), OAuthConst.HEADER_AUTHORIZATION_OAUTH);
	}
	
	/**
	 * create signature
	 * @param toSign base string
	 * @param keyString key string
	 * @return signature
	 */
	private String doSign(String toSign, String keyString) 
	throws IOException {
		final SecretKeySpec key = new SecretKeySpec(keyString.getBytes(OAuthConst.ENCODING), HMAC_SHA1);
		try {
			final Mac mac = Mac.getInstance(HMAC_SHA1);
			mac.init(key);
			final byte[] bytes = mac.doFinal(toSign.getBytes(OAuthConst.ENCODING));
			return Base64.encodeBase64String(bytes);
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IOException(e);
		}
	}
	
	/**
	 * アクセストークンリクエストのデータを生成
	 *   oauth_verifier=uw7NjWHT6OJ1MpJOXsHfNxoAhPKpgI8BlYDhxEjIBY
	 * @param oauthVerifier oath_verfier
	 * @return アクセストークンリクエストのデータ
	 */
	private byte[] getAccesstokenRequestData(String oauthVerifier) 
	throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append(OAuthConst.PARAM_OAUTH_VERIFIER);
		sb.append("=");
		sb.append(oauthVerifier);
		String data = sb.toString();
		return data.getBytes(OAuthConst.ENCODING);
	}
	
	/**
	 * OAuth処理をシステム管理サービスで行うかどうかを返す.
	 * @return OAuth処理をシステム管理サービスで行う場合true
	 */
	public boolean isRedirectSystem() {
		return true;
	}

}
