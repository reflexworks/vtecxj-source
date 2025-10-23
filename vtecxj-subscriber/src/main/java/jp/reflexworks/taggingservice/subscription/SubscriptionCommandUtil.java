package jp.reflexworks.taggingservice.subscription;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * コマンド実行ユーティリティ
 */
public class SubscriptionCommandUtil {

	/** 設定 : Podの削除シェル コマンドの配置パス */
	private static final String PROP_CMD_PATH_PODDEL = "_cmd.path.poddel";
	/** 設定 : サービスアカウントJSONファイル名 */
	//private static final String PROP_KUBECTL_FILE_SECRET = "_kubectl.file.secret";
	/** 設定 : サービスアカウント名 */
	//private static final String PROP_KUBECTL_SERVICEACCOUNT = "_kubectl.serviceaccount";
	/** 設定 : プロジェクトID */
	//private static final String PROP_GCP_PROJECTID = "_gcp.projectid";

	/** Podの削除シェル コマンドの配置パス デフォルト値 */
	private static final String CMD_PATH_PODDEL_DEFAULT = "/var/vtecx/sh/kubectl_delete_pod.sh";

	/** ロガー. */
	private static final Logger logger = LoggerFactory.getLogger(SubscriptionCommandUtil.class);

	/**
	 * 再起動コマンド実行処理.
	 * Podの削除処理
	 * @param podName 削除対象Pod
	 */
	public static void restart(String podName)
	throws IOException {
		// まずはシェルの配置パスを取得
		String cmdPath = TaggingEnvUtil.getSystemProp(PROP_CMD_PATH_PODDEL,
				CMD_PATH_PODDEL_DEFAULT);
		// サービスアカウント
		//String kubectlServiceAccount = TaggingEnvUtil.getSystemProp(PROP_KUBECTL_SERVICEACCOUNT,
		//		null);
		//String kubectlJsonFilename = TaggingEnvUtil.getSystemProp(PROP_KUBECTL_FILE_SECRET,
		//		null);
		//String kubectlJsonPath = null;
		//if (!StringUtils.isBlank(kubectlServiceAccount) &&
		//		!StringUtils.isBlank(kubectlJsonFilename)) {
		//	kubectlJsonPath = FileUtil.getResourceFilename(kubectlJsonFilename);
		//}
		//String projectId = TaggingEnvUtil.getSystemProp(PROP_GCP_PROJECTID, null);

		// $1 : Pod名
		// $2 : サービスアカウント名
		// $3 : サービスアカウント秘密鍵 (フルパス)
		// $4 : プロジェクトID
		//String[] command = {cmdPath, podName,
		//		kubectlServiceAccount, kubectlJsonPath, projectId}; // 起動コマンドを指定する

		// $1 : Pod名
		String[] command = {cmdPath, podName}; // 起動コマンドを指定する

		Runtime runtime = Runtime.getRuntime(); // ランタイムオブジェクトを取得する
		BufferedReader br = null;
		InputStream in = null;
		try {
			// ログ用接頭辞
			String logprefix = null;
			if (logger.isInfoEnabled()) {
				StringBuilder prefixsb = new StringBuilder();
				prefixsb.append("[restart] command = ");
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
			if (logger.isInfoEnabled()) {
				StringBuilder logsbc = new StringBuilder();
				logsbc.append(logprefix);
				logsbc.append(" [Return code] ");
				logsbc.append(returnCode);
				logger.info(logsbc.toString());
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
			if (logger.isInfoEnabled()) {
				logger.info(logprefix + " [out] " + str);
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
				throw new IllegalStateException(errsb.toString());
			}

		} catch (InterruptedException e) {
			throw new IOException(e);
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					logger.warn("[restart] Error occured (close).", e);
				}
			}
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					logger.warn("[restart] Error occured (close).", e);
				}
			}
		}
	}

}
