package jp.reflexworks.taggingservice.context;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.entry.Link;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.taggingservice.api.BaseReflexContext;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.batch.ReflexBDBCleanCallable;
import jp.reflexworks.taggingservice.bdb.InnerIndexBDBConst;
import jp.reflexworks.taggingservice.bdb.InnerIndexBDBManager;
import jp.reflexworks.taggingservice.conn.ReflexBDBConnectionInfo;
import jp.reflexworks.taggingservice.env.BDBEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.FetchInfo;
import jp.reflexworks.taggingservice.model.InnerIndexRequestParam;
import jp.reflexworks.taggingservice.model.ReflexBDBRequestInfo;
import jp.reflexworks.taggingservice.taskqueue.InnerIndexTaskQueueUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.ReflexBDBBackupUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * インデックスコンテキスト.
 */
public class InnerIndexContext implements BaseReflexContext {

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
	public InnerIndexContext(String serviceName, String namespace,
			ReflexBDBRequestInfo requestInfo,
			ReflexBDBConnectionInfo connectionInfo) {
		this.serviceName = serviceName;
		this.namespace = namespace;
		this.requestInfo = requestInfo;
		this.connectionInfo = connectionInfo;
	}

	/**
	 * Feed検索.
	 * @param serviceName サービス名
	 * @param param URIと検索条件
	 * @param distkeyItem DISTKEY項目
	 * @param distkeyValue DISTKEYの値
	 * @return Feed
	 */
	public FeedBase getFeed(InnerIndexRequestParam param,
			String distkeyItem, String distkeyValue)
	throws IOException, TaggingException {
		InnerIndexBDBManager bdbManager = new InnerIndexBDBManager();
		// ID一覧を取得
		FetchInfo<String> fetchInfo = bdbManager.getFeedKeys(namespace, param,
				distkeyItem, distkeyValue, this);
		if (fetchInfo == null ||
				((fetchInfo.getResult() == null || fetchInfo.getResult().isEmpty()) &&
				StringUtils.isBlank(fetchInfo.getPointerStr()))) {
			return null;
		}
		FeedBase retFeed = TaggingEntryUtil.createAtomFeed();
		List<Link> links = new ArrayList<Link>();
		retFeed.link = links;
		List<String> results = fetchInfo.getValues();
		if (results != null) {
			for (String id : results) {
				Link link = new Link();
				link._$rel = Link.REL_SELF;
				link._$title = id;
				links.add(link);
			}
		}

		// カーソル
		if (!StringUtils.isBlank(fetchInfo.getPointerStr())) {
			TaggingEntryUtil.setCursorToFeed(fetchInfo.getPointerStr(), retFeed);
		}
		// フェッチ制限超えの場合印を付加
		if (fetchInfo.isFetchExceeded()) {
			retFeed.rights = Constants.MARK_FETCH_LIMIT;
		}
		return retFeed;
	}

	/**
	 * 件数取得.
	 * @param serviceName サービス名
	 * @param param URIと検索条件
	 * @param distkeyItem DISTKEY項目
	 * @param distkeyValue DISTKEYの値
	 * @return 件数 (Feedのtitleに設定)
	 */
	public FeedBase getCount(InnerIndexRequestParam param,
			String distkeyItem, String distkeyValue)
	throws IOException, TaggingException {
		InnerIndexBDBManager bdbManager = new InnerIndexBDBManager();
		// ID一覧を取得
		FetchInfo<String> fetchInfo = bdbManager.getFeedKeys(namespace, param,
				distkeyItem, distkeyValue, this);
		return returnCount(fetchInfo);
	}

	/**
	 * テーブルリスト検索.
	 * @param serviceName サービス名
	 * @param param テーブル名
	 * @return テーブルリスト
	 */
	public FeedBase getList(InnerIndexRequestParam param)
	throws IOException, TaggingException {
		InnerIndexBDBManager bdbManager = new InnerIndexBDBManager();
		FetchInfo<?> fetchInfo = bdbManager.getList(namespace, param,
				requestInfo, connectionInfo);

		boolean isCount = param.getOption(InnerIndexRequestParam.PARAM_COUNT) != null;
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
	public FeedBase getStats(InnerIndexRequestParam param)
	throws IOException, TaggingException {
		InnerIndexBDBManager bdbManager = new InnerIndexBDBManager();
		return bdbManager.getStats(namespace, param,
				requestInfo, connectionInfo);
	}

	/**
	 * ディスク使用率を取得.
	 * @param param テーブル名
	 * @return ディスク使用率(%) (Feedのsubtitleに設定)
	 */
	public FeedBase getDiskUsage(InnerIndexRequestParam param)
	throws IOException, TaggingException {
		InnerIndexBDBManager bdbManager = new InnerIndexBDBManager();
		return bdbManager.getDiskUsage(param, requestInfo, connectionInfo);
	}

	/**
	 * 更新.
	 * @param feed Feed
	 * @param param リクエストパラメータ
	 */
	public void put(FeedBase feed, InnerIndexRequestParam param)
	throws IOException, TaggingException {
		InnerIndexBDBManager bdbManager = new InnerIndexBDBManager();
		boolean isPartial = param.getOptionBoolean(InnerIndexRequestParam.PARAM_PARTIAL);
		boolean isDelete = param.getOptionBoolean(InnerIndexRequestParam.PARAM_DELETE);
		bdbManager.put(namespace, feed, isPartial, isDelete, requestInfo, connectionInfo);
	}

	/**
	 * BDBクリーン.
	 * 注) この処理は指定されていない名前空間を全て削除するため、実行には注意が必要。
	 * @param param パラメータ
	 */
	public void cleanBDB(InnerIndexRequestParam param)
	throws IOException, TaggingException {
		ReflexBDBCleanCallable callable = new ReflexBDBCleanCallable(
				InnerIndexBDBConst.DB_NAMES,
				serviceName, namespace, requestInfo);
		InnerIndexTaskQueueUtil.addTask(callable, 0,
				serviceName, namespace, requestInfo);
	}

	/**
	 * BDB環境クローズ
	 */
	public void closeBDBEnv()
	throws IOException, TaggingException {
		InnerIndexBDBManager bdbManager = new InnerIndexBDBManager();
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
