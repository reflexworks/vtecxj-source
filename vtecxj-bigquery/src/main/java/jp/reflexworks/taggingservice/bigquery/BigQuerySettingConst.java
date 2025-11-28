package jp.reflexworks.taggingservice.bigquery;

import java.util.Arrays;
import java.util.List;

/**
 * サービスごとの設定項目一覧.
 * ここに定義されている項目は、/_settings/properties に設定できます。
 * サービスの情報のみ使用し、システムの情報を無視する設定は「IGNORE_SYSTEM_INFO」に「List<String>」形式で定義してください。
 * 
 * ```
 *   	public static final List<String> IGNORE_SYSTEM_INFO =
 *  			Arrays.asList(new String[]{
 * 					xxxx, ... 
 * 			});
 * ```
 */
public interface BigQuerySettingConst {

	/** BigQueryのプロジェクトID */
	public static final String BIGQUERY_PROJECTID = "_bigquery.projectid";
	/** BigQueryのデータセット名 */
	public static final String BIGQUERY_DATASET = "_bigquery.dataset";
	/** BigQueryのロケーション */
	public static final String BIGQUERY_LOCATION = "_bigquery.location";
	/** BigQueryのサービスアカウント(Email形式) */
	public static final String BIGQUERY_SERVICEACCOUNT = "_bigquery.serviceaccount";

	/**
	 * サービスの情報のみ使用し、システムの情報を無視する設定一覧.
	 */
	public static final List<String> IGNORE_SYSTEM_INFO =
			Arrays.asList(new String[]{
					BIGQUERY_PROJECTID, 
					BIGQUERY_DATASET,
					BIGQUERY_LOCATION,
					BIGQUERY_SERVICEACCOUNT,
			});

}
