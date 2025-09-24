package jp.reflexworks.taggingservice.bdb;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sleepycat.bind.EntryBinding;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.LockMode;

import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * getクラス.
 */
public class BDBGet<T> {

	/** ログ出力用メソッド名 */
	private static final String LOG_METHOD = "get";

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * データ1件検索.
	 * @param serviceName サービス名 (ログ用)
	 * @param txn トランザクション
	 * @param db データベース
	 * @param binding バインドクラス
	 * @param lockMode トランザクションのロックモード (採番・カウンタはRMW(書き込み(排他)ロック)、その他はDEFAULT(読み取り(共有)ロック))
	 * @param keyStr キー
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return データ
	 */
	public T get(String serviceName, BDBTransaction txn, BDBDatabase db, EntryBinding<T> binding,
			LockMode lockMode, String keyStr, RequestInfo requestInfo,
			ConnectionInfo connectionInfo) {
		if (StringUtils.isBlank(keyStr)) {
			throw new IllegalParameterException("The key is required.");
		}

		DatabaseEntry dbKey = BDBUtil.getDbKey(keyStr);
		DatabaseEntry dbData = new DatabaseEntry();
		long startTime = 0;
		if (BDBUtil.isEnableAccessLog()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
					BDBUtil.getStartLog(serviceName, LOG_METHOD, db, keyStr));
			startTime = new Date().getTime();
		}
		db.get(txn, dbKey, dbData, lockMode);
		if (BDBUtil.isEnableAccessLog()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
					BDBUtil.getEndLog(serviceName, LOG_METHOD, db, keyStr, startTime));
		}
		if (dbData.getSize() > 0) {
			return binding.entryToObject(dbData);
		} else {
			return null;
		}
	}

}
