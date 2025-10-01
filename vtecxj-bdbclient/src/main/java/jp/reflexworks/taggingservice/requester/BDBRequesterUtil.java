package jp.reflexworks.taggingservice.requester;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.mapper.CipherUtil;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.servlet.HttpStatus;
import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.servlet.ReflexServletUtil;
import jp.reflexworks.servlet.util.UrlUtil;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.bdbclient.BDBClientConst;
import jp.reflexworks.taggingservice.bdbclient.BDBClientUtil;
import jp.reflexworks.taggingservice.bdbclient.ConnConsistentHashMap;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.EntryDuplicatedException;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.NoExistingEntryException;
import jp.reflexworks.taggingservice.exception.NotInServiceException;
import jp.reflexworks.taggingservice.exception.OptimisticLockingException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.EntryMultipleInfo;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBIndexType;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBServerType;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.EntrySerializer;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.NamespaceUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.ConsistentHash;
import jp.sourceforge.reflex.util.DeflateUtil;
import jp.sourceforge.reflex.util.FileUtil;
import jp.sourceforge.reflex.util.HashFunction;
import jp.sourceforge.reflex.util.MD5;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * リクエスタで使用するユーティリティ
 */
public class BDBRequesterUtil {

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(BDBRequesterUtil.class);

	/**
	 * BDBリクエストのタイムアウト(ミリ秒)を取得.
	 * @return BDBリクエストのタイムアウト(ミリ秒)
	 */
	public static int getBDBRequestTimeoutMillis() {
		return TaggingEnvUtil.getSystemPropInt(BDBClientConst.BDBREQUEST_TIMEOUT_MILLIS,
				BDBClientConst.BDBREQUEST_TIMEOUT_MILLIS_DEFAULT);
	}

	/**
	 * BDBリクエストのアクセス失敗時リトライ総数を取得.
	 * @return BDBリクエストのアクセス失敗時リトライ総数
	 */
	public static int getBDBRequestRetryCount() {
		return TaggingEnvUtil.getSystemPropInt(BDBClientConst.BDBREQUEST_RETRY_COUNT,
				BDBClientConst.BDBREQUEST_RETRY_COUNT_DEFAULT);
	}

	/**
	 * BDBリクエストのアクセス失敗時リトライ時のスリープ時間を取得.
	 * @return BDBリクエストのアクセス失敗時のスリープ時間(ミリ秒)
	 */
	public static int getBDBRequestRetryWaitmillis() {
		return TaggingEnvUtil.getSystemPropInt(BDBClientConst.BDBREQUEST_RETRY_WAITMILLIS,
				BDBClientConst.BDBREQUEST_RETRY_WAITMILLIS_DEFAULT);
	}

	/**
	 * サービスのBDB情報格納URIを取得.
	 * /_bdb/service/{サービス名}
	 * @param serviceName サービス名
	 * @return サービスのBDB情報格納URI
	 */
	public static String getBDBServiceUri(String serviceName) {
		StringBuilder sb = new StringBuilder();
		sb.append(Constants.URI_BDB_SERVICE);
		sb.append("/");
		sb.append(serviceName);
		return sb.toString();
	}

	/**
	 * サービスの全文検索インデックス分散用フォルダURIを取得.
	 * /_bdb/service/{サービス名}/ftserver
	 * @param serviceName サービス名
	 * @return サービスの全文検索インデックス分散用フォルダURI
	 */
	public static String getBDBServiceFtserverUri(String serviceName) {
		StringBuilder sb = new StringBuilder();
		sb.append(getBDBServiceUri(serviceName));
		sb.append(Constants.URI_FTSERVER);
		return sb.toString();
	}

	/**
	 * サービスのインデックス分散用フォルダURIを取得.
	 * /_bdb/service/{サービス名}/idxserver
	 * @param serviceName サービス名
	 * @return サービスのインデックス分散用フォルダURI
	 */
	public static String getBDBServiceIdxserverUri(String serviceName) {
		StringBuilder sb = new StringBuilder();
		sb.append(getBDBServiceUri(serviceName));
		sb.append(Constants.URI_IDXSERVER);
		return sb.toString();
	}

	/**
	 * BDBサーバ情報格納URIを取得.
	 * /_bdb/server/{BDBサーバ名}
	 * @param bdbServer BDBサーバ名
	 * @return BDBサーバ情報格納URI
	 */
	public static String getBDBServerUri(String bdbServer) {
		StringBuilder sb = new StringBuilder();
		sb.append(Constants.URI_BDB_SERVER);
		sb.append("/");
		sb.append(bdbServer);
		return sb.toString();
	}

	/**
	 * BDBサーバリクエスト時のリクエストヘッダを編集.
	 * @param serviceName サービス名
	 * @param setOutput リクエストデータを設定する場合true
	 * @param namespace 名前空間
	 * @param isPutEntry Entry登録更新の場合true
	 * @param sid SID
	 * @param distkeyItem DISTKEY項目
	 * @param distkeyValue DISTKEYの値
	 * @return BDBサーバリクエスト時のリクエストヘッダ
	 */
	public static Map<String, String> getRequestHeader(String serviceName,
			boolean setOutput, boolean isPutEntry, String namespace, String sid,
			String distkeyItem, String distkeyValue) {
		Map<String, String> headers = new HashMap<String, String>();
		headers.put(Constants.HEADER_SERVICENAME, serviceName);
		headers.put(ReflexServletConst.X_REQUESTED_WITH,
				BDBClientConst.X_REQUESTED_WITH_VALUE);
		if (setOutput) {
			// Entry登録更新の場合は、InflateせずDeflate圧縮状態のまま登録してもらいたいので
			// 「Content-Encoding: Deflate」ヘッダをつけない。
			if (!isPutEntry) {
				headers.put(ReflexServletConst.HEADER_CONTENT_ENCODING,
						ReflexServletConst.HEADER_VALUE_DEFLATE);
			}
			headers.put(ReflexServletConst.HEADER_CONTENT_TYPE,
					ReflexServletConst.CONTENT_TYPE_MESSAGEPACK);
		}
		if (!StringUtils.isBlank(namespace)) {
			headers.put(Constants.HEADER_NAMESPACE, namespace);
		}
		if (!StringUtils.isBlank(sid)) {
			headers.put(Constants.HEADER_SID, sid);
		}
		if (!StringUtils.isBlank(distkeyItem)) {
			headers.put(Constants.HEADER_DISTKEY_ITEM, distkeyItem);
			// DISTKEY値はURLエンコードを行う
			headers.put(Constants.HEADER_DISTKEY_VALUE,
					UrlUtil.urlEncode(StringUtils.null2blank(distkeyValue)));
		}
		// バッチの場合リクエストヘッダにバッチ設定を付ける
		Set<String> bdbRequestHeaders = TaggingEnvUtil.getSystemPropSet(
				BDBClientConst.BDBREQUEST_HEADER_PREFIX);
		if (bdbRequestHeaders != null) {
			for (String bdbRequestHeader : bdbRequestHeaders) {
				String key = null;
				String val = null;
				int idx = bdbRequestHeader.indexOf(BDBClientConst.BDBREQUEST_HEADER_DELIMITER);
				if (idx == -1) {
					key = bdbRequestHeader;
				} else {
					key = bdbRequestHeader.substring(0, idx);
					val = bdbRequestHeader.substring(idx + 1);
				}
				if (!StringUtils.isBlank(key)) {
					headers.put(key, val);
				}
			}
		}
		return headers;
	}

	/**
	 * レスポンスデータのストリームからオブジェクト(FeedまたはEntry、またはEntryリスト)を取得する。
	 * MessagePack形式で受信
	 * @param urlStr URL (エラー時のログ出力用)
	 * @param in InputStream
	 * @param contentType Content-Typeヘッダ
	 * @param serviceName サービス名
	 * @param deflateUtil DeflateUtil
	 * @param isFeed オブジェクトがFeedの場合true
	 * @param isDecrypt 暗号項目の復号化をする場合true
	 * @return FeedまたはEntry
	 */
	public static Object getObject(String urlStr, InputStream in, String contentType,
			String serviceName, DeflateUtil deflateUtil, boolean isFeed, boolean isDecrypt)
	throws IOException {
		return getObject(urlStr, in, contentType, serviceName, deflateUtil, isFeed, null, isDecrypt);
	}

	/**
	 * レスポンスデータのストリームからオブジェクト(FeedまたはEntry、またはEntryリスト)を取得する。
	 * MessagePack形式で受信
	 * @param urlStr URL (エラー時のログ出力用)
	 * @param in InputStream
	 * @param contentType Content-Typeヘッダ
	 * @param serviceName サービス名
	 * @param deflateUtil DeflateUtil
	 * @param isFeed オブジェクトがFeedの場合true
	 * @param entryLengthList Entry複数検索の場合、バイト配列長リスト
	 * @param isDecrypt 暗号項目の復号化をする場合true
	 * @return FeedまたはEntry
	 */
	public static Object getObject(String urlStr, InputStream in, String contentType,
			String serviceName, DeflateUtil deflateUtil, boolean isFeed,
			List<Integer> entryLengthList, boolean isDecrypt)
	throws IOException {
		if (in == null) {
			return null;
		}
		if (contentType.startsWith(ReflexServletConst.CONTENT_TYPE_TEXT)) {
			return getTextFeed(urlStr, in, serviceName);
		} else if (isFeed) {
			return getFeed(urlStr, in, contentType, serviceName);
		} else if (entryLengthList != null && !entryLengthList.isEmpty()) {
			return getEntryList(in, entryLengthList, serviceName, deflateUtil, isDecrypt);
		} else {
			return getEntry(in, serviceName, deflateUtil, isDecrypt);
		}
	}
	
	/**
	 * レスポンスデータのテキスト情報をFeedのtitleにセットして変換
	 * @param urlStr リクエストURL
	 * @param in レスポンスデータのストリーム
	 * @param serviceName サービス名
	 * @return レスポンステキストをセットしたFeed
	 */
	public static FeedBase getTextFeed(String urlStr, InputStream in, String serviceName)
	throws IOException {
		if (logger.isInfoEnabled()) {
			logger.info("[getTextFeed] start.");
		}
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(in, Constants.ENCODING));
			String text = ReflexServletUtil.getBody(reader);
			FeedBase textFeed = TaggingEntryUtil.createFeed(serviceName);
			textFeed.title = text;
			return textFeed;

		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					logger.warn("[getTextFeed] close error. [URL]" + urlStr, e);
				}
			}
		}
	}

	/**
	 * レスポンスデータのストリームからFeedを取得する。
	 * MessagePack形式で受信
	 * @param urlStr URL (エラー時のログ出力用)
	 * @param in InputStream
	 * @param contentType Content-Typeヘッダ
	 * @param serviceName サービス名
	 * @return Feed
	 */
	public static FeedBase getFeed(String urlStr, InputStream in, String contentType,
			String serviceName)
	throws IOException {
		if (in == null) {
			return null;
		}
		FeedTemplateMapper mapper = TaggingEnvUtil.getResourceMapper(serviceName);
		FeedBase ret = null;
		if (contentType != null && contentType.startsWith(ReflexServletConst.CONTENT_TYPE_JSON)) {
			// JSON
			BufferedReader reader = null;
			try {
				reader = new BufferedReader(new InputStreamReader(in, Constants.ENCODING));
				ret = (FeedBase)mapper.fromJSON(reader);

			} finally {
				if (reader != null) {
					try {
						reader.close();
					} catch (IOException e) {
						logger.warn("[getObject] close error. [URL]" + urlStr, e);
					}
				}
			}

		} else {
			// MessagePack
			BufferedInputStream bin = null;
			try {
				bin = new BufferedInputStream(in);
				ret = (FeedBase)mapper.fromMessagePack(bin, true);

			} catch (EOFException e) {
				// レスポンスデータなし
				if (logger.isDebugEnabled()) {
					StringBuilder sb = new StringBuilder();
					sb.append("[getFeed] EOFException: ");
					sb.append(e.getMessage());
					sb.append(" [URL]");
					sb.append(urlStr);
					logger.debug(sb.toString());
				}
				return null;
			} catch (ClassNotFoundException e) {
				throw new IOException(e);
			} finally {
				if (bin != null) {
					try {
						bin.close();
					} catch (IOException e) {
						logger.warn("[getFeed] close error. [URL]" + urlStr, e);
					}
				}
			}
		}
		return ret;
	}

	/**
	 * レスポンスデータのストリームからEntryを取得する。
	 * MessagePack形式で受信。
	 * Entryオブジェクト形式で取得する場合、必ず復号化する。
	 * @param in InputStream
	 * @param contentType Content-Typeヘッダ
	 * @param serviceName サービス名
	 * @param deflateUtil DeflateUtil
	 * @param isDecrypt 暗号項目の復号化を行う場合true
	 * @return Entry
	 */
	public static EntryBase getEntry(InputStream in, String serviceName,
			DeflateUtil deflateUtil, boolean isDecrypt)
	throws IOException {
		if (in == null) {
			return null;
		}
		// レスポンスされるEntryはDeflate圧縮されたMessagePack。項目暗号化されている。
		FeedTemplateMapper mapper = TaggingEnvUtil.getResourceMapper(serviceName);
		CipherUtil cipherUtil = null;
		if (isDecrypt) {
			cipherUtil = new CipherUtil();
		}

		// ストリームからバイト配列を取得
		byte[] eVal = null;
		try {
			eVal = FileUtil.readInputStream(in);
		} finally {
			in.close();
		}

		return getEntry(eVal, mapper, deflateUtil, cipherUtil);
	}

	/**
	 * レスポンスデータのストリームからEntryを取得する。
	 * MessagePack形式で受信。
	 * Entryオブジェクト形式で取得する場合、必ず復号化する。
	 * @param eVal Entryバイト配列
	 * @param mapper FeedTemplateMapper
	 * @param deflateUtil DeflateUtil
	 * @param cipherUtil CipherUtil
	 * @return Entry
	 */
	public static EntryBase getEntry(byte[] eVal, FeedTemplateMapper mapper,
			DeflateUtil deflateUtil, CipherUtil cipherUtil)
	throws IOException {
		if (eVal == null || eVal.length == 0) {
			return null;
		}
		// Deflate解凍、デシリアライズ、暗号項目復号化
		if (eVal != null && eVal.length > 0) {
			return EntrySerializer.deserializeEntry(mapper, eVal, cipherUtil, deflateUtil);
		} else {
			return null;
		}
	}

	/**
	 * レスポンスデータのストリームからEntryを取得する。
	 * MessagePack形式で受信。
	 * Entryオブジェクト形式で取得する場合、必ず復号化する。
	 * @param in InputStream
	 * @param contentType Content-Typeヘッダ
	 * @param serviceName サービス名
	 * @param deflateUtil DeflateUtil
	 * @param isDecrypt 暗号項目の復号化を行う場合true
	 * @return Entry
	 */
	public static List<EntryBase> getEntryList(InputStream in, List<Integer> entryLengthList,
			String serviceName, DeflateUtil deflateUtil, boolean isDecrypt)
	throws IOException {
		if (in == null) {
			return null;
		}
		// レスポンスされるEntryはDeflate圧縮されたMessagePack。項目暗号化されている。
		FeedTemplateMapper mapper = TaggingEnvUtil.getResourceMapper(serviceName);
		CipherUtil cipherUtil = null;
		if (isDecrypt) {
			cipherUtil = new CipherUtil();
		}

		long startTime = 0;
		if (logger.isTraceEnabled()) {
			logger.debug("[getEntryList] readInputStream start.");
			startTime = new Date().getTime();
		}
		// ストリームからバイト配列を取得
		byte[] allData = null;
		try {
			allData = FileUtil.readInputStream(in);
		} finally {
			in.close();
		}
		if (logger.isTraceEnabled()) {
			logger.debug("[getEntryList] readInputStream end." + LogUtil.getElapsedTimeLog(startTime));
			startTime = new Date().getTime();
		}

		if (logger.isTraceEnabled()) {
			logger.debug("[getEntryList] System.arraycopy and deserialize entry start.");
			startTime = new Date().getTime();
		}

		List<EntryBase> entries = new ArrayList<>(entryLengthList.size());
		int srcPos = 0;
		for (int len : entryLengthList) {
			if (len > 0) {
				byte[] eVal = new byte[len];
				System.arraycopy(allData, srcPos, eVal, 0, len);
				srcPos += len;
				EntryBase entry = getEntry(eVal, mapper, deflateUtil, cipherUtil);
				entries.add(entry);
			} else {
				entries.add(null);
			}
		}

		if (logger.isTraceEnabled()) {
			logger.debug("[getEntryList] System.arraycopy and deserialize entry end." + LogUtil.getElapsedTimeLog(startTime));
			startTime = new Date().getTime();
		}

		return entries;
	}

	/**
	 * Entryバイト配列長をリストに変換する.
	 * @param entryLengthStr Entryバイト配列長文字列
	 * @return Entryバイト配列長をリストに変換したもの
	 */
	public static List<Integer> getEntryLengthList(String entryLengthStr) {
		if (StringUtils.isBlank(entryLengthStr)) {
			return null;
		}
		String[] tmpLengthsStr = entryLengthStr.split(Constants.HEADER_VALUE_SEPARATOR);
		List<Integer> lengthList = new ArrayList<>(tmpLengthsStr.length);
		for (String tmpLengthStr : tmpLengthsStr) {
			int len = 0;
			try {
				len = Integer.parseInt(tmpLengthStr);
			} catch (NumberFormatException e) {
				StringBuilder sb = new StringBuilder();
				sb.append("[getEntryLengthList] NumberFormatException: ");
				sb.append(e.getMessage());
				sb.append(" value=");
				sb.append(tmpLengthStr);
				logger.warn(sb.toString());
			}
			lengthList.add(len);
		}
		return lengthList;
	}

	/**
	 * FeedをMessagePack形式にシリアライズ
	 * @param feed Feed
	 * @param mapper FeedTemplateMapper
	 * @param deflateUtil Deflate圧縮解凍ユーティリティ
	 * @param isEncrypt 指定された項目を暗号化する場合true
	 * @return バイト配列
	 */
	public static byte[] toRequestData(FeedBase feed, FeedTemplateMapper mapper,
			DeflateUtil deflateUtil)
	throws IOException {
		return EntrySerializer.serialize(mapper, feed, null, deflateUtil);
	}

	/**
	 * EntryをMessagePack形式にシリアライズ
	 * @param entry Entry
	 * @param mapper FeedTemplateMapper
	 * @param deflateUtil Deflate圧縮解凍ユーティリティ
	 * @param isEncrypt 指定された項目を暗号化する場合true
	 * @return バイト配列
	 */
	public static byte[] toRequestData(EntryBase entry, FeedTemplateMapper mapper,
			DeflateUtil deflateUtil, boolean isEncrypt)
	throws IOException {
		CipherUtil cipherUtil = null;
		if (isEncrypt) {
			cipherUtil = new CipherUtil();
		}
		return EntrySerializer.serialize(mapper, entry, cipherUtil, deflateUtil);
	}

	/**
	 * EntryリストをEntryサーバに送信するための情報を編集.
	 * @param entries Entryリスト
	 * @param mapper FeedTemplateMapper
	 * @param deflateUtil Deflate圧縮解凍ユーティリティ
	 * @param isEncrypt 指定された項目を暗号化する場合true
	 * @return EntryリストをEntryサーバに送信するための情報
	 */
	public static EntryMultipleInfo toRequestDataMultiple(List<EntryBase> entries,
			FeedTemplateMapper mapper, DeflateUtil deflateUtil, boolean isEncrypt)
	throws IOException {
		CipherUtil cipherUtil = null;
		if (isEncrypt) {
			cipherUtil = new CipherUtil();
		}
		List<byte[]> dataList = new ArrayList<>();
		for (EntryBase entry : entries) {
			byte[] data = EntrySerializer.serialize(mapper, entry, cipherUtil, deflateUtil);
			dataList.add(data);
		}

		// IDリスト文字列
		List<String> idList = new ArrayList<>();
		for (EntryBase entry : entries) {
			String id = null;
			if (entry != null) {
				id = entry.id;
			}
			idList.add(id);
		}

		// 追加ヘッダ情報
		Map<String, String> additionalHeaders = BDBRequesterUtil.getEntryMultipleHeader(
				idList, dataList);

		// バイト配列を結合
		int size = 0;
		for (byte[] data : dataList) {
			if (data != null) {
				size += data.length;
			}
		}
		byte[] allData = new byte[size];
		int destPos = 0;
		for (byte[] data : dataList) {
			if (data != null && data.length > 0) {
				System.arraycopy(data, 0, allData, destPos, data.length);
				destPos += data.length;
			}
		}

		return new EntryMultipleInfo(allData, additionalHeaders);
	}

	/**
	 * 複数Entry処理のヘッダを取得.
	 * @param ids IDリスト
	 * @param dataList Entryバイト配列リスト
	 * @return 複数Entry処理ヘッダ
	 */
	public static Map<String, String> getEntryMultipleHeader(List<String> ids, List<byte[]> dataList) {
		if (ids == null || ids.isEmpty()) {
			return null;
		}
		Map<String, String> headers = new HashMap<>();
		StringBuilder sb = new StringBuilder();
		boolean isFirst = true;
		for (String id : ids) {
			if (isFirst) {
				isFirst = false;
			} else {
				sb.append(Constants.HEADER_VALUE_SEPARATOR);
			}
			sb.append(id);
		}
		headers.put(Constants.HEADER_ID, UrlUtil.urlEncode(sb.toString()));

		if (dataList != null && !dataList.isEmpty()) {
			sb = new StringBuilder();
			isFirst = true;
			for (byte[] data : dataList) {
				if (isFirst) {
					isFirst = false;
				} else {
					sb.append(Constants.HEADER_VALUE_SEPARATOR);
				}
				int len = 0;
				if (data != null) {
					len = data.length;
				}
				sb.append(len);
			}
			headers.put(Constants.HEADER_ENTRY_LENGTH, sb.toString());
		}
		return headers;
	}

	/**
	 * エラー判定.
	 * エラーに合った例外をスローする。
	 * @param url URL (ログ用)
	 * @param method メソッド (ログ用)
	 * @param status ステータス
	 * @param errorFeed エラーメッセージFeed
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 */
	public static void doException(String url, String method, int status,
			FeedBase errorFeed, String serviceName, RequestInfo requestInfo)
	throws IOException, TaggingException {
		String errMsg = null;
		if (errorFeed != null) {
			errMsg = errorFeed.title;
		}
		if (status != HttpStatus.SC_NO_CONTENT) {
		}
		errMsg = StringUtils.null2blank(errMsg);

		if (status == HttpStatus.SC_BAD_REQUEST) {
			throw new IllegalParameterException(errMsg);
		} else if (status == HttpStatus.SC_FAILED_DEPENDENCY) {
			throw new NotInServiceException(errMsg);
		} else if (status == HttpStatus.SC_CONFLICT) {
			if (errMsg != null && errMsg.startsWith(OptimisticLockingException.MSG)) {
				throw new OptimisticLockingException(errMsg);
			} else {
				throw new EntryDuplicatedException(errMsg);
			}
		} else if (status == HttpStatus.SC_NOT_FOUND) {
			throw new NoExistingEntryException(errMsg);
		} else if (status == HttpStatus.SC_METHOD_NOT_ALLOWED) {
			// このエラーが発生するのは内部エラーが原因
			warnLog(url, method, status, errMsg, serviceName, requestInfo);
			throw new IllegalStateException(errMsg);
		} else if (status == HttpStatus.SC_INTERNAL_SERVER_ERROR) {
			warnLog(url, method, status, errMsg, serviceName, requestInfo);
			throw new IOException(errMsg);
		} else {
			warnLog(url, method, status, errMsg, serviceName, requestInfo);
			throw new IOException(errMsg);	// 
		}
	}
	
	/**
	 * 内部エラーの場合のログ出力
	 * @param url URL (ログ用)
	 * @param method メソッド (ログ用)
	 * @param status ステータス
	 * @param errMsg エラーメッセージ
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 */
	private static void warnLog(String url, String method, int status,
			String errMsg, String serviceName, RequestInfo requestInfo) {
		StringBuilder sb = new StringBuilder();
		sb.append(LogUtil.getRequestInfoStr(requestInfo));
		sb.append("serviceName=");
		sb.append(serviceName);
		sb.append(" [doException] ");
		sb.append(method);
		sb.append(" ");
		sb.append(url);
		sb.append(" , status=");
		sb.append(status);
		sb.append(", errMsg: ");
		sb.append(errMsg);
		logger.warn(sb.toString());
	}

	/**
	 * レスポンスヘッダを取得
	 * @param http HttpURLConnection
	 * @return レスポンスヘッダを
	 */
	public static Map<String, String> getResponseHeaders(HttpURLConnection http) {
		Map<String, List<String>> headerFields = http.getHeaderFields();
		Map<String, String> respHeaders = new HashMap<String, String>();
		if (headerFields != null) {
			for (Map.Entry<String, List<String>> mapEntry : headerFields.entrySet()) {
				String key = mapEntry.getKey();
				String val = null;
				List<String> values = mapEntry.getValue();
				if (values != null) {
					int size = values.size();
					StringBuilder sb = new StringBuilder();
					for (int i = 0; i < size; i++) {
						if (i > 0) {
							sb.append(",");
						}
						sb.append(values.get(i));
					}
					val = sb.toString();
				}
				if (val == null) {
					val = "";
				}
				respHeaders.put(key, val);
			}
		}
		return respHeaders;
	}

	/**
	 * リクエスト開始ログを編集
	 * @param serviceName サービス名
	 * @param method メソッド
	 * @param url URL
	 * @param headers リクエストヘッダ
	 * @return ログ文字列
	 */
	static String getStartLog(String serviceName, String method, String url,
			Map<String, String> headers) {
		StringBuilder sb = new StringBuilder();
		sb.append("[request] start. ");
		sb.append(method);
		sb.append(" ");
		sb.append(url);
		sb.append(" : svc=");
		sb.append(serviceName);
		if (headers != null && headers.containsKey(Constants.HEADER_ID)) {
			sb.append(" : ids=");
			sb.append(UrlUtil.urlDecode(headers.get(Constants.HEADER_ID)));
		}
		return sb.toString();
	}

	/**
	 * リクエスト中ログを編集
	 * @param serviceName サービス名
	 * @param method メソッド
	 * @param url URL
	 * @param headers リクエストヘッダ
	 * @param status ステータス
	 * @return ログ文字列
	 */
	static String prepareLog(String serviceName, String method, String url,
			Map<String, String> headers, long startTime) {
		StringBuilder sb = new StringBuilder();
		sb.append("[request] prepare. ");
		sb.append(method);
		sb.append(" ");
		sb.append(url);
		sb.append(" : svc=");
		sb.append(serviceName);
		sb.append(LogUtil.getElapsedTimeLog(startTime));
		return sb.toString();
	}

	/**
	 * レスポンス受信ログを編集
	 * @param serviceName サービス名
	 * @param method メソッド
	 * @param url URL
	 * @param headers リクエストヘッダ
	 * @param status ステータス
	 * @return ログ文字列
	 */
	static String getResponseLog(String serviceName, String method, String url,
			Map<String, String> headers, int status, long startTime) {
		StringBuilder sb = new StringBuilder();
		sb.append("[request] get response. ");
		sb.append(method);
		sb.append(" ");
		sb.append(url);
		sb.append(" : status=");
		sb.append(status);
		sb.append(", svc=");
		sb.append(serviceName);
		sb.append(LogUtil.getElapsedTimeLog(startTime));
		return sb.toString();
	}

	/**
	 * リクエスト終了ログを編集
	 * @param serviceName サービス名
	 * @param method メソッド
	 * @param url URL
	 * @param headers リクエストヘッダ
	 * @param status ステータス
	 * @return ログ文字列
	 */
	static String getEndLog(String serviceName, String method, String url,
			Map<String, String> headers, int status, long startTime) {
		StringBuilder sb = new StringBuilder();
		sb.append("[request] end. ");
		sb.append(method);
		sb.append(" ");
		sb.append(url);
		sb.append(" : status=");
		sb.append(status);
		sb.append(", svc=");
		sb.append(serviceName);
		sb.append(LogUtil.getElapsedTimeLog(startTime));
		return sb.toString();
	}

	/**
	 * ManifestサーバのURLを取得
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return ManifestサーバのURL
	 */
	public static String getMnfServerUrl(String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		BDBClientServerManager bdbClientServerManager = new BDBClientServerManager();
		return bdbClientServerManager.getMnfServerUrl(serviceName, requestInfo, connectionInfo);
	}

	/**
	 * EntryサーバのURLリストを取得
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return EntryサーバのURLリスト
	 */
	public static List<String> getEntryServerUrls(String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		BDBClientServerManager bdbClientServerManager = new BDBClientServerManager();
		return bdbClientServerManager.getEntryServerUrls(serviceName, requestInfo, connectionInfo);
	}

	/**
	 * インデックスサーバのURLリストを取得
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return インデックスサーバのURLリスト
	 */
	public static List<String> getIdxServerUrls(String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		BDBClientServerManager bdbClientServerManager = new BDBClientServerManager();
		return bdbClientServerManager.getIdxServerUrls(serviceName, requestInfo, connectionInfo);
	}

	/**
	 * 全文検索インデックスサーバのURLリストを取得
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 全文検索インデックスサーバのURLリスト
	 */
	public static List<String> getFtServerUrls(String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		BDBClientServerManager bdbClientServerManager = new BDBClientServerManager();
		return bdbClientServerManager.getFtServerUrls(serviceName, requestInfo, connectionInfo);
	}

	/**
	 * 採番・カウンタサーバのURLリストを取得
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 採番・カウンタサーバのURLリスト
	 */
	public static List<String> getAlServerUrls(String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		BDBClientServerManager bdbClientServerManager = new BDBClientServerManager();
		return bdbClientServerManager.getAlServerUrls(serviceName, requestInfo, connectionInfo);
	}

	/**
	 * 名前空間を取得
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 名前空間
	 */
	public static String getNamespace(String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		return NamespaceUtil.getNamespace(serviceName, requestInfo, connectionInfo);
	}

	/**
	 * サーバ名からManifestサーバのURLを取得
	 * @param bdbServerName Manifestサーバ名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return ManifestサーバのURL
	 */
	public static String getMnfServerUrlByServerName(String bdbServerName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		BDBClientServerManager bdbClientServerManager = new BDBClientServerManager();
		return bdbClientServerManager.getMnfServerUrlByServerName(bdbServerName,
				requestInfo, connectionInfo);
	}

	/**
	 * サーバ名からEntryサーバのURLを取得
	 * @param bdbServerName Entryサーバ名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return EntryサーバのURL
	 */
	public static String getEntryServerUrlByServerName(String bdbServerName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		BDBClientServerManager bdbClientServerManager = new BDBClientServerManager();
		return bdbClientServerManager.getEntryServerUrlByServerName(bdbServerName,
				requestInfo, connectionInfo);
	}

	/**
	 * サーバ名からインデックスサーバのURLを取得
	 * @param bdbServerName インデックスサーバ名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return インデックスサーバのURL
	 */
	public static String getIdxServerUrlByServerName(String bdbServerName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		BDBClientServerManager bdbClientServerManager = new BDBClientServerManager();
		return bdbClientServerManager.getIdxServerUrlByServerName(bdbServerName,
				requestInfo, connectionInfo);
	}

	/**
	 * サーバ名から全文検索インデックスサーバのURLを取得
	 * @param bdbServerName 全文検索インデックスサーバ名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 全文検索インデックスサーバのURL
	 */
	public static String getFtServerUrlByServerName(String bdbServerName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		BDBClientServerManager bdbClientServerManager = new BDBClientServerManager();
		return bdbClientServerManager.getFtServerUrlByServerName(bdbServerName,
				requestInfo, connectionInfo);
	}

	/**
	 * サーバ名から採番・カウンタサーバのURLを取得
	 * @param bdbServerName 採番・カウンタサーバ名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 採番・カウンタサーバのURL
	 */
	public static String getAlServerUrlByServerName(String bdbServerName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		BDBClientServerManager bdbClientServerManager = new BDBClientServerManager();
		return bdbClientServerManager.getAlServerUrlByServerName(bdbServerName,
				requestInfo, connectionInfo);
	}

	/**
	 * サーバ振り分け.
	 * データのサーバ振り分け先リストにURLを指定する。
	 * （システム管理サービスはプロパティファイルにURLを直接指定するので、サーバ名を保持していないため。）
	 * ConsistentHashの生成に時間がかかるため、一度生成するとコネクション情報に保持する。
	 * 内部で使用するMessageDigestがスレッドセーフでないため、スレッド間のみの共有とする。
	 *
	 * @param serverType サーバタイプ
	 * @param servers サーバリスト、またはサーバURLリスト
	 * @param val 振り分け対象値
	 * @param serviceName サービス名
	 * @param connectionInfo コネクション情報
	 * @return 振り分けサーバ
	 */
	public static String assignServer(BDBServerType serverType, List<String> servers,
			String val, String serviceName, ConnectionInfo connectionInfo) {
		// コネクション情報から取得
		ConsistentHash<String> consistentHash = getConsistentHash(serverType, servers,
				serviceName, connectionInfo);
		return assign(consistentHash, val);
	}

	/**
	 * 振り分け.
	 * @param consistentHash ConsistentHash
	 * @param val 振り分け対象値
	 * @return 振り分けサーバ
	 */
	public static String assign(ConsistentHash<String> consistentHash, String val) {
		return consistentHash.get(val);
	}

	/**
	 * ConsistentHashマップを取得する.
	 * コネクション情報に存在する場合はオブジェクトを返す。なければ生成して返す。
	 * @param serviceName サービス名
	 * @param connectionInfo コネクション情報
	 * @return ConsistentHashマップ
	 */
	public static Map<BDBServerType, ConsistentHash<String>> getConsistentHashMap(
			String serviceName, ConnectionInfo connectionInfo) {
		Map<BDBServerType, ConsistentHash<String>> consistentHashMap = null;
		String key = BDBClientUtil.getActualConnName(
				BDBClientConst.CONNECTION_INFO_CONSISTENTHASHMAP, serviceName);
		ConnConsistentHashMap connConsistentHashMap =
				(ConnConsistentHashMap)connectionInfo.getSharing(key);
		if (connConsistentHashMap == null) {
			consistentHashMap = new HashMap<>();
			connConsistentHashMap = new ConnConsistentHashMap(consistentHashMap);
			connectionInfo.putSharing(key, connConsistentHashMap);
		} else {
			consistentHashMap = connConsistentHashMap.getConnection();
		}
		return consistentHashMap;
	}

	/**
	 * 指定されたサーバタイプのConsistentHashを取得する.
	 * コネクション情報に存在する場合はオブジェクトを返す。なければ生成して返す。
	 * @param serverType サーバタイプ
	 * @param servers サーバリスト
	 * @param serviceName サービス名
	 * @param connectionInfo コネクション情報
	 * @return ConsistentHash
	 */
	public static ConsistentHash<String> getConsistentHash(BDBServerType serverType,
			List<String> servers, String serviceName, ConnectionInfo connectionInfo) {
		Map<BDBServerType, ConsistentHash<String>> consistentHashMap =
				getConsistentHashMap(serviceName, connectionInfo);
		ConsistentHash<String> consistentHash = consistentHashMap.get(serverType);
		if (consistentHash == null) {
			consistentHash = createConsistentHash(servers);
			consistentHashMap.put(serverType, consistentHash);
		}
		return consistentHash;
	}

	/**
	 * サーバリストの更新.
	 * ConsistentHashオブジェクトを作り直す。
	 * @param serverType サーバタイプ
	 * @param servers サーバリスト
	 * @param serviceName サービス名
	 * @param connectionInfo コネクション情報
	 */
	public static void changeServerList(BDBServerType serverType, List<String> servers,
			String serviceName, ConnectionInfo connectionInfo) {
		// コネクション情報から取得
		Map<BDBServerType, ConsistentHash<String>> consistentHashMap =
				getConsistentHashMap(serviceName, connectionInfo);
		ConsistentHash<String> consistentHash = createConsistentHash(servers);
		consistentHashMap.put(serverType, consistentHash);
	}

	/**
	 * Consistent hashを生成
	 * @param nodes ノードリスト
	 * @return Consistent hash
	 */
	public static ConsistentHash<String> createConsistentHash(List<String> nodes) {
		HashFunction hashFunction = new MD5();
		int numberOfReplicas = TaggingEnvUtil.getSystemPropInt(
				BDBClientServerConst.CONSISTENTHASH_REPLICA_NUM,
				BDBClientServerConst.CONSISTENTHASH_REPLICA_NUM_DEFAULT);
		return new ConsistentHash<String>(hashFunction, numberOfReplicas, nodes);
	}

	/**
	 * インデックスタイプからサーバタイプを取得.
	 * @param indexType インデックスタイプ
	 * @return サーバタイプ
	 */
	public static BDBServerType getServerType(BDBIndexType indexType) {
		if (BDBIndexType.INDEX.equals(indexType)) {
			return BDBServerType.INDEX;
		} else if (BDBIndexType.FULLTEXT.equals(indexType)) {
			return BDBServerType.FULLTEXT;
		} else if (BDBIndexType.MANIFEST.equals(indexType)) {
			return BDBServerType.MANIFEST;
		}
		return null;	// Unreachable code.
	}

}
