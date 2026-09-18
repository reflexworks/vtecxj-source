package jp.reflexworks.taggingservice.service;

import java.util.List;

/**
 * フレームワーク内サービス管理権限の認証情報
 */
public class ServiceAuthenticationConst {
	
	/** サービス管理者 **/
	public static final String ACCOUNT_SERVICEADMIN = "_serviceadmin";
	/** サービス管理者 UID **/
	public static final String UID_SERVICEADMIN = "2";
	/** 予約済みUID **/
	public static final List<String> UID_RESERVED = List.of(
			"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10");

}
