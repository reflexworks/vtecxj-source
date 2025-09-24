package jp.reflexworks.taggingservice.blogic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.reflexworks.taggingservice.exception.PermissionException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.PushNotificationManager;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.reflexworks.taggingservice.util.GroupUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * メッセージ通知ビジネスロジッククラス.
 */
public class PushNotificationBlogic {

	/**
	 * メッセージ通知
	 * @param feed 通知メッセージ。entryの内容は以下の通り。
	 *          title: Push通知タイトル
	 *          subtitle: Push通知サブタイトル
	 *          content: Push通知メッセージ本文(body)
	 *          summary: dataに設定するメッセージ(Expo用)
	 *          category _$scheme="imageurl" _$label={通知イメージURL}(FCM用)
	 *          category _$scheme={dataのキー} _$label={dataの値}(Expo用)
	 * @param tos 送信先 (UID, account or group)
	 */
	public void pushNotification(FeedBase feed, String[] tos, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();

		// ログインしていない場合はエラー
		checkAuth(auth);
		// 入力チェック
		CheckUtil.checkNotNull(feed, "body");
		CheckUtil.checkNotNull(feed.entry, "body");
		for (EntryBase entry : feed.entry) {
			CheckUtil.checkNotNull(entry.getContentText(), "body");
		}
		CheckUtil.checkNotNull(tos, "destination");
		if (tos.length < 1) {
			throw new IllegalParameterException("destination element is required.");
		}

		SystemContext systemContext = new SystemContext(serviceName,
				requestInfo, connectionInfo);
		UserBlogic userBlogic = new UserBlogic();

		// 送信先をUIDリストに変更する。
		List<String> toUids = new ArrayList<>();
		for (String to : tos) {
			if (GroupUtil.isGroup(to)) {
				// グループ指定
				List<String> groupUids = GroupUtil.getGroupMemberUids(to, systemContext);
				if (groupUids != null && !groupUids.isEmpty()) {
					toUids.addAll(groupUids);
				}
			} else {
				// UIDかアカウント
				String uid = userBlogic.getUidByUser(to, systemContext);
				toUids.add(uid);
			}
		}

		PushNotificationManager pushNotificationManager = TaggingEnvUtil.getPushNotificationManager();
		if (pushNotificationManager == null) {
			throw new InvalidServiceSettingException("Push notification manager is nothing.");
		}
		for (EntryBase entry : feed.entry) {
			pushNotificationManager.pushNotification(entry, toUids,
					reflexContext.getAuth(), requestInfo, connectionInfo);
		}
	}

	/**
	 * ログインチェック.
	 * @param auth 認証情報
	 */
	private void checkAuth(ReflexAuthentication auth) throws TaggingException {
		// 認証情報なしはエラー
		String uid = auth.getUid();
		if (StringUtils.isBlank(uid)) {
			PermissionException pe = new PermissionException();
			pe.setSubMessage("Not logged in with push notification.");
			throw pe;
		}
	}

}
