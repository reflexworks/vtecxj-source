package jp.reflexworks.taggingservice.context;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.taggingservice.api.BaseReflexContext;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestType;
import jp.reflexworks.taggingservice.batch.ReflexBDBCleanCallable;
import jp.reflexworks.taggingservice.bdb.AllocidsConst;
import jp.reflexworks.taggingservice.bdb.AllocidsManager;
import jp.reflexworks.taggingservice.bdb.IncrementManager;
import jp.reflexworks.taggingservice.conn.ReflexBDBConnectionInfo;
import jp.reflexworks.taggingservice.env.BDBEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.AllocidsRequestParam;
import jp.reflexworks.taggingservice.model.FetchInfo;
import jp.reflexworks.taggingservice.model.ReflexBDBRequestInfo;
import jp.reflexworks.taggingservice.taskqueue.AllocidsTaskQueueUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.ReflexBDBBackupUtil;
import jp.reflexworks.taggingservice.util.ReflexCheckUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * マニフェストコンテキスト.
 */
public class AllocidsContext implements BaseReflexContext {

	/** サービス名 */
	private String serviceName;
	/** 名前空間 */
	private String namespace;
	/** リクエスト情報 */
	private ReflexBDBRequestInfo requestInfo;
	/** コネクション情報 */
	private ReflexBDBConnectionInfo connectionInfo;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ.
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public AllocidsContext(String serviceName, String namespace,
			ReflexBDBRequestInfo requestInfo,
			ReflexBDBConnectionInfo connectionInfo) {
		this.serviceName = serviceName;
		this.namespace = namespace;
		this.requestInfo = requestInfo;
		this.connectionInfo = connectionInfo;
	}

	/**
	 * 採番.
	 * @param param URIと採番数
	 * @return Feed titleに採番値。採番数が複数の場合はカンマでつなぐ。
	 */
	public FeedBase allocids(AllocidsRequestParam param)
	throws IOException, TaggingException {
		// GET /b{キー}?_allocids={採番数}
		String uri = param.getUri();
		String numStr = param.getOption(RequestType.PARAM_ALLOCIDS);
		// 入力チェック
		ReflexCheckUtil.checkUri(uri);
		ReflexCheckUtil.checkNotNull(numStr, "allocids number");
		ReflexCheckUtil.checkInt(numStr);
		int num = Integer.parseInt(numStr);

		AllocidsManager bdbManager = new AllocidsManager();
		String retAllocids = bdbManager.allocids(namespace, uri, num, serviceName,
				requestInfo, connectionInfo);
		FeedBase retFeed = TaggingEntryUtil.createAtomFeed();
		retFeed.title = retAllocids;
		return retFeed;
	}

	/**
	 * インクリメントの現在値取得.
	 * @param uri URI
	 * @return Feed 現在の加算数をtitleに設定。
	 */
	public FeedBase getids(String uri)
	throws IOException, TaggingException {
		// GET /b{キー}?_getids
		// 入力チェック
		ReflexCheckUtil.checkUri(uri);

		IncrementManager bdbManager = new IncrementManager();
		long ret = bdbManager.getids(namespace, uri, serviceName,
				requestInfo, connectionInfo);
		FeedBase retFeed = TaggingEntryUtil.createAtomFeed();
		retFeed.title = String.valueOf(ret);
		return retFeed;
	}

	/**
	 * 加算枠取得.
	 * @param uri URI
	 * @return Feed 加算枠をtitleに設定。
	 */
	public FeedBase getRangeids(String uri)
	throws IOException, TaggingException {
		// GET /b{キー}?_rangeids
		// 入力チェック
		ReflexCheckUtil.checkUri(uri);

		IncrementManager bdbManager = new IncrementManager();
		String ret = bdbManager.getRangeids(namespace, uri, serviceName,
				requestInfo, connectionInfo);
		FeedBase retFeed = TaggingEntryUtil.createAtomFeed();
		retFeed.title = ret;
		return retFeed;
	}

	/**
	 * インクリメント.
	 * @param param キーと加算数
	 * @return Feed titleに加算結果。
	 */
	public FeedBase addids(AllocidsRequestParam param)
	throws IOException, TaggingException {
		// PUT /b{キー}?_addids={加算数}
		String uri = param.getUri();
		String numStr = param.getOption(RequestType.PARAM_ADDIDS);
		// 入力チェック
		ReflexCheckUtil.checkUri(uri);
		ReflexCheckUtil.checkNotNull(numStr, "addids number");
		ReflexCheckUtil.checkInt(numStr);
		int num = Integer.parseInt(numStr);

		IncrementManager bdbManager = new IncrementManager();
		long ret = bdbManager.addids(namespace, uri, num, serviceName,
				requestInfo, connectionInfo);
		FeedBase retFeed = TaggingEntryUtil.createAtomFeed();
		retFeed.title = String.valueOf(ret);
		return retFeed;
	}

	/**
	 * 加算値設定.
	 * @param param キーと加算値
	 * @return Feed titleに採番値。採番数が複数の場合はカンマでつなぐ。
	 */
	public FeedBase setids(AllocidsRequestParam param)
	throws IOException, TaggingException {
		// PUT /b{キー}?_setids={値}
		String uri = param.getUri();
		String numStr = param.getOption(RequestType.PARAM_SETIDS);
		// 入力チェック
		ReflexCheckUtil.checkUri(uri);
		ReflexCheckUtil.checkNotNull(numStr, "setids number");
		ReflexCheckUtil.checkInt(numStr);
		int num = Integer.parseInt(numStr);

		IncrementManager bdbManager = new IncrementManager();
		bdbManager.setids(namespace, uri, num, serviceName,
				requestInfo, connectionInfo);
		FeedBase retFeed = TaggingEntryUtil.createAtomFeed();
		retFeed.title = numStr;
		return retFeed;
	}

	/**
	 * 加算枠設定.
	 * @param param キーと加算枠
	 * @return Feed titleに採番値。採番数が複数の場合はカンマでつなぐ。
	 */
	public FeedBase rangeids(AllocidsRequestParam param)
	throws IOException, TaggingException {
		// PUT /b{キー}?_rangeids={加算枠}
		String uri = param.getUri();
		String range = param.getOption(RequestType.PARAM_RANGEIDS);	// 加算枠の指定がない場合は削除
		// 入力チェック
		ReflexCheckUtil.checkUri(uri);
		ReflexCheckUtil.checkRangeIds(range);

		IncrementManager bdbManager = new IncrementManager();
		bdbManager.rangeids(namespace, uri, range, serviceName,
				requestInfo, connectionInfo);
		FeedBase retFeed = TaggingEntryUtil.createAtomFeed();
		retFeed.title = range;
		return retFeed;
	}

	/**
	 * テーブルリスト検索.
	 * @param serviceName サービス名
	 * @param param テーブル名
	 * @return テーブルリスト
	 */
	public FeedBase getList(AllocidsRequestParam param)
	throws IOException, TaggingException {
		AllocidsManager bdbManager = new AllocidsManager();
		FetchInfo<?> fetchInfo = bdbManager.getList(namespace, param,
				requestInfo, connectionInfo);

		boolean isCount = param.getOption(AllocidsRequestParam.PARAM_COUNT) != null;
		if (isCount) {
			return returnCount(fetchInfo);
		}
		if (fetchInfo == null ||
				((fetchInfo.getResult() == null || fetchInfo.getResult().isEmpty()) &&
				StringUtils.isBlank(fetchInfo.getPointerStr()))) {
			return null;
		}

		List<EntryBase> retEntries = new ArrayList<EntryBase>();
		Map<String, ?> result = fetchInfo.getResult();
		for (Map.Entry<String, ?> mapEntry : result.entrySet()) {
			// titleにキー、summaryに値を設定。
			EntryBase entry = TaggingEntryUtil.createAtomEntry();
			entry.title = mapEntry.getKey();
			entry.summary = mapEntry.getValue().toString();
			retEntries.add(entry);
		}

		FeedBase retFeed = TaggingEntryUtil.createAtomFeed();
		retFeed.entry = retEntries;

		// カーソル
		boolean setOption = false;
		if (!StringUtils.isBlank(fetchInfo.getPointerStr())) {
			TaggingEntryUtil.setCursorToFeed(fetchInfo.getPointerStr(), retFeed);
			setOption = true;
		}
		// フェッチ制限超えの場合印を付加
		if (fetchInfo.isFetchExceeded()) {
			retFeed.rights = Constants.MARK_FETCH_LIMIT;
			setOption = true;
		}
		if (!setOption && retEntries.isEmpty()) {
			// データが何もない場合はnullで返す
			return null;
		}
		return retFeed;
	}

	/**
	 * 件数返却
	 * @param fetchInfo 検索結果
	 * @return 件数 (Feedのtitleに設定)
	 */
	private FeedBase returnCount(FetchInfo<?> fetchInfo) {
		FeedBase retFeed = TaggingEntryUtil.createAtomFeed();
		int cnt = 0;
		if (fetchInfo != null) {
			Map<String, Object> result = (Map<String, Object>)fetchInfo.getResult();
			if (result != null) {
				cnt = result.size();
			}
			// カーソル
			if (!StringUtils.isBlank(fetchInfo.getPointerStr())) {
				TaggingEntryUtil.setCursorToFeed(fetchInfo.getPointerStr(), retFeed);
			}
			// フェッチ制限超えの場合印を付加
			if (fetchInfo.isFetchExceeded()) {
				retFeed.rights = Constants.MARK_FETCH_LIMIT;
			}
		}
		retFeed.title = String.valueOf(cnt);
		return retFeed;
	}

	/**
	 * BDB環境統計情報取得.
	 * @param param テーブル名
	 * @return BDB環境統計情報 (Feedのsubtitleに設定)
	 */
	public FeedBase getStats(AllocidsRequestParam param)
	throws IOException, TaggingException {
		AllocidsManager bdbManager = new AllocidsManager();
		return bdbManager.getStats(namespace, param,
				requestInfo, connectionInfo);
	}

	/**
	 * ディスク使用率を取得.
	 * @param param テーブル名
	 * @return ディスク使用率(%) (Feedのsubtitleに設定)
	 */
	public FeedBase getDiskUsage(AllocidsRequestParam param)
	throws IOException, TaggingException {
		AllocidsManager bdbManager = new AllocidsManager();
		return bdbManager.getDiskUsage(param, requestInfo, connectionInfo);
	}

	/**
	 * BDBクリーン.
	 * 注) この処理は指定されていない名前空間を全て削除するため、実行には注意が必要。
	 * @param param パラメータ
	 */
	public void cleanBDB(AllocidsRequestParam param)
	throws IOException, TaggingException {
		ReflexBDBCleanCallable callable = new ReflexBDBCleanCallable(
				AllocidsConst.DB_NAMES,
				serviceName, namespace, requestInfo);
		AllocidsTaskQueueUtil.addTask(callable, 0,
				serviceName, namespace, requestInfo);
	}

	/**
	 * BDB環境クローズ
	 */
	public void closeBDBEnv()
	throws IOException, TaggingException {
		AllocidsManager bdbManager = new AllocidsManager();
		bdbManager.closeBDBEnv(namespace, requestInfo, connectionInfo);
	}

	/**
	 * リクエスト情報を取得.
	 * @return リクエスト情報
	 */
	public ReflexBDBRequestInfo getRequestInfo() {
		return requestInfo;
	}

	/**
	 * コネクション情報を取得.
	 * @return コネクション情報
	 */
	public ReflexBDBConnectionInfo getConnectionInfo() {
		return connectionInfo;
	}

	/**
	 * 名前空間を取得.
	 * @return 名前空間
	 */
	public String getNamespace() {
		return namespace;
	}

	@Override
	public byte[] getHtmlContent(String requestUri) {
		throw new IllegalStateException("Invalid method.");	// 
	}

	@Override
	public ReflexAuthentication getAuth() {
		throw new IllegalStateException("Invalid method.");	// 
	}

	/**
	 * サービス名を取得.
	 * @return サービス名
	 */
	@Override
	public String getServiceName() {
		return serviceName;
	}

	/**
	 * ResourceMapperを取得.
	 * Atom標準ResourceMapperを返す。
	 * @return ResourceMapper
	 */
	@Override
	public FeedTemplateMapper getResourceMapper() {
		return BDBEnvUtil.getAtomResourceMapper();
	}

	@Override
	public EntryBase getEntry(String uri) throws IOException, TaggingException {
		throw new IllegalStateException("Invalid method.");	// 
	}

	@Override
	public EntryBase getEntry(String uri, boolean useCache) throws IOException, TaggingException {
		throw new IllegalStateException("Invalid method.");	// 
	}

	/**
	 * BDBバックアップ.
	 * クリーン処理後、バックアップ処理を行う
	 * @param storageUrl Cloud Storage格納先URL
	 */
	public void backupBDB(String storageUrl)
	throws IOException, TaggingException {
		// BDBクリーン、バックアップ
		ReflexBDBBackupUtil.backup(namespace, storageUrl, serviceName, requestInfo);
	}

}
