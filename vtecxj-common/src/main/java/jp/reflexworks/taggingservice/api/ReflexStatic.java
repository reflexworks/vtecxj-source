package jp.reflexworks.taggingservice.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.exception.StaticDuplicatedException;
import jp.reflexworks.taggingservice.plugin.ClosingForShutdown;

/**
 * static変数を保持する.
 */
public class ReflexStatic {

	/** 環境情報名 */
	private static final String NAME_REFLEX_ENV = "_reflexEnv";

	/** 環境情報マップ */
	private static ConcurrentMap<String, Object> statics =
			new ConcurrentHashMap<String, Object>();

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(ReflexStatic.class);

	/**
	 * コンストラクタ.
	 * このクラスはインスタンスを生成しない。
	 */
	private ReflexStatic() {};

	/**
	 * 環境情報を設定.
	 * @param pEnv 環境情報
	 */
	public static void setEnv(BaseReflexEnv pEnv) throws StaticDuplicatedException {
		setStatic(NAME_REFLEX_ENV, pEnv);
	}

	/**
	 * 環境情報を取得.
	 * @return 環境情報
	 */
	public static BaseReflexEnv getEnv() {
		BaseReflexEnv env = (BaseReflexEnv)getStatic(NAME_REFLEX_ENV);
		if (env == null) {
			throw new IllegalStateException("ReflexEnv is null.");
		}
		return env;
	}

	/**
	 * staticな情報を設定.
	 * 上書きはできません。
	 * @param name 名前
	 * @param obj static情報
	 */
	public static void setStatic(String name, Object obj)
	throws StaticDuplicatedException {
		Object ret = statics.putIfAbsent(name, obj);

		// 存在しなければnull
		// 既に値が存在していれば、その値
		if (ret != null) {
			throw new StaticDuplicatedException();
		}
	}

	/**
	 * staticな情報を設定.
	 * 上書きはできません。
	 * @param name 名前
	 */
	public static void deleteStatic(String name)
	throws StaticDuplicatedException {
		if (NAME_REFLEX_ENV.equals(name)) {
			logger.warn("This static infomation can't delete : " + name);
		}

		// 戻り値 : keyに以前に関連付けられていた値。keyのマッピングが存在しなかった場合はnull。
		statics.remove(name);
	}

	/**
	 * staticな情報を取得.
	 * @param name 名前
	 * @return static情報
	 */
	public static Object getStatic(String name) {
		return statics.get(name);
	}

	/**
	 * シャットダウン時の処理.
	 */
	public static void close() {
		for (Map.Entry<String, Object> mapEntry : statics.entrySet()) {
			Object obj = mapEntry.getValue();
			if (obj instanceof ClosingForShutdown) {
				try {
					((ClosingForShutdown)obj).close();
				} catch (Throwable e) {
					logger.warn("[close] " + obj.getClass().getName() +
							" " + e.getClass().getName() +
							" " + e.getMessage());
				}
			}
		}
		statics.clear();
	}

}
