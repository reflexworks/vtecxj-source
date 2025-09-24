package jp.reflexworks.taggingservice.auth;

import java.util.Locale;

import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.taggingservice.util.Constants;

/**
 * 認証処理 定数クラス.
 */
public interface AuthenticatorConst {

	/** 設定 : ２段階認証(TOTP)登録のQRコードサイズ */
	public static final String PROP_TOTP_QRCODE_CHS = "_totp.qrcode.chs";
	/** 設定 : ２段階認証(TOTP)公開鍵の文字列長 */
	public static final String PROP_TOTP_SECRET_LENGTH = "_totp.secret.length";
	/** 設定 : 信頼できる端末に設定する値(TDID)の文字列長 */
	public static final String PROP_TDID_SECRET_LENGTH = "_tdid.secret.length";

	/** 設定デフォルト値 : ２段階認証(TOTP)登録のQRコードサイズ */
	public static final int TOTP_QRCODE_CHS_DEFAULT = 180;
	/** 設定デフォルト値 : ２段階認証(TOTP)公開鍵の文字列長 */
	public static final int TOTP_SECRET_LENGTH_DEFAULT = 24;
	/** 設定デフォルト値 : 信頼できる端末に設定する値(TDID)の文字列長 */
	public static final int TDID_SECRET_LENGTH_DEFAULT = 28;

	/** URI : totp */
	public static final String URI_TOTP = "/totp";
	/** URI : totp_temp */
	public static final String URI_TOTP_TEMP = "/totp_temp";
	/** URI : trusted_device */
	public static final String URI_TRUSTED_DEVICE = "/trusted_device";

	/** URN : secret */
	public static final String URN_PREFIX_SECRET = Constants.URN_PREFIX + "secret:";
	/** length of URN secret */
	public static final int URN_PREFIX_SECRET_LEN = URN_PREFIX_SECRET.length();

	/** リクエストヘッダ : TOTPワンタイムパスワード */
	public static final String HEADER_AUTHORIZATION = ReflexServletConst.HEADER_AUTHORIZATION;
	/** リクエストヘッダの値接頭辞 : TOTPワンタイムパスワード */
	public static final String HEADER_AUTHORIZATION_TOTP = "TOTP ";
	/** リクエストヘッダの値接頭辞の文字列長 : TOTPワンタイムパスワード */
	public static final int HEADER_AUTHORIZATION_TOTP_LEN = HEADER_AUTHORIZATION_TOTP.length();
	/** リクエストヘッダ : 信頼される端末に追加 */
	public static final String HEADER_X_TRUSTED_DEVICE = "X-TRUSTED-DEVICE";
	/** リクエストヘッダ : 信頼される端末に追加(小文字) */
	public static final String HEADER_X_TRUSTED_DEVICE_LOWER = HEADER_X_TRUSTED_DEVICE.toLowerCase(Locale.ENGLISH);
	/** リクエストヘッダの値 : 信頼される端末に追加 */
	public static final String X_TRUSTED_DEVICE_VALUE = "true";

	/** Cookie key : TDID (信頼できる端末に指定する値) */
	public static final String TDID = "TDID";
	/** Cookie MaxAge(秒) : TDID */
	public static final int MAXAGE_TDID = 315532800;	// 約10年

	/** セッションに設定する仮認証フラグキー */
	public static final String SESSION_KEY_TEMPAUTH = "TEMP_AUTH";
	/** セッションに設定する仮認証フラグの値 */
	public static final long SESSION_VALUE_TEMPAUTH = 1L;

	/** 仮認証中のメッセージ */
	public static final String MSG_TEMP_AUTH = "Please send a one-time password.";
	/** 本登録完了メッセージ */
	public static final String MSG_CREATE_TOTP = "Two-factor authentication has been registed.";
	/** TOTP削除メッセージ */
	public static final String MSG_DELETE_TOTP = "Two-factor authentication has been deleted.";
	/** 信頼できる端末に指定する値(TDID)の更新メッセージ */
	public static final String MSG_CHANGE_TDID = "Trusted device id (tdid) has been changed.";

	/** URLパラメータ : ２段階認証(TOTP)登録のQRコードサイズ */
	public static final String PARAM_CHS = "_chs";

}
