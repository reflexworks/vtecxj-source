package jp.reflexworks.taggingservice.plugin;

import java.io.IOException;
import java.util.concurrent.Future;

import jp.reflexworks.atom.entry.EntryBase;
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
	
	/**
	 * ジョブの情報をバッチジョブ管理テーブルに設定.
	 * @param future ジョブ実行Future
	 * @param entry バッチジョブ管理テーブル
	 */
	public void setJobInfo(Future future, EntryBase entry)
	throws IOException, TaggingException;

}
