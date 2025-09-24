package jp.reflexworks.taggingservice.plugin.def;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.util.LogUtil;

/**
 * サービス削除機能.
 */
public class DeleteServiceDefaultCallable extends ReflexCallable<Boolean> {

	/** 削除サービス */
	private String delServiceName;
	/** サービスエントリー */
	private EntryBase serviceEntry;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param delServiceName 削除サービス
	 * @param serviceEntry サービスエントリー
	 * @param delServiceAuth 削除サービスの認証情報
	 */
	public DeleteServiceDefaultCallable(String delServiceName, EntryBase serviceEntry) {
		this.delServiceName = delServiceName;
		this.serviceEntry = serviceEntry;
	}

	/**
	 * サービス削除処理.
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		if (logger.isTraceEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
					"[deleteservice call] start.");
		}

		ServiceManagerDefault serviceManager = new ServiceManagerDefault();
		serviceManager.deleteserviceCallable(delServiceName, serviceEntry,
				(ReflexContext)getReflexContext());

		if (logger.isTraceEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
					"[deleteservice call] end.");
		}
		return true;
	}

}
