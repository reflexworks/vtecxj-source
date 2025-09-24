package jp.reflexworks.taggingservice.conn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.exception.ConnectionException;
import jp.reflexworks.taggingservice.model.ReflexBDBRequestInfo;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.sourceforge.reflex.util.DeflateUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * コネクション情報保持クラス.
 * 1スレッド内で使用するオブジェクト。
 */
public class ReflexBDBConnectionInfo implements ConnectionInfo {

	/** スレッド間共有コネクション */
	private static final long SHARING_BETWEEN_THREADS = Long.MIN_VALUE;

	/**
	 * スレッドごとのコネクション情報を保持.
	 * キー: スレッドID、値: スレッドごとのコネクション情報
	 */
	private final Map<Long, ThreadConnectionInfo> connections =
			new ConcurrentHashMap<Long, ThreadConnectionInfo>();

	/** リクエスト情報 */
	private ReflexBDBRequestInfo requestInfo;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ.
	 * @param deflateUtil Deflate圧縮解凍ツール
	 * @param requestInfo リクエスト情報
	 */
	public ReflexBDBConnectionInfo(DeflateUtil deflateUtil, ReflexBDBRequestInfo requestInfo) {
		this.requestInfo = requestInfo;
		createThreadConnectionInfo(deflateUtil);
	}

	/**
	 * コネクションを格納.
	 * @param name キー
	 * @param conn コネクションオブジェクト
	 */
	public void put(String name, ReflexConnection<?> conn) {
		long threadId = getThreadId();
		put(threadId, name, conn);
	}

	/**
	 * スレッド共有コネクションを格納.
	 * @param name キー
	 * @param conn コネクションオブジェクト
	 */
	public void putSharing(String name, ReflexConnection<?> conn) {
		put(SHARING_BETWEEN_THREADS, name, conn);
	}

	/**
	 * コネクションを格納.
	 * @param threadId スレッドID
	 * @param name キー
	 * @param conn コネクションオブジェクト
	 */
	private void put(long threadId, String name, ReflexConnection<?> conn) {
		ThreadConnectionInfo threadConnectionInfo =
				connections.get(threadId);
		if (threadConnectionInfo == null) {
			// スレッドコネクション情報を生成し、コネクションを格納
			threadConnectionInfo = createThreadConnectionInfo();
		}
		threadConnectionInfo.put(name, conn);
	}

	/**
	 * コネクションを取得.
	 * @param name キー
	 * @return コネクションオブジェクト
	 */
	public ReflexConnection<?> get(String name) {
		long threadId = getThreadId();
		return get(threadId, name);
	}

	/**
	 * コネクションを取得.
	 * @param name キー
	 * @return コネクションオブジェクト
	 */
	public ReflexConnection<?> getSharing(String name) {
		return get(SHARING_BETWEEN_THREADS, name);
	}

	/**
	 * スレッド間共有コネクションを取得.
	 * @return コネクションオブジェクトリスト
	 */
	public Map<String, ReflexConnection<?>> getSharings() {
		ThreadConnectionInfo threadConnectionInfo =
				connections.get(SHARING_BETWEEN_THREADS);
		if (threadConnectionInfo == null) {
			return null;
		}
		Set<String> connNames = threadConnectionInfo.getConnectionNames();
		if (connNames == null || connNames.isEmpty()) {
			return null;
		}
		Map<String, ReflexConnection<?>> sharingsMap = new ConcurrentHashMap<>();
		// Iteratorの取得によるConcurrentModificationException対応のため、コネクション名リストを複製する。
		List<String> tmpConnNames = new ArrayList<>(connNames);
		for (String connName : tmpConnNames) {
			ReflexConnection<?> conn = threadConnectionInfo.get(connName);
			sharingsMap.put(connName, conn);
		}
		return sharingsMap;
	}

	/**
	 * コネクションを取得.
	 * @param threadId スレッドID
	 * @param name キー
	 * @return コネクションオブジェクト
	 */
	private ReflexConnection<?> get(long threadId, String name) {
		ThreadConnectionInfo threadConnectionInfo =
				connections.get(threadId);
		if (threadConnectionInfo != null) {
			return threadConnectionInfo.get(name);
		}
		if (logger.isTraceEnabled()) {
			String msg = "Thread connection is nothing. threadId = " + threadId;
			ConnectionException e = new ConnectionException(msg);
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) + msg, e);
		}
		return null;
	}

	/**
	 * コネクションオブジェクトからコネクション名を取得.
	 * @param conn コネクションオブジェクト
	 * @return コネクション名
	 */
	public String getConnectionName(ReflexConnection<?> conn) {
		long threadId = getThreadId();
		ThreadConnectionInfo threadConnectionInfo =
				connections.get(threadId);
		if (threadConnectionInfo != null) {
			return threadConnectionInfo.getConnectionName(conn);
		}
		return null;
	}

	/**
	 * DeflateUtilを取得.
	 * @return DeflateUtil
	 */
	public DeflateUtil getDeflateUtil() {
		long threadId = getThreadId();
		ThreadConnectionInfo threadConnectionInfo =
				connections.get(threadId);
		if (threadConnectionInfo == null) {
			// スレッドコネクション情報を生成し、コネクションを格納
			threadConnectionInfo = createThreadConnectionInfo();
		}
		return threadConnectionInfo.getDeflateUtil();
	}

	/**
	 * クローズ処理.
	 */
	public void close() {
		for (Map.Entry<Long, ThreadConnectionInfo> mapEntry : connections.entrySet()) {
			ThreadConnectionInfo threadConnectionInfo = mapEntry.getValue();
			threadConnectionInfo.close();
		}
		connections.clear();
	}

	/**
	 * クローズ処理.
	 * コネクションエラー時のクローズ
	 * @param name コネクション名
	 */
	public void close(String name) {
		long threadId = getThreadId();
		ThreadConnectionInfo threadConnectionInfo =
				connections.get(threadId);
		if (threadConnectionInfo != null) {
			threadConnectionInfo.close(name);
		} else {
			if (logger.isInfoEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[close] Thread connection is nothing. threadId = ");
				sb.append(threadId);
				logger.info(sb.toString());
			}
		}
	}

	/**
	 * リセット処理.
	 * DeflateUtil以外をクローズします。
	 */
	public void reset() {
		for (Map.Entry<Long, ThreadConnectionInfo> mapEntry : connections.entrySet()) {
			ThreadConnectionInfo threadConnectionInfo = mapEntry.getValue();
			threadConnectionInfo.reset();
		}
		connections.clear();
	}

	/**
	 * スレッド番号を取得.
	 * @return スレッド番号
	 */
	private long getThreadId() {
		return Thread.currentThread().getId();
	}

	/**
	 * スレッドのコネクション情報を生成.
	 * @return スレッドのコネクション情報
	 */
	private ThreadConnectionInfo createThreadConnectionInfo() {
		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[createThreadConnectionInfo] threadId = ");
			sb.append(getThreadId());
			logger.trace(sb.toString());
		}
		// DeflateUtilを生成
		// ここで生成されたDeflateUtilは、リクエストの終了かReflexApplicationの終了時にまとめてクローズされる。
		DeflateUtil deflateUtil = new DeflateUtil();
		return createThreadConnectionInfo(deflateUtil);
	}

	/**
	 * スレッドのコネクション情報を生成.
	 * @return スレッドのコネクション情報
	 */
	private ThreadConnectionInfo createThreadConnectionInfo(DeflateUtil deflateUtil) {
		long threadId = getThreadId();
		ThreadConnectionInfo threadConnectionInfo =
				new ThreadConnectionInfo(deflateUtil);
		connections.put(threadId, threadConnectionInfo);
		return threadConnectionInfo;
	}

	@Override
	public String toString() {
		return "ConnectionInfo [threadIds =" + connections.keySet() + "]";
	}

}
