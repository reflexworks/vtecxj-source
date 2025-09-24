package jp.reflexworks.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ReflexApplication;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBServerType;
import jp.reflexworks.taggingservice.requester.BDBClientServerUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * BDBディスク使用量チェック処理.
 * 使用量が指定の割合を超えている場合、アラートメールを送信する。
 */
public class CheckBDBDiskUsageApp {
	
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
	public static final String CLASS_NAME = "jp.reflexworks.batch.CheckBDBDiskUsageBlogic";
	
	/** テスト名 */
	public static final String APP_NAME = "[CheckBDBDiskUsageApp]";
	
	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(CheckBDBDiskUsageApp.class);
	
	/**
	 * main
	 * @param args 引数
	 *             [0]BDBサーバ名・URL一覧ファイル名(フルパス)
	 *             [1]サーバタイプ(mnf,entry,idx,ft,al)
	 */
	public static void main(String[] args) {
		try {
			// 引数チェック
			if (args == null || args.length < 2) {
				throw new IllegalArgumentException("引数を指定してください。[0]BDBサーバ名・URL一覧ファイル名(フルパス)、[1]サーバタイプ(mnf,entry,idx,ft,al)");
			}
			String filepath = args[0];
			if (StringUtils.isBlank(filepath)) {
				throw new IllegalArgumentException("引数を指定してください。[0]BDBサーバ名・URL一覧ファイル名(フルパス)");
			}
			String serverTypeStr = args[1];
			if (StringUtils.isBlank(serverTypeStr)) {
				throw new IllegalArgumentException("引数を指定してください。[1]サーバタイプ(mnf,entry,idx,ft,al)");
			}
			BDBServerType serverType = BDBClientServerUtil.getServerTypeByStr(serverTypeStr);	// サーバタイプチェック
			if (serverType == null) {
				throw new IllegalArgumentException("引数[1]サーバタイプには (mnf,entry,idx,ft,al) のいずれかを指定してください。");
			}
			
			String[] blogicArgs = new String[]{PROPERTY_FILE_NAME, SYSTEM_SERVICE, 
					CLASS_NAME, filepath, serverTypeStr};
			ReflexApplication<Boolean> reflexApp = new ReflexApplication<Boolean>();
			reflexApp.exec(blogicArgs);
			
		} catch (Throwable e) {
			logger.error(APP_NAME + " Error occured. " + e.getClass().getName(), e);
		}

		System.exit(0);
	}
	
}
