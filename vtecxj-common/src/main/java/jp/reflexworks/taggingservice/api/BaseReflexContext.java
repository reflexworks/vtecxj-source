package jp.reflexworks.taggingservice.api;

import java.io.IOException;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.sourceforge.reflex.IReflexContext;

/**
 * ReflexContext ベースとなるインターフェース
 */
public interface BaseReflexContext extends IReflexContext {

	/**
	 * 認証情報取得.
	 * @return Reflex内で使用する認証情報
	 */
	public ReflexAuthentication getAuth();

	/**
	 * サービス名取得.
	 * @return サービス名
	 */
	public String getServiceName();

	/**
	 * ResourceMapper取得.
	 * <p>
	 * 起動時に生成したResourceMapperを返却します。
	 * </p>
	 * @return ResourceMapper
	 */
	public FeedTemplateMapper getResourceMapper();

	/**
	 * コネクション情報を取得.
	 * @return コネクション情報
	 */
	public ConnectionInfo getConnectionInfo();

	/**
	 * リクエスト情報を取得.
	 * @return リクエスト情報
	 */
	public RequestInfo getRequestInfo();

	/**
	 * 名前空間取得.
	 * @return 名前空間
	 */
	public String getNamespace() throws IOException, TaggingException;

	/**
	 * Entryを1件取得する.
	 * @param uri URI
	 * @return Entry
	 */
	public EntryBase getEntry(String uri) throws IOException, TaggingException;

	/**
	 * Entryを1件取得する.
	 * @param uri URI
	 * @param useCache キャッシュを使用する場合true
	 * @return Entry
	 */
	public EntryBase getEntry(String uri, boolean useCache) throws IOException, TaggingException;

}
