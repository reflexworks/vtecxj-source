package jp.reflexworks.taggingservice.bdb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sleepycat.bind.EntryBinding;
import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.je.DatabaseException;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.entry.Link;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestType;
import jp.reflexworks.taggingservice.context.ManifestContext;
import jp.reflexworks.taggingservice.env.BDBEnvManager;
import jp.reflexworks.taggingservice.env.BDBEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.BDBCondition;
import jp.reflexworks.taggingservice.model.FetchInfo;
import jp.reflexworks.taggingservice.model.ManifestRequestParam;
import jp.reflexworks.taggingservice.util.IndexUtil;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.PointerUtil;
import jp.reflexworks.taggingservice.util.ReflexBDBLogUtil;
import jp.reflexworks.taggingservice.util.ReflexCheckUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * BDB操作クラス.
 * 実際の処理は更新、検索、加算、採番担当の各クラスが実装。
 */
public class ManifestManager {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * マニフェスト登録・更新
	 * @param namespace 名前空間
	 * @param feed Feed
	 * @param serviceName サービス名(ログ用)
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void put(String namespace, FeedBase feed, String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		if (feed == null) {
			throw new IllegalParameterException("Feed is required.");
		}
		if (feed.link == null || feed.link.isEmpty()) {
			throw new IllegalParameterException("Feed.link is required.");
		}

		// キー:ID、値:Manifestリスト
		Map<String, List<String>> manifestMap = new LinkedHashMap<>();
		for (Link link : feed.link) {
			// <link rel="self" href={キー} title={ID} />
			String id = link._$title;
			if (StringUtils.isBlank(id)) {
				throw new IllegalParameterException("Feed.link._$title is required.");
			}
			List<String> manifests = manifestMap.get(id);
			if (manifests == null) {
				manifests = new ArrayList<>();
				manifestMap.put(id, manifests);
			}
			// 削除の場合、キーの部分に_deleteを指定する。
			if (!RequestType.PARAM_DELETE.equals(link._$href)) {
				// キーのフォーマットチェック
				ReflexCheckUtil.checkUri(link._$href);
				String manifestUri = BDBUtil.getManifestUri(link._$href);
				manifests.add(manifestUri);
			}
		}

		updateManifest(namespace, manifestMap, serviceName, requestInfo, connectionInfo);
	}

	/**
	 * マニフェストを更新.
	 * @param namespace 名前空間
	 * @param idUri キー
	 * @param manifestMap マニフェストマップ　キー:ID、値:マニフェストリスト
	 * @param serviceName サービス名(ログ用)
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	private void updateManifest(String namespace, Map<String, List<String>> manifestMap,
			String serviceName, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		int numRetries = BDBEnvUtil.getBDBRetryCount();
		int waitMillis = BDBEnvUtil.getBDBRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			String id = null;
			try {
				// BDB環境情報取得
				BDBEnv bdbEnv = getBDBEnvByNamespace(namespace, true);

				BDBDatabase dbManifest = bdbEnv.getDb(ManifestConst.DB_MANIFEST);
				BDBDatabase dbManifestAncestor = bdbEnv.getDb(ManifestConst.DB_MANIFEST_ANCESTOR);

				// トランザクション開始
				BDBTransaction bdbTxn = bdbEnv.beginTransaction();
				try {
					for (Map.Entry<String, List<String>> mapEntry : manifestMap.entrySet()) {
						id = mapEntry.getKey();
						List<String> manifests = mapEntry.getValue();
						// マニフェストを更新
						updateManifestProc(bdbTxn, dbManifest, dbManifestAncestor,
								id, manifests, serviceName, requestInfo, connectionInfo);
					}

					// コミット
					bdbTxn.commit();
					bdbTxn = null;

				} finally {
					if (bdbTxn != null) {
						try {
							bdbTxn.abort();
						} catch (DatabaseException e) {
							logger.warn(LogUtil.getRequestInfoStr(requestInfo) +
									"[updateManifest] " + e.getClass().getName(), e);
						}
					}
				}
				return;

			} catch (DatabaseException e) {
				// リトライ判定、入力エラー判定
				BDBUtil.convertError(e, id, requestInfo);
				if (r >= numRetries) {
					// リトライ対象だがリトライ回数を超えた場合
					BDBUtil.convertIOError(e, id);
				}
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) +
							"[updateManifest] " + ReflexBDBLogUtil.getRetryLog(e, r));
				}
				BDBUtil.sleep(waitMillis + r * 10);
			}
		}
		throw new IllegalStateException("Unreachable code.");
	}

	/**
	 * マニフェスト情報を更新
	 * @param bdbTxn トランザクション
	 * @param db マニフェストテーブル
	 * @param dbAncestor マニフェストAncestor
	 * @param id ID
	 * @param manifests Manifestリスト (Entry削除の場合はリストが空)
	 * @param serviceName サービス名(ログ用)
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	private void updateManifestProc(BDBTransaction bdbTxn,
			BDBDatabase db, BDBDatabase dbAncestor, String id, List<String> manifests,
			String serviceName, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		BDBGet<List<String>> bdbGetAncestor = new BDBGet<>();
		BDBPut<String> bdbPutString = new BDBPut<>();
		BDBPut<List<String>> bdbPutAncestor = new BDBPut<>();
		BDBDelete bdbDelete = new BDBDelete();

		ListBinding listBinding = new ListBinding();
		StringBinding stringBinding = BDBUtil.getStringBinding();

		// 現在のManifestを取得
		String idUri = TaggingEntryUtil.getUriById(id);
		List<String> currentManifests = bdbGetAncestor.get(serviceName, bdbTxn, dbAncestor,
				listBinding, BDBUtil.getLockMode(), idUri, requestInfo, connectionInfo);
		boolean isDelete = manifests == null || manifests.isEmpty();

		// 今回のManifestを生成
		boolean isPutAncestor = false;
		List<String> newManifests = new ArrayList<>();
		if (!isDelete) {
			// 登録更新
			for (String manifest : manifests) {
				newManifests.add(manifest);
				// 登録
				bdbPutString.put(serviceName, bdbTxn, db, stringBinding, manifest, id,
						requestInfo, connectionInfo);
				if (currentManifests == null || !currentManifests.contains(manifest)) {
					if (!isPutAncestor) {
						isPutAncestor = true;
					}
				}
			}
		}

		// 除去されたマニフェストを削除
		if (currentManifests != null) {
			for (String currentIndex : currentManifests) {
				if (newManifests.contains(currentIndex)) {
					continue;
				}
				bdbDelete.delete(serviceName, bdbTxn, db, currentIndex,
						requestInfo, connectionInfo);
				if (!isPutAncestor) {
					isPutAncestor = true;
				}
			}
		}

		// Ancestorを上書き
		if (isPutAncestor) {
			if (isDelete) {
				// 削除
				bdbDelete.delete(serviceName, bdbTxn, dbAncestor, idUri, requestInfo,
						connectionInfo);
			} else {
				// 更新
				bdbPutAncestor.put(serviceName, bdbTxn, dbAncestor, listBinding, idUri,
						newManifests, requestInfo, connectionInfo);
			}
		}
	}

	/**
	 * Entry検索
	 * @param namespace 名前空間
	 * @param feed Feed <link rel="self" href={キー} />
	 * @param serviceName サービス名(ログ用)
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return IDリスト <link rel="self" href={キー} title={ID} />
	 */
	public FeedBase getEntryKeys(String namespace, FeedBase feed, String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		if (feed == null) {
			throw new IllegalParameterException("Feed is required.");
		}
		if (feed.link == null || feed.link.isEmpty()) {
			throw new IllegalParameterException("Feed.link is required.");
		}

		List<Link> retLinks = new ArrayList<>();
		for (Link link : feed.link) {
			// <link rel="self" href={キー} />
			String uri = link._$href;
			ReflexCheckUtil.checkUri(uri);
			String manifestUri = BDBUtil.getManifestUri(uri);
			String id = getProc(namespace, manifestUri, serviceName,
					requestInfo, connectionInfo);
			Link retLink = new Link();
			retLink._$rel = Link.REL_SELF;
			retLink._$href = uri;
			retLink._$title = id;
			retLinks.add(retLink);
		}

		FeedBase retFeed = TaggingEntryUtil.createAtomFeed();
		retFeed.link = retLinks;
		return retFeed;
	}

	private String getProc(String namespace, String manifestUri, String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		int numRetries = BDBEnvUtil.getBDBRetryCount();
		int waitMillis = BDBEnvUtil.getBDBRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				// BDB環境情報取得
				BDBEnv bdbEnv = getBDBEnvByNamespace(namespace, true);

				BDBDatabase dbManifest = bdbEnv.getDb(ManifestConst.DB_MANIFEST);
				StringBinding stringBinding = BDBUtil.getStringBinding();
				BDBGet<String> bdbGet = new BDBGet<>();
				return bdbGet.get(serviceName, null, dbManifest, stringBinding, 
						BDBUtil.getLockMode(), manifestUri, requestInfo, connectionInfo);

			} catch (DatabaseException e) {
				// リトライ判定、入力エラー判定
				BDBUtil.convertError(e, manifestUri, requestInfo);
				if (r >= numRetries) {
					// リトライ対象だがリトライ回数を超えた場合
					BDBUtil.convertIOError(e, manifestUri);
				}
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) +
							"[get] " + ReflexBDBLogUtil.getRetryLog(e, r));
				}
				BDBUtil.sleep(waitMillis + r * 10);
			}
		}
		throw new IllegalStateException("Unreachable code.");

	}

	/**
	 * 条件検索.
	 * キーリストを返却する。
	 * @param namespace 名前空間
	 * @param param 親URI、検索条件(URI前方一致かどうか)
	 * @param reflexContext ReflexContext
	 * @return Entryの ID URI リストと、続きがある場合はカーソルを返す。
	 */
	public FetchInfo<String> getFeedKeys(String namespace,
			ManifestRequestParam param,
			ManifestContext reflexContext)
	throws IOException, TaggingException {
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();

		BDBQuery<String> bdbQuery = new BDBQuery<String>();
		StringBinding binding = BDBUtil.getStringBinding();
		String uri = param.getUri();
		boolean isUriForwardMatch = param.isUrlForwardMatch();
		ReflexCheckUtil.checkUri(uri);

		int limit = getLimit(param.getOption(ManifestRequestParam.PARAM_LIMIT));

		String cursorStr = PointerUtil.decode(
				param.getOption(ManifestRequestParam.PARAM_NEXT));
		BDBCondition bdbCondition = createBDBCondition(namespace, uri,
				isUriForwardMatch, cursorStr, requestInfo, connectionInfo);

		int numRetries = BDBEnvUtil.getBDBRetryCount();
		int waitMillis = BDBEnvUtil.getBDBRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				BDBEnv bdbEnv = getBDBEnvByNamespace(
						namespace);
				if (bdbEnv == null) {
					// 環境が存在しない場合はデータなし
					return null;
				}
				BDBDatabase db = bdbEnv.getDb(ManifestConst.DB_MANIFEST);
				FetchInfo<String> fetchInfo = bdbQuery.getByQuery(namespace, null, db,
						binding, bdbCondition, limit, requestInfo);

				return fetchInfo;

			} catch (DatabaseException e) {
				// リトライ判定、入力エラー判定
				BDBUtil.convertError(e, uri, requestInfo);
				if (r >= numRetries) {
					// リトライ対象だがリトライ回数を超えた場合
					BDBUtil.convertIOError(e, uri);
				}
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) +
							"[getFeedKeys] " + ReflexBDBLogUtil.getRetryLog(e, r));
				}
				BDBUtil.sleep(waitMillis + r * 10);
			}
		}
		throw new IllegalStateException("Unreachable code.");
	}

	/**
	 * テーブルデータ全件取得.
	 * キーリストを返却する。
	 * @param namespace 名前空間
	 * @param param テーブル名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return テーブルデータ全件リスト
	 *         続きがある場合はカーソルを返す。(Feedのlink rel="next"のhrefに設定)
	 */
	public FetchInfo<?> getList(String namespace, ManifestRequestParam param,
			RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[getList] namespace=");
			sb.append(namespace);
			logger.trace(sb.toString());
		}

		BDBQuery bdbQuery = new BDBQuery();
		int limit = getLimit(param.getOption(ManifestRequestParam.PARAM_LIMIT));
		String pointerStr = PointerUtil.decode(
				param.getOption(ManifestRequestParam.PARAM_NEXT));

		int numRetries = BDBEnvUtil.getBDBRetryCount();
		int waitMillis = BDBEnvUtil.getBDBRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				// テーブル判定
				BDBDatabase db = null;
				EntryBinding<?> binding = null;
				BDBEnv bdbEnv = getBDBEnvByNamespace(namespace);
				if (bdbEnv == null) {
					return null;
				}
				String tableName = param.getOption(ManifestRequestParam.PARAM_LIST);
				if (ManifestConst.DB_MANIFEST.equals(tableName)) {
					db = bdbEnv.getDb(ManifestConst.DB_MANIFEST);
					binding = BDBUtil.getStringBinding();
				} else if (ManifestConst.DB_MANIFEST_ANCESTOR.equals(tableName)) {
					db = bdbEnv.getDb(ManifestConst.DB_MANIFEST_ANCESTOR);
					binding = BDBUtil.getListBinding();

				} else {
					throw new IllegalArgumentException("The specified table does not exist. " + tableName);
				}

				String keyprefix = param.getOption(ManifestRequestParam.PARAM_KEYPREFIX);
				String keyend = IndexUtil.getEndKeyStr(keyprefix);
				BDBCondition bdbCondition = new BDBCondition(keyprefix, keyend, pointerStr);

				FetchInfo<?> fetchInfo = bdbQuery.getByQuery(namespace, null, db, binding,
						bdbCondition, limit, requestInfo);
				return fetchInfo;

			} catch (DatabaseException e) {
				// リトライ判定、入力エラー判定
				BDBUtil.convertError(e, null, requestInfo);
				if (r >= numRetries) {
					// リトライ対象だがリトライ回数を超えた場合
					BDBUtil.convertIOError(e, null);
				}
				if (logger.isInfoEnabled()) {
					logger.info(LogUtil.getRequestInfoStr(requestInfo) +
							"[getList] " + ReflexBDBLogUtil.getRetryLog(e, r));
				}
				BDBUtil.sleep(waitMillis + r * 10);
			}
		}
		throw new IllegalStateException("Unreachable code.");
	}

	/**
	 * リクエストパラメータから指定件数を取得.
	 * @param limitStr lパラメータの値
	 * @return 指定件数
	 */
	private int getLimit(String limitStr) {
		int defLimit = BDBEnvUtil.getEntryNumberLimit();
		return StringUtils.intValue(limitStr, defLimit);
	}

	/**
	 * BDB環境統計情報取得.
	 * @param namespace 名前空間
	 * @param param URLパラメータ
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return BDB環境統計情報 (Feedのsubtitleに設定)
	 */
	public FeedBase getStats(String namespace, ManifestRequestParam param,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		BDBEnvManager bdbEnvManager = new BDBEnvManager();
		return bdbEnvManager.getStatsByNamespace(ManifestConst.DB_NAMES, namespace,
				requestInfo, connectionInfo);
	}

	/**
	 * ディスク使用率取得.
	 * @param param URLパラメータ
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return ディスク使用率(%) (Feedのsubtitleに設定)
	 */
	public FeedBase getDiskUsage(ManifestRequestParam param,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		BDBEnvManager bdbEnvManager = new BDBEnvManager();
		return bdbEnvManager.getDiskUsage(requestInfo, connectionInfo);
	}

	/**
	 * BDB環境クローズ処理.
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void closeBDBEnv(String namespace,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		BDBEnvManager bdbEnvManager = new BDBEnvManager();
		bdbEnvManager.closeBDBEnv(namespace);
	}

	/**
	 * BDB検索条件オブジェクトを生成
	 * @param namespace 名前空間
	 * @param uri 親URI
	 * @param isUriForwardMatch キー前方一致検索を行う場合true
	 * @param cursorStr カーソル
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return BDB検索条件オブジェクト
	 */
	private BDBCondition createBDBCondition(String namespace, String uri,
			boolean isUriForwardMatch, String cursorStr,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String startKeyStr = null;	// 開始キー
		if (!isUriForwardMatch) {
			// 通常の親キー指定
			startKeyStr = BDBUtil.getManifestParentUri(uri);
		} else {
			// URL前方一致指定
			if (uri.endsWith("/")) {
				startKeyStr = BDBUtil.getManifestParentUri(uri);
			} else {
				startKeyStr = BDBUtil.getManifestUri(uri);
			}
		}
		String endKeyStr = editMatchingEnd(startKeyStr);	// 終了キー
		boolean excludeStartKey = false;	// 開始キーを含まないかどうか (デフォルトfalse=含む)
		boolean excludeEndKey = false;	// 終了キーを含まないかどうか (デフォルトfalse=含む)

		return new BDBCondition(startKeyStr, endKeyStr, cursorStr,
				excludeStartKey, excludeEndKey);
	}

	/**
	 * 検索の終端キーを取得.
	 * @param keyStr 検索キー
	 * @return 検索の終端キー
	 */
	private String editMatchingEnd(String keyStr) {
		return StringUtils.null2blank(keyStr) + BDBConst.FOWARD_MATCHING_END;
	}

	/**
	 * 名前空間からBDB環境情報を取得.
	 * 参照用。指定された名前空間のBDB環境が存在しない時は新規作成せずエラーとする。
	 * @param namespace 名前空間
	 * @return BDB環境情報
	 */
	private BDBEnv getBDBEnvByNamespace(String namespace)
	throws IOException, TaggingException {
		return getBDBEnvByNamespace(namespace, false);
	}

	/**
	 * 名前空間からBDB環境情報を取得.
	 * @param namespace 名前空間
	 * @param isCreate 指定された名前空間のBDB環境が存在しない時作成する場合true
	 * @return BDB環境情報
	 */
	private BDBEnv getBDBEnvByNamespace(String namespace,
			boolean isCreate)
	throws IOException, TaggingException {
		boolean setAccesstime = false;	// 
		BDBEnvManager bdbEnvManager = new BDBEnvManager();
		return bdbEnvManager.getBDBEnvByNamespace(ManifestConst.DB_NAMES,
				namespace, isCreate, setAccesstime);
	}

}
