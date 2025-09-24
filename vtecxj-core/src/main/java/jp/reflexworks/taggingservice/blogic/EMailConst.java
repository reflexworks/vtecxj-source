package jp.reflexworks.taggingservice.blogic;

import jp.reflexworks.atom.api.MailConst;

/**
 * メール送信に使用する定数クラス.
 */
public interface EMailConst extends MailConst {

	// TaggingService設定値
	/** プロパティ : 送信元メールアドレス */
	public static final String PROP_U_FROM = "_" + PROP_FROM;
	/** プロパティ : 送信元名 */
	public static final String PROP_U_FROM_PERSONAL = "_" + PROP_FROM_PERSONAL;
	/** プロパティ : 認証アカウント */
	public static final String PROP_U_USER = "_" + PROP_USER;
	/** プロパティ : 認証パスワード */
	public static final String PROP_U_PASSWORD = "_" + PROP_PASSWORD;
	/** プロパティ : SMTPサーバ */
	public static final String PROP_U_SMTP_HOST = "_" + PROP_SMTP_HOST;
	/** プロパティ : SMTPポート番号 */
	public static final String PROP_U_SMTP_PORT = "_" + PROP_SMTP_PORT;
	/** プロパティ : STARTTLSを行うかどうか */
	public static final String PROP_U_SMTP_STARTTLS = "_" + PROP_SMTP_STARTTLS;
	/** プロパティ : プロトコル(smtp or smtps) */
	public static final String PROP_U_TRANSPORT_PROTOCOL = "_" + PROP_TRANSPORT_PROTOCOL;
	/** プロパティ : 認証を行うかどうか */
	public static final String PROP_U_SMTP_AUTH = "_" + PROP_SMTP_AUTH;

	// 置換項目
	/** メッセージ置き換え文字列 : URL */
	public static final String REPLACE_REGEX_URL = "\\$\\{URL\\}";
	/** メッセージ置き換え文字列 : RXID (URL付加しない) */
	public static final String REPLACE_REGEX_RXID = "\\$\\{RXID\\}";
	/** メッセージ置き換え文字列 : RXID */
	public static final String REPLACE_RXID_PREFIX = "${RXID=";
	/** メッセージ置き換え文字列 : リンクトークン */
	public static final String REPLACE_LINK_PREFIX = "${LINK=";
	/** メッセージ置き換え文字列の終端 */
	public static final String REPLACE_SUFFIX = "}";
	/** メッセージ置き換え文字列RXIDの文字列長 */
	public static final int REPLACE_RXID_PREFIX_LEN = REPLACE_RXID_PREFIX.length();
	/** メッセージ置き換え文字列リンクトークンの文字列長 */
	public static final int REPLACE_LINK_PREFIX_LEN = REPLACE_LINK_PREFIX.length();
	/** メッセージ置き換え文字列 : UID */
	public static final String REPLACE_UID = "#";

}
