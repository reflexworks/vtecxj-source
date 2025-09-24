package jp.reflexworks.taggingservice.plugin;

import java.io.IOException;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.taggingservice.api.ConnectionInfo;
import jp.reflexworks.taggingservice.api.RequestInfo;
import jp.reflexworks.taggingservice.exception.TaggingException;

/**
 * 名前空間管理インターフェース.
 */
public interface NamespaceManager extends SettingService {

	/**
	 * 名前空間を取得.
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 名前空間
	 */
	public String getNamespace(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

	/**
	 * 名前空間を指定された値に更新.
	 * @param namespace 名前空間
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 */
	public void setNamespace(String namespace, String serviceName,
			RequestInfo requestInfo, ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

	/**
	 * 名前空間を変更.
	 * 名前空間を新しく発行し、設定を変更する。
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 新しく発行した名前空間
	 */
	public String changeNamespace(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

	/**
	 * 名前空間を発行しEntryを生成.
	 * 他のエントリーと同時に更新する場合に使用。
	 * @param serviceName サービス名
	 * @param requestInfo リクエスト情報
	 * @param connectionInfo コネクション情報
	 * @return 新しく発行した名前空間Entry
	 */
	public EntryBase createChangeNamespaceEntry(String serviceName, RequestInfo requestInfo,
			ConnectionInfo connectionInfo)
	throws IOException, TaggingException;

	/**
	 * 名前空間設定Entryを生成
	 * @param namespace 名前空間
	 * @param serviceName サービス名
	 * @return 名前空間設定Entry
	 */
	public EntryBase createNamespaceEntry(String namespace, String serviceName);

	/**
	 * static mapに、名前空間を設定.
	 * @param serviceName サービス名
	 * @param namespace 名前空間
	 */
	public void setStaticNamespace(String serviceName, String namespace);

}
