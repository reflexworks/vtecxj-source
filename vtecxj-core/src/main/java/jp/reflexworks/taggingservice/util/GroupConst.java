package jp.reflexworks.taggingservice.util;

/**
 * Group定数
 */
public interface GroupConst {
	
	/** URI : /$groupadmin */
	public static final String URI_$GROUPADMIN = Constants.URI_SERVICE_GROUP_PREFIX + "groupadmin";
	/** URI prefix : group - groupadmin */
	public static final String URI_GROUP_GROUPADMIN_PREFIX = Constants.URI_GROUP + URI_$GROUPADMIN + '_';

	/** URI prefix : グループ管理フォルダ - user */
	public static final String URI_LAYER_USER = "/user";
	/** URI prefix : グループ管理対象のグループフォルダ */
	public static final String URI_GROUP_SLASH = Constants.URI_GROUP + "/";
	/** /_group/ の文字列長 */
	public static int URI_GROUP_SLASH_LEN = URI_GROUP_SLASH.length();

	/** URI : settings - adduserByGroupadmin */
	public static final String URI_SETTINGS_ADDUSER_BYGROUPADMIN = 
			Constants.URI_SETTINGS + "/adduserByGroupadmin";

}
