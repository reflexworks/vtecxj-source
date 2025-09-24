package jp.reflexworks.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ReflexApplication;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * 夜間バッチ実行フラグON・OFF.
 */
public class BatchBDBFlagApp {

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
	/** ビジネスロジッククラス名 : 夜間バッチ実行フラグON・OFF */
	public static final String CLASS_NAME = "jp.reflexworks.batch.BatchBDBFlagBlogic";

	/** テスト名 */
	public static final String APP_NAME = "[BatchBDBFlagApp]";

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(BatchBDBFlagApp.class);

	/**
	 * main
	 * @param args 引数
	 *             [0]start または end
	 */
	public static void main(String[] args) {
		try {
			// 引数チェック
			if (args == null || args.length < 1) {
				throw new IllegalArgumentException("引数を指定してください。[0]フラグ(start または end)");
			}
			String flg = args[0];
			if (StringUtils.isBlank(flg)) {
				throw new IllegalArgumentException("引数を指定してください。[0]フラグ(start または end)");
			}

			// [1]には /_batch_bdb を指定。
			String[] blogicArgs = new String[]{PROPERTY_FILE_NAME, SYSTEM_SERVICE,
					CLASS_NAME, flg, BatchBDBConst.URI_BATCH_BDB};
			ReflexApplication<Boolean> reflexApp = new ReflexApplication<Boolean>();
			reflexApp.exec(blogicArgs);

		} catch (Throwable e) {
			logger.error(APP_NAME + " Error occured. " + e.getClass().getName(), e);
		}

		System.exit(0);
	}

}
