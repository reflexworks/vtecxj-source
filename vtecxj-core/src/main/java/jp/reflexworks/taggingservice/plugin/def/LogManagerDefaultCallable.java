package jp.reflexworks.taggingservice.plugin.def;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.util.LogUtil;

/**
 * リクエスト開始ログ出力.
 */
public class LogManagerDefaultCallable extends ReflexCallable<Boolean> {

	/** メインスレッド名 */
	private String mainThreadName;
	/** メソッド名 */
	private String method;
	/** リモートIPアドレス */
	private String lastForwarded;
	/** リクエストURL (+QueryString) */
	private String requestUrl;
	/** プロトコル */
	private String protocol;
	/** リクエストヘッダを文字列に変換したもの */
	private String requestHeadersString;
	/** リクエストデータのFeed */
	private FeedBase feed;
	/** リクエストデータサイズ */
	private int payloadLen;
	/** サービス名 */
	private String serviceName;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param mainThreadName メインスレッド名
	 * @param method メソッド
	 * @param lastForwarded リモートIPアドレス
	 * @param requestUrl リクエストURL (+QueryString)
	 * @param protocol プロトコル
	 * @param requestHeadersString リクエストヘッダを文字列に変換したもの
	 * @param feed Feed
	 * @param payloadLen リクエストデータサイズ
	 * @param serviceName サービス名
	 */
	public LogManagerDefaultCallable(String mainThreadName, String method, String lastForwarded,
			String requestUrl, String protocol, String requestHeadersString,
			FeedBase feed, int payloadLen, String serviceName) {
		this.mainThreadName = mainThreadName;
		this.method = method;
		this.lastForwarded = lastForwarded;
		this.requestUrl = requestUrl;
		this.protocol = protocol;
		this.requestHeadersString = requestHeadersString;
		this.feed = feed;
		this.payloadLen = payloadLen;
		this.serviceName = serviceName;
	}

	/**
	 * リクエスト開始ログ出力.
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		if (logger.isTraceEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[write logger of request start call] start.");
		}

		LogManagerDefault logManagerDefault = new LogManagerDefault();
		logManagerDefault.writeAccessStart(mainThreadName, method, lastForwarded,
				requestUrl, protocol, requestHeadersString, feed, payloadLen, serviceName,
				requestInfo);

		if (logger.isTraceEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[write logger of request start call] end.");
		}

		return true;
	}

}
