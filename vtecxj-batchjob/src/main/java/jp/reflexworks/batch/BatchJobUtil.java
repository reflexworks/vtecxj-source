package jp.reflexworks.batch;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.batch.BatchJobConst.CronTimeUnit;
import jp.reflexworks.js.JsExec;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.ReflexStatic;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.conn.ConnectionInfoImpl;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.RequestInfoImpl;
import jp.reflexworks.taggingservice.plugin.ServiceManager;
import jp.reflexworks.taggingservice.plugin.def.ServiceManagerDefault;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.taskqueue.TaskQueueUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.DateUtil;
import jp.sourceforge.reflex.util.DeflateUtil;
import jp.sourceforge.reflex.util.Requester;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * バッチジョブの実行時間算出ユーティリティ.
 */
public class BatchJobUtil {

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(BatchJobUtil.class);

	/**
	 * コンストラクタ.
	 */
	private BatchJobUtil() {}

	/**
	 * 現在時刻からジョブ実行範囲秒までのジョブ起動時刻を取得.
	 * @param batchjobSetting プロパティ設定内容を空白でsplitしたもの
	 * @param now 現在時刻
	 * @param nowParts 現在時刻の[0]分[1]時[2]日[3]月[4]年[5]曜日
	 * @param rangeDateStr 現在時刻にジョブ実行範囲秒を加えた日時のyyyyMMddHHmm形式文字列
	 * @return 次のジョブ起動時刻。設定が不正な場合はnull。
	 */
	public static List<String> getNextJobDates(String[] batchjobSetting, Date now,
			Integer[] nowParts, String rangeDateStr)
	throws IllegalParameterException {
		String minute = batchjobSetting[0];
		String hour = batchjobSetting[1];
		String date = batchjobSetting[2];
		String month = batchjobSetting[3];
		String day = batchjobSetting[4];

		List<String> nextJobDateStrs = new ArrayList<String>();
		if (!BatchJobConst.CRON_ASTERISK.equals(minute) &&
				BatchJobConst.CRON_ASTERISK.equals(hour) &&
				BatchJobConst.CRON_ASTERISK.equals(date) &&
				BatchJobConst.CRON_ASTERISK.equals(month) &&
				BatchJobConst.CRON_ASTERISK.equals(day)) {
			// ジョブ実行タイミング: {分} * * * *
			TreeSet<String> targetMms = getTargetMm(minute);
			if (targetMms == null || targetMms.isEmpty()) {
				throw new IllegalParameterException("The minute value is invalid. " + minute);
			}
			// 現在時刻の分を取得
			String nowMm = editZeroPadding2(nowParts[0]);
			// ジョブ実行範囲終了分
			String rangeDateMm = rangeDateStr.substring(10);

			// ジョブ実行タイミングの分を取得
			List<String> execMms = getExecDateStr(nowMm, rangeDateMm, targetMms);

			String nowYyyyMMddHHStr = editZeroPadding(nowParts[4], nowParts[3],
					nowParts[2], nowParts[1]);
			String nextYyyyMMddHHStr = null;
			for (String execMm : execMms) {
				String tmpYyyyMMddHHStr = null;
				if (execMm.compareTo(nowMm) >= 0) {
					tmpYyyyMMddHHStr = nowYyyyMMddHHStr;
				} else {
					if (nextYyyyMMddHHStr == null) {
						Date nextYyyyMMddHHDate = DateUtil.addTime(now, 0, 0, 0, 1, 0, 0, 0);
						nextYyyyMMddHHStr = DateUtil.getDateTimeFormat(
								nextYyyyMMddHHDate, "yyyyMMddHH");
					}
					tmpYyyyMMddHHStr = nextYyyyMMddHHStr;
				}
				String jobDateStr = tmpYyyyMMddHHStr + execMm;
				nextJobDateStrs.add(jobDateStr);
			}

		} else if (!BatchJobConst.CRON_ASTERISK.equals(minute) &&
				!BatchJobConst.CRON_ASTERISK.equals(hour) &&
				BatchJobConst.CRON_ASTERISK.equals(date) &&
				BatchJobConst.CRON_ASTERISK.equals(month) &&
				BatchJobConst.CRON_ASTERISK.equals(day)) {
			// ジョブ実行タイミング: {分} {時} * * *
			TreeSet<String> targetHHmms = getTargetHHmm(minute, hour);
			if (targetHHmms == null || targetHHmms.isEmpty()) {
				throw new IllegalParameterException("The value of hour or minute are invalid.");
			}
			// 現在時刻の時分を取得
			String nowHHmm = editZeroPadding2(nowParts[1]) + editZeroPadding2(nowParts[0]);
			// ジョブ実行範囲終了時分
			String rangeDateHHmm = rangeDateStr.substring(8);

			// ジョブ実行タイミングの時分を取得
			List<String> execHHmms = getExecDateStr(nowHHmm, rangeDateHHmm, targetHHmms);

			String nowYyyyMMddStr = editZeroPadding(nowParts[4], nowParts[3], nowParts[2]);
			String nextYyyyMMddStr = null;
			for (String execHHmm : execHHmms) {
				String tmpYyyyMMddStr = null;
				if (execHHmm.compareTo(nowHHmm) >= 0) {
					tmpYyyyMMddStr = nowYyyyMMddStr;
				} else {
					if (nextYyyyMMddStr == null) {
						Date nextYyyyMMddDate = DateUtil.addTime(now, 0, 0, 1, 0, 0, 0, 0);
						nextYyyyMMddStr = DateUtil.getDateTimeFormat(nextYyyyMMddDate, "yyyyMMdd");
					}
					tmpYyyyMMddStr = nextYyyyMMddStr;
				}
				String jobDateStr = tmpYyyyMMddStr + execHHmm;
				nextJobDateStrs.add(jobDateStr);
			}

		} else if (!BatchJobConst.CRON_ASTERISK.equals(minute) &&
				!BatchJobConst.CRON_ASTERISK.equals(hour) &&
				!BatchJobConst.CRON_ASTERISK.equals(date) &&
				BatchJobConst.CRON_ASTERISK.equals(month) &&
				BatchJobConst.CRON_ASTERISK.equals(day)) {
			// ジョブ実行タイミング: {分} {時} {日} * *
			TreeSet<String> targetDdHHmms = getTargetDdHHmm(minute, hour, date);
			if (targetDdHHmms == null || targetDdHHmms.isEmpty()) {
				throw new IllegalParameterException("The value of date, hour or minute are invalid.");
			}
			// 現在時刻の日時分を取得
			StringBuilder nowDdHHmmSb = new StringBuilder();
			nowDdHHmmSb.append(editZeroPadding2(nowParts[2]));
			nowDdHHmmSb.append(editZeroPadding2(nowParts[1]));
			nowDdHHmmSb.append(editZeroPadding2(nowParts[0]));
			String nowDdHHmm = nowDdHHmmSb.toString();
			// ジョブ実行範囲終了日時分
			String rangeDateDdHHmm = rangeDateStr.substring(6);

			// ジョブ実行タイミングの日時分を取得
			List<String> execDdHHmms = getExecDateStr(nowDdHHmm, rangeDateDdHHmm, targetDdHHmms);

			String nowYyyyMMStr = editZeroPadding(nowParts[4], nowParts[3]);
			String nextYyyyMMStr = null;
			for (String execDdHHmm : execDdHHmms) {
				String tmpYyyyMMStr = null;
				if (execDdHHmm.compareTo(nowDdHHmm) >= 0) {
					tmpYyyyMMStr = nowYyyyMMStr;
				} else {
					if (nextYyyyMMStr == null) {
						Date nextYyyyMMDate = DateUtil.addTime(now, 0, 1, 0, 0, 0, 0, 0);
						nextYyyyMMStr = DateUtil.getDateTimeFormat(nextYyyyMMDate, "yyyyMM");
					}
					tmpYyyyMMStr = nextYyyyMMStr;
				}
				String jobDateStr = tmpYyyyMMStr + execDdHHmm;

				// 実行日が29、30、31の場合、日付が正しいかチェックする。
				boolean isValid = false;
				String dd = execDdHHmm.substring(0, 2);
				if ("29".equals(dd) || "30".equals(dd) || "31".equals(dd)) {
					isValid = isValidMMdd(jobDateStr);
				} else {
					isValid = true;
				}
				if (isValid) {
					nextJobDateStrs.add(jobDateStr);
				}
			}

		} else if (!BatchJobConst.CRON_ASTERISK.equals(minute) &&
				!BatchJobConst.CRON_ASTERISK.equals(hour) &&
				!BatchJobConst.CRON_ASTERISK.equals(date) &&
				!BatchJobConst.CRON_ASTERISK.equals(month) &&
				BatchJobConst.CRON_ASTERISK.equals(day)) {
			// ジョブ実行タイミング: {分} {時} {日} {月} *
			TreeSet<String> targetMMddHHmms = getTargetMMddHHmm(minute, hour, date, month,
					String.valueOf(nowParts[4]));
			if (targetMMddHHmms == null || targetMMddHHmms.isEmpty()) {
				throw new IllegalParameterException(
						"The value of month, date, hour or minute are invalid.");
			}
			// 現在時刻の月日時分を取得
			StringBuilder nowMMddHHmmSb = new StringBuilder();
			nowMMddHHmmSb.append(editZeroPadding2(nowParts[3]));
			nowMMddHHmmSb.append(editZeroPadding2(nowParts[2]));
			nowMMddHHmmSb.append(editZeroPadding2(nowParts[1]));
			nowMMddHHmmSb.append(editZeroPadding2(nowParts[0]));
			String nowMMddHHmm = nowMMddHHmmSb.toString();
			// ジョブ実行範囲終了月日時分
			String rangeDateMMddHHmm = rangeDateStr.substring(4);

			// ジョブ実行タイミングの月日時分を取得
			List<String> execMMddHHmms = getExecDateStr(nowMMddHHmm, rangeDateMMddHHmm,
					targetMMddHHmms);

			int nowYyyy = nowParts[4];
			String nowYyyyStr = editZeroPadding(nowYyyy);
			String nextYyyyStr = null;
			for (String execMMddHHmm : execMMddHHmms) {
				String tmpYyyyStr = null;
				if (execMMddHHmm.compareTo(nowMMddHHmm) >= 0) {
					tmpYyyyStr = nowYyyyStr;
				} else {
					if (nextYyyyStr == null) {
						int nextYyyy = nowYyyy + 1;
						nextYyyyStr = editZeroPadding(nextYyyy);
					}
					tmpYyyyStr = nextYyyyStr;
				}
				String jobDateStr = tmpYyyyStr + execMMddHHmm;

				// 実行日が29、30、31の場合、日付が正しいかチェックする。
				boolean isValid = false;
				String dd = execMMddHHmm.substring(2, 4);
				if ("29".equals(dd) || "30".equals(dd) || "31".equals(dd)) {
					isValid = isValidMMdd(jobDateStr);
				} else {
					isValid = true;
				}
				if (isValid) {
					nextJobDateStrs.add(jobDateStr);
				}
			}

		} else if (!BatchJobConst.CRON_ASTERISK.equals(minute) &&
				!BatchJobConst.CRON_ASTERISK.equals(hour) &&
				BatchJobConst.CRON_ASTERISK.equals(date) &&
				BatchJobConst.CRON_ASTERISK.equals(month) &&
				!BatchJobConst.CRON_ASTERISK.equals(day)) {
			// ジョブ実行タイミング: {分} {時} * * {曜日}
			TreeSet<String> targetYYYYMMddHHmms = getTargetYYYYMMddHHmmByDay(minute, hour, day,
					nowParts, rangeDateStr);

			// 現在時刻の年月日時分を取得
			String nowYYYYMMddHHmm = editZeroPadding(nowParts[4], nowParts[3],
					nowParts[2], nowParts[1], nowParts[0]);
			// ジョブ実行タイミングの年月日時分を取得
			List<String> execYYYYMMddHHmms = getExecDateStr(nowYYYYMMddHHmm, rangeDateStr,
					targetYYYYMMddHHmms);
			nextJobDateStrs.addAll(execYYYYMMddHHmms);

		} else {
			// ジョブ実行タイミングのフォーマットエラー
			throw new IllegalParameterException("Batch job setting format are invalid. " +
					editCronSetting(batchjobSetting));
		}

		return nextJobDateStrs;
	}

	/**
	 * 実行対象のジョブ実行時刻一覧を取得.
	 * プロパティに定義されたジョブ実行時刻一覧から、今回の対象範囲分を抽出する。
	 * @param nowDateStr 現在時刻 (指定単位)
	 * @param rangeDateStr ジョブ実行範囲終端時刻 (指定単位)
	 * @param targetDateStrs プロパティに定義されたジョブ実行時刻一覧 (指定単位)
	 * @return 実行対象のジョブ実行時刻一覧 (指定単位)
	 */
	private static List<String> getExecDateStr(String nowDateStr, String rangeDateStr,
			TreeSet<String> targetDateStrs) {
		// ジョブ実行タイミングの指定単位時間を取得
		List<String> execDateStrs = new ArrayList<>();
		for (String targetDateStr : targetDateStrs) {
			if (nowDateStr.compareTo(targetDateStr) == 0) {
				// 現在時間の指定単位 = ジョブ実行時刻の指定単位
				execDateStrs.add(targetDateStr);
			} else if (nowDateStr.compareTo(targetDateStr) <= 0) {
				// ジョブ実行時刻の指定単位が現在時刻の指定単位より大きい場合
				// 現在時刻の指定単位と実行範囲終端の指定単位を比較
				if (nowDateStr.compareTo(rangeDateStr) <= 0) {
					if (targetDateStr.compareTo(rangeDateStr) <= 0) {
						// ジョブ実行時刻は現在時刻からジョブ実行範囲内
						execDateStrs.add(targetDateStr);
					}
				} else {
					// 現在時刻の指定単位 > 実行範囲終端の指定単位 の場合繰り上がり
					// ジョブ実行時刻は現在時刻からジョブ実行範囲内
					execDateStrs.add(targetDateStr);
				}
			} else {	// nowDateStr > targetDateStr
				// ジョブ実行時刻の指定単位が現在時刻の指定単位より小さい -> 次の値に繰り上がっている場合
				// 現在時刻の指定単位と実行範囲終端の指定単位を比較
				if (nowDateStr.compareTo(rangeDateStr) > 0) {
					if (targetDateStr.compareTo(rangeDateStr) <= 0) {
						// ジョブ実行時刻は現在時刻からジョブ実行範囲内
						execDateStrs.add(targetDateStr);
					}
				}
			}
		}
		return execDateStrs;
	}

	/**
	 * バッチジョブ時間設定のうち、指定時間単位の対象値を取得.
	 * 指定値の入力値チェック、範囲チェックも行う。
	 * @param val 指定時間単位の値
	 * @param timeUnit 指定時間単位
	 * @return 対象値
	 * @throws IllegalParameterException 指定値不正
	 */
	private static TreeSet<Integer> getTargetValues(String val, CronTimeUnit timeUnit) {
		// 時間単位のチェック
		if (CronTimeUnit.MINUTE != timeUnit && CronTimeUnit.HOUR != timeUnit &&
				CronTimeUnit.DATE != timeUnit && CronTimeUnit.MONTH != timeUnit) {
			throw new IllegalParameterException("The timeUnit is invalid. " + timeUnit.name());
		}
		int minStart = getStartValue(timeUnit);
		int maxEnd = getEndValue(timeUnit);
		TreeSet<Integer> targetValues = new TreeSet<>();
		// ジョブ実行対象の分を取得
		String[] parts = val.split(",");
		boolean isEvery = false;	// テストログ出力フラグ
		for (String part : parts) {
			int everyIdx = part.indexOf(BatchJobConst.CRON_EVERY);	// 間隔指定
			if (everyIdx > 0) {
				// 間隔指定
				String everyStr = part.substring(everyIdx + 1);
				if (!StringUtils.isInteger(everyStr)) {
					throw new IllegalParameterException("The value of every is not integer. " + everyStr);
				}
				int every = Integer.parseInt(everyStr);
				if (!isNotNegative(every)) {
					throw new IllegalParameterException("The value of every is negative. " + everyStr);
				}
				// 範囲の取得
				String range = part.substring(0, everyIdx);
				if (StringUtils.isBlank(range)) {
					throw new IllegalParameterException("The range value is not specified. " + range);
				}
				int start = 0;
				int end = 0;
				if (BatchJobConst.CRON_ASTERISK.equals(range)) {
					// 範囲: すべて
					start = minStart;
					end = maxEnd;
				} else {
					// 範囲: start-end
					int startEndIdx = range.indexOf("-");
					if (startEndIdx == -1) {
						throw new IllegalParameterException("The start-end of the range is not specified. " + range);
					}
					String startStr = range.substring(0, startEndIdx);
					String endStr = range.substring(startEndIdx + 1);
					if (!StringUtils.isInteger(startStr)) {
						throw new IllegalParameterException("The start of the range is not integer. " + range);
					}
					if (!StringUtils.isInteger(endStr)) {
						throw new IllegalParameterException("The end of the range is not integer. " + range);
					}
					start = Integer.parseInt(startStr);
					end = Integer.parseInt(endStr);
					if (start < minStart) {
						throw new IllegalParameterException("The start of the range is invalid. " + range);
					}
					if (end > maxEnd) {
						throw new IllegalParameterException("The end of the range is invalid. " + range);
					}
				}
				int tmpVal = (start - 1) + every;
				while (tmpVal <= end) {
					targetValues.add(tmpVal);
					tmpVal += every;
				}
				isEvery = true;
			} else {
				// 対象値指定
				int rangeIdx = part.indexOf("-");
				if (rangeIdx == -1) {
					// 固定値
					if (!StringUtils.isInteger(part)) {
						throw new IllegalParameterException("The value is not integer. " + part);
					}
					int fixedVal = Integer.parseInt(part);
					if (fixedVal < minStart || fixedVal > maxEnd) {
						throw new IllegalParameterException("The value is invalid. " + part);
					}
					targetValues.add(fixedVal);
				} else {
					// 範囲指定
					String startStr = part.substring(0, rangeIdx);
					String endStr = part.substring(rangeIdx + 1);
					if (!StringUtils.isInteger(startStr)) {
						throw new IllegalParameterException("The start of the range is not integer. " + part);
					}
					if (!StringUtils.isInteger(endStr)) {
						throw new IllegalParameterException("The end of the range is not integer. " + part);
					}
					int start = Integer.parseInt(startStr);
					int end = Integer.parseInt(endStr);
					if (start < minStart) {
						throw new IllegalParameterException("The start of the range is invalid. " + part);
					}
					if (end > maxEnd) {
						throw new IllegalParameterException("The end of the range is invalid. " + part);
					}
					for (int i = start; i <= end; i++) {
						targetValues.add(i);
					}
				}
			}
		}

		if (isEvery) {
			if (logger.isTraceEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append("[getTargetValues] [every] val=");
				sb.append(val);
				sb.append(", CronTimeUnit=");
				sb.append(timeUnit);
				sb.append(", targetValues=[");
				if (targetValues != null && !targetValues.isEmpty()) {
					boolean isFirst = true;
					for (int v : targetValues) {
						if (isFirst) {
							isFirst = false;
						} else {
							sb.append(", ");
						}
						sb.append(v);
					}
				}
				sb.append("]");
				logger.debug(sb.toString());
			}
		}

		return targetValues;
	}

	/**
	 * バッチジョブ時間設定のうち、曜日の対象値を取得.
	 * 指定値の入力値チェック、範囲チェックも行う。
	 * @param val バッチジョブの曜日指定
	 * @return 対象値 1(mon)〜7(sun)
	 * @throws IllegalParameterException 指定値不正
	 */
	private static TreeSet<Integer> getTargetDays(String val) {
		if (StringUtils.isBlank(val)) {
			throw new IllegalParameterException("The day is not specified.");
		}
		TreeSet<Integer> targetValues = new TreeSet<>();
		// ジョブ実行対象の分を取得
		String[] parts = val.split(",");
		for (String part : parts) {
			// 対象値指定
			int rangeIdx = part.indexOf("-");
			if (rangeIdx == -1) {
				// 固定値
				int dayNum = getDayNum(part);
				targetValues.add(dayNum);
			} else {
				// 範囲指定
				String startStr = part.substring(0, rangeIdx);
				String endStr = part.substring(rangeIdx + 1);
				int startDayNum = getDayNum(startStr);
				int endDayNum = getDayNum(endStr);

				int i = startDayNum;
				while (i != endDayNum) {
					targetValues.add(i);
					if (i == 7) {
						i = 1;
					} else {
						i++;
					}
				}
				targetValues.add(i);
			}
		}
		return targetValues;
	}

	/**
	 * バッチジョブ時間設定のうち、指定時間単位の対象値を取得.
	 * @param minute 分
	 * @return 対象の分
	 */
	private static TreeSet<String> getTargetMm(String minute) {
		TreeSet<Integer> targetMinutes = getTargetValues(minute, CronTimeUnit.MINUTE);
		if (targetMinutes == null || targetMinutes.isEmpty()) {
			return null;
		}
		TreeSet<String> targetMms = new TreeSet<>();
		for (int targetMin : targetMinutes) {
			String mm = StringUtils.zeroPadding(targetMin, 2);
			targetMms.add(mm);
		}
		return targetMms;
	}

	/**
	 * バッチジョブ時間設定のうち、指定時間単位の対象値を取得.
	 * @param minute 分
	 * @param hour 時間
	 * @return 対象の時間と分
	 */
	private static TreeSet<String> getTargetHHmm(String minute, String hour) {
		TreeSet<Integer> targetMinutes = getTargetValues(minute, CronTimeUnit.MINUTE);
		TreeSet<Integer> targetHours = getTargetValues(hour, CronTimeUnit.HOUR);
		if (targetMinutes == null || targetMinutes.isEmpty() ||
				targetHours == null || targetHours.isEmpty()) {
			return null;
		}
		TreeSet<String> targetHHmms = new TreeSet<>();
		for (int targetHour : targetHours) {
			String hh = StringUtils.zeroPadding(targetHour, 2);
			for (int targetMin : targetMinutes) {
				String hhmm = hh + StringUtils.zeroPadding(targetMin, 2);
				targetHHmms.add(hhmm);
			}
		}
		return targetHHmms;
	}

	/**
	 * バッチジョブ時間設定のうち、指定時間単位の対象値を取得.
	 * @param minute 分
	 * @param hour 時間
	 * @param date 日
	 * @return 対象の日、時間、分
	 */
	private static TreeSet<String> getTargetDdHHmm(String minute, String hour, String date) {
		TreeSet<String> targetHHmms = getTargetHHmm(minute, hour);
		TreeSet<Integer> targetDates = getTargetValues(date, CronTimeUnit.DATE);
		if (targetHHmms == null || targetHHmms.isEmpty() ||
				targetDates == null || targetDates.isEmpty()) {
			return null;
		}
		TreeSet<String> targetDdHHmms = new TreeSet<>();
		for (int targetDate : targetDates) {
			String dd = StringUtils.zeroPadding(targetDate, 2);
			for (String targetHHmm : targetHHmms) {
				String ddHHmm = dd + targetHHmm;
				targetDdHHmms.add(ddHHmm);
			}
		}
		return targetDdHHmms;
	}

	/**
	 * バッチジョブ時間設定のうち、指定時間単位の対象値を取得.
	 * @param minute 分
	 * @param hour 時間
	 * @param date 日
	 * @param month 月
	 * @param yyyy 現在の年4桁
	 * @return 対象の月、日、時間、分
	 */
	private static TreeSet<String> getTargetMMddHHmm(String minute, String hour,
			String date, String month, String yyyy) {
		TreeSet<String> targetDdHHmms = getTargetDdHHmm(minute, hour, date);
		TreeSet<Integer> targetMonths = getTargetValues(month, CronTimeUnit.MONTH);
		if (targetDdHHmms == null || targetDdHHmms.isEmpty() ||
				targetMonths == null || targetMonths.isEmpty()) {
			return null;
		}
		TreeSet<String> targetMMddHHmms = new TreeSet<>();
		for (int targetMonth : targetMonths) {
			String month2 = StringUtils.zeroPadding(targetMonth, 2);
			for (String targetDdHHmm : targetDdHHmms) {
				String mMddHHmm = month2 + targetDdHHmm;
				// 月日の有効チェック
				String tmpYyyyMMddHHmm = yyyy + mMddHHmm;
				if (isValidMMdd(tmpYyyyMMddHHmm)) {
					targetMMddHHmms.add(mMddHHmm);
				} else {
					if (logger.isDebugEnabled()) {
						StringBuilder sb = new StringBuilder();
						sb.append("[getTargetMMddHHmm] date and month is not valid. ");
						sb.append(mMddHHmm);
						sb.append(" year=");
						sb.append(yyyy);
						logger.debug(sb.toString());
					}
				}
			}
		}
		return targetMMddHHmms;
	}

	/**
	 * バッチジョブ時間設定のうち、指定時間単位の対象値を取得.
	 * 今日と、実行範囲が明日にまたがる場合は明日の、指定時間を返却する。
	 * @param minute 分
	 * @param hour 時間
	 * @param day 曜日
	 * @param nowParts 現在時刻の[0]分[1]時[2]日[3]月[4]年[5]曜日
	 * @param rangeDateStr 現在時刻にジョブ実行範囲秒を加えた日時のyyyyMMddHHmm形式文字列
	 * @return 対象の日時. 現在値より小さい値は弾いているので、先頭を使用すれば良い。
	 */
	private static TreeSet<String> getTargetYYYYMMddHHmmByDay(String minute, String hour,
			String day, Integer[] nowParts, String rangeDateStr) {
		TreeSet<String> targetHHmms = getTargetHHmm(minute, hour);
		// 曜日の指定はsun〜satか、0(sun)〜6(sat)、7(sun)
		// getTargetDaysで1〜7のいずれかに変換する。
		TreeSet<Integer> targetDays = getTargetDays(day);

		// 今日の曜日
		int nowDayNum = nowParts[5];

		// 今日の年月日
		String nowYYYYMMdd = editZeroPadding(nowParts[4], nowParts[3], nowParts[2]);
		// 実行範囲終端の年月日
		String rangeYYYYMMdd = rangeDateStr.substring(0, 8);

		// 実行範囲終端の曜日
		int rangeDayNum = 0;
		if (nowYYYYMMdd.equals(rangeYYYYMMdd)) {
			rangeDayNum = nowDayNum;
		} else {
			// 次の日の曜日も含む
			rangeDayNum = nowDayNum + 1;
			if (rangeDayNum > 7) {
				rangeDayNum = rangeDayNum - 7;
			}
		}

		// 今日の年月日時分文字列
		TreeSet<String> targetDates = new TreeSet<>();
		// 指定された曜日ごとに繰り返し
		for (int dayNum : targetDays) {
			String targetYYYYMMdd = null;
			if (dayNum == nowDayNum) {
				// 今日
				targetYYYYMMdd = nowYYYYMMdd;
			} else if (dayNum == rangeDayNum) {
				// 明日
				targetYYYYMMdd = rangeYYYYMMdd;
			}
			if (!StringUtils.isBlank(targetYYYYMMdd)) {
				for (String targetHHmm : targetHHmms) {
					targetDates.add(targetYYYYMMdd + targetHHmm);
				}
			}
		}
		return targetDates;
	}

	/**
	 * 数値が負の値でないかチェック.
	 * @param val 数値
	 * @return 正の値か0の場合true。
	 */
	private static boolean isNotNegative(int val) {
		return val >= 0;
	}

	/**
	 * 時間単位ごとの開始値を取得.
	 * @param timeUnit 時間単位
	 * @return 開始値
	 */
	private static int getStartValue(CronTimeUnit timeUnit) {
		if (CronTimeUnit.MINUTE == timeUnit) {
			return 0;
		} else if (CronTimeUnit.HOUR == timeUnit) {
			return 0;
		} else if (CronTimeUnit.DATE == timeUnit) {
			return 1;
		} else if (CronTimeUnit.MONTH == timeUnit) {
			return 1;
		} else {	// DAY
			return 0;	// sun
		}
	}

	/**
	 * 時間単位ごとの終了値を取得.
	 * @param timeUnit 時間単位
	 * @return 終了値
	 */
	private static int getEndValue(CronTimeUnit timeUnit) {
		if (CronTimeUnit.MINUTE == timeUnit) {
			return 59;
		} else if (CronTimeUnit.HOUR == timeUnit) {
			return 23;
		} else if (CronTimeUnit.DATE == timeUnit) {
			return 31;
		} else if (CronTimeUnit.MONTH == timeUnit) {
			return 12;
		} else {	// DAY
			return 6;	// sat
		}
	}

	/**
	 * バッチジョブの設定をログ用につなげるよう編集
	 * @param vals バッチジョブの設定
	 * @return ログ用文字列
	 */
	private static String editCronSetting(String... vals) {
		if (vals == null) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		boolean isFirst = true;
		for (String val : vals) {
			if (isFirst) {
				isFirst = false;
			} else {
				sb.append(" ");
			}
			sb.append(val);
		}
		return sb.toString();
	}

	/**
	 * 指定された年月日時分文字列について、月日が有効かどうかチェックする.
	 * @param yyyyMMddHHmm 年月日時分
	 * @return 月日が有効な場合true
	 */
	private static boolean isValidMMdd(String yyyyMMddHHmm) {
		try {
			Date tmpDate = DateUtil.getDate(yyyyMMddHHmm);
			String tmpMM = DateUtil.getDateTimeFormat(tmpDate, "MM");
			String tmpDd = DateUtil.getDateTimeFormat(tmpDate, "dd");
			String sourceMM = yyyyMMddHHmm.substring(4, 6);
			String sourceDd = yyyyMMddHHmm.substring(6, 8);
			if (sourceMM.equals(tmpMM) && sourceDd.equals(tmpDd)) {
				return true;
			}
		} catch (ParseException e) {
			if (logger.isDebugEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append("[isValidMMdd] ParseException: ");
				sb.append(e.getMessage());
				sb.append(" ");
				sb.append(yyyyMMddHHmm);
				logger.debug(sb.toString());
			}
		}
		return false;
	}

	/**
	 * 曜日を表す番号を取得.
	 * 日曜日は7を返す。(SimpleDateFormatに合わせる。)
	 * @param day 曜日
	 * @return 曜日を表す番号
	 */
	private static int getDayNum(String day) {
		String dayLower = day.toLowerCase(Locale.ENGLISH);
		if ("sun".equals(dayLower) || "0".equals(dayLower) || "7".equals(dayLower)) {
			return 7;
		} else if ("mon".equals(dayLower) || "1".equals(dayLower)) {
			return 1;
		} else if ("tue".equals(dayLower) || "2".equals(dayLower)) {
			return 2;
		} else if ("wed".equals(dayLower) || "3".equals(dayLower)) {
			return 3;
		} else if ("thu".equals(dayLower) || "4".equals(dayLower)) {
			return 4;
		} else if ("fri".equals(dayLower) || "5".equals(dayLower)) {
			return 5;
		} else if ("sat".equals(dayLower) || "6".equals(dayLower)) {
			return 6;
		}
		throw new IllegalParameterException("The day is invalid. " + day);
	}

	/**
	 * 日時の数値配列をつなげて文字列に編集する.
	 * ゼロパディングを行う
	 * @param datetimeParts 日時の数値配列。年、月、日、時、分 の順。
	 * @return 日時文字列
	 */
	private static String editZeroPadding(int... datetimeParts) {
		StringBuilder sb = new StringBuilder();
		int i = 0;
		for (int datetimePart : datetimeParts) {
			if (i == 0) {
				// 年
				sb.append(StringUtils.zeroPadding(datetimePart, 4));
			} else if (i < 5) {
				// 月・日・時・分
				sb.append(StringUtils.zeroPadding(datetimePart, 2));
			}
			i++;
		}
		return sb.toString();
	}

	/**
	 * 2桁のゼロパディングを行う
	 * @param part 日時の数値
	 * @return 日時文字列
	 */
	private static String editZeroPadding2(int part) {
		return StringUtils.zeroPadding(part, 2);
	}

	/**
	 * yyyyMMddHHmm形式文字列をDate型に変換する.
	 * @param yyyyMMddHHmm yyyyMMddHHmm形式文字列
	 * @return yyyyMMddHHmm形式文字列をDate型に変換したもの
	 */
	private static Date getDateByYyyyMMddHHmm(String yyyyMMddHHmm)
	throws ParseException {
		return DateUtil.getDate(yyyyMMddHHmm, BatchJobConst.FORMAT_BATCHJOB);
	}

	/**
	 * ロガー出力用接頭辞を編集
	 * @param methodName Javaメソッド名
	 * @param serviceName サービス名
	 * @return ロガー出力用接頭辞
	 */
	public static String getLoggerPrefix(String methodName, String serviceName) {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		sb.append(methodName);
		sb.append("] serviceName=");
		sb.append(serviceName);
		sb.append(" ");
		return sb.toString();
	}

	/**
	 * エラーメッセージ編集
	 * @param msg エラーメッセージ
	 * @param propName プロパティの名前
	 * @param propVal プロパティの値
	 * @return 編集したエラーメッセージ
	 */
	public static String editErrorMessage(String msg, String propName, String propVal) {
		StringBuilder sb = new StringBuilder();
		sb.append(msg);
		sb.append(" ");
		sb.append(propName);
		sb.append("=");
		sb.append(propVal);
		return sb.toString();
	}

	/**
	 * エラーメッセージ編集
	 * @param uri キー
	 * @param e 例外
	 * @return 編集したエラーメッセージ
	 */
	public static String editErrorMessage(String uri, Throwable e) {
		StringBuilder sb = new StringBuilder();
		sb.append("Key: ");
		sb.append(uri);
		sb.append(" ");
		sb.append(editErrorMessage(e));
		return sb.toString();
	}


	/**
	 * エラーメッセージ編集
	 * @param e 例外
	 * @return 編集したエラーメッセージ
	 */
	public static String editErrorMessage(Throwable e) {
		StringBuilder sb = new StringBuilder();
		sb.append(e.getClass().getSimpleName());
		sb.append(": ");
		sb.append(e.getMessage());
		return sb.toString();
	}

	/**
	 * 開始時刻と終了時刻の差分秒を求める.
	 * @param dateFrom 開始時刻
	 * @param dateTo 終了時刻
	 * @return 差分秒
	 */
	public static int getDiffSec(Date dateFrom, Date dateTo) {
		long dateTimeTo = dateTo.getTime();
		long dateTimeFrom = dateFrom.getTime();

		// 差分ミリ秒
		long diffMillis = dateTimeTo - dateTimeFrom;
		// マイナスの場合は0を返す。
		// (分まで同じ場合、ジョブ実行時刻は秒が0だが現在時刻は現在秒が設定されているため
		//  マイナスとなる場合がある。)
		if (diffMillis < 0) {
			return 0;
		}
		// 差分秒
		return (int)(diffMillis / 1000);
	}

	/**
	 * 開始時刻と終了時刻の差分秒を求める.
	 * (バッチ実行間隔が1時間だとしても3,600,000ミリ秒なので、intで足りる。)
	 * 1970年1月1日00:00:00 GMTからのミリ秒数 で計算する。
	 * @param dateTimeFrom 開始時刻 (ミリ秒)
	 * @param yyyyMMddHHmm 終了時刻 (yyyyMMddHHmm形式)
	 * @return 差分秒
	 */
	public static int getDiffMilliSec(long dateTimeFrom, String yyyyMMddHHmm)
	throws ParseException {
		// 終了時刻を計算用に変換
		Date dateTo = getDateByYyyyMMddHHmm(yyyyMMddHHmm);
		long dateTimeTo = dateTo.getTime();

		// 差分ミリ秒
		long diffMillis = dateTimeTo - dateTimeFrom;
		// マイナスの場合は0を返す。
		// (分まで同じ場合、ジョブ実行時刻は秒が0だが現在時刻は現在秒が設定されているため
		//  マイナスとなる場合がある。)
		if (diffMillis < 0) {
			return 0;
		}
		// 差分ミリ秒
		return (int)diffMillis;
	}

	/**
	 * バッチジョブ実行タイムアウト(秒)を取得
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return バッチジョブ実行タイムアウト(秒)
	 */
	public static int getJsTimeout(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo) {
		return JsExec.getTimeout(serviceName, requestInfo, connectionInfo);
	}

	/**
	 * バッチジョブ実行予定期間(秒)を取得.
	 * @return バッチジョブ実行予定期間(秒)
	 */
	public static int getBatchjobExecRangeSec() {
		int batchjobExecAdditionalSec = TaggingEnvUtil.getSystemPropInt(
				BatchJobConst.PROP_BATCHJOB_EXEC_ADDITIONAL_SEC,
				BatchJobConst.BATCHJOB_EXEC_ADDITIONAL_SEC_DEFAULT);
		return getBatchjobExecIntervalSec() + batchjobExecAdditionalSec;
	}

	/**
	 * バッチジョブ管理処理実行間隔(秒)を取得.
	 * @return バッチジョブ管理処理実行間隔(秒)
	 */
	public static int getBatchjobExecIntervalSec() {
		int batchjobExecIntervalMinute = TaggingEnvUtil.getSystemPropInt(
				BatchJobConst.PROP_BATCHJOB_EXEC_INTERVAL_MINUTE,
				BatchJobConst.BATCHJOB_EXEC_INTERVAL_MINUTE_DEFAULT);
		return batchjobExecIntervalMinute * 60;
	}

	/**
	 * 起動時のバッチジョブ管理処理実行間隔(秒)を取得.
	 * @return 起動時のバッチジョブ管理処理実行間隔(秒)
	 */
	public static int getBatchjobExecInitIntervalSec() {
		return TaggingEnvUtil.getSystemPropInt(
				BatchJobConst.PROP_BATCHJOB_EXEC_INIT_INTERVAL_SEC,
				BatchJobConst.BATCHJOB_EXEC_INIT_INTERVAL_SEC_DEFAULT);
	}

	/**
	 * 環境変数からPOD名を取得.
	 * @return POD名
	 */
	public static String getPodName() {
		String podName = System.getenv(BatchJobConst.ENV_PODNAME);
		if (StringUtils.isBlank(podName)) {
			podName = BatchJobConst.PODNAME_DEFAULT;
		}
		return podName;
	}

	/**
	 * バッチジョブ管理処理をTaskQueueに登録する.
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報 (ReadEntryMapを利用)
	 * @return Future
	 */
	public static Future<Boolean> addTaskOfManagement(ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// コネクション情報の引き継ぎなし。sharing情報を使用。
		return addTaskOfManagement(0, auth, requestInfo, connectionInfo);
	}

	/**
	 * バッチジョブ管理処理をTaskQueueに登録する.
	 * @param intervalSec TaskQueue実行までの待ち時間(秒)。リクエスト実行の場合は0を指定。
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo connectionInfo
	 * @return Future
	 */
	public static Future<Boolean> addTaskOfManagement(int intervalSec,
			ReflexAuthentication auth, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		BatchJobManagementCallable callable = new BatchJobManagementCallable(BatchJobConst.PODNAME);
		if (logger.isTraceEnabled()) {
			logger.debug("[addTaskOfManagement] intervalSec=" + intervalSec);
		}
		int intervalMilliSec = intervalSec * 1000;
		Future<Boolean> future = (Future<Boolean>)TaskQueueUtil.addTask(callable,
				intervalMilliSec, auth, requestInfo, connectionInfo);
		return future;
	}

	/**
	 * バッチジョブ処理をTaskQueueに登録する.
	 * @param jsFunction サーバサイドJS名
	 * @param batchJobTimeEntry バッチジョブ管理エントリー
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @param delay バッチジョブ実行までの待ち時間(ミリ秒)
	 * @return Future
	 */
	public static Future<Boolean> addTaskOfBatchJob(String jsFunction,
			EntryBase batchJobTimeEntry, ReflexRequest req, ReflexResponse resp,
			int delay)
	throws IOException, TaggingException {
		RequestInfo requestInfo = req.getRequestInfo();
		BatchJobCallable callable = new BatchJobCallable(BatchJobConst.PODNAME, jsFunction,
				batchJobTimeEntry, req, resp);
		// コネクション情報の引き継ぎなし
		//ConnectionInfo dummyConnectionInfo = new ConnectionInfoImpl(null, requestInfo);
		//return (Future<Boolean>)TaskQueueUtil.addTask(callable, delay,
		//		req.getAuth(), requestInfo, dummyConnectionInfo);
		return (Future<Boolean>)TaskQueueUtil.addTaskByMainThread(callable, delay, 
				req.getAuth(), requestInfo, null);
	}

	/**
	 * 指定されたサービスのバッチジョブFutureリストを設定.
	 * すでに終了しているFutureは除去する。
	 * @return 指定されたサービスのバッチジョブFutureリスト
	 */
	public static void setBatchJobFutureList(String serviceName, List<BatchJobFuture> futures) {
		if (StringUtils.isBlank(serviceName) || futures == null || futures.isEmpty()) {
			return;
		}
		List<BatchJobFuture> currentFutures = null;
		ConcurrentMap<String, List<BatchJobFuture>> batchJobFutureMap =
				(ConcurrentMap<String, List<BatchJobFuture>>)ReflexStatic.getStatic(
				BatchJobConst.STATIC_BATCHJOB_FUTURE_OF_JOB);
		if (batchJobFutureMap.containsKey(serviceName)) {
			currentFutures = batchJobFutureMap.get(serviceName);
		} else {
			currentFutures = batchJobFutureMap.putIfAbsent(serviceName, futures);
		}
		if (currentFutures != null) {
			currentFutures.addAll(futures);

			// すでに終了しているFutureは除去する。(waiting以外は除去)
			List<BatchJobFuture> deleteList = new ArrayList<>();
			for (BatchJobFuture future : currentFutures) {
				String batchJobStatus = future.getBatchJobStatus();
				if (!BatchJobConst.JOB_STATUS_WAITING.equals(batchJobStatus) &&
						!BatchJobConst.JOB_STATUS_RUNNING.equals(batchJobStatus)) {
					deleteList.add(future);
				}
			}
			for (BatchJobFuture deleteFuture : deleteList) {
				currentFutures.remove(deleteFuture);
			}
		}
	}

	/**
	 * バッチジョブFuture mapを取得.
	 * mapのキー:サービス名、値:Futureのリスト
	 * @return バッチジョブFuture map
	 */
	public static ConcurrentMap<String, List<BatchJobFuture>> getBatchJobFutureMap() {
		return (ConcurrentMap<String, List<BatchJobFuture>>)ReflexStatic.getStatic(
				BatchJobConst.STATIC_BATCHJOB_FUTURE_OF_JOB);
	}

	/**
	 * 指定されたサービスのバッチジョブFutureリストを取得.
	 * @return 指定されたサービスのバッチジョブFutureリスト
	 */
	public static List<BatchJobFuture> getBatchJobFutureList(String serviceName) {
		ConcurrentMap<String, List<BatchJobFuture>> batchJobFutureMap =
				(ConcurrentMap<String, List<BatchJobFuture>>)ReflexStatic.getStatic(
				BatchJobConst.STATIC_BATCHJOB_FUTURE_OF_JOB);
		if (batchJobFutureMap != null && batchJobFutureMap.containsKey(serviceName)) {
			return batchJobFutureMap.get(serviceName);
		}
		return null;
	}

	/**
	 * 待ち状態のジョブ管理エントリーを削除.
	 */
	public static void deleteWaiting() {
		String methodName = "deleteWaiting";

		// スケジュールされたバッチジョブのうち、待ち状態(waiting)のものを停止
		ConcurrentMap<String, List<BatchJobFuture>> batchJobFutureMap =
				BatchJobUtil.getBatchJobFutureMap();
		// 処理中のバッチジョブがなければ処理を抜ける。
		Map<String, List<BatchJobFuture>> runningFuturesMap = new HashMap<>();
		if (batchJobFutureMap == null || batchJobFutureMap.isEmpty()) {
			return;
		}

		ConnectionInfo connectionInfo = null;	// この処理で1回だけ生成(サービス間で共通使用)
		try {
			// まず処理待ち状態のバッチジョブを削除
			for (Map.Entry<String, List<BatchJobFuture>> mapEntry : batchJobFutureMap.entrySet()) {
				String serviceName = mapEntry.getKey();
				List<BatchJobFuture> futureList = mapEntry.getValue();
				if (futureList == null || futureList.isEmpty()) {
					// 処理中のバッチジョブがなければ次のサービスに移る。
					continue;
				}

				SystemContext systemContext = null;	// サービスごとに生成
				for (BatchJobFuture future : futureList) {
					String batchJobStatus = future.getBatchJobStatus();
					if (BatchJobConst.JOB_STATUS_WAITING.equals(batchJobStatus)) {
						EntryBase batchJobTimeEntry = future.getBatchJobTimeEntry();
						String batchJobTimeUri = batchJobTimeEntry.getMyUri();
						boolean isCancelled = future.cancel(false);
						if (logger.isDebugEnabled()) {
							StringBuilder sb = new StringBuilder();
							sb.append(getLoggerPrefix(methodName, serviceName));
							sb.append("waiting uri=");
							sb.append(batchJobTimeUri);
							sb.append(" isCancelled=");
							sb.append(isCancelled);
							logger.debug(sb.toString());
						}
						if (isCancelled) {
							// ジョブを取り消した場合、バッチジョブエントリーを削除する。
							if (connectionInfo == null) {
								connectionInfo = createConnectionInfo();
							}
							if (systemContext == null) {
								ReflexAuthentication auth = createAuth(serviceName);
								RequestInfo requestInfo = createRequestInfo(auth);
								systemContext = new SystemContext(auth, requestInfo,
										connectionInfo);
							}
							try {
								systemContext.delete(batchJobTimeUri,
										TaggingEntryUtil.getRevisionById(
												batchJobTimeEntry.id));
							} catch (TaggingException | IOException e) {
								logger.warn(getLoggerPrefix(methodName, serviceName) +
										"Error occured. " + e);
								systemContext.errorLog(e);
							}
						}
					} else if (BatchJobConst.JOB_STATUS_RUNNING.equals(batchJobStatus)) {
						// 実行中のバッチジョブはひとまず退避
						List<BatchJobFuture> runningFutures = runningFuturesMap.get(serviceName);
						if (runningFutures == null) {
							runningFutures = new ArrayList<>();
							runningFuturesMap.put(serviceName, runningFutures);
						}
						runningFutures.add(future);
					}
				}
			}

			// 実行中のバッチジョブが存在しない場合は処理を抜ける。
			if (runningFuturesMap.isEmpty()) {
				return;
			}
			// 実行中のバッチジョブの終了待ちは JsExec.close() で実行済み。

			// 実行中のバッチジョブをキャンセルする。
			for (Map.Entry<String, List<BatchJobFuture>> mapEntry : runningFuturesMap.entrySet()) {
				String serviceName = mapEntry.getKey();
				List<BatchJobFuture> futureList = mapEntry.getValue();

				SystemContext systemContext = null;	// サービスごとに生成
				for (BatchJobFuture future : futureList) {
					String batchJobStatus = future.getBatchJobStatus();
					if (BatchJobConst.JOB_STATUS_RUNNING.equals(batchJobStatus)) {
						// キャンセル、処理失敗
						EntryBase batchJobTimeEntry = future.getBatchJobTimeEntry();
						String batchJobTimeUri = batchJobTimeEntry.getMyUri();
						boolean isCancelled = future.cancel(false);
						if (logger.isDebugEnabled()) {
							StringBuilder sb = new StringBuilder();
							sb.append(getLoggerPrefix(methodName, serviceName));
							sb.append("running uri=");
							sb.append(batchJobTimeUri);
							sb.append(" isCancelled=");
							sb.append(isCancelled);
							logger.debug(sb.toString());
						}
						//if (isCancelled) {
							// ジョブを取り消した場合、バッチジョブエントリーを削除する。
							if (connectionInfo == null) {
								connectionInfo = createConnectionInfo();
							}
							if (systemContext == null) {
								ReflexAuthentication auth = createAuth(serviceName);
								RequestInfo requestInfo = createRequestInfo(auth);
								systemContext = new SystemContext(auth, requestInfo,
										connectionInfo);
							}
							try {
								batchJobTimeEntry.title = BatchJobConst.JOB_STATUS_FAILED;
								systemContext.put(batchJobTimeEntry);

							} catch (TaggingException | IOException e) {
								logger.warn(getLoggerPrefix(methodName, serviceName) +
										"Error occured. " + e);
								systemContext.errorLog(e);
							}
						//}

					}
				}
			}

		} finally {
			if (connectionInfo != null) {
				connectionInfo.close();
			}
		}
	}

	/**
	 * コネクション情報オブジェクトを作成.
	 * @return コネクション情報
	 */
	private static ConnectionInfo createConnectionInfo() {
		ReflexAuthentication systemServiceAuth =
				createAuth(TaggingEnvUtil.getSystemService());
		RequestInfo systemServiceRequestInfo =
				createRequestInfo(systemServiceAuth);
		DeflateUtil deflateUtil = new DeflateUtil();
		return new ConnectionInfoImpl(deflateUtil, systemServiceRequestInfo);
	}

	/**
	 * サービス管理者の認証情報を生成.
	 * @param serviceName サービス名
	 * @return サービス管理者の認証情報
	 */
	static ReflexAuthentication createAuth(String serviceName) {
		ServiceManager serviceManager = new ServiceManagerDefault();
		return serviceManager.createServiceAdminAuth(serviceName);
	}

	/**
	 * サービス管理者のリクエスト情報を生成.
	 * @param auth 認証情報
	 * @return サービス管理者のリクエスト情報
	 */
	static RequestInfo createRequestInfo(ReflexAuthentication auth) {
		return new RequestInfoImpl(auth.getServiceName(),
				BatchJobConst.REQUESTINFO_IP, auth.getUid(), auth.getAccount(),
				BatchJobConst.REQUESTINFO_METHOD, BatchJobConst.REQUESTINFO_URL);
	}

	/**
	 * バッチジョブ実行管理リクエスト.
	 */
	public static void requestBatchJob() {
		String url = TaggingEnvUtil.getSystemProp(BatchJobConst.PROP_URL_BATCHJOB, null);
		if (StringUtils.isBlank(url)) {
			throw new IllegalStateException("Batchjob url setting is requred.");
		}

		try {
			Requester requester = new Requester();
			int timeoutMillis = TaggingEnvUtil.getSystemPropInt(
					BatchJobConst.PROP_BATCHJOB_EXEC_REQUEST_TIMEOUT_MILLIS,
					BatchJobConst.BATCHJOB_EXEC_REQUEST_TIMEOUT_MILLIS_DEFAULT);
			if (logger.isDebugEnabled()) {
				logger.debug("[requestBatchJob] Request URL: " + url);
			}
			HttpURLConnection http = requester.request(url, BatchJobConst.METHOD_BATCHJOB,
					null, timeoutMillis);
			if (logger.isDebugEnabled()) {
				logger.debug("[requestBatchJob] Response status: " + http.getResponseCode());
			}

		} catch (IOException | RuntimeException | Error e) {
			logger.warn("[requestBatchJob] " + e.getClass().getName(), e);
		}
	}
	
	/**
	 * 有効なサービスのステータス一覧を取得.
	 * @return 有効なサービスのステータス一覧
	 */
	public static Set<String> getValidServiceStatuses() {
		// creating,staging,production,blocked
		Set<String> validServiceStatuses = new HashSet<String>();
		validServiceStatuses.add(Constants.SERVICE_STATUS_CREATING);
		validServiceStatuses.add(Constants.SERVICE_STATUS_STAGING);
		validServiceStatuses.add(Constants.SERVICE_STATUS_PRODUCTION);
		validServiceStatuses.add(Constants.SERVICE_STATUS_BLOCKED);
		return validServiceStatuses;
	}
	
	/**
	 * 有効なサービス一覧を取得.
	 * @param reflexContext ReflexContext
	 * @return 有効なサービス一覧
	 */
	public static List<String> getValidServices(ReflexContext reflexContext) 
	throws IOException, TaggingException {
		SystemContext systemContext = null;
		if (reflexContext instanceof SystemContext) {
			systemContext = (SystemContext)reflexContext;
		} else {
			systemContext = new SystemContext(reflexContext.getServiceName(),
					reflexContext.getRequestInfo(), reflexContext.getConnectionInfo());
		}
		
		Set<String> validServiceStatuses = getValidServiceStatuses();
		List<String> services = new ArrayList<>();
		String cursorStr = null;
		do {
			String uri = TaggingEntryUtil.addCursorToUri(Constants.URI_SERVICE, 
					cursorStr);
			FeedBase serviceFeed = systemContext.getFeed(uri);
			cursorStr = TaggingEntryUtil.getCursorFromFeed(serviceFeed);
			if (TaggingEntryUtil.isExistData(serviceFeed)) {
				for (EntryBase serviceEntry : serviceFeed.entry) {
					String serviceStatus = serviceEntry.subtitle;
					if (validServiceStatuses.contains(serviceStatus)) {
						String serviceUri = serviceEntry.getMyUri();
						String serviceName = serviceUri.substring(
								BatchJobConst.URI_SERVICE_SLASH_LEN);
						services.add(serviceName);
					}
				}
			}
		} while (!StringUtils.isBlank(cursorStr));

		return services;
	}

}
