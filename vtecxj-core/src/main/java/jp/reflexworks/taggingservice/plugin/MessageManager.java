package jp.reflexworks.taggingservice.plugin;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;

/**
 * メッセージ管理クラス.
 */
public interface MessageManager extends ReflexPlugin {

	/**
	 * 更新のメッセージを取得.
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgPut(String serviceName);

	/**
	 * 削除のメッセージを取得.
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgDelete(String serviceName);

	/**
	 * 受付完了のメッセージを取得.
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgAccept(String serviceName);

	/**
	 * フォルダ削除時のメッセージを取得.
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgDeleteFolder(String serviceName);

	/**
	 * フォルダクリア時のメッセージを取得.
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgClearFolder(String serviceName);

	/**
	 * 加算範囲指定完了のメッセージを取得.
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgRangeids(String serviceName);

	/**
	 * 加算範囲の値指定完了のメッセージを取得.
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgSetids(String serviceName);

	/**
	 * 署名検証OKのメッセージを取得.
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgValidSignature(String serviceName);

	/**
	 * 署名完了のメッセージを取得.
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgPutSignature(String serviceName);

	/**
	 * 複数署名完了のメッセージを取得.
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgPutSignatures(String serviceName);

	/**
	 * 署名削除のメッセージを取得.
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgDeleteSignature(String serviceName);

	/**
	 * ユーザ無効のメッセージを取得.
	 * @param user 処理対象ユーザ
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgRevokeUser(String user, String serviceName);

	/**
	 * ユーザ有効のメッセージを取得.
	 * @param user 処理対象ユーザ
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgActivateUser(String user, String serviceName);

	/**
	 * メール送信完了のメッセージを取得.
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgSendMail(String serviceName);

	/**
	 * サービス登録完了のメッセージを取得.
	 * @param newServiceName 登録サービス名
	 * @param systemService システム管理サービス名
	 * @return メッセージ
	 */
	public String getMsgCreateservice(String newServiceName, String systemService);

	/**
	 * サービス削除完了のメッセージを取得.
	 * @param delServiceName 削除サービス名
	 * @param systemService システム管理サービス名
	 * @return メッセージ
	 */
	public String getMsgDeleteservice(String delServiceName, String systemService);

	/**
	 * サービスリセット完了のメッセージを取得.
	 * @param resetServiceName リセットサービス名
	 * @param systemService システム管理サービス名
	 * @return メッセージ
	 */
	public String getMsgResetservice(String resetServiceName, String systemService);

	/**
	 * ログイン完了のメッセージを取得.
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgLogin(String serviceName);

	/**
	 * ログアウト完了のメッセージを取得.
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgLogout(String serviceName);

	/**
	 * 管理者によるユーザ登録のメッセージを取得.
	 * @param feed ユーザのトップエントリーリスト
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgAdduserByAdmin(FeedBase feed, String serviceName);

	/**
	 * ユーザ登録のメッセージを取得.
	 * 仮登録完了
	 * @param entry ユーザのトップエントリー
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgAdduser(EntryBase entry, String serviceName);

	/**
	 * パスワードリセットのメッセージを取得.
	 * パスワードリセットのためのメール送信完了
	 * @param entry ユーザのトップエントリー
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgPassreset(EntryBase entry, String serviceName);

	/**
	 * パスワード変更完了のメッセージを取得.
	 * @param entry ユーザのトップエントリー
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgChangepass(EntryBase entry, String serviceName);

	/**
	 * 管理者によるパスワード変更完了のメッセージを取得.
	 * @param feed パスワード変更情報
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgChangepassByAdmin(FeedBase feed, String serviceName);

	/**
	 * アカウント変更のためのメール送信のメッセージを取得.
	 * アカウント変更のためのメール送信完了
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgChangeaccount(String serviceName);

	/**
	 * アカウント変更完了のメッセージを取得.
	 * アカウント変更完了
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgChangeaccountVerify(String serviceName);

	/**
	 * ユーザ退会完了のメッセージを取得.
	 * @param auth 認証情報
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgCanceluser(ReflexAuthentication auth, String serviceName);

	/**
	 * アクセスキー変更完了のメッセージを取得.
	 * @param auth 認証情報
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgChangeAccesskey(ReflexAuthentication auth, String serviceName);

	/**
	 * コンテンツ登録完了のメッセージを取得.
	 * @param feed コンテンツFeed
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgPutContent(FeedBase feed, String serviceName);

	/**
	 * コンテンツ削除完了のメッセージを取得.
	 * @param uri コンテンツFeed
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgDeleteContent(String uri, String serviceName);

	/**
	 * キャッシュ登録完了のメッセージを取得.
	 * @param uri キー
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgPutCache(String uri, String serviceName);

	/**
	 * キャッシュの有効時間設定完了のメッセージを取得.
	 * @param uri キー
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgPutCacheExpire(String uri, String serviceName);

	/**
	 * キャッシュ削除完了のメッセージを取得.
	 * @param uri キー
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgDeleteCache(String uri, String serviceName);

	/**
	 * キャッシュデータが存在しないメッセージを取得.
	 * @param uri キー
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgNotExistCache(String uri, String serviceName);

	/**
	 * ユーザ削除完了のメッセージを取得.
	 * @param delAccount 削除アカウント
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgDeleteuser(String delAccount, String serviceName);

	/**
	 * サービス公開.
	 * @param targetServiceName 捜査対象サービス
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgProductionService(String targetServiceName, String serviceName);

	/**
	 * サービス非公開.
	 * @param targetServiceName 捜査対象サービス
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgStagingService(String targetServiceName, String serviceName);

	/**
	 * インデックス更新.
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgUpdateIndex(String serviceName);

	/**
	 * インデックス削除.
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgDeleteIndex(String serviceName);

	/**
	 * ログ出力.
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgWriteLog(String serviceName);

	/**
	 * グループ管理者登録のメッセージを取得.
	 * @param feed グループ管理者と管理するグループ情報
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgCreateGroupadmin(FeedBase feed, String serviceName);

	/**
	 * グループ管理グループ削除のメッセージを取得.
	 * @param feed 削除するグループ情報
	 * @param serviceName サービス名
	 * @return メッセージ
	 */
	public String getMsgDeleteGroupadmin(FeedBase feed, String serviceName);

}
