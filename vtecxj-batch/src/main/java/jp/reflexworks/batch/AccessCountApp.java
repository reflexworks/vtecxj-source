package jp.reflexworks.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ReflexApplication;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * 課金のためのカウンタ情報集計処理.
 *  ・アクセスカウンタをRedisからデータストアに移動する。
 *  ・ストレージ容量をRedisに登録する。
 */
public class AccessCountApp {
	
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
	public static final String CLASS_NAME = "jp.reflexworks.batch.AccessCountBlogic";

	/** テスト名 */
	public static final String APP_NAME = "[AccessCountApp]";
	
	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(AccessCountApp.class);
	
	/**
	 * main
	 * @param args 引数
	 *             [0]gsutilの格納ディレクトリ
	 */
	public static void main(String[] args) {
		try {
			// 引数チェック
			if (args == null || args.length <= 0) {
				throw new IllegalArgumentException("引数を指定してください。[0]gsutilの格納ディレクトリ");
			}
			String gsutilDir = args[0];
			if (StringUtils.isBlank(gsutilDir)) {
				throw new IllegalArgumentException("引数を指定してください。[0]gsutilの格納ディレクトリ");
			}
			
			String[] blogicArgs = new String[]{PROPERTY_FILE_NAME, SYSTEM_SERVICE, 
					CLASS_NAME, gsutilDir};
			
			ReflexApplication<Boolean> reflexApp = new ReflexApplication<Boolean>();
			reflexApp.exec(blogicArgs);
			
		} catch (Throwable e) {
			logger.error(APP_NAME + " Error occured. " + e.getClass().getName(), e);
		}

		System.exit(0);
	}
	
}
