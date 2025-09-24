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

		Runtime runtime = Runtime.getRuntime(); // ランタイムオブジェクトを取得する
		BufferedReader br = null;
		InputStream in = null;
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

			Process process = runtime.exec(command); // 指定したコマンドを実行する

			// リターンコード
			int returnCode = process.waitFor();
			if (isEnableAccesslog()) {
				StringBuilder logsbc = new StringBuilder();
				logsbc.append(logprefix);
				logsbc.append(" [Return code] ");
				logsbc.append(returnCode);
				logger.debug(logsbc.toString());
			}

			// 標準出力
			in = process.getInputStream();
			StringBuilder sb = new StringBuilder();
			br = new BufferedReader(new InputStreamReader(in));
			String line;
			while ((line = br.readLine()) != null) {
				sb.append(line + Constants.NEWLINE);
			}
			String str = sb.toString();
			if (isEnableAccesslog()) {
				logger.debug(logprefix + " [out] " + str);
			}

			br.close();
			in.close();

			// エラー出力
			in = process.getErrorStream();
			StringBuilder err = new StringBuilder();
			br = new BufferedReader(new InputStreamReader(in));
			while ((line = br.readLine()) != null) {
				err.append(line + Constants.NEWLINE);
			}
			String errStr = err.toString();
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

		} catch (Throwable e) {
			logger.warn("[initExecCommand] Error occurred.", e);
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					logger.warn("[initExecCommand] Error occurred (close).", e);
				}
			}
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					logger.warn("[initExecCommand] Error occurred (close).", e);
				}
			}
		}
	}
	
	/**
	 * 初期処理のアクセスログを出力するかどうかを取得.
	 * @return 初期処理のアクセスログを出力する場合true
	 */
	private static boolean isEnableAccesslog() {
		return TaggingEnvUtil.isEnableAccesslog() && logger.isDebugEnabled();
	}

}
