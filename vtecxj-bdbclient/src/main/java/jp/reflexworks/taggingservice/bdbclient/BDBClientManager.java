package jp.reflexworks.taggingservice.bdbclient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.api.Condition;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.entry.Link;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.blogic.AclBlogic;
import jp.reflexworks.taggingservice.conn.ConnectionInfoImpl;
import jp.reflexworks.taggingservice.context.ReflexContextUtil;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.EntryDuplicatedException;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.NoExistingEntryException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.index.UpdateIndexRequester;
import jp.reflexworks.taggingservice.migrate.BDBClientMaintenanceManager;
import jp.reflexworks.taggingservice.migrate.BDBClientServiceManager;
import jp.reflexworks.taggingservice.model.UpdatedInfo;
import jp.reflexworks.taggingservice.model.Value;
import jp.reflexworks.taggingservice.plugin.AllocateIdsManager;
import jp.reflexworks.taggingservice.plugin.DatastoreManager;
import jp.reflexworks.taggingservice.plugin.SettingService;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBResponseType;
import jp.reflexworks.taggingservice.requester.BDBClientServerManager;
import jp.reflexworks.taggingservice.requester.BDBRequester;
import jp.reflexworks.taggingservice.requester.BDBRequesterUtil;
import jp.reflexworks.taggingservice.service.BDBClientInitMainThreadManager;
import jp.reflexworks.taggingservice.sys.SystemAuthentication;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.sys.SystemUtil;
import jp.reflexworks.taggingservice.taskqueue.TaskQueueUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.Constants.OperationType;
import jp.reflexworks.taggingservice.util.IndexUtil;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.RetryUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.DeflateUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * Google Cloud Storageへのコンテンツアクセスクラス.
 * リトライ処理はこのクラスのpublicメソッド内で行う。<br>
 * privateメソッドや、呼び出される先でリトライを行わない。<br>
 */
public class BDBClientManager implements DatastoreManager {

	/** エイリアス用自動採番指定階層の文字列長 (#の部分のみ) */
	private static final int URI_NUMBERING_LEN = Constants.URI_NUMBERING.length() - 1;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 初期起動時の処理.
	 */
	public void init() {
		// システム管理サーバへ接続テスト。ここで接続できなければしばらく待ってリトライする。
		// (後続処理でBDBにアクセス出来ないとエラーでAPサーバ起動できないため。)

		BDBRequester<FeedBase> bdbRequester = new BDBRequester<>(BDBResponseType.FEED);
		BDBRequester<EntryBase> bdbRequesterEntry = new BDBRequester<>(BDBResponseType.ENTRY);
		String idUri = "/";
		String tmpMnfUri = idUri + "?" + RequestParam.PARAM_ENTRY;
		DeflateUtil deflateUtil = null;
		ConnectionInfo connectionInfo = null;
		try {
			// BDBサーバ接続テスト
			// Manifestサーバ、Entryサーバにリクエストする
			String serviceName = TaggingEnvUtil.getSystemService();
			RequestInfo requestInfo = SystemUtil.getRequestInfo(serviceName,
					"init", "init");
			deflateUtil = new DeflateUtil();
			connectionInfo = new ConnectionInfoImpl(deflateUtil, requestInfo);

			int numRetries = BDBClientUtil.getBDBRequestInitRetryCount();
			int waitMillis = BDBClientUtil.getBDBRequestInitRetryWaitmillis();

			String id = null;
			// Manifestサーバへリクエスト
			for (int r = 0; r <= numRetries; r++) {
				try {
					BDBResponseInfo<FeedBase> respInfo = bdbRequester.requestToManifest(
							tmpMnfUri, Constants.GET, null, serviceName,
							requestInfo, connectionInfo);
					FeedBase retFeed = respInfo.data;
					if (retFeed != null && retFeed.link != null && !retFeed.link.isEmpty()) {
						// <link rel="self" href={キー} title={ID} />
						id = retFeed.link.get(0)._$title;
					}
					break;

				} catch (IOException e) {
					// リトライ判定、入力エラー判定
					try {
						BDBClientUtil.convertError(e, Constants.GET, requestInfo);
					} catch (IOException ie) {
						throw new IllegalStateException(ie);
					}
					if (r >= numRetries) {
						// リトライ対象だがリトライ回数を超えた場合
						throw new IllegalStateException(e);
					}
					if (logger.isInfoEnabled()) {
						logger.info(LogUtil.getRequestInfoStr(requestInfo) +
								"[init] " + BDBClientUtil.getRetryLog(e, r));
					}
					BDBClientUtil.sleep(waitMillis + r * 10);
				} catch (TaggingException e) {
					throw new IllegalStateException(e);
				}
			}

			// Entryサーバへリクエスト
			if (StringUtils.isBlank(id)) {
				// 仮のIDを設定
				id = idUri + ",1";
			}
			String tmpEntryUri = id + "?" + RequestParam.PARAM_ENTRY;
			String bdbServerUrl = null;
			try {
				bdbServerUrl = BDBClientUtil.getEntryServerUrl(idUri, serviceName,
						requestInfo, connectionInfo);
			} catch (IOException | TaggingException e) {
				throw new IllegalStateException(e);
			}
			FeedTemplateMapper mapper = TaggingEnvUtil.getResourceMapper(serviceName);
			for (int r = 0; r <= numRetries; r++) {
				try {
					bdbRequesterEntry.request(bdbServerUrl, tmpEntryUri, Constants.GET,
							null, mapper, serviceName, requestInfo, connectionInfo);
					break;

				} catch (IOException e) {
					// リトライ判定、入力エラー判定
					try {
						BDBClientUtil.convertError(e, Constants.GET, requestInfo);
					} catch (IOException ie) {
						throw new IllegalStateException(ie);
					}
					if (r >= numRetries) {
						// リトライ対象だがリトライ回数を超えた場合
						throw new IllegalStateException(e);
					}
					if (logger.isInfoEnabled()) {
						logger.info(LogUtil.getRequestInfoStr(requestInfo) +
								"[init] " + BDBClientUtil.getRetryLog(e, r));
					}
					BDBClientUtil.sleep(waitMillis + r * 10);
				} catch (TaggingException e) {
					throw new IllegalStateException(e);
				}
			}

			// BDBサーバ情報格納先を準備
			BDBClientServerManager serverManager = new BDBClientServerManager();
			serverManager.init();

		} finally {
			if (connectionInfo != null) {
				connectionInfo.close();
			}
		}
	}

	/**
	 * シャットダウン時の処理.
	 */
	public void close() {
		// Do nothing.
	}

	/**
	 * 登録処理.
	 * <p>
	 * <ul>
	 *   <li>ACLチェック</li>
	 *   <li>自動採番、link selfの更新(エイリアス指定された場合)</li>
	 *   <li>トランザクション開始。指定されたFeed内の全てのEntryは同じトランザクションで更新される。</li>
	 *   <li>id、author、published、updatedの編集</li>
	 *   <li>更新、コミット</li>
	 * </ul>
	 * </p>
	 * @param feed Feed
	 * @param parentUri 親URI
	 * @param ext 自動採番の場合の拡張子
	 * @param originalServiceName 元のサービス名
	 * @param auth 認証情報(更新対象サービス)
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return entryに更新されたEntryが設定される。prevEntryはnull。
	 */
	public List<UpdatedInfo> post(FeedBase feed, String parentUri, String ext,
			String originalServiceName, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();

		boolean isFailed = false;
		try {
			// 処理フラグリスト
			// 添字はfeed.entryに紐づく。
			List<OperationType> flgs = null;

			BDBClientRetrieveManager retrieveManager = new BDBClientRetrieveManager();
			BDBClientUpdateManager updateManager = new BDBClientUpdateManager();
			FeedTemplateMapper mapper = TaggingEnvUtil.getResourceMapper(serviceName);

			// 前もって更新に必要な検索を行う。（パフォーマンス改善のため）
			Set<String> tmpEntryUris = getEntryUris(feed, parentUri, updateManager);
			prepareUpdate(tmpEntryUris, originalServiceName, auth, requestInfo, connectionInfo);

			// リトライはトランザクション単位。リトライはキー自動採番メソッド内で組む。
			int numRetries = BDBRequesterUtil.getBDBRequestRetryCount();
			int waitMillis = BDBRequesterUtil.getBDBRequestRetryWaitmillis();
			for (int r = 0; r <= numRetries; r++) {
				flgs = new ArrayList<>();
				try {
					// まず以下のことを行う。(トランザクション発行前)
					// 1. データストアからEntry取得 -> ID URIが分かる
					// 2. 処理判定(登録・更新・削除)
					// 3. ACLチェック
					// 4. キー自動採番(必要な場合)
					// 5. 引数のEntryのlink rel="self"のhrefをID URIに置き換える。
					//   -> ここまでできたらDatastoreUpdateManagerを呼び出す。

					for (EntryBase entry : feed.entry) {
						// データ存在チェック
						String myUri = entry.getMyUri();
						boolean isEntryDuplicated = false;
						if (!StringUtils.isBlank(myUri)) {
							EntryBase currentEntry =
									retrieveManager.getEntryBySystem(
											myUri, true, auth, requestInfo, connectionInfo);
							if (currentEntry != null) {
								// キー重複エラー
								// ACLチェック後にエラーを返す。
								isEntryDuplicated = true;
							}
						}
						OperationType flg = OperationType.INSERT;

						// ACLチェック
						AclBlogic aclBlogic = new AclBlogic();
						String action = BDBClientUtil.convertAclType(flg);
						String aclUri = null;
						if (isNumbering(entry, parentUri)) {
							aclUri = aclBlogic.getDummySelfidUri(parentUri);
						} else {
							aclUri = entry.getMyUri();
						}
						aclBlogic.checkAcl(aclUri, action, auth,
								requestInfo, connectionInfo);

						if (isEntryDuplicated) {
							// キー重複エラー
							throw new EntryDuplicatedException(
									EntryDuplicatedException.MESSAGE + " " + myUri);
						}

						flgs.add(flg);
					}

					// キー自動採番の場合は採番を行う。
					// リトライ時はentryに既にキーが指定されているので自動採番されない。
					setNumberingUri(feed.entry, parentUri, ext, auth, requestInfo,
							connectionInfo);

					break;

				} catch (IOException e) {
					// リトライ判定、入力エラー判定
					BDBClientUtil.convertError(e, Constants.POST, requestInfo);
					if (r >= numRetries) {
						// リトライ対象だがリトライ回数を超えた場合
						throw e;
					}
					if (logger.isInfoEnabled()) {
						logger.info(LogUtil.getRequestInfoStr(requestInfo) +
								"[post] " + BDBClientUtil.getRetryLog(e, r));
					}
					BDBClientUtil.sleep(waitMillis + r * 10);
				}
			}

			// 更新処理
			for (int r = 0; r <= numRetries; r++) {
				// リトライを考慮しコピーしたもので処理する。
				FeedBase tmpFeed = TaggingEntryUtil.copyFeed(feed, mapper);
				try {
					return updateManager.update(tmpFeed.entry, flgs, true,
							originalServiceName, auth, requestInfo, connectionInfo);

				} catch (IOException e) {
					// リトライ判定、入力エラー判定
					BDBClientUtil.convertError(e, Constants.POST, requestInfo);
					if (r >= numRetries) {
						// リトライ対象だがリトライ回数を超えた場合
						throw e;
					}
					if (logger.isInfoEnabled()) {
						logger.info(LogUtil.getRequestInfoStr(requestInfo) +
								"[post] " + BDBClientUtil.getRetryLog(e, r));
					}
					BDBClientUtil.sleep(waitMillis + r * 10);
				}
			}

		} finally {
			if (isFailed) {
				// バックアップを元に戻す
			}
		}
		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}

	/**
	 * 更新処理.
	 * <p>
	 * <ul>
	 *   <li>ACLチェック</li>
	 *   <li>自動採番、link selfの更新(エイリアス指定された場合)</li>
	 *   <li>トランザクション開始。指定されたFeed内の全てのEntryは同じトランザクションで更新される。</li>
	 *   <li>id、author、published、updatedの編集</li>
	 *   <li>更新、コミット</li>
	 * </ul>
	 * </p>
	 * @param feed Feed
	 * @param parentUri 親URI。自動採番登録時に使用。
	 * @param updateAllIndex インデックスをすべて更新(データパッチ用) (BDB版では使用しない)
	 * @param originalServiceName 元のサービス名
	 * @param auth 認証情報(更新対象サービス)
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return entryに更新されたEntryが設定される。prevEntryはnull。
	 */
	public List<UpdatedInfo> put(FeedBase feed, String parentUri,
			boolean updateAllIndex, String originalServiceName, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();

		boolean isFailed = false;
		try {
			// 処理フラグリスト
			// 添字はfeed.entryに紐づく。
			List<OperationType> flgs = null;

			BDBClientRetrieveManager retrieveManager = new BDBClientRetrieveManager();
			BDBClientUpdateManager updateManager = new BDBClientUpdateManager();
			FeedTemplateMapper mapper = TaggingEnvUtil.getResourceMapper(serviceName);

			// 前もって更新に必要な検索を行う。（パフォーマンス改善のため）
			Set<String> tmpEntryUris = getEntryUris(feed, parentUri, updateManager);
			prepareUpdate(tmpEntryUris, originalServiceName, auth, requestInfo, connectionInfo);

			int numRetries = BDBRequesterUtil.getBDBRequestRetryCount();
			int waitMillis = BDBRequesterUtil.getBDBRequestRetryWaitmillis();
			for (int r = 0; r <= numRetries; r++) {
				flgs = new ArrayList<OperationType>();
				try {
					// まず以下のことを行う。(トランザクション発行前)
					// 1. データストアからEntry取得 -> ID URIが分かる
					// 2. 処理判定(登録・更新・削除)
					// 3. ACLチェック
					// 4. キー自動採番(必要な場合)
					// 5. 引数のEntryのlink rel="self"のhrefをID URIに置き換える。
					//   -> ここまでできたらDatastoreUpdateManagerを呼び出す。

					for (EntryBase entry : feed.entry) {
						// データ存在チェック
						String myUri = entry.getMyUri();
						EntryBase currentEntry = null;
						if (!StringUtils.isBlank(myUri)) {
							currentEntry =
								retrieveManager.getEntryBySystem(
										myUri, true, auth, requestInfo, connectionInfo);
						}
						OperationType flg = judgeOperation(entry, currentEntry);
						// idに?_deletedがあれば除去
						TaggingEntryUtil.removeIdParam(entry);
						// データ存在チェック
						boolean isNoExisting = false;
						if (flg == OperationType.UPDATE || flg == OperationType.DELETE) {
							if (currentEntry == null) {
								// ACLチェック後にエラーを返す。
								isNoExisting = true;
							}
						}

						// ACLチェック
						AclBlogic aclBlogic = new AclBlogic();
						String action = BDBClientUtil.convertAclType(flg);
						String aclUri = null;
						if (flg == OperationType.INSERT && isNumbering(entry, parentUri)) {
							aclUri = aclBlogic.getDummySelfidUri(parentUri);
						} else {
							aclUri = entry.getMyUri();
						}
						aclBlogic.checkAcl(aclUri, action, auth,
								requestInfo, connectionInfo);

						if (isNoExisting) {
							// データ存在なし
							throw new NoExistingEntryException(
									NoExistingEntryException.MSG_PREFIX + myUri);
						}

						if (flg == OperationType.DELETE) {
							// 削除処理について、
							// idUri=myUriの場合削除、異なる場合エイリアスを除去して更新
							String idUri = TaggingEntryUtil.getUriById(currentEntry.id);
							flg = editDeleteAlias(entry, currentEntry, myUri, idUri,
									updateManager, requestInfo);
						}

						if (flg == OperationType.UPDATE || flg == OperationType.DELETE) {
							// 引数のEntryのlink rel="self"のhrefをID URIに置き換える。
							setIdUri(entry, currentEntry.id);
						}

						flgs.add(flg);
					}

					int i = 0;
					List<EntryBase> insertEntries = new ArrayList<>();
					for (EntryBase entry : feed.entry) {
						OperationType flg = flgs.get(i++);

						// キー自動採番の場合は採番を行う。
						// リトライ時はentryに既にキーが指定されているので自動採番されない。
						if (flg == OperationType.INSERT) {
							insertEntries.add(entry);
						}
					}
					setNumberingUri(insertEntries, parentUri, null, auth, requestInfo,
							connectionInfo);

					break;

				} catch (IOException e) {
					// リトライ判定、入力エラー判定
					BDBClientUtil.convertError(e, Constants.PUT, requestInfo);
					if (r >= numRetries) {
						// リトライ対象だがリトライ回数を超えた場合
						throw e;
					}
					if (logger.isInfoEnabled()) {
						logger.info(LogUtil.getRequestInfoStr(requestInfo) +
								"[put] " + BDBClientUtil.getRetryLog(e, r));
					}
					BDBClientUtil.sleep(waitMillis + r * 10);
				}
			}

			// 更新処理
			for (int r = 0; r <= numRetries; r++) {
				// リトライを考慮しコピーしたもので処理する。
				FeedBase tmpFeed = TaggingEntryUtil.copyFeed(feed, mapper);
				try {
					return updateManager.update(tmpFeed.entry, flgs, false,
							originalServiceName, auth, requestInfo, connectionInfo);

				} catch (IOException e) {
					// リトライ判定、入力エラー判定
					BDBClientUtil.convertError(e, Constants.PUT, requestInfo);
					if (r >= numRetries) {
						// リトライ対象だがリトライ回数を超えた場合
						throw e;
					}
					if (logger.isInfoEnabled()) {
						logger.info(LogUtil.getRequestInfoStr(requestInfo) +
								"[put] " + BDBClientUtil.getRetryLog(e, r));
					}
					BDBClientUtil.sleep(waitMillis + r * 10);
				}
			}

		} finally {
			if (isFailed) {
				// バックアップを元に戻す
			}
		}
		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}

	/**
	 * 削除処理.
	 * <p>
	 * <ul>
	 *   <li>ACLチェック</li>
	 *   <li>自動採番、link selfの更新(エイリアス指定された場合)</li>
	 *   <li>トランザクション開始。指定されたFeed内の全てのEntryは同じトランザクションで更新される。</li>
	 *   <li>id、author、published、updatedの編集</li>
	 *   <li>更新、コミット</li>
	 * </ul>
	 * </p>
	 * @param uriAndRevisions {uri},{revision}のリスト
	 * @param originalServiceName 元のサービス名
	 * @param auth 認証情報(更新対象サービス)
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return prevEntryに更新前Entryが設定される。
	 */
	public List<UpdatedInfo> delete(List<String> uriAndRevisions,
			String originalServiceName, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();

		boolean isFailed = false;
		try {
			// 処理フラグリスト
			// 添字はfeed.entryに紐づく。
			List<EntryBase> entries = null;
			List<OperationType> flgs = null;

			BDBClientRetrieveManager retrieveManager = new BDBClientRetrieveManager();
			BDBClientUpdateManager updateManager = new BDBClientUpdateManager();
			FeedTemplateMapper mapper = TaggingEnvUtil.getResourceMapper(serviceName);

			// キー自動採番(対象の場合) トランザクションの前に行うべき
			// リトライはトランザクション単位。リトライはキー自動採番メソッド内で組む。
			int numRetries = BDBRequesterUtil.getBDBRequestRetryCount();
			int waitMillis = BDBRequesterUtil.getBDBRequestRetryWaitmillis();
			for (int r = 0; r <= numRetries; r++) {
				entries = new ArrayList<EntryBase>();
				flgs = new ArrayList<OperationType>();
				try {
					// まず以下のことを行う。(トランザクション発行前)
					// 1. データストアからEntry取得 -> ID URIが分かる
					// 2. ACLチェック
					// 3. 引数のEntryのlink rel="self"のhrefをID URIに置き換える。
					//   -> ここまでできたらDatastoreUpdateManagerを呼び出す。

					for (String uriAndRevStr : uriAndRevisions) {
						String[] uriAndRev = BDBClientUtil.getUriAndRevision(
								uriAndRevStr);

						// データ存在チェック
						String myUri = uriAndRev[0];
						EntryBase currentEntry =
								retrieveManager.getEntryBySystem(
										myUri, true, auth, requestInfo, connectionInfo);

						EntryBase entry = null;
						if (currentEntry == null) {
							// データ未存在エラー
							throw new NoExistingEntryException(NoExistingEntryException.MSG_PREFIX + myUri);
						}

						// ACLチェック
						AclBlogic aclBlogic = new AclBlogic();
						String action = BDBClientUtil.convertAclType(OperationType.DELETE);
						String aclUri = myUri;
						aclBlogic.checkAcl(aclUri, action, auth,
								requestInfo, connectionInfo);

						entry = TaggingEntryUtil.createEntry(serviceName);
						String idUri = TaggingEntryUtil.getUriById(currentEntry.id);
						// idUri=myUriの場合削除、異なる場合エイリアスを除去して更新
						OperationType flg = editDeleteAlias(entry, currentEntry, myUri, idUri,
								updateManager, requestInfo);

						if (!StringUtils.isBlank(uriAndRev[1])) {
							entry.id = TaggingEntryUtil.getId(idUri, uriAndRev[1]);
						}
						setIdUri(entry, currentEntry.id);
						entries.add(entry);
						flgs.add(flg);
					}
					break;

				} catch (IOException e) {
					// リトライ判定、入力エラー判定
					BDBClientUtil.convertError(e, Constants.DELETE, requestInfo);
					if (r >= numRetries) {
						// リトライ対象だがリトライ回数を超えた場合
						throw e;
					}
					if (logger.isInfoEnabled()) {
						logger.info(LogUtil.getRequestInfoStr(requestInfo) +
								"[delete] " + BDBClientUtil.getRetryLog(e, r));
					}
					BDBClientUtil.sleep(waitMillis + r * 10);
				}
			}

			// 更新処理
			for (int r = 0; r <= numRetries; r++) {
				// リトライを考慮しコピーしたもので処理する。
				List<EntryBase> tmpEntries = TaggingEntryUtil.copyEntries(entries,
						mapper);
				try {
					return updateManager.update(tmpEntries, flgs, false,
							originalServiceName, auth, requestInfo, connectionInfo);

				} catch (IOException e) {
					// リトライ判定、入力エラー判定
					BDBClientUtil.convertError(e, Constants.DELETE, requestInfo);
					if (r >= numRetries) {
						// リトライ対象だがリトライ回数を超えた場合
						throw e;
					}
					if (logger.isInfoEnabled()) {
						logger.info(LogUtil.getRequestInfoStr(requestInfo) +
								"[delete] " + BDBClientUtil.getRetryLog(e, r));
					}
					BDBClientUtil.sleep(waitMillis + r * 10);
				}
			}

		} finally {
			if (isFailed) {
				// バックアップを元に戻す
			}
		}
		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}

	/**
	 * フォルダ削除.
	 * 指定されたURI配下のEntryを全て削除します。
	 * Entryがエイリアスを持つ場合、エイリアス配下のEntryも削除します。
	 * @param uri URI
	 * @param noDeleteSelf フォルダエントリー自体を削除しない場合true
	 * @param async 非同期の場合true、同期の場合false
	 * @param isParallel 並列削除を行う場合true
	 * @param originalServiceName 元のサービス名
	 * @param auth 認証情報(更新対象サービス)
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return Future 同期の場合はnull
	 */
	public Future deleteFolder(String uri, boolean noDeleteSelf, boolean async, 
			boolean isParallel, String originalServiceName, ReflexAuthentication auth, 
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// ACLチェック
		AclBlogic aclBlogic = new AclBlogic();
		String action = BDBClientUtil.convertAclType(OperationType.DELETE);
		aclBlogic.checkAcl(uri, action, auth, requestInfo, connectionInfo);
		// 配下のエントリー削除権限も必要
		String aclUri = aclBlogic.getDummySelfidUri(uri);
		aclBlogic.checkAcl(aclUri, action, auth, requestInfo, connectionInfo);

		// authはシステムを使用
		SystemAuthentication systemAuth = new SystemAuthentication(auth);
		// データ存在チェック
		BDBClientRetrieveManager retrieveManager = new BDBClientRetrieveManager();
		EntryBase entry = retrieveManager.getEntry(uri, false,
				systemAuth, requestInfo, connectionInfo);
		if (entry == null) {
			throw new NoExistingEntryException(NoExistingEntryException.MSG_PREFIX + uri);
		}

		// 削除対象ID URIリスト
		Map<String, String> deleteFolderIdUris = new ConcurrentHashMap<String, String>();
		BDBClientDeleteFolderCallable callable = new BDBClientDeleteFolderCallable(
				entry, uri, noDeleteSelf, isParallel, deleteFolderIdUris, 
				originalServiceName, systemAuth);
		if (!async) {
			callable.deleteFolderProc(requestInfo, connectionInfo);
			return null;
		} else {
			ConnectionInfo sharingConnectionInfo = BDBClientUtil.copySharingConnectionInfo(
					requestInfo, connectionInfo);
			return TaskQueueUtil.addTask(callable, 0, systemAuth, requestInfo, sharingConnectionInfo);
		}
	}

	/**
	 * 更新区分を判定.
	 * @param entry 入力Entry
	 * @param currentEntry 登録されたEntry
	 * @return 更新区分
	 */
	private OperationType judgeOperation(EntryBase entry, EntryBase currentEntry) {
		// Entryがデータストアに登録されておらず、idが指定されていない場合は登録処理。
		if (entry.id == null || entry.id.length() == 0) {
			if (currentEntry == null) {
				return OperationType.INSERT;
			} else {
				return OperationType.UPDATE;
			}
		}
		OperationType retFlg = OperationType.UPDATE;
		String param = TaggingEntryUtil.getParamById(entry.id);
		if (RequestParam.PARAM_DELETE.equals(param)) {
			// IDに「?_delete」パラメータが指定されていれば削除処理
			retFlg = OperationType.DELETE;
		} else {
			int rev = TaggingEntryUtil.getRevisionById(entry.id);
			if (rev == 0) {
				// 指定されたIDのリビジョンが0であれば登録処理
				retFlg = OperationType.INSERT;
			}
		}
		return retFlg;
	}

	/**
	 * 指定されたEntryのURIをID URIに置き換える。
	 * @param entry Entry
	 * @param id ID
	 */
	private void setIdUri(EntryBase entry, String currentId) {
		String myUri = entry.getMyUri();
		String idUri = TaggingEntryUtil.getUriById(currentId);
		if (!idUri.equals(myUri)) {
			entry.setMyUri(idUri);
		}
	}

	/**
	 * 登録時、Entryが自動採番の対象かどうかを判定する.
	 * @param entry Entry
	 * @param parentUri 指定された親階層
	 * @return Entryが自動採番の対象の場合true
	 */
	private boolean isNumbering(EntryBase entry, String parentUri) {
		boolean isNumbering = false;
		String uri = entry.getMyUri();
		if (StringUtils.isBlank(uri)) {
			isNumbering = true;
		} else if (uri.endsWith(Constants.URI_NUMBERING)) {
			isNumbering = true;
		}
		return isNumbering;
	}

	/**
	 * 登録時、キー自動採番指定であれば採番する.
	 * 必要数をまとめて採番する。
	 * @param entries Entryリスト
	 * @param parentUri 自動採番時の親階層
	 * @param ext 自動採番時の拡張子
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	private void setNumberingUri(List<EntryBase> entries, String parentUri, String ext,
			ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// 採番対象マップ　キー:親階層、値:Entryリスト
		Map<String, List<EntryBase>> numberingMap = new HashMap<>();
		for (EntryBase entry : entries) {
			String numberingParentUri = null;
			String uri = entry.getMyUri();
			if (StringUtils.isBlank(uri)) {
				numberingParentUri = parentUri;
			} else if (uri.endsWith(Constants.URI_NUMBERING)) {
				numberingParentUri = uri.substring(0, uri.length() - 1);
			}
			if (!StringUtils.isBlank(numberingParentUri)) {
				List<EntryBase> numberingEntries = numberingMap.get(numberingParentUri);
				if (numberingEntries == null) {
					numberingEntries = new ArrayList<>();
					numberingMap.put(numberingParentUri, numberingEntries);
				}
				numberingEntries.add(entry);
			}
		}

		for (Map.Entry<String, List<EntryBase>> mapEntry : numberingMap.entrySet()) {
			String numberingParentUri = mapEntry.getKey();
			List<EntryBase> numberingEntries = mapEntry.getValue();

			// 採番
			int size = numberingEntries.size();
			SystemAuthentication systemAuth = new SystemAuthentication(auth);
			AllocateIdsManager allocateIdsManager = TaggingEnvUtil.getAllocateIdsManager();
			List<String> allocids = allocateIdsManager.allocateIds(parentUri, size,
					systemAuth, requestInfo, connectionInfo);

			int i = 0;
			for (EntryBase entry : numberingEntries) {
				String allocid = allocids.get(i);
				i++;
				String numberingUri = TaggingEntryUtil.editSlash(numberingParentUri) + allocid;
				if (!StringUtils.isBlank(ext)) {
					numberingUri += "." + ext;
				}
				entry.setMyUri(numberingUri);

				// エイリアスに反映
				for (Link link : entry.link) {
					if (link == null) {
						continue;
					}
					if (Link.REL_ALTERNATE.equals(link._$rel) &&
							link._$href != null) {
						String aliasParent = null;
						if (link._$href.endsWith(Constants.URI_NUMBERING)) {
							aliasParent = link._$href.substring(0,
									link._$href.length() - URI_NUMBERING_LEN);
						} else {
							aliasParent = TaggingEntryUtil.editSlash(numberingParentUri);
						}
						String aliasUri = aliasParent + allocid;
						if (!StringUtils.isBlank(ext)) {
							aliasUri += "." + ext;
						}
						link._$href = aliasUri;
					}
				}
			}
		}
	}

	/**
	 * 更新のために検索が必要なURIリストを作成.
	 * パフォーマンス改善のための処理
	 * @param feed Feed
	 * @param parentUri 自動採番の親階層
	 * @param updateManager BDBClientUpdateManager
	 * @return URIリスト
	 */
	private Set<String> getEntryUris(FeedBase feed, String parentUri,
			BDBClientUpdateManager updateManager) {
		Set<String> entryUris = new HashSet<>();
		boolean isNumbering = false;
		for (EntryBase entry : feed.entry) {
			if (isNumbering(entry, parentUri)) {
				isNumbering = true;
			} else {
				String myUri = entry.getMyUri();
				entryUris.addAll(TaggingEntryUtil.getParentPathUris(myUri));
				if (!StringUtils.isBlank(entry.id)) {
					String tmpId = TaggingEntryUtil.removeParam(entry.id);
					if (!StringUtils.isBlank(tmpId)) {
						entryUris.addAll(TaggingEntryUtil.getParentPathUris(
								TaggingEntryUtil.getUriById(entry.id)));
					}
				}
			}
		}
		if (isNumbering) {
			entryUris.addAll(TaggingEntryUtil.getParentPathUris(parentUri));
		}
		return entryUris;
	}

	/**
	 * 更新のための検索をまとめて最初に行う.
	 * 最低限の検索のみ。
	 * @param entryUris 更新時に読むURIリスト
	 * @param originalServiceName 元のサービス名
	 * @param auth 認証情報(更新対象サービス)
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	private void prepareUpdate(Set<String> entryUris, String originalServiceName,
			ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		getEntries(new ArrayList<>(entryUris), true, originalServiceName,
				auth, requestInfo, connectionInfo);
	}

	/**
	 * Entry取得.
	 * @param uri URI
	 * @param useCache データストアキャッシュを検索する場合true
	 * @param originalServiceName 元のサービス名
	 * @param auth 認証情報(検索対象サービス)
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return Entry
	 */
	public EntryBase getEntry(String uri, boolean useCache, String originalServiceName,
			ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		long startTime = new Date().getTime();
		if (logger.isTraceEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
					"[getEntry] start. uri=" + uri);
		}

		String serviceName = auth.getServiceName();

		BDBClientRetrieveManager retrieveManager = new BDBClientRetrieveManager();
		// 共有スレッドメモリキャッシュ
		Map<String, Value<EntryBase>> readEntryMap = BDBClientUtil.getEntryMap(
				serviceName, connectionInfo);
		Map<String, Value<EntryBase>> backupEntryMap = null;
		if (!useCache) {
			// 共有スレッドメモリキャッシュのバックアップ
			backupEntryMap = new HashMap<>();
			backupEntryMap.putAll(readEntryMap);
		} else {
			// キャッシュから参照モード
			// まず共有スレッドメモリキャッシュ参照
			if (readEntryMap.containsKey(uri)) {
				if (logger.isTraceEnabled()) {
					logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
							"[getEntry] find by readEntryMap : " + uri);
				}
				// キャッシュを複製して返却
				FeedTemplateMapper mapper = TaggingEnvUtil.getResourceMapper(serviceName);
				EntryBase cacheEntry = readEntryMap.get(uri).value;
				return TaggingEntryUtil.copyEntry(cacheEntry, mapper);
			}
		}

		// 共有スレッドメモリキャッシュを使用しない場合、一旦クリアする。
		if (!useCache) {
			readEntryMap.clear();
		}

		// リトライはトランザクション単位。リトライはキー自動採番メソッド内で組む。
		int numRetries = BDBRequesterUtil.getBDBRequestRetryCount();
		int waitMillis = BDBRequesterUtil.getBDBRequestRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				// 検索
				EntryBase retEntry = retrieveManager.getEntry(uri, useCache,
						auth, requestInfo, connectionInfo);
				if (isEnableAccessLog()) {
					StringBuilder sb = new StringBuilder();
					sb.append(LogUtil.getRequestInfoStr(requestInfo));
					sb.append("[getEntry] end. uri=");
					sb.append(uri);
					sb.append(LogUtil.getElapsedTimeLog(startTime));
					logger.debug(sb.toString());
				}
				return retEntry;

			} catch (IOException e) {
				// リトライ判定、入力エラー判定
				BDBClientUtil.convertError(e, Constants.GET, requestInfo);
				if (r >= numRetries) {
					// リトライ対象だがリトライ回数を超えた場合
					throw e;
				}
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) +
							"[getEntry] " + BDBClientUtil.getRetryLog(e, r));
				}
				BDBClientUtil.sleep(waitMillis + r * 10);
			} finally {
				if (!useCache) {
					// バックアップした共有メモリキャッシュ情報を戻す。
					// ただし途中で検索したものは共有メモリキャッシュに残しておく。
					for (Map.Entry<String, Value<EntryBase>> mapEntry : backupEntryMap.entrySet()) {
						String backupUri = mapEntry.getKey();
						if (!readEntryMap.containsKey(backupUri)) {
							readEntryMap.put(backupUri, mapEntry.getValue());
						}
					}
				}
			}
		}
		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}

	/**
	 * Entry複数取得.
	 * @param uri URI
	 * @param useCache データストアキャッシュを検索する場合true
	 * @param originalServiceName 元のサービス名
	 * @param auth 認証情報(検索対象サービス)
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return Entry
	 */
	public FeedBase getEntries(List<String> uris, boolean useCache,
			String originalServiceName, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		BDBClientRetrieveManager retrieveManager = new BDBClientRetrieveManager();
		int numRetries = BDBRequesterUtil.getBDBRequestRetryCount();
		int waitMillis = BDBRequesterUtil.getBDBRequestRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				List<EntryBase> entries = retrieveManager.getEntries(uris, useCache, auth,
						requestInfo, connectionInfo);
				if (entries == null || entries.isEmpty()) {
					return null;
				}
				FeedBase feed = TaggingEntryUtil.createFeed(serviceName);
				feed.entry = entries;
				// URIリストをFeedのlinkに設定
				List<Link> links = new ArrayList<>();
				for (String uri : uris) {
					Link link = new Link();
					link._$rel = Link.REL_SELF;
					link._$href = uri;
					links.add(link);
				}
				feed.link = links;

				return feed;

			} catch (IOException e) {
				// リトライ判定、入力エラー判定
				BDBClientUtil.convertError(e, Constants.GET, requestInfo);
				if (r >= numRetries) {
					// リトライ対象だがリトライ回数を超えた場合
					throw e;
				}
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) +
							"[getEntries] " + BDBClientUtil.getRetryLog(e, r));
				}
				BDBClientUtil.sleep(waitMillis + r * 10);
			}
		}

		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}

	/**
	 * ID指定で複数Entry取得.
	 * @param feed Feedのlink(rel="self"のtitle)にIDを指定
	 * @param originalServiceName 元のサービス名
	 * @param auth 認証情報(検索対象サービス)
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return Entryを格納したFeed
	 */
	public FeedBase getEntriesByIds(FeedBase feed, String originalServiceName,
			ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		BDBClientRetrieveManager retrieveManager = new BDBClientRetrieveManager();
		// リトライはトランザクション単位。リトライはキー自動採番メソッド内で組む。
		int numRetries = BDBRequesterUtil.getBDBRequestRetryCount();
		int waitMillis = BDBRequesterUtil.getBDBRequestRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				// 検索
				return retrieveManager.getEntriesByIds(feed,
						auth, requestInfo, connectionInfo);

			} catch (IOException e) {
				// リトライ判定、入力エラー判定
				BDBClientUtil.convertError(e, Constants.GET, requestInfo);
				if (r >= numRetries) {
					// リトライ対象だがリトライ回数を超えた場合
					throw e;
				}
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) +
							"[getEntriesByIdUris] " + BDBClientUtil.getRetryLog(e, r));
				}
				BDBClientUtil.sleep(waitMillis + r * 10);
			} finally {
			}
		}
		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}

	/**
	 * Feed検索.
	 * @param uri URI
	 * @param conditions 検索条件
	 *        外側のListはOR、内側のListはAND
	 * @param isUrlForwardMatch URI前方一致の場合true
	 * @param limit 最大取得件数
	 * @param cursorStr カーソル
	 * @param useCache データストアキャッシュを検索する場合true
	 * @param originalServiceName 元のサービス名
	 * @param auth 認証情報(検索対象サービス)
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return Feed データなしで続きのデータも無い場合はnull。
	 */
	public FeedBase getFeed(String uri, List<List<Condition>> conditions,
			boolean isUrlForwardMatch, int limit, String cursorStr, boolean useCache,
			String originalServiceName, ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		// メモリキャッシュ
		Map<String, Value<FeedBase>> readFeedMap = BDBClientUtil.getFeedMap(
				serviceName, connectionInfo);
		boolean useMemCache = isReadMapByGetFeed(uri, conditions, originalServiceName,
				serviceName);

		// 共有メモリキャッシュ読み込みの場合、メモリ参照
		// メモリ内にあるオブジェクトをそのまま渡すので注意。
		// (共有メモリキャッシュオプションは内部でしか使用しないようにする。)
		// (feedのメモリキャッシュは「/_user?title={xxx}」のみ)
		String cacheUri = BDBClientUtil.getFeedUriForCache(uri, isUrlForwardMatch, conditions,
				false);
		String cacheUriWithLimit = BDBClientUtil.addLimitToFeedUriForCache(cacheUri, limit,
				cursorStr);
		if (useMemCache && useCache && readFeedMap.containsKey(cacheUriWithLimit)) {
			if (isEnableAccessLog()) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
						"[getFeed] find by readEntryMap (feed) : " + uri);
			}
			Value<FeedBase> value = readFeedMap.get(cacheUriWithLimit);
			if (value != null) {
				return value.value;
			} else {
				return null;
			}
		}

		// 入力チェック
		checkConditions(uri, conditions, isUrlForwardMatch, serviceName);

		ReflexContext reflexContext = ReflexContextUtil.getReflexContext(auth,
				requestInfo, connectionInfo);

		// リトライはトランザクション単位。リトライはキー自動採番メソッド内で組む。
		int numRetries = BDBRequesterUtil.getBDBRequestRetryCount();
		int waitMillis = BDBRequesterUtil.getBDBRequestRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				// 末尾に"/"を指定する
				if (!isUrlForwardMatch) {
					uri = TaggingEntryUtil.editSlash(uri);
				}

				// 検索
				BDBClientRetrieveManager retrieveManager = new BDBClientRetrieveManager();
				FeedBase retFeed = retrieveManager.getFeed(uri,
						isUrlForwardMatch, conditions, limit, cursorStr, useCache,
						cacheUri, reflexContext);

				// データ無し、かつ続きのデータも無しの場合はnullを返却する。
				if (retFeed == null || ((retFeed.entry == null || retFeed.entry.size() == 0) &&
						StringUtils.isBlank(TaggingEntryUtil.getCursorFromFeed(retFeed)))) {
					retFeed = null;
				}

				// readFeedMapに検索したFeedを登録する。
				if (useMemCache) {
					readFeedMap.put(cacheUriWithLimit, new Value<FeedBase>(retFeed));
				}

				return retFeed;

			} catch (IOException e) {
				// リトライ判定、入力エラー判定
				BDBClientUtil.convertError(e, Constants.GET, requestInfo);
				if (r >= numRetries) {
					// リトライ対象だがリトライ回数を超えた場合
					throw e;
				}
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) +
							"[getFeed] " + BDBClientUtil.getRetryLog(e, r));
				}
				BDBClientUtil.sleep(waitMillis + r * 10);
			}
		}
		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}

	/**
	 * 件数取得.
	 * @param uri URI
	 * @param conditions 検索条件
	 *        外側のListはOR、内側のListはAND
	 * @param isUrlForwardMatch URI前方一致の場合true
	 * @param limit 最大件数
	 * @param cursorStr カーソル
	 * @param useCache データストアキャッシュを検索する場合true
	 * @param originalServiceName 元のサービス名
	 * @param auth 認証情報(検索対象サービス)
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return Feed titleに件数、件数計算が途中の場合はlink rel="next"のhrefにカーソル。
	 */
	public FeedBase getCount(String uri, List<List<Condition>> conditions,
			boolean isUrlForwardMatch, Integer limit, String cursorStr,
			boolean useCache, String originalServiceName, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		ReflexContext reflexContext = ReflexContextUtil.getReflexContext(auth,
				requestInfo, connectionInfo);

		// セッション格納キー取得.
		String cacheUri = BDBClientUtil.getFeedUriForCache(uri, isUrlForwardMatch, conditions, true);

		// 入力チェック
		checkConditions(uri, conditions, isUrlForwardMatch, serviceName);

		// リトライはトランザクション単位。リトライはキー自動採番メソッド内で組む。
		int numRetries = BDBRequesterUtil.getBDBRequestRetryCount();
		int waitMillis = BDBRequesterUtil.getBDBRequestRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				// 末尾に"/"を指定する
				uri = TaggingEntryUtil.editSlash(uri);

				// 検索
				BDBClientRetrieveManager retrieveManager = new BDBClientRetrieveManager();
				FeedBase retFeed = retrieveManager.getCount(uri,
						isUrlForwardMatch, conditions, limit, cursorStr, useCache,
						cacheUri, reflexContext);

				return retFeed;

			} catch (IOException e) {
				// リトライ判定、入力エラー判定
				BDBClientUtil.convertError(e, Constants.GET, requestInfo);
				if (r >= numRetries) {
					// リトライ対象だがリトライ回数を超えた場合
					throw e;
				}
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) +
							"[getCount] " + BDBClientUtil.getRetryLog(e, r));
				}
				BDBClientUtil.sleep(waitMillis + r * 10);
			}
		}
		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}

	/**
	 * 自階層 + 上位階層のEntryリストを取得
	 * @param uri キー
	 * @param useCache キャッシュを使用する場合true (readEntryMapのキャッシュのみ)
	 * @param originalServiceName 元のサービス名
	 * @param auth 認証情報(検索対象サービス)
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 上位階層のEntryリスト
	 */
	public FeedBase getParentPathEntries(String uri, boolean useCache,
			String originalServiceName, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		BDBClientRetrieveManager retrieveManager = new BDBClientRetrieveManager();
		List<EntryBase> entries = retrieveManager.getParentPathEntries(uri, useCache,
				auth, requestInfo, connectionInfo);
		if (entries == null || entries.isEmpty()) {
			return null;
		}
		FeedBase feed = TaggingEntryUtil.createFeed(serviceName);
		feed.entry = entries;
		return feed;
	}

	/**
	 * Feed検索のメモリキャッシュ対象かどうか判定.
	 * メモリキャッシュ対象
	 *   ・/_user?title={アカウント} の検索結果
	 *   ・サービス初期設定にFeed検索して読み込む情報
	 * @param uri URI
	 * @param conditions 検索条件
	 * @param originalServiceName 元のサービス名
	 * @param serviceName 検索対象サービス名
	 * @return メモリキャッシュ対象の場合true
	 */
	private boolean isReadMapByGetFeed(String uri, List<List<Condition>> conditions,
			String originalServiceName, String serviceName) {
		if (!StringUtils.isBlank(uri)) {
			// ユーザ認証のためのFeed検索
			if (Constants.URI_USER.equals(uri) && conditions != null && conditions.size() == 1) {
				List<Condition> conditionList = conditions.get(0);
				if (conditionList != null && conditionList.size() == 1) {
					Condition condition = conditionList.get(0);
					if ("title".equals(condition.getProp()) &&
							Condition.EQUAL.equals(condition.getEquations())) {
						// /_user?title={アカウント}
						return true;
					}
				}
			}

			BDBClientInitMainThreadManager initMainThreadManager =
					new BDBClientInitMainThreadManager();
			List<Pattern> patternsFeedUri = initMainThreadManager.getPatternUserInfoFeedUris();
			for (Pattern pattern : patternsFeedUri) {
				Matcher matcher = pattern.matcher(uri);
				if (matcher.matches()) {
					return true;
				}
			}

			// リクエスト初期設定の読み込み情報
			List<SettingService> settingServiceList = TaggingEnvUtil.getSettingServiceList();
			// システム管理サービス分
			if (TaggingEnvUtil.getSystemService().equals(serviceName)) {
				for (SettingService settingService : settingServiceList) {
					List<String> settingUris = settingService.getSettingFeedUrisBySystem();
					if (settingUris != null && settingUris.contains(uri)) {
						if (logger.isTraceEnabled()) {
							logger.debug("[isReadMapByGetFeed] contains by settingService (system). uri=" + uri);
						}
						return true;
					}
				}
				// システム管理サービスの自サービス分
				for (SettingService settingService : settingServiceList) {
					List<String> settingUris = settingService.getSettingFeedUrisBySystem(originalServiceName);
					if (settingUris != null && settingUris.contains(uri)) {
						if (logger.isTraceEnabled()) {
							logger.debug("[isReadMapByGetFeed] contains by settingService. (service in system) uri=" + uri);
						}
						return true;
					}
				}
			}
			// 自サービス分
			for (SettingService settingService : settingServiceList) {
				List<String> settingUris = settingService.getSettingFeedUris();
				if (settingUris != null && settingUris.contains(uri)) {
					if (logger.isTraceEnabled()) {
						logger.debug("[isReadMapByGetFeed] contains by settingService. uri=" + uri);
					}
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * 削除でキーにエイリアスを指定した場合、エイリアスを除去して更新するため
	 * Entryを編集し、UPDATEフラグを返す。
	 * @param entry Entry
	 * @param currentEntry 現在のEntry
	 * @param myUri URI
	 * @param idUri ID URI
	 * @param updateManager DatastoreUpdateManager
	 * @param requestInfo リクエスト情報
	 * @return フラグ
	 */
	private OperationType editDeleteAlias(EntryBase entry, EntryBase currentEntry,
			String myUri, String idUri, BDBClientUpdateManager updateManager,
			RequestInfo requestInfo) {
		// idUri=myUriの場合削除、異なる場合エイリアスを除去して更新
		if (!myUri.equals(idUri)) {
			entry.link = currentEntry.link;
			updateManager.editRemoveAlias(entry, myUri, requestInfo);
			return OperationType.UPDATE;
		}
		return OperationType.DELETE;
	}

	/**
	 * データストアのアクセスログを出力するかどうか
	 * @return データストアのアクセスログを出力する場合true;
	 */
	private boolean isEnableAccessLog() {
		return BDBClientUtil.isEnableAccessLog() && logger.isDebugEnabled();
	}

	/**
	 * 条件入力チェック.
	 * @param uri URI
	 * @param allConditions 条件
	 * @param isUrlForwardMatch URL前方一致指定の場合true
	 * @param serviceName サービス名
	 */
	private void checkConditions(String uri, List<List<Condition>> allConditions,
			boolean isUrlForwardMatch, String serviceName) {
		if (allConditions == null) {
			return;
		}
		// 全文検索指定項目
		Map<String, Pattern> templateFullTextIndexMap =
				TaggingEnvUtil.getTemplateFullTextIndexMap(serviceName);
		// DISTKEY項目
		Map<String, Pattern> templateDistkeyMap =
				TaggingEnvUtil.getTemplateDistkeyMap(serviceName);
		List<String> distkeyItems = IndexUtil.getDistkeyItems(uri, templateDistkeyMap);

		for (List<Condition> conditions : allConditions) {
			boolean isFtCondition = false;	// 全文検索指定されているかどうか
			int sortCnt = 0;
			if (conditions != null) {
				for (Condition condition : conditions) {
					String equation = condition.getEquations();
					// 以下の条件以外はエラー
					// -eq-|-lt-|-le-|-gt-|-ge-|-ne-|-rg-|-fm-|-bm-|-ft-|-asc
					if (!Condition.EQUAL.equals(equation) &&
							!Condition.LESS_THAN.equals(equation) &&
							!Condition.LESS_THAN_OR_EQUAL.equals(equation) &&
							!Condition.GREATER_THAN.equals(equation) &&
							!Condition.GREATER_THAN_OR_EQUAL.equals(equation) &&
							!Condition.NOT_EQUAL.equals(equation) &&
							!Condition.REGEX.equals(equation) &&
							!Condition.FORWARD_MATCH.equals(equation) &&
							!Condition.BACKWARD_MATCH.equals(equation) &&
							!Condition.FULL_TEXT_SEARCH.equals(equation) &&
							!Condition.ASC.equals(equation)) {
						throw new IllegalParameterException("Invalid equation. " + condition.toString());
					}

					if (Condition.FULL_TEXT_SEARCH.equals(equation)) {
						// 全文検索指定されていない項目・URIであればエラー
						String prop = condition.getProp();
						if (templateFullTextIndexMap == null) {
							throw new IllegalParameterException("The item is not specified for full text search. " + prop);
						}
						Pattern pattern = templateFullTextIndexMap.get(prop);
						if (pattern == null) {
							throw new IllegalParameterException("The item is not specified for full text search. " + prop);
						}
						Matcher matcher = pattern.matcher(uri);
						if (!matcher.matches()) {
							throw new IllegalParameterException("The key is not specified for full text search. item=" + prop + ", key=" + uri);
						}

						isFtCondition = true;
					} else if (Condition.ASC.equals(equation) || Condition.DESC.equals(equation)) {
						sortCnt++;
						if (sortCnt > 1) {
							// ソートを複数指定している場合はエラー
							throw new IllegalParameterException("Only one sort condition can be specified.");
						}
					}
				}
			}

			if (isUrlForwardMatch) {
				// URI前方一致検索
				// 全文検索条件があればエラー
				if (isFtCondition) {
					throw new IllegalParameterException("Key forward match and full text search can not be specified at the same time.");
				}
			}
			if (isFtCondition && distkeyItems != null && !distkeyItems.isEmpty()) {
				// 全文検索かつDISTKEY対象キーの場合、DISTKEYが指定されていなければエラー
				boolean hasDistkey = false;
				for (Condition condition : conditions) {
					if (distkeyItems.contains(condition.getProp()) &&
							Condition.EQUAL.equals(condition.getEquations())) {
						hasDistkey = true;
						break;
					}
				}
				if (!hasDistkey) {
					throw new IllegalParameterException("Distkey is required in the case of the key and full text search.");
				}
			}
		}
	}

	/**
	 * 一括更新処理.
	 * 指定されたFeedを一定数に区切って非同期並列更新を行います。
	 * @param feed Feed
	 * @param parentUri 親URI。自動採番登録時に使用。
	 * @param updateAllIndex インデックスをすべて更新(データパッチ用)
	 * @param originalServiceName 元のサービス名
	 * @param auth 認証情報(更新対象サービス)
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public List<Future<List<UpdatedInfo>>> bulkPut(FeedBase feed, String parentUri, boolean async,
			boolean updateAllIndex, String originalServiceName, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		List<Future<List<UpdatedInfo>>> futures = new ArrayList<>();

		// 更新Entry最大数ごとに更新処理を行う。
		int limit = 1;	// 1件ずつ
		FeedBase tmpFeed = TaggingEntryUtil.createFeed(serviceName);
		tmpFeed.entry = new ArrayList<>();
		for (EntryBase entry : feed.entry) {
			tmpFeed.entry.add(entry);
			if (tmpFeed.entry.size() >= limit) {
				// 非同期処理
				BDBClientPutCallable callable = new BDBClientPutCallable(
						tmpFeed, parentUri, updateAllIndex, originalServiceName);
				Future<List<UpdatedInfo>> future = (Future<List<UpdatedInfo>>)TaskQueueUtil.addTask(
						callable, 0, auth, requestInfo, connectionInfo);
				futures.add(future);
				// クリア
				tmpFeed = TaggingEntryUtil.createFeed(serviceName);
				tmpFeed.entry = new ArrayList<>();
			}
		}
		if (!tmpFeed.entry.isEmpty()) {
			// 非同期処理
			BDBClientPutCallable callable = new BDBClientPutCallable(
					tmpFeed, parentUri, updateAllIndex, originalServiceName);
			Future<List<UpdatedInfo>> future = (Future<List<UpdatedInfo>>)TaskQueueUtil.addTask(
					callable, 0, auth, requestInfo, connectionInfo);
			futures.add(future);
		}

		// 同期処理の場合、非同期処理の終了を待つ。
		int waitMillis = TaggingEnvUtil.getSystemPropInt(
				BDBClientConst.BULKPUT_SYNC_WAITMILLIS,
				BDBClientConst.BULKPUT_SYNC_WAITMILLIS_DEFAULT);
		boolean isError = false;
		if (!async) {
			for (Future<List<UpdatedInfo>> future : futures) {
				while (!future.isDone()) {
					RetryUtil.sleep(waitMillis);
				}
				if (!isError) {
					try {
						List<UpdatedInfo> ret = future.get();
						if (ret == null || ret.isEmpty()) {
							isError = true;
						}
					} catch (ExecutionException e) {
						logger.warn("[bulkPut] ExecutionException: " + e.getMessage());
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
						logger.warn("[bulkPut] InterruptedException: " + e.getMessage());
						throw new IOException(e);
					}
				}
			}
		}
		return futures;
	}

	/**
	 * 一括更新処理.
	 * 指定されたFeedを一定数に区切って順番に更新します。非同期処理です。
	 * @param feed Feed
	 * @param parentUri 親URI。自動採番登録時に使用。
	 * @param async 非同期の場合true、同期の場合false
	 * @param updateAllIndex インデックスをすべて更新(データパッチ用)
	 * @param originalServiceName 元のサービス名
	 * @param auth 認証情報(更新対象サービス)
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return Future 同期処理の場合はnull
	 */
	public Future<List<UpdatedInfo>> bulkSerialPut(FeedBase feed, String parentUri, boolean async,
			boolean updateAllIndex, String originalServiceName, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		BDBClientBulkSerialPutCallable callable =
				new BDBClientBulkSerialPutCallable(feed, parentUri, updateAllIndex,
						originalServiceName);
		if (!async) {
			// 同期処理
			callable.putProc(auth, requestInfo, connectionInfo);
			// 同期処理の場合はnullを返す
			return null;
		} else {
			// 非同期処理
			return (Future<List<UpdatedInfo>>)TaskQueueUtil.addTask(callable, 0, auth, 
					requestInfo, connectionInfo);
		}
	}

	/**
	 * キャッシュのクリア.
	 * テンプレートの更新時に実行する。
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void clearCache(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo) {
		// メモリキャッシュ
		Map<String, Value<EntryBase>> readEntryMap = BDBClientUtil.getEntryMap(
				serviceName, connectionInfo);
		readEntryMap.clear();
		Map<String, Value<FeedBase>> readFeedMap = BDBClientUtil.getFeedMap(
				serviceName, connectionInfo);
		readFeedMap.clear();
	}

	/**
	 * サービス作成.
	 * データストアのサービス初期設定.
	 * BDBの接続先エントリーを作成する。
	 * @param newServiceName 新規サービス名
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void createservice(String newServiceName, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		BDBClientServiceManager serviceManager = new BDBClientServiceManager();
		serviceManager.createservice(newServiceName, auth, requestInfo, connectionInfo);
	}

	/**
	 * サービス作成失敗時のリセット処理.
	 * データストアの設定削除
	 * @param newServiceName 新規サービス名
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void resetCreateservice(String newServiceName, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		BDBClientServiceManager serviceManager = new BDBClientServiceManager();
		serviceManager.resetCreateservice(newServiceName, auth, requestInfo, connectionInfo);
	}

	/**
	 * サービスステータス変更に伴うBDBサーバ変更.
	 *  ・名前空間の変更
	 *  ・BDBサーバの割り当て直し
	 *  ・一部データの移行
	 * @param targetServiceName 対象サービス名
	 * @param newServiceStatus 新サービスステータス
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void changeServiceStatus(String targetServiceName, String newServiceStatus,
			ReflexContext reflexContext)
	throws IOException, TaggingException {
		BDBClientServiceManager serviceManager = new BDBClientServiceManager();
		serviceManager.changeServiceStatus(targetServiceName, newServiceStatus, reflexContext);
	}

	/**
	 * モニター.
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @return モニタリング結果
	 */
	public FeedBase monitor(ReflexRequest req, ReflexResponse resp)
	throws IOException, TaggingException {
		BDBClientMonitorManager monitorManager = new BDBClientMonitorManager();
		return monitorManager.monitor(req, resp);
	}

	/**
	 * インデックス更新
	 * @param indexFeed インデックス更新情報.
	 *                  link rel="self の href : 親キー
	 *                  title : 項目名 (任意)
	 *                  subtitle : インデックスサーバ名 (任意)
	 *                  category schema : DISTKEY項目名 (任意)
	 * @param isDelete 削除の場合true
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void putIndex(FeedBase indexFeed, boolean isDelete,
			ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// バッチジョブサーバにリクエスト
		SystemContext systemContext = new SystemContext(auth, requestInfo, connectionInfo);
		UpdateIndexRequester.requestUpdateIndex(indexFeed, isDelete, systemContext);
	}

	/**
	 * サーバ追加.
	 * @param targetServiceName 対象サービス名
	 * @param feed サーバ追加情報
	 * @param reflexContext システム管理サービスのReflexContext (ログイン情報含む)
	 */
	public void addServer(String targetServiceName, FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException {
		BDBClientMaintenanceManager serviceManager = new BDBClientMaintenanceManager();
		serviceManager.addServer(targetServiceName, feed, reflexContext);
	}

	/**
	 * サーバ削除.
	 * @param targetServiceName 対象サービス名
	 * @param feed サーバ削除情報
	 * @param reflexContext システム管理サービスのReflexContext (ログイン情報含む)
	 */
	public void removeServer(String targetServiceName, FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException {
		BDBClientMaintenanceManager serviceManager = new BDBClientMaintenanceManager();
		serviceManager.removeServer(targetServiceName, feed, reflexContext);
	}

	/**
	 * サービスの設定.
	 * BDBサーバ情報を取得する
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	@Override
	public void settingService(String serviceName, RequestInfo requestInfo, ConnectionInfo connectionInfo)
			throws IOException, TaggingException {
		// BDBサーバURL、サーバ名の取得処理
		BDBClientServerManager serverManager = new BDBClientServerManager();
		serverManager.settingService(serviceName, requestInfo, connectionInfo);
	}

	/**
	 * サービス情報クローズ.
	 * static領域にある指定されたサービスの情報を削除する。
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	@Override
	public void closeService(String serviceName, RequestInfo requestInfo, ConnectionInfo connectionInfo)
			throws IOException, TaggingException {
		BDBClientServerManager serverManager = new BDBClientServerManager();
		serverManager.closeService(serviceName, requestInfo, connectionInfo);
	}

	/**
	 * サービス初期設定に必要なURIリスト.
	 * サービス初期設定に必要なシステム管理サービスのURIリストを返却する。
	 * @param serviceName サービス名
	 * @return URIリスト
	 */
	@Override
	public List<String> getSettingEntryUrisBySystem(String serviceName) {
		BDBClientServerManager serverManager = new BDBClientServerManager();
		return serverManager.getSettingEntryUrisBySystem(serviceName);
	}

	/**
	 * サービス初期設定に必要なFeed検索用URIリスト.
	 * サービス初期設定に必要なシステム管理サービスのFeed検索用URIリストを返却する。
	 * @param serviceName サービス名
	 * @return URIリスト
	 */
	@Override
	public List<String> getSettingFeedUrisBySystem(String serviceName) {
		BDBClientServerManager serverManager = new BDBClientServerManager();
		return serverManager.getSettingFeedUrisBySystem(serviceName);
	}

	/**
	 * サービス初期設定に共通して必要なURIリスト.
	 * サービス初期設定に共通して必要なシステム管理サービスのURIリストを返却する。
	 * @return URIリスト
	 */
	@Override
	public List<String> getSettingEntryUrisBySystem() {
		BDBClientServerManager serverManager = new BDBClientServerManager();
		return serverManager.getSettingEntryUrisBySystem();
	}

	/**
	 * サービス初期設定に共通して必要なURIリスト.
	 * サービス初期設定に共通して必要なシステム管理サービスのFeed検索用URIリストを返却する。
	 * @return URIリスト
	 */
	@Override
	public List<String> getSettingFeedUrisBySystem() {
		BDBClientServerManager serverManager = new BDBClientServerManager();
		return serverManager.getSettingFeedUrisBySystem();
	}

	/**
	 * サービス初期設定に共通して必要なURIリスト.
	 * サービス初期設定に共通して必要な自サービスのURIリストを返却する。
	 * @return URIリスト
	 */
	@Override
	public List<String> getSettingEntryUris() {
		BDBClientServerManager serverManager = new BDBClientServerManager();
		return serverManager.getSettingEntryUris();
	}

	/**
	 * サービス初期設定に共通して必要なURIリスト.
	 * サービス初期設定に共通して必要な自サービスのFeed検索用URIリストを返却する。
	 * @return URIリスト
	 */
	@Override
	public List<String> getSettingFeedUris() {
		BDBClientServerManager serverManager = new BDBClientServerManager();
		return serverManager.getSettingFeedUris();
	}

	/**
	 * リクエスト・メインスレッド初期処理.
	 * @param servieName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	@Override
	public void initMainThread(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		BDBClientInitMainThreadManager initMainThreadManager = new BDBClientInitMainThreadManager();
		initMainThreadManager.initMainThread(serviceName, requestInfo, connectionInfo);
	}

	/**
	 * リクエスト・メインスレッド初期処理の事前準備.
	 * サービスステータス判定のため、キャッシュにEntryリストがある場合のみ取得。
	 * @param servieName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	@Override
	public void initMainThreadPreparation(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		BDBClientInitMainThreadManager initMainThreadManager = new BDBClientInitMainThreadManager();
		initMainThreadManager.initMainThreadPreparation(serviceName, requestInfo, connectionInfo);
	}

	/**
	 * 名前空間設定処理の後のサービス初期設定.
	 * @param servieName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	@Override
	public void settingServiceAfterNamespace(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		BDBClientInitMainThreadManager initMainThreadManager = new BDBClientInitMainThreadManager();
		initMainThreadManager.initMainThreadByService(serviceName, requestInfo, connectionInfo);
	}

	/**
	 * リクエスト・メインスレッドのユーザ情報初期処理.
	 * @param uid UID
	 * @param servieName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	@Override
	public void initMainThreadUser(String uid, String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		BDBClientInitMainThreadManager initMainThreadManager = new BDBClientInitMainThreadManager();
		initMainThreadManager.initMainThreadUser(uid, serviceName, requestInfo, connectionInfo);
	}
}
