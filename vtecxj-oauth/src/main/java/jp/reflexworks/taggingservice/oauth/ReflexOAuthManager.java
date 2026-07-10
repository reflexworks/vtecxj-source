package jp.reflexworks.taggingservice.oauth;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
import jp.reflexworks.taggingservice.api.SettingConst;
import jp.reflexworks.taggingservice.blogic.SessionBlogic;
import jp.reflexworks.taggingservice.blogic.UserBlogic;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.AuthenticationException;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
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
import jp.sourceforge.reflex.util.NumberingUtil;
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
	 * 既存ユーザとソーシャルログインユーザの紐付けリクエスト.
	 * WSSE認証を行い、対象アカウントのメールアドレス宛に確認コードを送信する。
	 * この時点ではユーザ情報・セッション情報の更新は行わない。
	 * @param provider OAuthプロバイダ
	 * @param wsse WSSE
	 * @param reflexContext ReflexContext
	 */
	@Override
	public void mergeUser(String provider, String wsse, ReflexContext reflexContext)
	throws IOException, TaggingException {
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();

		// 入力チェック
		CheckUtil.checkNotNull(provider, "provider");
		CheckUtil.checkNotNull(wsse, "authentication infomation");

		// 未ログインはエラー →呼び出し元でチェック済み
		String currentUid = auth.getUid();
		UserBlogic userBlogic = new UserBlogic();
		UserManager userManager = TaggingEnvUtil.getUserManager();
		SystemContext systemContext = new SystemContext(auth, requestInfo, connectionInfo);

		// 現ユーザがソーシャルログインユーザとして紐付け可能な状態かチェック
		checkCurrentUserForMerge(provider, currentUid, reflexContext, userBlogic, userManager);

		// WSSEからアカウントを取得し、ユーザトップエントリーを取得(`/_user?title={対象アカウント}`)。
		// ユーザトップエントリーが存在しない場合エラー。
		WsseAuth wsseAuth = AuthTokenUtil.parseWSSEheader(wsse);
		if (wsseAuth == null) {
			throw new IllegalParameterException("WSSE is invalid.");
		}
		// このAPIは常にAPIKeyを使わないWSSEとして扱う。(スマホ向けWSSE-APIKEYとは区別する)
		wsseAuth.useAPIKey = false;
		String[] usernameAndService = AuthTokenUtil.getUsernameAndService(wsseAuth);
		if (usernameAndService == null || usernameAndService.length < 1) {
			throw new IllegalParameterException("WSSE is invalid.");
		}
		String targetAccount = UserUtil.editAccount(usernameAndService[0]);
		EntryBase targetUserTopEntry = userBlogic.getUserTopEntryByAccount(targetAccount, systemContext);
		if (targetUserTopEntry == null) {
			throw new IllegalParameterException("The target account does not exist.");
		}
		// ユーザトップエントリーのキーからUIDを取得。(対象アカウントのUID)
		String targetUserTopUri = targetUserTopEntry.getMyUri();
		String targetUid = userBlogic.getUidByUri(targetUserTopUri);

		// `/_user/{対象UID}/oauth/{provider}`エントリーを取得。データが存在する場合エラー。
		checkTargetHasNoAlias(provider, targetUid, systemContext);

		// WSSE認証
		userManager.checkWsse(wsseAuth, null, systemContext);

		// 確認コードを発行し、キャッシュ登録・対象アカウントへメール送信
		sendMergeUserVerifyCode(provider, currentUid, targetUid, systemContext);
	}

	/**
	 * 既存ユーザとソーシャルログインユーザの紐付け実行.
	 * 確認コードを照合し、一致すれば紐付けを実行する。
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param verifyCode 確認コード
	 * @param reflexContext ReflexContext
	 * @return 更新後のユーザトップエントリー
	 */
	@Override
	public EntryBase verifyMergeUser(ReflexRequest req, ReflexResponse resp,
			String verifyCode, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		SystemContext systemContext = new SystemContext(auth, requestInfo, connectionInfo);
		String currentUid = auth.getUid();
		UserBlogic userBlogic = new UserBlogic();
		UserManager userManager = TaggingEnvUtil.getUserManager();

		String topUri = getMergeoauthuserUriByUid(currentUid);
		String targetUidUri = topUri + OAuthConst.URI_MERGEOAUTHUSER_TARGET_UID;
		String providerUri = topUri + OAuthConst.URI_MERGEOAUTHUSER_PROVIDER;
		String verifyUri = topUri + OAuthConst.URI_MERGEOAUTHUSER_VERIFY;
		String errorCountUri = topUri + OAuthConst.URI_MERGEOAUTHUSER_ERROR_COUNT;

		// キャッシュのエラーカウントが一定回数を超えている場合はエラー。
		int verifyFailedCountLimit = TaggingEnvUtil.getPropInt(serviceName,
				SettingConst.VERIFY_FAILED_COUNT, OAuthConst.VERIFY_FAILED_COUNT_DEFAULT);
		Long verifyErrorCnt = systemContext.getCacheLong(errorCountUri);
		if (verifyErrorCnt != null && verifyErrorCnt > verifyFailedCountLimit) {
			AuthenticationException ae = new AuthenticationException("Verify failed count exceeded.");
			ae.setSubMessage("Verify failed count exceeded: " + verifyErrorCnt);
			throw ae;
		}

		// キャッシュから対象UID・プロバイダ・確認コードを取得する。
		String targetUid = systemContext.getCacheString(targetUidUri);
		String provider = systemContext.getCacheString(providerUri);
		String verifyCodeOfCache = systemContext.getCacheString(verifyUri);
		if (StringUtils.isBlank(targetUid) || StringUtils.isBlank(provider) ||
				StringUtils.isBlank(verifyCodeOfCache)) {
			// 有効期限切れ、または登録なし
			AuthenticationException ae = new AuthenticationException("The account merge has timed out.");
			ae.setSubMessage("The account merge has timed out. uid=" + currentUid);
			throw ae;
		}

		// 確認コード確認
		if (!verifyCodeOfCache.equals(verifyCode)) {
			// 確認コード不一致
			systemContext.incrementCache(errorCountUri, 1);
			AuthenticationException ae = new AuthenticationException("The verification codes do not match.");
			ae.setSubMessage("The verification codes do not match. uid=" + currentUid);
			throw ae;
		}

		// 現ユーザ・対象アカウントの状況を再チェック(発行時からの状態変化に備えて再確認する)
		EntryBase socialAccountEntry = checkCurrentUserForMerge(provider, currentUid,
				reflexContext, userBlogic, userManager);
		checkTargetHasNoAlias(provider, targetUid, systemContext);

		// 対象アカウントのユーザトップエントリーを取得(SystemContext使用)
		String targetUserTopUri = userBlogic.getUserTopUriByUid(targetUid);
		EntryBase targetUserTopEntry = systemContext.getEntry(targetUserTopUri);
		if (targetUserTopEntry == null) {
			throw new IllegalParameterException("The target account does not exist.");
		}
		String targetAccount = targetUserTopEntry.title;

		String currentUserTopUri = userBlogic.getUserTopUriByUid(currentUid);
		EntryBase currentUserTopEntry = reflexContext.getEntry(currentUserTopUri);

		// ユーザ紐付け更新
		// ソーシャルアカウントエントリーのエイリアスである`/_user/{UID}/oauth/{provider}`を、
		// 指定アカウントのUIDに置き換える。
		String currentSocialAccountAlias = OAuthUtil.getSocialAccountAlias(provider, currentUid);
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

		// キャッシュの削除
		systemContext.deleteCacheString(targetUidUri);
		systemContext.deleteCacheString(providerUri);
		systemContext.deleteCacheString(verifyUri);
		systemContext.deleteCacheLong(errorCountUri);

		// 戻り値はユーザトップエントリー
		return targetUserTopEntry;
	}

	/**
	 * 現ユーザがソーシャルログインユーザとして紐付け可能な状態かチェックする.
	 * @param provider OAuthプロバイダ
	 * @param currentUid 現UID
	 * @param reflexContext ReflexContext
	 * @param userBlogic UserBlogic
	 * @param userManager UserManager
	 * @return 現ユーザのソーシャルアカウントエントリー(`/_user/{現UID}/oauth/{provider}`)
	 */
	private EntryBase checkCurrentUserForMerge(String provider, String currentUid,
			ReflexContext reflexContext, UserBlogic userBlogic, UserManager userManager)
	throws IOException, TaggingException {
		// `/_user/{現UID}/oauth/{provider}`エントリーが存在しない場合エラー
		String currentSocialAccountAlias = OAuthUtil.getSocialAccountAlias(provider, currentUid);
		EntryBase socialAccountEntry = reflexContext.getEntry(currentSocialAccountAlias);
		if (socialAccountEntry == null) {
			throw new PermissionException("You are not a '" + provider + "' social account.");
		}
		// `/_user/{現UID}`エントリーを取得。
		// title(アカウント)が`{ソーシャルアカウント}@@{provider}`でない場合エラー。
		String currentUserTopUri = userBlogic.getUserTopUriByUid(currentUid);
		EntryBase currentUserTopEntry = reflexContext.getEntry(currentUserTopUri);
		String taggingAccountSuffix = OAuthUtil.getTaggingAccountSuffix(provider);
		String currentAccount = currentUserTopEntry.title;
		if (StringUtils.isBlank(currentAccount) ||
				!currentAccount.endsWith(taggingAccountSuffix)) {
			throw new PermissionException("You are not a '" + provider + "' social account name.");
		}
		// パスワード認証用エントリー/_user/{現UID}/authを取得。パスワードの設定がある場合エラー。
		String currentUserAuthUri = userManager.getUserAuthUriByUid(currentUid);
		EntryBase currentUserAuthEntry = reflexContext.getEntry(currentUserAuthUri);
		if (currentUserAuthEntry != null) {
			String currentPassword = userManager.getPassword(currentUserAuthEntry);
			if (!StringUtils.isBlank(currentPassword)) {
				throw new PermissionException("Password login credential has already been registered.");
			}
		}
		return socialAccountEntry;
	}

	/**
	 * 対象アカウントがまだ指定providerのソーシャルアカウントでないことをチェックする.
	 * @param provider OAuthプロバイダ
	 * @param targetUid 対象UID
	 * @param reflexContext ReflexContext
	 */
	private void checkTargetHasNoAlias(String provider, String targetUid,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		String targetSocialAccountAlias = OAuthUtil.getSocialAccountAlias(provider, targetUid);
		EntryBase targetSocialAccountEntry = reflexContext.getEntry(targetSocialAccountAlias);
		if (targetSocialAccountEntry != null) {
			throw new IllegalParameterException("The target account is already a '" + provider + "' social account.");
		}
	}

	/**
	 * 確認コードを発行し、Redisキャッシュに登録した上で対象アカウントのメールアドレス宛に送信する.
	 * @param provider OAuthプロバイダ
	 * @param currentUid 現UID
	 * @param targetUid 対象UID
	 * @param systemContext SystemContext
	 */
	private void sendMergeUserVerifyCode(String provider, String currentUid, String targetUid,
			SystemContext systemContext)
	throws IOException, TaggingException {
		String serviceName = systemContext.getServiceName();
		UserManager userManager = TaggingEnvUtil.getUserManager();

		// メール設定チェック (/_settings/mergeoauthuser)
		EntryBase mailEntry = systemContext.getEntry(Constants.URI_SETTINGS_MERGEOAUTHUSER, true);
		if (mailEntry == null || StringUtils.isBlank(mailEntry.title) ||
				(StringUtils.isBlank(mailEntry.summary) && StringUtils.isBlank(mailEntry.getContentText()))) {
			// mergeoauthuserでメールの設定が無い場合はエラー
			throw new InvalidServiceSettingException("There is no mail setting. (mergeoauthuser)");
		}

		// 対象アカウントのメールアドレスを取得
		String targetEmail = userManager.getEmailByUid(targetUid, systemContext);
		if (StringUtils.isBlank(targetEmail)) {
			throw new IllegalParameterException("The target account has no email address.");
		}

		// 確認コードを発行
		int verifyCodeLen = TaggingEnvUtil.getPropInt(serviceName,
				SettingConst.VERIFY_CODE_LENGTH, OAuthConst.VERIFY_CODE_LENGTH_DEFAULT);
		boolean enableAlphabetVerify = TaggingEnvUtil.getPropBoolean(serviceName,
				SettingConst.ENABLE_ALPHABET_VERIFY, false);
		String verifyCode = null;
		if (enableAlphabetVerify) {
			verifyCode = NumberingUtil.randomString(verifyCodeLen).toUpperCase(Locale.ENGLISH);
		} else {
			verifyCode = NumberingUtil.randomNumber(verifyCodeLen);
		}

		// 指定情報をキャッシュ(Redis)に登録。
		// キー:/_mergeoauthuser/{現UID}/target_uid、値:対象アカウントのUID
		// キー:/_mergeoauthuser/{現UID}/provider、値:OAuthプロバイダ
		// キー:/_mergeoauthuser/{現UID}/verify、値:確認コード
		// キー:/_mergeoauthuser/{現UID}/error_count、値:0 (確認コード不一致回数)
		// 有効期限はRXIDと同じ。
		String topUri = getMergeoauthuserUriByUid(currentUid);
		int expireSec = TaggingEnvUtil.getRxidMinute(serviceName) * 60;
		systemContext.setCacheString(topUri + OAuthConst.URI_MERGEOAUTHUSER_TARGET_UID, targetUid, expireSec);
		systemContext.setCacheString(topUri + OAuthConst.URI_MERGEOAUTHUSER_PROVIDER, provider, expireSec);
		systemContext.setCacheString(topUri + OAuthConst.URI_MERGEOAUTHUSER_VERIFY, verifyCode, expireSec);
		systemContext.setCacheLong(topUri + OAuthConst.URI_MERGEOAUTHUSER_ERROR_COUNT, 0, expireSec);

		// 指定されたメールアドレスにメールを送信する。本文に確認コードを埋め込む。
		mailEntry.summary = replaceVerify(mailEntry.summary, verifyCode);
		if (mailEntry.content != null && !StringUtils.isBlank(mailEntry.content._$$text)) {
			mailEntry.content._$$text = replaceVerify(mailEntry.content._$$text, verifyCode);
		}
		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append("[sendMergeUserVerifyCode] sendMail [title]: ");
			sb.append(mailEntry.title);
			sb.append(" [summary]: ");
			sb.append(mailEntry.summary);
			sb.append(" [content]: ");
			sb.append(mailEntry.getContentText());
			logger.debug(sb.toString());
		}
		systemContext.sendMail(mailEntry, targetEmail, null);
	}

	/**
	 * メッセージの指定部分を確認コードに変換.
	 * ${VERIFY}の部分を確認コードに変換する。
	 * @param message メッセージ
	 * @param verifyCode 確認コード
	 * @return 変換したメッセージ
	 */
	private String replaceVerify(String message, String verifyCode) {
		return StringUtils.replaceAll(message, OAuthConst.REPLACE_REGEX_VERIFY,
				StringUtils.null2blank(verifyCode));
	}

	/**
	 * 紐付けリクエストに使用するURI(/_mergeoauthuser/{uid})を取得.
	 * @param uid UID
	 * @return 紐付けリクエストに使用するURI(/_mergeoauthuser/{uid})
	 */
	private String getMergeoauthuserUriByUid(String uid) {
		StringBuilder sb = new StringBuilder();
		sb.append(OAuthConst.URI_MERGEOAUTHUSER);
		sb.append("/");
		sb.append(uid);
		return sb.toString();
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
