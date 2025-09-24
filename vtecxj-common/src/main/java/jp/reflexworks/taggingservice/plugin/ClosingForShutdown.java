package jp.reflexworks.taggingservice.plugin;

/**
 * サーブレット停止時にクローズ処理が必要なインスタンスが継承するインターフェース.
 * static領域に格納されるインスタンスで、クローズ処理が必要なものは当インターフェースを実装してください。
 */
public interface ClosingForShutdown {
	
	/**
	 * シャットダウン時の処理.
	 */
	public void close();

}
