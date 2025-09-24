package jp.reflexworks.taggingservice.blogic;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.MonitorManager;

/**
 * モニター処理ビジネスロジック.
 */
public class MonitorBlogic {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * モニター.
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @return モニタリング結果
	 */
	public FeedBase monitor(ReflexRequest req, ReflexResponse resp)
	throws IOException, TaggingException {
		MonitorManager monitorManager = TaggingEnvUtil.getMonitorManager();
		if (monitorManager == null) {
			throw new IllegalParameterException("The function is not provided. _monitor");
		}
		return monitorManager.monitor(req, resp);
	}

}
