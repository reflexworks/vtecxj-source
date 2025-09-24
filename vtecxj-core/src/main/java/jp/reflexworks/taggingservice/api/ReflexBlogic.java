package jp.reflexworks.taggingservice.api;

import java.io.IOException;

import jp.reflexworks.taggingservice.exception.TaggingException;

/**
 * バッチ用ビジネスロジックインターフェース
 * @param <T> ReflexContext
 */
public interface ReflexBlogic<T extends ReflexContext, O> {
	
	/**
	 * 実行処理.
	 * @param reflexContext ReflexContext
	 * @param args 引数 (ReflexApplication実行時の引数の4番目以降)
	 * @return 処理結果
	 */
	public O exec(T reflexContext, String[] args) throws IOException, TaggingException;
	
}
