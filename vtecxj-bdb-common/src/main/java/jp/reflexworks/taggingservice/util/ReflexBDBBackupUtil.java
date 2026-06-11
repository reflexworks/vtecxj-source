package jp.reflexworks.taggingservice.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.BDBEnvConst;
import jp.reflexworks.taggingservice.env.BDBEnvManager;
import jp.reflexworks.taggingservice.env.BDBEnvUtil;
import jp.reflexworks.taggingservice.env.ReflexEnvUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * データ移行時のバックアップ処理.
 */
public class ReflexBDBBackupUtil {

	/** Storage URLに使用できる文字パターン */
	public static final String PATTERN_STR_STORAGE_URL = "(gs://|https://storage\\.googleapis\\.com/)[a-zA-Z0-9_\\-\\./%]+";
	/** Storage URLに使用できる文字パターンオブジェクト */
	public static final Pattern PATTERN_STORAGE_URL = Pattern.compile(PATTERN_STR_STORAGE_URL);

	/** ロガー. */
	private static final Logger logger = LoggerFactory.getLogger(ReflexBDBBackupUtil.class);

	/**
	 * バックアップ処理.
	 * シェルを実行する。
	 * @param namespace 名前空間
	 * @param storageUrl Cloud Storage格納先URL
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 */
	public static void backup(String namespace, String storageUrl,
			String serviceName, RequestInfo requestInfo)
	throws IOException {
		// BDBデータが存在するかどうかチェック
		// (割り当てられていてもまだデータが存在しない場合がある)
		// BDBログファイルディレクトリ : {_bdb.dir}/{stage}/{namespace}
		String bdbNamespaceDir = BDBEnvUtil.getBDBDirByNamespace(namespace);
		File bdbNamespaceFile = new File(bdbNamespaceDir);
		if (!bdbNamespaceFile.exists()) {
			if (logger.isInfoEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append("[backup] bdb dir does not exist. ");
				sb.append(bdbNamespaceDir);
				logger.info(sb.toString());
			}
			return;
		}

		// BDBクリーン処理
		BDBEnvManager envManager = new BDBEnvManager();
		envManager.clean(namespace);

		// BDBバックアップ処理
		// まずはシェルの配置パスを取得
		String cmdPath = ReflexEnvUtil.getSystemProp(BDBEnvConst.CMD_PATH_BACKUP,
				BDBEnvConst.CMD_PATH_BACKUP_DEFAULT);

		// $1 : 名前空間
		// $2 : Cloud Storage URL

		// コマンドインジェクション防止: namespace は英数字・ハイフン・アンダースコア・ドットのみ許可
		if (namespace == null || !ServiceCommonUtil.matchServiceNamePattern(namespace)) {
			throw new IllegalArgumentException("Invalid namespace: " + namespace);
		}
		// storageUrl は gs:// または https://storage.googleapis.com/ のみ許可
		if (!matchStorageUrl(storageUrl)) {
			throw new IllegalArgumentException("Invalid storageUrl: " + storageUrl);
		}
		String[] command = {cmdPath, namespace, storageUrl}; // 起動コマンドを指定する

		try {
			// ログ用接頭辞
			String logprefix = null;
			if (logger.isInfoEnabled()) {
				StringBuilder prefixsb = new StringBuilder();
				prefixsb.append("[backup] command = ");
				boolean isFirst = true;
				for (String part : command) {
					if (isFirst) {
						isFirst = false;
					} else {
						prefixsb.append(" ");
					}
					prefixsb.append(part);
				}
				logprefix = prefixsb.toString();
			}

			Process process = new ProcessBuilder(command).start(); // 指定したコマンドを実行する
			StreamReader stdout = new StreamReader(process.getInputStream());
			StreamReader stderr = new StreamReader(process.getErrorStream());
			stdout.start();
			stderr.start();

			// リターンコード
			int returnCode = process.waitFor();
			stdout.join();
			stderr.join();
			if (logger.isInfoEnabled()) {
				StringBuilder logsbc = new StringBuilder();
				logsbc.append(logprefix);
				logsbc.append(" [Return code] ");
				logsbc.append(returnCode);
				logger.info(logsbc.toString());
			}

			// 標準出力
			String str = stdout.getResult();
			if (logger.isInfoEnabled()) {
				logger.info(logprefix + " [out] " + str);
			}

			// エラー出力
			String errStr = stderr.getResult();
			if (!StringUtils.isBlank(errStr)) {
				if (logger.isInfoEnabled()) {
					// エラー出力
					// 以下のメッセージがエラー出力されているが問題なし。
					// 「Activated service account credentials for: [サービスアカウント名]」
					StringBuilder logsb = new StringBuilder();
					logsb.append(logprefix);
					logsb.append(" [Error out] ");
					logsb.append(errStr);
					logger.info(logsb.toString());
				}
			}

			// リターンコードが0でなければエラー
			if (returnCode != 0) {
				StringBuilder errsb = new StringBuilder();
				errsb.append("ReturnCode=");
				errsb.append(returnCode);
				errsb.append(" ");
				errsb.append(errStr);
				throw new IOException(errsb.toString());
			}

		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException(e);
		}
	}

	/**
	 * Storage URL 入力値チエック
	 * @param storageUrl Storage URL
	 * @return Storage URL 入力値が正しければtrue
	 */
	private static boolean matchStorageUrl(String storageUrl) {
		// storageUrl は gs:// または https://storage.googleapis.com/ のみ許可
		if (storageUrl == null) {
			return false;
		}
		Matcher matcher = PATTERN_STORAGE_URL.matcher(storageUrl);
		return matcher.matches();
	}

	/**
	 * プロセス出力を読み取るスレッド.
	 */
	private static class StreamReader extends Thread {

		/** 入力ストリーム. */
		private final InputStream in;
		/** 読み取り結果. */
		private final StringBuilder result = new StringBuilder();
		/** 読み取り例外. */
		private IOException exception;

		/**
		 * コンストラクタ.
		 * @param in 入力ストリーム
		 */
		StreamReader(InputStream in) {
			this.in = in;
		}

		@Override
		public void run() {
			try (BufferedReader br = new BufferedReader(new InputStreamReader(in, Constants.ENCODING))) {
				String line;
				while ((line = br.readLine()) != null) {
					result.append(line);
					result.append(Constants.NEWLINE);
				}
			} catch (IOException e) {
				exception = e;
			}
		}

		/**
		 * 読み取り結果を取得.
		 * @return 読み取り結果
		 */
		String getResult() throws IOException {
			if (exception != null) {
				throw exception;
			}
			return result.toString();
		}
	}

}
