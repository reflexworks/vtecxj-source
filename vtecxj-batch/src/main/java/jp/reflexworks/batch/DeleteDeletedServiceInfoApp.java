package jp.reflexworks.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ReflexApplication;

/**
 * 一定期間経過した削除済みサービスの情報削除.
 *
 * 注) eclipseから実行する際、クラスパスに「target/test-classes」を追加すること。
 */
public class DeleteDeletedServiceInfoApp {

	/**
	 * バッチ用起動メソッド.
	 * @param args (1) プロパティファイル
	 *             (2) サービス名
	 *             (3) ビジネスロジックのクラス名
	 *             (4〜) ビジネスロジックへの引き渡し文字列
	 */

	/** プロパティファイル名 */
	private static final String PROPERTY_FILE_NAME = VtecxBatchConst.PROPERTY_FILE_NAME;
	/** システムサービス名 */
	private static final String SYSTEM_SERVICE = VtecxBatchConst.SYSTEM_SERVICE;

	/** ビジネスロジッククラス名 */
	private static final String CLASS_NAME = "jp.reflexworks.batch.DeleteDeletedServiceInfoBlogic";

	/** テスト名 */
	private static final String APP_NAME = "[DeleteDeletedServiceInfoApp]";

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(DeleteDeletedServiceInfoApp.class);

	/**
	 * main
	 * @param args 引数
	 */
	public static void main(String[] args) {
		try {
			String[] blogicArgs = new String[]{PROPERTY_FILE_NAME, SYSTEM_SERVICE,
					CLASS_NAME};
			ReflexApplication<Boolean> reflexApp = new ReflexApplication<Boolean>();
			reflexApp.exec(blogicArgs);

		} catch (Throwable e) {
			logger.error(APP_NAME + " Error occured. " + e.getClass().getName(), e);
		}

		System.exit(0);
	}

}
