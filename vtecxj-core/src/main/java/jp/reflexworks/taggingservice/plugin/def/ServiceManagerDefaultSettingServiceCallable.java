package jp.reflexworks.taggingservice.plugin.def;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.SettingService;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.util.LogUtil;

/**
 * リクエスト初期処理 各管理クラスのサービス情報更新チェック.
 */
public class ServiceManagerDefaultSettingServiceCallable extends ReflexCallable<Boolean> {

	/** 対象サービス名 */
	private String serviceName;
	/** 設定サービス */
	private SettingService settingService;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * コンストラクタ
	 * @param serviceName 対象サービス名
	 * @param settingService 設定サービス
	 */
	public ServiceManagerDefaultSettingServiceCallable(String serviceName,
			SettingService settingService) {
		this.serviceName = serviceName;
		this.settingService = settingService;
	}

	/**
	 * リクエスト初期処理 各管理クラスのサービス情報更新チェック.
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		RequestInfo requestInfo = getRequestInfo();
		if (logger.isTraceEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[setting service call] start. " +
					settingService.getClass().getName());
		}

		settingService.settingService(serviceName, requestInfo, getConnectionInfo());

		if (logger.isTraceEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[setting service call] end. " +
					settingService.getClass().getName());
		}

		return true;
	}

}
