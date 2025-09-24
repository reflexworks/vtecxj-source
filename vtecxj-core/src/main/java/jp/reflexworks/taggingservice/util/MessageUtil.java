package jp.reflexworks.taggingservice.util;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;

public class MessageUtil {
	
	/**
	 * Feedを生成し、titleにメッセージを入れます。
	 * @param msg メッセージ
	 * @param serviceName サービス名
	 * @return Feed
	 */
	public static FeedBase createMessageFeed(String msg, String serviceName) {
		if (msg == null) {
			return null;
		}
		FeedBase feed = TaggingEntryUtil.createFeed(serviceName);
		feed.setTitle(msg);
		return feed;
	}
	
	/**
	 * Feedを生成し、titleにメッセージを入れます。
	 * @param msg メッセージ
	 * @param serviceName サービス名
	 * @return Feed
	 */
	public static FeedBase createMessageFeed(Long msg, String serviceName) {
		if (msg == null) {
			return null;
		}
		return createMessageFeed(msg.toString(), serviceName);
	}

	/**
	 * フィードのキーをカンマ区切りで並べて返却します.
	 * @param feed Feed
	 * @param serviceName サービス名
	 * @return フィードのキーをtitleに設定したメッセージフィード
	 */
	public static FeedBase getUrisMessageFeed(FeedBase feed, String serviceName) {
		String msg = getUris(feed);
		return createMessageFeed(msg, serviceName);
	}
	
	/**
	 * フィードのキーをカンマ区切りで並べて返却します.
	 * @param feed Feed
	 * @param serviceName サービス名
	 * @return フィードのキーをtitleに
	 */
	public static String getUris(FeedBase feed) {
		StringBuilder sb = new StringBuilder();
		if (feed != null && feed.entry != null) {
			boolean isFirst = true;
			for (EntryBase entry : feed.entry) {
				if (isFirst) {
					isFirst = false;
				} else {
					sb.append(",");
				}
				sb.append(entry.getMyUri());
			}
		}
		return sb.toString();
	}

}
