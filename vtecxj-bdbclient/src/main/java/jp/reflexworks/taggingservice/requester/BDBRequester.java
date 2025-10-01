package jp.reflexworks.taggingservice.requester;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.servlet.HttpStatus;
import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.servlet.util.UrlUtil;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.bdbclient.BDBClientUtil;
import jp.reflexworks.taggingservice.bdbclient.BDBResponseInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.EntryMultipleInfo;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBResponseType;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.sourceforge.reflex.util.DeflateUtil;
import jp.sourceforge.reflex.util.Requester;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * BDBへのリクエストクラス.
 */
public class BDBRequester<T> {

	/** BDBサーバからのレスポンス戻り値の型指定 */
	private BDBResponseType responseType;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ.
	 * @param responseType BDBサーバからのレスポンス戻り値の型指定
	 */
	public BDBRequester(BDBResponseType responseType) {
		this.responseType = responseType;
	}

	/**
	 * Manifestサーバへのリクエスト処理.
	 * @param requestUri サーブレットパスより後のURL
	 * @param method リクエストメソッド
	 * @param reqFeed 送信するFeed
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return レスポンス情報
	 */
	public BDBResponseInfo<T> requestToManifest(String requestUri, String method,
			FeedBase reqFeed, String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// リクエスト先を取得
		String url = BDBRequesterUtil.getMnfServerUrl(serviceName,
				requestInfo, connectionInfo);
		if (StringUtils.isBlank(url)) {
			throw new IllegalStateException("Manifest URL setting is required.");
		}
		String urlStr = editRequestUrl(url, requestUri);
		FeedTemplateMapper mapper = TaggingEnvUtil.getAtomResourceMapper();
		// 暗号化・復号化なし
		return requestByUrl(urlStr, method, reqFeed, null, null, null, null, mapper,
				serviceName, requestInfo, connectionInfo);
	}

	/**
	 * BDBサーバへのリクエスト処理.
	 * @param bdbServerUrl BDBサーバURL
	 * @param requestUri サーブレットパスより後のURL
	 * @param method リクエストメソッド
	 * @param reqObj 送信するオブジェクト (Feed, Entry or InputStream)
	 * @param mapper FeedTemplateMapper
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return レスポンス情報
	 */
	public BDBResponseInfo<T> request(String bdbServerUrl, String requestUri,
			String method, Object reqObj, FeedTemplateMapper mapper, String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		return request(bdbServerUrl, requestUri, method, reqObj, null, null, null, null,
				mapper, serviceName, requestInfo, connectionInfo);
	}

	/**
	 * BDBサーバへのリクエスト処理.
	 * @param bdbServerUrl BDBサーバURL
	 * @param requestUri サーブレットパスより後のURL
	 * @param method リクエストメソッド
	 * @param reqObj 送信するオブジェクト (Feed, Entry or InputStream)
	 * @param additionalHeaders 追加リクエストヘッダ
	 * @param mapper FeedTemplateMapper
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return レスポンス情報
	 */
	public BDBResponseInfo<T> request(String bdbServerUrl, String requestUri,
			String method, Object reqObj, Map<String, String> additionalHeaders,
			FeedTemplateMapper mapper, String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		return request(bdbServerUrl, requestUri, method, reqObj, null, null, null,
				additionalHeaders, mapper, serviceName, requestInfo, connectionInfo);
	}

	/**
	 * BDBサーバへのリクエスト処理.
	 * @param bdbServerUrl BDBサーバURL
	 * @param requestUri サーブレットパスより後のURL
	 * @param method リクエストメソッド
	 * @param reqObj 送信するオブジェクト (Feed, Entry or InputStream)
	 * @param sid SID
	 * @param distkeyItem DISTKEY項目
	 * @param distkeyValue DISTKEYの値
	 * @param additionalHeaders 追加リクエストヘッダ
	 * @param mapper FeedTemplateMapper
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return レスポンス情報
	 */
	public BDBResponseInfo<T> request(String bdbServerUrl, String requestUri, String method,
			Object reqObj, String sid, String distkeyItem, String distkeyValue,
			Map<String, String> additionalHeaders, FeedTemplateMapper mapper,
			String serviceName, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// URLとPathInfo+QueryStringをくっつける
		String urlStr = editRequestUrl(bdbServerUrl, requestUri);
		return requestByUrl(urlStr, method, reqObj, sid, distkeyItem, distkeyValue,
				additionalHeaders, mapper, serviceName, requestInfo, connectionInfo);
	}

	/**
	 * BDBサーバへのリクエスト処理.
	 * @param urlStr URL
	 * @param method リクエストメソッド
	 * @param reqObj 送信するオブジェクト
	 * @param sid SID
	 * @param distkeyItem DISTKEY項目
	 * @param distkeyValue DISTKEYの値
	 * @param isEncrypt 暗号化・復号化する場合true
	 * @param mapper FeedTemplateMapper
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return レスポンス情報
	 */
	public BDBResponseInfo<T> requestByUrl(String urlStr, String method, Object reqObj,
			String sid, String distkeyItem, String distkeyValue,
			Map<String, String> additionalHeaders, FeedTemplateMapper mapper,
			String serviceName, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// 名前空間を取得
		String namespace = BDBRequesterUtil.getNamespace(serviceName, requestInfo,
				connectionInfo);
		return requestByUrl(urlStr, method, reqObj, namespace, sid, distkeyItem, distkeyValue,
				additionalHeaders, mapper, serviceName, requestInfo, connectionInfo);
	}

	/**
	 * BDBサーバへのリクエスト処理.
	 * @param urlStr URL
	 * @param method リクエストメソッド
	 * @param reqObj 送信するオブジェクト
	 * @param namespace 名前空間
	 * @param sid SID
	 * @param distkeyItem DISTKEY項目
	 * @param distkeyValue DISTKEYの値
	 * @param additionalHeaders 追加リクエストヘッダ
	 * @param isEncrypt 暗号化・復号化する場合true
	 * @param mapper FeedTemplateMapper
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return レスポンス情報
	 */
	public BDBResponseInfo<T> requestByUrl(String urlStr, String method, Object reqObj,
			String namespace, String sid, String distkeyItem, String distkeyValue,
			Map<String, String> additionalHeaders,
			FeedTemplateMapper mapper, String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// リクエストデータにEntryを指定している場合、暗号化を行う。
		boolean isEncrypt = false;
		boolean isPutEntry = false;
		if (reqObj instanceof EntryBase || reqObj instanceof List) {
			isEncrypt = true;
			isPutEntry = true;
		} else if (reqObj instanceof InputStream) {	// InputStreamの場合Entry登録とみなす
			isPutEntry = true;
		} else if (responseType == BDBResponseType.ENTRY ||
				responseType == BDBResponseType.ENTRYLIST) {
			isEncrypt = true;
		}

		return requestByUrlProc(urlStr, method, reqObj, namespace, sid, distkeyItem,
				distkeyValue, additionalHeaders, isEncrypt, isPutEntry, mapper, serviceName,
				requestInfo, connectionInfo);
	}

	/**
	 * BDBサーバへのリクエスト処理.
	 * @param urlStr URL
	 * @param method リクエストメソッド
	 * @param reqObj 送信するFeedまたはEntry、またはInputStream。
	 * @param namespace 名前空間
	 * @param sid SID
	 * @param distkeyItem DISTKEY項目
	 * @param distkeyValue DISTKEYの値
	 * @param additionalHeaders 追加リクエストヘッダ
	 * @param isEncrypt 暗号化・復号化する場合true
	 * @param isPutEntry Entry登録更新の場合true
	 * @param mapper FeedTemplateMapper
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return レスポンス情報
	 */
	private BDBResponseInfo<T> requestByUrlProc(String urlStr, String method,
			Object reqObj, String namespace, String sid, String distkeyItem,
			String distkeyValue, Map<String, String> additionalHeaders,
			boolean isEncrypt, boolean isPutEntry, FeedTemplateMapper mapper,
			String serviceName, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// BDBサーバにリクエスト
		try {
			// リクエスト情報設定
			boolean setOutput = (reqObj != null);
			Map<String, String> reqHeader = BDBRequesterUtil.getRequestHeader(serviceName,
					setOutput, isPutEntry, namespace, sid, distkeyItem, distkeyValue);
			if (additionalHeaders != null) {
				for (Map.Entry<String, String> mapEntry : additionalHeaders.entrySet()) {
					reqHeader.put(mapEntry.getKey(), mapEntry.getValue());
				}
			}
			int timeoutMillis = BDBRequesterUtil.getBDBRequestTimeoutMillis();
			byte[] inputData = null;
			InputStream reqStream = null;
			if (setOutput) {
				if (reqObj != null) {
					if (reqObj instanceof InputStream) {
						reqStream = (InputStream)reqObj;
					} else if (reqObj instanceof FeedBase) {
						inputData = BDBRequesterUtil.toRequestData((FeedBase)reqObj, mapper,
								connectionInfo.getDeflateUtil());
					} else if (reqObj instanceof EntryBase) {
						inputData = BDBRequesterUtil.toRequestData((EntryBase)reqObj, mapper,
								connectionInfo.getDeflateUtil(), isEncrypt);
					} else if (reqObj instanceof List) {	// List<EntryBase>
						// Entryリストをバイト配列リストに変換する。
						// リクエストヘッダを指定する。(id、バイト長)
						EntryMultipleInfo entryMultipleInfo = BDBRequesterUtil.toRequestDataMultiple(
								(List<EntryBase>)reqObj, mapper,
								connectionInfo.getDeflateUtil(), isEncrypt);
						inputData = entryMultipleInfo.getEntriesData();
						Map<String, String> entryMultipleHeaders =
								entryMultipleInfo.getAdditionalHeaders();
						if (entryMultipleHeaders != null) {
							for (Map.Entry<String, String> mapEntry : entryMultipleHeaders.entrySet()) {
								reqHeader.put(mapEntry.getKey(), mapEntry.getValue());
							}
						}
					} else {
						logger.warn(LogUtil.getRequestInfoStr(requestInfo) +
								"[requestByUrlProc] request object type is invalid. " +
								reqObj.getClass().getName());
					}
				}
			}

			Requester requester = new Requester();

			int numRetries = BDBRequesterUtil.getBDBRequestRetryCount();
			int waitMillis = BDBRequesterUtil.getBDBRequestRetryWaitmillis();
			for (int r = 0; r <= numRetries; r++) {
				try {
					long startTime = 0;
					if (BDBClientUtil.isEnableAccessLog()) {
						logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
								BDBRequesterUtil.getStartLog(serviceName, method, urlStr, reqHeader));
						startTime = new Date().getTime();
					}

					// リクエスト
					HttpURLConnection http = null;
					if (inputData == null && reqStream != null) {
						http = requester.prepare(urlStr, method, reqStream,
								reqHeader, timeoutMillis);
					} else {
						http = requester.prepare(urlStr, method, inputData,
								reqHeader, timeoutMillis);
					}
					/*
					if (BDBClientUtil.isEnableAccessLog()) {
						logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
								BDBRequesterUtil.prepareLog(serviceName, method, urlStr,
										reqHeader, startTime));
						startTime = new Date().getTime();
					}
					*/

					int status = http.getResponseCode();

					/*
					if (BDBClientUtil.isEnableAccessLog()) {
						logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
								BDBRequesterUtil.getResponseLog(serviceName, method, urlStr,
										reqHeader, status, startTime));
						startTime = new Date().getTime();
					}
					*/

					if (status < 400) {
						// 成功
						T data = null;
						Map<String, String> respHeaders = BDBRequesterUtil.getResponseHeaders(http);
						if (status != HttpStatus.SC_NO_CONTENT) {
							data = getObject(urlStr, http.getInputStream(),
									http.getContentType(), respHeaders, serviceName,
									connectionInfo.getDeflateUtil(), isEncrypt);
						}
						BDBResponseInfo<T> respInfo = new BDBResponseInfo<T>(status, data);
						respInfo.headers = respHeaders;

						if (BDBClientUtil.isEnableAccessLog()) {
							logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
									BDBRequesterUtil.getEndLog(serviceName, method, urlStr,
											reqHeader, status, startTime));
							startTime = new Date().getTime();
						}

						return respInfo;

					} else {
						// エラー
						String respContextType = http.getContentType();
						FeedBase errFeed = (FeedBase)BDBRequesterUtil.getObject(urlStr,
								http.getErrorStream(), respContextType, serviceName,
								connectionInfo.getDeflateUtil(), true, false);

						if (BDBClientUtil.isEnableAccessLog()) {
							logger.debug(LogUtil.getRequestInfoStr(requestInfo) +
									BDBRequesterUtil.getEndLog(serviceName, method, urlStr,
											reqHeader, status, startTime));
							startTime = new Date().getTime();
						}
						// ContextTypeが"text/"で始まる場合は致命的エラー
						if (!StringUtils.isBlank(respContextType) &&
								respContextType.startsWith(ReflexServletConst.CONTENT_TYPE_TEXT)) {
							StringBuilder sb = new StringBuilder();
							sb.append(LogUtil.getRequestInfoStr(requestInfo));
							sb.append("[requestByUrlProc] Unexpected Error occured.");
							sb.append(" method=");
							sb.append(method);
							sb.append(" urlStr=");
							sb.append(urlStr);
							sb.append(" status=");
							sb.append(status);
							sb.append(" message=");
							if (errFeed != null) {
								sb.append(errFeed.title);
							} else {
								sb.append("null");
							}
							logger.warn(sb.toString());
							// statusを500にする
							status = HttpStatus.SC_INTERNAL_SERVER_ERROR;
						}
						BDBRequesterUtil.doException(urlStr, method, status, errFeed, serviceName,
								requestInfo);
					}

				} catch (IOException e) {
					if (logger.isDebugEnabled()) {
						StringBuilder sb = new StringBuilder();
						sb.append(LogUtil.getRequestInfoStr(requestInfo));
						sb.append("[requestByUrlProc] ");
						sb.append(e.getClass().getName());
						sb.append(" ");
						sb.append(e.getMessage());
						sb.append(" [request] ");
						sb.append(method);
						sb.append(" ");
						sb.append(urlStr);
						logger.debug(sb.toString());
					}
					// リトライ判定、入力エラー判定
					BDBClientUtil.convertError(e, method, requestInfo);
					if (r >= numRetries) {
						// リトライ対象だがリトライ回数を超えた場合
						throw e;
					}
					if (logger.isInfoEnabled()) {
						StringBuilder sb = new StringBuilder();
						sb.append(LogUtil.getRequestInfoStr(requestInfo));
						sb.append("[request] ");
						sb.append(method);
						sb.append(" ");
						sb.append(urlStr);
						sb.append(" ");
						sb.append(BDBClientUtil.getRetryLog(e, r));
						logger.info(sb.toString());
					}
					BDBClientUtil.sleep(waitMillis);
				}
			}

		} finally {
			// 何かあれば処理する
		}

		// 通らない
		throw new IllegalStateException("Unreachable code generates internal error");
	}

	/**
	 * レスポンスデータのストリームからオブジェクトを取得する。
	 * MessagePack形式で受信。
	 * Entryオブジェクトとして受け取る場合、復号化する。
	 * @param urlStr URL (エラー時のログ出力用)
	 * @param in InputStream
	 * @param contentType Content-Typeヘッダ
	 * @param respHeaders レスポンスヘッダ
	 * @param serviceName サービス名
	 * @param deflateUtil DeflateUtil
	 * @param isDecrypt 暗号項目の復号化をする場合true
	 * @return オブジェクト
	 */
	private T getObject(String urlStr, InputStream in, String contentType,
			Map<String, String> respHeaders, String serviceName,
			DeflateUtil deflateUtil, boolean isDecrypt)
	throws IOException {
		if (in == null) {
			return null;
		}
		if (BDBResponseType.INPUTSTREAM.equals(responseType)) {
			return (T)in;
		}
		boolean isFeed = BDBResponseType.FEED.equals(responseType);
		List<Integer> entryLengthList = null;
		if (BDBResponseType.ENTRYLIST.equals(responseType)) {
			String entryLengthStr = null;
			if (respHeaders != null) {
				entryLengthStr = respHeaders.get(Constants.HEADER_ENTRY_LENGTH);
			}
			entryLengthList = BDBRequesterUtil.getEntryLengthList(entryLengthStr);
		}
		return (T)BDBRequesterUtil.getObject(urlStr, in, contentType, serviceName,
				deflateUtil, isFeed, entryLengthList, isDecrypt);
	}

	/**
	 * URLとPathInfo、QueryStringをつなげる.
	 * @param url URL
	 * @param requestUri PathInfo + QueryString
	 * @return リクエストURL
	 */
	private String editRequestUrl(String url, String requestUri) {
		String encodeUri = UrlUtil.urlEncodePathInfoQuery(requestUri);
		StringBuilder sb = new StringBuilder();
		sb.append(url);
		//sb.append(StringUtils.null2blank(requestUri));
		sb.append(StringUtils.null2blank(encodeUri));
		return sb.toString();
	}

	/**
	 * BDBサーバへのリクエスト処理.
	 * 移行時の旧サーバ参照用。
	 * @param bdbServerUrl BDBサーバURL
	 * @param requestUri サーブレットパスより後のURL
	 * @param method リクエストメソッド
	 * @param namespace 名前空間
	 * @param mapper FeedTemplateMapper
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return レスポンス情報
	 */
	public BDBResponseInfo<T> requestByMigration(String bdbServerUrl, String requestUri,
			String method, String namespace, FeedTemplateMapper mapper, String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// URLとPathInfo+QueryStringをくっつける
		String urlStr = editRequestUrl(bdbServerUrl, requestUri);
		boolean isEncrypt = false;
		if (responseType == BDBResponseType.ENTRY) {
			isEncrypt = true;
		}
		boolean isPutEntry = false;
		return requestByUrlProc(urlStr, method, null, namespace, null, null, null, null,
				isEncrypt, isPutEntry, mapper, serviceName, requestInfo, connectionInfo);
	}

}
