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
 * OAuthプロバイダ : Facebook
 */
public class FacebookProvider implements OAuthProvider {
	
	/** 認証URL (GET) */
	/*
	  client_id={app-id}
	  &redirect_uri={redirect-uri}
	  &state={state-param} 
	 */
	private static final String AUTHORIZATION_URL = "https://www.facebook.com/v3.2/dialog/oauth";
	
	/** アクセストークンURL (GET) */
	/*
	   client_id={app-id}
	   &redirect_uri={redirect-uri}
	   &client_secret={app-secret}
	   &code={code-parameter}	
	 */
	private static final String ACCESSTOKEN_URL = "https://graph.facebook.com/v3.2/oauth/access_token";
	/** アクセストークン取得メソッド */
	private static final String ACCESSTOKEN_METHOD = Constants.GET;
	
	/** ユーザ情報取得URL (GET) */
	/*
	   input_token={input-token}
	 */
	private static final String USER_URL = "https://graph.facebook.com/v3.2/me";
	/** ユーザ情報取得メソッド */
	private static final String USER_METHOD = Constants.GET;

	/** ユーザ情報 : id (GUID) */
	private static final String GUID = "id";
	/** ユーザ情報 : email */
	private static final String EMAIL = "email";
	/** ユーザ情報 : name */
	private static final String NAME = "name";
	
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
		String accesstokenUrl = getAccesstokenUrl(req, code, secret, systemContext);
		OAuthResponseInfo accessTokenRespInfo = OAuthUtil.request(accesstokenUrl, 
				ACCESSTOKEN_METHOD);
		if (logger.isDebugEnabled()) {
			logger.debug("[callback] accessToken response: " + accessTokenRespInfo);
		}
		if (accessTokenRespInfo.status != HttpStatus.SC_OK) {
			throw OAuthUtil.newAuthenticationException(getProviderName(), "OAuth accessToken response: " + accessTokenRespInfo);
		}
		
		// アクセストークンを取得
		// レスポンスデータはJSON形式
		/*
			{
			  "access_token": {access-token}, 
			  "token_type": {type},
			  "expires_in":  {seconds-til-expiration}
			} 
		 */
		Map<String, String> dataMap = OAuthUtil.convertFromJson(getProviderName(), accessTokenRespInfo.data);
		String accessToken = dataMap.get(OAuthConst.PARAM_ACCESS_TOKEN);
		if (StringUtils.isBlank(accessToken)) {
			throw OAuthUtil.newAuthenticationException(getProviderName(), "You could not get OAuth access token.");
		}
		
		// ユーザ情報を取得
		String userUrl = getUserUrl(accessToken);
		OAuthResponseInfo userRespInfo = OAuthUtil.request(userUrl, USER_METHOD);
		if (logger.isDebugEnabled()) {
			logger.debug("[callback] user response: " + userRespInfo);
		}
		if (userRespInfo.status != HttpStatus.SC_OK) {
			throw OAuthUtil.newAuthenticationException(getProviderName(), "user response: " + userRespInfo);
		}
		
		// 取得データはJSON形式
		// idがユーザ識別子
		// name: ログインユーザ名、email: メールアドレス(null。取得できなさそう。)
		//   {"id":"123456789012345","name":"Vtecx Test"}
		
		Map<String, String> userMap = OAuthUtil.convertFromJson(getProviderName(), 
				userRespInfo.data);
		String guid = OAuthUtil.getValue(userMap.get(GUID));
		String email = OAuthUtil.getValue(userMap.get(EMAIL));
		String nickname = OAuthUtil.getValue(userMap.get(NAME));
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
		sb.append(secret);
		sb.append("&");
		sb.append(OAuthConst.PARAM_REDIRECT_URI);
		sb.append("=");
		sb.append(URLEncoder.encode(redirectUrl, OAuthConst.ENCODING));
		return sb.toString();
	}

	/**
	 * アクセストークン取得URLを生成.
	 * @param req リクエスト
	 * @param code code
	 * @param secret secret
	 * @param systemContext SystemContetx
	 * @return アクセストークン取得URL
	 */
	private String getAccesstokenUrl(ReflexRequest req, String code, String secret, 
			SystemContext systemContext) 
	throws IOException, TaggingException {
		String serviceName = systemContext.getServiceName();
		// client_id
		String clientId = OAuthUtil.getClientId(getProviderName(), serviceName);
		// client_secret
		String clientSecret = OAuthUtil.getClientSecret(getProviderName(), serviceName);
		// redirect_url
		String redirectUrl = OAuthUtil.getRedirectUri(getProviderName(), serviceName);
		
		StringBuilder sb = new StringBuilder();
		sb.append(ACCESSTOKEN_URL);
		sb.append("?");
		sb.append(OAuthConst.PARAM_CLIENT_ID);
		sb.append("=");
		sb.append(URLEncoder.encode(clientId, OAuthConst.ENCODING));
		sb.append("&");
		sb.append(OAuthConst.PARAM_STATE);
		sb.append("=");
		sb.append(secret);
		sb.append("&");
		sb.append(OAuthConst.PARAM_CLIENT_SECRET);
		sb.append("=");
		sb.append(URLEncoder.encode(clientSecret, OAuthConst.ENCODING));
		sb.append("&");
		sb.append(OAuthConst.PARAM_CODE);
		sb.append("=");
		sb.append(code);
		sb.append("&");
		sb.append(OAuthConst.PARAM_REDIRECT_URI);
		sb.append("=");
		sb.append(URLEncoder.encode(redirectUrl, OAuthConst.ENCODING));
		return sb.toString();
	}
	
	/**
	 * ユーザ取得URLを取得
	 * @param accessToken アクセストークン
	 * @return ユーザ取得URL
	 */
	private String getUserUrl(String accessToken) 
	throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append(USER_URL);
		sb.append("?");
		sb.append(OAuthConst.PARAM_ACCESS_TOKEN);
		sb.append("=");
		sb.append(URLEncoder.encode(accessToken, OAuthConst.ENCODING));
		sb.append("&fields=id,name,email");
		return sb.toString();
	}
	
	/**
	 * OAuth処理をシステム管理サービスで行うかどうかを返す.
	 * @return OAuth処理をシステム管理サービスで行う場合true
	 */
	public boolean isRedirectSystem() {
		return true;
	}

}
