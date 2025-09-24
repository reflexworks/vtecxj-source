package jp.reflexworks.taggingservice.bdbclient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.api.AtomConst;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.entry.Link;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.blogic.AclBlogic;
import jp.reflexworks.taggingservice.blogic.AclConst;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.EntryDuplicatedException;
import jp.reflexworks.taggingservice.exception.HierarchyFormatException;
import jp.reflexworks.taggingservice.exception.NoExistingEntryException;
import jp.reflexworks.taggingservice.exception.OptimisticLockingException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.index.EntryManager;
import jp.reflexworks.taggingservice.model.UpdatedInfo;
import jp.reflexworks.taggingservice.model.Value;
import jp.reflexworks.taggingservice.plugin.CacheManager;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBResponseType;
import jp.reflexworks.taggingservice.requester.BDBRequester;
import jp.reflexworks.taggingservice.requester.BDBRequesterUtil;
import jp.reflexworks.taggingservice.service.BDBClientInitMainThreadManager;
import jp.reflexworks.taggingservice.sys.SystemAuthentication;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.taskqueue.TaskQueueUtil;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.Constants.OperationType;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.MetadataUtil;
import jp.reflexworks.taggingservice.util.RetryUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * データストアの更新系処理を行うクラス.
 */
public class BDBClientUpdateManager {

	/** ACLチェック時の登録 */
	private static final String ACL_TYPE_CREATE = AtomConst.ACL_TYPE_CREATE;
	/** ACLチェック時の削除 */
	private static final String ACL_TYPE_DELETE = AtomConst.ACL_TYPE_DELETE;

	/** post実行メソッド */
	private static final String METHOD_POST = Constants.POST;
	/** put実行メソッド */
	private static final String METHOD_PUT = Constants.PUT;
	/** delete実行メソッド */
	private static final String METHOD_DELETE = Constants.DELETE;

	/** 排他制御に使用するキー接頭辞 */
	private static final String EXCLUSIVE_KEY_PREFIX = "EXCL:";
	/** 排他制御に使用するダミー文字列 */
	private static final String EXCLUSIVE_TEXT = "excl";
	
	/** ログ出力しないURL接頭辞 */
	private static final String PARENTURI_LOG = Constants.URI_LOG + "/";
	private static final String PARENTURI_LOGIN_HISTORY = Constants.URI_LOGIN_HISTORY + "/";

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 更新処理.
	 * <p>
	 * <ul>
	 *   <li>トランザクション開始。指定されたFeed内の全てのEntryは同じトランザクションで更新される。</li>
	 *   <li>id、author、published、updatedの編集</li>
	 *   <li>更新、コミット</li>
	 * </ul>
	 * </p>
	 * @param entries Entryリスト。link selfのhrefはID URIが設定されている。
	 * @param flgs 処理区分リスト。添字はEntryリストに紐づく。
	 * @param isPost POSTの場合true
	 * @param originalServiceName 実行元サービス名
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 登録したFeed. 引数のfeedオブジェクトに格納されるEntryと同一インスタンスです。
	 */
	public List<UpdatedInfo> update(List<EntryBase> entries, List<OperationType> flgs,
			boolean isPost, String originalServiceName, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		String currentTime = MetadataUtil.getCurrentTime();
		SystemContext systemContext = new SystemContext(auth, requestInfo, connectionInfo);

		int size = entries.size();
		BDBRequester<FeedBase> requesterFeed = new BDBRequester<>(BDBResponseType.FEED);

		List<String> exclusionIdUris = new ArrayList<>();
		List<UpdatedInfo> updatedInfos = new ArrayList<>();
		boolean isDeletedReadEntryMap = false;
		try {
			List<Link> mnfGetLinks = new ArrayList<>();
			// まずCacheによる排他を行う
			for (EntryBase entry : entries) {
				String uri = entry.getMyUri();
				boolean isExclusion = exclusive(uri, systemContext);
				if (!isExclusion) {
					if (logger.isInfoEnabled()) {
						StringBuilder sb = new StringBuilder();
						sb.append(LogUtil.getRequestInfoStr(requestInfo));
						sb.append("[update] OptimisticLockingException (exclusive) uri = ");
						sb.append(uri);
						logger.info(sb.toString());
					}
					throw new OptimisticLockingException(
							OptimisticLockingException.MSG_PREFIX + uri);
				}
				exclusionIdUris.add(uri);

				// ManifestサーバへのID取得リクエスト生成
				Link mnfLink = createManifestLink(uri, null);
				mnfGetLinks.add(mnfLink);
			}

			// Manifestサーバに、EntryのID取得リクエスト。
			// POST /b?e
			// feedのlink <link rel="self" href={キー} />
			FeedBase mnfReqGetFeed = TaggingEntryUtil.createAtomFeed();
			mnfReqGetFeed.link = mnfGetLinks;
			String mnfGetRequestUri = BDBClientUtil.getGetIdByManifestUri();
			String mnfGetMethod = METHOD_POST;	// リクエストデータを指定するのでPOST

			BDBResponseInfo<FeedBase> respInfoMnf = requesterFeed.requestToManifest(
					mnfGetRequestUri, mnfGetMethod, mnfReqGetFeed, serviceName,
					requestInfo, connectionInfo);
			FeedBase mnfRespFeed = respInfoMnf.data;	// link の title に{ID}がセットされている。
			List<Link> mnfRespLinks = mnfRespFeed.link;

			// 楽観的排他チェック
			for (int i = 0; i < size; i++) {
				EntryBase entry = entries.get(i);
				OperationType flg = flgs.get(i);
				Link currentInfoLink = mnfRespLinks.get(i);
				String currentId = currentInfoLink._$title;
				String uri = entry.getMyUri();

				if (flg == OperationType.INSERT) {
					// 登録でEntry重複はエラー
					if (!StringUtils.isBlank(currentId)) {
						throw new EntryDuplicatedException(
								EntryDuplicatedException.MESSAGE + " " + uri);
					}
				} else {
					// 更新・削除でEntry存在なしはエラー
					if (StringUtils.isBlank(currentId)) {
						throw new NoExistingEntryException(
								NoExistingEntryException.MSG_PREFIX + uri);
					}
					// 楽観的排他チェック
					String[] iduriAndRev = BDBClientUtil.getUriAndRevision(entry.id);
					if (iduriAndRev[1] != null && !entry.id.equals(currentId)) {
						if (logger.isInfoEnabled()) {
							StringBuilder sb = new StringBuilder();
							sb.append(LogUtil.getRequestInfoStr(requestInfo));
							sb.append("[update] OptimisticLockingException updatedEntry.id = ");
							sb.append(entry.id);
							sb.append(" currentEntry.id = ");
							sb.append(currentId);
							logger.info(sb.toString());
						}
						throw new OptimisticLockingException(
								OptimisticLockingException.MSG_PREFIX + uri);
					}
				}
			}

			// 現在のEntryを取得する
			List<String> getEntryIds = new ArrayList<>();
			List<Integer> getEntryCnt = new ArrayList<>();
			int cnt = 0;
			for (int i = 0; i < size; i++) {
				Link currentInfoLink = mnfRespLinks.get(i);
				String currentId = currentInfoLink._$title;
				OperationType flg = flgs.get(i);
				if (OperationType.UPDATE.equals(flg) || OperationType.DELETE.equals(flg)) {
					getEntryIds.add(currentId);
					getEntryCnt.add(cnt);
					cnt++;
				} else {
					// 登録時はEntry取得しない
					getEntryCnt.add(null);
				}
			}
			EntryManager entryManager = new EntryManager();
			List<EntryBase> tmpCurrentEntries = entryManager.getEntiesByIds(getEntryIds,
					auth, requestInfo, connectionInfo);

			Map<String, Value<EntryBase>> tmpReadEntryMap = BDBClientUtil.getTmpEntryMap(
					serviceName, connectionInfo);

			List<EntryBase> currentEntries = new ArrayList<>();
			for (int i = 0; i < size; i++) {
				Integer getCnt = getEntryCnt.get(i);
				EntryBase currentEntry = null;
				if (getCnt != null) {
					currentEntry = tmpCurrentEntries.get(getCnt);
				}
				currentEntries.add(currentEntry);

				// 一時Entryキャッシュに更新情報を追加する。(階層チェックに使用)
				EntryBase entry = entries.get(i);
				OperationType flg = flgs.get(i);
				if (flg == OperationType.INSERT || flg == OperationType.UPDATE) {
					BDBClientUtil.setEntryMap(entry, entry.getMyUri(), serviceName,
							tmpReadEntryMap, connectionInfo);
				} else {
					// Deleteの場合、nullを追加する。
					Link mnfLink = mnfRespLinks.get(i);
					String uri = entry.getMyUri();
					String idUri = TaggingEntryUtil.getUriById(mnfLink._$title);
					if (uri.equals(idUri)) {
						// 指定されたURIがID URIの場合、Entryを削除
						String prevIdUri = TaggingEntryUtil.getUriById(currentEntry.id);
						tmpReadEntryMap.put(prevIdUri, new Value<EntryBase>(null));
						List<String> prevAliases = currentEntry.getAlternate();
						if (prevAliases != null) {
							for (String prevAlias : prevAliases) {
								tmpReadEntryMap.put(prevAlias, new Value<EntryBase>(null));
							}
						}
					} else {
						// ID URIでない場合はエイリアス除去
						tmpReadEntryMap.put(uri, new Value<EntryBase>(null));
					}
				}
			}

			// Entry更新処理はスレッド化して並行処理する。
			// Entryチェック・編集処理はスレッド化して並行処理する。 
			List<Future<UpdatedInfo>> futures = new ArrayList<>();
			int waitMillis = TaggingEnvUtil.getSystemPropInt(
					BDBClientConst.BULKPUT_SYNC_WAITMILLIS,
					BDBClientConst.BULKPUT_SYNC_WAITMILLIS_DEFAULT);

			// 入力チェック、編集処理
			int len = entries.size();
			for (int i = 0; i < len; i++) {
				EntryBase entry = entries.get(i);
				OperationType flg = flgs.get(i);
				EntryBase currentEntry = currentEntries.get(i);

				boolean isDisabledErrorLogEntry = false;
				if (Constants.URI_LOG.equals(TaggingEntryUtil.removeLastSlash(
						TaggingEntryUtil.getParentUri(entry.getMyUri())))) {
					// ログエントリー登録でエラーになった場合、エラーログ出力しない。
					isDisabledErrorLogEntry = true;
				}
				BDBClientCheckAndEditEntryCallable callable = new BDBClientCheckAndEditEntryCallable(
						entry, flg, currentEntry, currentTime, entries);
				Future<UpdatedInfo> future = (Future<UpdatedInfo>)TaskQueueUtil.addTask(
						callable, isDisabledErrorLogEntry, 0, auth, requestInfo,
						connectionInfo);
				futures.add(future);
			}

			// Entryチェック完了を待つ
			for (Future<UpdatedInfo> future : futures) {
				while (!future.isDone()) {
					RetryUtil.sleep(waitMillis);
				}
				try {
					UpdatedInfo updatedInfo = future.get();
					updatedInfos.add(updatedInfo);

				} catch (ExecutionException e) {
					StringBuilder sb = new StringBuilder();
					sb.append(LogUtil.getRequestInfoStr(requestInfo));
					sb.append("[update] ExecutionException: ");
					sb.append(e.getMessage());
					logger.warn(sb.toString());
					Throwable cause = e.getCause();
					if (cause instanceof IOException) {
						throw (IOException)cause;
					} else if (cause instanceof TaggingException) {
						throw (TaggingException)cause;
					} else if (cause instanceof RuntimeException) {
						throw (RuntimeException)cause;
					} else if (cause instanceof Error) {
						throw (Error)cause;
					} else {
						throw new IOException(cause);
					}
				} catch (InterruptedException e) {
					StringBuilder sb = new StringBuilder();
					sb.append(LogUtil.getRequestInfoStr(requestInfo));
					sb.append("[update] InterruptedException: ");
					sb.append(e.getMessage());
					logger.warn(sb.toString());

					throw new IOException(e);
				}
			}

			// Entryサーバに登録更新リクエスト。
			List<EntryBase> updateEntries = new ArrayList<>();
			for (UpdatedInfo updatedInfo : updatedInfos) {
				if (updatedInfo.getFlg() == OperationType.INSERT ||
						updatedInfo.getFlg() == OperationType.UPDATE) {
					updateEntries.add(updatedInfo.getUpdEntry());
				}
			}
			entryManager.putEntries(updateEntries, auth, requestInfo, connectionInfo);

			// Manifestサーバに更新リクエスト。
			FeedBase mnfReqPutFeed = TaggingEntryUtil.createAtomFeed();
			List<Link> mnfPutLinks = new ArrayList<>();
			mnfReqPutFeed.link = mnfPutLinks;
			for (int i = 0; i < len; i++) {
				OperationType flg = flgs.get(i);
				boolean isDelete = (flg == OperationType.DELETE);
				UpdatedInfo updatedInfo = updatedInfos.get(i);
				EntryBase entry = null;
				if (isDelete) {
					entry = updatedInfo.getPrevEntry();
				} else {
					entry = updatedInfo.getUpdEntry();
				}

				// ID URI
				String idUri = entry.getMyUri();
				Link mnfLink = createManifestLink(idUri, entry.id, isDelete);
				mnfPutLinks.add(mnfLink);
				if (!isDelete) {
					// Alias
					List<String> aliases = entry.getAlternate();
					if (aliases != null) {
						for (String alias : aliases) {
							mnfLink = createManifestLink(alias, entry.id, isDelete);
							mnfPutLinks.add(mnfLink);
						}
					}
				}
			}
			String mnfPutRequestUri = BDBClientUtil.getPutManifestUri();
			String mnfPutMethod = METHOD_PUT;
			requesterFeed.requestToManifest(
					mnfPutRequestUri, mnfPutMethod, mnfReqPutFeed, serviceName,
					requestInfo, connectionInfo);

			// 更新時はReadEntryMapにもEntryを残さない。(大量データ登録時のメモリリーク防止)
			deleteReadEntryMap(updatedInfos, serviceName, requestInfo, connectionInfo);
			isDeletedReadEntryMap = true;
			
			// 登録完了後の同期処理 (サーバサイドJSに対応)
			AfterCommitManager afterCommitManager = new AfterCommitManager();
			afterCommitManager.sync(updatedInfos, originalServiceName, auth, systemContext);

			// 登録完了後の非同期処理
			// 認証情報はスーパーユーザ
			AfterCommitCallable callable = new AfterCommitCallable(updatedInfos,
					originalServiceName);
			ConnectionInfo sharingConnectionInfo = BDBClientUtil.copySharingConnectionInfo(
					requestInfo, connectionInfo);
			callable.addTask(systemContext.getAuth(), requestInfo, sharingConnectionInfo);

			return updatedInfos;

		} finally {
			// 排他の解除
			for (String exclusionUri : exclusionIdUris) {
				releaseExclusion(exclusionUri, systemContext);
			}

			// 一時メモリのクリア
			BDBClientUtil.clearTmpEntryMap(serviceName, connectionInfo);
			// 更新時はReadEntryMapにもEntryを残さない。(大量データ登録時のメモリリーク防止)
			if (!isDeletedReadEntryMap) {
				deleteReadEntryMap(updatedInfos, serviceName, requestInfo, connectionInfo);
			}
		}
	}

	/**
	 * Entry更新のためのチェック、Entry編集.
	 * 検索チェックがあるため、各Entryごとにスレッド実行する。
	 * @param entry 更新Entry
	 * @param flg 更新区分
	 * @param currentEntry 現在のEntry
	 * @param currentTime 現在時刻
	 * @param entries 同一トランザクションで更新予定のEntryリスト
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 更新対象となるEntry
	 */
	UpdatedInfo checkAndEditEntry(EntryBase entry, OperationType flg,
			EntryBase currentEntry, String currentTime, List<EntryBase> entries,
			ReflexAuthentication auth, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		// link selfのhrefはID URIが設定されている。
		String uri = entry.getMyUri();
		if (logger.isInfoEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[doUpdate] uri=");
			sb.append(uri);
			sb.append(" entry.id=");
			sb.append(entry.id);
			sb.append(" flg=");
			sb.append(flg);
			sb.append(" uid=");
			sb.append(auth.getUid());
			
			// /_log と /_login_history 配下の登録・削除はログ出力しない
			boolean writeLog = true;
			if (uri.startsWith(PARENTURI_LOG) || uri.startsWith(PARENTURI_LOGIN_HISTORY)) {
				if (!BDBClientUtil.isEnableAccessLog() &&
						flg != OperationType.UPDATE) {
					writeLog = false;
				}
			}
			if (writeLog) {
				logger.info(sb.toString());
			}
		}
		String currentId = null;
		if (currentEntry != null) {
			currentId = currentEntry.id;
		}

		BDBClientRetrieveManager retrieveManager = new BDBClientRetrieveManager();
		SystemAuthentication systemAuth = new SystemAuthentication(auth);
		FeedTemplateMapper mapper = TaggingEnvUtil.getResourceMapper(serviceName);

		if (flg == OperationType.INSERT || flg == OperationType.UPDATE) {
			// templateの場合、前バージョンとの整合性チェックを行う
			CheckUtil.checkTemplate(entry, currentEntry, serviceName, mapper);

			// link selfとエイリアスのキーが重複していないかチェック
			CheckUtil.checkDuplicatedAlias(entry);

			// エイリアスのEntry存在チェック
			checkAliasEntry(entry, currentEntry, flg, entries, retrieveManager, systemAuth,
					requestInfo, connectionInfo);

			// エイリアス数が上限を超える場合はエラー -> blogicのcheckPost、checkPutでチェック済み

		}

		if (flg == OperationType.INSERT) {
			// author、published、updateのセット (entryを直接編集)
			// リビジョン1でidをセット
			TaggingEntryUtil.editInsertEntry(entry, currentTime, auth);

			// バリデーションチェック(authorセット後に行う必要があるためここで行う。)
			CheckUtil.validate(entry, currentEntry, auth, requestInfo, connectionInfo);

		} else if (flg == OperationType.UPDATE) {
			// author、published、updateのセット (entryを直接編集)
			TaggingEntryUtil.editUpdateEntry(currentEntry, entry,
					currentTime, auth, false);

			// バリデーションチェック(authorセット後に行う必要があるためここで行う。)
			CheckUtil.validate(entry, currentEntry, auth, requestInfo, connectionInfo);

			// マージ
			entry = TaggingEntryUtil.mergeUpdateEntry(currentEntry, entry, mapper);

			// リビジョンをセット
			int newRev = TaggingEntryUtil.getRevisionById(currentId) + 1;
			entry.id = TaggingEntryUtil.createId(TaggingEntryUtil.getUriById(entry.id),
					newRev);

		} else {	// DELETE
			entry.id = currentId;
		}

		// 階層チェック
		checkLayer(entry, currentEntry, flg, retrieveManager, systemAuth,
				auth, requestInfo, connectionInfo);

		// 更新情報を返却する
		return new UpdatedInfo(flg, entry, currentEntry);
	}

	/**
	 * 更新時の階層チェック
	 * @param putEntry 登録更新Entry
	 * @param currentEntry 現在Datastoreに登録されているEntry
	 * @param flg INSERT、UPDATE、DELETEのいずれか
	 * @param retrieveManager 検索処理クラス
	 * @param systemAuth システム認証情報
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	private void checkLayer(EntryBase putEntry, EntryBase currentEntry,
			OperationType flg, BDBClientRetrieveManager retrieveManager,
			SystemAuthentication systemAuth, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// エイリアス追加が可能かACLチェック
		AclBlogic aclBlogic = new AclBlogic();
		if (flg == OperationType.INSERT) {
			// INSERTの場合、上位階層の存在チェック(存在しないとエラー)
			String uri = putEntry.getMyUri();
			checkUpperLayer(uri, retrieveManager, systemAuth, requestInfo, connectionInfo);
			List<String> aliases = putEntry.getAlternate();
			if (aliases != null) {
				for (String alias : aliases) {
					// 上位階層存在チェック(存在しないとエラー)
					checkUpperLayer(alias, retrieveManager, systemAuth, requestInfo, connectionInfo);
					// ACLチェック
					aclBlogic.checkAcl(alias, ACL_TYPE_CREATE, auth,
							requestInfo, connectionInfo);
				}
			}

		} else if (flg == OperationType.DELETE) {
			// DELETEの場合、下位階層の存在チェック(存在するとエラー)
			String uri = currentEntry.getMyUri();
			checkLowerLayer(uri, retrieveManager, systemAuth, requestInfo, connectionInfo);
			List<String> aliases = currentEntry.getAlternate();
			if (aliases != null) {
				for (String alias : aliases) {
					// 下位階層存在チェック(存在するとエラー)
					checkLowerLayer(alias, retrieveManager, systemAuth, requestInfo,
							connectionInfo);
					// ACLチェック →削除(Entry本体の削除)の場合はエイリアスに対してACLチェックを行わない。
					//aclBlogic.checkAcl(alias, ACL_TYPE_DELETE, auth,
					//		requestInfo, connectionInfo);
				}
			}

		} else {	// UPDATE
			// エイリアスの追加・削除を調査する
			List<String> putAliases = putEntry.getAlternate();
			List<String> currentAliases = currentEntry.getAlternate();
			if (putAliases != null) {
				for (String putAlias : putAliases) {
					boolean exist = false;
					if (currentAliases != null && currentAliases.contains(putAlias)) {
						exist = true;
					}
					if (!exist) {
						// エイリアス新規追加
						// 上位階層チェック(存在しないとエラー)
						checkUpperLayer(putAlias, retrieveManager, systemAuth,
								requestInfo, connectionInfo);
						// ACLチェック
						aclBlogic.checkAcl(putAlias, ACL_TYPE_CREATE, auth,
								requestInfo, connectionInfo);
					}
				}
			}
			if (currentAliases != null) {
				for (String currentAlias : currentAliases) {
					boolean exist = false;
					if (putAliases != null && putAliases.contains(currentAlias)) {
						exist = true;
					}
					if (!exist) {
						// エイリアス削除
						// 下位階層存在チェック(存在するとエラー)
						checkLowerLayer(currentAlias, retrieveManager, systemAuth,
								requestInfo, connectionInfo);
						// ACLチェック
						// エイリアスの削除は、エイリアス自体は本エントリーであるため、
						// エイリアスの親階層に削除権限があるかどうかチェックする。
						String currentAliasParent = TaggingEntryUtil.removeLastSlash(
								TaggingEntryUtil.getParentUri(currentAlias)) + 
								AclConst.URI_LAYER_DUMMY;
						aclBlogic.checkAcl(currentAliasParent, ACL_TYPE_DELETE, auth,
								requestInfo, connectionInfo);
					}
				}
			}
		}
	}

	/**
	 * 上位階層が存在するかチェック
	 * 存在しない場合、HierarchyFormatExceptionをスローする。
	 * @param uri URI
	 * @param retrieveManager 検索処理クラス
	 * @param systemAuth システム認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	private void checkUpperLayer(String uri, BDBClientRetrieveManager retrieveManager,
			SystemAuthentication systemAuth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws TaggingException, IOException {
		String serviceName = systemAuth.getServiceName();
		// 上位階層チェック
		String parent = TaggingEntryUtil.removeLastSlash(TaggingEntryUtil.getParentUri(uri));
		if (!TaggingEntryUtil.isTop(parent)) {
			// 同じFeed内で上位階層を登録している場合は処理を抜ける。
			Map<String, Value<EntryBase>> tmpReadEntryMap =
					BDBClientUtil.getTmpEntryMap(
							serviceName, connectionInfo);
			if (tmpReadEntryMap.containsKey(parent)) {
				EntryBase tmpEntry = tmpReadEntryMap.get(parent).value;
				if (tmpEntry != null) {
					return;	// チェックOK
				} else {
					// 同じFeed内で削除済み
					throw new HierarchyFormatException(
							HierarchyFormatException.MESSAGE_REQUIRE_PARENT + " " + parent);
				}

			} else {
				// Entry検索
				EntryBase parentEntry = retrieveManager.requestGetEntry(parent, true, systemAuth,
						requestInfo, connectionInfo);
				if (parentEntry == null) {
					throw new HierarchyFormatException(
							HierarchyFormatException.MESSAGE_REQUIRE_PARENT + " " + parent);
				}
			}
		}
	}

	/**
	 * 下位階層が存在するかチェックする.
	 * @param uri URI
	 * @param retrieveManager 検索処理クラス
	 * @param systemAuth システム認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	private void checkLowerLayer(String uri, BDBClientRetrieveManager retrieveManager,
			SystemAuthentication systemAuth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws TaggingException, IOException {
		FeedBase childFeed = retrieveManager.requestGetFeed(uri, false, null, 1, null, false,
				systemAuth, requestInfo, connectionInfo);
		if (TaggingEntryUtil.isExistData(childFeed)) {
			throw new HierarchyFormatException(
					HierarchyFormatException.MESSAGE_EXIST_CHILD + " " + uri);
		}
	}

	/**
	 * エイリアスのEntryが存在するかどうかチェックする.
	 * @param entry Entry
	 * @param currentEntry 現在のEntry
	 * @param flg 更新区分
	 * @param entries 同一トランザクションで更新予定のEntryリスト
	 * @param retrieveManager 検索処理クラス
	 * @param systemAuth システム認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	private void checkAliasEntry(EntryBase entry, EntryBase currentEntry,
			OperationType flg, List<EntryBase> entries, 
			BDBClientRetrieveManager retrieveManager, SystemAuthentication systemAuth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws TaggingException, IOException {
		List<String> aliases = entry.getAlternate();
		String idUri = null;
		if (flg == OperationType.UPDATE && currentEntry != null) {
			idUri = TaggingEntryUtil.getUriById(currentEntry.id);
		}
		if (aliases != null) {
			String myUri = entry.getMyUri();
			for (String alias : aliases) {
				EntryBase aliasEntry = retrieveManager.requestGetEntry(alias, false, systemAuth,
						requestInfo, connectionInfo);
				if (aliasEntry != null) {
					// 自分であればOK
					String ancestorUri = aliasEntry.getMyUri();
					if (flg == OperationType.UPDATE) {
						if (ancestorUri.equals(idUri)) {
							continue;
						}
					}
					// 同じキーが更新予定のエントリーリストに存在するが、エイリアスを削除している場合はOK
					boolean delAlias = false;
					for (EntryBase tmpEntry : entries) {
						String tmpUri = tmpEntry.getMyUri();
						if (myUri.equals(tmpUri)) {
							continue;
						}
						if (ancestorUri.equals(tmpUri)) {
							if (tmpEntry.link != null) {
								boolean hasAlias = false;
								boolean hasMyAlias = false;
								for (Link link : tmpEntry.link) {
									if (Link.REL_ALTERNATE.equals(link._$rel)) {
										hasAlias = true;
										if (alias.equals(link._$href)) {
											hasMyAlias = true;
										}
									}
								}
								if (hasAlias && !hasMyAlias) {
									delAlias = true;
									break;
								}
							}
						}
					}
					if (delAlias) {
						continue;
					}

					throw new EntryDuplicatedException(
							EntryDuplicatedException.MESSAGE + " " + alias);
				}
			}
		}
	}

	/**
	 * Entry検索用メモリキャッシュを更新.
	 * 更新時はすべて削除する。(サーバサイドJSなどによる大量データ登録時のメモリリーク防止のため)
	 * @param updatedInfos 更新情報
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	private void deleteReadEntryMap(List<UpdatedInfo> updatedInfos,
			String serviceName, RequestInfo requestInfo, ConnectionInfo connectionInfo) {
		Map<String, Value<EntryBase>> readEntryMap = BDBClientUtil.getEntryMap(
				serviceName, connectionInfo);
		Set<String> deleteSystemUris = new HashSet<>();
		for (UpdatedInfo updatedInfo : updatedInfos) {
			// prev
			EntryBase prevEntry = updatedInfo.getPrevEntry();
			if (prevEntry != null) {
				// id
				String idUri = TaggingEntryUtil.getUriById(prevEntry.id);
				if (BDBClientUtil.isSystemUri(idUri)) {
					deleteSystemUris.add(idUri);
				}
				// alias
				List<String> aliases = prevEntry.getAlternate();
				if (aliases != null) {
					for (String alias : aliases) {
						if (BDBClientUtil.isSystemUri(alias)) {
							deleteSystemUris.add(alias);
						}
					}
				}
			}
			// upd
			EntryBase updEntry = updatedInfo.getUpdEntry();
			if (updEntry != null) {
				// id
				String idUri = TaggingEntryUtil.getUriById(updEntry.id);
				if (BDBClientUtil.isSystemUri(idUri)) {
					deleteSystemUris.add(idUri);
				}
				// alias
				List<String> aliases = updEntry.getAlternate();
				if (aliases != null) {
					for (String alias : aliases) {
						if (BDBClientUtil.isSystemUri(alias)) {
							deleteSystemUris.add(alias);
						}
					}
				}
			}
		}

		BDBClientUtil.removeEntryMap(readEntryMap, deleteSystemUris);

		// 更新したEntryのうち、メインスレッド初期処理に必要なもののみReadEntryMapに登録する。
		BDBClientInitMainThreadManager initMainThreadManager =
				new BDBClientInitMainThreadManager();
		initMainThreadManager.setUpdatedInfoToReadEntryMap(updatedInfos, serviceName,
				requestInfo, connectionInfo);
	}

	/**
	 * フォルダ削除処理
	 * @param entry Entry
	 * @param uri URI
	 * @param noDeleteSelf フォルダエントリー自体を削除しない場合true
	 * @param isParallel 並列削除を行う場合true
	 * @param deleteFolderIdUris 削除対象ID URIリスト
	 * @param retrieveManager DatastoreRetrieveManager
	 * @param originalServiceName 実行元サービス名
	 * @param systemAuth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void deleteFolder(EntryBase entry, String uri, boolean noDeleteSelf, 
			boolean isParallel,Map<String, String> deleteFolderIdUris, 
			BDBClientRetrieveManager retrieveManager,
			String originalServiceName, SystemAuthentication systemAuth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		deleteFolderProc(entry, uri, noDeleteSelf, isParallel, deleteFolderIdUris, 
				retrieveManager, originalServiceName, systemAuth, 
				requestInfo, connectionInfo);
	}

	/**
	 * 指定されたキー配下のEntryを検索し再帰的に本処理を実行、自Entryを削除する.
	 * @param entry Entry
	 * @param uri 削除対象URI
	 * @param noDeleteSelf フォルダエントリー自体を削除しない場合true
	 * @param isParallel 並列削除を行う場合true
	 * @param deleteFolderIdUris 削除対象ID URIリスト
	 * @param retrieveManager 検索処理
	 * @param originalServiceName 実行元サービス名
	 * @param mapper FeedTemplateMapper
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 更新情報
	 */
	public UpdatedInfo deleteFolderProc(EntryBase entry, String uri, boolean noDeleteSelf, 
			boolean isParallel,Map<String, String> deleteFolderIdUris,
			BDBClientRetrieveManager retrieveManager, String originalServiceName,
			SystemAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		// entryがnullの場合は処理を抜ける。
		if (entry == null) {
			return null;
		}
		// ID URIがすでに処理中の場合、エイリアスであれば処理を抜ける
		String idUri = TaggingEntryUtil.getUriById(entry.id);
		String tmpUri = deleteFolderIdUris.putIfAbsent(idUri, uri);
		if (tmpUri != null) {
			if (logger.isTraceEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[deleteFolderProc] idUri is deplicated. uri = ");
				sb.append(uri);
				sb.append(" , idUri = ");
				sb.append(idUri);
				logger.debug(sb.toString());
			}
			if (!uri.equals(idUri)) {
				return null;
			}
		}

		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[deleteFolderProc] start. uri = ");
			sb.append(uri);
			sb.append(" isParallel=");
			sb.append(isParallel);
			logger.debug(sb.toString());
		}

		// コピーしたEntryで処理する。
		FeedTemplateMapper mapper = TaggingEnvUtil.getResourceMapper(serviceName);
		EntryBase tmpEntry = TaggingEntryUtil.copyEntry(entry, mapper);
		List<Future<UpdatedInfo>> futures = new ArrayList<>();

		// 削除対象URIがlink selfの場合、Entry削除
		OperationType flg = OperationType.DELETE;
		if (uri.equals(idUri)) {
			// Entryのalias、link selfについてfeed検索、Entry削除
			List<String> aliases = tmpEntry.getAlternate();
			if (aliases != null) {
				for (String alias : aliases) {
					List<Future<UpdatedInfo>> tmpFutures = deleteFeed(alias, isParallel,
							deleteFolderIdUris, retrieveManager, originalServiceName, auth,
							requestInfo, connectionInfo);
					if (tmpFutures != null) {
						futures.addAll(tmpFutures);
					}
				}
			}
			List<Future<UpdatedInfo>> tmpFutures = deleteFeed(idUri, isParallel,
					deleteFolderIdUris, retrieveManager, originalServiceName, auth,
					requestInfo, connectionInfo);
			if (tmpFutures != null) {
				futures.addAll(tmpFutures);
			}
		} else {
			// 削除対象URIがエイリアスの場合、エイリアスのみ除去更新
			List<Future<UpdatedInfo>> tmpFutures = deleteFeed(uri, isParallel,
					deleteFolderIdUris, retrieveManager, originalServiceName, auth,
					requestInfo, connectionInfo);
			if (tmpFutures != null) {
				futures.addAll(tmpFutures);
			}
			editRemoveAlias(tmpEntry, uri, requestInfo);
			flg = OperationType.UPDATE;
		}

		if (!futures.isEmpty()) {
			for (Future<UpdatedInfo> future : futures) {
				try {
					// 並列処理の完了を待つ
					future.get();

				} catch (ExecutionException e) {
					Throwable cause = e.getCause();
					if (logger.isInfoEnabled()) {
						logger.info(LogUtil.getRequestInfoStr(requestInfo) +
								"[deleteFolderProc] ExecutionException: " +
								cause.getMessage());
					}
					if (cause instanceof IOException) {
						throw (IOException)cause;
					} else if (cause instanceof TaggingException) {
						throw (TaggingException)cause;
					} else {
						throw new IOException(cause);
					}
				} catch (InterruptedException e) {
					if (logger.isInfoEnabled()) {
						logger.info(LogUtil.getRequestInfoStr(requestInfo) +
								"[deleteFolderProc] InterruptedException: " +
								e.getMessage());
					}
					throw new IOException(e);
				}
			}
		}

		// 楽観的排他制御は行わない
		tmpEntry.id = null;

		// 自Entryを削除
		if (noDeleteSelf) {
			return null;
		} else {
			List<EntryBase> entries = new ArrayList<>();
			entries.add(tmpEntry);
			List<OperationType> flgs = new ArrayList<>();
			flgs.add(flg);
	
			// Entry削除
			int numRetries = BDBRequesterUtil.getBDBRequestRetryCount();
			int waitMillis = BDBRequesterUtil.getBDBRequestRetryWaitmillis();
			for (int r = 0; r <= numRetries; r++) {
				try {
					List<UpdatedInfo> tmpUpdatedInfos = update(entries, flgs, false,
							originalServiceName, auth, requestInfo, connectionInfo);
					if (tmpUpdatedInfos != null && !tmpUpdatedInfos.isEmpty()) {
						return tmpUpdatedInfos.get(0);	// 1件のみ
					} else {
						if (logger.isInfoEnabled()) {
							logger.info(LogUtil.getRequestInfoStr(requestInfo) +
									"[deleteFolderProc] updatedInfo is null. uri=" + uri);
						}
						return null;
					}
	
				} catch (IOException e) {
					// リトライ判定、入力エラー判定
					BDBClientUtil.convertError(e, Constants.DELETE, requestInfo);
					if (r >= numRetries) {
						// リトライ対象だがリトライ回数を超えた場合
						throw e;
					}
					if (logger.isInfoEnabled()) {
						logger.info(LogUtil.getRequestInfoStr(requestInfo) +
								"[deleteFolderProc] " + BDBClientUtil.getRetryLog(e, r));
					}
					BDBClientUtil.sleep(waitMillis + r * 10);
	
				} catch (OptimisticLockingException e) {
					// 排他エラーの場合リトライ
					if (r >= numRetries) {
						// リトライ対象だがリトライ回数を超えた場合
						throw e;
					}
					if (logger.isInfoEnabled()) {
						logger.info(LogUtil.getRequestInfoStr(requestInfo) +
								"[deleteFolderProc] " + BDBClientUtil.getRetryLog(e, r));
					}
					BDBClientUtil.sleep(waitMillis + r * 10);
	
				} catch (NoExistingEntryException e) {
					// 他スレッドで削除された場合、処理を飛ばす
					if (logger.isInfoEnabled()) {
						StringBuilder sb = new StringBuilder();
						sb.append(LogUtil.getRequestInfoStr(requestInfo));
						sb.append("[deleteFolderProc] NoExistingEntryException (continue): ");
						sb.append(e.getMessage());
						logger.info(sb.toString());
					}
					return null;
				}
			}
			// 通らない
			throw new IllegalStateException("The code that should not pass.");
		}
	}

	/**
	 * Feed検索し、各Entryについて削除処理を呼び出す.
	 * @param uri URI
	 * @param isParallel 並列削除を行う場合true
	 * @param deleteFolderIdUris 削除対象ID URIリスト
	 * @param retrieveManager 検索処理
	 * @param originalServiceName 実行元サービス名
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 並列処理オブジェクト
	 */
	private List<Future<UpdatedInfo>> deleteFeed(String uri, boolean isParallel,
			Map<String, String> deleteFolderIdUris, BDBClientRetrieveManager retrieveManager,
			String originalServiceName, SystemAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		int limit = TaggingEnvUtil.getEntryNumberDefault(serviceName);

		String cursorStr = null;
		List<Future<UpdatedInfo>> futures = new ArrayList<>();
		do {
			FeedBase feed = retrieveManager.requestGetFeed(uri, false, null, limit,
					cursorStr, false, auth, requestInfo, connectionInfo);

			cursorStr = TaggingEntryUtil.getCursorFromFeed(feed);
			if (feed != null && feed.entry != null) {
				for (EntryBase entry : feed.entry) {
					String myUri = TaggingEntryUtil.getSpecifiedParentUri(entry, uri);
					if (isParallel) {
						// 並列削除指定の場合、TaskQueueで並列処理を行う。
						DeleteFolderProcCallable callable = new DeleteFolderProcCallable(
								entry, myUri, deleteFolderIdUris, originalServiceName);
						Future<UpdatedInfo> future = callable.addTask(auth, requestInfo, connectionInfo);
						futures.add(future);
					} else {
						// 現スレッドでフォルダ削除処理を実行
						deleteFolderProc(entry, myUri, false, isParallel, deleteFolderIdUris,
								retrieveManager, originalServiceName, auth,
								requestInfo, connectionInfo);
					}
				}
			}

		} while (!StringUtils.isBlank(cursorStr));

		return futures;
	}

	/**
	 * Entryからエイリアスを除去する.
	 * @param entry Entry
	 * @param alias エイリアス
	 * @param requestInfo リクエスト情報
	 */
	public void editRemoveAlias(EntryBase entry, String alias, RequestInfo requestInfo) {
		int len = entry.link.size();
		List<String> aliases = entry.getAlternate();
		if (aliases == null) {
			// データ不正
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("aliases is null. entry.id = ");
			sb.append(entry.id);
			sb.append(" alias = ");
			sb.append(alias);
			sb.append(" link.size = ");
			sb.append(len);
			String msg = sb.toString();
			logger.warn(msg);
			throw new IllegalStateException(msg);
		}
		boolean isSetBlank = false;
		if (aliases.size() == 1) {
			isSetBlank = true;
		}
		for (int i = 0; i < len; i++) {
			Link link = entry.link.get(i);
			if (Link.REL_ALTERNATE.equals(link._$rel) &&
					alias.equals(link._$href)) {
				if (isSetBlank) {
					link._$href = "";
					// 合わせてlink titleも削除する。
					if (!StringUtils.isBlank(link._$title)) {
						link._$title = null;
					}
					if (!StringUtils.isBlank(link._$type)) {
						link._$type = null;
					}
					if (!StringUtils.isBlank(link._$length)) {
						link._$length = null;
					}
				} else {
					entry.link.remove(i);
				}
				break;
			}
		}
	}

	/**
	 * 排他処理.
	 * Redisに排他フラグを立てる。
	 * @param idUri ID URI
	 * @param systemContext SystemContext
	 * @return 排他が成功した場合true
	 */
	private boolean exclusive(String idUri, SystemContext systemContext)
	throws IOException, TaggingException {
		String key = EXCLUSIVE_KEY_PREFIX + idUri;
		CacheManager cacheManager = TaggingEnvUtil.getCacheManager();
		boolean ret = cacheManager.setStringIfAbsent(key, EXCLUSIVE_TEXT, systemContext);
		if (ret) {
			if (logger.isTraceEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(systemContext.getRequestInfo()));
				sb.append("[exclusive] uri = ");
				sb.append(idUri);
				logger.debug(sb.toString());
			}
			// 有効期間の設定
			int sec = TaggingEnvUtil.getSystemPropInt(BDBClientConst.BDBCLIENT_EXCLUSION_EXPIRE_SEC,
					BDBClientConst.BDBCLIENT_EXCLUSION_EXPIRE_SEC_DEFAULT);
			cacheManager.setExpireString(key, sec, systemContext);
		}
		return ret;
	}

	/**
	 * 排他解除.
	 * Redisから排他フラグを削除する。
	 * @param idUri ID URI
	 * @param systemContext SystemContext
	 * @return 排他が成功した場合true
	 */
	private boolean releaseExclusion(String idUri, SystemContext systemContext)
	throws IOException, TaggingException {
		String key = EXCLUSIVE_KEY_PREFIX + idUri;
		CacheManager cacheManager = TaggingEnvUtil.getCacheManager();
		boolean ret = cacheManager.deleteString(key, systemContext);
		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(systemContext.getRequestInfo()));
			sb.append("[releaseExclusion] uri = ");
			sb.append(idUri);
			sb.append(" ret=");
			sb.append(ret);
			logger.debug(sb.toString());
		}
		return ret;
	}

	/**
	 * ID指定でEntryを1件削除.
	 * Entryサーバにリクエストする。
	 * @param id ID
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void requestDeleteEntryById(String id, String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// BDBサーバにリクエスト
		try {
			// IDをキーにEntry削除
			String method = METHOD_DELETE;
			String idUri = TaggingEntryUtil.getUriById(id);
			String entryServerUrl = BDBClientUtil.getEntryServerUrl(idUri, serviceName,
					requestInfo, connectionInfo);
			String entryUriStr = BDBClientUtil.getEntryUri(id);
			FeedTemplateMapper mapper = TaggingEnvUtil.getResourceMapper(serviceName);

			BDBRequester<FeedBase> requester = new BDBRequester<>(BDBResponseType.FEED);
			requester.request(entryServerUrl, entryUriStr, method, null,
					mapper, serviceName, requestInfo, connectionInfo);

		} finally {
			// 何かあれば処理する
		}
	}

	/**
	 * Manifestサーバへの情報を生成.
	 * @param uri URI
	 * @param id ID
	 * @return Manifestサーバへの情報
	 */
	private Link createManifestLink(String uri, String id) {
		return createManifestLink(uri, id, false);
	}

	/**
	 * Manifestサーバへの情報を生成.
	 * @param uri URI
	 * @param id ID
	 * @return Manifestサーバへの情報
	 */
	private Link createManifestLink(String uri, String id, boolean isDelete) {
		Link mnfLink = new Link();
		mnfLink._$rel = Link.REL_SELF;
		if (!isDelete) {
			mnfLink._$href = uri;
		} else {
			// 削除の場合、キーの部分に_deleteを指定する。
			mnfLink._$href = RequestParam.PARAM_DELETE;
		}
		mnfLink._$title = id;
		return mnfLink;
	}

}
