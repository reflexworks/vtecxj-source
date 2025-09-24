package jp.reflexworks.taggingservice.pushnotification;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.jav.exposerversdk.ExpoPushMessage;
import io.github.jav.exposerversdk.ExpoPushMessageTicketPair;
import io.github.jav.exposerversdk.ExpoPushTicket;
import io.github.jav.exposerversdk.PushClient;
import io.github.jav.exposerversdk.PushClientException;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * Expo server へプッシュ通知を実現する。
 */
public class ReflexExpoManager {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * Expoサーバへのプッシュ通知
	 * @param entry 通知メッセージ
	 *          title: Push通知タイトル
	 *          subtitle: Push通知サブタイトル
	 *          content: Push通知メッセージ本文(body)
	 *          summary: dataに設定するメッセージ
	 *          category _$scheme={dataのキー} _$label={dataの値}
	 * @param expoPushInfos 送信先Expoトークン等情報リスト
	 * @param reflexContext ReflexContext
	 */
	void pushExpo(EntryBase entry, 
			List<ReflexPushNotificationInfo> expoPushInfos, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		String title = entry.title;
		String subtitle = entry.subtitle;
		String body = entry.getContentText();
		String dataMessage = entry.summary;
		Map<String, String> dataMap = ReflexPushNotificationUtil.getDataMap(entry);

		int totalOkSize = 0;
		int totalErrorSize = 0;
		int totalCnt = 0;
		StringBuilder errorTicketMessagesBuffer = new StringBuilder();;
		List<Exception> exceptionList = new ArrayList<>();

		boolean isAccessLog = ReflexPushNotificationUtil.isEnableAccessLog();
		boolean isDebugLog = ReflexPushNotificationUtil.isDebugLog(serviceName);
		String fromInfo = getFromInfo(entry, expoPushInfos, auth);
		try {
			Map<String, Object> data = new HashMap<>();
			if (!StringUtils.isBlank(dataMessage) || dataMap != null) {
				if (!StringUtils.isBlank(dataMessage)) {
					data.put(ReflexPushNotificationConst.MESSAGE, dataMessage);
				}
				if (dataMap != null) {
					data.putAll(dataMap);
				}
			}

			for (ReflexPushNotificationInfo expoPushInfo : expoPushInfos) {
				ExpoPushMessage expoPushMessage = new ExpoPushMessage();
				expoPushMessage.getTo().addAll(expoPushInfo.pushTokens);
				if (!StringUtils.isBlank(title)) {
					expoPushMessage.setTitle(title);
				}
				if (!StringUtils.isBlank(subtitle)) {
					expoPushMessage.setSubtitle(subtitle);
				}
				if (!StringUtils.isBlank(body)) {
					expoPushMessage.setBody(body);
				}
				expoPushMessage.setBadge(expoPushInfo.badge);
				if (!data.isEmpty()) {
					expoPushMessage.setData(data);
				}
				List<ExpoPushMessage> expoPushMessages = new ArrayList<>();
				expoPushMessages.add(expoPushMessage);

				PushClient client = new PushClient();
				// Pushメッセージが大きい場合、サイズごとにchunkに分ける。(中のListがchunk)
				List<List<ExpoPushMessage>> chunks = client.chunkPushNotifications(expoPushMessages);
	
				List<CompletableFuture<List<ExpoPushTicket>>> messageRepliesFutures = new ArrayList<>();
	
				for (List<ExpoPushMessage> chunk : chunks) {
					messageRepliesFutures.add(client.sendPushNotificationsAsync(chunk));
				}
	
				// Wait for each completable future to finish
				List<ExpoPushTicket> allTickets = new ArrayList<>();
				List<ExpoPushMessage> allExpoPushMessages = new ArrayList<>();
				for (CompletableFuture<List<ExpoPushTicket>> messageReplyFuture : messageRepliesFutures) {
					try {
						for (ExpoPushTicket ticket : messageReplyFuture.get()) {
							allTickets.add(ticket);
							allExpoPushMessages.add(expoPushMessage);
						}
					} catch (InterruptedException e) {
						if (logger.isDebugEnabled()) {
							logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
									"[pushExpo] InterruptedException: " + e.getMessage(), e);
						}
						exceptionList.add(e);
					} catch (ExecutionException e) {
						if (logger.isDebugEnabled()) {
							logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
									"[pushExpo] ExecutionException: " + e.getMessage(), e);
						}
						exceptionList.add(e);
					}
				}
	
				// 結果の取得  
				List<ExpoPushMessageTicketPair<ExpoPushMessage>> zippedMessagesTickets =
						client.zipMessagesTickets(allExpoPushMessages, allTickets);
				int cnt = zippedMessagesTickets.size();
				totalCnt += cnt;
				
				// Push通知成功
				List<ExpoPushMessageTicketPair<ExpoPushMessage>> okTicketMessages =
						client.filterAllSuccessfulMessages(zippedMessagesTickets);
				int okSize = 0;
				if (okTicketMessages != null) {
					okSize = okTicketMessages.size();
					totalOkSize += okSize;
				}

				// Push通知失敗
				List<ExpoPushMessageTicketPair<ExpoPushMessage>> errorTicketMessages =
						client.filterAllMessagesWithError(zippedMessagesTickets);
				int errorSize = 0;
				if (errorTicketMessages != null && !errorTicketMessages.isEmpty()) {
					errorSize = errorTicketMessages.size();
					totalErrorSize += errorSize;
					StringBuilder err = new StringBuilder();
					err.append("[ErrorTicket] ");
					boolean isFirst = true;
					for (ExpoPushMessageTicketPair<ExpoPushMessage> errorTicketPair : errorTicketMessages) {
						if (isFirst) {
							isFirst = false;
						} else {
							err.append(", ");
						}
						StringBuilder tmp = new StringBuilder();
						tmp.append(errorTicketPair.ticket.getDetails().getError());
						tmp.append(":");
						tmp.append(errorTicketPair.ticket.getMessage());
						String ticketErrorMessage = tmp.toString();
						if (logger.isDebugEnabled()) {
							logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
									"[pushExpo] ticket Error. " + ticketErrorMessage);
						}
						err.append(ticketErrorMessage);
						err.append(" ");
					}
					errorTicketMessagesBuffer.append(err.toString());
				}
			}
			
			StringBuilder sb = new StringBuilder();
			sb.append("[Send] Number of successes: ");
			sb.append(totalOkSize);

			sb.append(", failures: ");
			sb.append(totalErrorSize);
			sb.append(" / ");
			sb.append(totalCnt);
			sb.append(", ");
			sb.append(fromInfo);

			String errorTicketMessagesString = errorTicketMessagesBuffer.toString();
			if (!StringUtils.isBlank(errorTicketMessagesString)) {
				sb.append(". ");
				sb.append(errorTicketMessagesString);
			}
			if (isAccessLog) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[pushExpo] " + 
						sb.toString());
			}
			if (isDebugLog) {
				reflexContext.log(ReflexPushNotificationConst.DEBUGLOG_TITLE_EXPO, 
						ReflexPushNotificationConst.DEBUGLOG_SUBTITLE, sb.toString());
				if (!exceptionList.isEmpty()) {
					for (Throwable e : exceptionList) {
						StringBuilder err = new StringBuilder();
						err.append("[Error] ");
						err.append(e.getMessage());
						err.append(", ");
						err.append(fromInfo);
						reflexContext.log(ReflexPushNotificationConst.DEBUGLOG_TITLE_EXPO, 
								ReflexPushNotificationConst.DEBUGLOG_SUBTITLE_WARN, err.toString());
					}
				}
			}

		} catch (PushClientException e) {
			if (logger.isDebugEnabled()) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
						"[pushExpo] PushClientException: " + e.getMessage());
			}
			throw new IOException(e);
		}
	}
	
	/**
	 * 送信情報を文字列化する。(ログ用)
	 * myUid、expoUids、通知メッセージを文字列にする。
	 * @param entry 通知メッセージ
	 * @param expoUids 送信先UIDリスト(Expoサーバ送信分)
	 * @param auth 認証情報
	 * @return 送信情報
	 */
	private String getFromInfo(EntryBase entry, List<ReflexPushNotificationInfo> expoUids, ReflexAuthentication auth) {
		String serviceName = auth.getServiceName();
		StringBuilder sb = new StringBuilder();
		sb.append("myUid=");
		sb.append(auth.getUid());
		sb.append(", expoUids=");
		boolean isFirst = true;
		for (ReflexPushNotificationInfo expoPushInfo : expoUids) {
			if (isFirst) {
				isFirst = false;
			} else {
				sb.append(",");
			}
			sb.append(expoPushInfo.uid);
		}
		sb.append(", message entry=");
		sb.append(TaggingEnvUtil.getResourceMapper(serviceName).toJSON(entry));
		return sb.toString();
	}

}
