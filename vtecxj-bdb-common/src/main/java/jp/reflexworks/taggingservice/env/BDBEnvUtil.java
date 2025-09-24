package jp.reflexworks.taggingservice.env;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexStatic;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.reflexworks.taggingservice.plugin.TaskQueueManager;
import jp.sourceforge.reflex.util.FileUtil;
import jp.sourceforge.reflex.util.StringUtils;

public class BDBEnvUtil {

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(BDBEnvUtil.class);

	/**
	 * FeedTemplateMapperを取得.
	 * すでにメモリ上に保持されているものを取得します。データストア参照は行いません。
	 * @return FeedTemplateMapper
	 */
	public static FeedTemplateMapper getAtomResourceMapper() {
		ReflexBDBEnvBase bdbEnv = (ReflexBDBEnvBase)ReflexStatic.getEnv();
		if (bdbEnv != null) {
			return bdbEnv.getAtomResourceMapper();
		}
		throw new IllegalStateException("The TaggingService environment does not exist.");
	}

	/**
	 * String型の値を取得.
	 * システム管理サービスの値を取得します。
	 * @param name プロパティ名
	 * @return 値。指定がない場合はnullを返します。
	 */
	public static String getSystemProp(String name) {
		ReflexBDBEnvBase env = (ReflexBDBEnvBase)ReflexStatic.getEnv();
		return env.getSystemProp(name, null);
	}

	/**
	 * String型の値を取得.
	 * システム管理サービスの値を取得します。
	 * @param name プロパティ名
	 * @param defVal デフォルト値
	 * @return 値
	 */
	public static String getSystemProp(String name, String defVal) {
		ReflexBDBEnvBase env = (ReflexBDBEnvBase)ReflexStatic.getEnv();
		return env.getSystemProp(name, defVal);
	}

	/**
	 * int型の値を取得.
	 * システム管理サービスの値を取得します。
	 * @param name プロパティ名
	 * @param defVal デフォルト値
	 * @return 値
	 * @throws InvalidServiceSettingException Integerに変換できない値が設定されている場合
	 */
	public static int getSystemPropInt(String name, int defVal) {
		ReflexBDBEnvBase env = (ReflexBDBEnvBase)ReflexStatic.getEnv();
		return env.getSystemPropInteger(name, defVal);
	}

	/**
	 * long型の値を取得.
	 * システム管理サービスの値を取得します。
	 * @param name プロパティ名
	 * @param defVal デフォルト値
	 * @return 値
	 * @throws InvalidServiceSettingException Longに変換できない値が設定されている場合
	 */
	public static long getSystemPropLong(String name, long defVal) {
		ReflexBDBEnvBase env = (ReflexBDBEnvBase)ReflexStatic.getEnv();
		return env.getSystemPropLong(name, defVal);
	}

	/**
	 * double型の値を取得.
	 * システム管理サービスの値を取得します。
	 * @param name プロパティ名
	 * @param defVal デフォルト値
	 * @return 値
	 * @throws InvalidServiceSettingException Doubleに変換できない値が設定されている場合
	 */
	public static double getSystemPropDouble(String name, double defVal) {
		ReflexBDBEnvBase env = (ReflexBDBEnvBase)ReflexStatic.getEnv();
		return env.getSystemPropDouble(name, defVal);
	}

	/**
	 * boolean型の値を取得.
	 * システム管理サービスの値を取得します。
	 * @param name プロパティ名
	 * @param defVal デフォルト値
	 * @return 値
	 * @throws InvalidServiceSettingException Booleanに変換できない値が設定されている場合
	 */
	public static boolean getSystemPropBoolean(String name, boolean defVal) {
		ReflexBDBEnvBase env = (ReflexBDBEnvBase)ReflexStatic.getEnv();
		return env.getSystemPropBoolean(name, defVal);
	}

	/**
	 * システムサービスの、先頭が指定されたキーと等しい環境設定情報一覧を取得
	 * @param prefix キーの先頭部分
	 * @return 環境設定情報一覧
	 */
	public static Map<String, String> getSystemPropMap(String prefix) {
		ReflexBDBEnvBase env = (ReflexBDBEnvBase)ReflexStatic.getEnv();
		return env.getSystemPropMap(prefix);
	}

	/**
	 * システムサービスの先頭が指定されたキーと等しい環境設定情報一覧を取得
	 * @param prefix キーの先頭部分
	 * @return 環境設定情報一覧をSortedMapで返却
	 */
	public static SortedMap<String, String> getSystemPropSortedMap(String prefix) {
		ReflexBDBEnvBase env = (ReflexBDBEnvBase)ReflexStatic.getEnv();
		return env.getSystemPropSortedMap(prefix);
	}

	/**
	 * システムサービスの、先頭が指定されたキーと等しい環境設定情報一覧を取得
	 * @param prefix キーの先頭部分
	 * @return 環境設定情報一覧をSetで返却
	 */
	public static Set<String> getSystemPropSet(String prefix) {
		ReflexBDBEnvBase env = (ReflexBDBEnvBase)ReflexStatic.getEnv();
		return env.getSystemPropSet(prefix);
	}

	/**
	 * サーバ稼働中かどうか.
	 * 起動処理中の場合falseを返す。
	 * @return サーバ稼働中の場合true
	 */
	public static boolean isRunning() {
		ReflexBDBEnvBase env = (ReflexBDBEnvBase)ReflexStatic.getEnv();
		return env.isRunning();
	}

	/**
	 * BDBデータ格納ディレクトリを取得.
	 * @return BDBデータ格納ディレクトリ
	 */
	public static String getBDBHomeDir() {
		String bdbHome = ReflexEnvUtil.getSystemProp(BDBEnvConst.BDB_DIR,
				BDBEnvConst.BDB_DIR_DEFAULT);
		String stage = getStage();
		StringBuilder sb = new StringBuilder();
		sb.append(bdbHome);
		if (!StringUtils.isBlank(stage)) {
			sb.append(File.separator);
			sb.append(stage);
		}
		return sb.toString();
	}

	/**
	 * BDBデータ格納ディレクトリを取得.
	 * @param namespace 名前空間
	 * @return BDBデータ格納ディレクトリ ({_bdb.dir}/namespace)
	 */
	public static String getBDBDirByNamespace(String namespace) {
		String bdbDir = getBDBHomeDir();
		StringBuilder sb = new StringBuilder();
		sb.append(bdbDir);
		sb.append(File.separator);
		sb.append(namespace);
		return sb.toString();
	}

	/**
	 * 環境ステージを取得.
	 * @return 環境ステージ
	 */
	public static final String getStage() {
		return ReflexEnvUtil.getSystemProp(BDBEnvConst.ENV_STAGE, BDBEnvConst.ENV_STAGE_DEFAULT);
	}

	/**
	 * BDB設定プロパティを取得.
	 * @return BDB設定プロパティ
	 */
	public static Properties getBDBProperties() {
		String bdbPropertyFilename = ReflexEnvUtil.getSystemProp(
				BDBEnvConst.BDB_PROPERTY_FILENAME, null);
		if (!StringUtils.isBlank(bdbPropertyFilename)) {
			try {
				String bdbPropertyFilepath = FileUtil.getResourceFilename(bdbPropertyFilename);
				if (!StringUtils.isBlank(bdbPropertyFilepath)) {
					try (InputStream in = FileUtil.getInputStreamFromFile(bdbPropertyFilepath)) {
						Properties properties = new Properties();
						properties.load(in);
						return properties;
					}
				}
			} catch (IOException e) {
				StringBuilder sb = new StringBuilder();
				sb.append("[getBDBProperties] ");
				sb.append(e.getClass().getSimpleName());
				sb.append(" ");
				sb.append(e.getMessage());
				logger.warn(sb.toString());
			}
		}
		return null;
	}

	/**
	 * Index最大数を取得.
	 * @return Index最大数
	 */
	public static int getIndexLimit() {
		return ReflexEnvUtil.getSystemPropInt(BDBEnvConst.INDEX_LIMIT,
				BDBEnvConst.INDEX_LIMIT_DEFAULT);
	}

	/**
	 * Feed内のEntry最大値を取得.
	 * @return Feed内のEntry最大値
	 */
	public static int getEntryNumberLimit() {
		return ReflexEnvUtil.getSystemPropInt(BDBEnvConst.ENTRY_NUMBER_LIMIT,
				BDBEnvConst.ENTRY_NUMBER_LIMIT_DEFAULT);
	}

	/**
	 * フェッチ最大値を取得.
	 * @return フェッチ最大値
	 */
	public static int getFetchLimit() {
		return ReflexEnvUtil.getSystemPropInt(BDBEnvConst.FETCH_LIMIT,
				BDBEnvConst.FETCH_LIMIT_DEFAULT);
	}

	/**
	 * BDBのアクセス失敗時リトライ総数を取得.
	 * @return BDBのアクセス失敗時リトライ総数
	 */
	public static int getBDBRetryCount() {
		return ReflexEnvUtil.getSystemPropInt(BDBEnvConst.BDB_RETRY_COUNT,
				BDBEnvConst.BDB_RETRY_COUNT_DEFAULT);
	}

	/**
	 * BDBのアクセス失敗時リトライ時のスリープ時間を取得.
	 * @return BDBのアクセス失敗時のスリープ時間(ミリ秒)
	 */
	public static int getBDBRetryWaitmillis() {
		return ReflexEnvUtil.getSystemPropInt(BDBEnvConst.BDB_RETRY_WAITMILLIS,
				BDBEnvConst.BDB_RETRY_WAITMILLIS_DEFAULT);
	}

	/**
	 * BDBの環境取得失敗時リトライ総数を取得.
	 * @return BDBの環境取得失敗時リトライ総数
	 */
	public static int getBDBEnvRetryCount() {
		return ReflexEnvUtil.getSystemPropInt(BDBEnvConst.BDBENV_RETRY_COUNT,
				BDBEnvConst.BDBENV_RETRY_COUNT_DEFAULT);
	}

	/**
	 * BDBの環境取得失敗時リトライ時のスリープ時間を取得.
	 * @return BDBの環境取得失敗時のスリープ時間(ミリ秒)
	 */
	public static int getBDBEnvRetryWaitmillis() {
		return ReflexEnvUtil.getSystemPropInt(BDBEnvConst.BDBENV_RETRY_WAITMILLIS,
				BDBEnvConst.BDBENV_RETRY_WAITMILLIS_DEFAULT);
	}

	/**
	 * BDBデータをDeflate圧縮するかどうか.
	 * @return BDBデータをDeflate圧縮しない場合true
	 */
	public static boolean isDisableDeflate() {
		return ReflexEnvUtil.getSystemPropBoolean(BDBEnvConst.DISABLE_DEFLATE_DATA, false);
	}

	/**
	 * レスポンスについてGZIP圧縮を行うかどうかを取得.
	 * この値はシステムで固定となる想定。
	 * @return レスポンスデータをGZIP圧縮する場合true
	 */
	public static boolean isGZip() {
		return true;	// 
	}

	/**
	 * レスポンスのXMLに名前空間を出力するかどうかを取得.
	 * この値はシステムで固定となる想定。
	 * @return レスポンスのXMLに名前空間を出力する場合true
	 */
	public static boolean isPrintNamespace() {
		return false;	// 
	}

	/**
	 * レスポンスヘッダに、ブラウザにキャッシュを残さないオプションを付けるかどうかを取得.
	 * @return ブラウザにキャッシュを残さないオプションを付ける場合true
	 */
	public static boolean isNoCache(ReflexRequest req) {
		return true;	// 
	}

	/**
	 * レスポンスヘッダに、フレームオプションのSameOrigin指定を付けるかどうかを取得.
	 * @return フレームオプションのSameOrigin指定を付ける場合true
	 */
	public static boolean isSameOrigin(ReflexRequest req) {
		return true;	// 
	}

	/**
	 * 非同期処理管理プラグインを取得.
	 * @return Task queue manager
	 */
	public static TaskQueueManager getTaskQueueManager() {
		ReflexBDBEnvBase env = (ReflexBDBEnvBase)ReflexStatic.getEnv();
		return env.getTaskQueueManager();
	}

	/**
	 * シャットダウン処理.
	 */
	public static void destroy() {
		ReflexBDBEnvBase env = (ReflexBDBEnvBase)ReflexStatic.getEnv();
		env.close();
		ReflexStatic.close();
	}

	/**
	 * データストアの統計ログを出力するかどうか.
	 * @return データストアの統計ログを出力する場合true
	 */
	public static boolean isEnableStatsLog() {
		return ReflexEnvUtil.getSystemPropBoolean(
				BDBEnvConst.BDB_ENABLE_STATSLOG, false) && logger.isDebugEnabled();
	}

}
