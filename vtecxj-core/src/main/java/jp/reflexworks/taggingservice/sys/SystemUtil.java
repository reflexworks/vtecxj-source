package jp.reflexworks.taggingservice.sys;

import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.model.RequestInfoImpl;

/**
 * システム処理で使用するユーティリティ
 */
public class SystemUtil {

	/**
	 * システム処理用リクエスト情報を取得.
	 * @param serviceName サービス名
	 * @param method メソッド
	 * @param clsName クラス名
	 * @return リクエスト情報
	 */
	public static RequestInfo getRequestInfo(String serviceName, String method,
			String clsName) {
		return new RequestInfoImpl(serviceName, "local", SystemAuthentication.UID_SYSTEM,
				"system", method, clsName);
	}

	/**
	 * 認証情報がシステム権限かどうか判定
	 * @param auth 認証情報
	 * @return システム権限の場合true
	 */
	public static boolean isSystem(ReflexAuthentication auth) {
		if (auth != null) {
			if (auth instanceof SystemAuthentication) {
				return true;
			}
		}
		return false;
	}

}
