package jp.reflexworks.taggingservice.plugin;

import java.io.IOException;

import jp.reflexworks.taggingservice.exception.TaggingException;

/**
 * SecretManagerを使用しているプラグインが継承するインターフェース
 * SecretManagerの値を更新したときの再読み込み呼び出しメソッドを定義します。
 */
public interface InUseSecretManager {
	
	/**
	 * SecretManagerの再読み込み.
	 */
	public void reloadSecret() throws IOException, TaggingException;

}
