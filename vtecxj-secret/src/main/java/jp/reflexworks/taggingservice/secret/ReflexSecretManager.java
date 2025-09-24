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
	 * サーバ起動時に呼び出されるため、プロパティ値は ServletContextUtil から取得する。
	 * 取得した暗号化キーはResourceMapper(エントリーのシリアライズ・デシリアライズツール)にセットする。
	 * @return 暗号化キー
	 */
	@Override
	public String getSecretKey(ServletContextUtil contextUtil)
	throws IOException, TaggingException {
		String secretFilename = contextUtil.get(ReflexSecretConst.PROP_SECRET_FILE_SECRET);
		String projectId = contextUtil.get(ReflexSecretConst.PROP_GCP_PROJECTID);
		String secretId = contextUtil.get(ReflexSecretConst.PROP_SECRETKEY_NAME);

		String retValue = null;
		
		if (StringUtils.isBlank(secretFilename)) {
			logger.warn("[getSecretOfResourceMapper] No secret filename setting.");
			return retValue;
		}
		if (StringUtils.isBlank(projectId)) {
			logger.warn("[getSecretOfResourceMapper] No project id setting.");
			return retValue;
		}
		if (StringUtils.isBlank(secretId)) {
			logger.warn("[getSecretOfResourceMapper] No secret key name setting.");
			return retValue;
		}
		
		String versionId = contextUtil.get(ReflexSecretConst.PROP_SECRETKEY_VERSION);
		if (StringUtils.isBlank(versionId)) {
			versionId = ReflexSecretConst.VERSION_LATEST;
		}

		String jsonPath = FileUtil.getResourceFilename(secretFilename);
		Credentials credentials = getCredentials(jsonPath);

		SecretManagerServiceSettings.Builder clientSettingsBuilder = 
				SecretManagerServiceSettings.newBuilder()
				.setCredentialsProvider(FixedCredentialsProvider.create(credentials));

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
