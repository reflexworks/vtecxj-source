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
 * EntryサーバへEntry登録更新・削除処理.
 */
public class BDBClientUpdateEntriesCallable extends ReflexCallable<Boolean> {

	/** EntryサーバURL */
	private String entryServerUrl;
	/** リクエストURI */
	private String entryMultipleUri;
	/** リクエストメソッド */
	private String method;
	/** 更新Entryリスト */
	private List<EntryBase> entries;
	/** 追加リクエストヘッダ */
	private Map<String, String> additionalHeaders;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param entryServerUrl EntryサーバURL
	 * @param entryMultipleUri リクエストURI
	 * @param method リクエストメソッド
	 * @param entries 更新Entryリスト(登録更新の場合に指定)
	 * @param additionalHeaders 追加リクエストヘッダ(削除の場合に指定)
	 */
	public BDBClientUpdateEntriesCallable(String entryServerUrl, String entryMultipleUri,
			String method, List<EntryBase> entries, Map<String, String> additionalHeaders) {
		this.entryServerUrl = entryServerUrl;
		this.entryMultipleUri = entryMultipleUri;
		this.method = method;
		this.entries = entries;
		this.additionalHeaders = additionalHeaders;
	}

	/**
	 * 非同期処理登録.
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報 (ReadEntryMapを利用)
	 * @return Future
	 */
	public Future<Boolean> addTask(ReflexAuthentication auth, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		return (Future<Boolean>)TaskQueueUtil.addTask(this, 0, auth, requestInfo,
				connectionInfo);
	}

	/**
	 * EntryサーバへEntry更新処理.
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		if (logger.isTraceEnabled()) {
			logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
					"[UpdateEntries call] start.");
		}

		try {
			String serviceName = getServiceName();
			ConnectionInfo connectionInfo = getConnectionInfo();
			FeedTemplateMapper mapper = TaggingEnvUtil.getResourceMapper(serviceName);

			BDBRequester<List<EntryBase>> requester = new BDBRequester<>(
					BDBResponseType.FEED);
			requester.request(entryServerUrl, entryMultipleUri, method, entries,
					additionalHeaders, mapper, serviceName, requestInfo, connectionInfo);

			return true;

		} finally {
			if (logger.isTraceEnabled()) {
				logger.trace(LogUtil.getRequestInfoStr(requestInfo) +
						"[UpdateEntries call] end.");
			}
		}
	}

}
