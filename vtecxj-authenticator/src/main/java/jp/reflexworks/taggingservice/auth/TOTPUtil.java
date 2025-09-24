package jp.reflexworks.taggingservice.auth;

import java.io.IOException;
import java.net.URLEncoder;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;

import jp.reflexworks.taggingservice.util.Constants;

/**
 * TOTP (Time-based One Time Password)
 */
public class TOTPUtil {

	/**
	 * 公開鍵を生成.
	 * Google Authenticatorへ送る公開鍵の英字は大文字でないとエラーになる。(iPhoneで確認)
	 * @return 公開鍵
	 */
	public static String createTotpSecret() {
		GoogleAuthenticator gAuth = new GoogleAuthenticator();
		GoogleAuthenticatorKey key = gAuth.createCredentials();
		return key.getKey();
	}

	/**
	 * ２段階認証をGoogle Authenticatorに登録するためのQRコードURLを取得.
	 * @param totpSecret 公開鍵
	 * @param account アカウント
	 * @param chs QRコードサイズ
	 * @param serviceName サービス名
	 * @return ２段階認証をGoogle Authenticatorに登録するためのQRコードURL
	 */
	public static String getTotpQRcodeUrl(String totpSecret, String account, int chs,
			String serviceName)
	throws IOException {
		// request uri
		// otpauth://totp/{サービス名}:{アカウント}?secret={公開鍵}&issuer={サービス名}
		StringBuilder sb = new StringBuilder();
		sb.append("otpauth://totp/");
		sb.append(serviceName);
		sb.append(":");
		sb.append(account);
		sb.append("?secret=");
		sb.append(totpSecret);
		sb.append("&issuer=");
		sb.append(serviceName);
		String chl = URLEncoder.encode(sb.toString(), Constants.ENCODING);

		// request url
		// https://chart.googleapis.com/chart?chs={QRコードサイズ}x{QRコードサイズ}&cht=qr&chl=「otpauth://totp/{サービス名}:{アカウント}?secret={公開鍵}&issuer={サービス名}」をURLEncodeした文字列
		StringBuilder url = new StringBuilder();
		url.append("https://chart.googleapis.com/chart?chs=");
		url.append(chs);
		url.append("x");
		url.append(chs);
		url.append("&cht=qr&chl=");
		url.append(chl);

		return url.toString();
	}

	/**
	 * TOTPワンタイムパスワード検証
	 * @param totpSecret 公開鍵
	 * @param onetimePasswordStr ワンタイムパスワード
	 * @return 検証OKの場合true
	 */
	public static boolean verifyOnetimePassword(String totpSecret, int onetimePassword) {
		GoogleAuthenticator gAuth = new GoogleAuthenticator();
		return gAuth.authorize(totpSecret, onetimePassword);
	}

}
