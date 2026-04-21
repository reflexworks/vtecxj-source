package jp.reflexworks.taggingservice.blogic;

import java.io.IOException;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.SecretManager;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.Constants;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.TaggingEntryUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * SecretManagerの再読み込みビジネスロジック
 */
public class SecretBlogic {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * Secret Managerから指定された名称の値を取得.
	 * まずはキャッシュから取得する。
	 * @param secretId Secret Managerから取得したい値の名前
	 * @param versionId Secret Managerから取得したい値のバージョン。指定無しの場合はlatest
	 * @param reflexContext SystemContext
	 * @return Secret Managerから取得した値
	 */
	public String getSecretKey(String secretId, String versionId, 
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		if (isEnableAccessLog()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[getSecretKey] start. secretId=");
			sb.append(secretId);
			sb.append(", versionId=");
			sb.append(StringUtils.null2blank(versionId));
			logger.info(sb.toString());
		}
		// まずSecretCacheConnectionを取得。なければreloadsecret実行日時を取得して生成。
		String systemService = TaggingEnvUtil.getSystemService();
		SystemContext systemContext = new SystemContext(systemService, requestInfo, connectionInfo);
		SecretCacheConnection secretCacheConn = getConnectionInfo(connectionInfo);
		long reloadSecretTime = 0L;
		if (secretCacheConn == null) {
			Long tmpReloadSecretTime = systemContext.getCacheLong(SecretConst.URI_SECRET_RELOADSECRET);
			if (tmpReloadSecretTime != null) {
				reloadSecretTime = tmpReloadSecretTime;
			}
			secretCacheConn = new SecretCacheConnection(reloadSecretTime);
			setConnectionInfo(connectionInfo, secretCacheConn);
			if (isEnableAccessLog()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[getSecretKey] secretCacheConn=null, reloadSecretTime=");
				sb.append(reloadSecretTime);
				logger.info(sb.toString());
			}
		} else {
			// すでにコネクションに値があればそれを返す
			String connVal = secretCacheConn.getSecretValue(secretId, versionId);
			if (connVal != null) {
				if (isEnableAccessLog()) {
					StringBuilder sb = new StringBuilder();
					sb.append(LogUtil.getRequestInfoStr(requestInfo));
					sb.append("[getSecretKey] return connVal=");
					sb.append(connVal);
					logger.info(sb.toString());
				}
				return connVal;
			}
			// reloadsecret実行日時を取得
			reloadSecretTime = secretCacheConn.getReloadSecretTime();
			if (isEnableAccessLog()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[getSecretKey] reloadSecretTime=");
				sb.append(reloadSecretTime);
				logger.info(sb.toString());
			}
		}
		
		// 値をキャッシュから取得
		String uriSecretCache = getUriSecretCache(secretId, versionId);
		EntryBase secretCacheEntry = systemContext.getCacheEntry(uriSecretCache);
		if (secretCacheEntry != null && !StringUtils.isBlank(secretCacheEntry.subtitle)) {
			// 値の取得日時がreloadsecrettimeより後の場合、キャッシュの値を返す
			long secretCacheTime = StringUtils.longValue(secretCacheEntry.subtitle);
			if (isEnableAccessLog()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[getSecretKey] secretCacheTime=");
				sb.append(secretCacheTime);
				logger.info(sb.toString());
			}
			if (reloadSecretTime <= secretCacheTime) {
				// リロード指定よりキャッシュが後に取得された場合、キャッシュの値を返す。
				String cacheVal = secretCacheEntry.title;
				// コネクションに格納
				secretCacheConn.setSecretValue(secretId, versionId, cacheVal);
				if (isEnableAccessLog()) {
					StringBuilder sb = new StringBuilder();
					sb.append(LogUtil.getRequestInfoStr(requestInfo));
					sb.append("[getSecretKey] return cacheVal=");
					sb.append(cacheVal);
					logger.info(sb.toString());
				}
				return cacheVal;
			}
		}
		
		// Secret Managerから値を取得
		SecretManager secretManager = TaggingEnvUtil.getSecretManager();
		String secretVal = secretManager.getSecretKey(secretId, versionId);
		
		// Redisに格納
		long currentTime = getCurrentTime();
		secretCacheEntry = TaggingEntryUtil.createEntry(systemService);
		secretCacheEntry.title = secretVal;
		secretCacheEntry.subtitle = Long.toString(currentTime);
		systemContext.setCacheEntry(uriSecretCache, secretCacheEntry);
		
		// コネクションに格納
		secretCacheConn.setSecretValue(secretId, versionId, secretVal);
		if (isEnableAccessLog()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[getSecretKey] return secretVal=");
			sb.append(secretVal);
			sb.append(", currentTime=");
			sb.append(currentTime);
			logger.info(sb.toString());
		}
		return secretVal;
	}

	/**
	 * シークレットの更新.
	 * Redisにreloadsecret実行日時を登録する。
	 * @param req リクエスト
	 * @param auth 認証情報
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void reloadSecret(ReflexContext reflexContext)
	throws IOException, TaggingException {
		// システム管理サービスかどうか
		String serviceName = reflexContext.getServiceName();
		if (!TaggingEnvUtil.getSystemService().equals(serviceName)) {
			throw new IllegalParameterException("Forbidden request to this service.");
		}
		// サービス管理者かどうか
		AclBlogic aclBlogic = new AclBlogic();
		aclBlogic.checkAuthedGroup(reflexContext.getAuth(), Constants.URI_GROUP_ADMIN);

		// Redisにreloadsecret実行日時を登録する
		long now = getCurrentTime();
		reflexContext.setCacheLong(SecretConst.URI_SECRET_RELOADSECRET, now);
	}
	
	/**
	 * シークレットキャッシュコネクションを取得.
	 * @param connectionInfo コネクション情報
	 * @param name 名前
	 * @return コネクション情報用コネクション
	 */
	private SecretCacheConnection getConnectionInfo(ConnectionInfo connectionInfo) {
		return (SecretCacheConnection) connectionInfo.get(SecretConst.CONN_NAME_SECRET);
	}

	/**
	 * シークレットキャッシュコネクションをコネクション情報に格納.
	 * @param connectionInfo コネクション情報
	 * @param conn シークレットキャッシュコネクション
	 */
	private void setConnectionInfo(ConnectionInfo connectionInfo,
			SecretCacheConnection conn) {
		connectionInfo.put(SecretConst.CONN_NAME_SECRET, conn);
	}

	/**
	 * 現在日時を取得
	 * @return 現在日時(エポック秒)
	 */
	private long getCurrentTime() {
		return new Date().getTime();
	}
	
	/**
	 * シークレットキャッシュのキーを取得
	 * @param name シークレット名
	 * @param versionId バージョン
	 * @return シークレットキャッシュのキー
	 */
	private String getUriSecretCache(String name, String versionId) {
		StringBuilder sb = new StringBuilder();
		sb.append(SecretConst.URI_SECRET_CACHE_PREFEX);
		sb.append(name);
		if (!StringUtils.isBlank(versionId)) {
			sb.append(SecretConst.URI_SECRET_CACHE_DELIMITER);
			sb.append(versionId);
		}
		return sb.toString();
	}
	
	/**
	 * デバッグログ出力判定
	 * @return デバッグログ出力の場合true
	 */
	static boolean isEnableAccessLog() {
		return TaggingEnvUtil.getSystemPropBoolean(
				SecretConst.SECRET_ENABLE_ACCESSLOG, false);
	}

}
