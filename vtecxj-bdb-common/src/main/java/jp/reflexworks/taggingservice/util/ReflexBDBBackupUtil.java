package jp.reflexworks.taggingservice.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

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

		String[] command = {cmdPath, namespace, storageUrl}; // 起動コマンドを指定する

		Runtime runtime = Runtime.getRuntime(); // ランタイムオブジェクトを取得する
		BufferedReader br = null;
		InputStream in = null;
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
				throw new IOException(errsb.toString());
			}

		} catch (InterruptedException e) {
			throw new IOException(e);
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					logger.warn("[backup] Error occured (close).", e);
				}
			}
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					logger.warn("[backup] Error occured (close).", e);
				}
			}
		}
	}

}
