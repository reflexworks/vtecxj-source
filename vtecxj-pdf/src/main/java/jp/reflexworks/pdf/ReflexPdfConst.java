package jp.reflexworks.pdf;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.lowagie.text.pdf.PdfWriter;

import jp.reflexworks.atom.api.AtomConst;

/**
 * ReflexPdf 定数クラス.
 */
public class ReflexPdfConst {
	
	/** 設定 : 一時ファイル生成ディレクトリ */
	public static final String PDF_TEMP_DIR = "_pdf.temp.dir";
	/** 設定デフォルト : 一時ファイル生成ディレクトリ */
	public static final String PDF_TEMP_DIR_DEFAULT = "/var/vtecx/tmp";
	
	/** エンコード */
	public static final String ENCODING = AtomConst.ENCODING;
	/** 改行 */
	public static final String NEWLINE = AtomConst.NEWLINE;
	/** Hash Algorithm */
	public static final String HASH_ALGORITHM = "SHA-256";
	/** true */
	public static final String TRUE = Boolean.TRUE.toString();
	/** false */
	public static final String FALSE = Boolean.FALSE.toString();
	/** 暗号化方式 */
	public static final int ENCRYPTION_TYPE = PdfWriter.ENCRYPTION_AES_128;
	/** 署名キーストアの形式 */
	public static final String KEYSTORE_TYPE = "pkcs12";
	
	/** creator */
	public static final String CREATOR_DEFAULT = "Reflex PDF";
	
	/** PDF version */
	public static final Map<String, Character> PDF_VERSIONS = new ConcurrentHashMap<>();
	static {
		PDF_VERSIONS.put("1.2", PdfWriter.VERSION_1_2);
		PDF_VERSIONS.put("1.3", PdfWriter.VERSION_1_3);
		PDF_VERSIONS.put("1.4", PdfWriter.VERSION_1_4);
		PDF_VERSIONS.put("1.5", PdfWriter.VERSION_1_5);
		PDF_VERSIONS.put("1.6", PdfWriter.VERSION_1_6);
		PDF_VERSIONS.put("1.7", PdfWriter.VERSION_1_7);
	}
	/** PDF version default */
	public static final char PDF_VERSION_DEFAULT = PDF_VERSIONS.get("1.7");

	/** 用紙の向き : 縦長 */
	public static final String PORTRAIT = "portrait";
	/** 用紙の向き : 横長 */
	public static final String LANDSCAPE = "landscape";
	
	/** 用紙サイズ デフォルト */
	public static final String PAGESIZE_DEFAULT = "A4";
	/** ドキュメントのマージン デフォルト */
	public static final float MARGIN_DEFAULT = 36f;
	/** テーブルのセルの数 デフォルト */
	public static final int COLS_DEFAULT = 1;
	/** フォント名 デフォルト */
	public static final String FONT_NAME_DEFAULT = "HeiseiKakuGo-W5";
	/** フォントのエンコーディング デフォルト */
	public static final String FONT_ENCODING_DEFAULT = "UniJIS-UCS2-H";
	/** フォントサイズ デフォルト */
	public static final float FONT_SIZE_DEFAULT = 12f;
	/** 線の太さ デフォルト */
	public static final float BORDER_WIDTH_DEFAULT = 0.5f;
	/** 線の色 デフォルト */
	public static final String BORDER_COLOR_DEFAULT = "#000000";
	
	/** ドキュメント */
	public static final String DOCUMENT = "document";
	/** table */
	public static final String TABLE = "table";
	/** tr */
	public static final String TR = "tr";
	/** td */
	public static final String TD = "td";
	/** 文字列 */
	public static final String TEXT = "text";
	
	/** 内部指定名 : ページ数 */
	public static final String PAGE = "&page";
	
	/** QRコードのファイルフォーマット */
	public static final String QRCODE_FORMAT = "png";


}
