package jp.reflexworks.taggingservice.oauth.provider;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Map;

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
import jp.sourceforge.reflex.util.StringUtils;

/**
 * OAuthプロバイダ : Google
 */
public class GoogleProvider implements OAuthProvider {
	
	/** 認証URL (GET or POST) */
	/*
	 * client_id
	 * redirect_uri
	 * scope
	 * access_type (= online (default) or offline)
	 * state (= profile, email, openid ...)
	 * response_type (= code)
	 */
	private static final String AUTHORIZATION_URL = "https://accounts.google.com/o/oauth2/v2/auth";
	
	/** アクセストークンURL (POST) */
	/* 
	  ★ Data
	    code	The authorization code returned from the initial request.
	    client_id	The client ID obtained from the API Console.
	    client_secret	The client secret obtained from the API Console.
	    redirect_uri	One of the redirect URIs listed for your project in the API Console.
	    grant_type	As defined in the OAuth 2.0 specification, this field must contain a value of authorization_code.
	 */
	private static final String ACCESSTOKEN_URL = "https://www.googleapis.com/oauth2/v4/token";
	/** アクセストークン取得メソッド */
	private static final String ACCESSTOKEN_METHOD = Constants.POST;
	
	/** ユーザ情報取得URL (GET) */
	/*
	  ★ Header
	   Authorization: Bearer {Access token}
	 */
	private static final String USER_URL = "https://www.googleapis.com/oauth2/v3/userinfo";
	/** ユーザ情報取得メソッド */
	private static final String USER_METHOD = Constants.GET;
	/** ユーザ情報 : sub (GUID) */
	private static final String GUID = "sub";
	
	
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
		
		// secretを生成しRedisに登録
		String secret = OAuthUtil.createSecret(getProviderName(), systemContext);
		
		// リダイレクト
		String authorizationUrl = getAuthorizationUrl(req, secret, systemContext);
		resp.sendRedirect(authorizationUrl);
	}
	
	/**
	 * リダイレクトURLを生成.
	 * @param req リクエスト
	 * @param secret secret
	 * @param systemContext SystemContext
	 * @return リダイレクトURL
	 */
	private String getAuthorizationUrl(ReflexRequest req, String secret,
			SystemContext systemContext) 
	throws IOException, TaggingException {
		String serviceName = systemContext.getServiceName();
		// client_id
		String clientId = OAuthUtil.getClientId(getProviderName(), serviceName);
		// redirect_url
		String redirectUrl = OAuthUtil.getRedirectUri(getProviderName(), serviceName);
		
		StringBuilder sb = new StringBuilder();
		sb.append(AUTHORIZATION_URL);
		sb.append("?");
		sb.append(OAuthConst.PARAM_CLIENT_ID);
		sb.append("=");
		sb.append(clientId);
		sb.append("&");
		sb.append(OAuthConst.PARAM_STATE);
		sb.append("=");
		sb.append(secret);
		sb.append("&");
		sb.append(OAuthConst.PARAM_REDIRECT_URI);
		sb.append("=");
		sb.append(URLEncoder.encode(redirectUrl, OAuthConst.ENCODING));
		sb.append("&access_type=online");
		sb.append("&scope=profile");
		sb.append("&response_type=code");
		
		return sb.toString();
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
		// errorパラメータが設定されている場合はエラー
		String error = param.getOption(OAuthConst.PARAM_ERROR);
		if (!StringUtils.isBlank(error)) {
			throw OAuthUtil.newAuthenticationException(getProviderName(), "error by google: " + error);
		}
		
		// codeとstateを取得
		String code = param.getOption(OAuthConst.PARAM_CODE);
		if (StringUtils.isBlank(code)) {
			throw OAuthUtil.newAuthenticationException(getProviderName(), "OAuth code is not specified.");
		}
		String secret = param.getOption(OAuthConst.PARAM_STATE);
		if (StringUtils.isBlank(secret)) {
			throw OAuthUtil.newAuthenticationException(getProviderName(), "OAuth state is not specified.");
		}
		
		SystemContext systemContext = new SystemContext(serviceName, 
				req.getRequestInfo(), req.getConnectionInfo());
		
		// Redisからstateまたはリクエストトークンをキーにサービス名を取得(存在しなければエラー)
		String targetService = OAuthUtil.getServiceNameBySecret(systemContext, getProviderName(),
				secret);
		if (StringUtils.isBlank(targetService)) {
			throw OAuthUtil.newAuthenticationException(getProviderName(), "The OAuth secret is not registed. " + secret);
		}
		
		// アクセストークンを取得
		byte[] accessTokenData = getAccessTokenData(req, code, systemContext);
		OAuthResponseInfo accessTokenRespInfo = OAuthUtil.request(ACCESSTOKEN_URL, 
				ACCESSTOKEN_METHOD, null, accessTokenData);
		if (logger.isDebugEnabled()) {
			logger.debug("[callback] accessToken response: " + accessTokenRespInfo);
		}
		if (accessTokenRespInfo.status != HttpStatus.SC_OK) {
			throw OAuthUtil.newAuthenticationException(getProviderName(), "OAuth accessToken response: " + accessTokenRespInfo);
		}
		Map<String, String> accessTokenDataMap = OAuthUtil.convertFromJson(getProviderName(), 
				accessTokenRespInfo.data);
		String accessToken = accessTokenDataMap.get(OAuthConst.PARAM_ACCESS_TOKEN);
		if (StringUtils.isBlank(accessToken)) {
			throw OAuthUtil.newAuthenticationException(getProviderName(), "You could not get OAuth access token.");
		}
		
		// ユーザ情報を取得
		Map<String, String> userHeaders = OAuthUtil.getAuthorizationHeaders(accessToken, 
				OAuthConst.HEADER_AUTHORIZATION_BEARER);
		OAuthResponseInfo userRespInfo = OAuthUtil.request(USER_URL, USER_METHOD, userHeaders);
		if (logger.isDebugEnabled()) {
			logger.debug("[callback] user response: " + userRespInfo);
		}
		if (userRespInfo.status != HttpStatus.SC_OK) {
			throw OAuthUtil.newAuthenticationException(getProviderName(), "user response: " + userRespInfo);
		}
		Map<String, String> userMap = OAuthUtil.convertFromJson(getProviderName(), 
				userRespInfo.data);
		String guid = userMap.get(GUID);
		String nickname = null;
		if (StringUtils.isBlank(guid)) {
			throw OAuthUtil.newAuthenticationException(getProviderName(), "You could not get OAuth guid.");
		}
		
		return new OAuthInfo(guid, null, nickname);
	}
	
	/**
	 * アクセストークン取得リクエストデータを取得
	 *    code	The authorization code returned from the initial request.
	 *    client_id	The client ID obtained from the API Console.
	 *    client_secret	The client secret obtained from the API Console.
	 *    redirect_uri	One of the redirect URIs listed for your project in the API Console.
	 *    grant_type	As defined in the OAuth 2.0 specification, this field must contain a value of authorization_code.
	 * @param secret ランダム値
	 * @param code code
	 * @param systemContext SystemContext
	 * @return アクセストークンURL
	 */
	private byte[] getAccessTokenData(ReflexRequest req, String code, SystemContext systemContext) 
	throws IOException, TaggingException {
		String serviceName = systemContext.getServiceName();
		String clientId = OAuthUtil.getClientId(getProviderName(), serviceName);
		String clientSecret = OAuthUtil.getClientSecret(getProviderName(), serviceName);
		String redirectUrl = OAuthUtil.getRedirectUri(getProviderName(), serviceName);
		
		StringBuilder sb = new StringBuilder();
		sb.append(OAuthConst.PARAM_CODE);
		sb.append("=");
		sb.append(URLEncoder.encode(code, OAuthConst.ENCODING));
		sb.append("&");
		sb.append(OAuthConst.PARAM_CLIENT_ID);
		sb.append("=");
		sb.append(URLEncoder.encode(clientId, OAuthConst.ENCODING));
		sb.append("&");
		sb.append(OAuthConst.PARAM_CLIENT_SECRET);
		sb.append("=");
		sb.append(URLEncoder.encode(clientSecret, OAuthConst.ENCODING));
		sb.append("&");
		sb.append(OAuthConst.PARAM_REDIRECT_URI);
		sb.append("=");
		sb.append(URLEncoder.encode(redirectUrl, OAuthConst.ENCODING));
		sb.append("&grant_type=authorization_code");
		String dataStr = sb.toString();
		return dataStr.getBytes(OAuthConst.ENCODING);
	}
	
	/**
	 * OAuth処理をシステム管理サービスで行うかどうかを返す.
	 * @return OAuth処理をシステム管理サービスで行う場合true
	 */
	public boolean isRedirectSystem() {
		return true;
	}

}
