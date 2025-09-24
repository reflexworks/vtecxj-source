package jp.reflexworks.taggingservice.migrate;

import jp.reflexworks.taggingservice.util.Constants;

/**
 * データ移行の前のBDBバックアップ 定数クラス
 */
public interface BackupForMigrateConst {

	/** 設定 : BDBバックアップのバケット名 */
	public static final String PROP_BDBBACKUP_BUCKET = "_bdbbackup.bucket";
	/** 設定 : BDBバックアップのディレクトリ名 */
	public static final String PROP_BDBBACKUP_DIRECTORY = "_bdbbackup.directory";

	/** 設定デフォルト : BDBバックアップのディレクトリ名 */
	public static final String BDBBACKUP_DIRECTORY_DEFAULT = "temp_backup_for_migration";

	/** BDBバックアップ Method */
	public static final String METHOD = Constants.PUT;

	/** BDBバックアップの現在日時フォルダフォーマット */
	public static final String DATE_FORMAT = "yyyyMMddHHmmss";

}
