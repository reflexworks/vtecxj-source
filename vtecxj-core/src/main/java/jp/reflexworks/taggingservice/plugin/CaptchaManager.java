package jp.reflexworks.taggingservice.plugin;

import java.io.IOException;

import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.exception.TaggingException;

/**
 * キャプチャ管理クラス.
 */
public interface CaptchaManager extends ReflexPlugin {
	
	/**
	 * キャプチャ判定.
	 * @param req リクエスト
	 * @param action アクション
	 * @throws AuthenticationException キャプチャ認証エラー
	 */
	public void verify(ReflexRequest req, String action)
	throws IOException, TaggingException;

	/**
	 * キャプチャ不要なWSSE認証回数を取得.
	 * @param serviceName サービス名
	 * @return キャプチャ不要なWSSE認証回数
	 */
	public int getWsseWithoutCaptchaCount(String serviceName);

}
