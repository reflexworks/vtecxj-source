package jp.reflexworks.taggingservice.plugin;

import java.io.IOException;
import java.util.List;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.servlet.util.WsseAuth;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.exception.AuthenticationException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.sys.SystemContext;

/**
 * ユーザ管理インターフェース.
 */
public interface UserManager extends ReflexPlugin {

	/**
	 * ログインユーザのユーザ情報Entryを取得
	 * @param reflexContext ReflexContext
	 * @return ログインユーザのユーザ情報Entry
	 */
	public FeedBase whoami(ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * アカウントからUIDを取得.
	 * @param account アカウント
	 * @param systemContext SystemContext
	 * @return UID
	 */
	public String getUidByAccount(String account, SystemContext systemContext)
	throws IOException, TaggingException;

	/**
	 * UIDからアカウントを取得.
	 * @param UID アカウント
	 * @param systemContext SystemContext
	 * @return アカウント
	 */
	public String getAccountByUid(String account, SystemContext systemContext)
	throws IOException, TaggingException;

	/**
	 * アカウントからユーザトップエントリーを取得します.
	 * <p>
	 * ユーザトップエントリーのURIはUIDのため、ユーザ名を検索条件にルートエントリー配下をフィード検索します.
	 * </p>
	 * @param account アカウント
	 * @param systemContext SystemContext
	 * @return ユーザトップエントリー
	 */
	public EntryBase getUserTopEntryByAccount(String account,
			SystemContext systemContext)
	throws IOException, TaggingException;

	/**
	 * UIDからユーザトップエントリーを取得します.
	 * @param uid UID
	 * @param reflexContext ReflexContext
	 * @return ユーザトップエントリー
	 */
	public EntryBase getUserTopEntryByUid(String uid,
			ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * UIDからユーザ認証エントリーを取得します.
	 * @param uid UID
	 * @param reflexContext ReflexContext
	 * @return ユーザ認証エントリー
	 */
	public EntryBase getUserAuthEntryByUid(String uid, ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * ログインユーザのRXIDを生成
	 * @param reflexContext ReflexContext
	 * @return RXID
	 */
	public String createRXID(ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * 指定されたアカウントのRXIDを生成
	 * @param account アカウント
	 * @param systemContext SystemContext
	 * @return RXID
	 */
	public String createRXIDByAccount(String account, SystemContext systemContext)
	throws IOException, TaggingException;

	/**
	 * WSSE認証.
	 * @param wsseAuth WSSE認証情報
	 * @param req リクエスト
	 * @param systemContext SystemContext
	 * @throws AuthenticationException WSSE認証エラー
	 */
	public void checkWsse(WsseAuth wsseAuth, ReflexRequest req, SystemContext systemContext)
	throws IOException, TaggingException;

	/**
	 * ユーザ登録.
	 * @param feed ユーザ登録情報
	 * @param reflexContext ReflexContext
	 * @return ユーザのトップエントリー
	 */
	public EntryBase adduser(FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * 管理者によるユーザ登録.
	 * @param feed ユーザ登録情報(複数ユーザ対応)
	 * @param reflexContext ReflexContext
	 * @return ユーザのトップエントリーリスト
	 */
	public FeedBase adduserByAdmin(FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * グループ管理者によるユーザ登録.
	 * @param feed ユーザ登録情報(複数ユーザ対応)
	 * @param groupName グループ名
	 * @param reflexContext ReflexContext
	 * @return ユーザのトップエントリーリスト
	 */
	public FeedBase adduserByGroupadmin(FeedBase feed, String groupName, ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * 外部連携によるユーザ登録.
	 * ユーザ1件を登録する。
	 * @param account アカウント
	 * @param nickname ニックネーム
	 * @param feed ユーザ登録情報(任意)。キーの#はUIDに置き換える。
	 * @param reflexContext ReflexContext
	 * @return ユーザのトップエントリー
	 */
	public EntryBase adduserByLink(String account, String nickname, 
			FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * ユーザステータスに対応した処理を行う.
	 *  ・Interimの場合、本登録処理を行う。
	 *  ・Activateの場合処理を抜ける。
	 * その他の場合、ユーザステータスエラー
	 * @param auth 認証情報
	 * @param reflexContext ReflexContext
	 */
	public void processByUserstatus(ReflexAuthentication auth, ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * パスワードリセットのためのメール送信.
	 * @param feed パスワードリセット情報
	 * @param reflexContext ReflexContext
	 * @return ユーザのトップエントリー
	 */
	public EntryBase passreset(FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * パスワード更新.
	 * @param feed パスワード更新情報
	 * @param reflexContext ReflexContext
	 * @return ユーザのトップエントリー
	 */
	public EntryBase changepass(FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * 管理者によるパスワード更新.
	 * @param feed パスワード更新情報
	 * @param reflexContext ReflexContext
	 * @return 更新情報
	 */
	public FeedBase changepassByAdmin(FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * アカウント更新のためのメール送信.
	 * @param feed アカウント更新情報
	 * @param reflexContext ReflexContext
	 */
	public void changeaccount(FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * アカウント更新.
	 * @param verifyCode 認証コード
	 * @param reflexContext ReflexContext
	 * @return ユーザのトップエントリー
	 */
	public EntryBase verifyChangeaccount(String verifyCode, ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * アクセストークンを取得.
	 * @param reflexContext ReflexContext
	 * @return アクセストークン
	 */
	public String getAccessToken(ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * リンクトークンを取得.
	 * @param uri URI
	 * @param reflexContext ReflexContext
	 * @return リンクトークン
	 */
	public String getLinkToken(String uri, ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * アクセスキー更新.
	 * @param reflexContext ReflexContext
	 */
	public void changeAccessKey(ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * ユーザのトップエントリーURIを取得.
	 * @param uid UID
	 * @return ユーザのトップエントリーURI
	 */
	public String getUserTopUriByUid(String uid);

	/**
	 * ユーザの認証エントリーURIを取得.
	 * @param uid UID
	 * @return ユーザの認証エントリーURI
	 */
	public String getUserAuthUriByUid(String uid);

	/**
	 * グループ情報を格納する親キーを取得します.
	 * <p>
	 * 認証情報は、設定情報「group.uri」で設定したパターンから作成したキーのエントリーに格納されます。
	 * 設定がない場合はnullを返却します。
	 * グループの親キーは「/_user/{uid}/group」です。
	 * </p>
	 * @param uid UID
	 * @return グループ情報を格納する親キー
	 */
	public String getGroupUriByUid(String uid);

	/**
	 * グループ情報を取得します.
	 * @param uid UID
	 * @param reflexContext ReflexContext
	 * @return グループリスト
	 */
	public List<String> getGroupsByUid(String uid, ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * 指定したURI配下のキーのエントリーで自分が署名していないものを取得.
	 * ただしすでにグループ参加状態のものは除く。
	 * @param uri 親キー
	 * @param reflexContext ReflexContext
	 * @return 親キー配下のエントリーで署名していないEntryリスト
	 */
	public FeedBase getNoGroupMember(String uri, ReflexContext reflexContext) 
	throws IOException, TaggingException;
	
	/**
	 * グループに参加登録する.
	 * グループ参加エントリーの登録処理。署名はなし。
	 * @param group グループ名
	 * @param selfid 自身のグループエイリアスのselfid (/_user/{UID}/group/{selfid})
	 * @param feed UIDリスト(ユーザ管理者・グループ管理者のみ指定可)
	 * @param reflexContext ReflexContext
	 * @return グループエントリー
	 */
	public FeedBase addGroup(String group, String selfid, FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * グループに参加署名する.
	 * グループ作成者がユーザへの参加承認をしている状態の時、ユーザがグループに参加する。
	 * 自身のグループエイリアス作成(登録がない場合→再加入を想定)と、自身のグループエイリアスへの署名を行う。
	 * @param group グループ名
	 * @param selfid 自身のグループエイリアスのselfid (/_user/{UID}/group/{selfid})
	 * @param reflexContext ReflexContext
	 * @return グループエントリー
	 */
	public EntryBase joinGroup(String group, String selfid, ReflexContext reflexContext)
	throws IOException, TaggingException;
	
	/**
	 * グループから退会する.
	 * グループエントリーの、自身のグループエイリアスを削除する。
	 * @param group グループ名
	 * @param reflexContext ReflexContext
	 * @return 退会したグループエントリー
	 */
	public EntryBase leaveGroup(String group, ReflexContext reflexContext)
	throws IOException, TaggingException;
	
	/**
	 * グループ参加エントリー削除.
	 * @param group グループ名
	 * @param feed UIDリスト(ユーザ管理者・グループ管理者のみ指定可)
	 * @param reflexContext ReflexContext
	 * @return 退会したグループエントリー
	 */
	public FeedBase removeGroup(String group, FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * アクセスキーを格納するキーを取得します.
	 * @param uid UID
	 * @return アクセスキーを格納するキー
	 */
	public String getAccessKeyUriByUid(String uid);

	/**
	 * URIからUIDを取得します.
	 * @param uri URI
	 * @return UID
	 */
	public String getUidByUri(String uri);

	/**
	 * ユーザ登録リクエストのエントリーから、メールアドレスを取得します。
	 * <p>
	 * ユーザエントリーのuriに設定する、以下の形式の文字列からメールアドレスを取り出します。
	 * パスワード設定が無い場合もエラーとしません。
	 * <ul>
	 * <li>urn:vte.cx:auth:{email},{password}</li>
	 * </ul>
	 * </p>
	 * @param entry ユーザ登録リクエストのエントリー
	 * @return メールアドレス
	 */
	public String getEmailByAdduser(EntryBase entry);

	/**
	 * UIDからパスワードを取得します.
	 * @param uid UID
	 * @param systemContext SystemContext
	 * @return パスワード
	 * @throws IOException
	 * @throws TaggingException
	 */
	public String getPasswordByUid(String uid, SystemContext systemContext)
	throws IOException, TaggingException;

	/**
	 * Entryからパスワードを抽出します.
	 * @param entry Entry
	 * @return パスワード
	 */
	public String getPassword(EntryBase entry);

	/**
	 * アカウントを取得.
	 * UIDを元にユーザトップエントリーを検索し、アカウントを取得する。
	 * @param uid UID
	 * @param reflexContext ReflexContext
	 * @return アカウント
	 */
	public String getAccountByUid(String uid, ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * ニックネームを取得.
	 * @param uid UID
	 * @param reflexContext ReflexContext
	 * @return ニックネーム
	 */
	public String getNicknameByUid(String uid, ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * メールアドレスを取得.
	 * @param uid UID
	 * @param reflexContext ReflexContext
	 * @return メールアドレス
	 */
	public String getEmailByUid(String uid, ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * ユーザトップエントリーから、メールアドレスを取得します。
	 * @param entry ユーザトップエントリー
	 * @return メールアドレス
	 */
	public String getEmail(EntryBase entry);

	/**
	 * アカウントからユーザステータスを取得.
	 * ユーザ管理者権限、グループ管理権限がある場合のみ実行可。(ユーザトップエントリーのACL設定に基づく)
	 * <p>
	 * <ul>
	 * <li>登録なし : Nothing</li>
	 * <li>仮登録 : Interim</li>
	 * <li>本登録 : Activated</li>
	 * <li>無効 : Revoked</li>
	 * </ul>
	 * </p>
	 * @param account アカウント
	 * @param reflexContext ReflexContext
	 * @return ユーザステータスのEntry
	 */
	public EntryBase getUserstatusByAccount(String account, ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * UIDからユーザステータスを取得.
	 * ユーザ管理者権限がある場合のみ実行可。
	 * <p>
	 * <ul>
	 * <li>登録なし : Nothing</li>
	 * <li>仮登録 : Interim</li>
	 * <li>本登録 : Activated</li>
	 * <li>無効 : Revoked</li>
	 * </ul>
	 * </p>
	 * @param uid UID
	 * @param systemContext ReflexContext
	 * @return ユーザステータスのEntry
	 */
	public EntryBase getUserstatusByUid(String uid, SystemContext systemContext)
	throws IOException, TaggingException;

	/**
	 * ユーザステータス保持Entryからユーザステータスを取得.
	 * <p>
	 * <ul>
	 * <li>登録なし : Nothing</li>
	 * <li>仮登録 : Interim</li>
	 * <li>本登録 : Activated</li>
	 * <li>無効 : Revoked</li>
	 * </ul>
	 * </p>
	 * @param entry ユーザステータス保持Entry
	 * @return ユーザステータス
	 */
	public String getUserstatus(EntryBase entry);

	/**
	 * ユーザステータス一覧を取得.
	 * ユーザ管理者権限がある場合のみ実行可。
	 * <p>
	 * <ul>
	 * <li>登録なし : Nothing</li>
	 * <li>仮登録 : Interim</li>
	 * <li>本登録 : Activated</li>
	 * <li>無効 : Revoked</li>
	 * <li>退会 : Cancelled</li>
	 * </ul>
	 * </p>
	 * @param limit 一覧件数。nullの場合はデフォルト値。
	 * @param cursorStr カーソル
	 * @param reflexContext ReflexContext
	 * @return ユーザステータス一覧。ユーザ数が一覧が多い場合はカーソルを返す。
	 */
	public FeedBase getUserstatusList(Integer limit, String cursorStr,
			ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * ユーザ権限剥奪.
	 * @param feed アカウント(entryのtitle)、またはUID(entryのlink selfのhref)
	 * @param isDeleteGroups RevokedまたはCancelledに変更時、同時にグループを削除する場合true
	 * @param reflexContext ReflexContext
	 * @return 処理対象ユーザのステータスEntryを格納したFeed
	 */
	public FeedBase revokeUser(FeedBase feed, boolean isDeleteGroups, ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * ユーザを有効にする.
	 * @param feed アカウント(entryのtitle)、またはUID(entryのlink selfのhref)
	 * @param reflexContext ReflexContext
	 * @return 処理対象ユーザのステータスEntryを格納したFeed
	 */
	public FeedBase activateUser(FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * ユーザを削除する.
	 * @param feed アカウント(entryのtitle)、またはUID(entryのlink selfのhref)
	 * @param async 削除を非同期に行う場合true
	 * @param reflexContext ReflexContext
	 * @return 処理対象ユーザEntryを格納したFeed
	 */
	public FeedBase deleteUser(FeedBase feed, boolean async, ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * ユーザ退会.
	 * @param feed アカウント(entryのtitle)、またはUID(entryのlink selfのhref)
	 * @param isDeleteGroups RevokedまたはCancelledに変更時、同時にグループを削除する場合true
	 * @param reflexContext ReflexContext
	 * @return 処理対象ユーザのステータスEntryを格納したFeed
	 */
	public FeedBase cancelUser(FeedBase feed, boolean isDeleteGroups, ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * ユーザ初期設定に共通して必要なURIリスト.
	 * @param uid UID
	 * @return URIリスト
	 */
	public List<String> getUserSettingEntryUris(String uid);

	/**
	 * ユーザ初期設定に共通して必要なURIリスト.
	 * @param uid UID
	 * @return URIリスト
	 */
	public List<String> getUserSettingFeedUris(String uid);

	/**
	 * ２段階認証(TOTP)登録.
	 * @param req リクエスト
	 * @param reflexContext ReflexContext
	 * @return ２段階認証のための情報
	 */
	public FeedBase createTotp(ReflexRequest req, ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * ２段階認証(TOTP)削除.
	 * @param account ２段階認証削除アカウント
	 * @param reflexContext ReflexContext
	 * @return ２段階認証削除情報
	 */
	public FeedBase deleteTotp(String account, ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * ２段階認証(TOTP)参照.
	 * @param req リクエスト
	 * @param reflexContext ReflexContext
	 * @return ２段階認証情報
	 */
	public FeedBase getTotp(ReflexRequest req, ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * 信頼できる端末にセットする値(TDID)の更新.
	 * @param auth 認証情報
	 * @param reflexContext ReflexContext
	 * @return 信頼できる端末にセットする値(TDID)の更新情報
	 */
	public FeedBase changeTdid(ReflexAuthentication auth, ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * グループ管理者登録.
	 * @param feed グループ管理者情報(複数指定可)
	 * @param reflexContext ReflexContext
	 * @return 
	 */
	public FeedBase createGroupadmin(FeedBase feed, ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * グループ管理用グループの削除.
	 * @param feed グループ管理用グループ情報(複数指定可)
	 * @param async 削除を非同期に行う場合true
	 * @param reflexContext ReflexContext
	 */
	public void deleteGroupadmin(FeedBase feed, boolean async, ReflexContext reflexContext)
	throws IOException, TaggingException;

}
