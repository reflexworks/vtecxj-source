package jp.reflexworks.taggingservice.env;

import java.util.Date;

import jp.sourceforge.reflex.util.DateUtil;

/**
 * static情報操作ユーティリティ
 */
public class StaticInfoUtil {

	/**
	 * システム管理サービスの情報static保存期間(秒)を取得.
	 * @return システム管理サービスの情報static保存期間(秒)
	 */
	public static int getStaticinfoTimelimitSec() {
		return ReflexEnvUtil.getSystemPropInt(ReflexEnvConst.STATICINFO_TIMELIMIT_SEC,
				ReflexEnvConst.STATICINFO_TIMELIMIT_SEC_DEFAULT);
	}

	/**
	 * static情報lock取得リトライ総数を取得.
	 * @return static情報lock取得リトライ総数
	 */
	public static int getStaticinfoRetryCount() {
		return ReflexEnvUtil.getSystemPropInt(ReflexEnvConst.STATICINFO_RETRY_COUNT,
				ReflexEnvConst.STATICINFO_RETRY_COUNT_DEFAULT);
	}

	/**
	 * static情報lock取得リトライ時のスリープ時間(ミリ秒)を取得.
	 * @return static情報lock取得リトライ時のスリープ時間(ミリ秒)
	 */
	public static int getStaticinfoRetryWaitmillis() {
		return ReflexEnvUtil.getSystemPropInt(ReflexEnvConst.STATICINFO_RETRY_WAITMILLIS,
				ReflexEnvConst.STATICINFO_RETRY_WAITMILLIS_DEFAULT);
	}

	/**
	 * アクセス時間がstatic情報保持期間を超過しているかどうか
	 * @param accessTime アクセス時間
	 * @return アクセス時間がstatic情報保持期間を超過している場合true
	 */
	public static boolean isExceeded(Date accessTime) {
		if (accessTime == null) {
			return true;
		}
		Date now = new Date();
		int timelimitSec = getStaticinfoTimelimitSec();

		// accessTime + limit >= now -> false (期限内)
		// accessTime + limit < now -> true (超過)
		Date accessTimePlusLimit = DateUtil.addTime(accessTime, 0, 0, 0, 0, 0, timelimitSec, 0);
		return now.after(accessTimePlusLimit);
	}

}
