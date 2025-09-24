package jp.reflexworks.taggingservice.bdbclient;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.BDBClientException;
import jp.reflexworks.taggingservice.exception.OutOfRangeException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.IncrementManager;
import jp.reflexworks.taggingservice.requester.BDBRequester;
import jp.reflexworks.taggingservice.requester.BDBRequesterUtil;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBResponseType;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBServerType;
import jp.reflexworks.taggingservice.util.Constants;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * インクリメント処理管理クラス.
 */
public class BDBClientIncrementManager implements IncrementManager {

	/** addids実行メソッド */
	private static final String METHOD_ADDIDS = Constants.PUT;
	/** getids実行メソッド */
	private static final String METHOD_GETIDS = Constants.GET;
	/** setids実行メソッド */
	private static final String METHOD_SETIDS = Constants.PUT;
	/** setrange実行メソッド */
	private static final String METHOD_SETRANGE = Constants.PUT;
	/** getrange実行メソッド */
	private static final String METHOD_GETRANGE = Constants.GET;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 初期起動時の処理.
	 */
	public void init() {
		// Do nothing.
	}

	/**
	 * シャットダウン時の処理.
	 */
	public void close() {
		// Do nothing.
	}

	/**
	 * 加算.
	 * @param uri URI
	 * @param num 加算数。0は現在番号の取得。
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 加算結果
	 * @throws OutOfRangeException 加算範囲が一度きりの指定で、加算範囲を超えた場合。
	 */
	public long increment(String uri, long num, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		if (num == 0) {
			return getNumber(uri, auth, requestInfo, connectionInfo);
		}

		String serviceName = auth.getServiceName();
		// リクエスト情報
		String uriStr = getAddidsUri(serviceName, uri, num);
		String method = METHOD_ADDIDS;
		String serverUrl = getServerUrl(uri, serviceName, requestInfo, connectionInfo);
		FeedTemplateMapper mapper = TaggingEnvUtil.getAtomResourceMapper();

		BDBRequester<FeedBase> requester = new BDBRequester<>(BDBResponseType.FEED);
		BDBResponseInfo<FeedBase> respInfo = requester.request(
				serverUrl, uriStr, method, null, mapper, serviceName,
				requestInfo, connectionInfo);

		// 成功
		FeedBase feed = respInfo.data;
		String errMsg = null;
		if (feed == null) {
			errMsg = "The increment feed is null. uri=" + uri;
		} else if (StringUtils.isBlank(feed.title)) {
			errMsg = "The increment feed.title is null. uri=" + uri;
		} else {
			String numStr = feed.title;
			if (StringUtils.isLong(numStr)) {
				return Long.parseLong(numStr);	// OK
			} else {
				errMsg = "The increment number is invalid. uri=" + uri + ", num=" + numStr;
			}
		}
		throw new BDBClientException(errMsg);
	}

	/**
	 * 現在値を取得.
	 * @param uri URI
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 設定結果
	 */
	private long getNumber(String uri, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		// リクエスト情報
		String uriStr = getGetidsUri(serviceName, uri);
		String method = METHOD_GETIDS;
		String serverUrl = getServerUrl(uri, serviceName, requestInfo, connectionInfo);
		FeedTemplateMapper mapper = TaggingEnvUtil.getAtomResourceMapper();

		BDBRequester<FeedBase> requester = new BDBRequester<>(BDBResponseType.FEED);
		BDBResponseInfo<FeedBase> respInfo = requester.request(
				serverUrl, uriStr, method, null, mapper, serviceName,
				requestInfo, connectionInfo);

		// 成功
		FeedBase feed = respInfo.data;
		String errMsg = null;
		if (feed == null) {
			errMsg = "The getids feed is null. uri=" + uri;
		} else if (StringUtils.isBlank(feed.title)) {
			errMsg = "The getids feed.title is null. uri=" + uri;
		} else {
			String numStr = feed.title;
			if (StringUtils.isLong(numStr)) {
				return Long.parseLong(numStr);	// OK
			} else {
				errMsg = "The getids number is invalid. uri=" + uri + ", num=" + numStr;
			}
		}
		throw new BDBClientException(errMsg);
	}

	/**
	 * 値セット.
	 * @param uri URI
	 * @param num 設定値
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 設定結果
	 */
	public long setNumber(String uri, long num, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		// リクエスト情報
		String uriStr = getSetidsUri(serviceName, uri, num);
		String method = METHOD_SETIDS;
		String serverUrl = getServerUrl(uri, serviceName, requestInfo, connectionInfo);
		FeedTemplateMapper mapper = TaggingEnvUtil.getAtomResourceMapper();

		BDBRequester<FeedBase> requester = new BDBRequester<>(BDBResponseType.FEED);
		requester.request(serverUrl, uriStr, method, null, mapper, serviceName,
				requestInfo, connectionInfo);

		// 成功
		return num;
	}

	/**
	 * 加算範囲セット.
	 * 「{開始値}-{終了値}!」の形式で指定します。
	 * 「-{終了値}」は任意指定です。
	 * 末尾に!を指定すると、番号のインクリメントは一度きりとなります。(任意指定)
	 * インクリメント時に終了値を超える場合はエラーを返します。
	 * 末尾に!が指定されない場合、インクリメント時に終了値を超える場合は開始値に戻り加算を行います。
	 * @param uri URI
	 * @param range 設定値
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 設定結果
	 */
	public String setRange(String uri, String range, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();

		// 加算枠の削除の場合そのまま空の値を設定する。
		// リクエスト情報
		String uriStr = getRangeidsUri(serviceName, uri, range);
		String method = METHOD_SETRANGE;
		String serverUrl = getServerUrl(uri, serviceName, requestInfo, connectionInfo);
		FeedTemplateMapper mapper = TaggingEnvUtil.getAtomResourceMapper();

		BDBRequester<FeedBase> requester = new BDBRequester<>(BDBResponseType.FEED);
		requester.request(serverUrl, uriStr, method, null, mapper, serviceName,
				requestInfo, connectionInfo);

		// 成功
		return range;
	}

	/**
	 * 加算範囲取得.
	 * 「{開始値}-{終了値}!」の形式で指定します。
	 * 「-{終了値}」は任意指定です。
	 * 末尾に!を指定すると、番号のインクリメントは一度きりとなります。(任意指定)
	 * インクリメント時に終了値を超える場合はエラーを返します。
	 * 末尾に!が指定されない場合、インクリメント時に終了値を超える場合は開始値に戻り加算を行います。
	 * @param uri URI
	 * @param range 設定値
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 設定結果
	 */
	public String getRange(String uri, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		// リクエスト情報
		String uriStr = getGetRangeidsUri(serviceName, uri);
		String method = METHOD_GETRANGE;
		String serverUrl = getServerUrl(uri, serviceName, requestInfo, connectionInfo);
		FeedTemplateMapper mapper = TaggingEnvUtil.getAtomResourceMapper();

		BDBRequester<FeedBase> requester = new BDBRequester<>(BDBResponseType.FEED);
		BDBResponseInfo<FeedBase> respInfo = requester.request(
				serverUrl, uriStr, method, null, mapper, serviceName,
				requestInfo, connectionInfo);

		// 成功
		FeedBase feed = respInfo.data;
		String range = null;
		if (feed != null) {
			range = feed.title;
		}
		return range;
	}

	/**
	 * 全ての加算情報を削除.
	 * サービス削除時に使用
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void deleteAll(String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// Do nothing.
	}

	/**
	 * addidsリクエストURLを編集.
	 * @param serviceName サービス名
	 * @param uri URI
	 * @param num 加算数
	 * @return リクエストURL
	 */
	private String getAddidsUri(String serviceName, String uri, long num)
	throws IOException, TaggingException {
		StringBuilder sb = new StringBuilder();
		sb.append(uri);
		sb.append("?");
		sb.append(RequestParam.PARAM_ADDIDS);
		sb.append("=");
		sb.append(num);
		return sb.toString();
	}

	/**
	 * getidsリクエストURLを編集.
	 * @param serviceName サービス名
	 * @param uri URI
	 * @param num 加算数
	 * @return リクエストURL
	 */
	private String getGetidsUri(String serviceName, String uri)
	throws IOException, TaggingException {
		StringBuilder sb = new StringBuilder();
		sb.append(uri);
		sb.append("?");
		sb.append(RequestParam.PARAM_GETIDS);
		return sb.toString();
	}

	/**
	 * setidsリクエストURLを編集.
	 * @param serviceName サービス名
	 * @param uri URI
	 * @param num 設定数
	 * @return リクエストURL
	 */
	private String getSetidsUri(String serviceName, String uri, long num)
	throws IOException, TaggingException {
		StringBuilder sb = new StringBuilder();
		sb.append(uri);
		sb.append("?");
		sb.append(RequestParam.PARAM_SETIDS);
		sb.append("=");
		sb.append(num);
		return sb.toString();
	}

	/**
	 * rangeidsリクエストURLを編集.
	 * @param serviceName サービス名
	 * @param uri URI
	 * @param range 加算枠
	 * @return リクエストURL
	 */
	private String getRangeidsUri(String serviceName, String uri, String range)
	throws IOException, TaggingException {
		StringBuilder sb = new StringBuilder();
		sb.append(uri);
		sb.append("?");
		sb.append(RequestParam.PARAM_RANGEIDS);
		if (!StringUtils.isBlank(range)) {
			sb.append("=");
			sb.append(urlEncode(range));
		}
		return sb.toString();
	}

	/**
	 * rangeidsリクエストURLを編集.
	 * @param serviceName サービス名
	 * @param uri URI
	 * @param range 加算枠
	 * @return リクエストURL
	 */
	private String getGetRangeidsUri(String serviceName, String uri)
	throws IOException, TaggingException {
		StringBuilder sb = new StringBuilder();
		sb.append(uri);
		sb.append("?");
		sb.append(RequestParam.PARAM_RANGEIDS);
		return sb.toString();
	}

	/**
	 * URLエンコード
	 * @param str 文字列
	 * @return URLエンコードした文字列
	 */
	private String urlEncode(String str) {
		return BDBClientUtil.urlEncode(str);
	}

	/**
	 * キーの担当サーバURLを取得.
	 * @param uri URI
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return サーバURL
	 */
	private String getServerUrl(String uri, String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		List<String> serverUrls = BDBRequesterUtil.getAlServerUrls(serviceName,
				requestInfo, connectionInfo);
		return BDBRequesterUtil.assignServer(BDBServerType.ALLOCIDS, serverUrls, uri,
				serviceName, connectionInfo);
	}

}
