package jp.reflexworks.taggingservice.util;

/**
 * Index 定数クラス
 */
public interface InnerIndexConst {

	// {parent}#{Index項目}/{Index項目の値}\u0001{selfid}

	/** 親キーとIndex項目の区切り文字 */
	public static final String ITEM_PREFIX = "#";
	/** Index項目とIndex項目値の区切り文字 */
	public static final String ITEM_END = "/";

}
