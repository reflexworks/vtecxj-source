package jp.reflexworks.taggingservice.api;

import java.util.List;

import jp.reflexworks.atom.api.Condition;

/**
 * 検索条件保持クラス.
 * <p>
 * 検索で指定する各種設定を保持するクラスです。<br>
 * URLパラメータに指定する項目も定義しています。<br>
 * </p>
 */
public interface RequestParam extends RequestType {

	// 一般的なパラメータはRequestType (interface) に移動。

	/** URLパラメータ : モニタリングオプション */
	public static final String PARAM_MONITOR = "_monitor";
	/** URLパラメータ : 件数取得無制限 */
	public static final String PARAM_NOLIMIT = "_nolimit";
	/** URLパラメータ : データストアキャッシュを使用しない */
	public static final String PARAM_NOCACHE = "_nocache";
	/** URLパラメータ : データストアキャッシュを使用する */
	public static final String PARAM_USECACHE = "_usecache";
	/** URLパラメータ : データストアキャッシュを更新する(データパッチ用) */
	public static final String PARAM_REFRESHCACHE = "_refreshcache";
	/** URLパラメータ : インデックスをすべて更新(データパッチ用) */
	public static final String PARAM_UPDATEALLINDEX = "_updateallindex";
	/** キーソート指定 */
	public static final String KEYSORT = "@key";
	/** URLパラメータ : インデックス部分更新 */
	public static final String PARAM_UPDATEINDEX = "_updateindex";
	/** URLパラメータ : インデックス部分削除 */
	public static final String PARAM_DELETEINDEX = "_deleteindex";
	/** URLパラメータ : インデックス使用チェック */
	public static final String PARAM_CHECKINDEX = "_checkindex";
	/** URLパラメータ : サーバの追加 */
	public static final String PARAM_ADDSERVER = "_addserver";
	/** URLパラメータ : サーバの削除 */
	public static final String PARAM_REMOVESERVER = "_removeserver";
	/** URLパラメータ : キャッシュの全データクリア(メンテナンス用) */
	public static final String PARAM_CACHEFLUSHALL = "_cacheflushall";
	/** URLパラメータ : ２段階認証(TOTP)登録 */
	public static final String PARAM_CREATETOTP = "_createtotp";
	/** URLパラメータ : ２段階認証(TOTP)削除 */
	public static final String PARAM_DELETETOTP = "_deletetotp";
	/** URLパラメータ : ２段階認証(TOTP)参照 */
	public static final String PARAM_GETTOTP = "_gettotp";
	/** URLパラメータ : 信頼される端末に指定する値(TDID)の更新 */
	public static final String PARAM_CHANGETDID = "_changetdid";
	/** URLパラメータ : アプリリダイレクト */
	public static final String PARAM_REDIRECT_APP = "_redirect_app";
	/** URLパラメータ : パスワード変更一時トークン */
	public static final String PARAM_PASSRESET_TOKEN = "_passreset_token";
	/** URLパラメータ : グループの署名なしリスト取得 */
	public static final String PARAM_NO_GROUP_MEMBER = "_no_group_member";
	/** URLパラメータ : グループ取得 */
	public static final String PARAM_GROUP = "_group";
	/** URLパラメータ : グループメンバーかどうか判定 */
	public static final String PARAM_IS_GROUP_MEMBER = "_is_group_member";
	/** URLパラメータ : グループ参加署名 */
	public static final String PARAM_JOINGROUP = "_joingroup";
	/** URLパラメータ : グループ退会 */
	public static final String PARAM_LEAVEGROUP = "_leavegroup";
	/** URLパラメータ : 管理者によるグループ退会 */
	public static final String PARAM_LEAVEGROUP_BYADMIN = "_leavegroupByAdmin";
	/** URLパラメータ : selfid */
	public static final String PARAM_SELFID = "_selfid";
	/** URLパラメータ : PDF生成 */
	public static final String PARAM_PDF = "_pdf";
	/** URLパラメータ : メッセージキュー未送信チェック */
	public static final String PARAM_CHECK_MQ = "_check_mq";
	/** URLパラメータ : BDBQリトライチェック */
	public static final String PARAM_CHECK_BDBQ = "_check_bdbq";
	/** URLパラメータ : 既存ユーザとソーシャルログインユーザを紐付ける */
	public static final String PARAM_MERGEOAUTHUSER = "_mergeoauthuser";
	/** URLパラメータ : サイズ指定コンテンツ登録 */
	public static final String PARAM_BYSIZE = "_bysize";
	/** URLパラメータ : 拡張子 */
	public static final String PARAM_EXT = "_ext";
	/** URLパラメータ : 署名付きURL取得 */
	public static final String PARAM_SIGNEDURL = "_signedurl";
	/** URLパラメータ : グループ管理者によるユーザ登録オプション */
	public static final String PARAM_ADDUSER_BYGROUPADMIN = "_adduserByGroupadmin";
	/** URLパラメータ : グループ参加登録(署名はなし) */
	public static final String PARAM_ADDGROUP = "_addgroup";
	/** URLパラメータ : 管理者によるグループ参加登録(署名はなし) */
	public static final String PARAM_ADDGROUP_BYADMIN = "_addgroupByAdmin";
	/** URLパラメータ : グループ管理者作成 */
	public static final String PARAM_CREATEGROUPADMIN = "_creategroupadmin";
	/** URLパラメータ : グループ管理用グループ削除 */
	public static final String PARAM_DELETEGROUPADMIN = "_deletegroupadmin";
	/** URLパラメータ : グループ削除(revokeuserまたはcanceluserで使用) */
	public static final String PARAM_DELETEGROUP = "_deletegroup";

	/**
	 * Entry検索かどうか返却します.
	 * @return Entry検索の場合true
	 */
	public boolean isEntry();

	/**
	 * Feed検索かどうか返却します.
	 * @return Feed検索の場合true
	 */
	public boolean isFeed();

	/**
	 * レスポンスフォーマットを返却
	 * <p>
	 * <ul>
	 *   <li>1 : XML</li>
	 *   <li>2 : JSON</li>
	 *   <li>3 : MessagePack</li>
	 *   <li>4 : multipart/form-data</li>
	 *   <li>0 : Text</li>
	 * </ul>
	 * </p>
	 * @return レスポンスフォーマット
	 */
	public int getFormat();

	/**
	 * URL前方一致指定かどうかを取得.
	 * @return PathInfoの末尾に"*"が設定されている場合true
	 */
	public boolean isUrlForwardMatch();

	/**
	 * 検索条件リストを取得.
	 * @return 検索条件リスト
	 */
	public List<List<Condition>> getConditionsList();

	/**
	 * ソート条件を取得.
	 * @return ソート条件
	 */
	public Condition getSort();

	/**
	 * オプションの値を指定.
	 * ReflexContext内で不要なオプションを削除したり、必要なオプションをセットしたりするのに使用。
	 * @param name オプション名
	 * @param val 値
	 */
	public void setOption(String name, String val);

}
