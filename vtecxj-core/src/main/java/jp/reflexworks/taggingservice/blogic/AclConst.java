package jp.reflexworks.taggingservice.blogic;

import jp.reflexworks.atom.api.AtomConst;

/**
 * ACLに使用する定数
 */
public interface AclConst extends AtomConst {

	//<uri>{OpenID|Google|OAuth|*},{C|R|U|D|A|N},{pass}</uri>
	/** urn指定 id */
	public static final int IDX_ID = 0;
	/** urn指定 Acl Type */
	public static final int IDX_ACLTYPE = 1;
	/** urn指定 Password */
	public static final int IDX_PASS = 2;

	/** WSSE Createdの未来時間チェック(分) */
	public static final int CREATED_AFTER_MINUTE = 5;
	/** WSSE Createdの過去時間チェック(分)(デフォルト値) */
	public static final int CREATED_BEFORE_MINUTE = 5;

	/**
	 * 親階層取得時に使用するダミーのselfid.
	 * "#"はリクエスト送信の際に以降が送信されないため使用しない。
	 */
	public static final String URI_VALUE_DUMMY = "_";
	/** ダミーのselfid文字列長 */
	public static final int URI_VALUE_DUMMY_LENGTH = URI_VALUE_DUMMY.length();
	/**
	 * 親階層取得時に使用するダミーのselfid.
	 * "#"はリクエスト送信の際に以降が送信されないため使用しない。
	 */
	public static final String URI_LAYER_DUMMY = "/" + URI_VALUE_DUMMY;
	/** ダミーのselfid文字列長 */
	public static final int URI_LAYER_DUMMY_LENGTH = URI_LAYER_DUMMY.length();

}
