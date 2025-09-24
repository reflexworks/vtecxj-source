package jp.reflexworks.taggingservice.model;

import jp.reflexworks.atom.api.Condition;
import jp.reflexworks.atom.mapper.FeedTemplateMapper.Meta;

/**
 * データストア検索用に編集した検索条件.
 */
public class EditedCondition {

	/** 親階層(isUriForwardMatch=false)、またはURI前方一致条件(isUriForwardMatch=true) */
	private String conditionUri;
	/** URI前方一致の場合true */
	private boolean isUriForwardMatch;

	/** DISTKEY 項目名 */
	private String distkeyItem;
	/** DISTKEY 値 */
	private String distkeyValue;

	/** インデックスに使用する条件 */
	private Condition indexCondition;
	/** インデックスに使用する条件と同じ項目で範囲指定を行っている場合に指定する条件 */
	private Condition indexConditionRange;
	/** インデックスに使用する項目のMeta情報 */
	private Meta indexMeta;
	/** インメモリ条件 (インデックス検索分を除いた条件) */
	private Condition[] innerConditions;
	/**
	 * 全文検索条件.
	 * インデックスに使用する条件か、全文検索条件のいずれかが指定される。
	 */
	private Condition ftCondition;

	/**
	 * コンストラクタ.
	 * @param parentUri 親階層(isUriForwardMatch=false)、またはURI前方一致条件(isUriForwardMatch=true)
	 * @param isUriForwardMatch URI前方一致検索の場合true
	 * @param indexCondition インデックスに使用する条件
	 * @param indexConditionRange インデックスに使用する条件と同じ項目で範囲指定を行っている場合に指定する条件
	 * @param indexMeta インデックスに使用する項目のMeta情報
	 * @param innerConditions インメモリ条件 (インデックス検索分を除いた条件)
	 * @param ftCondition 全文検索条件
	 * @param distkeyItem DISTKEY項目
	 * @param distkeyValue DISTKEYの値
	 */
	public EditedCondition(String conditionUri, boolean isUriForwardMatch,
			Condition indexCondition, Condition indexConditionRange,
			Meta indexMeta, Condition[] innerConditions, Condition ftCondition,
			String distkeyItem, String distkeyValue) {
		this.conditionUri = conditionUri;
		this.isUriForwardMatch = isUriForwardMatch;
		this.indexCondition = indexCondition;
		this.indexConditionRange = indexConditionRange;
		this.indexMeta = indexMeta;
		this.innerConditions = innerConditions;
		this.ftCondition = ftCondition;
		this.distkeyItem = distkeyItem;
		this.distkeyValue = distkeyValue;
	}

	/**
	 * 親階層またはURI前方一致条件を取得
	 * @return 親階層またはURI前方一致条件
	 */
	public String getConditionUri() {
		return conditionUri;
	}

	/**
	 * URI前方一致検索かどうかを取得
	 * @return URI前方一致検索の場合true
	 */
	public boolean isUriForwardMatch() {
		return isUriForwardMatch;
	}

	/**
	 * インデックスに使用する条件を取得
	 * @return インデックスに使用する条件
	 */
	public Condition getIndexCondition() {
		return indexCondition;
	}

	/**
	 * インデックスに使用する条件と同じ項目で範囲指定を行っている場合に指定する条件を取得
	 * @return インデックスに使用する条件と同じ項目で範囲指定を行っている場合に指定する条件
	 */
	public Condition getIndexConditionRange() {
		return indexConditionRange;
	}

	/**
	 * インデックスに使用する項目のMeta情報を取得
	 * @return インデックスに使用する項目のMeta情報
	 */
	public Meta getIndexMeta() {
		return indexMeta;
	}

	/**
	 * インメモリ条件 (インデックス検索分を除いた条件) を取得
	 * @return インメモリ条件 (インデックス検索分を除いた条件)
	 */
	public Condition[] getInnerConditions() {
		return innerConditions;
	}

	/**
	 * 全文検索条件を取得
	 * @return 全文検索条件
	 */
	public Condition getFtCondition() {
		return ftCondition;
	}

	/**
	 * DISTKEY項目名を取得
	 * @return DISTKEY項目名
	 */
	public String getDistkeyItem() {
		return distkeyItem;
	}

	/**
	 * DISTKEYの値を取得
	 * @return DISTKEYの値
	 */
	public String getDistkeyValue() {
		return distkeyValue;
	}

	/**
	 * このオブジェクトの文字列表現を取得.
	 * @reutrn 文字列
	 */
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("parentUri=");
		sb.append(conditionUri);
		sb.append(", isUriForwardMatch=");
		sb.append(isUriForwardMatch);
		sb.append(", indexCondition=");
		sb.append(indexCondition);
		if (indexConditionRange != null) {
			sb.append(", indexConditionRange=");
			sb.append(indexConditionRange);
		}
		sb.append(", innerConditions=");
		if (innerConditions != null && innerConditions.length > 0) {
			sb.append("[");
			boolean isFirst = true;
			for (Condition innerCondition : innerConditions) {
				if (isFirst) {
					isFirst = false;
				} else {
					sb.append(", ");
				}
				sb.append(innerCondition);
			}
			sb.append("]");
		} else {
			sb.append("null");
		}
		sb.append(", ftCondition=");
		sb.append(ftCondition);
		sb.append(", distkeyItem=");
		sb.append(distkeyItem);
		sb.append(", distkeyValue=");
		sb.append(distkeyValue);
		return sb.toString();
	}

}
