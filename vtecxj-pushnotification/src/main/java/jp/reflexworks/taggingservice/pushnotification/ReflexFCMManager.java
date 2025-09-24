package jp.reflexworks.taggingservice.pushnotification;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContentInfo;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.RetryUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * FCM (Firebase Cloud Messaging) でプッシュ通知を実現する。
 */
public class ReflexFCMManager {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * FCMプッシュ通知
	 * @param entry 通知メッセージ
	 *          title: Push通知タイトル
	 *          content: Push通知メッセージ本文(body)
	 *          category _$scheme="imageurl" _$label={通知イメージURL}(FCM用) その他はdataに設定。
	 * @param fcmPushInfos 送信先FCMトークン等情報リスト
	 * @param systemContext SystemContext
	 */
	void pushFCM(EntryBase entry, 
			List<ReflexPushNotificationInfo> fcmPushInfos,
			SystemContext systemContext)
	throws IOException, TaggingException {
		String serviceName = systemContext.getServiceName();
		ReflexAuthentication auth = systemContext.getAuth();
		RequestInfo requestInfo = systemContext.getRequestInfo();

		EntryBase privateKeyEntry = getFirebasePrivateKeyEntry(systemContext);
		if (privateKeyEntry == null) {
			throw new InvalidServiceSettingException("Firebase private key is required.");
		}
		
		// TODO (2022.11.16)badge未対応。
		//      必要になったら fcmPushInfos 内のbadge情報を使用する。
		//      その際、メッセージごとに送信処理を行うよう改修が必要になる。
		//      また v1 への移行が必要になるかもしれない。
		
		List<String> fcmTokens = new ArrayList<>();
		List<String> fcmUids = new ArrayList<>();
		for (ReflexPushNotificationInfo pushInfo : fcmPushInfos) {
			fcmTokens.addAll(pushInfo.pushTokens);
			fcmUids.add(pushInfo.uid);
		}
		
		FirebaseApp firebaseApp = null;
		String firebaseAppName = getFirebaseAppName(privateKeyEntry, serviceName);
		try {
			firebaseApp = FirebaseApp.getInstance(firebaseAppName);
		} catch (IllegalStateException | NullPointerException e) {
			// このサービス・サーバではFirebase生成なし
			if (logger.isTraceEnabled()) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
						"[pushFCM] [FirebaseApp.getInstance] IllegalStateException(InvalidServiceSettingException): " + e.getMessage());
			}
		}

		// Firebaseオブジェクトが未生成か、/_settings/firebase.json エントリーが更新された場合
		if (firebaseApp == null) {
			try {
				FirebaseOptions firebaseOptions = getFirebaseOptions(systemContext);
				if (firebaseOptions == null) {
					throw new InvalidServiceSettingException("Firebase service account setting is required.");
				}
				firebaseApp = FirebaseApp.initializeApp(firebaseOptions, firebaseAppName);
					
			} catch (IllegalStateException e) {
				// 他のスレッドでFirebase登録
				if (logger.isDebugEnabled()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
							"[pushFCM] [FirebaseApp.initializeApp] IllegalStateException: " + e.getMessage());
				}
				firebaseApp = FirebaseApp.getInstance(firebaseAppName);
				if (firebaseApp == null) {
					throw e;
				}
			}
		}

		String title = entry.title;
		String body = entry.getContentText();
		String imageUrl = null;
		Map<String, String> dataMap = ReflexPushNotificationUtil.getDataMap(entry);
		if (dataMap != null && dataMap.containsKey(ReflexPushNotificationConst.IMAGEURL)) {
			imageUrl = dataMap.get(ReflexPushNotificationConst.IMAGEURL);
			dataMap.remove(ReflexPushNotificationConst.IMAGEURL);
		}
		
		Notification.Builder nBuilder = Notification.builder().setBody(body);
		if (!StringUtils.isBlank(title)) {
			nBuilder.setTitle(title);
		}
		if (!StringUtils.isBlank(imageUrl)) {
			nBuilder.setImage(imageUrl);
		}
		Notification notification = nBuilder.build();

		MulticastMessage.Builder multicastBuilder = MulticastMessage.builder();
		multicastBuilder.setNotification(notification);
		multicastBuilder.addAllTokens(fcmTokens);
		if (dataMap != null && !dataMap.isEmpty()) {
			multicastBuilder.putAllData(dataMap);
		}
		MulticastMessage fcmMessage = multicastBuilder.build();

		boolean isAccessLog = ReflexPushNotificationUtil.isEnableAccessLog();
		boolean isDebugLog = ReflexPushNotificationUtil.isDebugLog(serviceName);
		String fromInfo = getFromInfo(entry, fcmUids, auth);
		// リトライ回数
		int numRetries = getFcmPushRetryCount();
		int waitMillis = getFcmPushRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				// Send a message to the device corresponding to the provided
				// registration token.
				BatchResponse response = FirebaseMessaging.getInstance(firebaseApp).sendMulticast(
						fcmMessage);

				// See the BatchResponse reference documentation
				// for the contents of response.
				int successCnt = response.getSuccessCount();
				int failureCnt = response.getFailureCount();
				int totalCnt = successCnt + failureCnt;
				if (isAccessLog || isDebugLog) {
					StringBuilder sb = new StringBuilder();
					sb.append("[send] Number of successes: ");
					sb.append(successCnt);
					sb.append(", failures: ");
					sb.append(failureCnt);
					sb.append(" / ");
					sb.append(totalCnt);
					sb.append(", ");
					sb.append(fromInfo);

					if (failureCnt > 0) {
						List<SendResponse> sendResponses = response.getResponses();
						if (sendResponses != null) {
							sb.append(" [ErrorResponse]");
							boolean isFirst = true;
							for (SendResponse sendResp : sendResponses) {
								FirebaseMessagingException fcmException = sendResp.getException();
								if (fcmException != null) {
									if (isFirst) {
										isFirst = false;
									} else {
										sb.append("; ");
									}
									StringBuilder exsb = new StringBuilder();
									exsb.append(fcmException.getMessage());
									exsb.append(", ErrorCode=");
									exsb.append(fcmException.getErrorCode());
									exsb.append(", MessagingErrorCode=");
									exsb.append(fcmException.getMessagingErrorCode());
									String exStr = exsb.toString();
									sb.append(exStr);
									if (isAccessLog) {
										StringBuilder acsb = new StringBuilder();
										acsb.append(LogUtil.getRequestInfoStr(requestInfo));
										acsb.append("[pushFCM] [send]");
										acsb.append(fcmException.getClass().getName());
										acsb.append(" : ");
										acsb.append(exStr);
										logger.debug(acsb.toString());
									}
								}
							}
						}
					}

					if (isAccessLog) {
						logger.debug(LogUtil.getRequestInfoStr(requestInfo) + 
								"[pushFCM] " + sb.toString());
					}
					if (isDebugLog) {
						systemContext.log(ReflexPushNotificationConst.DEBUGLOG_TITLE_FCM, 
								ReflexPushNotificationConst.DEBUGLOG_SUBTITLE, sb.toString());
					}
				}
				break;

			} catch (FirebaseMessagingException e) {
				// エラー判定
				if (logger.isDebugEnabled()) {
					StringBuilder sb = new StringBuilder();
					sb.append(LogUtil.getRequestInfoStr(systemContext.getRequestInfo()));
					sb.append("[pushFCM] FirebaseMessagingException errorCode=");
					sb.append(e.getErrorCode());
					sb.append(", messagingErrorCode=");
					sb.append(e.getMessagingErrorCode());
					sb.append(", ");
					sb.append(e.getMessage());
					logger.debug(sb.toString());
				}
				
				boolean isRetry = false;
				if (r < numRetries) {
					isRetry = isRetryError(e);
				}
				if (isRetry) {
					if (logger.isDebugEnabled()) {
						logger.debug(LogUtil.getRequestInfoStr(requestInfo) + 
								RetryUtil.getRetryLog(e, r));
					}
					RetryUtil.sleep(waitMillis);
				} else {
					throw new IOException(e);
				}
			}
		}
	}

	/**
	 * サービスアカウント秘密鍵JSONよりFirebaseの認証情報を取得.
	 * @param systemContext SystemContext
	 * @return Firebaseの認証情報
	 */
	private FirebaseOptions getFirebaseOptions(SystemContext systemContext)
	throws IOException, TaggingException {
		byte[] secret = null;
		ReflexContentInfo contentInfo = systemContext.getContent(
				ReflexPushNotificationConst.URI_SECRET_JSON);
		if (contentInfo != null) {
			secret = contentInfo.getData();
		}
		if (secret != null && secret.length > 0) {
			try {
				ByteArrayInputStream in = new ByteArrayInputStream(secret);
				return FirebaseOptions.builder().setCredentials(
						GoogleCredentials.fromStream(in)).build();
			} catch (IOException e) {
				if (logger.isDebugEnabled()) {
					StringBuilder sb = new StringBuilder();
					sb.append(LogUtil.getRequestInfoStr(systemContext.getRequestInfo()));
					sb.append("[getFirebaseOptions] ");
					sb.append(e.getClass().getName());
					sb.append(": ");
					sb.append(e.getMessage());
					logger.debug(sb.toString());
				}
				// サービス設定不正のため InvalidServiceSettingException でラップする。
				throw new InvalidServiceSettingException(e);
			}
		}
		return null;
	}
	
	/**
	 * 送信情報を文字列化する。(ログ用)
	 * myUid、fcmUids、通知メッセージを文字列にする。
	 * @param entry 通知メッセージ
	 * @param fcmUids 送信先UIDリスト(FCM送信分)
	 * @param auth 認証情報
	 * @return 送信情報
	 */
	private String getFromInfo(EntryBase entry, List<String> fcmUids, ReflexAuthentication auth) {
		String serviceName = auth.getServiceName();
		StringBuilder sb = new StringBuilder();
		sb.append("myUid=");
		sb.append(auth.getUid());
		sb.append(", fcmUids=");
		boolean isFirst = true;
		for (String fcmUid : fcmUids) {
			if (isFirst) {
				isFirst = false;
			} else {
				sb.append(",");
			}
			sb.append(fcmUid);
		}
		sb.append(", message entry=");
		sb.append(TaggingEnvUtil.getResourceMapper(serviceName).toJSON(entry));
		return sb.toString();
	}
	
	/**
	 * Firebase秘密鍵を取得.
	 *  /_settings/firebase.json エントリー を取得.
	 * @param systemContext SystemContext
	 * @return Firebase秘密鍵
	 */
	private EntryBase getFirebasePrivateKeyEntry(SystemContext systemContext) 
	throws IOException, TaggingException {
		return systemContext.getEntry(ReflexPushNotificationConst.URI_SECRET_JSON);
	}

	/**
	 * FirebaseApp名を取得.
	 * {サービス名}#{更新日時}#{revision}。
	 * FirebaseAppは一度作成すると更新できないので、リビジョンごとに生成する必要がある。
	 * @param entry /_settings/firebase.json エントリー
	 * @param serviceName サービス名
	 * @return FirebaseApp名
	 */
	private String getFirebaseAppName(EntryBase entry, String serviceName) {
		if (entry == null) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		sb.append(serviceName);
		sb.append("#");
		sb.append(entry.updated);
		sb.append("#");
		sb.append(TaggingEntryUtil.getRevisionById(entry.id));
		return sb.toString();
	}

	/**
	 * FCM Push通知失敗時リトライ総数を取得.
	 * @return FCM Push通知失敗時リトライ総数
	 */
	private int getFcmPushRetryCount() {
		return TaggingEnvUtil.getSystemPropInt(
				ReflexPushNotificationConst.FCM_PUSH_RETRY_COUNT,
				ReflexPushNotificationConst.FCM_PUSH_RETRY_COUNT_DEFAULT);
	}

	/**
	 * FCM Push通知失敗でリトライ時のスリープ時間(ミリ秒)を取得.
	 * @return FCM Push通知失敗でリトライ時のスリープ時間(ミリ秒)
	 */
	private int getFcmPushRetryWaitmillis() {
		return TaggingEnvUtil.getSystemPropInt(
				ReflexPushNotificationConst.FCM_PUSH_RETRY_WAITMILLIS,
				ReflexPushNotificationConst.FCM_PUSH_RETRY_WAITMILLIS_DEFAULT);
	}
	
	/**
	 * FCM送信失敗を分析し、リトライ可能か判定する.
	 * @param e FirebaseMessagingException
	 * @return リトライ可能の場合true
	 */
	private boolean isRetryError(FirebaseMessagingException e) {
		// タイムアウトの場合リトライする
		String errMsg = e.getMessage();
		if (StringUtils.isBlank(errMsg)) {
			return false;
		}
		String errMsgLower = errMsg.toLowerCase(Locale.ENGLISH);
		if (errMsgLower.indexOf("timed out") > -1) {
			return true;
		}
		
		return false;
	}

}
