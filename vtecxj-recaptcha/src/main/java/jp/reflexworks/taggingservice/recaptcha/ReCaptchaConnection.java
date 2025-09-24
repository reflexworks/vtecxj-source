package jp.reflexworks.taggingservice.recaptcha;

import com.google.cloud.recaptchaenterprise.v1.RecaptchaEnterpriseServiceClient;
import com.google.recaptchaenterprise.v1.Assessment;
import com.google.recaptchaenterprise.v1.CreateAssessmentRequest;

import jp.reflexworks.taggingservice.conn.ReflexConnection;

/**
 * reCAPTCHA Enterpriseコネクション.
 */
public class ReCaptchaConnection implements ReflexConnection<RecaptchaEnterpriseServiceClient> {
	
	/** reCAPTCHA Enterprise コネクション */
	private RecaptchaEnterpriseServiceClient client;

	/**
	 * コンストラクタ.
	 * @param reCAPTCHA Enterprise コネクション
	 */
	public ReCaptchaConnection(RecaptchaEnterpriseServiceClient client) {
		this.client = client;
	}
	
	/**
	 * Cloud Storageコネクションを取得.
	 * @return Cloud Storageコネクション
	 */
	public RecaptchaEnterpriseServiceClient getConnection() {
		return client;
	}
	
	/**
	 * クローズ処理.
	 */
	public void close() {
		client.close();
	}
	
	/**
	 * リクエストを評価する
	 * @param createAssessmentRequest 評価リクエスト
	 * @return リクエストの評価
	 */
	public Assessment createAssessment(CreateAssessmentRequest createAssessmentRequest) {
		return client.createAssessment(createAssessmentRequest);
	}

}
