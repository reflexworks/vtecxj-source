package jp.reflexworks.taggingservice.migrate;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.PermissionException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.ServiceManager;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBServerType;
import jp.reflexworks.taggingservice.service.TaggingServiceUtil;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * サーバ追加・削除管理クラス
 */
public class BDBClientMaintenanceManager {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * サーバ追加.
	 * @param targetServiceName 処理対象サービス
	 * @param feed サーバ追加情報
	 * @param reflexContext システム管理サービスのReflexContext
	 */
	public void addServer(String targetServiceName, FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String systemService = reflexContext.getServiceName();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		// サービスステータスエントリーを取得
		SystemContext systemContext = new SystemContext(systemService,
				requestInfo, connectionInfo);
		checkServiceStatus(targetServiceName, false, systemContext);

		// 入力チェック
		checkFeed(feed, true);	// adding

		// バッチジョブサーバに処理依頼リクエスト
		// リクエストの戻りを待って終了する。
		String requestUri = editRequestUriAddServer(targetServiceName);
		String method = Constants.PUT;
		BDBClientMigrateUtil.requestMigrate(targetServiceName, requestUri, method, feed, reflexContext);
	}

	/**
	 * サーバ削除.
	 * @param feed サーバ削除情報
	 * @param reflexContext システム管理サービスのReflexContext (異なるサービスであればエラー)
	 */
	public void removeServer(String targetServiceName, FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String systemService = reflexContext.getServiceName();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		// サービスステータスエントリーを取得
		SystemContext systemContext = new SystemContext(systemService,
				requestInfo, connectionInfo);
		checkServiceStatus(targetServiceName, true, systemContext);

		// 入力チェック
		checkFeed(feed, false);	// removing

		// バッチジョブサーバに処理依頼リクエスト
		// リクエストの戻りを待って終了する。
		String requestUri = editRequestUriRemoveServer(targetServiceName);
		String method = Constants.PUT;
		BDBClientMigrateUtil.requestMigrate(targetServiceName, requestUri, method, feed, reflexContext);
	}

	/**
	 * サーバ追加に伴うデータ移行リクエストURIを編集.
	 *   ?_addserver
	 * @param serviceName 対象サービス名
	 * @return リクエストURI
	 */
	private String editRequestUriAddServer(String serviceName) {
		StringBuilder sb = new StringBuilder();
		sb.append("?");
		sb.append(RequestParam.PARAM_ADDSERVER);
		sb.append("=");
		sb.append(serviceName);
		return sb.toString();
	}

	/**
	 * サーバ削除に伴うデータ移行リクエストURIを編集.
	 *   ?_removeserver
	 * @param serviceName 対象サービス名
	 * @return リクエストURI
	 */
	private String editRequestUriRemoveServer(String serviceName) {
		StringBuilder sb = new StringBuilder();
		sb.append("?");
		sb.append(RequestParam.PARAM_REMOVESERVER);
		sb.append("=");
		sb.append(serviceName);
		return sb.toString();
	}

	/**
	 * サービスステータスをチェックする.
	 * productionかmaintenance_failureでなければエラー
	 * @param targetServiceName 対象サービス
	 * @param isRemove 削除の場合true (エラーメッセージ用)
	 * @param systemContext システム管理サービスのSystemContext
	 */
	private void checkServiceStatus(String targetServiceName, boolean isRemove,
			SystemContext systemContext)
	throws IOException, TaggingException {
		// サービスステータスエントリーを取得
		ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
		String serviceUri = serviceManager.getServiceUri(targetServiceName);
		EntryBase serviceEntry = systemContext.getEntry(serviceUri, false);

		String myServiceStatus = TaggingServiceUtil.getServiceStatus(serviceEntry);
		if (!Constants.SERVICE_STATUS_PRODUCTION.equals(myServiceStatus) &&
				!Constants.SERVICE_STATUS_MAINTENANCE_FAILURE.equals(myServiceStatus)) {
			String msg = null;
			if (isRemove) {
				msg = "It cannot be remove server in this status. ";
			} else {
				msg = "It cannot be add server in this status. ";
			}
			throw new PermissionException(msg + myServiceStatus);
		}
	}

	/**
	 * 入力チェック
	 * @param feed 入力Feed
	 * @param isAdding サーバ追加の場合true
	 */
	private void checkFeed(FeedBase feed, boolean isAdding) {
		// 入力チェック
		CheckUtil.checkNotNull(feed, "server infomation");
		CheckUtil.checkNotNull(feed.entry, "server infomation");

		// キー:サーバタイプ、値:サーバ名リスト
		Map<BDBServerType, Set<String>> serverMap = new HashMap<>();
		for (EntryBase entry : feed.entry) {
			CheckUtil.checkNotNull(entry.title, "server type");
			String serverTypeStr = entry.title;
			// サーバタイプのチェックと、サーバタイプURIの取得
			BDBServerType serverType = getServerType(serverTypeStr);
			String value = entry.subtitle;
			if (isAdding) {
				CheckUtil.checkNotNull(value, "server name to add");
			} else {
				CheckUtil.checkNotNull(value, "server name to remove");
			}

			// サーバ追加の場合、サーバタイプ重複は不可
			if (serverMap.containsKey(serverType)) {
				throw new IllegalParameterException("server type is duplicated. " + serverTypeStr);
			}

			Set<String> servers = serverMap.get(serverType);
			if (servers == null) {
				servers = new HashSet<>();
				serverMap.put(serverType, servers);
			}

			if (!isAdding || !StringUtils.isInteger(value)) {
				String[] valueParts = value.split(BDBClientMaintenanceConst.DELIMITER_SERVERNAME);
				for (String valuePart : valueParts) {
					// サーバ削除の場合、サーバ名重複は不可
					if (servers != null && servers.contains(valuePart)) {
						StringBuilder sb = new StringBuilder();
						sb.append("server name is duplicated. server type=");
						sb.append(serverTypeStr);
						sb.append(", server name=");
						sb.append(valuePart);
						throw new IllegalParameterException(sb.toString());
					}
					servers.add(valuePart);
				}
			} else {
				servers.add(value);
			}
		}
	}

	/**
	 * サーバタイプからサーバタイプを取得.
	 * @param serverTypeStr サーバタイプ
	 * @return サーバタイプ
	 */
	private BDBServerType getServerType(String serverTypeStr) {
		if (BDBClientServerConst.SERVERTYPE_ENTRY.equals(serverTypeStr)) {
			return BDBServerType.ENTRY;
		} else if (BDBClientServerConst.SERVERTYPE_IDX.equals(serverTypeStr)) {
			return BDBServerType.INDEX;
		} else if (BDBClientServerConst.SERVERTYPE_FT.equals(serverTypeStr)) {
			return BDBServerType.FULLTEXT;
		} else if (BDBClientServerConst.SERVERTYPE_AL.equals(serverTypeStr)) {
			return BDBServerType.ALLOCIDS;
		} else {
			throw new IllegalParameterException("server type is invalid. " + serverTypeStr);
		}
	}

}
