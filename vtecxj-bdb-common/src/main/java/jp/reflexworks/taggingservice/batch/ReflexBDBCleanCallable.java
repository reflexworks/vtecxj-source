package jp.reflexworks.taggingservice.batch;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.bdb.BDBEnv;
import jp.reflexworks.taggingservice.bdb.BDBUtil;
import jp.reflexworks.taggingservice.env.BDBEnvManager;
import jp.reflexworks.taggingservice.env.BDBEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.taskqueue.ReflexBDBCallable;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * BDBクリーン非同期処理.
 * まずは指定のない名前空間削除処理を行う。(※要注意処理)
 * 次にBDBクリーン処理を行う。
 */
public class ReflexBDBCleanCallable extends ReflexBDBCallable<Boolean> {

	/** 名前空間一覧の区切り文字 */
	public static final String DELIMITER_NAMESPACES = ",";

	/** テーブル名リスト */
	private List<String> dbNames;
	/** サービス名 */
	private String serviceName;
	/** 名前空間(有効な名前空間をカンマ区切り) */
	private String namespaces;
	/** リクエスト情報 */
	private RequestInfo requestInfo;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param dbNames テーブル名リスト
	 * @param serviceName サービス名
	 * @param namespaces 有効な名前空間(カンマ区切り)
	 * @param requestInfo リクエスト情報
	 */
	public ReflexBDBCleanCallable(List<String> dbNames, String serviceName,
			String namespaces, RequestInfo requestInfo) {
		this.dbNames = dbNames;
		this.serviceName = serviceName;
		this.namespaces = namespaces;
		this.requestInfo = requestInfo;
	}

	/**
	 * BDBクリーン 非同期処理.
	 * まずは指定のない名前空間削除処理を行う。(※要注意処理)
	 * 次にBDBクリーン処理を行う。
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		if (logger.isTraceEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
					"[cleanBDB call] start.");
		}

		// カンマ区切りの名前空間を一覧に変換
		Set<String> namespaceSet = getNamespaceSet();

		// 名前空間削除
		boolean ret = deleteNs(namespaceSet);

		// BDBクリーナー
		cleanBDB();

		if (logger.isTraceEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
					"[cleanBDB call] end.");
		}

		return ret;
	}

	/**
	 * クリーン
	 */
	private void cleanBDB() {
		BDBEnvManager envManager = new BDBEnvManager();
		// すべてのオープン中BDB環境をクリーンする。
		envManager.clean();
	}

	/**
	 * 名前空間削除.
	 * 注) 指定のない名前空間は全て削除される。
	 */
	private boolean deleteNs(Set<String> namespaceSet)
	throws IOException, TaggingException {
		if (logger.isDebugEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append("[deleteNs] valid namespaces=");
			if (namespaceSet == null) {
				sb.append("null");
			} else {
				boolean isFirst = true;
				for (String namespace : namespaceSet) {
					if (isFirst) {
						isFirst = false;
					} else {
						sb.append(",");
					}
					sb.append(namespace);
				}
			}
			logger.debug(sb.toString());
		}

		// BDBホームディレクトリから配下のディレクトリ一覧を取得する。(ファイルシステム)
		String bdbHomeDir = BDBEnvUtil.getBDBHomeDir();
		if (logger.isTraceEnabled()) {
			logger.debug("[deleteNs] bdbHomeDir=" + bdbHomeDir);
		}
		if (StringUtils.isBlank(bdbHomeDir)) {
			logger.warn("[deleteNs] The BDB home directory is not specified.");
			return false;
		}
		File bdbHome = new File(bdbHomeDir);
		if (!bdbHome.exists()) {
			logger.warn("[deleteNs] The BDB home directory is missing. " + bdbHomeDir);
			return false;
		}
		File[] nsDirs = bdbHome.listFiles();
		if (nsDirs == null || nsDirs.length == 0) {
			logger.warn("[deleteNs] The BDB namespace directory is missing. " + bdbHomeDir);
			return false;
		}

		for (File nsDir : nsDirs) {
			if (nsDir.isFile()) {
				continue;	// ファイルは無視する
			}
			if (ignoreNs(nsDir)) {
				continue;	// 削除対象外ディレクトリ
			}
			String nsDirStr = nsDir.getName();
			if (!namespaceSet.contains(nsDirStr)) {
				if (logger.isDebugEnabled()) {
					logger.debug("[deleteNs] nsDirStr=" + nsDirStr);
				}
				deleteNsProc(nsDirStr);
			}
		}
		return false;
	}

	/**
	 * 名前空間削除.
	 * @param namespace 削除する名前空間
	 */
	private boolean deleteNsProc(String namespace)
	throws IOException, TaggingException {
		logger.info(LogUtil.getRequestInfoStr(requestInfo) +
				"[deleteNs] delete start. namespace=" + namespace);
		// BDB環境をクローズ
		BDBEnv bdbEnv = BDBUtil.getBDBEnvByNamespace(dbNames, namespace, false);
		String bdbDir = null;
		if (bdbEnv != null) {
			bdbDir = bdbEnv.getBdbDir();
			//bdbEnv.close();
			BDBEnvManager bdbEnvManager = new BDBEnvManager();
			bdbEnvManager.closeBDBEnv(namespace);
		} else {
			bdbDir = BDBEnvUtil.getBDBDirByNamespace(namespace);
		}
		// BDBディレクトリ削除
		File dir = new File(bdbDir);
		boolean ret = false;
		if (dir.exists()) {
			ret = deleteFile(dir);
			logger.info(LogUtil.getRequestInfoStr(requestInfo) +
					"[deleteNs] delete=" + ret + ". namespace=" + namespace);
		} else {
			logger.info(LogUtil.getRequestInfoStr(requestInfo) +
					"[deleteNs] BDB does not exist. namespace=" + namespace);
		}
		return ret;
	}

	/**
	 * ファイルの削除.
	 * ファイルがディレクトリの場合、配下のファイルを削除する。
	 * @param file ファイル
	 */
	private boolean deleteFile(File file) {
		if (file.isDirectory()) {
			// ファイルがディレクトリの場合、配下のファイルを削除する。
			File[] chFiles = file.listFiles();
			if (chFiles != null) {
				for (File chFile : chFiles) {
					deleteFile(chFile);
				}
			}
		}
		boolean ret = file.delete();
		if (!ret) {
			logger.warn("[deleteFile] The file can't delete. " + file.getName());
		}
		return ret;
	}

	/**
	 * カンマ区切りの名前空間一覧をSetにして取得.
	 * @return 名前空間Set
	 */
	private Set<String> getNamespaceSet() {
		return getSplitSet(namespaces);
	}

	/**
	 * カンマ区切りの名前空間一覧をSetにして取得.
	 * @return 名前空間Set
	 */
	private Set<String> getSplitSet(String str) {
		if (StringUtils.isBlank(str)) {
			return null;
		}
		Set<String> set = new HashSet<String>();
		if (str != null) {
			String[] strParts = str.split(DELIMITER_NAMESPACES);
			for (String strPart : strParts) {
				set.add(strPart);
			}
		}
		return set;
	}

	/**
	 * BDBホーム配下のディレクトリのうち、名前空間でないとみなすかどうか.
	 * @param dir ディレクトリ
	 * @return 名前空間ディレクトリでない場合true
	 */
	private boolean ignoreNs(File dir) {
		String dirName = dir.getName();
		if ("lost+found".equals(dirName)) {	// マウント時に自動作成されるディレクトリ
			return true;
		}
		return false;
	}

}
