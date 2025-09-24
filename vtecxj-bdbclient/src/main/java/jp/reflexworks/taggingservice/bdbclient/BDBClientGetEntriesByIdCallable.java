package jp.reflexworks.taggingservice.bdbclient;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexAuthentication;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.requester.BDBRequester;
import jp.reflexworks.taggingservice.requester.BDBClientServerConst.BDBResponseType;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.taskqueue.TaskQueueUtil;
import jp.reflexworks.taggingservice.util.LogUtil;

/**
 * EntryサーバへEntry取得処理.
 */
public class BDBClientGetEntriesByIdCallable extends ReflexCallable<List<EntryBase>> {

	/** EntryサーバURL */
	private String entryServerUrl;
	/** リクエストURI */
	private String entryMultipleUriStr;
	/** リクエストメソッド */
	private String method;
	/** 追加リクエストヘッダ */
	private Map<String, String> additionalHeaders;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param entryServerUrl EntryサーバURL
	 * @param entryMultipleUriStr リクエストURI
	 * @param method リクエストメソッド
	 * @param additionalHeaders 追加リクエストヘッダ
	 */
	public BDBClientGetEntriesByIdCallable(String entryServerUrl, String entryMultipleUriStr,
			String method, Map<String, String> additionalHeaders) {
		this.entryServerUrl = entryServerUrl;
		this.entryMultipleUriStr = entryMultipleUriStr;
		this.method = method;
		this.additionalHeaders = additionalHeaders;
	}

	/**
	 * 非同期処理登録.
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報 (ReadEntryMapを利用)
	 * @return Future
	 */
	public Future<List<EntryBase>> addTask(ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		return (Future<List<EntryBase>>)TaskQueueUtil.addTask(this, 0, auth, requestInfo,
				connectionInfo);
	}

	/**
	 * EntryサーバへEntry取得処理.
	 */
	@Override
	public List<EntryBase> call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
					"[GetEntriesById call] start.");
		}

		try {
			String serviceName = getServiceName();
			ConnectionInfo connectionInfo = getConnectionInfo();
			FeedTemplateMapper mapper = TaggingEnvUtil.getResourceMapper(serviceName);

			BDBRequester<List<EntryBase>> requester = new BDBRequester<>(
					BDBResponseType.ENTRYLIST);
			BDBResponseInfo<List<EntryBase>> respInfo = requester.request(entryServerUrl,
					entryMultipleUriStr, method, null, additionalHeaders, mapper,
					serviceName, requestInfo, connectionInfo);
			// 成功
			return respInfo.data;

		} finally {
			if (logger.isTraceEnabled()) {
				logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
						"[GetEntriesById call] end.");
			}
		}
	}

}
