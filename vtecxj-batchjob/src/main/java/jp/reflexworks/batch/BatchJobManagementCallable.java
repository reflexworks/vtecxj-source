package jp.reflexworks.batch;

import java.io.IOException;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;

/**
 * バッチジョブ管理処理.
 */
public class BatchJobManagementCallable extends ReflexCallable<Boolean> {

	/** POD名 */
	private String podName;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ.
	 * @param podName POD名
	 */
	public BatchJobManagementCallable(String podName) {
		this.podName = podName;
	}

	/**
	 * バッチジョブ管理処理 実行
	 */
	@Override
	public Boolean call()
	throws IOException, TaggingException {
		long startTime = 0;
		if (logger.isTraceEnabled()) {
			logger.debug("[call] start. podName=" + podName);
			startTime = new Date().getTime();
		}

		ReflexContext reflexContext = (ReflexContext)getReflexContext();

		// バッチジョブ実行管理処理を実行
		BatchJobBlogic blogic = new BatchJobBlogic();
		blogic.execManagement(podName, reflexContext);

		if (logger.isTraceEnabled()) {
			long finishTime = new Date().getTime();
			long time = finishTime - startTime;
			StringBuilder sb = new StringBuilder();
			sb.append("[call] end - ");
			sb.append(time);
			sb.append("ms.");
			logger.debug(sb.toString());
		}

		return true;
	}

}
