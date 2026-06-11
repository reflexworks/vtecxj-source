package jp.reflexworks.taggingservice.subscription;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.codec.binary.Base64;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.auth.oauth2.TokenVerifier;
import com.google.auth.oauth2.TokenVerifier.VerificationException;

import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.Constants;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * サブスクリプションユーティリティ
 */
public class SubscriptionUtil {

	/** ロガー. */
	private static final Logger logger = LoggerFactory.getLogger(SubscriptionUtil.class);
	/** Workload Identityサービスアカウント. */
	private static volatile String workloadIdentityServiceAccount;

	/**
	 * 受信したJSON形式のメッセージから値を取得する.
	 * message.data をBase64デコードする。
	 *
	 *   {
	 *     "message": {
	 *       "attributes": {
	 *         "key": "value"
	 *       },
	 *       "data": "SGVsbG8gQ2xvdWQgUHViL1N1YiEgSGVyZSBpcyBteSBtZXNzYWdlIQ==",
	 *       "messageId": "136969346945"
	 *     },
	 *     "subscription": "projects/myproject/subscriptions/mysubscription"
	 *   }
	 *
	 * @param json 受信したJSON形式メッセージ
	 * @return 値
	 */
	public static String getMessage(String json) throws IOException {
		JSONParser jsonParser = new JSONParser();
		try {
			Object o = jsonParser.parse(json);
			if (o instanceof JSONObject) {
				JSONObject jsonObj = (JSONObject)o;
				Object message = jsonObj.get(SubscriptionConst.MESSAGE);
				if (message instanceof JSONObject) {
					JSONObject messageObj = (JSONObject)message;
					Object data = messageObj.get(SubscriptionConst.DATA);
					if (data instanceof String) {
						String dataStr = (String)data;
						return new String(Base64.decodeBase64(dataStr), Constants.ENCODING);
					}
				}
			}
			return null;

		} catch (ParseException e) {
			throw new IOException(e);
		}
	}

	/**
	 * Authorization Bearerトークン検証
	 * @param req リクエスト
	 * @param systemContext SystemContext
	 * @return 検証OKの場合true
	 */
	public static boolean authenticate(ReflexRequest req, SystemContext systemContext)
	throws IOException, TaggingException {
		// Get the Cloud Pub/Sub-generated JWT in the "Authorization" header.
		String authorizationHeader = req.getHeader(ReflexServletConst.HEADER_AUTHORIZATION);
		return authenticateToken(authorizationHeader, systemContext);
	}

	/**
	 * サブスクリプションの認証.
	 * @param authorizationValue リクエストヘッダのAuthorizationの値
	 * @param systemContext SystemContext
	 * @return 認証OKの場合true
	 */
	public static boolean authenticateToken(String authorizationValue,
			SystemContext systemContext)
	throws IOException, TaggingException {
		String bearerToken = getBearerToken(authorizationValue);
		if (StringUtils.isBlank(bearerToken)) {
			if (logger.isDebugEnabled()) {
				logger.debug("[authenticateToken] bearerToken is required.");
			}
			return false;
		}

		String audience = getAudience();
		if (StringUtils.isBlank(audience)) {
			if (logger.isDebugEnabled()) {
				logger.debug("[authenticateToken] audience is required.");
			}
			return false;
		}

		String serviceAccount = getServiceAccount();
		if (StringUtils.isBlank(serviceAccount)) {
			if (logger.isDebugEnabled()) {
				logger.debug("[authenticateToken] service account is required.");
			}
			return false;
		}

		// BearerTokenが認証済み(Redisに格納済み)の場合はtrueを返す。
		String key = getBearerTokenCacheKey(bearerToken, audience, serviceAccount);
		String val = systemContext.getCacheString(key);
		if (bearerToken.equals(val)) {
			// 認証OK
			if (logger.isDebugEnabled()) {
				logger.debug("[authenticateToken] The BearerToken is authenticated.");
			}
			return true;
		}

		// BearerTokenの署名、issuer、audience、期限、サービスアカウントの確認
		boolean ret = verifyBearerToken(bearerToken, audience, serviceAccount);
		if (ret) {
			// 認証済みのBearerTokenをキャッシュに格納
			systemContext.setCacheString(key, bearerToken, getBearerTokenCacheExpireSec());
		} else {
			// 認証NG
			if (logger.isDebugEnabled()) {
				logger.debug("[authenticateToken] The BearerToken is an authentication error.");
			}
		}
		return ret;
	}

	/**
	 * リクエストヘッダ Authorization の値 "Bearer {Token}" のうち、Tokenを抽出する。
	 * @param str リクエストヘッダ Authorization の値
	 * @return Bearer Token
	 */
	private static String getBearerToken(String str) {
		if (StringUtils.isBlank(str)) {
			return null;
		}
		String prefix = "Bearer ";
		if (!str.startsWith(prefix)) {
			return null;
		}
		String token = str.substring(prefix.length());
		if (StringUtils.isBlank(token)) {
			return null;
		}
		return token;
	}

	/**
	 * 指定されたPodまたはDeploymentが再起動中かどうか
	 * @param name PodまたはDeployment名
	 * @param systemContext SystemContext
	 * @return 指定されたPodまたはDeploymentが再起動中の場合true
	 */
	public static boolean isRebooting(String name, SystemContext systemContext)
	throws IOException, TaggingException {
		String key = getRebootCacheKey(name);
		String val = systemContext.getCacheString(key);
		return SubscriptionConst.REBOOT_VALUE.equals(val);
	}

	/**
	 * 指定されたPodまたはDeploymentに再起動中フラグを立てる.
	 * @param name PodまたはDeployment名
	 * @param systemContext SystemContext
	 */
	public static void setRebooting(String name, SystemContext systemContext)
	throws IOException, TaggingException {
		String key = getRebootCacheKey(name);
		systemContext.setCacheString(key, SubscriptionConst.REBOOT_VALUE, getRebootCacheExpireSec());
	}

	/**
	 * 指定されたPodまたはDeploymentに再起動中フラグを立てる.
	 * @param name PodまたはDeployment名
	 * @param systemContext SystemContext
	 */
	public static void deleteRebooting(String name, SystemContext systemContext)
	throws IOException, TaggingException {
		String key = getRebootCacheKey(name);
		systemContext.deleteCacheString(key);
	}

	/**
	 * Bearerトークンキャッシュキーを取得
	 * @param bearerToken Bearerトークン
	 * @param audience Audience
	 * @param serviceAccount サービスアカウント
	 * @return Bearerトークンキャッシュキー
	 */
	private static String getBearerTokenCacheKey(String bearerToken, String audience,
			String serviceAccount) {
		StringBuilder sb = new StringBuilder();
		sb.append(SubscriptionConst.BEARERTOKEN_CACHEKEY_PREFIX);
		sb.append(audience);
		sb.append("/");
		sb.append(serviceAccount);
		sb.append("/");
		sb.append(bearerToken);
		return sb.toString();
	}

	/**
	 * Bearerトークンキャッシュ有効時間(秒)を取得
	 * @return Bearerトークンキャッシュ有効時間(秒)
	 */
	private static int getBearerTokenCacheExpireSec() {
		return TaggingEnvUtil.getSystemPropInt(SubscriptionConst.PROP_BEARERTOKEN_CACHE_EXPIRE_SEC,
				SubscriptionConst.BEARERTOKEN_CACHE_EXPIRE_SEC_DEFAULT);
	}

	/**
	 * 再起動中Pod・Deploymentキャッシュキーを取得
	 * @param name PodまたはDeployment名
	 * @return 再起動中Pod・Deploymentキャッシュキー
	 */
	private static String getRebootCacheKey(String name) {
		StringBuilder sb = new StringBuilder();
		sb.append(SubscriptionConst.REBOOT_CACHEKEY_PREFIX);
		sb.append(name);
		return sb.toString();
	}

	/**
	 * 再起動中Pod・Deploymentキャッシュ有効時間(秒)を取得
	 * @return 再起動中Pod・Deploymentキャッシュ有効時間(秒)
	 */
	private static int getRebootCacheExpireSec() {
		return TaggingEnvUtil.getSystemPropInt(SubscriptionConst.PROP_REBOOT_CACHE_EXPIRE_SEC,
				SubscriptionConst.REBOOT_CACHE_EXPIRE_SEC_DEFAULT);
	}

	/**
	 * Bearerトークンの署名、issuer、audience、期限、サービスアカウントを検証する.
	 * @param bearerToken Bearerトークン
	 * @param audience Audience
	 * @param serviceAccount サービスアカウント
	 * @return 検証OKの場合true
	 */
	private static boolean verifyBearerToken(String bearerToken, String audience,
			String serviceAccount) {
		try {
			TokenVerifier verifier = TokenVerifier.newBuilder()
					.setAudience(audience)
					.setIssuer(SubscriptionConst.GOOGLE_TOKEN_ISSUER)
					.build();
			JsonWebSignature jwt = verifier.verify(bearerToken);
			if (jwt.getPayload().getExpirationTimeSeconds() == null) {
				if (logger.isDebugEnabled()) {
					logger.debug("[verifyBearerToken] exp is required.");
				}
				return false;
			}

			Object emailObj = jwt.getPayload().get(SubscriptionConst.EMAIL);
			if (!(emailObj instanceof String)) {
				if (logger.isDebugEnabled()) {
					logger.debug("[verifyBearerToken] email is not String. " + emailObj);
				}
				return false;
			}
			String email = (String)emailObj;
			if (logger.isDebugEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append("[verifyBearerToken] email: ");
				sb.append(email);
				sb.append(", service account: ");
				sb.append(serviceAccount);
				logger.debug(sb.toString());
			}
			return serviceAccount.equals(email);

		} catch (VerificationException e) {
			if (logger.isDebugEnabled()) {
				logger.debug("[verifyBearerToken] Token verification failed.", e);
			}
			return false;
		}
	}

	/**
	 * Audienceを取得.
	 * @return Audience
	 */
	private static String getAudience() {
		return TaggingEnvUtil.getSystemProp(SubscriptionConst.PROP_AUDIENCE, null);
	}

	/**
	 * サブスクリプション認証サービスアカウントを取得.
	 * @return サービスアカウント
	 */
	private static String getServiceAccount() {
		String serviceAccount = TaggingEnvUtil.getSystemProp(
				SubscriptionConst.PROP_SERVICEACCOUNT, null);
		if (StringUtils.isBlank(serviceAccount)) {
			serviceAccount = TaggingEnvUtil.getSystemProp(
					SubscriptionConst.PROP_SERVICEACCOUNT_KUBECTL, null);
		}
		if (!StringUtils.isBlank(serviceAccount)) {
			return StringUtils.trim(serviceAccount);
		}
		return getWorkloadIdentityServiceAccount();
	}

	/**
	 * Workload Identityサービスアカウントを取得.
	 * @return サービスアカウント
	 */
	private static String getWorkloadIdentityServiceAccount() {
		String serviceAccount = workloadIdentityServiceAccount;
		if (!StringUtils.isBlank(serviceAccount)) {
			return serviceAccount;
		}
		synchronized (SubscriptionUtil.class) {
			serviceAccount = workloadIdentityServiceAccount;
			if (!StringUtils.isBlank(serviceAccount)) {
				return serviceAccount;
			}
			try {
				serviceAccount = requestWorkloadIdentityServiceAccount();
			} catch (IOException e) {
				if (logger.isDebugEnabled()) {
					logger.debug("[getWorkloadIdentityServiceAccount] Metadata request failed.", e);
				}
				return null;
			}
			if (!StringUtils.isBlank(serviceAccount)) {
				workloadIdentityServiceAccount = serviceAccount;
			}
			return serviceAccount;
		}
	}

	/**
	 * Metadata ServerからWorkload Identityサービスアカウントを取得.
	 * @return サービスアカウント
	 */
	private static String requestWorkloadIdentityServiceAccount() throws IOException {
		HttpURLConnection http = null;
		try {
			URI uri = new URI(SubscriptionConst.METADATA_SERVICEACCOUNT_EMAIL_URL);
			http = (HttpURLConnection)uri.toURL().openConnection();
			http.setRequestMethod(Constants.GET);
			http.setRequestProperty(SubscriptionConst.HEADER_METADATA_FLAVOR,
					SubscriptionConst.METADATA_FLAVOR_GOOGLE);
			int timeoutMillis = TaggingEnvUtil.getSystemPropInt(
					SubscriptionConst.PROP_METADATA_REQUEST_TIMEOUT_MILLIS,
					SubscriptionConst.METADATA_REQUEST_TIMEOUT_MILLIS_DEFAULT);
			if (timeoutMillis > 0) {
				http.setConnectTimeout(timeoutMillis);
				http.setReadTimeout(timeoutMillis);
			}
			int status = http.getResponseCode();
			if (status != HttpURLConnection.HTTP_OK) {
				if (logger.isDebugEnabled()) {
					logger.debug("[requestWorkloadIdentityServiceAccount] Response status is not OK. "
							+ status);
				}
				return null;
			}
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(
					http.getInputStream(), Constants.ENCODING))) {
				String serviceAccount = StringUtils.trim(reader.readLine());
				if (logger.isDebugEnabled()) {
					logger.debug("[requestWorkloadIdentityServiceAccount] service account: "
							+ serviceAccount);
				}
				return serviceAccount;
			}
		} catch (URISyntaxException e) {
			throw new IOException(e);
		} finally {
			if (http != null) {
				http.disconnect();
			}
		}
	}

	/**
	 * ログアラートを解析、処理.
	 * OOMの場合のみPod名を返却する。
	 * @param msg メッセージ
	 * @return OOMの場合、Pod名を取得。そうでない場合はnull。
	 */
	public static String getPodnameIfOom(String msg)
	throws IOException {
		JSONParser jsonParser = new JSONParser();
		try {
			Object o = jsonParser.parse(msg);
			if (o instanceof JSONObject) {
				JSONObject jsonObj = (JSONObject)o;
				Object textPayloadObj = jsonObj.get(SubscriptionConst.LOGSINK_TEXTPAYLOAD);
				if (textPayloadObj != null && textPayloadObj instanceof String) {
					String textPayload = (String)textPayloadObj;
					if (textPayload.indexOf(SubscriptionConst.MESSAGE_OOM_PREFIX) > -1) {
						// OutOfMemoryError発生
						// 該当Podを抽出する。
						Object resourceObj = jsonObj.get(SubscriptionConst.LOGSINK_RESOURCE);
						if (resourceObj != null && resourceObj instanceof JSONObject) {
							Object labelsObj = ((JSONObject)resourceObj).get(SubscriptionConst.LOGSINK_LABELS);
							if (labelsObj != null && labelsObj instanceof JSONObject) {
								Object podNameObj = ((JSONObject)labelsObj).get(SubscriptionConst.LOGSINK_POD_NAME);
								if (podNameObj != null && podNameObj instanceof String) {
									String podName = (String)podNameObj;
									return podName;
								}
							}
						}
					}
				}
			}

		} catch (ParseException e) {
			throw new IOException(e);
		}

		return null;
	}

}
