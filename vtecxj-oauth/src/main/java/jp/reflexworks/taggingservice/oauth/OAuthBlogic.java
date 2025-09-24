package jp.reflexworks.taggingservice.oauth;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.entry.Link;
import jp.reflexworks.servlet.HttpStatus;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.blogic.LoginLogoutBlogic;
import jp.reflexworks.taggingservice.blogic.SecurityBlogic;
import jp.reflexworks.taggingservice.blogic.SessionBlogic;
import jp.reflexworks.taggingservice.blogic.UserBlogic;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.AuthenticationException;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.reflexworks.taggingservice.exception.NoExistingEntryException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.DatastoreManager;
import jp.reflexworks.taggingservice.plugin.RequestResponseManager;
import jp.reflexworks.taggingservice.plugin.UserManager;
import jp.reflexworks.taggingservice.provider.ProviderUtil;
import jp.reflexworks.taggingservice.servlet.TaggingServletUtil;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.MessageUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * OAuth ビジネスロジック.
 */
public class OAuthBlogic {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * OAuthプロバイダへリダイレクト処理.
	 * 一般サービスの場合はシステム管理サービスへリダイレクトする。
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param oauthProvider OAuthプロバイダ用ビジネスロジック
	 */
	void oauth(ReflexRequest req, ReflexResponse resp,
			OAuthProvider oauthProvider)
	throws IOException, TaggingException {
		// OAuth設定があるかどうかチェック
		checkSettings(oauthProvider, req.getServiceName());
		// プロバイダのOAuth認可
		oauthProvider.oauth(req, resp);
	}

	/**
	 * OAuthプロバイダから認可後にリダイレクトされた時に呼ばれる処理.
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param oauthProvider OAuthプロバイダ用ビジネスロジック
	 */
	void callback(ReflexRequest req, ReflexResponse resp,
			OAuthProvider oauthProvider)
	throws IOException, TaggingException {
		// OAuth設定があるかどうかチェック
		checkSettings(oauthProvider, req.getServiceName());

		// コールバック処理
		// OAuthプロバイダからユーザ識別情報を取得する。
		OAuthInfo oauthInfo = oauthProvider.callback(req);
		String provider = oauthProvider.getProviderName();
		SystemContext systemContext = new SystemContext(req.getServiceName(), 
				req.getRequestInfo(), req.getConnectionInfo());
		// TaggingServiceユーザとの紐付け
		String uid = link(req, resp, provider, oauthInfo, systemContext);
		// ログイン
		login(req, resp, provider, uid, systemContext);
	}
	
	/**
	 * TaggingServiceユーザとの紐付けを行う.
	 * 未登録の場合はユーザ登録。
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param provider OAuthプロバイダ名
	 * @param oauthInfo ユーザ識別情報
	 * @param systemContext SystemContext
	 * @return UID
	 */
	private String link(ReflexRequest req, ReflexResponse resp,
			String provider, OAuthInfo oauthInfo, SystemContext systemContext) 
	throws IOException, TaggingException {
		String serviceName = req.getServiceName();
		RequestInfo requestInfo = req.getRequestInfo();
		
		UserBlogic userBlogic = new UserBlogic();

		// ソーシャルアカウントエントリーを取得
		// /_oauth/{provider}/{ソーシャルアカウント}
		String oauthUserId = oauthInfo.getOAuthId();
		String socialAccountUri = OAuthUtil.getSocialAccountUri(provider, oauthUserId);
		EntryBase socialAccountEntry = systemContext.getEntry(socialAccountUri);
		String uid = OAuthUtil.getUidBySocialAccountEntry(provider, socialAccountEntry);
		boolean isActivated = false;
		if (!StringUtils.isBlank(uid)) {
			// ユーザステータスのチェック
			EntryBase userTopEntry = userBlogic.getUserTopEntryByUid(uid, systemContext);
			String userStatus = userBlogic.getUserStatusByUserTopEntry(userTopEntry);
			if (logger.isDebugEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[link] UID=");
				sb.append(uid);
				sb.append(", uri=");
				sb.append(socialAccountEntry.getMyUri());
				sb.append(", userstatus=");
				sb.append(userStatus);
				logger.debug(sb.toString());
			}
			if (Constants.USERSTATUS_ACTIVATED.equals(userStatus)) {
				isActivated = true;
			} else if (Constants.USERSTATUS_REVOKED.equals(userStatus)) {
				throw OAuthUtil.newAuthenticationException(provider, "User status is revoked.");
			}
		}
		if (!isActivated) {
			// ユーザ登録
			String account = OAuthUtil.getTaggingAccount(provider, oauthUserId);
			String nickname = oauthInfo.getNickname();
			FeedBase additionalFeed = getSocialAccountFeed(provider, oauthUserId, serviceName);
			if (logger.isDebugEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[link] adduser by oauth start. social account=");
				sb.append(account);
				sb.append(", nickname=");
				sb.append(nickname);
				logger.debug(sb.toString());
			}
			EntryBase userTopEntry = userBlogic.adduserByLink(account, nickname, 
					additionalFeed, systemContext);
			if (userTopEntry != null) {
				uid = userBlogic.getUidByUri(userTopEntry.getMyUri());
			}
			if (logger.isDebugEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[link] adduser by oauth end. social account=");
				sb.append(account);
				sb.append(", uid=");
				sb.append(uid);
				logger.debug(sb.toString());
			}
		}
		return uid;
	}

	/**
	 * ログイン処理.
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param provider プロバイダ
	 * @param uid UID
	 * @param systemContext SystemContext
	 */
	private void login(ReflexRequest req, ReflexResponse resp, String provider,
			String uid, SystemContext systemContext)
	throws IOException, TaggingException {
		String serviceName = req.getServiceName();
		RequestInfo requestInfo = systemContext.getRequestInfo();
		ConnectionInfo connectionInfo = systemContext.getConnectionInfo();

		// IPアドレスのチェック(UIDをキーとする)(ブラックリストチェック)
		SecurityBlogic securityBlogic = new SecurityBlogic();
		securityBlogic.checkAuthFailureCount(
				uid, req, systemContext);

		// アカウントを取得
		UserManager userManager = TaggingEnvUtil.getUserManager();
		String account = userManager.getAccountByUid(uid, systemContext);

		// セッション生成
		String authType = OAuthUtil.getAuthType(provider);
		SessionBlogic sessionBlogic = new SessionBlogic();
		ReflexAuthentication auth = sessionBlogic.createSession(account, uid, authType,
				serviceName, requestInfo, connectionInfo);

		// 認証情報をリクエストオブジェクトに紐付ける
		RequestResponseManager reqRespManager = TaggingEnvUtil.getRequestResponseManager();
		reqRespManager.afterAuthenticate(req, resp, auth);

		// ログイン処理
		// loginメソッド内でログイン履歴が出力される。
		LoginLogoutBlogic loginLogoutBlogic = new LoginLogoutBlogic();
		FeedBase retFeed = loginLogoutBlogic.login(req, resp);
		if (retFeed != null) {
			TaggingServletUtil.doResponse(req, resp, retFeed, HttpStatus.SC_OK);
		}
	}

	/**
	 * セッションの認証情報を取得.
	 * @param req リクエスト
	 * @return セッションの認証情報
	 */
	private ReflexAuthentication getAuthFromSession(ReflexRequest req)
	throws IOException, TaggingException {
		SessionBlogic sessionBlogic = new SessionBlogic();
		// セッションIDを取得
		String sessionId = sessionBlogic.getSessionIdFromRequest(req);
		if (StringUtils.isBlank(sessionId)) {
			return null;
		}

		String serviceName = req.getServiceName();
		RequestInfo requestInfo = req.getRequestInfo();
		ConnectionInfo connectionInfo = req.getConnectionInfo();
		SystemContext systemContext = new SystemContext(serviceName, requestInfo,
				connectionInfo);
		String uid = sessionBlogic.getUidFromSession(systemContext, sessionId);
		if (StringUtils.isBlank(uid)) {
			return null;
		}
		// 認証に使用するユーザ情報のキャッシュ読み込み
		DatastoreManager datastoreManager = TaggingEnvUtil.getDatastoreManager();
		datastoreManager.initMainThreadUser(uid, serviceName, requestInfo, connectionInfo);
		// 認証情報を取得
		return sessionBlogic.getAuthFromSession(uid, systemContext, sessionId);
	}

	/**
	 * 紐付け削除処理.
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param provider プロバイダ名
	 */
	void deleteOAuthid(ReflexRequest req, ReflexResponse resp, String provider)
	throws IOException, TaggingException {
		// ログイン中かどうかのチェック
		ReflexAuthentication sessionAuth = getAuthFromSession(req);
		if (sessionAuth == null) {
			throw OAuthUtil.newAuthenticationException(provider, "[deleteOAuthid] session auth is not exist.");
		}
		String uid = sessionAuth.getUid();
		if (StringUtils.isBlank(uid)) {
			throw OAuthUtil.newAuthenticationException(provider, "[deleteOAuthid] The uid of session auth is not exist.");
		}

		String serviceName = req.getServiceName();
		RequestInfo requestInfo = req.getRequestInfo();
		ConnectionInfo connectionInfo = req.getConnectionInfo();
		SystemContext systemContext = new SystemContext(serviceName,
				requestInfo, connectionInfo);

		// ソーシャルアカウント登録チェック
		EntryBase socialAccountEntry = OAuthUtil.getSocialAccountEntryByUid(provider,
				sessionAuth.getUid(), systemContext);
		if (socialAccountEntry == null) {
			throw new NoExistingEntryException();
		}

		// 削除
		systemContext.delete(socialAccountEntry.getMyUri());

		// レスポンス
		FeedBase retFeed = MessageUtil.createMessageFeed(OAuthConst.MSG_DELETE_OAUTHID, serviceName);
		TaggingServletUtil.doResponse(req, resp, retFeed, HttpStatus.SC_OK);
	}
	
	/**
	 * ユーザ登録時の追加登録エントリーリストを取得
	 * @param provider OAuthプロバイダ名
	 * @param oauthUserId ソーシャルアカウント
	 * @param serviceName サービス名
	 * @return ユーザ登録時の追加登録エントリーリスト
	 */
	private FeedBase getSocialAccountFeed(String provider, String oauthUserId,
			String serviceName) {
		List<EntryBase> entries = new ArrayList<>();
		String socialAccountAliasParent = OAuthUtil.getSocialAccountAliasParent(OAuthConst.MARK_UID);
		EntryBase parentEntry = TaggingEntryUtil.createEntry(serviceName);
		parentEntry.setMyUri(socialAccountAliasParent);
		entries.add(parentEntry);
		String socialAccountUri = OAuthUtil.getSocialAccountUri(provider, oauthUserId);
		String socialAccountAlias = OAuthUtil.getSocialAccountAlias(provider, 
				OAuthConst.MARK_UID);
		EntryBase socialAccountEntry = TaggingEntryUtil.createEntry(serviceName);
		socialAccountEntry.setMyUri(socialAccountUri);
		socialAccountEntry.addAlternate(socialAccountAlias);
		entries.add(socialAccountEntry);
		
		FeedBase retFeed = TaggingEntryUtil.createFeed(serviceName);
		retFeed.entry = entries;
		return retFeed;
	}
	
	/**
	 * OAuth設定が行われているかどうかチェック.
	 * @param oauthProvider OAuthプロバイダ
	 * @param serviceName サービス名
	 */
	private void checkSettings(OAuthProvider oauthProvider, String serviceName) 
	throws InvalidServiceSettingException {
		String provider = oauthProvider.getProviderName();
		String clientId = OAuthUtil.getClientId(provider, serviceName);
		String clientSecret = OAuthUtil.getClientSecret(provider, serviceName);
		String clientRedirectUri = OAuthUtil.getRedirectUri(provider, serviceName);
		try {
			CheckUtil.checkNotNull(clientId, "client_id setting");
			CheckUtil.checkNotNull(clientSecret, "client_secret setting");
			CheckUtil.checkNotNull(clientRedirectUri, "redirect_uri setting");
		} catch (IllegalParameterException e) {
			// サービス設定の問題なので例外を変更する。
			throw new InvalidServiceSettingException(e.getMessage());
		}
	}
	
	/**
	 * ソーシャルアカウントとTaggingServiceアカウントの連携、ログイン.
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param provider OAuthプロバイダ名
	 * @param feed ソーシャルアカウント・ニックネーム
	 * @param state state
	 */
	void linkLogin(ReflexRequest req, ReflexResponse resp,
			String provider, FeedBase feed, String state) 
	throws IOException, TaggingException {
		String serviceName = req.getServiceName();
		// APIKeyチェック
		ProviderUtil.checkAPIKey(req);
		// stateチェック
		SystemContext systemContext = new SystemContext(serviceName, 
				req.getRequestInfo(), req.getConnectionInfo());
		checkStateProc(provider, state, systemContext);
		// 入力チェック
		CheckUtil.checkFeed(feed, false);
		EntryBase entry = feed.entry.get(0);
		String account = entry.title;
		String nickname = entry.subtitle;
		CheckUtil.checkNotNull(account, "social account");

		OAuthInfo oauthInfo = new OAuthInfo(account, null, nickname);

		// TaggingServiceユーザとの紐付け
		String uid = link(req, resp, provider, oauthInfo, systemContext);
		// ログイン
		login(req, resp, provider, uid, systemContext);
	}
	
	/**
	 * stateを生成し、キャッシュに登録する.
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param provider OAuthプロバイダ名
	 * @return state.
	 *         Feedのtitleにstate、subtitleにcliend_id、link rel="self"のhrefにredirect_uriをセット
	 */
	FeedBase createState(ReflexRequest req, ReflexResponse resp, String provider) 
	throws IOException, TaggingException {
		String serviceName = req.getServiceName();
		RequestInfo requestInfo = req.getRequestInfo();
		// APIKeyチェック
		ProviderUtil.checkAPIKey(req);
		// stateを生成しキャッシュに登録
		SystemContext systemContext = new SystemContext(serviceName, 
				requestInfo, req.getConnectionInfo());
		String state = OAuthUtil.createSecret(provider, systemContext);
		return createReturnState(provider, state, false, serviceName, requestInfo);
	}
	
	/**
	 * stateが正しいかどうかチェック
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param provider OAuthプロバイダ名
	 * @param state state
	 * @return stateが正しい場合以下を戻す。
	 *         Feedのtitleにstate、subtitleにcliend_id、rightsにclient_secret、link rel="self"のhrefにredirect_uriをセット
	 * @throws AuthenticationException stateが不正な場合
	 */
	FeedBase checkState(ReflexRequest req, ReflexResponse resp, String provider,
			String state) 
	throws IOException, TaggingException {
		String serviceName = req.getServiceName();
		RequestInfo requestInfo = req.getRequestInfo();
		// APIKeyチェック
		ProviderUtil.checkAPIKey(req);
		// stateチェック
		SystemContext systemContext = new SystemContext(serviceName, 
				req.getRequestInfo(), req.getConnectionInfo());
		checkStateProc(provider, state, systemContext);
		return createReturnState(provider, state, true, serviceName, requestInfo);
	}
	
	/**
	 * stateが正しいかどうかチェック.
	 * @param provider OAuthプロバイダ名
	 * @param state state
	 * @param systemContext SystemContext
	 * @throws AuthenticationException stateが不正な場合
	 */
	private void checkStateProc(String provider, String state, SystemContext systemContext) 
	throws IOException, TaggingException {
		String serviceName = systemContext.getServiceName();
		String retServiceName = OAuthUtil.getServiceNameBySecret(systemContext, 
				provider, state);
		if (!serviceName.equals(retServiceName)) {
			AuthenticationException ae = new AuthenticationException("Invalid state.");
			ae.setSubMessage("Invalid state.");
			throw ae;
		}
	}
	
	/**
	 * createState、checkStateの戻り値を生成.
	 * @param provider OAuthプロバイダ名
	 * @param state state
	 * @param setClientSecret client_secretを戻す場合true
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @return createState、checkStateの戻り値
	 */
	private FeedBase createReturnState(String provider, String state, 
			boolean setClientSecret, String serviceName, RequestInfo requestInfo) {
		FeedBase retFeed = TaggingEntryUtil.createFeed(serviceName);
		retFeed.title = state;
		retFeed.subtitle = OAuthUtil.getClientId(provider, serviceName);
		if (setClientSecret) {
			retFeed.rights = OAuthUtil.getClientSecret(provider, serviceName);
		}
		Link link = new Link();
		link._$rel = Link.REL_SELF;
		link._$href = OAuthUtil.getRedirectUri(provider, serviceName);
		retFeed.link = new ArrayList<>();
		retFeed.link.add(link);
		if (logger.isDebugEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[createState] state=");
			sb.append(retFeed.title);
			sb.append(", client_id=");
			sb.append(retFeed.subtitle);
			sb.append(", redirect_uri=");
			sb.append(link._$href);
			logger.debug(sb.toString());
		}
		return retFeed;
	}
	
}
