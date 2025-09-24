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

}
