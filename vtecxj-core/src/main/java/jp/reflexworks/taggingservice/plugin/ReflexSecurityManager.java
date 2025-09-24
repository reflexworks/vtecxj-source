package jp.reflexworks.taggingservice.plugin;

import java.io.IOException;

import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.exception.AuthenticationException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.sys.SystemContext;

/**
 * セキュリティ管理クラス.
 */
public interface ReflexSecurityManager extends ReflexPlugin {

	/**
	 * 初期起動時の処理.
	 */
	public void init();

	/**
	 * Hostヘッダのチェック (DNSリバインディング対策).
	 * エラーの場合AuthenticationExceptionをスローします。
	 * @param req リクエスト
	 * @param resp レスポンス
	 */
	public void checkRequestHost(ReflexRequest req, ReflexResponse resp)
	throws IOException, TaggingException;

	/**
	 * Hostヘッダのチェック (DNSリバインディング対策).
	 * 内部ネットワークからのリクエスト用。
	 * エラーの場合AuthenticationExceptionをスローします。
	 * @param req リクエスト
	 * @param resp レスポンス
	 */
	public void checkRequestHostByInside(ReflexRequest req, ReflexResponse resp)
	throws IOException, TaggingException;

	/**
	 * X-Requested-Withヘッダチェック.
	 * 以下の場合、リクエストヘッダにX-Requested-Withの指定がないとエラー。
	 * <ul>
	 *   <li>POST、PUT、DELETE</li>
	 *   <li>戻り値がJSON</li>
	 * </ul>
	 * @param req リクエスト
	 */
	public void checkRequestedWith(ReflexRequest req)
	throws IOException, TaggingException;

	/**
	 * 認証失敗回数を取得
	 * @param user ユーザ識別値
	 * @param req リクエスト
	 * @param systemContext SystemContext
	 * @return ユーザ識別値+IPアドレスでの認証失敗回数
	 */
	public long getAuthFailureCount(String user, ReflexRequest req,
			SystemContext systemContext)
	throws IOException, TaggingException;

	/**
	 * 認証失敗回数を加算
	 * @param user ユーザ識別値
	 * @param req リクエスト
	 * @param systemContext SystemContext
	 * @return ユーザ識別値+IPアドレスでの認証失敗回数
	 */
	public long incrementAuthFailureCount(String user, ReflexRequest req,
			SystemContext systemContext)
	throws IOException, TaggingException;

	/**
	 * 認証失敗回数をクリア
	 * @param user ユーザ識別値
	 * @param req リクエスト
	 * @param systemContext SystemContext
	 */
	public void clearAuthFailureCount(String user, ReflexRequest req,
			SystemContext systemContext)
	throws IOException, TaggingException;

	/**
	 * ホワイトリストチェック.
	 * サービス管理者の場合、このメソッドを呼び出します。
	 * ホワイトリストが設定されている場合、設定にないリモートアドレスからのリクエストを認証エラーとします。
	 * @param req リクエスト
	 * @param systemContext SystemContext
	 * @throws AuthenticationException ホワイトリストチェックエラー
	 */
	public void checkWhiteRemoteAddress(ReflexRequest req, SystemContext systemContext)
	throws IOException, TaggingException;

	/**
	 * RXID使用回数チェック.
	 * GETでPathInfoが指定されたパターンと合致しているものは、指定された回数。その他は1回。
	 * RXIDの使用回数が上記を超えている場合はAuthenticationExceptionをスローします。
	 * @param rxid RXID
	 * @param req リクエスト
	 * @param systemContext SystemContext
	 */
	public void checkRXIDCount(String rxid, ReflexRequest req,
			SystemContext systemContext)
	throws IOException, TaggingException;

	/**
	 * IPアドレスを取得.
	 * @param req リクエスト
	 * @return IPアドレス
	 */
	public String getIPAddr(ReflexRequest req);

	/**
	 * 登録されたクロスドメインの場合、Origin URLを許可するレスポンスヘッダを付加する.
	 * @param req リクエスト
	 * @param resp レスポンス
	 */
	public void checkCORS(ReflexRequest req, ReflexResponse resp)
	throws IOException, TaggingException;

}
