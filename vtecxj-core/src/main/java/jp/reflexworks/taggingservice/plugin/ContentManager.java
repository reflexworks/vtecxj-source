package jp.reflexworks.taggingservice.plugin;

import java.io.IOException;
import java.util.Map;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.entry.FeedBase;
import jp.reflexworks.taggingservice.api.ReflexContentInfo;
import jp.reflexworks.taggingservice.api.ReflexContext;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.sys.SystemContext;

/**
 * コンテンツ管理インタフェース.
 * このクラスを実装するクラスは起動時に1つのインスタンスが生成され、staticに保持されます。
 * @param <T> コネクション
 */
public interface ContentManager extends ReflexPlugin {

	/**
	 * コンテンツをアップロードする.
	 * @param uri URI
	 * @param data アップロードデータ
	 * @param headers アップロードデータのヘッダ情報
	 * @param isBySize 画像ファイルのサイズ展開を行う場合true
	 * @param reflexContext データアクセスコンテキスト
	 * @return 登録Entry
	 */
	public EntryBase upload(String uri, byte[] data, Map<String, String> headers,
			boolean isBySize, ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * コンテンツをダウンロードする.
	 * @param entry コンテンツEntry
	 * @param reflexContext データアクセスコンテキスト
	 * @return コンテンツ情報。存在しない場合はnullを返す。
	 */
	public ReflexContentInfo download(EntryBase entry, ReflexContext reflexContext)
	throws IOException, TaggingException;

	/**
	 * コンテンツを削除する.
	 * @param uri URI
	 * @param reflexContext データアクセスコンテキスト
	 * @return コンテンツEntry。存在しない場合はnullを返す。
	 */
	public EntryBase delete(String uri, ReflexContext reflexContext)
	throws IOException, TaggingException;
	
	/**
	 * エントリー削除後のコンテンツ削除
	 * @param prevEntry 削除されたエントリー
	 * @param systemContext SystemContext
	 */
	public void afterDeleteEntry(EntryBase prevEntry, SystemContext systemContext)
	throws IOException, TaggingException;

	/**
	 * 署名付きURLを取得.
	 * @param method コンテンツ取得の場合GET、コンテンツ登録の場合PUT、自動採番登録の場合POST
	 * @param uri キー
	 * @param headers リクエストヘッダ
	 * @param reflexContext ReflexContext
	 * @return 署名付きURL
	 */
	public FeedBase getSignedUrl(String method, String uri, Map<String, String> headers, 
			ReflexContext reflexContext)
	throws IOException, TaggingException;

}
