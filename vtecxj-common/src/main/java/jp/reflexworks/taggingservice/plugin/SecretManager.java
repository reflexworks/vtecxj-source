package jp.reflexworks.taggingservice.plugin;

import java.io.IOException;

import jp.reflexworks.servlet.util.ServletContextUtil;
import jp.reflexworks.taggingservice.exception.TaggingException;

/**
 * シークレット管理インターフェース
 */
public interface SecretManager extends ReflexPlugin {

	/**
	 * 暗号化キーを取得.
	 * サーバ起動時に呼び出されるため、プロパティ値は ServletContextUtil から取得する。
	 * 取得した暗号化キーはResourceMapper(エントリーのシリアライズ・デシリアライズツール)にセットする。
	 * @return 暗号化キー
	 */
	public String getSecretKey(ServletContextUtil contextUtil)
	throws IOException, TaggingException;

	/**
	 * Secret Managerから指定された名称の値を取得.
	 * 取り扱いには注意すること。
	 * @param secretId Secret Managerから取得したい値の名前
	 * @param versionId Secret Managerから取得したい値のバージョン。指定無しの場合はlatest
	 * @return Secret Managerから取得した値
	 */
	public String getSecretKey(String secretId, String versionId)
	throws IOException, TaggingException;

}
