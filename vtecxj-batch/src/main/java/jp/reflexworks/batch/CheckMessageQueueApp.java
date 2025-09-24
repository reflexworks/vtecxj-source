package jp.reflexworks.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ReflexApplication;

/**
 * メッセージキュー未送信チェック処理のリクエスト処理.
 *  ・有効なサービス一覧を取得
 *  ・バッチジョブサーバにリクエストする
 */
public class CheckMessageQueueApp {
	
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
	public static final String CLASS_NAME = "jp.reflexworks.batch.CheckMessageQueueBlogic";

	/** テスト名 */
	public static final String APP_NAME = "[CheckMessageQueueApp]";
	
	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(CheckMessageQueueApp.class);
	
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
