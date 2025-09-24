package jp.reflexworks.taggingservice.util;

import java.util.Enumeration;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;

import jp.reflexworks.atom.api.AtomConst;
import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.servlet.util.AuthTokenUtil;
import jp.reflexworks.servlet.util.WsseAuth;
import jp.reflexworks.servlet.util.WsseUtil;
import jp.sourceforge.reflex.util.NumberingUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * ユーザ設定に関するユーティリティ.
 */
public class UserUtil {
	
	/** ユーザ初期処理完了 */
	public static final int URN_PREFIX_AUTH_LEN = AtomConst.URN_PREFIX_AUTH.length();
	/** 設定 login.dispatch でサービスを表す文字 */
	public static final String LOGIN_DISPATCH_SERVICE = "#";

	/** URN : auth パスワードの開始文字 */
	public static final String URN_AUTH_PASSWORD_START = ",";
	/** URN : auth + comma */
	public static final String URN_PREFIX_AUTH_COMMA = AtomConst.URN_PREFIX_AUTH + URN_AUTH_PASSWORD_START;
	/** URN : auth + comma length */
	public static final int URN_PREFIX_AUTH_COMMA_LEN = URN_PREFIX_AUTH_COMMA.length();

	/** usersecret生成時、SHA1ハッシュ(ランダム文字列生成用)前のバイト数 */
	public static final int USERSECRET_LEN = 24;
	public static final int URN_PREFIX_USERSECRET_LEN = 
			Constants.URN_PREFIX_USERSECRET.length();


	/** アカウント使用可能文字 : 英数字、ハイフン(-)、アンダースコア(_)、@、$、ドット(.) */
	private static final String REGEX_ACCOUNTCHK = "[^A-Za-z0-9\\-_@\\$\\.]";
	/** アカウント使用不可文字の置き換え文字 */
	private static final String ACCOUNTCHK_CHANGECHAR = "";
	
	/**
	 * リクエストからWSSE情報を取得.
	 * @param req リクエスト
	 * @return WSSE情報
	 */
	public static WsseAuth getWsseAuth(HttpServletRequest req) {
		WsseUtil wsseUtil = new WsseUtil();
		WsseAuth wsseAuth = wsseUtil.getWsseAuth(req);
		return editWsseAuth(wsseAuth);
	}
	
	/**
	 * WSSE情報を編集.
	 * @param wsseAuth WSSE情報
	 * @return
	 */
	private static WsseAuth editWsseAuth(WsseAuth wsseAuth) {
		if (wsseAuth != null && !StringUtils.isBlank(wsseAuth.username)) {
			String currentUsername = wsseAuth.username;
			String suffix = null;
			int idx = currentUsername.indexOf(AuthTokenUtil.RXIDNAME_SEPARATOR);
			if (idx < 0) {
				suffix = "";
			} else {
				suffix = currentUsername.substring(idx);
				currentUsername = currentUsername.substring(0, idx);
			}
			String account = editAccount(currentUsername);
			wsseAuth.username = account + suffix;
		}
		return wsseAuth;
	}
	
	/**
	 * メールアドレスをアカウントに変換します。
	 * <p>
	 * 1. 大文字を小文字にします。
	 * 2. アカウント使用可能文字以外を空文字にします。
	 * </p>
	 */
	public static String editAccount(String username) {
		String account = null;
		if (username != null) {
			account = username.toLowerCase(Locale.ENGLISH);
			account = account.replaceAll(REGEX_ACCOUNTCHK, ACCOUNTCHK_CHANGECHAR);
			
			// アカウントの@以前について、"."を空文字にする。
			int idx = account.indexOf("@");
			if (idx > 0) {
				String part1 = account.substring(0, idx);
				String part2 = account.substring(idx);
				part1 = part1.replace(".", ACCOUNTCHK_CHANGECHAR);
				account = part1 + part2;
			}
		}
		return account;
	}

	/**
	 * ランダムな文字列を生成します
	 * @param len バイト数
	 * @return 生成された文字列
	 */
	public static String createRandomString(int len) {
		return NumberingUtil.randomString(len);
	}

	/**
	 * 認証情報エントリーに設定するWSSE情報の文字列を取得します.
	 * <p>
	 * /{UID}/_auth エントリーのuriに設定する、以下の形式のWSSE情報文字列を作成します。
	 * <ul>
	 *   <li>urn:vte.cx:auth:,{password}</li>
	 * </ul>
	 * (2015.2.19)usernameは /@{ログインサービス名}/{UID} エントリーのtitleの値とするよう変更。
	 * </p>
	 * @param password パスワード
	 * @return 認証情報エントリーに設定するWSSE情報の文字列
	 */
	public static String createAuthUrn(String password) {
		StringBuilder sb = new StringBuilder();
		sb.append(URN_PREFIX_AUTH_COMMA);
		sb.append(StringUtils.null2blank(password));
		return sb.toString();
	}
	
	/**
	 * リクエストヘッダの「Authorization」から指定されたサブキーの値を取得.
	 *   例)「Authorization: Token {token}」の{token}部分を取得する。
	 * @param req リクエスト
	 * @param subKey サブキー
	 * @return 値
	 */
	public static String getAuthorization(HttpServletRequest req, String subKey) {
		return getHeader(req, ReflexServletConst.HEADER_AUTHORIZATION, subKey);
	}
	
	/**
	 * リクエストヘッダから指定されたサブキーの値を取得.
	 *   例)「Authorization: Token {token}」の{token}部分を取得する。
	 * @param req リクエスト
	 * @param key キー
	 * @param subKey サブキー
	 * @return 値
	 */
	public static String getHeader(HttpServletRequest req, String key, String subKey) {
		if (req == null || StringUtils.isBlank(key) || StringUtils.isBlank(subKey)) {
			return null;
		}
		Enumeration<String> enu = req.getHeaders(key);
		if (enu == null) {
			return null;
		}
		while (enu.hasMoreElements()) {
			String tmp = enu.nextElement();
			if (tmp.startsWith(subKey)) {
				return tmp.substring(subKey.length());
			}
		}
		return null;
	}

}
