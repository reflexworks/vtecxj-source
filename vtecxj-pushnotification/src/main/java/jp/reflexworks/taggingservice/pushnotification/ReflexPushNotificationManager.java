package jp.reflexworks.taggingservice.pushnotification;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.Contributor;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.blogic.UserBlogic;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.PushNotificationManager;
import jp.reflexworks.taggingservice.pushnotification.ReflexPushNotificationConst.PushType;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * プッシュ通知管理クラス.
 * FCM (Firebase Cloud Messaging) 、またはExpoサーバを利用してプッシュ通知を実現する。
 */
public class ReflexPushNotificationManager implements PushNotificationManager {

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
	public void close() {
		// Do nothing.
	}

	/**
	 * プッシュ通知.
	 * @param entry 通知メッセージ
	 *          title: Push通知タイトル
	 *          subtitle: Push通知サブタイトル
	 *          content: Push通知メッセージ本文(body)
	 *          summary: dataに設定するメッセージ(Expo用)
	 *          category _$scheme="imageurl" _$label={通知イメージURL}(FCM用)
	 *          category _$scheme={dataのキー} _$label={dataの値}(Expo用)
	 * @param toUids 送信先UIDリスト
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void pushNotification(EntryBase entry,
			List<String> toUids, ReflexAuthentication auth, 
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		boolean isAccessLog = ReflexPushNotificationUtil.isEnableAccessLog();
		CheckUtil.checkNotNull(entry, "Push notification message");
		CheckUtil.checkNotNull(entry.getContentText(), "Push notification message");
		if (toUids == null || toUids.isEmpty()) {
			if (isAccessLog) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
						"[pushNotification] toUids is null.");
			}
			return;
		}

		String serviceName = auth.getServiceName();
		SystemContext systemContext = new SystemContext(auth, requestInfo, connectionInfo);

		// 指定されたUIDのPush通知情報(トークン等)を取得
		List<ReflexPushNotificationInfo> fcmPushInfos = new ArrayList<>();
		List<ReflexPushNotificationInfo> expoPushInfos = new ArrayList<>();
		
		for (String toUid : toUids) {
			// バッジ数を追加・取得
			Long badge = addBadge(toUid, systemContext);
			// Push通知トークンを取得
			List<ReflexPushNotificationInfo> pushNotificationInfos =
					getPushNotificationTokens(toUid, badge, systemContext);
			if (pushNotificationInfos != null) {
				for (ReflexPushNotificationInfo pushInfo : pushNotificationInfos) {
					if (PushType.FCM.equals(pushInfo.type)) {
						fcmPushInfos.add(pushInfo);
					} else if (PushType.EXPO.equals(pushInfo.type)) {
						expoPushInfos.add(pushInfo);
					}
				}
			}
		}

		boolean isDebugLog = ReflexPushNotificationUtil.isDebugLog(serviceName);
		if (isAccessLog || isDebugLog) {
			StringBuilder sb = new StringBuilder();
			sb.append("myUid=");
			sb.append(auth.getUid());
			sb.append(", toUids=");
			boolean isFirst = true;
			for (String toUid : toUids) {
				if (isFirst) {
					isFirst = false;
				} else {
					sb.append(",");
				}
				sb.append(toUid);
			}
			if (!fcmPushInfos.isEmpty()) {
				sb.append(", fcmUids=");
				isFirst = true;
				for (ReflexPushNotificationInfo pushInfo : fcmPushInfos) {
					if (isFirst) {
						isFirst = false;
					} else {
						sb.append(",");
					}
					sb.append(pushInfo.uid);
				}
			}
			if (!expoPushInfos.isEmpty()) {
				sb.append(", expoUids=");
				isFirst = true;
				for (ReflexPushNotificationInfo pushInfo : expoPushInfos) {
					if (isFirst) {
						isFirst = false;
					} else {
						sb.append(",");
					}
					sb.append(pushInfo.uid);
				}
			}
			sb.append(", message entry=");
			sb.append(TaggingEnvUtil.getResourceMapper(serviceName).toJSON(entry));
			
			if (isAccessLog) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[pushNotification] " + 
						sb.toString());
			}
		}

		// FCMメッセージ通知を送信
		if (!fcmPushInfos.isEmpty()) {
			ReflexFCMManager fcmManager = new ReflexFCMManager();
			fcmManager.pushFCM(entry, fcmPushInfos, systemContext);
		} else {
			if (isAccessLog) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
						"[pushNotification] There is no target FCM token.");
			}
		}

		// Expoメッセージ通知を送信
		if (!expoPushInfos.isEmpty()) {
			ReflexExpoManager expoManager = new ReflexExpoManager();
			expoManager.pushExpo(entry, expoPushInfos, systemContext);
		} else {
			if (isAccessLog) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
						"[pushNotification] There is no target Expo token.");
			}
		}
	}

	/**
	 * 設定されているPush通知登録トークンを取得.
	 * @param uid UID
	 * @param badge バッジ数
	 * @param systemContext SystemContext
	 * @return Push通知情報リスト
	 */
	private List<ReflexPushNotificationInfo> getPushNotificationTokens(
			String uid, Long badge, SystemContext systemContext)
	throws IOException, TaggingException {
		String pushNotificationUri = getPushNotificationUri(uid);
		EntryBase pushNotificationEntry = systemContext.getEntry(pushNotificationUri);
		List<String> fcmTokens = null;
		List<String> expoTokens = null;
		if (pushNotificationEntry != null && pushNotificationEntry.contributor != null) {
			for (Contributor contributor : pushNotificationEntry.contributor) {
				// FCM
				String fcmToken = getFCMToken(contributor);
				if (!StringUtils.isBlank(fcmToken)) {
					if (fcmTokens == null) {
						fcmTokens = new ArrayList<>();
					}
					fcmTokens.add(fcmToken);
				}

				// Expo
				String expoToken = getExpoToken(contributor);
				if (!StringUtils.isBlank(expoToken)) {
					if (expoTokens == null) {
						expoTokens = new ArrayList<>();
					}
					expoTokens.add(expoToken);
				}
			}
		}
		List<ReflexPushNotificationInfo> pushInfos = new ArrayList<>();
		if (fcmTokens != null) {
			ReflexPushNotificationInfo pushInfo = new ReflexPushNotificationInfo(uid, 
					PushType.FCM, fcmTokens, badge);
			pushInfos.add(pushInfo);
		}
		if (expoTokens != null) {
			ReflexPushNotificationInfo pushInfo = new ReflexPushNotificationInfo(uid, 
					PushType.EXPO, expoTokens, badge);
			pushInfos.add(pushInfo);
		}
		return pushInfos;
	}

	/**
	 * Push通知の登録トークン設定エントリーのURIを取得.
	 * バッジ数の管理にも使用。
	 * @param uid UID
	 * @return Push通知登録トークン設定エントリーのURI
	 */
	private String getPushNotificationUri(String uid) {
		UserBlogic userBlogic = new UserBlogic();
		String userTopUri = userBlogic.getUserTopUriByUid(uid);
		return userTopUri + ReflexPushNotificationConst.URI_PUSH_NOTIFICATION;
	}

	/**
	 * FCM登録トークンを取得.
	 * contributor.uriに設定。
	 * urn:vte.cx:fcm:{FCM登録トークン}
	 * @param contributor Contributor
	 * @return FCM登録トークン
	 */
	private String getFCMToken(Contributor contributor) {
		if (contributor == null || StringUtils.isBlank(contributor.uri)) {
			return null;
		}
		if (!contributor.uri.startsWith(ReflexPushNotificationConst.URN_PREFIX_FCM)) {
			return null;
		}
		return contributor.uri.substring(ReflexPushNotificationConst.URN_PREFIX_FCM_LEN);
	}

	/**
	 * Expo登録トークンを取得.
	 * contributor.uriに設定。
	 * urn:vte.cx:expo:{FCM登録トークン}
	 * @param contributor Contributor
	 * @return Expo登録トークン
	 */
	private String getExpoToken(Contributor contributor) {
		if (contributor == null || StringUtils.isBlank(contributor.uri)) {
			return null;
		}
		if (!contributor.uri.startsWith(ReflexPushNotificationConst.URN_PREFIX_EXPO)) {
			return null;
		}
		return contributor.uri.substring(ReflexPushNotificationConst.URN_PREFIX_EXPO_LEN);
	}

	/**
	 * バッジ数をインクリメントし、返却する。
	 * @param uid UID
	 * @param systemContext SystemContext
	 * @return バッジ数
	 */
	private Long addBadge(String uid, SystemContext systemContext)
	throws IOException, TaggingException {
		String uri = getPushNotificationUri(uid);
		FeedBase ret = systemContext.addids(uri, 1);
		return Long.parseLong(ret.title);
	}

}
