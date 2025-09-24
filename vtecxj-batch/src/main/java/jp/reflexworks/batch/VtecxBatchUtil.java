package jp.reflexworks.batch;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.servlet.util.UrlUtil;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.bdbclient.BDBResponseInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.Constants;
import jp.sourceforge.reflex.util.FileUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * vte.cxバッチ処理 ユーティリティ
 */
public class VtecxBatchUtil {

	/** プロパティファイル名 */
	private static final String PROPERTY_FILE_NAME = VtecxBatchConst.PROPERTY_FILE_NAME;

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(VtecxBatchUtil.class);

	/**
	 * プロパティオブジェクトを取得
	 * @return プロパティオブジェクト
	 */
	public static Properties getProperties()
	throws IOException {
		String propertyPath = getPropertyPath();
		if (propertyPath != null && propertyPath.length() > 0) {
			InputStream in = FileUtil.getInputStreamFromFile(propertyPath);
			if (in != null) {
				Properties props = new Properties();
				props.load(in);
				return props;
			} else {
				throw new IllegalStateException("The property file stream is null. " + PROPERTY_FILE_NAME);
			}
		} else {
			throw new IllegalStateException("The property file does not exist. " + PROPERTY_FILE_NAME);
		}
	}

	/**
	 * プロパティファイルの絶対パスを返却
	 * @return プロパティファイルの絶対パス
	 */
	private static String getPropertyPath() throws FileNotFoundException {
		return FileUtil.getResourceFilename(PROPERTY_FILE_NAME);
	}

	/**
	 * 秘密鍵JSONを取得
	 * @param secretFilename 秘密鍵JSONファイル名
	 * @return 秘密鍵JSON
	 */
	public static byte[] getSecret(String secretFilename)
	throws IOException {
		String jsonPath = FileUtil.getResourceFilename(secretFilename);
		if (!StringUtils.isBlank(jsonPath)) {
			byte[] secret = getSecretFileData(jsonPath);
			if (secret == null || secret.length == 0) {
				throw new IllegalStateException("[getSecret] secret file is empty. " + secretFilename);
			}
			return secret;
		} else {
			throw new IllegalStateException("[getSecret] secret file is not found. " + secretFilename);
		}
	}

	/**
	 * ファイルから秘密鍵データを取得.
	 * @return 秘密鍵データ
	 */
	private static byte[] getSecretFileData(String filePath) {
		byte[] data = null;
		InputStream isJson = null;
		try {
			isJson = FileUtil.getInputStreamFromFile(filePath);
			ByteArrayOutputStream bout = new ByteArrayOutputStream();
			byte[] buffer = new byte[BatchBDBConst.BUFFER_SIZE];
			int len = 0;
			while ((len = isJson.read(buffer)) > 0) {
				bout.write(buffer, 0, len);
			}
			data = bout.toByteArray();

		} catch (IOException e) {
			logger.warn("[getSecretFileData] IO error.", e);
		} finally {
			if (isJson != null) {
				try {
					isJson.close();
				} catch (IOException e) {
					logger.warn("[getSecretFileData] close error.", e);
				}
			}
		}
		return data;
	}

	/**
	 * ファイルをList形式で取得.
	 * @param filename ファイル名(フルパス)
	 * @return List
	 */
	public static List<String> getListFromFile(String filename)
	throws IOException {
		File file = new File(filename);
		if (!file.exists() || !file.isFile()) {
			throw new IllegalStateException("The file does not exist. " + filename);
		}
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(
					new FileInputStream(file), Constants.ENCODING));
			List<String> list = new ArrayList<String>();
			String line = null;
			while ((line = reader.readLine()) != null) {
				list.add(line);
			}
			if (list.isEmpty()) {
				throw new IllegalStateException("The file is empty.");
			}
			return list;

		} finally {
			if (reader != null) {
				reader.close();
			}
		}
	}

	/**
	 * Key-Value形式のファイルを読み、Mapにして返却.
	 * @param filename ファイル名(フルパス)
	 * @param delimiter KeyとValueの区切り文字
	 * @return Map (並行処理に対応し、ConcurrentHashMap)
	 */
	public static Map<String, String> getKeyValueList(String filename, String delimiter)
	throws IOException {
		File file = new File(filename);
		return getKeyValueList(file, delimiter);
	}

	/**
	 * Key-Value形式のファイルを読み、Mapにして返却.
	 * @param file ファイル
	 * @param delimiter KeyとValueの区切り文字
	 * @return Map (並行処理に対応し、ConcurrentHashMap)
	 */
	public static Map<String, String> getKeyValueList(File file, String delimiter)
	throws IOException {
		String filePath = file.getPath();
		if (!file.exists() || !file.isFile()) {
			throw new IllegalStateException("The file does not exist. file=" + filePath);
		}
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(
					new FileInputStream(file), Constants.ENCODING));
			Map<String, String> map = new ConcurrentHashMap<String, String>();
			String line = null;
			while ((line = reader.readLine()) != null) {
				int idx = line.indexOf(delimiter);
				if (idx >= 1 && line.length() >= idx + 1) {
					String key = line.substring(0, idx);
					String val = line.substring(idx + 1);
					map.put(key, val);
				} else {
					StringBuilder sb = new StringBuilder();
					sb.append("The format is invalid. ");
					sb.append(line);
					sb.append(" file=");
					sb.append(filePath);
					logger.warn(sb.toString());
				}
			}
			if (map.isEmpty()) {
				throw new IllegalStateException("The key-value data does not exist. file=" + filePath);
			}
			return map;

		} finally {
			if (reader != null) {
				reader.close();
			}
		}
	}

	/**
	 * カーソルを追加
	 * @param uri URI
	 * @param cursorStr カーソル文字列
	 * @return カーソルを追加したURI
	 */
	public static String addCursorStr(String uri, String cursorStr) {
		if (!StringUtils.isBlank(cursorStr)) {
			return UrlUtil.addParam(uri, RequestParam.PARAM_NEXT, cursorStr);
		} else {
			return uri;
		}
	}

	/**
	 * BDBサーバごとの処理
	 * @param systemContext SystemContext
	 * @param method メソッド
	 * @param urlStr BDB URL
	 * @param namespace 名前空間
	 * @return Futute
	 */
	public static Future<BDBResponseInfo<FeedBase>> request(SystemContext systemContext, String method,
			String urlStr, String namespace)
	throws IOException, TaggingException {
		StringBuilder sb = new StringBuilder();
		sb.append("[request] ");
		sb.append(method);
		sb.append(" ");
		sb.append(urlStr);
		sb.append(" namespace=");
		sb.append(namespace);
		logger.info(sb.toString());

		VtecxBatchRequestCallable callable = new VtecxBatchRequestCallable(urlStr, method, namespace);
		return callable.addTask(systemContext.getAuth(), systemContext.getRequestInfo(),
				systemContext.getConnectionInfo());
	}

}
