package jp.reflexworks.taggingservice.plugin;

import java.io.IOException;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.exception.TaggingException;

/**
 * モニター管理インターフェース.
 */
public interface MonitorManager extends ReflexPlugin {

	/**
	 * モニター
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @return メッセージ
	 */
	public FeedBase monitor(ReflexRequest req, ReflexResponse resp)
	throws IOException, TaggingException;

}
