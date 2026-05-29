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
		if (isEnabledAccessLog()) {
			logger.info("[call] start. podName=" + podName);
			startTime = new Date().getTime();
		}

		ReflexContext reflexContext = (ReflexContext)getReflexContext();

		// バッチジョブ実行管理処理を実行
		BatchJobBlogic blogic = new BatchJobBlogic();
		blogic.execManagement(podName, reflexContext);

		if (isEnabledAccessLog()) {
			long finishTime = new Date().getTime();
			long time = finishTime - startTime;
			StringBuilder sb = new StringBuilder();
			sb.append("[call] end - ");
			sb.append(time);
			sb.append("ms.");
			logger.info(sb.toString());
		}

		return true;
	}
	
	/**
	 * アクセスログを出力する場合true
	 * @return 
	 */
	private boolean isEnabledAccessLog() {
		return BatchJobUtil.isEnableAccessLog();
	}

}
