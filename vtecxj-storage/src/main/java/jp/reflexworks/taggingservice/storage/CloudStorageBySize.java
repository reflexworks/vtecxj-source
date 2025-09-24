package jp.reflexworks.taggingservice.storage;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import javax.imageio.ImageIO;

import org.imgscalr.Scalr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * 画像のサイズ指定アップロード処理.
 */
public class CloudStorageBySize {
	
	/** サイズ別コンテンツ登録のサイズ指定 文字列長 */
	private static int CONTENT_BYSIZE_PREFIX_LEN = CloudStorageSettingConst.CONTENT_BYSIZE_PREFIX.length();

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());
	
	/** サイズ指定登録処理の対象拡張子 */
	enum ExtensionBySize { 
		jpg(".jpg"), 
		png(".png"), 
		gif(".gif"), 
		jpeg(".jpeg"); 

		/** 拡張子文字列 */
		private final String text;
		
		/** 
		 * コンストラクタ
		 * @param text 拡張子
		 */
		ExtensionBySize(final String text) {
			this.text = text;
		}
		
		/**
		 * 文字列取得
		 * @return 拡張子
		 */
		public String getString() {
			return this.text;
		}

		/**
		 * 指定文字列が本enumの拡張子に含まれるかどうか
		 * @param str 文字列
		 * @return 指定文字列が本enumの拡張子に含まれる場合true
		 */
		public static boolean isMember(String str) {
			for (ExtensionBySize extension : ExtensionBySize.values()) {
				if (extension.getString().equals(str)) {
					return true;
				}
			}
			return false;
		}
	}

	/**
	 * コンテンツのサイズ指定アップロード.
	 * @param uri URI
	 * @param data アップロードデータ
	 * @param headers アップロードコンテンツのヘッダ情報
	 * @param cloudStorageManager CloudStorageManager
	 * @param systemContext データアクセスコンテキスト
	 */
	void uploadBySize(String uri, byte[] data, Map<String, String> headers,
			CloudStorageManager cloudStorageManager, SystemContext systemContext)
	throws IOException, TaggingException {
		String serviceName = systemContext.getServiceName();
		RequestInfo requestInfo = systemContext.getRequestInfo();
		if (isEnableAccessLog()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
					"[uploadBySize] start. uri = " + uri);
		}
		
		// URIを拡張子前と拡張子に分割
		ImageUri imageUri = new ImageUri(uri);
		
		Map<String, String> props = TaggingEnvUtil.getPropMap(serviceName, 
				CloudStorageSettingConst.CONTENT_BYSIZE_PREFIX);
		if (props == null || props.isEmpty()) {
			if (logger.isDebugEnabled()) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
						"[uploadBySize] property setting is not exist.");
			}
			return;
		}
		
		// _content.bysize.{ファイル名末尾につける文字列(s, m, lなど)}={ピクセル}
		for (Map.Entry<String, String> mapEntry : props.entrySet()) {
			ImageSize imageSize = new ImageSize(mapEntry.getKey(), mapEntry.getValue());
			// ファイル名編集
			String uriBySize = getUriBySize(imageUri, imageSize.name);
			// リサイズ
			byte[] dataBySize = resize(data, imageSize.pixel, imageUri.extension);
			// アップロード
			cloudStorageManager.uploadProc(uriBySize, dataBySize, headers, systemContext);
		}

		if (isEnableAccessLog()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
					"[uploadBySize] end. uri = " + uri);
		}
	}
	
	/**
	 * 拡張子チェック.
	 *  ・jpg、png、gifのいずれかのみOK
	 * @param uri URI
	 */
	void checkExtension(String uri) {
		String extension = getExtension(uri);
		if (!ExtensionBySize.isMember(extension)) {
			throw new IllegalParameterException("Not an image file extension.");
		}
	}
	
	/**
	 * 拡張子を取得.
	 * @param uri URI
	 * @return 拡張子(ドット(.)付き)
	 */
	private String getExtension(String uri) {
		ImageUri imageUri = new ImageUri(uri);
		return imageUri.extension;
	}
	
	/**
	 * リサイズ用ファイル名を取得.
	 * @param imageUri 分割したURI
	 * @param sizeName サイズ名
	 * @return リサイズ用ファイル名
	 */
	private String getUriBySize(ImageUri imageUri, String sizeName) {
		StringBuilder sb = new StringBuilder();
		sb.append(imageUri.pathPrefix);
		sb.append("_");
		sb.append(sizeName);
		sb.append(imageUri.extension);
		return sb.toString();
	}
	
	/**
	 * リサイズ
	 * @param data 画像データ
	 * @param pixel ピクセル
	 * @param extension 拡張子
	 * @return リサイズした画像データ
	 */
	private byte[] resize(byte[] data, int pixel, String extension) 
	throws IOException {
		ByteArrayInputStream bin = new ByteArrayInputStream(data);
		BufferedImage inputImage = ImageIO.read(bin);
		BufferedImage scalrImage = Scalr.resize(inputImage, pixel);
		String formatName = extension.substring(1);	// 拡張子先頭の.を除く
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		ImageIO.write(scalrImage, formatName, bout);
		return bout.toByteArray();
	}
	
	/**
	 * ストレージへのアクセスログを出力するかどうかを取得.
	 * @return ストレージへのアクセスログを出力する場合true
	 */
	private boolean isEnableAccessLog() {
		return CloudStorageUtil.isEnableAccessLog() && logger.isDebugEnabled();
	}
	
	/**
	 * 画像URIのパス分割
	 */
	private class ImageUri {
		/** 拡張子まで */
		String pathPrefix;
		/** .拡張子 */
		String extension;
		
		/**
		 * コンストラクタ
		 * @param uri URI
		 */
		ImageUri(String uri) {
			int idx = uri.lastIndexOf(".");
			if (idx > 0) {
				this.pathPrefix = uri.substring(0, idx);
				this.extension = uri.substring(idx);
			}
		}
	}
	
	private class ImageSize {
		/** 名前 */
		String name;
		/** ピクセル */
		int pixel;
		
		/**
		 * コンストラクタ.
		 * 設定「_content.bysize.{ファイル名末尾につける文字列(s, m, lなど)}={ピクセル}」から
		 * 「ファイル名末尾につける文字列」と「ピクセル」を抽出する。
		 * @param propName 設定名
		 * @param propVal 設定値
		 */
		ImageSize(String propName, String propVal) 
		throws InvalidServiceSettingException {
			// _content.bysize.{ファイル名末尾につける文字列(s, m, lなど)}={ピクセル}
			this.name = propName.substring(CONTENT_BYSIZE_PREFIX_LEN);
			if (!StringUtils.isPositiveNumber(propVal)) {
				throw new InvalidServiceSettingException("Please set a positive integer number: " + propName);
			}
			this.pixel = StringUtils.intValue(propVal);
		}
	}

}
