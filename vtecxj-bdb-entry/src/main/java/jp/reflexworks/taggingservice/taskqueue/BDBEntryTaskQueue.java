package jp.reflexworks.taggingservice.taskqueue;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.conn.ReflexBDBConnectionInfo;
import jp.reflexworks.taggingservice.context.BDBEntryContext;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.ReflexBDBRequestInfo;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.sourceforge.reflex.util.DeflateUtil;

/**
 * TaggingService非同期処理.
 * 例外発生時の処理を共通化する。
 */
public class BDBEntryTaskQueue<T> implements ReflexTaskQueue<T> {

	/** 非同期処理 */
	private ReflexBDBCallable<T> callable;
	/** リクエスト情報 */
	private ReflexBDBRequestInfo requestInfo;
	/** サービス名 */
	private String serviceName;
	/** 名前空間 */
	private String namespace;
	/** 後で同期する場合true */
	private boolean sync;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ.
	 * @param callable 非同期処理
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 */
	public BDBEntryTaskQueue(ReflexBDBCallable<T> callable, String serviceName,
			String namespace, ReflexBDBRequestInfo requestInfo) {
		this.callable = callable;
		this.serviceName = serviceName;
		this.namespace = namespace;
		this.requestInfo = requestInfo;
	}

	/**
	 * 非同期処理.
	 */
	@Override
	public T call() throws IOException, TaggingException {
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
					"[call] start. " + callable.getClass().getName());
		}

		DeflateUtil deflateUtil = null;
		ReflexBDBConnectionInfo connectionInfo = null;
		try {
			deflateUtil = new DeflateUtil();
			connectionInfo = new ReflexBDBConnectionInfo(deflateUtil, requestInfo);

			// ReflexContext生成
			BDBEntryContext reflexContext = new BDBEntryContext(
					serviceName, namespace, requestInfo, connectionInfo);
			callable.setReflexContext(reflexContext);

			return callable.call();

		} catch (IOException | TaggingException | RuntimeException e) {
			// ログ出力
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[call] ");
			sb.append(e.getClass().getSimpleName());
			logger.error(sb.toString(), e);
			if (sync) {
				throw e;
			} else {
				return null;
			}

		} finally {
			if (connectionInfo != null) {
				connectionInfo.close();
			}
			if (logger.isTraceEnabled()) {
				logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
						"[call] end. " + callable.getClass().getName());
			}
		}
	}

}
