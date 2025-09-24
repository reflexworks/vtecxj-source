package jp.reflexworks.taggingservice.plugin;

import java.io.IOException;
import java.util.List;

import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.UpdatedInfo;

/**
 * エントリー更新後に呼び出しが必要なインスタンスが継承するインターフェース.
 */
public interface CallingAfterCommit {
	
	/**
	 * エントリー更新後の処理
	 * @param updatedInfos 更新情報リスト
	 * @param reflexContext ReflexContext
	 */
	public void doAfterCommit(List<UpdatedInfo> updatedInfos, ReflexContext reflexContext)
	throws IOException, TaggingException;
}
