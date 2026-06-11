package jp.reflexworks.taggingservice.env;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.util.Constants;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * コマンド実行ユーティリティ
 */
public class TaggingEnvCommandUtil {

	/** ロガー. */
	private static final Logger logger = LoggerFactory.getLogger(TaggingEnvCommandUtil.class);

	/**
	 * 初期コマンド実行処理.
	 * @param cmdPath コマンドパス
	 */
	public static void initExecCommand(String cmdPath) {
		if (StringUtils.isBlank(cmdPath)) {
			return;
		}

		String[] command = {cmdPath}; // 起動コマンドを指定する

		try {
			// ログ用接頭辞
			String logprefix = null;
			if (isEnableAccesslog()) {
				StringBuilder prefixsb = new StringBuilder();
				prefixsb.append("[initExecCommand] command = ");
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
			if (isEnableAccesslog()) {
				StringBuilder logsbc = new StringBuilder();
				logsbc.append(logprefix);
				logsbc.append(" [Return code] ");
				logsbc.append(returnCode);
				logger.debug(logsbc.toString());
			}

			// 標準出力
			String str = stdout.getResult();
			if (isEnableAccesslog()) {
				logger.debug(logprefix + " [out] " + str);
			}

			// エラー出力
			String errStr = stderr.getResult();
			if (!StringUtils.isBlank(errStr)) {
				if (isEnableAccesslog()) {
					// エラー出力
					// 以下のメッセージがエラー出力されているが問題なし。
					// 「Activated service account credentials for: [サービスアカウント名]」
					StringBuilder logsb = new StringBuilder();
					logsb.append(logprefix);
					logsb.append(" [err out] ");
					logsb.append(errStr);
					logger.debug(logsb.toString());
				}
			}

			// リターンコードが0でなければエラー
			if (returnCode != 0) {
				StringBuilder errsb = new StringBuilder();
				errsb.append("ReturnCode=");
				errsb.append(returnCode);
				errsb.append(" ");
				errsb.append(errStr);
				throw new IllegalStateException(errsb.toString());
			}

		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			logger.warn("[initExecCommand] Error occurred.", e);
		} catch (Throwable e) {
			logger.warn("[initExecCommand] Error occurred.", e);
		}
	}
	
	/**
	 * 初期処理のアクセスログを出力するかどうかを取得.
	 * @return 初期処理のアクセスログを出力する場合true
	 */
	private static boolean isEnableAccesslog() {
		return TaggingEnvUtil.isEnableAccesslog() && logger.isDebugEnabled();
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
