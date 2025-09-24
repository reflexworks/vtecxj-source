package jp.reflexworks.taggingservice.conn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.ConnectionException;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.sourceforge.reflex.util.DeflateUtil;

/**
 * コネクション情報保持クラス.
 * 1スレッド内で使用するオブジェクト。
 */
public class ConnectionInfoImpl implements ConnectionInfo {

	/** スレッド間共有コネクション */
	private static final long SHARING_BETWEEN_THREADS = Long.MIN_VALUE;

	/**
	 * スレッドごとのコネクション情報を保持.
	 * キー: スレッドID、値: スレッドごとのコネクション情報
	 */
	private final Map<Long, TaggingThreadConnectionInfo> connections =
			new ConcurrentHashMap<Long, TaggingThreadConnectionInfo>();

	/** リクエスト情報 */
	private RequestInfo requestInfo;

	/** TaskQueueの場合true */
	private boolean isTaskQueue;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ.
	 * @param deflateUtil Deflate圧縮解凍ツール
	 * @param requestInfo リクエスト情報
	 */
	public ConnectionInfoImpl(DeflateUtil deflateUtil, RequestInfo requestInfo) {
		this(deflateUtil, requestInfo, null);
	}

	/**
	 * コンストラクタ.
	 * @param deflateUtil Deflate圧縮解凍ツール
	 * @param requestInfo リクエスト情報
	 * @param mainConnectionInfo コネクション情報 (スレッド間共有オブジェクトを抽出するため保持)
	 */
	public ConnectionInfoImpl(DeflateUtil deflateUtil, RequestInfo requestInfo,
			ConnectionInfo mainConnectionInfo) {
		this.requestInfo = requestInfo;
		long threadId = getThreadId();
		createThreadConnectionInfo(threadId, deflateUtil);
		// スレッド間共有オブジェクトがあれば取得し、本オブジェクトで保持する。
		if (mainConnectionInfo != null) {
			isTaskQueue = true;
			Map<String, ReflexConnection<?>> sharingConnectionInfoMap =
					mainConnectionInfo.getSharings();
			if (sharingConnectionInfoMap != null) {
				for (Map.Entry<String, ReflexConnection<?>> mapEntry :
						sharingConnectionInfoMap.entrySet()) {
					String name = mapEntry.getKey();
					ReflexConnection<?> conn = mapEntry.getValue();
					if (name != null && conn != null) {
						putSharing(name, conn);
					} else {
						if (logger.isDebugEnabled()) {
							logger.debug("[constructor] ReflexConnection is null. name=" + name);
						}
					}
				}
			}
		}
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
	 * スレッド間共有コネクションを格納.
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
		TaggingThreadConnectionInfo threadConnectionInfo =
				connections.get(threadId);
		if (threadConnectionInfo == null) {
			// スレッドコネクション情報を生成し、コネクションを格納
			threadConnectionInfo = createThreadConnectionInfo(threadId);
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
	 * スレッド間共有コネクションを取得.
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
		TaggingThreadConnectionInfo threadConnectionInfo =
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
			if (conn != null) {
				sharingsMap.put(connName, conn);
			} else {
				if (logger.isDebugEnabled()) {
					logger.debug("[getSharings] ReflexConnection is null. name=" + connName);
				}
			}
		}
		return sharingsMap;
	}

	/**
	 * コネクションを取得.
	 * @param name キー
	 * @return コネクションオブジェクト
	 */
	private ReflexConnection<?> get(long threadId, String name) {
		TaggingThreadConnectionInfo threadConnectionInfo =
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
	 * @param conn RDBコネクション
	 * @return コネクション名
	 */
	public String getConnectionName(ReflexConnection<?> conn) {
		long threadId = getThreadId();
		TaggingThreadConnectionInfo threadConnectionInfo =
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
		TaggingThreadConnectionInfo threadConnectionInfo =
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
		if (isTaskQueue) {
			// 自スレッドのみクローズ
			long threadId = getThreadId();
			TaggingThreadConnectionInfo threadConnectionInfo =
					connections.get(threadId);
			threadConnectionInfo.close();
		} else {
			// メインスレッドなので全てのコネクションをクローズ
			for (Map.Entry<Long, TaggingThreadConnectionInfo> mapEntry : connections.entrySet()) {
				TaggingThreadConnectionInfo threadConnectionInfo = mapEntry.getValue();
				threadConnectionInfo.close();
			}
			connections.clear();
		}
	}

	/**
	 * クローズ処理.
	 * コネクションエラー時のクローズ
	 * @param name コネクション名
	 */
	public void close(String name) {
		long threadId = getThreadId();
		TaggingThreadConnectionInfo threadConnectionInfo =
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
		for (Map.Entry<Long, TaggingThreadConnectionInfo> mapEntry : connections.entrySet()) {
			TaggingThreadConnectionInfo threadConnectionInfo = mapEntry.getValue();
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
	private TaggingThreadConnectionInfo createThreadConnectionInfo() {
		long threadId = getThreadId();
		return createThreadConnectionInfo(threadId);
	}

	/**
	 * スレッドのコネクション情報を生成.
	 * @param threadId スレッドID
	 * @return スレッドのコネクション情報
	 */
	private TaggingThreadConnectionInfo createThreadConnectionInfo(long threadId) {
		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[createThreadConnectionInfo] threadId = ");
			sb.append(threadId);
			logger.trace(sb.toString());
		}
		// DeflateUtilを生成
		// ここで生成されたDeflateUtilは、リクエストの終了かReflexApplicationの終了時にまとめてクローズされる。
		// ただしスレッド間共有の場合作成しない。
		DeflateUtil deflateUtil = null;
		if (threadId != SHARING_BETWEEN_THREADS) {
			deflateUtil = new DeflateUtil();
		}
		return createThreadConnectionInfo(threadId, deflateUtil);
	}

	/**
	 * スレッドのコネクション情報を生成.
	 * @param threadId スレッドID
	 * @param deflateUtil DeflateUtil
	 * @return スレッドのコネクション情報
	 */
	private TaggingThreadConnectionInfo createThreadConnectionInfo(long threadId,
			DeflateUtil deflateUtil) {
		TaggingThreadConnectionInfo threadConnectionInfo =
				new TaggingThreadConnectionInfo(deflateUtil);
		connections.put(threadId, threadConnectionInfo);
		return threadConnectionInfo;
	}

	@Override
	public String toString() {
		return "ConnectionInfo [threadIds =" + connections.keySet() + "]";
	}

}
