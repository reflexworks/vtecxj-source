package jp.reflexworks.taggingservice.recaptcha;

/**
 * reCAPTCHA Enterprise 接続情報.
 */
public class ReCaptchaInfo {
	
	/** プロジェクトID */
	private String projectId;

	/** 秘密鍵 */
	private byte[] secret;
	
	/**
	 * コンストラクタ.
	 * @param projectId プロジェクトID
	 * @param secret 秘密鍵
	 */
	public ReCaptchaInfo(String projectId, byte[] secret) {
		this.projectId = projectId;
		this.secret = secret;
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
	public byte[] getSecret() {
		return secret;
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
