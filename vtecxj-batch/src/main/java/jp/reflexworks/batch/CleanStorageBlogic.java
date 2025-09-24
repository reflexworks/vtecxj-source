package jp.reflexworks.batch;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage.BucketListOption;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.taggingservice.api.ReflexBlogic;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.storage.CloudStorage;
import jp.reflexworks.taggingservice.storage.CloudStorageException;
import jp.reflexworks.taggingservice.storage.CloudStorageUtil;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * ストレージクリーン処理.
 *  ・不要なバケットを削除する。
 */
public class CleanStorageBlogic implements ReflexBlogic<ReflexContext, Boolean> {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 実行処理.
	 * @param reflexContext ReflexContext
	 * @param args 引数 (ReflexApplication実行時の引数の4番目以降)
	 *             [0]有効なサービス・名前空間マップファイル名(サービス名:名前空間)(フルパス)
	 */
	public Boolean exec(ReflexContext reflexContext, String[] args) {
		// 引数チェック
		if (args == null || args.length < 1) {
			throw new IllegalArgumentException("引数を指定してください。[0]有効なサービス・名前空間マップファイル名");
		}
		String validNamespacesFilename = args[0];
		if (StringUtils.isBlank(validNamespacesFilename)) {
			throw new IllegalArgumentException("引数を指定してください。[0]有効なサービス・名前空間マップファイル名");
		}

		// サービス名がシステム管理サービスでなければエラー
		String systemService = reflexContext.getServiceName();
		if (!systemService.equals(TaggingEnvUtil.getSystemService())) {
			throw new IllegalStateException("Please specify system service for the service name.");
		}

		// SystemContext作成
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		SystemContext systemContext = new SystemContext(systemService,
				requestInfo, reflexContext.getConnectionInfo());

		try {
			// 有効なサービス名・名前空間を取得
			Map<String, String> validNamespaces = VtecxBatchUtil.getKeyValueList(
					validNamespacesFilename, VtecxBatchConst.DELIMITER_SERVICE_NAMESPACE);

			execProc(systemContext, validNamespaces);

		} catch (IOException | TaggingException e) {
			throw new RuntimeException(e);
		}
		return true;
	}

	/**
	 * BDB Entryをデータストアにコピーする処理.
	 * システム管理サービスで実行してください。
	 * @param systemContext SystemContext
	 * @param validNamespaces サービスと名前空間の一覧
	 */
	public void execProc(SystemContext systemContext, Map<String, String> validNamespaces)
	throws IOException, TaggingException {
		// サービス名がシステム管理サービスでなければエラー
		String systemService = systemContext.getServiceName();
		if (!systemService.equals(TaggingEnvUtil.getSystemService())) {
			throw new IllegalStateException("Please specify system service for the service name.");
		}
		if (validNamespaces == null || validNamespaces.isEmpty()) {
			throw new IllegalStateException("有効なサービスがありません。");
		}

		// `/_service/{有効なサービス名}/content` エントリーを検索する。
		// エイリアス`/_bucket/{バケット名}`よりバケット名を取得し、有効なサービスのバケットリストに追加する。
		Set<String> validBucketNames = new HashSet<>();
		for (Map.Entry<String, String> mapEntry : validNamespaces.entrySet()) {
			String serviceName = mapEntry.getKey();
			String uri = CloudStorageUtil.getContentUri(serviceName);
			EntryBase entry = systemContext.getEntry(uri);
			String tmpBucketName = CloudStorageUtil.getBucketNameByEntry(entry);
			if (!StringUtils.isBlank(tmpBucketName)) {
				validBucketNames.add(tmpBucketName);
			}
		}

		// 有効なバケットが1つもない場合、安全のため処理を終了する。
		if (validBucketNames.isEmpty()) {
			logger.info("[delete bucket] There is no valid bucket.");
			return;
		}

		// ストレージ接続オブジェクトを取得
		CloudStorage storage = getStorage(systemService);

		// バケット名の接頭辞 `{stage}-`
		String bucketNamePrefix = CloudStorageUtil.getBucketPrefix();
		// バケット名接頭辞チェック
		boolean isOk = false;
		for (String tmpBucketName : validBucketNames) {
			if (tmpBucketName.startsWith(bucketNamePrefix)) {
				isOk = true;
				break;
			}
		}
		if (!isOk) {
			// 有効なバケット名が接頭辞と1個も合致しない場合、安全のため処理を終了する。
			logger.info("[delete bucket] Valid bucket names do not match the prefix. " + bucketNamePrefix);
			return;
		}

		// Storageからバケット一覧を取得する。
		// `{stage}-` で前方一致検索とする。
		Page<Bucket> currentBuckets = getBucketList(bucketNamePrefix, storage);
		// バケット名のうち、有効なサービスのバケットリストに存在しないものを削除する。
		for (Bucket bucket : currentBuckets.iterateAll()) {
			String bucketName = bucket.getName();
			if (!validBucketNames.contains(bucketName)) {
				// 削除
				// まずはコンテンツを削除
				Page<Blob> blobs = bucket.list();
				for (Blob blob : blobs.iterateAll()) {
					boolean retDelBlob = blob.delete();
					if (!retDelBlob) {
						logger.warn("[delete bucket object] failed. " + blob.getName());
					}
				}

				// バケットを削除
				boolean ret = bucket.delete();
				if (ret) {
					logger.info("[delete bucket] succeeded. " + bucketName);
				} else {
					logger.warn("[delete bucket] failed. " + bucketName);
				}
				
				// バケット情報を持つエントリーを削除(エイリアス除去)
				String bucketUri = CloudStorageUtil.getBucketUri(bucketName);
				try {
					systemContext.delete(bucketUri);
					logger.info("[delete bucket entry] succeeded. " + bucketName);
				} catch (TaggingException te) {
					// loggerに出力し、処理を続ける。
					StringBuilder sb = new StringBuilder();
					sb.append("[delete bucket entry] failed. ");
					sb.append(bucketName);
					sb.append(" ");
					sb.append(te.getClass().getName());
					sb.append(": ");
					sb.append(te.getMessage());
					logger.warn(sb.toString(), te);
				}
			}
		}
	}

	/**
	 * ストレージ接続オブジェクトを取得.
	 * @param systemService サービス名
	 * @return ストレージ接続オブジェクト
	 */
	private CloudStorage getStorage(String systemService)
	throws IOException {
		// 秘密鍵の取得
		byte[] secret = CloudStorageUtil.getBucketSecret(systemService);
		// Storage接続インタフェース取得
		return CloudStorageUtil.getStorage(secret);
	}

	/**
	 * 先頭が指定された接頭辞のバケットリストを取得.
	 * @param prefix 接頭辞
	 * @param storage ストレージ接続オブジェクト
	 * @return バケットリスト
	 */
	private Page<Bucket> getBucketList(String prefix, CloudStorage storage)
	throws IOException {
		// リトライ回数
		int numRetries = CloudStorageUtil.getStorageDownloadRetryCount();
		int waitMillis = CloudStorageUtil.getStorageDownloadRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				// バケットリスト取得
				BucketListOption bucketListOption = BucketListOption.prefix(prefix);
				return storage.list(bucketListOption);

			} catch (CloudStorageException e) {
				// リトライ判定、入力エラー判定
				CloudStorageUtil.convertError(e);
				if (r >= numRetries) {
					// リトライ対象だがリトライ回数を超えた場合
					CloudStorageUtil.convertIOError(e);
				}
				CloudStorageUtil.sleep(waitMillis + r * 10);
			}
		}

		throw new IllegalStateException("Unreachable code. (getBucketList)");
	}

}
