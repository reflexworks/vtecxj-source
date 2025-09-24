package jp.reflexworks.taggingservice.storage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContentInfo;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.taskqueue.TaskQueueUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.FileUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * ローカルキャッシュ管理クラス.
 */
public class LocalCacheManager {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンテンツをローカルキャッシュに書き込む.
	 * @param uri URI
	 * @param revision リビジョン
	 * @param updated 更新日時
	 * @param in アップロードコンテンツのストリーム
	 * @param headers アップロードコンテンツのヘッダ情報
	 * @param auth 認証情報
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return コンテンツファイルとヘッダ情報の合計サイズ(byte)
	 */
	long writeToCache(String uri, int revision, String updated, byte[] data,
			Map<String, String> headers, ReflexAuthentication auth,
			String namespace,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[writeToCache] start. uri = ");
			sb.append(uri);
			sb.append(" data size = ");
			sb.append(data.length);
			logger.trace(sb.toString());
		}
		String serviceName = auth.getServiceName();
		String lockpath = CloudStorageUtil.getCacheContentFilepath(uri, serviceName,
				namespace, requestInfo, connectionInfo);
		long totalSize = 0;
		try {
			// ロック取得
			CloudStorageUtil.lockCache(lockpath);
			// リビジョンチェック -> 削除の後再登録の場合があるため、リビジョンチェックは行わない。

			// ディレクトリが存在しない場合作成する
			mkdir(serviceName, namespace, requestInfo, connectionInfo);
			// ヘッダ情報をローカルファイルに出力
			totalSize += writeHeadersToCache(uri, revision, updated, headers,
					serviceName, namespace, requestInfo, connectionInfo);
			// コンテンツをローカルファイルに出力
			totalSize += writeContentToCache(uri, data, serviceName, namespace,
					requestInfo, connectionInfo);

			// コンテンツキャッシュ容量チェックTaskQueueを呼び出す。
			LocalCacheCleanerCallable cacheCleanerCallable =
					new LocalCacheCleanerCallable();
			// TaskQueueエラー時のログエントリー書き込みはしない。
			TaskQueueUtil.addTask(cacheCleanerCallable, true, 0, auth, requestInfo,
					connectionInfo);

		} finally {
			CloudStorageUtil.releaseCacheLock(lockpath);
		}

		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[writeToCache] end. uri = ");
			sb.append(uri);
			sb.append(" total cache size = ");
			sb.append(totalSize);
			logger.trace(sb.toString());
		}
		return totalSize;
	}

	/**
	 * ディレクトリが存在しない場合作成する.
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	private void mkdir(String serviceName, String namespace, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// コンテンツディレクトリ
		String contentDirStr = CloudStorageUtil.getCacheContentDirpath(serviceName,
				namespace, requestInfo, connectionInfo);
		File contentDir = new File(contentDirStr);
		if (!contentDir.exists()) {
			boolean ret = contentDir.mkdirs();
			if (!ret) {
				logger.warn(LogUtil.getRequestInfoStr(requestInfo) +
						"[mkdir] File.mkdirs failed. dir = " + contentDirStr);
			}
		}
		// ヘッダ情報ディレクトリ
		String headerDirStr = CloudStorageUtil.getCacheHeaderDirpath(serviceName,
				namespace, requestInfo, connectionInfo);
		File headerDir = new File(headerDirStr);
		if (!headerDir.exists()) {
			boolean ret = headerDir.mkdirs();
			if (!ret) {
				logger.warn(LogUtil.getRequestInfoStr(requestInfo) +
						"[mkdir] File.mkdirs failed. dir = " + headerDirStr);
			}
		}
	}

	/**
	 * ヘッダ情報をファイルに出力.
	 * 1行目はリビジョン、2行目以降はヘッダ情報を出力する。
	 * @param uri URI
	 * @param revision リビジョン
	 * @param updated 更新日時
	 * @param headers ヘッダ情報
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return ヘッダ情報ファイルのサイズ(byte)
	 */
	private long writeHeadersToCache(String uri, int revision, String updated,
			Map<String, String> headers, String serviceName, String namespace,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String headerFilepath = CloudStorageUtil.getCacheHeaderFilepath(uri,
				serviceName, namespace, requestInfo, connectionInfo);
		File headerFile = new File(headerFilepath);
		int numRetries = CloudStorageUtil.getStorageCacheRetryCount();
		int waitMillis = CloudStorageUtil.getStorageCacheRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			OutputStreamWriter writer = null;
			try {
				writer = new OutputStreamWriter(new FileOutputStream(headerFile),
						Constants.CHARSET);
				// 1行目はリビジョン,更新日時を出力
				writer.write(getLocalCacheRevision(revision, updated));
				writer.write(Constants.NEWLINE);

				// 2行目以降はヘッダ情報を出力
				if (headers != null) {
					for (Map.Entry<String, String> mapEntry : headers.entrySet()) {
						String key = mapEntry.getKey();
						String val = mapEntry.getValue();

						boolean write = false;
						if ((ReflexServletConst.HEADER_CONTENT_TYPE.equals(key) ||
								ReflexServletConst.HEADER_CONTENT_ENCODING.equals(key) ||
								ReflexServletConst.HEADER_CONTENT_LANGUAGE.equals(key)) &&
								!StringUtils.isBlank(val)) {
							write = true;
						} else if (ReflexServletConst.HEADER_CONTENT_DISPOSITION.equals(key) &&
								!StringUtils.isBlank(val) &&
								val.indexOf(ReflexServletConst.HEADER_FORM_DATA) == -1) {
							write = true;
						}
						if (write) {
							writer.write(key);
							writer.write(CloudStorageConst.CACHE_HEADERS_DELIMITER);
							writer.write(val);
							writer.write(Constants.NEWLINE);
						}
					}
				}
				break;

			} catch (IOException e) {
				// リトライ
				CloudStorageUtil.checkRetryError(e, requestInfo);
				if (r >= numRetries) {
					// リトライ対象だがリトライ回数を超えた場合
					throw e;
				}
				CloudStorageUtil.sleep(waitMillis);

			} finally {
				if (writer != null) {
					writer.close();
				}
			}
		}
		return headerFile.length();
	}

	/**
	 * コンテンツをファイルに出力.
	 * @param uri URI
	 * @param in コンテンツの入力ストリーム
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return コンテンツファイルのサイズ(byte)
	 */
	private long writeContentToCache(String uri, byte[] data, String serviceName,
			String namespace, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		if (data == null) {
			return 0;
		}

		String contentFilepath = CloudStorageUtil.getCacheContentFilepath(uri,
				serviceName, namespace, requestInfo, connectionInfo);
		File contentFile = new File(contentFilepath);
		int numRetries = CloudStorageUtil.getStorageCacheRetryCount();
		int waitMillis = CloudStorageUtil.getStorageCacheRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				// ファイルに書き込み。
				// クローズ処理はメソッド内で行っている。
				FileUtil.writeToFile(data, contentFile);
				break;

			} catch (IOException e) {
				// リトライ
				CloudStorageUtil.checkRetryError(e, requestInfo);
				if (r >= numRetries) {
					// リトライ対象だがリトライ回数を超えた場合
					throw e;
				}
				CloudStorageUtil.sleep(waitMillis);
			}
		}
		return contentFile.length();
	}

	/**
	 * コンテンツを削除する.
	 * @param uri URI
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 削除したファイルの合計サイズ (クリーナーで使用)
	 */
	long deleteFromCache(String uri, String serviceName, String namespace,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String contentFilepath = CloudStorageUtil.getCacheContentFilepath(uri,
				serviceName, namespace, requestInfo, connectionInfo);
		long totalSize = 0;
		try {
			// ロック取得
			CloudStorageUtil.lockCache(contentFilepath);
			// コンテンツ
			File contentFile = new File(contentFilepath);
			if (contentFile.exists()) {
				totalSize += contentFile.length();
				boolean ret = contentFile.delete();
				if (!ret) {
					throw new IOException("The content cache file is not deleted. uri = " + uri);
				}
			}

			// ヘッダ情報
			String headerFilepath = CloudStorageUtil.getCacheHeaderFilepath(uri,
					serviceName, namespace, requestInfo, connectionInfo);
			File headerFile = new File(headerFilepath);
			if (headerFile.exists()) {
				totalSize += headerFile.length();
				boolean ret = headerFile.delete();
				if (!ret) {
					throw new IOException("The content cache file is not deleted. (headers) uri = " + uri);
				}
			}

		} finally {
			CloudStorageUtil.releaseCacheLock(contentFilepath);
		}

		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[deleteFromCache] uri = ");
			sb.append(uri);
			sb.append(" size = ");
			sb.append(totalSize);
			sb.append(" bytes. (content and headers)");
			logger.trace(sb.toString());
		}
		return totalSize;
	}

	/**
	 * ローカルキャッシュからコンテンツを取得する.
	 * @param uri URI
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return コンテンツの入力ストリーム
	 */
	InputStream getContentFromCache(String uri, String serviceName, String namespace,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
					"[getContentFromCache] start. uri = " + uri);
		}
		String filename = CloudStorageUtil.getCacheContentFilepath(uri, serviceName,
				namespace, requestInfo, connectionInfo);
		File file = new File(filename);
		if (!file.exists()) {
			String msg = "The storage cache file doesn't exist. filename = " + filename;
			logger.warn(LogUtil.getRequestInfoStr(requestInfo) +
					"[getContentFromCache] " + msg);
			throw new IOException(msg);
		}
		return new FileInputStream(file);
	}

	/**
	 * ローカルキャッシュからコンテンツのヘッダ情報を取得する.
	 * @param uri URI
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return コンテンツのヘッダ情報
	 */
	Map<String, String> getHeadersFromCache(String uri, String serviceName,
			String namespace, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
					"[getHeadersFromCache] start. uri = " + uri);
		}
		String filename = CloudStorageUtil.getCacheHeaderFilepath(uri, serviceName,
				namespace, requestInfo, connectionInfo);
		File file = new File(filename);
		if (!file.exists()) {
			String msg = "The storage cache header file doesn't exist. filename = " + filename;
			logger.warn(LogUtil.getRequestInfoStr(requestInfo) +
					"[getHeadersFromCache] " + msg);
			throw new IOException(msg);
		}

		Map<String, String> headers = new HashMap<String, String>();
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(
					new FileInputStream(file), Constants.CHARSET));
			String line = reader.readLine();	// 1行目はリビジョンなので読み捨てる。
			if (line != null) {
				// 2行目以降がヘッダ情報
				while ((line = reader.readLine()) != null) {
					if (!StringUtils.isBlank(line)) {
						int idx = line.indexOf(CloudStorageConst.CACHE_HEADERS_DELIMITER);
						String key = null;
						String val = null;
						if (idx == -1) {
							key = line;
							val = "";
						} else {
							key = line.substring(0, idx);
							val = line.substring(idx + 1);
						}
						headers.put(key, val);
					}
				}
			}
		} finally {
			if (reader != null) {
				reader.close();
			}
		}

		return headers;
	}

	/**
	 * コンテンツをキャッシュから取得する.
	 * @param entry Entry
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return コンテンツ情報
	 */
	ReflexContentInfo readFromCache(EntryBase entry, String serviceName,
			String namespace, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String uri = entry.getMyUri();
		int revision = TaggingEntryUtil.getRevisionById(entry.id);
		String updated = entry.updated;

		// コンテンツ
		String contentFilepath = CloudStorageUtil.getCacheContentFilepath(uri,
				serviceName, namespace, requestInfo, connectionInfo);
		File contentFile = new File(contentFilepath);
		if (!contentFile.exists() || !contentFile.isFile()) {
			if (logger.isTraceEnabled()) {
				logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
						"[readFromCache] contentFile doesn't exist. " + contentFile);
			}
			return null;
		}

		// ヘッダ情報
		String headerFilepath = CloudStorageUtil.getCacheHeaderFilepath(uri,
				serviceName, namespace, requestInfo, connectionInfo);
		File headerFile = new File(headerFilepath);
		if (!headerFile.exists() || !headerFile.isFile()) {
			if (logger.isTraceEnabled()) {
				logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
						"[readFromCache] headerFile doesn't exist. " + headerFile);
			}
			return null;
		}
		Map<String, String> headers = new HashMap<String, String>();
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(
					new FileInputStream(headerFile), Constants.CHARSET));
			String line = reader.readLine();	// 1行目
			if (StringUtils.isBlank(line)) {
				if (logger.isTraceEnabled()) {
					logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
							"[readFromCache] Nothing is written on the first line of headerFile. " + headerFile);
				}
				return null;
			}

			// リビジョンと更新日時チェック
			if (line.indexOf(CloudStorageConst.CACHE_REVISION_UPDATED_DELIMITER) < 1) {
				if (logger.isDebugEnabled()) {
					StringBuilder sb = new StringBuilder();
					sb.append(LogUtil.getRequestInfoStr(requestInfo));
					sb.append("[readFromCache] The format of cache revision is invalid. " + line);
					logger.debug(sb.toString());
				}
				return null;
			}
			// リビジョン+更新日時の比較
			String entryRev = getLocalCacheRevision(revision, updated);
			if (!line.equals(entryRev)) {
				if (logger.isDebugEnabled()) {
					StringBuilder sb = new StringBuilder();
					sb.append(LogUtil.getRequestInfoStr(requestInfo));
					sb.append("[readFromCache] The cache revision is old. cache revision = ");
					sb.append(line);
					sb.append(" :  entry revision = ");
					sb.append(entryRev);
					logger.debug(sb.toString());
				}
				return null;
			}

			// 2行目以降はヘッダ情報
			while((line = reader.readLine()) != null) {
				if (!StringUtils.isBlank(line)) {
					int idx = line.indexOf(CloudStorageConst.CACHE_HEADERS_DELIMITER);
					String key = null;
					String val = null;
					if (idx > -1) {
						key = line.substring(0, idx);
						val = line.substring(idx + 1);
					} else {
						key = line;
						val = "";
					}
					headers.put(key, val);
				}
			}

		} finally {
			if (reader != null) {
				reader.close();
			}
		}

		return new LocalCacheInfo(uri, contentFile, headers, entry);
	}

	/**
	 * ローカルキャッシュの更新時刻を更新する.
	 * @param uri URI
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 正常に処理できた場合true
	 */
	boolean setLastModifiredToCache(String uri, String serviceName, String namespace,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
					"[setLastModifiredToCache] start. uri = " + uri);
		}
		String filename = CloudStorageUtil.getCacheContentFilepath(uri, serviceName,
				namespace, requestInfo, connectionInfo);
		File file = new File(filename);
		if (!file.exists()) {
			if (logger.isTraceEnabled()) {
				logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
						"[setLastModifiredToCache] The storage cache file doesn't exist. filename = " + filename);
			}
			return false;
		}
		boolean ret = file.setLastModified(new Date().getTime());
		if (!ret) {
			if (logger.isDebugEnabled()) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
						"[setLastModifiredToCache] setLastModified failed. filename = " + filename);
			}
			return false;
		}
		return true;
	}

	/**
	 * ローカルキャッシュのリビジョン(リビジョン+更新日時)を取得
	 * @param revision リビジョン
	 * @param updated 更新日時
	 * @return ローカルキャッシュのリビジョン
	 */
	private String getLocalCacheRevision(int revision, String updated) {
		StringBuilder sb = new StringBuilder();
		sb.append(revision);
		sb.append(CloudStorageConst.CACHE_REVISION_UPDATED_DELIMITER);
		sb.append(updated);
		return sb.toString();
	}

}
