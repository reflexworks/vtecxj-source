package jp.reflexworks.taggingservice.env;

/**
 * Tagging BDB 設定定数クラス.
 */
public interface BDBEnvConst extends ReflexEnvConst {

	/** BDBデータ格納ディレクトリ */
	public static final String BDB_DIR = "_bdb.dir";
	/** BDB設定プロパティファイル */
	public static final String BDB_PROPERTY_FILENAME = "_bdb.property.filename";
	/** MessagePackデータの圧縮を行わないかどうか (BDB格納データ) **/
	public static final String DISABLE_DEFLATE_DATA = "_disable.deflate.data";
	/** BDB検索・更新に失敗したときのリトライ回数 **/
	public static final String BDB_RETRY_COUNT = "_bdb.retry.count";
	/** BDB検索・更新リトライ時のスリープ時間(ミリ秒) **/
	public static final String BDB_RETRY_WAITMILLIS = "_bdb.retry.waitmillis";
	/** BDBへのアクセスログを出力するかどうか */
	public static final String BDB_ENABLE_ACCESSLOG = "_bdb.enable.accesslog";
	/** BDBサーバへのリクエストログを出力するかどうか */
	public static final String BDB_ENABLE_REQUESTLOG = "_bdb.enable.requestlog";
	/** BDBの統計ログを出力するかどうか */
	public static final String BDB_ENABLE_STATSLOG = "_bdb.enable.statslog";
	/** BDB環境取得に失敗したときのリトライ回数 **/
	public static final String BDBENV_RETRY_COUNT = "_bdbenv.retry.count";
	/** BDB環境取得リトライ時のスリープ時間(ミリ秒) **/
	public static final String BDBENV_RETRY_WAITMILLIS = "_bdbenv.retry.waitmillis";
	/** Entryの最大サイズ(バイト) */
	public static final String ENTRY_MAX_BYTES = "_entry.max.bytes";
	/** データ移行前のバックアップシェル コマンドの配置パス */
	public static final String CMD_PATH_BACKUP = "_cmd.path.backup";
	/** ディスク使用量取得シェル コマンドの配置パス */
	public static final String CMD_PATH_DISKUSAGE = "_cmd.path.diskusage";

	/** BDBデータ格納ディレクトリデフォルト */
	public static final String BDB_DIR_DEFAULT = "/bdb";
	/** 設定デフォルト : BDBリトライ総数 */
	public static final int BDB_RETRY_COUNT_DEFAULT = 5;
	/** 設定デフォルト : BDBリトライ時のスリープ時間(ミリ秒) */
	public static final int BDB_RETRY_WAITMILLIS_DEFAULT = 200;
	/** 設定デフォルト : BDB環境取得リトライ総数 */
	public static final int BDBENV_RETRY_COUNT_DEFAULT = 100;
	/** 設定デフォルト : BDB環境取得リトライ時のスリープ時間(ミリ秒) */
	public static final int BDBENV_RETRY_WAITMILLIS_DEFAULT = 100;
	/** 設定デフォルト : Entryの最大サイズ(バイト) (1MB) */
	public static final long ENTRY_MAX_BYTES_DEFAULT = 1048576;
	/** 設定デフォルト : データ移行前のバックアップシェル コマンドの配置パス */
	public static final String CMD_PATH_BACKUP_DEFAULT = "/var/vtecx/sh/bdb_backup.sh";
	/** 設定デフォルト : ディスク使用量取得シェル コマンドの配置パス */
	public static final String CMD_PATH_DISKUSAGE_DEFAULT = "/var/vtecx/sh/bdb_diskusage.sh";

}
