package jp.reflexworks.taggingservice.blogic;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexResponse;
import jp.reflexworks.taggingservice.api.ReflexStatic;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.RequestParam;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.IllegalParameterException;
import jp.reflexworks.taggingservice.exception.StaticDuplicatedException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.CacheManager;
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
	 * @param requestInfo RequestInfo
	 * @param connectionInfo ConnectionInfo
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
		// まずSecretCacheConnectionを取得。
		String systemService = TaggingEnvUtil.getSystemService();
		SystemContext systemContext = new SystemContext(systemService, requestInfo, connectionInfo);
		SecretCacheConnection secretCacheConn = getConnectionInfo(connectionInfo);
		// すでにコネクションに値があればそれを返す
		String connVal = secretCacheConn.getSecretValue(secretId, versionId);
		if (connVal != null) {
			if (isEnableAccessLog()) {
				StringBuilder sb = new StringBuilder();
				sb.append(LogUtil.getRequestInfoStr(requestInfo));
				sb.append("[getSecretKey] return connVal. (masked)");
				logger.info(sb.toString());
			}
			return connVal;
		}

		String secretVal = null;
		// バージョン指定の有無
		if (!StringUtils.isBlank(versionId)) {
			// バージョン指定がある場合
			secretVal = getSecretKeyFromStatic(secretId, versionId, requestInfo, connectionInfo);

		} else {
			// バージョン指定がない場合
			versionId = getVersionIdFromCache(secretId, systemContext);
			if (SecretConst.SECRET_VERSIONID_NOVALUE.equals(versionId)) {
				// Secret Managerに登録がない
				secretVal = null;
			} else if (!StringUtils.isBlank(versionId)) {
				// シークレットのlatestバージョンが取得できた場合
				secretVal = getSecretKeyFromStatic(secretId, versionId, requestInfo, connectionInfo);
			} else {
				// シークレットのlatestバージョンが取得できなかった場合
				secretVal = getSecretKeyByLatest(secretId, systemContext);
			}
		}
		
		// コネクションに格納
		secretCacheConn.setSecretValue(secretId, versionId, secretVal);
		if (isEnableAccessLog()) {
			StringBuilder sb = new StringBuilder();
			sb.append(LogUtil.getRequestInfoStr(requestInfo));
			sb.append("[getSecretKey] return secretVal. (masked)");
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

		// シークレットのキャッシュ一覧を取得
		CacheManager cacheManager = TaggingEnvUtil.getCacheManager();
		List<String> keys = cacheManager.keysString(
				SecretConst.URI_SECRET_LATEST_KEYLIST, reflexContext);
		if (keys != null && !keys.isEmpty()) {
			// シークレットのキャッシュを削除
			for (String uri : keys) {
				cacheManager.deleteString(uri, reflexContext);
			}
		}
	}
	
	/**
	 * シークレットキャッシュコネクションを取得.
	 * @param connectionInfo コネクション情報
	 * @param name 名前
	 * @return コネクション情報用コネクション
	 */
	private SecretCacheConnection getConnectionInfo(ConnectionInfo connectionInfo) {
		SecretCacheConnection secretCacheConn = 
				(SecretCacheConnection) connectionInfo.get(SecretConst.CONN_NAME_SECRET);
		if (secretCacheConn == null) {
			secretCacheConn = new SecretCacheConnection();
			connectionInfo.put(SecretConst.CONN_NAME_SECRET, secretCacheConn);
		}
		return secretCacheConn;
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
	 * シークレットバージョンのキャッシュのキーを取得
	 * @param name シークレット名
	 * @return シークレットバージョンのキャッシュのキー
	 */
	private String getUriSecretVersionCache(String name) {
		StringBuilder sb = new StringBuilder();
		sb.append(SecretConst.URI_SECRET_LATEST_PREFEX);
		sb.append(name);
		return sb.toString();
	}
	
	/**
	 * Secret Managerから指定された名称の値を取得.
	 * @param secretId Secret Managerから取得したい値の名前
	 * @param versionId Secret Managerから取得したい値のバージョン。指定がある状態で呼び出される。
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return Secret Managerから取得した値
	 */
	private String getSecretKeyFromStatic(String secretId, String versionId, 
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		// static情報を取得
		Map<String, String> staticMap = getSecretStaticMap();
		// static情報のMapキーを取得
		String mapKey = getSecretStaticKey(secretId, versionId);
		String secretValue = staticMap.get(mapKey);
		if (!StringUtils.isBlank(secretValue)) {
			if (SecretConst.SECRET_NOVALUE.equals(secretValue)) {
				// SecretManagerにリクエスト済みで値なし
				return null;
			} else {
				return secretValue;
			}
		}
		// 値がない場合、SecretManagerからバージョン指定で値を取得する
		String[] secretResult = getSecretByManager(secretId, versionId, 
				requestInfo, connectionInfo);
		if (secretResult != null) {
			return secretResult[0];
		} else {
			return null;
		}
	}

	/**
	 * static領域からシークレット格納Mapを取得.
	 * @return シークレット格納Map
	 *	  キー: `{シークレットのキー}${バージョン}`
	 *	  値: シークレットの値
	 */
	private Map<String, String> getSecretStaticMap() 
	throws IOException {
		Map<String, String> staticMap = (Map<String, String>)ReflexStatic.getStatic(
				SecretConst.STATIC_NAME_SECRET_STATIC);
		if (staticMap == null) {
			try {
				staticMap = new ConcurrentHashMap<String, String>();
				ReflexStatic.setStatic(SecretConst.STATIC_NAME_SECRET_STATIC, staticMap);

			} catch (StaticDuplicatedException e) {
				// Do nothing.
				if (logger.isInfoEnabled()) {
					logger.info("[getSecretStaticMap] StaticDuplicatedException: " + e.getMessage());
				}
				staticMap = (Map<String, String>)ReflexStatic.getStatic(
						SecretConst.STATIC_NAME_SECRET_STATIC);
			}
		}
		return staticMap;
	}
	
	/**
	 * Static情報格納Mapのキー: `{シークレットのキー}${バージョン}` を返す。
	 * @param secretId シークレットのキー
	 * @param versionId シークレットのバージョン
	 * @return Static情報格納Mapのキー
	 */
	private String getSecretStaticKey(String secretId, String versionId) {
		StringBuilder sb = new StringBuilder();
		sb.append(secretId);
		sb.append(SecretConst.SECRET_STATIC_KEY_DELIMITER);
		sb.append(versionId);
		return sb.toString();
	}
	
	/**
	 * Redisからlatestのバージョンを取得する
	 * @param secretId Secret Managerから取得したい値の名前
	 * @param systemContext SystemContext
	 * @return latestのバージョン
	 */
	private String getVersionIdFromCache(String secretId, SystemContext systemContext) 
	throws IOException, TaggingException {
		String uri = getUriSecretVersionCache(secretId);
		return systemContext.getCacheString(uri);
	}
	
	/**
	 * Secret Managerから指定された名称の値を取得.
	 * @param secretId Secret Managerから取得したい値の名前
	 * @param versionId Secret Managerから取得したい値のバージョン。指定がある状態で呼び出される。
	 * @param requestInfo RequestInfo
	 * @param connectionInfo ConnectionInfo
	 * @return Secret Managerから取得した値
	 */
	private String getSecretKeyByLatest(String secretId, SystemContext systemContext)
	throws IOException, TaggingException {
		RequestInfo requestInfo = systemContext.getRequestInfo();
		ConnectionInfo connectionInfo = systemContext.getConnectionInfo();
		// SecretManagerからlatestバージョンで値を取得する。
		String[] secretResult = getSecretByManager(secretId, null, requestInfo, connectionInfo);
		String secretValue = null;
		String cacheVersionId = null;
		if (secretResult == null) {
			cacheVersionId = SecretConst.SECRET_VERSIONID_NOVALUE;
		} else {
			secretValue = secretResult[0];
			cacheVersionId = secretResult[1];
		}
		// Redisにlatestのバージョンを登録する。
		String uri = getUriSecretVersionCache(secretId);
		systemContext.setCacheString(uri, cacheVersionId);
		
		return secretValue;
	}
	
	/**
	 * Secret Managerからシークレットを取得
	 * @param secretId Secret Managerから取得したい値の名前
	 * @param versionId Secret Managerから取得したい値のバージョン。指定無しの場合はlatest
	 * @param requestInfo RequestInfo
	 * @param connectionInfo ConnectionInfo
	 * @return [0]Secret Managerから取得した値 [1]バージョンID
	 */
	private String[] getSecretByManager(String secretId, String versionId,
			RequestInfo requestInfo, ConnectionInfo connectionInfo) 
	throws IOException, TaggingException {
		String staticValue = null;
		String staticVersionId = null;
		// SecretManagerから値を取得する。
		SecretManager secretManager = TaggingEnvUtil.getSecretManager();
		String[] secretResults = secretManager.getSecretKey(secretId, versionId);
		if (secretResults == null) {
			staticValue = SecretConst.SECRET_NOVALUE;
			if (StringUtils.isBlank(versionId)) {
				staticVersionId = SecretConst.SECRET_VERSIONID_NOVALUE;
			}
		} else {
			staticValue = secretResults[0];
			staticVersionId = secretResults[1];
		}
		// static情報のMapキーを取得
		String mapKey = getSecretStaticKey(secretId, staticVersionId);
		// static情報に格納
		Map<String, String> staticMap = getSecretStaticMap();
		staticMap.put(mapKey, staticValue);
		
		return secretResults;
	}


	/**
	 * デバッグログ出力判定
	 * @return デバッグログ出力の場合true
	 */
	static boolean isEnableAccessLog() {
		return TaggingEnvUtil.getSystemPropBoolean(
				SecretConst.SECRET_ENABLE_ACCESSLOG, false);
	}

	/**
	 * Secret Managerのモニター処理
	 * @param req リクエスト
	 * @param resp レスポンス
	 * @return モニター結果
	 *         titleにキー、subtitleにバージョン、rightsに値
	 */
	public FeedBase monitor(ReflexRequest req, ReflexResponse resp)
	throws IOException, TaggingException {
		RequestInfo requestInfo = req.getRequestInfo();
		ConnectionInfo connectionInfo = req.getConnectionInfo();
		RequestParam param = (RequestParam)req.getRequestType();
		String key = param.getOption(SecretConst.PARAM_MONITOR_KEY);
		String version = param.getOption(SecretConst.PARAM_MONITOR_VERSION);
		if (StringUtils.isBlank(key)) {
			throw new IllegalParameterException("Key parameter is required.");
		}
		String resultValue = getSecretKey(key, version, requestInfo, connectionInfo);
		FeedBase feed = TaggingEntryUtil.createFeed(req.getServiceName());
		feed.title = key;
		feed.subtitle = version;
		feed.rights = resultValue;
		return feed;
	}

}
