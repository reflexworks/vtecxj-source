package jp.reflexworks.taggingservice.memorysort;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.auth.Authentication;
import jp.reflexworks.taggingservice.blogic.SessionBlogic;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.AuthenticationException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.AuthenticationManager;
import jp.reflexworks.taggingservice.plugin.DatastoreManager;
import jp.reflexworks.taggingservice.plugin.UserManager;
import jp.reflexworks.taggingservice.sys.SystemAuthentication;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * インメモリソートサーバの認証処理管理クラス.
 */
public class MemorySortAuthenticationManager implements AuthenticationManager {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * サーバ起動時の初期処理.
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
	 * 認証.
	 * @param req リクエスト
	 * @return 認証情報
	 */
	@Override
	public ReflexAuthentication autheticate(ReflexRequest req)
	throws IOException, TaggingException {
		String serviceName = req.getServiceName();
		RequestInfo requestInfo = req.getRequestInfo();
		ConnectionInfo connectionInfo = req.getConnectionInfo();
		SystemContext systemContext = new SystemContext(serviceName,
				requestInfo, connectionInfo);

		ReflexAuthentication auth = null;
		// セッション認証のみ
		SessionBlogic sessionBlogic = new SessionBlogic();
		String sessionId = sessionBlogic.getSessionIdFromRequest(req);
		if (!StringUtils.isBlank(sessionId)) {
			String uid = sessionBlogic.getUidFromSession(systemContext, sessionId);
			if (StringUtils.isBlank(uid)) {
				StringBuilder msgBld = new StringBuilder();
				msgBld.append("Session timeout or incorrect value. SID=");
				msgBld.append(sessionId);
				String msg = msgBld.toString();
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) +
							"[authenticate] " + msg);
				}
				AuthenticationException ae = new AuthenticationException();
				ae.setSubMessage(msg);
				throw ae;
			}
			// 認証に使用するユーザ情報のキャッシュ読み込み
			DatastoreManager datastoreManager = TaggingEnvUtil.getDatastoreManager();
			datastoreManager.initMainThreadUser(uid, serviceName, requestInfo, connectionInfo);
			// 認証情報を取得
			auth = sessionBlogic.getAuthFromSession(uid, systemContext, sessionId);

			boolean ret = sessionBlogic.resetExpire(auth, requestInfo, connectionInfo);
			if (!ret) {
				StringBuilder msgBld = new StringBuilder();
				msgBld.append("Session timeout. SID=");
				msgBld.append(sessionId);
				String msg = msgBld.toString();
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) +
							"[authenticate] " + msg);
				}
				AuthenticationException ae = new AuthenticationException();
				ae.setSubMessage(msg);
				throw ae;
			}
		} else {
			StringBuilder msgBld = new StringBuilder();
			msgBld.append("SessionId is null.");
			String msg = msgBld.toString();
			if (logger.isInfoEnabled()) {
				logger.info(LogUtil.getRequestInfoStr(requestInfo) +
						"[authenticate] " + msg);
			}
			AuthenticationException ae = new AuthenticationException();
			ae.setSubMessage(msg);
			throw ae;
		}

		// 認証情報にグループを付加する。
		setGroups(auth, systemContext);

		return auth;
	}

	/**
	 * 認証後の処理.
	 * 仮認証に対応。
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param auth 認証情報
	 * @return 処理を継続する場合true
	 */
	public boolean afterAutheticate(ReflexRequest req, ReflexResponse resp, ReflexAuthentication auth)
	throws IOException {
		return true;
	}

	/**
	 * セッションIDをレスポンスに設定
	 * @param req リクエスト
	 * @param resp レスポンス
	 */
	@Override
	public void setSessionIdToResponse(ReflexRequest req, ReflexResponse resp)
	throws IOException, TaggingException {
		// Do nothing.
	}

	/**
	 * セッションIDをレスポンスから削除
	 * @param req リクエスト
	 * @param resp レスポンス
	 */
	@Override
	public void deleteSessionIdFromResponse(ReflexRequest req, ReflexResponse resp)
	throws IOException, TaggingException {
		// Do nothing.
	}

	/**
	 * サービス連携認証.
	 * 対象サービス名とそのAPIKeyのチェック、及びログインユーザの登録チェックを行う。
	 * @param targetServiceName 対象サービス名
	 * @param targetApiKey 対象サービスのAPIKey
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 対象サービスでのログインユーザ認証情報
	 */
	@Override
	public ReflexAuthentication authenticateByCooperationService(
			String targetServiceName, String targetApiKey,
			ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// 対象サービス名の入力がない、または現在のサービス名と同じ場合は認証情報をそのまま返す。
		if (auth == null || StringUtils.isBlank(targetServiceName)) {
			return auth;
		}
		String serviceName = auth.getServiceName();
		if (serviceName.equals(targetServiceName)) {
			return auth;
		}

		// ログインチェック
		String account = auth.getAccount();
		if (StringUtils.isBlank(account)) {
			String msg = "Login is required.";
			AuthenticationException ae = new AuthenticationException(msg);
			throw ae;
		}

		// 対象サービスのサービスキーチェック -> TaggingserviceCoreで行うチェック

		// 対象サービスのUIDを取得
		SystemContext systemContext = new SystemContext(targetServiceName,
				requestInfo, connectionInfo);
		UserManager userManager = TaggingEnvUtil.getUserManager();
		String targetUid = userManager.getUidByAccount(account, systemContext);
		if (StringUtils.isBlank(targetUid)) {
			String msg = "The user is not registered for the specified service. service = " + targetServiceName;
			AuthenticationException ae = new AuthenticationException(msg);
			throw ae;
		}

		// 対象サービスでのグループを取得
		List<String> targetGroups = userManager.getGroupsByUid(targetUid, systemContext);
		AuthenticationManager authManager = TaggingEnvUtil.getAuthenticationManager();
		ReflexAuthentication targetAuth = authManager.createAuth(account, targetUid,
				auth.getSessionId(), auth.getLinkToken(),
				auth.getAuthType(), targetGroups, targetServiceName);

		// ユーザステータスのチェック。仮登録の場合もエラー。 -> TaggingserviceCoreで行うチェック

		return targetAuth;
	}

	/**
	 * 認証情報にグループを追加
	 * @param auth 認証情報
	 * @param systemContext SystemContext
	 */
	public void setGroups(ReflexAuthentication auth, SystemContext systemContext)
	throws IOException, TaggingException {
		String uid = auth.getUid();
		if (uid.equals(SystemAuthentication.UID_SYSTEM) ||
				uid.equals(Constants.NULL_UID)) {
			return;
		}
		UserManager userManager = TaggingEnvUtil.getUserManager();
		List<String> groups = userManager.getGroupsByUid(uid, systemContext);
		auth.clearGroup();
		if (groups != null) {
			for (String group : groups) {
				auth.addGroup(group);
			}
		}
	}

	/**
	 * 認証情報の複製.
	 * ReflexContextでExternalを設定するために使用する。
	 * @param auth 認証情報
	 * @return 複製した認証オブジェクト
	 */
	@Override
	public ReflexAuthentication copyAuth(ReflexAuthentication auth) {
		return new Authentication(auth);
	}

	/**
	 * 認証情報の生成.
	 * @param account アカウント
	 * @param uid UID
	 * @param sessionId セッションID
	 * @param authType 認証方法
	 * @param serviceName サービス名
	 * @return 生成した認証情報
	 */
	@Override
	public ReflexAuthentication createAuth(String account, String uid, String sessionId,
			String authType, String serviceName) {
		return new Authentication(account, uid, sessionId, authType, serviceName);
	}

	/**
	 * 認証情報の生成.
	 * @param account アカウント
	 * @param uid UID
	 * @param sessionId セッションID
	 * @param linkToken リンクトークン
	 * @param authType 認証方法
	 * @param serviceName サービス名
	 * @return 生成した認証情報
	 */
	@Override
	public ReflexAuthentication createAuth(String account, String uid, String sessionId,
			String linkToken, String authType, String serviceName) {
		return new Authentication(account, uid, sessionId, linkToken, authType, serviceName);
	}

	/**
	 * 認証情報の生成.
	 * @param account アカウント
	 * @param uid UID
	 * @param sessionId セッションID
	 * @param linkToken リンクトークン
	 * @param authType 認証方法
	 * @param groups 参加グループリスト
	 * @param serviceName サービス名
	 * @return 生成した認証情報
	 */
	@Override
	public ReflexAuthentication createAuth(String account, String uid, String sessionId,
			String linkToken, String authType, List<String> groups, String serviceName) {
		return new Authentication(account, uid, sessionId, linkToken, authType, groups, serviceName);
	}

}
