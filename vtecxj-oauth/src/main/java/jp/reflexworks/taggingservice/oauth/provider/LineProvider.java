package jp.reflexworks.taggingservice.oauth.provider;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.servlet.HttpStatus;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.oauth.OAuthConst;
import jp.reflexworks.taggingservice.oauth.OAuthInfo;
import jp.reflexworks.taggingservice.oauth.OAuthProvider;
import jp.reflexworks.taggingservice.oauth.OAuthResponseInfo;
import jp.reflexworks.taggingservice.oauth.OAuthUtil;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * OAuthプロバイダ : Line
 */
public class LineProvider implements OAuthProvider {
	
	/** 認証URL (GET) */
	private static final String AUTHORIZATION_URL = "https://access.line.me/oauth2/v2.1/authorize";
	
	/** アクセストークンURL (POST) */
	private static final String ACCESSTOKEN_URL = "https://api.line.me/oauth2/v2.1/token";
	/** アクセストークン取得メソッド */
	private static final String ACCESSTOKEN_METHOD = Constants.POST;
	
	/** ユーザ情報取得URL (GET) */
	/*
	  ★ Header
	   Authorization: Bearer {Access token}
	 */
	private static final String USER_URL = "https://api.line.me/v2/profile";
	/** ユーザ情報取得メソッド */
	private static final String USER_METHOD = Constants.GET;
	/** ユーザ情報 : sub (GUID) */
	private static final String GUID = "userId";
	/** ユーザ情報 : ニックネーム */
	private static final String NICKNAME = "displayName";
	
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
	 * @param req リクエスト
	 * @param resp レスポンス
	 */
	public void oauth(ReflexRequest req, ReflexResponse resp) 
	throws IOException, TaggingException {
		String serviceName = req.getServiceName();
		RequestInfo requestInfo = req.getRequestInfo();
		SystemContext systemContext = new SystemContext(serviceName, 
				requestInfo, req.getConnectionInfo());
		if (logger.isDebugEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + 
					"[oauth] start. providerName=" + getProviderName());
		}
		
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
		sb.append("response_type=code");
		sb.append("&");
		sb.append(OAuthConst.PARAM_CLIENT_ID);
		sb.append("=");
		sb.append(clientId);	// チャネルID
		sb.append("&");
		sb.append(OAuthConst.PARAM_REDIRECT_URI);
		sb.append("=");
		sb.append(URLEncoder.encode(redirectUrl, OAuthConst.ENCODING));
		sb.append("&");
		sb.append(OAuthConst.PARAM_STATE);
		sb.append("=");
		sb.append(secret);
		sb.append("&scope=profile");
		
		return sb.toString();
	}
	
	/**
	 * OAuthプロバイダからcallback.
	 * ユーザ識別情報を取得するところまで行う。
	 * @param req リクエスト
	 * @return OAuth認証で取得したユーザ情報
	 */
	public OAuthInfo callback(ReflexRequest req) 
	throws IOException, TaggingException {
		RequestInfo requestInfo = req.getRequestInfo();
		RequestParam param = (RequestParam)req.getRequestType();
		// errorパラメータが設定されている場合はエラー
		String error = param.getOption(OAuthConst.PARAM_ERROR);
		if (!StringUtils.isBlank(error)) {
			throw OAuthUtil.newAuthenticationException(getProviderName(), "error by line: " + error);
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
		
		// 自サービスの処理
		String serviceName = req.getServiceName();
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
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + 
					"[callback] accessToken response: " + accessTokenRespInfo);
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
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + 
					"[callback] user response: " + userRespInfo);
		}
		if (userRespInfo.status != HttpStatus.SC_OK) {
			throw OAuthUtil.newAuthenticationException(getProviderName(), "user response: " + userRespInfo);
		}
		Map<String, String> userMap = OAuthUtil.convertFromJson(getProviderName(), 
				userRespInfo.data);
		String guid = userMap.get(GUID);
		if (StringUtils.isBlank(guid)) {
			throw OAuthUtil.newAuthenticationException(getProviderName(), "You could not get OAuth guid.");
		}
		String nickname = userMap.get(NICKNAME);
		
		return new OAuthInfo(guid, null, nickname);
	}
	
	/**
	 * アクセストークン取得リクエストデータを取得
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
		String redirectUri = OAuthUtil.getRedirectUri(getProviderName(), serviceName);
		
		StringBuilder sb = new StringBuilder();
		sb.append("grant_type=authorization_code");
		sb.append("&");
		sb.append(OAuthConst.PARAM_CODE);
		sb.append("=");
		sb.append(code);
		sb.append("&");
		sb.append(OAuthConst.PARAM_REDIRECT_URI);
		sb.append("=");
		sb.append(URLEncoder.encode(redirectUri, OAuthConst.ENCODING));
		sb.append("&");
		sb.append(OAuthConst.PARAM_CLIENT_ID);
		sb.append("=");
		sb.append(URLEncoder.encode(clientId, OAuthConst.ENCODING));
		sb.append("&");
		sb.append(OAuthConst.PARAM_CLIENT_SECRET);
		sb.append("=");
		sb.append(URLEncoder.encode(clientSecret, OAuthConst.ENCODING));
		String dataStr = sb.toString();
		return dataStr.getBytes(OAuthConst.ENCODING);
	}

}
