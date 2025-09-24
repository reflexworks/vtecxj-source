package jp.reflexworks.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ReflexApplication;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * ストレージクリーン処理.
 *  ・不要なバケットを削除する。
 */
public class CleanStorageApp {

	/**
	 * バッチ用起動メソッド.
	 * @param args (1) プロパティファイル
	 *             (2) サービス名
	 *             (3) ビジネスロジックのクラス名
	 *             (4〜) ビジネスロジックへの引き渡し文字列
	 */

	/** プロパティ名 */
	public static final String PROPERTY_FILE_NAME = VtecxBatchConst.PROPERTY_FILE_NAME;
	/** システムサービス名 */
	public static final String SYSTEM_SERVICE = "admin";
	/** ビジネスロジッククラス名 */
	public static final String CLASS_NAME = "jp.reflexworks.batch.CleanStorageBlogic";

	/** テスト名 */
	public static final String APP_NAME = "[CleanStorageApp]";

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(CleanStorageApp.class);

	/**
	 * main
	 * @param args 引数
	 *             [0]有効なサービス・名前空間マップファイル名(サービス名:名前空間)(フルパス)
	 */
	public static void main(String[] args) {
		try {
			// 引数チェック
			if (args == null || args.length < 1) {
				throw new IllegalArgumentException("引数を指定してください。[0]有効なサービス・名前空間マップファイル名");
			}
			String validNamespacesFilename = args[0];
			if (StringUtils.isBlank(validNamespacesFilename)) {
				throw new IllegalArgumentException("引数を指定してください。[0]有効なサービス・名前空間マップファイル名");
			}

			String[] blogicArgs = new String[]{PROPERTY_FILE_NAME, SYSTEM_SERVICE,
					CLASS_NAME, validNamespacesFilename};

			ReflexApplication<Boolean> reflexApp = new ReflexApplication<Boolean>();
			reflexApp.exec(blogicArgs);

		} catch (Throwable e) {
			logger.error(APP_NAME + " Error occured. " + e.getClass().getName(), e);
		}

		System.exit(0);
	}

}
