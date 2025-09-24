package jp.reflexworks.taggingservice.plugin;

import java.io.IOException;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;

/**
 * WebSocket管理インターフェース.
 */
public interface WebSocketManager extends ReflexPlugin {

	/**
	 * WebSocketメッセージ送信.
	 * @param messageFeed メッセージ情報格納Feed
	 * @param channel チャネル
	 * @param myAuth 自身の認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void onMessage(FeedBase messageFeed, String channel,
			ReflexAuthentication myAuth, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

	/**
	 * WebSocket接続をクローズ.
	 * 認証ユーザのWebSocketについて、指定されたチャネルの接続をクローズする。
	 * @param channel チャネル
	 * @param myAuth 自身の認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void close(String channel, ReflexAuthentication myAuth, 
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

}
