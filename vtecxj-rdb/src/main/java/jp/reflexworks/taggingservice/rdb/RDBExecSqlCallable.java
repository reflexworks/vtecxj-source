package jp.reflexworks.taggingservice.rdb;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.util.LogUtil;

/**
 * RDB登録非同期処理.
 */
public class RDBExecSqlCallable extends ReflexCallable<int[]> {

	/** SQL */
	private String[] sqls;
	
	/** 大量データ登録の場合にtrueを指定 */
	private boolean isBulk;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param sqls SQLリスト
	 * @param isBulk 大量データ登録の場合にtrueを指定。trueの場合、このメソッド内でトランザクションの操作を行わない。
	 *             (AutoCommit ONを想定)
	 */
	public RDBExecSqlCallable(String[] sqls, boolean isBulk) {
		this.sqls = sqls;
		this.isBulk = isBulk;
	}

	/**
	 * SQL実行.
	 */
	@Override
	public int[] call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) + "[RDB execSql call] start.");
		}

		// ReflexContextを取得
		ReflexContext reflexContext = (ReflexContext)getReflexContext();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();

		ReflexRDBManager rdbManager = new ReflexRDBManager();
		return rdbManager.execSqlProc(sqls, isBulk, getAuth(), requestInfo, connectionInfo);
	}

}
