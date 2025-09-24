package jp.reflexworks.taggingservice.taskqueue;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexEnv;
import jp.reflexworks.taggingservice.api.ReflexStatic;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.conn.ConnectionInfoImpl;
import jp.reflexworks.taggingservice.context.ReflexContextUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.sourceforge.reflex.util.DeflateUtil;

/**
 * TaggingService非同期処理.
 * 例外発生時の処理を共通化する。
 */
public class TaggingTaskQueue<T> implements ReflexTaskQueue<T> {

	/** 非同期処理 */
	private ReflexCallable<T> callable;
	/** 認証情報 */
	private ReflexAuthentication auth;
	/** リクエスト情報 */
	private RequestInfo requestInfo;
	/** コネクション情報 (スレッド間共有オブジェクトを抽出するため保持) */
	private ConnectionInfo mainConnectionInfo;
	/** TaggingException発生時スタックトレースログを出力する場合true */
	private boolean printTagingException;
	/** 例外発生時にログエントリー出力しないどうか */
	private boolean isDisabledErrorLogEntry;
	/** 移行処理かどうか */
	private boolean isMigrate;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ.
	 * @param callable 非同期処理
	 * @param sync 後で同期する場合true
	 * @param printTagingException TaggingException発生時スタックトレースログを出力する場合true
	 * @param isDisabledErrorLogEntry 例外発生時にログエントリー出力しないどうか
	 * @param isMigrate 移行処理の場合true (移行処理の場合、サービスの設定を参照しない)
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param mainConnectionInfo コネクション情報 (スレッド間共有オブジェクトを抽出するため保持)
	 */
	public TaggingTaskQueue(ReflexCallable<T> callable, boolean printTagingException,
			boolean isDisabledErrorLogEntry, boolean isMigrate, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo mainConnectionInfo) {
		this.callable = callable;
		this.printTagingException = printTagingException;
		this.isDisabledErrorLogEntry = isDisabledErrorLogEntry;
		this.auth = auth;
		this.requestInfo = requestInfo;
		this.mainConnectionInfo = mainConnectionInfo;
		this.isMigrate = isMigrate;
	}

	/**
	 * 非同期処理.
	 * @return 戻り値
	 */
	@Override
	public T call() throws IOException, TaggingException {
		if (logger.isTraceEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
					"[call] start. " + callable.getClass().getName());
		}

		DeflateUtil deflateUtil = null;
		ConnectionInfo connectionInfo = null;
		try {
			deflateUtil = new DeflateUtil();
			connectionInfo = new ConnectionInfoImpl(deflateUtil, requestInfo, mainConnectionInfo);

			// ReflexContext生成
			ReflexContext reflexContext = ReflexContextUtil.getReflexContext(
					auth, requestInfo, connectionInfo);
			callable.setReflexContext(reflexContext);

			// callメソッドを直接実行するため同期処理となり、処理終了後にfinallyが呼ばれる。
			return callable.call();

		} catch (IOException | TaggingException | RuntimeException | Error e) {
			// ログ出力
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[call] ");
			sb.append(e.getClass().getName());
			sb.append(": ");
			sb.append(e.getMessage());
			boolean isPrintError = false;
			if (e instanceof TaggingException || e instanceof IllegalParameterException) {
				if (printTagingException) {
					isPrintError = true;
				}
			} else {
				isPrintError = true;
			}
			if (isPrintError) {
				logger.error(sb.toString(), e);
			} else {
				if (logger.isDebugEnabled()) {
					logger.debug(sb.toString());
				}
			}
			// 初期データ登録の場合ログ出力しない
			boolean isInitialized = ((ReflexEnv)ReflexStatic.getEnv()).isInitialized();
			if (!isDisabledErrorLogEntry && !isInitialized) {
				if (deflateUtil == null) {
					deflateUtil = new DeflateUtil();
				}
				if (connectionInfo == null) {
					connectionInfo = new ConnectionInfoImpl(deflateUtil, requestInfo,
							mainConnectionInfo);
				}
				SystemContext systemContext = new SystemContext(auth, requestInfo, connectionInfo);
				systemContext.errorLog(e);
			}
			throw e;

		} finally {
			if (connectionInfo != null) {
				connectionInfo.close();
			} else if (deflateUtil != null) {
				deflateUtil.close();
			}
			if (logger.isTraceEnabled()) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[call] end. " + callable.getClass().getName());
			}
		}
	}

}
