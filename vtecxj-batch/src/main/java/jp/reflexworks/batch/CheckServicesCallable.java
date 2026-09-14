package jp.reflexworks.batch;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.util.LogUtil;

/**
 * サービス単位の定期チェック処理のリクエスト処理.
 * サービスごとに行う処理
 *  ・バッチジョブサーバへ {@code PUT ?_check} リクエストする
 *    (メッセージキュー未送信チェック・BDBQリトライチェック・バッチジョブ実行管理を統合)
 *
 * (旧 {@code CheckMessageQueueCallable} / {@code CheckRetryBdbqCallable} を統合。)
 */
public class CheckServicesCallable extends ReflexCallable<Boolean> {

	/** サービス名 */
	private String serviceName;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param serviceName サービス名
	 */
	public CheckServicesCallable(String serviceName) {
		this.serviceName = serviceName;
	}

	/**
	 * バッチジョブサーバにリクエストする処理.
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		if (logger.isTraceEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo)
					+ "[CheckServicesCallable] start. serviceName=" + serviceName);
		}

		new CheckServicesBlogic().request(serviceName);

		return true;
	}

}
