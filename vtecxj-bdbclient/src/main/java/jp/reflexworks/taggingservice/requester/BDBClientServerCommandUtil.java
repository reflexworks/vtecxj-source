package jp.reflexworks.taggingservice.requester;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * コマンド実行ユーティリティ
 */
public class BDBClientServerCommandUtil {

	/** 標準出力のエコーと見出し行数 (先頭) */
	private static final int ECHO_AND_HEADER_START = 1;
	/** 標準出力のエコーと見出し行数 (末尾) */
	private static final int ECHO_AND_HEADER_END = 0;

	/** ロガー. */
	private static final Logger logger = LoggerFactory.getLogger(BDBClientServerCommandUtil.class);

	/**
	 * PodのIPアドレス取得コマンド実行処理.
	 * @param deploymentName デプロイメント名 (URLのホスト部分)
	 * @param requestInfo リクエスト情報
	 * @return デプロイメント名に対応したPodのIPアドレスリスト
	 */
	/*
	public static List<String> getPodIps(String deploymentName, RequestInfo requestInfo)
	throws IOException {
		long startTime = 0;
		if (BDBClientUtil.isEnableAccessLog()) {
			startTime = new Date().getTime();
		}
		// まずはシェルの配置パスを取得
		String cmdPath = TaggingEnvUtil.getSystemProp(BDBClientServerConst.PROP_CMD_PATH_GETPODIP,
				BDBClientServerConst.CMD_PATH_GETPODIP_DEFAULT);
		// サービスアカウント
		String kubectlServiceAccount = TaggingEnvUtil.getSystemProp(BDBClientServerConst.PROP_KUBECTL_SERVICEACCOUNT,
				null);
		String kubectlJsonFilename = TaggingEnvUtil.getSystemProp(BDBClientServerConst.PROP_KUBECTL_FILE_SECRET,
				null);
		String kubectlJsonPath = null;
		if (!StringUtils.isBlank(kubectlServiceAccount) &&
				!StringUtils.isBlank(kubectlJsonFilename)) {
			kubectlJsonPath = FileUtil.getResourceFilename(kubectlJsonFilename);
		}
		String projectId = TaggingEnvUtil.getSystemProp(BDBClientServerConst.PROP_GCP_PROJECTID, null);
		String outFilepath = TaggingEnvUtil.getSystemProp(BDBClientServerConst.PROP_GETPODIP_OUT_FILEPATH,
				BDBClientServerConst.GETPODIP_OUT_FILEPATH_DEFAULT);

		// $1 : 結果出力ファイルパス
		// $2 : サービスアカウント名
		// $3 : サービスアカウント秘密鍵 (フルパス)
		// $4 : プロジェクトID

		String[] command = {cmdPath, outFilepath,
				kubectlServiceAccount, kubectlJsonPath, projectId}; // 起動コマンドを指定する
		if (BDBClientUtil.isEnableAccessLog()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[getPodIps.accesslog] prepare");
			sb.append(LogUtil.getElapsedTimeLog(startTime));
			logger.debug(sb.toString());
		}

		if (BDBClientUtil.isEnableAccessLog()) {
			startTime = new Date().getTime();
		}
		Runtime runtime = Runtime.getRuntime(); // ランタイムオブジェクトを取得する
		if (BDBClientUtil.isEnableAccessLog()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[getPodIps.accesslog] Runtime.getRuntime");
			sb.append(LogUtil.getElapsedTimeLog(startTime));
			logger.debug(sb.toString());
		}

		BufferedReader br = null;
		InputStream in = null;
		List<String> podIps = new ArrayList<>();
		try {
			// ログ用接頭辞
			String logprefix = null;
			if (BDBClientUtil.isEnableAccessLog()) {
				StringBuilder prefixsb = new StringBuilder();
				prefixsb.append("[getPodIps] command = ");
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

			if (BDBClientUtil.isEnableAccessLog()) {
				startTime = new Date().getTime();
			}
			Process process = runtime.exec(command); // 指定したコマンドを実行する
			if (BDBClientUtil.isEnableAccessLog()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[getPodIps.accesslog] runtime.exec");
				sb.append(LogUtil.getElapsedTimeLog(startTime));
				logger.debug(sb.toString());
			}

			// リターンコード
			if (BDBClientUtil.isEnableAccessLog()) {
				startTime = new Date().getTime();
			}
			int returnCode = process.waitFor();
			if (BDBClientUtil.isEnableAccessLog()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[getPodIps.accesslog] process.waitFor");
				sb.append(LogUtil.getElapsedTimeLog(startTime));
				logger.debug(sb.toString());
			}
			if (BDBClientUtil.isEnableAccessLog()) {
				StringBuilder logsbc = new StringBuilder();
				logsbc.append(logprefix);
				logsbc.append(" [Return code] ");
				logsbc.append(returnCode);
				logger.debug(logsbc.toString());
			}

			// 標準出力
			if (BDBClientUtil.isEnableAccessLog()) {
				startTime = new Date().getTime();
			}
			in = process.getInputStream();
			br = new BufferedReader(new InputStreamReader(in));
			String line;
			List<String> lineList = new ArrayList<>();
			StringBuilder logsb = new StringBuilder();	// ログ用
			boolean isFirst = true;
			while ((line = br.readLine()) != null) {
				if (isFirst) {
					isFirst = false;
					//continue;
				} else {
					logsb.append(Constants.NEWLINE);
				}
				logsb.append(line);
				lineList.add(line);
			}

			br.close();
			in.close();
			if (BDBClientUtil.isEnableAccessLog()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[getPodIps.accesslog] get stdout");
				sb.append(LogUtil.getElapsedTimeLog(startTime));
				logger.debug(sb.toString());
			}
			if (BDBClientUtil.isEnableAccessLog()) {
				StringBuilder logsbo = new StringBuilder();
				logsbo.append(logprefix);
				logsbo.append(" [Out] ");
				logsbo.append(logsb);
				logger.debug(logsbo.toString());
			}

			// Podリスト等を取得
			int cnt = lineList.size();
			int endLogIdx = cnt - ECHO_AND_HEADER_END - 1;
			int i = 0;
			for (String tmpline : lineList) {
				if (i < ECHO_AND_HEADER_START || i > endLogIdx) {
					// 見出し・ログ行
					if (BDBClientUtil.isEnableAccessLog()) {
						StringBuilder sb = new StringBuilder();
						sb.append(LogUtil.getRequestInfoStr(requestInfo));
						sb.append("[getPodIps.accesslog] line: ");
						sb.append(tmpline);
						logger.debug(sb.toString());
					}
				} else {
					// PodのIPアドレスを取得
					String[] lineParts = tmpline.split(" +");
					if (lineParts.length < 2) {
						StringBuilder logsbc = new StringBuilder();
						logsbc.append(logprefix);
						logsbc.append("Invalid line: ");
						logsbc.append(tmpline);
						logger.warn(logsbc.toString());
						continue;
					}
					String podName = lineParts[0];
					String podIp = lineParts[1];
					if (podName.startsWith(deploymentName)) {
						podIps.add(podIp);
					}
				}
				i++;
			}

			// エラー出力
			if (BDBClientUtil.isEnableAccessLog()) {
				startTime = new Date().getTime();
			}
			in = process.getErrorStream();
			StringBuilder err = new StringBuilder();
			br = new BufferedReader(new InputStreamReader(in));
			while ((line = br.readLine()) != null) {
				err.append(line + Constants.NEWLINE);
			}
			String errStr = err.toString();
			if (!StringUtils.isBlank(errStr)) {
				if (BDBClientUtil.isEnableAccessLog()) {
					// エラー出力
					// 以下のメッセージがエラー出力されているが問題なし。
					// 「Activated service account credentials for: [サービスアカウント名]」
					StringBuilder logsbe = new StringBuilder();
					logsbe.append(logprefix);
					logsbe.append(" [Error out] ");
					logsbe.append(errStr);
					logger.debug(logsbe.toString());
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
			if (BDBClientUtil.isEnableAccessLog()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[getPodIps.accesslog] get stderr");
				sb.append(LogUtil.getElapsedTimeLog(startTime));
				logger.debug(sb.toString());
			}

			return podIps;

		} catch (InterruptedException e) {
			throw new IOException(e);
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					logger.warn("[getPodIps] Error occured (close).", e);
				}
			}
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					logger.warn("[getPodIps] Error occured (close).", e);
				}
			}
		}
	}
	*/

}
