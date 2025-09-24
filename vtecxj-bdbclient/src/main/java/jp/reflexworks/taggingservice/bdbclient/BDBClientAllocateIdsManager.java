package jp.reflexworks.taggingservice.bdbclient;

import java.io.IOException;
import java.util.Arrays;
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
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.AllocateIdsManager;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBResponseType;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBServerType;
import jp.reflexworks.taggingservice.requester.BDBRequester;
import jp.reflexworks.taggingservice.requester.BDBRequesterUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * 自動採番処理管理クラス.
 */
public class BDBClientAllocateIdsManager implements AllocateIdsManager {

	/** allocids実行メソッド */
	private static final String METHOD_ALLOCIDS = Constants.GET;

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
	 * 採番.
	 * @param uri URI
	 * @param num 採番数
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 採番結果
	 */
	public List<String> allocateIds(String uri, int num, ReflexAuthentication auth,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		String serviceName = auth.getServiceName();
		try {
			// リクエスト情報設定
			String uriStr = getAllocidsUri(serviceName, uri, num);
			String method = METHOD_ALLOCIDS;
			String serverUrl = getServerUrl(uri, serviceName, requestInfo, connectionInfo);
			FeedTemplateMapper mapper = TaggingEnvUtil.getAtomResourceMapper();

			BDBRequester<FeedBase> requester = new BDBRequester<>(BDBResponseType.FEED);
			BDBResponseInfo<FeedBase> respInfo = requester.request(
					serverUrl, uriStr, method, null, mapper, serviceName,
					requestInfo, connectionInfo);

			// 成功
			FeedBase feed = respInfo.data;
			if (feed != null && !StringUtils.isBlank(feed.title)) {
				String[] allocidsArray = feed.title.split(",");
				return Arrays.asList(allocidsArray);
			} else {
				return null;
			}

		} finally {
			// 何かあれば処理する
		}
	}

	/**
	 * allocidsリクエストURLを編集.
	 * @param serviceName サービス名
	 * @param uri URI
	 * @param num 採番数
	 * @return allocidsリクエストURL
	 */
	private String getAllocidsUri(String serviceName, String uri, int num)
	throws IOException, TaggingException {
		StringBuilder sb = new StringBuilder();
		sb.append(uri);
		sb.append("?");
		sb.append(RequestParam.PARAM_ALLOCIDS);
		sb.append("=");
		sb.append(num);
		return sb.toString();
	}

	/**
	 * 全ての採番情報を削除.
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
