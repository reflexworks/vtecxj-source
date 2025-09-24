package jp.reflexworks.taggingservice.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletContext;
import jp.reflexworks.atom.entry.FeedBase;
import jp.sourceforge.reflex.IResourceMapper;
import jp.sourceforge.reflex.exception.XMLException;
import jp.sourceforge.reflex.util.ResourceUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * ファイル読み込み操作ユーティリティ.
 */
public class FileReaderUtil {
	
	public static final String WAR_DIR = "WEB-INF" + File.separator + 
			"classes" + File.separator;

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(FileReaderUtil.class);

	/**
	 * XMLファイルからFeedオブジェクトを取得
	 * @param mapper ResourceMapper
	 * @param filename ファイル名
	 * @param servletContext ServletContext
	 * @return Feed
	 */
	public static FeedBase getFeedFromFile(IResourceMapper mapper, String filename,
			ServletContext servletContext) 
	throws XMLException {
		FeedBase feed = null;
		BufferedReader reader = null;
		try {
			reader = getReader(filename, servletContext);
			if (reader != null) {
				Object tmp = mapper.fromXML(reader);
				if (tmp instanceof FeedBase) {
					feed = (FeedBase)tmp;
				}
			}
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (Exception e) {}	// Do nothing.
			}
		}
		return feed;
	}

	/**
	 * ファイルの内容を文字列で取得
	 * @param filename ファイル名
	 * @param servletContext ServletContext
	 * @return ファイルの内容文字列
	 */
	public static String getStringFromFile(String filename, 
			ServletContext servletContext) {
		BufferedReader reader = null;
		String retStr = null;
		try {
			reader = getReader(filename, servletContext);
			if (reader != null) {
				String line = null;
				boolean isFirst = true;
				StringBuilder buf = new StringBuilder();
				while ((line = reader.readLine()) != null) {
					if (isFirst) {
						isFirst = false;
					} else {
						buf.append(Constants.NEWLINE);
					}
					buf.append(line);
				}
				retStr = buf.toString();
			}
		} catch (Exception e) {
			// Do nothing.
			logger.warn(e.getClass().getSimpleName(), e);
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (Exception e) {}	// Do nothing.
			}
		}
		return retStr;
	}

	/**
	 * ファイルの内容を文字列の配列で取得
	 * @param filename ファイル名
	 * @param servletContext ServletContext
	 */
	public static String[] getArrayFromFile(String filename,
			ServletContext servletContext) {
		String text = getStringFromFile(filename, servletContext);
		return convertArray(text);
	}

	/**
	 * ファイルの内容を文字列のリストで取得
	 * @param filename ファイル名
	 * @param servletContext ServletContext
	 * @return ファイルの内容リスト
	 */
	public static List<String> getListFromFile(String filename,
			ServletContext servletContext) {
		String text = getStringFromFile(filename, servletContext);
		return convertList(text);
	}

	/**
	 * FileのReaderを取得
	 * @param fileName ファイル名
	 * @param servletContext ServletContext
	 * @return Reader
	 */
	private static BufferedReader getReader(String fileName, 
			ServletContext servletContext) {
		URL path = ResourceUtil.getResourceURL(fileName);
		File file = null;

		// test log
		if (logger.isDebugEnabled()) {
			logger.debug("[FileReaderUtil.getReader] fileName = " + fileName + 
					", path = " + path);
		}

		if (path != null) {
			try {
				file = new File(path.toURI());
			} catch (URISyntaxException e) {
				logger.warn(e.getClass().getSimpleName(), e);
			}
		} else {
			if (servletContext != null) {
				String tmpFileName = WAR_DIR + fileName;
				String realPath = servletContext.getRealPath(tmpFileName);
				if (logger.isDebugEnabled()) {
					logger.debug("[FileReaderUtil.getReader] realPath = " + realPath);
				}
				file = new File(realPath);
			}
		}
		
		if (file != null) {
			try {
				if (file.exists()) {
					return new BufferedReader(new InputStreamReader(
							new FileInputStream(file), Constants.ENCODING));
				}
			} catch (Exception e) {
				// Do nothing.
				logger.warn(e.getClass().getSimpleName(), e);
			}
		}
		return null;
	}

	/**
	 * テキストの改行ごとに文字列を区切り、配列にして返却します.
	 * @param text 改行のあるテキスト
	 * @return 改行ごとに区切った文字列の配列
	 */
	public static String[] convertArray(String text) {
		List<String> lines = convertList(text);
		if (lines != null) {
			return lines.toArray(new String[0]);
		}
		return null;
	}

	/**
	 * テキストの改行ごとに文字列を区切り、配列にして返却します.
	 * @param text 改行のあるテキスト
	 * @return 改行ごとに区切った文字列の配列
	 */
	public static List<String> convertList(String text) {
		if (StringUtils.isBlank(text)) {
			return null;
		}
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new StringReader(text));
			
			List<String> lines = new ArrayList<String>();
			String line = null;
			while ((line = reader.readLine()) != null) {
				if (!"".equals(StringUtils.trim(line))) {
					lines.add(line);
				}
			}
			if (lines.size() > 0) {
				return lines;
			}
		} catch (IOException e) {
			logger.warn(e.getClass().getSimpleName(), e);
		}
		return null;
	}

}
