package jp.reflexworks.taggingservice.bigquery;

import com.google.cloud.bigquery.BigQueryException;

/**
 * BigQuery実行例外.
 */
public class ReflexBigQueryException extends Exception {

	/** 生成シリアルバージョンUID */
	private static final long serialVersionUID = -145927027425721196L;

	/**
	 * コンストラクタ
	 * @param e BigQuery例外
	 */
	public ReflexBigQueryException(BigQueryException e) {
		super(e);
	}

}
