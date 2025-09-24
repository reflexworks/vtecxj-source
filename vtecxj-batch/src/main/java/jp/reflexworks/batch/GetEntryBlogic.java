package jp.reflexworks.batch;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.taggingservice.api.ReflexBlogic;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * GET Entryバッチ.
 */
public class GetEntryBlogic implements ReflexBlogic<ReflexContext, EntryBase> {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 実行処理.
	 * @param reflexContext ReflexContext
	 * @param args 引数 (ReflexApplication実行時の引数の4番目以降)
	 *             [0]URI
	 *             [1]useCache (true/false、デフォルトはtrue)
	 */
	public EntryBase exec(ReflexContext reflexContext, String[] args) {
		if (args == null) {
			throw new IllegalStateException("引数がnullです。");
		}
		if (args.length < 1) {
			throw new IllegalStateException("引数が不足しています。[0]URI、[1]useCache");
		}
		Boolean useCache = null;
		if (args.length > 1) {
			useCache = StringUtils.booleanValue(args[1]);
		}

		SystemContext systemContext = new SystemContext(reflexContext.getServiceName(),
				reflexContext.getRequestInfo(), reflexContext.getConnectionInfo());

		EntryBase entry = null;
		try {
			String requestUri = args[0];
			if (logger.isTraceEnabled()) {
				logger.debug("[URI] = " + requestUri + " [useCache]" + useCache);
			}
			if (useCache != null) {
				entry = systemContext.getEntry(requestUri, useCache);
			} else {
				entry = systemContext.getEntry(requestUri);
			}

			FeedTemplateMapper mapper = systemContext.getResourceMapper();
			if (logger.isTraceEnabled()) {
				logger.trace("\n" + mapper.toXML(entry));
			}

		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (TaggingException e) {
			throw new RuntimeException(e);
		}
		return entry;
	}

}
