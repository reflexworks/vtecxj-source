package jp.reflexworks.taggingservice.plugin;

import java.io.IOException;
import java.util.Date;

import jakarta.servlet.http.HttpServletRequest;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;

/**
 * サービス管理インターフェース.
 * サービスの登録、停止、削除、有効チェックを行う。
 */
public interface ServiceManager extends SettingService {

	/**
	 * 指定されたサービスが有効かどうかチェックする。
	 * @param req リクエスト (メンテナンス失敗再処理時に使用)
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 有効である場合true
	 */
	public boolean isEnabled(ReflexRequest req, String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException;

	/**
	 * サービスステータスを取得.
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return サービスステータス。サービスが存在しない場合はnull。
	 */
	public String getServiceStatus(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

	/**
	 * サービスエントリーを取得.
	 * subtitleにサービスステータスが設定されている。
	 * @param serviceName サービス名
	 * @param useCache キャッシュを使用する場合true
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return サービスエントリー。サービスが存在しない場合はnull。
	 */
	public EntryBase getServiceEntry(String serviceName, boolean useCache,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

	/**
	 * サービス情報の設定がない場合に設定.
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void settingServiceIfAbsent(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

	/**
	 * APIKeyを取得.
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return APIKey
	 */
	public String getAPIKey(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

	/**
	 * サービスキーを取得.
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return サービスキー
	 */
	public String getServiceKey(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

	/**
	 * APIKeyを更新.
	 * @param reflexContext ReflexContext
	 * @return APIKey
	 */
	public String changeAPIKey(ReflexContext ReflexContext)
	throws IOException, TaggingException;

	/**
	 * サービスキーを更新.
	 * @param reflexContext ReflexContext
	 * @return サービスキー
	 */
	public String changeServiceKey(ReflexContext ReflexContext)
	throws IOException, TaggingException;

	/**
	 * リクエストからサービス名を取得します.
	 * @param req リクエスト
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return サービス名
	 */
	public String getMyServiceName(HttpServletRequest req,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

	/**
	 * 各サービスのHost名を取得.
	 * @param serviceName サービス名
	 * @return Host名
	 */
	public String getHost(String serviceName);

	/**
	 * ログイン管理サービスから各サービスへログインする際のURLを取得(コンテキストパスまで)
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 各サービスへログインする際のURL(コンテキストパスまで)
	 */
	public String getRedirectUrlContextPath(String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

	/**
	 * サービス作成.
	 * @param feed 新規サービス情報
	 * @param reflexContext システム管理サービスのReflexContext
	 * @return 作成したサービス名
	 */
	public String createservice(FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * サービス削除.
	 * @param delServiceName 削除サービス名
	 * @param reflexContext システム管理サービスのReflexContext
	 */
	public void deleteservice(String delServiceName, ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * サービスのURIを取得.
	 * @param serviceName サービス名
	 * @return サービスのURI
	 */
	public String getServiceUri(String serviceName);

	/**
	 * サービス管理者用認証情報を生成.
	 * 内部処理で使用.
	 * @param serviceName サービス名
	 * @return サービス管理者用認証情報
	 */
	public ReflexAuthentication createServiceAdminAuth(String serviceName);

	/**
	 * サービスステータスによるhttp・httpsリクエストチェック.
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @return 処理継続可能な場合true、終了(リダイレクト)の場合false
	 */
	public boolean checkServiceStatus(ReflexRequest req, ReflexResponse resp)
	throws IOException, TaggingException;

	/**
	 * サービス公開.
	 * @param targetServiceName 対象サービス名
	 * @param reflexContext システム管理サービスのReflexContext
	 * @return 公開したサービス名
	 */
	public String serviceToProduction(String targetServiceName, ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * サービス非公開.
	 * @param targetServiceName 対象サービス名
	 * @param reflexContext システム管理サービスのReflexContext
	 * @return 非公開にしたサービス名
	 */
	public String serviceToStaging(String targetServiceName, ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * サービス名の入力チェック.
	 * @param serviceName サービス名
	 */
	public void checkServicename(String serviceName);

	/**
	 * サービス名を小文字変換.
	 * @param serviceName サービス名
	 * @return 小文字変換したサービス名
	 */
	public String editServiceName(String serviceName);

	/**
	 * アクセスカウンタをインクリメント.
	 * アクセス数が規定数を超えている場合、PaymentRequiredExceptionエラーを返す。
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void incrementAccessCounter(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

	/**
	 * 各サービスのHost名+ContextPathを取得.
	 * @param serviceName サービス名
	 * @return Host名+ContextPath
	 */
	public String getServiceServerContextpath(String serviceName);

	/**
	 * サービスに関するStatic情報を更新する必要があるかどうかチェックする.
	 * @param targetServiceName 対象サービス名
	 * @param accessTime Static情報更新時間
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return サービスエントリー
	 */
	public boolean isNeedToUpdateStaticInfo(String targetServiceName, Date accessTime,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

}
