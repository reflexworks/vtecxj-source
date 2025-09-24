package jp.reflexworks.taggingservice.util;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.zip.DataFormatException;

import jp.sourceforge.reflex.util.DeflateUtil;
import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.mapper.CipherUtil;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;

/**
 * Entry、Feedオブジェクトをバイト配列に変換します.
 * また、バイト配列をEntryまたはFeedに戻します。
 */
public class EntrySerializer {

	/**
	 * シリアライズ.
	 * 暗号化、MessagePack形式変換、圧縮を行います。
	 * @param env 環境変数
	 * @param mapper FeedTemplateMapper
	 * @param entry Entry
	 * @param cipherUtil 暗号化ツール。nullの場合は暗号化・復号化なし。
	 * @param deflateUtil 圧縮ツール。nullの場合は圧縮・解凍なし。
	 * @return Entryのバイト配列
	 */
	public static final byte[] serialize(
			FeedTemplateMapper mapper, EntryBase entry, 
			CipherUtil cipherUtil, DeflateUtil deflateUtil) 
	throws IOException {
		if (entry == null) {
			return null;
		}
		try {
			// 暗号化
			if (cipherUtil != null) {
				cipherUtil.encrypt(entry);
			}
	
			byte[] eVal = null;
			byte[] msgVal = mapper.toMessagePack(entry);
			if (deflateUtil == null) {
				eVal = msgVal;
			} else {
				eVal = deflateUtil.deflate(msgVal);
			}
	
			// 復号化 (元に戻す)
			if (cipherUtil != null) {
				cipherUtil.decrypt(entry);
			}
	
			return eVal;
			
		} catch (GeneralSecurityException e) {
			throw new IOException(e);
		} 
	}
	
	/**
	 * シリアライズ.
	 * 暗号化、MessagePack形式変換、圧縮を行います。
	 * @param env 環境変数
	 * @param mapper FeedTemplateMapper
	 * @param feed Feed
	 * @param cipherUtil 暗号化ツール。nullの場合は暗号化・復号化なし。
	 * @param deflateUtil 圧縮ツール。nullの場合は圧縮・解凍なし。
	 * @return Feedのバイト配列
	 */
	public static final byte[] serialize(
			FeedTemplateMapper mapper, FeedBase feed, 
			CipherUtil cipherUtil, DeflateUtil deflateUtil) 
	throws IOException {
		if (feed == null) {
			return null;
		}
		try {
			// 暗号化
			if (cipherUtil != null) {
				cipherUtil.encrypt(feed);
			}
	
			byte[] fVal = null;
			byte[] msgVal = mapper.toMessagePack(feed);
			if (deflateUtil == null) {
				fVal = msgVal;
			} else {
				fVal = deflateUtil.deflate(msgVal);
			}
			
			// 復号化 (元に戻す)
			if (cipherUtil != null) {
				cipherUtil.decrypt(feed);
			}
			
			return fVal;
			
		} catch (GeneralSecurityException e) {
			throw new IOException(e);
		} 
	}

	/**
	 * Entryオブジェクトにデシリアライズ.
	 * @param env 環境変数
	 * @param mapper FeedTemplateMapper
	 * @param eVal Entryバイト配列
	 * @param cipherUtil 暗号化ツール。nullの場合は暗号化・復号化なし。
	 * @param deflateUtil 圧縮ツール。nullの場合は圧縮・解凍なし。
	 * @return Entry
	 */
	public static final EntryBase deserializeEntry(
			FeedTemplateMapper mapper, byte[] eVal, 
			CipherUtil cipherUtil, DeflateUtil deflateUtil) 
	throws IOException {
		if (eVal == null || eVal.length == 0) {
			return null;
		}
		
		EntryBase entry = null;
		try {
			byte[] msgVal = null;
			if (deflateUtil == null) {
				msgVal = eVal;
			} else {
				msgVal = deflateUtil.inflate(eVal);
			}
			entry = (EntryBase) mapper.fromMessagePack(msgVal, false);	// Entry

			// 復号化
			if (cipherUtil != null) {
				cipherUtil.decrypt(entry);
			}

		} catch (DataFormatException e) {
			throw new IOException(e);
		} catch (ClassNotFoundException e) {
			throw new IOException(e);
		} catch (GeneralSecurityException e) {
			throw new IOException(e);
		}
		return entry;
	}
	
	/**
	 * Feedオブジェクトにデシリアライズ.
	 * @param env 環境変数
	 * @param mapper FeedTemplateMapper
	 * @param entry Entry
	 * @param cipherUtil 暗号化ツール。nullの場合は暗号化・復号化なし。
	 * @param deflateUtil 圧縮ツール。nullの場合は圧縮・解凍なし。
	 * @return Feed
	 */
	public static final FeedBase deserializeFeed(
			FeedTemplateMapper mapper, byte[] fVal, 
			CipherUtil cipherUtil, DeflateUtil deflateUtil) 
	throws IOException {
		if (fVal == null || fVal.length == 0) {
			return null;
		}
		
		FeedBase feed = null;
		try {
			byte[] msgVal = null;
			if (deflateUtil == null) {
				msgVal = fVal;
			} else {
				msgVal = deflateUtil.inflate(fVal);
			}
			feed = (FeedBase) mapper.fromMessagePack(msgVal, true);	// Feed

			// 復号化
			if (cipherUtil != null) {
				cipherUtil.decrypt(feed);
			}

		} catch (DataFormatException e) {
			throw new IOException(e);
		} catch (ClassNotFoundException e) {
			throw new IOException(e);
		} catch (GeneralSecurityException e) {
			throw new IOException(e);
		}
		return feed;
	}
}
