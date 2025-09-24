package jp.reflexworks.taggingservice.api;

import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import jp.reflexworks.atom.mapper.FeedTemplateMapper;
import jp.reflexworks.taggingservice.exception.InvalidServiceSettingException;

/**
 * 環境情報.
 * 実装クラスはサービス起動時に１つだけ生成される。
 */
public interface BaseReflexEnv {

	/**
	 * 初期処理.
	 */
	public void init();

	/**
	 * 終了処理.
	 */
	public void close();

	/**
	 * サービスのResourceMapperを返却.
	 * @param serviceName サービス名
	 * @return サービスのResourceMapper
	 */
	public FeedTemplateMapper getResourceMapper(String serviceName);

	/**
	 * 標準ATOM形式のResourceMapperを返却.
	 * @return 標準ATOM形式のResourceMapper
	 */
	public FeedTemplateMapper getAtomResourceMapper();

	/**
	 * システムサービスの設定値を返却
	 * @param key キー
	 * @return 設定値
	 */
	public String getSystemProp(String key);

	/**
	 * システムサービスの設定値を返却
	 * 設定値が無い場合はデフォルト値を返却します。
	 * @param key キー
	 * @param def デフォルト値。設定値がnullの場合に適用される。
	 * @return 設定値
	 */
	public String getSystemProp(String key, String def);

	/**
	 * システムサービスの設定値を返却.
	 * Integer型に置き換えられない場合はnullを返却します。
	 * @param key キー
	 * @return 設定値
	 * @throws InvalidServiceSettingException Integerに変換できない値が設定されている場合
	 */
	public Integer getSystemPropInteger(String key)
	throws InvalidServiceSettingException;

	/**
	 * システムサービスの設定値を返却.
	 * 設定値が無い場合、Integer型に置き換えられない場合はデフォルト値を返却します。
	 * @param key キー
	 * @param def デフォルト値。設定値がnullの場合に適用される。
	 * @return 設定値
	 * @throws InvalidServiceSettingException Integerに変換できない値が設定されている場合
	 */
	public int getSystemPropInteger(String key, int def)
	throws InvalidServiceSettingException;

	/**
	 * システムサービスの設定値を返却.
	 * 設定値が無い場合、Long型に置き換えられない場合はnullを返却します。
	 * @param key キー
	 * @return 設定値
	 * @throws InvalidServiceSettingException Longに変換できない値が設定されている場合
	 */
	public Long getSystemPropLong(String key)
	throws InvalidServiceSettingException;

	/**
	 * システムサービスの設定値を返却.
	 * 設定値が無い場合、Long型に置き換えられない場合はデフォルト値を返却します。
	 * @param key キー
	 * @return 設定値
	 * @throws InvalidServiceSettingException Longに変換できない値が設定されている場合
	 */
	public long getSystemPropLong(String key, long def)
	throws InvalidServiceSettingException;

	/**
	 * システムサービスの設定値を返却.
	 * 設定値が無い場合、Double型に置き換えられない場合はnullを返却します。
	 * @param key キー
	 * @param def デフォルト値。設定値がnullの場合に適用される。
	 * @return 設定値
	 * @throws InvalidServiceSettingException Doubleに変換できない値が設定されている場合
	 */
	public Double getSystemPropDouble(String key)
	throws InvalidServiceSettingException;

	/**
	 * システムサービスの設定値を返却.
	 * 設定値が無い場合、Double型に置き換えられない場合はデフォルト値を返却します。
	 * @param key キー
	 * @param def デフォルト値。設定値がnullの場合に適用される。
	 * @return 設定値
	 * @throws InvalidServiceSettingException Doubleに変換できない値が設定されている場合
	 */
	public double getSystemPropDouble(String key, double def)
	throws InvalidServiceSettingException;

	/**
	 * システムサービスの設定値を返却.
	 * 設定値が無い場合、Boolean型に置き換えられない場合はnullを返却します。
	 * @param key キー
	 * @return 設定値
	 * @throws InvalidServiceSettingException Booleanに変換できない値が設定されている場合
	 */
	public Boolean getSystemPropBoolean(String key)
	throws InvalidServiceSettingException;

	/**
	 * システムサービスの設定値を返却.
	 * 設定値が無い場合、Boolean型に置き換えられない場合はデフォルト値を返却します。
	 * @param key キー
	 * @return 設定値
	 * @throws InvalidServiceSettingException Booleanに変換できない値が設定されている場合
	 */
	public boolean getSystemPropBoolean(String key, boolean def)
	throws InvalidServiceSettingException;

	/**
	 * システムサービスの、先頭が指定されたキーと等しい環境設定情報一覧を取得
	 * @param prefix キーの先頭部分
	 * @return 環境設定情報一覧
	 */
	public Map<String, String> getSystemPropMap(String prefix);

	/**
	 * システムサービスの先頭が指定されたキーと等しい環境設定情報一覧を取得
	 * @param prefix キーの先頭部分
	 * @return 環境設定情報一覧をSortedMapで返却
	 */
	public SortedMap<String, String> getSystemPropSortedMap(String prefix);

	/**
	 * システムサービスの、先頭が指定されたキーと等しい環境設定情報一覧を取得
	 * @param prefix キーの先頭部分
	 * @return 環境設定情報一覧をSetで返却
	 */
	public Set<String> getSystemPropSet(String prefix);

	/**
	 * web.xmlまたはプロパティファイルから値を取得.
	 * @param name 名前
	 * @return web.xmlまたはプロパティファイルに設定された値
	 */
	public String getContextValue(String name);

	/**
	 * システムサービスを取得
	 * @return システムサービス名
	 */
	public String getSystemService();

	/**
	 * サーバ稼働中かどうか.
	 * 起動処理中の場合falseを返す。
	 * @return サーバ稼働中の場合true
	 */
	public boolean isRunning();

}
