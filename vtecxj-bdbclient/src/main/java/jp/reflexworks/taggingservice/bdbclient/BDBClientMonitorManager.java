package jp.reflexworks.taggingservice.bdbclient;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.servlet.util.UrlUtil;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBResponseType;
import jp.reflexworks.taggingservice.requester.BDBRequester;
import jp.reflexworks.taggingservice.requester.BDBRequesterUtil;
import jp.reflexworks.taggingservice.util.CheckUtil;
import jp.reflexworks.taggingservice.util.Constants;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * データストアのモニター処理を行うクラス.
 */
public class BDBClientMonitorManager {

	/** GETメソッド */
	private static final String METHOD_GET = Constants.GET;

	/**
	 * モニター.
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @return モニタリング結果
	 */
	public FeedBase monitor(ReflexRequest req, ReflexResponse resp)
	throws IOException, TaggingException {
		// テーブル内容取得のリクエスト
		RequestParam param = (RequestParam)req.getRequestType();
		String monitor = param.getOption(RequestParam.PARAM_MONITOR);
		String targetService = param.getOption(BDBClientConst.PARAM_SERVICE);
		String server = param.getOption(RequestParam.PARAM_SERVER);
		CheckUtil.checkNotNull(targetService, "list servicename (" + BDBClientConst.PARAM_SERVICE + ")");

		// BDBサーバにリクエスト
		RequestInfo requestInfo = req.getRequestInfo();
		ConnectionInfo connectionInfo = req.getConnectionInfo();
		try {
			// リクエスト情報設定
			String uriStr = getMonitorUri(req);
			String method = METHOD_GET;

			BDBRequester<FeedBase> requester = new BDBRequester<>(BDBResponseType.FEED);
			BDBResponseInfo<FeedBase> respInfo = null;
			if (monitor.equals(BDBClientConst.MONITOR_MANIFEST)) {
				// Manifest
				respInfo = requester.requestToManifest(uriStr, method, null,
						targetService, requestInfo, connectionInfo);
			} else if (monitor.equals(BDBClientConst.MONITOR_ENTRY)) {
				// Entry
				String url = BDBRequesterUtil.getEntryServerUrlByServerName(server,
						requestInfo, connectionInfo);
				FeedTemplateMapper mapper = TaggingEnvUtil.getAtomResourceMapper();
				respInfo = requester.request(url, uriStr, method, null, mapper,
						targetService, requestInfo, connectionInfo);
			} else if (monitor.equals(BDBClientConst.MONITOR_INDEX)) {
				// インデックス
				String url = BDBRequesterUtil.getIdxServerUrlByServerName(server,
						requestInfo, connectionInfo);
				FeedTemplateMapper mapper = TaggingEnvUtil.getAtomResourceMapper();
				respInfo = requester.request(url, uriStr, method, null, mapper,
						targetService, requestInfo, connectionInfo);
			} else if (monitor.equals(BDBClientConst.MONITOR_FULLTEXTSEARCH)) {
				// 全文検索インデックス
				String url = BDBRequesterUtil.getFtServerUrlByServerName(server,
						requestInfo, connectionInfo);
				FeedTemplateMapper mapper = TaggingEnvUtil.getAtomResourceMapper();
				respInfo = requester.request(url, uriStr, method, null, mapper,
						targetService, requestInfo, connectionInfo);
			} else if (monitor.equals(BDBClientConst.MONITOR_ALLOCIDS)) {
				// 採番・カウンタ
				String url = BDBRequesterUtil.getAlServerUrlByServerName(server,
						requestInfo, connectionInfo);
				FeedTemplateMapper mapper = TaggingEnvUtil.getAtomResourceMapper();
				respInfo = requester.request(url, uriStr, method, null, mapper,
						targetService, requestInfo, connectionInfo);
			} else {
				throw new IllegalParameterException("monitor is invalid. " + monitor);
			}
			// 成功
			return respInfo.data;

		} catch (IllegalStateException e) {
			// サーバ名不正の場合、パラメータエラーに変換する
			String msg = e.getMessage();
			if (msg != null && "The bdb server assigned to the service does not exist.".equals(msg)) {
				throw new IllegalParameterException(msg);
			}
			throw e;
		} finally {
			// 何かあれば処理する
		}
	}

	/**
	 * BDBサーバへのリクエストURLを編集.
	 * @param req リクエスト
	 * @return BDBサーバへのリクエストURL
	 */
	private String getMonitorUri(ReflexRequest req)
	throws IOException, TaggingException {
		Set<String> ignoreParams = new HashSet<String>();
		ignoreParams.add(RequestParam.PARAM_MONITOR);
		ignoreParams.add(RequestParam.PARAM_SERVICE);
		ignoreParams.add(RequestParam.PARAM_XML);
		ignoreParams.add(RequestParam.PARAM_JSON);
		//String queryString = UrlUtil.editQueryString(req, ignoreParams, null, true);
		String queryString = UrlUtil.editQueryString(req, ignoreParams, null, false);
		StringBuilder sb = new StringBuilder();
		sb.append(req.getPathInfo());
		if (!StringUtils.isBlank(queryString)) {
			sb.append(queryString);
		}
		return sb.toString();
	}

}
