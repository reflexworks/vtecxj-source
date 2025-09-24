package jp.reflexworks.taggingservice.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.util.Constants;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * 加算処理 範囲情報
 */
public class IncrementRangeInfo {

	/** 元の値 */
	private String range;
	/** 開始値 */
	private Long start = 0L;
	/** 終了値 */
	private Long end;
	/** 返却値を開始-終了間で繰り返さないかどうか */
	private boolean isNoRotation;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param range {start}-{end}!
	 */
	public IncrementRangeInfo(String range) {
		this.range = range;
		if (!StringUtils.isBlank(range)) {
			int idx = range.indexOf(Constants.INCREMENT_RANGE_SEPARATOR, 1);
			String startStr = null;
			String endStr = null;
			if (idx > 0) {
				startStr = range.substring(0, idx);
				if (range.endsWith(Constants.INCREMENT_RANGE_NOROTATE)) {
					endStr = range.substring(idx + 1, range.length() - 1);
					isNoRotation = true;
				} else {
					endStr = range.substring(idx + 1);
				}
			} else {
				startStr = range;
			}
			try {
				start = Long.parseLong(startStr);
			} catch (NumberFormatException e) {
				logger.warn("[constructor] NumberFormatException" + e.getMessage() +
						" startStr = " + startStr);
			}
			if (!StringUtils.isBlank(endStr)) {
				try {
					end = Long.parseLong(endStr);
				} catch (NumberFormatException e) {
					logger.warn("[constructor] NumberFormatException: " + e.getMessage() +
							" endStr = " + endStr);
				}
			}
		}
	}

	/**
	 * 開始値を取得.
	 * @return 開始値
	 */
	public Long getStart() {
		return start;
	}

	/**
	 * 終了値を取得
	 * @return 終了値
	 */
	public Long getEnd() {
		return end;
	}

	/**
	 * 返却値を開始-終了間で繰り返すかどうかを取得
	 * @return trueの場合、繰り返さない。
	 */
	public boolean isNoRotation() {
		return isNoRotation;
	}

	/**
	 * 採番範囲を取得.
	 * @return 採番可能数
	 */
	public Long getRangeNum() {
		// start < end が前提
		if (end != null) {
			return end - (start - 1);
		}
		return null;
	}

	/**
	 * 元の文字列を取得.
	 * @return 加算範囲文字列
	 */
	public String getRange() {
		return range;
	}

	/**
	 * 文字列
	 * @reutrn 文字列
	 */
	@Override
	public String toString() {
		if (start == null) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		sb.append(start);
		if (end != null) {
			sb.append(Constants.INCREMENT_RANGE_SEPARATOR);
			sb.append(end);
			if (isNoRotation) {
				sb.append(Constants.INCREMENT_RANGE_NOROTATE);
			}
		}
		return sb.toString();
	}

}
