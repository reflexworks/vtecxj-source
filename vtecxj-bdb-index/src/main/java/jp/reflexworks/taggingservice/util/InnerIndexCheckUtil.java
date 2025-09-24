package jp.reflexworks.taggingservice.util;

import jp.reflexworks.atom.api.Condition;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;

/**
 * チェックユーティリティ.
 */
public class InnerIndexCheckUtil extends ReflexCheckUtil {

	/**
	 * 検索条件チェック
	 * @param condition 検索条件
	 * @param conditionRange 検索条件(範囲)
	 */
	public static void checkCondition(Condition condition, Condition conditionRange) {
		InnerIndexCheckUtil.checkConditionProc(condition);
		String equation = condition.getEquations();
		if (conditionRange != null) {
			// 範囲指定がある場合、最初の条件はgtまたはgeで、範囲指定の条件はltまたはle
			if (!Condition.GREATER_THAN.equals(equation) &&
					!Condition.GREATER_THAN_OR_EQUAL.equals(equation)) {
				throw new IllegalParameterException("The range specification condition is only 'gt' or 'ge'. " + condition);
			}
			InnerIndexCheckUtil.checkConditionProc(conditionRange);
			String equationRange = conditionRange.getEquations();
			if (!Condition.LESS_THAN.equals(equationRange) &&
					!Condition.LESS_THAN_OR_EQUAL.equals(equationRange)) {
				throw new IllegalParameterException("The range specification condition (range) is only 'lt' or 'le'. " + conditionRange);
			}
		}
	}

	/**
	 * 演算子チェック.
	 * インデックス検索で指定可能な演算子のみOKとする。
	 * (eq gt ge lt le fm asc)
	 * @param equation 検索演算子
	 */
	public static void checkEquation(String equation) {
		// インデックス検索は以下の条件のみ
		// eq gt ge lt le fm asc
		if (Condition.EQUAL.equals(equation) ||
				Condition.GREATER_THAN.equals(equation) ||
				Condition.GREATER_THAN_OR_EQUAL.equals(equation) ||
				Condition.LESS_THAN.equals(equation) ||
				Condition.LESS_THAN_OR_EQUAL.equals(equation) ||
				Condition.FORWARD_MATCH.equals(equation) ||
				Condition.ASC.equals(equation)) {
			// OK
		} else {
			throw new IllegalParameterException("Equation is invalid. " + equation);
		}
	}

	/**
	 * 検索条件フォーマットチェック
	 * @param condition 検索条件
	 */
	private static void checkConditionProc(Condition condition) {
		checkNotNull(condition, "condition");
		checkNotNull(condition.getProp(), "condition property");
		String equation = condition.getEquations();
		checkNotNull(equation, "condition equation");
		checkEquation(equation);
	}

}
