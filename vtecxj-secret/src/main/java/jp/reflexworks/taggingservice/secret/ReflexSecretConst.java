package jp.reflexworks.taggingservice.secret;

/**
 * シークレット管理 定数クラス
 */
public interface ReflexSecretConst {

	/** Google Cloud プロジェクトID */
	static final String PROP_GCP_PROJECTID = "_gcp.projectid";
	/** Secret ManagerのサービスアカウントJSONファイル名 */
	static final String PROP_SECRET_FILE_SECRET = "_secret.file.secret";
	/** 暗号化キーのシークレット名 */
	static final String PROP_SECRETKEY_NAME = "_secret.secretkey.name";
	/** 暗号化キーのシークレットバージョン(オプション) */
	static final String PROP_SECRETKEY_VERSION = "_secret.secretkey.version";
	
	/** 最新バージョン取得時の指定値 */
	static final String VERSION_LATEST = "latest";

}
