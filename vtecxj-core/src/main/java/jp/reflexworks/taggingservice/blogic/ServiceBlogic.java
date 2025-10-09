package jp.reflexworks.taggingservice.blogic;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.context.ReflexContextUtil;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.NotInServiceException;
import jp.reflexworks.taggingservice.exception.PermissionException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.AuthenticationManager;
import jp.reflexworks.taggingservice.plugin.ServiceManager;
import jp.reflexworks.taggingservice.plugin.UserManager;
import jp.reflexworks.taggingservice.service.TaggingServiceUtil;
import jp.reflexworks.taggingservice.sys.SystemAuthentication;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * サービス関連ビジネスロジック.
 */
public class ServiceBlogic {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 自サービス名を取得
	 * @param reflexContext ReflexContext
	 * @return 自サービス名
	 */
	public FeedBase getService(ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		FeedBase feed = TaggingEntryUtil.createFeed(serviceName);
		feed.title = serviceName;
		return feed;
	}

	/**
	 * サービス作成.
	 * @param feed 新規サービス情報
	 * @param reflexContext ReflexContext
	 * @return 作成サービス名
	 */
	public String createservice(FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// サービス作成はシステム管理サービスからのみ実行可能
		String serviceName = reflexContext.getServiceName();
		if (!TaggingEnvUtil.getSystemService().equals(serviceName)) {
			throw new IllegalParameterException("Forbidden request to this service.");
		}
		// 認証なしはエラー
		ReflexAuthentication auth = reflexContext.getAuth();
		if (auth == null || StringUtils.isBlank(auth.getUid())) {
			PermissionException pe = new PermissionException();
			pe.setSubMessage("Authentication is required for createservice.");
			throw pe;
		}

		ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
		return serviceManager.createservice(feed, reflexContext);
	}

	/**
	 * サービス削除.
	 * @param delServiceName サービス名
	 * @param reflexContext ReflexContext
	 */
	public void deleteservice(String delServiceName, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// サービス削除はシステム管理サービスからのみ実行可能
		String serviceName = reflexContext.getServiceName();
		if (!TaggingEnvUtil.getSystemService().equals(serviceName)) {
			throw new IllegalParameterException("Forbidden request to this service.");
		}

		// 入力チェック
		CheckUtil.checkNotNull(delServiceName, "Service name");
		ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
		serviceManager.checkServicename(delServiceName);

		// サービス名は小文字のみ
		delServiceName = serviceManager.editServiceName(delServiceName);

		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();

		// サービスが存在するかどうかチェック
		String serviceStatus = serviceManager.getServiceStatus(delServiceName,
				requestInfo, connectionInfo);
		if (StringUtils.isBlank(serviceStatus)) {
			throw new IllegalParameterException("The service does not exist. " + delServiceName);
		}
		// 削除済み、削除中の場合は処理しない。
		if (Constants.SERVICE_STATUS_DELETED.equals(serviceStatus)) {
			throw new IllegalParameterException("The service has been deleted. " + delServiceName);
		}
		if (Constants.SERVICE_STATUS_DELETING.equals(serviceStatus)) {
			throw new IllegalParameterException("The service is being deleted. " + delServiceName);
		}

		// 削除できるのはシステム管理サービス管理者か、指定されたサービスの管理者のみ
		checkOperateServiceAuth(delServiceName, false, reflexContext.getRequest(),
				reflexContext.getAuth(), requestInfo, connectionInfo);
		// サービス削除
		serviceManager.deleteservice(delServiceName, reflexContext);
	}

	/**
	 * サービスを操作する権限があるかどうかチェックする.
	 * システム管理サービス管理者か、指定サービスの管理者であればOK
	 * @param targetServiceName 対象サービス
	 * @param checkTargetServiceGroup 対象サービスのグループをチェックする場合true
	 * @param req リクエスト
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 指定されたサービスでの認証情報。
	 */
	public void checkOperateServiceAuth(String targetServiceName,
			boolean checkTargetServiceGroup, ReflexRequest req,
			ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		if (auth == null || StringUtils.isBlank(auth.getUid()) ||
				StringUtils.isBlank(auth.getAccount())) {
			PermissionException pe = new PermissionException();
			pe.setSubMessage("Authentication is required to operate a service.");
			throw pe;
		}
		AclBlogic aclBlogic = new AclBlogic();
		if (aclBlogic.isInTheGroup(auth, Constants.URI_GROUP_ADMIN)) {
			// システム管理サービス管理ユーザの場合は処理可能。

		} else {
			// 管理ユーザでない場合、対象ユーザの管理ユーザかどうかチェックする。
			// ただしサービスステータスが"failure"の場合は、管理ユーザの情報が残っていない場合もあるので
			// このチェックは行わない。
			String serviceStatus = getServiceStatus(targetServiceName,
					requestInfo, connectionInfo);
			boolean checkSystemService = false;
			if (StringUtils.isBlank(serviceStatus)) {
				// サービスステータスが取得できない場合、サービスの登録なし。
				throw new IllegalParameterException("The service does not exist. " + targetServiceName);
			}
			if (Constants.SERVICE_STATUS_FAILURE.equals(serviceStatus)) {
				// サービスステータスが"failure"の場合は対象サービスのチェックを行わない。
				checkSystemService = true;

			} else {
				// 対象サービスの初期設定
				initTargetService(targetServiceName, req, requestInfo, connectionInfo);
				// 対象サービスのユーザであるかどうか、サービス管理者かどうかチェック(オプション)
				SystemContext targetSystemContext = new SystemContext(targetServiceName,
						requestInfo, connectionInfo);
				UserManager userManager = TaggingEnvUtil.getUserManager();
				String account = auth.getAccount();
				String delServiceUid = userManager.getUidByAccount(account, targetSystemContext);
				if (StringUtils.isBlank(delServiceUid)) {
					if (checkTargetServiceGroup) {
						PermissionException pe = new PermissionException();
						StringBuilder sb = new StringBuilder();
						sb.append("Not registed in service. service=");
						sb.append(targetServiceName);
						sb.append(", account=");
						sb.append(account);
						pe.setSubMessage(sb.toString());
						throw pe;
					} else {
						if (logger.isDebugEnabled()) {
							logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
									"[getOperateServiceAuth] delServiceUid is null.");
						}
						checkSystemService = true;
					}
				} else {
					// 対象ユーザが対象サービスの管理者かどうか
					List<String> groups = userManager.getGroupsByUid(delServiceUid,
							targetSystemContext);
					if (groups == null || !groups.contains(Constants.URI_GROUP_ADMIN)) {
						PermissionException pe = new PermissionException();
						StringBuilder sb = new StringBuilder();
						sb.append("Not an service administrator. service=");
						sb.append(targetServiceName);
						sb.append(", account=");
						sb.append(account);
						pe.setSubMessage(sb.toString());
						throw pe;
					}
				}
			}
			if (checkSystemService) {
				// 対象サービスのデータをチェックしない場合、システム管理サービスのサービス作成者UIDをチェックする。
				SystemContext systemContext = new SystemContext(TaggingEnvUtil.getSystemService(),
						requestInfo, connectionInfo);
				String serviceUri = getServiceUri(targetServiceName);
				EntryBase serviceEntry = systemContext.getEntry(serviceUri, true);
				String uid = auth.getUid();
				boolean isAdminUid = false;
				if (serviceEntry != null) {
					if (uid.equals(serviceEntry.rights)) {
						isAdminUid = true;
					}
				}
				if (!isAdminUid) {
					PermissionException pe = new PermissionException();
					StringBuilder sb = new StringBuilder();
					sb.append("Not an service administrator. service=");
					sb.append(targetServiceName);
					sb.append(", uid=");
					sb.append(uid);
					pe.setSubMessage(sb.toString());
					throw pe;
				}
			}
		}
	}

	/**
	 * リクエストからサービス名を取得.
	 * @param req リクエスト
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return サービス名
	 */
	public String getMyServiceName(HttpServletRequest req, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
		return serviceManager.getMyServiceName(req, requestInfo, connectionInfo);
	}

	/**
	 * APIKeyを取得.
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return APIKey
	 */
	public String getAPIKey(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
		return serviceManager.getAPIKey(serviceName, requestInfo, connectionInfo);
	}

	/**
	 * サービスキーを取得.
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return サービスキー
	 */
	public String getServiceKey(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
		return serviceManager.getServiceKey(serviceName, requestInfo, connectionInfo);
	}

	/**
	 * APIKeyを変更.
	 * APIKeyを再発行します.
	 * @param reflexContext ReflexContext
	 * @return 新たに発行したAPIKey
	 */
	public String changeAPIKey(ReflexContext reflexContext)
	throws IOException, TaggingException {
		// サービス管理者かどうか
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAuthedGroup(reflexContext.getAuth(), Constants.URI_GROUP_ADMIN);
		// APIKeyを更新
		ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
		return serviceManager.changeAPIKey(reflexContext);
	}

	/**
	 * サービスキーを変更.
	 * サービスキーを再発行します.
	 * @param reflexContext ReflexContext
	 * @return 新たに発行したサービスキー
	 */
	public String changeServiceKey(ReflexContext reflexContext)
	throws IOException, TaggingException {
		// サービス管理者かどうか
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAuthedGroup(reflexContext.getAuth(), Constants.URI_GROUP_ADMIN);
		// サービスキーを更新
		ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
		return serviceManager.changeServiceKey(reflexContext);
	}

	/**
	 * サービスのURIを取得.
	 * @param serviceName サービス名
	 * @return サービスのURL (/_service/{サービス名})
	 */
	public String getServiceUri(String serviceName) {
		ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
		return serviceManager.getServiceUri(serviceName);
	}

	/**
	 * システム管理サービスから指定されたサービスへリダイレクトする際のURLを取得.
	 * URLはコンテキストパスまでを返却する。
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 指定されたサービスのURL(コンテキストパスまで)
	 */
	public String getRedirectUrlContextPath(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
		return serviceManager.getRedirectUrlContextPath(serviceName, requestInfo,
				connectionInfo);
	}

	/**
	 * サービスごとのホスト名を取得.
	 * @param serviceName サービス名
	 * @return ホスト名
	 */
	public String getHost(String serviceName) {
		ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
		return serviceManager.getHost(serviceName);
	}

	/**
	 * サービスのステータスを取得.
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return サービスのステータス
	 */
	public String getServiceStatus(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
		return serviceManager.getServiceStatus(serviceName, requestInfo, connectionInfo);
	}

	/**
	 * サービス公開
	 * @param targetServiceName 公開サービス
	 * @param reflexContext ReflexContext
	 * @return 公開したサービス名
	 */
	public String serviceToProduction(String targetServiceName, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 実行サービスがシステム管理サービスでなければエラー
		String serviceName = reflexContext.getServiceName();
		if (!TaggingEnvUtil.getSystemService().equals(serviceName)) {
			throw new IllegalParameterException("Forbidden request to this service.");
		}
		// BaaSでない場合処理を抜ける
		if (!TaggingServiceUtil.isBaaS()) {
			throw new IllegalParameterException("Invalid request.");
		}
		// 認証なしはエラー
		ReflexAuthentication auth = reflexContext.getAuth();
		if (auth == null || StringUtils.isBlank(auth.getUid())) {
			PermissionException pe = new PermissionException();
			pe.setSubMessage("Authentication is required for productionservice.");
			throw pe;
		}

		// 入力チェック
		CheckUtil.checkNotNull(targetServiceName, "Service name");

		// サービス名は小文字のみ
		ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
		targetServiceName = serviceManager.editServiceName(targetServiceName);

		// ステータス更新できるのはシステム管理サービス管理者か、指定されたサービスの管理者のみ
		checkOperateServiceAuth(targetServiceName, true, reflexContext.getRequest(),
				auth, reflexContext.getRequestInfo(), reflexContext.getConnectionInfo());

		// サービス公開
		return serviceManager.serviceToProduction(targetServiceName, reflexContext);
	}

	/**
	 * サービス非公開
	 * @param targetServiceName 公開サービス
	 * @param reflexContext ReflexContext
	 * @return 非公開にしたサービス名
	 */
	public String serviceToStaging(String targetServiceName, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 実行サービスがシステム管理サービスでなければエラー
		String serviceName = reflexContext.getServiceName();
		if (!TaggingEnvUtil.getSystemService().equals(serviceName)) {
			throw new IllegalParameterException("Forbidden request to this service.");
		}
		// BaaSでない場合処理を抜ける
		if (!TaggingServiceUtil.isBaaS()) {
			throw new IllegalParameterException("Invalid request.");
		}
		// 認証なしはエラー
		ReflexAuthentication auth = reflexContext.getAuth();
		if (auth == null || StringUtils.isBlank(auth.getUid())) {
			PermissionException pe = new PermissionException();
			pe.setSubMessage("Authentication is required for productionservice.");
			throw pe;
		}

		// 入力チェック
		CheckUtil.checkNotNull(targetServiceName, "Service name");

		// サービス名は小文字のみ
		ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
		targetServiceName = serviceManager.editServiceName(targetServiceName);

		// ステータス更新できるのはシステム管理サービス管理者か、指定されたサービスの管理者のみ
		checkOperateServiceAuth(targetServiceName, true, reflexContext.getRequest(),
				auth, reflexContext.getRequestInfo(), reflexContext.getConnectionInfo());

		// サービス非公開
		return serviceManager.serviceToStaging(targetServiceName, reflexContext);
	}

	/**
	 * 先頭が/@/で始まるURIの場合、システム管理サービスの認証情報を作成する.
	 * @param uri URI
	 * @param auth 認証情報
	 * @return URIに合わせた認証情報
	 */
	public ReflexAuthentication getAuthForGet(String uri, ReflexAuthentication auth) {
		String serviceName = auth.getServiceName();
		String systemService = TaggingEnvUtil.getSystemService();
		// 先頭が/@/で始まるURIの場合、システム管理サービスのデータを読む。
		ReflexAuthentication tmpAuth = null;
		if (uri.startsWith(Constants.URI_SYSTEM_REFERENCE) &&
				!systemService.equals(serviceName)) {
			// システム管理サービス
			// グループはそのまま引き継ぐ
			if (auth instanceof SystemAuthentication) {
				tmpAuth = new SystemAuthentication(auth.getAccount(), auth.getUid(),
						systemService);
			} else {
				AuthenticationManager authManager = TaggingEnvUtil.getAuthenticationManager();
				tmpAuth = authManager.createAuth(auth.getAccount(), auth.getUid(),
						auth.getSessionId(), auth.getLinkToken(), auth.getAuthType(),
						auth.getGroups(), systemService);
			}
		} else {
			// 通常のサービス
			tmpAuth = auth;
		}
		return tmpAuth;
	}

	/**
	 * 先頭が/@/で始まるURIの場合、システム管理サービスの認証情報を保持したReflexContext作成する.
	 * @param uri URI
	 * @param reflexContext ReflexContext
	 * @return URIに合わせたReflexContext
	 */
	public ReflexContext getReflexContextForGet(String uri, ReflexContext reflexContext) {
		ReflexAuthentication currentAuth = reflexContext.getAuth();
		String currentServiceName = currentAuth.getServiceName();
		String systemService = TaggingEnvUtil.getSystemService();
		// システム管理サービスの場合はそのまま返す。
		if (systemService.equals(currentServiceName)) {
			return reflexContext;
		}
		ReflexAuthentication auth = getAuthForGet(uri, currentAuth);
		if (systemService.equals(auth.getServiceName())) {
			// /@/から始まるURI。
			// システム管理サービス用のReflexContextを作成。
			return ReflexContextUtil.getReflexContext(auth,
					reflexContext.getRequestInfo(), reflexContext.getConnectionInfo());

		} else {
			// 一般サービス。そのまま返す。
			return reflexContext;
		}
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
	public ReflexAuthentication authenticateByCooperationService(
			String targetServiceName, String targetApiKey,
			ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		AuthenticationManager authManager = TaggingEnvUtil.getAuthenticationManager();
		return authManager.authenticateByCooperationService(targetServiceName, targetApiKey,
				auth, requestInfo, connectionInfo);
	}

	/**
	 * サービス連携時の対象サービス初期設定
	 * @param targetServiceName 対象サービス名
	 * @param req リクエスト
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void initTargetService(String targetServiceName, ReflexRequest req,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// サービスが稼働していなければエラー
		ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
		boolean isEnabledService = serviceManager.isEnabled(req, targetServiceName,
				requestInfo, connectionInfo);
		if (!isEnabledService) {
			throw new NotInServiceException(targetServiceName);
		}
		// このノードでサービス情報を保持していない場合、設定処理を行う。
		serviceManager.settingServiceIfAbsent(targetServiceName, requestInfo,
					connectionInfo);
	}

	/**
	 * このリクエストがsecure(https)かどうか判定.
	 * @param req リクエスト
	 * @return このリクエストがsecure(https)の場合true
	 */
	public boolean isSecure(ReflexRequest req)
	throws IOException, TaggingException {
		// サービスステータスが"production"の場合https通信なのでsecure属性を付加する。
		String serviceStatus = getServiceStatus(req.getServiceName(),
				req.getRequestInfo(), req.getConnectionInfo());
		return Constants.SERVICE_STATUS_PRODUCTION.equals(serviceStatus);
	}

	/**
	 * サービスに関するStatic情報を更新する必要があるかどうかチェックする.
	 * @param targetServiceName 対象サービス名
	 * @param accessTime Static情報更新時間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return サービスエントリー
	 */
	public boolean isNeedToUpdateStaticInfo(String targetServiceName, Date accessTime,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
		return serviceManager.isNeedToUpdateStaticInfo(targetServiceName, accessTime,
				requestInfo, connectionInfo);
	}

}
