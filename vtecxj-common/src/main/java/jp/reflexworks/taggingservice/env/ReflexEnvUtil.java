package jp.reflexworks.taggingservice.env;

import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.taggingservice.api.BaseReflexEnv;
import jp.reflexworks.taggingservice.api.ReflexStatic;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;

/**
 * 環境情報ユーティリティ.
 */
public class ReflexEnvUtil {

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(ReflexEnvUtil.class);

	/**
	 * コンストラクタ(生成不可).
	 */
	private ReflexEnvUtil() {}

	/**
	 * FeedTemplateMapperを取得.
	 * すでにメモリ上に保持されているものを取得します。データストア参照は行いません。
	 * @param serviceName サービス名
	 * @return FeedTemplateMapper
	 */
	public static FeedTemplateMapper getResourceMapper(String serviceName) {
		BaseReflexEnv env = ReflexStatic.getEnv();
		if (env != null) {
			return env.getResourceMapper(serviceName);
		}
		throw new IllegalStateException("TaggingService environment does not exist.");
	}

	/**
	 * String型の値を取得.
	 * システム管理サービスの値を取得します。
	 * @param name プロパティ名
	 * @param defVal デフォルト値
	 * @return 値
	 */
	public static String getSystemProp(String name, String defVal) {
		BaseReflexEnv env = ReflexStatic.getEnv();
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
		BaseReflexEnv env = ReflexStatic.getEnv();
		try {
			return env.getSystemPropInteger(name, defVal);
		} catch (InvalidServiceSettingException e) {
			logger.warn("[getSystemPropInt] InvalidServiceSettingException : " + e.getMessage());
			return defVal;
		}
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
		BaseReflexEnv env = ReflexStatic.getEnv();
		try {
			return env.getSystemPropLong(name, defVal);
		} catch (InvalidServiceSettingException e) {
			logger.warn("[getSystemPropLong] InvalidServiceSettingException : " + e.getMessage());
			return defVal;
		}
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
		BaseReflexEnv env = ReflexStatic.getEnv();
		try {
			return env.getSystemPropDouble(name, defVal);
		} catch (InvalidServiceSettingException e) {
			logger.warn("[getSystemPropInt] InvalidServiceSettingException : " + e.getMessage());
			return defVal;
		}
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
		BaseReflexEnv env = ReflexStatic.getEnv();
		try {
			return env.getSystemPropBoolean(name, defVal);
		} catch (InvalidServiceSettingException e) {
			logger.warn("[getSystemPropLong] InvalidServiceSettingException : " + e.getMessage());
			return defVal;
		}
	}

	/**
	 * システムサービスの、先頭が指定されたキーと等しい環境設定情報一覧を取得
	 * @param prefix キーの先頭部分
	 * @return 環境設定情報一覧
	 */
	public static Map<String, String> getSystemPropMap(String prefix) {
		BaseReflexEnv env = ReflexStatic.getEnv();
		return env.getSystemPropMap(prefix);
	}

	/**
	 * システムサービスの先頭が指定されたキーと等しい環境設定情報一覧を取得
	 * @param prefix キーの先頭部分
	 * @return 環境設定情報一覧をSortedMapで返却
	 */
	public static SortedMap<String, String> getSystemPropSortedMap(String prefix) {
		BaseReflexEnv env = ReflexStatic.getEnv();
		return env.getSystemPropSortedMap(prefix);
	}

	/**
	 * システムサービスの、先頭が指定されたキーと等しい環境設定情報一覧を取得
	 * @param prefix キーの先頭部分
	 * @return 環境設定情報一覧をSetで返却
	 */
	public static Set<String> getSystemPropSet(String prefix) {
		BaseReflexEnv env = ReflexStatic.getEnv();
		return env.getSystemPropSet(prefix);
	}

	/**
	 * サーバ稼働中かどうか.
	 * 起動処理中の場合falseを返す。
	 * @return サーバ稼働中の場合true
	 */
	public static boolean isRunning() {
		BaseReflexEnv env = ReflexStatic.getEnv();
		return env.isRunning();
	}

}
