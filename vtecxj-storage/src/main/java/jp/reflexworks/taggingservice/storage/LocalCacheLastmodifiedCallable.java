package jp.reflexworks.taggingservice.storage;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.util.LogUtil;

/**
 * ローカルキャッシュ更新時刻更新処理.
 */
public class LocalCacheLastmodifiedCallable extends ReflexCallable<Boolean> {

	/** URI */
	private String uri;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ.
	 * @param uri URI
	 */
	public LocalCacheLastmodifiedCallable(String uri) {
		this.uri = uri;
	}

	/**
	 * ローカルキャッシュの更新時刻を更新する.
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		ConnectionInfo connectionInfo = getConnectionInfo();
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) + "[set lastModified call] start.");
		}
		LocalCacheManager localCacheManager = new LocalCacheManager();
		return localCacheManager.setLastModifiredToCache(uri, getServiceName(),
				getNamespace(), requestInfo, connectionInfo);
	}

}
