package jp.reflexworks.taggingservice.blogic;

import java.util.Date;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.util.MessageUtil;
import jp.sourceforge.reflex.util.DateUtil;

/**
 * 現在時間取得ビジネスロジック
 */
public class DatetimeBlogic {
	
	/**
	 * 現在時間取得.
	 * @param serviceName サービス名
	 * @return 現在時間
	 */
	public FeedBase getDatetime(String serviceName) {
		String datetime = DateUtil.getDateTime(new Date());
		return MessageUtil.createMessageFeed(datetime, serviceName);
	}

}
