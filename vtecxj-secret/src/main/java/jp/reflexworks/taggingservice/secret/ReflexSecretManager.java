package jp.reflexworks.taggingservice.secret;

import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.NotFoundException;
import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretManagerServiceSettings;
import com.google.cloud.secretmanager.v1.SecretVersionName;

import jp.reflexworks.servlet.util.ServletContextUtil;
import jp.reflexworks.taggingservice.env.TaggingEnvUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.plugin.SecretManager;
import jp.reflexworks.taggingservice.util.RetryUtil;
import jp.sourceforge.reflex.util.FileUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * シークレット管理クラス
 */
public class ReflexSecretManager implements SecretManager {
	
	/** ロガー. */
	private static final Logger logger = LoggerFactory.getLogger(ReflexSecretManager.class);

	/**
	 * 初期処理.
	 */
	@Override
	public void init() {
		// Do nothing.
	}

	/**
	 * シャットダウン処理.
	 */
	@Override
	public void close() {
		// Do nothing.
	}

	/**
	 * 暗号化キーを取得.
	 * サーバ起動時に呼び出される用のメソッド。プロパティ値は ServletContextUtil から取得する。
	 * 取得した暗号化キーはResourceMapper(エントリーのシリアライズ・デシリアライズツール)にセットする。
	 * @param contextUtil ServletContextUtil
	 * @return 暗号化キー
	 */
	@Override
	public String getSecretKey(ServletContextUtil contextUtil)
	throws IOException, TaggingException {
		String secretFilename = contextUtil.get(ReflexSecretConst.PROP_SECRET_FILE_SECRET);
		String projectId = contextUtil.get(ReflexSecretConst.PROP_GCP_PROJECTID);
		String secretId = contextUtil.get(ReflexSecretConst.PROP_SECRETKEY_NAME);
		String versionId = contextUtil.get(ReflexSecretConst.PROP_SECRETKEY_VERSION);
		int numRetries = StringUtils.intValue(
				contextUtil.get(ReflexSecretConst.PROP_SECRET_RETRY_COUNT),
				ReflexSecretConst.SECRET_RETRY_COUNT_DEFAULT);
		int waitMillis = StringUtils.intValue(
				contextUtil.get(ReflexSecretConst.PROP_SECRET_RETRY_WAITMILLIS),
				ReflexSecretConst.SECRET_RETRY_WAITMILLIS_DEFAULT);
		String[] result = getSecretKey(projectId, secretId, versionId, secretFilename, 
				numRetries, waitMillis);
		if (result != null) {
			return result[0];
		}
		return null;
	}

	/**
	 * Secret Managerから指定された名称の値を取得.
	 * 取り扱いには注意すること。
	 * @param secretId Secret Managerから取得したい値の名前
	 * @param versionId Secret Managerから取得したい値のバージョン。指定無しの場合はlatest
	 * @return [0]Secret Managerから取得した値 [1]バージョンID
	 */
	@Override
	public String[] getSecretKey(String secretId, String versionId)
	throws IOException, TaggingException {
		String secretFilename = 
				TaggingEnvUtil.getSystemProp(ReflexSecretConst.PROP_SECRET_FILE_SECRET, null);
		String projectId = 
				TaggingEnvUtil.getSystemProp(ReflexSecretConst.PROP_GCP_PROJECTID, null);
		int numRetries = getSecretRetryCount();
		int waitMillis = getSecretRetryWaitmillis();
		return getSecretKey(projectId, secretId, versionId, secretFilename, 
				numRetries, waitMillis);
	}

	/**
	 * Secret Managerから指定された名称の値を取得.
	 * @param projectId Google Cloud の Project ID
	 * @param secretId Secret Managerから取得したい値の名前
	 * @param versionId Secret Managerから取得したい値のバージョン。指定無しの場合はlatest
	 * @param secretFilename サービスアカウントJSON鍵。Workload Identityの設定があれば不要。
	 * @param numRetries 処理失敗時のリトライ回数
	 * @param waitMillis 処理失敗時のスリープ時間(ミリ秒)
	 * @return [0]Secret Managerから取得した値 [1]バージョンID
	 */
	private String[] getSecretKey(String projectId, String secretId, String versionId, 
			String secretFilename, int numRetries, int waitMillis)
	throws IOException, TaggingException {
		if (StringUtils.isBlank(projectId)) {
			logger.warn("[getSecretKey] No project id setting.");
			return null;
		}
		if (StringUtils.isBlank(secretId)) {
			logger.warn("[getSecretKey] No secret key name setting.");
			return null;
		}
		
		if (StringUtils.isBlank(versionId)) {
			versionId = ReflexSecretConst.VERSION_LATEST;
		}

		String jsonPath = FileUtil.getResourceFilename(secretFilename);
		if (logger.isTraceEnabled()) {
			logger.info("[getSecretKey] jsonPath = " + jsonPath);
		}
		
		SecretManagerServiceSettings.Builder clientSettingsBuilder = null;
		if (StringUtils.isBlank(jsonPath)) {
			// デフォルト設定
			clientSettingsBuilder = SecretManagerServiceSettings.newBuilder();

		} else {
			// サービスアカウントJSON鍵
			Credentials credentials = getCredentials(jsonPath);
			clientSettingsBuilder = SecretManagerServiceSettings.newBuilder()
					.setCredentialsProvider(FixedCredentialsProvider.create(credentials));
		}

		SecretManagerServiceSettings settings = clientSettingsBuilder.build();

		// Initialize client that will be used to send requests. This client only needs to be created
		// once, and can be reused for multiple requests. After completing all of your requests, call
		// the "close" method on the client to safely clean up any remaining background resources.
		String secretPayload = null;
		String actualVersionId = null;
		try (SecretManagerServiceClient client = SecretManagerServiceClient.create(settings)) {
			// Build the name.
			SecretVersionName secretVersionName = SecretVersionName.of(projectId, secretId, versionId);
			// test log
			if (logger.isTraceEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append("[getSecretKey] secretId=");
				sb.append(secretId);
				sb.append(", versionId=");
				sb.append(versionId);
				sb.append(", secretVersion=");
				sb.append(secretVersionName.getSecretVersion());
				logger.info(sb.toString());
			}

			// シークレットの値を取得。一時的な接続エラーはリトライする。
			// 対象のシークレットが存在しない場合(NotFoundException)も含め、
			// リトライ対象外のエラーはReflexSecretExceptionとしてスローされる。
			AccessSecretVersionResponse response = accessSecretVersion(
					client, secretVersionName, secretId, numRetries, waitMillis);
			String actualSecretVersionName = response.getName();
			actualVersionId = SecretVersionName.parse(actualSecretVersionName).getSecretVersion();
			secretPayload = response.getPayload().getData().toStringUtf8();
		}
		if (secretPayload != null && actualVersionId != null) {
			return new String[] {secretPayload, actualVersionId};
		}
		return null;
	}

	/**
	 * Secret Managerからシークレットバージョンの値を取得.
	 * 一時的な接続エラー(ApiException#isRetryable()がtrue)の場合は設定回数までリトライする。
	 * リトライ対象外のエラー、またはリトライ回数を超えた場合はReflexSecretExceptionをスローする。
	 * @param client SecretManagerServiceClient (呼び出し元でtry-with-resources管理する)
	 * @param secretVersionName 取得対象のシークレットバージョン名
	 * @param secretId ログ・例外メッセージ出力用のシークレット名
	 * @param numRetries 処理失敗時のリトライ回数
	 * @param waitMillis 処理失敗時のスリープ時間(ミリ秒)
	 * @return 取得結果
	 * @throws ReflexSecretException リトライ対象外のエラー、またはリトライ回数を超えた場合
	 */
	private AccessSecretVersionResponse accessSecretVersion(
			SecretManagerServiceClient client, SecretVersionName secretVersionName, 
			String secretId, int numRetries, int waitMillis)
	throws ReflexSecretException {
		for (int r = 0; r <= numRetries; r++) {
			try {
				return client.accessSecretVersion(secretVersionName);

			} catch (NotFoundException e) {
				// シークレットは登録されている前提。未登録は環境設定ミスの可能性が高いため、
				// リトライせず例外として検知できるようにする。
				throw new ReflexSecretException(
						"[accessSecretVersion] Secret not found. secretId=" + secretId, e);

			} catch (ApiException e) {
				if (!e.isRetryable() || r >= numRetries) {
					throw new ReflexSecretException(
							"[accessSecretVersion] Failed to access Secret Manager. secretId=" +
							secretId + ", statusCode=" + e.getStatusCode(), e);
				}
				if (logger.isInfoEnabled()) {
					logger.info("[accessSecretVersion] " + RetryUtil.getRetryLog(e, r));
				}
				RetryUtil.sleep(waitMillis);

			} catch (RuntimeException e) {
				// ApiException以外の想定外の実行時エラー。リトライせず即時ラップしてスローする。
				throw new ReflexSecretException(
						"[accessSecretVersion] Unexpected runtime error. secretId=" + secretId, e);
			}
		}
		// 通らない
		throw new IllegalStateException("The code that should not pass.");
	}

	/**
	 * Secret Managerアクセスエラー時の総リトライ回数を取得.
	 * @return 総リトライ回数
	 */
	private static int getSecretRetryCount() {
		return TaggingEnvUtil.getSystemPropInt(ReflexSecretConst.PROP_SECRET_RETRY_COUNT,
				ReflexSecretConst.SECRET_RETRY_COUNT_DEFAULT);
	}

	/**
	 * Secret Managerアクセスエラーリトライ時の待ち時間(ミリ秒)を取得.
	 * @return 待ち時間(ミリ秒)
	 */
	private static int getSecretRetryWaitmillis() {
		return TaggingEnvUtil.getSystemPropInt(ReflexSecretConst.PROP_SECRET_RETRY_WAITMILLIS,
				ReflexSecretConst.SECRET_RETRY_WAITMILLIS_DEFAULT);
	}

	/**
	 * Cloud Storageアクセスオブジェクトの生成
	 * @param jsonPath サービスアカウントJSONのファイルパス
	 * @return Cloud Storageアクセスオブジェクト
	 */
	private GoogleCredentials getCredentials(String jsonPath) throws IOException {
		// json秘密鍵を読み込む
		try (InputStream jsonFile = FileUtil.getInputStreamFromFile(jsonPath)) {
			return GoogleCredentials.fromStream(jsonFile);
		}
	}

}
