package jp.reflexworks.taggingservice.oauth;

import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.util.Constants;

/**
 * TaggingService OAuth 定数クラス.
 */
public interface OAuthConst {

	/** プロパティ : secret発行リトライ総数 */
	public static final String OAUTH_CREATESECRET_RETRY_COUNT = "_oauth.createsecret.retry.count";
	/** プロパティ : プロバイダへのリクエストタイムアウト(ミリ秒) */
	public static final String OAUTH_REQUEST_TIMEOUT_MILLIS = "_oauth.request.timeout.millis";

	/** プロパティデフォルト値 : secret発行リトライ総数 */
	public static final int OAUTH_CREATESECRET_RETRY_COUNT_DEFAULT = 200;
	/** プロパティデフォルト値 : プロバイダへのリクエストタイムアウト(ミリ秒) */
	public static final int OAUTH_REQUEST_TIMEOUT_MILLIS_DEFAULT = 20000;

	/** URI階層 : oauth */
	public static final String ACTION_OAUTH = "oauth";
	/** URI階層 : callback */
	public static final String ACTION_CALLBACK = "callback";
	/** URI階層 : redirect */
	public static final String ACTION_REDIRECT = "redirect";
	/** URI階層 : link */
	public static final String ACTION_LINK = "link";
	/** URI階層 : mergeuser */
	public static final String ACTION_MERGEUSER = "mergeuser";
	/** URI階層 : oauthid */
	public static final String ACTION_OAUTHID = "oauthid";
	/** URI階層 : create_state */
	public static final String ACTION_CREATE_STATE = "create_state";
	/** URI階層 : check_state */
	public static final String ACTION_CHECK_STATE = "check_state";

	/** parameter : client_id */
	public static final String PARAM_CLIENT_ID = "client_id";
	/** parameter : client_secret */
	public static final String PARAM_CLIENT_SECRET = "client_secret";
	/** parameter : state */
	public static final String PARAM_STATE = "state";
	/** parameter : code */
	public static final String PARAM_CODE = "code";
	/** parameter : access_token */
	public static final String PARAM_ACCESS_TOKEN = "access_token";
	/** parameter : redirect_uri */
	public static final String PARAM_REDIRECT_URI = "redirect_uri";
	/** parameter : access_type */
	public static final String PARAM_ACCESS_TYPE = "access_type";
	/** parameter : scope */
	public static final String PARAM_SCOPE = "scope";
	/** parameter : response_type */
	public static final String PARAM_RESPONSE_TYPE = "response_type";
	/** parameter : grant_type */
	public static final String PARAM_GRANT_TYPE = "grant_type";
	/** parameter : input_token */
	public static final String PARAM_INPUT_TOKEN = "input_token";
	/** parameter : fields */
	public static final String PARAM_FIELDS = "fields";
	/** parameter : error */
	public static final String PARAM_ERROR = "error";
	/** parameter : oauth_token */
	public static final String PARAM_OAUTH_TOKEN = "oauth_token";
	/** parameter : oauth_verifier */
	public static final String PARAM_OAUTH_VERIFIER = "oauth_verifier";

	/** taggingservice parameter : _service */
	public static final String PARAM_SERVICE = RequestParam.PARAM_SERVICE;

	/** value : code */
	public static final String CODE = "code";
	/** value : openid */
	public static final String OPENID = "openid";
	/** value : authorization_code */
	public static final String AUTHORIZATION_CODE = "authorization_code";

	/** 認証ヘッダ : Basic */
	public static final String HEADER_AUTHORIZATION_BASIC = "Basic ";
	/** 認証ヘッダ : Bearer */
	public static final String HEADER_AUTHORIZATION_BEARER = "Bearer ";
	/** 認証ヘッダ : OAuth */
	public static final String HEADER_AUTHORIZATION_OAUTH = "OAuth ";
	/** 認証ヘッダ : Token */
	public static final String HEADER_AUTHORIZATION_TOKEN = ReflexServletConst.HEADER_AUTHORIZATION_TOKEN;

	/** OAuthProvider 実装クラスのパッケージ */
	public static final String OAUTHPROVIDER_PACKAGE = "jp.reflexworks.taggingservice.oauth.provider";
	/** OAuthProvider 実装クラス名の末尾 */
	public static final String OAUTHPROVIDER_CLASSNAME_SUFFIX = "Provider";
	/** OAuthProvider 実装クラス名の末尾 */
	public static final int OAUTHPROVIDER_CLASSNAME_SUFFIX_LEN = OAUTHPROVIDER_CLASSNAME_SUFFIX.length();

	/** secretの長さ */
	public static final int SECRET_LEN = 24;

	/** encoding */
	public static final String ENCODING = Constants.ENCODING;

	/** URI : /_oauthsecret (Redisに使用) */
	public static final String URI_OAUTHSECRET = "/_oauthsecret";
	/** URI : ユーザ識別子 */
	public static final String URI_OAUTH_ID = "/oauth_id";
	/** URI : email */
	public static final String URI_OAUTH_EMAIL = "/oauth_email";
	/** URI : /_oauth (データストアに使用) */
	public static final String URI_OAUTH = "/_oauth";
	/** URI : /oauth (データストアのエイリアスに使用) */
	public static final String URI_USER_OAUTH = "/oauth";

	/** message : 紐付けリクエスト依頼 */
	public static final String MSG_REQUIRE_LINK = "It is required to link social accounts with logged-in users.";
	/** message : 既存アカウントのパスワード確認リクエスト依頼 */
	public static final String MSG_REQUIRE_INPUTPASS = "It is required to input password.";
	/** message : 紐付けリクエストか、既存アカウントのパスワード確認リクエスト依頼 */
	public static final String MSG_REQUIRE_LINK_OR_INPUTPASS = "It is required to link social accounts with logged-in users or input password.";
	/** message : メール確認依頼 */
	public static final String MSG_REQUIRE_CONFIRM_EMAIL = "A confirmation email has been sent to your address.";
	/** message : メールアドレス登録リクエスト依頼 */
	public static final String MSG_REQUIRE_REGIST_EMAIL = "It is required to regist email.";
	/** message : 紐付けリクエスト完了 (パスワード確認も同じ) */
	public static final String MSG_COMPLETE_LINK = "The link is completed.";
	/** message : 紐付け削除リクエスト完了 */
	public static final String MSG_DELETE_OAUTHID = "Social account is deleted.";

	/** TaggingServiceアカウントの区切り文字 */
	public static final String MARK_ACCOUNT = "@@";
	/** 外部連携用ユーザ登録の追加EntryのキーのUIDを表す記号 */
	public static final String MARK_UID = "#";
	
}
