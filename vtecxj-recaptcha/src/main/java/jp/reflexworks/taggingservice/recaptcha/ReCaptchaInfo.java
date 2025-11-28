package jp.reflexworks.taggingservice.recaptcha;

/**
 * reCAPTCHA Enterprise 接続情報.
 */
public class ReCaptchaInfo {
	
	/** プロジェクトID */
	private String projectId;

	/** 秘密鍵 */
	//private byte[] secret;

	/** サービスアカウント */
	private String serviceAccount;

	/**
	 * コンストラクタ.
	 * @param projectId プロジェクトID
	 * @param secret 秘密鍵
	 * @param serviceAccount サービスアカウント
	 */
	/*
	public ReCaptchaInfo(String projectId, byte[] secret, String serviceAccount) {
		this.projectId = projectId;
		this.secret = secret;
		this.serviceAccount = serviceAccount;
	}
	*/

	/**
	 * コンストラクタ.
	 * @param projectId プロジェクトID
	 * @param serviceAccount サービスアカウント
	 */
	public ReCaptchaInfo(String projectId, String serviceAccount) {
		this.projectId = projectId;
		this.serviceAccount = serviceAccount;
	}

	/**
	 * プロジェクトIDを取得
	 * @return プロジェクトID
	 */
	public String getProjectId() {
		return projectId;
	}

	/**
	 * 秘密鍵を取得
	 * @return 秘密鍵
	 */
	/*
	public byte[] getSecret() {
		return secret;
	}
	*/

	/**
	 * サービスアカウントを取得
	 * @return サービスアカウント
	 */
	public String getServiceAccount() {
		return serviceAccount;
	}

	/**
	 * 文字列表現を返却.
	 * @return このインスタンスの文字列表現
	 */
	@Override
	public String toString() {
		return "ReCaptchaInfo [projectId=" + projectId + "]";
	}

}
