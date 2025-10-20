package jp.reflexworks.vtecx.init;

import java.io.IOException;
import java.text.ParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ReflexInitialization;
import jp.reflexworks.taggingservice.util.Constants;

/**
 * 初期データ登録 起動クラス.
 */
public class InitializeSystemApp {

	/**
	 * バッチ用起動メソッド.
	 * @param args (1) プロパティファイル
	 *             (2) サービス名
	 *             (3) ビジネスロジックのクラス名
	 *             (4〜) ビジネスロジックへの引き渡し文字列
	 */

	/** プロパティ名 */
	public static final String PROPERTY_FILE_NAME = InitializeConst.PROPERTY_FILE_NAME;

	/** パッチ名 */
	public static final String APP_NAME = "[InitializeSystemApp]";

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(InitializeSystemApp.class);

	/**
	 * main
	 * @param args 引数
	 */
	public static void main(String[] args) {
		int exitStatus = 0;
		try {
			InitializeSystemApp myApp = new InitializeSystemApp();
			myApp.initAdminBySystem();
			
		} catch (Throwable e) {
			logger.error(APP_NAME + " Error occured. " + e.getClass().getName(), e);
			exitStatus = -1;
		}
		System.exit(exitStatus);
	}

	/**
	 * システム管理サービスへサービスEntry登録
	 * 最初に実行すること
	 */
	public void initAdminBySystem() throws ParseException, IOException {

		/** システム管理サービス名 */
		final String SERVICE_NAME = "admin";
		/** ビジネスロジッククラス名 */
		final String CLASS_NAME = "jp.reflexworks.vtecx.init.InitializeSystemBlogic";
		/** サービスステータス */
		final String SERVICE_STATUS = Constants.SERVICE_STATUS_PRODUCTION;

		String[] args = new String[]{PROPERTY_FILE_NAME, SERVICE_NAME, CLASS_NAME, SERVICE_STATUS};

		logger.debug(APP_NAME + "InitializeSystemApp start");

		ReflexInitialization<FeedBase> reflexApp = new ReflexInitialization<FeedBase>();
		reflexApp.exec(args);

		logger.debug(APP_NAME + "InitializeSystemApp end");
	}

}
