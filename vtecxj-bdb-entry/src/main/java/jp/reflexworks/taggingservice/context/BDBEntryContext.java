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
import jp.reflexworks.taggingservice.batch.ReflexBDBCleanCallable;
import jp.reflexworks.taggingservice.bdb.BDBEntryConst;
import jp.reflexworks.taggingservice.bdb.BDBEntryManager;
import jp.reflexworks.taggingservice.conn.ReflexBDBConnectionInfo;
import jp.reflexworks.taggingservice.env.BDBEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.BDBEntryRequestParam;
import jp.reflexworks.taggingservice.model.FetchInfo;
import jp.reflexworks.taggingservice.model.ReflexBDBRequestInfo;
import jp.reflexworks.taggingservice.taskqueue.BDBEntryTaskQueueUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.ReflexBDBBackupUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * エントリーサーバコンテキスト.
 */
public class BDBEntryContext implements BaseReflexContext {

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
	public BDBEntryContext(String serviceName, String namespace,
			ReflexBDBRequestInfo requestInfo,
			ReflexBDBConnectionInfo connectionInfo) {
		this.serviceName = serviceName;
		this.namespace = namespace;
		this.requestInfo = requestInfo;
		this.connectionInfo = connectionInfo;
	}

	/**
	 * Entry登録更新
	 * @param id ID
	 * @param data Entryデータ
	 */
	public void put(String id, byte[] data)
	throws IOException, TaggingException {
		BDBEntryManager bdbManager = new BDBEntryManager();
		bdbManager.put(namespace, id, data, serviceName, requestInfo, connectionInfo);
	}

	/**
	 * Entry複数登録更新
	 * @param ids IDリスト
	 * @param dataList Entryデータリスト
	 */
	public void putMultiple(List<String> ids, List<byte[]> dataList)
	throws IOException, TaggingException {
		if (ids == null || ids.isEmpty()) {
			throw new IllegalParameterException("ID is required.");
		}
		if (dataList == null || dataList.isEmpty()) {
			throw new IllegalParameterException("Entry data is required.");
		}
		int idsSize = ids.size();
		int dataListSize = dataList.size();
		if (idsSize < dataListSize) {
			StringBuilder sb = new StringBuilder();
			sb.append("ID is required. idsSize=");
			sb.append(idsSize);
			sb.append(", dataListSize=");
			sb.append(dataListSize);
			throw new IllegalParameterException(sb.toString());
		}
		if (idsSize > dataListSize) {
			StringBuilder sb = new StringBuilder();
			sb.append("Entry data is required. idsSize=");
			sb.append(idsSize);
			sb.append(", dataListSize=");
			sb.append(dataListSize);
			throw new IllegalParameterException(sb.toString());
		}
		int i = 0;
		for (byte[] data : dataList) {
			String id = ids.get(i);
			i++;
			put(id, data);
		}
	}

	/**
	 * Entry削除
	 * @param id ID
	 */
	public void delete(String id)
	throws IOException, TaggingException {
		BDBEntryManager bdbManager = new BDBEntryManager();
		bdbManager.delete(namespace, id, serviceName, requestInfo, connectionInfo);
	}

	/**
	 * Entry複数削除
	 * @param ids IDリスト
	 */
	public void deleteMultiple(List<String> ids)
	throws IOException, TaggingException {
		if (ids == null || ids.isEmpty()) {
			throw new IllegalParameterException("ID is required.");
		}
		for (String id : ids) {
			delete(id);
		}
	}

	/**
	 * Entry取得
	 * @param id ID
	 * @return Entryバイト配列
	 */
	public byte[] get(String id)
	throws IOException, TaggingException {
		BDBEntryManager bdbManager = new BDBEntryManager();
		return bdbManager.get(namespace, id, serviceName, requestInfo, connectionInfo);
	}

	/**
	 * Entry複数取得
	 * @param ids IDリスト
	 * @return Entryバイト配列リスト
	 */
	public List<byte[]> getMultiple(List<String> ids)
	throws IOException, TaggingException {
		if (ids == null || ids.isEmpty()) {
			throw new IllegalParameterException("ID is required.");
		}
		List<byte[]> bytesList = new ArrayList<>();
		for (String id : ids) {
			byte[] entryBytes = get(id);
			bytesList.add(entryBytes);
		}
		return bytesList;
	}

	/**
	 * テーブルリスト検索.
	 * @param serviceName サービス名
	 * @param param テーブル名
	 * @return テーブルリスト
	 */
	public FeedBase getList(BDBEntryRequestParam param)
	throws IOException, TaggingException {
		BDBEntryManager bdbManager = new BDBEntryManager();
		FetchInfo<?> fetchInfo = bdbManager.getList(namespace, param,
				requestInfo, connectionInfo);

		boolean isCount = param.getOption(BDBEntryRequestParam.PARAM_COUNT) != null;
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
	public FeedBase getStats(BDBEntryRequestParam param)
	throws IOException, TaggingException {
		BDBEntryManager bdbManager = new BDBEntryManager();
		return bdbManager.getStats(namespace, param,
				requestInfo, connectionInfo);
	}

	/**
	 * ディスク使用率を取得.
	 * @param param テーブル名
	 * @return ディスク使用率(%) (Feedのsubtitleに設定)
	 */
	public FeedBase getDiskUsage(BDBEntryRequestParam param)
	throws IOException, TaggingException {
		BDBEntryManager bdbManager = new BDBEntryManager();
		return bdbManager.getDiskUsage(param, requestInfo, connectionInfo);
	}

	/**
	 * BDBクリーン.
	 * 注) この処理は指定されていない名前空間を全て削除するため、実行には注意が必要。
	 * @param param パラメータ
	 */
	public void cleanBDB(BDBEntryRequestParam param)
	throws IOException, TaggingException {
		ReflexBDBCleanCallable callable = new ReflexBDBCleanCallable(
				BDBEntryConst.DB_NAMES,
				serviceName, namespace, requestInfo);
		BDBEntryTaskQueueUtil.addTask(callable, 0,
				serviceName, namespace, requestInfo);
	}

	/**
	 * BDB環境クローズ
	 */
	public void closeBDBEnv()
	throws IOException, TaggingException {
		BDBEntryManager bdbManager = new BDBEntryManager();
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
