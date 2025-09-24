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

		//String[] command = {"df", "-m", "|grep", "'" + grepDir + "'"}; // 起動コマンドを指定する
		String[] command = {cmdFilepath, bdbHome}; // 起動コマンドを指定する

		Runtime runtime = Runtime.getRuntime(); // ランタイムオブジェクトを取得する
		BufferedReader br = null;
		InputStream in = null;
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

			Process process = runtime.exec(command); // 指定したコマンドを実行する

			// リターンコード
			int returnCode = process.waitFor();
			if (logger.isTraceEnabled()) {
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
			String str = StringUtils.trim(sb.toString());
			if (logger.isDebugEnabled()) {
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
			throw new IOException(e);
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					logger.warn("[getDiskUsage] Error occured (close).", e);
				}
			}
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					logger.warn("[getDiskUsage] Error occured (close).", e);
				}
			}
		}
	}

}
