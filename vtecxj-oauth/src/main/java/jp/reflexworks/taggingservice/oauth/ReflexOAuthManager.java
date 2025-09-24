package jp.reflexworks.taggingservice.oauth;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.entry.Link;
import jp.reflexworks.servlet.util.AuthTokenUtil;
import jp.reflexworks.servlet.util.WsseAuth;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.blogic.SessionBlogic;
import jp.reflexworks.taggingservice.blogic.UserBlogic;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.PermissionException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.AuthenticationManager;
import jp.reflexworks.taggingservice.plugin.OAuthManager;
import jp.reflexworks.taggingservice.plugin.RequestResponseManager;
import jp.reflexworks.taggingservice.plugin.UserManager;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.reflexworks.taggingservice.util.UserUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * OAuth管理プラグイン.
 * 一部TaggingServiceの通常リクエストからOAuthの設定等に連携が必要な処理
 */
public class ReflexOAuthManager implements OAuthManager {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 初期処理.
	 * サーバ起動時に一度だけ呼び出されます。
	 */
	@Override
	public void init() {
		// Do nothing.
	}

	/**
	 * シャットダウン処理.
	 */
	@Override
	public void close() {
		// Do nothing.
	}

	/**
	 * 既存ユーザとソーシャルログインユーザの紐付け処理
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param provider OAuthプロバイダ
	 * @param rxid RXID
	 * @param reflexContext ReflexContext
	 * @return 更新後のユーザトップエントリー
	 */
	@Override
	public EntryBase mergeUser(ReflexRequest req, ReflexResponse resp, 
			String provider, String rxid, ReflexContext reflexContext) 
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		
		// 入力チェック
		CheckUtil.checkNotNull(provider, "provider");
		CheckUtil.checkNotNull(rxid, "authentication infomation");

		// 未ログインはエラー →呼び出し元でチェック済み
		String currentUid = auth.getUid();
		// `/_user/{現UID}/oauth/{provider}`エントリーが存在しない場合エラー
		String currentSocialAccountAlias = OAuthUtil.getSocialAccountAlias(provider, currentUid);
		EntryBase socialAccountEntry = reflexContext.getEntry(currentSocialAccountAlias);
		if (socialAccountEntry == null) {
			throw new PermissionException("You are not a '" + provider + "' social account.");
		}
		// `/_user/{現UID}`エントリーを取得。
		// title(アカウント)が`{ソーシャルアカウント}@@{provider}`でない場合エラー。
		UserBlogic userBlogic = new UserBlogic();
		String currentUserTopUri = userBlogic.getUserTopUriByUid(currentUid);
		EntryBase currentUserTopEntry = reflexContext.getEntry(currentUserTopUri);
		String taggingAccountSuffix = OAuthUtil.getTaggingAccountSuffix(provider);
		String currentAccount = currentUserTopEntry.title;
		if (StringUtils.isBlank(currentAccount) || 
				!currentAccount.endsWith(taggingAccountSuffix)) {
			throw new PermissionException("You are not a '" + provider + "' social account name.");
		}
		// パスワード認証用エントリー/_user/{現UID}/authを取得。パスワードの設定がある場合エラー。
		UserManager userManager = TaggingEnvUtil.getUserManager();
		String currentUserAuthUri = userManager.getUserAuthUriByUid(currentUid);
		EntryBase currentUserAuthEntry = reflexContext.getEntry(currentUserAuthUri);
		if (currentUserAuthEntry != null) {
			String currentPassword = userManager.getPassword(currentUserAuthEntry);
			if (!StringUtils.isBlank(currentPassword)) {
				throw new PermissionException("Password login credential has already been registered.");
			}
		}
		
		// メールアドレスをアカウントに変換し、ユーザトップエントリーを取得(`/_user?title={対象アカウント}`)。
		// ユーザトップエントリーが存在しない場合エラー。
		WsseAuth wsseAuth = AuthTokenUtil.parseRXID(rxid);
		if (wsseAuth == null) {
			throw new IllegalParameterException("RXID is invalid.");
		}
		String[] usernameAndService = AuthTokenUtil.getUsernameAndService(
				wsseAuth);
		if (usernameAndService == null || usernameAndService.length < 1) {
			throw new IllegalParameterException("RXID is invalid.");
		}
		String targetAccount = UserUtil.editAccount(usernameAndService[0]);
		SystemContext systemContext = new SystemContext(auth, requestInfo, connectionInfo);
		EntryBase targetUserTopEntry = userBlogic.getUserTopEntryByAccount(targetAccount, systemContext);
		if (targetUserTopEntry == null) {
			throw new IllegalParameterException("The target account does not exist.");
		}
		// ユーザトップエントリーのキーからUIDを取得。(対象アカウントのUID)
		String targetUserTopUri = targetUserTopEntry.getMyUri();
		String targetUid = userBlogic.getUidByUri(targetUserTopUri);
		// `/_user/{対象UID}/oauth/{provider}`エントリーを取得。データが存在する場合エラー。
		String targetSocialAccountAlias = OAuthUtil.getSocialAccountAlias(provider, targetUid);
		EntryBase targetSocialAccountEntry = systemContext.getEntry(targetSocialAccountAlias);
		if (targetSocialAccountEntry != null) {
			throw new IllegalParameterException("The target account is already a '" + provider + "' social account.");
		}
		// RXID認証
		userManager.checkWsse(wsseAuth, null, systemContext);
		
		// ユーザ紐付け更新
		// ソーシャルアカウントエントリーのエイリアスである`/_user/{UID}/oauth/{provider}`を、
		// 指定アカウントのUIDに置き換える。
		List<Link> links = new ArrayList<>();
		for (Link currentLink : socialAccountEntry.link) {
			if (Link.REL_SELF.equals(currentLink._$rel)) {
				// エイリアス検索のため、rel="self"はエイリアスになっている。ID URIに戻す。
				Link selfLink = new Link();
				selfLink._$rel = Link.REL_SELF;
				selfLink._$href = TaggingEntryUtil.getUriById(socialAccountEntry.id);
				links.add(selfLink);
			} else if (Link.REL_ALTERNATE.equals(currentLink._$rel) &&
					currentSocialAccountAlias.equals(currentLink._$href)) {
				Link targetLink = new Link();
				targetLink._$rel = Link.REL_ALTERNATE;
				targetLink._$href = OAuthUtil.getSocialAccountAlias(provider, targetUid);
				links.add(targetLink);
			} else {
				links.add(currentLink);
			}
		}
		socialAccountEntry.link = links;
		// ログインユーザのユーザステータスを`Nothing`にする。(`/_user/{現UID}`)
		currentUserTopEntry.summary = Constants.USERSTATUS_NOTHING;
		FeedBase feed = TaggingEntryUtil.createFeed(serviceName);
		// 親階層 /_user/{UID}/oauth
		String socialAccountAliasParent = OAuthUtil.getSocialAccountAliasParent(targetUid);
		EntryBase parentEntry = TaggingEntryUtil.createEntry(serviceName);
		parentEntry.setMyUri(socialAccountAliasParent);
		feed.addEntry(parentEntry);
		// ソーシャルアカウントエントリー、旧ユーザトップエントリー
		feed.addEntry(socialAccountEntry);
		feed.addEntry(currentUserTopEntry);
		systemContext.put(feed);

		// ログインユーザのセッション情報を指定アカウントに切り替える。
		changeSession(req, resp, provider, targetAccount, targetUid);
		
		// 現ユーザ削除処理
		feed = TaggingEntryUtil.createFeed(serviceName);
		currentUserTopEntry.title = null;
		feed.addEntry(currentUserTopEntry);
		userManager.deleteUser(feed, true, systemContext);

		// 戻り値はユーザトップエントリー
		return targetUserTopEntry;
	}
	
	/**
	 * 対象のアカウントでセッションを作成し直す.
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param provider OAuthプロバイダ
	 * @param account アカウント
	 * @param uid UID
	 */
	private void changeSession(ReflexRequest req, ReflexResponse resp,
			String provider, String account, String uid) 
	throws IOException, TaggingException {
		String serviceName = req.getServiceName();
		RequestInfo requestInfo  = req.getRequestInfo();
		ConnectionInfo connectionInfo = req.getConnectionInfo();

		// セッションを削除する。
		SessionBlogic sessionBlogic = new SessionBlogic();
		sessionBlogic.deleteSession(req.getAuth(), requestInfo, connectionInfo);

		// セッション生成
		String authType = OAuthUtil.getAuthType(provider);
		ReflexAuthentication auth = sessionBlogic.createSession(account, uid, authType,
				serviceName, requestInfo, connectionInfo);

		// 認証情報をリクエストオブジェクトに紐付ける
		RequestResponseManager reqRespManager = TaggingEnvUtil.getRequestResponseManager();
		reqRespManager.afterAuthenticate(req, resp, auth);

		// レスポンスにセッションIDをセット
		AuthenticationManager authenticationManager = 
				TaggingEnvUtil.getAuthenticationManager();
		authenticationManager.setSessionIdToResponse(req, resp);
	}
	
	/**
	 * ユーザ削除.
	 * ソーシャルログインエントリーを削除する。
	 * @param uid UID
	 * @param systemContext SystemContext
	 */
	public void deleteUser(String uid, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		// /_user/{UID}/oauth をfeed検索
		String parentUri = OAuthUtil.getSocialAccountAliasParent(uid);
		FeedBase feed = reflexContext.getFeed(parentUri);
		if (TaggingEntryUtil.isExistData(feed)) {
			List<EntryBase> delEntries = new ArrayList<>();
			FeedBase delFeed = TaggingEntryUtil.createFeed(serviceName);
			delFeed.entry = delEntries;
			for (EntryBase entry : feed.entry) {
				String idUri = TaggingEntryUtil.getUriById(entry.id);
				EntryBase delEntry = TaggingEntryUtil.createEntry(serviceName);
				delEntry.setMyUri(idUri);
				delEntries.add(delEntry);
			}
			reflexContext.delete(delFeed);
		}
	}

}
