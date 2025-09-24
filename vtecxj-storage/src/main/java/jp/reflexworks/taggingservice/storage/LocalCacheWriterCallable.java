package jp.reflexworks.taggingservice.storage;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.taggingservice.api.ReflexContentInfo;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.FileUtil;

/**
 * キャッシュへコンテンツ登録非同期処理.
 */
public class LocalCacheWriterCallable extends ReflexCallable<Boolean> {

	/** コンテンツEntry */
	private EntryBase entry;
	/** コンテンツ情報 */
	private ReflexContentInfo contentInfo;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param entry Entry
	 * @param contentInfo コンテンツ情報
	 */
	public LocalCacheWriterCallable(EntryBase entry,
			ReflexContentInfo contentInfo) {
		this.entry = entry;
		this.contentInfo = contentInfo;
	}

	/**
	 * キャッシュへコンテンツ登録する.
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) + "[writer call] start.");
		}
		int revision = TaggingEntryUtil.getRevisionById(entry.id);
		String updated = TaggingEntryUtil.getUpdated(entry);
		byte[] data = FileUtil.readInputStream(contentInfo.getInputStream());
		LocalCacheManager localCacheManager = new LocalCacheManager();
		localCacheManager.writeToCache(contentInfo.getUri(), revision, updated,
				data, contentInfo.getHeaders(), getAuth(), getNamespace(),
				getRequestInfo(), getConnectionInfo());
		return true;
	}
}
