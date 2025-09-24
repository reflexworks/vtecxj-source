package jp.reflexworks.taggingservice.taskqueue;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.MemorySortManager;
import jp.reflexworks.taggingservice.util.LogUtil;

/**
 * インメモリソート機能.
 */
public class MemorySortCallable extends ReflexCallable<Boolean> {

	/** 検索条件 */
	private RequestParam param;
	/** 検索条件文字列 (セッション格納時のキーに使用) */
	private String conditionName;
	/** 対象サービスの認証情報 */
	private ReflexAuthentication tmpAuth;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param param 検索条件
	 * @param conditionName 検索条件文字列 (セッション格納時のキーに使用)
	 * @param tmpAuth 対象サービスの認証情報
	 */
	public MemorySortCallable(RequestParam param, String conditionName, ReflexAuthentication tmpAuth) {
		this.param = param;
		this.conditionName = conditionName;
		this.tmpAuth = tmpAuth;
	}

	/**
	 * インメモリソート処理.
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		if (logger.isTraceEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[memory sort call] start.");
		}

		// ReflexContextを取得 (セッションも含まれる。)
		ReflexContext reflexContext = (ReflexContext)getReflexContext();

		// インメモリソートサーバにリクエストを投げる。
		// セッションに結果を書き込むのはインメモリソートサーバが行ってくれる。
		MemorySortManager memorySortManager = TaggingEnvUtil.getMemorySortManager();
		memorySortManager.requestSort(param, conditionName, tmpAuth, reflexContext);

		if (logger.isTraceEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[memory sort call] end.");
		}
		return true;
	}

}
