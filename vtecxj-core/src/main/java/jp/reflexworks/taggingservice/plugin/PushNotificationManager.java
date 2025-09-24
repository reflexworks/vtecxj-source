package jp.reflexworks.taggingservice.plugin;

import java.io.IOException;
import java.util.List;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;

/**
 * プッシュ通知管理インターフェース.
 */
public interface PushNotificationManager extends ReflexPlugin {

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
	throws IOException, TaggingException;

}
