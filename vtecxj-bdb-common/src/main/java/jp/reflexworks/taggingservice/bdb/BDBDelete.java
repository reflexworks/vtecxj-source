package jp.reflexworks.taggingservice.bdb;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.OperationStatus;

import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * deleteクラス.
 */
public class BDBDelete {

	/** ログ出力用メソッド名 */
	private static final String LOG_METHOD = "delete";

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * データ1件削除.
	 * @param serviceName サービス名 (ログ用)
	 * @param txn トランザクション
	 * @param db データベース
	 * @param keyStr キー
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 更新結果
	 */
	public OperationStatus delete(String serviceName, BDBTransaction txn, BDBDatabase db,
			String keyStr, RequestInfo requestInfo,
			ConnectionInfo connectionInfo) {
		if (StringUtils.isBlank(keyStr)) {
			throw new IllegalParameterException("The key is required.");
		}

		DatabaseEntry dbKey = BDBUtil.getDbKey(keyStr);

		long startTime = 0;
		if (BDBUtil.isEnableAccessLog()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
					BDBUtil.getStartLog(serviceName, LOG_METHOD, db, keyStr));
			startTime = new Date().getTime();
		}
		OperationStatus ret = db.delete(txn, dbKey);
		if (BDBUtil.isEnableAccessLog()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
					BDBUtil.getEndLog(serviceName, LOG_METHOD, db, keyStr, startTime));
		}
		return ret;
	}

}
