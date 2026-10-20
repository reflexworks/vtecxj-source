package jp.reflexworks.taggingservice.util;

import jakarta.servlet.http.HttpServletRequest;

import jp.reflexworks.taggingservice.env.TaggingEnvConst;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;

/**
 * 内部リクエスト判定ユーティリティ.
 * <p>
 * 設定「_reflex.internal.port」に指定されたポートで受け付けたリクエストを、
 * クラスタ内部からのリクエストと判定する。
 * このポートはLoadBalancerに公開せず、ClusterIP Service と NetworkPolicy で
 * 到達元を限定すること。(Hostヘッダ等のリクエスト内容は外部から偽装可能なため判定に使用しない。)
 * </p>
 */
public final class InternalRequestUtil {

	/**
	 * コンストラクタ.
	 */
	private InternalRequestUtil() {}

	/**
	 * 内部リクエストかどうか判定.
	 * @param req リクエスト
	 * @return 内部リクエストポートで受け付けたリクエストの場合true
	 */
	public static boolean isInternalRequest(HttpServletRequest req) {
		if (req == null) {
			return false;
		}
		int internalPort = getInternalPort();
		if (internalPort <= 0) {
			return false;
		}
		return req.getLocalPort() == internalPort;
	}

	/**
	 * 内部リクエスト受付ポートを取得.
	 * @return 内部リクエスト受付ポート。設定がない場合0
	 */
	private static int getInternalPort() {
		return TaggingEnvUtil.getSystemPropInt(TaggingEnvConst.REFLEX_INTERNAL_PORT, 0);
	}

}
