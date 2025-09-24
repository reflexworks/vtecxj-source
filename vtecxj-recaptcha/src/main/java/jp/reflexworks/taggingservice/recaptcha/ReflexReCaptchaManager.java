package jp.reflexworks.taggingservice.recaptcha;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.ApiException;
import com.google.auth.oauth2.GoogleCredentials;
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
import jp.reflexworks.taggingservice.exception.StaticDuplicatedException;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.CaptchaManager;
import jp.reflexworks.taggingservice.plugin.def.CaptchaManagerDefault;
import jp.reflexworks.taggingservice.sys.SystemContext;
import jp.reflexworks.taggingservice.util.LogUtil;
import jp.reflexworks.taggingservice.util.RetryUtil;
import jp.sourceforge.reflex.util.FileUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * キャプチャ管理クラス.
 * reCAPTCHA Enterpriseによるキャプチャ認証を行います。
 */
public class ReflexReCaptchaManager implements CaptchaManager {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 初期起動時の処理.
	 */
	public void init() {
		ReflexEnv env = (ReflexEnv)ReflexStatic.getEnv();

		// reCAPTCHA Enterprise用static情報をメモリに格納
		ReCaptchaEnv reCaptchaEnv = new ReCaptchaEnv();
		try {
			ReflexStatic.setStatic(ReCaptchaConst.STATIC_NAME_RECAPTCHA, reCaptchaEnv);
		} catch (StaticDuplicatedException e) {
			reCaptchaEnv = (ReCaptchaEnv)ReflexStatic.getStatic(ReCaptchaConst.STATIC_NAME_RECAPTCHA);
		}

		try {
			String serviceName = env.getSystemService();
			// サービスアカウント秘密鍵を取得
			String secretFilename = env.getSystemProp(ReCaptchaConst.RECAPTCHA_FILE_SECRET);
			String jsonPath = FileUtil.getResourceFilename(secretFilename);
			if (!StringUtils.isBlank(jsonPath)) {
				byte[] secret = getSecretFileData(jsonPath);
				if (secret != null) {
					// 鍵を格納する。
					reCaptchaEnv.setSecret(serviceName, secret);
				}
			}

		} catch (FileNotFoundException e) {
			logger.warn("[init] json file is not found.", e);
			throw new IllegalStateException(e);
		} catch (IOException e) {
			logger.warn("[init] IOException", e);
			throw new IllegalStateException(e);
		}
	}

	/**
	 * シャットダウン処理.
	 */
	public void close() {
		// Do nothing.
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
		RecaptchaEnterpriseServiceSettings settings = getRecaptchaEnterpriseServiceSettings(reCaptchaInfo);
		for (int r = 0; r <= numRetries; r++) {
			try (RecaptchaEnterpriseServiceClient client = 
					RecaptchaEnterpriseServiceClient.create(settings)) {

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
			} catch (IOException e) {
				// リトライ判定
				RetryUtil.checkRetry(e, r, numRetries, waitMillis, requestInfo);
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
	 * reCAPTCHA EnterpriseのプロジェクトIDを取得
	 * @param serviceName サービス名
	 * @return reCAPTCHA EnterpriseのプロジェクトID
	 */
	private String getReCaptchaProjectId(String serviceName) {
		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append("[getReCaptchaProjectId] serviceName=");
			sb.append(serviceName);
			sb.append(", service secretkey=");
			sb.append(TaggingEnvUtil.getProp(serviceName, ReCaptchaSettingConst.RECAPTCHA_SITEKEY_V2, null));
			logger.debug(sb.toString());
		}
		return TaggingEnvUtil.getProp(serviceName, ReCaptchaSettingConst.RECAPTCHA_PROJECTID, 
				TaggingEnvUtil.getSystemProp(ReCaptchaConst.GCP_PROJECTID, null)); 
	}

	/**
	 * reCAPTCHA Enterprise 認証設定オブジェクトを取得.
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return reCAPTCHA Enterprise 認証設定オブジェクト
	 */
	private RecaptchaEnterpriseServiceSettings getRecaptchaEnterpriseServiceSettings(
			ReCaptchaInfo reCaptchaInfo)
	throws IOException, TaggingException {
		// json秘密鍵を読み込む
		InputStream jsonFile = null;
		try {
			jsonFile = new ByteArrayInputStream(reCaptchaInfo.getSecret());

			GoogleCredentials credentials = GoogleCredentials.fromStream(jsonFile);
			RecaptchaEnterpriseServiceSettings recaptchaEnterpriseServiceSettings =
				RecaptchaEnterpriseServiceSettings.newBuilder()
				    .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
				    .build();
			return recaptchaEnterpriseServiceSettings;

		} finally {
			if (jsonFile != null) {
				jsonFile.close();
			}
		}
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
			secret = reCaptchaEnv.getSecret(serviceName);
		}
		
		// プロジェクトIDを取得
		String projectId = getReCaptchaProjectId(serviceName);
		
		if (secret != null && secret.length > 0 && !StringUtils.isBlank(projectId)) {
			return new ReCaptchaInfo(projectId, secret);
		}

		throw new InvalidServiceSettingException(ReCaptchaConst.MSG_NO_SETTINGS);
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
		return (ReCaptchaEnv)ReflexStatic.getStatic(ReCaptchaConst.STATIC_NAME_RECAPTCHA);
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

}
