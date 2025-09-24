package jp.reflexworks.taggingservice.oauth.provider;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Map;

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
import jp.sourceforge.reflex.util.StringUtils;

/**
 * OAuthプロバイダ : Yahoo
 */
public class YahooProvider implements OAuthProvider {
	
	/** 認証URL (GET or POST) */
	/*
	 * response_type=code
	 * &client_id={client_id}
	 * &redirect_uri={redirect_uri}
	 * &scope=openid
	 * &state={secret} (option)
	 * &nonce={nonce} (option)
	 */
	private static final String AUTHORIZATION_URL = "https://auth.login.yahoo.co.jp/yconnect/v2/authorization";
	
	/** アクセストークンURL (POST) */
	/*
	  ★ Header
	   Authorization: Basic {Client IDとClient Secretを":"（コロン）で連結し、Base64エンコードした値}
	  ★ Data
	   grant_type=authorization_code
	   client_id={client_id}
	   &client_secret={client_secret}
	   &redirect_uri={redirect_uri}
	   &code={code}	
	 */
	private static final String ACCESSTOKEN_URL = "https://auth.login.yahoo.co.jp/yconnect/v2/token";
	/** アクセストークン取得メソッド */
	private static final String ACCESSTOKEN_METHOD = Constants.POST;
	
	/** ユーザ情報取得URL (GET or POST) */
	/*
	  ★ Header
	   Authorization: Bearer {Access token}
	 */
	private static final String USER_URL = "https://userinfo.yahooapis.jp/yconnect/v2/attribute";
	/** ユーザ情報取得メソッド */
	private static final String USER_METHOD = Constants.GET;
	/** ユーザ情報 : id (GUID) */
	private static final String GUID = "sub";
	/** ユーザ情報 : email */
	//private static final String EMAIL = "email";
	
	
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
	 * OAuthプロバイダからcallback
	 * @param req リクエスト
	 * @return OAuth認証で取得したユーザ情報
	 */
	public OAuthInfo callback(ReflexRequest req) 
	throws IOException, TaggingException {
		String serviceName = req.getServiceName();
		RequestParam param = (RequestParam)req.getRequestType();
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
		Map<String, String> accessTokenHeaders = OAuthUtil.getAuthorizationHeaders(
				getAuthorizationBase(systemContext), OAuthConst.HEADER_AUTHORIZATION_BASIC);
		byte[] accessTokenData = getAccessTokenData(req, code, systemContext.getServiceName());
		OAuthResponseInfo accessTokenRespInfo = OAuthUtil.request(ACCESSTOKEN_URL, 
				ACCESSTOKEN_METHOD, accessTokenHeaders, accessTokenData);
		if (logger.isDebugEnabled()) {
			logger.debug("[callback] accessToken response: " + accessTokenRespInfo);
		}
		if (accessTokenRespInfo.status != HttpStatus.SC_OK) {
			throw OAuthUtil.newAuthenticationException(getProviderName(), "OAuth accessToken response: " + accessTokenRespInfo);
		}
		
		// アクセストークンのレスポンスデータはJSON形式
		/*
			{
			  "access_token": "SlAV32hkKG",
			  "token_type": "Bearer",
			  "refresh_token": "8xLOxBtZp8",
			  "expires_in": 3600,
			  "id_token": "eyJhbXciOiXSUzI1XiIsImtpXCI6IjFlOWdkazcifQ.ewogImlzcyI6ICJodHRwOi8vc2VydmVyLmV4YW1wbGUuY29tIiwKICJzdWIiOiAiMjQ4Mjg5NzYxMDAxIiwKICJhdWQiOiAiczZCaGRSa3F0MyIsCiAibm9uY2UiOiAibi0wUzZfV3pBMk1qIiwKICJleHAiOiAxMzExMjgxOTcwLAogImlhdCI6IDEzMTEyODA5NzAKfQ.ggW8hZ1EuVLuxNuuIJKX_V8a_OMXzR0EHR9R6jgdqrOOF4daGU96Sr_P6qJp6IcmD3HP99Obi1PRs-cwh3LO-p146waJ8IhehcwL7F09JdijmBqkvPeB2T9CJNqeGpe-gccMg4vfKjkM8FcGvnzZUN4_KSP0aAp1tOJ1zZwgjxqGByKHiOtX7TpdQyHE5lcMiKPXfEIQILVq0pc_E2DzL7emopWoaoZTF_m0_N0YzFC6g6EJbOEoRoSK5hoDalrcvRYLSrQAZZKflyuVCyixEoV9GfNQC3_osjzw2PAithfubEEBLuVVk4XUVrWOLrLl0nx7RkKU8NXNHq-rvKMzqg"			} 
		 */
		Map<String, String> dataMap = OAuthUtil.convertFromJson(getProviderName(), accessTokenRespInfo.data);
		String accessToken = dataMap.get(OAuthConst.PARAM_ACCESS_TOKEN);
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
		
		// 取得データは以下の形
		// subがユーザ識別子
		// {"sub":"YQPKANCDXBN7BVYE27BWRXXXXX"}
		
		Map<String, String> userMap = OAuthUtil.convertFromJson(getProviderName(), 
				userRespInfo.data);
		String guid = OAuthUtil.getValue(userMap.get(GUID));
		String email = null;
		String nickname = null;
		if (StringUtils.isBlank(guid)) {
			throw OAuthUtil.newAuthenticationException(getProviderName(), "You could not get OAuth guid.");
		}
		
		return new OAuthInfo(guid, email, nickname);
	}

	/**
	 * リダイレクトURLを生成.
	 * @param req リクエスト
	 * @param secret secret
	 * @param systemContext SystemContext
	 * @return リダイレクトURL
	 */
	private String getAuthorizationUrl(ReflexRequest req, String secret, SystemContext systemContext) 
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
		sb.append(URLEncoder.encode(clientId, OAuthConst.ENCODING));
		sb.append("&");
		sb.append(OAuthConst.PARAM_STATE);
		sb.append("=");
		sb.append(URLEncoder.encode(secret, OAuthConst.ENCODING));
		sb.append("&");
		sb.append(OAuthConst.PARAM_REDIRECT_URI);
		sb.append("=");
		sb.append(URLEncoder.encode(redirectUrl, OAuthConst.ENCODING));
		sb.append("&response_type=code");
		sb.append("&scope=openid");
		
		return sb.toString();
	}
	
	/**
	 * Authorization Base値を取得.
	 * Client IDとClient Secretを":"（コロン）で連結し、Base64エンコードを行った値
	 * @return Authorization Base値
	 */
	private String getAuthorizationBase(SystemContext systemContext) 
	throws IOException, TaggingException {
		String serviceName = systemContext.getServiceName();
		// client_id
		String clientId = OAuthUtil.getClientId(getProviderName(), serviceName);
		// client_secret
		String clientSecret = OAuthUtil.getClientSecret(getProviderName(), serviceName);
		String str = clientId + ":" + clientSecret;
		return Base64.encodeBase64String(str.getBytes(OAuthConst.ENCODING));
	}
	
	/**
	 * アクセストークンURLを取得
	 *   grant_type=authorization_code
	 *   &redirect_uri={redirect_uri}
	 *   &code={code}	
	 * @param req リクエスト
	 * @param code code
	 * @param serviceName サービス名
	 * @return アクセストークンURL
	 */
	private byte[] getAccessTokenData(ReflexRequest req, String code,
			String serviceName) 
	throws IOException, TaggingException {
		// redirect_url
		String redirectUrl = OAuthUtil.getOAuthRedirectUrl(req, getProviderName(), serviceName);
		
		StringBuilder sb = new StringBuilder();
		sb.append(OAuthConst.PARAM_CODE);
		sb.append("=");
		sb.append(URLEncoder.encode(code, OAuthConst.ENCODING));
		sb.append("&");
		sb.append(OAuthConst.PARAM_REDIRECT_URI);
		sb.append("=");
		sb.append(URLEncoder.encode(redirectUrl, OAuthConst.ENCODING));
		sb.append("&grant_type=authorization_code");
		
		String accessTokenDataStr = sb.toString();
		return accessTokenDataStr.getBytes(OAuthConst.ENCODING);
	}
	
	/**
	 * OAuth処理をシステム管理サービスで行うかどうかを返す.
	 * @return OAuth処理をシステム管理サービスで行う場合true
	 */
	public boolean isRedirectSystem() {
		return true;
	}

}
