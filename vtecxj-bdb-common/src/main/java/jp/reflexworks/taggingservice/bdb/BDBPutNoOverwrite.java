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
import jp.sourceforge.reflex.util.StringUtils;

/**
 * putNoOverwriteクラス.
 */
public class BDBPutNoOverwrite<T> {

	/** ログ出力用メソッド名 */
	private static final String LOG_METHOD = "putNoOverwrite";

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * データ1件登録.
	 * @param namespace 名前空間 (ログ用)
	 * @param txn トランザクション
	 * @param db データベース
	 * @param binding バインドクラス
	 * @param keyStr キー
	 * @param data データ
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 登録結果
	 */
	public OperationStatus putNoOverwrite(String namespace, BDBTransaction txn, BDBDatabase db,
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
		long startTime = 0;
		if (BDBUtil.isEnableAccessLog()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
					BDBUtil.getStartLog(namespace, LOG_METHOD, db, keyStr));
			startTime = new Date().getTime();
		}
		OperationStatus ret = db.putNoOverwrite(txn, dbKey, dbData);

		if (BDBUtil.isEnableAccessLog()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
					BDBUtil.getEndLog(namespace, LOG_METHOD, db, keyStr, startTime));
		}
		return ret;
	}

}
