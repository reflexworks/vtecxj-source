package jp.reflexworks.taggingservice.recaptcha;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.ApiException;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.cloud.recaptchaenterprise.v1.RecaptchaEnterpriseServiceClient;
import com.google.cloud.recaptchaenterprise.v1.RecaptchaEnterpriseServiceSettings;
import com.google.recaptchaenterprise.v1.Assessment;
import com.google.recaptchaenterprise.v1.CreateAssessmentRequest;
import com.google.recaptchaenterprise.v1.Event;
import com.google.recaptchaenterprise.v1.ProjectName;

import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.ReflexContentInfo;
import jp.reflexworks.taggingservice.api.ReflexEnv;
import jp.reflexworks.taggingservice.api.ReflexRequest;
import jp.reflexworks.taggingservice.api.ReflexStatic;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.api.SettingConst;
import jp.reflexworks.taggingservice.blogic.SecurityConst;
import jp.reflexworks.taggingservice.env.TaggingEnvConst;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.AuthenticationException;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;
import jp.reflexworks.taggingservice.exception.MappingFunctionException;
import jp.reflexworks.taggingservice.exception.StaticDuplicatedException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.CaptchaManager;
import jp.reflexworks.taggingservice.plugin.def.CaptchaManagerDefault;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.sourceforge.reflex.util.FileUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * キャプチャ管理クラス.
 * reCAPTCHA Enterpriseによるキャプチャ認証を行います。
 */
public class ReflexReCaptchaManager implements CaptchaManager {

	/** ロガー. */
	private static Logger logger = LoggerFactory.getLogger(ReflexReCaptchaManager.class);

	/** 
	 * reCAPTCHA Enterprise接続オブジェクト(com.google.cloud.bigquery.BigQuery)はスレッドセーフのため使い回す。
	 * キー:サービス名、値:reCAPTCHA Enterprise接続オブジェクト
	 */
	private static final Cache<String, RecaptchaEnterpriseServiceClient> clientCache = 
			Caffeine.newBuilder()
			.expireAfterAccess(ReCaptchaConst.CACHE_EXPIRE_MIN, TimeUnit.MINUTES)
			.maximumSize(ReCaptchaConst.CACHE_MAXSIZE)
			.removalListener(
					// 型指定できないので、中でcastする。
					(key, value, cause) -> {
						RecaptchaEnterpriseServiceClient client = (RecaptchaEnterpriseServiceClient)value;
						if (client != null) {
							try {
								client.close();  // コネクション解放
							} catch (Throwable e) {
								StringBuilder sb = new StringBuilder();
								sb.append("[clientCache removalListener] Error occured. ");
								sb.append(e.getClass().getName());
								sb.append(": ");
								sb.append(e.getMessage());
								logger.warn(sb.toString(), e);
							}
						}
					})
			.build();

    /**
	 * 初期起動時の処理.
	 */
	public void init() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();

		// reCAPTCHA Enterprise用static情報をメモリに格納
		ReCaptchaEnv reCaptchaEnv = new ReCaptchaEnv();
		try {
			ReflexStatic.setStatic(ReCaptchaConst.STATIC_NAME_RECAPTCHA_ENV, reCaptchaEnv);
		} catch (StaticDuplicatedException e) {
			reCaptchaEnv = (ReCaptchaEnv)ReflexStatic.getStatic(ReCaptchaConst.STATIC_NAME_RECAPTCHA_ENV);
		}

		// システム管理サービスのサービスアカウント秘密鍵を取得
		try {
			String secretFilename = env.getSystemProp(ReCaptchaConst.RECAPTCHA_FILE_SECRET);
			String jsonPath = FileUtil.getResourceFilename(secretFilename);
			if (!StringUtils.isBlank(jsonPath)) {
				byte[] secret = getSecretFileData(jsonPath);
				if (secret != null) {
					// 鍵を格納する。
					reCaptchaEnv.setDefaultSecret(secret);
				}
			}
		} catch (FileNotFoundException e) {
			logger.warn("[init] json file is not found.", e);
			throw new IllegalStateException(e);
		} catch (IOException e) {
			logger.warn("[init] IOException", e);
			throw new IllegalStateException(e);
		}
		
		// Googleデフォルト認証情報を取得
		try {
			GoogleCredentials googleCredentials = GoogleCredentials.getApplicationDefault();
			reCaptchaEnv.setGoogleCredentials(googleCredentials);
		} catch (IOException e) {
			logger.warn("[init] IOException", e);
			//throw new IllegalStateException(e);
		}
	}

	/**
	 * シャットダウン処理.
	 */
	public void close() {
		try {
			// すべてのエントリを削除 → removalListener が順次呼ばれる
			clientCache.invalidateAll();
			// 保留中の削除通知などを即時処理したい場合
			clientCache.cleanUp();
		} catch (Throwable e) {
			logger.warn("[close] Error occured.", e);
			throw new IllegalStateException(e);
		}
	}

	/**
	 * キャプチャ判定.
	 * @param req リクエスト
	 * @param action キャプチャチェックアクション
	 * @throws AuthenticationException キャプチャ認証エラー
	 */
	public void verify(ReflexRequest req, String action)
	throws IOException, TaggingException {
		// リクエストがnullの場合はエラー
		if (req == null) {
			String msg = "Captcha verify failed: The request is null.";
			if (logger.isDebugEnabled()) {
				logger.debug("[verify] " + msg);
			}
			AuthenticationException ae = new AuthenticationException();
			ae.setSubMessage(msg);
			throw ae;
		}

		String serviceName = req.getServiceName();
		RequestInfo requestInfo = req.getRequestInfo();
		ConnectionInfo connectionInfo = req.getConnectionInfo();
		if (logger.isDebugEnabled()) {
			logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[verify] start.");
		}

		boolean isV2 = false;
		String recaptchaResponse = null;
		// Enterprise v3の設定`_recaptcha.sitekey`がある場合
		String sitekey = getReCaptchaKey(serviceName);
		if (logger.isTraceEnabled()) {
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + "[verify] sitekey(v3)=" + sitekey);
		}
		if (!StringUtils.isBlank(sitekey)) {
			// URLパラメータ`g-recaptcha-token`の値を取得。なければ認証エラー。
			recaptchaResponse = req.getParameter(SecurityConst.REQUEST_PARAM_RECAPTCHA_TOKEN);
		} else {
			// Enterprise v2の設定`_recaptcha.sitekey.v2`がある場合
			sitekey = getReCaptchaKeyV2(serviceName);
			if (logger.isTraceEnabled()) {
				logger.info(LogUtil.getRequestInfoStr(requestInfo) + "[verify] sitekey(v2)=" + sitekey);
			}
			if (!StringUtils.isBlank(sitekey)) {
				recaptchaResponse = req.getParameter(SecurityConst.REQUEST_PARAM_RECAPTCHA_RESPONSE);
				isV2 = true;
			}
		}
		if (StringUtils.isBlank(sitekey)) {
			// 旧reCAPTCHA
			CaptchaManagerDefault captchaManagerDefault = new CaptchaManagerDefault();
			captchaManagerDefault.verify(req, action);
			return;
		}

		if (logger.isInfoEnabled()) {
			logger.info(LogUtil.getRequestInfoStr(requestInfo) + "[verify] reCAPTCHA Enterprise check start.");
		}

		if (StringUtils.isBlank(recaptchaResponse)) {
			String msg = "Captcha verify failed: recaptcha response parameter is null.";
			if (logger.isDebugEnabled()) {
				logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[verify] " + msg);
			}
			AuthenticationException ae = new AuthenticationException();
			ae.setSubMessage(msg);
			throw ae;
		}

		// reCAPTCHAリクエスト
		// リトライ回数
		int numRetries = TaggingEnvUtil.getSystemPropInt(SecurityConst.PROP_RECAPTCHA_RETRY_COUNT,
				SecurityConst.RECAPTCHA_RETRY_COUNT_DEFAULT);
		int waitMillis = TaggingEnvUtil.getSystemPropInt(SecurityConst.PROP_RECAPTCHA_RETRY_WAITMILLIS,
				SecurityConst.RECAPTCHA_RETRY_WAITMILLIS_DEFAULT);
		// 接続情報
		ReCaptchaInfo reCaptchaInfo = getReCaptchaInfo(serviceName, requestInfo, connectionInfo);
		RecaptchaEnterpriseServiceClient client = getReCaptchaClient(reCaptchaInfo,
				serviceName, requestInfo, connectionInfo);
		for (int r = 0; r <= numRetries; r++) {
			try {
				// 追跡するイベントのプロパティを設定する。
				Event event = Event.newBuilder().setSiteKey(sitekey).setToken(recaptchaResponse).build();

				// 評価リクエストを作成する。
				CreateAssessmentRequest createAssessmentRequest =
						CreateAssessmentRequest.newBuilder()
						.setParent(ProjectName.of(reCaptchaInfo.getProjectId()).toString())
						.setAssessment(Assessment.newBuilder().setEvent(event).build())
						.build();

				Assessment response = client.createAssessment(createAssessmentRequest);

				// トークンが有効かどうかを確認する。
				if (!response.getTokenProperties().getValid()) {
					String msg = "The CreateAssessment call failed because the token was: "
							+ response.getTokenProperties().getInvalidReason().name();
					if (logger.isDebugEnabled()) {
						logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[verify] " + msg);
					}
					AuthenticationException ae = new AuthenticationException();
					ae.setSubMessage(msg);
					throw ae;
				}

				// 想定どおりのアクションが実行されたかどうかを確認する。
				if (!response.getTokenProperties().getAction().equals(action)) {
					StringBuilder sb = new StringBuilder();
					sb.append("The action attribute in reCAPTCHA tag is: ");
					sb.append(response.getTokenProperties().getAction());
					sb.append(". ");
					sb.append("The action attribute in the reCAPTCHA tag does not match the action (");
					sb.append(action);
					sb.append(") you are expecting to score");
					sb.append(". ");
					String msg = sb.toString();
					AuthenticationException ae = new AuthenticationException();
					ae.setSubMessage(msg);
					throw ae;
				}

				float score = response.getRiskAnalysis().getScore();
				if (logger.isTraceEnabled()) {
					StringBuilder sb = new StringBuilder();
					sb.append(LogUtil.getRequestInfoStr(requestInfo));
					sb.append("[verify] The reCAPTCHA score is: ");
					sb.append(score);
					logger.debug(sb.toString());
				}
				// スコアによるしきい値以下はエラーとする。
				double scoreThreshold = TaggingEnvUtil.getSystemPropDouble(
						SecurityConst.PROP_RECAPTCHA_SCORE_THRESHOLD, 
						SecurityConst.RECAPTCHA_SCORE_THRESHOLD_DEFAULT);
				if (score <= scoreThreshold) {
					// エラー
					String msg = "Captcha verify failed: The captcha score is lower than the threshold. " + score;
					if (logger.isDebugEnabled()) {
						logger.debug(LogUtil.getRequestInfoStr(requestInfo) + "[verify] " + msg);
					}
					AuthenticationException ae = new AuthenticationException();
					ae.setSubMessage(msg);
					throw ae;
				}

				// 認証OK
				break;

			} catch (ApiException e) {
				// 例外を編集
				convertException(e);
			}
		}
	}

	/**
	 * キャプチャ不要なWSSE認証回数を取得.
	 * @param serviceName サービス名
	 * @return キャプチャ不要なWSSE認証回数
	 */
	public int getWsseWithoutCaptchaCount(String serviceName) {
		String secretkey = getReCaptchaKey(serviceName);
		if (StringUtils.isBlank(secretkey)) {
			secretkey = getReCaptchaKeyV2(serviceName);
			if (StringUtils.isBlank(secretkey)) {
				// 旧Captchaチェックの値を取得
				CaptchaManagerDefault captchaManagerDefault = new CaptchaManagerDefault();
				return captchaManagerDefault.getWsseWithoutCaptchaCount(serviceName);
			}
		}
		try {
			return TaggingEnvUtil.getPropInt(serviceName,
					SettingConst.WSSE_WITHOUT_CAPTCHA,
					TaggingEnvUtil.getSystemPropInt(
							SettingConst.WSSE_WITHOUT_CAPTCHA,
							TaggingEnvConst.WSSE_WITHOUT_CAPTCHA_DEFAULT));
		} catch (InvalidServiceSettingException e) {
			return TaggingEnvConst.WSSE_WITHOUT_CAPTCHA_DEFAULT;
		}

	}

	/**
	 * reCAPTCHAキーを取得
	 * @param serviceName サービス名
	 * @return reCAPTCHAのシークレットキー(Enterprise)
	 */
	private String getReCaptchaKey(String serviceName) {
		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append("[getReCaptchaKey] serviceName=");
			sb.append(serviceName);
			sb.append(", service secretkey=");
			sb.append(TaggingEnvUtil.getProp(serviceName, ReCaptchaSettingConst.RECAPTCHA_SITEKEY, null));
			logger.debug(sb.toString());
		}
		return TaggingEnvUtil.getProp(serviceName, ReCaptchaSettingConst.RECAPTCHA_SITEKEY, null); 
	}

	/**
	 * V2のreCAPTCHAキーを取得
	 * @param serviceName サービス名
	 * @return reCAPTCHA V2のシークレットキー(Enterprise)
	 */
	private String getReCaptchaKeyV2(String serviceName) {
		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append("[getReCaptchaKeyV2] serviceName=");
			sb.append(serviceName);
			sb.append(", service secretkey=");
			sb.append(TaggingEnvUtil.getProp(serviceName, ReCaptchaSettingConst.RECAPTCHA_SITEKEY_V2, null));
			logger.debug(sb.toString());
		}
		return TaggingEnvUtil.getProp(serviceName, ReCaptchaSettingConst.RECAPTCHA_SITEKEY_V2, null); 
	}

	/**
	 * reCAPTCHA Enterprise 認証設定オブジェクトを取得.
	 * @param reCaptchaInfo reCaptcha設定情報
	 * @param secret サービスアカウント秘密鍵
	 * @param requestInfo リクエスト情報
	 * @return reCAPTCHA Enterprise 認証設定オブジェクト
	 */
	private RecaptchaEnterpriseServiceSettings getRecaptchaEnterpriseServiceSettings(
			ReCaptchaInfo reCaptchaInfo, byte[] secret, RequestInfo requestInfo)
	throws IOException, TaggingException {
		RecaptchaEnterpriseServiceSettings recaptchaEnterpriseServiceSettings = null;
		if (secret == null) {
			if (StringUtils.isBlank(reCaptchaInfo.getServiceAccount())) {
				// デフォルト設定
				recaptchaEnterpriseServiceSettings =
						RecaptchaEnterpriseServiceSettings.newBuilder().build();
			} else {
				if (logger.isInfoEnabled()) {	// TODO test
					StringBuilder sb = new StringBuilder();
					sb.append(LogUtil.getRequestInfoStr(requestInfo));
					sb.append("[getRecaptchaEnterpriseServiceSettings] userCredentials");
					logger.info(sb.toString());
				}
				// 1. ユーザのSAになりすますためのCredentialを作成
				// vtecxのSA -> IAM Credentials API -> ユーザSAの短期トークン
				GoogleCredentials userCredentials = ImpersonatedCredentials.create(
						getReCaptchaEnv().getGoogleCredentials(),
						reCaptchaInfo.getServiceAccount(),
						null, // delegateEmail (通常はnull)
						ReCaptchaConst.SCOPES, // 必要なスコープ
						ReCaptchaConst.TOKEN_EXPIRE_SEC // トークンの有効期限(秒)
						);

				// クレデンシャルをリフレッシュして有効化を確認
				userCredentials.refreshIfExpired();

				// 2. クライアント設定に認証情報を注入
				recaptchaEnterpriseServiceSettings = RecaptchaEnterpriseServiceSettings.newBuilder()
						.setCredentialsProvider(() -> userCredentials)
						.build();
			}
			
		} else {
			// json秘密鍵を読み込む
			InputStream jsonFile = null;
			try {
				jsonFile = new ByteArrayInputStream(secret);

				GoogleCredentials credentials = GoogleCredentials.fromStream(jsonFile);
				recaptchaEnterpriseServiceSettings =
					RecaptchaEnterpriseServiceSettings.newBuilder()
					    .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
					    .build();

			} finally {
				if (jsonFile != null) {
					jsonFile.close();
				}
			}
		}
		return recaptchaEnterpriseServiceSettings;
	}

	/**
	 * reCAPTCHA Enterprise 接続情報を取得.
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return reCAPTCHA Enterprise 接続情報
	 */
	private ReCaptchaInfo getReCaptchaInfo(String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append("[getReCaptchaProjectId] serviceName=");
			sb.append(serviceName);
			sb.append(", service secretkey=");
			sb.append(TaggingEnvUtil.getProp(serviceName, ReCaptchaSettingConst.RECAPTCHA_SITEKEY_V2, null));
			logger.debug(sb.toString());
		}
		
		// まずサービスアカウントを取得
		String serviceAccount = TaggingEnvUtil.getProp(serviceName, 
				ReCaptchaSettingConst.RECAPTCHA_SERVICEACCOUNT, null);
		String defVal = null;
		if (StringUtils.isBlank(serviceAccount)) {
			// サービスごとのサービスアカウント設定がなければ、システム管理サービスの設定利用OK
			defVal = TaggingEnvUtil.getSystemProp(ReCaptchaConst.GCP_PROJECTID, null);
		}
		
		// プロジェクトIDの指定がなければエラー
		String projectId = TaggingEnvUtil.getProp(serviceName, ReCaptchaSettingConst.RECAPTCHA_PROJECTID, 
				defVal); 
		if (!StringUtils.isBlank(projectId)) {
			return new ReCaptchaInfo(projectId, serviceAccount);
		}

		throw new InvalidServiceSettingException(ReCaptchaConst.MSG_NO_SETTINGS);
	}
	
	/**
	 * サービスアカウント秘密鍵を取得
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return サービスアカウント秘密鍵
	 */
	private byte[] getSecret(String serviceName, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		SystemContext systemContext = new SystemContext(serviceName,
				requestInfo, connectionInfo);

		// サービスごとの設定を取得。なければデフォルトの秘密鍵を使用する。
		byte[] secret = null;
		ReflexContentInfo contentInfo = systemContext.getContent(ReCaptchaConst.URI_SECRET_JSON);
		if (contentInfo != null) {
			secret = contentInfo.getData();
		} else {
			// デフォルト秘密鍵の取得
			ReCaptchaEnv reCaptchaEnv = getReCaptchaEnv();
			secret = reCaptchaEnv.getDefaultSecret();
		}
		return secret;
	}

	/**
	 * ファイルから秘密鍵データを取得.
	 * @return 秘密鍵データ
	 */
	private byte[] getSecretFileData(String secretFilename) 
	throws IOException {
		return FileUtil.getBytesFromFile(secretFilename);
	}

	/**
	 * reCAPTHCA Enterprise用static情報を取得.
	 * @return reCAPTHCA Enterprise用static情報
	 */
	private ReCaptchaEnv getReCaptchaEnv() {
		return (ReCaptchaEnv)ReflexStatic.getStatic(ReCaptchaConst.STATIC_NAME_RECAPTCHA_ENV);
	}
	
	/**
	 * 例外をTaggingExceptionに変換
	 * @param e 例外
	 * @throws TaggingException
	 */
	private void convertException(ApiException e) 
	throws TaggingException {
		if (e instanceof com.google.api.gax.rpc.PermissionDeniedException) {
			// 認証エラーはサービス指定不足
			throw new InvalidServiceSettingException(e);
		} else {
			throw e;
		}
	}
	
	/**
	 * reCAPTCHA Enterprise接続オブジェクトを取得
	 * @param recaptchaInfo reCAPTCHA Enterprise情報
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return reCAPTCHA Enterprise接続オブジェクト
	 */
	private RecaptchaEnterpriseServiceClient getReCaptchaClient(ReCaptchaInfo recaptchaInfo,
			String serviceName, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		try {
			return clientCache.get(serviceName, key -> {
				try {
					return createReCaptchaClient(
							recaptchaInfo, serviceName, requestInfo, connectionInfo);
				} catch (IOException | TaggingException e) {
					// RuntimeExceptionしかスローできないため、一旦ラップする。
					throw new MappingFunctionException(e);
				}
			});
		} catch (MappingFunctionException re) {
			Throwable t = re.getCause();
			if (t != null) {
				if (t instanceof IOException) {
					throw (IOException)t;
				} else if (t instanceof TaggingException) {
					throw (TaggingException)t;
				}
			}
			throw re;
		}
	}
	
	/**
	 * reCAPTCHA Enterprise接続オブジェクトを作成
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return reCAPTCHA Enterprise接続オブジェクト
	 */
	private RecaptchaEnterpriseServiceClient createReCaptchaClient(ReCaptchaInfo reCaptchaInfo,
			String serviceName, RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException {
		byte[] secret = getSecret(serviceName, requestInfo, connectionInfo);
		RecaptchaEnterpriseServiceSettings settings = getRecaptchaEnterpriseServiceSettings(
				reCaptchaInfo, secret, requestInfo);
		return RecaptchaEnterpriseServiceClient.create(settings);
	}

}
