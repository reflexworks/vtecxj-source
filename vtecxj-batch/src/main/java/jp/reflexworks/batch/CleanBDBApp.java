package jp.reflexworks.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ReflexApplication;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * BDBクリーンリクエスト処理.
 *  ・各BDBサーバにクリーンリクエストを行う。
 *  ・その際、対象のサーバの有効な名前空間をリクエストヘッダに設定する。
 */
public class CleanBDBApp {

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
	public static final String CLASS_NAME = "jp.reflexworks.batch.CleanBDBBlogic";

	/** テスト名 */
	public static final String APP_NAME = "[CleanBDBApp]";

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(CleanBDBApp.class);

	/**
	 * main
	 * @param args 引数
	 *             [0]サーバ一覧ファイル名(サーバ名:URL)(フルパス)
	 *             [1]有効なサービスのサーバごとの名前空間一覧格納ディレクトリ(フルパス)
	 *                配下のファイルの内容は(サービス名:名前空間)のリスト
	 */
	public static void main(String[] args) {
		try {
			// 引数チェック
			if (args == null || args.length < 2) {
				throw new IllegalArgumentException("引数を指定してください。[0]サーバ一覧ファイル名、[1]有効なサービスのサーバごとの名前空間一覧格納ディレクトリ");
			}
			String serversFilename = args[0];
			if (StringUtils.isBlank(serversFilename)) {
				throw new IllegalArgumentException("引数[0]のサーバ一覧ファイル名を指定してください。");
			}
			String validNamespacesDirname = args[1];
			if (StringUtils.isBlank(validNamespacesDirname)) {
				throw new IllegalArgumentException("引数[1]の有効なサービスのサーバごとの名前空間一覧格納ディレクトリを指定してください。");
			}

			String[] blogicArgs = new String[]{PROPERTY_FILE_NAME, SYSTEM_SERVICE,
					CLASS_NAME, serversFilename, validNamespacesDirname};

			ReflexApplication<Boolean> reflexApp = new ReflexApplication<Boolean>();
			reflexApp.exec(blogicArgs);

		} catch (Throwable e) {
			logger.error(APP_NAME + " Error occured. " + e.getClass().getName(), e);
		}

		System.exit(0);
	}

}
