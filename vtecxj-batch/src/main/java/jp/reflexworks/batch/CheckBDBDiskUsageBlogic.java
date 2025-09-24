package jp.reflexworks.batch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.Contributor;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexBlogic;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.bdbclient.BDBResponseInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBServerType;
import jp.reflexworks.taggingservice.requester.BDBClientServerUtil;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.NamespaceUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * BDBディスク使用量チェック処理.
 * 使用量が指定の割合を超えている場合、アラートメールを送信する。
 */
public class CheckBDBDiskUsageBlogic implements ReflexBlogic<ReflexContext, Boolean> {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 実行処理.
	 * @param reflexContext ReflexContext
	 * @param args 引数 (ReflexApplication実行時の引数の4番目以降)
	 *             [0]BDBサーバ名・URL一覧ファイル名(フルパス)
	 *             [1]サーバタイプ(mnf,entry,idx,ft,al)
	 */
	public Boolean exec(ReflexContext reflexContext, String[] args) {
		// 引数チェック
		if (args == null || args.length < 2) {
			throw new IllegalArgumentException("引数を指定してください。[0]BDBサーバ名・URL一覧ファイル名(フルパス)、[1]サーバタイプ(mnf,entry,idx,ft,al)");
		}
		String filepath = args[0];
		if (StringUtils.isBlank(filepath)) {
			throw new IllegalArgumentException("引数を指定してください。[0]BDBサーバ名・URL一覧ファイル名(フルパス)");
		}
		String serverTypeStr = args[1];
		if (StringUtils.isBlank(serverTypeStr)) {
			throw new IllegalArgumentException("引数を指定してください。[1]サーバタイプ(mnf,entry,idx,ft,al)");
		}
		BDBServerType serverType = BDBClientServerUtil.getServerTypeByStr(serverTypeStr);	// サーバタイプチェック
		if (serverType == null) {
			throw new IllegalArgumentException("引数[1]サーバタイプには (mnf,entry,idx,ft,al) のいずれかを指定してください。");
		}

		// サービス名がシステム管理サービスでなければエラー
		String systemService = reflexContext.getServiceName();
		if (!systemService.equals(TaggingEnvUtil.getSystemService())) {
			throw new IllegalStateException("Please specify system service for the service name.");
		}

		// SystemContext作成
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		SystemContext systemContext = new SystemContext(systemService,
				requestInfo, reflexContext.getConnectionInfo());

		try {
			// BDBサーバ名・URLを取得
			Map<String, String> serverUrlMap = VtecxBatchUtil.getKeyValueList(
					filepath, VtecxBatchConst.DELIMITER_SERVER_URL);
			if (serverUrlMap == null || serverUrlMap.isEmpty()) {
				throw new IllegalStateException("No BDB server. serverType : " + serverType.name());
			}
			
			// 各サーバにディスク使用量取得リクエスト
			Map<String, Future<BDBResponseInfo<FeedBase>>> futureMap = new HashMap<>();
			for (Map.Entry<String, String> mapEntry : serverUrlMap.entrySet()) {
				String serverName = mapEntry.getKey();
				String serverUrl = mapEntry.getValue();
				Future<BDBResponseInfo<FeedBase>> future = request(serverType, serverName, 
						serverUrl, systemContext);
				futureMap.put(serverName, future);
			}
			
			// ディスク使用量チェック。指定割合を超えていればアラートメールを送信する。
			for (Map.Entry<String, Future<BDBResponseInfo<FeedBase>>> mapEntry : futureMap.entrySet()) {
				String serverName = mapEntry.getKey();
				Future<BDBResponseInfo<FeedBase>> future = mapEntry.getValue();
				String logPrefix = "[exec] serverName=" + serverName + " ";
				try {
					BDBResponseInfo<FeedBase> responseInfo = future.get();
					FeedBase feed = responseInfo.data;
					String diskUsageStr = null;
					if (feed != null) {
						diskUsageStr = feed.title;
						if (logger.isTraceEnabled()) {
							logger.debug(logPrefix + "diskUsage=" + diskUsageStr);
						}
					} else {
						if (logger.isDebugEnabled()) {
							logger.debug(logPrefix + "feed is null.");
						}
					}
					checkDiskUsage(serverName, diskUsageStr, systemContext);

				} catch (ExecutionException | InterruptedException e) {
					StringBuilder sb = new StringBuilder();
					sb.append(logPrefix);
					sb.append(e.getClass().getSimpleName());
					sb.append(": ");
					sb.append(e.getMessage());
					logger.warn(sb.toString(), e);
				}
			}

		} catch (IOException | TaggingException e) {
			throw new RuntimeException(e);
		}
		return true;
	}

	/**
	 * BDBディスク使用量チェック処理.
	 * 使用量が指定の割合を超えている場合、アラートメールを送信する。
	 * @param serverType サーバタイプ
	 * @param serverName サーバ名
	 * @param serverUrl サーバURL
	 * @param systemContext SystemContext
	 * @return Future
	 */
	private Future<BDBResponseInfo<FeedBase>> request(BDBServerType serverType, String serverName, 
			String serverUrl, SystemContext systemContext)
	throws IOException, TaggingException {
		String systemService = systemContext.getServiceName();
		RequestInfo requestInfo = systemContext.getRequestInfo();
		ConnectionInfo connectionInfo = systemContext.getConnectionInfo();
		String namespace = NamespaceUtil.getNamespace(systemService, requestInfo, connectionInfo);
		String requestUrl = getRequestUrl(serverUrl);
		return VtecxBatchUtil.request(systemContext, Constants.GET, requestUrl, namespace);
	}
	
	/**
	 * リクエストURLを取得
	 *  {serverUrl}?_diskusage
	 * @param serverUrl サーバURL
	 * @return リクエストURL
	 */
	private String getRequestUrl(String serverUrl) {
		StringBuilder sb = new StringBuilder();
		sb.append(serverUrl);
		sb.append("?");
		sb.append(RequestParam.PARAM_DISKUSAGE);
		return sb.toString();
	}
	
	/**
	 * ディスク使用率のチェック.
	 * 指定されたディスク使用率を超えた場合、アラートメールを送信する。
	 * @param serverName サーバ名
	 * @param diskUsageStr ディスク使用率(%)
	 * @param systemContext SystemContext
	 */
	private void checkDiskUsage(String serverName, String diskUsageStr, SystemContext systemContext) 
	throws IOException, TaggingException {
		String logPrefix = "[checkDiskUsage] serverName=" + serverName;
		if (StringUtils.isBlank(diskUsageStr)) {
			logger.warn(logPrefix + " : disk usage is null.");
			return;
		}
		if (!StringUtils.isInteger(diskUsageStr)) {
			logger.warn(logPrefix + " : disk usage is not integer. " + diskUsageStr);
			return;
		}
		int diskUsage = Integer.parseInt(diskUsageStr);
		if (diskUsage < 0) {
			logger.warn(logPrefix + " : disk usage is a negative number. " + diskUsageStr);
			return;
		}
		
		int alertUsage = TaggingEnvUtil.getSystemPropInt(BatchBDBConst.DISK_USAGE_ALERT, 
				BatchBDBConst.DISK_USAGE_ALERT_DEFAULT);
		if (logger.isDebugEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(logPrefix);
			sb.append(", diskUsage=");
			sb.append(diskUsageStr);
			sb.append(", alertUsage=");
			sb.append(alertUsage);
			logger.debug(sb.toString());
		}
		if (diskUsage >= alertUsage) {
			// メール送信
			sendAlertMail(serverName, diskUsageStr, systemContext);
		}
	}
	
	/**
	 * アラートメール送信
	 * @param serverName サーバ名
	 * @param diskUsageStr ディスク使用率(%)
	 * @param systemContext SystemContext
	 */
	private void sendAlertMail(String serverName, String diskUsageStr, SystemContext systemContext) 
	throws IOException, TaggingException {
		// メール内容は /_settings/diskusage_alert から取得する
		EntryBase mailEntry = systemContext.getEntry(BatchBDBConst.URI_SETTINGS_DISKUSAGE_ALERT);
		if (mailEntry == null) {
			logger.warn("[sendAlertMail] setting does not exist. " + BatchBDBConst.URI_SETTINGS_DISKUSAGE_ALERT);
			return;
		}

		// 送信先
		List<String> tmpEmails = new ArrayList<>();
		if (mailEntry.contributor != null) {
			for (Contributor contributor : mailEntry.contributor) {
				if (!StringUtils.isBlank(contributor.email)) {
					tmpEmails.add(contributor.email);
				}
			}
		}
		if (tmpEmails.isEmpty()) {
			logger.warn("[sendAlertMail] destination does not exist. " + BatchBDBConst.URI_SETTINGS_DISKUSAGE_ALERT);
			return;
		}
		String[] to = tmpEmails.toArray(new String[0]);

		// メッセージ編集
		String title = mailEntry.title;
		String textMessage = mailEntry.summary;
		String htmlMessage = mailEntry.getContentText();
		
		// 値変換
		textMessage = replace(textMessage, BatchBDBConst.REPLACE_REGEX_SERVERNAME, serverName);
		textMessage = replace(textMessage, BatchBDBConst.REPLACE_REGEX_DISKUSAGE, diskUsageStr);
		htmlMessage = replace(htmlMessage, BatchBDBConst.REPLACE_REGEX_SERVERNAME, serverName);
		htmlMessage = replace(htmlMessage, BatchBDBConst.REPLACE_REGEX_DISKUSAGE, diskUsageStr);

		systemContext.sendHtmlMail(title, textMessage, htmlMessage, to, null, null, null);
	}

	/**
	 * メッセージの指定部分を指定された値に変換.
	 * @param message メッセージ
	 * @param replaceRegex 変換箇所
	 * @param val 値
	 * @return 変換したメッセージ
	 */
	public String replace(String message, String replaceRegex, String val) {
		return StringUtils.replaceAll(message, replaceRegex, 
				StringUtils.null2blank(val));
	}

}
