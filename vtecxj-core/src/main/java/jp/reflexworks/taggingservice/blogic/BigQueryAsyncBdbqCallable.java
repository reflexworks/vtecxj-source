package jp.reflexworks.taggingservice.blogic;

import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.taskqueue.ReflexCallable;
import jp.reflexworks.taggingservice.util.Constants.OperationType;

/**
 * BDB-BigQuery登録処理のうちの、BigQuery非同期登録処理.
 */
public class BigQueryAsyncBdbqCallable extends ReflexCallable<Boolean> {

	/** 登録・更新・削除区分 */
	private OperationType operationType;

	/** BDB登録データ */
	private FeedBase feed;
	/** 登録時の自動採番用親キー */
	private String parentUri;
	/** エンティティの第一階層と異なる名前をテーブル名にする場合に指定 */
	private Map<String, String> tableNames;
	
	/** BDB削除キーリスト */
	private String[] uris;
	
	/** ロガー. */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 登録・更新時のコンストラクタ
	 * @param operationType 登録・更新・削除区分
	 * @param feed BDB登録データ
	 * @param parentUri 登録時の自動採番用親キー
	 * @param tableNames エンティティの第一階層と異なる名前をテーブル名にする場合に指定
	 */
	public BigQueryAsyncBdbqCallable(OperationType operationType, FeedBase feed, 
			String parentUri, Map<String, String> tableNames) {
		this.operationType = operationType;
		this.feed = feed;
		this.parentUri = parentUri;
		this.tableNames = tableNames;
	}

	/**
	 * 登録・更新時のコンストラクタ
	 * @param operationType 登録・更新・削除区分
	 * @param uris 削除キーリスト
	 * @param tableNames エンティティの第一階層と異なる名前をテーブル名にする場合に指定
	 */
	public BigQueryAsyncBdbqCallable(OperationType operationType, String[] uris, 
			Map<String, String> tableNames) {
		this.operationType = operationType;
		this.uris = uris;
		this.tableNames = tableNames;
	}

	/**
	 * BDB-BigQuery登録処理のうちの、BDB非同期更新の結果確認.
	 */
	@Override
	public Boolean call() throws IOException, TaggingException {
		ReflexContext reflexContext = (ReflexContext)getReflexContext();
		BigQueryBlogic blogic = new BigQueryBlogic();
		if (operationType.equals(OperationType.INSERT)) {
			blogic.postBdbqProc(feed, parentUri, tableNames, reflexContext);
		} else if (operationType.equals(OperationType.UPDATE)) {
			blogic.putBdbqProc(feed, parentUri, tableNames, reflexContext);
		} else {	// DELETE
			blogic.deleteBdbqProc(uris, tableNames, reflexContext);
		}
		return true;
	}

}
