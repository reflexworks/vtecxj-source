package jp.reflexworks.taggingservice.service;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.service.BDBClientInitMainThreadConst.SettingDataType;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.util.LogUtil;

/**
 * リクエスト初期処理 必要なFeed検索を並列で行う.
 */
public class BDBClientInitMainThreadFeedCallable extends ReflexCallable<FeedBase> {

	/** URI */
	private String uri;
	/** 実行元サービス名 */
	private String originalServiceName;
	/** 設定データタイプ : システム管理サービス、システム管理サービスのサービス固有、自サービス */
	private SettingDataType settingDataType;
	/** メインスレッドキャッシュの有効期限(秒) */
	private int expireSec;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @paran settingDataType 設定データタイプ
	 * @param uri uri
	 * @param originalServiceName 実行元サービス名
	 * @param expireSec メインスレッドキャッシュの有効期限(秒)
	 */
	public BDBClientInitMainThreadFeedCallable(SettingDataType settingDataType,
			String uri, String originalServiceName, int expireSec) {
		this.settingDataType = settingDataType;
		this.uri = uri;
		this.originalServiceName = originalServiceName;
		this.expireSec = expireSec;
	}

	/**
	 * リクエスト初期処理 必要なFeed検索を並列で行う.
	 * @return 取得したFeed
	 */
	@Override
	public FeedBase call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		if (logger.isTraceEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[init mainThread feed call] start. uri=" + uri);
		}

		ReflexContext reflexContext = (ReflexContext)getReflexContext();
		BDBClientInitMainThreadManager manager = new BDBClientInitMainThreadManager();
		FeedBase feed = manager.initMainThreadEachFeed(settingDataType, uri, originalServiceName,
				expireSec, reflexContext);

		if (logger.isTraceEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[init mainThread feed call] end. uri=" + uri);
		}

		return feed;
	}

}
