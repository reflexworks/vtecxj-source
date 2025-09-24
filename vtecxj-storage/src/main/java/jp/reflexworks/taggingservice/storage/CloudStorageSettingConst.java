package jp.reflexworks.taggingservice.storage;

/**
 * サービスごとの設定項目一覧.
 * ここに定義されている項目は、/_settings/properties に設定できます。
 */
public interface CloudStorageSettingConst {

	/** サイズ別コンテンツ登録のサイズ指定 **/
	public static final String CONTENT_BYSIZE_PREFIX = "_content.bysize.";
	/** 署名付きURLの有効期限(分) */
	public static final String STORAGE_SIGNEDURL_EXPIRE_MIN = "_storage.signedurl.expire.min";
	/** bucketのCORS Origin */
	public static final String STORAGE_BUCKET_CORS_ORIGIN = "_storage.bucket.cors.origin";

}
