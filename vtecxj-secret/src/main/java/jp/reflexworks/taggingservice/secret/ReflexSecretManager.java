package jp.reflexworks.taggingservice.secret;

import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.gax.core.FixedCredentialsProvider;
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
import jp.sourceforge.reflex.util.FileUtil;
import jp.sourceforge.reflex.util.StringUtils;

/**
 * シークレット管理クラス
 */
public class ReflexSecretManager implements SecretManager {

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

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
	 * @return 暗号化キー
	 */
	@Override
	public String getSecretKey(ServletContextUtil contextUtil)
	throws IOException, TaggingException {
		String secretFilename = contextUtil.get(ReflexSecretConst.PROP_SECRET_FILE_SECRET);
		String projectId = contextUtil.get(ReflexSecretConst.PROP_GCP_PROJECTID);
		String secretId = contextUtil.get(ReflexSecretConst.PROP_SECRETKEY_NAME);
		String versionId = contextUtil.get(ReflexSecretConst.PROP_SECRETKEY_VERSION);
		return getSecretKey(projectId, secretId, versionId, secretFilename);
	}

	/**
	 * Secret Managerから指定された名称の値を取得.
	 * 取り扱いには注意すること。
	 * @param secretId Secret Managerから取得したい値の名前
	 * @param versionId Secret Managerから取得したい値のバージョン。指定無しの場合はlatest
	 * @return Secret Managerから取得した値
	 */
	@Override
	public String getSecretKey(String secretId, String versionId)
	throws IOException, TaggingException {
		String secretFilename = 
				TaggingEnvUtil.getSystemProp(ReflexSecretConst.PROP_SECRET_FILE_SECRET, null);
		String projectId = 
				TaggingEnvUtil.getSystemProp(ReflexSecretConst.PROP_GCP_PROJECTID, null);
		return getSecretKey(projectId, secretId, versionId, secretFilename);
	}

	/**
	 * Secret Managerから指定された名称の値を取得.
	 * @param projectId Google Cloud の Project ID
	 * @param secretId Secret Managerから取得したい値の名前
	 * @param versionId Secret Managerから取得したい値のバージョン。指定無しの場合はlatest
	 * @param secretFilename サービスアカウントJSON鍵。Workload Identityの設定があれば不要。
	 * @return Secret Managerから取得した値
	 */
	private String getSecretKey(String projectId, String secretId, String versionId, String secretFilename)
	throws IOException, TaggingException {
		String retValue = null;
		
		if (StringUtils.isBlank(projectId)) {
			logger.warn("[getSecretKey] No project id setting.");
			return retValue;
		}
		if (StringUtils.isBlank(secretId)) {
			logger.warn("[getSecretKey] No secret key name setting.");
			return retValue;
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
		try (SecretManagerServiceClient client = SecretManagerServiceClient.create(settings)) {
			// Build the name.
			SecretVersionName secretVersionName = SecretVersionName.of(projectId, secretId, versionId);

			// シークレットの値を取得
			AccessSecretVersionResponse response = client.accessSecretVersion(secretVersionName);
			retValue = response.getPayload().getData().toStringUtf8();
		}
		return retValue;
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
