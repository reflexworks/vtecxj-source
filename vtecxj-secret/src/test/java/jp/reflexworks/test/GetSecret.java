package jp.reflexworks.test;

import java.io.IOException;
import java.io.InputStream;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretManagerServiceSettings;
import com.google.cloud.secretmanager.v1.SecretVersionName;

import jp.sourceforge.reflex.util.FileUtil;

public class GetSecret {

	/**
	 * メイン処理
	 * @param args [0]シークレットID
	 *             [1]プロジェクトID
	 *             [2]JSON秘密鍵
	 * @throws Exception
	 */
	public static void main(String... args) throws Exception {
		if (args == null || args.length < 3) {
			throw new IllegalArgumentException("Arguments are required. [0]Secret ID [1]Project ID [2]JSON secret path");
		}

		// Sets your Google Cloud Platform project ID.
		String secretId = args[0];
		String projectId = args[1];
		String jsonPath = args[2];

		getSecret(projectId, secretId, jsonPath);
	}

	// Get an existing secret.
	public static void getSecret(String projectId, String secretId, String jsonPath) throws IOException {

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
			String versionId = "latest";
			SecretVersionName secretVersionName = SecretVersionName.of(projectId, secretId, versionId);
			System.out.println("[getSecret] secretVersionName: " + secretVersionName);

			AccessSecretVersionResponse response = client.accessSecretVersion(secretVersionName);

			String actualSecretVersionName = response.getName();
			String actualVersionId = SecretVersionName.parse(actualSecretVersionName).getSecretVersion();

			String secretPayload = response.getPayload().getData().toStringUtf8();

			System.out.println("[getSecret] actual secret version name: " + actualSecretVersionName);
			System.out.println("[getSecret] actual version id: " + actualVersionId);
			System.out.println("[getSecret] Latest secret payload: " + secretPayload);
		}
	}

	/**
	 * Cloud Storageアクセスオブジェクトの生成
	 * @param jsonPath サービスアカウントJSONのファイルパス
	 * @return Cloud Storageアクセスオブジェクト
	 */
	static GoogleCredentials getCredentials(String jsonPath) throws IOException {
		// json秘密鍵を読み込む
		try (InputStream jsonFile = FileUtil.getInputStreamFromFile(jsonPath)) {
			return GoogleCredentials.fromStream(jsonFile);
		}
	}

}
