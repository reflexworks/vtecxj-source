package jp.reflexworks.taggingservice.plugin;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Future;

import jp.reflexworks.atom.api.Condition;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.UpdatedInfo;

/**
 * データストア管理インタフェース.
 * このクラスを実装するクラスは起動時に1つのインスタンスが生成され、staticに保持されます。
 * @param <T> コネクション
 */
public interface DatastoreManager extends SettingService {

	/**
	 * 登録処理.
	 * @param feed Feed
	 * @param parentUri 親URI
	 * @param ext 自動採番登録の場合拡張子
	 * @param originalServiceName 元のサービス名
	 * @param auth 認証情報(更新対象サービス)
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 登録情報. 中身のEntryは引数のfeedオブジェクトに格納されるEntryと同一インスタンスです。
	 */
	public List<UpdatedInfo> post(FeedBase feed, String parentUri, String ext,
			String originalServiceName, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

	/**
	 * 更新処理.
	 * @param feed Feed
	 * @param parentUri 親URI
	 * @param updateAllIndex インデックスをすべて更新(データパッチ用)
	 * @param originalServiceName 元のサービス名
	 * @param auth 認証情報(更新対象サービス)
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 更新情報.
	 */
	public List<UpdatedInfo> put(FeedBase feed, String parentUri,
			boolean updateAllIndex, String originalServiceName, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

	/**
	 * 削除処理.
	 * @param ids IDまたはURIのリスト
	 * @param originalServiceName 元のサービス名
	 * @param auth 認証情報(更新対象サービス)
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 更新情報.
	 */
	public List<UpdatedInfo> delete(List<String> ids, String originalServiceName,
			ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

	/**
	 * 一括更新処理.
	 * 指定されたFeedを一定数に区切って非同期並列更新を行う。
	 * @param feed Feed
	 * @param parentUri 親URI
	 * @param async 非同期の場合true、同期の場合false
	 * @param updateAllIndex インデックスをすべて更新(データパッチ用)
	 * @param originalServiceName 元のサービス名
	 * @param auth 認証情報(更新対象サービス)
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public List<Future<List<UpdatedInfo>>> bulkPut(FeedBase feed, String parentUri, boolean async,
			boolean updateAllIndex, String originalServiceName, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

	/**
	 * 一括更新処理.
	 * 指定されたFeedを一定数に区切って順番に更新する。この処理を非同期で行う。
	 * @param feed Feed
	 * @param parentUri 親URI
	 * @param async 非同期の場合true、同期の場合false
	 * @param updateAllIndex インデックスをすべて更新(データパッチ用)
	 * @param originalServiceName 元のサービス名
	 * @param auth 認証情報(更新対象サービス)
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public Future<List<UpdatedInfo>> bulkSerialPut(FeedBase feed, String parentUri, boolean async,
			boolean updateAllIndex, String originalServiceName, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

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
	public Future deleteFolder(String uri, boolean noDeleteSelf, boolean async, boolean isParallel,
			String originalServiceName, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

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
			ReflexAuthentication auth, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

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
	throws IOException, TaggingException;

	/**
	 * Feed検索.
	 * @param uri URI
	 * @param conditions 検索条件
	 * @param isUrlForwardMatch URI前方一致の場合true
	 * @param limit 最大取得件数
	 * @param cursorStr カーソル
	 * @param useCache データストアキャッシュを検索する場合true
	 * @param originalServiceName 元のサービス名
	 * @param auth 認証情報(検索対象サービス)
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return Feed
	 */
	public FeedBase getFeed(String uri, List<List<Condition>> conditions,
			boolean isUrlForwardMatch, int limit, String cursorStr, boolean useCache,
			String originalServiceName, ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

	/**
	 * 件数取得.
	 * @param uri URI
	 * @param conditions 検索条件
	 * @param isUrlForwardMatch URI前方一致の場合true
	 * @param limit 最大件数(任意指定)
	 * @param cursorStr カーソル
	 * @param useCache データストアキャッシュを検索する場合true
	 * @param originalServiceName 元のサービス名
	 * @param auth 認証情報(検索対象サービス)
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return Feed titleに件数、件数計算が途中の場合はlink rel="next"のhrefにカーソル。
	 */
	public FeedBase getCount(String uri, List<List<Condition>> conditions,
			boolean isUrlForwardMatch, Integer limit, String cursorStr, boolean useCache,
			String originalServiceName, ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

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
	throws IOException, TaggingException;

	/**
	 * Entryリストを取得
	 * @param uris キーリスト
	 * @param useCache キャッシュを使用する場合true (readEntryMapのキャッシュのみ)
	 * @param originalServiceName 元のサービス名
	 * @param auth 認証情報(検索対象サービス)
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return Entryリスト
	 */
	public FeedBase getEntries(List<String> uris, boolean useCache,
			String originalServiceName, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

	/**
	 * キャッシュのクリア.
	 * テンプレートの更新時に実行する。
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void clearCache(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo);

	/**
	 * サービス作成.
	 * データストアのサービス初期設定
	 * @param newServiceName 新規サービス名
	 * @param serviceStatus サービスステータス (staging/production)
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void createservice(String newServiceName, String serviceStatu, 
			ReflexAuthentication auth, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

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
	throws IOException, TaggingException;

	/**
	 * モニター
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @return モニタリング結果
	 */
	public FeedBase monitor(ReflexRequest req, ReflexResponse resp)
	throws IOException, TaggingException;

	/**
	 * インデックス更新
	 * @param indexFeed インデックス更新情報
	 * @param isDelete 削除の場合true
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void putIndex(FeedBase indexFeed, boolean isDelete,
			ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

	/**
	 * サービスステータス変更に伴うBDBサーバ変更.
	 *  ・名前空間の変更
	 *  ・BDBサーバの割り当て直し
	 *  ・一部データの移行
	 * @param targetServiceName 対象サービス名
	 * @param newServiceStatus 新サービスステータス
	 * @param reflexContext システム管理サービスのReflexContext (ログイン情報含む)
	 */
	public void changeServiceStatus(String targetServiceName, String newServiceStatus,
			ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * サーバ追加.
	 * @param targetServiceName 対象サービス名
	 * @param feed サーバ追加情報
	 * @param reflexContext システム管理サービスのReflexContext (ログイン情報含む)
	 */
	public void addServer(String targetServiceName, FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * サーバ削除.
	 * @param targetServiceName 対象サービス名
	 * @param feed サーバ削除情報
	 * @param reflexContext システム管理サービスのReflexContext (ログイン情報含む)
	 */
	public void removeServer(String targetServiceName, FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * リクエスト・メインスレッド初期処理.
	 * @param servieName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void initMainThread(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

	/**
	 * リクエスト・メインスレッド初期処理の事前準備.
	 * サービスステータス判定のため、キャッシュにEntryリストがある場合のみ取得。
	 * @param servieName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void initMainThreadPreparation(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

	/**
	 * 名前空間設定処理の後のサービス初期設定.
	 * @param servieName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void settingServiceAfterNamespace(String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

	/**
	 * リクエスト・メインスレッドのユーザ情報初期処理.
	 * @param uid UID
	 * @param servieName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void initMainThreadUser(String uid, String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

}
