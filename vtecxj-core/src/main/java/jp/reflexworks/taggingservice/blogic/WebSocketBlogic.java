package jp.reflexworks.taggingservice.blogic;

import java.io.IOException;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.WebSocketManager;
import jp.reflexworks.taggingservice.util.CheckUtil;

/**
 * WebSocketビジネスロジッククラス.
 * WebSocket未接続時に、ReflexContextからWebSocketメッセージ通知を行いたい場合に使用。
 */
public class WebSocketBlogic {

	/**
	 * WebSocketメッセージ送信.
	 * @param messageFeed メッセージ情報格納Feed。entryの内容は以下の通り。
	 *          summary : メッセージ
	 *          link rel="to"のhref属性 : 送信先。以下のいずれか。複数指定可。
	 *              UID
	 *              アカウント
	 *              グループ(*)
	 *              ポーリング(#)
	 *          title : WebSocket送信ができなかった場合のPush通知のtitle
	 *          subtitle : WebSocket送信ができなかった場合のPush通知のsubtitle(Expo用)
	 *          content : WebSocket送信ができなかった場合のPush通知のbody
	 *          category : WebSocket送信ができなかった場合のPush通知のdata(Expo用、key-value形式)
	 *              dataのキーに_$schemeの値、dataの値に_$labelの値をセットする。
	 *          rights : trueが指定されている場合、WebSocket送信ができなかった場合にPush通知しない。
	 * @param channel チャネル
	 * @param auth 自身の認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void onMessage(FeedBase messageFeed, String channel,
			ReflexAuthentication auth, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// 入力チェック
		CheckUtil.checkUri(channel, "channel");
		CheckUtil.checkNotNull(messageFeed, "messageFeed");
		CheckUtil.checkNotNull(messageFeed.entry, "message");

		WebSocketManager webSocketManager = TaggingEnvUtil.getWebSocketManager();
		if (webSocketManager == null) {
			throw new InvalidServiceSettingException("WebSocket manager is nothing.");
		}
		webSocketManager.onMessage(messageFeed, channel, auth, requestInfo, connectionInfo);
	}

	/**
	 * WebSocket接続をクローズ.
	 * 認証ユーザのWebSocketについて、指定されたチャネルの接続をクローズする。
	 * @param channel チャネル
	 * @param auth 自身の認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void close(String channel,
			ReflexAuthentication auth, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// 入力チェック
		CheckUtil.checkUri(channel, "channel");

		WebSocketManager webSocketManager = TaggingEnvUtil.getWebSocketManager();
		if (webSocketManager == null) {
			throw new InvalidServiceSettingException("WebSocket manager is nothing.");
		}
		webSocketManager.close(channel, auth, requestInfo, connectionInfo);
	}

}
