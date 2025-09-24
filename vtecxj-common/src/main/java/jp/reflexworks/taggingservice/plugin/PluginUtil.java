package jp.reflexworks.taggingservice.plugin;

import java.lang.reflect.InvocationTargetException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.exception.PluginException;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * プラグインクラスの生成を行うクラス.
 */
public class PluginUtil {
	
	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(PluginUtil.class);
	
	/**
	 * プラグインオブジェクトを生成.
	 * @param clsName クラス名
	 * @param defaultCls デフォルトクラス
	 * @throws PluginException クラス生成時のエラー。causeに実際のエラーを設定。
	 */
	public static Object newInstance(String clsName, Class defaultCls) 
	throws PluginException {
		Class clsPlugin = forName(clsName);
		if (clsPlugin == null && defaultCls != null) {
			clsPlugin = defaultCls;
		}
		return newInstance(clsPlugin);
	}

	/**
	 * プラグインオブジェクトを生成.
	 * @param clsPlugin プラグインクラス
	 * @throws PluginException クラス生成時のエラー。causeに実際のエラーを設定。
	 */
	public static Object newInstance(Class clsPlugin) 
	throws PluginException {
		if (clsPlugin != null) {
			try {
				// プラグインクラスオブジェクトの生成
				return clsPlugin.getDeclaredConstructor().newInstance();
			} catch (InstantiationException | IllegalAccessException |
					NoSuchMethodException | InvocationTargetException e) {
				throw new PluginException(e);
			}
		}
		return null;
	}
	
	/**
	 * クラス名からクラスオブジェクトを取得.
	 * @param clsName クラス名
	 * @return クラスオブジェクト
	 * @throws PluginException クラス生成時のエラー。causeに実際のエラーを設定。
	 */
	public static Class forName(String clsName) 
	throws PluginException {
		if (!StringUtils.isBlank(clsName)) {
			try {
				return Class.forName(clsName);
			} catch (ClassNotFoundException e) {
				throw new PluginException(e);
			}
		}
		return null;
	}

}
