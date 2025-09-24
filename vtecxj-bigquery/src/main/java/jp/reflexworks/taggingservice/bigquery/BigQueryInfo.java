package jp.reflexworks.taggingservice.bigquery;

/**
 * BigQuery接続情報.
 */
public class BigQueryInfo {
	
	/** プロジェクトID */
	private String projectId;
	
	/** データセット名 */
	private String datasetId;
	
	/** ロケーション */
	private String location;

	/** 秘密鍵 */
	private byte[] secret;
	
	/**
	 * コンストラクタ.
	 * @param projectId プロジェクトID
	 * @param datasetId データセット名
	 * @param location ロケーション
	 * @param secret 秘密鍵
	 */
	public BigQueryInfo(String projectId, String datasetId, String location, 
			byte[] secret) {
		this.projectId = projectId;
		this.datasetId = datasetId;
		this.location = location;
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
	 * データセット名を取得
	 * @return データセット名
	 */
	public String getDatasetId() {
		return datasetId;
	}

	/**
	 * ロケーションを取得
	 * @return ロケーション
	 */
	public String getLocation() {
		return location;
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
		return "BigQueryInfo [projectId=" + projectId + ", datasetId=" + datasetId + "]";
	}

}
