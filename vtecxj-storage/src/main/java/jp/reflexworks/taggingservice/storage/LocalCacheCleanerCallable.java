package jp.reflexworks.taggingservice.storage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * キャッシュ容量確認非同期処理.
 */
public class LocalCacheCleanerCallable extends ReflexCallable<Boolean> {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * キャッシュ容量確認.
	 * 指定された容量を超えた場合、更新時刻の古いものから削除する。
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) + "[cleaner call] start.");
		}
		clean();
		return true;
	}

	/**
	 * キャッシュ容量確認.
	 * 容量を超える場合は更新時刻の古いものから削除する。
	 */
	private void clean() throws IOException, TaggingException {
		String cacheDirStr = CloudStorageUtil.getStorageCacheDir();
		if (StringUtils.isBlank(cacheDirStr)) {
			return;
		}
		File cacheDir = new File(cacheDirStr);
		if (!cacheDir.exists() || !cacheDir.isDirectory()) {
			return;
		}
		File[] serviceDirs = cacheDir.listFiles();
		if (serviceDirs == null || serviceDirs.length == 0) {
			return;
		}
		long totalSize = 0;
		List<File> contentFileList = new ArrayList<File>();
		for (File serviceDir : serviceDirs) {
			if (serviceDir != null && serviceDir.isDirectory()) {
				// コンテンツファイル
				File contentDir = getContentDir(serviceDir);
				if (contentDir != null && contentDir.isDirectory()) {
					File[] contentFiles = contentDir.listFiles();
					if (contentFiles != null && contentFiles.length > 0) {
						for (File contentFile : contentFiles) {
							if (contentFile.isFile()) {
								contentFileList.add(contentFile);
								totalSize += contentFile.length();
							}
						}
					}
				}
				// ヘッダ情報ファイル
				File headersDir = getHeadersDir(serviceDir);
				if (headersDir != null && headersDir.isDirectory()) {
					File[] headersFiles = headersDir.listFiles();
					if (headersFiles != null && headersFiles.length > 0) {
						for (File headersFile : headersFiles) {
							if (headersFile.isFile()) {
								totalSize += headersFile.length();
							}
						}
					}
				}
			}
		}

		// 現在のローカルキャッシュ総バイト数と、最大バイト数しきい値を比較する。
		long cacheMaxsize = CloudStorageUtil.getCacheMaxsize();
		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(getRequestInfo()));
			sb.append("[clean] localCache TotalSize = ");
			sb.append(totalSize);
			sb.append(" MaxSize = ");
			sb.append(cacheMaxsize);
			logger.trace(sb.toString());
		}
		if (totalSize <= cacheMaxsize) {
			return;
		}
		// コンテンツファイルの更新日時の古いもの順にソートする。
		LocalCacheComparator comparator = new LocalCacheComparator();
		Collections.sort(contentFileList, comparator);
		// 古いものから削除していく。
		LocalCacheManager localCacheManager = new LocalCacheManager();
		String serviceName = getServiceName();
		String namespace = getNamespace();
		RequestInfo requestInfo = getRequestInfo();
		ConnectionInfo connectionInfo = getConnectionInfo();
		for (File contentFile : contentFileList) {
			String uri = CloudStorageUtil.getUriByCacheFilename(contentFile.getName());
			long size = localCacheManager.deleteFromCache(uri, serviceName, namespace,
					requestInfo, connectionInfo);
			totalSize -= size;
			if (totalSize <= cacheMaxsize) {
				// 最大サイズしきい値より容量が減った場合は処理終了。
				return;
			}
		}
	}

	/**
	 * ローカルキャッシュのサービスディレクトリからコンテンツディレクトリを取得する.
	 *  {ローカルキャッシュディレクトリ}/{サービスディレクトリ}/content
	 * @param serviceDir サービスディレクトリ
	 * @return コンテンツディレクトリ
	 */
	private File getContentDir(File serviceDir) {
		return getUnderServiceDir(serviceDir, CloudStorageConst.CACHE_DIR_CONTENTS);
	}

	/**
	 * ローカルキャッシュのサービスディレクトリからヘッダ情報ディレクトリを取得する.
	 *  {ローカルキャッシュディレクトリ}/{サービスディレクトリ}/headers
	 * @param serviceDir サービスディレクトリ
	 * @return ヘッダ情報ディレクトリ
	 */
	private File getHeadersDir(File serviceDir) {
		return getUnderServiceDir(serviceDir, CloudStorageConst.CACHE_DIR_HEADERS);
	}

	/**
	 * ローカルキャッシュのサービスディレクトリから指定された配下のディレクトリを取得する.
	 *  {ローカルキャッシュディレクトリ}/{サービスディレクトリ}/{指定ディレクトリ}
	 * @param serviceDir サービスディレクトリ
	 * @param dir content or headers
	 * @return サービスディレクトリ配下のディレクトリ
	 */
	private File getUnderServiceDir(File serviceDir, String dir) {
		if (serviceDir == null) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		sb.append(serviceDir.getPath());
		sb.append(File.separator);
		sb.append(dir);
		return new File(sb.toString());
	}

}
