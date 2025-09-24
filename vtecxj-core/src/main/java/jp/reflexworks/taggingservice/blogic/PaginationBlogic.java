package jp.reflexworks.taggingservice.blogic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.api.Condition;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.entry.Link;
import jp.reflexworks.servlet.util.UrlUtil;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.api.SettingConst;
import jp.reflexworks.taggingservice.context.ReflexContextUtil;
import jp.reflexworks.taggingservice.env.TaggingEnvConst;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.reflexworks.taggingservice.exception.NoEntryException;
import jp.reflexworks.taggingservice.exception.OptimisticLockingException;
import jp.reflexworks.taggingservice.exception.PermissionException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.DatastoreManager;
import jp.reflexworks.taggingservice.plugin.MemorySortManager;
import jp.reflexworks.taggingservice.util.AccessTimeComparator;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.RetryUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * ページング機能 ビジネスロジック.
 */
public class PaginationBlogic {

	/** 検索条件文字列から除外するパラメータ名 */
	public static final Set<String> IGNORE_PARAMS = new ConcurrentSkipListSet<String>();
	static {
		IGNORE_PARAMS.add(RequestParam.PARAM_PAGINATION);
		IGNORE_PARAMS.add(RequestParam.PARAM_XML);
		IGNORE_PARAMS.add(RequestParam.PARAM_JSON);
		IGNORE_PARAMS.add(RequestParam.PARAM_MESSAGEPACK);
		IGNORE_PARAMS.add(RequestParam.PARAM_NEXT);
		IGNORE_PARAMS.add(RequestParam.PARAM_NUMBER);
		IGNORE_PARAMS.add(RequestParam.PARAM_RXID);
		IGNORE_PARAMS.add(RequestParam.PARAM_TOKEN);
	}

	/** 開始ページ・終了ページ区切り文字 */
	public static final String PAGINATION_NUM_DELIMITER = ",";

	/** カーソルリスト最終ページ */
	public static final String CURSORLIST_LAST = "last";
	/** メモリソートの場合 */
	public static final String PAGINATION_MEMORYSORT = "memorysort";
	/** 通常のFeed検索の場合 */
	public static final String PAGINATION_GETFEED = RequestParam.PARAM_FEED;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * ページング.
	 * カーソルリストの取得、セッション保持処理を呼び出します。
	 * @param param RequestParam
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return title:最終ページ番号、subtitle:範囲内のエントリー件数、
	 *         link rel="next" があれば続きのデータあり。
	 *         rights:通常検索の場合"f"、メモリソートの場合"memorysort"
	 */
	public FeedBase paging(RequestParam param, String targetServiceName,
			String targetServiceKey, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 入力チェック
		// 戻り値は[0]開始ページ番号、[1]終了ページ番号
		Integer[] pageNums = checkPagination(param);
		String uri = param.getUri();

		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();

		// 対象サービス指定の場合、対象サービス認証を行う。
		ServiceBlogic serviceBlogic = new ServiceBlogic();
		ReflexAuthentication tmpAuth = serviceBlogic.authenticateByCooperationService(
				targetServiceName, targetServiceKey, auth, requestInfo, connectionInfo);

		// 先頭が/@/で始まるURIの場合、システム管理サービスのデータを読む。
		tmpAuth = serviceBlogic.getAuthForGet(uri, tmpAuth);
		String tmpServiceName = tmpAuth.getServiceName();

		// ACLチェック
		AclBlogic aclBlogic = new AclBlogic();
		String aclUri = null;
		if (param.isUrlForwardMatch()) {
			aclUri = uri;
		} else {
			aclUri = aclBlogic.getDummySelfidUri(uri);
		}
		aclBlogic.checkAcl(aclUri, AclConst.ACL_TYPE_RETRIEVE, tmpAuth,
				requestInfo, connectionInfo);

		// セッション存在チェック
		checkSession(tmpAuth);

		// パラメータ内容チェック
		//   取得ページ数が規定のページ数を超えるとエラー
		//   開始ページ数指定の場合、セッションに「`開始ページ数-1ページ`」のカーソルが登録されていなければエラー
		String conditionName = getConditionName(param);
		checkPagenationLimit(param, conditionName, pageNums, tmpServiceName, reflexContext);

		// 同期処理に変更
		int startPageNum = 1;
		if (pageNums[0] != null && pageNums[0] > 1) {
			startPageNum = pageNums[0];
		}
		int endPageNum = pageNums[1];
		return pagingProc(param, conditionName, startPageNum, endPageNum, reflexContext, tmpAuth);
	}

	/**
	 * セッション存在チェック.
	 * @param auth 認証情報
	 * @param auth
	 */
	private void checkSession(ReflexAuthentication auth) 
	throws PermissionException {
		if (auth == null || auth.getSessionId() == null) {
			PermissionException pe = new PermissionException();
			pe.setSubMessage("The session is required.");
			throw new PermissionException();
		}
	}

	/**
	 * ページング処理入力チェック
	 * @param param 入力情報
	 * @return 開始ページ番号,終了ページ番号
	 */
	private Integer[] checkPagination(RequestParam param) {
		// URIチェック
		CheckUtil.checkRequestParam(param);

		// ページ番号
		String name = "Pagination number";
		String pagination = param.getOption(RequestParam.PARAM_PAGINATION);
		CheckUtil.checkNotNull(pagination, name);

		String startPageNumStr = null;
		String endPageNumStr = null;
		int idx = pagination.indexOf(PAGINATION_NUM_DELIMITER);
		if (idx > -1) {
			startPageNumStr = pagination.substring(0, idx);
			endPageNumStr = pagination.substring(idx + 1);
		} else {
			endPageNumStr = pagination;
		}

		// 数値チェック
		Integer startPageNum = null;
		Integer endPageNum = null;
		name = "Pagination end number";
		CheckUtil.checkInt(endPageNumStr, name);
		endPageNum = Integer.parseInt(endPageNumStr);
		CheckUtil.checkPositiveNumber(endPageNum, name);

		if (startPageNumStr != null) {
			name = "Pagination start number";
			CheckUtil.checkInt(startPageNumStr, name);
			startPageNum = Integer.parseInt(startPageNumStr);
			CheckUtil.checkPositiveNumber(startPageNum, name);
			// 大小チェック
			CheckUtil.checkCompare(startPageNum, endPageNum, true);
		}

		/*
		// l=*の指定はエラー -> 全件をインメモリソートしたい場合もあるかもしれないのでチェックしない。
		String limit = param.getOption(RequestParam.PARAM_LIMIT);
		if (limit != null) {
			name = "limit";
			CheckUtil.checkInt(limit, name);
		}
		*/

		return new Integer[]{startPageNum, endPageNum};
	}

	/**
	 * ページング処理最大数チェック
	 * @param param 検索条件
	 * @param conditionName キー+検索条件文字列
	 * @param pageNums 開始・終了ページ番号
	 * @param targetServiceName 対象サービス名
	 * @param reflexContext ReflexContext
	 */
	private void checkPagenationLimit(RequestParam param, String conditionName,
			Integer[] pageNums, String targetServiceName, ReflexContext reflexContext)
	throws IOException, TaggingException {
		//   取得ページ数が規定のページ数を超えるとエラー
		//     システム設定: _pagination.limit
		long paginationLimit = TaggingEnvUtil.getSystemPropLong(
				TaggingEnvConst.PAGINATION_LIMIT,
				TaggingEnvConst.PAGINATION_LIMIT_DEFAULT);
		int pageCount = 0;
		int startPage = 1;
		if (pageNums[0] == null) {
			pageCount = pageNums[1];
		} else {
			pageCount = pageNums[1] - pageNums[0] + 1;
			startPage = pageNums[0];
		}
		if (pageCount > paginationLimit) {
			throw new IllegalParameterException("The maximum pagination limit is exceeded. " + pageCount);
		}

		//   開始ページ数指定の場合、セッションに「開始ページ数-1ページ」のカーソルが登録されていなければエラー
		if (startPage > 1) {
			FeedBase cursorListFeed = getCursorList(conditionName, targetServiceName,
					reflexContext);
			checkStartPageNumber(cursorListFeed, startPage);
		}
	}

	/**
	 * 開始ページ番号チェック.
	 * 開始ページ番号が2以上の場合呼び出してください。
	 * @param cursorListFeed カーソルリスト
	 * @param startPageNum 開始ページ番号
	 */
	private void checkStartPageNumber(FeedBase cursorListFeed, int startPageNum) {
		if (cursorListFeed != null && cursorListFeed.link != null) {
			if (cursorListFeed.link.size() >= startPageNum - 1 &&
					!StringUtils.isBlank(cursorListFeed.link.get(startPageNum - 2)._$href)) {
				// OK
			} else {
				// 検索済みで続きのページが無い場合かどうか確認
				Link lastLink = cursorListFeed.link.get(cursorListFeed.link.size() - 1);
				if (isLastPage(lastLink)) {
					throw new IllegalParameterException("The entry of the specified page does not exist. Specified page number = " + startPageNum);
				} else {
					throw new IllegalParameterException("Please make a previous pagination index in advance. Specified page number = " + startPageNum);
				}
			}
		} else {
			// 未検索
			throw new IllegalParameterException("Please make a previous pagination index in advance. Specified page number = " + startPageNum);
		}
	}

	/**
	 * カーソルリストを取得
	 * @param conditionName キー+検索条件文字列
	 * @param targetServiceName 対象サービス名
	 * @param reflexContext ReflexContext
	 * @return カーソルリスト格納Feed
	 */
	private FeedBase getCursorList(String conditionName, String targetServiceName,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		String cursorListKey = getCursorListKey(conditionName, targetServiceName);
		return reflexContext.getSessionFeed(cursorListKey);
	}

	/**
	 * 検索条件文字列を取得.
	 * @param param 検索条件
	 * @return 検索条件文字列
	 */
	public String getConditionName(RequestParam param) {
		String uri = param.getUri();
		String queryString = UrlUtil.editQueryString(param.getQueryString(), IGNORE_PARAMS, null, false);
		StringBuilder sb = new StringBuilder();
		sb.append(uri);
		if (param.isUrlForwardMatch()) {
			sb.append(RequestParam.WILDCARD);
		}
		if (!StringUtils.isBlank(queryString)) {
			sb.append(queryString);
		}
		return sb.toString();
	}

	/**
	 * セッションのカーソルリスト格納キーを取得.
	 * _PAGINATION_LIST+キー+検索条件
	 * @param conditionName キー+検索条件文字列.
	 * @param serviceName サービス名
	 * @return セッションのカーソルリスト格納キー
	 */
	private String getCursorListKey(String conditionName, String serviceName) {
		StringBuilder sb = new StringBuilder();
		sb.append(PaginationConst.SESSION_KEY_CURSORLIST);
		sb.append(Constants.SVC_PREFIX_VAL);
		sb.append(serviceName);
		sb.append(Constants.SVC_PREFIX_VAL);
		sb.append(conditionName);
		return sb.toString();
	}

	/**
	 * セッションのページング処理ロックキーを取得.
	 * _PAGINATION_LOCK+キー+検索条件
	 * @param conditionName キー+検索条件文字列.
	 * @param serviceName サービス名
	 * @return セッションのページング処理ロックキー
	 */
	public String getLockKey(String conditionName, String serviceName) {
		StringBuilder sb = new StringBuilder();
		sb.append(PaginationConst.SESSION_KEY_LOCK);
		sb.append(Constants.SVC_PREFIX_VAL);
		sb.append(serviceName);
		sb.append(Constants.SVC_PREFIX_VAL);
		sb.append(conditionName);
		return sb.toString();
	}

	/**
	 * セッションのカーソルリストアクセス時刻格納キーを取得.
	 * _PAGINATION_ACCESSTIME+キー+検索条件
	 * @param conditionName キー+検索条件文字列.
	 * @return セッションのカーソルリストアクセス時刻格納キー
	 */
	private String getAccessTimeKey(String conditionName, String serviceName) {
		StringBuilder sb = new StringBuilder();
		sb.append(PaginationConst.SESSION_KEY_ACCESSTIME);
		sb.append(Constants.SVC_PREFIX_VAL);
		sb.append(serviceName);
		sb.append(Constants.SVC_PREFIX_VAL);
		sb.append(conditionName);
		return sb.toString();
	}

	/**
	 * インメモリソートのページ数格納キーを取得.
	 * @param conditionName キー+検索条件文字列.
	 * @param serviceName サービス名
	 * @return インメモリソートのページ数格納キー
	 */
	public String getMemorySortPagenumKey(String conditionName, String serviceName) {
		StringBuilder sb = new StringBuilder();
		sb.append(PaginationConst.SESSION_KEY_MEMORYSORT_PAGENUM);
		sb.append(Constants.SVC_PREFIX_VAL);
		sb.append(serviceName);
		sb.append(Constants.SVC_PREFIX_VAL);
		sb.append(conditionName);
		return sb.toString();
	}

	/**
	 * インメモリソートの対象ページキーリスト格納キーを取得.
	 * @param conditionName キー+検索条件文字列.
	 * @param num ページ数
	 * @param serviceName サービス名
	 * @return インメモリソートの対象ページキーリスト格納キー
	 */
	public String getMemorySortListKey(String conditionName, int num, String serviceName) {
		StringBuilder sb = new StringBuilder();
		sb.append(PaginationConst.SESSION_KEY_MEMORYSORT_LIST);
		sb.append(Constants.SVC_PREFIX_VAL);
		sb.append(serviceName);
		sb.append(Constants.SVC_PREFIX_VAL);
		sb.append(conditionName);
		sb.append(PaginationConst.SESSION_KEY_MEMORYSORT_LIST_DELIMITER);
		sb.append(num);
		return sb.toString();
	}

	/**
	 * ページング処理
	 * @param param 検索条件
	 * @param conditionName 検索条件
	 * @param startPageNum 開始ページ番号
	 * @param endPageNum 終了ページ番号
	 * @param reflexContext ReflexContext
	 * @param tmpAuth 対象サービスの認証情報
	 * @return title:最終ページ番号、subtitle:範囲内のエントリー件数、
	 *         link rel="next" があれば続きのデータあり。
	 *         rights:通常検索の場合"f"、メモリソートの場合"memorysort"
	 */
	public FeedBase pagingProc(RequestParam param, String conditionName,
			int startPageNum, int endPageNum,
			ReflexContext reflexContext, ReflexAuthentication tmpAuth)
	throws IOException, TaggingException {
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		// 呼び出し元サービス名
		String sourceServiceName = reflexContext.getServiceName();
		// 処理対象サービス名
		String targetServiceName = tmpAuth.getServiceName();
		ReflexContext targetReflexContext = null;
		if (sourceServiceName.equals(targetServiceName)) {
			targetReflexContext = reflexContext;
		} else {
			targetReflexContext = ReflexContextUtil.getReflexContext(tmpAuth,
					requestInfo, connectionInfo);
		}

		// 処理中フラグキー
		String lockKey = getLockKey(conditionName, targetServiceName);
		long lock = 0;
		boolean callMemorySort = false;
		try {
			// 処理中フラグをONにする。
			lock = reflexContext.incrementSession(lockKey, 1);
			if (lock > 1) {
				if (logger.isDebugEnabled()) {
					StringBuilder sb = new StringBuilder();
					sb.append(LogUtil.getRequestInfoStr(requestInfo));
					sb.append("[pagingProc] Pagination locked. conditionName=");
					sb.append(conditionName);
					logger.debug(sb.toString());
				}
				String msg = "Pagination locked. " + conditionName;
				throw new OptimisticLockingException(msg);
			}

			// インメモリソート非同期処理を呼び出すかどうか
			String currentMemorySortConditionName = null;
			// インメモリソート対象の場合、インメモリソート処理排他フラグをONにする。
			if (param.getSort() != null) {
				String memorySortLockKey = PaginationConst.SESSION_KEY_MEMORYSORT_LOCK;
				currentMemorySortConditionName = reflexContext.getSessionString(memorySortLockKey);
				if (!conditionName.equals(currentMemorySortConditionName) ||
						startPageNum <= 1) {
					if (logger.isDebugEnabled()) {
						StringBuilder sb = new StringBuilder();
						sb.append(LogUtil.getRequestInfoStr(requestInfo));
						sb.append("[pagingProc] setSessionString. key=");
						sb.append(memorySortLockKey);
						sb.append(" value=");
						sb.append(conditionName);
						logger.debug(sb.toString());
					}
					// 上書きされたインメモリソートは、スレッド側で停止する。
					reflexContext.setSessionString(memorySortLockKey, conditionName);
					callMemorySort = true;
				}
			}

			// インメモリソート対象の場合、インメモリソート処理を実行する。
			// 非同期なのでページ数検索前に実行する。
			if (callMemorySort) {
				MemorySortManager memorySortManager = TaggingEnvUtil.getMemorySortManager();
				memorySortManager.sort(param, conditionName, tmpAuth, reflexContext);
				lock = 0;
			}

			// カーソルリストアクセス時間を設定
			setAccessTime(conditionName, targetServiceName, reflexContext);

			// カーソルリスト数チェック
			// カーソルリスト一覧を取得
			List<String> keys = reflexContext.getSessionFeedKeys();
			List<String> cursorListKeys = getCursorListKeys(keys);
			int cursorListCnt = 0;
			if (cursorListKeys != null) {
				cursorListCnt = cursorListKeys.size();
			}
			int cursorListLimit = TaggingEnvUtil.getSystemPropInt(
					TaggingEnvConst.POINTERSLIST_LIMIT,
					TaggingEnvConst.POINTERSLIST_LIMIT_DEFAULT);
			if (cursorListCnt >= cursorListLimit) {
				// アクセス時刻の古いカーソルリストを削除
				deleteCursorList(cursorListKeys, cursorListLimit, reflexContext);
			}

			// セッションからカーソルリスト取得
			int startIdx = startPageNum - 1;
			FeedBase cursorListFeed = getCursorList(conditionName, targetServiceName,
					reflexContext);
			if (cursorListFeed == null) {
				cursorListFeed = TaggingEntryUtil.createFeed(sourceServiceName);
			}
			if (cursorListFeed.link == null) {
				cursorListFeed.link = new ArrayList<Link>();
			} else {
				// 検索対象箇所のカーソルがすでに設定されている場合は削除する。
				int size = cursorListFeed.link.size();
				if (size >= startPageNum) {
					for (int i = size - 1; i >= startIdx; i--) {
						cursorListFeed.link.remove(i);
					}
				}
			}

			// 開始ページが2ページ以上の場合、検索条件にカーソルをセットする。
			String cursorStr = null;
			if (startPageNum > 1) {
				// カーソルリストを元に開始ページ番号チェック
				checkStartPageNumber(cursorListFeed, startPageNum);
				cursorStr = getCursorFromList(cursorListFeed,
						startPageNum - 2);
			}

			// 以下指定ページ数繰り返し
			long entryCnt = 0;
			boolean noEntry = false;
			boolean hasNext = true;
			String baseConditionUri = removeSortParam(conditionName, param.getSort());
			for (int i = startIdx; i < endPageNum; i++) {
				// カーソルがある場合検索条件に付加する。
				String conditionUri = editConditionUri(baseConditionUri, cursorStr);

				// 検索
				FeedBase feed = targetReflexContext.getFeed(conditionUri);
				if (TaggingEntryUtil.isExistData(feed)) {
					entryCnt += feed.entry.size();
				}

				// カーソルがあれば格納、なければlast指定し終了
				cursorStr = TaggingEntryUtil.getCursorFromFeed(feed);
				if (!StringUtils.isBlank(cursorStr)) {
					setCursorToList(cursorListFeed, cursorStr);
				} else {
					setLastToList(cursorListFeed);
					hasNext = false;
					if (i == 0 && (feed == null || feed.entry == null || feed.entry.isEmpty())) {
						noEntry = true;
					}
					break;
				}
			}

			// 最終ページ番号取得
			int lastPageNum = 0;
			if (!noEntry && cursorListFeed != null && cursorListFeed.link != null) {
				lastPageNum = cursorListFeed.link.size();	// 最終データはカーソルなしの"title=last"
			}

			// カーソルリストを登録
			String cursorListKey = getCursorListKey(conditionName, targetServiceName);
			reflexContext.setSessionFeed(cursorListKey, cursorListFeed);
			if (logger.isDebugEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[pagingProc] The cursor list was created. ");
				sb.append(conditionName);
				sb.append(" start=");
				sb.append(startPageNum);
				sb.append(" end=");
				sb.append(endPageNum);
				sb.append(" lastPage=");
				sb.append(lastPageNum);
				logger.debug(sb.toString());
			}

			FeedBase feed = TaggingEntryUtil.createFeed(sourceServiceName);
			feed.title = String.valueOf(lastPageNum);
			feed.subtitle = String.valueOf(entryCnt);
			if (hasNext) {
				Link link = new Link();
				link._$rel = Link.REL_NEXT;
				feed.link = new ArrayList<>();
				feed.link.add(link);
			}
			// メモリソート検索の場合一度に全てのカーソルインデックスを作成するため、その旨を伝える。
			if (callMemorySort) {
				feed.rights = PAGINATION_MEMORYSORT;
			} else {
				feed.rights = PAGINATION_GETFEED;
			}
			return feed;

		} finally {
			if (lock == 1 && !callMemorySort) {
				// 処理中フラグをOFFにする。
				releaseLock(conditionName, targetServiceName, reflexContext);
				if (logger.isDebugEnabled()) {
					StringBuilder sb = new StringBuilder();
					sb.append(LogUtil.getRequestInfoStr(requestInfo));
					sb.append("[pagingProc] releaseLock end. conditionName=");
					sb.append(conditionName);
					logger.debug(sb.toString());
				}
			}
		}
	}

	/**
	 * ページネーションロック解除
	 * @param conditionName 検索条件文字列 (セッション格納時のキーに使用)
	 * @param targetServiceName 対象サービス名
	 * @param reflexContext ReflexContext
	 */
	public void releaseLock(String conditionName, String targetServiceName,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		String lockKey = getLockKey(conditionName, targetServiceName);
		reflexContext.setSessionLong(lockKey, 0);
	}

	/**
	 * 検索条件にカーソルを付加する。
	 * @param conditionName URI+検索条件
	 * @param cursorStr カーソル
	 * @return URI+検索条件+カーソル
	 */
	public String editConditionUri(String conditionName, String cursorStr) {
		StringBuilder sb = new StringBuilder();
		sb.append(conditionName);
		if (!StringUtils.isBlank(cursorStr)) {
			if (conditionName.indexOf("?") == -1) {
				sb.append("?");
			} else {
				sb.append("&");
			}
			sb.append(RequestParam.PARAM_NEXT);
			sb.append("=");
			sb.append(cursorStr);
		}
		return sb.toString();
	}

	/**
	 * キーリストの中からカーソルリストのキーを抽出
	 * @param keys キーリスト
	 * @return カーソルリストのキーリスト
	 */
	private List<String> getCursorListKeys(List<String> keys) {
		if (keys != null && !keys.isEmpty()) {
			// 先頭が`_PAGINATION_LIST`のキーがカーソルリスト
			List<String> cursorListKeys = new ArrayList<String>();
			for (String key : keys) {
				if (key.startsWith(PaginationConst.SESSION_KEY_CURSORLIST)) {
					cursorListKeys.add(key);
				}
			}
			return cursorListKeys;
		}
		return null;
	}

	/**
	 * カーソルリストを上限数まで削除.
	 * @param cursorListKeys カーソルリストキーリスト
	 * @param cursorListLimit カーソルリスト上限数
	 * @param reflexContext ReflexContext
	 */
	private void deleteCursorList(List<String> cursorListKeys, int cursorListLimit,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		// カーソルリストキーリストのうちアクセス時刻が新しいものを上限数-1分取得
		List<String> recentAccessKeys = getRecentAccessKeys(cursorListKeys,
				cursorListLimit - 1, reflexContext);

		// アクセス時刻が新しいキーリストに含まれないものを削除する。
		for (String cursorListKey : cursorListKeys) {
			if (!recentAccessKeys.contains(cursorListKey)) {
				String conditionName = getConditionNameByCursorListKey(cursorListKey);
				String targetServiceName = getTargetServiceNameByCursorListKey(cursorListKey);
				// ロック
				String lockKey = getLockKey(conditionName, targetServiceName);
				long lock = 0;
				try {
					lock = reflexContext.incrementSession(lockKey, 1);
					if (lock == 1) {
						// カーソルリスト削除
						reflexContext.deleteSessionFeed(cursorListKey);
						// アクセス時刻削除
						String accessTimeKey = getAccessTimeKey(conditionName, serviceName);
						reflexContext.deleteSessionLong(accessTimeKey);

						// インメモリソートリストが存在する場合、そちらも削除する。
						String pageNumKey = getMemorySortPagenumKey(conditionName, serviceName);
						Long pageNum = reflexContext.getSessionLong(pageNumKey);
						if (pageNum != null) {
							for (int i = 1; i <= pageNum; i++) {
								String listKey = getMemorySortListKey(conditionName, i, serviceName);
								reflexContext.deleteSessionFeed(listKey);
							}
							reflexContext.deleteSessionLong(pageNumKey);
						}

						if (logger.isDebugEnabled()) {
							logger.debug(LogUtil.getRequestInfoStr(reflexContext.getRequestInfo()) +
									"[deleteCursorList] The cursor list was deleted. " + conditionName);
						}
					} else {
						if (logger.isDebugEnabled()) {
							logger.debug(LogUtil.getRequestInfoStr(reflexContext.getRequestInfo()) +
									"[deleteCursorList] The cursor list was not deleted because it locked. " + conditionName);
						}
					}
				} finally {
					if (lock == 1) {
						// ロック削除
						reflexContext.deleteSessionLong(lockKey);
					}
				}
			}
		}
	}

	/**
	 * アクセス時刻の新しいものからカーソルリスト保持上限数分のキーリストを返却.
	 * @param accessListMap カーソルリストキーとアクセス時刻のMap
	 * @param cursorListLimit カーソルリスト保持上限数
	 * @param targetServiceName 対象サービス名
	 * @return カーソルリスト保持上限数分のキーリスト
	 */
	private List<String> getRecentAccessKeys(List<String> cursorListKeys,
			int cursorListLimit, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// キー: カーソルリストのキー、値: アクセス時刻
		Map<String, Long> accessTimeMap = new HashMap<String, Long>();
		for (String cursorListKey : cursorListKeys) {
			String conditionName = getConditionNameByCursorListKey(cursorListKey);
			String targetServiceName = getTargetServiceNameByCursorListKey(cursorListKey);
			String accesstimeKey = getAccessTimeKey(conditionName, targetServiceName);
			Long accessTime = reflexContext.getSessionLong(accesstimeKey);
			accessTimeMap.put(cursorListKey, accessTime);
		}

		// cursorListKeysをソート
		AccessTimeComparator comparator = new AccessTimeComparator(accessTimeMap);
		Collections.sort(cursorListKeys, comparator);

		// 先頭cursorListLimit分を抽出し返却
		return cursorListKeys.subList(0, cursorListLimit);
	}

	/**
	 * カーソルリストキーからURI+検索条件文字列を取得.
	 * カーソルリストキーは、_PAGINATION_LIST@{serviceName}@{URI+検索条件文字列}
	 * @param cursorListKey カーソルリストキー
	 * @return URI+検索条件文字列
	 */
	private String getConditionNameByCursorListKey(String cursorListKey) {
		int idx = cursorListKey.indexOf(Constants.SVC_PREFIX_VAL);
		int idx2 = cursorListKey.indexOf(Constants.SVC_PREFIX_VAL, idx + 1);
		return cursorListKey.substring(idx2 + 1);
	}

	/**
	 * カーソルリストキーから対象サービス名を取得.
	 * カーソルリストキーは、_PAGINATION_LIST@{serviceName}@{URI+検索条件文字列}
	 * @param cursorListKey カーソルリストキー
	 * @return URI+検索条件文字列
	 */
	private String getTargetServiceNameByCursorListKey(String cursorListKey) {
		int idx = cursorListKey.indexOf(Constants.SVC_PREFIX_VAL);
		int idx2 = cursorListKey.indexOf(Constants.SVC_PREFIX_VAL, idx + 1);
		return cursorListKey.substring(idx + 1, idx2);
	}

	/**
	 * カーソルをカーソルリストに追加.
	 * @param cursorListFeed カーソルリスト
	 * @param cursorStr カーソル
	 */
	private void setCursorToList(FeedBase cursorListFeed, String cursorStr) {
		Link link = new Link();
		link._$href = cursorStr;
		cursorListFeed.link.add(link);
	}

	/**
	 * 最終ページフラグをカーソルリストに追加.
	 * @param cursorListFeed カーソルリスト
	 */
	private void setLastToList(FeedBase cursorListFeed) {
		Link link = new Link();
		link._$title = PaginationBlogic.CURSORLIST_LAST;
		cursorListFeed.link.add(link);
	}

	/**
	 * ページ指定検索
	 * @param param 検索条件
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @param reflexContext ReflexContext(元のサービス)
	 * @return 検索結果
	 */
	public FeedBase getPage(RequestParam param, String targetServiceName,
			String targetServiceKey, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 入力チェック
		int page = checkGetPage(param);

		String uri = param.getUri();
		String serviceName = reflexContext.getServiceName();
		ReflexAuthentication auth = reflexContext.getAuth();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();

		// 対象サービス指定の場合、対象サービス認証を行う。
		ServiceBlogic serviceBlogic = new ServiceBlogic();
		ReflexAuthentication tmpAuth = serviceBlogic.authenticateByCooperationService(
				targetServiceName, targetServiceKey, auth, requestInfo, connectionInfo);

		// 先頭が/@/で始まるURIの場合、システム管理サービスのデータを読む。
		tmpAuth = serviceBlogic.getAuthForGet(uri, tmpAuth);
		if (StringUtils.isBlank(targetServiceName)) {
			targetServiceName = serviceName;
		}
		String tmpServiceName = tmpAuth.getServiceName();

		// ACLチェック
		AclBlogic aclBlogic = new AclBlogic();
		String aclUri = null;
		if (param.isUrlForwardMatch()) {
			aclUri = uri;
		} else {
			aclUri = aclBlogic.getDummySelfidUri(uri);
		}
		aclBlogic.checkAcl(aclUri, AclConst.ACL_TYPE_RETRIEVE, tmpAuth,
				requestInfo, connectionInfo);

		// セッション存在チェック
		checkSession(tmpAuth);

		// カーソルリストアクセス時間を設定
		String conditionName = getConditionName(param);
		setAccessTime(conditionName, tmpServiceName, reflexContext);

		// 処理中の場合一定時間待つ
		checkLock(conditionName, targetServiceName, reflexContext);

		// インメモリソートか、カーソルリストからの取得かを判定
		if (param.getSort() == null) {
			// カーソルリストからの取得
			return getPageByCursorList(conditionName, page, tmpAuth, reflexContext);
		} else {
			// インメモリソート
			return getPageByMemorySort(conditionName, page, tmpAuth, reflexContext);
		}
	}

	/**
	 * カーソルリストから指定ページを取得.
	 * @param conditionName 検索条件
	 * @param page ページ番号
	 * @param tmpAuth 検索対象サービスの認証情報
	 * @param reflexContext ReflexContext(元のサービス)
	 * @return Feed
	 */
	private FeedBase getPageByCursorList(String conditionName, int page,
			ReflexAuthentication tmpAuth, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		String tmpServiceName = tmpAuth.getServiceName();

		// カーソルリストと入力値のチェック
		FeedBase cursorListFeed = getCursorList(conditionName, tmpServiceName,
				reflexContext);
		checkCursorList(cursorListFeed, page);

		// カーソル取得
		String cursorStr = null;
		if (page > 1) {
			cursorStr = getCursorFromList(cursorListFeed, page - 2);
			if (cursorStr == null) {
				if (logger.isDebugEnabled()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
							"[getPage] cursor is null. (last page)");
				}
				return null;
			}
		}

		// カーソルを指定してFeed検索
		String conditionUri = editConditionUri(conditionName, cursorStr);
		// /@/配下のエントリーはシステム管理サービスのデータを読む。
		// 対象サービス指定の場合にも対応
		ReflexContext tmpReflexContext = null;
		if (serviceName.equals(tmpServiceName)) {
			tmpReflexContext = reflexContext;
		} else {
			tmpReflexContext = ReflexContextUtil.getReflexContext(tmpAuth,
					requestInfo, connectionInfo);
		}
		return tmpReflexContext.getFeed(conditionUri);
	}

	/**
	 * インメモリソートリストから指定ページを取得.
	 * @param conditionName 検索条件
	 * @param page ページ番号
	 * @param tmpAuth 検索対象サービスの認証情報
	 * @param reflexContext ReflexContext(元のサービス)
	 * @return Feed
	 */
	private FeedBase getPageByMemorySort(String conditionName, int page,
			ReflexAuthentication tmpAuth, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();	// 元のサービス
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		String targetServiceName = tmpAuth.getServiceName();	// 検索対象サービス

		// ページ番号チェック
		checkGetPageByMemorySort(conditionName, page, targetServiceName, reflexContext);

		// 指定ページのIDリストを取得。
		String idsKey = getMemorySortListKey(conditionName, page, targetServiceName);
		FeedBase idsFeed = reflexContext.getSessionFeed(idsKey);
		if (idsFeed == null || idsFeed.link == null || idsFeed.link.isEmpty()) {
			throw new NoEntryException();
		}

		// IDをキーにエントリーを取得し、Feedに詰めて返却する。
		DatastoreManager datastoreManager = TaggingEnvUtil.getDatastoreManager();
		FeedBase tmpFeed = datastoreManager.getEntriesByIds(idsFeed, serviceName,
				tmpAuth, requestInfo, connectionInfo);
		boolean isEmptyPageEntries = tmpFeed == null || tmpFeed.entry == null ||
				tmpFeed.entry.isEmpty();

		// IDをキーとしているので、Entryが更新された場合IDも変更されるためEntryが抽出されない。
		// この場合ID URIの等しいEntryを抽出する。ただし削除された場合は抽出されない。
		List<EntryBase> retEntries = new ArrayList<>();
		List<String> ids = TaggingEntryUtil.getIds(idsFeed);
		for (String id : ids) {
			EntryBase entry = null;
			if (!isEmptyPageEntries) {
				for (EntryBase tmpEntry : tmpFeed.entry) {
					if (tmpEntry != null && id.equals(tmpEntry.id)) {
						entry = tmpEntry;
					}
				}
			}
			if (entry == null) {
				// ID URIでEntryを抽出
				String idUri = TaggingEntryUtil.getUriById(id);
				entry = datastoreManager.getEntry(idUri, true, serviceName,
						tmpAuth, requestInfo, connectionInfo);
			}
			if (entry != null) {
				retEntries.add(entry);
			}
		}
		FeedBase retFeed = TaggingEntryUtil.createFeed(targetServiceName);
		retFeed.entry = retEntries;

		// Feed編集 (項目ACL適用)
		String parentUri = getParentUriByConditionName(conditionName);
		boolean isNometa = getNometaByConditionName(conditionName);
		DatastoreBlogic datastoreBlogic = new DatastoreBlogic();
		datastoreBlogic.editFeed(parentUri, retFeed, isNometa, tmpAuth);
		return retFeed;
	}

	/**
	 * ページ指定検索入力チェック
	 * @param param 入力情報
	 * @return ページ番号
	 */
	private int checkGetPage(RequestParam param) {
		// URIチェック
		CheckUtil.checkRequestParam(param);

		// ページ番号
		String name = "Page number";
		String pageStr = param.getOption(RequestParam.PARAM_NUMBER);
		CheckUtil.checkNotNull(pageStr, name);
		// 数値チェック
		CheckUtil.checkInt(pageStr, name);
		int page = Integer.parseInt(pageStr);
		CheckUtil.checkPositiveNumber(page, name);
		return page;
	}

	/**
	 * カーソルリストと入力値のチェック
	 * @param cursorListFeed カーソルリスト
	 * @param page 指定ページ
	 */
	private void checkCursorList(FeedBase cursorListFeed, int page) {
		if (cursorListFeed == null || cursorListFeed.link == null) {
			// セッションに指定条件のカーソルリストが存在しない場合は400エラー
			throw new IllegalParameterException("Please make a pagination index in advance.");
		}
		int size = cursorListFeed.link.size();
		if (page > 1 && size <= page - 1) {
			// 前ページにカーソルがあるか、最終ページかを確認
			if (isLastPage(cursorListFeed.link.get(size - 1))) {
				// 最終ページ
				throw new IllegalParameterException("There is no designated page.");
			} else {
				// 続きのカーソルリスト作成を行っていない
				throw new IllegalParameterException("Please make a pagination index in advance.");
			}
		}
	}

	/**
	 * カーソルリストからカーソルを取得.
	 * @param cursorListFeed カーソルリスト
	 * @param i インデックス
	 * @return カーソル
	 */
	private String getCursorFromList(FeedBase cursorListFeed, int i) {
		if (cursorListFeed != null && cursorListFeed.link != null &&
				cursorListFeed.link.size() > i) {
			return cursorListFeed.link.get(i)._$href;
		}
		return null;
	}

	/**
	 * カーソルリストアクセス時間を設定.
	 * @param conditionName URI+検索条件
	 * @param targetServiceName 対象サービス名
	 * @param reflexContext ReflexContext
	 */
	private void setAccessTime(String conditionName, String targetServiceName,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		String accessTimeKey = getAccessTimeKey(conditionName, targetServiceName);
		reflexContext.setSessionLong(accessTimeKey, new Date().getTime());
	}

	/**
	 * 最終ページかどうか判定
	 * @param link Link(カーソルまたは最終ページ格納オブジェクト)
	 * @return 最終ページの場合true
	 */
	private boolean isLastPage(Link link) {
		return StringUtils.isBlank(link._$href) &&
				CURSORLIST_LAST.equals(link._$title);
	}

	/**
	 * ページ番号チェック.
	 * インメモリソート処理中の場合、一定期間待つ。
	 * @param conditionName 検索条件
	 * @param page ページ番号
	 * @param tmpServiceName 対象サービス名
	 * @param reflexContext ReflexContext
	 */
	private void checkGetPageByMemorySort(String conditionName, int page,
			String tmpServiceName, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// インメモリソートリストの総ページ数を読んでページ数チェック
		String memorysortPageNum = getMemorySortPagenumKey(conditionName, tmpServiceName);

		String myServiceName = reflexContext.getServiceName();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		int numRetries = getProcessingGetRetryCount(myServiceName, requestInfo);
		int waitMillis = getProcessingGetRetryWaitmillis(myServiceName, requestInfo);
		for (int r = 0; r <= numRetries; r++) {
			boolean isRetry = false;
			Long pageNum = reflexContext.getSessionLong(memorysortPageNum);
			if (pageNum == null) {
				// ページネーション未処理
				// インメモリソート処理排他フラグを読み、処理中かどうかをチェックする。
				String lockConditionName = reflexContext.getSessionString(
						PaginationConst.SESSION_KEY_MEMORYSORT_LOCK);
				if (conditionName.equals(lockConditionName)) {
					// 処理中
					isRetry = true;
				} else {
					// ページリストなし
					throw new IllegalParameterException("Please make a pagination index in advance.");
				}
			} else if (page > pageNum) {
				// ページ番号が最大ページを超えている場合エラー
				throw new IllegalParameterException("There is no designated page. The last page: " + pageNum);
			}

			if (!isRetry) {
				// 正常
				return;
			}
			// 処理中のためスリープ
			if (logger.isDebugEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[checkGetPageByMemorySort] in progress. retry(");
				sb.append(r);
				sb.append(" / ");
				sb.append(numRetries);
				sb.append(")");
				logger.debug(sb.toString());
			}
			RetryUtil.sleep(waitMillis);
		}
		// 処理中でリトライ回数超過
		throw new IllegalParameterException("This process is still in progress. Please wait.");
	}

	/**
	 * 検索条件文字列から親キーを取得.
	 * @param conditionName 検索条件文字列
	 * @return 親キー
	 */
	private String getParentUriByConditionName(String conditionName) {
		int idx = conditionName.indexOf("?");
		if (idx > -1) {
			return conditionName.substring(0, idx);
		} else {
			return conditionName;
		}
	}

	/**
	 * 検索条件文字列からnometa指定されているかどうかを取得.
	 * @param conditionName 検索条件文字列
	 * @return nometa指定されている場合true
	 */
	private boolean getNometaByConditionName(String conditionName) {
		String nometa = getParamValueByConditionName(conditionName, RequestParam.PARAM_NOMETA);
		return !StringUtils.isBlank(nometa);
	}

	/**
	 * 検索条件文字列から指定された名前の値を取得.
	 * @param conditionName 検索条件文字列
	 * @param name パラメータ名
	 * @return パラメータの値。値の設定が無い場合は空文字("")。パラメータの指定が無い場合はnull。
	 */
	private String getParamValueByConditionName(String conditionName, String name) {
		int idx = conditionName.indexOf("?");
		if (idx == -1) {
			return null;
		}
		String paramStr = conditionName.substring(idx + 1);
		String[] paramParts = paramStr.split("&");
		for (String paramPart : paramParts) {
			String key = null;
			String val = null;
			int j = paramPart.indexOf("=");
			if (j == -1) {
				key = paramPart;
				val = "";
			} else {
				key = paramPart.substring(0, j);
				val = paramPart.substring(j + 1);
			}
			if (name.equals(key)) {
				return val;
			}
		}
		return null;
	}

	/**
	 * インメモリソート等処理中の場合のリトライ回数を取得.
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @return インメモリソート等処理中の場合のリトライ回数
	 */
	private int getProcessingGetRetryCount(String serviceName, RequestInfo requestInfo) {
		try {
			return TaggingEnvUtil.getPropInt(
					serviceName,
					SettingConst.PROCESSING_GET_RETRY_COUNT,
					TaggingEnvConst.PROCESSING_GET_RETRY_COUNT_DEFAULT);
		} catch (InvalidServiceSettingException e) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[getProcessingGetRetryCount] InvalidServiceSettingException: ");
			sb.append(SettingConst.PROCESSING_GET_RETRY_COUNT);
			logger.warn(sb.toString(), e);
			return TaggingEnvUtil.getSystemPropInt(
					SettingConst.PROCESSING_GET_RETRY_COUNT,
					TaggingEnvConst.PROCESSING_GET_RETRY_COUNT_DEFAULT);
		}
	}

	/**
	 * インメモリソート等処理中の場合のリトライ時待ち時間(ミリ秒)を取得.
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @return インメモリソート等処理中の場合のリトライ時待ち時間(ミリ秒)
	 */
	private int getProcessingGetRetryWaitmillis(String serviceName, RequestInfo requestInfo) {
		try {
			return TaggingEnvUtil.getPropInt(
					serviceName,
					SettingConst.PROCESSING_GET_RETRY_WAITMILLIS,
					TaggingEnvConst.PROCESSING_GET_RETRY_WAITMILLIS_DEFAULT);
		} catch (InvalidServiceSettingException e) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[getProcessingGetRetryWaitmillis] InvalidServiceSettingException: ");
			sb.append(SettingConst.PROCESSING_GET_RETRY_WAITMILLIS);
			logger.warn(sb.toString(), e);
			return TaggingEnvUtil.getSystemPropInt(
					SettingConst.PROCESSING_GET_RETRY_WAITMILLIS,
					TaggingEnvConst.PROCESSING_GET_RETRY_WAITMILLIS_DEFAULT);
		}
	}

	/**
	 * 処理中フラグをチェックする.
	 * 処理中の場合、一定時間待つ。
	 * @param conditionName キー+検索条件文字列
	 * @param targetServiceName 対象サービス名
	 * @param reflexContext ReflexContext
	 */
	private void checkLock(String conditionName, String targetServiceName,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		// 処理中フラグキー
		String lockKey = getLockKey(conditionName, targetServiceName);

		String myServiceName = reflexContext.getServiceName();
		RequestInfo requestInfo = reflexContext.getRequestInfo();

		int numRetries = getProcessingGetRetryCount(myServiceName, requestInfo);
		int waitMillis = getProcessingGetRetryWaitmillis(myServiceName, requestInfo);
		for (int r = 0; r <= numRetries; r++) {
			Long lock = reflexContext.getSessionLong(lockKey);
			if (lock == null || lock == 0) {
				return;
			}
			// 処理中のためスリープ
			if (logger.isDebugEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[checkLock] in progress. retry(");
				sb.append(r);
				sb.append(" / ");
				sb.append(numRetries);
				sb.append(")");
				logger.debug(sb.toString());
			}
			RetryUtil.sleep(waitMillis);
		}
		// 処理中でリトライ回数超過
		throw new IllegalParameterException("This process is still in progress. Please wait.");
	}

	/**
	 * PathInfo+QueryString文字列から、ソート条件を除去して返却する.
	 * @param conditionName PathInfo+QueryString
	 * @param sortCondition ソート条件
	 * @return 編集したPathInfo+QueryString
	 */
	public String removeSortParam(String conditionName, Condition sortCondition) {
		String baseConditionUri = null;
		if (sortCondition != null) {
			Set<String> ignoreSortParam = new HashSet<>();
			// sパラメータ指定
			ignoreSortParam.add(RequestParam.PARAM_SORT);
			// 項目-{asc|desc}指定
			StringBuilder sb = new StringBuilder();
			sb.append(sortCondition.getProp());
			sb.append(Condition.DELIMITER);
			sb.append(sortCondition.getEquations());
			ignoreSortParam.add(sb.toString());

			baseConditionUri = UrlUtil.editPathInfoQuery(conditionName, ignoreSortParam, null, false);
		} else {
			baseConditionUri = conditionName;
		}
		return baseConditionUri;
	}

}
