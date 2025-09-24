package jp.reflexworks.taggingservice.storage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.cloud.storage.Cors;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.StorageException;
import com.google.common.collect.ImmutableList;

import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.UpdatedInfo;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.Constants.OperationType;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.PropertyUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * Google Cloud StorageへのCORS設定クラス.
 */
public class CloudStorageCorsManager {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * エントリー更新後に呼び出される処理
	 * @param updatedInfos 更新情報
	 * @param reflexContext ReflexContext
	 * @param cloudStorageManager CloudStorageManager
	 */
	public void doAfterCommit(List<UpdatedInfo> updatedInfos, ReflexContext reflexContext, 
			CloudStorageManager CloudStorageManager) 
	throws IOException, TaggingException {
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		if (logger.isTraceEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[doAfterCommit] start.");
		}
		
		// サービス設定("/_settings/properties")が更新されたかどうか判定
		UpdatedInfo updatedInfo = null;
		for (UpdatedInfo tmpUpdatedInfo : updatedInfos) {
			String uri = null;
			if (tmpUpdatedInfo.getFlg() == OperationType.UPDATE ||
					tmpUpdatedInfo.getFlg() == OperationType.INSERT) {
				uri = tmpUpdatedInfo.getUpdEntry().getMyUri();
			} else {	// DELETE
				uri = tmpUpdatedInfo.getPrevEntry().getMyUri();
			}
			if (Constants.URI_SETTINGS_PROPERTIES.equals(uri)) {
				updatedInfo = tmpUpdatedInfo;
			}
		}
		if (updatedInfo == null) {
			if (logger.isTraceEnabled()) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[doAfterCommit] setting properties have not changed.");
			}
			return;
		}

		// バケットのCORS設定が変更されたかどうか確認
		OperationType flg = updatedInfo.getFlg();
		String prevContextStr = null;
		if (flg == OperationType.UPDATE || flg == OperationType.DELETE) {
			prevContextStr = updatedInfo.getPrevEntry().rights;
		}
		String updContextStr = null;
		if (flg == OperationType.UPDATE || flg == OperationType.INSERT) {
			updContextStr = updatedInfo.getUpdEntry().rights;
		}
		Map<String, String> prevPropMap = PropertyUtil.parsePropertiesMap(prevContextStr);
		Map<String, String> updPropMap = PropertyUtil.parsePropertiesMap(updContextStr);
		String prevVal = StringUtils.null2blank(prevPropMap.get(CloudStorageSettingConst.STORAGE_BUCKET_CORS_ORIGIN));
		String updVal = StringUtils.null2blank(updPropMap.get(CloudStorageSettingConst.STORAGE_BUCKET_CORS_ORIGIN));
		if (prevVal.equals(updVal)) {
			if (logger.isDebugEnabled()) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[doAfterCommit] cors settings have not changed.");
			}
			return;
		}

		if (logger.isDebugEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[doAfterCommit] prev = ");
			sb.append(prevVal);
			sb.append(", upd = ");
			sb.append(updVal);
			logger.debug(sb.toString());
		}
		
		// バケットのCORS設定更新
		configureBucketCors(updVal, reflexContext, CloudStorageManager);
	}
	
	/**
	 * バケットのCORS設定変更
	 * @param origin Origin
	 * @param reflexContext ReflexContext
	 * @param cloudStorageManager CloudStorageManager
	 */
	private void configureBucketCors(String origin, ReflexContext reflexContext, 
			CloudStorageManager cloudStorageManager) 
	throws TaggingException, IOException {
		String serviceName = reflexContext.getServiceName();
		RequestInfo requestInfo = reflexContext.getRequestInfo();
		ConnectionInfo connectionInfo = reflexContext.getConnectionInfo();
		String namespace = reflexContext.getNamespace();
		String bucketName = cloudStorageManager.getBucketName(true, namespace,
				serviceName, requestInfo, connectionInfo);
		
		CloudStorageConnection storage = cloudStorageManager.getStorageBucket(
				serviceName, connectionInfo);
		
		CloudStorageBucket bucket = null;
		int numRetries = CloudStorageUtil.getStorageUploadRetryCount();
		int waitMillis = CloudStorageUtil.getStorageUploadRetryWaitmillis();
		for (int r = 0; r <= numRetries; r++) {
			try {
				bucket = storage.get(bucketName);
				break;

			} catch (CloudStorageException e) {
				// リトライ判定、入力エラー判定
				CloudStorageUtil.convertError(e);
				if (r >= numRetries) {
					// リトライ対象だがリトライ回数を超えた場合
					CloudStorageUtil.convertIOError(e);
				}
				CloudStorageUtil.sleep(waitMillis + r * 10);
			}
		}

		List<Cors> corsList = null;
		if (StringUtils.isBlank(origin)) {
			// DELETE
			// getCors() returns the List and copying over to an ArrayList so it's mutable.
			corsList = new ArrayList<>(bucket.getCors());
			// Clear bucket CORS configuration.
			corsList.clear();

		} else {
			// INSERT, UPDATE
			List<HttpMethod> methodList = new ArrayList<>();
			methodList.add(HttpMethod.GET);
			methodList.add(HttpMethod.PUT);
			methodList.add(HttpMethod.DELETE);

			List<String> responseHeader = new ArrayList<>();
			responseHeader.add(ReflexServletConst.HEADER_CONTENT_TYPE);
			responseHeader.add(ReflexServletConst.HEADER_CONTENT_DISPOSITION);
			
			int maxAgeSeconds = TaggingEnvUtil.getSystemPropInt(
					CloudStorageConst.STORAGE_BUCKET_CORS_MAXAGE_SEC, 
					CloudStorageConst.STORAGE_BUCKET_CORS_MAXAGE_SEC_DEFAULT);

			Cors cors =
					Cors.newBuilder()
					.setOrigins(ImmutableList.of(Cors.Origin.of(origin)))
					.setMethods(methodList)
					.setResponseHeaders(responseHeader)
					.setMaxAgeSeconds(maxAgeSeconds)
					.build();
			
			corsList = new ArrayList<>();
			corsList.add(cors);
		}

		for (int r = 0; r <= numRetries; r++) {
			try {
				bucket.toBuilder().setCors(corsList).build().update();
				break;

			} catch (StorageException se) {
				CloudStorageException e = CloudStorageUtil.convertException(se);
				// リトライ判定、入力エラー判定
				CloudStorageUtil.convertError(e);
				if (r >= numRetries) {
					// リトライ対象だがリトライ回数を超えた場合
					CloudStorageUtil.convertIOError(e);
				}
				CloudStorageUtil.sleep(waitMillis + r * 10);
			}
		}
	}

}
