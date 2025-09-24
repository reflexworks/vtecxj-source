package jp.reflexworks.taggingservice.plugin.def;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexStatic;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.blogic.ServiceBlogic;
import jp.reflexworks.taggingservice.env.StaticInfoUtil;
import jp.reflexworks.taggingservice.env.TaggingEnvConst;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.LockingException;
import jp.reflexworks.taggingservice.exception.StaticDuplicatedException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.StaticInfo;
import jp.reflexworks.taggingservice.plugin.NamespaceManager;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.RetryUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * メール管理クラス.
 */
public class NamespaceManagerDefault implements NamespaceManager {

	/** 名前空間保持プロパティ名(システム管理サービス) */
	private static final String NAMESPACE_SYSTEM = TaggingEnvConst.NAMESPACE_SYSTEM;
	/** 名前空間保持URI(親階層)(一般サービス) */
	private static final String PARENT_URI_NAMESPACE = Constants.URI_NAMESPACE + "/";
	/** 名前空間の番号区切り文字 */
	private static final String NAME_DELIMITER = "X";

	/** メモリ上のstaticオブジェクト格納キー : 名前空間情報 lock */
	private static final String STATIC_NAME_NAMESPACE_LOCK ="_mapper_namespace_lock";
	/** メモリ上のstaticオブジェクト格納キー : 名前空間 */
	private static final String STATIC_NAME_NAMESPACE ="_mapper_namespace";

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 初期起動時の処理.
	 */
	public void init() {
		// 名前空間情報取得時刻
		// キー: サーバ名、値: ロックフラグ
		ConcurrentMap<String, Boolean> namespaceLockMap = new ConcurrentHashMap<>();
		try {
			ReflexStatic.setStatic(STATIC_NAME_NAMESPACE_LOCK, namespaceLockMap);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_NAMESPACE_LOCK, e);
		}
		// 名前空間情報
		// キー: サービス名、値: 名前空間
		ConcurrentMap<String, StaticInfo<String>> namespaceMap = new ConcurrentHashMap<>();
		try {
			ReflexStatic.setStatic(STATIC_NAME_NAMESPACE, namespaceMap);
		} catch (StaticDuplicatedException e) {
			logger.warn("[init] StaticDuplicatedException: " + STATIC_NAME_NAMESPACE, e);
		}
	}

	/**
	 * シャットダウン時の終了処理
	 */
	public void close() {
		// Do nothing.
	}

	/**
	 * 名前空間を取得.
	 * Static情報に値が存在しない場合は、設定中なので少し待つ。
	 * settingServiceが実行済みであることを想定。
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 名前空間
	 */
	public String getNamespace(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		if (StringUtils.isBlank(serviceName)) {
			return null;
		}
		// システム管理サービスかどうか
		String systemService = TaggingEnvUtil.getSystemService();
		if (systemService.equals(serviceName)) {
			// システム管理サービスの場合、プロパティ設定
			return TaggingEnvUtil.getSystemProp(NAMESPACE_SYSTEM, serviceName);
		}

		long startTime = 0;
		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[getNamespace] start. serviceName=");
			sb.append(serviceName);
			logger.debug(sb.toString());
			startTime = new Date().getTime();
		}

		int numRetries = StaticInfoUtil.getStaticinfoRetryCount();
		int waitMillis = StaticInfoUtil.getStaticinfoRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			// まずstatic情報から取得
			StaticInfo<String> namespaceInfo = getStaticNamespaceInfo(serviceName);
			boolean isExceeded = false;
			if (namespaceInfo != null) {
				// 取得時間のチェック
				ServiceBlogic serviceBlogic = new ServiceBlogic();
				isExceeded = serviceBlogic.isNeedToUpdateStaticInfo(serviceName,
						namespaceInfo.getAccesstime(), requestInfo, connectionInfo);
				if (logger.isTraceEnabled()) {
					StringBuilder sb = new StringBuilder();
					sb.append(LogUtil.getRequestInfoStr(requestInfo));
					sb.append("[getNamespace] check NeedToUpdateStaticInfo. serviceName=");
					sb.append(serviceName);
					sb.append(LogUtil.getElapsedTimeLog(startTime));
					logger.debug(sb.toString());
					startTime = new Date().getTime();
				}
				if (!isExceeded) {
					// static情報が存在し期限内であれば返す。
					return namespaceInfo.getInfo();
				}
				// 一般サービスの場合、BDBから取得
				String ret = getNamespaceFromBDB(serviceName, requestInfo, connectionInfo);
				if (logger.isTraceEnabled()) {
					StringBuilder sb = new StringBuilder();
					sb.append(LogUtil.getRequestInfoStr(requestInfo));
					sb.append("[getNamespace] getNamespaceFromBDB end. serviceName=");
					sb.append(serviceName);
					sb.append(LogUtil.getElapsedTimeLog(startTime));
					logger.debug(sb.toString());
					startTime = new Date().getTime();
				}
				return ret;
			}

			int tmpWaitMillis = waitMillis + r;
			if (logger.isTraceEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[getNamespace] serviceName=");
				sb.append(serviceName);
				sb.append(" sleep (");
				sb.append(r);
				sb.append(") (");
				sb.append(tmpWaitMillis);
				sb.append(" ms)");
				logger.debug(sb.toString());
			}
			// Static情報がnullの場合、サービス初期処理が終了していないため一定時間スリープする。
			RetryUtil.sleep(tmpWaitMillis);
		}

		// ここにたどり着いた場合、lockエラー
		String errMsg = "The static infomation is locked. namespace (get namespace)";
		if (logger.isDebugEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[getNamespace] serviceName=");
			sb.append(serviceName);
			sb.append(" LockingException: ");
			sb.append(errMsg);
			logger.debug(sb.toString());
		}
		throw new LockingException(errMsg);
	}

	/**
	 * 名前空間を指定された値に更新.
	 * @param namespace 名前空間
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void setNamespace(String namespace, String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// 入力チェック
		checkInput(namespace, serviceName);
		// 更新
		EntryBase entry = createNamespaceEntry(namespace, serviceName);
		setNamespace(entry, serviceName, requestInfo, connectionInfo);
	}

	/**
	 * 名前空間を指定された値に更新.
	 * @param entry 名前空間Entry
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	private void setNamespace(EntryBase entry, String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String systemService = TaggingEnvUtil.getSystemService();
		SystemContext systemContext = new SystemContext(systemService,
				requestInfo, connectionInfo);
		String namespace = entry.title;

		ConcurrentMap<String, Boolean> namespaceLockMap = getStaticNamespaceLockMap();

		int numRetries = StaticInfoUtil.getStaticinfoRetryCount();
		int waitMillis = StaticInfoUtil.getStaticinfoRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			// lockフラグを取得
			Boolean lock = false;
			try {
				lock = namespaceLockMap.putIfAbsent(serviceName, true);
				if (lock == null) {
					systemContext.put(entry);
					setStaticNamespace(serviceName, namespace);
					return;
				}
				// lockフラグを取得できなかった場合、一定時間待つ
				RetryUtil.sleep(waitMillis + r * 1);

			} finally {
				if (lock == null) {
					// lock解除
					namespaceLockMap.remove(serviceName);
				}
			}
		}
		// ここにたどり着いた場合、lockエラー
		String errMsg = "The static infomation is locked. namespace (set namespace)";
		if (logger.isDebugEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[getNamespace] serviceName=");
			sb.append(serviceName);
			sb.append(" LockingException: ");
			sb.append(errMsg);
			logger.debug(sb.toString());
		}
		throw new LockingException(errMsg);
	}

	/**
	 * 名前空間を変更.
	 * 名前空間を新しく発行し、設定を変更する。
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 新しく発行した名前空間
	 */
	public String changeNamespace(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		EntryBase entry = createChangeNamespaceEntry(serviceName, requestInfo, connectionInfo);
		// 更新
		setNamespace(entry, serviceName, requestInfo, connectionInfo);
		return entry.title;
	}

	/**
	 * 名前空間を変更したEntryを生成.
	 * 他のエントリーと同時に更新する場合に使用。
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 新しく発行した名前空間Entry
	 */
	public EntryBase createChangeNamespaceEntry(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// 入力チェック
		checkService(serviceName);
		// 現在の名前空間を取得
		String currentNamespace = getNamespaceFromBDB(serviceName,
				requestInfo, connectionInfo);
		// 名前空間を編集
		StringBuilder ns = new StringBuilder();
		int idx = currentNamespace.indexOf(NAME_DELIMITER);
		if (idx == -1) {
			ns.append(currentNamespace);
			ns.append(NAME_DELIMITER);
			ns.append("2");
		} else {
			ns.append(currentNamespace.substring(0, idx));
			ns.append(NAME_DELIMITER);
			String numStr = currentNamespace.substring(idx + 1);
			int num = 2;
			if (StringUtils.isInteger(numStr)) {
				num = StringUtils.intValue(numStr, 1) + 1;
			}
			ns.append(num);
		}
		String newNamespace = ns.toString();
		// 名前空間Entryを生成
		return createNamespaceEntry(newNamespace, serviceName);
	}

	/**
	 * 名前空間設定Entryを生成
	 * @param namespace 名前空間
	 * @param serviceName サービス名
	 * @return 名前空間設定Entry
	 */
	public EntryBase createNamespaceEntry(String namespace, String serviceName) {
		String uri = getNamespaceUri(serviceName);
		String systemService = TaggingEnvUtil.getSystemService();
		EntryBase entry = TaggingEntryUtil.createEntry(systemService);
		entry.setMyUri(uri);
		entry.title = namespace;
		return entry;
	}

	/**
	 * 入力値をチェック.
	 *  ・nullチェック
	 *  ・システム管理サービスでないかどうか
	 * @param namespace 名前空間
	 * @param serviceName サービス名
	 */
	private void checkInput(String namespace, String serviceName) {
		CheckUtil.checkNotNull(namespace, "namespace");
		checkService(serviceName);
	}

	/**
	 * サービス名チェック
	 *  ・nullチェック
	 *  ・システム管理サービスでないかどうか
	 * @param serviceName
	 */
	private void checkService(String serviceName) {
		CheckUtil.checkNotNull(serviceName, "serviceName");
		String systemService = TaggingEnvUtil.getSystemService();
		if (systemService.equals(serviceName)) {
			String msg = "For system service, please change the value of the property file.";
			if (logger.isDebugEnabled()) {
				logger.debug("[setNamespace] " + msg);
			}
			throw new IllegalParameterException(msg);
		}
	}

	/**
	 * 名前空間設定EntryのURIを取得
	 * @param serviceName サービス名
	 * @return 名前空間設定EntryのURI
	 */
	private String getNamespaceUri(String serviceName) {
		return PARENT_URI_NAMESPACE + serviceName;
	}

	/**
	 * 一般サービスの名前空間取得.
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 名前空間
	 */
	private String getNamespaceFromBDB(String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String uri = getNamespaceUri(serviceName);
		String systemService = TaggingEnvUtil.getSystemService();
		SystemContext systemContext = new SystemContext(systemService,
				requestInfo, connectionInfo);

		ConcurrentMap<String, Boolean> namespaceLockMap = getStaticNamespaceLockMap();

		int numRetries = StaticInfoUtil.getStaticinfoRetryCount();
		int waitMillis = StaticInfoUtil.getStaticinfoRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			// lockフラグを取得
			Boolean lock = false;
			try {
				lock = namespaceLockMap.putIfAbsent(serviceName, true);
				if (lock == null) {
					// BDBから名前空間を取得
					EntryBase entry = systemContext.getEntry(uri, true);
					String namespace = null;
					if (entry != null && !StringUtils.isBlank(entry.title)) {
						namespace = entry.title;
					} else {
						namespace = serviceName;
					}
					setStaticNamespace(serviceName, namespace);
					return namespace;
				}

				int tmpWaitMillis = waitMillis + r;
				if (logger.isDebugEnabled()) {
					StringBuilder sb = new StringBuilder();
					sb.append(LogUtil.getRequestInfoStr(requestInfo));
					sb.append("[getNamespaceFromBDB] serviceName=");
					sb.append(serviceName);
					sb.append(" sleep (");
					sb.append(r);
					sb.append(") (");
					sb.append(tmpWaitMillis);
					sb.append(" ms)");
					logger.debug(sb.toString());
				}
				// lockフラグを取得できなかった場合、一定時間待つ
				RetryUtil.sleep(tmpWaitMillis);

			} finally {
				if (lock == null) {
					// lock解除
					namespaceLockMap.remove(serviceName);
				}
			}
		}
		// ここにたどり着いた場合、lockエラー
		String errMsg = "The static infomation is locked. namespace (get namespace from bdb)";
		if (logger.isDebugEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[getNamespace] serviceName=");
			sb.append(serviceName);
			sb.append(" LockingException: ");
			sb.append(errMsg);
			logger.debug(sb.toString());
		}
		throw new LockingException(errMsg);
	}

	/**
	 * static mapより、名前空間を取得.
	 * @param serviceName サービス名
	 * @return 名前空間と取得日時
	 */
	private StaticInfo<String> getStaticNamespaceInfo(String serviceName) {
		ConcurrentMap<String, StaticInfo<String>> namespaceMap =
				(ConcurrentMap<String, StaticInfo<String>>)ReflexStatic.getStatic(
						STATIC_NAME_NAMESPACE);
		if (namespaceMap == null) {
			throw new IllegalStateException("static infomation does not exist. " + STATIC_NAME_NAMESPACE);
		}
		return namespaceMap.get(serviceName);
	}

	/**
	 * static mapに、名前空間を設定.
	 * @param serviceName サービス名
	 * @param namespaceInfo 名前空間と取得日時
	 */
	private void setStaticNamespaceInfo(String serviceName, StaticInfo<String> namespaceInfo) {
		ConcurrentMap<String, StaticInfo<String>> namespaceMap =
				(ConcurrentMap<String, StaticInfo<String>>)ReflexStatic.getStatic(
						STATIC_NAME_NAMESPACE);
		if (namespaceMap == null) {
			throw new IllegalStateException("static infomation does not exist. " + STATIC_NAME_NAMESPACE);
		}
		namespaceMap.put(serviceName, namespaceInfo);
	}

	/**
	 * static mapから、名前空間を削除.
	 * @param serviceName サービス名
	 */
	private void removeStaticNamespaceInfo(String serviceName) {
		ConcurrentMap<String, StaticInfo<String>> namespaceMap =
				(ConcurrentMap<String, StaticInfo<String>>)ReflexStatic.getStatic(
						STATIC_NAME_NAMESPACE);
		if (namespaceMap == null) {
			throw new IllegalStateException("static infomation does not exist. " + STATIC_NAME_NAMESPACE);
		}
		namespaceMap.remove(serviceName);
	}

	/**
	 * static mapより、名前空間lock mapを取得.
	 * @return 名前空間lock map
	 */
	private ConcurrentMap<String, Boolean> getStaticNamespaceLockMap() {
		ConcurrentMap<String, Boolean> namespaceLockMap =
				(ConcurrentMap<String, Boolean>)ReflexStatic.getStatic(
						STATIC_NAME_NAMESPACE_LOCK);
		if (namespaceLockMap == null) {
			throw new IllegalStateException("static infomation does not exist. " + STATIC_NAME_NAMESPACE_LOCK);
		}
		return namespaceLockMap;
	}

	/**
	 * static mapに、名前空間を設定.
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 */
	public void setStaticNamespace(String serviceName, String namespace) {
		StaticInfo<String> namespaceInfo = new StaticInfo<>(namespace, new Date());
		setStaticNamespaceInfo(serviceName, namespaceInfo);
	}

	/**
	 * サービスの設定.
	 * 名前空間を取得する
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	@Override
	public void settingService(String serviceName, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// 名前空間の取得設定処理
		// システム管理サービスかどうか
		String systemService = TaggingEnvUtil.getSystemService();
		if (systemService.equals(serviceName)) {
			// システム管理サービスの場合、プロパティ設定なので何もしない
			return;
		}

		long startTime = 0;
		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[settingService] start. serviceName=");
			sb.append(serviceName);
			logger.debug(sb.toString());
			startTime = new Date().getTime();
		}
		// まずstatic情報から取得
		StaticInfo<String> namespaceInfo = getStaticNamespaceInfo(serviceName);
		boolean isExceeded = false;
		if (namespaceInfo == null) {
			isExceeded = true;
			if (logger.isTraceEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[settingService] namespaceInfo is null in static. serviceName=");
				sb.append(serviceName);
				sb.append(LogUtil.getElapsedTimeLog(startTime));
				logger.debug(sb.toString());
				startTime = new Date().getTime();
			}

		} else {
			// 取得時間のチェック
			ServiceBlogic serviceBlogic = new ServiceBlogic();
			isExceeded = serviceBlogic.isNeedToUpdateStaticInfo(serviceName,
					namespaceInfo.getAccesstime(), requestInfo, connectionInfo);
			if (logger.isTraceEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[settingService] check NeedToUpdateStaticInfo. serviceName=");
				sb.append(serviceName);
				sb.append(LogUtil.getElapsedTimeLog(startTime));
				logger.debug(sb.toString());
				startTime = new Date().getTime();
			}
		}

		if (isExceeded) {
			// 一般サービスの場合、BDBから取得
			getNamespaceFromBDB(serviceName, requestInfo, connectionInfo);

			if (logger.isTraceEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[settingService] getNamespaceFromBDB. serviceName=");
				sb.append(serviceName);
				sb.append(LogUtil.getElapsedTimeLog(startTime));
				logger.debug(sb.toString());
				startTime = new Date().getTime();
			}
		}
	}

	/**
	 * サービス情報クローズ.
	 * static領域にある指定されたサービスの情報を削除する。
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	@Override
	public void closeService(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		removeStaticNamespaceInfo(serviceName);
	}

	/**
	 * サービス初期設定に必要なURIリスト.
	 * サービス初期設定に必要なシステム管理サービスのURIリストを返却する。
	 * @param serviceName サービス名
	 * @return URIリスト
	 */
	@Override
	public List<String> getSettingEntryUrisBySystem(String serviceName) {
		List<String> uris = new ArrayList<>();
		uris.add(getNamespaceUri(serviceName));
		return uris;
	}

	/**
	 * サービス初期設定に必要なFeed検索用URIリスト.
	 * サービス初期設定に必要なシステム管理サービスのFeed検索用URIリストを返却する。
	 * @param serviceName サービス名
	 * @return URIリスト
	 */
	@Override
	public List<String> getSettingFeedUrisBySystem(String serviceName) {
		return null;
	}

	/**
	 * サービス初期設定に共通して必要なURIリスト.
	 * サービス初期設定に共通して必要なシステム管理サービスのURIリストを返却する。
	 * @return URIリスト
	 */
	@Override
	public List<String> getSettingEntryUrisBySystem() {
		List<String> uris = new ArrayList<>();
		uris.add(Constants.URI_NAMESPACE);
		return uris;
	}

	/**
	 * サービス初期設定に共通して必要なURIリスト.
	 * サービス初期設定に共通して必要なシステム管理サービスのFeed検索用URIリストを返却する。
	 * @return URIリスト
	 */
	@Override
	public List<String> getSettingFeedUrisBySystem() {
		return null;
	}

	/**
	 * サービス初期設定に共通して必要なURIリスト.
	 * サービス初期設定に共通して必要な自サービスのURIリストを返却する。
	 * @return URIリスト
	 */
	@Override
	public List<String> getSettingEntryUris() {
		return null;
	}

	/**
	 * サービス初期設定に共通して必要なURIリスト.
	 * サービス初期設定に共通して必要な自サービスのFeed検索用URIリストを返却する。
	 * @return URIリスト
	 */
	@Override
	public List<String> getSettingFeedUris() {
		return null;
	}

}
