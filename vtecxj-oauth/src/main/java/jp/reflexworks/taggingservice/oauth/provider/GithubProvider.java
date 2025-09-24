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
 * OAuthプロバイダ : GitHub
 */
public class GithubProvider implements OAuthProvider {
	
	/** 認証URL (GET) */
	private static final String AUTHORIZATION_URL = "https://github.com/login/oauth/authorize";
	
	/** アクセストークンURL (POST) */
	private static final String ACCESSTOKEN_URL = "https://github.com/login/oauth/access_token";
	/** アクセストークン取得メソッド */
	private static final String ACCESSTOKEN_METHOD = Constants.POST;
	
	/** ユーザ情報取得URL (GET) */
	/*
	  ★ Header
	   Authorization: Token {Access token}
	 */
	private static final String USER_URL = "https://api.github.com/user";
	/** ユーザ情報取得メソッド */
	private static final String USER_METHOD = Constants.GET;
	/** ユーザ情報 : id (GUID) */
	private static final String GUID = "id";
	/** ユーザ情報 : email */
	private static final String EMAIL = "email";
	/** ユーザ情報 : login */
	private static final String LOGIN = "login";
	
	
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
		String authorizationUrl = getAuthorizationUrl(secret, systemContext);
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
		String accesstokenUrl = getAccesstokenUrl(code, secret, systemContext);
		OAuthResponseInfo accessTokenRespInfo = OAuthUtil.request(accesstokenUrl, 
				ACCESSTOKEN_METHOD);
		if (logger.isDebugEnabled()) {
			logger.debug("[callback] accessToken response: " + accessTokenRespInfo);
		}
		if (accessTokenRespInfo.status != HttpStatus.SC_OK) {
			throw OAuthUtil.newAuthenticationException(getProviderName(), "OAuth accessToken response: " + accessTokenRespInfo);
		}
		
		// アクセストークンを取得
		// access_token=xxxxxxx&scope=&token_type=bearer の形
		Map<String, String> dataMap = OAuthUtil.convertParam(accessTokenRespInfo.data);
		String accessToken = dataMap.get(OAuthConst.PARAM_ACCESS_TOKEN);
		if (StringUtils.isBlank(accessToken)) {
			throw OAuthUtil.newAuthenticationException(getProviderName(), "You could not get OAuth access token.");
		}
		
		// ユーザ情報を取得
		String userUrl = getUserUrl();
		Map<String, String> userHeaders = OAuthUtil.getAuthorizationHeaders(accessToken, 
				OAuthConst.HEADER_AUTHORIZATION_TOKEN);
		OAuthResponseInfo userRespInfo = OAuthUtil.request(userUrl, USER_METHOD, userHeaders);
		if (logger.isDebugEnabled()) {
			logger.debug("[callback] user response: " + userRespInfo);
		}
		if (userRespInfo.status != HttpStatus.SC_OK) {
			throw OAuthUtil.newAuthenticationException(getProviderName(), "user response: " + userRespInfo);
		}
		
		// 取得データは以下の形
		// idがユーザ識別子
		// login: ログインユーザ名、email: メールアドレス(null。取得できなさそう。)
		//   {"login":"username","id":999999,"node_id":"XXXXXXXXX","avatar_url":"https://avatars1.githubusercontent.com/u/9999999?v=4","gravatar_id":"","url":"https://api.github.com/users/username","html_url":"https://github.com/username","followers_url":"https://api.github.com/users/username/followers","following_url":"https://api.github.com/users/username/following{/other_user}","gists_url":"https://api.github.com/users/username/gists{/gist_id}","starred_url":"https://api.github.com/users/username/starred{/owner}{/repo}","subscriptions_url":"https://api.github.com/users/username/subscriptions","organizations_url":"https://api.github.com/users/username/orgs","repos_url":"https://api.github.com/users/username/repos","events_url":"https://api.github.com/users/username/events{/privacy}","received_events_url":"https://api.github.com/users/username/received_events","type":"User","site_admin":false,"name":null,"company":null,"blog":"","location":null,"email":null,"hireable":null,"bio":null,"public_repos":1,"public_gists":0,"followers":0,"following":0,"created_at":"2013-04-08T02:45:23Z","updated_at":"2019-02-22T05:36:04Z"}
		
		Map<String, String> userMap = OAuthUtil.convertFromJson(getProviderName(), 
				userRespInfo.data);
		String guid = OAuthUtil.getValue(userMap.get(GUID));
		String email = OAuthUtil.getValue(userMap.get(EMAIL));
		String nickname = OAuthUtil.getValue(userMap.get(LOGIN));
		if (StringUtils.isBlank(guid)) {
			throw OAuthUtil.newAuthenticationException(getProviderName(), "You could not get OAuth guid.");
		}
		
		return new OAuthInfo(guid, email, nickname);
	}

	/**
	 * リダイレクトURLを生成.
	 * @param secret secret
	 * @param systemContext SystemContext
	 * @return リダイレクトURL
	 */
	private String getAuthorizationUrl(String secret, SystemContext systemContext) 
	throws IOException, TaggingException {
		String serviceName = systemContext.getServiceName();
		// client_id
		String clientId = OAuthUtil.getClientId(getProviderName(), serviceName);

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
		return sb.toString();
	}

	/**
	 * アクセストークン取得URLを生成.
	 * @param code code
	 * @param secret secret
	 * @param systemContext SystemContetx
	 * @return リダイレクトURL
	 */
	private String getAccesstokenUrl(String code, String secret, SystemContext systemContext) 
	throws IOException, TaggingException {
		String serviceName = systemContext.getServiceName();
		// client_id
		String clientId = OAuthUtil.getClientId(getProviderName(), serviceName);
		// client_secret
		String clientSecret = OAuthUtil.getClientSecret(getProviderName(), serviceName);
		
		StringBuilder sb = new StringBuilder();
		sb.append(ACCESSTOKEN_URL);
		sb.append("?");
		sb.append(OAuthConst.PARAM_CLIENT_ID);
		sb.append("=");
		sb.append(URLEncoder.encode(clientId, OAuthConst.ENCODING));
		sb.append("&");
		sb.append(OAuthConst.PARAM_STATE);
		sb.append("=");
		sb.append(URLEncoder.encode(secret, OAuthConst.ENCODING));
		sb.append("&");
		sb.append(OAuthConst.PARAM_CLIENT_SECRET);
		sb.append("=");
		sb.append(URLEncoder.encode(clientSecret, OAuthConst.ENCODING));
		sb.append("&");
		sb.append(OAuthConst.PARAM_CODE);
		sb.append("=");
		sb.append(URLEncoder.encode(code, OAuthConst.ENCODING));
		return sb.toString();
	}
	
	/**
	 * ユーザ取得URLを取得
	 * @return ユーザ取得URL
	 */
	private String getUserUrl() {
		StringBuilder sb = new StringBuilder();
		sb.append(USER_URL);
	
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
