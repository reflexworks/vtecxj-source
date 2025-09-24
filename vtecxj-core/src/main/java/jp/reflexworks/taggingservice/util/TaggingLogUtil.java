package jp.reflexworks.taggingservice.util;

import java.io.IOException;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * ログエントリー番号取得ユーティリティ.
 */
public class TaggingLogUtil {

	/**
	 * ログのキーに使用する降順番号の取得.
	 * 指定されたキーでaddidsを取得し、Long.MAX_VALUE からaddidsの値を引く。
	 * @param systemContext
	 * @param addidsUri
	 * @return 降順番号
	 */
	public static String getLogNum(SystemContext systemContext, String addidsUri)
	throws IOException, TaggingException {
		FeedBase feed = systemContext.addids(addidsUri, 1);
		if (feed != null) {
			String num = feed.title;
			if (!StringUtils.isBlank(num)) {
				long numl = StringUtils.longValue(num);
				if (numl > 0) {
					numl = Long.MAX_VALUE - numl;
					return String.valueOf(numl);
				}
			}
		}
		return null;
	}

}
