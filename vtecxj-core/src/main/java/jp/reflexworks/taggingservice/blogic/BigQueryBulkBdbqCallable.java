package jp.reflexworks.taggingservice.blogic;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.UpdatedInfo;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;

/**
 * BDB-BigQuery登録処理のうちの、BigQuery非同期登録処理.
 */
public class BigQueryBulkBdbqCallable extends ReflexCallable<Boolean> {

	/** BDB更新Futures */
	private List<Future<List<UpdatedInfo>>> futures;
	/** エンティティの第一階層と異なる名前をテーブル名にする場合に指定 */
	private Map<String, String> tableNames;

	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 登録時のコンストラクタ
	 * @param futures BDB更新Futures
	 * @param tableNames エンティティの第一階層と異なる名前をテーブル名にする場合に指定
	 */
	public BigQueryBulkBdbqCallable(List<Future<List<UpdatedInfo>>> futures, Map<String, String> tableNames) {
		this.futures = futures;
		this.tableNames = tableNames;
	}

	/**
	 * BDB-BigQuery登録処理のうちの、BDB非同期更新の結果確認.
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		ReflexContext reflexContext = (ReflexContext)getReflexContext();
		BigQueryBlogic blogic = new BigQueryBlogic();
		blogic.bulkPutBqProc(futures, tableNames, reflexContext);
		return true;
	}

}
