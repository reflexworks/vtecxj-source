package jp.reflexworks.taggingservice.servlet;

import jakarta.servlet.http.HttpServletRequest;
import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.taggingservice.util.Constants;

/**
 * Method Override判定を行うユーティリティ.
 */
public final class XHttpMethodOverrideUtil {

	/** GET */
	public static final String GET = Constants.GET;
	/** POST */
	public static final String POST = Constants.POST;
	/** PUT */
	public static final String PUT = Constants.PUT;
	/** DELETE */
	public static final String DELETE = Constants.DELETE;
	
	/** GET + space */
	public static final String GET_SPACE = GET + " ";
	/** POST + space */
	public static final String POST_SPACE = POST + " ";
	/** PUT + space */
	public static final String PUT_SPACE = PUT + " ";
	/** DELETE + space */
	public static final String DELETE_SPACE = DELETE + " ";

	/**
	 * メソッドを取得.
	 * リクエストヘッダに「X-Override-Method」指定がある場合、そちらを優先する。
	 * @param req リクエスト
	 * @return X-Override-Methodを優先させたメソッド
	 */
	public static String getOverrideMethod(HttpServletRequest req) {
		String method = req.getMethod();
		if (POST.equals(method)) {
			// Method判定
			String override = req.getHeader(ReflexServletConst.HEADER_METHOD_OVERRIDE);
			if (override != null) {
				if (PUT.equalsIgnoreCase(override) || override.startsWith(PUT_SPACE)) {
					return PUT;
				} else if (DELETE.equalsIgnoreCase(override) || override.startsWith(DELETE_SPACE)) {
					return DELETE;
				} else if (GET.equalsIgnoreCase(override) || override.startsWith(GET_SPACE)) {
					return GET;
				}
			}
		}
		return method;
	}
	
}
