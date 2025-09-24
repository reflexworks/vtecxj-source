package jp.reflexworks.taggingservice.blogic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import jp.reflexworks.atom.api.AtomConst;
import jp.reflexworks.atom.api.Condition;
import jp.reflexworks.atom.entry.Category;
import jp.reflexworks.atom.entry.Contributor;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.entry.Link;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.atom.mapper.FeedTemplateMapper.Meta;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.reflexworks.taggingservice.exception.NoExistingEntryException;
import jp.reflexworks.taggingservice.exception.PermissionException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.EditedCondition;
import jp.reflexworks.taggingservice.model.UpdatedInfo;
import jp.reflexworks.taggingservice.plugin.DatastoreManager;
import jp.reflexworks.taggingservice.plugin.ServiceManager;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.AuthenticationUtil;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.IndexUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.reflexworks.taggingservice.util.TaggingIndexUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * データストア呼び出しのためのビジネスロジック.
 */
public class DatastoreBlogic {

	/**
	 * Entry検索
	 * @param param RequestParam
	 * @param useCache データストアキャッシュを検索する場合true
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 検索したEntry
	 */
	public EntryBase getEntry(RequestParam param, boolean useCache,
			String targetServiceName, String targetServiceKey,
			ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		// 対象サービス指定の場合、対象サービス認証を行う。
		ServiceBlogic serviceBlogic = new ServiceBlogic();
		ReflexAuthentication tmpAuth = serviceBlogic.authenticateByCooperationService(
				targetServiceName, targetServiceKey, auth, requestInfo, connectionInfo);

		return getEntry(param, useCache, serviceName, tmpAuth, requestInfo, connectionInfo);
	}

	/**
	 * Entry検索
	 * @param param RequestParam
	 * @param useCache データストアキャッシュを検索する場合true
	 * @param serviceName 元のサービス名
	 * @param tmpAuth 対象サービスの認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 検索したEntry
	 */
	private EntryBase getEntry(RequestParam param, boolean useCache,
			String serviceName, ReflexAuthentication tmpAuth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// キー入力チェック
		checkGetEntry(param);
		String uri = param.getUri();

		// 先頭が/@/で始まるURIの場合、システム管理サービスのデータを読む。
		ServiceBlogic serviceBlogic = new ServiceBlogic();
		tmpAuth = serviceBlogic.getAuthForGet(uri, tmpAuth);

		// ACLチェック
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAcl(uri, AclConst.ACL_TYPE_RETRIEVE, tmpAuth,
				requestInfo, connectionInfo);

		// Entry検索
		DatastoreManager datastoreManager = TaggingEnvUtil.getDatastoreManager();
		EntryBase entry = datastoreManager.getEntry(uri, useCache, serviceName,
				tmpAuth, requestInfo, connectionInfo);

		// Entry編集 (項目ACL適用)
		boolean isNometa = param.getOption(RequestParam.PARAM_NOMETA) != null;
		editEntry(uri, entry, isNometa, tmpAuth);

		return entry;
	}

	/**
	 * Feed検索
	 * @param param RequestParam
	 * @param useCache データストアキャッシュを検索する場合true
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 検索したFeed
	 */
	public FeedBase getFeed(RequestParam param, boolean useCache,
			String targetServiceName, String targetServiceKey,
			ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		// 対象サービス指定の場合、対象サービス認証を行う。
		ServiceBlogic serviceBlogic = new ServiceBlogic();
		ReflexAuthentication tmpAuth = serviceBlogic.authenticateByCooperationService(
				targetServiceName, targetServiceKey, auth, requestInfo, connectionInfo);

		return getFeed(param, useCache, serviceName, tmpAuth, requestInfo, connectionInfo);
	}

	/**
	 * Feed検索
	 * @param param RequestParam
	 * @param useCache データストアキャッシュを検索する場合true
	 * @param serviceName 元のサービス名
	 * @param targetAuth 対象サービスの認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 検索したFeed
	 */
	private FeedBase getFeed(RequestParam param, boolean useCache,
			String serviceName, ReflexAuthentication tmpAuth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// キー入力チェック
		checkGetFeed(param);
		String uri = param.getUri();

		// 先頭が/@/で始まるURIの場合、システム管理サービスのデータを読む。
		ServiceBlogic serviceBlogic = new ServiceBlogic();
		tmpAuth = serviceBlogic.getAuthForGet(uri, tmpAuth);

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

		// インメモリソート対象の場合エラー
		if (param.getSort() != null) {
			throw new IllegalParameterException(
					"This item cannot be sorted. Please sort by pagination. " +
					param.getSort().getProp());
		}

		// Feed検索
		DatastoreManager datastoreManager = TaggingEnvUtil.getDatastoreManager();
		String limitStr = param.getOption(RequestParam.PARAM_LIMIT);
		int limit = getLimit(limitStr, serviceName);
		boolean isNoLimit = RequestParam.WILDCARD.equals(limitStr);
		String cursorStr = getCursorStr(param);
		FeedBase feed = null;
		if (!isNoLimit) {
			feed = datastoreManager.getFeed(uri,
					param.getConditionsList(), param.isUrlForwardMatch(), limit,
					cursorStr, useCache, serviceName, tmpAuth, requestInfo, connectionInfo);
		} else {
			List<EntryBase> entries = new ArrayList<EntryBase>();
			String tmpCursorStr = cursorStr;
			do {
				FeedBase tmpFeed = datastoreManager.getFeed(uri, param.getConditionsList(),
						param.isUrlForwardMatch(), limit, tmpCursorStr, useCache, serviceName,
						tmpAuth, requestInfo, connectionInfo);
				if (tmpFeed != null && tmpFeed.entry != null) {
					entries.addAll(tmpFeed.entry);
				}
				tmpCursorStr = TaggingEntryUtil.getCursorFromFeed(tmpFeed);

			} while (!StringUtils.isBlank(tmpCursorStr));
			if (!entries.isEmpty()) {
				feed = TaggingEntryUtil.createFeed(serviceName);
				feed.entry = entries;
			}
		}

		// Feed編集 (項目ACL適用)
		boolean isNometa = param.getOption(RequestParam.PARAM_NOMETA) != null;
		String tmpParentUri = uri;
		if (param.isUrlForwardMatch()) {
			tmpParentUri = TaggingEntryUtil.removeLastSlash(
					TaggingEntryUtil.getParentUri(uri));
		}
		editFeed(tmpParentUri, feed, isNometa, tmpAuth);

		return feed;
	}

	/**
	 * 件数取得
	 * @param param RequestParam
	 * @param useCache データストアキャッシュを検索する場合true
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 件数(Feedのtitleに設定)、
	 *         フェッチ数を超えた場合はFeedのlink rel="next"のhrefにカーソルを設定。
	 */
	public FeedBase getCount(RequestParam param, boolean useCache,
			String targetServiceName, String targetServiceKey,
			ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// キー入力チェック
		checkGetFeed(param);
		String uri = param.getUri();
		String serviceName = auth.getServiceName();

		// 対象サービス指定の場合、対象サービス認証を行う。
		ServiceBlogic serviceBlogic = new ServiceBlogic();
		ReflexAuthentication tmpAuth = serviceBlogic.authenticateByCooperationService(
				targetServiceName, targetServiceKey, auth, requestInfo, connectionInfo);

		// 先頭が/@/で始まるURIの場合、システム管理サービスのデータを読む。
		tmpAuth = serviceBlogic.getAuthForGet(uri, tmpAuth);

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

		// 件数取得
		String cursorStr = getCursorStr(param);
		String limitStr = param.getOption(RequestParam.PARAM_LIMIT);
		boolean isNoLimit = RequestParam.WILDCARD.equals(limitStr);
		Integer limit = null;
		if (!StringUtils.isBlank(limitStr) && StringUtils.isInteger(limitStr)) {
			limit = Integer.parseInt(limitStr);
		}
		DatastoreManager datastoreManager = TaggingEnvUtil.getDatastoreManager();
		FeedBase feed = null;
		if (!isNoLimit) {
			feed = datastoreManager.getCount(uri, param.getConditionsList(),
					param.isUrlForwardMatch(), limit, cursorStr,
					useCache, serviceName, tmpAuth, requestInfo, connectionInfo);
		} else {
			String tmpCursorStr = cursorStr;
			long cnt = 0;
			do {
				FeedBase tmpFeed = datastoreManager.getCount(uri,
						param.getConditionsList(), param.isUrlForwardMatch(), limit,
						tmpCursorStr, useCache, serviceName, tmpAuth, requestInfo, connectionInfo);
				if (tmpFeed != null) {
					long tmpCnt = StringUtils.longValue(tmpFeed.title);
					cnt += tmpCnt;
				}
				tmpCursorStr = TaggingEntryUtil.getCursorFromFeed(tmpFeed);
			} while (!StringUtils.isBlank(tmpCursorStr));
			feed = TaggingEntryUtil.createFeed(serviceName);
			feed.title = String.valueOf(cnt);
		}

		return feed;
	}

	/**
	 * 上位階層のエントリーリストを検索.
	 * @param uri キー
	 * @param useCache メモリ上のキャッシュを参照する場合true
	 * @return 上位階層のエントリーリスト
	 */
	public FeedBase getParentPathEntries(String uri, boolean useCache,
			ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();

		// 自階層 + 親階層一覧
		List<String> layerUris = TaggingEntryUtil.getParentPathUris(uri);

		// Entry検索
		DatastoreManager datastoreManager = TaggingEnvUtil.getDatastoreManager();
		FeedBase parentPathFeed = datastoreManager.getParentPathEntries(uri, useCache,
				serviceName, auth, requestInfo, connectionInfo);
		if (TaggingEntryUtil.isExistData(parentPathFeed)) {
			int i = 0;
			for (EntryBase parentPathEntry : parentPathFeed.entry) {
				String layerUri = layerUris.get(i);
				editEntry(layerUri, parentPathEntry, false, auth);
				i++;
			}
			return parentPathFeed;

		} else {
			return null;
		}
	}

	/**
	 * 登録.
	 * キーが指定されていない場合、親階層+自動採番値をキーとします。
	 * キーが重複している場合はエラーを返します。
	 * @param feed Feed
	 * @param parentUri キー自動採番の場合の親階層
	 * @param ext キー自動採番の場合の拡張子
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 登録したFeed
	 */
	public FeedBase post(FeedBase feed, String parentUri, String ext,
			String targetServiceName, String targetServiceKey,
			ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		// キー入力チェック
		checkPost(feed, parentUri, serviceName);
		// バリデーションチェック -> authorが必要なので先の処理でチェックする。

		// 対象サービス指定の場合、対象サービス認証を行う。
		ServiceBlogic serviceBlogic = new ServiceBlogic();
		ReflexAuthentication tmpAuth = serviceBlogic.authenticateByCooperationService(
				targetServiceName, targetServiceKey, auth, requestInfo, connectionInfo);

		// 登録処理
		DatastoreManager datastoreManager = TaggingEnvUtil.getDatastoreManager();
		List<UpdatedInfo> updatedInfos = datastoreManager.post(feed, parentUri, ext, 
				serviceName, tmpAuth, requestInfo, connectionInfo);

		// Entry編集 (項目ACL適用)
		return editUpdatedInfo(updatedInfos, false, tmpAuth, requestInfo, connectionInfo);
	}

	/**
	 * 更新
	 * @param feed Feed
	 * @param parentUri キー自動採番の場合の親階層
	 * @param updateAllIndex インデックスをすべて更新(データパッチ用)
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 更新したFeed
	 */
	public FeedBase put(FeedBase feed, String parentUri,
			boolean updateAllIndex,
			String targetServiceName, String targetServiceKey,
			ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		// キー入力チェック
		checkPut(feed, parentUri, false, serviceName);
		// バリデーションチェック -> authorが必要なので先の処理でチェックする。

		// 対象サービス指定の場合、対象サービス認証を行う。
		ServiceBlogic serviceBlogic = new ServiceBlogic();
		ReflexAuthentication tmpAuth = serviceBlogic.authenticateByCooperationService(
				targetServiceName, targetServiceKey, auth, requestInfo, connectionInfo);

		// 更新処理
		DatastoreManager datastoreManager = TaggingEnvUtil.getDatastoreManager();
		List<UpdatedInfo> updatedInfos = datastoreManager.put(feed, parentUri,
				updateAllIndex, serviceName, tmpAuth, requestInfo, connectionInfo);

		// Entry編集 (項目ACL適用)
		return editUpdatedInfo(updatedInfos, false, tmpAuth, requestInfo, connectionInfo);
	}

	/**
	 * 一括更新.
	 * Feedのうち最大更新数ごとにput処理を呼び出す。一貫性を保証しない。
	 * 各put処理は並列に実行する。
	 * @param feed Feed
	 * @param parentUri キー自動採番の場合の親階層
	 * @param async 非同期の場合true、同期の場合false
	 * @param updateAllIndex インデックスをすべて更新(データパッチ用)
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public List<Future<List<UpdatedInfo>>> bulkPut(FeedBase feed, String parentUri, boolean async,
			boolean updateAllIndex, 
			String targetServiceName, String targetServiceKey,
			ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		// not nullチェック
		checkPut(feed, parentUri, true, serviceName);

		// 対象サービス指定の場合、対象サービス認証を行う。
		ServiceBlogic serviceBlogic = new ServiceBlogic();
		ReflexAuthentication tmpAuth = serviceBlogic.authenticateByCooperationService(
				targetServiceName, targetServiceKey, auth, requestInfo, connectionInfo);

		// 一括更新処理
		DatastoreManager datastoreManager = TaggingEnvUtil.getDatastoreManager();
		return datastoreManager.bulkPut(feed, parentUri, async, updateAllIndex,
				serviceName, tmpAuth, requestInfo, connectionInfo);
	}

	/**
	 * 一括更新.
	 * Feedのうち最大更新数ごとにput処理を呼び出す。一貫性を保証しない。
	 * 各putは直列に実行する。
	 * @param feed Feed
	 * @param parentUri キー自動採番の場合の親階層
	 * @param async 非同期の場合true、同期の場合false
	 * @param updateAllIndex インデックスをすべて更新(データパッチ用)
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public Future<List<UpdatedInfo>> bulkSerialPut(FeedBase feed, String parentUri, boolean async,
			boolean updateAllIndex, 
			String targetServiceName, String targetServiceKey,
			ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		// not nullチェック
		checkPut(feed, parentUri, true, serviceName);

		// 対象サービス指定の場合、対象サービス認証を行う。
		ServiceBlogic serviceBlogic = new ServiceBlogic();
		ReflexAuthentication tmpAuth = serviceBlogic.authenticateByCooperationService(
				targetServiceName, targetServiceKey, auth, requestInfo, connectionInfo);

		// 一括更新処理
		DatastoreManager datastoreManager = TaggingEnvUtil.getDatastoreManager();
		return datastoreManager.bulkSerialPut(feed, parentUri, async, updateAllIndex,
				serviceName, tmpAuth, requestInfo, connectionInfo);
	}

	/**
	 * 削除
	 * @param ids 削除対象IDリスト (リビジョン無しでも可)
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public FeedBase delete(List<String> ids,
			String targetServiceName, String targetServiceKey,
			ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		// キー入力チェック
		checkDelete(ids, serviceName);

		// 対象サービス指定の場合、対象サービス認証を行う。
		ServiceBlogic serviceBlogic = new ServiceBlogic();
		ReflexAuthentication tmpAuth = serviceBlogic.authenticateByCooperationService(
				targetServiceName, targetServiceKey, auth, requestInfo, connectionInfo);

		// 削除処理
		DatastoreManager datastoreManager = TaggingEnvUtil.getDatastoreManager();
		List<UpdatedInfo> updatedInfos = datastoreManager.delete(ids, serviceName, tmpAuth,
				requestInfo, connectionInfo);

		// Entry編集 (項目ACL適用、サービス名除去)
		return editUpdatedInfo(updatedInfos, true, tmpAuth, requestInfo, connectionInfo);	// 更新前情報を返す
	}

	/**
	 * フォルダ削除
	 * @param uri 削除対象フォルダ
	 * @param async 非同期の場合true、同期の場合false
	 * @param isParallel 並列削除を行う場合true
	 * @param noDeleteSelf フォルダエントリー自体を削除しない場合true
	 * @param targetServiceName 対象サービス名
	 * @param targetServiceKey 対象サービスのサービスキー
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return Future 同期の場合はnull
	 */
	public Future deleteFolder(String uri, boolean noDeleteSelf, boolean async, 
			boolean isParallel, String targetServiceName, String targetServiceKey,
			ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException{
		String serviceName = auth.getServiceName();
		// 入力チェック
		CheckUtil.checkUri(uri);

		// 対象サービス指定の場合、対象サービス認証を行う。
		ServiceBlogic serviceBlogic = new ServiceBlogic();
		ReflexAuthentication tmpAuth = serviceBlogic.authenticateByCooperationService(
				targetServiceName, targetServiceKey, auth, requestInfo, connectionInfo);

		// フォルダ削除
		DatastoreManager datastoreManager = TaggingEnvUtil.getDatastoreManager();
		return datastoreManager.deleteFolder(uri, noDeleteSelf, async, isParallel, 
				serviceName, tmpAuth, requestInfo, connectionInfo);
	}

	/**
	 * エイリアス追加
	 * @param feed Feed
	 * @param parentUri キー自動採番の場合の親階層
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public FeedBase addAlias(FeedBase feed, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		return updateAlias(feed, true, auth, requestInfo, connectionInfo);
	}

	/**
	 * エイリアス削除
	 * @param feed Feed
	 * @param parentUri キー自動採番の場合の親階層
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public FeedBase removeAlias(FeedBase feed, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		return updateAlias(feed, false, auth, requestInfo, connectionInfo);
	}

	/**
	 * エイリアス更新
	 * @param feed Feed
	 * @param isAdd エイリアス追加の場合true、エイリアス削除の場合false
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 更新したFeed
	 */
	private FeedBase updateAlias(FeedBase feed, boolean isAdd, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		// 入力チェック
		CheckUtil.checkFeed(feed, false);

		FeedBase paramFeed = TaggingEntryUtil.createFeed(serviceName);
		// Entry読み込み
		// 参照権限が無い場合もあるため、システム権限で読み込む。
		SystemContext systemContext = new SystemContext(auth, requestInfo, connectionInfo);
		for (EntryBase entry : feed.entry) {
			String uri = entry.getMyUri();

			EntryBase currentEntry = systemContext.getEntry(uri);
			if (currentEntry == null) {
				throw new NoExistingEntryException(NoExistingEntryException.MSG_PREFIX + uri);
			}

			List<String> aliases = CheckUtil.checkUpdateAlias(entry);
			List<String> currentAliases = currentEntry.getAlternate();
			List<String> addingAliases = new ArrayList<String>();

			if (isAdd) {
				// エイリアス追加
				for (String alias : aliases) {
					if (currentAliases != null) {
						addingAliases.addAll(currentAliases);
					}
					if (!addingAliases.contains(alias)) {
						addingAliases.add(alias);
					}	// elseの場合、すでにエイリアスが存在する。
				}

			} else {
				// エイリアス削除
				if (currentAliases != null) {
					for (String currentAlias : currentAliases) {
						if (aliases.contains(currentAlias)) {
							// 削除分
						} else {
							// 残すエイリアス
							addingAliases.add(currentAlias);
						}
					}
				}	// currentAliasesがnullの場合、元々エイリアスが存在しない。
			}

			// 更新情報を設定
			EntryBase paramEntry = TaggingEntryUtil.createEntry(serviceName);
			paramEntry.setMyUri(uri);
			paramEntry.id = entry.id;
			if (!addingAliases.isEmpty()) {
				for (String addingAlias : addingAliases) {
					paramEntry.addAlternate(addingAlias);
				}
			} else {
				// エイリアスが空になった場合、空のタグを指定する。
				Link blankAlternate = new Link();
				blankAlternate._$rel = Link.REL_ALTERNATE;
				paramEntry.addLink(blankAlternate);
			}
			paramFeed.addEntry(paramEntry);
		}

		return put(paramFeed, null, false, null, null, auth, requestInfo, connectionInfo);
	}

	/**
	 * ACL追加
	 * @param feed Feed
	 * @param parentUri キー自動採番の場合の親階層
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 更新したFeed
	 */
	public FeedBase addAcl(FeedBase feed, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		return updateAcl(feed, true, auth, requestInfo, connectionInfo);
	}

	/**
	 * ACL削除
	 * @param feed Feed
	 * @param parentUri キー自動採番の場合の親階層
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 更新したFeed
	 */
	public FeedBase removeAcl(FeedBase feed, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		return updateAcl(feed, false, auth, requestInfo, connectionInfo);
	}

	/**
	 * ACL更新
	 * @param feed Feed
	 * @param isAdd ACL追加の場合true、ACL削除の場合false
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 更新したFeed
	 */
	private FeedBase updateAcl(FeedBase feed, boolean isAdd, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		// 入力チェック
		CheckUtil.checkFeed(feed, false);
		// 認証なしはエラー
		if (auth == null || StringUtils.isBlank(auth.getUid())) {
			PermissionException pe = new PermissionException();
			pe.setSubMessage("Authentication is required for update acl.");
			throw pe;
		}

		FeedBase paramFeed = TaggingEntryUtil.createFeed(serviceName);
		// Entry読み込み
		// 参照権限が無い場合もあるため、システム権限で読み込む。
		SystemContext systemContext = new SystemContext(auth, requestInfo, connectionInfo);
		for (EntryBase entry : feed.entry) {
			String uri = entry.getMyUri();

			EntryBase currentEntry = systemContext.getEntry(uri);
			if (currentEntry == null) {
				new NoExistingEntryException(NoExistingEntryException.MSG_PREFIX + uri);
			}

			Set<String> aclUrns = CheckUtil.checkUpdateACL(entry);

			List<Contributor> addingContributors = new ArrayList<Contributor>();

			if (isAdd) {
				// ACL追加
				Set<String> existUrns = new HashSet<String>();
				if (currentEntry.contributor != null) {
					// すでに設定されているACL
					for (Contributor currentCont : currentEntry.contributor) {
						if (!StringUtils.isBlank(currentCont.uri) &&
								currentCont.uri.startsWith(AtomConst.URN_PREFIX_ACL)) {
							// ACL設定
							if (aclUrns.contains(currentCont.uri)) {
								// すでにACL設定済み
								existUrns.add(currentCont.uri);
							}
						}
						addingContributors.add(currentCont);
					}
				}

				// 追加分
				for (String aclUrn : aclUrns) {
					if (!existUrns.contains(aclUrn)) {
						Contributor addingCont = new Contributor();
						addingCont.uri = aclUrn;
						addingContributors.add(addingCont);
					}
				}

			} else {
				// ACL削除
				Set<String> delUrns = new HashSet<String>();
				// すでに設定されているACL
				if (currentEntry.contributor != null) {
					for (Contributor currentCont : currentEntry.contributor) {
						if (!StringUtils.isBlank(currentCont.uri) &&
								currentCont.uri.startsWith(AtomConst.URN_PREFIX_ACL)) {
							// ACL設定
							if (aclUrns.contains(currentCont.uri)) {
								delUrns.add(currentCont.uri);	// 削除分
							} else {
								addingContributors.add(currentCont);	// これまで通り設定
							}
						} else {
							// ACL以外
							addingContributors.add(currentCont);
						}
					}
				}
				// 削除指定分
				for (String aclUrn : aclUrns) {
					if (!delUrns.contains(aclUrn)) {
						// 存在しないACLを削除指定 -> 何もしない?
					}
				}
			}

			// 更新情報を設定
			EntryBase paramEntry = TaggingEntryUtil.createEntry(serviceName);
			paramEntry.setMyUri(uri);
			paramEntry.id = entry.id;
			paramEntry.contributor = addingContributors;

			paramFeed.addEntry(paramEntry);
		}

		return put(paramFeed, null, false, null, null, auth, requestInfo, connectionInfo);
	}

	/**
	 * 登録時入力チェック
	 * @param feed Feed
	 * @param uri 自動採番用親階層
	 * @param serviceName サービス名
	 */
	private void checkPost(FeedBase feed, String uri, String serviceName) {
		// 入力チェック、URI重複チェック
		CheckUtil.checkFeed(feed, false);
		CheckUtil.checkCommonUri(uri, serviceName);
		// EntryごとのURIチェック
		for (EntryBase entry : feed.entry) {
			CheckUtil.checkEntryKey(entry, uri);
			CheckUtil.checkIdAtPost(entry.id);
			CheckUtil.checkCommonUri(entry.getMyUri(), serviceName);
			List<String> aliases = entry.getAlternate();
			if (aliases != null) {
				for (String alias : aliases) {
					CheckUtil.checkCommonUri(alias, serviceName);
				}
			}
		}
	}

	/**
	 * 更新時入力チェック.
	 * @param feed Feed
	 * @param uri 自動採番用親階層
	 * @param isBulk 一括処理の場合true
	 * @param serviceName サービス名
	 */
	private void checkPut(FeedBase feed, String uri, boolean isBulk, String serviceName) {
		// 入力チェック、URI重複チェック
		CheckUtil.checkFeed(feed, true, isBulk);
		CheckUtil.checkCommonUri(uri, serviceName);
		// EntryごとのURIチェック
		for (EntryBase entry : feed.entry) {
			if (isDelete(entry.id)) {
				CheckUtil.checkEntryKey(entry);
			} else {
				CheckUtil.checkEntryKey(entry, uri);
			}
			CheckUtil.checkIdAtPut(entry.id);
			CheckUtil.checkCommonUri(entry.getMyUri(), serviceName);
			List<String> aliases = entry.getAlternate();
			if (aliases != null) {
				for (String alias : aliases) {
					CheckUtil.checkCommonUri(alias, serviceName);
				}
			}
		}
	}

	/**
	 * 削除時入力チェック.
	 * @param ids IDまたはURIのリスト
	 * @param serviceName サービス名
	 */
	private void checkDelete(List<String> ids, String serviceName) {
		// 入力チェック
		CheckUtil.checkIds(ids);

		for (String idOrUri : ids) {
			boolean isId = false;
			if (idOrUri != null) {
				int idx = idOrUri.indexOf(",");
				if (idx > -1) {
					// リビジョンあり
					isId = true;
				}
			}
			if (isId) {
				// リビジョンあり
				CheckUtil.checkIdAtDelete(idOrUri);
			} else {
				// リビジョンなし
				CheckUtil.checkUri(idOrUri, "ID or Key");
				CheckUtil.checkLastSlash(idOrUri, "ID or Key");
			}
			CheckUtil.checkCommonUri(idOrUri, serviceName);
		}
		CheckUtil.checkDuplicateUrl(ids);
	}

	/**
	 * Entry検索時入力チェック
	 * @param param 入力情報
	 */
	private void checkGetEntry(RequestParam param) {
		CheckUtil.checkRequestParam(param);
	}

	/**
	 * Feed検索時入力チェック
	 * @param param 入力情報
	 */
	private void checkGetFeed(RequestParam param) {
		CheckUtil.checkRequestParam(param);
	}

	/**
	 * Entryの編集
	 * <p>
	 * 項目ACL適用、サービス名除去 -> サービス名除去は廃止<br>
	 * 引数のentryが編集されます.
	 * </p>
	 * @param uri URI
	 * @param entry Entry
	 * @param isNometa meta情報(id, author, published, updated)を除去する場合true
	 * @param auth 認証情報
	 */
	private void editEntry(String uri, EntryBase entry, boolean isNometa,
			ReflexAuthentication auth) {
		if (entry == null) {
			return;
		}
		if (!AuthenticationUtil.isSystemuser(auth)) {
			// 項目ACL適用
			String uid = auth.getUid();
			List<String> groups = auth.getGroups();
			entry.maskprop(uid, groups);

			// URIを指定された値に置き換える。
			entry.setMyUri(uri);

			if (isNometa) {
				TaggingEntryUtil.editNometa(entry);
			}
		}
	}

	/**
	 * Feed内Entryの編集
	 * <p>
	 * 項目ACL適用、サービス名除去 -> サービス名付加・除去は廃止<br>
	 * 引数のFeed内Entryが編集されます.
	 * </p>
	 * @param parentUri 親階層
	 * @param feed Feed
	 * @param isNometa meta情報(id, author, published, updated)を除去する場合true
	 * @param auth 認証情報
	 */
	public void editFeed(String parentUri, FeedBase feed, boolean isNometa,
			ReflexAuthentication auth) {
		if (feed == null || feed.entry == null || feed.entry.isEmpty()) {
			return;
		}
		if (!AuthenticationUtil.isSystemuser(auth)) {
			// 項目ACL適用
			String uid = auth.getUid();
			List<String> groups = auth.getGroups();
			feed.maskprop(uid, groups);

			// URIを指定された値に置き換える。
			for (EntryBase entry : feed.entry) {
				String editUri = TaggingEntryUtil.getSpecifiedParentUri(entry, parentUri);
				entry.setMyUri(editUri);
				if (isNometa) {
					TaggingEntryUtil.editNometa(entry);
				}
			}
		}
	}

	/**
	 * 更新戻り値の編集.
	 * @param updatedInfos 更新情報
	 * @param isPrevEntry 更新前Entryを戻す場合true (Deleteの場合)
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 更新Feed
	 */
	private FeedBase editUpdatedInfo(List<UpdatedInfo> updatedInfos,
			boolean isPrevEntry, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo) {
		String serviceName = auth.getServiceName();
		FeedBase retFeed = null;
		if (updatedInfos != null && !updatedInfos.isEmpty()) {
			retFeed = TaggingEntryUtil.createFeed(serviceName);
			List<EntryBase> retEntries = new ArrayList<EntryBase>();
			retFeed.entry = retEntries;
			for (UpdatedInfo updatedInfo : updatedInfos) {
				EntryBase retEntry = null;
				if (isPrevEntry) {
					retEntry = updatedInfo.getPrevEntry();
				} else {
					retEntry = updatedInfo.getUpdEntry();
				}
				retEntry = editReturnEntry(retEntry, auth, requestInfo, connectionInfo);
				retEntries.add(retEntry);
			}
		}
		return retFeed;

	}

	/**
	 * Feed内Entryの編集
	 * <p>
	 * 項目ACL適用、サービス名除去<br>
	 * 引数のFeed内Entryが編集されます.
	 * </p>
	 * @param entry Entry
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @reutrn 編集したEntry
	 */
	private EntryBase editReturnEntry(EntryBase entry, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo) {
		if (entry == null) {
			return null;
		}
		String serviceName = auth.getServiceName();
		FeedTemplateMapper mapper = TaggingEnvUtil.getResourceMapper(serviceName);
		EntryBase retEntry = TaggingEntryUtil.copyEntry(entry, mapper);
		if (!AuthenticationUtil.isSystemuser(auth)) {
			String uid = auth.getUid();
			List<String> groups = auth.getGroups();
			// 項目ACL適用
			retEntry.maskprop(uid, groups);
		}
		return retEntry;
	}

	/**
	 * リクエストパラメータから指定件数を取得.
	 * @param limitStr lパラメータの値
	 * @param serviceName サービス名
	 * @return 指定件数
	 */
	public int getLimit(String limitStr, String serviceName)
			throws InvalidServiceSettingException {
		if (RequestParam.WILDCARD.equals(limitStr)) {
			return TaggingEnvUtil.getEntryNumberLimit();
		}
		int defLimit = TaggingEnvUtil.getEntryNumberDefault(serviceName);
		return StringUtils.intValue(limitStr, defLimit);
	}

	/**
	 * リクエストパラメータからカーソルを取得.
	 * @param param リクエストパラメータ
	 * @return カーソル
	 */
	private String getCursorStr(RequestParam param) {
		return param.getOption(RequestParam.PARAM_NEXT);
	}

	/**
	 * 削除処理かどうか.
	 * @param id ID
	 * @return 削除処理の場合true
	 */
	private boolean isDelete(String id) {
		if (id != null && id.indexOf(RequestParam.PARAM_DELETE) > -1) {
			return true;
		}
		return false;
	}

	/**
	 * インデックス更新
	 * @param indexFeed インデックス更新情報
	 * @param isDelete 削除の場合true
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void putIndex(FeedBase indexFeed, boolean isDelete,
			ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		// ACLチェック サービス管理者でなければエラー
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAuthedGroup(auth, Constants.URI_GROUP_ADMIN);

		// 入力チェック
		checkPutIndex(indexFeed, serviceName);

		// インデックス更新処理
		DatastoreManager datastoreManager = TaggingEnvUtil.getDatastoreManager();
		datastoreManager.putIndex(indexFeed, isDelete, auth, requestInfo, connectionInfo);
	}

	/**
	 * インデックス更新リクエスト内容チェック.
	 * @param indexFeed インデックス更新情報
	 * @param serviceName サービス名
	 */
	private void checkPutIndex(FeedBase indexFeed, String serviceName) {
		// 入力チェック
		CheckUtil.checkNotNull(indexFeed, "Parent key and item name");
		CheckUtil.checkNotNull(indexFeed.entry, "Parent key and item name");

		// インデックス情報 キー:項目、値:親キーのパターン
		Map<String, Pattern> templateIndexMap =
				TaggingEnvUtil.getTemplateIndexMap(serviceName);
		Map<String, Pattern> templateFullTextIndexMap =
				TaggingEnvUtil.getTemplateFullTextIndexMap(serviceName);
		Map<String, Pattern> templateDistkeyMap =
				TaggingEnvUtil.getTemplateDistkeyMap(serviceName);

		// EntryごとのURIチェック
		for (EntryBase entry : indexFeed.entry) {
			// 親キー
			String parentUri = entry.getMyUri();
			CheckUtil.checkUri(parentUri, "Parent key");
			CheckUtil.checkCommonUri(parentUri, serviceName);
			// 項目
			String itemName = entry.title;
			if (StringUtils.isBlank(itemName)) {
				// すべての項目が対象
			} else {
				// インデックス対象かどうか
				boolean hasIndex = TaggingIndexUtil.useIndex(parentUri, itemName, templateIndexMap);
				boolean hasFulltextIndex = TaggingIndexUtil.useIndex(parentUri, itemName,
						templateFullTextIndexMap);
				if (!hasIndex && !hasFulltextIndex) {
					StringBuilder sb = new StringBuilder();
					sb.append("The item and key are not defined. key=");
					sb.append(parentUri);
					sb.append(", item=");
					sb.append(itemName);
					throw new IllegalParameterException(sb.toString());
				}
			}
			// DISTKEYが指定されている場合
			if (entry.category != null && !entry.category.isEmpty()) {
				for (Category category : entry.category) {
					if (!StringUtils.isBlank(category._$scheme)) {
						String distkeyItem = category._$scheme;
						if (!TaggingIndexUtil.useIndex(parentUri, distkeyItem, templateDistkeyMap)) {
							StringBuilder sb = new StringBuilder();
							sb.append("The distkey and key are not defined. key=");
							sb.append(parentUri);
							sb.append(", distkey=");
							sb.append(distkeyItem);
							throw new IllegalParameterException(sb.toString());
						}
					}
				}
			}
		}
	}

	/**
	 * インデックス情報を編集.
	 * 項目名が指定されていない場合、対象の項目をすべて指定する。
	 * @param indexFeed インデックス情報
	 * @param serviceName サービス名
	 * @return 編集したインデックス情報
	 */
	/*
	private FeedBase editIndexInfo(FeedBase indexFeed, String serviceName) {
		// インデックス情報 キー:項目、値:親キーのパターン
		Map<String, Pattern> templateIndexMap =
				TaggingEnvUtil.getTemplateIndexMap(serviceName);
		Map<String, Pattern> templateFullTextIndexMap =
				TaggingEnvUtil.getTemplateFullTextIndexMap(serviceName);
		Map<String, Pattern> templateDistkeyMap =
				TaggingEnvUtil.getTemplateDistkeyMap(serviceName);

		List<EntryBase> retIndexInfos = new ArrayList<>();
		for (EntryBase indexInfo : indexFeed.entry) {
			String parentUri = indexInfo.getMyUri();
			List<String> distkeyItems = TaggingIndexUtil.getIndexItemNames(parentUri,
					templateDistkeyMap);

			String item = indexInfo.title;
			if (StringUtils.isBlank(item)) {
				// インデックス項目が指定されていない場合は、親キーが対象のインデックスすべて。
				List<String> indexItems = TaggingIndexUtil.getIndexItemNames(parentUri,
						templateIndexMap, templateFullTextIndexMap);
				if (indexItems != null) {
					for (String tmpItem : indexItems) {
						EntryBase tmpInfo = TaggingEntryUtil.createEntry(serviceName);
						tmpInfo.setMyUri(parentUri);
						tmpInfo.title = tmpItem;
						retIndexInfos.add(tmpInfo);
					}
				}
			} else {
				// インデックス項目が指定されている場合はそのまま返す。
				retIndexInfos.add(indexInfo);
			}
		}
		FeedBase retIndexInfo = TaggingEntryUtil.createFeed(serviceName);
		retIndexInfo.entry = retIndexInfos;
		return retIndexInfo;
	}
	*/

	/**
	 * Feed検索でのインデックス使用チェック
	 * @param param 検索条件
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return メッセージ (Feedのtitleに設定)
	 */
	public FeedBase checkIndex(RequestParam param, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		// ACLチェック サービス管理者でなければエラー
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAuthedGroup(auth, Constants.URI_GROUP_ADMIN);
		// キー入力チェック
		checkGetFeed(param);
		String uri = param.getUri();

		// Metalist
		List<Meta> metalist = TaggingEnvUtil.getResourceMapper(serviceName).getMetalist();
		// インデックス情報 キー:項目、値:親キーのパターン
		Map<String, Pattern> templateIndexMap =
				TaggingEnvUtil.getTemplateIndexMap(serviceName);
		Map<String, Pattern> templateFullTextIndexMap =
				TaggingEnvUtil.getTemplateFullTextIndexMap(serviceName);
		Map<String, Pattern> templateDistkeyMap =
				TaggingEnvUtil.getTemplateDistkeyMap(serviceName);

		List<Link> results = new ArrayList<>();

		// OR検索ごと
		List<List<Condition>> conditionList = param.getConditionsList();
		boolean isUrlForwardMatch = param.isUrlForwardMatch();
		if (conditionList == null || conditionList.isEmpty()) {
			Link link = new Link();
			link._$href = "";
			link._$title = DatastoreConst.MSG_CHECKINDEX_NO_INDEX;
			results.add(link);
		} else {
			for (List<Condition> conditions : conditionList) {
				Link link = new Link();
				link._$href = getConditionStr(conditions);

				EditedCondition editedCondition = IndexUtil.editCondition(uri,
						isUrlForwardMatch, conditions, metalist,
						templateIndexMap, templateFullTextIndexMap, templateDistkeyMap);
				StringBuilder sb = new StringBuilder();
				if (editedCondition.getFtCondition() != null) {
					sb.append(DatastoreConst.MSG_CHECKINDEX_FULLTEXTINDEX);
					sb.append(editedCondition.getFtCondition().getProp());
					if (editedCondition.getDistkeyItem() != null) {
						sb.append(", ");
						sb.append(DatastoreConst.MSG_CHECKINDEX_DISTKEY);
						sb.append(editedCondition.getDistkeyItem());
					}
				} else if (editedCondition.getIndexCondition() != null) {
					sb.append(DatastoreConst.MSG_CHECKINDEX_INDEX);
					sb.append(editedCondition.getIndexCondition().getProp());
					if (editedCondition.getDistkeyItem() != null) {
						sb.append(", ");
						sb.append(DatastoreConst.MSG_CHECKINDEX_DISTKEY);
						sb.append(editedCondition.getDistkeyItem());
					}
				} else {
					if (editedCondition.getDistkeyItem() != null) {
						sb.append(DatastoreConst.MSG_CHECKINDEX_DISTKEY);
						sb.append(editedCondition.getDistkeyItem());
					} else {
						sb.append(DatastoreConst.MSG_CHECKINDEX_NO_INDEX);
					}
				}
				link._$title = sb.toString();
				results.add(link);
			}
		}

		FeedBase retFeed = TaggingEntryUtil.createFeed(serviceName);
		retFeed.title = "Key=" + uri;
		retFeed.link = results;
		return retFeed;
	}

	/**
	 * 検索条件の文字列表現を取得.
	 * @param conditions 検索条件リスト
	 * @return 検索条件の文字列表現
	 */
	private String getConditionStr(List<Condition> conditions) {
		StringBuilder sb = new StringBuilder();
		boolean isFirst = true;
		for (Condition condition : conditions) {
			if (isFirst) {
				isFirst = false;
			} else {
				sb.append("&");
			}
			sb.append(condition.toString());
		}
		return sb.toString();
	}

	/**
	 * サーバ追加.
	 * @param targetServiceName 対象サービス名
	 * @param feed サーバ追加情報
	 * @param reflexContext ReflexContext
	 */
	public void addServer(String targetServiceName, FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// サービス管理者またはシステム管理サービス管理者でなければエラー
		// 対象サービスがシステム管理サービスはエラー
		checkServiceAdmin(targetServiceName, reflexContext);

		DatastoreManager datastoreManager = TaggingEnvUtil.getDatastoreManager();
		datastoreManager.addServer(targetServiceName, feed, reflexContext);
	}

	/**
	 * サーバ削除.
	 * @param targetServiceName 対象サービス名
	 * @param feed サーバ削除情報
	 * @param reflexContext ReflexContext
	 */
	public void removeServer(String targetServiceName, FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException {
		// サービス管理者またはシステム管理サービス管理者でなければエラー
		// 対象サービスがシステム管理サービスはエラー
		checkServiceAdmin(targetServiceName, reflexContext);

		DatastoreManager datastoreManager = TaggingEnvUtil.getDatastoreManager();
		datastoreManager.removeServer(targetServiceName, feed, reflexContext);
	}

	/**
	 * 認証チェック : システム管理サービス管理者か、操作対象のサービス管理者か
	 * @param targetServiceName 対象サービス名
	 * @param reflexContext ログインサービスのReflexContext
	 */
	private void checkServiceAdmin(String targetServiceName, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String tmpServiceName = reflexContext.getServiceName();
		ReflexAuthentication auth = reflexContext.getAuth();

		// 認証チェック
		// 実行サービスがシステム管理サービスでなければエラー
		if (!TaggingEnvUtil.getSystemService().equals(tmpServiceName)) {
			throw new IllegalParameterException("Forbidden request to this service.");
		}
		// 認証なしはエラー
		if (auth == null || StringUtils.isBlank(auth.getUid())) {
			PermissionException pe = new PermissionException();
			pe.setSubMessage("Authentication is required for productionservice.");
			throw pe;
		}
		// 対象サービス名入力チェック
		CheckUtil.checkNotNull(targetServiceName, "Service name");

		// 対象サービスがシステム管理サービスは不可
		String systemService = TaggingEnvUtil.getSystemService();
		if (systemService.equals(targetServiceName)) {
			throw new IllegalParameterException("It cannot be processed by admin service.");
		}

		// サービス名は小文字のみ
		ServiceManager serviceManager = TaggingEnvUtil.getServiceManager();
		targetServiceName = serviceManager.editServiceName(targetServiceName);

		// ステータス更新できるのはシステム管理サービス管理者か、指定されたサービスの管理者のみ
		ServiceBlogic serviceBlogic = new ServiceBlogic();
		serviceBlogic.checkOperateServiceAuth(targetServiceName, true,
				reflexContext.getRequest(), auth,
				reflexContext.getRequestInfo(), reflexContext.getConnectionInfo());
	}

	/**
	 * エントリーのidに"?_delete"を付加する.
	 * @param entry エントリー
	 */
	public void editEntryToDelete(EntryBase entry) {
		StringBuilder sb = new StringBuilder();
		sb.append(StringUtils.null2blank(entry.id));
		sb.append("?");
		sb.append(RequestParam.PARAM_DELETE);
		entry.id = sb.toString();
	}

}
