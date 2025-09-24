package jp.reflexworks.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ReflexApplication;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * 有効な名前空間一覧を取得し、ファイルに出力する.
 * {サービス名}:{名前空間}
 *
 * 注) eclipseから実行する際、クラスパスに「target/test-classes」を追加すること。
 */
public class WriteNamespacesApp {

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
	private static final String CLASS_NAME = "jp.reflexworks.batch.WriteNamespacesBlogic";

	/** テスト名 */
	private static final String APP_NAME = "[WriteNamespacesApp]";

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(WriteNamespacesApp.class);

	/**
	 * main
	 * @param args 引数
	 *             [0]出力ファイル名(フルパス)
	 *             [1]抽出対象サービスステータス(複数指定の場合カンマ区切り)
	 */
	public static void main(String[] args) {
		try {
			// 引数チェック
			if (args == null || args.length <= 1) {
				throw new IllegalArgumentException("引数を指定してください。[0]出力ファイル名(フルパス)、[1]抽出対象サービスステータス");
			}
			String filepath = args[0];
			String statuses = args[1];
			if (StringUtils.isBlank(filepath) || StringUtils.isBlank(statuses)) {
				throw new IllegalArgumentException("引数を指定してください。[0]出力ファイル名(フルパス)、[1]抽出対象サービスステータス");
			}

			String[] blogicArgs = new String[]{PROPERTY_FILE_NAME, SYSTEM_SERVICE,
					CLASS_NAME, filepath, statuses};
			ReflexApplication<Boolean> reflexApp = new ReflexApplication<Boolean>();
			reflexApp.exec(blogicArgs);

		} catch (Throwable e) {
			logger.error(APP_NAME + " Error occured. " + e.getClass().getName(), e);
		}

		System.exit(0);
	}

}
