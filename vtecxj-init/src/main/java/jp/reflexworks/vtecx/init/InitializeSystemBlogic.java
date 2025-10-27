package jp.reflexworks.vtecx.init;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.api.AtomConst;
import jp.reflexworks.atom.entry.Contributor;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.servlet.util.AuthTokenUtil;
import jp.reflexworks.taggingservice.api.ReflexBlogic;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.SecretManager;
import jp.reflexworks.taggingservice.plugin.def.UserManagerDefaultConst;
import jp.reflexworks.taggingservice.service.TaggingServiceUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * 初期データ登録.
 */
public class InitializeSystemBlogic implements ReflexBlogic<ReflexContext, FeedBase> {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 実行処理.
	 * @param reflexContext ReflexContext
	 * @param args 引数 (ReflexApplication実行時の引数の4番目以降)
	 *             [0] production or staging
	 */
	public FeedBase exec(ReflexContext reflexContext, String[] args)
	throws IOException, TaggingException {
		String systemService = reflexContext.getServiceName();

		logger.info("[exec] start.");

		// Redisのクリア
		logger.info("[exec] cacheFlushAll start.");
		boolean retCacheFlushAll = reflexContext.cacheFlushAll();
		logger.info("[exec] cacheFlushAll end. ret=" + retCacheFlushAll);

		boolean isBaaS = TaggingServiceUtil.isBaaS();
		
		// サービスステータス
		String serviceStatus = null;
		if (isBaaS) {
			if (args.length > 0 && Constants.SERVICE_STATUS_PRODUCTION.equals(args[0])) {
				serviceStatus = Constants.SERVICE_STATUS_PRODUCTION;
			} else {
				serviceStatus = Constants.SERVICE_STATUS_STAGING;
			}
		} else {
			serviceStatus = Constants.SERVICE_STATUS_PRODUCTION;
		}

		// サービスEntryの登録
		FeedBase feed = TaggingEntryUtil.createFeed(systemService);

		// /_service/{サービス名}

		// ルートエントリー (/)
		feed.addEntry(createRootEntry());
		// システムフォルダ (/@)
		feed.addEntry(createSystemFolder());
		// サービスフォルダ (/_service)
		feed.addEntry(createServiceFolder());
		// サービスフォルダ (/_service/admin)
		feed.addEntry(createServiceSystem(systemService, serviceStatus));
		// 設定情報フォルダ
		feed.addEntry(createSettingsEntry());
		// adduser設定
		feed.addEntry(createAdduserEntry());
		// passreset設定
		feed.addEntry(createPassresetEntry());
		// メンテナンス通知のメール送信設定
		feed.addEntry(createMaintenanceNotice());
		// HTMLフォルダ
		feed.addEntry(createHtmlEntry());
		// ログフォルダ
		feed.addEntry(createLogEntry());
		// ログイン履歴フォルダ
		feed.addEntry(createLoginHistoryEntry());
		// ユーザフォルダ (/_user)
		feed.addEntry(createUserFolder());
		// 名前空間フォルダ (/_namespace)
		feed.addEntry(createNamespaceFolder());
		// バケットフォルダ (/_bucket)
		feed.addEntry(createBucketFolder());
		// システム管理サービスの名前空間
		feed.addEntry(createNamespaceSystemFolder(systemService));
		// BDBフォルダ (/_bdb)
		feed.addEntry(createBDBFolder());
		// BDBの各サービス設定フォルダ (/_bdb/service)
		feed.addEntry(createBDBServiceFolder());
		// BDBのstaging割り当てフォルダ (/_bdb/staging)
		feed.addEntry(createBDBStagingFolder());
		// Manifestサーバのstaging割り当てフォルダ (/_bdb/staging/mnfserver)
		feed.addEntry(createBDBStagingMnfFolder());
		// Entryサーバのstaging割り当てフォルダ (/_bdb/staging/entryserver)
		feed.addEntry(createBDBStagingEntryFolder());
		// インデックスサーバのstaging割り当てフォルダ (/_bdb/staging/idxserver)
		feed.addEntry(createBDBStagingIdxFolder());
		// 全文検索インデックスサーバのstaging割り当てフォルダ (/_bdb/staging/ftserver)
		feed.addEntry(createBDBStagingFtFolder());
		// 採番・カウンタサーバのstaging割り当てフォルダ (/_bdb/staging/alserver)
		feed.addEntry(createBDBStagingAlFolder());
		// BDBのproduction割り当てフォルダ (/_bdb/production)
		feed.addEntry(createBDBProductionFolder());
		// Manifestサーバのproduction割り当てフォルダ (/_bdb/production/mnfserver)
		feed.addEntry(createBDBProductionMnfFolder());
		// Entryサーバのproduction割り当てフォルダ (/_bdb/production/entryserver)
		feed.addEntry(createBDBProductionEntryFolder());
		// インデックスサーバのproduction割り当てフォルダ (/_bdb/production/idxserver)
		feed.addEntry(createBDBProductionIdxFolder());
		// 全文検索インデックスサーバのproduction割り当てフォルダ (/_bdb/production/ftserver)
		feed.addEntry(createBDBProductionFtFolder());
		// 採番・カウンタサーバのproduction割り当てフォルダ (/_bdb/production/alserver)
		feed.addEntry(createBDBProductionAlFolder());
		// BDBサーバ予約済みフォルダ (/_bdb/reservation)
		feed.addEntry(createBDBReservationFolder());

		// Manifestサーバフォルダ (/_bdb/mnfserver)
		feed.addEntry(createBDBMnfServerFolder());
		// Manifestサーバ (/_bdb/mnfserver/{サーバ名})
		feed.addEntries(createBDBMnfServers());
		// Entryサーバフォルダ (/_bdb/entryserver)
		feed.addEntry(createBDBEntryServerFolder());
		// Entryサーバ (/_bdb/entryserver/{サーバ名})
		feed.addEntries(createBDBEntryServers());
		// Indexサーバフォルダ (/_bdb/idxserver)
		feed.addEntry(createBDBIdxServerFolder());
		// Indexサーバ (/_bdb/idxserver/{サーバ名})
		feed.addEntries(createBDBIdxServers());
		// 全文検索Indexサーバフォルダ (/_bdb/ftserver)
		feed.addEntry(createBDBFtServerFolder());
		// 全文検索Indexサーバ (/_bdb/ftserver/{サーバ名})
		feed.addEntries(createBDBFtServers());
		// 採番・カウンタサーバフォルダ (/_bdb/alserver)
		feed.addEntry(createBDBAlServerFolder());
		// 採番・カウンタサーバ (/_bdb/alserver/{サーバ名})
		feed.addEntries(createBDBAlServers());

		// システム管理サービスのプロパティ設定 (/_settings/properties)
		feed.addEntry(createSettingsProperties());

		FeedBase retFeed = null;

		logger.info("[exec] post start.");

		retFeed = reflexContext.post(feed);

		logger.info("[exec] post end.");

		// UIDの初期設定
		retFeed = reflexContext.rangeids(UserManagerDefaultConst.URI_ADDIDS_UID,
				InitializeConst.UID_START_INIT);

		// 管理ユーザ登録
		FeedBase adduserFeed = createAdduserAdminFeed();
		retFeed = reflexContext.adduserByAdmin(adduserFeed);

		// 管理ユーザのUIDを取得 (/_user/の配下)
		String uid = retFeed.entry.get(0).getMyUri().substring(7);
		//logger.info("uid = " + uid);

		// 管理者権限を追加
		// グループフォルダ(/_group と各管理グループ)もこちらで登録する。
		List<EntryBase> adminuserEntries = createAdminuserEntries(uid, reflexContext);
		feed = TaggingEntryUtil.createFeed(systemService);
		feed.entry = adminuserEntries;
		retFeed = reflexContext.put(feed);

		return retFeed;
	}

	/**
	 * Root Entryを生成
	 * @return Entry
	 */
	private EntryBase createRootEntry() 
	throws IOException, TaggingException {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		entry.setMyUri("/");
		// システム管理サービスのAPIKey
		SecretManager secretManager = TaggingEnvUtil.getSecretManager();
		String secretNameApiKey = TaggingEnvUtil.getSystemProp(
				InitializeConst.SECRET_INIT_SYSTEMSERVICE_APIKEY_NAME, null);
		if (StringUtils.isBlank(secretNameApiKey)) {
			throw new IllegalArgumentException("apikey name setting is required.");
		}
		String apiKey = secretManager.getSecretKey(secretNameApiKey, null);
		if (StringUtils.isBlank(apiKey)) {
			throw new IllegalArgumentException("apikey setting is required.");
		}

		Contributor contributor = new Contributor();
		contributor.uri = Constants.URN_PREFIX_APIKEY + apiKey;
		entry.addContributor(contributor);

		// ACL
		Contributor aclContributor = TaggingEntryUtil.getAclContributor(
				Constants.URI_GROUP_ADMIN, Constants.ACL_TYPE_CRUD);
		entry.addContributor(aclContributor);

		return entry;
	}

	/**
	 * システムフォルダEntryを生成
	 * @return Entry
	 */
	private EntryBase createSystemFolder() {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		entry.setMyUri(Constants.URI_SYSTEM_MANAGER);
		return entry;
	}

	/**
	 * サービスフォルダEntryを生成
	 * @return Entry
	 */
	private EntryBase createServiceFolder() {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		entry.setMyUri(Constants.URI_SERVICE);
		return entry;
	}

	/**
	 * ユーザフォルダEntryを生成
	 * @return Entry
	 */
	private EntryBase createUserFolder() {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		entry.setMyUri(Constants.URI_USER);
		return entry;
	}

	/**
	 * 名前空間フォルダEntryを生成
	 * @return Entry
	 */
	private EntryBase createNamespaceFolder() {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		entry.setMyUri(Constants.URI_NAMESPACE);
		return entry;
	}

	/**
	 * 名前空間フォルダEntryを生成
	 * @param systemService システム管理サービス名
	 * @return Entry
	 */
	private EntryBase createNamespaceSystemFolder(String systemService) {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		StringBuilder sb = new StringBuilder();
		sb.append(Constants.URI_NAMESPACE);
		sb.append("/");
		sb.append(systemService);
		entry.setMyUri(sb.toString());
		// titleに名前空間
		entry.title = systemService;

		return entry;
	}

	/**
	 * BDBフォルダEntryを生成
	 * @return Entry
	 */
	private EntryBase createBDBFolder() {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		entry.setMyUri(InitializeConst.URI_BDB);
		return entry;
	}

	/**
	 * BDBのサービス設定フォルダEntryを生成
	 * @return Entry
	 */
	private EntryBase createBDBServiceFolder() {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		entry.setMyUri(InitializeConst.URI_BDB_SERVICE);
		return entry;
	}

	/**
	 * BDBのstaging割り当て設定フォルダEntryを生成
	 * @return Entry
	 */
	private EntryBase createBDBStagingFolder() {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		entry.setMyUri(InitializeConst.URI_BDB_STAGING);
		return entry;
	}

	/**
	 * Manifestサーバのstaging割り当て設定フォルダEntryを生成
	 * @return Entry
	 */
	private EntryBase createBDBStagingMnfFolder() {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		entry.setMyUri(InitializeConst.URI_BDB_STAGING + InitializeConst.URI_MNFSERVER);
		return entry;
	}

	/**
	 * Entryサーバのstaging割り当て設定フォルダEntryを生成
	 * @return Entry
	 */
	private EntryBase createBDBStagingEntryFolder() {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		entry.setMyUri(InitializeConst.URI_BDB_STAGING + InitializeConst.URI_ENTRYSERVER);
		return entry;
	}

	/**
	 * インデックスサーバのstaging割り当て設定フォルダEntryを生成
	 * @return Entry
	 */
	private EntryBase createBDBStagingIdxFolder() {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		entry.setMyUri(InitializeConst.URI_BDB_STAGING + InitializeConst.URI_IDXSERVER);
		return entry;
	}

	/**
	 * 全文検索インデックスサーバのstaging割り当て設定フォルダEntryを生成
	 * @return Entry
	 */
	private EntryBase createBDBStagingFtFolder() {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		entry.setMyUri(InitializeConst.URI_BDB_STAGING + InitializeConst.URI_FTSERVER);
		return entry;
	}

	/**
	 * 採番・カウンタサーバのstaging割り当て設定フォルダEntryを生成
	 * @return Entry
	 */
	private EntryBase createBDBStagingAlFolder() {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		entry.setMyUri(InitializeConst.URI_BDB_STAGING + InitializeConst.URI_ALSERVER);
		return entry;
	}

	/**
	 * BDBのproduction割り当て設定フォルダEntryを生成
	 * @return Entry
	 */
	private EntryBase createBDBProductionFolder() {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		entry.setMyUri(InitializeConst.URI_BDB_PRODUCTION);
		return entry;
	}

	/**
	 * Manifestサーバのproduction割り当て設定フォルダEntryを生成
	 * @return Entry
	 */
	private EntryBase createBDBProductionMnfFolder() {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		entry.setMyUri(InitializeConst.URI_BDB_PRODUCTION + InitializeConst.URI_MNFSERVER);
		return entry;
	}

	/**
	 * Entryサーバのproduction割り当て設定フォルダEntryを生成
	 * @return Entry
	 */
	private EntryBase createBDBProductionEntryFolder() {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		entry.setMyUri(InitializeConst.URI_BDB_PRODUCTION + InitializeConst.URI_ENTRYSERVER);
		return entry;
	}

	/**
	 * インデックスtサーバのproduction割り当て設定フォルダEntryを生成
	 * @return Entry
	 */
	private EntryBase createBDBProductionIdxFolder() {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		entry.setMyUri(InitializeConst.URI_BDB_PRODUCTION + InitializeConst.URI_IDXSERVER);
		return entry;
	}

	/**
	 * 全文検索インデックスサーバのproduction割り当て設定フォルダEntryを生成
	 * @return Entry
	 */
	private EntryBase createBDBProductionFtFolder() {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		entry.setMyUri(InitializeConst.URI_BDB_PRODUCTION + InitializeConst.URI_FTSERVER);
		return entry;
	}

	/**
	 * 採番・カウンタサーバのproduction割り当て設定フォルダEntryを生成
	 * @return Entry
	 */
	private EntryBase createBDBProductionAlFolder() {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		entry.setMyUri(InitializeConst.URI_BDB_PRODUCTION + InitializeConst.URI_ALSERVER);
		return entry;
	}

	/**
	 * BDBサーバ予約済み設定フォルダEntryを生成
	 * @return Entry
	 */
	private EntryBase createBDBReservationFolder() {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		entry.setMyUri(InitializeConst.URI_BDB_RESERVATION);
		return entry;
	}

	/**
	 * ManifestサーバフォルダEntryを生成
	 * @return Entry
	 */
	private EntryBase createBDBMnfServerFolder() {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		entry.setMyUri(InitializeConst.URI_BDB_MNFSERVER);
		return entry;
	}

	/**
	 * ManifestサーバEntryを生成
	 * @return Entryリスト
	 */
	private List<EntryBase> createBDBMnfServers() {
		List<EntryBase> bdbServers = new ArrayList<>();
		// production
		Map<String, String> initBdbservers = TaggingEnvUtil.getSystemPropMap(
				InitializeConst.INIT_BDBSERVER_MANIFEST_PREFIX);
		if (initBdbservers != null) {
			for (Map.Entry<String, String> mapEntry : initBdbservers.entrySet()) {
				String serverName = mapEntry.getKey().substring(
						InitializeConst.INIT_BDBSERVER_MANIFEST_PREFIX_LEN);
				String host = mapEntry.getValue();
				EntryBase bdbServerEntry = createBDBMnfServer(serverName, host, 
						Constants.SERVICE_STATUS_PRODUCTION);
				bdbServers.add(bdbServerEntry);
			}
		}
		// staging
		initBdbservers = TaggingEnvUtil.getSystemPropMap(
				InitializeConst.INIT_BDBSERVER_STAGING_MANIFEST_PREFIX);
		if (initBdbservers != null) {
			for (Map.Entry<String, String> mapEntry : initBdbservers.entrySet()) {
				String serverName = mapEntry.getKey().substring(
						InitializeConst.INIT_BDBSERVER_STAGING_MANIFEST_PREFIX_LEN);
				String host = mapEntry.getValue();
				EntryBase bdbServerEntry = createBDBMnfServer(serverName, host, 
						Constants.SERVICE_STATUS_STAGING);
				bdbServers.add(bdbServerEntry);
			}
		}
		return bdbServers;
	}

	/**
	 * ManifestサーバEntryを生成
	 *   /_bdb/mnfserver/{serverName}
	 *   title=http://{host}/b
	 * @param serverName サーバ名
	 * @param host ホスト名
	 * @param serviceStatus サービスステータス (staging/production)
	 * @return Entry
	 */
	private EntryBase createBDBMnfServer(String serverName, String host, String serviceStatus) {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		StringBuilder sb = new StringBuilder();
		sb.append(InitializeConst.URI_BDB_MNFSERVER);
		sb.append("/");
		sb.append(serverName);
		entry.setMyUri(sb.toString());

		// titleにBDBサーバのURL(http〜サーブレットパスまで)を設定
		sb = new StringBuilder();
		sb.append(InitializeConst.PROTOCOL);
		sb.append(host);
		sb.append(InitializeConst.SERVLET_PATH);
		entry.title = sb.toString();

		// 指定された公開区分に割り当て /_bdb/{production|staging}/mnfserver/{サーバ名}
		sb = new StringBuilder();
		sb.append(InitializeConst.URI_BDB);
		sb.append("/");
		sb.append(serviceStatus);
		sb.append(InitializeConst.URI_MNFSERVER);
		sb.append("/");
		sb.append(serverName);
		entry.addAlternate(sb.toString());

		return entry;
	}

	/**
	 * EntryサーバフォルダEntryを生成
	 *   /_bdb/entryserver
	 * @return Entry
	 */
	private EntryBase createBDBEntryServerFolder() {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		entry.setMyUri(InitializeConst.URI_BDB_ENTRYSERVER);
		return entry;
	}

	/**
	 * EntryサーバEntryを生成
	 * @return Entryリスト
	 */
	private List<EntryBase> createBDBEntryServers() {
		List<EntryBase> bdbServers = new ArrayList<>();
		// production
		Map<String, String> initBdbservers = TaggingEnvUtil.getSystemPropMap(
				InitializeConst.INIT_BDBSERVER_ENTRY_PREFIX);
		if (initBdbservers != null) {
			for (Map.Entry<String, String> mapEntry : initBdbservers.entrySet()) {
				String serverName = mapEntry.getKey().substring(
						InitializeConst.INIT_BDBSERVER_ENTRY_PREFIX_LEN);
				String host = mapEntry.getValue();
				EntryBase bdbServerEntry = createBDBEntryServer(serverName, host, 
						Constants.SERVICE_STATUS_PRODUCTION);
				bdbServers.add(bdbServerEntry);
			}
		}
		// staging
		initBdbservers = TaggingEnvUtil.getSystemPropMap(
				InitializeConst.INIT_BDBSERVER_STAGING_ENTRY_PREFIX);
		if (initBdbservers != null) {
			for (Map.Entry<String, String> mapEntry : initBdbservers.entrySet()) {
				String serverName = mapEntry.getKey().substring(
						InitializeConst.INIT_BDBSERVER_STAGING_ENTRY_PREFIX_LEN);
				String host = mapEntry.getValue();
				EntryBase bdbServerEntry = createBDBEntryServer(serverName, host, 
						Constants.SERVICE_STATUS_STAGING);
				bdbServers.add(bdbServerEntry);
			}
		}
		return bdbServers;
	}

	/**
	 * EntryサーバEntryを生成
	 *   /_bdb/entryserver/{serverName}
	 *   title=http://{host}/b
	 * @param serverName サーバ名
	 * @param host ホスト名
	 * @param serviceStatus サービスステータス (staging/production)
	 * @return Entry
	 */
	private EntryBase createBDBEntryServer(String serverName, String host, String serviceStatus) {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		StringBuilder sb = new StringBuilder();
		sb.append(InitializeConst.URI_BDB_ENTRYSERVER);
		sb.append("/");
		sb.append(serverName);
		entry.setMyUri(sb.toString());

		// titleにBDBサーバのURL(http〜サーブレットパスまで)を設定
		sb = new StringBuilder();
		sb.append(InitializeConst.PROTOCOL);
		sb.append(host);
		sb.append(InitializeConst.SERVLET_PATH);
		entry.title = sb.toString();

		// 指定された公開区分に割り当て /_bdb/{production|staging}/entryserver/{サーバ名}
		sb = new StringBuilder();
		sb.append(InitializeConst.URI_BDB);
		sb.append("/");
		sb.append(serviceStatus);
		sb.append(InitializeConst.URI_ENTRYSERVER);
		sb.append("/");
		sb.append(serverName);
		entry.addAlternate(sb.toString());

		return entry;
	}

	/**
	 * インデックスサーバフォルダEntryを生成
	 *   /_bdb/idxserver
	 * @return Entry
	 */
	private EntryBase createBDBIdxServerFolder() {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		entry.setMyUri(InitializeConst.URI_BDB_IDXSERVER);
		return entry;
	}

	/**
	 * IndexサーバEntryを生成
	 * @return Entryリスト
	 */
	private List<EntryBase> createBDBIdxServers() {
		List<EntryBase> bdbServers = new ArrayList<>();
		// production
		Map<String, String> initBdbservers = TaggingEnvUtil.getSystemPropMap(
				InitializeConst.INIT_BDBSERVER_INDEX_PREFIX);
		if (initBdbservers != null) {
			for (Map.Entry<String, String> mapEntry : initBdbservers.entrySet()) {
				String serverName = mapEntry.getKey().substring(
						InitializeConst.INIT_BDBSERVER_INDEX_PREFIX_LEN);
				String host = mapEntry.getValue();
				EntryBase bdbServerEntry = createBDBIdxServer(serverName, host, 
						Constants.SERVICE_STATUS_PRODUCTION);
				bdbServers.add(bdbServerEntry);
			}
		}
		// staging
		initBdbservers = TaggingEnvUtil.getSystemPropMap(
				InitializeConst.INIT_BDBSERVER_STAGING_INDEX_PREFIX);
		if (initBdbservers != null) {
			for (Map.Entry<String, String> mapEntry : initBdbservers.entrySet()) {
				String serverName = mapEntry.getKey().substring(
						InitializeConst.INIT_BDBSERVER_STAGING_INDEX_PREFIX_LEN);
				String host = mapEntry.getValue();
				EntryBase bdbServerEntry = createBDBIdxServer(serverName, host, 
						Constants.SERVICE_STATUS_STAGING);
				bdbServers.add(bdbServerEntry);
			}
		}
		return bdbServers;
	}

	/**
	 * IndexサーバEntryを生成
	 *   /_bdb/idxserver/{serverName}
	 *   title=http://{host}/b
	 * @param serverName サーバ名
	 * @param host ホスト名
	 * @param serviceStatus サービスステータス (staging/production)
	 * @return Entry
	 */
	private EntryBase createBDBIdxServer(String serverName, String host, String serviceStatus) {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		StringBuilder sb = new StringBuilder();
		sb.append(InitializeConst.URI_BDB_IDXSERVER);
		sb.append("/");
		sb.append(serverName);
		entry.setMyUri(sb.toString());

		// titleにBDBサーバのURL(http〜サーブレットパスまで)を設定
		sb = new StringBuilder();
		sb.append(InitializeConst.PROTOCOL);
		sb.append(host);
		sb.append(InitializeConst.SERVLET_PATH);
		entry.title = sb.toString();

		// 指定された公開区分に割り当て /_bdb/{production|staging}/idxserver/{サーバ名}
		sb = new StringBuilder();
		sb.append(InitializeConst.URI_BDB);
		sb.append("/");
		sb.append(serviceStatus);
		sb.append(InitializeConst.URI_IDXSERVER);
		sb.append("/");
		sb.append(serverName);
		entry.addAlternate(sb.toString());

		return entry;
	}

	/**
	 * 全文検索インデックスサーバフォルダEntryを生成
	 *   /_bdb/ftserver
	 * @return Entry
	 */
	private EntryBase createBDBFtServerFolder() {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		entry.setMyUri(InitializeConst.URI_BDB_FTSERVER);
		return entry;
	}

	/**
	 * 全文検索IndexサーバEntryを生成
	 * @return Entryリスト
	 */
	private List<EntryBase> createBDBFtServers() {
		List<EntryBase> bdbServers = new ArrayList<>();
		// production
		Map<String, String> initBdbservers = TaggingEnvUtil.getSystemPropMap(
				InitializeConst.INIT_BDBSERVER_FULLTEXTSEARCH_PREFIX);
		if (initBdbservers != null) {
			for (Map.Entry<String, String> mapEntry : initBdbservers.entrySet()) {
				String serverName = mapEntry.getKey().substring(
						InitializeConst.INIT_BDBSERVER_FULLTEXTSEARCH_PREFIX_LEN);
				String host = mapEntry.getValue();
				EntryBase bdbServerEntry = createBDBFtServer(serverName, host, 
						Constants.SERVICE_STATUS_PRODUCTION);
				bdbServers.add(bdbServerEntry);
			}
		}
		// staging
		initBdbservers = TaggingEnvUtil.getSystemPropMap(
				InitializeConst.INIT_BDBSERVER_STAGING_FULLTEXTSEARCH_PREFIX);
		if (initBdbservers != null) {
			for (Map.Entry<String, String> mapEntry : initBdbservers.entrySet()) {
				String serverName = mapEntry.getKey().substring(
						InitializeConst.INIT_BDBSERVER_STAGING_FULLTEXTSEARCH_PREFIX_LEN);
				String host = mapEntry.getValue();
				EntryBase bdbServerEntry = createBDBFtServer(serverName, host, 
						Constants.SERVICE_STATUS_STAGING);
				bdbServers.add(bdbServerEntry);
			}
		}
		return bdbServers;
	}

	/**
	 * 全文検索IndexサーバEntryを生成
	 *   /_bdb/ftserver/{serverName}
	 *   title=http://{host}/b
	 * @param serverName サーバ名
	 * @param host ホスト名
	 * @param serviceStatus サービスステータス (staging/production)
	 * @return Entry
	 */
	private EntryBase createBDBFtServer(String serverName, String host, String serviceStatus) {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		StringBuilder sb = new StringBuilder();
		sb.append(InitializeConst.URI_BDB_FTSERVER);
		sb.append("/");
		sb.append(serverName);
		entry.setMyUri(sb.toString());

		// titleにBDBサーバのURL(http〜サーブレットパスまで)を設定
		sb = new StringBuilder();
		sb.append(InitializeConst.PROTOCOL);
		sb.append(host);
		sb.append(InitializeConst.SERVLET_PATH);
		entry.title = sb.toString();

		// 指定された公開区分に割り当て /_bdb/{production|staging}/ftserver/{サーバ名}
		sb = new StringBuilder();
		sb.append(InitializeConst.URI_BDB);
		sb.append("/");
		sb.append(serviceStatus);
		sb.append(InitializeConst.URI_FTSERVER);
		sb.append("/");
		sb.append(serverName);
		entry.addAlternate(sb.toString());

		return entry;
	}

	/**
	 * 採番・カウンタサーバフォルダEntryを生成
	 *   /_bdb/alserver
	 * @return Entry
	 */
	private EntryBase createBDBAlServerFolder() {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		entry.setMyUri(InitializeConst.URI_BDB_ALSERVER);
		return entry;
	}

	/**
	 * 採番・カウンタサーバEntryを生成
	 * @return Entryリスト
	 */
	private List<EntryBase> createBDBAlServers() {
		List<EntryBase> bdbServers = new ArrayList<>();
		// production
		Map<String, String> initBdbservers = TaggingEnvUtil.getSystemPropMap(
				InitializeConst.INIT_BDBSERVER_ALLOCIDS_PREFIX);
		if (initBdbservers != null) {
			for (Map.Entry<String, String> mapEntry : initBdbservers.entrySet()) {
				String serverName = mapEntry.getKey().substring(
						InitializeConst.INIT_BDBSERVER_ALLOCIDS_PREFIX_LEN);
				String host = mapEntry.getValue();
				EntryBase bdbServerEntry = createBDBAlServer(serverName, host, 
						Constants.SERVICE_STATUS_PRODUCTION);
				bdbServers.add(bdbServerEntry);
			}
		}
		// staging
		initBdbservers = TaggingEnvUtil.getSystemPropMap(
				InitializeConst.INIT_BDBSERVER_STAGING_ALLOCIDS_PREFIX);
		if (initBdbservers != null) {
			for (Map.Entry<String, String> mapEntry : initBdbservers.entrySet()) {
				String serverName = mapEntry.getKey().substring(
						InitializeConst.INIT_BDBSERVER_STAGING_ALLOCIDS_PREFIX_LEN);
				String host = mapEntry.getValue();
				EntryBase bdbServerEntry = createBDBAlServer(serverName, host, 
						Constants.SERVICE_STATUS_STAGING);
				bdbServers.add(bdbServerEntry);
			}
		}
		return bdbServers;
	}

	/**
	 * 採番・カウンタサーバEntryを生成
	 *   /_bdb/alserver/{serverName}
	 *   title=http://{host}/b
	 * @param serverName サーバ名
	 * @param host ホスト名
	 * @param serviceStatus サービスステータス (staging/production)
	 * @return Entry
	 */
	private EntryBase createBDBAlServer(String serverName, String host, String serviceStatus) {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		StringBuilder sb = new StringBuilder();
		sb.append(InitializeConst.URI_BDB_ALSERVER);
		sb.append("/");
		sb.append(serverName);
		entry.setMyUri(sb.toString());

		// titleにBDBサーバのURL(http〜サーブレットパスまで)を設定
		sb = new StringBuilder();
		sb.append(InitializeConst.PROTOCOL);
		sb.append(host);
		sb.append(InitializeConst.SERVLET_PATH);
		entry.title = sb.toString();

		// 指定された公開区分に割り当て /_bdb/{production|staging}/alserver/{サーバ名}
		sb = new StringBuilder();
		sb.append(InitializeConst.URI_BDB);
		sb.append("/");
		sb.append(serviceStatus);
		sb.append(InitializeConst.URI_ALSERVER);
		sb.append("/");
		sb.append(serverName);
		entry.addAlternate(sb.toString());

		return entry;
	}

	/**
	 * システム管理サービスのプロパティEntryを生成
	 * @return Entry
	 */
	private EntryBase createSettingsProperties() {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		StringBuilder sb = new StringBuilder();
		sb.append(InitializeConst.URI_SETTINGS_PROPERTIES);
		entry.setMyUri(sb.toString());

		// rightsにシステム管理サービスの設定
		entry.rights = "_json.startarraybracket=false";	// JSONのレスポンスにfeedをつける。

		return entry;
	}

	/**
	 * システム管理サービスフォルダEntryを生成
	 * @param systemService サービス名
	 * @param serviceStatus サービスステータス (production or staging)
	 * @return Entry
	 */
	private EntryBase createServiceSystem(String systemService, String serviceStatus) {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		StringBuilder sb = new StringBuilder();
		sb.append(Constants.URI_SERVICE);
		sb.append("/");
		sb.append(systemService);
		entry.setMyUri(sb.toString());

		// サービスステータス
		entry.subtitle = serviceStatus;

		return entry;
	}

	/**
	 * システム管理ユーザのエントリー郡を生成
	 * @param uid UID
	 * @param systemService システム管理サービス
	 * @return システム管理ユーザエントリーリスト
	 */
	private List<EntryBase> createAdminuserEntries(String uid, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String serviceName = reflexContext.getServiceName();
		List<EntryBase> entries = new ArrayList<EntryBase>();
		EntryBase entry = null;

		// /_group
		entry = TaggingEntryUtil.createEntry(serviceName);
		entry.setMyUri(Constants.URI_GROUP);
		entries.add(entry);

		String userTopUri = Constants.URI_USER + "/" + uid;
		String userGroupUri = userTopUri + Constants.URI_LAYER_GROUP;

		// /_group/$admin
		entry = TaggingEntryUtil.createEntry(serviceName);
		entry.setMyUri(Constants.URI_GROUP_ADMIN);
		entries.add(entry);

		// /_group/$admin/{UID} : /_user/{UID}/group/$admin をエイリアスに追加
		entry = TaggingEntryUtil.createEntry(serviceName);
		String groupUri = editGroupUri(Constants.URI_GROUP_ADMIN, uid);
		entry.setMyUri(groupUri);
		String groupAlias = userGroupUri + Constants.URI_$ADMIN;
		entry.addAlternate(groupAlias);
		entries.add(entry);

		// /_group/$content
		entry = TaggingEntryUtil.createEntry(serviceName);
		entry.setMyUri(Constants.URI_GROUP_CONTENT);
		entries.add(entry);

		// /_group/$content/{UID} : /{UID}/group/$content をエイリアスに追加
		entry = TaggingEntryUtil.createEntry(serviceName);
		groupUri = editGroupUri(Constants.URI_GROUP_CONTENT, uid);
		entry.setMyUri(groupUri);
		groupAlias = userGroupUri + Constants.URI_$CONTENT;
		entry.addAlternate(groupAlias);
		entries.add(entry);

		// /_group/$useradmin
		entry = TaggingEntryUtil.createEntry(serviceName);
		entry.setMyUri(Constants.URI_GROUP_USERADMIN);
		entries.add(entry);

		// /_group/$useradmin/{UID} : /_user/{UID}/group/$useradmin をエイリアスに追加
		entry = TaggingEntryUtil.createEntry(serviceName);
		groupUri = editGroupUri(Constants.URI_GROUP_USERADMIN, uid);
		entry.setMyUri(groupUri);
		groupAlias = userGroupUri + Constants.URI_$USERADMIN;
		entry.addAlternate(groupAlias);
		entries.add(entry);

		String userServiceUri = userTopUri + "/service";
		// /_user/{UID}/service/admin -> /_service/{サービス名} エントリーのエイリアス
		String userServiceAdminUri = userServiceUri + "/" + serviceName;
		entry = TaggingEntryUtil.createEntry(serviceName);
		entry.setMyUri(Constants.URI_SERVICE + "/" + serviceName);
		entry.addAlternate(userServiceAdminUri);
		entry.rights = uid;
		entries.add(entry);

		return entries;
	}

	/**
	 * Settings Entryを生成
	 * @param serviceName サービス名
	 * @return Entry
	 */
	private EntryBase createSettingsEntry() {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		entry.setMyUri(Constants.URI_SETTINGS);
		return entry;
	}

	/**
	 * adduser Entryを生成
	 * @param serviceName サービス名
	 * @return Entry
	 */
	private EntryBase createAdduserEntry() {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		entry.setMyUri(Constants.URI_SETTINGS_ADDUSER);

		// TODO メール初期設定もこちらで可能

		return entry;
	}

	/**
	 * passreset Entryを生成
	 * @param serviceName サービス名
	 * @return Entry
	 */
	private EntryBase createPassresetEntry() {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		entry.setMyUri(Constants.URI_SETTINGS_PASSRESET);

		// TODO メール初期設定もこちらで可能

		return entry;
	}

	/**
	 * GKEクラスタメンテナンス通知設定を生成
	 * @param serviceName サービス名
	 * @return Entry
	 */
	private EntryBase createMaintenanceNotice() 
	throws IOException, TaggingException {
		SecretManager secretManager = TaggingEnvUtil.getSecretManager();
		// システム管理サービスの管理ユーザメールアドレス
		String secretNameEmail = TaggingEnvUtil.getSystemProp(
				InitializeConst.SECRET_INIT_SYSTEMSERVICE_EMAIL_NAME, null);
		if (StringUtils.isBlank(secretNameEmail)) {
			throw new IllegalArgumentException("system service email name is required.");
		}
		String email = secretManager.getSecretKey(secretNameEmail, null);
		if (StringUtils.isBlank(email)) {
			throw new IllegalArgumentException("system service email is required.");
		}

		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		entry.setMyUri(InitializeConst.URI_SETTINGS_MAINTENANCE_NOTICE);
		// 送信先メールアドレス
		Contributor cont = new Contributor();
		cont.email = email;
		entry.addContributor(cont);
		// タイトル
		entry.title = "[vte.cx]GKEメンテナンスのお知らせ";
		// テキスト本文
		StringBuilder sb = new StringBuilder();
		sb.append("GKE クラスタのメンテナンス通知を受け取りました。");
		sb.append(Constants.NEWLINE);
		sb.append("内容は以下の通りです。");
		sb.append(Constants.NEWLINE);
		sb.append("------------");
		sb.append(Constants.NEWLINE);
		sb.append("${NOTICE}");
		sb.append(Constants.NEWLINE);
		sb.append("------------");
		sb.append(Constants.NEWLINE);
		entry.summary = sb.toString();

		// TODO HTMLメッセージがあれば content._$$text に設定する。

		return entry;
	}

	/**
	 * HTML Entryを生成
	 * @param serviceName サービス名
	 * @return Entry
	 */
	private EntryBase createHtmlEntry() {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		entry.setMyUri(Constants.URI_HTML);
		return entry;
	}

	/**
	 * Log Entryを生成
	 * @param serviceName サービス名
	 * @return Entry
	 */
	private EntryBase createLogEntry() {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		entry.setMyUri(Constants.URI_LOG);
		return entry;
	}

	/**
	 * ログイン履歴 Entryを生成
	 * @param serviceName サービス名
	 * @return Entry
	 */
	private EntryBase createLoginHistoryEntry() {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		entry.setMyUri(Constants.URI_LOGIN_HISTORY);
		return entry;
	}

	/**
	 * バケットフォルダEntryを生成
	 * @return Entry
	 */
	private EntryBase createBucketFolder() {
		EntryBase entry = TaggingEntryUtil.createAtomEntry();
		// URI
		entry.setMyUri(InitializeConst.URI_BUCKET);
		return entry;
	}

	/**
	 * サービス管理ユーザ登録のFeedを生成
	 * @return サービス管理ユーザ登録のFeed
	 */
	private FeedBase createAdduserAdminFeed() 
	throws IOException, TaggingException {
		SecretManager secretManager = TaggingEnvUtil.getSecretManager();
		// システム管理サービスの管理ユーザメールアドレス
		String secretNameEmail = TaggingEnvUtil.getSystemProp(
				InitializeConst.SECRET_INIT_SYSTEMSERVICE_EMAIL_NAME, null);
		if (StringUtils.isBlank(secretNameEmail)) {
			throw new IllegalArgumentException("system service email name is required.");
		}
		String username = secretManager.getSecretKey(secretNameEmail, null);
		if (StringUtils.isBlank(username)) {
			throw new IllegalArgumentException("system service email is required.");
		}
		
		String secretNamePswd = TaggingEnvUtil.getSystemProp(
				InitializeConst.SECRET_INIT_SYSTEMSERVICE_PASSWORD_NAME, null);
		if (StringUtils.isBlank(secretNamePswd)) {
			throw new IllegalArgumentException("system service password name is required.");
		}
		String tmpPswd = secretManager.getSecretKey(secretNamePswd, null);
		if (StringUtils.isBlank(tmpPswd)) {
			throw new IllegalArgumentException("system service password is required.");
		}
		String password = AuthTokenUtil.hash(tmpPswd);
		String nickname = null;
		return createAdduserFeed(username, password, nickname);
	}

	/**
	 * サービス管理ユーザ登録のFeedを生成
	 * @param username ユーザ名
	 * @param password パスワード
	 * @param nickname ニックネーム
	 * @return サービス管理ユーザ登録のFeed
	 */
	private FeedBase createAdduserFeed(String username, String password, String nickname) {
		FeedBase feed = TaggingEntryUtil.createAtomFeed();
		EntryBase entry = TaggingEntryUtil.createAtomEntry();

		//   <entry>
		//     <contributor>
		//       <uri>urn:vte.cx:auth:{メールアドレス},{パスワード}</uri>
		//       <name>{ニックネーム}</name>
		//     </contributor>
		//     <title>メールのタイトル(任意)</title>
		//     <summary>メール本文(任意)</summary>
		//   </entry>

		Contributor contributor = new Contributor();
		StringBuilder sb = new StringBuilder();
		sb.append(AtomConst.URN_PREFIX_AUTH);
		sb.append(username);
		if (password != null) {
			sb.append(",");
			sb.append(password);
		}
		contributor.uri = sb.toString();
		contributor.name = nickname;
		entry.addContributor(contributor);

		feed.addEntry(entry);
		return feed;
	}

	/**
	 * グループ参加URIを生成
	 * @param parentUri 親URI
	 * @param uid UID
	 * @return グループ参加URI
	 */
	private String editGroupUri(String parentUri, String uid) {
		StringBuilder sb = new StringBuilder();
		sb.append(parentUri);
		sb.append("/");
		sb.append(uid);
		return sb.toString();
	}

}
