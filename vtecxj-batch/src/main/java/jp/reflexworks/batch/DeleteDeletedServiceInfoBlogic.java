package jp.reflexworks.batch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.api.Condition;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.entry.Link;
import jp.reflexworks.taggingservice.api.ReflexBlogic;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.ServiceManager;
import jp.reflexworks.taggingservice.service.TaggingServiceUtil;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.DateUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * 一定期間経過した削除済みサービスの情報削除.
 */
public class DeleteDeletedServiceInfoBlogic implements ReflexBlogic<ReflexContext, Boolean> {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 実行処理.
	 * @param reflexContext ReflexContext
	 * @param args 引数 (ReflexApplication実行時の引数の4番目以降)
	 */
	public Boolean exec(ReflexContext reflexContext, String[] args) {
		// サービス名がシステム管理サービスでなければエラー
		String systemService = reflexContext.getServiceName();
		if (!systemService.equals(TaggingEnvUtil.getSystemService())) {
			throw new IllegalStateException("Please specify system service for the service name.");
		}

		// SystemContext作成
		SystemContext systemContext = new SystemContext(systemService,
				reflexContext.getRequestInfo(), reflexContext.getConnectionInfo());

		try {
			deleteDeletedService(systemContext);

		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (TaggingException e) {
			throw new RuntimeException(e);
		}
		return true;
	}
	
	/**
	 * 一定期間経過した削除済みサービスの情報削除処理
	 * @param systemContext SystemContext
	 */
	private void deleteDeletedService(SystemContext systemContext)
	throws IOException, TaggingException {
		String systemService = systemContext.getServiceName();
		// 設定から削除済みサービス情報の削除待ち日数を取得
		ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
		int deletionDay = serviceManager.getServicePendingDeletionDay();
		int serviceLimit = TaggingEnvUtil.getEntryNumberLimit();
		int accessCounterLimit = TaggingEnvUtil.getUpdateEntryNumberLimit();

		// 削除待ち日数を超えた削除済みサービスをFeed検索
		String requestUri = getRequestUri(deletionDay, serviceLimit);
		String cursorStr = null;
		do {
			String tmpUri = addCursorStr(requestUri, cursorStr);
			FeedBase feed = systemContext.getFeed(tmpUri);
			cursorStr = TaggingEntryUtil.getCursorFromFeed(feed);
			if (TaggingEntryUtil.isExistData(feed)) {
				for (EntryBase entry : feed.entry) {
					String targetServiceUri = entry.getMyUri();
					// アクセスカウンタ(addids)を削除
					String targetService = TaggingEntryUtil.getSelfidUri(targetServiceUri);
					if (logger.isInfoEnabled()) {
						logger.info("[DeleteDeletedServiceInfo] delete deleted service: " + targetService);
					}
					String accessCounterUri = addLimit(
							TaggingServiceUtil.getAccessCountUri(targetService), accessCounterLimit);
					FeedBase accessCounterFeed = systemContext.getidsList(accessCounterUri);
					if (accessCounterFeed != null && 
							TaggingEntryUtil.isExistData(accessCounterFeed)) {
						// アクセスカウンタを削除
						List<Link> links = new ArrayList<>();
						for (EntryBase accessCounterEntry : accessCounterFeed.entry) {
							// TODO test log
							if (logger.isInfoEnabled()) {
								logger.info("[DeleteDeletedServiceInfo] delete deleted service's access counter: " + 
										accessCounterEntry.title + " " + accessCounterEntry.summary);
							}

							Link link = new Link();
							link._$href = accessCounterEntry.title;
							links.add(link);
						}
						FeedBase deleteidsFeed = TaggingEntryUtil.createFeed(systemService);
						deleteidsFeed.link = links;
						systemContext.deleteids(deleteidsFeed);
					}
					
					// 取得した`/_service/{サービス名}`エントリーをフォルダ削除
					systemContext.deleteFolder(targetServiceUri, false, true);
				}
			}
			
		} while (!StringUtils.isBlank(cursorStr));

	}
	
	/**
	 * 削除待ち日数を超えた削除済みサービスをFeed検索するためのURIを取得
	 * @param deletionDay 削除待ち日数
	 * @return Feed検索URI
	 */
	private String getRequestUri(int deletionDay, int limit) {
		// /_service?subtitle=deleted&updated-lt-{削除待ち日時}
		Date now = new Date();
		Date deletionDate = DateUtil.addTime(now, 0, 0, 0 - deletionDay, 0, 0, 0, 0);
		String timezoneId = TimeZone.getDefault().getID();
		String deletionDateStr = DateUtil.getDateTimeMillisec(deletionDate, timezoneId);

		StringBuilder sb = new StringBuilder();
		sb.append(Constants.URI_SERVICE);
		sb.append("?subtitle=");
		sb.append(Constants.SERVICE_STATUS_DELETED);
		sb.append("&updated");
		sb.append(Condition.DELIMITER);
		sb.append(Condition.LESS_THAN);
		sb.append(Condition.DELIMITER);
		sb.append(deletionDateStr);
		String tmpUri = sb.toString();
		return addLimit(tmpUri, limit);
	}
	
	/**
	 * カーソル付加
	 * @param requestUri リクエストURI
	 * @param cursorStr カーソル
	 * @return カーソルを付加したリクエストURI
	 */
	private String addCursorStr(String requestUri, String cursorStr) {
		if (StringUtils.isBlank(cursorStr)) {
			return requestUri;
		}
		StringBuilder sb = new StringBuilder();
		sb.append(requestUri);
		sb.append("&");
		sb.append(RequestParam.PARAM_NEXT);
		sb.append("=");
		sb.append(cursorStr);
		return sb.toString();
	}
	
	/**
	 * リクエストURIに最大取得件数パラメータを付加
	 * @param uri リクエストURI
	 * @param limit 最大取得件数
	 * @return 編集したリクエストURI
	 */
	private String addLimit(String uri, int limit) {
		StringBuilder sb = new StringBuilder();
		boolean isFirstParam = true;
		if (!StringUtils.isBlank(uri)) {
			sb.append(uri);
			if (uri.indexOf("?") >= 0) {
				isFirstParam = false;
			}
		}
		if (isFirstParam) {
			sb.append("?");
		} else {
			sb.append("&");
		}
		sb.append(RequestParam.PARAM_LIMIT);
		sb.append("=");
		sb.append(limit);
		return sb.toString();
	}

}
