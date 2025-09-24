package jp.reflexworks.pdf;

import java.awt.Color;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.RectangleReadOnly;
import com.lowagie.text.exceptions.BadPasswordException;
import com.lowagie.text.pdf.Barcode;
import com.lowagie.text.pdf.Barcode128;
import com.lowagie.text.pdf.Barcode39;
import com.lowagie.text.pdf.BarcodeEAN;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfAction;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfSignatureAppearance;
import com.lowagie.text.pdf.PdfStamper;
import com.lowagie.text.pdf.PdfWriter;

import jp.reflexworks.pdf.exception.IllegalPdfParameterException;
import jp.reflexworks.pdf.signature.TSAClient;
import jp.reflexworks.pdf.signature.TimestampSignatureImpl;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.PdfManager;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * ReflexPdf 処理クラス.
 */
public class ReflexPdfManager implements PdfManager {
	
	/** ロガー */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * init
	 */
	@Override
	public void init() {
		// Do nothing.
	}

	/**
	 * close
	 */
	@Override
	public void close() {
		// Do nothing.
	}

	/**
	 * PDF生成.
	 * @param htmlTemplate HTMLテンプレート
	 * @param reflexContext ReflexContext
	 * @return PDFデータ
	 */
	@Override
	public byte[] toPdf(String htmlTemplate, ReflexContext reflexContext) 
	throws IOException, TaggingException {
		if (StringUtils.isBlank(StringUtils.trim(htmlTemplate))) {
			throw new IllegalPdfParameterException("html template is required.");
		}
		
		XMLEventReader reader = null;
		Document document = null;
		PdfWriter writer = null;
		
		try (BufferedInputStream bin = new BufferedInputStream(
				new ByteArrayInputStream(htmlTemplate.getBytes(ReflexPdfConst.ENCODING)))) {
			XMLInputFactory factory = XMLInputFactory.newInstance();
			reader = factory.createXMLEventReader(bin);

			// meta情報
			Map<String, String> metaMap = new HashMap<>();
			// タグのqueue
			Deque<String> tagQueue = new ArrayDeque<>();
			// stylesのqueue
			Deque<Map<String, String>> stylesQueue = new ArrayDeque<>();
			// attributeのqueue
			Deque<Map<String, String>> attributesQueue = new ArrayDeque<>();
			// PDF Table
			Deque<PdfPTable> pdfTables = new ArrayDeque<>();
			// Tableのstyles
			Deque<Map<String, String>> tableStylesQueue = new ArrayDeque<>();
			// セル内文字列または画像
			Phrase cellElement = null;
			PdfPTable cellTable = null;
			// 文字列
			String text = null;
			// ページ数
			int pageNum = 0;
			
			// BaseFont保持Map
			Map<String, BaseFont> baseFontMap = new HashMap<>();
			// Font保持Map (document, table, tr, td, string)
			Map<String, Font> fontMap = new HashMap<>();
			
			// 署名情報保持Map
			List<Map<String, String>> signatureMapList = new ArrayList<>();

			// PDF仮出力先
			ByteArrayOutputStream bout = new ByteArrayOutputStream();

			while (reader.hasNext()) {
				XMLEvent event = reader.nextEvent();

				if (event.isStartElement()) {
					StartElement element = event.asStartElement();
					String tag = element.getName().getLocalPart().toLowerCase(Locale.ENGLISH);
					Map<String, String> styles = getStyles(element);

					// qNameチェック
					int layer = tagQueue.size();
					if (layer == 0) {
						// 第１階層は <html> でなければエラー
						if (!"html".equals(tag)) {
							throw new IllegalPdfParameterException("'<html>' must be specified for the first layer.");
						}
					} else if (layer == 1) {
						// 第２階層は <head> か <body> でなければエラー
						if (!"head".equals(tag) && !"body".equals(tag)) {
							throw new IllegalPdfParameterException("'<head>' or '<body>' must be specified for the second layer.");
						}
					} else if (layer == 2) {
						String upperTag = tagQueue.peek();
						if ("head".equals(upperTag)) {
							// 上位階層が <head> の場合、第３階層は <meta> でなければエラー
							if (!"meta".equals(tag)) {
								throw new IllegalPdfParameterException("'<meta>' must be specified in the hierarchy next to '<head>'.");
							}
							String attName = getAttribute(element, "name");
							Map<String, String> tmpMetaMap = null;
							if ("pdf".equals(attName)) {
								// 文書の定義
								tmpMetaMap = getMetaInfo(getAttribute(element, "content"));
								if (tmpMetaMap != null) {
									metaMap.putAll(tmpMetaMap);
								}
								if (!metaMap.containsKey(ReflexPdfConst.PAGE)) {
									metaMap.put(ReflexPdfConst.PAGE, "0");
								}
							}
							if (isSignature(tmpMetaMap)) {
								// 署名の指定がある場合、署名リストに格納する。
								signatureMapList.add(tmpMetaMap);
							}
							
						} else {
							// 上位階層が <body> の場合、第３階層は <div class="_page"> でなければエラー
							if (!"div".equals(tag)) {
								throw new IllegalPdfParameterException("'<div class=\"_page\">' must be specified in the hierarchy next to '<body>'.");
							}
							String attClass = getAttribute(element, "class");
							if (!"_page".equals(attClass)) {
								throw new IllegalPdfParameterException("'<div class=\"_page\">' must be specified in the hierarchy next to '<body>'.");
							}
							
							if (document == null) {
								document = createDocument(styles);
								
								// step 2: creation of the writer
								writer = PdfWriter.getInstance(document, bout);

								// PDFバージョン
								editVersion(writer, metaMap);
								
								// titleや作者名などを設定
								editDocument(document, metaMap);
								
								// デフォルトのフォントを取得
								Font docFont = getDefaultFont(styles);
								fontMap.put(ReflexPdfConst.DOCUMENT, docFont);
								baseFontMap.put(getDefaultFontName(styles), docFont.getBaseFont());
								
								// パスワード暗号化
								if (isEncryption(metaMap)) {
									encrypt(writer, metaMap);
								}

								// step 3: we open the document
								document.open();
	
							} else {
								// 2ページ目以降
								newPage(document, styles);
							}
							// ページ数インクリメント
							pageNum++;
						}
					} else {
						styles.put(ReflexPdfConst.PAGE, String.valueOf(pageNum));

						if ("table".equals(tag)) {
							// テーブル
							if (pdfTables.size() > 0) {
								String upperTag = tagQueue.peek();
								if (!"td".equals(upperTag)) {
									throw new IllegalPdfParameterException("The upper tag of '<table>' must be '<div class=\"_page\">' or '<td>'.");
								}
							}
							// セルの数
							int cols = getStyleInt(styles, "cols", ReflexPdfConst.COLS_DEFAULT);
							// セルの列幅比率
							String widthsStr = getStyle(styles, "widths", null);
							PdfPTable pdfTable = null;
							if (StringUtils.isBlank(widthsStr)) {
								// セルの数でテーブル生成
								pdfTable = new PdfPTable(cols);
							} else {
								// セルの列幅比率でテーブル生成
								float[] widths = getWidths(widthsStr);
								if (styles.containsKey("cols") &&
										widths.length != cols) {
									throw new IllegalPdfParameterException("The numbers of 'cols' and 'widths' in the table are different.'");
								}
								pdfTable = new PdfPTable(widths);
							}
							pdfTables.push(pdfTable);
							// テーブルの幅
							String widthStr = getStyle(styles, "width", null);
							if (!StringUtils.isBlank(widthStr)) {
								pdfTable.setTotalWidth(getFloat(widthStr));
							}
							// デフォルトcellの各種設定
							setCellProperties(styles, pdfTable.getDefaultCell(), null);
							//tableStyles = styles;
							tableStylesQueue.push(styles);
							
							// font
							Font tableFont = getFont(styles, 
									fontMap.get(ReflexPdfConst.DOCUMENT), baseFontMap);
							fontMap.put(getFontMapKey(ReflexPdfConst.TABLE, pdfTables.size()), 
									tableFont);
							String fontName = getFontName(styles, null);
							if (!StringUtils.isBlank(fontName) && 
									!baseFontMap.containsKey(fontName)) {
								baseFontMap.put(fontName, tableFont.getBaseFont());
							}
							
						} else if ("tr".equals(tag)) {
							// テーブル行定義
							String upperTag = tagQueue.peek();
							if (!"table".equals(upperTag)) {
								throw new IllegalPdfParameterException("The upper tag of '<tr>' must be '<table>'.");
							}

						} else if ("td".equals(tag)) {
							// テーブルセル定義
							String upperTag = tagQueue.peek();
							if (!"tr".equals(upperTag)) {
								throw new IllegalPdfParameterException("The upper tag of '<td>' must be '<tr>'.");
							}
							
							// font
							int tableSize = pdfTables.size();
							Font tdFont = getFont(styles, 
									fontMap.get(getFontMapKey(ReflexPdfConst.TABLE, tableSize)), 
									baseFontMap);
							fontMap.put(getFontMapKey(ReflexPdfConst.TD, tableSize), tdFont);
							String fontName = getFontName(styles, null);
							if (!StringUtils.isBlank(fontName) && 
									!baseFontMap.containsKey(fontName)) {
								baseFontMap.put(fontName, tdFont.getBaseFont());
							}

						} else if ("p".equals(tag) || "div".equals(tag) || "span".equals(tag) ||
								"a".equals(tag)) {
							// 文字列、またはdivに指定されたその他の機能
							// font
							Font defaultFont = null;
							int tableSize = pdfTables.size();
							if (tableSize > 0) {
								defaultFont = fontMap.get(getFontMapKey(ReflexPdfConst.TD, 
										tableSize));
							}
							if (defaultFont == null) {
								defaultFont = fontMap.get(ReflexPdfConst.DOCUMENT);
							}
							Font stringFont = getFont(styles, defaultFont, baseFontMap);
							fontMap.put(ReflexPdfConst.TEXT, stringFont);
							String fontName = getFontName(styles, null);
							if (!StringUtils.isBlank(fontName) && 
									!baseFontMap.containsKey(fontName)) {
								baseFontMap.put(fontName, stringFont.getBaseFont());
							}

							text = null;	// 文字列取得開始

							// その他の機能
							String attClass = getAttribute(element, "class");
							if ("div".equals(tag)) {
								if ("_signature".equals(attClass)) {
									// 署名
									signatureMapList.add(styles);
								} else if ("_line".equals(attClass)) {
									// 罫線
									drawLine(writer, styles);
								} else if ("_rectangle".equals(attClass)) {
									// 四角形
									drawRectangle(writer, styles);
								} else if ("_circle".equals(attClass)) {
									// 円
									drawCircle(writer, styles);
								} else if ("_qrcode".equals(attClass)) {
									// QRコード
									Phrase tmpCellElement = null;
									String upperTag = tagQueue.peek();
									if ("td".equals(upperTag)) {
										if (cellElement == null) {
											cellElement = new Phrase();
										}
										tmpCellElement = cellElement;
									}
									drawQrcode(document, writer, tmpCellElement, element, styles);
								} else if (!StringUtils.isBlank(attClass) && 
										attClass.startsWith("_barcode")) {
									// バーコード各種
									Phrase tmpCellElement = null;
									String upperTag = tagQueue.peek();
									if ("td".equals(upperTag)) {
										if (cellElement == null) {
											cellElement = new Phrase();
										}
										tmpCellElement = cellElement;
									}
									drawBarcode(document, writer, tmpCellElement, element, 
											styles, attClass, baseFontMap);
								}
							}

						} else if ("img".equals(tag)) {
							// 画像
							Phrase tmpCellElement = null;
							String upperTag = tagQueue.peek();
							if ("td".equals(upperTag)) {
								if (cellElement == null) {
									cellElement = new Phrase();
								}
								tmpCellElement = cellElement;
							}
							drawImage(document, tmpCellElement, element, styles, reflexContext);

						} else if ("br".equals(tag)) {
							// ここでは何もしない。

						} else {
							// その他のタグはエラー
							throw new IllegalPdfParameterException("Invalid tag. " + tag);
						}
					}

					tagQueue.push(editTag(tag, element));
					stylesQueue.push(styles);
					attributesQueue.push(getAttributes(element));

				} else if (event.isCharacters()) {
					// 文字列の取得
					String tag = tagQueue.peek();
					String tmpText = event.asCharacters().getData();
					if ("p".equals(tag) || "div".equals(tag) || "span".equals(tag) ||
							"a".equals(tag)) {
						if (text == null) {
							text = tmpText;
						} else if (tmpText != null) {
							text += tmpText;
						}
					} else if (!"br".equals(tag)) {
						text = tmpText;
					}

				} else if (event.isEndElement()) {
					String tag = tagQueue.pop();
					String upperTag = tagQueue.peek();
					Map<String, String> styles = stylesQueue.pop();
					Map<String, String> attributes = attributesQueue.pop();

					if ("table".equals(tag)) {
						int tableSize = pdfTables.size();
						PdfPTable pdfTable = pdfTables.pop();
						if (tableSize > 1) {
							// セル内テーブル
							cellTable = pdfTable;
						} else {
							// テーブル出力
							drawTable(document, writer, pdfTable, styles);
						}
						pdfTable = null;
						tableStylesQueue.pop();
						fontMap.remove(getFontMapKey(ReflexPdfConst.TABLE, tableSize));
						
					} else if ("tr".equals(tag)) {
						// Do nothing.
						
					} else if ("td".equals(tag)) {
						fontMap.remove(getFontMapKey(ReflexPdfConst.TD, pdfTables.size()));
						
						PdfPCell pdfCell = null;
						if (cellElement != null) {
							pdfCell = new PdfPCell(cellElement);
							cellElement = null;
						} else if (cellTable != null) {
							pdfCell = new PdfPCell(cellTable);
							cellTable = null;
						} else {
							pdfCell = new PdfPCell();
						}
						// セルの各種設定
						Map<String, String> tableStyles = tableStylesQueue.peek();
						setCellProperties(styles, pdfCell, tableStyles);
						// セルの列結合
						int colspan = getStyleInt(styles, "colspan", 0);
						if (colspan > 1) {
							pdfCell.setColspan(colspan);
						}
						// セルの行結合
						int rowspan = getStyleInt(styles, "rowspan", 0);
						if (rowspan > 1) {
							pdfCell.setRowspan(rowspan);
						}
						PdfPTable pdfTable = pdfTables.peek();
						pdfTable.addCell(pdfCell);

					} else if ("p".equals(tag) || "div".equals(tag) || "span".equals(tag) ||
							"a".equals(tag)) {
						// 文字列の書き込みをここで行う。
						if (!StringUtils.isBlank(text)) {
							Font font = fontMap.get(ReflexPdfConst.TEXT);

							// pとdivは改行する
							boolean isBr = false;
							if ("p".equals(tag) || "div".equals(tag)) {
								isBr = true;
							}
							
							Chunk chunk = createTextChunk(tag, text, font, styles, attributes);
							
							String leadingStr = getStyle(styles, "leading", null);
							Phrase phrase = null;
							if ("td".equals(upperTag) && cellElement != null) {
								cellElement.add(chunk);
								phrase = cellElement;
							} else {
								if (StringUtils.isBlank(leadingStr)) {
									phrase = new Phrase(chunk);
								} else {
									phrase = new Phrase(getFloat(leadingStr), chunk);
								}
							}
							if ("td".equals(upperTag) && cellElement == null) {
								cellElement = phrase;
							}
							if (isBr) {
								Chunk brChunk = new Chunk(ReflexPdfConst.NEWLINE, font);
								phrase.add(brChunk);
							}

							if ("td".equals(upperTag)) {
								// Do nothing.
							} else {
								drawText(document, writer, phrase, text, font, styles);
							}
						}
						
						fontMap.remove(ReflexPdfConst.TEXT);

					} else if ("br".equals(tag)) {
						// 改行
						if ("p".equals(upperTag) || "div".equals(upperTag) || 
								"span".equals(upperTag) || "a".equals(upperTag)) {
							// ここの処理は、<p><span><div>内に指定された<br>の場合有効となる。
							if (text == null) {
								text = "\n";
							} else {
								text += "\n";
							}
						} else {
							Chunk brChunk = new Chunk(ReflexPdfConst.NEWLINE);
							Phrase phrase = null;
							if ("td".equals(upperTag) && cellElement != null) {
								cellElement.add(brChunk);
								phrase = cellElement;
							} else {
								phrase = new Phrase(brChunk);
							}
							if ("td".equals(upperTag) && cellElement == null) {
								cellElement = phrase;
							}
							document.add(phrase);
						}
					}
					
					if (!"br".equals(tag)) {
						text = null;
					}
				}
			}
			if (document != null) {
				try {
					document.close();
					document = null;
				} catch (Throwable e) {
					logger.warn("[generatePdf] document close error.", e);
				}
			} else {
				throw new IllegalPdfParameterException("'<body>' and '<div class=\"_page\">' must be specified.");
			}
			
			byte[] pdfData = bout.toByteArray();
			
			// 署名
			for (Map<String, String> signatureMap : signatureMapList) {
				pdfData = signature(pdfData, signatureMap, metaMap, reflexContext);
			}
			
			// タイムスタンプ
			if (metaMap.containsKey("timestamp")) {
				pdfData = timestamp(pdfData, metaMap);
			}
			
			return pdfData;

		} catch (XMLStreamException e) {
			// テンプレート不正
			throw new IllegalPdfParameterException(e.getMessage(), e);
		} finally {
			if (document != null) {
				try {
					document.close();
				} catch (Throwable e) {
					logger.warn("[toPdf] document close error.", e);
				}
			}
			if (reader != null) {
				try {
					reader.close();
				} catch (Throwable e) {
					logger.warn("[toPdf] XMLEventReader close error.", e);
				}
			}
		}
	}
	
	/**
	 * XMLのattribute(属性)の値を取得.
	 * @param element StartElement
	 * @param name attributeの名前
	 * @return attributeの値
	 */
	private String getAttribute(StartElement element, String name) {
		Attribute attr = element.getAttributeByName(new QName(name));
		if (attr != null) {
			return attr.getValue();
		}
		return null;
	}
	
	/**
	 * XMLのタグのattribute(属性)一覧を取得.
	 * @param element StartElement
	 * @return attribute map
	 */
	private Map<String, String> getAttributes(StartElement element) {
		Map<String, String> attrMap = new HashMap<>();
		Iterator<Attribute> it = element.getAttributes();
		while (it.hasNext()) {
			Attribute attr = it.next();
			attrMap.put(attr.getName().getLocalPart(), attr.getValue());
		}
		return attrMap;
	}
	
	/**
	 * styleに指定された値をMapに変換する.
	 * @param element StartElement
	 * @return styleをMapに変換したもの。
	 */
	private Map<String, String> getStyles(StartElement element) {
		String styleStr = getAttribute(element, "style");
		return getStyles(styleStr);
	}

	/**
	 * styleに指定された値をMapに変換する.
	 * @param styleStr styleに指定された文字列
	 * @return styleをMapに変換したもの。
	 */
	private Map<String, String> getStyles(String styleStr) {
		Map<String, String> styles = new HashMap<>();
		if (!StringUtils.isBlank(styleStr)) {
			String[] styleParts = styleStr.split(";");
			for (String stylePart : styleParts) {
				if (!StringUtils.isBlank(stylePart)) {
					int idx = stylePart.indexOf(":");
					String name = null;
					String val = null;
					if (idx == -1) {
						name = stylePart;
					} else {
						name = stylePart.substring(0, idx);
						val = stylePart.substring(idx + 1);
					}
					if (!StringUtils.isBlank(name)) {
						styles.put(name, val);
					}
				}
			}
		}
		return styles;
	}
	
	/**
	 * PageSizeを取得.
	 * @param pagesize styleに指定されたpagesize
	 * @param orientation styleに指定されたorientation
	 * @return PageSize
	 */
	private Rectangle getPageSize(String pagesize, String orientation) 
	throws IllegalPdfParameterException {
		Rectangle pageSizeRectangle = null;
		boolean isSpecifiedNumber = false;	// サイズを数値指定かどうか
		float x = 0;	// ピクセル直接指定(横)
		float y = 0;	// ピクセル直接指定(縦)
		if (StringUtils.isBlank(pagesize)) {
			pagesize = ReflexPdfConst.PAGESIZE_DEFAULT;
		} else {
			// 入力チェック
			int idx = pagesize.indexOf(",");
			if (idx > -1) {
				// 縦横ピクセル直接指定
				String xstr = pagesize.substring(0, idx);
				String ystr = pagesize.substring(idx + 1);
				try {
					x = Float.parseFloat(xstr);
					if (!StringUtils.isPositiveNumber(x)) {
						throw new IllegalPdfParameterException("PageSize x is not positive number. " + xstr);
					}
				} catch (NumberFormatException e) {
					throw new IllegalPdfParameterException("PageSize x is not number. " + xstr);
				}
				try {
					y = Float.parseFloat(ystr);
					if (!StringUtils.isPositiveNumber(y)) {
						throw new IllegalPdfParameterException("PageSize y is not positive number. " + ystr);
					}
				} catch (NumberFormatException e) {
					throw new IllegalPdfParameterException("PageSize y is not number. " + ystr);
				}
				isSpecifiedNumber = true;
			} else {
				// A・B用紙サイズ指定
				try {
					pageSizeRectangle = PageSize.getRectangle(pagesize);
				} catch (RuntimeException e) {
					throw new IllegalPdfParameterException("PageSize is invalid. " + pagesize);
				}
				// 縦横指定
				boolean isLandscape = false;
				if (!StringUtils.isBlank(orientation)) {
					if (ReflexPdfConst.LANDSCAPE.equals(orientation)) {
						isLandscape = true;
					} else if (ReflexPdfConst.PORTRAIT.equals(orientation)) {
						// Do nothing.
					} else {
						throw new IllegalPdfParameterException("Orientation is invalid. " + orientation);
					}
				}
				if (isLandscape) {
					// 横長の場合、縦横比を逆にする。
					x = pageSizeRectangle.getTop();
					y = pageSizeRectangle.getRight();
					isSpecifiedNumber = true;
				}
			}
		}
		if (isSpecifiedNumber) {
			pageSizeRectangle = new RectangleReadOnly(x, y);
		}
		return new Rectangle(pageSizeRectangle);
	}

	/**
	 * 数値(float)に変換.
	 * @param val 値
	 * @return float
	 */
	private float getFloat(String val) throws IllegalPdfParameterException {
		if (StringUtils.isBlank(val)) {
			return 0;
		}
		try {
			return Float.parseFloat(val);
		} catch (NumberFormatException e) {
			throw new IllegalPdfParameterException(e.getMessage());
		}
	}
	
	/**
	 * 真偽値(boolean)に変換.
	 * trueかfalse以外はエラー
	 * @param val 値
	 * @return boolean
	 */
	private boolean getBoolean(String val) throws IllegalPdfParameterException {
		if (StringUtils.isBlank(val)) {
			return false;
		}
		if (StringUtils.isBoolean(val)) {
			return Boolean.parseBoolean(val);
		} else {
			throw new IllegalPdfParameterException("Not a boolean value. " + val);
		}
	}
	
	/**
	 * 文字列をバイト配列に変換.
	 * nullや空文字の場合はnullを返す。
	 * @param val 文字列
	 * @return バイト配列
	 */
	private byte[] getBytes(String val) 
	throws IOException {
		if (StringUtils.isBlank(val)) {
			return null;
		}
		return val.getBytes(ReflexPdfConst.ENCODING);
	}
	
	/**
	 * Documentオブジェクトを生成.
	 * @param styles style情報Map
	 * @return Documentオブジェクト
	 */
	private Document createDocument(Map<String, String> styles) 
	throws IllegalPdfParameterException {
		// step 1: creation of a document-object
		// 引数 : Rectangle pageSize, float marginLeft, float marginRight, float marginTop, float marginBottom
		Rectangle pageSize = getPageSize(styles.get("pagesize"), styles.get("orientation"));
		float marginLeft = getStyleFloat(styles, "left", ReflexPdfConst.MARGIN_DEFAULT);
		float marginRight = getStyleFloat(styles, "right", ReflexPdfConst.MARGIN_DEFAULT);
		float marginTop = getStyleFloat(styles, "top", ReflexPdfConst.MARGIN_DEFAULT);
		float marginBottom = getStyleFloat(styles, "bottom", ReflexPdfConst.MARGIN_DEFAULT);
		String bgColorStr = getStyle(styles, "bgcolor", null);
		if (!StringUtils.isBlank(bgColorStr)) {
			pageSize.setBackgroundColor(getColor(bgColorStr));
		}
		return new Document(pageSize, marginLeft, marginRight, marginTop, marginBottom);
	}

	/**
	 * タイトル、作者名などを設定.
	 * @param document Document
	 * @param metaMap meta情報
	 */
	private void editDocument(Document document, Map<String, String> metaMap) {
		// PDFファイルの「文書のプロパティ」「概要」
		String title = metaMap.get("title");
		if (!StringUtils.isBlank(title)) {
			document.addTitle(title);
		}
		String author = metaMap.get("author");
		if (!StringUtils.isBlank(author)) {
			document.addAuthor(author);
		}
		String subject = metaMap.get("subject");
		if (!StringUtils.isBlank(subject)) {
			document.addSubject(subject);
		}
		String keywords = metaMap.get("keywords");
		if (!StringUtils.isBlank(keywords)) {
			document.addKeywords(keywords);
		}
		
		// creatorはReflex
		document.addCreator(ReflexPdfConst.CREATOR_DEFAULT);
	}
	
	/**
	 * 改ページ処理.
	 * @param document Document
	 * @param styles style情報Map
	 */
	private void newPage(Document document, Map<String, String> styles) 
	throws IllegalPdfParameterException {
		Rectangle pageSize = null;
		if (styles.containsKey("pagesize")) {
			pageSize = getPageSize(styles.get("pagesize"), styles.get("orientation"));
			document.setPageSize(pageSize);
		} else {
			pageSize = document.getPageSize();
		}
		if (styles.containsKey("top") || styles.containsKey("left") || 
				styles.containsKey("right") || styles.containsKey("bottom")) {
			float marginLeft = getStyleFloat(styles, "left", ReflexPdfConst.MARGIN_DEFAULT);
			float marginRight = getStyleFloat(styles, "right", ReflexPdfConst.MARGIN_DEFAULT);
			float marginTop = getStyleFloat(styles, "top", ReflexPdfConst.MARGIN_DEFAULT);
			float marginBottom = getStyleFloat(styles, "bottom", ReflexPdfConst.MARGIN_DEFAULT);
			document.setMargins(marginLeft, marginRight, marginTop, marginBottom);
		}
		document.newPage();
		String bgColorStr = getStyle(styles, "bgcolor", null);
		if (!StringUtils.isBlank(bgColorStr)) {
			pageSize.setBackgroundColor(getColor(bgColorStr));
		}
	}
	
	/**
	 * PDFのバージョンを指定.
	 * @param writer PdfWriter
	 * @param metaMap meta情報
	 */
	private void editVersion(PdfWriter writer, Map<String, String> metaMap) 
	throws IllegalPdfParameterException {
		String versionStr = metaMap.get("version");
		Character version = null;
		if (StringUtils.isBlank(versionStr)) {
			version = ReflexPdfConst.PDF_VERSION_DEFAULT;
		} else {
			version = ReflexPdfConst.PDF_VERSIONS.get(versionStr);
		}
		if (version == null) {
			throw new IllegalPdfParameterException("PDF version is invalid. " + versionStr);
		}
		writer.setPdfVersion(version);
	}

	/**
	 * stylesマップから値を取得する。nullの場合デフォルト値を返却する。
	 * @param styles stylesマップ
	 * @param defaultStyles デフォルトのstylesマップ
	 * @param name stylesマップのキー
	 * @return 値
	 */
	private String getStyle(Map<String, String> styles, 
			Map<String, String> defaultStyles, String name) {
		String val = getStyle(styles, name, null);
		if (!StringUtils.isBlank(val)) {
			return val;
		} else {
			return getStyle(defaultStyles, name, null);
		}
	}

	/**
	 * stylesマップから値を取得する。nullの場合デフォルト値を返却する。
	 * @param styles stylesマップ
	 * @param name stylesマップのキー
	 * @param defVal デフォルト値
	 * @return 値
	 */
	private String getStyle(Map<String, String> styles, String name, String defVal) {
		if (styles == null) {
			return defVal;
		}
		String val = styles.get(name);
		if (!StringUtils.isBlank(val)) {
			return val;
		} else {
			return defVal;
		}
	}
	
	/**
	 * stylesマップから値を取得する。nullの場合デフォルト値を返却する。
	 * @param styles stylesマップ
	 * @param name stylesマップのキー
	 * @param defVal デフォルト値
	 * @return 値
	 */
	private int getStyleInt(Map<String, String> styles, String name, int defVal) {
		if (styles == null) {
			return defVal;
		}
		String val = styles.get(name);
		if (!StringUtils.isBlank(val)) {
			if (StringUtils.isInteger(val)) {
				return Integer.parseInt(val);
			}
		}
		return defVal;
	}
	
	/**
	 * stylesマップから値を取得する。nullの場合デフォルト値を返却する。
	 * @param styles stylesマップ
	 * @param name stylesマップのキー
	 * @param defVal デフォルト値
	 * @return 値
	 */
	private float getStyleFloat(Map<String, String> styles, String name, float defVal) 
	throws IllegalPdfParameterException {
		if (styles == null) {
			return defVal;
		}
		String val = styles.get(name);
		if (!StringUtils.isBlank(val)) {
			if (StringUtils.isNumber(val)) {
				try {
					return Float.parseFloat(val);
				} catch (NumberFormatException e) {
					throw new IllegalPdfParameterException(e.getMessage());
				}
			}
		}
		return defVal;
	}

	/**
	 * stylesマップから値を取得する。nullの場合デフォルト値を返却する。
	 * @param styles stylesマップ
	 * @param name stylesマップのキー
	 * @param defVal デフォルト値
	 * @return 値
	 */
	private boolean getStyleBoolean(Map<String, String> styles, String name, boolean defVal) {
		if (styles == null) {
			return defVal;
		}
		String val = styles.get(name);
		if (!StringUtils.isBlank(val)) {
			if (StringUtils.isBoolean(val)) {
				return Boolean.parseBoolean(val);
			}
		}
		return defVal;
	}
	
	/**
	 * stylesマップに設定があるかどうか
	 * @param styles stylesマップ
	 * @param name stylesマップのキー
	 * @return stylesマップに設定がある場合true
	 */
	private boolean containsKey(Map<String, String> styles, String name) {
		if (styles == null) {
			return false;
		}
		return styles.containsKey(name);
	}

	/**
	 * デフォルトフォントを取得.
	 * @param styles stylesマップ
	 * @return デフォルトフォント
	 */
	private Font getDefaultFont(Map<String, String> styles) 
	throws IOException {
		String fontName = getDefaultFontName(styles);
		String fontEncoding = getFontEncoding(styles, ReflexPdfConst.FONT_ENCODING_DEFAULT);
		// TODO 埋め込みフォント対応
		BaseFont bf = BaseFont.createFont(fontName, fontEncoding, 
				BaseFont.NOT_EMBEDDED);

		float fontSize = getFontSize(styles, ReflexPdfConst.FONT_SIZE_DEFAULT);
		Color fontColor = getFontColor(styles, null);
		int fontStyle = getFontStyle(styles, Font.NORMAL);
		return new Font(bf, fontSize, fontStyle, fontColor);
	}
	
	/**
	 * フォントを取得.
	 * @param styles stylesマップ
	 * @param defaultFont デフォルトフォント
	 * @param baseFontMap BaseFont保持Map
	 * @return フォント
	 */
	private Font getFont(Map<String, String> styles, Font defaultFont,
			Map<String, BaseFont> baseFontMap) 
	throws IOException {
		BaseFont bf = null;
		String fontName = getStyle(styles, "font", null);
		if (StringUtils.isBlank(fontName)) {
			bf = defaultFont.getBaseFont();
		} else {
			if (baseFontMap.containsKey(fontName)) {
				bf = baseFontMap.get(fontName);
			} else {
				String fontEncoding = getFontEncoding(styles, 
						defaultFont.getBaseFont().getEncoding());
				// TODO 埋め込みフォント対応
				bf = BaseFont.createFont(fontName, fontEncoding, 
						BaseFont.NOT_EMBEDDED);
			}
		}

		float fontSize = getFontSize(styles, defaultFont.getSize());
		Color fontColor = getFontColor(styles, defaultFont.getColor());
		int fontStyle = getFontStyle(styles, defaultFont.getStyle());
		if (bf.equals(defaultFont.getBaseFont()) &&
				fontSize == defaultFont.getSize() &&
				((fontColor != null && fontColor.equals(defaultFont.getColor())) ||
						(fontColor == null && defaultFont == null)) &&
				fontStyle == defaultFont.getStyle()) {
			return defaultFont;
		} else {
			return new Font(bf, fontSize, fontStyle, fontColor);
		}
	}

	/**
	 * styleに指定されたフォント名を取得.
	 * 指定がない場合はデフォルトのフォント名を返す。
	 * @param styles stylesマップ
	 * @return styleに指定されたフォント名
	 */
	private String getDefaultFontName(Map<String, String> styles) {
		return getFontName(styles, ReflexPdfConst.FONT_NAME_DEFAULT);
	}

	/**
	 * styleに指定されたフォント名を取得.
	 * @param styles stylesマップ
	 * @param defaultFontName デフォルトのフォント名
	 * @return styleに指定されたフォント名
	 */
	private String getFontName(Map<String, String> styles, String defaultFontName) {
		return getStyle(styles, "font", defaultFontName);
	}

	/**
	 * styleに指定されたフォントエンコーディングを取得.
	 * @param styles stylesマップ
	 * @param defaultEncoding デフォルトのフォントエンコーディング
	 * @return フォントエンコーディング
	 */
	private String getFontEncoding(Map<String, String> styles, String defaultEncoding) {
		return getStyle(styles, "fontencoding", defaultEncoding);
	}
	
	/**
	 * フォントサイズを取得
	 * @param styles stylesマップ
	 * @param defaultSize デフォルトサイズ
	 * @return フォントサイズ
	 */
	private float getFontSize(Map<String, String> styles, float defaultSize) 
	throws IllegalPdfParameterException {
		String fontSizeStr = getStyle(styles, "fontsize", null);
		if (StringUtils.isBlank(fontSizeStr)) {
			fontSizeStr = getStyle(styles, "size", null);
		}
		float fontSize = getFloat(removePx(fontSizeStr));
		if (fontSize <= 0) {
			fontSize = defaultSize;
		}
		return fontSize;
	}
	
	/**
	 * フォント色を取得
	 * @param styles stylesマップ
	 * @param defaultColor デフォルトフォント色
	 * @return フォント色
	 */
	private Color getFontColor(Map<String, String> styles, Color defaultColor) 
	throws IllegalPdfParameterException {
		String fontColorStr = getStyle(styles, "fontcolor", null);
		if (StringUtils.isBlank(fontColorStr)) {
			fontColorStr = getStyle(styles, "color", null);
		}
		Color fontColor = getColor(fontColorStr);
		if (fontColor != null) {
			return fontColor;
		}
		return defaultColor;
	}
	
	/**
	 * フォントスタイルを取得.
	 * @param styles フォントスタイル (bold, italic, underline, strikethru)
	 * @param defaultFontStyle デフォルトのフォントスタイル
	 * @return フォントスタイル
	 */
	private int getFontStyle(Map<String, String> styles, int defaultFontStyle) 
	throws IllegalPdfParameterException {
		String fontStyleStr = getStyle(styles, "fontstyle", null);
		if (StringUtils.isBlank(fontStyleStr)) {
			fontStyleStr = getStyle(styles, "style", null);
		}
		int fontStyle = Font.NORMAL;
		if (!StringUtils.isBlank(fontStyleStr)) {
			String[] fontStyleParts = fontStyleStr.toLowerCase(Locale.ENGLISH).split(",");
			for (String fontStylePart : fontStyleParts) {
				if ("bold".equals(fontStylePart)) {
					fontStyle |= Font.BOLD;
				} else if ("italic".equals(fontStylePart)) {
					fontStyle |= Font.ITALIC;
				} else if ("underline".equals(fontStylePart)) {
					fontStyle |= Font.UNDERLINE;
				} else if ("strikethru".equals(fontStylePart)) {
					fontStyle |= Font.STRIKETHRU;
				} else {
					throw new IllegalPdfParameterException("Font style is invalid. " + fontStylePart);
				}
			}
		} else {
			fontStyle = defaultFontStyle;
		}
		return fontStyle;
	}
	
	/**
	 * Colorオブジェクトを生成.
	 * 指定がない場合nullを返す。
	 * @param colorStr 色文字列 (#xxxxxx)
	 * @return Colorオブジェクト
	 */
	private Color getColor(String colorStr) {
		if (StringUtils.isBlank(colorStr)) {
			return null;
		}
		return Color.decode(colorStr);
	}
	
	/**
	 * widthsのカンマ区切りをfloat配列にして返却.
	 * @param widthsStr widthsのカンマ区切り
	 * @return widthsのfloat配列
	 */
	private float[] getWidths(String widthsStr) 
	throws IllegalPdfParameterException {
		String[] widthParts = widthsStr.split(",");
		int len = widthParts.length;
		float[] ret = new float[len];
		for (int i = 0; i < len; i++) {
			ret[i] = getFloat(widthParts[i]);
		}
		return ret;
	}
	
	/**
	 * Horizontal Alignment int値を取得.
	 * @param alignhStr Horizontal Alignment文字列 (left, center, right, justified)
	 * @return Horizontal Alignment int値
	 */
	private int getHorizontalAlignment(String alignhStr) 
	throws IllegalPdfParameterException {
		if ("center".equals(alignhStr)) {
			return Element.ALIGN_CENTER;
		} else if ("left".equals(alignhStr)) {
			return Element.ALIGN_LEFT;
		} else if ("right".equals(alignhStr)) {
			return Element.ALIGN_RIGHT;
		} else if ("justified".equals(alignhStr)) {
			return Element.ALIGN_JUSTIFIED;
		} else {
			throw new IllegalPdfParameterException("Horizontal alignment is invalid. " + alignhStr);
		}
	}
	/**
	 * Vertical Alignment int値を取得.
	 * @param aligyhStr Vertical Alignment文字列 (bottom, baseline, top, bottom)
	 * @return Vertical Alignment int値
	 */
	private int getVerticalAlignment(String alignyStr) 
	throws IllegalPdfParameterException {
		if ("middle".equals(alignyStr)) {
			return Element.ALIGN_MIDDLE;
		} else if ("baseline".equals(alignyStr)) {
			return Element.ALIGN_BASELINE;
		} else if ("top".equals(alignyStr)) {
			return Element.ALIGN_TOP;
		} else if ("bottom".equals(alignyStr)) {
			return Element.ALIGN_BOTTOM;
		} else {
			throw new IllegalPdfParameterException("Vertical alignment is invalid. " + alignyStr);
		}
	}
	
	/**
	 * ピクセル(px)を除去して返却
	 * @param str 文字列
	 * @return 編集した文字列
	 */
	private String removePx(String str) {
		if (!StringUtils.isBlank(str) && str.endsWith("px")) {
			return str.substring(0, str.length() - 2);
		}
		return str;
	}
	
	/**
	 * セルの各種設定を行う.
	 * @param styles style情報Map
	 * @param cell セル
	 * @param defaultStyles tableタグの指定情報
	 */
	private void setCellProperties(Map<String, String> styles, PdfPCell cell,
			Map<String, String> defaultStyles) 
	throws IllegalPdfParameterException {
		// cellpadding (セルの全方向境界線からの間隔)
		String cellpaddingStr = getStyle(styles, defaultStyles, "cellpadding");
		if (!StringUtils.isBlank(cellpaddingStr)) {
			cell.setPadding(getFloat(cellpaddingStr));
		}
		String paddingTopStr = getStyle(styles, defaultStyles, "paddingtop");
		if (!StringUtils.isBlank(paddingTopStr)) {
			cell.setPaddingTop(getFloat(paddingTopStr));
		}
		String paddingLeftStr = getStyle(styles, defaultStyles, "paddingleft");
		if (!StringUtils.isBlank(paddingLeftStr)) {
			cell.setPaddingLeft(getFloat(paddingLeftStr));
		}
		String paddingRightStr = getStyle(styles, defaultStyles, "paddingright");
		if (!StringUtils.isBlank(paddingRightStr)) {
			cell.setPaddingRight(getFloat(paddingRightStr));
		}
		String paddingBottomStr = getStyle(styles, defaultStyles, "paddingbottom");
		if (!StringUtils.isBlank(paddingBottomStr)) {
			cell.setPaddingBottom(getFloat(paddingBottomStr));
		}
		// border width
		String borderWidthStr = getStyle(styles, defaultStyles, "border");
		if (!StringUtils.isBlank(borderWidthStr)) {
			cell.setBorderWidth(getFloat(borderWidthStr));
		}
		// border color
		String borderColorStr = getStyle(styles, defaultStyles, "bordercolor");
		if (!StringUtils.isBlank(borderColorStr)) {
			cell.setBorderColor(getColor(borderColorStr));
		}
		String borderColorTopStr = getStyle(styles, defaultStyles, "bordercolortop");
		if (!StringUtils.isBlank(borderColorTopStr)) {
			cell.setBorderColorTop(getColor(borderColorTopStr));
		}
		String borderColorLeftStr = getStyle(styles, defaultStyles, "bordercolorleft");
		if (!StringUtils.isBlank(borderColorLeftStr)) {
			cell.setBorderColorLeft(getColor(borderColorLeftStr));
		}
		String borderColorRightStr = getStyle(styles, defaultStyles, "bordercolorright");
		if (!StringUtils.isBlank(borderColorRightStr)) {
			cell.setBorderColorRight(getColor(borderColorRightStr));
		}
		String borderColorBottomStr = getStyle(styles, defaultStyles, "bordercolorbottom");
		if (!StringUtils.isBlank(borderColorBottomStr)) {
			cell.setBorderColorBottom(getColor(borderColorBottomStr));
		}
		// borderを表示するかどうか
		String topStr = getStyle(styles, defaultStyles, "top");
		String leftStr = getStyle(styles, defaultStyles, "left");
		String rightStr = getStyle(styles, defaultStyles, "right");
		String bottomStr = getStyle(styles, defaultStyles, "bottom");
		if (!StringUtils.isBlank(topStr) || !StringUtils.isBlank(leftStr) ||
				!StringUtils.isBlank(rightStr) || !StringUtils.isBlank(bottomStr)) {
			// デフォルト値
			boolean setBorder = false;
			String tmpBorderWidth = getStyle(styles, "border", null);
			if (!StringUtils.isBlank(tmpBorderWidth)) {
				// セルのborderが0の場合、ここでfalseになる。
				if (getFloat(tmpBorderWidth) > 0) {
					setBorder = true;
				}
			} else {
				tmpBorderWidth = getStyle(defaultStyles, "border", null);
				if (!StringUtils.isBlank(tmpBorderWidth)) {
					if (getFloat(tmpBorderWidth) > 0) {
						setBorder = true;
					}
				} else {
					// セル、tableのどちらにも指定がない場合はデフォルト
					setBorder = true;
				}
			}
			int border = 0;
			if (!StringUtils.isBlank(topStr)) {
				if (getBoolean(topStr)) {
					border += Rectangle.TOP;
				}
			} else if (setBorder) {
				border += Rectangle.TOP;
			}
			if (!StringUtils.isBlank(leftStr)) {
				if (getBoolean(leftStr)) {
					border += Rectangle.LEFT;
				}
			} else if (setBorder) {
				border += Rectangle.LEFT;
			}
			if (!StringUtils.isBlank(rightStr)) {
				if (getBoolean(rightStr)) {
					border += Rectangle.RIGHT;
				}
			} else if (setBorder) {
				border += Rectangle.RIGHT;
			}
			if (!StringUtils.isBlank(bottomStr)) {
				if (getBoolean(bottomStr)) {
					border += Rectangle.BOTTOM;
				}
			} else if (setBorder) {
				border += Rectangle.BOTTOM;
			}
			cell.setBorder(border);
			// 線を引く指定でborder widthが0の場合、デフォルト値を設定
			if (border > 0 && cell.getBorderWidth() == 0) {
				cell.setBorderWidth(ReflexPdfConst.BORDER_WIDTH_DEFAULT);
			}
		}
		
		// background color
		String bgcolorStr = getStyle(styles, defaultStyles, "bgcolor");
		if (!StringUtils.isBlank(bgcolorStr)) {
			cell.setBackgroundColor(getColor(bgcolorStr));
		}
		// horizontal alignment (セル内データの横方向の配置)
		String alignh = getStyle(styles, "halign", null);
		if (StringUtils.isBlank(alignh)) {
			alignh = getStyle(styles, "align", null);
			if (StringUtils.isBlank(alignh)) {
				alignh = getStyle(defaultStyles, "halign", null);
				if (StringUtils.isBlank(alignh)) {
					alignh = getStyle(defaultStyles, "align", null);
				}
			}
		}
		if (!StringUtils.isBlank(alignh)) {
			cell.setHorizontalAlignment(getHorizontalAlignment(alignh));
		}
		// vertical alignment (セル内データの縦方向の配置)
		String valign = getStyle(styles, defaultStyles, "valign");
		if (!StringUtils.isBlank(valign)) {
			cell.setVerticalAlignment(getVerticalAlignment(valign));
		}
		// 改行するかどうか
		String nowrap = getStyle(styles, defaultStyles, "nowrap");
		if (!StringUtils.isBlank(nowrap)) {
			cell.setNoWrap(getBoolean(nowrap));
		}
		// 改行ピッチ
		String leading = getStyle(styles, defaultStyles, "leading");
		if (!StringUtils.isBlank(leading)) {
			cell.setLeading(getFloat(leading), 0);
		}
		// 文字間隔
		String space = getStyle(styles, defaultStyles, "space");
		if (!StringUtils.isBlank(space)) {
			cell.setSpaceCharRatio(getFloat(removePx(space)));
		}
		// セルの高さ
		float height = getStyleFloat(styles, "fixedheight", 0);
		if (height > 0) {
			cell.setFixedHeight(height);
		} else {
			height = getStyleFloat(styles, "minheight", 0);
			if (height == 0) {
				height = getStyleFloat(styles, "height", 0);
			}
			if (height > 0) {
				cell.setMinimumHeight(height);
			} else {
				height = getStyleFloat(defaultStyles, "fixedheight", 0);
				if (height > 0) {
					cell.setFixedHeight(height);
				} else {
					height = getStyleFloat(defaultStyles, "minheight", 0);
					if (height == 0) {
						height = getStyleFloat(defaultStyles, "height", 0);
					}
					if (height > 0) {
						cell.setMinimumHeight(height);
					}
				}
			}
		}
		// インデント
		String indent = getStyle(styles, defaultStyles, "indent");
		if (StringUtils.isBlank(indent)) {
			indent = getStyle(styles, defaultStyles, "indentleft");
		}
		if (!StringUtils.isBlank(indent)) {
			cell.setIndent(getFloat(indent));
		}
		String indentright = getStyle(styles, defaultStyles, "indentright");
		if (!StringUtils.isBlank(indentright)) {
			cell.setRightIndent(getFloat(indentright));
		}
	}
	
	/**
	 * フォント格納Mapのキーを取得.
	 * {tag}#{idx}
	 * @param tag タグ名
	 * @param idx tableの階層
	 * @return フォント格納Mapのキー
	 */
	private String getFontMapKey(String tag, int idx) {
		StringBuilder sb = new StringBuilder();
		sb.append(tag);
		sb.append("#");
		sb.append(idx);
		return sb.toString();
	}
	
	/**
	 * meta情報を抽出し、Mapにして返却する.
	 * <meta name="pdf" content="title=xxx,author=xxx,subject=xxx,keywords=xxx" />
	 * @param contentStr meta情報 (key=value,key=value, ...)
	 */
	private Map<String, String> getMetaInfo(String contentStr) {
		if (StringUtils.isBlank(contentStr)) {
			return null;
		}
		String[] contentParts = contentStr.split(",");
		Map<String, String> metaMap = new HashMap<>();
		for (String contentPart : contentParts) {
			if (StringUtils.isBlank(contentStr)) {
				continue;
			}
			int idx = contentPart.indexOf("=");
			String key = null;
			String val = null;
			if (idx == -1) {
				key = contentPart;
				val = "";
			} else {
				key = contentPart.substring(0, idx);
				val = contentPart.substring(idx + 1);
			}
			metaMap.put(key, val);
		}
		return metaMap;
	}
	
	/**
	 * パスワード暗号化.
	 * @param writer PdfWriter
	 * @param metaMap 文書情報
	 * @param encrypt 暗号化方法
	 * @param inPdf 入力PDFファイルデータ
	 * @return パスワード暗号化処理をしたPDFファイルデータ
	 */
	private void encrypt(PdfWriter writer, Map<String, String> metaMap) 
	throws IOException, IllegalPdfParameterException {
		String password = metaMap.get("password");
		String ownerPassword = metaMap.get("ownerpassword");
		if ((!StringUtils.isBlank(password) && StringUtils.isBlank(ownerPassword)) ||
				(!StringUtils.isBlank(ownerPassword) && StringUtils.isBlank(password))) {
			throw new IllegalPdfParameterException(
					"Both the password and the ownerpassword are required.");
		}
		int permissions = getPermissions(metaMap);
		writer.setEncryption(getBytes(password), getBytes(ownerPassword), 
				permissions, ReflexPdfConst.ENCRYPTION_TYPE);
	}
	
	/**
	 * パスワード暗号化を行うかどうか判定.
	 * @param metaMap PDF文書情報
	 * @return パスワード暗号化を行う場合true
	 */
	private boolean isEncryption(Map<String, String> metaMap) {
		if (metaMap.containsKey("password") || metaMap.containsKey("ownerpassword") ||
				metaMap.containsKey("allowprinting") || 
				metaMap.containsKey("allowmodifycontents") || 
				metaMap.containsKey("allowcopy") || 
				metaMap.containsKey("allowmodifyannotations") || 
				metaMap.containsKey("allowfillin") || 
				metaMap.containsKey("allowscreenreaders") || 
				metaMap.containsKey("allowassembly")) {
			return true;
		}
		return false;
	}
	
	/**
	 * 文書に関する制限を取得.
	 * 指定がない場合はデフォルト値を返す。
	 * @param metaMap PDF文書情報
	 * @return 文書に関する制限
	 */
	private int getPermissions(Map<String, String> metaMap) {
		int permissions = 0;
		// 印刷 (デフォルトtrue)
		String allowprinting = metaMap.get("allowprinting");
		if (!ReflexPdfConst.FALSE.equals(allowprinting)) {
			permissions |= PdfWriter.ALLOW_PRINTING;
		}
		// 文書の変更 (デフォルトtrue)
		String allowmodifycontents = metaMap.get("allowmodifycontents");
		if (!ReflexPdfConst.FALSE.equals(allowmodifycontents)) {
			permissions |= PdfWriter.ALLOW_MODIFY_CONTENTS;
		}
		// 内容のコピーと抽出 (デフォルトtrue)
		String allowcopy = metaMap.get("allowcopy");
		if (!ReflexPdfConst.FALSE.equals(allowcopy)) {
			permissions |= PdfWriter.ALLOW_COPY;
		}
		// 注釈、フォームフィールドの入力および署名 (デフォルトfalse)
		String allowmodifyannotations = metaMap.get("allowmodifyannotations");
		if (ReflexPdfConst.TRUE.equals(allowmodifyannotations)) {
			permissions |= PdfWriter.ALLOW_MODIFY_ANNOTATIONS;
		}
		// フォームフィールドの入力および署名 (デフォルトfalse)
		String allowfillin = metaMap.get("allowfillin");
		if (ReflexPdfConst.TRUE.equals(allowfillin)) {
			permissions |= PdfWriter.ALLOW_FILL_IN;
		}
		// アクセシビリティのための内容の抽出 (デフォルトtrue)
		String allowscreenreaders = metaMap.get("allowscreenreaders");
		if (!ReflexPdfConst.FALSE.equals(allowscreenreaders)) {
			permissions |= PdfWriter.ALLOW_SCREENREADERS;
		}
		// 文書アセンブリ (デフォルトfalse)
		String allowassembly = metaMap.get("allowassembly");
		if (ReflexPdfConst.TRUE.equals(allowassembly)) {
			permissions |= PdfWriter.ALLOW_ASSEMBLY;
		}
		return permissions;
	}
	
	/**
	 * 署名.
	 * @param inPdf PDFファイルデータ
	 * @param map 署名情報
	 * @param metaMap PDF文書情報 (暗号化の場合使用)
	 * @param reflexContext ReflexContext
	 * @return 署名を付加したPDFファイルデータ
	 */
	private byte[] signature(byte[] inPdf, Map<String, String> map, 
			Map<String, String> metaMap, ReflexContext reflexContext) 
	throws IOException, TaggingException {
		String signatureUri = map.get("signature");
		if (StringUtils.isBlank(signatureUri)) {
			throw new IllegalPdfParameterException("'signature' is required in '<meta content=\"signature\">' or '<div class=\"_signature\">' tag.");
		}
		String signaturepassword = map.get("signaturepassword");
		String signaturereason = map.get("signaturereason");
		String signaturelocation = map.get("signaturelocation");
		char[] passwordChars = null;
		if (!StringUtils.isBlank(signaturepassword)) {
			passwordChars = signaturepassword.toCharArray();
		}

		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		PdfStamper stp = null;
		try {
			KeyStore ks = KeyStore.getInstance(ReflexPdfConst.KEYSTORE_TYPE);
			ks.load(getSignature(signatureUri, reflexContext), passwordChars);
			String alias = (String)ks.aliases().nextElement();
			PrivateKey key = (PrivateKey)ks.getKey(alias, passwordChars);
			Certificate[] chain = ks.getCertificateChain(alias);
			
			PdfReader reader = null;
			if (isEncryption(metaMap)) {
				String ownerpasswordStr = getStyle(metaMap, "ownerpassword", null);
				reader = new PdfReader(inPdf, ownerpasswordStr.getBytes(ReflexPdfConst.ENCODING));
			} else {
				reader = new PdfReader(inPdf);
			}
			
			stp = PdfStamper.createSignature(reader, bout, '\0', 
					new File(getTempFilename()), true);
			
			PdfSignatureAppearance sap = stp.getSignatureAppearance();
			sap.setCrypto(key, chain, null, PdfSignatureAppearance.WINCER_SIGNED);
			
			if (!StringUtils.isBlank(signaturereason)) {
				sap.setReason(signaturereason);
			}
			if (!StringUtils.isBlank(signaturelocation)) {
				sap.setLocation(signaturelocation);
			}
	
			int page = getStyleInt(map, ReflexPdfConst.PAGE, 0);
			if (page > 0 && 
					map.containsKey("absolutex") && map.containsKey("absolutey") &&
					map.containsKey("width") && map.containsKey("height")) {
				float absolutex = getStyleFloat(map, "absolutex", 0);
				float absolutey = getStyleFloat(map, "absolutey", 0);
				float width = getStyleFloat(map, "width", 0);
				float height = getStyleFloat(map, "height", 0);
				
				// comment next line to have an invisible signature
				sap.setVisibleSignature(new Rectangle(absolutex, absolutey - height, 
						absolutex + width, absolutey), page, null);
				if (map.containsKey("image")) {
					// 印影
					Image image = Image.getInstance(getContent(map.get("image"), reflexContext));
					sap.setImage(image);
				}
			}
		} catch (GeneralSecurityException | BadPasswordException e) {
			// keystoreのパスワード不正
			StringBuilder sb = new StringBuilder();
			sb.append(e.getMessage());
			sb.append(". signature uri=");
			sb.append(signatureUri);
			throw new IllegalPdfParameterException(sb.toString());
		} catch (IOException e) {
			// 原因例外が GeneralSecurityException であれば入力エラー
			Throwable cause = e.getCause();
			if (cause != null && cause instanceof GeneralSecurityException) {
				StringBuilder sb = new StringBuilder();
				sb.append(e.getMessage());
				sb.append(". signature uri=");
				sb.append(signatureUri);
				throw new IllegalPdfParameterException(sb.toString());
			}
			throw e;
		} finally {
			if (stp != null) {
				try {
					stp.close();
				} catch (IOException e) {
					// Do nothing.
					if (logger.isDebugEnabled()) {
						logger.warn("[signature] PdfStamper close error.", e);
					}
				}
			}
		}

		return bout.toByteArray();
	}
	
	/**
	 * 署名かどうか.
	 * @param map PDF文書情報またはstyle情報
	 * @return 署名の場合true
	 */
	private boolean isSignature(Map<String, String> map) {
		if (map != null && map.containsKey("signature")) {
			return true;
		}
		return false;
	}
	
	/**
	 * 一時ファイルパスを取得.
	 * @return 一時ファイルパス
	 */
	private String getTempFilename() {
		String tempDir = TaggingEnvUtil.getSystemProp(ReflexPdfConst.PDF_TEMP_DIR, 
				ReflexPdfConst.PDF_TEMP_DIR_DEFAULT);
		String threadName = Thread.currentThread().getName();
		return tempDir + threadName + ".tmp";
	}

	/**
	 * PDFファイルにタイムスタンプを付加.
	 * @param inPdf 入力PDFファイルデータ
	 * @param metaMap PDF文書情報
	 * @return タイムスタンプを付加したPDFファイルデータ
	 */
	private byte[] timestamp(byte[] inPdf, Map<String, String> metaMap) 
	throws IOException {
		String url = metaMap.get("timestamp");
		String tsUsername = metaMap.get("timestampusername");
		String tsPassword = metaMap.get("timestamppassword");
		String ownerPassword = metaMap.get("ownerpassword");
		try {
			MessageDigest digest = MessageDigest.getInstance(ReflexPdfConst.HASH_ALGORITHM);
			TSAClient tsaClient = new TSAClient(new URL(url), tsUsername, tsPassword, digest);
			return timestamp(inPdf, ownerPassword, tsaClient);
		} catch (MalformedURLException e) {
			throw new IllegalPdfParameterException(e.getMessage());
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * PDFファイルにタイムスタンプを付加.
	 * @param inPdf 入力PDFファイルデータ
	 * @param ownerPassword 入力PDFファイルの権限パスワード
	 * @param tsaClient TSAClient
	 * @return タイムスタンプを付加したPDFファイルデータ
	 */
	private byte[] timestamp(byte[] inPdf, String ownerPassword, TSAClient tsaClient) 
	throws IOException {
		PDSignature signature = new PDSignature();
		signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
		signature.setSubFilter(COSName.getPDFName("ETSI.RFC3161"));
		signature.setSignDate(Calendar.getInstance());
		
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		try (PDDocument pdf = PDDocument.load(inPdf, ownerPassword)) {
			TimestampSignatureImpl sig = new TimestampSignatureImpl(tsaClient);
			pdf.addSignature(signature, sig);
			pdf.saveIncremental(bout);
		}
		return bout.toByteArray();
	}

	/**
	 * キーからデータを取得.
	 * @param uri URI
	 * @return データ
	 */
	private byte[] getContent(String uri, ReflexContext reflexContext) 
	throws IOException, TaggingException {
		return ReflexPdfUtil.getContent(uri, reflexContext);
	}

	/**
	 * キーからInputStreamを取得.
	 * @param uri URI
	 * @return InputStream
	 */
	private InputStream getSignature(String uri, ReflexContext reflexContext) 
	throws IOException, TaggingException {
		byte[] data = ReflexPdfUtil.getSignatureContent(uri, reflexContext);
		return new ByteArrayInputStream(data);
	}
	
	/**
	 * 内部保持タグの編集.
	 *  <div class="{class}">の場合、classの値を返す。その他はタグを返す。
	 * @param tag タグ
	 * @param element タグの属性
	 * @return 編集したタグ
	 */
	private String editTag(String tag, StartElement element) {
		if ("div".equals(tag)) {
			String attrClass = getAttribute(element, "class");
			if (!StringUtils.isBlank(attrClass) &&
					attrClass.startsWith("_")) {
				return attrClass;
			}
		}
		return tag;
	}
	
	/**
	 * テーブルを出力する.
	 * @param document Document 
	 * @param writer PdfWriter
	 * @param pdfTable PdfPTable
	 * @param styles style情報Map
	 */
	private void drawTable(Document document, PdfWriter writer, PdfPTable pdfTable, 
			Map<String, String> styles) 
	throws IllegalPdfParameterException {
		// absolutex, absolutey が指定されている場合絶対位置指定
		String absolutexStr = getStyle(styles, "absolutex", null);
		String absoluteyStr = getStyle(styles, "absolutey", null);
		if (!StringUtils.isBlank(absolutexStr)) {
			if (StringUtils.isBlank(absoluteyStr)) {
				throw new IllegalPdfParameterException("If 'absolutex' is specified, 'absolutey' is required. absolutex=" + absolutexStr);
			}
			// 絶対位置指定の場合、width必須
			if (StringUtils.isBlank(getStyle(styles, "width", null))) {
				throw new IllegalPdfParameterException("If 'absolutex' is specified, 'width' is required. absolutex=" + absolutexStr);
			}
			pdfTable.writeSelectedRows(0, -1, 
					getFloat(removePx(absolutexStr)), 
					getFloat(removePx(absoluteyStr)), 
					writer.getDirectContent());
			
		} else if (!StringUtils.isBlank(absoluteyStr)) {
			throw new IllegalPdfParameterException("If 'absolutey' is specified, 'absolutex' is required. absolutey=" + absoluteyStr);
		} else {
			document.add(pdfTable);
		}
	}
	
	/**
	 * 文字列のChunkオブジェクトを生成.
	 * @param tag タグ
	 * @param text 文字列
	 * @param font フォント
	 * @param styles style情報Map
	 * @param attributes 属性情報Map
	 * @return Chunkオブジェクト
	 */
	private Chunk createTextChunk(String tag, String text, Font font, 
			Map<String, String> styles, Map<String, String> attributes)
	throws IllegalPdfParameterException {
		Chunk chunk = new Chunk(text, font);
		String bgcolorStr = getStyle(styles, "bgcolor", null);
		if (!StringUtils.isBlank(bgcolorStr)) {
			Color bgcolor = getColor(bgcolorStr);
			chunk.setBackground(bgcolor);
		}
		float hscale = getStyleFloat(styles, "hscale", 0);
		if (hscale > 0) {
			chunk.setHorizontalScaling(hscale);
		}
		String spaceStr = getStyle(styles, "space", null);
		if (!StringUtils.isBlank(spaceStr)) {
			chunk.setCharacterSpacing(getFloat(spaceStr));
		}
		if ("a".equals(tag)) {
			String href = attributes.get("href");
			chunk.setAction(new PdfAction(href));
		}
		return chunk;
	}
	
	/**
	 * 文字列を出力する.
	 * @param document Document 
	 * @param writer PdfWriter
	 * @param phrase 文字列をセットしたPhrase
	 * @param text 文字列 (絶対位置指定の場合のテーブルの幅計算に使用)
	 * @param font フォント (絶対位置指定の場合のテーブルの幅計算に使用)
	 * @param styles style情報Map
	 */
	private void drawText(Document document, PdfWriter writer, Phrase phrase,
			String text, Font font, Map<String, String> styles) 
	throws IllegalPdfParameterException {
		String absolutexStr = getStyle(styles, "absolutex", null);
		String absoluteyStr = getStyle(styles, "absolutey", null);
		boolean isAbsolute = !StringUtils.isBlank(absolutexStr) || 
				!StringUtils.isBlank(absoluteyStr);
		if (isAbsolute) {
			// テーブルの幅計算
			int margin = 2;
			float width = text.length() * (font.getSize() + 0.4f) + margin;

			// 絶対位置指定
			PdfPTable tableText = new PdfPTable(1);
			PdfPCell cellText = new PdfPCell(phrase);
			cellText.setHorizontalAlignment(Element.ALIGN_LEFT);
			cellText.setVerticalAlignment(Element.ALIGN_TOP);
			cellText.setNoWrap(true);
			cellText.setBorderWidth(0);
			tableText.addCell(cellText);
			
			tableText.setTotalWidth(width);
			// 配置
			tableText.writeSelectedRows(0, -1, getFloat(absolutexStr), 
					getFloat(absoluteyStr), writer.getDirectContent());

		} else {
			// 順に配置
			document.add(phrase);
		}
	}
	
	/**
	 * 画像を出力する.
	 * @param document Document
	 * @param cellElement tableの場合、cellのelement
	 * @param element StartElement
	 * @param styles style情報Map
	 * @param reflexContext ReflexContext
	 */
	private void drawImage(Document document, Phrase cellElement, 
			StartElement element, Map<String, String> styles, ReflexContext reflexContext)
	throws IOException, TaggingException {
		String src = getAttribute(element, "src");
		if (StringUtils.isBlank(src)) {
			throw new IllegalPdfParameterException("'src' is required 'img' tag.");
		}
		Image img = Image.getInstance(getContent(src, reflexContext));
		drawImage(document, cellElement, element, styles, img);
	}
	
	/**
	 * 画像を出力する.
	 * @param document Document
	 * @param cellElement tableの場合、cellのelement
	 * @param element StartElement
	 * @param styles style情報Map
	 * @param img 画像
	 */
	private void drawImage(Document document, Phrase cellElement, 
			StartElement element, Map<String, String> styles, Image img)
	throws IOException, TaggingException {
		float width = getStyleInt(styles, "plainwidth", 0);
		if (width <= 0) {
			width = getFloat(getAttribute(element, "width"));
		}
		float height = getStyleInt(styles, "plainheight", 0);
		if (height <= 0) {
			height = getFloat(getAttribute(element, "height"));
		}
		// サイズ
		if (width > 0 && height > 0) {
			img.scaleAbsolute(width, height);
		} else if (width > 0) {
			img.scaleAbsoluteWidth(width);
		} else if (height > 0) {
			img.scaleAbsoluteHeight(height);
		}
		// 絶対位置
		String absolutexStr = getStyle(styles, "absolutex", null);
		String absoluteyStr = getStyle(styles, "absolutey", null);
		if (!StringUtils.isBlank(absolutexStr) || 
				!StringUtils.isBlank(absoluteyStr)) {
			float absolutex = getFloat(removePx(absolutexStr));
			float absolutey = getFloat(removePx(absoluteyStr));
			img.setAbsolutePosition(absolutex, absolutey);
		}
		if (cellElement != null) {
			cellElement.add(new Chunk(img, 0, 0));
		} else {
			document.add(img);
		}
	}

	/**
	 * 線の描画.
	 * @param writer PdfWriter
	 * @param styles style情報Map
	 */
	private void drawLine(PdfWriter writer, Map<String, String> styles) 
	throws IllegalPdfParameterException {
		PdfContentByte cb = writer.getDirectContent();
		// 線の設定
		setLineStyle(cb, styles);
		
		// 線の描画
		String x1Str = getStyle(styles, "x1", null);
		if (StringUtils.isBlank(x1Str)) {
			throw new IllegalPdfParameterException("'x1' is required for the line.");
		}
		String x2Str = getStyle(styles, "x2", null);
		if (StringUtils.isBlank(x2Str)) {
			throw new IllegalPdfParameterException("'x2' is required for the line.");
		}
		String y1Str = getStyle(styles, "y1", null);
		if (StringUtils.isBlank(y1Str)) {
			throw new IllegalPdfParameterException("'y1' is required for the line.");
		}
		String y2Str = getStyle(styles, "y2", null);
		if (StringUtils.isBlank(y2Str)) {
			throw new IllegalPdfParameterException("'y2' is required for the line.");
		}
		
		float x1 = getFloat(x1Str);
		float x2 = getFloat(x2Str);
		float y1 = getFloat(y1Str);
		float y2 = getFloat(y2Str);

		cb.moveTo(x1, y1);
		cb.lineTo(x2, y2);

		// stroke the lines
		cb.stroke();
	}
	
	/**
	 * 四角形の描画.
	 * @param writer PdfWriter
	 * @param styles style情報Map
	 */
	private void drawRectangle(PdfWriter writer, Map<String, String> styles) 
	throws IllegalPdfParameterException {
		PdfContentByte cb = writer.getDirectContent();
		// 線の設定
		setLineStyle(cb, styles);
		// 塗りつぶし設定
		boolean isFill = setFillStyle(cb, styles);

		// 四角形の描画
		String widthStr = getStyle(styles, "width", null);
		if (StringUtils.isBlank(widthStr)) {
			throw new IllegalPdfParameterException("'width' is required for the rectangle.");
		}
		String heightStr = getStyle(styles, "height", null);
		if (StringUtils.isBlank(heightStr)) {
			throw new IllegalPdfParameterException("'height' is required for the rectangle.");
		}
		float width = getFloat(widthStr);
		float height = getFloat(heightStr);
		float absolutex = getStyleFloat(styles, "absolutex", 0f);
		float absolutey = getStyleFloat(styles, "absolutey", 0f);
		cb.rectangle(absolutex, absolutey, width, height);
		if (isFill) {
			cb.fillStroke();
		} else {
			cb.stroke();
		}
	}
	
	/**
	 * 円の描画.
	 * @param writer PdfWriter
	 * @param styles style情報Map
	 */
	private void drawCircle(PdfWriter writer, Map<String, String> styles) 
	throws IllegalPdfParameterException {
		PdfContentByte cb = writer.getDirectContent();
		// 線の設定
		setLineStyle(cb, styles);
		// 塗りつぶし設定
		boolean isFill = setFillStyle(cb, styles);

		// 円の描画
		String radiusStr = getStyle(styles, "radius", null);
		if (StringUtils.isBlank(radiusStr)) {
			throw new IllegalPdfParameterException("'radius' is required for the circle.");
		}
		float radius = getFloat(radiusStr);
		float absolutex = getStyleFloat(styles, "absolutex", 0f);
		float absolutey = getStyleFloat(styles, "absolutey", 0f);
		cb.circle(absolutex, absolutey, radius);
		if (isFill) {
			cb.fillStroke();
		} else {
			cb.stroke();
		}
	}

	/**
	 * 罫線・四角形・円の描画の線について設定
	 * @param cb PdfContentByte
	 * @param styles style情報Map
	 */
	private void setLineStyle(PdfContentByte cb, Map<String, String> styles) 
	throws IllegalPdfParameterException {
		// 線の幅
		float linewidth = getStyleFloat(styles, "linewidth", 1f);
		cb.setLineWidth(linewidth);
		// 線の色
		// ※OpenPDFの機能上?、floatだとRGB各色はffか00しか表現できない。値があればffに変換される。
		//  このため引数intのメソッドを使用すること。
		String colorStr = getStyle(styles, "color", ReflexPdfConst.BORDER_COLOR_DEFAULT);
		Color color = Color.decode(colorStr);
		cb.setRGBColorStroke(color.getRed(), color.getGreen(), color.getBlue());
		// 破線
		float linedushoff = getStyleFloat(styles, "linedushoff", 0);
		float linedushon = getStyleFloat(styles, "linedushon", 1);
		cb.setLineDash(linedushon, linedushoff, 0);
	}

	/**
	 * 四角形・円の塗りつぶし色設定
	 * @param cb PdfContentByte
	 * @param styles style情報Map
	 * @return 塗りつぶしがある場合true
	 */
	private boolean setFillStyle(PdfContentByte cb, Map<String, String> styles) 
	throws IllegalPdfParameterException {
		cb.resetRGBColorFill();
		// 塗りつぶしの色
		// ※OpenPDFの機能上?、floatだとRGB各色はffか00しか表現できない。値があればffに変換される。
		//  このため引数intのメソッドを使用すること。
		String colorStr = getStyle(styles, "colorfill", null);
		if (!StringUtils.isBlank(colorStr)) {
			Color color = Color.decode(colorStr);
			cb.setRGBColorFill(color.getRed(), color.getGreen(), color.getBlue());
			return true;
		}
		return false;
	}
	
	/**
	 * バーコードを出力する.
	 * @param document Document
	 * @param writer PdfWriter
	 * @param cellElement tableの場合、cellのelement
	 * @param element StartElement
	 * @param styles style情報Map
	 * @param type バーコードの種類
	 * @param baseFontMap フォントマップ
	 */
	private void drawBarcode(Document document, PdfWriter writer, Phrase cellElement, 
			StartElement element, Map<String, String> styles, String type, 
			Map<String, BaseFont> baseFontMap) 
	throws IllegalPdfParameterException, IOException, TaggingException {
		String value = getStyle(styles, "value", null);
		if (StringUtils.isBlank(value)) {
			throw new IllegalPdfParameterException("'value' is required for the barcode.");
		}
		Barcode barcode = null;
		if ("_barcodeEAN".equals(type)) {
			// JAN(EAN、UPC)規格
			BarcodeEAN barcodeEAN = new BarcodeEAN();
			barcode = barcodeEAN;
			barcodeEAN.setCodeType(Barcode.EAN13);
		} else if ("_barcode39".equals(type)) {
			// code39規格
			Barcode39 barcode39 = new Barcode39();
			barcode = barcode39;
			barcode39.setStartStopText(getStyleBoolean(styles, "startstop", false));
			barcode39.setExtended(getStyleBoolean(styles, "extended", false));
		} else if ("_barcode128".equals(type)) {
			// code128規格
			Barcode128 barcode128 = new Barcode128();
			barcode = barcode128;
			int codeType = Barcode128.CODE128;
			String codeTypeStr = getStyle(styles, "codetype", null);
			if ("UCC".equalsIgnoreCase(codeTypeStr)) {
				codeType = Barcode128.CODE128_UCC;
			} else if ("RAW".equalsIgnoreCase(codeTypeStr)) {
				codeType = Barcode128.CODE128_RAW;
			}
			barcode128.setCodeType(codeType);
		} else {
			throw new IllegalPdfParameterException("Invalid barcode type : " + type);
		}
		barcode.setCode(value);
		barcode.setBarHeight(getStyleFloat(styles, "barheight", 30f));	// バーの高さ
		barcode.setX(getStyleFloat(styles, "barwidth", 0.75f));	// バーの幅
		barcode.setSize(getStyleFloat(styles, "size", 10f));	// 文字の大きさ
		String barcodeValueFontStr = getStyle(styles, "font", null);
		if (!StringUtils.isBlank(barcodeValueFontStr)) {
			if ("null".equalsIgnoreCase(barcodeValueFontStr)) {
				barcode.setFont(null);	// 文字表示なし
			} else if (baseFontMap.containsKey(barcodeValueFontStr)) {
				barcode.setFont(baseFontMap.get(barcodeValueFontStr));
			} else {
				throw new IllegalPdfParameterException("Invalid font : " + barcodeValueFontStr);
			}
		}

		PdfContentByte cb = writer.getDirectContent();
		Image imageBarcode = barcode.createImageWithBarcode(cb, null, null);
		drawImage(document, cellElement, element, styles, imageBarcode);
	}
	
	/**
	 * QRコードを出力する.
	 * @param document Document
	 * @param writer PdfWriter
	 * @param cellElement tableの場合、cellのelement
	 * @param element StartElement
	 * @param styles style情報Map
	 */
	private void drawQrcode(Document document, PdfWriter writer, Phrase cellElement, 
			StartElement element, Map<String, String> styles) 
	throws IllegalPdfParameterException, IOException, TaggingException {
		String value = getStyle(styles, "value", null);
		if (StringUtils.isBlank(value)) {
			throw new IllegalPdfParameterException("'value' is required for the qrcode.");
		}
		int width = getStyleInt(styles, "width", 100);
		int height = getStyleInt(styles, "height", 100);
		Map hints = new HashMap();
		if (containsKey(styles, "version")) {
			hints.put(EncodeHintType.QR_VERSION, getStyleInt(styles, "version", 1));
		}
		if (containsKey(styles, "errorcorrectionlevel")) {
			hints.put(EncodeHintType.ERROR_CORRECTION, getStyle(styles, "errorcorrectionlevel", "L"));
		}
		if (containsKey(styles, "margin")) {
			hints.put(EncodeHintType.MARGIN, getStyleInt(styles, "margin", 4));
		}
		if (containsKey(styles, "maskpattern")) {
			hints.put(EncodeHintType.QR_MASK_PATTERN, getStyleInt(styles, "maskpattern", 2));
		}
		try {
			QRCodeWriter qrWriter = new QRCodeWriter();
			BitMatrix bitMatrix = qrWriter.encode(value, BarcodeFormat.QR_CODE, 
					width, height, hints);
			ByteArrayOutputStream bout = new ByteArrayOutputStream();
			MatrixToImageWriter.writeToStream(bitMatrix, ReflexPdfConst.QRCODE_FORMAT, bout);
			
			Image img = Image.getInstance(bout.toByteArray());
			drawImage(document, cellElement, element, styles, img);

		} catch (WriterException e) {
			throw new IOException(e);
		}
	}

}
