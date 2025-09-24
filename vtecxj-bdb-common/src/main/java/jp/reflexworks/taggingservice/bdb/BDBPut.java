package jp.reflexworks.taggingservice.bdb;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sleepycat.bind.EntryBinding;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.OperationStatus;

import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.ReflexBDBCheckUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * putクラス.
 */
public class BDBPut<T> {

	/** ログ出力用メソッド名 */
	private static final String LOG_METHOD = "put";

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * データ1件更新.
	 * @param serviceName サービス名 (ログ用)
	 * @param txn トランザクション
	 * @param db データベース
	 * @param binding バインドクラス
	 * @param keyStr キー
	 * @param data データ
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 更新結果
	 */
	public OperationStatus put(String serviceName, BDBTransaction txn, BDBDatabase db,
			EntryBinding<T> binding, String keyStr, T data, RequestInfo requestInfo,
			ConnectionInfo connectionInfo) {
		if (StringUtils.isBlank(keyStr)) {
			throw new IllegalParameterException("The key is required.");
		}
		if (data == null) {
			throw new IllegalParameterException("The data is required.");
		}

		DatabaseEntry dbKey = BDBUtil.getDbKey(keyStr);
		DatabaseEntry dbData = new DatabaseEntry();
		binding.objectToEntry(data, dbData);

		// Entryの最大サイズチェック
		ReflexBDBCheckUtil.checkEntryMaxBytes(dbData.getData(), keyStr);

		long startTime = 0;
		if (BDBUtil.isEnableAccessLog()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
					BDBUtil.getStartLog(serviceName, LOG_METHOD, db, keyStr));
			startTime = new Date().getTime();
		}
		OperationStatus ret = db.put(txn, dbKey, dbData);
		if (BDBUtil.isEnableAccessLog()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
					BDBUtil.getEndLog(serviceName, LOG_METHOD, db, keyStr, startTime));
		}
		return ret;
	}

}
