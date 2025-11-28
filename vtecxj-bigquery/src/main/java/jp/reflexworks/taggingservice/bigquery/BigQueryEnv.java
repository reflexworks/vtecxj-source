package jp.reflexworks.taggingservice.bigquery;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.bigquery.BigQuery;

/**
 * BigQuery用staticクラス
 */
public class BigQueryEnv {

	/** 
	 * Googleデフォルト認証情報
	 *  (環境変数 GOOGLE_APPLICATION_CREDENTIALS 等から自動取得)
	 */
    private GoogleCredentials googleCredentials;

	/** 
	 * Googleデフォルト接続情報
	 */
    private BigQuery bigQueryDefault;

	/**
	 * Googleデフォルト認証情報を取得
	 * @return Googleデフォルト認証情報を取得
	 */
	public GoogleCredentials getGoogleCredentials() {
		return googleCredentials;
	}

	/**
	 * Googleデフォルト認証情報を設定
	 * @param Googleデフォルト認証情報
	 */
	void setGoogleCredentials(GoogleCredentials googleCredentials) {
		this.googleCredentials = googleCredentials;
	}

	/**
	 * BigQueryデフォルト接続オブジェクトを取得
	 * @return BigQueryデフォルト接続オブジェクト
	 */
	public BigQuery getBigQueryDefault() {
		return bigQueryDefault;
	}

	/**
	 * BigQueryデフォルト接続オブジェクトを設定
	 * @param bigqueryDefault BigQueryデフォルト接続オブジェクト
	 */
	void setBigQueryDefault(BigQuery bigQueryDefault) {
		this.bigQueryDefault = bigQueryDefault;
	}

}
