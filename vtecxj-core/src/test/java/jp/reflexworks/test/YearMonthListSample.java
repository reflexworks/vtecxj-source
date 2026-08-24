package jp.reflexworks.test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import jp.sourceforge.reflex.util.StringUtils;

public class YearMonthListSample {

	private static final DateTimeFormatter YYYYMM =
			DateTimeFormatter.ofPattern("yyyyMM");

	/**
	 * 現在日付から引数の日数までのyyyyMMリストを出力します。
	 * @param args [0]日数
	 * @throws Exception
	 */
	public static void main(String... args) throws Exception {
		if (args == null || args.length < 1) {
			throw new IllegalArgumentException("Arguments are required. [0]daysAgo");
		}

		String daysAgoStr = args[0];
		int daysAgo = StringUtils.intValue(daysAgoStr);

		List<String> keys = getKeys(daysAgo);
		for (String key : keys) {
			System.out.println(key);
		}
	}

	public static List<String> getKeys(int daysAgo) {
		LocalDate today = LocalDate.now();
		LocalDate from = today.minusDays(daysAgo);

		YearMonth startMonth = YearMonth.from(from);
		YearMonth endMonth = YearMonth.from(today);

		List<String> keys = new ArrayList<>();

		for (YearMonth month = startMonth;
				!month.isAfter(endMonth);
				month = month.plusMonths(1)) {

			keys.add(month.format(YYYYMM));
		}

		return keys;
	}

}
