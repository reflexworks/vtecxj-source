package jp.reflexworks.taggingservice.blogic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.PermissionException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.OAuthManager;
import jp.reflexworks.taggingservice.plugin.UserManager;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.GroupUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.reflexworks.taggingservice.util.UserUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * ユーザ関連ビジネスロジック.
 */
public class UserBlogic {

	/**
	 * ログインユーザのユーザ情報Entryを取得
	 * @param reflexContext ReflexContext
	 * @return ログインユーザのユーザ情報Entry
	 */
	public FeedBase whoami(ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		// 認証情報のチェック
		ReflexAuthentication auth = reflexContext.getAuth();
		checkAuth(auth);
		UserManager userManager = TaggingEnvUtil.getUserManager();
		EntryBase userTopEntry = userManager.getUserTopEntryByUid(auth.getUid(), reflexContext);
		if (userTopEntry != null) {
			TaggingEntryUtil.editNometa(userTopEntry);
			return TaggingEntryUtil.createFeed(serviceName, userTopEntry);
		}
		return null;
	}

	/**
	 * アカウントからUIDを取得.
	 * @param account アカウント
	 * @param systemContext SystemContext
	 * @return UID
	 */
	public String getUidByAccount(String account, SystemContext systemContext)
	throws IOException, TaggingException {
		UserManager userManager = TaggingEnvUtil.getUserManager();
		return userManager.getUidByAccount(account, systemContext);
	}

	/**
	 * アカウントからUIDを取得.
	 * @param account アカウント
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return UID
	 */
	public String getUidByAccount(String account, String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		SystemContext systemContext = new SystemContext(serviceName,
				requestInfo, connectionInfo);
		return getUidByAccount(account, systemContext);
	}

	/**
	 * アカウントからユーザトップエントリーを取得します.
	 * <p>
	 * ユーザトップエントリーのURIはUIDのため、ユーザ名を検索条件にルートエントリー配下をフィード検索します.
	 * </p>
	 * @param account アカウント
	 * @param systemContext SystemContext
	 * @return ユーザトップエントリー
	 */
	public EntryBase getUserTopEntryByAccount(String account,
			SystemContext systemContext)
	throws IOException, TaggingException {
		UserManager userManager = TaggingEnvUtil.getUserManager();
		return userManager.getUserTopEntryByAccount(account, systemContext);
	}

	/**
	 * UIDからユーザトップエントリーを取得します.
	 * @param uid UID
	 * @param systemContext SystemContext
	 * @return ユーザトップエントリー
	 */
	public EntryBase getUserTopEntryByUid(String uid,
			SystemContext systemContext)
	throws IOException, TaggingException {
		UserManager userManager = TaggingEnvUtil.getUserManager();
		return userManager.getUserTopEntryByUid(uid, systemContext);
	}

	/**
	 * ログインユーザのRXIDを生成
	 * @param reflexContext ReflexContext
	 * @return RXID
	 */
	public String createRXID(ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 認証情報のチェック
		checkAuth(reflexContext.getAuth());
		// RXID発行
		UserManager userManager = TaggingEnvUtil.getUserManager();
		return userManager.createRXID(reflexContext);
	}

	/**
	 * 指定されたアカウントのRXIDを生成
	 * @param account アカウント
	 * @param reflexContext ReflexContext
	 * @return RXID
	 */
	public String createRXID(String account, SystemContext systemContext)
	throws IOException, TaggingException {
		if (StringUtils.isBlank(account)) {
			throw new IllegalParameterException("Account is required.");
		}
		// RXID発行
		UserManager userManager = TaggingEnvUtil.getUserManager();
		return userManager.createRXIDByAccount(account, systemContext);
	}

	/**
	 * ユーザ登録.
	 * @param feed ユーザ登録情報
	 * @param reflexContext ReflexContext
	 * @return ユーザのトップエントリー
	 */
	public EntryBase adduser(FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException {
		UserManager userManager = TaggingEnvUtil.getUserManager();
		return userManager.adduser(feed, reflexContext);
	}

	/**
	 * 外部連携によるユーザ登録.
	 * @param account アカウント
	 * @param nickname ニックネーム
	 * @param feed 追加登録エントリー
	 * @param reflexContext ReflexContext
	 * @return ユーザのトップエントリー
	 */
	public EntryBase adduserByLink(String account, String nickname,
			FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException {
		UserManager userManager = TaggingEnvUtil.getUserManager();
		return userManager.adduserByLink(account, nickname, feed, reflexContext);
	}

	/**
	 * 管理者によるユーザ登録.
	 * @param feed ユーザ登録情報
	 * @param reflexContext ReflexContext
	 * @return ユーザのトップエントリーリスト
	 */
	public FeedBase adduserByAdmin(FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// ACLチェック
		// $useradminグループメンバーでなければエラー
		ReflexAuthentication auth = reflexContext.getAuth();
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAuthedGroup(auth, Constants.URI_GROUP_USERADMIN);

		UserManager userManager = TaggingEnvUtil.getUserManager();
		return userManager.adduserByAdmin(feed, reflexContext);
	}

	/**
	 * グループ管理者によるユーザ登録.
	 * @param feed ユーザ登録情報
	 * @param groupName グループ名
	 * @param reflexContext ReflexContext
	 * @return ユーザのトップエントリーリスト
	 */
	public FeedBase adduserByGroupadmin(FeedBase feed, String groupName, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// ACLチェック
		// 指定されたグループの$groupadminグループメンバーでなければエラー
		String groupadminGroup = GroupUtil.getGroupadminGroup(groupName);
		ReflexAuthentication auth = reflexContext.getAuth();
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAuthedGroup(auth, groupadminGroup);

		UserManager userManager = TaggingEnvUtil.getUserManager();
		return userManager.adduserByGroupadmin(feed, groupName, reflexContext);
	}

	/**
	 * パスワードリセットのためのメール送信.
	 * @param feed パスワードリセット情報
	 * @param reflexContext ReflexContext
	 * @return ユーザのトップエントリー
	 */
	public EntryBase passreset(FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException {
		UserManager userManager = TaggingEnvUtil.getUserManager();
		return userManager.passreset(feed, reflexContext);
	}

	/**
	 * パスワード更新.
	 * @param feed パスワード更新情報
	 * @param reflexContext ReflexContext
	 * @return ユーザのトップエントリー
	 */
	public EntryBase changepass(FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException {
		UserManager userManager = TaggingEnvUtil.getUserManager();
		return userManager.changepass(feed, reflexContext);
	}

	/**
	 * 管理者によるパスワード更新.
	 * @param feed パスワード更新情報
	 * @param reflexContext ReflexContext
	 * @return 更新情報
	 */
	public FeedBase changepassByAdmin(FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// ユーザ管理者またはグループ管理者かどうかチェック
		GroupUtil.checkUseradminOrGroupadmin(reflexContext.getAuth());

		UserManager userManager = TaggingEnvUtil.getUserManager();
		return userManager.changepassByAdmin(feed, reflexContext);
	}

	/**
	 * アカウント更新のためのメール送信.
	 * @param feed アカウント更新情報
	 * @param reflexContext ReflexContext
	 */
	public void changeaccount(FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// ログインしているかどうかチェック
		ReflexAuthentication auth = reflexContext.getAuth();
		AclBlogic aclBlogic = new AclBlogic();
		if (!aclBlogic.isAuthUser(auth)) {
			PermissionException pe = new PermissionException();
			pe.setSubMessage("User login is required for change account.");
			throw new PermissionException();
		}

		UserManager userManager = TaggingEnvUtil.getUserManager();
		userManager.changeaccount(feed, reflexContext);
	}

	/**
	 * アカウント更新.
	 * @param verifyCode 認証コード
	 * @param reflexContext ReflexContext
	 */
	public EntryBase verifyChangeaccount(String verifyCode, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// ログインしているかどうかチェック
		ReflexAuthentication auth = reflexContext.getAuth();
		AclBlogic aclBlogic = new AclBlogic();
		if (!aclBlogic.isAuthUser(auth)) {
			PermissionException pe = new PermissionException();
			pe.setSubMessage("User login is required for change account.");
			throw new PermissionException();
		}

		UserManager userManager = TaggingEnvUtil.getUserManager();
		return userManager.verifyChangeaccount(verifyCode, reflexContext);
	}

	/**
	 * アクセストークンを取得.
	 * @param reflexContext ReflexContext
	 * @return アクセストークン
	 */
	public String getAccessToken(ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 認証済かどうかチェック
		checkAuth(reflexContext.getAuth());

		// アクセストークン取得処理
		UserManager userManager = TaggingEnvUtil.getUserManager();
		return userManager.getAccessToken(reflexContext);
	}

	/**
	 * リンクトークンを取得.
	 * @param uri URI
	 * @param reflexContext ReflexContext
	 * @return リンクトークン
	 */
	public String getLinkToken(String uri, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 認証情報のチェック
		checkAuth(reflexContext.getAuth());

		UserManager userManager = TaggingEnvUtil.getUserManager();
		return userManager.getLinkToken(uri, reflexContext);
	}

	/**
	 * アクセスキー更新.
	 * @param feed アクセスキー更新情報
	 * @param reflexContext ReflexContext
	 */
	public void changeAccessKey(ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 認証情報のチェック
		checkAuth(reflexContext.getAuth());

		UserManager userManager = TaggingEnvUtil.getUserManager();
		userManager.changeAccessKey(reflexContext);
	}

	/**
	 * 認証済かどうかチェック
	 * @param auth 認証情報
	 * @throws PermissionException 認証されていない場合
	 */
	public void checkAuth(ReflexAuthentication auth)
	throws PermissionException {
		if (auth == null || StringUtils.isBlank(auth.getUid()) ||
				StringUtils.isBlank(auth.getAccount())) {
			PermissionException pe = new PermissionException();
			pe.setSubMessage("Authentication is required.");
			throw pe;
		}
	}

	/**
	 * メールアドレスからユーザステータスを取得.
	 * ユーザ管理者権限がある場合のみ実行可。
	 * <p>
	 * <ul>
	 * <li>登録なし : Nothing</li>
	 * <li>仮登録 : Interim</li>
	 * <li>本登録 : Activated</li>
	 * <li>無効 : Revoked</li>
	 * </ul>
	 * </p>
	 * @param email メールアドレス
	 * @param reflexContext ReflexContext
	 * @return ユーザステータス
	 */
	public EntryBase getUserstatusByEmail(String email, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// ユーザ管理者またはグループ管理者かどうかチェック
		GroupUtil.checkUseradminOrGroupadmin(reflexContext.getAuth());
		// 入力チェック
		CheckUtil.checkNotNull(email, "Account");
		// ユーザステータス取得
		String account = UserUtil.editAccount(email);
		UserManager userManager = TaggingEnvUtil.getUserManager();
		return userManager.getUserstatusByAccount(account, reflexContext);
	}

	/**
	 * ユーザステータス一覧を取得.
	 * ユーザ管理者権限がある場合のみ実行可。
	 * <p>
	 * <ul>
	 * <li>登録なし : Nothing</li>
	 * <li>仮登録 : Interim</li>
	 * <li>本登録 : Activated</li>
	 * <li>無効 : Revoked</li>
	 * </ul>
	 * </p>
	 * @param limitStr 一覧件数。nullの場合はデフォルト値。*の場合は制限なし。
	 * @param cursorStr カーソル
	 * @param reflexContext ReflexContext
	 * @return ユーザステータス一覧
	 */
	public FeedBase getUserstatusList(String limitStr, String cursorStr,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		// ユーザ管理者かどうかチェック
		GroupUtil.checkUseradmin(reflexContext.getAuth());

		Integer limit = null;
		boolean isNoLimit = false;
		if (StringUtils.isInteger(limitStr)) {
			limit = Integer.parseInt(limitStr);
		} else if (RequestParam.WILDCARD.equals(limitStr)) {
			isNoLimit = true;
		}

		// ユーザステータス一覧取得
		UserManager userManager = TaggingEnvUtil.getUserManager();
		if (!isNoLimit) {
			return userManager.getUserstatusList(limit, cursorStr, reflexContext);
		} else {
			String tmpCursorStr = cursorStr;
			List<EntryBase> entries = new ArrayList<EntryBase>();
			do {
				FeedBase tmpFeed = userManager.getUserstatusList(limit, tmpCursorStr,
						reflexContext);
				if (tmpFeed != null && tmpFeed.entry != null) {
					entries.addAll(tmpFeed.entry);
				}
				tmpCursorStr = TaggingEntryUtil.getCursorFromFeed(tmpFeed);
			} while (!StringUtils.isBlank(tmpCursorStr));
			if (entries.isEmpty()) {
				return null;
			} else {
				FeedBase feed = TaggingEntryUtil.createFeed(serviceName);
				feed.entry = entries;
				return feed;
			}
		}
	}

	/**
	 * ユーザ権限剥奪.
	 * @param email ユーザ名
	 * @param isDeleteGroups RevokedまたはCancelledに変更時、同時にグループを削除する場合true
	 * @param reflexContext ReflexContext
	 * @return 処理対象ユーザのトップエントリー
	 */
	public EntryBase revokeUser(String email, boolean isDeleteGroups, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		FeedBase feed = null;
		if (!StringUtils.isBlank(email)) {
			feed = TaggingEntryUtil.createFeed(serviceName);
			EntryBase entry = TaggingEntryUtil.createEntry(serviceName);
			entry.title = email;
			feed.addEntry(entry);
		}
		FeedBase retFeed = revokeUser(feed, isDeleteGroups, reflexContext);
		if (TaggingEntryUtil.isExistData(retFeed)) {
			return retFeed.entry.get(0);
		}
		return null;
	}

	/**
	 * ユーザ権限剥奪.
	 * @param feed アカウント(title)、またはUID(link selfのhref)
	 * @param isDeleteGroups RevokedまたはCancelledに変更時、同時にグループを削除する場合true
	 * @param reflexContext ReflexContext
	 * @return 処理対象ユーザのトップエントリー
	 */
	public FeedBase revokeUser(FeedBase feed, boolean isDeleteGroups, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// ユーザ管理者またはグループ管理者かどうかチェック
		GroupUtil.checkUseradminOrGroupadmin(reflexContext.getAuth());
		// ユーザを無効にする
		UserManager userManager = TaggingEnvUtil.getUserManager();
		return userManager.revokeUser(feed, isDeleteGroups, reflexContext);
	}

	/**
	 * ユーザを有効にする.
	 * @param email ユーザ名
	 * @param reflexContext ReflexContext
	 * @return 処理対象ユーザのトップエントリー
	 */
	public EntryBase activateUser(String email, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		FeedBase feed = null;
		if (!StringUtils.isBlank(email)) {
			feed = TaggingEntryUtil.createFeed(serviceName);
			EntryBase entry = TaggingEntryUtil.createEntry(serviceName);
			entry.title = email;
			feed.addEntry(entry);
		}
		FeedBase retFeed = activateUser(feed, reflexContext);
		if (TaggingEntryUtil.isExistData(retFeed)) {
			return retFeed.entry.get(0);
		}
		return null;
	}

	/**
	 * ユーザを有効にする.
	 * @param feed アカウント(title)、またはUID(link selfのhref)
	 * @param reflexContext ReflexContext
	 * @return 処理対象ユーザのトップエントリー
	 */
	public FeedBase activateUser(FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// ユーザ管理者またはグループ管理者かどうかチェック
		GroupUtil.checkUseradminOrGroupadmin(reflexContext.getAuth());
		// ユーザを有効にする
		UserManager userManager = TaggingEnvUtil.getUserManager();
		return userManager.activateUser(feed, reflexContext);
	}

	/**
	 * ユーザ退会.
	 * @param isDeleteGroups RevokedまたはCancelledに変更時、同時にグループを削除する場合true
	 * @param reflexContext ReflexContext
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @return 処理対象ユーザのトップエントリー
	 */
	public EntryBase cancelUser(boolean isDeleteGroups, ReflexContext reflexContext, 
			ReflexRequest req, ReflexResponse resp)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();

		// ログインしているかどうかチェック
		AclBlogic aclBlogic = new AclBlogic();
		if (!aclBlogic.isAuthUser(auth)) {
			PermissionException pe = new PermissionException();
			pe.setSubMessage("User login is required for cancelling user.");
			throw new PermissionException();
		}

		FeedBase feed = TaggingEntryUtil.createFeed(serviceName);
		EntryBase entry = TaggingEntryUtil.createEntry(serviceName);
		entry.title = auth.getAccount();
		feed.addEntry(entry);

		// ユーザを退会する
		// 本人が実行する処理だが、ユーザステータスは一般ユーザが更新できないため、SystemContext生成を行う。
		SystemContext systemContext = new SystemContext(auth,
				requestInfo, connectionInfo);
		UserManager userManager = TaggingEnvUtil.getUserManager();
		FeedBase retFeed = userManager.cancelUser(feed, isDeleteGroups, systemContext);
		if (TaggingEntryUtil.isExistData(retFeed)) {
			// セッションがあればクリアする。
			LoginLogoutBlogic loginLogoutBlogic = new LoginLogoutBlogic();
			loginLogoutBlogic.logout(req, resp);

			return retFeed.entry.get(0);
		}
		return null;
	}

	/**
	 * ユーザを削除する.
	 * @param account アカウント
	 * @param async 削除を非同期に行う場合true
	 * @param reflexContext ReflexContext
	 * @return 処理対象ユーザのトップエントリー
	 */
	public EntryBase deleteUser(String account, boolean async, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		FeedBase feed = null;
		if (!StringUtils.isBlank(account)) {
			feed = TaggingEntryUtil.createFeed(serviceName);
			EntryBase entry = TaggingEntryUtil.createEntry(serviceName);
			entry.title = account;
			feed.addEntry(entry);
		}
		FeedBase retFeed = deleteUser(feed, async, reflexContext);
		if (TaggingEntryUtil.isExistData(retFeed)) {
			return retFeed.entry.get(0);
		}
		return null;
	}

	/**
	 * ユーザを削除する.
	 * @param feed アカウント(title)、またはUID(link selfのhref)
	 * @param async 削除を非同期に行う場合true
	 * @param reflexContext ReflexContext
	 * @return 処理対象ユーザのトップエントリー
	 */
	public FeedBase deleteUser(FeedBase feed, boolean async, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// ユーザ管理者またはグループ管理者かどうかチェック
		GroupUtil.checkUseradminOrGroupadmin(reflexContext.getAuth());
		// ユーザ削除
		UserManager userManager = TaggingEnvUtil.getUserManager();
		return userManager.deleteUser(feed, async, reflexContext);
	}

	/**
	 * ユーザのトップエントリーURIを取得.
	 * @param uid UID
	 * @return ユーザのトップエントリーURI
	 */
	public String getUserTopUriByUid(String uid) {
		UserManager userManager = TaggingEnvUtil.getUserManager();
		return userManager.getUserTopUriByUid(uid);
	}

	/**
	 * ユーザトップエントリーからユーザステータスを取得
	 * @param userTopEntry ユーザトップエントリー
	 * @return ユーザステータス
	 */
	public String getUserStatusByUserTopEntry(EntryBase userTopEntry) {
		UserManager userManager = TaggingEnvUtil.getUserManager();
		return userManager.getUserstatus(userTopEntry);
	}

	/**
	 * URIからUIDを取得します.
	 * @param uri URI
	 * @return UID
	 */
	public String getUidByUri(String uri) {
		UserManager userManager = TaggingEnvUtil.getUserManager();
		return userManager.getUidByUri(uri);
	}

	/**
	 * UIDまたはアカウントの存在チェックを行い、UIDを返却する.
	 * @param user UIDまたはアカウント
	 * @param systemContext SystemContext
	 * @return UID
	 */
	public String getUidByUser(String user, SystemContext systemContext)
	throws IOException, TaggingException {
		// UID検索
		EntryBase userTopEntry = getUserTopEntryByUid(user, systemContext);
		if (userTopEntry != null) {
			checkUserStatus(userTopEntry, user);
		} else {
			// アカウント検索
			userTopEntry = getUserTopEntryByAccount(user, systemContext);
			if (userTopEntry != null) {
				checkUserStatus(userTopEntry, user);
			}
		}
		String uid = null;
		if (userTopEntry != null) {
			uid = getUidByUri(userTopEntry.getMyUri());
		}
		if (StringUtils.isBlank(uid)) {
			throw new IllegalParameterException("The specified user does not exist. " + user);
		}
		return uid;
	}

	/**
	 * ユーザステータスチェック.
	 * Activated以外はエラーとする。
	 * @param userTopEntry ユーザトップエントリー
	 * @param user ユーザ (UIDまたはアカウント。ログ用。)
	 */
	private void checkUserStatus(EntryBase userTopEntry, String user) {
		// ユーザステータスチェック
		UserBlogic userBlogic = new UserBlogic();
		String userStatus = userBlogic.getUserStatusByUserTopEntry(userTopEntry);
		if (!Constants.USERSTATUS_ACTIVATED.equals(userStatus)) {
			throw new IllegalParameterException("The specified user is invalid. " + user);
		}
	}

	/**
	 * ２段階認証(TOTP)登録.
	 * @param req リクエスト
	 * @param reflexContext ReflexContext
	 * @return ２段階認証のための情報
	 */
	public FeedBase createTotp(ReflexRequest req, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// ログインしていない場合は認証エラー
		ReflexAuthentication auth = reflexContext.getAuth();
		checkAuth(auth);
		UserManager userManager = TaggingEnvUtil.getUserManager();
		return userManager.createTotp(req, reflexContext);
	}

	/**
	 * ２段階認証(TOTP)削除.
	 * @param account アカウント(サービス管理者の場合のみ指定可能)
	 * @param req リクエスト
	 * @param reflexContext ReflexContext
	 * @return ２段階認証削除情報
	 */
	public FeedBase deleteTotp(String pAccount, ReflexRequest req, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// ログインしていない場合は認証エラー
		ReflexAuthentication auth = reflexContext.getAuth();
		checkAuth(auth);
		// アカウント指定はサービス管理者のみ
		String account = auth.getAccount();
		if (!StringUtils.isBlank(pAccount) && !pAccount.equals(auth.getAccount())) {
			AclBlogic aclBlogic = new AclBlogic();
			aclBlogic.checkAuthedGroup(auth, Constants.URI_GROUP_USERADMIN);
			account = pAccount;
		}
		UserManager userManager = TaggingEnvUtil.getUserManager();
		return userManager.deleteTotp(account, reflexContext);
	}

	/**
	 * ２段階認証(TOTP)参照.
	 * @param req リクエスト
	 * @param reflexContext ReflexContext
	 * @return ２段階認証情報
	 */
	public FeedBase getTotp(ReflexRequest req, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// ログインしていない場合は認証エラー
		ReflexAuthentication auth = reflexContext.getAuth();
		checkAuth(auth);
		UserManager userManager = TaggingEnvUtil.getUserManager();
		return userManager.getTotp(req, reflexContext);
	}

	/**
	 * 信頼できる端末にセットする値の更新.
	 * @param auth 認証情報
	 * @param reflexContext ReflexContext
	 * @return ２段階認証情報
	 */
	public FeedBase changeTdid(ReflexRequest req, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// ログインしていない場合は認証エラー
		ReflexAuthentication auth = reflexContext.getAuth();
		checkAuth(auth);
		UserManager userManager = TaggingEnvUtil.getUserManager();
		return userManager.changeTdid(auth, reflexContext);
	}

	/**
	 * 指定したURI配下のキーのエントリーで自分が署名していないものを取得.
	 * ただしすでにグループ参加状態のものは除く。
	 * @param uri 親キー
	 * @param reflexContext ReflexContext
	 * @return 親キー配下のエントリーで署名していないEntryリスト
	 */
	public FeedBase getNoGroupMember(String uri, ReflexContext reflexContext) 
	throws IOException, TaggingException {
		UserManager userManager = TaggingEnvUtil.getUserManager();
		return userManager.getNoGroupMember(uri, reflexContext);
	}
	
	/**
	 * グループに参加登録する.
	 * グループ参加エントリーの登録処理。署名はなし。
	 * @param group グループ名
	 * @param selfid 自身のグループエイリアスのselfid (/_user/{UID}/group/{selfid})
	 * @param reflexContext ReflexContext
	 * @return グループエントリー
	 */
	public EntryBase addGroup(String group, String selfid, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 認証情報のチェック
		ReflexAuthentication auth = reflexContext.getAuth();
		checkAuth(auth);
		// 入力チェック
		GroupUtil.checkGroupUri(group);
		String tmpSelfid = GroupUtil.checkGroupSelfid(selfid, group);
		
		UserManager userManager = TaggingEnvUtil.getUserManager();
		FeedBase retFeed = userManager.addGroup(group, tmpSelfid, null, reflexContext);
		return retFeed.entry.get(0);
	}
	
	/**
	 * 管理者によるグループ参加登録を行う.
	 * グループ参加エントリーの登録処理。署名はなし。
	 * @param group グループ名
	 * @param selfid 自身のグループエイリアスのselfid (/_user/{UID}/group/{selfid})
	 * @param feed UIDリスト(ユーザ管理者のみ指定可)
	 * @param reflexContext ReflexContext
	 * @return グループエントリー
	 */
	public FeedBase addGroupByAdmin(String group, String selfid, FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// ユーザ管理者またはグループ管理者かどうかチェック
		GroupUtil.checkUseradminOrGroupadmin(reflexContext.getAuth());
		// 入力チェック
		GroupUtil.checkGroupUri(group);
		String tmpSelfid = GroupUtil.checkGroupSelfid(selfid, group);
		CheckUtil.checkFeed(feed, false);
		CheckUtil.checkParentUri(feed, Constants.URI_USER);

		UserManager userManager = TaggingEnvUtil.getUserManager();
		return userManager.addGroup(group, tmpSelfid, feed, reflexContext);
	}

	/**
	 * グループに参加署名する.
	 * グループ作成者がユーザへの参加承認をしている状態の時、ユーザがグループに参加する。
	 * 自身のグループエイリアス作成(登録がない場合→再加入を想定)と、自身のグループエイリアスへの署名を行う。
	 * @param group グループキー
	 * @param selfid 自身のグループエイリアスのselfid (/_user/{UID}/group/{selfid}) 
	 *               指定がない場合はグループキーの一番下の階層の値
	 * @param reflexContext ReflexContext
	 * @return グループエントリー
	 */
	public EntryBase joinGroup(String group, String selfid, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 認証情報のチェック
		ReflexAuthentication auth = reflexContext.getAuth();
		checkAuth(auth);
		// 入力チェック
		GroupUtil.checkGroupUri(group);
		String tmpSelfid = GroupUtil.checkGroupSelfid(selfid, group);

		UserManager userManager = TaggingEnvUtil.getUserManager();
		return userManager.joinGroup(group, tmpSelfid, reflexContext);
	}
	
	/**
	 * グループから退会する.
	 * グループエントリーの、自身のグループエイリアスを削除する。
	 * @param group グループ名
	 * @param reflexContext ReflexContext
	 * @return 退会したグループエントリー
	 */
	public EntryBase leaveGroup(String group, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 認証情報のチェック
		ReflexAuthentication auth = reflexContext.getAuth();
		checkAuth(auth);
		// 入力チェック
		CheckUtil.checkNotNull(group, "group");
		
		UserManager userManager = TaggingEnvUtil.getUserManager();
		return userManager.leaveGroup(group, reflexContext);
	}
	
	/**
	 * 管理者によるグループ退会処理.
	 * @param group グループ名
	 * @param feed UIDリスト(ユーザ管理者のみ指定可)
	 * @param reflexContext ReflexContext
	 * @return グループエントリー
	 */
	public FeedBase leaveGroupByAdmin(String group, FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// ユーザ管理者またはグループ管理者かどうかチェック
		GroupUtil.checkUseradminOrGroupadmin(reflexContext.getAuth());
		// 入力チェック
		GroupUtil.checkGroupUri(group);
		CheckUtil.checkFeed(feed, false);
		CheckUtil.checkParentUri(feed, Constants.URI_USER);
		
		UserManager userManager = TaggingEnvUtil.getUserManager();
		return userManager.removeGroup(group, feed, reflexContext);
	}

	/**
	 * 既存ユーザとソーシャルログインユーザを紐付ける
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param feed subtitleにOAuthプロバイダ、rightsにRXID
	 * @param reflexContext ReflexContext
	 * @return 更新後のユーザトップエントリー
	 */
	public EntryBase mergeOAuthUser(ReflexRequest req, ReflexResponse resp,
			FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 未ログインはエラー
		ReflexAuthentication auth = reflexContext.getAuth();
		checkAuth(auth);
		// 引数のnullチェック
		CheckUtil.checkNotNull(feed, "parameter");
		String provider = feed.subtitle;
		String rxid = feed.rights;
		// 既存ユーザとソーシャルログインユーザの紐付け処理
		OAuthManager oauthManager = TaggingEnvUtil.getOAuthManager();
		return oauthManager.mergeUser(req, resp, provider, rxid, reflexContext);
	}

	/**
	 * グループ管理者登録.
	 * @param feed ユーザ登録情報
	 * @param reflexContext ReflexContext
	 * @return グループエントリーリスト
	 */
	public FeedBase createGroupadmin(FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// ユーザ管理者かどうかチェック
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAuthedGroup(reflexContext.getAuth(), Constants.URI_GROUP_USERADMIN);

		UserManager userManager = TaggingEnvUtil.getUserManager();
		return userManager.createGroupadmin(feed, reflexContext);
	}

	/**
	 * グループ管理用グループの削除.
	 * @param feed グループ管理用グループ情報(複数指定可)
	 * @param async 削除を非同期に行う場合true
	 * @param reflexContext ReflexContext
	 */
	public void deleteGroupadmin(FeedBase feed, boolean async, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// ユーザ管理者かどうかチェック
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAuthedGroup(reflexContext.getAuth(), Constants.URI_GROUP_USERADMIN);

		// グループ管理用グループ削除
		UserManager userManager = TaggingEnvUtil.getUserManager();
		userManager.deleteGroupadmin(feed, async, reflexContext);
	}

}
