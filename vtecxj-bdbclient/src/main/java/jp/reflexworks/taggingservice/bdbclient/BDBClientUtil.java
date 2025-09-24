package jp.reflexworks.taggingservice.bdbclient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.api.AtomConst;
import jp.reflexworks.atom.api.Condition;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.servlet.util.UrlUtil;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.conn.ConnectionInfoImpl;
import jp.reflexworks.taggingservice.conn.ReflexConnection;
import jp.reflexworks.taggingservice.conn.ReflexConnectionUtil;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.Value;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBServerType;
import jp.reflexworks.taggingservice.requester.BDBRequesterUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.Constants.OperationType;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.RetryUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * BDBクライアント ユーティリティクラス.
 */
public class BDBClientUtil {

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(BDBClientUtil.class);

	/**
	 * BDBリクエストの初期起動でのアクセス失敗時リトライ総数を取得.
	 * @return BDBリクエストの初期起動でのアクセス失敗時リトライ総数
	 */
	public static int getBDBRequestInitRetryCount() {
		return TaggingEnvUtil.getSystemPropInt(BDBClientConst.BDBREQUEST_INIT_RETRY_COUNT,
				BDBClientConst.BDBREQUEST_INIT_RETRY_COUNT_DEFAULT);
	}

	/**
	 * BDBリクエストの初期起動でのアクセス失敗時リトライ時のスリープ時間を取得.
	 * @return BDBリクエストの初期起動でのアクセス失敗時のスリープ時間(ミリ秒)
	 */
	public static int getBDBRequestInitRetryWaitmillis() {
		return TaggingEnvUtil.getSystemPropInt(BDBClientConst.BDBREQUEST_INIT_RETRY_WAITMILLIS,
				BDBClientConst.BDBREQUEST_INIT_RETRY_WAITMILLIS_DEFAULT);
	}

	/**
	 * データストア一括更新の失敗時リトライ総数を取得.
	 * @return データストア一括更新の失敗時リトライ総数
	 */
	public static int getBulkPutRetryCount() {
		return TaggingEnvUtil.getSystemPropInt(BDBClientConst.BULKPUT_RETRY_COUNT,
				BDBClientConst.BULKPUT_RETRY_COUNT_DEFAULT);
	}

	/**
	 * データストア一括更新の失敗リトライ時のスリープ時間を取得.
	 * @return データストア一括更新の失敗リトライ時のスリープ時間(ミリ秒)
	 */
	public static int getBulkPutRetryWaitmillis() {
		return TaggingEnvUtil.getSystemPropInt(BDBClientConst.BULKPUT_RETRY_WAITMILLIS,
				BDBClientConst.BULKPUT_RETRY_WAITMILLIS_DEFAULT);
	}

	/**
	 * ACL Type変換
	 * @param flg OperationType.INSERT, UPDATE, DELETE
	 * @return AclConst.ACL_TYPE_CREATE, ACL_TYPE_UPDATE, ACL_TYPE_DELETE
	 */
	public static String convertAclType(OperationType flg) {
		if (flg == OperationType.INSERT) {
			return AtomConst.ACL_TYPE_CREATE;
		} else if (flg == OperationType.UPDATE) {
			return AtomConst.ACL_TYPE_UPDATE;
		} else if (flg == OperationType.DELETE) {
			return AtomConst.ACL_TYPE_DELETE;
		}
		return null;
	}

	/**
	 * 例外を変換する.
	 * リトライ対象の場合例外をスローしない。
	 * @param e データストア例外
	 * @param method GET, POST, PUT or DELETE
	 * @param requestInfo リクエスト情報
	 */
	public static void convertError(IOException e, String method, RequestInfo requestInfo)
	throws IOException {
		if (RetryUtil.isRetryError(e, method)) {
			return;
		}
		throw e;
	}

	/**
	 * リトライログメッセージを取得
	 * @param e 例外
	 * @param r リトライ回数
	 * @return リトライログメッセージ
	 */
	public static String getRetryLog(Throwable e, int r) {
		return RetryUtil.getRetryLog(e, r);
	}

	/**
	 * スリープ処理.
	 * @param waitMillis スリープ時間(ミリ秒)
	 */
	public static void sleep(long waitMillis) {
		RetryUtil.sleep(waitMillis);
	}

	/**
	 * URI + リビジョンの文字列から、URIとリビジョンを分割して返却する。
	 * リビジョンの指定がない場合はURIのみ返却する。
	 * @param uriAndRevision URI + リビジョンの文字列
	 * @return [0]URI、[1]リビジョン
	 */
	public static String[] getUriAndRevision(String uriAndRevStr) {
		String[] uriAndRev = TaggingEntryUtil.getUriAndRevisionById(uriAndRevStr);
		if (uriAndRev == null) {
			uriAndRev = new String[]{uriAndRevStr, null};
		}
		return uriAndRev;
	}

	/**
	 * Entry検索用メモリキャッシュを取得.
	 * スレッド内にメモリキャッシュが存在しない場合、メモリキャッシュを登録して返却します。
	 * @param serviceName サービス名
	 * @param connectionInfo コネクション情報
	 * @return Entry検索用メモリキャッシュ
	 */
	public static Map<String, Value<EntryBase>> getEntryMap(String serviceName,
			ConnectionInfo connectionInfo) {
		Map<String, Value<EntryBase>> readEntryMap = null;
		String mapKey = getActualConnName(
				BDBClientConst.CONNECTION_INFO_ENTRYMAP, serviceName);
		ConnEntryMap connEntryMap = (ConnEntryMap)connectionInfo.getSharing(mapKey);
		if (connEntryMap != null) {
			readEntryMap = connEntryMap.getConnection();
		} else {
			readEntryMap = new ConcurrentHashMap<>();
			connEntryMap = new ConnEntryMap(readEntryMap);
			connectionInfo.putSharing(mapKey, connEntryMap);
		}
		return readEntryMap;
	}

	/**
	 * Feed検索用メモリキャッシュを取得.
	 * スレッド内にメモリキャッシュが存在しない場合、メモリキャッシュを登録して返却します。
	 * @param serviceName サービス名
	 * @param connectionInfo コネクション情報
	 * @return Feed検索用メモリキャッシュ
	 */
	public static Map<String, Value<FeedBase>> getFeedMap(String serviceName,
			ConnectionInfo connectionInfo) {
		Map<String, Value<FeedBase>> readFeedMap = null;
		String mapKey = getActualConnName(
				BDBClientConst.CONNECTION_INFO_FEEDMAP, serviceName);
		ConnFeedMap connFeedMap = (ConnFeedMap)connectionInfo.getSharing(mapKey);
		if (connFeedMap != null) {
			readFeedMap = connFeedMap.getConnection();
		} else {
			readFeedMap = new ConcurrentHashMap<>();
			connFeedMap = new ConnFeedMap(readFeedMap);
			connectionInfo.putSharing(mapKey, connFeedMap);
		}
		return readFeedMap;
	}

	/**
	 * 読み出したエントリーを格納するMapに指定されたentryを追加します
	 * @param entry エントリー
	 * @param uri 検索URI (エントリーがnullの場合使用)
	 * @param serviceName サービス名
	 * @param readEntryMap Entryメモリキャッシュ
	 * @param connectionInfo コネクション情報 (ResourceMapper取得に使用)
	 */
	public static void setEntryMap(EntryBase entry, String uri, String serviceName,
			Map<String, Value<EntryBase>> readEntryMap, ConnectionInfo connectionInfo) {
		if (entry == null) {
			if (!StringUtils.isBlank(uri)) {
				readEntryMap.put(uri, new Value<EntryBase>(entry));
			}
			return;
		}
		FeedTemplateMapper mapper = TaggingEnvUtil.getResourceMapper(serviceName);
		Map<String, Value<EntryBase>> tmpMap = new HashMap<>();
		EntryBase copyEntry = TaggingEntryUtil.copyEntry(entry, mapper);
		String idUri = copyEntry.getMyUri();
		tmpMap.put(idUri, new Value<EntryBase>(copyEntry));
		List<String> aliases = entry.getAlternate();
		if (aliases != null) {
			for (String alias : aliases) {
				copyEntry = TaggingEntryUtil.copyEntry(entry, mapper);
				tmpMap.put(alias, new Value<EntryBase>(copyEntry));
			}
		}
		readEntryMap.putAll(tmpMap);
	}

	/**
	 * リクエスト内読み込みキャッシュに登録.
	 * @param entry Entry
	 * @param uri 検索URI (エントリーがnullの場合使用)
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public static void setReadEntryMap(EntryBase entry, String uri, String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo) {
		Map<String, Value<EntryBase>> readEntryMap = BDBClientUtil.getEntryMap(serviceName,
				connectionInfo);
		setEntryMap(entry, uri, serviceName, readEntryMap, connectionInfo);
	}

	/**
	 * リクエスト内読み込みキャッシュに登録.
	 * @param requestUri リクエストURI
	 * @param feed Feed
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public static void setReadFeedMap(String requestUri, FeedBase feed, String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo) {
		Map<String, Value<FeedBase>> readFeedMap = BDBClientUtil.getFeedMap(serviceName,
				connectionInfo);
		readFeedMap.put(requestUri, new Value<FeedBase>(feed));
	}

	/**
	 * EntryメモリキャッシュからEntryを削除する.
	 * ルートエントリー、/_ で始まるエントリー以外をすべて削除する。
	 * @param readEntryMap Entryメモリキャッシュ
	 * @param deleteSystemUris ルートエントリー、/_ で始まるURIのうち、更新されたもの
	 */
	public static void removeEntryMap(Map<String, Value<EntryBase>> readEntryMap,
			Set<String> deleteSystemUris) {
		if (readEntryMap == null) {
			return;
		}
		List<String> delUris = new ArrayList<>();
		for (String uri : readEntryMap.keySet()) {
			if (!isSystemUri(uri)) {
				delUris.add(uri);
			} else if (deleteSystemUris.contains(uri)) {
				delUris.add(uri);
			}
		}
		for (String delUri : delUris) {
			readEntryMap.remove(delUri);
		}
	}

	/**
	 * リクエスト内読み込みキャッシュから削除.
	 * パラメータを取り外し、親階層が等しい場合は削除する。
	 * @param parentUri 親階層
	 * @param feed Feed
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public static void removeReadFeedMap(String parentUri, String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo) {
		Map<String, Value<FeedBase>> readFeedMap = BDBClientUtil.getFeedMap(serviceName,
				connectionInfo);
		removeFeedMap(readFeedMap, parentUri);
	}

	/**
	 * リクエスト内Feed読み込みキャッシュから削除.
	 * パラメータを取り外し、親階層が等しい場合は削除する。
	 * @param readEntryMap Feedメモリキャッシュ
	 * @param parentUri リクエストURI
	 */
	public static void removeFeedMap(Map<String, Value<FeedBase>> readFeedMap,
			String parentUri) {
		if (readFeedMap == null || StringUtils.isBlank(parentUri)) {
			return;
		}

		List<String> delUris = new ArrayList<>();
		for (String requestUri : readFeedMap.keySet()) {
			String pathInfo = UrlUtil.getPathInfo(requestUri);
			if (parentUri.equals(pathInfo)) {
				delUris.add(requestUri);
			}
		}
		for (String delUri : delUris) {
			readFeedMap.remove(delUri);
		}
	}

	/**
	 * システムURIであるかどうか.
	 * システムURI:ルートエントリー、/_ で始まるURI の場合
	 * @param uri URI
	 * @return システムURIであればtrue
	 */
	public static boolean isSystemUri(String uri) {
		if (Constants.URI_ROOT.equals(uri) ||
				uri.startsWith(Constants.URI_SYSTEM_PREFIX)) {
			return true;
		}
		return false;
	}

	/**
	 * コネクション格納名を取得.
	 * <p>
	 * 「コネクション格納名@サービス名」
	 * </p>
	 * @param connName
	 * @param serviceName
	 * @return コネクション格納名
	 */
	public static String getActualConnName(String connName, String serviceName) {
		return ReflexConnectionUtil.getActualConnName(connName, serviceName);
	}

	/**
	 * データストアへのアクセスログを出力するかどうか.
	 * @return データストアへのアクセスログを出力する場合true
	 */
	public static boolean isEnableAccessLog() {
		return TaggingEnvUtil.getSystemPropBoolean(
				BDBClientConst.BDBREQUEST_ENABLE_ACCESSLOG, false);
	}

	/**
	 * URLエンコード
	 * @param str 文字列
	 * @return URLエンコードした文字列
	 */
	public static String urlEncode(String str) {
		return UrlUtil.urlEncode(str);
	}

	/**
	 * 登録予定Entryメモリキャッシュを取得.
	 * スレッド内にメモリキャッシュが存在しない場合、メモリキャッシュを登録して返却します。
	 * @param serviceName サービス名
	 * @param connectionInfo コネクション情報
	 * @return 登録予定Entryメモリキャッシュ
	 */
	public static Map<String, Value<EntryBase>> getTmpEntryMap(String serviceName,
			ConnectionInfo connectionInfo) {
		Map<String, Value<EntryBase>> readEntryMap = null;
		String mapKey = getActualConnName(
				BDBClientConst.CONNECTION_INFO_TMP_ENTRYMAP, serviceName);
		ConnEntryMap connEntryMap = (ConnEntryMap)connectionInfo.getSharing(mapKey);
		if (connEntryMap != null) {
			readEntryMap = connEntryMap.getConnection();
		} else {
			readEntryMap = new ConcurrentHashMap<>();
			connEntryMap = new ConnEntryMap(readEntryMap);
			connectionInfo.putSharing(mapKey, connEntryMap);
		}
		return readEntryMap;
	}

	/**
	 * 登録予定Entryメモリキャッシュをクリアする.
	 * @param serviceName サービス名
	 * @param connectionInfo コネクション情報
	 */
	public static void clearTmpEntryMap(String serviceName,
			ConnectionInfo connectionInfo) {
		String mapKey = getActualConnName(
				BDBClientConst.CONNECTION_INFO_TMP_ENTRYMAP, serviceName);
		ConnEntryMap connEntryMap = (ConnEntryMap)connectionInfo.getSharing(mapKey);
		if (connEntryMap != null) {
			connEntryMap.clear();
		}
	}

	/**
	 * Feedメモリキャッシュ用キーに最大取得件数を追加
	 * @param cacheUri Feedメモリキャッシュ用キー
	 * @param limit 最大取得件数
	 * @param cursorStr カーソル
	 * @return Feedメモリキャッシュ用キー
	 */
	public static String addLimitToFeedUriForCache(String cacheUri, int limit,
			String cursorStr) {
		StringBuilder sb = new StringBuilder();
		sb.append(cacheUri);
		if (cacheUri != null && cacheUri.indexOf("?") > -1) {
			sb.append("&");
		} else {
			sb.append("?");
		}
		sb.append(RequestParam.PARAM_LIMIT);
		sb.append("=");
		sb.append(limit);
		if (!StringUtils.isBlank(cursorStr)) {
			sb.append("&");
			sb.append(RequestParam.PARAM_NEXT);
			sb.append("=");
			sb.append(cursorStr);
		}
		return sb.toString();
	}

	/**
	 * Feedメモリキャッシュ用キー生成
	 * @param uri URI
	 * @param isUriForwardMatch URI前方一致検索を行う場合true
	 * @param conditions 検索条件
	 * @param isCount 件数取得の場合true
	 * @return Feedメモリキャッシュ用キー
	 */
	public static String getFeedUriForCache(String uri, boolean isUriForwardMatch,
			List<List<Condition>> conditions, boolean isCount) {
		StringBuilder sb = new StringBuilder();
		sb.append(uri);
		if (isUriForwardMatch) {
			sb.append(RequestParam.WILDCARD);
		}
		sb.append("?");
		if (isCount) {
			sb.append(RequestParam.PARAM_COUNT);
		} else {
			sb.append(RequestParam.PARAM_FEED);
		}
		if (conditions != null && conditions.size() > 0) {
			int size = conditions.size();
			for (List<Condition> conditionList : conditions) {
				if (size > 1) {
					sb.append("&");
					sb.append(Condition.OR_START);
				}

				boolean isFirstInner = size > 1;	// OR条件指定の場合true
				for (Condition condition : conditionList) {
					if (isFirstInner) {
						isFirstInner = false;
					} else {
						sb.append("&");
					}
					sb.append(condition.toString());
				}

				if (size > 1) {
					sb.append("&");
					sb.append(Condition.OR_END);
				}
			}
		}
		return sb.toString();
	}

	/**
	 * キーの担当サーバURLを取得.
	 * @param idUri ID URI
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return サーバURL
	 */
	public static String getEntryServerUrl(String idUri, String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		long startTime = 0;
		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[getEntryServerUrl] start. idUri=");
			sb.append(idUri);
			logger.debug(sb.toString());
			startTime = new Date().getTime();
		}
		List<String> serverUrls = BDBRequesterUtil.getEntryServerUrls(serviceName,
				requestInfo, connectionInfo);
		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[getEntryServerUrl] getEntryServerUrls end.");
			sb.append(LogUtil.getElapsedTimeLog(startTime));
			logger.debug(sb.toString());
			startTime = new Date().getTime();
		}
		String retServer = BDBRequesterUtil.assignServer(BDBServerType.ENTRY, serverUrls, idUri,
				serviceName, connectionInfo);
		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[getEntryServerUrl] assignServer end.");
			sb.append(LogUtil.getElapsedTimeLog(startTime));
			logger.debug(sb.toString());
			startTime = new Date().getTime();
		}
		return retServer;
	}

	/**
	 * Entry取得リクエストURIを編集
	 * @param id ID
	 * @return Entry取得リクエストURI
	 */
	public static String getEntryUri(String id) {
		StringBuilder sb = new StringBuilder();
		sb.append(id);
		sb.append("?");
		sb.append(RequestParam.PARAM_ENTRY);
		return sb.toString();
	}

	/**
	 * Entry複数取得リクエストURIを編集
	 * @return Entry複数取得リクエストURI
	 */
	public static String getEntryMultipleUri() {
		StringBuilder sb = new StringBuilder();
		sb.append("?");
		sb.append(RequestParam.PARAM_ENTRY);
		sb.append("&");
		sb.append(RequestParam.PARAM_MULTIPLE);
		return sb.toString();
	}

	/**
	 * ManifestサーバからID取得リクエストURIを編集.
	 * POSTメソッドで、URIリストをリクエストデータに指定する時に使用。
	 * @return ManifestサーバからID取得リクエストURI
	 */
	public static String getGetIdByManifestUri() {
		return getGetIdByManifestUri(null);
	}

	/**
	 * ManifestサーバからID取得リクエストURIを編集
	 * @param uri URI
	 * @return ManifestサーバからID取得リクエストURI
	 */
	public static String getGetIdByManifestUri(String uri) {
		StringBuilder sb = new StringBuilder();
		sb.append(StringUtils.null2blank(uri));
		sb.append("?");
		sb.append(RequestParam.PARAM_ENTRY);
		return sb.toString();
	}

	/**
	 * Manifestサーバへインデックス更新リクエストURIを編集
	 * @return Manifestサーバへインデックス更新リクエストURI
	 */
	public static String getPutManifestUri() {
		StringBuilder sb = new StringBuilder();
		sb.append("?");
		sb.append(RequestParam.PARAM_INDEX);
		return sb.toString();
	}

	/**
	 * listオプションURIを取得
	 * @param dbName テーブル名
	 * @param keyprefix キー前方一致条件
	 * @param cursorStr カーソル文字列
	 * @return listオプションURI
	 */
	public static String getListUri(String dbName, String keyprefix, String cursorStr) {
		// GET /b?_list={dbName}&_keyprefix={keyprefix}
		StringBuilder sb = new StringBuilder();
		sb.append("?");
		sb.append(RequestParam.PARAM_LIST);
		sb.append("=");
		sb.append(urlEncode(dbName));
		if (!StringUtils.isBlank(keyprefix)) {
			sb.append("&");
			sb.append(RequestParam.PARAM_KEYPREFIX);
			sb.append("=");
			sb.append(urlEncode(keyprefix));
		}
		if (!StringUtils.isBlank(cursorStr)) {
			sb.append("&");
			sb.append(RequestParam.PARAM_NEXT);
			sb.append("=");
			sb.append(urlEncode(cursorStr));
		}
		return sb.toString();
	}

	/**
	 * ConnectionInfoの共有情報のみコピーする.
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return ConnectionInfoの共有情報のみコピーしたオブジェクト
	 */
	public static ConnectionInfo copySharingConnectionInfo(RequestInfo requestInfo,
			ConnectionInfo connectionInfo) {
		// ConnectionInfoのReadEntryMapをコピーする
		ConnectionInfo afterCommitConnectionInfo = new ConnectionInfoImpl(null, requestInfo);
		Map<String, ReflexConnection<?>> sharingConnectionInfoMap =
				connectionInfo.getSharings();
		if (sharingConnectionInfoMap != null) {
			for (Map.Entry<String, ReflexConnection<?>> mapEntry :
					sharingConnectionInfoMap.entrySet()) {
				String name = mapEntry.getKey();
				if (name.startsWith(BDBClientConst.CONNECTION_INFO_ENTRYMAP) ||
						name.startsWith(BDBClientConst.CONNECTION_INFO_FEEDMAP)) {
					ReflexConnection<?> conn = mapEntry.getValue();
					afterCommitConnectionInfo.putSharing(name, conn);
				}
			}
		}
		return afterCommitConnectionInfo;
	}

	/**
	 * Entryサーバ最大取得数を取得.
	 * リクエストヘッダ制限に対応
	 * @return Entryサーバ最大取得数
	 */
	public static int getEntryserverGetLimit() {
		return TaggingEnvUtil.getSystemPropInt(BDBClientConst.ENTRYSERVER_GET_LIMIT,
				BDBClientConst.ENTRYSERVER_GET_LIMIT_DEFAULT);
	}

	/**
	 * Entryサーバ最大更新数を取得.
	 * リクエストヘッダ制限に対応
	 * @return Entryサーバ最大更新数
	 */
	public static int getEntryserverPutLimit() {
		return TaggingEnvUtil.getSystemPropInt(BDBClientConst.ENTRYSERVER_PUT_LIMIT,
				BDBClientConst.ENTRYSERVER_PUT_LIMIT_DEFAULT);
	}

}
