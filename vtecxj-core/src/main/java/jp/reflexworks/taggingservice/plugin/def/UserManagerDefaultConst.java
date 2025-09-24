package jp.reflexworks.taggingservice.plugin.def;

import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.UserUtil;

/**
 * ユーザ管理 定数クラス.
 */
public interface UserManagerDefaultConst {

	/** URN : auth パスワードの開始文字 */
	public static final String URN_AUTH_PASSWORD_START = UserUtil.URN_AUTH_PASSWORD_START;
	/** URN : auth + comma */
	public static final String URN_PREFIX_AUTH_COMMA = Constants.URN_PREFIX_AUTH + URN_AUTH_PASSWORD_START;

	/** アカウントが設定されている項目 */
	public static final String FIELD_ACCOUNT = "title";

	/** UID発行URI */
	public static final String URI_ADDIDS_UID = "/_uid";
	/** URI : changeaccount */
	public static final String URI_CHANGEACCOUNT = "/_changeaccount";
	/** URI : email */
	public static final String URI_EMAIL = "/email";
	/** URI : nickname */
	public static final String URI_NICKNAME = "/nickname";
	/** URI : phash */
	public static final String URI_PHASH = "/phash";
	/** URI : verify */
	public static final String URI_VERIFY = "/verify";
	/** URI : error_count */
	public static final String URI_ERROR_COUNT = "/error_count";

	/** WSSE Createdの未来時間チェック(分) */
	public static final int CREATED_AFTER_MINUTE = 5;
	/** WSSE Createdの過去時間チェック(分)(デフォルト値) */
	public static final int CREATED_BEFORE_MINUTE = 5;

	/**
	 * パスワードのbyte数=8
	 * base64化の手順
	 *   0. 8byte = 64bit
	 *   1. 6bitずつに分割。余りは0を追加 : 6bitの塊が11個
	 *   2. 変換表により、4文字ずつ変換。4文字に満たない場合は=を追加 : 12文字
	 * (2016.2.22) パスワード生成時はバイト数でなく文字数を指定するように変更。
	 */
	static final int PASSWORD_LEN = 12;

	/** usersecret生成時、SHA1ハッシュ(ランダム文字列生成用)前のバイト数 */
	public static final int USERSECRET_LEN = 24;
	/** 認証コードの文字数 デフォルト */
	public static final int VERIFY_CODE_LENGTH_DEFAULT = 6;
	/** 認証コード検証失敗可能回数 デフォルト **/
	public static final int VERIFY_FAILED_COUNT_DEFAULT = 10;
	/** メッセージ置き換え文字列 : 認証コード */
	public static final String REPLACE_REGEX_VERIFY = "\\$\\{VERIFY\\}";
	/** メッセージ置き換え文字列 : パスワード変更一時トークン */
	public static final String REPLACE_REGEX_PASSRESET_TOKEN = "\\$\\{PASSRESET_TOKEN\\}";
	/** パスワード変更で旧パスワード・一時トークンチェックを行わない旧バージョン */
	public static final String CHANGEPHASH_LEGACY = "true";
	/** パスワード変更一時トークンの文字数 */
	public static final int PASSRESET_TOKEN_LEN = 16;
	/** パスワード変更一時トークンのRedisキャッシュ格納キー接頭辞 */
	public static final String URI_CACHESTRING_PASSRESET_TOKEN_PREFIX = "/_#passreset_token/";

	/** ユーザ登録区分 : adduser、adduserByAdmin, adduserByGroupadmin, 外部連携 */
	enum AdduserType {USER, ADMIN, GROUPADMIN, LINK};

	/** 処理区分 : adduser、passreset、changepass、changeaccount */
	enum UserAuthType {ADDUSER, PASSRESET, CHANGEPASS, CHANGEACCOUNT};

	/** URN : oldphash */
	public static final String URN_PREFIX_OLDPHASH = Constants.URN_PREFIX + "oldphash:";
	/** URN : passreset_token */
	public static final String URN_PREFIX_PASSRESET_TOKEN = Constants.URN_PREFIX + "passreset_token:";

	/** 仮登録ユーザの仮削除時、アカウントの前につける文字列 */
	public static final String USER_NOTHING_PREFIX = "****";
	
	/** 外部連携によるユーザ登録で合わせて登録するEntryのURIについて、UID変換文字 */
	public static final String REGEX_CONVERT_UID = "#";

}
