package jp.reflexworks.taggingservice.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.sourceforge.reflex.util.FileUtil;

/**
 * ローカルキャッシュをクラウドストレージにアップロードする非同期処理.
 */
public class CloudStorageUploaderCallable extends ReflexCallable<Boolean> {

	/** エラーログのタイトル */
	private static final String TITLE = "CloudStorageUploaderCallable";

	/** URI */
	private String uri;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param uri URI
	 */
	public CloudStorageUploaderCallable(String uri) {
		this.uri = uri;
	}

	/**
	 * ローカルキャッシュをクラウドストレージにアップロードする.
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		ConnectionInfo connectionInfo = getConnectionInfo();
		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[upload call] start. uri = ");
			sb.append(uri);
			logger.trace(sb.toString());
		}

		String serviceName = getServiceName();
		String namespace = getNamespace();
		CloudStorageManager cloudStorageManager = new CloudStorageManager();
		LocalCacheManager localCacheManager = new LocalCacheManager();
		// ローカルキャッシュからコンテンツとヘッダ情報を取得
		InputStream in = localCacheManager.getContentFromCache(uri, serviceName,
				namespace, requestInfo, connectionInfo);
		if (in == null) {
			String msg = "[upload call] cache file stream is null. uri = " + uri;
			if (logger.isDebugEnabled()) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) + msg);
			}
			((ReflexContext)getReflexContext()).log(TITLE, Constants.WARN, msg);
			return false;

		} else {
			byte[] data = FileUtil.readInputStream(in);
			Map<String, String> headers = localCacheManager.getHeadersFromCache(
					uri, serviceName, namespace, requestInfo, connectionInfo);
			// ストレージにアップロード
			cloudStorageManager.uploadToStorage(uri, data, headers, namespace, 
					serviceName, requestInfo, connectionInfo);
			return true;
		}
	}

}
