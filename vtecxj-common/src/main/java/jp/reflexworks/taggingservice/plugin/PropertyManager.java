package jp.reflexworks.taggingservice.plugin;

import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.regex.Pattern;

import jp.reflexworks.servlet.util.ServletContextUtil;

/**
 * プロパティ管理インターフェース.
 * このクラスを実装するクラスは起動時に1つのインスタンスが生成され、staticに保持されます。
 */
public interface PropertyManager extends SettingService {

	/**
	 * 初期処理
	 * @param contextUtil web.xmlとプロパティファイルから値を取得するクラス
	 */
	public void init(ServletContextUtil contextUtil);

	/**
	 * シャットダウン時の処理
	 */
	public void close();

	/**
	 * 設定値を返却
	 * @param serviceName サービス名
	 * @param key キー
	 * @return 設定値
	 */
	public String getValue(String serviceName, String key);

	/**
	 * 先頭が指定されたキーと等しい環境設定情報一覧を取得
	 * @param seriviceName サービス名
	 * @param prefix キーの先頭部分
	 * @return 環境設定情報一覧
	 */
	public Map<String, String> getMap(String serviceName, String prefix);

	/**
	 * 設定値のPatternオブジェクトを返却.
	 * @param serviceName サービス名
	 * @param key キー
	 * @return 設定値のPatternオブジェクト
	 */
	public Pattern getPattern(String serviceName, String key);

	/**
	 * 先頭が指定されたキーと等しい環境設定情報一覧を取得
	 * @param seriviceName サービス名
	 * @param prefix キーの先頭部分
	 * @return 環境設定情報一覧をSortedMapで返却
	 */
	public SortedMap<String, String> getSortedMap(String serviceName, String prefix);

	/**
	 * 先頭が指定されたキーと等しい環境設定情報一覧を取得
	 * @param prefix キーの先頭部分
	 * @param seriviceName サービス名
	 * @return 環境設定情報一覧をSetで返却
	 */
	public Set<String> getSet(String serviceName, String prefix);

	/**
	 * サービス固有の設定値を返却.
	 *   /_settings/properties に設定された値のみ参照し返却.
	 * @param serviceName サービス名
	 * @param key キー
	 * @return 設定値
	 */
	public String getServiceSettingValue(String serviceName, String key);

}
