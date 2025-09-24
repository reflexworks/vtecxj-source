package jp.reflexworks.taggingservice.migrate;

import java.io.IOException;
import java.util.concurrent.Future;

import jp.reflexworks.atom.api.EntryUtil.UriPair;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.ServiceManager;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBServerType;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.taskqueue.TaskQueueUtil;
import jp.reflexworks.taggingservice.requester.BDBClientServerUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * サーバ追加・削除によるデータ移行処理ユーティリティ
 */
public class MigrateUtil {

	/** Manifestキー : ルート */
	public static final String ROOT_KEY = EntryBase.TOP + Constants.END_PARENT_URI_STRING + "/";
	/** Manifestキー : システムエントリー第一階層 */
	public static final String SYSTEM_LAYER_PREFIX = "/" + Constants.END_PARENT_URI_STRING;

	/**
	 * サーバ追加・削除時の現在のサーババックアップURLを取得
	 * /_bdb/service/{サービス名}/previous_backup
	 * @param serviceName サービス名
	 * @return サーババックアップURL (バックアップフォルダまで)
	 */
	public static String getPreviousBackupUri(String serviceName) {
		StringBuilder sb = new StringBuilder();
		sb.append(Constants.URI_BDB_SERVICE);
		sb.append("/");
		sb.append(serviceName);
		sb.append(BDBClientServerConst.URI_PREVIOUS_BACKUP);
		return sb.toString();
	}

	/**
	 * 割り当て済みBDBサーバURIを取得.
	 *  /_bdb/service/{サービス名}/{mnfserver|entryserver|idxserver|ftserver|alserver}/{BDBサーバ名}
	 * @param serviceName サービス名
	 * @param serverType ServerType
	 * @param serverName サーバ名
	 * @return 割り当て済みBDBサーバURI
	 */
	public static String getAssignedServerNameUri(String serviceName, BDBServerType serverType,
			String serverName) {
		StringBuilder sb = new StringBuilder();
		sb.append(BDBClientServerUtil.getAssignedServerUri(serviceName, serverType));
		sb.append("/");
		sb.append(serverName);
		return sb.toString();
	}

	/**
	 * サーバ追加・削除時の現在のサーババックアップURLを取得
	 * /_bdb/service/{サービス名}/previous_backup/{entry|idx|ft|al}server
	 * @param serviceName
	 * @param serverType
	 * @return サーババックアップURI (サーバタイプまで)
	 */
	public static String getPreviousBackupServerTypeUri(String serviceName,
			BDBServerType serverType) {
		StringBuilder sb = new StringBuilder();
		sb.append(getPreviousBackupUri(serviceName));
		sb.append(BDBClientServerUtil.getServerTypeUri(serverType));
		return sb.toString();
	}

	/**
	 * サーバ追加・削除時の現在のサーババックアップURLを取得
	 * /_bdb/service/{サービス名}/previous_backup/{entry|idx|ft|al}server/{サーバ名}
	 * @param serviceName
	 * @param serverType
	 * @param bdbServerName
	 * @return サーババックアップURI (サーバ名まで)
	 */
	public static String getPreviousBackupServerUri(String serviceName,
			BDBServerType serverType, String bdbServerName) {
		StringBuilder sb = new StringBuilder();
		sb.append(getPreviousBackupServerTypeUri(serviceName, serverType));
		sb.append("/");
		sb.append(bdbServerName);
		return sb.toString();
	}

	/**
	 * Manifestのuriに指定する値を取得.
	 * Feed検索の範囲指定のため親階層とselfidの間の`/`のみ`\ufffe`に変換。
	 * @param uri URI
	 * @return Manifestのuriに指定する値
	 */
	public static String getManifestUri(String uri) {
		UriPair uriPair = TaggingEntryUtil.getUriPair(uri);
		StringBuilder sb = new StringBuilder();
		sb.append(TaggingEntryUtil.removeLastSlash(uriPair.parent));
		sb.append(Constants.END_PARENT_URI_STRING);
		sb.append(uriPair.selfid);
		return sb.toString();
	}

	/**
	 * Manifestのuriに指定する値を取得.
	 * Feed検索の範囲指定のため親階層とselfidの間の`/`のみ`\ufffe`に変換。
	 * @param uri URI
	 * @return Manifestのuriに指定する値
	 */
	public static String editManifestKeyUri(String keyStr) {
		if (StringUtils.isBlank(keyStr)) {
			return keyStr;
		}
		// ルートエントリー
		if (ROOT_KEY.equals(keyStr)) {
			return "/";
		} else if (keyStr.startsWith(SYSTEM_LAYER_PREFIX)) {
			return "/" + keyStr.substring(2);
		}
		return keyStr.replace(Constants.END_PARENT_URI_STRING, "/");
	}

	/**
	 * 非同期処理登録.
	 * @param callable 非同期処理
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報 (スレッド間共有オブジェクトを抽出するため設定)
	 * @return Future
	 */
	public static Future<Boolean> addTask(ReflexCallable<Boolean> callable, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// 移行処理のためサービスの設定を参照しないオプション
		return (Future<Boolean>)TaskQueueUtil.addTask(callable, false, false, true, 0,
				auth, requestInfo, connectionInfo);
	}

	/**
	 * 対象サービスのサービス管理者認証情報を生成
	 * @param serviceName 対象サービス
	 * @return 対象サービスのサービス管理者認証情報
	 */
	public static ReflexAuthentication createServiceAdminAuth(String serviceName) {
		ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
		return serviceManager.createServiceAdminAuth(serviceName);
	}

}
