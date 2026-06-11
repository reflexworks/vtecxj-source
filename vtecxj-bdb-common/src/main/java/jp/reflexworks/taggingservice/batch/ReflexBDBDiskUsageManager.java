package jp.reflexworks.taggingservice.batch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.BDBEnvConst;
import jp.reflexworks.taggingservice.env.ReflexEnvUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * ディスク使用量のチェッククラス.
 */
public class ReflexBDBDiskUsageManager {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * ディスク使用率(%)を取得.
	 * dfを実行するシェルを実行する。
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return ディスク使用率(%)
	 */
	public String getDiskUsage(RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException {
		String bdbHome = ReflexEnvUtil.getSystemProp(BDBEnvConst.BDB_DIR, BDBEnvConst.BDB_DIR_DEFAULT);
		String cmdFilepath = ReflexEnvUtil.getSystemProp(BDBEnvConst.CMD_PATH_DISKUSAGE, 
				BDBEnvConst.CMD_PATH_DISKUSAGE_DEFAULT);
		
		// df -m |grep {grepDir}

		// コマンドインジェクション防止: bdbHome はパスとして有効な文字のみ許可
		if (bdbHome == null || !bdbHome.matches("[a-zA-Z0-9_\\-\\./]+")) {
			throw new IllegalArgumentException("Invalid bdbHome: " + bdbHome);
		}
		//String[] command = {"df", "-m", "|grep", "'" + grepDir + "'"}; // 起動コマンドを指定する
		String[] command = {cmdFilepath, bdbHome}; // 起動コマンドを指定する

		try {
			// ログ用接頭辞
			String logprefix = null;
			if (logger.isDebugEnabled()) {
				StringBuilder prefixsb = new StringBuilder();
				prefixsb.append("[getDiskUsage] command = ");
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
			if (logger.isTraceEnabled()) {
				StringBuilder logsbc = new StringBuilder();
				logsbc.append(logprefix);
				logsbc.append(" [Return code] ");
				logsbc.append(returnCode);
				logger.debug(logsbc.toString());
			}

			// 標準出力
			String str = StringUtils.trim(stdout.getResult());
			if (logger.isDebugEnabled()) {
				logger.debug(logprefix + " [out] " + str);
			}

			// エラー出力
			String errStr = stderr.getResult();
			if (!StringUtils.isBlank(errStr)) {
				if (logger.isDebugEnabled()) {
					// エラー出力
					// 以下のメッセージがエラー出力されているが問題なし。
					// 「Activated service account credentials for: [サービスアカウント名]」
					StringBuilder logsb = new StringBuilder();
					logsb.append(logprefix);
					logsb.append(" [Error out] ");
					logsb.append(errStr);
					logger.debug(logsb.toString());
				}
			}

			// リターンコードが1でなければエラー
			if (returnCode > 1) {
				StringBuilder errsb = new StringBuilder();
				errsb.append("ReturnCode=");
				errsb.append(returnCode);
				errsb.append(" ");
				errsb.append(errStr);
				throw new IllegalStateException(errsb.toString());
			}
			
			// 文字列から"n%"を抽出する。
			String[] parts = str.split(" +");
			for (int i = parts.length - 1; i >= 0; i--) {
				String part = parts[i];
				if (logger.isTraceEnabled()) {
					StringBuilder psb = new StringBuilder();
					psb.append(logprefix);
					psb.append(" part[");
					psb.append(i);
					psb.append("] = ");
					psb.append(part);
					logger.debug(psb.toString());
				}
				if (part.endsWith("%")) {
					return part.substring(0, part.length() - 1);
				}
			}
			return null;

		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException(e);
		}
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
