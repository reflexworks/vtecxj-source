package jp.reflexworks.taggingservice.memorysort;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.api.Condition;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.entry.Link;
import jp.reflexworks.atom.mapper.FeedTemplateConst;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.atom.mapper.FeedTemplateMapper.Meta;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.blogic.DatastoreBlogic;
import jp.reflexworks.taggingservice.blogic.PaginationBlogic;
import jp.reflexworks.taggingservice.blogic.PaginationConst;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.LockingException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.memorysort.comparator.BooleanComparator;
import jp.reflexworks.taggingservice.memorysort.comparator.DateComparator;
import jp.reflexworks.taggingservice.memorysort.comparator.DoubleComparator;
import jp.reflexworks.taggingservice.memorysort.comparator.FloatComparator;
import jp.reflexworks.taggingservice.memorysort.comparator.IntegerComparator;
import jp.reflexworks.taggingservice.memorysort.comparator.KeyComparator;
import jp.reflexworks.taggingservice.memorysort.comparator.LongComparator;
import jp.reflexworks.taggingservice.memorysort.comparator.StringComparator;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.MessageUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * インメモリソート　ビジネスロジック
 */
public class MemorySortBlogic {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * Feed検索とインメモリソート
	 * @param param リクエストパラメータ
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @param reflexContext ReflexContext
	 * @return 総ページ数
	 */
	public FeedBase getFeedAndMemorySort(RequestParam param, 
			String targetServiceName, String targetServiceKey,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		PaginationBlogic paginationBlogic = new PaginationBlogic();
		String conditionName = paginationBlogic.getConditionName(param);
		if (logger.isDebugEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[getFeedAndMemorySort] start. conditionName=");
			sb.append(conditionName);
			sb.append(" sid=");
			sb.append(reflexContext.getAuth().getSessionId());
			logger.debug(sb.toString());
		}

		try {
			// 入力チェック
			// サービス名が設定されていなければエラー
			String serviceName = reflexContext.getServiceName();
			CheckUtil.checkNotNull(serviceName, "serviceName");

			// インメモリソート条件が指定されていなければエラー
			Condition sortCondition = param.getSort();
			if (sortCondition == null) {
				throw new IllegalParameterException("The memory sort condition is not specified.");
			}

			if (logger.isDebugEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[getFeedAndMemorySort] getFeed start. conditionName=");
				sb.append(conditionName);
				logger.debug(sb.toString());
			}

			// データ取得
			// BDBサーバへFeed検索リクエストを送信する。
			// レスポンスにカーソルが設定されている場合はカーソルを付けて再検索し、条件に合うデータを全件取得する。
			List<EntryBase> entries = new ArrayList<EntryBase>();
			String cursorStr = null;
			int limit = getLimit(param, serviceName);
			String baseConditionUri = paginationBlogic.removeSortParam(conditionName, param.getSort());
			do {
				String conditionUri = paginationBlogic.editConditionUri(baseConditionUri, cursorStr);
				FeedBase tmpFeed = reflexContext.getFeed(conditionUri, targetServiceName, targetServiceKey);
				cursorStr = TaggingEntryUtil.getCursorFromFeed(tmpFeed);
				if (tmpFeed != null && tmpFeed.entry != null && !tmpFeed.entry.isEmpty()) {
					entries.addAll(tmpFeed.entry);
				}

				// 検索の合間にインメモリソート処理排他フラグを読む。
				String memoryLock = reflexContext.getSessionString(PaginationConst.SESSION_KEY_MEMORYSORT_LOCK);
				if (!conditionName.equals(memoryLock)) {
					// 処理停止
					if (logger.isInfoEnabled()) {
						StringBuilder sb = new StringBuilder();
						sb.append(LogUtil.getRequestInfoStr(requestInfo));
						sb.append("[getFeedAndMemorySort] memory sort stopped. conditionName=");
						sb.append(conditionName);
						sb.append(" (next condition=");
						sb.append(memoryLock);
						sb.append(")");
						logger.info(sb.toString());
					}
					return null;
				}

			} while (!StringUtils.isBlank(cursorStr));

			if (logger.isDebugEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[getFeedAndMemorySort] getFeed end. sort start. conditionName=");
				sb.append(conditionName);
				logger.debug(sb.toString());
			}

			// ソート
			// 指定されたソート条件でソート処理を行う。
			String tmpServiceName = targetServiceName;
			if (StringUtils.isBlank(tmpServiceName)) {
				tmpServiceName = serviceName;
			}
			Comparator<EntryBase> comparator = getComparator(sortCondition, tmpServiceName);
			if (comparator == null) {
				// テンプレートが変更された等
				throw new LockingException("The template might be changed.");
			}
			entries.sort(comparator);

			if (logger.isDebugEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[getFeedAndMemorySort] sort end. setSession start. conditionName=");
				sb.append(conditionName);
				logger.debug(sb.toString());
			}

			// セッションへ結果を登録
			// インメモリソート済みIDリストをページごとに登録する。
			int pageNum = 0;
			int cnt = 0;
			List<EntryBase> tmpEntries = new ArrayList<EntryBase>();
			for (EntryBase entry : entries) {
				tmpEntries.add(entry);
				cnt++;
				if (cnt >= limit) {
					pageNum++;
					FeedBase pageFeed = createMemorySortPage(tmpEntries, serviceName);
					String sessionKey = paginationBlogic.getMemorySortListKey(
							conditionName, pageNum, tmpServiceName);
					reflexContext.setSessionFeed(sessionKey, pageFeed);
					tmpEntries.clear();
					cnt = 0;
				}
			}
			if (!tmpEntries.isEmpty()) {
				pageNum++;
				FeedBase pageFeed = createMemorySortPage(tmpEntries, serviceName);
				String sessionKey = paginationBlogic.getMemorySortListKey(conditionName, pageNum,
						tmpServiceName);
				reflexContext.setSessionFeed(sessionKey, pageFeed);
			}

			// インメモリソートリストの総ページ数を登録する。
			String sessionKey = paginationBlogic.getMemorySortPagenumKey(conditionName, 
					tmpServiceName);
			reflexContext.setSessionLong(sessionKey, pageNum);

			if (logger.isDebugEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[getFeedAndMemorySort] setSession end. conditionName=");
				sb.append(conditionName);
				logger.debug(sb.toString());
			}

			// feedのtitleに総ページ数を設定
			FeedBase retFeed = createMessageFeed(String.valueOf(pageNum), serviceName);
			return retFeed;

		} finally {
			// インメモリソート処理排他フラグを削除
			String memoryLock = reflexContext.getSessionString(PaginationConst.SESSION_KEY_MEMORYSORT_LOCK);
			if (conditionName.equals(memoryLock)) {
				reflexContext.deleteSessionString(PaginationConst.SESSION_KEY_MEMORYSORT_LOCK);
				if (logger.isDebugEnabled()) {
					StringBuilder sb = new StringBuilder();
					sb.append(LogUtil.getRequestInfoStr(requestInfo));
					sb.append("[getFeedAndMemorySort] deleteSession (memorysort_lock) end. conditionName=");
					sb.append(conditionName);
					logger.debug(sb.toString());
				}
			}
		}
	}

	/**
	 * インメモリソートコンパレータを取得.
	 * @param sortCondition ソート条件
	 * @param targetServiceName 対象サービス名
	 * @return インメモリソートコンパレータ
	 */
	private Comparator<EntryBase> getComparator(Condition sortCondition, String targetServiceName) {
		String fieldName = sortCondition.getProp();
		boolean isDesc = Condition.DESC.equals(sortCondition.getEquations());
		if (RequestParam.KEYSORT.equals(fieldName)) {
			return new KeyComparator(isDesc);
		}
		FeedTemplateMapper mapper = TaggingEnvUtil.getResourceMapper(targetServiceName);
		List<Meta> metalist = mapper.getMetalist();
		Meta fieldMeta = null;
		for (Meta meta : metalist) {
			if (fieldName.equals(meta.name)) {
				fieldMeta = meta;
				break;
			}
		}
		if (fieldMeta != null) {
			String type = fieldMeta.type;
			if (FeedTemplateConst.META_TYPE_STRING.equals(type)) {
				return new StringComparator(fieldName, isDesc);
			} else if (FeedTemplateConst.META_TYPE_INTEGER.equals(type)) {
				return new IntegerComparator(fieldName, isDesc);
			} else if (FeedTemplateConst.META_TYPE_LONG.equals(type)) {
				return new LongComparator(fieldName, isDesc);
			} else if (FeedTemplateConst.META_TYPE_DATE.equals(type)) {
				return new DateComparator(fieldName, isDesc);
			} else if (FeedTemplateConst.META_TYPE_BOOLEAN.equals(type)) {
				return new BooleanComparator(fieldName, isDesc);
			} else if (FeedTemplateConst.META_TYPE_DOUBLE.equals(type)) {
				return new DoubleComparator(fieldName, isDesc);
			} else if (FeedTemplateConst.META_TYPE_FLOAT.equals(type)) {
				return new FloatComparator(fieldName, isDesc);
			}
		}
		return null;
	}

	/**
	 * 戻り値のメッセージFeedを作成.
	 * @param msg メッセージ
	 * @return メッセージFeed
	 */
	private FeedBase createMessageFeed(String msg, String serviceName) {
		return MessageUtil.createMessageFeed(msg, serviceName);
	}

	/**
	 * 1ページあたりの最大件数を取得.
	 * @param param 検索条件
	 * @param serviceName サービス名
	 * @return 1ページあたりの最大件数
	 */
	private int getLimit(RequestParam param, String serviceName)
	throws TaggingException {
		DatastoreBlogic datastoreBlogic = new DatastoreBlogic();
		return datastoreBlogic.getLimit(param.getOption(RequestParam.PARAM_LIMIT), serviceName);
	}

	/**
	 * Feedを生成し、linkタグのtitleに対象ページのIDを順に設定.
	 * @param tmpEntries 1ページ分のエントリーリスト.
	 * @param serviceName サービス名
	 * @return 1ページ分のキーリスト
	 */
	private FeedBase createMemorySortPage(List<EntryBase> tmpEntries, String serviceName) {
		List<Link> links = new ArrayList<>();
		for (EntryBase tmpEntry : tmpEntries) {
			Link link = new Link();
			link._$rel = Link.REL_SELF;
			link._$title = tmpEntry.id;
			links.add(link);
		}
		FeedBase feed = TaggingEntryUtil.createFeed(serviceName);
		feed.link = links;
		return feed;
	}

}
