package jp.reflexworks.taggingservice.plugin;

import java.io.IOException;
import java.util.concurrent.Future;

import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.exception.TaggingException;

/**
 * ジョブ実行管理プラグインクラス.
 */
public interface JobManager extends ReflexPlugin {
	
	/**
	 * ジョブ実行処理.
	 * @param jobName ジョブ名
	 * @param reflexContext ReflexContext
	 */
	public Future runJob(String jobName, ReflexContext reflexContext)
	throws IOException, TaggingException;

}
