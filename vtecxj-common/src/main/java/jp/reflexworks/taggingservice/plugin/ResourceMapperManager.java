package jp.reflexworks.taggingservice.plugin;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Pattern;

import jp.reflexworks.atom.entry.EntryBase;
import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.servlet.util.ServletContextUtil;
import jp.reflexworks.taggingservice.api.BaseReflexContext;
import jp.reflexworks.taggingservice.exception.TaggingException;
import jp.reflexworks.taggingservice.model.Template;

/**
 * ResourceMapperを生成、保持するクラスのインターフェース.
 */
public interface ResourceMapperManager extends SettingService {

	/**
	 * 初期処理に呼ばれるメソッド.
	 * @param contextUtil ServletContextUtil
	 * @param secretKey 暗号化キー
	 */
	public void init(ServletContextUtil contextUtil, String secretKey);

	/**
	 * 標準ATOM形式のResourceMapperを返却.
	 * @reutrn 標準ATOM形式のResourceMapper
	 */
	public FeedTemplateMapper getAtomResourceMapper();

	/**
	 * サービスのResourceMapperを返却.
	 * @param serviceName サービス名
	 * @reutrn ResourceMapper
	 */
	public FeedTemplateMapper getResourceMapper(String serviceName);

	/**
	 * テンプレート項目のIndex一覧を取得
	 * @param serviceName サービス名
	 * @return テンプレート項目のIndexを格納したMap
	 */
	public Map<String, Pattern> getTemplateIndexMap(String serviceName);

	/**
	 * テンプレート項目の全文検索Index一覧を取得
	 * @param serviceName サービス名
	 * @return テンプレート項目の全文検索Indexを格納したMap
	 */
	public Map<String, Pattern> getTemplateFullTextIndexMap(String serviceName);

	/**
	 * テンプレート項目のDISTKEY一覧を取得
	 * @param serviceName サービス名
	 * @return テンプレート項目のDISTKEYを格納したMap
	 */
	public Map<String, Pattern> getTemplateDistkeyMap(String serviceName);

	/**
	 * テンプレートエントリーのキーであればtrueを返却します.
	 * <p>
	 * キーが /_settings/template であればテンプレートエントリーです。
	 * </p>
	 * @param uri キー
	 * @return テンプレートエントリーのキーであればtrue
	 */
	public boolean isTemplateUri(String uri);

	/**
	 * テンプレート情報を取得.
	 * テンプレート保持Entry /_settings/template のcontextとrightsから
	 * テンプレート情報を取得します。
	 * @param settingsEntry テンプレート保持Entry (/_settings/template)
	 * @param serviceName サービス名
	 * @return テンプレート情報
	 */
	public Template getTemplate(EntryBase settingsEntry, String serviceName);

	/**
	 * サービス初期設定時の処理.
	 * 実行ノードで指定されたサービスが初めて実行された際に呼び出されます。
	 * この処理は他のスレッドを排他して呼ばれます。
	 * SettingService インターフェースの settingService メソッドは呼ばれず、こちらのメソッドが実行されます。
	 * @param systemContext システム権限のReflexContext
	 * @return サービス初期設定で内部情報が更新された場合true
	 */
	public boolean settingService(BaseReflexContext systemContext)
	throws IOException, TaggingException;

}
