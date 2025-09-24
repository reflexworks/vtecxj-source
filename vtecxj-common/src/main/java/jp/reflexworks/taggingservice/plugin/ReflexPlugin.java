package jp.reflexworks.taggingservice.plugin;

/**
 * TaggingService plugin インターフェース.
 * プラグインは本クラスを継承してください。
 */
public interface ReflexPlugin extends ClosingForShutdown {
	
	/**
	 * 初期起動時の処理.
	 */
	public void init();

}
