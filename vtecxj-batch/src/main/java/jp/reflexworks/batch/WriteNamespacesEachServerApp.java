package jp.reflexworks.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ReflexApplication;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * 有効な名前空間一覧を取得し、ファイルに出力する.
 * ファイルの内容: {サービス名}:{名前空間}
 *
 * 注) eclipseから実行する際、クラスパスに「target/test-classes」を追加すること。
 */
public class WriteNamespacesEachServerApp {

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
	private static final String CLASS_NAME = "jp.reflexworks.batch.WriteNamespacesEachServerBlogic";

	/** テスト名 */
	private static final String APP_NAME = "[WriteNamespacesEachServerApp]";

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(WriteNamespacesEachServerApp.class);

	/**
	 * main
	 * @param args 引数
	 *             [0]出力ディレクトリ(フルパス)
	 *             [1]抽出対象サービスステータス(複数指定の場合カンマ区切り)
	 *             [2]サーバタイプ (`mnf`,`entry`,`idx`,`ft`,`al`のいずれか)
	 */
	public static void main(String[] args) {
		try {
			// 引数チェック
			if (args == null || args.length < 3) {
				throw new IllegalArgumentException("引数を指定してください。[0]出力ディレクトリ(フルパス)、[1]抽出対象サービスステータス、[2]サーバタイプ");
			}
			String filepath = args[0];
			String statuses = args[1];
			String serverTypeStr = args[2];
			if (StringUtils.isBlank(filepath) || StringUtils.isBlank(statuses) || StringUtils.isBlank(serverTypeStr)) {
				throw new IllegalArgumentException("引数を指定してください。[0]出力ディレクトリ(フルパス)、[1]抽出対象サービスステータス、[2]サーバタイプ");
			}

			String[] blogicArgs = new String[]{PROPERTY_FILE_NAME, SYSTEM_SERVICE,
					CLASS_NAME, filepath, statuses, serverTypeStr};
			ReflexApplication<Boolean> reflexApp = new ReflexApplication<Boolean>();
			reflexApp.exec(blogicArgs);

		} catch (Throwable e) {
			logger.error(APP_NAME + " Error occured. " + e.getClass().getName(), e);
		}

		System.exit(0);
	}

}
