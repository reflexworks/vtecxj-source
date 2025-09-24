package jp.reflexworks.taggingservice.util;

import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.sys.SystemAuthentication;

/**
 * 認証についてのユーティリティクラス
 */
public class AuthenticationUtil {
	
	/**
	 * システムユーザかどうかの判定
	 * @param auth 認証情報
	 * @return システムユーザの場合true
	 */
	public static boolean isSystemuser(ReflexAuthentication auth) {
		if (auth != null && auth instanceof SystemAuthentication) {
			return true;
		}
		return false;
	}
	
	/**
	 * スーパーユーザかどうかの判定
	 * @param auth 認証情報
	 * @return スーパーユーザの場合true
	 */
	public static boolean isSuperuser(ReflexAuthentication auth) {
		if (auth != null) {
			String superUser = TaggingEnvUtil.getSuperuser();
			if (superUser != null && superUser.equals(auth.getAccount())) {
				return true;
			}
		}
		return false;

	}

}
