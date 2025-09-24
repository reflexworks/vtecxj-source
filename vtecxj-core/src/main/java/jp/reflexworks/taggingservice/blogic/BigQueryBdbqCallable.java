package jp.reflexworks.taggingservice.blogic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.Category;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.entry.Link;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.BigQueryManager;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;

/**
 * BDB-BigQuery登録処理のうちの、BigQuery非同期登録処理.
 */
public class BigQueryBdbqCallable extends ReflexCallable<Boolean> {

	/** BigQuery登録データ */
	private FeedBase feed;
	/** BigQuery削除キーリスト */
	private String[] uris;
	/** エンティティの第一階層と異なる名前をテーブル名にする場合に指定 */
	private Map<String, String> tableNames;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 登録時のコンストラクタ
	 * @param feed BigQuery登録データ
	 * @param tableNames エンティティの第一階層と異なる名前をテーブル名にする場合に指定
	 */
	public BigQueryBdbqCallable(FeedBase feed, Map<String, String> tableNames) {
		this.feed = feed;
		this.tableNames = tableNames;
	}

	/**
	 * 削除時のコンストラクタ
	 * @param uris BigQuery登録データ
	 * @param tableNames エンティティの第一階層と異なる名前をテーブル名にする場合に指定
	 * @param isDelete BigQuery削除処理の場合true
	 */
	public BigQueryBdbqCallable(String[] uris, Map<String, String> tableNames) {
		this.uris = uris;
		this.tableNames = tableNames;
	}

	/**
	 * BDB-BigQuery登録処理のうちの、BigQuery非同期登録処理.
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		ReflexAuthentication auth = getAuth();
		RequestInfo requestInfo = getRequestInfo();
		ConnectionInfo connectionInfo = getConnectionInfo();
		String type = null;
		if (feed != null) {
			type = "postBq";
		} else {
			type = "deleteBq";
		}
		if (logger.isDebugEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[bdbq call] start. type=" + type);
		}

		try {
			BigQueryManager manager = TaggingEnvUtil.getBigQueryManager();
			if (manager == null) {
				throw new InvalidServiceSettingException("BigQuery manager is nothing.");
			}
			if (feed != null) {
				manager.postBq(feed, tableNames, false, false, 
						auth, requestInfo, connectionInfo);
			} else {
				manager.deleteBq(uris, tableNames, false, auth, requestInfo, connectionInfo);
			}
	
			if (logger.isDebugEnabled()) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[bdbq call] end. type=" + type);
			}
		} catch (IOException | TaggingException | RuntimeException | Error e) {
			try {
				// BigQueryに登録失敗した情報をBDBに書き込む。
				postRetryBdbqEntry();
				
			} catch (IOException | RuntimeException | Error pe) {	// TaggingExceptionは再登録の対象外
				StringBuilder sb = new StringBuilder();
				sb.append("[call] Error occured. ");
				sb.append(pe.getClass().getName());
				sb.append(": ");
				sb.append(pe.getMessage());
				logger.warn(sb.toString(), pe);
			}
			// エラースロー → 非同期処理の上記クラスでログエントリーにエラー情報を書き込む。
			throw e;
		}

		return true;
	}
	
	// BigQueryへの登録または削除処理でエラーが発生した場合、
	// キー(id)とtableNamesをBDBに登録しておく。
	// BDBQエラーエントリー
	//   * キー: /_bdbq/{自動採番}
	//   * link 
	//       _$hrefにキー
	//       _$typeに登録/削除区分
	//   * categoryにtableNamesリスト
	//       _$scheme: スキーマ第一階層名
	//       _$label: テーブル名
	
	/**
	 * BDBQリトライデータ登録
	 */
	private void postRetryBdbqEntry() 
	throws IOException, TaggingException {
		ReflexContext reflexContext = (ReflexContext)getReflexContext();
		EntryBase bdbqEntry = createRetryBdbqEntry();
		reflexContext.post(bdbqEntry);
	}
	
	/**
	 * BDBQリトライEntryを生成
	 * @return BDBQリトライEntry
	 */
	private EntryBase createRetryBdbqEntry() 
	throws IOException, TaggingException {
		ReflexContext reflexContext = (ReflexContext)getReflexContext();
		String serviceName = getServiceName();
		FeedBase addidsFeed = reflexContext.addids(BigQueryBdbqConst.URI_BDBQ, 1);
		if (logger.isDebugEnabled()) {
			logger.debug("[createBigQueryInfoEntry] addids = " + addidsFeed.title);
		}
		String uri = createBdbqUri(addidsFeed.title);
		EntryBase bdbqEntry = TaggingEntryUtil.createEntry(serviceName);
		List<Link> links = new ArrayList<>();
		bdbqEntry.link = links;
		if (feed != null && feed.entry != null) {
			for (EntryBase tmpEntry : feed.entry) {
				Link link = new Link();
				link._$rel = Link.REL_VIA;
				link._$href = tmpEntry.getMyUri();
				link._$type = BigQueryBdbqConst.TYPE_INSERT;
				links.add(link);
			}
		}
		if (uris != null) {
			for (String tmpUri : uris) {
				Link link = new Link();
				link._$rel = Link.REL_VIA;
				link._$href = tmpUri;
				link._$type = BigQueryBdbqConst.TYPE_DELETE;
				links.add(link);
			}
		}
		if (tableNames != null && !tableNames.isEmpty()) {
			List<Category> categories = new ArrayList<>();
			bdbqEntry.category = categories;
			for (Map.Entry<String, String> mapEntry : tableNames.entrySet()) {
				Category category = new Category();
				category._$scheme = mapEntry.getKey();
				category._$label = mapEntry.getValue();
				categories.add(category);
			}
		}
		bdbqEntry.setMyUri(uri);
		return bdbqEntry;
	}
	
	/**
	 * BDBQリトライデータのキーを生成
	 * @param addids 連番
	 * @return BDBQリトライデータのキー
	 */
	private String createBdbqUri(String addids) {
		StringBuilder sb = new StringBuilder();
		sb.append(BigQueryBdbqConst.URI_BDBQ);
		sb.append("/");
		sb.append(addids);
		return sb.toString();
	}

}
