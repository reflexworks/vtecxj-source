package jp.reflexworks.taggingservice.subscription;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;

import org.apache.commons.codec.binary.Base64;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.servlet.ReflexServletConst;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.Constants;
import jp.sourceforge.reflex.util.Requester;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * サブスクリプションユーティリティ
 */
public class SubscriptionUtil {

	/** ロガー. */
	private static final Logger logger = LoggerFactory.getLogger(SubscriptionUtil.class);

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

		// BearerTokenが認証済み(Redisに格納済み)の場合はtrueを返す。
		String key = getBearerTokenCacheKey(bearerToken);
		String val = systemContext.getCacheString(key);
		if (bearerToken.equals(val)) {
			// 認証OK
			if (logger.isDebugEnabled()) {
				logger.debug("[authenticateToken] The BearerToken is authenticated.");
			}
			return true;
		}

		// BearerTokenの内容の確認
		if (!isValidBearerToken(bearerToken)) {
			if (logger.isDebugEnabled()) {
				logger.debug("[authenticateToken] The BearerToken is not valid.");
			}
			return false;
		}

		// Googleへトークンの認証リクエスト
		String url = getAuthTokenUrl(bearerToken);
		boolean ret = authenticateRequest(url);
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
	 * トークン検証リクエスト
	 * @param url URL
	 * @return 検証OKの場合true
	 */
	private static boolean authenticateRequest(String url)
	throws IOException {
		Requester requester = new Requester();
		// 検証リクエスト
		int timeoutMillis = TaggingEnvUtil.getSystemPropInt(
				SubscriptionConst.PROP_LOGALERT_REQUEST_TIMEOUT_MILLIS,
				SubscriptionConst.LOGALERT_REQUEST_TIMEOUT_MILLIS_DEFAULT);
		HttpURLConnection http = requester.request(url, SubscriptionConst.METHOD_TOKEN,
				null, timeoutMillis);
		int status = http.getResponseCode();
		if (status != 200) {
			if (logger.isDebugEnabled()) {
				logger.debug("[authenticateToken] Response status is not OK. " + status);
			}
			return false;
		}

		JSONParser jsonParser = new JSONParser();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
				http.getInputStream(), Constants.ENCODING))) {
			Object o = jsonParser.parse(reader);
			if (o instanceof JSONObject) {
				JSONObject jsonObj = (JSONObject)o;

				String emailVelifiedStr = (String)jsonObj.get(SubscriptionConst.EMAIL_VERIFIED);
				if ("true".equals(emailVelifiedStr)) {
					return true;
				}
				if (logger.isDebugEnabled()) {
					logger.debug("[authenticateToken] Email velified is false.");
				}
			}

			if (logger.isDebugEnabled()) {
				logger.debug("[authenticateToken] Response data is not JSONObject.");
			}
			return false;

		} catch (ParseException e) {
			throw new IOException(e);
		}
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
		int idx = str.indexOf(" ");
		if (idx <= 0) {
			return null;
		}
		return str.substring(idx + 1);
	}

	/**
	 * Bearerトークン認証URLを取得
	 *   https://oauth2.googleapis.com/tokeninfo?id_token={Bearerトークン}
	 * @param bearerToken Bearerトークン
	 * @return URL
	 */
	private static String getAuthTokenUrl(String bearerToken) {
		return SubscriptionConst.URL_TOKEN + bearerToken;
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
	 * @return Bearerトークンキャッシュキー
	 */
	private static String getBearerTokenCacheKey(String bearerToken) {
		StringBuilder sb = new StringBuilder();
		sb.append(SubscriptionConst.BEARERTOKEN_CACHEKEY_PREFIX);
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
	 * Bearerトークンの正当性チェック.
	 * トークンの内容は以下の通り。
	 * Authorization: Bearer {ヘッダーをBase64エンコードした値}.{クレームセットをBase64エンコードした値}.{署名}
	 *
	 * 上記のうち、クレームセットをBase64デコードしたJSONより、以下の項目をチェックする。
	 *   ・ "aud": "https://admin.vte.cx/l/"
	 *   ・ "email": "{サブスクリプション登録時に設定するサービスアカウント名}"
	 *
	 * @param bearerToken Bearerトークン
	 * @return
	 */
	private static boolean isValidBearerToken(String bearerToken)
	throws IOException, TaggingException {
		if (StringUtils.isBlank(bearerToken)) {
			if (logger.isDebugEnabled()) {
				logger.debug("[isValidBearerToken] Bearer token is null.");
			}
			return false;
		}
		// [0]ヘッダー、[1]クレームセット、[2]署名
		String[] bearerParts = bearerToken.split(SubscriptionConst.BEARER_DELIMITER_REGEX);
		if (bearerParts.length < 3) {
			if (logger.isDebugEnabled()) {
				logger.debug("[isValidBearerToken] The bearer token format is invalid.");
			}
			return false;
		}
		// クレームセットを取り出しデコードする。
		String claimSet = new String(Base64.decodeBase64(bearerParts[1]), Constants.ENCODING);
		JSONParser jsonParser = new JSONParser();
		try {
			Object o = jsonParser.parse(claimSet);
			if (o != null && o instanceof JSONObject) {
				JSONObject jsonObj = (JSONObject)o;
				// aud (pushエンドポイントURL)
				// チェックのためにBDBサーバに検索しに行くのはNG。(BDBサーバがOOMの場合エラーとなるため)

				// email (サービスアカウント名)
				Object emailObj = jsonObj.get(SubscriptionConst.EMAIL);
				if (emailObj != null && emailObj instanceof String) {
					String email = (String)emailObj;

					String serviceAccount = TaggingEnvUtil.getSystemProp(
							SubscriptionConst.PROP_SERVICEACCOUNT, null);
					if (StringUtils.isBlank(serviceAccount)) {
						serviceAccount = TaggingEnvUtil.getSystemProp(
								SubscriptionConst.PROP_SERVICEACCOUNT_KUBECTL, null);
					}

					if (logger.isDebugEnabled()) {
						StringBuilder sb = new StringBuilder();
						sb.append("[isValidBearerToken] email: ");
						sb.append(email);
						sb.append(", service account: ");
						sb.append(serviceAccount);
						logger.debug(sb.toString());
					}

					if (StringUtils.isBlank(serviceAccount)) {
						return false;
					}

					if (!serviceAccount.equals(email)) {
						return false;
					}

				} else {
					if (logger.isDebugEnabled()) {
						logger.debug("[isValidBearerToken] email is not String. " + emailObj);
					}
					return false;
				}

			} else {
				if (logger.isDebugEnabled()) {
					logger.debug("[isValidBearerToken] claimSet is not JSON. " + claimSet);
				}
				return false;
			}
			return true;

		} catch (ParseException e) {
			throw new IOException(e);
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
